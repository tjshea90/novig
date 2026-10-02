package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Freshness
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
import java.time.Instant

/**
 * How old a ParlayAPI quote is (Tj, 2026-10-02: "most odds say 9 minutes old … Is 9 minute old odds still good data?", RESEARCH.md §63). Its
 * /odds docs: "every bookmaker's `last_update` is the freshest of (price-change, no-change verification heartbeat). On hot-cycle sources
 * (Pinnacle, FanDuel) that means a maximum age around the 2-second poll interval, even if the price hasn't moved." The market's own
 * `last_update` is when its price last moved: in its real tennis answer (2026-10-01) every market stamp sat 4-60 minutes behind its book's,
 * books seen 2-50 s before the answer. The Odds API's market stamp is "the last time our system saw odds for that market" and stays.
 */
class ParlayFreshnessTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `a ParlayAPI quote is as old as its book's last sighting, not its price's last move`() {
        val raw = res("parlay-tennis-atp.json")
        // De Minaur (Games): Pinnacle seen at 01:37:07.342 (stale 4 s); its total and spread last moved at 01:15:14.
        val parlay = TheOddsApiClient.parseEvents(raw, json, seenByBook = true).first { it.home.startsWith("Alex De Minaur") }
        val pinnacle = parlay.markets.filter { it.bookKey == "pinnacle" }
        assertEquals(2, pinnacle.size)
        assertTrue(pinnacle.all { it.lastUpdateMs == 1790818627342L })

        // Read as The Odds API's format: the market's own stamp, 22 minutes older.
        val asOddsApi = TheOddsApiClient.parseEvents(raw, json).first { it.home.startsWith("Alex De Minaur") }
        assertTrue(asOddsApi.markets.filter { it.bookKey == "pinnacle" }.all { it.lastUpdateMs == ms("2026-10-01T01:15:14Z") })

        // What it decides: at the answer's own moment (01:37:11), a game 3+ hours off keeps quotes 10 minutes. The market stamp says 22
        // minutes (out of the fair price); the book's says 4 seconds (in it).
        val at = 1790818627342L + 4_000
        val farOff = at + 4 * 3_600_000L
        assertTrue(Freshness.fresh(pinnacle.first().lastUpdateMs, at, farOff))
        assertFalse(Freshness.fresh(asOddsApi.markets.first { it.bookKey == "pinnacle" }.lastUpdateMs, at, farOff))
    }

    @Test
    fun `across the whole answer, no ParlayAPI quote is stamped older than its book was seen`() {
        for (file in listOf("parlay-tennis-atp.json", "parlay-tennis-wta.json")) {
            val raw = res(file)
            val parlay = TheOddsApiClient.parseEvents(raw, json, seenByBook = true)
            val oddsApi = TheOddsApiClient.parseEvents(raw, json)
            val newer = parlay.flatMap { it.markets }.zip(oddsApi.flatMap { it.markets })
            assertTrue(newer.isNotEmpty())
            // Never older than the market's own stamp, and in this answer always newer (every price had sat still a while).
            assertTrue(file, newer.all { (p, o) -> p.lastUpdateMs!! >= o.lastUpdateMs!! })
            assertTrue(file, newer.count { (p, o) -> p.lastUpdateMs!! > o.lastUpdateMs!! } >= newer.size * 9 / 10)
        }
    }

    @Test
    fun `a market stamp newer than its book's wins, and a book with no stamp leaves the market's`() {
        val raw = """[{"id":"e","sport_key":"americanfootball_ncaaf","commence_time":"2026-10-03T19:30:00Z","home_team":"Houston","away_team":"UCF",
            "bookmakers":[
              {"key":"betonline","title":"BetOnline.ag","last_update":"2026-10-02T03:40:00Z","markets":[
                {"key":"totals","last_update":"2026-10-02T03:41:00Z","outcomes":[{"name":"Over","price":1.91,"point":51.5},{"name":"Under","price":1.91,"point":51.5}]}]},
              {"key":"bovada","title":"Bovada","markets":[
                {"key":"totals","last_update":"2026-10-02T03:30:00Z","outcomes":[{"name":"Over","price":1.9,"point":51.5},{"name":"Under","price":1.9,"point":51.5}]}]}
            ]}]"""
        val e = TheOddsApiClient.parseEvents(raw, json, seenByBook = true).single()
        assertEquals(ms("2026-10-02T03:41:00Z"), e.markets.single { it.bookKey == "betonlineag" }.lastUpdateMs)
        assertEquals(ms("2026-10-02T03:30:00Z"), e.markets.single { it.bookKey == "bovada" }.lastUpdateMs)
    }

    // ---- through the clients ------------------------------------------------------------------------------------------------

    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }
    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))

    private fun client(feed: OddsFeed) = TheOddsApiClient(
        httpClient = OkHttpClient(),
        pool = KeyPool(if (feed == OddsFeed.PARLAY) QuotaPolicy.PARLAY else QuotaPolicy.ODDS_API, { listOf("k") }, meter),
        json = json,
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        clock = { 42L },
        minIntervalMs = 0,
        feed = feed,
    )

    private val answer = """[{"id":"e","sport_key":"americanfootball_ncaaf","commence_time":"2026-10-03T19:30:00Z","home_team":"Houston","away_team":"UCF",
        "bookmakers":[{"key":"pinnacle","title":"Pinnacle","last_update":"2026-10-02T03:43:58Z","last_update_ms":1790912638500,"stale_seconds":2.1,"markets":[
          {"key":"totals","last_update":"2026-10-02T03:35:00Z","outcomes":[{"name":"Over","price":1.95,"point":51.5},{"name":"Under","price":1.95,"point":51.5}]},
          {"key":"h2h","last_update":"2026-10-02T03:20:00Z","outcomes":[{"name":"Houston","price":1.8},{"name":"UCF","price":2.1}]}]}]}]"""

    @Test
    fun `ParlayAPI's game lines and a bet's books are dated by the book, The Odds API's by the market`() = runTest {
        server.enqueue(MockResponse().setBody(answer))
        val parlay = client(OddsFeed.PARLAY).fetch("americanfootball_ncaaf", listOf("pinnacle"), listOf("h2h", "totals"))
        assertTrue(parlay.events.single().markets.all { it.lastUpdateMs == 1790912638500L })

        server.enqueue(MockResponse().setBody(answer))
        val oddsApi = client(OddsFeed.ODDS_API).fetch("americanfootball_ncaaf", listOf("pinnacle"), listOf("h2h", "totals"))
        val byKind = oddsApi.events.single().markets.associate { it.kind to it.lastUpdateMs }
        assertEquals(ms("2026-10-02T03:35:00Z"), byKind[LineKind.TOTAL])
        assertEquals(ms("2026-10-02T03:20:00Z"), byKind[LineKind.MONEYLINE])
    }

    @Test
    fun `a decimal or missing millisecond stamp never fails the answer`() {
        val raw = answer.replace("1790912638500", "1790912638500.75")
        assertEquals(1790912638500L, TheOddsApiClient.parseEvents(raw, json, seenByBook = true).single().markets.first().lastUpdateMs)
        val noMs = answer.replace(""","last_update_ms":1790912638500""", "")
        assertEquals(ms("2026-10-02T03:43:58Z"), TheOddsApiClient.parseEvents(noMs, json, seenByBook = true).single().markets.first().lastUpdateMs)
    }
}
