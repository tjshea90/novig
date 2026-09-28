package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ExchangeClientsTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val nfl = Leagues.byNovigName("NFL")!!
    private val mlb = Leagues.byNovigName("MLB")!!
    private val ufc = Leagues.byNovigName("UFC")!!
    private val settings = ScanSettings()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun base(path: String) = server.url(path).toString().trimEnd('/')

    private fun meter(clock: () -> Long = System::currentTimeMillis) =
        UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock)

    private fun pinnPool(keys: List<String>, m: UsageMeter = meter()) = KeyPool(QuotaPolicy.PINNAPI, { keys }, m)

    // ---- ExchangeQuote --------------------------------------------------------------------------

    @Test
    fun `an exchange quote becomes two book prices whose vig is the bid-ask gap`() {
        val (a, b) = ExchangeQuote.toDecimal(0.62, 0.63, 0.03)!!
        assertEquals(1 / 0.63, a, 1e-12)
        assertEquals(1 / 0.38, b, 1e-12)
        assertEquals(0.01, 1 / a + 1 / b - 1, 1e-12)
    }

    @Test
    fun `thin, crossed or extreme quotes are refused`() {
        assertNull(ExchangeQuote.toDecimal(0.63, 0.70, 0.03)) // 7c wide
        assertNull(ExchangeQuote.toDecimal(0.63, 0.62, 0.03)) // crossed
        assertNull(ExchangeQuote.toDecimal(0.985, 0.99, 0.03)) // a 99c favorite says nothing useful
        assertNull(ExchangeQuote.toDecimal(null, 0.5, 0.03))
    }

    // ---- Polymarket -----------------------------------------------------------------------------

    @Test
    fun `polymarket games carry moneyline, half-point spreads and liquid tight totals only`() {
        val events = PolymarketClient.parse(PolymarketClient.parseMarkets(ExchangeFixtures.polymarketNfl, json), nfl, 0.03, 7L, json)
        val g = events.first { it.id == "pm:nfl-bal-dal-2026-09-27" }
        assertEquals("Ravens", g.away)
        assertEquals("Cowboys", g.home)
        assertEquals(Instant.parse("2026-09-27T20:25:00Z").toEpochMilli(), g.commenceMs)
        val ml = g.markets.single { it.kind == LineKind.MONEYLINE }
        assertEquals(1 / 0.63, ml.quotes.first { it.side == Side.AWAY }.decimalOdds, 1e-12)
        assertEquals(1 / 0.38, ml.quotes.first { it.side == Side.HOME }.decimalOdds, 1e-12)
        // Ravens -3.5 is the AWAY side, so the home line is Cowboys +3.5. The -3 line can push: dropped.
        val sp = g.markets.single { it.kind == LineKind.SPREAD }
        assertEquals(3.5, sp.line!!, 0.0)
        // 47.5 is 7c wide and 51.5 has $250 behind it: only 50.5 survives.
        assertEquals(listOf(50.5), g.markets.filter { it.kind == LineKind.TOTAL }.map { it.line })
        assertEquals("polymarket", ml.bookKey)
        assertEquals(7L, ml.lastUpdateMs)
        assertEquals(2, events.size)
    }

    @Test
    fun `polymarket is asked for the league tag, the chosen families and the scan window`() = runBlocking {
        server.enqueue(MockResponse().setBody(ExchangeFixtures.polymarketNfl))
        val c = PolymarketClient(OkHttpClient(), json, base("/"), clock = { Instant.parse("2026-09-25T12:00:00Z").toEpochMilli() })
        val snap = c.odds(nfl, settings.copy(families = setOf(MarketFamily.MONEYLINE, MarketFamily.TOTAL), daysAhead = 3))
        val url = server.takeRequest().requestUrl!!
        assertEquals("450", url.queryParameter("tag_id"))
        assertEquals(listOf("moneyline", "totals"), url.queryParameterValues("sports_market_types"))
        assertEquals("2026-09-29T12:00:00Z", url.queryParameter("end_date_max"))
        assertEquals("polymarket", snap.provider)
        assertEquals(1, server.requestCount) // a short page means the last page
    }

    @Test
    fun `polymarket pages until a short page`() = runBlocking {
        val full = (1..100).joinToString(",", "[", "]") { """{"id":"$it","sportsMarketType":"moneyline"}""" }
        server.enqueue(MockResponse().setBody(full))
        server.enqueue(MockResponse().setBody("[]"))
        PolymarketClient(OkHttpClient(), json, base("/")).odds(nfl, settings)
        server.takeRequest()
        assertEquals("100", server.takeRequest().requestUrl!!.queryParameter("offset"))
    }

    @Test
    fun `a league polymarket doesn't list is never requested`() {
        assertTrue(!PolymarketClient(OkHttpClient(), json).supports(Leagues.byNovigName("Boxing")!!))
    }

    // ---- Kalshi ---------------------------------------------------------------------------------

    private fun kalshiEvents(vararg bodies: String) = bodies.flatMap { json.decodeFromString(KalshiClient.PageDto.serializer(), it).events }

    @Test
    fun `kalshi merges a game's winner, spread and total events into one game`() {
        val g = KalshiClient.parse(kalshiEvents(ExchangeFixtures.kalshiNflGame, ExchangeFixtures.kalshiNflSpread, ExchangeFixtures.kalshiNflTotal), nfl, 0.03, 5L).single()
        assertEquals("Carolina Panthers", g.away)
        assertEquals("Cleveland Browns", g.home)
        assertEquals("2026-09-27", g.etDate)

        // Moneyline from both teams' asks: 0.58 + 0.43 = 1% hold.
        val ml = g.markets.single { it.kind == LineKind.MONEYLINE }
        assertEquals(1 / 0.58, ml.quotes.first { it.side == Side.AWAY }.decimalOdds, 1e-12)
        assertEquals(1 / 0.43, ml.quotes.first { it.side == Side.HOME }.decimalOdds, 1e-12)

        // "CAR wins by over 2.5": Yes = CAR (away) -2.5, No = CLE (home) +2.5. The home line is +2.5.
        val spreads = g.markets.filter { it.kind == LineKind.SPREAD }
        assertEquals(setOf(2.5, 20.5), spreads.map { it.line }.toSet()) // CLE -3.5 is 10c wide, CAR -7 can push
        val s25 = spreads.first { it.line == 2.5 }
        assertEquals(1 / 0.46, s25.quotes.first { it.side == Side.AWAY }.decimalOdds, 1e-12)
        assertEquals(-2.5, s25.quotes.first { it.side == Side.AWAY }.point!!, 0.0)
        assertEquals(1 / 0.55, s25.quotes.first { it.side == Side.HOME }.decimalOdds, 1e-12)

        val total = g.markets.single { it.kind == LineKind.TOTAL }
        assertEquals(43.5, total.line!!, 0.0)
        assertEquals(1 / 0.52, total.quotes.first { it.side == Side.OVER }.decimalOdds, 1e-12)
    }

    @Test
    fun `a kalshi quote with only a few contracts behind it is not a fair price`() {
        val thin = ExchangeFixtures.kalshiNflTotal.replace(
            "\"floor_strike\":43.5,\"status\":\"active\"",
            "\"floor_strike\":43.5,\"status\":\"active\",\"yes_bid_size_fp\":\"4.00\",\"yes_ask_size_fp\":\"9000.00\"",
        )
        assertTrue(thin != ExchangeFixtures.kalshiNflTotal)
        val g = KalshiClient.parse(kalshiEvents(ExchangeFixtures.kalshiNflGame, thin), nfl, 0.03, 0L).single()
        assertTrue(g.markets.none { it.kind == LineKind.TOTAL })
    }

    @Test
    fun `kalshi baseball codes give the exact Eastern start time`() {
        val games = KalshiClient.parse(kalshiEvents(ExchangeFixtures.kalshiMlbGame), mlb, 0.03, 0L)
        val pit = games.first { it.away == "Pittsburgh" }
        assertNull(pit.etDate)
        assertEquals(Instant.parse("2026-09-25T22:40:00Z").toEpochMilli(), pit.commenceMs) // 6:40pm EDT
        assertEquals("New York M", games.first { it.home == "Washington" }.away)
    }

    @Test
    fun `kalshi fights use each fighter's full name`() {
        val g = KalshiClient.parse(kalshiEvents(ExchangeFixtures.kalshiUfc), ufc, 0.03, 0L).single()
        assertEquals("Luis Hernandez", g.away)
        assertEquals("Sedriques Dumas", g.home)
        assertEquals(1, g.markets.size)
    }

    @Test
    fun `kalshi asks only for the series of the chosen families, without a key`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                when (request.requestUrl!!.queryParameter("series_ticker")) {
                    "KXNFLGAME" -> ExchangeFixtures.kalshiNflGame
                    "KXNFLTOTAL" -> ExchangeFixtures.kalshiNflTotal
                    else -> """{"events":[]}"""
                },
            )
        }
        val snap = KalshiClient(OkHttpClient(), json, base("/")).odds(nfl, settings.copy(families = setOf(MarketFamily.MONEYLINE, MarketFamily.TOTAL)))
        val asked = (1..server.requestCount).map { server.takeRequest().requestUrl!! }
        assertEquals(listOf("KXNFLGAME", "KXNFLTOTAL"), asked.map { it.queryParameter("series_ticker") })
        assertTrue(asked.all { it.queryParameter("with_nested_markets") == "true" && it.queryParameter("status") == "open" })
        assertEquals(2, snap.events.single().markets.size)
    }

    /**
     * Tj, 2026-09-28: "now it is reading the API very slow". A scan asks Kalshi for every league's game lines first,
     * then the rest: the game-line series are asked once, the props after, and nothing twice.
     */
    @Test
    fun `kalshi reads a league's game-line series first, then only its props, nothing twice`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                when (request.requestUrl!!.queryParameter("series_ticker")) {
                    "KXNFLGAME" -> ExchangeFixtures.kalshiNflGame
                    "KXNFLTOTAL" -> ExchangeFixtures.kalshiNflTotal
                    else -> """{"events":[]}"""
                },
            )
        }
        var t = 1_000L
        val kalshi = KalshiClient(OkHttpClient(), json, base("/"), clock = { t })
        assertTrue(kalshi.linesFirst)
        val s = settings.copy(families = setOf(MarketFamily.MONEYLINE, MarketFamily.TOTAL, MarketFamily.PLAYER_PROPS))
        val lines = kalshi.lines(nfl, s)!!
        t = 21_000L
        fun asked() = (1..server.requestCount - seen).map { server.takeRequest().requestUrl!!.queryParameter("series_ticker") }.also { seen = server.requestCount }
        assertEquals(listOf("KXNFLGAME", "KXNFLTOTAL"), asked())
        assertEquals(2, lines.events.single().markets.size)
        val full = kalshi.odds(nfl, s)
        val props = asked()
        assertTrue(props.isNotEmpty())
        assertTrue("no game-line series asked again: $props", props.none { it == "KXNFLGAME" || it == "KXNFLTOTAL" })
        assertTrue(props.all { KalshiClient.familyOf(it!!) == MarketFamily.PLAYER_PROPS })
        assertEquals(2, full.events.single().markets.size)
        // Quotes read 20 s before odds() was asked are stamped with when they were read, never newer.
        assertTrue(full.events.single().markets.all { it.lastUpdateMs == 1_000L })
        // A scan later reads everything afresh: what [lines] read is used once.
        kalshi.odds(nfl, s)
        assertTrue(asked().containsAll(listOf("KXNFLGAME", "KXNFLTOTAL")))
    }

    private var seen = 0

    @Test
    fun `kalshi event codes parse with and without a time`() {
        val a = KalshiClient.parseCode("KXNFLGAME-26OCT05ATLNO")!!
        assertEquals("2026-10-05", a.date.toString())
        assertNull(a.time)
        assertEquals("ATLNO", a.teams)
        val b = KalshiClient.parseCode("KXMLBSPREAD-26SEP251805CHCBOSG2")!!
        assertEquals("18:05", b.time.toString())
        assertEquals("CHCBOSG2", b.teams)
        assertNull(KalshiClient.parseCode("KXNFLGAME-NOPE"))
    }

    // ---- pinnapi --------------------------------------------------------------------------------

    @Test
    fun `pinnacle's board parses per league, with every alternate line`() = runBlocking {
        server.enqueue(MockResponse().setBody(ExchangeFixtures.pinnapiFootball))
        val c = PinnapiClient(OkHttpClient(), json, pinnPool(listOf("trial-key")), base("/kit/v1"), clock = { 9L })
        val snap = c.odds(nfl, settings)
        val r = server.takeRequest()
        assertEquals("trial-key", r.getHeader("x-portal-apikey"))
        assertEquals("5", r.requestUrl!!.queryParameter("sport_id"))
        assertEquals("prematch", r.requestUrl!!.queryParameter("event_type"))
        val e = snap.events.single()
        assertEquals("Dallas Cowboys", e.home)
        assertEquals(2.45, e.markets.first { it.kind == LineKind.MONEYLINE }.quotes.first { it.side == Side.HOME }.decimalOdds, 0.0)
        assertEquals(setOf(3.5, 3.0), e.markets.filter { it.kind == LineKind.SPREAD }.map { it.line }.toSet())
        assertEquals(-3.5, e.markets.first { it.line == 3.5 }.quotes.first { it.side == Side.AWAY }.point!!, 0.0)
        assertEquals("pinnacle", e.markets.first().bookKey)
    }

    @Test
    fun `NFL and NCAAF share one pinnacle request per scan`() = runBlocking {
        server.enqueue(MockResponse().setBody(ExchangeFixtures.pinnapiFootball))
        val c = PinnapiClient(OkHttpClient(), json, pinnPool(listOf("k")), base("/kit/v1"), clock = { 9L })
        c.odds(nfl, settings)
        val college = c.odds(Leagues.byNovigName("NCAAF")!!, settings)
        assertEquals(1, server.requestCount)
        assertEquals("Texas Longhorns", college.events.single().away)
    }

    @Test
    fun `a pinnacle daily 429 rests that key until pinnapi's retry time and rotates to the next`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited","window":"day","limit":100,"retry_after_ms":7200000}"""))
        server.enqueue(MockResponse().setBody(ExchangeFixtures.pinnapiFootball))
        var now = 1_000L
        val m = meter { now }
        val c = PinnapiClient(OkHttpClient(), json, pinnPool(listOf("k1", "k2"), m), base("/kit/v1"), clock = { now })
        runBlocking { c.odds(nfl, settings) }
        assertEquals("k1", server.takeRequest().getHeader("x-portal-apikey"))
        assertEquals("k2", server.takeRequest().getHeader("x-portal-apikey"))
        assertEquals(1_000L + 7_200_000L, m.flow.value.providers.getValue("pinnacle").keys.getValue("k1").depletedUntil)
    }

    @Test
    fun `with every pinnacle key spent the scan says until when, without calling`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited","window":"day","limit":100,"retry_after_ms":7200000}"""))
        var now = 0L
        val m = meter { now }
        val c = PinnapiClient(OkHttpClient(), json, pinnPool(listOf("k"), m), base("/kit/v1"), clock = { now }, shareMs = 0)
        val e = assertThrows(ReferenceException::class.java) { runBlocking { c.odds(nfl, settings) } }
        assertTrue(e.message!!, e.message!!.contains("used up for another 2h 0m"))
        now = 3_600_000L
        assertThrows(ReferenceException::class.java) { runBlocking { c.odds(nfl, settings) } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a bad pinnacle key says so`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_key"}"""))
        val m = meter()
        val c = PinnapiClient(OkHttpClient(), json, pinnPool(listOf("bad"), m), base("/kit/v1"))
        assertThrows(ReferenceException::class.java) { runBlocking { c.odds(nfl, settings) } }
        assertTrue(m.flow.value.providers.getValue("pinnacle").keys.getValue("bad").lastNote!!.contains("key refused"))
    }

    // ---- PinnWire (Pinnacle with player props), RESEARCH.md §22 --------------------------------

    private fun wirePool(keys: List<String>, m: UsageMeter = meter()) = KeyPool(QuotaPolicy.PINNWIRE, { keys }, m)

    /** A real PinnWire reply (2026-09-27 06:40Z, NFL, `include_specials=1`), trimmed to one game. */
    private val pinnwireFootball: String =
        javaClass.classLoader!!.getResource("pinnwire-football.json")!!.readText()

    private fun pinnacle(wire: List<String>, pinn: List<String>, m: UsageMeter = meter(), shareMs: Long = 60_000) = PinnapiClient(
        OkHttpClient(), json,
        listOf(PinnapiClient.pinnwire(wirePool(wire, m), base("/kit/v1")), PinnapiClient.pinnapi(pinnPool(pinn, m), base("/kit/v1"))),
        clock = { 9L }, shareMs = shareMs,
    )

    @Test
    fun `a real PinnWire board parses game lines and Pinnacle's player props`() = runBlocking {
        server.enqueue(MockResponse().setBody(pinnwireFootball))
        val snap = pinnacle(listOf("wire-key"), emptyList()).odds(nfl, settings)
        val r = server.takeRequest()
        assertEquals("wire-key", r.getHeader("x-api-key"))
        assertEquals("1", r.requestUrl!!.queryParameter("include_specials"))
        val e = snap.events.single()
        assertEquals("Pittsburgh Steelers", e.home)
        assertEquals("Cincinnati Bengals", e.away)
        assertEquals(2.56, e.markets.first { it.kind == LineKind.MONEYLINE }.quotes.first { it.side == Side.HOME }.decimalOdds, 0.0)
        val props = e.markets.filter { it.kind == LineKind.PLAYER_PROP }
        // Six player props; the game prop (points range) and the futures row are left out.
        assertEquals(
            setOf("RECEPTIONS", "RECEIVING_YARDS", "PASSING_YARDS", "TOUCHDOWNS", "FIELD_GOALS_MADE", "INTERCEPTIONS_THROWN"),
            props.map { it.stat }.toSet(),
        )
        val rec = props.single { it.stat == "RECEPTIONS" }
        assertEquals("Chase Brown", rec.subject)
        assertEquals(3.5, rec.line!!, 0.0)
        assertEquals(2.06, rec.quotes.single { it.side == Side.OVER }.decimalOdds, 0.0)
        assertEquals(1.746, rec.quotes.single { it.side == Side.UNDER }.decimalOdds, 0.0)
        assertEquals("Ja'Marr Chase", props.single { it.stat == "TOUCHDOWNS" }.subject)
        assertTrue(props.all { it.bookKey == "pinnacle" && it.period == 0 })
    }

    @Test
    fun `without player props the feed isn't asked for them`() = runBlocking {
        server.enqueue(MockResponse().setBody(ExchangeFixtures.pinnapiFootball))
        pinnacle(listOf("w"), emptyList()).odds(nfl, settings.copy(families = setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD)))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("include_specials"))
    }

    @Test
    fun `PinnWire keys go first, pinnapi's when they're spent, and pinnapi is never asked for props`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited","window":"day","limit":100,"retry_after_ms":7200000}"""))
        server.enqueue(MockResponse().setBody(ExchangeFixtures.pinnapiFootball))
        val snap = pinnacle(listOf("w"), listOf("p")).odds(nfl, settings)
        val first = server.takeRequest()
        assertEquals("w", first.getHeader("x-api-key"))
        val second = server.takeRequest()
        assertEquals("p", second.getHeader("x-portal-apikey"))
        assertNull(second.requestUrl!!.queryParameter("include_specials"))
        assertEquals("Dallas Cowboys", snap.events.single().home)
    }

    @Test
    fun `a Pinnacle feed without keys is skipped without a call`() = runBlocking {
        server.enqueue(MockResponse().setBody(ExchangeFixtures.pinnapiFootball))
        pinnacle(emptyList(), listOf("p")).odds(nfl, settings)
        assertEquals(1, server.requestCount)
        assertEquals("p", server.takeRequest().getHeader("x-portal-apikey"))
    }

    @Test
    fun `with no Pinnacle key at all the scan says to add one`() {
        val e = assertThrows(ReferenceException::class.java) { runBlocking { pinnacle(emptyList(), emptyList()).odds(nfl, settings) } }
        assertTrue(e.message!!, e.message!!.contains("PinnWire"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `Pinnacle prop names give the player and Novig's stat`() {
        assertEquals("DJ Moore", PinnacleProps.player("DJ Moore Total Receptions", "Receptions"))
        assertEquals("Bo Bichette", PinnacleProps.player("Bo Bichette Total Bases", "Bases"))
        assertEquals("Josh Allen", PinnacleProps.player("Josh Allen Total Touchdown Passes", "Touchdown Passes"))
        assertNull(PinnacleProps.player("Receptions", "Receptions"))
        assertEquals("TOTAL_BASES", PinnacleProps.stat(6, "Bases"))
        assertEquals("THREE_POINTERS_MADE", PinnacleProps.stat(3, "Threes Made"))
        // Football's "Points" isn't basketball's.
        assertNull(PinnacleProps.stat(5, "Points"))
    }
}
