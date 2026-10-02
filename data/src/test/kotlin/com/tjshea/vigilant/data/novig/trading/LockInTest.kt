package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tj, 2026-10-02 ~18:50Z: "if I place a bet early and it significantly shifts a certain way, I could take the other side of the bet later on and guarantee a
 * profit no matter which side of the bet wins … It must guarantee profit because I will put real money on it." RESEARCH.md §67.
 */
class LockInTest {

    private val game = MarketFee(coefficient = 0.03, makerCredit = 0.5, charged = FeeCharge.WHEN_LIVE)
    private val futures = MarketFee(coefficient = 0.06, makerCredit = 0.7, charged = FeeCharge.ALWAYS)

    private fun ready(r: LockResult) = (r as? LockResult.Ready)?.plan ?: throw AssertionError("expected a lock, got $r")

    @Test
    fun `a bet bought at 40 whose other side now sells at 55 locks 50 cents either way`() {
        // 1,000 contracts of A at 0.40 = $4.00 spent; B now 0.55: buy 1,000 B for $5.50; either way the 1,000 winning contracts pay $10.00.
        val p = ready(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.55, 5_000)), game, live = false, pushable = false, minProfit = 0.01))
        assertEquals("B", p.buyOutcomeId)
        assertEquals(1_000L, p.contracts)
        assertEquals(0.55, p.limitPrice, 1e-9)
        assertEquals(0.50, p.ifHeldWins, 1e-9)
        assertEquals(0.50, p.ifOtherWins, 1e-9)
        assertEquals(0.50, p.guaranteed, 1e-9)
        assertTrue(!p.feeCharged)
    }

    @Test
    fun `no lock until the other side is cheap enough, and it says the price that would do it`() {
        // Bought A at 0.50 ($5.00 for 1,000): the other side must cost under 0.50 (49.5 on the grid for a cent's profit).
        val none = LockIn.plan(Held("A", 1_000, 5.00), Held("B", 0, 0.0), listOf(TakeLevel(0.505, 9_999)), game, live = false, pushable = false, minProfit = 0.01)
        none as LockResult.None
        assertEquals(0.495, none.needPrice!!, 1e-9)
        assertTrue(none.reason, none.reason.contains("a lock needs"))
        val yes = ready(LockIn.plan(Held("A", 1_000, 5.00), Held("B", 0, 0.0), listOf(TakeLevel(0.495, 9_999)), game, live = false, pushable = false, minProfit = 0.01))
        assertEquals(0.05, yes.guaranteed, 1e-9)
    }

    @Test
    fun `it takes the cheapest levels first, the limit is the deepest level needed, and the worst case is at the limit`() {
        // 300 at 0.50 and 600 at 0.52 make 900; the last 100 come from 0.58 (0.60 is never reached).
        val ladder = listOf(TakeLevel(0.52, 600), TakeLevel(0.50, 300), TakeLevel(0.60, 10_000), TakeLevel(0.58, 5_000))
        val p = ready(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), ladder, game, live = false, pushable = false, minProfit = 0.01))
        assertEquals(0.58, p.limitPrice, 1e-9)
        // Worst case: all 1,000 at 0.58 = $5.80: $10 − $4 − $5.80 = $0.20 sure. As the book reads: $1.50 + $3.12 + $0.58 = $5.20: $0.80.
        assertEquals(0.20, p.guaranteed, 1e-9)
        assertEquals(5.20, p.expectedCost, 1e-9)
        assertEquals(0.80, p.expected, 1e-9)
    }

    @Test
    fun `a book too thin at a locking price is no lock, never a part lock`() {
        val r = LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.50, 400), TakeLevel(0.70, 10_000)), game, live = false, pushable = false, minProfit = 0.01)
        r as LockResult.None
        assertTrue(r.reason, r.reason.contains("only 400 of the 1000"))
    }

    @Test
    fun `in game the fee is in the worst case, and a line that can push isn't locked while a fee is charged`() {
        val pre = ready(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.55, 5_000)), game, live = false, pushable = false, minProfit = 0.01))
        val live = ready(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.55, 5_000)), game, live = true, pushable = false, minProfit = 0.01))
        // Fee = 0.03 × 0.55 × 0.45 per $1 of payout × $10 of payout = $0.07425.
        assertEquals(pre.guaranteed - 0.07425, live.guaranteed, 1e-9)
        assertTrue(live.feeCharged)
        val pushes = LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.55, 5_000)), game, live = true, pushable = true, minProfit = 0.01)
        assertTrue(pushes is LockResult.None)
        // Pregame, no fee: a push only refunds the bets, so a pushable line still locks (break-even at worst).
        assertTrue(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.55, 5_000)), game, live = false, pushable = true, minProfit = 0.01) is LockResult.Ready)
        // Futures charge always.
        assertTrue(ready(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.55, 5_000)), futures, live = false, pushable = false, minProfit = 0.01)).feeCharged)
        // No fee object: no lock.
        assertTrue(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), listOf(TakeLevel(0.55, 5_000)), null, live = false, pushable = false, minProfit = 0.01) is LockResult.None)
    }

    @Test
    fun `a market already part locked tops up only the difference, and an even one needs nothing`() {
        val p = ready(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 400, 2.00), listOf(TakeLevel(0.50, 5_000)), game, live = false, pushable = false, minProfit = 0.01))
        assertEquals(600L, p.contracts)
        assertEquals(10.0 - 6.0 - 3.0, p.guaranteed, 1e-9)
        assertTrue(LockIn.plan(Held("A", 500, 2.0), Held("B", 500, 2.0), listOf(TakeLevel(0.40, 5_000)), game, live = false, pushable = false, minProfit = 0.01) is LockResult.None)
        // Held on the other side: it buys A.
        assertEquals("A", ready(LockIn.plan(Held("A", 0, 0.0), Held("B", 1_000, 4.00), listOf(TakeLevel(0.55, 5_000)), game, live = false, pushable = false, minProfit = 0.01)).buyOutcomeId)
    }

    @Test
    fun `the minimum profit raises the price needed`() {
        val ladder = listOf(TakeLevel(0.55, 5_000))
        assertTrue(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), ladder, game, live = false, pushable = false, minProfit = 0.50) is LockResult.Ready)
        assertTrue(LockIn.plan(Held("A", 1_000, 4.00), Held("B", 0, 0.0), ladder, game, live = false, pushable = false, minProfit = 0.51) is LockResult.None)
    }

    @Test
    fun `property - no plan can pay less than the minimum whichever side wins, however it fills at or under the limit, or on a fair-value void`() {
        val rnd = Random(20261002)
        var locks = 0
        repeat(20_000) {
            val heldA = rnd.nextLong(1, 20_000)
            val priceA = LockIn.GRID_DESC[rnd.nextInt(LockIn.GRID_DESC.size)]
            val spentA = heldA * 0.01 * priceA * (1 + rnd.nextDouble() * 0.02)
            val heldB = if (rnd.nextInt(4) == 0) rnd.nextLong(0, heldA) else 0L
            val spentB = heldB * 0.01 * LockIn.GRID_DESC[rnd.nextInt(LockIn.GRID_DESC.size)]
            val ladder = List(rnd.nextInt(0, 5)) { TakeLevel(LockIn.GRID_DESC[rnd.nextInt(LockIn.GRID_DESC.size)], rnd.nextLong(1, 30_000)) }
            val fee = if (rnd.nextBoolean()) game else futures
            val live = rnd.nextBoolean()
            val minProfit = listOf(0.01, 0.05, 0.25, 1.0)[rnd.nextInt(4)]
            val r = LockIn.plan(Held("A", heldA, spentA), Held("B", heldB, spentB), ladder, fee, live, pushable = false, minProfit = minProfit)
            if (r !is LockResult.Ready) return@repeat
            locks++
            val p = r.plan
            val spent = spentA + spentB
            // Any fill at or under the limit: the worst is all of it at the limit with the most fee.
            for (price in listOf(p.limitPrice, ladder.minOf { it.price })) {
                val cost = p.contracts * 0.01 * (price + LockIn.feeRate(fee, live, price))
                val qA = heldA
                val qB = heldB + p.contracts
                val aWins = qA * 0.01 - spent - cost
                val bWins = qB * 0.01 - spent - cost
                assertTrue("A wins pays $aWins under $minProfit: $p", aWins >= minProfit - 1e-9)
                assertTrue("B wins pays $bWins under $minProfit: $p", bWins >= minProfit - 1e-9)
                // A fair-value void pays each side its price, the two summing to 1.
                val v = rnd.nextDouble()
                assertTrue(v * qA * 0.01 + (1 - v) * qB * 0.01 - spent - cost >= minProfit - 1e-9)
            }
            assertTrue(p.limitPrice <= ladder.maxOf { it.price } + 1e-9)
            assertTrue(p.guaranteed >= minProfit - 1e-9)
        }
        assertTrue("the property ran on real locks ($locks)", locks > 500)
    }

    @Test
    fun `whole-number lines and tying moneylines can push`() {
        assertTrue(LockIn.pushable("SPREAD", -3.0, "NFL"))
        assertTrue(!LockIn.pushable("SPREAD", -3.5, "NFL"))
        assertTrue(LockIn.pushable("TOTAL", 8.0, "MLB"))
        assertTrue(LockIn.pushable("MONEYLINE", 0.0, "NFL"))
        assertTrue(!LockIn.pushable("MONEYLINE", 0.0, "NBA"))
        assertTrue(LockIn.pushable("PLAYER_RECEPTIONS", null, "NFL")) // no line stated: assume it can
    }

    @Test
    fun `holding is worth the fair chance of each side's payout less what was spent`() {
        // 1,000 A at 0.40 ($4): at a 50% fair chance, holding is worth $1.00; the lock at 0.55 above pays $0.50 for sure.
        assertEquals(1.0, LockIn.holdValue(Held("A", 1_000, 4.0), Held("B", 0, 0.0), "A", 0.50), 1e-9)
    }
}
