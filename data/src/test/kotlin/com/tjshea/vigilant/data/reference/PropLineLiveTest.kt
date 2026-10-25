package com.tjshea.vigilant.data.reference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * PropLine Streaming Lite (PSL, Tj 2026-10-25): the stream's moves, pulls, scratches and heartbeats laid over a board read by REST, the frames as the docs give them
 * (research/PROPLINE_STREAMING_2026-10-25.md), and `/v1/freshness` parsed from the shape of a real answer (2026-10-25, research/propline/docs_2026-10-25/).
 */
class PropLineLiveTest {
    private val json = Json { ignoreUnknownKeys = true }
    private var now = 1_790_500_000_000L
    private val start = now + 2 * 3_600_000L
    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()
    private fun obj(s: String): JsonObject = json.parseToJsonElement(s).jsonObject

    private val readAt = now - 60_000L
    private val total = RefBookMarket(
        "draftkings", "DraftKings", LineKind.TOTAL,
        listOf(RefQuote(Side.OVER, 1.91, 47.5, feedOutcomeId = "11"), RefQuote(Side.UNDER, 1.91, 47.5, feedOutcomeId = "12")), readAt - 30_000L,
    )
    private val prop = RefBookMarket(
        "fanduel", "FanDuel", LineKind.PLAYER_PROP,
        listOf(RefQuote(Side.OVER, 1.87, 249.5, feedOutcomeId = "21"), RefQuote(Side.UNDER, 1.95, 249.5, feedOutcomeId = "22")), readAt - 30_000L, subject = "Josh Allen", stat = "PASSING_YARDS",
    )
    private val game = RefEvent("pl:555", "americanfootball_nfl", start, "Buffalo Bills", "Miami Dolphins", listOf(total, prop))

    private fun move(id: String, price: Int, point: Double?, type: String = "line_movement", player: String? = null) = obj(
        """{"sport_key":"football_nfl","event":{"id":555},"bookmaker_key":"draftkings","outcome_id":$id,"player_name":${player?.let { "\"$it\"" } ?: "null"},
           "previous":{"price_american":-110,"point":47.5},"current":${if (type == "line_pulled") "null" else """{"price_american":$price,"point":$point}"""}}""",
    )

    private fun live() = PropLineLive { now }.also { it.onReady(continuous = false, now = readAt - 1_000L); it.heard(now) }

    @Test fun aMoveOnBothSidesReplacesThePricesAndPointAndStampsTheMarket() {
        val lv = live()
        now += 1_000L
        lv.onLine("line_movement", move("11", -120, 48.5), now)
        lv.onLine("line_movement", move("12", 100, 48.5), now)
        val m = lv.overlay(listOf(game), readAt, now).single().markets.first { it.kind == LineKind.TOTAL }
        assertEquals(48.5, m.quotes[0].point!!, 1e-9)
        assertEquals(1.0 + 100.0 / 120.0, m.quotes[0].decimalOdds, 1e-9)
        assertEquals(2.0, m.quotes[1].decimalOdds, 1e-9)
        assertEquals(now, m.lastUpdateMs)
    }

    @Test fun oneSideMovedToANewNumberLeavesTheMarketOutUntilTheOtherSideArrives() {
        val lv = live()
        lv.onLine("line_movement", move("11", -120, 48.5), now)
        assertTrue(lv.overlay(listOf(game), readAt, now).single().markets.none { it.kind == LineKind.TOTAL })
        lv.onLine("line_movement", move("12", 100, 48.5), now)
        assertTrue(lv.overlay(listOf(game), readAt, now).single().markets.any { it.kind == LineKind.TOTAL })
    }

    @Test fun aPulledLineTakesItsMarketOut() {
        val lv = live()
        lv.onLine("line_pulled", move("12", 0, null, "line_pulled"), now)
        assertTrue(lv.overlay(listOf(game), readAt, now).single().markets.none { it.kind == LineKind.TOTAL })
    }

    /** PX (2026-10-25): a pull from before the board read that still shows the rung is already in that board: the rung stays (it was back); a pull after the read takes it out. */
    @Test fun aPullOlderThanTheReadLeavesTheRungThatCameBack() {
        val lv = live()
        lv.onLine("line_pulled", move("12", 0, null, "line_pulled"), readAt - 5 * 60_000L)
        assertTrue(lv.overlay(listOf(game), readAt, now).single().markets.any { it.kind == LineKind.TOTAL })
        lv.onLine("line_pulled", move("12", 0, null, "line_pulled"), readAt + 1_000L)
        assertTrue(lv.overlay(listOf(game), readAt, now).single().markets.none { it.kind == LineKind.TOTAL })
    }

