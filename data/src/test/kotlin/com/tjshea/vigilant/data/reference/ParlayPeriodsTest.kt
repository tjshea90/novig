package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * More books' 1st-half lines (Tj, 2026-09-30, PARLAY_API.md §6.7): ParlayAPI's `/live/period_markets?period=1H` read into each book's
 * paired sides (a spread's away −0.5 with its home +0.5, a total's over with its under), for Novig's `SPREAD_1H` / `TOTAL_1H`, and only
 * bought (2 credits) when Novig lists such a market in the league. Real answer: `parlay-period-markets-nfl-1h.json` (one game).
 */
class ParlayPeriodsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-09-30T05:00:00Z").toEpochMilli()
    private val nfl = Leagues.byNovigName("NFL")!!

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun source() = ParlayPeriodSource(
        TheOddsApiClient(
            OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter),
            json, baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
        ),
        json,
    )

    @Test
    fun `each book's two sides pair into one 1st-half line, moneylines left out`() {
        val events = ParlayPeriodSource.parse(res("parlay-period-markets-nfl-1h.json"), json, "americanfootball_nfl", now)
        val game = events.single()
        assertEquals("Cleveland Browns", game.home)
        assertEquals("Pittsburgh Steelers", game.away)
        assertTrue(game.markets.all { it.period == 1 })
        assertTrue(game.markets.none { it.kind == LineKind.MONEYLINE })
        // bet365: Steelers -0.5 (-110) with Browns +0.5 (-110); the line is the home side's number.
        val b365 = game.markets.single { it.bookKey == "bet365" && it.kind == LineKind.SPREAD }
        assertEquals(0.5, b365.line!!, 0.0)
        assertEquals(1.909, b365.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-3)
        assertEquals(-0.5, b365.quotes.single { it.side == Side.AWAY }.point!!, 0.0)
        // FanDuel hangs a different number: its own pair.
        assertEquals(1.5, game.markets.single { it.bookKey == "fanduel" && it.kind == LineKind.SPREAD }.line!!, 0.0)
        // Totals: BetMGM 19.5 over -105 / under -115.
        val mgm = game.markets.single { it.bookKey == "betmgm" && it.kind == LineKind.TOTAL }
        assertEquals(19.5, mgm.line!!, 0.0)
        assertEquals(Side.OVER, mgm.quotes.first().side)
        // Each quote timed by its own age: Pinnacle's rows here were 5 hours old (the scan's freshness rule drops them).
        val pin = game.markets.filter { it.bookKey == "pinnacle" }
        assertTrue(pin.isNotEmpty())
        assertTrue(pin.all { now - it.lastUpdateMs!! > 5 * 3_600_000L })
        assertTrue(game.markets.filter { it.bookKey == "draftkings" }.all { now - it.lastUpdateMs!! < 60_000 })
    }

    @Test
    fun `bought only for football and basketball, only with 1st-half lines on, and only when Novig lists one in the league`() = runTest {
        val src = source()
        assertTrue(src.supports(nfl))
        assertFalse(src.supports(Leagues.byNovigName("MLB")!!))
        assertFalse(src.supports(Leagues.byNovigName("NHL")!!))
        val event = NovigEvent("e1", "FOOTBALL", "NFL", "OPEN_PREGAME", "Pittsburgh Steelers @ Cleveland Browns", now + 86_400_000L)
        fun market(type: String) = NovigMarket("m-$type", "e1", type, "OPEN", "PIT @ CLE $type", now + 86_400_000L, null, emptyList())
        // Novig lists no 1st-half market: nothing spent.
        assertTrue(src.odds(nfl, ScanSettings(), ScanContext(listOf(event), listOf(market("SPREAD")))).events.isEmpty())
        // 1st-half lines switched off: nothing spent.
        val half = ScanContext(listOf(event), listOf(market("SPREAD_1H")))
        assertTrue(src.odds(nfl, ScanSettings(families = setOf(MarketFamily.SPREAD)), half).events.isEmpty())
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setBody(res("parlay-period-markets-nfl-1h.json")).setHeader("x-requests-last", "2"))
        val snap = src.odds(nfl, ScanSettings(), half)
        assertEquals("parlay_1h", snap.provider)
        assertEquals(1, snap.events.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/sports/americanfootball_nfl/live/period_markets", url.encodedPath)
        assertEquals("1H", url.queryParameter("period"))
    }
}
