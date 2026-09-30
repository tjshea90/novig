package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetGrader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * "Second opinion (ParlayAPI, 5 credits)" (Tj, 2026-09-30, PARLAY_API.md §6.4): one bet sent to `/v1/verdict` at its own price, scoped to
 * Novig, the answer's credits recorded into the meter (it sends no credit headers), a busy answer asked once more. Real answers:
 * `parlay-verdict-h2h.json`, `parlay-verdict-prop.json`, `parlay-verdict-busy-503.json`.
 */
class ParlayVerdictTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val now = 1_790_000_000_000L

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        ParlayMarketKeys.clear()
    }

    @After fun tearDown() { server.shutdown() }

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun verdicts(active: Boolean = true) = ParlayVerdicts(
        TheOddsApiClient(
            OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter),
            json, baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
        ),
        json, active = { active },
    )

    private val game = "Pittsburgh Steelers @ Cleveland Browns"

    @Test
    fun `the real answers read, and Vigilant's own EV comes from their fair line, not their edge_pct`() {
        val h2h = Verdict.parse(res("parlay-verdict-h2h.json"), json)!!
        assertEquals("FAIR", h2h.verdict)
        assertEquals(138, h2h.fairAmerican)
        assertEquals(0.42, h2h.fairProbability!!, 1e-9)
        assertEquals("pinnacle", h2h.fairSource)
        assertEquals(133, h2h.bestAmerican)
        assertEquals("novig", h2h.bestBook)
        assertEquals(17, h2h.booksCompared)
        assertEquals(-1.2, h2h.movementPp!!, 1e-9)
        // Novig +133 costs 0.4292 per $1: 0.42 / 0.4292 - 1 = -2.1% (their edge_pct says -0.88, a probability-point gap).
        assertEquals(-0.0214, h2h.evAt(1.0 / 2.33)!!, 1e-3)
        val prop = Verdict.parse(res("parlay-verdict-prop.json"), json)!!
        assertEquals("NO_DATA", prop.verdict)
        assertEquals(106, prop.fairAmerican)
        assertEquals(0.486, prop.fairProbability!!, 1e-9)
        assertTrue(prop.note!!.contains("outside your set"))
        assertNull(Verdict.parse(res("parlay-verdict-busy-503.json"), json))
    }

    @Test
    fun `each kind of bet becomes a query, by the team's full name, and what verdict can't grade doesn't`() {
        val ml = VerdictQuery.of("americanfootball_nfl", game, BetGrader.Pick.Moneyline("CLE"), 131)!!
        assertEquals("h2h", ml.market)
        assertEquals("Cleveland Browns", ml.side)
        assertEquals("Cleveland Browns", ml.home)
        assertEquals("Pittsburgh Steelers", ml.away)
        val spread = VerdictQuery.of("americanfootball_nfl", game, BetGrader.Pick.Spread("Pittsburgh Steelers", -2.5, BetGrader.Period.GAME), -110)!!
        assertEquals("spreads" to "Pittsburgh Steelers", spread.market to spread.side)
        assertEquals(-2.5, spread.line!!, 0.0)
        val total = VerdictQuery.of("americanfootball_nfl", game, BetGrader.Pick.Total(false, 41.5, BetGrader.Period.GAME), -105)!!
        assertEquals(Triple("totals", "under", 41.5), Triple(total.market, total.side, total.line))
        // The first half isn't something /verdict grades.
        assertNull(VerdictQuery.of("americanfootball_nfl", game, BetGrader.Pick.Total(true, 20.5, BetGrader.Period.FIRST_HALF), -110))
        // A prop: ParlayAPI's canonical key for the stat until its own board shows the name it uses.
        val q = { VerdictQuery.of("americanfootball_nfl", game, BetGrader.Pick.Prop("Aaron Rodgers", "PASSING_ATTEMPTS", false, 29.5), -110)!! }
        assertEquals("player_pass_attempts", q().market)
        ParlayMarketKeys.record("americanfootball_nfl", "PASSING_ATTEMPTS", "player_passing_attempts")
        ParlayMarketKeys.record("americanfootball_nfl", "PASSING_ATTEMPTS", "prophetx_player_total_passing_attempts")
        assertEquals("player_passing_attempts", q().market)
        assertEquals(listOf("Aaron Rodgers", "under"), listOf(q().player, q().side))
        assertTrue(q().params().containsAll(listOf("books" to "novig", "line" to "29.5", "price" to "-110", "sharpBook" to "pinnacle")))
    }

    @Test
    fun `a tap asks once, 5 credits, and what the answer says is left goes into the meter`() = runTest {
        server.enqueue(MockResponse().setBody(res("parlay-verdict-prop.json")))
        val r = verdicts().ask(VerdictQuery("americanfootball_nfl", "player_passing_attempts", "under", "Buffalo Bills", "Miami Dolphins", "Josh Allen", 29.5, -110))
        assertEquals("NO_DATA", (r as ParlayVerdicts.Result.Answered).verdict.verdict)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/verdict", url.encodedPath)
        assertEquals("Josh Allen", url.queryParameter("player"))
        assertEquals("novig", url.queryParameter("books"))
        val k = meter.flow.value.providers.getValue("parlay").keys.getValue("pk")
        assertEquals(19_883, k.remaining)
        assertEquals(117, k.used)
    }

    @Test
    fun `a busy board is asked once more, then it says so, and nothing is asked with ParlayAPI off`() = runTest {
        val q = VerdictQuery("americanfootball_nfl", "h2h", "Cleveland Browns", "Cleveland Browns", "Pittsburgh Steelers", price = 131)
        server.enqueue(MockResponse().setResponseCode(503).setBody(res("parlay-verdict-busy-503.json")))
        server.enqueue(MockResponse().setBody(res("parlay-verdict-h2h.json")))
        assertTrue(verdicts().ask(q) is ParlayVerdicts.Result.Answered)
        assertEquals(2, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(503).setBody(res("parlay-verdict-busy-503.json")))
        server.enqueue(MockResponse().setResponseCode(503).setBody(res("parlay-verdict-busy-503.json")))
        assertEquals(ParlayVerdicts.Result.Busy, verdicts().ask(q))
        assertEquals(4, server.requestCount)
        assertEquals(ParlayVerdicts.Result.Off, verdicts(active = false).ask(q))
        assertEquals(4, server.requestCount)
    }
}
