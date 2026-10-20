package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerStore
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** KV4: the unchanged Novig bid desk, handed a Kalshi client in Novig's tongue, posts a post-only GTC bid on Kalshi under the fair, in whole contracts on the cent grid, and does not post it twice. */
class KalshiBidRunnerTest {
    private val now = 1_790_500_000_000L
    private val start = now + 3 * 3_600_000L
    private val nfl: League = Leagues.ALL.first { it.oddsApiSportKey == "americanfootball_nfl" }

    private class Fake(override val id: String, override val displayName: String, val snap: RefSnapshot) : ReferenceSource {
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = snap
    }

    private fun ev(id: String, book: String, away: Double, home: Double, ids: Boolean = false) = RefEvent(
        id, nfl.oddsApiSportKey, start, "Dallas Cowboys", "Baltimore Ravens",
        listOf(
            RefBookMarket(
                book, book, LineKind.MONEYLINE,
                listOf(
                    RefQuote(Side.AWAY, 1 / away, null, if (ids) "KXNFLGAME-26OCT25BALDAL-BAL|yes" else null, if (ids) 800.0 else null),
                    RefQuote(Side.HOME, 1 / home, null, if (ids) "KXNFLGAME-26OCT25BALDAL-DAL|yes" else null, if (ids) 800.0 else null),
                ),
                now - 2_000,
            ),
        ),
    )

    // Pinnacle and Circa say the Ravens are 43.5%; Kalshi sells them at 44c (no +EV to take, room for a bid under the fair).
    private fun sources() = listOf(
        Fake("pinnacle", "Pinnacle", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("p", "pinnacle", 0.435, 0.575)), now, provider = "pinnacle")),
        Fake("oddspapi", "Circa", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("c", "circa", 0.436, 0.574)), now, provider = "oddspapi")),
        Fake(KalshiClient.VENUE_ID, "Kalshi", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("kalshi:26OCT25BALDAL", "kalshi", 0.44, 0.61, ids = true)), now, provider = KalshiClient.VENUE_ID)),
    )

    private val settings = ScanSettings(
        leagues = setOf("NFL"), fairSource = FairSource.SHARP, devigMethod = DevigMethod.MULTIPLICATIVE, sharpBooks = setOf("pinnacle", "circa"), minBooks = 1,
        usePolymarket = false, useKalshi = false, useOddsApi = false, sgoPro = false, oddsPapi = true,
        maker = true, makerKinds = BetKind.entries.toSet(), trapEarlyHours = 0, trapFirstListed = false, sharpAutoBet = SharpMode.OFF, apiMaxPerDay = 100.0, apiMaxPerGame = 0.0,
        makerMargin = 0.03, makerStake = 5.0,
    )

    private class Fake2(val now: Long) : KalshiTradingClient(OkHttpClient(), Json, { null }) {
        val buys = ArrayList<List<Any?>>()
        val resting = ArrayList<KalshiOrder>()
        override val ready get() = true
        override suspend fun buy(ticker: String, outcomeSide: String, price: Double, count: Long, clientOrderId: String, tif: String, postOnly: Boolean, expirationMs: Long?): KalshiPlaced {
            buys += listOf(ticker, outcomeSide, price, count, tif, postOnly, expirationMs)
            resting += KalshiOrder("o-${buys.size}", clientOrderId, ticker, outcomeSide, "resting", if (outcomeSide == "yes") price else 1 - price, if (outcomeSide == "yes") 1 - price else price, count.toDouble(), 0.0, count.toDouble(), 0.0, 0.0, 0.0, 0.0, now, expirationMs)
            return KalshiPlaced("o-${buys.size}", clientOrderId, 0.0, count.toDouble(), null, null)
        }
        override suspend fun orders(status: String?, ticker: String?, limit: Int): List<KalshiOrder> = resting.filter { status == null || it.status == status }
        override suspend fun order(orderId: String): KalshiOrder? = resting.firstOrNull { it.orderId == orderId }
        override suspend fun fills(sinceMs: Long?, ticker: String?, orderId: String?, limit: Int): List<KalshiFill> = emptyList()
        override suspend fun cancel(orderId: String, ticker: String?): Boolean { resting.removeAll { it.orderId == orderId }; return true }
        override suspend fun balance() = KalshiBalance(100.0, 0.0)
    }

    private suspend fun result(): ScanResult = KalshiScanner.create { now }.scan(settings, sources()).result!!

    private fun runner(client: Fake2): KalshiBidRunner {
        val outcomes = KalshiOutcomes()
        val tracker = BetTracker(File.createTempFile("kalshi-bids-bets", ".json").also { it.delete() }, { now })
        val store = MakerStore(File.createTempFile("kalshi-bids", ".json").also { it.delete() })
        val desk = MakerDesk(KalshiAsNovig(client, outcomes) { now }, tracker, store, clock = { now }, pause = {}, cancelStrays = false)
        return KalshiBidRunner(desk, outcomes, cash = { 100.0 }, clock = { now })
    }

    @Test
    fun `a Kalshi bid is a post-only GTC under the fair, in whole contracts on the cent grid, and is not posted twice`() = runTest {
        val client = Fake2(now)
        val r = runner(client)
        val res = result()
        val st = r.pass(settings, res, stopReason = null)!!
        assertTrue(st.note, client.buys.isNotEmpty())
        val b = client.buys.first()
        assertEquals("KXNFLGAME-26OCT25BALDAL-BAL", b[0])
        assertEquals("yes", b[1])
        val price = b[2] as Double
        assertTrue("under the fair .435, got $price", price < 0.435 && price >= 0.40)
        assertEquals(Math.round(price * 100) / 100.0, price, 1e-12)
        assertEquals("good_till_canceled", b[4])
        assertEquals(true, b[5])
        assertTrue((b[3] as Long) >= 1L)
        assertTrue(b[6] as Long > now)
        val before = client.buys.size
        r.pass(settings, res, stopReason = null)
        assertEquals("the same bid is not posted twice", before, client.buys.size)
    }

    @Test
    fun `a stop takes every bid down`() = runTest {
        val client = Fake2(now)
        val r = runner(client)
        val res = result()
        r.pass(settings, res, null)
        assertTrue(client.resting.isNotEmpty())
        r.stop("test")
        assertTrue(client.resting.isEmpty())
    }
}
