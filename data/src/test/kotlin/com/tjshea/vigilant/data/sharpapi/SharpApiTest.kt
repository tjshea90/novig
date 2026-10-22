package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SharpAPI (SHARPAPI.md; Tj 2026-10-21, SA): the rows as the docs show them, turned into Vigilant's lines; the store read once then kept by deltas. */
class SharpApiTest {
    private val start = java.time.Instant.parse("2026-10-25T23:00:00Z").toEpochMilli()
    private val now = start - 3 * 3_600_000L
    private val nfl = Leagues.byNovigName("NFL")!!

    private fun row(
        id: String, book: String, market: String, sel: String, type: String, dec: Double, line: Double? = null, player: String? = null,
        side: String? = null, live: Boolean = false, active: Boolean = true, segment: String? = null, at: Long = now - 5_000,
    ) = SaRow(id, book, "nfl_chiefs_raiders_2026-10-25_b3", "nfl", "Kansas City Chiefs", "Las Vegas Raiders", start, market, segment, sel, type, side, player, line, dec, at, live, active, false, null)

    private fun ctx(families: Set<MarketFamily> = MarketFamily.entries.toSet(), live: Boolean = false) = SharpApiConvert.Context(nfl, null, families, includeLive = false, nowMs = now, liveOnly = live)

