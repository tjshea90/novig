package com.tjshea.vigilant.data.reference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLS3 (Tj, 2026-11-17: "Novig has its own socket ... can that socket be used better for something else"): the second Streaming Lite socket carries PropLine's live `game_state` (scores every ~5 s for MLB, NBA, NHL,
 * NFL, NCAAF) as a score source for the live lane, beside the steam/grades/+EV journals; and the account's own earlier signals subscription is brought up to date instead of being left beside a new one.
 */
class PropLineGameStateTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun obj(s: String): JsonObject = json.parseToJsonElement(s).jsonObject

    private fun state(status: String = "in_progress", home: String = "Boston Celtics", away: String = "New York Knicks", hs: Int = 55, aws: Int = 52, clock: String? = "6:02", period: Int = 3) =
        """{"event_id":"692353","sport_key":"basketball_nba","home_team":"$home","away_team":"$away","status":"$status","home_score":$hs,"away_score":$aws,"period":"Q$period","period_number":$period,"clock":${clock?.let { "\"$it\"" } ?: "null"}}"""

    @Test
    fun aLiveGameBecomesAScoreInNovigsOrientationWhicheverWayTheTeamsAreListed() {
        var now = 1_000_000L
        val g = PropLineGameState { now }
        g.apply(obj(state()), now)
        val straight = g.scoreFor("NBA", "Boston Celtics", "New York Knicks", now)
        assertNotNull(straight)
        assertEquals(55, straight!!.game.homeScore); assertEquals(52, straight.game.awayScore)
        assertEquals(3, straight.game.period); assertEquals(362.0, straight.game.clockSec, 1e-9)
        assertEquals("PropLine", straight.source)
        // Novig lists the teams the other way round: the scores follow the names, not the order.
        val swapped = g.scoreFor("NBA", "New York Knicks", "Boston Celtics", now)!!
        assertEquals(52, swapped.game.homeScore); assertEquals(55, swapped.game.awayScore)
        now += 1; assertEquals(1, g.live())
    }

    @Test
    fun noScoreForADifferentLeagueAFinishedGameAnUnstartedGameOrASilentSocket() {
        val now = 5_000_000L
        val g = PropLineGameState { now }
        g.apply(obj(state()), now)
        assertNull("another league", g.scoreFor("NHL", "Boston Celtics", "New York Knicks", now))
        assertNull("an unknown league", g.scoreFor("Curling", "Boston Celtics", "New York Knicks", now))
        g.apply(obj(state(status = "final")), now)
        assertNull("final is not live", g.scoreFor("NBA", "Boston Celtics", "New York Knicks", now))
        g.apply(obj(state(status = "upcoming")), now)
        assertNull("not started", g.scoreFor("NBA", "Boston Celtics", "New York Knicks", now))
        g.apply(obj(state()), now)
        assertNotNull(g.scoreFor("NBA", "Boston Celtics", "New York Knicks", now))
        g.onClosed()
        assertNull("a closed socket vouches for nothing", g.scoreFor("NBA", "Boston Celtics", "New York Knicks", now))
        g.heard(now)
        assertNotNull(g.scoreFor("NBA", "Boston Celtics", "New York Knicks", now))
        assertNull("a live row nobody changed for ten minutes is not believed", g.scoreFor("NBA", "Boston Celtics", "New York Knicks", now + PropLineGameState.STALE_MS + 1))
    }

    @Test
    fun teamsThatMatchInNeitherOrBothOrdersGiveNothing() {
        val now = 9_000_000L
        val g = PropLineGameState { now }
        g.apply(obj(state()), now)
        assertNull(g.scoreFor("NBA", "Los Angeles Lakers", "Golden State Warriors", now))
        assertNull("one team right, the other wrong", g.scoreFor("NBA", "Boston Celtics", "Golden State Warriors", now))
    }

    @Test
    fun mlbHasNoClockAndNflKeepsItsLeagueKey() {
        val now = 7_000_000L
        val g = PropLineGameState { now }
        g.apply(obj("""{"event_id":"1","sport_key":"baseball_mlb","home_team":"Houston Astros","away_team":"Texas Rangers","status":"in_progress","home_score":2,"away_score":1,"period":"Top 7th","period_number":7,"clock":null}"""), now)
        val s = g.scoreFor("MLB", "Houston Astros", "Texas Rangers", now)!!
        assertEquals(7, s.game.period); assertEquals(0.0, s.game.clockSec, 0.0)
        g.apply(obj("""{"event_id":"2","sport_key":"football_nfl","home_team":"Dallas Cowboys","away_team":"New York Giants","status":"in_progress","home_score":10,"away_score":7,"period":"Q2","period_number":2,"clock":"3:15"}"""), now)
        assertNotNull("PropLine's `football_nfl` is the app's NFL", g.scoreFor("NFL", "Dallas Cowboys", "New York Giants", now))
        assertEquals(2, g.live())
    }

    @Test
    fun clocksParse() {
        assertEquals(362.0, PropLineGameState.seconds("6:02")!!, 1e-9)
        assertEquals(48.3, PropLineGameState.seconds("0:48.3")!!, 1e-9)
        assertNull(PropLineGameState.seconds("Q3"))
    }

    @Test
    fun aGameStateFrameOnTheSignalsSocketReachesTheLiveLaneAndAClosedSocketTakesItBack() {
        val now = 20_000_000L
        val st = PropLineStream(OkHttpClient(), json, { "k" }, PropLineLive { now }, CoroutineScope(Dispatchers.Unconfined), clock = { now })
        st.handleFrame(PropLineStream.Sub.SIGNALS, obj("""{"type":"event","seq":1,"event_type":"game_state","data":${state()}}"""))
        assertEquals(1, st.gameState.live())
        assertNotNull(st.gameState.scoreFor("NBA", "Boston Celtics", "New York Knicks", now))
        assertEquals(1L, st.counts["game_state"])
        // The heartbeat's `caught_up_at` says how far behind PropLine's sending is.
        st.handleFrame(PropLineStream.Sub.SIGNALS, obj("""{"type":"heartbeat","at":"2026-11-17T23:00:07Z","caught_up_at":"2026-11-17T23:00:05Z","books":[]}"""))
        assertTrue(st.gameState.heardAtMs > 0)
    }

    /** Six real `game_state` deliveries from PropLine's own recorded game (Magic at Celtics, 2026-11-16, propline-live-recorder's sample): event_id is a NUMBER, the status stays `in_progress` through the last play. */
    private fun realFrames(): List<JsonObject> =
        javaClass.getResourceAsStream("/propline-game-state-nba-2026-11-16.jsonl")!!.bufferedReader().readLines().filter { it.isNotBlank() }.map { obj(it) }

    @Test
    fun realRecordedFramesAreReadAndAGameEndsAtItsLastPlay() {
        val now = 30_000_000L
        val g = PropLineGameState { now }
        val frames = realFrames()
        assertEquals(6, frames.size)
        assertTrue("the real frame is accepted (event_id is numeric)", g.apply(frames[0]["data"] as JsonObject, now))
        val first = g.scoreFor("NBA", "Boston Celtics", "Orlando Magic", now)!!
        assertEquals(2, first.game.homeScore); assertEquals(0, first.game.awayScore); assertEquals(1, first.game.period); assertEquals(665.0, first.game.clockSec, 1e-9)
        g.apply(frames[2]["data"] as JsonObject, now)
        val mid = g.scoreFor("NBA", "Orlando Magic", "Boston Celtics", now)!!          // Novig lists them the other way round
        assertEquals(55, mid.game.homeScore); assertEquals(52, mid.game.awayScore); assertEquals(3, mid.game.period); assertEquals(504.0, mid.game.clockSec, 1e-9)
        g.apply(frames[3]["data"] as JsonObject, now)
        assertEquals(13.7, g.scoreFor("NBA", "Boston Celtics", "Orlando Magic", now)!!.game.clockSec, 1e-9)
        g.apply(frames[4]["data"] as JsonObject, now)       // "Period End" in the 4th quarter: the game can still go to overtime
        assertNotNull(g.scoreFor("NBA", "Boston Celtics", "Orlando Magic", now))
        g.apply(frames[5]["data"] as JsonObject, now)       // "Game End": status still says in_progress
        assertNull("a finished game is no live score", g.scoreFor("NBA", "Boston Celtics", "Orlando Magic", now))
        assertEquals(0, g.live())
    }

    @Test
    fun realFramesThroughTheStreamReachTheLiveLane() {
        val now = 40_000_000L
        val st = PropLineStream(OkHttpClient(), json, { "k" }, PropLineLive { now }, CoroutineScope(Dispatchers.Unconfined), clock = { now })
        realFrames().forEachIndexed { i, row -> st.handleFrame(PropLineStream.Sub.SIGNALS, obj("""{"type":"event","seq":${i + 1},"event_type":"game_state","data":${row["data"]}}""")) }
        assertEquals(6L, st.counts["game_state"])
        assertNull("the last real frame is Game End", st.gameState.scoreFor("NBA", "Boston Celtics", "Orlando Magic", now))
        assertEquals(1, st.gameState.size())
    }

    // ---- the subscriptions PropLine holds ----

    private class Fake(val webhooks: String, val patchOk: Boolean = true) {
        val calls = java.util.Collections.synchronizedList(ArrayList<String>())
        val bodies = java.util.Collections.synchronizedList(ArrayList<String>())
        val http: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val r = chain.request()
            val path = r.url.encodedPath
            val call = "${r.method} $path"
            val answer: Pair<Int, String> = when {
                r.url.host.startsWith("ws.") -> 503 to ""
                call == "GET /v1/webhooks" -> 200 to webhooks
                r.method == "PATCH" -> { calls += call; r.body?.let { b -> val buf = okio.Buffer(); b.writeTo(buf); bodies += buf.readUtf8() }; (if (patchOk) 200 else 422) to """{"id":940}""" }
                r.method == "POST" -> { calls += call; r.body?.let { b -> val buf = okio.Buffer(); b.writeTo(buf); bodies += buf.readUtf8() }; 200 to """{"id":941}""" }
                r.method == "DELETE" -> { calls += call; 200 to "{}" }
                else -> 404 to ""
            }
            Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(answer.first).message("x").body(answer.second.toResponseBody(null)).build()
        }).build()
    }

    private val held = """[
        {"id":939,"transport":"websocket","active":true,"events":["line_movement","line_added","line_pulled","market_suspended"],"filter_sport_key":"baseball_mlb,basketball_nba","filter_bookmaker_key":"betmgm,draftkings"},
        {"id":940,"transport":"websocket","active":true,"events":["steam","resolution","ev"],"filter_sport_key":"baseball_mlb,basketball_nba","filter_bookmaker_key":"novig","steam_props":true,"min_steam_score":40,"min_ev_pct":1,"max_ev_pct":15}
    ]"""

    private fun runStream(f: Fake, until: (Fake) -> Boolean): Fake = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val st = PropLineStream(f.http, json, { "k" }, PropLineLive(), scope, base = "https://api.prop-line.com/v1")
        st.want(true, setOf("baseball_mlb", "basketball_nba"), setOf("betmgm", "draftkings"))
        try { withTimeout(8_000) { while (!until(f)) delay(25) } } finally { st.close("test") ; scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
        f
    }

    @Test
    fun theEnginesEarlierSignalsSubscriptionIsBroughtUpToDateInPlaceAndNothingIsMadeOrRemoved() {
        val f = runStream(Fake(held)) { f -> f.calls.any { it == "PATCH /v1/webhooks/940" } }
        Thread.sleep(300)
        assertTrue(f.calls.any { it == "PATCH /v1/webhooks/940" })
        assertFalse("no new subscription beside it", f.calls.any { it.startsWith("POST") })
        assertFalse("nothing removed", f.calls.any { it.startsWith("DELETE") })
        val body = obj(f.bodies.first { "game_state" in it })
        assertTrue(body["events"].toString().contains("game_state"))
        assertEquals("true", body["active"].toString())
    }

    @Test
    fun ifPropLineWillNotUpdateTheEventsTheNewSubscriptionIsMadeAndTheEarlierOneOfOursGoes() {
        val f = runStream(Fake(held, patchOk = false)) { f -> f.calls.any { it == "DELETE /v1/webhooks/940" } }
        assertTrue(f.calls.any { it == "POST /v1/webhooks" })
        assertTrue(f.calls.any { it == "DELETE /v1/webhooks/940" })
        assertFalse("the board subscription (#939) is never deleted", f.calls.any { it == "DELETE /v1/webhooks/939" })
    }

    @Test
    fun aSubscriptionOfAnotherShapeIsNeverTouched() {
        val other = """[{"id":77,"transport":"http","active":true,"url":"https://example.com/h","events":["steam","resolution","ev"]},
            {"id":78,"transport":"websocket","active":true,"events":["steam"]}]"""
        val f = runStream(Fake(other)) { f -> f.calls.count { it.startsWith("POST") } >= 2 }
        assertTrue(f.calls.none { it.contains("/77") || it.contains("/78") })
        assertEquals(2, f.calls.count { it == "POST /v1/webhooks" })
    }
}
