package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TheOddsApiClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))

    private fun pool(keys: List<String>) = KeyPool(QuotaPolicy.ODDS_API, { keys }, meter)

    private fun client(keys: List<String> = listOf("test-key")) = TheOddsApiClient(
        httpClient = OkHttpClient(),
        pool = pool(keys),
        json = json,
        baseUrl = server.url("/v4").toString().trimEnd('/'),
        clock = { 42L },
        minIntervalMs = 0,
    )

    @Test
    fun `parses every book's moneyline, spread and total with points and sides`() = runTest {
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "497").setHeader("x-requests-used", "3"))

        val snap = client().fetch("americanfootball_nfl", listOf("pinnacle", "draftkings"))

        assertEquals(497, snap.creditsRemaining)
        assertEquals(42L, snap.fetchedAtMs)
        val e = snap.events.single()
        assertEquals("Dallas Cowboys", e.home)
        assertEquals(1790540700000L, e.commenceMs)
        val pinSpread = e.markets.first { it.bookKey == "pinnacle" && it.kind == LineKind.SPREAD }
        // The line is the HOME side's handicap: Dallas (home) +3.5.
        assertEquals(3.5, pinSpread.line!!, 0.0)
        assertEquals(-3.5, pinSpread.quotes.first { it.side == Side.AWAY }.point!!, 0.0)
        val total = e.markets.first { it.kind == LineKind.TOTAL }
        assertEquals(47.5, total.line!!, 0.0)
        assertEquals(Side.OVER, total.quotes.first().side)
    }

    @Test
    fun `asks for named bookmakers, never regions, and never Novig itself`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        client().fetch("americanfootball_nfl", listOf("pinnacle", "novig", "draftkings", "pinnacle"))
        val url = server.takeRequest().requestUrl!!
        assertEquals("pinnacle,draftkings", url.queryParameter("bookmakers"))
        assertEquals(null, url.queryParameter("regions"))
        assertEquals("h2h,spreads,totals", url.queryParameter("markets"))
        assertEquals("test-key", url.queryParameter("apiKey"))
    }

    @Test
    fun `a scan asks only for the market families being priced, which is all it pays for`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        val nfl = Leagues.byNovigName("NFL")!!
        val snap = client().odds(nfl, ScanSettings(families = setOf(MarketFamily.MONEYLINE, MarketFamily.TOTAL)))
        assertEquals("h2h,totals", server.takeRequest().requestUrl!!.queryParameter("markets"))
        assertEquals("oddsapi", snap.provider)
    }

    @Test
    fun `back-to-back calls are spaced out as their docs ask`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        val spaced = TheOddsApiClient(OkHttpClient(), pool(listOf("k")), json, server.url("/v4").toString().trimEnd('/'), minIntervalMs = 300)
        val t0 = System.currentTimeMillis()
        spaced.fetch("americanfootball_nfl", listOf("pinnacle"))
        spaced.fetch("baseball_mlb", listOf("pinnacle"))
        assertTrue(System.currentTimeMillis() - t0 >= 300)
        Unit
    }

    @Test
    fun `caps bookmakers at ten so a refresh costs one region of credits`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        client().fetch("basketball_nba", (1..14).map { "book$it" })
        assertEquals(10, server.takeRequest().requestUrl!!.queryParameter("bookmakers")!!.split(',').size)
    }

    @Test
    fun `an out of season sport is an empty result, not a dead key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Unknown sport"}"""))
        assertTrue(client().fetch("basketball_wnba", listOf("pinnacle")).events.isEmpty())
    }

    @Test(expected = TheOddsApiException::class)
    fun `a server error throws instead of silently returning nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        client().fetch("americanfootball_nfl", listOf("pinnacle"))
    }

    @Test
    fun `a used-up key rotates to the next key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Usage quota has been reached","error_code":"OUT_OF_USAGE_CREDITS"}"""))
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi))
        val snap = client(keys = listOf("used-up", "fresh")).fetch("americanfootball_nfl", listOf("pinnacle"))
        assertFalse(snap.events.isEmpty())
        assertEquals("used-up", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
        assertEquals("fresh", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
    }

    @Test
    fun `every key used up surfaces the quota reason`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Usage quota has been reached"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("Usage quota has been reached"))
        val e = assertThrows(AllKeysExhaustedException::class.java) {
            runBlocking { client(keys = listOf("a", "b")).fetch("americanfootball_nfl", listOf("pinnacle")) }
        }
        assertTrue(e.message, e.message!!.contains("used up"))
    }

    @Test
    fun `outcomes that name neither team are dropped with their whole market`() {
        val body = """[{"id":"x","commence_time":"2026-09-27T20:25:00Z","home_team":"A","away_team":"B","bookmakers":[
            {"key":"k","title":"K","markets":[{"key":"h2h","outcomes":[{"name":"A","price":1.9},{"name":"Someone","price":1.9}]}]}]}]"""
        assertTrue(TheOddsApiClient.parseEvents(body, json).single().markets.isEmpty())
    }

    @Test
    fun `every call's usage headers land in the meter, per key`() = runTest {
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "488").setHeader("x-requests-used", "12").setHeader("x-requests-last", "3"))
        client(keys = listOf("k1", "k2")).fetch("americanfootball_nfl", listOf("pinnacle"))
        val u = meter.flow.value.providers.getValue("oddsapi").keys.getValue("k1")
        assertEquals(488, u.remaining)
        assertEquals(12, u.used)
        assertEquals(500, u.limit)
        assertEquals(3, u.lastCost)
    }

    @Test
    fun `a key that can't afford the next call is skipped before it's refused`() = runTest {
        server.enqueue(MockResponse().setBody("[]").setHeader("x-requests-remaining", "2").setHeader("x-requests-used", "498").setHeader("x-requests-last", "0"))
        server.enqueue(MockResponse().setBody("[]").setHeader("x-requests-remaining", "500").setHeader("x-requests-used", "0"))
        val c = client(keys = listOf("low", "fresh"))
        c.fetch("americanfootball_nfl", listOf("pinnacle"))          // learns: "low" has 2 left
        c.fetch("americanfootball_nfl", listOf("pinnacle"))          // needs 3 (h2h,spreads,totals): goes straight to "fresh"
        assertEquals("low", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
        assertEquals("fresh", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
    }

    @Test
    fun `a wrong key is reported as refused, not as used up`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"API key is not valid","error_code":"INVALID_KEY"}"""))
        assertThrows(AllKeysExhaustedException::class.java) { runBlocking { client().fetch("americanfootball_nfl", listOf("pinnacle")) } }
        val u = meter.flow.value.providers.getValue("oddsapi").keys.getValue("test-key")
        assertTrue(u.lastNote!!.contains("INVALID_KEY"))
    }

    // ---- ParlayAPI: the same format under /v1 (Tj, 2026-09-30, RESEARCH.md §43) ---------------------------------------

    private fun parlay(keys: List<String> = listOf("pk")) = TheOddsApiClient(
        httpClient = OkHttpClient(),
        pool = KeyPool(QuotaPolicy.PARLAY, { keys }, meter),
        json = json,
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        clock = { 42L },
        minIntervalMs = 0,
        feed = OddsFeed.PARLAY,
    )

    @Test
    fun `ParlayAPI is its own source with its own books, and its usage headers land in its own meter`() = runTest {
        val c = parlay()
        assertEquals("parlay", c.id)
        assertEquals("ParlayAPI", c.displayName)
        // Its own list (Pinnacle first), whatever books Settings picked for The Odds API.
        val books = c.booksFor(ScanSettings(referenceBooks = listOf("draftkings")))
        assertEquals("pinnacle", books.first())
        assertFalse("novig" in books)
        // Pinnacle only asks for Pinnacle's book alone, from either feed.
        assertEquals(listOf("pinnacle"), c.booksFor(ScanSettings(pinnacleOnly = true)))
        // Its docs: "Every response includes x-requests-used, x-requests-remaining, and x-requests-last" (parlay-api.com/docs, 2026-09-30).
        // A Starter key (20,000 a month), early in its month.
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "19970").setHeader("x-requests-used", "30").setHeader("x-requests-last", "1"))
        val snap = c.fetch("americanfootball_nfl", books)
        assertEquals(19970, snap.creditsRemaining)
        assertEquals("parlay", snap.provider)
        assertTrue(server.takeRequest().requestUrl!!.encodedPath.startsWith("/v1/sports/americanfootball_nfl/odds"))
        val u = meter.flow.value.providers.getValue("parlay").keys.getValue("pk")
        assertEquals(19970, u.remaining)
        assertEquals(20000, u.limit)
        assertEquals(1, u.lastCost)
        // x-credits-* names are read too, should a response carry only those.
        server.enqueue(MockResponse().setBody("[]").setHeader("x-credits-remaining", "19969"))
        assertEquals(19969, c.fetch("americanfootball_nfl", books).creditsRemaining)
    }

    @Test
    fun `a ParlayAPI key out of credits rotates to the next`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"credit_limit_exceeded"}"""))
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi))
        val snap = parlay(listOf("spent", "fresh")).fetch("americanfootball_nfl", listOf("pinnacle"))
        assertFalse(snap.events.isEmpty())
        // ParlayAPI's best practices (2026-09-30): the key in the X-API-Key header, never in the URL.
        val first = server.takeRequest()
        assertEquals("spent", first.getHeader("X-API-Key"))
        assertEquals(null, first.requestUrl!!.queryParameter("apiKey"))
        assertEquals("fresh", server.takeRequest().getHeader("X-API-Key"))
    }

    /** Tj, 2026-10-07: a Starter key past its day's share and free keys added: the free keys take the scans, one after another. */
    @Test
    fun `ParlayAPI scans go on to each free key in turn once the paid key can't pay`() = runTest {
        val store = JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() })
        val month = QuotaPolicy.PARLAY.periodStart(System.currentTimeMillis())
        store.update {
            UsageBook(providers = mapOf("parlay" to com.tjshea.vigilant.data.keys.ProviderUsage(keys = mapOf(
                // 100 of 20,000 left: whatever the day, below the reserve a scan leaves for closing lines.
                "paid" to com.tjshea.vigilant.data.keys.KeyUsage(periodStart = month, used = 19_900, remaining = 100, limit = 20_000),
                "free1" to com.tjshea.vigilant.data.keys.KeyUsage(periodStart = month, used = 0, remaining = 1_000, limit = 1_000),
                "free2" to com.tjshea.vigilant.data.keys.KeyUsage(periodStart = month, used = 0, remaining = 1_000, limit = 1_000),
            ))))
        }
        val c = TheOddsApiClient(
            httpClient = OkHttpClient(),
            pool = KeyPool(QuotaPolicy.PARLAY, { listOf("paid", "free1", "free2") }, UsageMeter(store)),
            json = json, baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { 42L }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
        )
        // free1 answers twice, the second leaving it below its last-100 reserve: the third scan call goes to free2.
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "900").setHeader("x-requests-used", "100").setHeader("x-requests-last", "3"))
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "99").setHeader("x-requests-used", "901").setHeader("x-requests-last", "3"))
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "997").setHeader("x-requests-used", "3").setHeader("x-requests-last", "3"))
        repeat(3) { assertFalse(c.fetch("americanfootball_nfl", listOf("pinnacle")).events.isEmpty()) }
        assertEquals(listOf("free1", "free1", "free2"), List(3) { server.takeRequest().getHeader("X-API-Key") })
    }

    /** A free key reaches back 48 hours: a call asking for more is that call's fault (HTTP 403 HISTORICAL_LIMIT), never the key's. */
    @Test
    fun `a ParlayAPI call past the plan's history fails alone and leaves the key in use`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"HISTORICAL_LIMIT","message":"Your plan reaches back 48 hours"}"""))
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi))
        val c = parlay(listOf("free"))
        assertThrows(TheOddsApiException::class.java) { runBlocking { c.fetch("americanfootball_nfl", listOf("pinnacle")) } }
        assertFalse(meter.flow.value.providers.getValue("parlay").keys.getValue("free").refused)
        assertFalse(c.fetch("americanfootball_nfl", listOf("pinnacle")).events.isEmpty())
    }
}
