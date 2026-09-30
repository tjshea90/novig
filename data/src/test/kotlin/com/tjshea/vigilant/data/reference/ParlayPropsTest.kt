package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * What ParlayAPI's Starter plan adds to a scan (Tj, 2026-09-30): a whole league's player props in one 3-credit call, alternate spreads and
 * totals for every game, and one key per book whichever feed named it.
 */
class ParlayPropsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private val now = 1_790_000_000_000L

    private fun row(book: String, player: String, market: String, line: Double?, over: Int?, under: Int?, period: String? = "FULL", age: Int = 12, event: String = "ev1") =
        """{"event_id":"$event","sport_key":"americanfootball_nfl","commence_time":"2026-10-04T17:00:00Z","home_team":"Buffalo Bills",
            "away_team":"Miami Dolphins","bookmaker":"$book","player":"$player","market_key":"$market","line":${line ?: "null"},
            "over_price":${over ?: "null"},"under_price":${under ?: "null"},"age_seconds":$age${period?.let { ",\"period\":\"$it\"" } ?: ""}}"""

    @Test
    fun `a league's props rows become games of two-sided lines, each timed by its own age`() {
        val body = """{"sport_key":"americanfootball_nfl","props":[
            ${row("pinnacle", "Josh Allen", "player_pass_yds", 245.5, -115, -105)},
            ${row("caesars", "Josh Allen", "player_pass_yds", 245.5, -110, -110, age = 90)},
            ${row("draftkings", "Josh Allen", "player_pass_yds_1st_half", 120.5, -110, -110, period = "1H")},
            ${row("draftkings", "Josh Allen", "player_pass_yds", 250.5, -110, -110, period = "UNKNOWN")},
            ${row("fanduel", "James Cook", "player_anytime_td", 0.5, 120, null)},
            ${row("fanduel", "Dalton Kincaid", "player_receptions", 3.5, -130, 110)},
            ${row("fanduel", "Somebody", "player_fantasy_points", 12.5, -110, -110)}
        ]}"""
        val page = ParlayProps.parse(body, json, "americanfootball_nfl", now)
        assertEquals(7, page.rows)
        val game = page.events.single()
        assertEquals("Buffalo Bills", game.home)
        assertEquals("Miami Dolphins", game.away)
        // Full-game lines only; a one-sided yes, and a stat Novig doesn't list, left out.
        assertEquals(3, game.markets.size)
        val pin = game.markets.first { it.bookKey == "pinnacle" }
        assertEquals(LineKind.PLAYER_PROP, pin.kind)
        assertEquals("PASSING_YARDS", pin.stat)
        assertEquals("Josh Allen", pin.subject)
        assertEquals(245.5, pin.line!!, 0.0)
        assertEquals(Odds.americanToDecimal(-115), pin.quotes.first { it.side == Side.OVER }.decimalOdds, 1e-9)
        assertEquals(now - 12_000, pin.lastUpdateMs)
        // ParlayAPI's "caesars" is The Odds API's (and PropLine's) williamhill_us: one book, one key.
        val caesars = game.markets.first { it.bookKey == "williamhill_us" }
        assertEquals("Caesars", caesars.bookTitle)
        assertEquals(now - 90_000, caesars.lastUpdateMs)
    }

    @Test
    fun `alternate spreads and totals pair each number's two sides`() {
        val body = """[{"id":"e1","sport_key":"americanfootball_nfl","commence_time":"2026-10-04T17:00:00Z","home_team":"Buffalo Bills",
          "away_team":"Miami Dolphins","bookmakers":[{"key":"pinnacle","title":"Pinnacle","last_update":"2026-10-04T16:00:00Z","markets":[
            {"key":"alternate_spreads","outcomes":[
              {"name":"Buffalo Bills","price":1.55,"point":-3.5},{"name":"Miami Dolphins","price":2.5,"point":3.5},
              {"name":"Buffalo Bills","price":2.4,"point":-9.5},{"name":"Miami Dolphins","price":1.6,"point":9.5},
              {"name":"Buffalo Bills","price":3.1,"point":-13.5}]},
            {"key":"alternate_totals","outcomes":[
              {"name":"Over","price":1.7,"point":44.5},{"name":"Under","price":2.15,"point":44.5},{"name":"Over","price":2.6,"point":51.5}]}]}]}]"""
        val markets = TheOddsApiClient.parseEvents(body, json).single().markets
        val spreads = markets.filter { it.kind == LineKind.SPREAD }
        assertEquals(listOf(-3.5, -9.5), spreads.map { it.line })
        assertEquals(9.5, spreads.first { it.line == -9.5 }.quotes.first { it.side == Side.AWAY }.point!!, 0.0)
        val totals = markets.filter { it.kind == LineKind.TOTAL }
        assertEquals(listOf(44.5), totals.map { it.line })
    }

    @Test
    fun `ParlayAPI asks for alternate lines with its main lines, The Odds API doesn't`() {
        val families = listOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD, MarketFamily.TOTAL)
        assertEquals(listOf("h2h", "spreads", "totals"), TheOddsApiClient.marketsFor(families))
        assertEquals(listOf("alternate_spreads", "alternate_totals", "h2h", "spreads", "totals"), TheOddsApiClient.marketsFor(families, OddsFeed.PARLAY))
        assertEquals(listOf("h2h"), TheOddsApiClient.marketsFor(listOf(MarketFamily.MONEYLINE), OddsFeed.PARLAY))
    }

    @Test
    fun `a ParlayAPI game-line key is The Odds API's key`() {
        val body = """[{"id":"e1","sport_key":"americanfootball_nfl","commence_time":"2026-10-04T17:00:00Z","home_team":"A","away_team":"B","bookmakers":[
            {"key":"betonline","title":"BetOnline","markets":[{"key":"h2h","outcomes":[{"name":"A","price":1.9},{"name":"B","price":1.95}]}]},
            {"key":"prophetx","title":"ProphetX","markets":[{"key":"h2h","outcomes":[{"name":"A","price":1.92},{"name":"B","price":1.96}]}]}]}]"""
        val books = TheOddsApiClient.parseEvents(body, json).single().markets.map { it.bookKey to it.bookTitle }
        assertEquals(listOf("betonlineag" to "BetOnline.ag", "prophetx" to "ProphetX"), books)
    }

    private fun client() = TheOddsApiClient(
        OkHttpClient(),
        KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, UsageMeter(JsonFileStore(File.createTempFile("u", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))),
        json, baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
    )

    @Test
    fun `one call buys the whole league's props from the real sportsbooks`() = runTest {
        server.enqueue(MockResponse().setBody("""{"props":[${row("pinnacle", "Josh Allen", "player_pass_yds", 245.5, -115, -105)}]}""").setHeader("x-requests-last", "3"))
        val source = ParlayPropsSource(client())
        val nfl = Leagues.byNovigName("NFL")!!
        assertTrue(source.supports(nfl))
        val snap = source.odds(nfl, ScanSettings())
        assertEquals(1, snap.events.single().markets.size)
        assertEquals("parlay_props", snap.provider)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/sports/americanfootball_nfl/props", url.encodedPath)
        assertEquals(ParlayProps.BOOKS.joinToString(","), url.queryParameter("bookmakers"))
        assertTrue(url.queryParameter("markets")!!.split(",").containsAll(listOf("player_pass_yds", "player_receptions", "player_anytime_td")))
        assertEquals("american", url.queryParameter("oddsFormat"))
        assertEquals(1, server.requestCount) // a short page: no second one
        // Player props off: nothing asked.
        val off = source.odds(nfl, ScanSettings(families = setOf(MarketFamily.MONEYLINE)))
        assertTrue(off.events.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a full page asks for the next one and the parts of a game join up`() = runTest {
        val first = (0 until ParlayProps.PAGE).joinToString(",") { i -> row("pinnacle", "Player $i", "player_receptions", 3.5, -110, -110) }
        server.enqueue(MockResponse().setBody("[$first]"))
        server.enqueue(MockResponse().setBody("[${row("draftkings", "Player 1", "player_receptions", 3.5, -120, 100)}]"))
        val snap = ParlayPropsSource(client()).odds(Leagues.byNovigName("NFL")!!, ScanSettings())
        assertEquals(ParlayProps.PAGE + 1, snap.events.single().markets.size)
        server.takeRequest()
        assertEquals(ParlayProps.PAGE.toString(), server.takeRequest().requestUrl!!.queryParameter("offset"))
    }

    @Test
    fun `decimal and American prices both read, nonsense doesn't`() {
        assertEquals(1.91, ParlayProps.decimal(1.91)!!, 0.0)
        assertEquals(2.2, ParlayProps.decimal(120.0)!!, 1e-9)
        assertNull(ParlayProps.decimal(0.5))
        assertNull(ParlayProps.decimal(null))
        assertFalse(ParlayProps.BOOKS.contains("novig"))
    }
}
