package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PinnLiveTraderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val base = 1_791_420_000_000L
    private var now = base

    private val market = NovigMarket("mkt", "ev1", "MONEY", "OPEN", "d", base, MarketFee.GAME, listOf(NovigOutcome("ml-a", "OKC", "TBD"), NovigOutcome("ml-b", "MIL", "TBD")), 0.0)
    private val event = NovigEvent("ev1", "BASKETBALL", "NBA", NovigEvent.STATUS_LIVE, "Milwaukee Bucks @ Oklahoma City Thunder", base)
    private val target = LiveTarget(1, event, market, PinnLineType.MONEYLINE, null, false, mapOf(PinnSide.HOME to "ml-a", PinnSide.AWAY to "ml-b"))
    private val verdict = LiveVerdict.Bet(PinnSide.HOME, fair = 0.57, ask = 0.50, fee = 0.0075, ev = 0.13, move = 0.07, stableMs = 1_000, overround = 0.04, contracts = 10_000, limitPrice = 0.52)

    private fun candidate(outcomeId: String = "ml-a", at: Long = now, v: LiveVerdict.Bet = verdict) = LiveCandidate(
        target, PinnSide.HOME, outcomeId, v, "s;0;m", null,
        BetTarget(market, outcomeId, "NBA", event.description, base, "Moneyline", "Oklahoma City Thunder", v.fair, at, "pinnodds"),
        MarketFee.GAME, "40-38", "{}", at, at - 1_000,
    )

    private class FakeOrders : LiveOrders {
        data class Placed(val outcomeId: String, val price: Double, val qty: Long, val clientId: String)
        val placed = ArrayList<Placed>()
        var failure: Exception? = null
        var status = "FILLED"
        var fillQty: Long? = null
        var terminal = true
        override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String): String {
            placed += Placed(outcomeId, price, qty, clientId)
            failure?.let { throw it }
            return "order-${placed.size}"
        }
        override suspend fun order(orderId: String): NovigOrder? {
            val p = placed.last()
            val filled = if (status == "FILLED") (fillQty ?: p.qty) else 0L
            return NovigOrder(orderId, p.clientId, "mkt", p.outcomeId, p.price, p.qty, if (terminal) 0L else p.qty, "IOC", if (terminal) status else "OPEN", 0L)
        }
        override suspend fun fills(orderId: String): List<NovigFill> {
            val p = placed.last()
            val q = if (status == "FILLED") (fillQty ?: p.qty) else 0L
            return if (q <= 0) emptyList() else listOf(NovigFill("f1", orderId, p.clientId, "mkt", p.outcomeId, q, q * 0.50 * 0.01, true, q * 0.0075 * 0.01, 0L))
        }
    }

    private class Rig(val trader: PinnLiveTrader, val orders: FakeOrders, val journal: DayJournal<LiveRecord>, val halts: MutableList<String>, val logged: MutableList<String>)

    private fun rig(
        rules: LiveTradeRules = LiveTradeRules(enabled = true, bet = true, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0),
        gate: String? = null, ownBids: List<LiveOwnBid> = emptyList(), loss: Double = 0.0, scope: kotlinx.coroutines.CoroutineScope, lock: Mutex? = null, logOk: Boolean = true,
    ): Rig {
        val orders = FakeOrders()
        val journal = DayJournal(tmp.newFolder("j" + System.nanoTime()), "pinn-live", LiveRecord.serializer()) { it.atMs }
        val halts = ArrayList<String>()
        val logged = ArrayList<String>()
        var n = 0
        val trader = PinnLiveTrader(
            orders = orders, scope = scope, rules = { rules }, gate = { gate }, ownBids = { ownBids }, journal = journal, onHalt = { halts += it },
            logFills = { _, id, _ -> logged += id; logOk }, lossToday = { loss }, clock = { now }, dayStart = { base - 3_600_000L }, pause = { now += it },
            newClientId = { "00000000-0000-4000-8000-%012d".format(++n) }, lock = lock,
        )
        return Rig(trader, orders, journal, halts, logged)
    }

    @Test
    fun `paper mode decides and journals but sends nothing`() = runTest {
        val r = rig(LiveTradeRules(enabled = true, bet = false, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0), gate = "STOP ALL is on", scope = backgroundScope)
        val rec = r.trader.attempt(candidate())!!
        assertEquals("PAPER", rec.outcome)
        assertEquals("PAPER", rec.mode)
        assertTrue(r.orders.placed.isEmpty())
        assertEquals("paper ignores the money gate", 1, r.trader.status.value.paper)
        assertEquals(1, r.journal.readAll().size)
    }

    @Test
    fun `a bet is one IOC order sized by the stake at the worst price, and the real fills go to the Tracker`() = runTest {
        val r = rig(scope = backgroundScope)
        val rec = r.trader.attempt(candidate())!!
        val p = r.orders.placed.single()
        assertEquals("ml-a", p.outcomeId)
        assertEquals(0.52, p.price, 0.0)
        // $2 at the worst price 0.52 + its fee 0.03*0.52*0.48 = 0.0075 -> 0.5275 cents a contract.
        assertEquals(379L, p.qty)
        assertEquals("FILLED", rec.outcome)
        assertEquals(379L, rec.filled)
        assertEquals(listOf("order-1"), r.logged)
        assertEquals(1, r.trader.status.value.bets)
        assertTrue(r.trader.status.value.spent > 0.0)
        assertEquals("the move to decision time is journaled", 1_000L, rec.decisionMs)
        assertEquals(rec, r.journal.readAll().single())
    }

    @Test
    fun `nothing filled is a miss, not a bet`() = runTest {
        val r = rig(scope = backgroundScope)
        r.orders.status = "CANCELED"
        val rec = r.trader.attempt(candidate())!!
        assertEquals("MISSED", rec.outcome)
        assertTrue(r.logged.isEmpty())
        assertEquals(1, r.trader.status.value.missed)
        assertEquals(0, r.trader.status.value.bets)
    }

    @Test
    fun `a part fill is recorded as part`() = runTest {
        val r = rig(scope = backgroundScope)
        r.orders.fillQty = 100
        val rec = r.trader.attempt(candidate())!!
        assertEquals("PARTIAL", rec.outcome)
        assertEquals(100L, rec.filled)
    }

    @Test
    fun `a fill the Tracker could not take is said so and kept in the journal`() = runTest {
        val r = rig(scope = backgroundScope, logOk = false)
        val rec = r.trader.attempt(candidate())!!
        assertEquals("FILLED", rec.outcome)
        assertTrue(rec.message.contains("Sync with Novig"))
    }

    @Test
    fun `a market Novig refuses is left alone and 451 stands the whole trader down`() = runTest {
        val r = rig(scope = backgroundScope)
        r.orders.failure = NovigApiException(400, "NOT_LIVE_TRADABLE", null)
        assertEquals("REFUSED", r.trader.attempt(candidate())!!.outcome)
        r.orders.failure = null
        now += LiveTradeLimits.OUTCOME_COOLDOWN_MS + 1
        assertNull(r.trader.attempt(candidate()))
        assertEquals(1, r.trader.status.value.skipped["market refused lately"])
        assertEquals("only the first order was sent", 1, r.orders.placed.size)
        // 451: judged network. Another market is now also stood down.
        val r2 = rig(scope = backgroundScope)
        r2.orders.failure = NovigApiException(451, "ANONYMIZED_NETWORK", null)
        assertEquals("REFUSED", r2.trader.attempt(candidate())!!.outcome)
        r2.orders.failure = null
        now += LiveTradeLimits.OUTCOME_COOLDOWN_MS + 1
        assertNull(r2.trader.attempt(candidate("ml-b")))
        assertEquals(1, r2.trader.status.value.skipped["stood down"])
        assertNotNull(r2.trader.status.value.standDownUntilMs)
    }

    @Test
    fun `an answer that never came halts the trader - nothing is assumed`() = runTest {
        val r = rig(scope = backgroundScope)
        r.orders.failure = IOException("timeout")
        val rec = r.trader.attempt(candidate())!!
        assertEquals("UNCONFIRMED", rec.outcome)
        assertEquals(1, r.halts.size)
        now += LiveTradeLimits.OUTCOME_COOLDOWN_MS + 1
        assertNull("halted until Resume", r.trader.attempt(candidate("ml-b")))
        assertEquals(1, r.trader.status.value.skipped["halted"])
    }

    @Test
    fun `an order that has not ended in time halts it too`() = runTest {
        val r = rig(scope = backgroundScope)
        r.orders.terminal = false
        assertEquals("UNCONFIRMED", r.trader.attempt(candidate())!!.outcome)
        assertEquals(1, r.halts.size)
    }

    @Test
    fun `the same outcome is not tried twice inside the cooldown`() = runTest {
        val r = rig(scope = backgroundScope)
        assertNotNull(r.trader.attempt(candidate()))
        now += 5_000
        assertNull(r.trader.attempt(candidate()))
        assertEquals(1, r.trader.status.value.skipped["cooldown"])
        now += LiveTradeLimits.OUTCOME_COOLDOWN_MS
        assertNotNull(r.trader.attempt(candidate()))
    }

    @Test
    fun `the per-game and per-day caps cut the size and then stop it`() = runTest {
        val r = rig(LiveTradeRules(enabled = true, bet = true, stake = 5.0, maxPerGame = 3.0, maxPerDay = 25.0, haltLoss = 0.0), scope = backgroundScope)
        val first = r.trader.attempt(candidate())!!
        assertTrue("the game cap, not the stake, sized it", first.contracts * 0.005275 <= 3.0 + 1e-9)
        now += LiveTradeLimits.OUTCOME_COOLDOWN_MS + 1
        // $3 cap on the game; what the first spent leaves less than the least order's worth after a few more.
        var tried = 0
        while (tried++ < 6 && r.trader.attempt(candidate()) != null) now += LiveTradeLimits.OUTCOME_COOLDOWN_MS + 1
        assertTrue("limit reached", (r.trader.status.value.skipped["limit reached"] ?: 0) >= 1)
        val spent = r.trader.records().filter { it.mode == "BET" }.sumOf { it.paid + it.feePaid }
        assertTrue("never over the game cap: $spent", spent <= 3.0 + 1e-9)
    }

    @Test
    fun `the gate, a halted setting, STOP and a disabled trader each stop a real bet`() = runTest {
        val gated = rig(gate = "wallet too low", scope = backgroundScope)
        assertNull(gated.trader.attempt(candidate()))
        assertEquals(1, gated.trader.status.value.skipped["wallet too low"])
        val halted = rig(LiveTradeRules(enabled = true, bet = true, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0, halted = "x"), scope = backgroundScope)
        assertNull(halted.trader.attempt(candidate()))
        assertEquals(1, halted.trader.status.value.skipped["halted"])
        val off = rig(LiveTradeRules(enabled = false, bet = true, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0), scope = backgroundScope)
        assertNull(off.trader.attempt(candidate()))
        assertTrue(off.orders.placed.isEmpty())
    }

    @Test
    fun `a resting bid of Vigilant's own that this buy could trade against is a wash and is left alone`() = runTest {
        val r = rig(ownBids = listOf(LiveOwnBid("mkt", "ml-b", 0.50)), scope = backgroundScope)
        assertNull(r.trader.attempt(candidate()))
        assertEquals(1, r.trader.status.value.skipped["own bid in the way"])
        val far = rig(ownBids = listOf(LiveOwnBid("mkt", "ml-b", 0.30)), scope = backgroundScope)
        assertNotNull(far.trader.attempt(candidate()))
    }

    @Test
    fun `a day's loss over Tj's limit halts it before the next order`() = runTest {
        val r = rig(loss = 12.0, scope = backgroundScope)
        assertNull(r.trader.attempt(candidate()))
        assertTrue(r.halts.single().contains("lost"))
        assertTrue(r.orders.placed.isEmpty())
    }

    @Test
    fun `it shares the app's one-order lock and skips rather than waiting`() = runTest {
        val lock = Mutex()
        val r = rig(scope = backgroundScope, lock = lock)
        lock.lock()
        assertNull(r.trader.attempt(candidate()))
        assertEquals(1, r.trader.status.value.skipped["busy"])
        lock.unlock()
        assertNotNull(r.trader.attempt(candidate()))
        assertFalse("the lock is released after an attempt", lock.isLocked)
    }

    @Test
    fun `the source of the code never rests, cancels or amends an order`() {
        val src = java.io.File("src/main/kotlin/com/tjshea/vigilant/data/pinnodds/PinnLiveTrader.kt").takeIf { it.exists() }
            ?: java.io.File("data/src/main/kotlin/com/tjshea/vigilant/data/pinnodds/PinnLiveTrader.kt")
        val text = src.readText().lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/**") }.joinToString("\n")
        for (word in listOf("\"GTC\"", "\"GTT\"", "\"FOK\"", "\"PO\"", "cancelOrder", "cancel(")) assertFalse("$word", text.contains(word))
    }
}
