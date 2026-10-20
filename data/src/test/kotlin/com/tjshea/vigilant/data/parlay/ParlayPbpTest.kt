package com.tjshea.vigilant.data.parlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CH47 (Tj, 2026-10-20): ParlayAPI's live play-by-play stream as the default score source; the frames below are the REAL ones the probe read (Release parlay-samples, sse_basketball_nba.txt). */
class ParlayPbpTest {
    private val initial = """{"sport_key": "basketball_nba", "matches": [{"match_id": "401909089", "sport_key": "basketball_nba", "primary_source": "espn_nba", "last_event_id": 3891657, "last_event_at_ms": 1792540108579, "team_or_player_a": "Philadelphia 76ers", "team_or_player_b": "New York Knicks", "score_a": "23", "score_b": "25", "state": {"id": "401909089198", "clock": "0.0", "period": 1, "players": {}, "description": "End of the 1st Quarter", "scoring_play": false, "commence_time": "2026-10-20T23:00Z"}, "in_play": true, "last_event_age_seconds": 15.5}, {"match_id": "401909088", "sport_key": "basketball_nba", "primary_source": "espn_nba", "last_event_id": 3890731, "last_event_at_ms": 1792532203668, "team_or_player_a": "Boston Celtics", "team_or_player_b": "Detroit Pistons", "score_a": "113", "score_b": "121", "state": {"id": "401909088732", "clock": "0.0", "period": 4, "players": {}, "description": "End of Game", "scoring_play": false, "commence_time": "2026-10-20T19:00Z"}, "in_play": true, "last_event_age_seconds": 7920.4}]}"""
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    @Test
    fun `the real initial_state is read, the finished game is not live, and a score is given in Novig's orientation either way round`() {
        val ms = ParlayPbp.parseInitial(initial, 1_000L)
        assertEquals(2, ms.size)
        val live = ms.first { it.id == "401909089" }
        assertEquals(23, live.scoreA); assertEquals(25, live.scoreB); assertEquals(1, live.period); assertEquals(0.0, live.clockSec!!, 1e-9)
        assertTrue(live.inPlay); assertTrue(ParlayPbp.clockTrusted(live))
        assertFalse(ms.first { it.id == "401909088" }.inPlay)   // "End of Game" although the feed still says in_play
        val g = ParlayPbp.asGame(live, home = "New York Knicks", away = "Philadelphia 76ers")!!
        assertEquals(25, g.homeScore); assertEquals(23, g.awayScore)
        val swapped = ParlayPbp.asGame(live, home = "Philadelphia 76ers", away = "New York Knicks")!!
        assertEquals(23, swapped.homeScore); assertEquals(25, swapped.awayScore)
        assertNull(ParlayPbp.asGame(live, "Boston Celtics", "Miami Heat"))
    }

    @Test
    fun `a pbp_event with only the changed keys is merged over the match, and a match from a non-ESPN source is not used as a score`() {
        val first = ParlayPbp.parseInitial(initial, 1_000L).first { it.id == "401909089" }
        val next = ParlayPbp.parseEvent("""{"match_id":"401909089","score_a":"26","score_b":"25","state":{"clock":"11:42","period":2,"description":"Joel Embiid makes layup"},"captured_at_ms":1792540200000}""", 2_000L) { first }!!
        assertEquals("Philadelphia 76ers", next.a); assertEquals(26, next.scoreA); assertEquals(2, next.period); assertEquals(702.0, next.clockSec!!, 1e-9); assertEquals("espn_nba", next.source)
        assertNotNull(ParlayPbp.scoreFor(listOf(next), "New York Knicks", "Philadelphia 76ers", heardMs = 5_000L))
        assertEquals(5_000L, ParlayPbp.scoreFor(listOf(next), "New York Knicks", "Philadelphia 76ers", heardMs = 5_000L)!!.atMs)
        assertNull(ParlayPbp.scoreFor(listOf(next.copy(source = "sofascore")), "New York Knicks", "Philadelphia 76ers", 5_000L))
        assertNull(ParlayPbp.scoreFor(listOf(next.copy(clockSec = null)), "New York Knicks", "Philadelphia 76ers", 5_000L))
    }

    @Test
    fun `the stream sends the key in a header, fills the board from initial_state and pbp_event, and gives a score for a live game`() {
        val body = "event: initial_state\ndata: $initial\n\n: keepalive\n\n" +
            "event: pbp_event\ndata: {\"match_id\":\"401909089\",\"score_a\":\"26\",\"score_b\":\"25\",\"state\":{\"clock\":\"11:42\",\"period\":2}}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
        val s = ParlayPbpStream(OkHttpClient(), scope, key = { "k1" }, baseUrl = server.url("").toString().trimEnd('/'), backoffMs = 60_000L)
        assertNull(s.scoreFor("NBA", "New York Knicks", "Philadelphia 76ers"))   // the first ask opens the stream
        val deadline = System.currentTimeMillis() + 5_000
        var got: com.tjshea.vigilant.data.novig.lab.AltScore? = null
        while (got == null && System.currentTimeMillis() < deadline) { Thread.sleep(25); got = s.scoreFor("NBA", "New York Knicks", "Philadelphia 76ers") }
        assertNotNull(got)
        assertEquals(25, got!!.game.homeScore); assertEquals(26, got.game.awayScore); assertEquals("ParlayAPI", got.source)
        val req = server.takeRequest()
        assertEquals("/v1/sports/basketball_nba/live/sse", req.requestUrl!!.encodedPath)
        assertEquals("k1", req.getHeader("X-API-Key"))
        assertNull(req.requestUrl!!.queryParameter("apiKey"))
        assertNull(s.scoreFor("MLS", "A", "B"))   // a league ParlayAPI's stream is not asked about
    }

    @Test
    fun `a refused stream is left alone for a while and a missing key opens nothing`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val s = ParlayPbpStream(OkHttpClient(), scope, key = { "k1" }, baseUrl = server.url("").toString().trimEnd('/'), backoffMs = 60_000L)
        s.scoreFor("NHL", "A", "B")
        val deadline = System.currentTimeMillis() + 5_000
        while (server.requestCount == 0 && System.currentTimeMillis() < deadline) Thread.sleep(25)
        Thread.sleep(200)
        s.scoreFor("NHL", "A", "B")
        Thread.sleep(200)
        assertEquals(1, server.requestCount)
        val none = ParlayPbpStream(OkHttpClient(), scope, key = { null }, baseUrl = server.url("").toString().trimEnd('/'))
        none.scoreFor("NBA", "A", "B"); Thread.sleep(100)
        assertEquals(1, server.requestCount)
    }
}
