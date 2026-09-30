package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.keys.CreditsHeldBackException
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.OddsFeed
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Pinnacle's closes from ParlayAPI (Tj, 2026-09-30, RESEARCH.md §43, §45): its daily closes file (player props and Pinnacle's game lines,
 * `rows` of `player_name`/`market_key`/`line`/`over_price`/`under_price`/`snapshot_time`) and its game-line `closing-lines` (flat
 * `home_odds`/`away_odds` rows), in the shapes Tj's key got back on 2026-09-30, and how the back-fill asks it first, only with a key.
 */
class ParlayClosesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val NFL = "americanfootball_nfl"

    private fun meter() = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))

    private fun pool(vararg keys: String, meter: UsageMeter = meter()) = KeyPool(QuotaPolicy.PARLAY, { keys.toList() }, meter)
    private val start = Instant.parse("2026-09-27T17:00:00Z").toEpochMilli()

    private fun bet(id: String, market: String, selection: String, starts: Long = start, league: String = "NFL") = TrackedBet(
        id, starts - 86_400_000L, league, "Los Angeles Chargers @ Buffalo Bills", starts, market, selection, "m", "", 0.5, 0.5, 0.52, 0.04, 10.0,
    )

    private fun pick(market: String, selection: String) = BetGrader.pickOf(market, selection)!!

    private fun p(american: Int) = if (american < 0) -american / (-american + 100.0) else 100.0 / (american + 100.0)

    private val propFile = """
        {"as_of":"2026-09-28T06:00:00Z","date":"2026-09-27","row_count":4,"rows":[
          {"game_date":"2026-09-27","sport_key":"americanfootball_nfl","commence_time":"2026-09-27T17:00:00Z","home_team":"Buffalo Bills",
           "away_team":"Los Angeles Chargers","source":"pinnacle","player_name":"Dalton Kincaid","market_key":"player_receptions",
           "market_label":"Player Receptions","line":3.5,"over_price":-125,"under_price":105,"snapshot_time":"2026-09-27T16:58:00Z"},
          {"game_date":"2026-09-27","sport_key":"americanfootball_nfl","commence_time":"2026-09-27T17:00:00Z","home_team":"Buffalo Bills",
           "away_team":"Los Angeles Chargers","source":"pinnacle","player_name":"Josh Allen","market_key":"player_pass_yds",
           "line":245.5,"over_price":null,"under_price":null,"over_implied_prob":0.52,"under_implied_prob":0.5,"snapshot_time":"2026-09-27T16:58:00Z"},
          {"game_date":"2026-09-27","sport_key":"americanfootball_nfl","commence_time":"2026-09-27T17:00:00Z","home_team":"Buffalo Bills",
           "away_team":"Los Angeles Chargers","source":"draftkings","player_name":"Khalil Shakir","market_key":"player_receptions",
           "line":4.5,"over_price":-110,"under_price":-110,"snapshot_time":"2026-09-27T16:58:00Z"},
          {"game_date":"2026-09-20","sport_key":"americanfootball_nfl","commence_time":"2026-09-20T17:00:00Z","home_team":"Buffalo Bills",
           "away_team":"Miami Dolphins","source":"pinnacle","player_name":"Dalton Kincaid","market_key":"player_receptions",
           "line":4.5,"over_price":-110,"under_price":-110,"snapshot_time":"2026-09-20T16:58:00Z"}
        ]}
    """.trimIndent()

    private val gameFile = """
        [{"id":"e1","sport_key":"americanfootball_nfl","commence_time":"2026-09-27T17:00:00Z","home_team":"Buffalo Bills",
          "away_team":"Los Angeles Chargers","bookmakers":[
            {"key":"draftkings","markets":[{"key":"h2h","outcomes":[{"name":"Buffalo Bills","price":-345},{"name":"Los Angeles Chargers","price":275}]}]},
            {"key":"pinnacle","last_update":"2026-09-27T16:59:00Z","markets":[
              {"key":"h2h","outcomes":[{"name":"Buffalo Bills","price":-320},{"name":"Los Angeles Chargers","price":280}]},
              {"key":"spreads","outcomes":[{"name":"Buffalo Bills","price":-108,"point":-7.0},{"name":"Los Angeles Chargers","price":-102,"point":7.0}]},
              {"key":"totals","outcomes":[{"name":"Over","price":-105,"point":50.5},{"name":"Under","price":-105,"point":50.5}]}]}]},
         {"id":"e2","sport_key":"americanfootball_nfl","commence_time":"2026-09-27T20:25:00Z","home_team":"Denver Broncos",
          "away_team":"New York Jets","bookmakers":[{"key":"pinnacle","markets":[
            {"key":"h2h","outcomes":[{"name":"Denver Broncos","price":-200},{"name":"New York Jets","price":170}]}]}]}]
    """.trimIndent()

    // ---- parsing ------------------------------------------------------------------------------------------------------

    @Test
    fun `a prop's Pinnacle close, devigged, at its exact line, for either side`() {
        val root = json.parseToJsonElement(propFile)
        val over = ParlayCloses.parseProp(root, pick("Player Receptions", "Dalton Kincaid Over 3.5") as BetGrader.Pick.Prop, NFL, start)
            as CloseLookup.Found
        assertEquals(p(-125) / (p(-125) + p(105)), over.fair, 1e-9)
        assertEquals("ParlayAPI · Pinnacle close", over.via)
        val under = ParlayCloses.parseProp(root, pick("Player Receptions", "Dalton Kincaid Under 3.5") as BetGrader.Pick.Prop, NFL, start)
            as CloseLookup.Found
        assertEquals(1.0, over.fair + under.fair, 1e-9)
        // Another line isn't the same bet; last week's game isn't this one.
        val moved = ParlayCloses.parseProp(root, pick("Player Receptions", "Dalton Kincaid Over 4.5") as BetGrader.Pick.Prop, NFL, start)
        assertEquals("Pinnacle closed this prop at 3.5, not your 4.5", (moved as CloseLookup.None).reason)
    }

    @Test
    fun `implied probabilities stand in for a missing price, and another book's row is never Pinnacle's`() {
        val root = json.parseToJsonElement(propFile)
        val allen = ParlayCloses.parseProp(root, pick("Player Passing Yards", "Josh Allen Over 245.5") as BetGrader.Pick.Prop, NFL, start)
            as CloseLookup.Found
        assertEquals(0.52 / 1.02, allen.fair, 1e-9)
        val shakir = ParlayCloses.parseProp(root, pick("Player Receptions", "Khalil Shakir Over 4.5") as BetGrader.Pick.Prop, NFL, start)
        assertTrue(shakir is CloseLookup.None)
    }

    @Test
    fun `a game line's Pinnacle close in The Odds API's event shape, should closing-lines ever answer so`() {
        val root = json.parseToJsonElement(gameFile)
        val ml = ParlayCloses.parseGameLine(root, bet("ml", "Moneyline", "Buffalo Bills"), pick("Moneyline", "Buffalo Bills")) as CloseLookup.Found
        assertEquals(p(-320) / (p(-320) + p(280)), ml.fair, 1e-9)
        val dog = ParlayCloses.parseGameLine(root, bet("d", "Moneyline", "Los Angeles Chargers"), pick("Moneyline", "Los Angeles Chargers")) as CloseLookup.Found
        assertEquals(1.0, ml.fair + dog.fair, 1e-9)
        val spread = ParlayCloses.parseGameLine(root, bet("s", "Spread", "Buffalo Bills -7"), pick("Spread", "Buffalo Bills -7")) as CloseLookup.Found
        assertEquals(p(-108) / (p(-108) + p(-102)), spread.fair, 1e-9)
        assertTrue(ParlayCloses.parseGameLine(root, bet("s2", "Spread", "Buffalo Bills -6.5"), pick("Spread", "Buffalo Bills -6.5")) is CloseLookup.None)
        assertEquals(0.5, (ParlayCloses.parseGameLine(root, bet("t", "Total", "Under 50.5"), pick("Total", "Under 50.5")) as CloseLookup.Found).fair, 1e-9)
        // Another game at another time is never matched to this one.
        val late = bet("x", "Moneyline", "Buffalo Bills", starts = start + 86_400_000L)
        assertTrue(ParlayCloses.parseGameLine(root, late, pick("Moneyline", "Buffalo Bills")) is CloseLookup.None)
    }

    /** Rows as ParlayAPI's closes file sent them for MLB on 2026-09-29 (Tj's key), trimmed to one game. */
    private val mlbFile = """
        {"as_of":"2026-09-30T04:10:00Z","date":"2026-09-29","row_count":9,"rows":[
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Atlanta Braves","market_key":"moneyline","market_label":"Moneyline","line":null,"over_price":-174,"under_price":null,"snapshot_time":"2026-09-29T16:54:29+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Philadelphia Phillies","market_key":"moneyline","market_label":"Moneyline","line":null,"over_price":160,"under_price":null,"snapshot_time":"2026-09-29T16:54:29+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Atlanta Braves","market_key":"spreads","market_label":"Spread","line":-1.5,"over_price":126,"under_price":null,"snapshot_time":"2026-09-29T16:54:29+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Philadelphia Phillies","market_key":"spreads","market_label":"Spread","line":1.5,"over_price":-145,"under_price":null,"snapshot_time":"2026-09-29T16:54:29+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Total","market_key":"totals","market_label":"Total","line":6.5,"over_price":-121,"under_price":108,"snapshot_time":"2026-09-29T16:54:29+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Total","market_key":"alternate_totals","market_label":"Alternate Total Runs","line":7.5,"over_price":110,"under_price":-128,"snapshot_time":"2026-09-29T02:34:09+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Matt Olson","market_key":"player_bases","market_label":"Total Bases","line":1.5,"over_price":-105,"under_price":-115,"snapshot_time":"2026-09-29T17:40:00+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Kyle Schwarber","market_key":"player_home_runs","market_label":"Home Runs","line":0.5,"over_price":210,"under_price":-280,"snapshot_time":"2026-09-29T03:05:00+00:00"},
          {"game_date":"2026-09-29","sport_key":"baseball_mlb","commence_time":"2026-09-29T18:10:00Z","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","source":"pinnacle","player_name":"Chris Sale","market_key":"player_strikeouts","market_label":"Strikeouts","line":6.5,"over_price":-130,"under_price":110,"snapshot_time":"2026-09-29T17:55:00+00:00"}
        ]}
    """.trimIndent()

    private val mlbStart = Instant.parse("2026-09-29T18:10:00Z").toEpochMilli()

    private fun mlbBet(id: String, market: String, selection: String) = TrackedBet(
        id, mlbStart - 86_400_000L, "MLB", "Philadelphia Phillies @ Atlanta Braves", mlbStart, market, selection, "m", "", 0.5, 0.5, 0.52, 0.04, 10.0,
    )

    @Test
    fun `the closes file's game lines, one team a row, at the closing number, and a price from hours before isn't a close`() {
        val root = json.parseToJsonElement(mlbFile)
        val ml = ParlayCloses.parseFileGameLine(root, mlbBet("ml", "Moneyline", "Atlanta Braves"), pick("Moneyline", "Atlanta Braves")) as CloseLookup.Found
        assertEquals(p(-174) / (p(-174) + p(160)), ml.fair, 1e-9)
        val dog = ParlayCloses.parseFileGameLine(root, mlbBet("d", "Moneyline", "Philadelphia Phillies"), pick("Moneyline", "Philadelphia Phillies")) as CloseLookup.Found
        assertEquals(1.0, ml.fair + dog.fair, 1e-9)
        val rl = ParlayCloses.parseFileGameLine(root, mlbBet("s", "Spread", "Atlanta Braves -1.5"), pick("Spread", "Atlanta Braves -1.5")) as CloseLookup.Found
        assertEquals(p(126) / (p(126) + p(-145)), rl.fair, 1e-9)
        assertTrue(ParlayCloses.parseFileGameLine(root, mlbBet("s2", "Spread", "Atlanta Braves -2.5"), pick("Spread", "Atlanta Braves -2.5")) is CloseLookup.None)
        val under = ParlayCloses.parseFileGameLine(root, mlbBet("t", "Total", "Under 6.5"), pick("Total", "Under 6.5")) as CloseLookup.Found
        assertEquals(p(108) / (p(-121) + p(108)), under.fair, 1e-9)
        // The 7.5 was last priced 15 hours before first pitch: not a close.
        val early = ParlayCloses.parseFileGameLine(root, mlbBet("t2", "Total", "Over 7.5"), pick("Total", "Over 7.5")) as CloseLookup.None
        assertTrue(early.reason, early.reason.contains("15h before the start"))
    }

    @Test
    fun `props in the closes file under each book's own market name, and one priced the night before isn't a close`() {
        val root = json.parseToJsonElement(mlbFile)
        val olson = ParlayCloses.parseProp(root, pick("Total Bases", "Matt Olson Over 1.5") as BetGrader.Pick.Prop, "baseball_mlb", mlbStart) as CloseLookup.Found
        assertEquals(p(-105) / (p(-105) + p(-115)), olson.fair, 1e-9)
        val sale = ParlayCloses.parseProp(root, pick("Pitcher Strikeouts", "Chris Sale Under 6.5") as BetGrader.Pick.Prop, "baseball_mlb", mlbStart) as CloseLookup.Found
        assertEquals(p(110) / (p(-130) + p(110)), sale.fair, 1e-9)
        val hr = ParlayCloses.parseProp(root, pick("Home Runs", "Kyle Schwarber Over 0.5") as BetGrader.Pick.Prop, "baseball_mlb", mlbStart)
        assertTrue(hr.toString(), hr is CloseLookup.None && hr.reason.contains("h before the start"))
    }

    /** A row as ParlayAPI's `/sports/{sport}/closing-lines` sent it on 2026-09-30 (Tj's key): Pinnacle's moneyline at the start. */
    private val flatLines = """
        [{"sport_key":"baseball_mlb","game_date":"2026-09-29","home_team":"Atlanta Braves","away_team":"Philadelphia Phillies","bookmaker":"pinnacle",
          "bookmaker_title":"Pinnacle","home_odds":-170,"away_odds":155,"draw_odds":null,"market_key":"h2h","commence_time":"2026-09-29T18:10:00Z",
          "last_update":"2026-09-29T18:10:00Z","archive_source":"pinnacle","event_id":"2026-09-29_Atlanta_Braves_Philadelphia_Phillies"},
         {"sport_key":"soccer_epl","game_date":"2026-09-29","home_team":"Arsenal","away_team":"Chelsea","bookmaker":"pinnacle","home_odds":120,
          "away_odds":230,"draw_odds":250,"market_key":"h2h","commence_time":"2026-09-29T19:00:00Z","last_update":"2026-09-29T19:00:00Z"}]
    """.trimIndent()

    @Test
    fun `closing-lines' flat rows give the moneyline at the start, never a three-way one`() {
        val root = json.parseToJsonElement(flatLines)
        val home = ParlayCloses.parseGameLine(root, mlbBet("ml", "Moneyline", "Atlanta Braves"), pick("Moneyline", "Atlanta Braves")) as CloseLookup.Found
        assertEquals(p(-170) / (p(-170) + p(155)), home.fair, 1e-9)
        val away = ParlayCloses.parseGameLine(root, mlbBet("a", "Moneyline", "Philadelphia Phillies"), pick("Moneyline", "Philadelphia Phillies")) as CloseLookup.Found
        assertEquals(1.0, home.fair + away.fair, 1e-9)
        assertTrue(ParlayCloses.parseGameLine(root, mlbBet("s", "Spread", "Atlanta Braves -1.5"), pick("Spread", "Atlanta Braves -1.5")) is CloseLookup.None)
        val soccer = TrackedBet(
            "x", mlbStart, "EPL", "Chelsea @ Arsenal", Instant.parse("2026-09-29T19:00:00Z").toEpochMilli(), "Moneyline", "Arsenal", "m", "", 0.5, 0.5, 0.52, 0.04, 10.0,
        )
        assertTrue(ParlayCloses.parseGameLine(root, soccer, pick("Moneyline", "Arsenal")) is CloseLookup.None)
    }

    @Test
    fun `the Odds API sport key of a bet's league`() {
        assertEquals("americanfootball_nfl", ParlayCloses.sportKeyOf(bet("a", "Moneyline", "Buffalo Bills")))
        assertEquals(null, ParlayCloses.sportKeyOf(bet("b", "Moneyline", "Buffalo Bills", league = "Nowhere League")))
    }

    // ---- over HTTP ----------------------------------------------------------------------------------------------------

    private fun serve(handler: (RecordedRequest) -> MockResponse): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = handler(request) }
        start()
    }

    @Test
    fun `asks Pinnacle's props file and closing lines with the key, and keeps each answer for hours`() = runBlocking {
        val seen = ArrayList<okhttp3.HttpUrl>()
        val server = serve { r ->
            seen += r.requestUrl!!
            when {
                r.path!!.startsWith("/v1/historical/closing-lines.json") -> MockResponse().setBody(propFile)
                r.path!!.startsWith("/v1/sports/americanfootball_nfl/closing-lines") -> MockResponse().setBody(gameFile)
                else -> MockResponse().setResponseCode(404)
            }
        }
        try {
            var now = start + 2 * 86_400_000L
            val closes = ParlayCloses(OkHttpClient(), pool("pk-1"), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
            val bets = listOf(bet("prop", "Player Receptions", "Dalton Kincaid Over 3.5"), bet("ml", "Moneyline", "Buffalo Bills"))
            val out = closes.closes(bets)
            assertTrue(out["prop"] is CloseLookup.Found)
            assertTrue(out["ml"] is CloseLookup.Found)
            val file = seen.first { it.encodedPath.endsWith("closing-lines.json") }
            assertEquals("2026-09-27", file.queryParameter("date"))
            assertEquals("americanfootball_nfl", file.queryParameter("sport_key"))
            assertEquals("pinnacle", file.queryParameter("source"))
            assertEquals(null, file.queryParameter("apiKey")) // ParlayAPI's best practices: the key in a header, never the URL
            val lines = seen.first { it.encodedPath.endsWith("/closing-lines") }
            assertEquals("pinnacle", lines.queryParameter("bookmakers"))
            assertEquals("3", lines.queryParameter("daysFrom"))
            val asked = closes.requests
            // Within KEEP_MS the same league-day isn't bought again.
            now += 60 * 60_000L
            closes.closes(bets)
            assertEquals(asked, closes.requests)
            now += ParlayCloses.KEEP_MS
            closes.closes(bets)
            assertTrue(closes.requests > asked)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `past the plan's window is nothing to find, out of credits is asked again later`() = runBlocking {
        var body = """{"detail":{"error":"HISTORICAL_LIMIT"}}"""
        val server = serve { MockResponse().setResponseCode(403).setBody(body) }
        try {
            val now = start + 5 * 86_400_000L
            val ml = bet("ml", "Moneyline", "Buffalo Bills")
            val limit = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
            assertTrue(limit.closes(listOf(ml))["ml"] is CloseLookup.None)
            body = """{"error":"credit_limit_exceeded"}"""
            val broke = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { now })
            assertTrue(broke.closes(listOf(ml))["ml"] is CloseLookup.Later)
            // Older than the 30 days `closing-lines` reaches: only the day's closes file is asked.
            body = """{"detail":{"error":"HISTORICAL_LIMIT"}}"""
            val before = server.requestCount
            val old = ParlayCloses(OkHttpClient(), pool("k"), json, server.url("/v1").toString().trimEnd('/'), clock = { start + 40 * 86_400_000L })
            assertTrue(old.closes(listOf(ml))["ml"] is CloseLookup.None)
            assertEquals(1, old.requests)
            repeat(before) { server.takeRequest() }
            assertTrue(server.takeRequest().path!!.startsWith("/v1/historical/closing-lines.json"))
        } finally {
            server.shutdown()
        }
    }

    // ---- in the back-fill ---------------------------------------------------------------------------------------------

    private fun tracker(vararg bets: TrackedBet): BetTracker {
        val f = File.createTempFile("bets", ".json").also { it.delete() }
        f.writeText(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets.toList()))
        return BetTracker(f)
    }

    private class Fake(val answer: CloseLookup) : CloseSource {
        var asked = 0
        override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> { asked += bets.size; return bets.associate { it.id to answer } }
    }

    @Test
    fun `with a key ParlayAPI is asked first, without one it isn't asked or counted`() = runBlocking {
        val server = serve { MockResponse().setBody(gameFile) }
        try {
            val now = start + 86_400_000L
            val url = server.url("/v1").toString().trimEnd('/')
            val espn = Fake(CloseLookup.Found(0.7, "ESPN · DraftKings close"))
            val t = tracker(bet("ml", "Moneyline", "Buffalo Bills"))
            val withKey = ParlayCloses(OkHttpClient(), pool("k"), json, url, clock = { now })
            CloseBackfill(t, listOf(withKey, espn), clock = { now }).run()
            assertEquals("ParlayAPI · Pinnacle close", t.all().single().closeVia)
            assertEquals(0, espn.asked)
            assertEquals("Pinnacle via ParlayAPI", ClosingLine.sourceLabel(t.all().single().closeVia!!))

            // No key: never asked, and ESPN + Novig saying "never" is enough to stop looking.
            val t2 = tracker(bet("prop", "Player Receptions", "Dalton Kincaid Over 3.5"))
            val keyless = ParlayCloses(OkHttpClient(), pool(), json, url, clock = { now })
            assertFalse(keyless.active)
            // Switched off in Settings, a key doesn't make it asked.
            assertFalse(ParlayCloses(OkHttpClient(), pool("k"), json, url).apply { enabled = false }.active)
            val none = Fake(CloseLookup.None("nope"))
            CloseBackfill(t2, listOf(keyless, none, Fake(CloseLookup.None("nor here"))), clock = { now }).run()
            assertEquals(0, keyless.requests)
            val b = t2.all().single()
            assertTrue(b.closeFinal)
            assertEquals("nope; nor here", b.closeNote)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `scans leave a key's last credits to the closing lines`() = runBlocking {
        val server = serve { r ->
            if (r.path!!.contains("/closing-lines")) MockResponse().setBody(gameFile).setHeader("x-requests-remaining", "285").setHeader("x-requests-used", "19715")
            else MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "290").setHeader("x-requests-used", "19710").setHeader("x-requests-last", "3")
        }
        try {
            val url = server.url("/v1").toString().trimEnd('/')
            val shared = pool("pk")
            val scans = TheOddsApiClient(OkHttpClient(), shared, json, baseUrl = url, minIntervalMs = 0, feed = OddsFeed.PARLAY)
            scans.fetch("americanfootball_nfl", listOf("pinnacle")) // the server says 290 left: under the 300 kept back
            val refused = runCatching { scans.fetch("americanfootball_nfl", listOf("pinnacle")) }.exceptionOrNull()
            assertTrue(refused is CreditsHeldBackException)
            assertEquals("The last 300 credits on your ParlayAPI key are kept for closing lines.", refused!!.message)
            assertEquals(1, server.requestCount)
            // The closing lines still get them.
            val now = start + 86_400_000L
            val closes = ParlayCloses(OkHttpClient(), shared, json, url, clock = { now })
            assertTrue(closes.closes(listOf(bet("ml", "Moneyline", "Buffalo Bills")))["ml"] is CloseLookup.Found)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    /** Tj's diagnostics, 2026-09-30: "100 started with no close found yet", 69 of them marked final by ESPN and Novig before ParlayAPI existed. */
    @Test
    fun `a bet every old source gave up on is asked again once ParlayAPI is added, and not again after`() = runBlocking {
        val now = start + 2 * 86_400_000L
        val given = bet("prop", "Player Receptions", "Dalton Kincaid Over 3.5").copy(
            closeFinal = true, closeLookedAtMs = start + 3_600_000L, closeNote = "ESPN keeps full-game moneylines, spreads and totals only; No Novig outcome on record for this bet",
        )
        val t = tracker(given)
        val pinnacle = object : CloseSource {
            var asked = 0
            override val id = ParlayCloses.ID
            override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> { asked += bets.size; return bets.associate { it.id to CloseLookup.None("Not in ParlayAPI's file") } }
        }
        val espn = Fake(CloseLookup.None("ESPN keeps full-game moneylines, spreads and totals only"))
        CloseBackfill(t, listOf(pinnacle, espn), clock = { now }).run()
        assertEquals(1, pinnacle.asked)
        val b = t.all().single()
        assertTrue(b.closeFinal)
        assertTrue(ParlayCloses.ID in b.closeAskedOf!!)
        // Asked of every source now: left alone.
        CloseBackfill(t, listOf(pinnacle, espn), clock = { now + CloseBackfill.RETRY_MS }).run()
        assertEquals(1, pinnacle.asked)
        // Found: the Pinnacle close counts.
        val found = tracker(given)
        CloseBackfill(found, listOf(Fake(CloseLookup.Found(0.58, "ParlayAPI · Pinnacle close"))), clock = { now }).run()
        assertEquals(0.58, found.all().single().closeFair!!, 0.0)
    }
}