    @Test
    fun `a moneyline, a spread pair, a total and a prop become Vigilant lines, each book its own`() {
        val rows = listOf(
            row("1", "pinnacle", "moneyline", "Kansas City Chiefs", "home", 1.40),
            row("2", "pinnacle", "moneyline", "Las Vegas Raiders", "away", 3.10),
            row("3", "circa", "point_spread", "Kansas City Chiefs", "home", 1.91, line = -7.5),
            row("4", "circa", "point_spread", "Las Vegas Raiders", "away", 1.91, line = 7.5),
            row("5", "pinnacle", "total_points", "Over", "over", 1.95, line = 44.5),
            row("6", "pinnacle", "total_points", "Under", "under", 1.87, line = 44.5),
            row("7", "bookmaker", "player_passing_yards", "Over", "over", 1.90, line = 255.5, player = "Patrick Mahomes"),
            row("8", "bookmaker", "player_passing_yards", "Under", "under", 1.92, line = 255.5, player = "Patrick Mahomes"),
            row("9", "pinnacle", "team_total", "Kansas City Chiefs Over", "home_over", 1.9, line = 26.5),
            row("10", "pinnacle", "team_total", "Kansas City Chiefs Under", "home_under", 1.9, line = 26.5),
        )
        val e = SharpApiConvert.events(rows, ctx()).single()
        assertEquals("Kansas City Chiefs", e.home)
        val ml = e.markets.single { it.kind == LineKind.MONEYLINE }
        assertEquals("pinnacle", ml.bookKey)
        assertEquals(1.40, ml.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
        val sp = e.markets.single { it.kind == LineKind.SPREAD }
        assertEquals("circa", sp.bookKey)
        assertEquals(-7.5, sp.line!!, 1e-9)
        assertEquals(7.5, sp.quotes.single { it.side == Side.AWAY }.point!!, 1e-9)
        assertEquals(44.5, e.markets.single { it.kind == LineKind.TOTAL }.line!!, 1e-9)
        val prop = e.markets.single { it.kind == LineKind.PLAYER_PROP }
        assertEquals("Patrick Mahomes", prop.subject)
        assertEquals("PASSING_YARDS", prop.stat)
        assertEquals("bookmaker", prop.bookKey)
        assertEquals(RefBookMarket.HOME, e.markets.single { it.kind == LineKind.TEAM_TOTAL }.subject)
        assertEquals(now - 5_000, ml.lastUpdateMs)
    }

    @Test
    fun `a book whose rows carry no start time takes the game's start from the other books' rows`() {
        val rows = listOf(
            row("1", "pinnacle", "moneyline", "Kansas City Chiefs", "home", 1.40),
            row("2", "pinnacle", "moneyline", "Las Vegas Raiders", "away", 3.10),
            row("3", "bwin", "moneyline", "Kansas City Chiefs", "home", 1.38).copy(startMs = null),
            row("4", "bwin", "moneyline", "Las Vegas Raiders", "away", 3.20).copy(startMs = null),
        )
        val e = SharpApiConvert.events(rows, ctx()).single()
        assertEquals(start, e.commenceMs)
        assertEquals(setOf("pinnacle", "bwin"), e.markets.map { it.bookKey }.toSet())
        assertTrue(SharpApiConvert.census(rows, ctx()).contains("kept for pairing 4"))
    }

    @Test
    fun `a pair that does not close, a suspended row, an excluded book and a live row price nothing in the pregame read`() {
        val rows = listOf(
            row("1", "pinnacle", "moneyline", "Kansas City Chiefs", "home", 1.40),
            row("3", "circa", "point_spread", "Kansas City Chiefs", "home", 1.91, line = -7.5),
            row("4", "circa", "point_spread", "Las Vegas Raiders", "away", 1.91, line = 6.5),
            row("5", "pinnacle", "total_points", "Over", "over", 1.95, line = 44.5, active = false),
            row("6", "pinnacle", "total_points", "Under", "under", 1.87, line = 44.5),
            row("7", "novig", "moneyline", "Kansas City Chiefs", "home", 1.45),
            row("8", "novig", "moneyline", "Las Vegas Raiders", "away", 3.0),
            row("9", "pinnacle", "moneyline", "Kansas City Chiefs", "home", 1.30, live = true),
            row("10", "pinnacle", "moneyline", "Las Vegas Raiders", "away", 3.6, live = true),
        )
        assertTrue(SharpApiConvert.events(rows, ctx()).isEmpty())
        // The live feed reads only the live rows.
        val live = SharpApiConvert.events(rows, ctx(live = true)).single().markets.single()
        assertEquals(1.30, live.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
    }

    @Test
    fun `market names, segments and books read as Vigilant's`() {
        assertEquals(SharpApiConvert.Ref(LineKind.SPREAD, 1), SharpApiConvert.refOf("NFL", "1st_half_point_spread", "1st_half"))
        assertEquals(SharpApiConvert.Ref(LineKind.TOTAL, RefBookMarket.PERIOD_FIRST_INNING), SharpApiConvert.refOf("MLB", "1st_inning_total_runs", null))
        assertEquals(SharpApiConvert.Ref(LineKind.SPREAD, 0), SharpApiConvert.refOf("MLB", "run_line", "full_game"))
        assertNull(SharpApiConvert.refOf("NFL", "1st_half_moneyline", "1st_half"))
        assertNull(SharpApiConvert.refOf("NFL", "moneyline_3-way", null))
        assertEquals(SharpApiConvert.Ref(LineKind.SPREAD, RefBookMarket.PERIOD_SETS), SharpApiConvert.refOf("ATP", "set_handicap", null))
        assertEquals("williamhill_us", SharpApiBooks.appKey("caesars"))
        assertEquals("betfair_ex_eu", SharpApiBooks.appKey("betfair"))
        assertNull(SharpApiBooks.appKey("kalshi"))
        assertNull(SharpApiBooks.appKey("novig"))
        assertEquals("nfl", SharpApiBooks.leagueSlug(nfl))
        // Only the families the scan asks for.
        val rows = listOf(row("1", "pinnacle", "moneyline", "Kansas City Chiefs", "home", 1.4), row("2", "pinnacle", "moneyline", "Las Vegas Raiders", "away", 3.1))
        assertTrue(SharpApiConvert.events(rows, ctx(setOf(MarketFamily.TOTAL))).isEmpty())
    }

    @Test
    fun `a row of the docs' own example parses`() {
        val o = Json.parseToJsonElement(
            """{"id":"draftkings_33483153_moneyline_PHO","sportsbook":"draftkings","event_id":"33483153","sport":"basketball","league":"nba","home_team":"PHI 76ers","away_team":"PHO Suns","market_type":"moneyline","selection":"PHO Suns","selection_type":"away","odds_american":-150,"odds_decimal":1.667,"odds_probability":0.60,"line":null,"is_main_line":true,"is_alternate_line":false,"event_start_time":"2026-01-26T19:00:00Z","timestamp":"2026-01-26T02:10:24.125Z","is_live":false}""",
        ).jsonObject
        val r = SaRow.parse(o)!!
        assertEquals("away", r.selectionType)
        assertEquals(1.667, r.decimal, 1e-9)
        assertNull(r.line)
        assertEquals(java.time.Instant.parse("2026-01-26T02:10:24.125Z").toEpochMilli(), r.atMs)
        assertTrue(r.active)
        assertFalse(r.live)
    }

    @Test
    fun `the store reads a league by cursor pages, then keeps it by deltas with explicit removals`() = runBlocking {
        val server = MockWebServer()
        var deltaSince: String? = null
        val keys = mutableListOf<String?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                keys += request.getHeader("X-API-Key")
                val u = request.requestUrl!!
                return when (u.encodedPath) {
                    "/api/v1/odds" -> if (u.queryParameter("cursor") == null) MockResponse().setBody(page(listOf(json("a", 1.5), json("b", 2.6)), "c1", "2026-10-25T20:00:00Z"))
                    else MockResponse().setBody(page(listOf(json("c", 1.9)), null, "2026-10-25T20:00:01Z"))
                    "/api/v1/odds/delta" -> {
                        deltaSince = u.queryParameter("since")
                        MockResponse().setBody("""{"data":[${json("a", 1.45)}],"removed":[{"id":"c","sportsbook":"pinnacle","removed_at":"2026-10-25T20:00:03Z"}],"pagination":{"limit":500,"offset":0,"count":1,"has_more":false,"next_offset":null,"next_cursor":null},"updated_at":"2026-10-25T20:00:04Z","meta":{"server_time":"2026-10-25T20:00:04Z"}}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val client = SharpApiClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, { "sk_test_FAKE" }, baseUrl = server.url("/api/v1").toString().trimEnd('/'))
            val feed = SharpApiFeed(client)
            assertTrue(feed.ensure("nfl"))
            assertEquals(setOf("a", "b", "c"), feed.rows("nfl").map { it.id }.toSet())
            assertTrue(feed.deltaStep("nfl"))
            // The watermark is the first baseline page's updated_at, then the terminal page's server_time.
            assertEquals("2026-10-25T20:00:00Z", deltaSince)
            assertEquals(setOf("a", "b"), feed.rows("nfl").map { it.id }.toSet())
            assertEquals(1.45, feed.rows("nfl").single { it.id == "a" }.decimal, 1e-9)
            assertTrue(feed.deltaStep("nfl"))
            assertEquals("2026-10-25T20:00:04Z", deltaSince)
            assertTrue(keys.all { it == "sk_test_FAKE" })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a refused key stops every call, a 429 rests them`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"invalid_api_key","message":"API key not recognized"}}"""))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("""{"error":{"code":"rate_limited","message":"slow down","retry_after":1}}"""))
        server.start()
        try {
            var key = "sk_bad_FAKE"
            val client = SharpApiClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, { key }, baseUrl = server.url("/api/v1").toString().trimEnd('/'))
            val e = runCatching { client.get("/odds") }.exceptionOrNull() as SharpApiException
            assertTrue(e.keyRefused)
            assertTrue(client.down())
            assertEquals(1, server.requestCount)
            // A new key is tried again.
            key = "sk_new_FAKE"
            assertFalse(client.down())
            val r = runCatching { client.get("/odds") }.exceptionOrNull() as SharpApiException
            assertEquals("rate_limited", r.code)
            assertTrue(client.down())
        } finally {
            server.shutdown()
        }
    }

    private fun json(id: String, dec: Double) =
        """{"id":"$id","sportsbook":"pinnacle","event_id":"nfl_chiefs_raiders_2026-10-25_b3","league":"nfl","home_team":"Kansas City Chiefs","away_team":"Las Vegas Raiders","market_type":"moneyline","selection":"x","selection_type":"home","odds_decimal":$dec,"event_start_time":"2026-10-25T23:00:00Z","timestamp":"2026-10-25T20:00:00Z","is_live":false}"""

    private fun page(rows: List<String>, next: String?, updated: String) =
        """{"data":[${rows.joinToString(",")}],"pagination":{"limit":200,"offset":0,"count":${rows.size},"has_more":${next != null},"next_cursor":${next?.let { "\"$it\"" } ?: "null"}},"updated_at":"$updated"}"""
}

/** SharpAPI's Pinnacle as the live lane's feed (SA): Pinnodds frames from the store's live Pinnacle rows; a changed line is sent again, a line taken off the board is sent closed, a stale store sends nothing. */
class SharpApiLiveFeedTest {
    private var now = java.time.Instant.parse("2026-10-25T23:30:00Z").toEpochMilli()
    private val start = now - 30 * 60_000L