    @Test fun theHeartbeatConfirmsAnUnmovedMarketOnlyWhileTheSocketCoversTheRead() {
        val lv = live()
        val readOfBook = now - 20_000L
        lv.onHeartbeat(json.parseToJsonElement("""[{"bookmaker_key":"draftkings","sport_key":"football_nfl","pregame_read_at":"${iso(readOfBook)}","read_age_seconds":20,"reread_seconds":75,"events":12}]""") as kotlinx.serialization.json.JsonArray, now)
        val m = lv.overlay(listOf(game), readAt, now).single().markets.first { it.kind == LineKind.TOTAL }
        assertEquals(readOfBook, m.lastUpdateMs)
        assertEquals(1.91, m.quotes[0].decimalOdds, 1e-9)
        // A board read BEFORE the socket's coverage began is not confirmed (an event before it may be missing).
        val m2 = lv.overlay(listOf(game), readAt - 10 * 60_000L, now).single().markets.first { it.kind == LineKind.TOTAL }
        assertEquals(total.lastUpdateMs, m2.lastUpdateMs)
        // A book PropLine no longer reads (its read far older than its reread gap) confirms nothing.
        lv.onHeartbeat(json.parseToJsonElement("""[{"bookmaker_key":"draftkings","sport_key":"football_nfl","pregame_read_at":"${iso(now - 600_000L)}","read_age_seconds":600,"reread_seconds":75}]""") as kotlinx.serialization.json.JsonArray, now)
        assertEquals(total.lastUpdateMs, lv.overlay(listOf(game), readAt, now).single().markets.first { it.kind == LineKind.TOTAL }.lastUpdateMs)
    }

    @Test fun aSilentSocketCoversNothing() {
        val lv = live()
        assertTrue(lv.covers(readAt, now))
        now += PropLineLive.SILENT_MS + 1
        assertFalse(lv.live(now))
        assertFalse(lv.covers(readAt, now))
    }

    @Test fun aScratchTakesThatBooksPlayerMarketsOutAndThreeBooksMakeAPlayerUnavailable() {
        val lv = live()
        val sus = obj("""{"sport_key":"football_nfl","event":{"id":555},"bookmaker_key":"fanduel","subject":"Josh Allen","reason":"off_the_board","markets":[{"key":"player_pass_yds"}],"books_agreeing":4}""")
        lv.onSuspended(sus, now)
        assertTrue(lv.overlay(listOf(game), readAt, now).single().markets.none { it.kind == LineKind.PLAYER_PROP })
        assertNotNull(lv.scratched("americanfootball_nfl", "Josh Allen", now))
        assertNull(lv.scratched("americanfootball_nfl", "Stefon Diggs", now))
        // A returning price (a move on that player at that book) brings the markets back.
        val back = obj("""{"sport_key":"football_nfl","event":{"id":555},"bookmaker_key":"fanduel","outcome_id":21,"player_name":"Josh Allen","current":{"price_american":-115,"point":249.5}}""")
        lv.onLine("line_movement", back, now)
        assertTrue(lv.overlay(listOf(game), readAt, now).single().markets.any { it.kind == LineKind.PLAYER_PROP })
    }

    @Test fun oneBookPullingIsNotAScratch() {
        val lv = live()
        lv.onSuspended(obj("""{"sport_key":"football_nfl","event":{"id":555},"bookmaker_key":"fanduel","subject":"Josh Allen","books_agreeing":1}"""), now)
        assertNull(lv.scratched("americanfootball_nfl", "Josh Allen", now))
    }

    @Test fun aBookPropLineStoppedWritingIsLeftOutOfThatClassOnly() {
        val lv = live()
        lv.staleBooks = mapOf("fanduel" to (false to true))
        val ms = lv.overlay(listOf(game), readAt, now).single().markets
        assertTrue(ms.none { it.bookKey == "fanduel" && it.kind == LineKind.PLAYER_PROP })
        assertTrue(ms.any { it.kind == LineKind.TOTAL })
    }

    @Test fun withNothingHeardTheBoardIsAsRead() {
        val lv = PropLineLive { now }
        assertEquals(listOf(game), lv.overlay(listOf(game), readAt, now))
    }

    @Test fun freshnessParsesTheClassesAndOnlyAnOutageIsFiltered() {
        val text = """{"as_of":"2026-10-25T06:28:36Z","stale_threshold_seconds":1800,"bookmakers":[
          {"key":"pinnacle","market_count":194954,"latest_update":"2026-10-25T06:28:36Z","staleness_seconds":0,"is_stale":false,"worst_staleness_seconds":1160039,"suspended_market_count":199,
           "market_classes":{"game_lines":{"market_count":147490,"staleness_seconds":0,"is_stale":false,"worst_staleness_seconds":1160039},"props":{"market_count":47464,"staleness_seconds":0,"is_stale":false}}},
          {"key":"bookmaker_eu","market_count":900,"staleness_seconds":4000,"is_stale":true,
           "market_classes":{"game_lines":{"staleness_seconds":4000,"is_stale":true},"props":null}},
          {"key":"betus","market_count":10,"staleness_seconds":2000,"is_stale":true}]}"""
        val rows = PropLineFreshness.parse(text, json)!!
        assertEquals(listOf("pinnacle", "bookmaker", "betus"), rows.map { it.book })
        assertEquals(0, rows[0].propsAgeS)
        assertEquals(1160039, rows[0].worstAgeS)
        val f = PropLineFreshness(OkHttpClient(), json)
        assertEquals(mapOf("bookmaker" to (true to false), "betus" to (true to false)), f.outages(rows))
    }

