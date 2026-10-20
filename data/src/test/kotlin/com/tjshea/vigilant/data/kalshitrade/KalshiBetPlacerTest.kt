package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.book.BookRef
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.novig.trading.PlanResult
import com.tjshea.vigilant.data.tracker.BetTracker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** KV3: the Kalshi placer's sequence on a stand-in Kalshi: whole contracts, the cent-rounded fee in the edge, IOC at the judged ask, a lost answer looked up and never resent. */
class KalshiBetPlacerTest {
    private val now = 1_790_500_000_000L
    private val start = now + 5 * 3_600_000L

    /** Records what the placer sends; answers from the tops and the script. */
    private class Fake : KalshiTradingClient(OkHttpClient(), Json, { null }) {
        var tops: Map<String, KalshiTop> = emptyMap()
        val buys = ArrayList<List<Any?>>()
        var fillAll = true
        var failWith: Exception? = null
        var found: KalshiOrder? = null
        val cancelled = ArrayList<String>()
        override val ready get() = true
        override suspend fun tops(tickers: Collection<String>) = tops.filterKeys { it in tickers }
        override suspend fun buy(ticker: String, outcomeSide: String, price: Double, count: Long, clientOrderId: String, tif: String, postOnly: Boolean, expirationMs: Long?): KalshiPlaced {
            buys += listOf(ticker, outcomeSide, price, count, tif)
            failWith?.let { throw it }
            val f = if (fillAll) count.toDouble() else 0.0
            return KalshiPlaced("o-1", clientOrderId, f, 0.0, if (fillAll) price else null, if (fillAll) KalshiFees.takerFeeDollars(count.toDouble(), price) / count else null)
        }
        override suspend fun order(orderId: String): KalshiOrder? = found ?: KalshiOrder(orderId, null, "T", "yes", if (fillAll) "executed" else "canceled", 0.4, 0.6, buys.lastOrNull()?.get(3).let { (it as? Long)?.toDouble() ?: 0.0 }, if (fillAll) (buys.lastOrNull()?.get(3) as Long).toDouble() else 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null, null)
        override suspend fun fills(sinceMs: Long?, ticker: String?, orderId: String?, limit: Int): List<KalshiFill> =
            if (fillAll && buys.isNotEmpty()) listOf(KalshiFill("f1", orderId ?: "o-1", "T", buys.last()[1] as String, (buys.last()[3] as Long).toDouble(), buys.last()[2] as Double, true, KalshiFees.takerFeeDollars((buys.last()[3] as Long).toDouble(), buys.last()[2] as Double), now)) else emptyList()
        override suspend fun findByClientId(clientOrderId: String, ticker: String): KalshiOrder? = found
        override suspend fun cancel(orderId: String, ticker: String?): Boolean { cancelled += orderId; return true }
    }

    private fun target(fair: Double = 0.46): BetTarget {
        val a = NovigOutcome("ev|ml|AWAY", "Ravens", "TBD", BookRef(null, "KXNFLGAME-26OCT25BALDAL-BAL|yes", null))
        val h = NovigOutcome("ev|ml|HOME", "Cowboys", "TBD", BookRef(null, "KXNFLGAME-26OCT25BALDAL-DAL|yes", null))
        val m = NovigMarket("ev|ml", "ev", "MONEY", "OPEN", "Ravens @ Cowboys MONEY", start, KalshiFees.TAKER, listOf(a, h))
        return BetTarget(m, "ev|ml|AWAY", "NFL", "Ravens @ Cowboys", start, "Moneyline", "Baltimore Ravens", fair, now - 20_000, "vigilant", book = "Kalshi")
    }

    private fun tops(ask: Double = 0.40, size: Double = 500.0, status: String = "active") = mapOf(
        "KXNFLGAME-26OCT25BALDAL-BAL" to KalshiTop("KXNFLGAME-26OCT25BALDAL-BAL", ask - 0.02, ask, 300.0, size, status),
        "KXNFLGAME-26OCT25BALDAL-DAL" to KalshiTop("KXNFLGAME-26OCT25BALDAL-DAL", 0.58, 0.60, 300.0, 300.0, status),
    )

    private val limits = BetLimits.manual(maxStake = 50.0, maxPerDay = 200.0)
    private fun tracker() = BetTracker(File.createTempFile("kalshi-bets", ".json").also { it.delete() }, { now })

    private fun placer(f: Fake, t: BetTracker = tracker(), l: BetLimits = limits) = KalshiBetPlacer(f, t, { l }, clock = { now }, pause = {})

    @Test
    fun `a +EV bet is whole contracts at the ask, with the fee rounded up in its edge, and is logged with the real fill`() = runTest {
        val f = Fake().also { it.tops = tops() }
        val t = tracker()
        val p = placer(f, t)
        val plan = (p.plan(target(), 20.0) as PlanResult.Ready).plan
        // $20 buys 48 contracts at 40c + fee (0.07 x .4 x .6 = 1.68c each): 48 x 0.4168 = 20.0
        assertEquals(4_800L, plan.contracts)
        assertEquals(0.40, plan.limitPrice, 1e-12)
        assertEquals(KalshiFees.takerFeeDollars(48.0, 0.40), plan.feeDollars, 1e-12)
        assertEquals(0.46 / ((48 * 0.40 + plan.feeDollars) / 48) - 1.0, plan.evPercent, 1e-9)
        val placed = p.place(target(), 20.0, plan.limitPrice) as PlaceResult.Placed
        assertEquals(listOf<Any?>("KXNFLGAME-26OCT25BALDAL-BAL", "yes", 0.40, 48L, "immediate_or_cancel"), f.buys.single())
        assertEquals(0.40, placed.bet.price, 1e-9)
        assertEquals(4_800L, placed.bet.contracts)
        assertEquals("Kalshi", placed.bet.book)
        assertEquals(1, t.all().size)
    }