    private fun live(id: String, sel: String, type: String, dec: Double, market: String = "moneyline", line: Double? = null, book: String = "pinnacle") =
        SaRow(id, book, "nfl_chiefs_raiders_2026-10-25_b3", "nfl", "Kansas City Chiefs", "Las Vegas Raiders", start, market, null, sel, type, null, null, line, dec, now - 500, true, true, false, 5000.0)

    @Test
    fun `live Pinnacle rows become frames, changes and closings follow`() {
        val feed = SharpApiFeed(SharpApiClient(OkHttpClient(), Json, { "" }), clock = { now })
        val frames = mutableListOf<String>()
        val lf = SharpApiLiveFeed(feed, { setOf("NFL") }, { f, _ -> frames += f }, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), clock = { now })
        val nfl = listOf(Leagues.byNovigName("NFL")!! to "nfl")
        feed.seed("nfl", listOf(live("1", "Chiefs", "home", 1.5), live("2", "Raiders", "away", 2.7), live("3", "Chiefs", "home", 1.4, book = "draftkings"), live("4", "Raiders", "away", 3.0, book = "draftkings")))
        lf.update(nfl, now)
        assertEquals(1, frames.size)
        val f = Json.parseToJsonElement(frames.single()).jsonObject
        val m = (f["rec"]!!.jsonObject["markets"] as kotlinx.serialization.json.JsonArray).single().jsonObject
        assertEquals("s;0;m", (m["key"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(1, lf.liveGames)
        // Unchanged: nothing new in the frame's markets.
        lf.update(nfl, now)
        assertEquals(0, (Json.parseToJsonElement(frames.last()).jsonObject["rec"]!!.jsonObject["markets"] as kotlinx.serialization.json.JsonArray).size)
        // Pinnacle took the moneyline down: it is sent closed.
        feed.seed("nfl", listOf(live("3", "Chiefs", "home", 1.4, book = "draftkings"), live("4", "Raiders", "away", 3.0, book = "draftkings")))
        lf.update(nfl, now)
        assertTrue(frames.last().contains("\"closed\""))
        // A store that has not been brought up to date for seconds prices nothing.
        feed.seed("nfl", listOf(live("1", "Chiefs", "home", 1.5), live("2", "Raiders", "away", 2.7)))
        val n = frames.size
        now += SharpApiLiveFeed.STALE_MS + 1_000L
        lf.update(nfl, now)
        assertEquals(n, frames.size)
        assertEquals(0, lf.liveGames)
    }
}

/** The extras read the docs' own example answers (SHARPAPI.md §3). */
class SharpApiExtrasTest {
    @Test
    fun `a closing snapshot gives every book's close with SharpAPI's devigged fair`() {
        val body = Json.parseToJsonElement(
            """{"success":true,"data":{"event_id":"mlb_athletics_mariners_2026-04-22","sport":"baseball","league":"mlb","home_team":"Seattle Mariners","away_team":"Athletics","event_start_time":"2026-04-22T22:10:00Z","captured_at":"2026-04-22T22:09:58.412Z","books":{"pinnacle":{"captured_at":"2026-04-22T22:09:50.120Z","seconds_before_kickoff":8,"capture_trigger":"kickoff","is_final":true,"odds":[{"sportsbook":"pinnacle","market_type":"moneyline","selection":"Seattle Mariners","selection_type":"home","line":null,"odds_american":-158,"odds_decimal":1.633,"implied_probability":0.6122,"no_vig_probability":0.5980,"fair_close_decimal":1.6722,"closing_probability":0.5980,"source_updated_at":"2026-04-22T22:09:46.300Z"}]}}},"meta":{"source":"valkey"}}""",
        ).jsonObject
        val c = SharpApiExtras.parseClosings(body, 1L).single()
        assertEquals("pinnacle", c.book)
        assertEquals(0.598, c.closingProbability!!, 1e-9)
        assertEquals("kickoff", c.trigger)
        assertEquals("home", c.selectionType)
        assertTrue(SharpApiExtras.parseClosings(Json.parseToJsonElement("""{"success":true,"data":{"event_id":"x","books":{}}}""").jsonObject, 1L).isEmpty())
    }

    @Test
    fun `an EV row keeps the fields a study needs`() {
        val body = Json.parseToJsonElement(
            """{"data":[{"id":"h","event_id":"nba_x","ev_percentage":4.2,"odds_american":105,"odds_decimal":2.05,"fair_probability":0.51,"sharp_book":"pinnacle","cross_ref_count":2,"selection":"Over","display_selection":"Over 27.5","market_type":"player_points","line":27.5,"sportsbook":"novig","league":"nba","home_team":"A","away_team":"B","start_time":"2026-02-08T19:00:00Z","is_live":false,"player_name":"Jayson Tatum","quality_tier":"B","warnings":["SINGLE_SHARP_REF"],"oldest_odds_age_seconds":12.5,"kelly_percent":2.1}],"pagination":{"has_more":false}}""",
        ).jsonObject
        val e = SharpApiExtras.parseEv(body, 5L).single()
        assertEquals("Over 27.5", e.selection)
        assertEquals(4.2, e.evPercent!!, 1e-9)
        assertEquals("Jayson Tatum", e.player)
        assertEquals(listOf("SINGLE_SHARP_REF"), e.warnings)
        assertEquals(2, e.crossRefs)
    }
}

/** SharpAPI's WebSocket (SS): the per-book snapshot fills the store, compact deltas merge by id, removals delete, game state becomes the live lane's score. */
class SharpApiStreamTest {
    private var now = java.time.Instant.parse("2026-10-25T23:30:00Z").toEpochMilli()

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `frames go into the store and the score board`() {
        val feed = SharpApiFeed(SharpApiClient(OkHttpClient(), Json, { "" }), clock = { now })
        val scores = SharpApiScores(clock = { now })
        val st = SharpApiStream(OkHttpClient(), { "sk_test_FAKE" }, feed, scores, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), clock = { now })
        st.handle(obj("""{"type":"connected","stream_id":"ws_x","global_seq":"10","features":{"ev":true}}"""))
        assertTrue(st.live)
        st.handle(obj("""{"type":"initial","source":"pinnacle","data":[{"id":"p1","sportsbook":"pinnacle","event_id":"nfl_chiefs_raiders_2026-10-25_b3","league":"nfl","home_team":"Kansas City Chiefs","away_team":"Las Vegas Raiders","market_type":"moneyline","selection":"Kansas City Chiefs","selection_type":"home","odds_decimal":1.5,"event_start_time":"2026-10-25T23:00:00Z","timestamp":"2026-10-25T23:29:58Z","is_live":true}],"count":1}"""))
        assertEquals(1.5, feed.rows("nfl").single().decimal, 1e-9)
        // A compact delta: only the id and what changed.
        st.handle(obj("""{"type":"odds:update","source":"pinnacle","data":[{"id":"p1","odds_decimal":1.62,"odds_american":-161,"timestamp":"2026-10-25T23:30:00Z"},{"id":"unknown","odds_decimal":2.0}],"count":2}"""))
        val r = feed.rows("nfl").single()
        assertEquals(1.62, r.decimal, 1e-9)
        assertTrue(r.live)
        assertEquals(1L, feed.streamMissed)
        st.handle(obj("""{"type":"odds:locked","source":"pinnacle","data":[{"id":"p1","is_active":false}]}"""))
        assertFalse(feed.rows("nfl").single().active)
        st.handle(obj("""{"type":"odds:removed","source":"pinnacle","ids":["p1"],"count":1}"""))
        assertTrue(feed.rows("nfl").isEmpty())
        // Game state: a snapshot, an update carrying only what changed, a removal.
        st.handle(obj("""{"type":"gamestate:snapshot","data":[{"event_id":"nfl_chiefs_raiders_2026-10-25_b3","home_team":"Kansas City Chiefs","away_team":"Las Vegas Raiders","sport":"football","league":"nfl","home_score":7,"away_score":3,"is_live":true,"game_period":"Q2","period_number":2,"game_clock":"5:42","book_count":4}]}"""))
        val sc = scores.scoreFor("NFL", "Kansas City Chiefs", "Las Vegas Raiders")!!
        assertEquals(7, sc.game.homeScore)
        assertEquals(342.0, sc.game.clockSec, 1e-9)
        assertEquals(2, sc.game.period)
        st.handle(obj("""{"type":"gamestate:update","data":[{"event_id":"nfl_chiefs_raiders_2026-10-25_b3","away_score":10}]}"""))
        assertEquals(10, scores.scoreFor("NFL", "Kansas City Chiefs", "Las Vegas Raiders")!!.game.awayScore)
        assertEquals(7, scores.scoreFor("NFL", "Kansas City Chiefs", "Las Vegas Raiders")!!.game.homeScore)
        // Novig naming the teams the other way round reads the score the other way round.
        assertEquals(10, scores.scoreFor("NFL", "Las Vegas Raiders", "Kansas City Chiefs")!!.game.homeScore)
        st.handle(obj("""{"type":"gamestate:removed","data":["nfl_chiefs_raiders_2026-10-25_b3"]}"""))
        assertNull(scores.scoreFor("NFL", "Kansas City Chiefs", "Las Vegas Raiders"))
    }

    @Test
    fun `the REST game state replaces the slate and a final is not a live score`() {
        val scores = SharpApiScores(clock = { now })
        scores.applyRest(obj("""{"data":{"football":{"nfl_a_b_2026-10-25_b3":{"home_team":"A Team","away_team":"B Team","sport":"football","league":"nfl","home_score":21,"away_score":20,"is_live":false,"status":"final","winner":"home"}}}}"""))
        assertEquals(1, scores.size())
        assertNull(scores.scoreFor("NFL", "A Team", "B Team"))
        assertEquals(0, scores.live())
        // A settled final is handed out once for the journal (GS).
        assertEquals(21, scores.takeFinals().single().homeScore)
        assertTrue(scores.takeFinals().isEmpty())
        assertEquals(342.0, SharpApiScores.seconds("5:42")!!, 1e-9)
        assertNull(SharpApiScores.seconds("HT"))
    }
}