    @Test fun theDashboardListsStaleBooksFirst() {
        val st = com.tjshea.vigilant.data.cloud.CloudStatus(propLineFresh = listOf(
            PropLineFreshness.Row("pinnacle", 0, 3), PropLineFreshness.Row("bookmaker", 4000, null, linesStale = true), PropLineFreshness.Row("fanduel", 20, 600),
        ))
        val lines = com.tjshea.vigilant.data.cloud.DashboardModel.propLineFresh(st, now)
        assertEquals(com.tjshea.vigilant.data.cloud.DashboardModel.Tone.BAD, lines[0].tone)
        assertTrue(lines[0].text.contains("STALE"))
        assertEquals(com.tjshea.vigilant.data.cloud.DashboardModel.Tone.WARN, lines[1].tone)
        assertEquals("game lines 0s · props 3s", lines[2].text)
        // The status file carries the rows (the phone reads them back).
        val back = com.tjshea.vigilant.data.cloud.CloudStatus.parse(com.tjshea.vigilant.data.cloud.CloudStatus.text(st))!!
        assertEquals(st.propLineFresh, back.propLineFresh)
    }

    @Test fun streamFramesFollowTheDocs() {
        val lv = PropLineLive { now }
        val st = PropLineStream(OkHttpClient(), json, { "k" }, lv, CoroutineScope(Dispatchers.Unconfined), clock = { now })
        val heard = ArrayList<String>()
        st.tap = { type, sport, _, _ -> heard += "$type/$sport" }
        st.handleFrame(PropLineStream.Sub.BOARD, obj("""{"type":"ready","webhook_id":12,"latest_seq":4213,"oldest_available_seq":3902,"truncated":false}"""))
        assertTrue(lv.live(now))
        st.handleFrame(PropLineStream.Sub.BOARD, obj("""{"type":"event","seq":4214,"event_type":"line_movement","created_at":"${iso(now - 900)}","sent_at":"${iso(now - 300)}","data":${move("11", -125, 47.5)}}"""))
        // The same seq again (a resume re-sends) is dropped.
        st.handleFrame(PropLineStream.Sub.BOARD, obj("""{"type":"event","seq":4214,"event_type":"line_movement","data":${move("11", 300, 47.5)}}"""))
        assertEquals(1L, st.counts["line_movement"])
        val q = lv.overlay(listOf(game), readAt, now).single().markets.first { it.kind == LineKind.TOTAL }.quotes[0]
        assertEquals(1.0 + 100.0 / 125.0, q.decimalOdds, 1e-9)
        st.handleFrame(PropLineStream.Sub.SIGNALS, obj("""{"type":"event","seq":9,"event_type":"steam","data":{"sport_key":"football_nfl","steam_score":61}}"""))
        assertEquals(listOf("steam/football_nfl"), heard)
        // A lost buffer (truncated) starts coverage again: a board read before is no longer kept current.
        now += 5_000L
        st.handleFrame(PropLineStream.Sub.BOARD, obj("""{"type":"ready","webhook_id":12,"latest_seq":5000,"truncated":true}"""))
        assertFalse(lv.covers(readAt, now))
        assertTrue(lv.covers(now, now))
    }

    @Test fun boardOutcomeIdsAreReadFromIncludeBookIds() {
        val raw = """{"id":"555","home_team":"Buffalo Bills","away_team":"Miami Dolphins","commence_time":"${iso(start)}","bookmakers":[
          {"key":"bookmaker_eu","title":"BookMaker.eu","last_update":"${iso(readAt)}","markets":[{"key":"totals","last_update":"${iso(readAt)}","outcomes":[
            {"name":"Over","price":-110,"point":47.5,"last_seen_at":"${iso(readAt)}","outcome_id":13261133999},
            {"name":"Under","price":-110,"point":47.5,"last_seen_at":"${iso(readAt)}","outcome_id":13261134000}]}]}]}"""
        val e = PropLineClient.parseEvent(raw, json, "americanfootball_nfl")!!
        val m = e.markets.single()
        assertEquals("bookmaker", m.bookKey)
        assertEquals(listOf("13261133999", "13261134000"), m.quotes.map { it.feedOutcomeId })
    }
}
