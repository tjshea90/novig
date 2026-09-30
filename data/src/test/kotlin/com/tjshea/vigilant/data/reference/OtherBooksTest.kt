package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetGrader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * A ParlayAPI pick's sheet kept saying "no other book" (Tj, 2026-09-30: "Is there a backup fall back provider or api that can be used so that I
 * always see other sports books odds for any bet when I click on it"). Measured that day: the scans' readers drop one-sided lines (FanDuel,
 * Caesars, Bovada and ProphetX list home runs "Over" only) and didn't ask for the books that price both sides (bet365, Hard Rock, Fliff,
 * betPARX). [OtherBooks] reads every real sportsbook with one-sided lines kept, from ParlayAPI and PropLine side by side, The Odds API last.
 * `parlay-props-hr-books.json` is ParlayAPI's real answer for Jahmai Jones's home-run prop (Red Sox @ Yankees), trimmed, no key in it.
 */
class OtherBooksTest {

    private val json = Json { ignoreUnknownKeys = true }
    // The fixture's rows were read at 14:35Z on 2026-09-30; the game starts at 00:00Z (10 minutes' freshness: over 3 hours away).
    private val now = Instant.parse("2026-09-30T14:35:00Z").toEpochMilli()
    private val start = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
    private val hr = javaClass.classLoader!!.getResource("parlay-props-hr-books.json")!!.readText()

    private lateinit var server: MockWebServer
    private val routes = HashMap<String, () -> MockResponse>()
    private val asked = ArrayList<String>()

