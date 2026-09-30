package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ParlayAPI's own +EV list at Novig (Tj, 2026-09-30, PARLAY_API.md §6.5): `/best-bets?books=novig` (10 credits a league, only on a tap), its
 * `bet` text read into player, side, line, stat and game, each play turned into the row Novig's catalog and book re-price, and only what's
 * +EV at Novig's real price kept (its first real play listed Novig at +2122 on a +900 fair). Real answers: `parlay-best-bets-*.json`.
 */
class ParlayBestBetsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val now = 1_790_000_000_000L
    private val mlb = Leagues.byNovigName("MLB")!!

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun bestBets(active: Boolean = true) = ParlayBestBets(
        TheOddsApiClient(
            OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter),
            json, baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
        ),
        json, active = { active }, clock = { now },
    )

    @Test
    fun `its bet text reads into player, side, line, stat and game`() {
        assertEquals(
            listOf("Carson Kelly", "Over", "0.5", "Home Runs", "Chicago Cubs", "San Diego Padres"),
            ParlayBestBets.parseBet("Carson Kelly Over 0.5 Home Runs (Chicago Cubs @ San Diego Padres)"),
        )
        assertEquals(
            listOf("Amon-Ra St. Brown", "Under", "64.5", "Receiving Yards", "Detroit Lions", "Green Bay Packers"),
            ParlayBestBets.parseBet("Amon-Ra St. Brown Under 64.5 Receiving Yards (Detroit Lions @ Green Bay Packers)"),
        )
        assertNull(ParlayBestBets.parseBet("New York Yankees -1.5 (Boston Red Sox @ New York Yankees)"))
    }

    @Test
    fun `the real answer's plays and its edge alert become rows at Novig, with ParlayAPI's fair price`() {
        val board = ParlayBestBets.parse(res("parlay-best-bets-mlb.json"), json, mlb, now)!!
        assertEquals(6, board.plays.size)
        val kelly = board.plays.first()
        assertEquals("Carson Kelly", kelly.player)
        assertTrue(kelly.over)
        assertEquals(0.5, kelly.line, 0.0)
        assertEquals("Chicago Cubs @ San Diego Padres", kelly.event)
        assertEquals(900, kelly.fairAmerican)
        assertEquals(2122, kelly.listedAmerican)
        assertEquals("BET", kelly.verdict)
        assertNotNull(kelly.stat)
        assertFalse(kelly.alert)
        val row = kelly.row()
        assertEquals("Carson Kelly Over 0.5", row.bet)
        assertEquals("MLB", row.league)
        assertEquals("Novig", row.book)
        assertEquals(0.10, row.fairProbability!!, 1e-9)
        assertTrue(row.market.startsWith("Player "))
        assertTrue(kelly.key.startsWith(ParlayPlay.KEY_PREFIX))
        // RBIs read as Novig's stat too.
        assertNotNull(board.plays.first { it.player == "Mauricio Dubon" }.stat)
        // The edge alert: "verify first", its fair price from its edge (probability points over +5163's 1.9%).
        val happ = board.plays.single { it.alert }
        assertEquals("Ian Happ", happ.player)
        assertTrue(happ.caveat!!.contains("verify"))
        assertEquals(0.1250, 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(happ.fairAmerican!!), 0.002)
        // The empty NFL board: nothing, and its summary.
        val empty = ParlayBestBets.parse(res("parlay-best-bets-empty-nfl.json"), json, Leagues.byNovigName("NFL")!!, now)!!
        assertTrue(empty.plays.isEmpty())
        assertTrue(empty.summary!!.startsWith("No +EV plays"))
    }

    @Test
    fun `a play is shown only at Novig's own price, +2122 listed but +850 now is under a +900 fair`() {
        val board = ParlayBestBets.parse(res("parlay-best-bets-mlb.json"), json, mlb, now)!!
        val plays = board.plays.take(2)
        val rows = plays.map { it.row(startsAtMs = now + 3_600_000L) }
        val live = mapOf(
            rows[0].key to LivePrice(american = 850, available = 40.0, ev = -0.05, atMs = now),
            // Seiya Suzuki isn't in Novig's catalog: no price.
        )
        val picks = ParlayPick.priced(plays, rows, live)
        assertEquals(850, picks[0].row.odds)
        assertEquals(-0.05, picks[0].ev!!, 0.0)
        assertTrue(picks[0].found)
        assertEquals(now + 3_600_000L, picks[0].row.startsAtMs)
        assertFalse(picks[1].found)
        assertNull(picks[1].ev)
    }

    @Test
    fun `a tap reads a league's board at Novig for 10 credits, the answer's credits into the meter, and nothing with ParlayAPI off`() = runTest {
        server.enqueue(MockResponse().setBody(res("parlay-best-bets-mlb.json")))
        val board = bestBets().read(mlb)!!
        assertEquals(6, board.plays.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/sports/baseball_mlb/best-bets", url.encodedPath)
        assertEquals("novig", url.queryParameter("books"))
        assertEquals("3", url.queryParameter("min_books"))
        assertEquals(19_871, meter.flow.value.providers.getValue("parlay").keys.getValue("pk").remaining)
        assertNull(bestBets(active = false).read(mlb))
        // Tennis has no ParlayAPI props: never asked.
        Leagues.ALL.firstOrNull { !ParlayBestBets.supports(it) }?.let { assertNull(bestBets().read(it)) }
        assertEquals(1, server.requestCount)
    }
}
