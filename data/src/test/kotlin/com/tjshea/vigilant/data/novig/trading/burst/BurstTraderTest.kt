package com.tjshea.vigilant.data.novig.trading.burst

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.burst.Cover
import com.tjshea.vigilant.data.novig.burst.CoverMath
import com.tjshea.vigilant.data.novig.burst.Ladders
import com.tjshea.vigilant.data.novig.burst.WindowOpening
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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

/**
 * The real-money burst trader on a fake order port and virtual time (RESEARCH.md §95): what it sends, what it refuses, and when it stops itself. The cover is the one of the recorder's tests
 * (moneyline YES at 0.539, the -1.5 spread NOT at 0.435: 25,000 on offer, a net of 1.1 cents a contract after both in-play fees).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BurstTraderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val base = 1_790_000_000_000L

    private fun market(id: String, type: String, a: String, b: String, strike: Double?) =
        NovigMarket(id, "ev1", type, "OPEN", "d", 0L, MarketFee.GAME, listOf(NovigOutcome("$id-a", a, "TBD"), NovigOutcome("$id-b", b, "TBD")), strike)

    private val ml = Ladders.line(market("ml", "MONEY", "ATL", "NO", 0.0))!!
    private val sp = Ladders.line(market("s1", "SPREAD", "ATL -1.5", "NO +1.5", 1.5))!!

    private fun book(id: String, aBids: List<Pair<Int, Long>> = emptyList(), bBids: List<Pair<Int, Long>> = emptyList()) =
        NovigBook(id, 1L, mapOf("$id-a" to aBids.map { BidLevel(it.first, it.second) }, "$id-b" to bBids.map { BidLevel(it.first, it.second) }), 0L)

    private fun cover(noBid: Int = 565, yesBidOnNo: Int = 461, depth: Long = 25_000L): Cover =
        CoverMath.cover(ml, book("ml", bBids = listOf(yesBidOnNo to 40_000L)), sp, book("s1", aBids = listOf(noBid to depth)))!!

    private class Placed(val order: NovigTradingClient.NewOrder)

    /** An order port that fills what a script says and keeps every order it was given. */
    private class FakeOrders : BurstOrders {
        val batches = ArrayList<List<NovigTradingClient.NewOrder>>()
        var fillOf: (NovigTradingClient.NewOrder, Int) -> Long = { o, _ -> o.qty }
        var placeFailure: Throwable? = null
        var neverEnds = false
        private val byId = HashMap<String, Pair<NovigTradingClient.NewOrder, Long>>()
        private var n = 0
        override suspend fun placeBatch(orders: List<NovigTradingClient.NewOrder>): Map<String, String> {
            batches += orders
            placeFailure?.let { throw it }
            return orders.associate { o -> val id = "ord-${++n}"; byId[id] = o to fillOf(o, batches.size); o.clientId to id }
        }
        override suspend fun order(orderId: String): NovigOrder? {
            val (o, filled) = byId[orderId] ?: return null
            if (neverEnds) return NovigOrder(orderId, o.clientId, "m", o.outcomeId, o.price, o.qty, o.qty, "IOC", "OPEN", 0L)
            return NovigOrder(orderId, o.clientId, "m", o.outcomeId, o.price, o.qty, o.qty - filled, "IOC", if (filled == o.qty) "FILLED" else "CANCELED", 0L)
        }
        override suspend fun fills(orderId: String): List<NovigFill> {
            val (o, filled) = byId[orderId] ?: return emptyList()
            if (filled == 0L) return emptyList()
            val cost = filled * o.price * 0.01
            return listOf(NovigFill("f-$orderId", orderId, o.clientId, "m", o.outcomeId, filled, cost, true, 0.03 * o.price * (1 - o.price) * filled * 0.01, 0L))
        }
    }

    private class Rig(val scope: TestScope, val orders: FakeOrders, val trader: BurstTrader, val journal: BurstTradeJournal, val halts: MutableList<String>, var rules: BurstTradeRules, var gate: String?, val own: MutableList<OwnBid>) {
        fun window(c: Cover, openedAgoMs: Long = 0L, current: Cover? = c) = WindowOpening("NFL", "ev1", "A @ B", c, 1_790_000_000_000L + scope.currentTime - openedAgoMs) { current }
    }

    private val on = BurstTradeRules(enabled = true, stakePerLeg = 1.0, maxPerGame = 5.0, maxPerDay = 10.0, haltLoss = 3.0)

    private fun TestScope.rig(rules: BurstTradeRules = on): Rig {
        val orders = FakeOrders()
        val halts = ArrayList<String>()
        val own = ArrayList<OwnBid>()
        lateinit var r: Rig
        var counter = 0
        val trader = BurstTrader(
            orders = orders, scope = backgroundScope, rules = { r.rules }, gate = { r.gate }, ownBids = { own }, journal = BurstTradeJournal(tmp.newFolder("t" + System.nanoTime())),
            onHalt = { halts += it }, clock = { base + currentTime }, dayStart = { base - 3_600_000L }, newClientId = { java.util.UUID.nameUUIDFromBytes("c${++counter}".toByteArray()).toString() },
        )
        r = Rig(this, orders, trader, BurstTradeJournal(tmp.newFolder("j" + System.nanoTime())), halts, rules, null, own)
        return r
    }

    private fun Rig.send(w: WindowOpening) { trader.onOpen(w) }

    /** Lets the attempt run, then moves on past the 2 s a ladder is left alone after one (so a test of several skips doesn't just see the cooldown). */
    private fun TestScope.step(r: Rig) { runCurrent(); advanceTimeBy(BurstTradeLimits.COOLDOWN_MS + 1); runCurrent() }

    // ---- off, and what stops it before an order ---------------------------------------------------------------------------------

    @Test
    fun `off by default and off when the rules say so - nothing is ever sent, not even counted`() = runTest {
        val r = rig(rules = on.copy(enabled = false))
        r.send(r.window(cover())); runCurrent()
        assertTrue(r.orders.batches.isEmpty())
        assertTrue(r.trader.status.value.skipped.isEmpty())
    }

    @Test
    fun `each reason to hold back is a skip with a name - the gate, a halt, an old window, a cover that no longer pays, a thin net, an own bid, the caps, a cooldown`() = runTest {
        val r = rig()
        r.gate = "STOP ALL is on"
        r.send(r.window(cover())); step(r)
        r.gate = null
        r.rules = on.copy(halted = "an order's answer was lost")
        r.send(r.window(cover())); step(r)
        r.rules = on
        r.send(r.window(cover(), openedAgoMs = 400)); step(r)
        r.send(r.window(cover(), current = null)); step(r)
        r.rules = on.copy(minNet = 0.05)
        r.send(r.window(cover())); step(r)
        r.rules = on
        r.own += OwnBid("ml", "ml-b", 0.47)      // a bid of ours on the NO side of the moneyline at 0.47 >= 1 - 0.539: buying YES at 0.539 would trade with it
        r.send(r.window(cover())); step(r)
        r.own.clear()
        r.rules = on.copy(maxPerDay = 0.5)
        r.send(r.window(cover())); step(r)
        assertTrue("nothing was sent", r.orders.batches.isEmpty())
        val s = r.trader.status.value.skipped
        assertEquals(setOf("STOP ALL is on", "halted", "window too old", "no longer pays", "net under the minimum", "own bid in the way", "limit reached"), s.keys)
        assertTrue(s.values.all { it == 1 })
    }

    @Test
    fun `an own bid on the same side is no clash, only one that could trade against the order`() = runTest {
        val r = rig()
        r.own += OwnBid("ml", "ml-a", 0.60)      // a bid on YES itself: buying YES doesn't trade with it
        r.own += OwnBid("ml", "ml-b", 0.30)      // a bid on NO at 0.30 < 1 - 0.539 = 0.461: below the ask, can't match
        r.send(r.window(cover())); runCurrent()
        assertEquals(1, r.orders.batches.size)
    }

    // ---- both legs ----------------------------------------------------------------------------------------------------------------

    @Test
    fun `both legs go in ONE batch of two IOC orders at the seen asks, sized to the stake, and both filling is a lock with its profit`() = runTest {
        val r = rig()
        r.send(r.window(cover())); runCurrent()
        assertEquals("one request", 1, r.orders.batches.size)
        val (yes, no) = r.orders.batches.single()
        assertEquals("ml-a", yes.outcomeId); assertEquals(0.539, yes.price, 1e-9)
        assertEquals("s1-b", no.outcomeId); assertEquals(0.435, no.price, 1e-9)
        assertEquals("IOC", yes.tif); assertEquals("IOC", no.tif)
        // $1 a leg: 1 / (0.539 x 0.01) = 185 contracts on the dearer leg; both legs the same size.
        assertEquals(185L, yes.qty); assertEquals(185L, no.qty)
        assertTrue("distinct client ids, each a UUID", yes.clientId != no.clientId && NovigTradingClient.isUuid(yes.clientId) && NovigTradingClient.isUuid(no.clientId))
        val rec = r.trader.status.value
        assertEquals(1, rec.locked)
        // 185 contracts pay 185 cents; the legs cost 185 x (0.539 + 0.435) cents + fees.
        val fees = 185 * 0.01 * (0.03 * 0.539 * 0.461 + 0.03 * 0.435 * 0.565)
        assertEquals(185 * 0.01 - 185 * 0.01 * 0.974 - fees, rec.lockedProfit, 1e-9)
        assertEquals(0.0, rec.nakedCost, 0.0)
        assertTrue(rec.last.orEmpty(), rec.last.orEmpty().startsWith("LOCKED"))
        assertTrue("it will not fire again at once: the cooldown", run { r.send(r.window(cover())); runCurrent(); r.trader.status.value.skipped["cooldown"] == 1 })
        assertEquals(1, r.orders.batches.size)
    }

    @Test
    fun `the size is the least of the stake, what is on offer, and what the game's and the day's caps leave`() = runTest {
        val thin = rig().also { it.send(it.window(cover(depth = 120))); runCurrent() }
        assertEquals(120L, thin.orders.batches.single().first().qty)
        val game = rig(rules = on.copy(maxPerGame = 1.0, stakePerLeg = 5.0)).also { it.send(it.window(cover())); runCurrent() }
        // $1 for the game, both legs and a 2-cent fee margin: 1 / ((0.539 + 0.435 + 0.02) x 0.01) = 100 contracts.
        assertEquals(100L, game.orders.batches.single().first().qty)
        val tiny = rig(rules = on.copy(stakePerLeg = 0.05)).also { it.send(it.window(cover())); runCurrent() }
        assertTrue("under 20 contracts is not worth an order", tiny.orders.batches.isEmpty())
    }

    @Test
    fun `the day's cap counts what was spent - the second cover finds it used`() = runTest {
        val r = rig(rules = on.copy(maxPerDay = 2.0, maxPerGame = 50.0, stakePerLeg = 1.0))
        r.send(r.window(cover())); runCurrent()
        advanceTimeBy(5_000)
        r.send(r.window(cover())); runCurrent()
        advanceTimeBy(5_000)
        r.send(r.window(cover())); runCurrent()
        // $1 a leg is about $1.8 for the pair: the first fits under $2, the next two don't.
        assertEquals(1, r.orders.batches.size)
        assertTrue(r.trader.status.value.skipped.containsKey("limit reached"))
    }

    // ---- a leg alone --------------------------------------------------------------------------------------------------------------

    @Test
    fun `a leg that fills alone is hedged at the break-even price, once, and a hedge that fills makes it a lock`() = runTest {
        val r = rig()
        r.orders.fillOf = { o, batch -> if (o.outcomeId == "s1-b" && batch == 1) 0L else o.qty }   // the spread's NOT never fills in the first batch
        r.send(r.window(cover())); runCurrent()
        assertEquals("the batch, then one hedge", 2, r.orders.batches.size)
        val hedge = r.orders.batches[1].single()
        assertEquals("the missing leg", "s1-b", hedge.outcomeId)
        assertEquals(185L, hedge.qty)
        assertEquals("IOC", hedge.tif)
        // break-even: 1 - 0.539 - p - fees >= 0  =>  p about 0.445.
        assertTrue("limit ${hedge.price}", hedge.price in 0.43..0.46)
        assertEquals(1, r.trader.status.value.locked)
        assertTrue(r.halts.isEmpty())
    }

    @Test
    fun `a hedge that fails leaves a leg held alone - counted, and two covers in a row halt the trader until Tj resumes it`() = runTest {
        val r = rig(rules = on.copy(haltLoss = 100.0))
        r.orders.fillOf = { o, _ -> if (o.outcomeId == "s1-b") 0L else o.qty }
        r.send(r.window(cover())); runCurrent()
        assertEquals(1, r.trader.status.value.naked)
        assertTrue(r.trader.status.value.nakedCost > 0.9)
        assertTrue("one is not a halt", r.halts.isEmpty())
        advanceTimeBy(5_000)
        r.send(r.window(cover())); runCurrent()
        assertEquals(2, r.trader.status.value.naked)
        assertEquals(1, r.halts.size)
        assertTrue(r.halts.single(), r.halts.single().contains("2 covers in a row") && r.halts.single().contains("Resume"))
        // Halted: nothing more, until the setting is cleared and it is resumed.
        r.rules = on.copy(halted = r.halts.single())
        advanceTimeBy(60_000)
        val sent = r.orders.batches.size
        r.send(r.window(cover())); runCurrent()
        assertEquals(sent, r.orders.batches.size)
        r.rules = on.copy(haltLoss = 100.0)
        r.trader.resumed()
        advanceTimeBy(60_000)
        r.orders.fillOf = { o, _ -> o.qty }
        r.send(r.window(cover())); runCurrent()
        assertEquals("trading again after Resume", sent + 1, r.orders.batches.size)
    }

    @Test
    fun `legs held alone that cost more than Tj's limit in a day halt it even if they were not in a row`() = runTest {
        val r = rig(rules = on.copy(haltLoss = 0.5))
        r.orders.fillOf = { o, _ -> if (o.outcomeId == "s1-b") 0L else o.qty }
        r.send(r.window(cover())); runCurrent()
        assertEquals(1, r.halts.size)
        assertTrue(r.halts.single(), r.halts.single().contains("held alone today cost") && r.halts.single().contains("limit"))
    }

    @Test
    fun `a partial lock locks what both legs filled and holds the rest alone`() = runTest {
        val r = rig(rules = on.copy(haltLoss = 100.0))
        r.orders.fillOf = { o, b -> if (b == 1 && o.outcomeId == "s1-b") 100L else if (b == 1) o.qty else 0L }   // NO fills 100 of 185; the hedge buys nothing
        r.send(r.window(cover())); runCurrent()
        val s = r.trader.status.value
        assertEquals(1, s.partial)
        assertTrue(s.last.orEmpty(), s.last.orEmpty().contains("100 locked") && s.last.orEmpty().contains("85 held alone"))
        assertTrue(s.lockedProfit > 0.0)
    }

    // ---- Novig says no ------------------------------------------------------------------------------------------------------------

    @Test
    fun `a market Novig refuses in play is left alone for ten minutes, and the account's own refusal stands the trader down`() = runTest {
        val r = rig()
        r.orders.placeFailure = NovigApiException(400, "NOT_LIVE_TRADABLE", "not tradable in play")
        r.send(r.window(cover())); runCurrent()
        assertEquals(1, r.trader.status.value.refused)
        advanceTimeBy(5_000)
        r.send(r.window(cover())); runCurrent()
        assertEquals("the market is barred: no second order", 1, r.orders.batches.size)
        assertEquals(1, r.trader.status.value.skipped["market refused lately"])
        advanceTimeBy(11 * 60_000L)
        r.orders.placeFailure = null
        r.send(r.window(cover())); runCurrent()
        assertEquals("ten minutes on it tries again", 2, r.orders.batches.size)
        // 451: the network is judged, nothing is sent for a while.
        val s = rig()
        s.orders.placeFailure = NovigApiException(451, "ANONYMIZED_NETWORK", null)
        s.send(s.window(cover())); runCurrent()
        s.orders.placeFailure = null
        advanceTimeBy(5_000)
        s.send(s.window(cover())); runCurrent()
        assertEquals(1, s.orders.batches.size)
        assertEquals(1, s.trader.status.value.skipped["stood down"])
        assertNotNull(s.trader.status.value.standDownUntilMs)
    }

    @Test
    fun `an answer that never came halts it, and nothing is assumed - no hedge, no second try`() = runTest {
        val r = rig()
        r.orders.placeFailure = IOException("timeout")
        r.send(r.window(cover())); runCurrent()
        assertEquals(1, r.halts.size)
        assertTrue(r.halts.single(), r.halts.single().contains("answer was lost"))
        assertEquals(1, r.orders.batches.size)
        val line = BurstTradeJournal(java.io.File(tmp.root, "none")).readAll()
        assertTrue(line.isEmpty())
        assertEquals(0, r.trader.status.value.attempts)
    }

    @Test
    fun `an order that has not ended in time halts it - an IOC ends at once, so something is wrong`() = runTest {
        val r = rig()
        r.orders.neverEnds = true
        r.send(r.window(cover())); advanceTimeBy(3_000); runCurrent()
        assertEquals(1, r.halts.size)
        assertTrue(r.halts.single(), r.halts.single().contains("had not ended"))
    }

    // ---- the math and the journal ------------------------------------------------------------------------------------------------

    @Test
    fun `the break-even price is the dearest at which the other leg still leaves the cover paying nothing after both fees`() = runTest {
        val r = rig()
        val c = cover()
        val p = r.trader.breakEven(0.539, c, heldYes = true)!!
        val net = 1.0 - 0.539 - p - 0.03 * 0.539 * 0.461 - 0.03 * p * (1 - p)
        assertTrue("net at the limit is not negative: $net", net >= 0.0)
        val one = p + 0.001
        assertTrue("one tick dearer is a loss", 1.0 - 0.539 - one - 0.03 * 0.539 * 0.461 - 0.03 * one * (1 - one) < 0.0)
        assertNull("a leg held at 0.99 leaves no price that pays", r.trader.breakEven(0.99, c, heldYes = true))
    }

    @Test
    fun `every attempt is journaled with what it sent and what filled, and the journal survives a restart`() = runTest {
        val dir = tmp.newFolder("jj")
        val orders = FakeOrders()
        val journal = BurstTradeJournal(dir)
        val trader = BurstTrader(orders, backgroundScope, { on }, { null }, { emptyList() }, journal, { }, { base + currentTime }, { base - 3_600_000L })
        trader.onOpen(WindowOpening("NFL", "ev1", "A @ B", cover(), base + currentTime) { cover() })
        runCurrent()
        val back = BurstTradeJournal(dir).readAll()
        assertEquals(1, back.size)
        with(back.single()) {
            assertEquals("LOCKED", outcome); assertEquals(185L, contracts); assertEquals(185L, yesFilled); assertEquals(185L, noFilled)
            assertEquals("ML ATL YES / Spr ATL -1.5 NOT", pair); assertTrue(lockedProfit > 0.0); assertEquals(0L, nakedContracts)
        }
        // A new trader on the same journal knows what was spent today: its caps carry over a restart.
        val again = BurstTrader(FakeOrders(), backgroundScope, { on.copy(maxPerDay = 1.0) }, { null }, { emptyList() }, BurstTradeJournal(dir), { }, { base + currentTime + 5_000 }, { base - 3_600_000L })
        again.onOpen(WindowOpening("NFL", "ev1", "A @ B", cover(), base + currentTime + 5_000) { cover() })
        runCurrent()
        assertEquals(1, again.status.value.skipped["limit reached"])
    }

    @Test
    fun `the trader never rests or cancels anything - every order it can send is IOC`() {
        val src = java.io.File("src/main/kotlin/com/tjshea/vigilant/data/novig/trading/burst/BurstTrader.kt").readText()
        val code = src.lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }.joinToString("\n")
        assertFalse(code.contains("\"GTC\"") || code.contains("\"GTT\"") || code.contains("\"PO\"") || code.contains("\"FOK\""))
        assertFalse(code.contains("cancelOrder") || code.contains("cancelOrders"))
        assertEquals("every order is built with tif IOC", 3, Regex("\"IOC\"").findAll(code).count())
    }
}