    @Before fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    asked += request.requestUrl!!.toString()
                    return routes[path]?.invoke() ?: MockResponse().setResponseCode(404)
                }
            }
            start()
        }
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun base(prefix: String) = server.url(prefix).toString().trimEnd('/')

    private fun parlay() = TheOddsApiClient(
        OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk-FAKE") }, meter), json,
        baseUrl = base("/parlay/v1"), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
    )

    private fun propLine() = PropLineClient(OkHttpClient(), KeyPool(QuotaPolicy.PROPLINE, { listOf("pl-FAKE") }, meter), json, base("/pl/v1"), clock = { now }, minIntervalMs = 0)

    private fun oddsApi() = TheOddsApiClient(
        OkHttpClient(), KeyPool(QuotaPolicy.ODDS_API, { listOf("oa-FAKE") }, meter), json, baseUrl = base("/oa/v4"), clock = { now }, minIntervalMs = 0,
    )

    private fun books(parlayOn: Boolean = true, propLineOn: Boolean = false, oddsApiOn: Boolean = false) = OtherBooks(
        parlay(), propLine(), oddsApi(), json, on = { OtherBooks.Sources(parlayOn, propLineOn, oddsApiOn) }, clock = { now },
    )

    private val game = "Boston Red Sox @ New York Yankees"
    private val market = "Player Home Runs"

    /** PropLine's game list and one game's board, in its documented shape (American prices). */
    private fun propLineRoutes(board: String) {
        routes["/pl/v1/sports/baseball_mlb/events"] = {
            MockResponse().setBody("""[{"id":"777","sport_key":"baseball_mlb","home_team":"New York Yankees","away_team":"Boston Red Sox","commence_time":"2026-10-01T00:00:00Z"}]""")
        }
        routes["/pl/v1/sports/baseball_mlb/events/777/odds"] = { MockResponse().setBody(board) }
    }

    private fun board(player: String) = """{"id":"777","home_team":"New York Yankees","away_team":"Boston Red Sox","commence_time":"2026-10-01T00:00:00Z","bookmakers":[
        {"key":"draftkings","title":"DraftKings","last_update":"2026-09-30T14:34:00Z","markets":[{"key":"batter_home_runs","last_update":"2026-09-30T14:34:00Z","outcomes":[
          {"name":"Over","description":"$player","price":650,"point":0.5},{"name":"Under","description":"$player","price":-1000,"point":0.5}]}]},
        {"key":"betmgm","title":"BetMGM","last_update":"2026-09-30T14:33:00Z","markets":[{"key":"batter_home_runs","last_update":"2026-09-30T14:33:00Z","outcomes":[
          {"name":"Over","description":"$player","price":600,"point":0.5}]}]},
        {"key":"novig","title":"Novig","markets":[{"key":"batter_home_runs","outcomes":[{"name":"Over","description":"$player","price":590,"point":0.5}]}]}]}"""

    @Test
    fun `ParlayAPI's real rows keep every real sportsbook, one-sided books kept, pick'em apps and Novig left out`() {
        val rows = OtherBooks.parlayRows(hr, json, "baseball_mlb", now).filter { it.player == "Jahmai Jones" }
        val byBook = rows.associateBy { it.book }
        assertEquals(setOf("bet365", "caesars", "fanduel", "fliff", "hardrock", "parx", "pinnacle", "prophetx"), byBook.keys)
        // FanDuel's is "Over" only: kept, as CNO's page shows it.
        assertEquals(570, byBook.getValue("fanduel").over)
        assertNull(byBook.getValue("fanduel").under)
        assertEquals(700 to -1100, byBook.getValue("bet365").over to byBook.getValue("bet365").under)
        // Every row's game start reads, whatever its format ("…Z", "…+00:00", "…-04:00").
        assertTrue(rows.all { it.startsMs == start })
    }

    @Test
    fun `a pick's sheet gets every book with a current price, the older ones apart with their age`() = runBlocking {
        routes["/parlay/v1/sports/baseball_mlb/props"] = { MockResponse().setBody(hr) }
        val r = books().view("MLB", game, start, market, "Jahmai Jones Over 0.5", marketKey = "player_home_runs")
        val view = assertNotNullAnd(r.view)
        val prices = view.prices.associateBy { it.code }
        // bet365 prices both sides; FanDuel and ProphetX only the over: all three shown (the scans' reader kept none of this but ProphetX's).
        assertEquals(setOf("B365", "FD", "PX"), prices.keys)
        assertEquals(700 to -1100, prices.getValue("B365").odds to prices.getValue("B365").otherOdds)
        assertEquals(570 to null, prices.getValue("FD").odds to prices.getValue("FD").otherOdds)
        assertEquals("Jahmai Jones Under 0.5", view.otherBet)
        // Caesars (11 min), Pinnacle (20), Fliff (23), Hard Rock (45), betPARX (52): older than 10 minutes, shown apart, never counted.
        assertEquals(listOf("CZR", "PN", "FL", "HR-IN", "betPARX"), r.older.map { it.code })
        assertEquals(listOf(OtherBooks.PARLAY), r.sources)
        assertNull(r.why)
        // Only that market, every book (no book filter), an hour back.
        val url = asked.single()
        assertTrue(url, "markets=batter_home_runs%2Cplayer_home_runs" in url && "bookmakers" !in url && "maxAgeSec=3600" in url)
    }

    @Test
    fun `a league's market is read once for every pick in it (two minutes)`() = runBlocking {
        routes["/parlay/v1/sports/baseball_mlb/props"] = { MockResponse().setBody(hr) }
        val b = books()
        b.view("MLB", game, start, market, "Jahmai Jones Over 0.5")
        val riley = b.view("MLB", "Philadelphia Phillies @ Atlanta Braves", null, market, "Austin Riley Over 0.5")
        assertNotNull(riley.view ?: riley.older.firstOrNull())
        assertEquals(1, asked.size)
    }

    @Test
    fun `PropLine's board fills in when ParlayAPI has no book for the bet, side by side`() = runBlocking {
        routes["/parlay/v1/sports/baseball_mlb/props"] = { MockResponse().setBody("[]") }
        propLineRoutes(board("Jahmai Jones"))
        val r = books(propLineOn = true).view("MLB", game, start, market, "Jahmai Jones Over 0.5")
        val prices = assertNotNullAnd(r.view).prices.associateBy { it.code }
        assertEquals(setOf("DK", "MGM"), prices.keys)
        assertEquals(650 to -1000, prices.getValue("DK").odds to prices.getValue("DK").otherOdds)
        assertEquals(600 to null, prices.getValue("MGM").odds to prices.getValue("MGM").otherOdds)
        assertEquals(listOf(OtherBooks.PROPLINE), r.sources)
        // The Odds API (credits) wasn't asked: PropLine found books.
        assertTrue(asked.none { "/oa/" in it })
    }

    @Test
    fun `The Odds API is asked only when neither ParlayAPI nor PropLine has another book`() = runBlocking {
        routes["/parlay/v1/sports/baseball_mlb/props"] = { MockResponse().setBody("[]") }
        propLineRoutes(board("Somebody Else"))
        routes["/oa/v4/sports/baseball_mlb/events"] = {
            MockResponse().setBody("""[{"id":"oa1","sport_key":"baseball_mlb","commence_time":"2026-10-01T00:00:00Z","home_team":"New York Yankees","away_team":"Boston Red Sox"}]""")
        }
        routes["/oa/v4/sports/baseball_mlb/events/oa1/odds"] = {
            MockResponse().setBody(
                """{"id":"oa1","home_team":"New York Yankees","away_team":"Boston Red Sox","bookmakers":[{"key":"fanduel","title":"FanDuel","last_update":"2026-09-30T14:34:30Z",
                "markets":[{"key":"batter_home_runs","last_update":"2026-09-30T14:34:30Z","outcomes":[{"name":"Over","description":"Jahmai Jones","price":6.7,"point":0.5}]}]}]}""",
            ).setHeader("x-requests-last", "1")
        }
        val r = books(propLineOn = true, oddsApiOn = true).view("MLB", game, start, market, "Jahmai Jones Over 0.5")
        assertEquals(listOf("FD"), assertNotNullAnd(r.view).prices.map { it.code })
        assertEquals(570, r.view!!.prices.single().odds)
        assertEquals(listOf(OtherBooks.ODDS_API), r.sources)
    }

    @Test
    fun `none anywhere says who was asked and what's off`() = runBlocking {
        routes["/parlay/v1/sports/baseball_mlb/props"] = { MockResponse().setBody("[]") }
        val r = books().view("MLB", game, start, market, "Jahmai Jones Over 0.5")
        assertNull(r.view)
        assertEquals("No other sportsbook prices Jahmai Jones Over 0.5 right now (ParlayAPI asked; PropLine and The Odds API off or without a key)", r.why)
        val off = books(parlayOn = false).view("MLB", game, start, market, "Jahmai Jones Over 0.5")
        assertEquals("No odds source is on with a key (ParlayAPI, PropLine or The Odds API in Settings)", off.why)
    }

    @Test
    fun `one line per book, a current price beating an older two-sided one, then two-sided beats one-sided`() {
        val stale = OtherBooks.Line("fanduel", 570, -900, OtherBooks.PARLAY, now - 30 * 60_000L)
        val fresh = OtherBooks.Line("fanduel", 560, null, OtherBooks.PROPLINE, now - 30_000L)
        assertEquals(fresh, OtherBooks.merge(listOf(stale, fresh), now, start).single())
        val both = OtherBooks.Line("draftkings", 650, -1000, OtherBooks.PROPLINE, now - 60_000L)
        val one = OtherBooks.Line("draftkings", 660, null, OtherBooks.PARLAY, now - 10_000L)
        assertEquals(both, OtherBooks.merge(listOf(one, both), now, start).single())
        // Pinnacle and the exchanges lead, then books pricing both sides.
        val pn = OtherBooks.Line("pinnacle", 534, -808, OtherBooks.PARLAY, now)
        assertEquals(listOf("PN", "DK", "FD"), OtherBooks.merge(listOf(fresh, both, pn), now, start).map { it.code })
    }

    @Test
    fun `a prop's other side is named for the table`() {
        assertEquals("Jahmai Jones Under 0.5", OtherBooks.otherSide("Jahmai Jones Over 0.5"))
        assertEquals("Josh Allen Over 29.5", OtherBooks.otherSide("Josh Allen Under 29.5"))
        assertNull(OtherBooks.otherSide("Dallas Cowboys"))
    }

    @Test
    fun `a game line or an unreadable bet asks nothing`() = runBlocking {
        val r = books().view("NFL", "Pittsburgh Steelers @ Cleveland Browns", null, "Moneyline", "Cleveland Browns")
        assertNull(r.view)
        assertTrue(asked.isEmpty())
        assertNotNull(BetGrader.pickOf(market, "Jahmai Jones Over 0.5"))
    }

    private fun <T : Any> assertNotNullAnd(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun `a busy props board is asked again once, after the wait it names`() = runBlocking {
        var n = 0
        routes["/parlay/v1/sports/baseball_mlb/props"] = {
            if (n++ == 0) MockResponse().setResponseCode(503).setHeader("Retry-After", "1")
                .setBody("""{"error":"props_temporarily_busy","detail":"The props board is being rebuilt under load. Retry in a couple of seconds."}""")
            else MockResponse().setBody(hr)
        }
        val r = books().view("MLB", game, start, market, "Jahmai Jones Over 0.5")
        assertEquals(2, n)
        assertNotNull(r.view)
    }
}
