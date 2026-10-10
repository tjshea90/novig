package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.pinnodds.LiveOrders
import com.tjshea.vigilant.data.pinnodds.LiveOwnBid
import com.tjshea.vigilant.data.pinnodds.LiveRecord
import com.tjshea.vigilant.data.pinnodds.LiveTradeRules
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The tail taker: only the conservative, decided, positive-edge tails; one IOC order; every stop the Pinnodds trader has. */
@OptIn(ExperimentalCoroutinesApi::class)
class TailTakerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val base = 1_791_420_000_000L
    private var now = base

    private val market = NovigMarket("mkt", "ev1", "TOTAL", "OPEN", "Total 53.5", base, MarketFee.GAME, listOf(NovigOutcome("over", "Over 53.5", "TBD"), NovigOutcome("under", "Under 53.5", "TBD")), 0.0)

    private fun cand(rule: String = "cons", fair: Double = 0.95, ask: Double = 0.88, edge: Double = 0.07, outcome: String = "under", contracts: Long = 500L) =
        TailCandidate(rule, "mkt", "ev1", outcome, "Total 53.5", "UNDER", 53.5, ask, fair, edge, contracts, 41.0, 0.20)

    private fun offer(c: TailCandidate = cand()) = TailOffer(c, BetTarget(market, c.outcomeId, "NBA", "A @ B", base, "Total", "Total 53.5 under", c.fair, now, "tail"), MarketFee.GAME, "50-44, 20% left", now)

    private class FakeOrders : LiveOrders {
        data class Placed(val outcomeId: String, val price: Double, val qty: Long, val clientId: String)
        val placed = ArrayList<Placed>()
        var failure: Exception? = null
        var status = "FILLED"
        override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String): String {
            placed += Placed(outcomeId, price, qty, clientId)
            failure?.let { throw it }
            return "order-${placed.size}"
        }
        override suspend fun order(orderId: String): NovigOrder? {
            val p = placed.last()
            return NovigOrder(orderId, p.clientId, "mkt", p.outcomeId, p.price, p.qty, 0L, "IOC", status, 0L)
        }
        override suspend fun fills(orderId: String): List<NovigFill> {
            val p = placed.last()
            return if (status != "FILLED") emptyList() else listOf(NovigFill("f1", orderId, p.clientId, "mkt", p.outcomeId, p.qty, p.qty * 0.88 * 0.01, true, p.qty * 0.003 * 0.01, 0L))
        }
    }

    private class Rig(val taker: TailTaker, val orders: FakeOrders, val journal: DayJournal<LiveRecord>, val halts: MutableList<String>, val logged: MutableList<String>)

    private fun rig(
        rules: LiveTradeRules = LiveTradeRules(enabled = true, bet = true, stake = 1.0, maxPerGame = 3.0, maxPerDay = 10.0, haltLoss = 5.0),
        gate: String? = null, ownBids: List<LiveOwnBid> = emptyList(), loss: Double = 0.0, minEdge: Double = 0.05, scope: kotlinx.coroutines.CoroutineScope, lock: Mutex? = null,
    ): Rig {
        val orders = FakeOrders()
        val journal = DayJournal(tmp.newFolder("j" + System.nanoTime()), "tail-live", LiveRecord.serializer()) { it.atMs }
        val halts = ArrayList<String>()
        val logged = ArrayList<String>()
        var n = 0
        val taker = TailTaker(
            orders = orders, scope = scope, rules = { rules }, minEdge = { minEdge }, gate = { gate }, ownBids = { ownBids }, journal = journal, onHalt = { halts += it },
            logFills = { _, id, _ -> logged += id; true }, lossToday = { loss }, clock = { now }, dayStart = { base - 3_600_000L }, pause = { now += it },
            newClientId = { "00000000-0000-4000-8000-%012d".format(++n) }, lock = lock,
        )
        return Rig(taker, orders, journal, halts, logged)
    }

    @Test
    fun `a decided tail is one IOC order sized by the stake, and the fills go to the Tracker`() = runTest {
        val r = rig(scope = backgroundScope)
        val rec = r.taker.attempt(offer())!!
        val p = r.orders.placed.single()
        assertEquals("under", p.outcomeId)
        assertTrue("the limit is at least the ask and still leaves the minimum edge", p.price >= 0.88 - 1e-9 && 0.95 / (p.price + 0.03 * p.price * (1 - p.price)) - 1.0 >= 0.05 - 1e-9)
        assertTrue("$1 buys about 100 contracts", p.qty in 80L..120L)
        assertEquals("FILLED", rec.outcome)
        assertEquals(listOf("order-1"), r.logged)
        assertEquals(1, r.taker.status.value.bets)
        assertEquals(rec, r.journal.readAll().single())
    }

    @Test
    fun `only the conservative rules, a fair of 92 percent or more, and an edge that is real are bet`() = runTest {
        val r = rig(scope = backgroundScope)
        assertNull(r.taker.attempt(offer(cand(rule = "explore"))))
        assertNull(r.taker.attempt(offer(cand(fair = 0.91, edge = 0.08, outcome = "o2"))))
        assertNull("under his minimum edge", r.taker.attempt(offer(cand(edge = 0.04, outcome = "o3"))))
        assertNull("an edge this large is a wrong game state", r.taker.attempt(offer(cand(edge = 0.45, outcome = "o4"))))
        assertTrue(r.orders.placed.isEmpty())
        val s = r.taker.status.value.skipped
        assertEquals(1, s["not the conservative rules"])
        assertEquals(1, s["edge too large to be real"])
    }

    @Test
    fun `his own minimum edge can be higher than the default`() = runTest {
        val r = rig(minEdge = 0.10, scope = backgroundScope)
        assertNull(r.taker.attempt(offer(cand(edge = 0.07))))
        assertTrue(r.orders.placed.isEmpty())
    }

    @Test
    fun `paper mode decides and journals but sends nothing`() = runTest {
        val r = rig(LiveTradeRules(enabled = true, bet = false, stake = 1.0, maxPerGame = 3.0, maxPerDay = 10.0, haltLoss = 5.0), gate = "STOP ALL is on", scope = backgroundScope)
        val rec = r.taker.attempt(offer())!!
        assertEquals("PAPER", rec.outcome)
        assertTrue(r.orders.placed.isEmpty())
        assertEquals(1, r.taker.status.value.paper)
    }

    @Test
    fun `the same outcome is not tried twice inside a minute, and not at all after a refusal of its market`() = runTest {
        val r = rig(scope = backgroundScope)
        r.taker.attempt(offer())!!
        assertNull(r.taker.attempt(offer()))
        assertEquals(1, r.taker.status.value.skipped["cooldown"])
        now += 61_000L
        r.orders.failure = NovigApiException(400, "ORDER_TOO_SMALL", "too small")
        r.taker.attempt(offer())!!
        now += 61_000L
        assertNull(r.taker.attempt(offer()))
        assertEquals(1, r.taker.status.value.skipped["market refused lately"])
    }

    @Test
    fun `the game cap and the day cap bound the size`() = runTest {
        val r = rig(LiveTradeRules(enabled = true, bet = true, stake = 5.0, maxPerGame = 1.0, maxPerDay = 10.0, haltLoss = 5.0), scope = backgroundScope)
        val rec = r.taker.attempt(offer())!!
        assertTrue("$5 stake but $1 left in the game", rec.contracts in 80L..120L)
        now += 61_000L
        assertNull("the game is at its cap", r.taker.attempt(offer(cand(outcome = "over"))))
        assertEquals(1, r.taker.status.value.skipped["limit reached"])
    }

    @Test
    fun `a lost answer halts it and nothing is assumed`() = runTest {
        val r = rig(scope = backgroundScope)
        r.orders.failure = java.io.IOException("timeout")
        val rec = r.taker.attempt(offer())!!
        assertEquals("UNCONFIRMED", rec.outcome)
        assertEquals(1, r.halts.size)
        now += 1_000L
        assertNull("inside the halt's grace nothing more is sent", r.taker.attempt(offer(cand(outcome = "over"))))
        assertEquals(1, r.taker.status.value.skipped["halted"])
        assertEquals(1, r.orders.placed.size)
    }

    @Test
    fun `a day's loss past his limit halts it before any order`() = runTest {
        val r = rig(loss = 5.0, scope = backgroundScope)
        assertNull(r.taker.attempt(offer()))
        assertTrue(r.orders.placed.isEmpty())
        assertEquals(1, r.halts.size)
    }

    @Test
    fun `an own bid that would trade against the order keeps it out, and a nothing-filled order is a miss`() = runTest {
        val clash = rig(ownBids = listOf(LiveOwnBid("mkt", "over", 0.20)), scope = backgroundScope)
        assertNull(clash.taker.attempt(offer()))
        assertEquals(1, clash.taker.status.value.skipped["own bid in the way"])
        val miss = rig(scope = backgroundScope)
        miss.orders.status = "CANCELED"
        assertEquals("MISSED", miss.taker.attempt(offer())!!.outcome)
        assertEquals(1, miss.taker.status.value.missed)
        assertTrue(miss.logged.isEmpty())
    }

    @Test
    fun `a gate that says no stops a real bet, and the order lock is taken around it`() = runTest {
        val gated = rig(gate = "wallet too low", scope = backgroundScope)
        assertNull(gated.taker.attempt(offer()))
        val lock = Mutex().also { it.tryLock() }
        val busy = rig(scope = backgroundScope, lock = lock)
        assertNull(busy.taker.attempt(offer()))
        assertEquals(1, busy.taker.status.value.skipped["busy"])
    }
}
