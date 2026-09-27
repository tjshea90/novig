package com.tjshea.vigilant.data.book

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.PropLinePropsSource
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.ScanContext
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Vigilant MGM's scan (Tj, 2026-09-27: "make it also do the same exact functions to find positive EV
 * on betmgm"): BetMGM's posted odds priced against every other book's fair line, from the same
 * requests that fetch those books, with PropLine's real client against a mock server.
 */
class SportsbookScannerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = HashMap<String, () -> MockResponse>()
    private val hour = 3_600_000L
    private var now = 1_790_500_000_000L
    private val start = now + 5 * hour

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.requestUrl!!.encodedPath]?.invoke() ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { now })

    /** PropLine as Vigilant MGM builds it: no Novig relay, BetMGM's own ids asked for. */
    private fun propLine() = PropLineClient(
        OkHttpClient(), KeyPool(QuotaPolicy.PROPLINE, { listOf("pl-key") }, meter), json,
        server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, relayNovig = false, bookIds = true,
    )

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()
    private fun seen() = iso(now - 20_000)

    private fun ok(body: String) = MockResponse().setBody(body)
        .setHeader("X-Daily-Limit", "1000").setHeader("X-Daily-Used", "42").setHeader("X-Daily-Remaining", "958")

    /** Ravens @ Cowboys: Pinnacle and DraftKings for the fair line, BetMGM's own prices with its ids. */
    private fun board(mgmBal: Int = 150, mgmSeen: String = seen()) = """
    [{"id":"555","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens","commence_time":"${iso(start)}","is_outright":false,
      "bookmakers":[
        {"key":"pinnacle","title":"Pinnacle","last_update":"${seen()}","markets":[
          {"key":"h2h","last_update":"${seen()}","outcomes":[
            {"name":"Dallas Cowboys","price":-150,"point":null,"side":"home","last_seen_at":"${seen()}"},
            {"name":"Baltimore Ravens","price":130,"point":null,"side":"away","last_seen_at":"${seen()}"}]},
          {"key":"spreads","last_update":"${seen()}","outcomes":[
            {"name":"Dallas Cowboys","price":-110,"point":-3.5,"side":"home","last_seen_at":"${seen()}"},
            {"name":"Baltimore Ravens","price":-110,"point":3.5,"side":"away","last_seen_at":"${seen()}"}]}]},
        {"key":"draftkings","title":"DraftKings","last_update":"${seen()}","markets":[
          {"key":"h2h","last_update":"${seen()}","outcomes":[
            {"name":"Dallas Cowboys","price":-155,"point":null,"side":"home","last_seen_at":"${seen()}"},
            {"name":"Baltimore Ravens","price":130,"point":null,"side":"away","last_seen_at":"${seen()}"}]}]},
        {"key":"betmgm","title":"BetMGM","last_update":"$mgmSeen","link":"https://sports.{state}.betmgm.com/en/sports/events/ravens-at-cowboys-17345678",
         "book_event_id":"17345678","markets":[
          {"key":"h2h","last_update":"$mgmSeen","outcomes":[
            {"name":"Dallas Cowboys","price":-175,"point":null,"side":"home","last_seen_at":"$mgmSeen","book_outcome_id":"888-1001"},
            {"name":"Baltimore Ravens","price":$mgmBal,"point":null,"side":"away","last_seen_at":"$mgmSeen","book_outcome_id":"888-1002"}]},
          {"key":"spreads","last_update":"$mgmSeen","outcomes":[
            {"name":"Dallas Cowboys","price":-105,"point":-3.5,"side":"home","last_seen_at":"$mgmSeen","book_outcome_id":"889-2001"},
            {"name":"Baltimore Ravens","price":-115,"point":3.5,"side":"away","last_seen_at":"$mgmSeen","book_outcome_id":"889-2002"}]},
          {"key":"totals","last_update":"$mgmSeen","team":null,"outcomes":[
            {"name":"Over","price":-110,"point":51.5,"last_seen_at":"$mgmSeen"},
            {"name":"Under","price":-110,"point":51.5,"last_seen_at":"$mgmSeen"}]}]}
      ]}]
    """.trimIndent()

    /** The game's props: Pinnacle's receptions line and BetMGM's price on it. */
    private val props = """
    {"id":"555","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens","commence_time":"${iso(start)}","bookmakers":[
      {"key":"pinnacle","title":"Pinnacle","markets":[{"key":"player_receptions","last_update":"${seen()}","outcomes":[
        {"name":"Over","description":"CeeDee Lamb","price":-120,"point":6.5,"last_seen_at":"${seen()}"},
        {"name":"Under","description":"CeeDee Lamb","price":100,"point":6.5,"last_seen_at":"${seen()}"}]}]},
      {"key":"betmgm","title":"BetMGM","book_event_id":"17345678","markets":[{"key":"player_receptions","last_update":"${seen()}","outcomes":[
        {"name":"Over","description":"CeeDee Lamb","price":120,"point":6.5,"last_seen_at":"${seen()}","book_outcome_id":"990-3001"},
        {"name":"Under","description":"CeeDee Lamb","price":-150,"point":6.5,"last_seen_at":"${seen()}","book_outcome_id":"990-3002"}]}]}
    ]}
    """.trimIndent()

    /** Only Pinnacle is sharp, no blend: each fair number below is Pinnacle's, devigged multiplicatively. */
    private val settings = ScanSettings(
        leagues = setOf("NFL"),
        referenceBooks = listOf("pinnacle", "draftkings", "betmgm"),
        fairSource = FairSource.SHARP,
        devigMethod = DevigMethod.MULTIPLICATIVE,
        sharpBooks = setOf("pinnacle", "betmgm"),
        minBooks = 1,
        usePinnacle = false, usePolymarket = false, useKalshi = false, useOddsApi = false,
    )

    private fun routesUp(board: String = board()) {
        routes["/v1/sports/americanfootball_nfl/odds"] = { ok(board) }
        routes["/v1/sports/americanfootball_nfl/events/555/odds"] = { ok(props) }
    }

    private fun ScanResult.bet(selection: String) = opportunities.single { it.selection == selection }

    @Test
    fun `BetMGM's price is priced against every other book's fair line, never its own`() = runTest {
        routesUp()
        val client = propLine()
        val scanner = SportsbookScanner(Sportsbook.BETMGM, clock = { now })
        val report = scanner.scan(settings, listOf(client, PropLinePropsSource(client)))
        val result = report.result!!

        // Pinnacle -150/+130: fair 0.5798/0.4202. BetMGM's +150 on the Ravens pays 2.5: +5.04%.
        val bal = result.bet("Baltimore Ravens")
        val fairBal = (1 / 2.3) / (1 / 2.3 + 1 / (1 + 100.0 / 150))
        assertEquals(fairBal, bal.fairProbability!!, 1e-9)
        assertEquals(fairBal * 2.5 - 1, bal.evPercent!!, 1e-9)
        assertEquals(1 / 2.5, bal.quote!!.price, 1e-12)
        assertEquals(0.0, bal.quote!!.fee, 0.0)
        // BetMGM is marked sharp in these settings, and still never prices its own fair line.
        assertFalse(bal.fair!!.booksUsed.any { it.contains("BetMGM") })
        assertEquals(listOf("Pinnacle"), bal.fair!!.sharpBooksUsed)
        assertTrue(bal.evPercent!! > 0.05)
        assertTrue(result.bet("Dallas Cowboys").evPercent!! < 0)
        // The feed shows the edges, best first (the prop's is below).
        assertEquals(listOf("CeeDee Lamb Over 6.5", "Baltimore Ravens"), result.feed(settings).map { it.selection })

        // BetMGM's props came in the same per-game request as Pinnacle's: +120 on 6.5 receptions over.
        val lamb = result.bet("CeeDee Lamb Over 6.5")
        val fairOver = (1 / (1 + 100.0 / 120)) / (1 / (1 + 100.0 / 120) + 1 / 2.0)
        assertEquals(fairOver * 2.2 - 1, lamb.evPercent!!, 1e-9)
        // BetMGM's 51.5 total has no other book on that number: not priced.
        assertTrue(result.opportunities.none { it.selection.startsWith("Over 51.5") })
    }

    @Test
    fun `no request is made just for BetMGM - one league board and one props call carry it`() = runTest {
        routesUp()
        val client = propLine()
        SportsbookScanner(Sportsbook.BETMGM, clock = { now }).scan(settings, listOf(client, PropLinePropsSource(client)))

        val boards = requests.filter { it.requestUrl!!.encodedPath.endsWith("/americanfootball_nfl/odds") }
        val gameProps = requests.filter { it.requestUrl!!.encodedPath.contains("/events/555/odds") }
        assertEquals(1, boards.size)
        assertEquals(1, gameProps.size)
        // The only other call is the free game list the props source matches against (re-used from the board).
        assertEquals(2, requests.size)
        for (r in boards + gameProps) {
            val books = r.requestUrl!!.queryParameter("bookmakers")!!.split(',')
            assertEquals("betmgm", books.first())
            assertTrue("pinnacle" in books && "draftkings" in books)
            // Vigilant MGM has no Novig reads to order: Novig isn't asked for.
            assertFalse("novig" in books)
            assertEquals("true", r.requestUrl!!.queryParameter("includeBookIds"))
            assertEquals("true", r.requestUrl!!.queryParameter("includeLinks"))
        }
    }

    @Test
    fun `a BetMGM bet carries BetMGM's own ids, and its link opens the exact bet slip`() = runTest {
        routesUp()
        val client = propLine()
        val result = SportsbookScanner(Sportsbook.BETMGM, clock = { now }).scan(settings, listOf(client, PropLinePropsSource(client))).result!!
        val ref = result.bet("Baltimore Ravens").outcome.bookRef!!
        assertEquals("17345678", ref.eventId)
        assertEquals("888-1002", ref.outcomeId)
        assertEquals("https://sports.nj.betmgm.com/en/sports?options=17345678-888-1002&type=Single", BetMgmLinks.link(ref, "nj").url)
        assertTrue(BetMgmLinks.link(ref, "nj").exact)
        // The prop's ids ride its own per-game call.
        assertEquals("990-3001", result.bet("CeeDee Lamb Over 6.5").outcome.bookRef!!.outcomeId)
    }

    @Test
    fun `a BetMGM price its feed last saw over five minutes ago is never priced`() = runTest {
        routesUp(board(mgmSeen = iso(now - 6 * 60_000)))
        val client = propLine()
        val result = SportsbookScanner(Sportsbook.BETMGM, clock = { now }).scan(settings, listOf(client)).result
        assertTrue(result == null || result.opportunities.none { it.selection == "Baltimore Ravens" })
    }

    @Test
    fun `a BetMGM bet leaves the feed once BetMGM's own price is five minutes old`() = runTest {
        routesUp(board(mgmSeen = iso(now - 60_000)))
        val client = propLine()
        val result = SportsbookScanner(Sportsbook.BETMGM, clock = { now }).scan(settings, listOf(client)).result!!
        val bal = result.bet("Baltimore Ravens")
        // BetMGM's price (1 min old) is older than Pinnacle's (20 s): the bet ages from BetMGM's.
        assertEquals(now - 60_000, bal.fairAsOfMs)
        assertFalse(bal.fairIsOld(now + 3 * 60_000))
        assertTrue(bal.fairIsOld(now + 4 * 60_000 + 1))
    }

    @Test
    fun `recheck re-reads BetMGM's lines with one request per league and re-prices`() = runTest {
        routesUp()
        val client = propLine()
        val scanner = SportsbookScanner(Sportsbook.BETMGM, clock = { now })
        val first = scanner.scan(settings, listOf(client, PropLinePropsSource(client))).result!!
        val id = first.bet("Baltimore Ravens").market.marketId
        requests.clear()

        now += 30_000
        routes["/v1/sports/americanfootball_nfl/odds"] = { ok(board(mgmBal = 120)) }
        val report = scanner.recheck(settings, listOf(id))
        assertEquals(1, requests.size)
        assertTrue(requests.single().requestUrl!!.encodedPath.endsWith("/americanfootball_nfl/odds"))
        assertEquals(1, report.read)
        assertEquals(0, report.failed)
        // BetMGM moved to +120: the edge is gone.
        val bal = report.result!!.bet("Baltimore Ravens")
        assertEquals(1 / 2.2, bal.quote!!.price, 1e-12)
        assertTrue(bal.evPercent!! < 0)
    }

    @Test
    fun `re-pricing under new settings makes no request`() = runTest {
        routesUp()
        val client = propLine()
        val scanner = SportsbookScanner(Sportsbook.BETMGM, clock = { now })
        scanner.scan(settings, listOf(client, PropLinePropsSource(client)))
        requests.clear()
        val repriced = scanner.reprice(settings.copy(families = setOf(MarketFamily.MONEYLINE)))!!
        assertTrue(requests.isEmpty())
        assertTrue(repriced.opportunities.all { it.market.marketType == "MONEY" })
        assertEquals(emptySet<String>(), scanner.unscannedLeagues(settings))
        assertEquals(setOf("MLB"), scanner.unscannedLeagues(settings.copy(leagues = setOf("NFL", "MLB"))))
    }

    /** Pinnacle's own feed, listing the game with home and away the other way round from PropLine. */
    private class ReversedPinnacle(val now: Long, val start: Long) : ReferenceSource {
        override val id = "pinnacle"
        override val displayName = "Pinnacle"
        override suspend fun odds(league: League, settings: ScanSettings) = RefSnapshot(
            league.oddsApiSportKey,
            listOf(
                RefEvent(
                    "pn-1", league.oddsApiSportKey, start, home = "Baltimore Ravens", away = "Dallas Cowboys",
                    markets = listOf(RefBookMarket("pinnacle", "Pinnacle", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 2.10, 3.5), RefQuote(Side.AWAY, 1.80, -3.5)), now)),
                ),
            ),
            now,
        )
    }

    @Test
    fun `a fair feed with home and away reversed lines up with BetMGM's board`() = runTest {
        routesUp()
        val client = propLine()
        val result = SportsbookScanner(Sportsbook.BETMGM, clock = { now })
            .scan(settings.copy(usePinnacle = true), listOf(client, ReversedPinnacle(now - 5_000, start))).result!!
        // Pinnacle's own feed (first in the merge): Cowboys -3.5 at 1.80, Ravens +3.5 at 2.10, fair 0.5385.
        // BetMGM's Cowboys -3.5 at -105 (1.952): +5.1%. Read the wrong way round, it would be priced off
        // PropLine's copy of Pinnacle (50/50) or not at all.
        val fairDal = (1 / 1.80) / (1 / 1.80 + 1 / 2.10)
        val dal = result.bet("Dallas Cowboys -3.5")
        assertEquals(fairDal, dal.fairProbability!!, 1e-9)
        assertEquals(fairDal * (1 + 100.0 / 105) - 1, dal.evPercent!!, 1e-9)
        assertEquals((1 - fairDal) * (1 + 100.0 / 115) - 1, result.bet("Baltimore Ravens +3.5").evPercent!!, 1e-9)
    }

    @Test
    fun `Novig's scan is untouched - the default PropLine client asks exactly as before`() = runTest {
        routesUp()
        val novigClient = PropLineClient(
            OkHttpClient(), KeyPool(QuotaPolicy.PROPLINE, { listOf("pl-key") }, meter), json,
            server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0,
        )
        novigClient.odds(com.tjshea.vigilant.data.scanner.Leagues.byNovigName("NFL")!!, settings)
        val url = requests.single().requestUrl!!
        // Novig's relay rides along as before; no book ids or links are asked for (PropLine sends them null then).
        assertEquals("pinnacle,draftkings,betmgm,novig", url.queryParameter("bookmakers"))
        assertNull(url.queryParameter("includeBookIds"))
        assertNull(url.queryParameter("includeLinks"))
    }

    @Test
    fun `BetMGM ids sent as numbers still parse`() {
        val raw = board().replace("\"book_event_id\":\"17345678\"", "\"book_event_id\":17345678")
            .replace("\"book_outcome_id\":\"888-1002\"", "\"book_outcome_id\":1002")
        val e = PropLineClient.parseEvents(raw, json, "americanfootball_nfl").single()
        val ml = e.markets.single { it.bookKey == "betmgm" && it.kind == LineKind.MONEYLINE }
        assertEquals("17345678", ml.bookEventId)
        assertEquals("1002", ml.quotes.single { it.side == Side.AWAY }.bookOutcomeId)
        // Every other book still parses.
        assertTrue(e.markets.any { it.bookKey == "pinnacle" })
    }

    @Test
    fun `a feed with no BetMGM prices puts nothing on the board`() {
        // A board built from a snapshot with only other books has no games.
        val snap = RefSnapshot("americanfootball_nfl", listOf(RefEvent("x", "americanfootball_nfl", start, "Dallas Cowboys", "Baltimore Ravens",
            listOf(RefBookMarket("pinnacle", "Pinnacle", LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 1.6, null), RefQuote(Side.AWAY, 2.4, null)), now)))), now)
        assertTrue(BookBoard.build(Sportsbook.BETMGM, listOf(snap), now, now).games.isEmpty())
    }
}