    @Test
    fun `a NO side is ordered as NO at 1 minus the yes bid`() = runTest {
        val f = Fake().also { it.tops = tops(ask = 0.60) }
        val t = target().copy(outcomeId = "ev|ml|AWAY").let { tg ->
            // The Ravens' NO: the same ticker bought as NO (price 1 - yes bid = 0.42).
            val a = NovigOutcome("ev|ml|AWAY", "Ravens", "TBD", BookRef(null, "KXNFLGAME-26OCT25BALDAL-BAL|no", null))
            tg.copy(market = tg.market.copy(outcomes = listOf(a, tg.market.outcomes[1])), fair = 0.55)
        }
        val plan = (placer(f).plan(t, 10.0) as PlanResult.Ready).plan
        assertEquals(0.42, plan.limitPrice, 1e-12)
        placer(f).place(t, 10.0, plan.limitPrice)
        assertEquals("no", f.buys.single()[1])
        assertEquals(0.42, f.buys.single()[2] as Double, 1e-12)
    }

    @Test
    fun `a negative EV price is never ordered, a closed market is refused, and the words say Kalshi`() = runTest {
        val f = Fake().also { it.tops = tops(ask = 0.60) }
        val r = placer(f).plan(target(fair = 0.46), 10.0) as PlanResult.Refused
        assertTrue(r.negativeEv)
        assertTrue(r.reason, r.reason.contains("Kalshi") && !r.reason.contains("Novig"))
        f.tops = tops(status = "closed")
        val closed = placer(f).plan(target(), 10.0) as PlanResult.Refused
        assertTrue(closed.reason, closed.reason.contains("closed"))
        assertTrue(f.buys.isEmpty())
    }

    @Test
    fun `a stake under one contract, or one the cent rounding makes negative, is refused with why`() = runTest {
        val f = Fake().also { it.tops = tops() }
        val tiny = placer(f).plan(target(), 0.30) as PlanResult.Refused
        assertTrue(tiny.reason, tiny.reason.contains("less than one Kalshi contract"))
        // One contract at 40c: model fee 1.68c rounds to 2c: edge 0.46/0.42 - 1 = 9.5%, still fine by hand; an auto-bet that wants 12% is refused for the rounding.
        val auto = limits.copy(manual = false, minEv = 0.12)
        val r = placer(f, l = auto).plan(target(fair = 0.4685), 0.45, limitsOverride = auto)
        assertTrue(r.toString(), r is PlanResult.Refused)
    }

    @Test
    fun `an IOC that matched nothing is not a bet`() = runTest {
        val f = Fake().also { it.tops = tops(); it.fillAll = false }
        val t = tracker()
        val r = placer(f, t).place(target(), 10.0, 0.40)
        assertTrue(r.toString(), r is PlaceResult.NotFilled)
        assertTrue(t.all().isEmpty())
    }

    @Test
    fun `a lost answer is looked up by its client id and the order is never sent again`() = runTest {
        val f = Fake().also { it.tops = tops(); it.failWith = KalshiApiException(0, "network", "timeout") }
        val r = placer(f).place(target(), 10.0, 0.40)
        assertTrue(r.toString(), r is PlaceResult.Unconfirmed)
        assertEquals(1, f.buys.size)
        // Found on the lookup: followed to its end and logged.
        f.found = KalshiOrder("o-9", null, "T", "yes", "executed", 0.4, 0.6, 24.0, 24.0, 0.0, 9.6, 0.0, 0.4, 0.0, null, null)
        val t = tracker()
        val again = placer(f, t).place(target(), 10.0, 0.40)
        assertTrue(again.toString(), again is PlaceResult.Placed)
        assertEquals(2, f.buys.size)
        assertEquals(1, t.all().size)
    }

    @Test
    fun `the same bet is not placed twice while the first is open`() = runTest {
        val f = Fake().also { it.tops = tops() }
        val t = tracker()
        val p = placer(f, t)
        assertTrue(p.place(target(), 10.0, 0.40) is PlaceResult.Placed)
        val second = p.plan(target(), 10.0)
        assertTrue(second.toString(), second is PlanResult.Refused && second.reason.contains("already bet"))
        assertNull((p.plan(target(), 10.0, allowRepeat = true) as? PlanResult.Refused))
    }

    @Test
    fun `a price that moved above what the sheet showed is refused`() = runTest {
        val f = Fake().also { it.tops = tops(ask = 0.42) }
        val r = placer(f).place(target(), 10.0, 0.40)
        assertTrue(r.toString(), r is PlaceResult.Refused && r.reason.contains("moved"))
        assertTrue(f.buys.isEmpty())
    }
}
