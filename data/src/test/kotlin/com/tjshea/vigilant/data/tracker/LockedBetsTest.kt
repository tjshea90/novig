package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locked-in markets in the Tracker (Tj, 2026-10-02 20:06Z: "Add options to remove arbitraged locked bets out of stats and bet trackers. It makes no
 * sense for me to track a bet that is already cashed out. Maybe a stat tracker for amount and percentage of bets locked in and the total profit and
 * percentage of profit for those bets").
 */
class LockedBetsTest {

    private val now = 1_800_000_000_000L

    /** An API bet: [contracts] of [outcome] in [market] for [paid] dollars (a contract pays $0.01). */
    private fun api(
        id: String, market: String, outcome: String, contracts: Long, paid: Double, lockFor: String? = null, status: BetStatus = BetStatus.PENDING,
        created: Long = now - 86_400_000L,
    ): TrackedBet {
        val payout = contracts * 0.01
        return TrackedBet(
            id = id, createdAtMs = created, league = "NFL", eventName = "A @ B", startsTs = now + 3_600_000L, marketLabel = "Moneyline", selection = outcome,
            marketId = market, outcomeId = outcome, price = paid / payout, cost = paid / payout, fairAtBet = 0.5, evPercentAtBet = 0.03, stake = paid,
            status = status, orderId = "o-$id", contracts = contracts, paid = paid, fee = 0.0, lockFor = lockFor,
        )
    }

    private fun mark(id: String, market: String = "m9") = api(id, market, "A", 1000, 4.0).copy(orderId = null, contracts = null)

    // m1: 1000 A for $4.00, locked with 1000 B for $5.50 → pays $10 either way: $0.50 profit.
    private val pick1 = api("p1", "m1", "A", 1000, 4.00)
    private val lock1 = api("l1", "m1", "B", 1000, 5.50, lockFor = "p1")
    // m2: two picks on A (600 + 400) locked with one 1000 on B: $10 − $9.20 = $0.80.
    private val pick2a = api("p2a", "m2", "A", 600, 2.40)
    private val pick2b = api("p2b", "m2", "A", 400, 1.60)
    private val lock2 = api("l2", "m2", "B", 1000, 5.20, lockFor = "p2a")
    // m3: partly locked (1000 A, 500 B): still riding.
    private val pick3 = api("p3", "m3", "A", 1000, 4.00)
    private val lock3 = api("l3", "m3", "B", 500, 2.60, lockFor = "p3")
    // m4: an API bet on one side only.
    private val single = api("s", "m4", "A", 1000, 4.00)

    private val all = listOf(pick1, lock1, pick2a, pick2b, lock2, pick3, lock3, single, mark("mk"))

    @Test
    fun `a market is locked when API bets hold both sides equally - not one side, not unequal sides, never a ✓ mark`() {
        val m = LockedBets.markets(all)
        assertEquals(setOf("m1", "m2"), m.keys)
        assertEquals(0.50, m.getValue("m1").lockedProfit, 1e-9)
        assertEquals(0.80, m.getValue("m2").lockedProfit, 1e-9)
        assertEquals(1000L, m.getValue("m2").contracts)
        assertEquals(listOf("p2a", "p2b"), m.getValue("m2").picks.map { it.id })
        assertEquals(1, LockedBets.partly(all))
        assertEquals(setOf("p1", "l1", "p2a", "p2b", "l2"), LockedBets.ids(all))
        // A ✓ mark beside API bets on the other side doesn't make a lock (its contracts aren't known).
        assertTrue(LockedBets.markets(listOf(api("x", "m9", "B", 1000, 5.0), mark("mk"))).isEmpty())
    }

    @Test
    fun `hiding takes every bet of a locked market out, judged over all bets, and leaves the rest`() {
        assertEquals(listOf("p3", "l3", "s", "mk"), LockedBets.hide(all).map { it.id })
        // A period holding only the pick still hides it: its lock (in another period) is judged from every bet.
        assertEquals(emptyList<String>(), LockedBets.hide(listOf(pick1), all).map { it.id })
        assertEquals(listOf("p1"), LockedBets.hide(listOf(pick1)).map { it.id })
    }

    @Test
    fun `the lock numbers - picks locked and their share, profit locked on what both sides staked, graded or not`() {
        val s = LockedBets.stats(all)
        // Picks: p1, p2a, p2b, p3, s, mk (locks aren't picks); 3 of them locked.
        assertEquals(3, s.lockedBets)
        assertEquals(6, s.bets)
        assertEquals(0.5, s.share!!, 1e-12)
        assertEquals(2, s.markets)
        assertEquals(4.00 + 5.50 + 2.40 + 1.60 + 5.20, s.staked, 1e-9)
        assertEquals(1.30, s.profit, 1e-9)
        assertEquals(1.30 / 18.70, s.roi!!, 1e-12)
        assertEquals(0, s.graded)
        assertEquals(5, s.hiddenBets)
        assertEquals(1, s.partly)
        // Once graded, what the results paid (the same, a contract paying $0.01 whichever side won).
        val graded = all.map { if (it.marketId == "m1") it.copy(status = if (it.outcomeId == "A") BetStatus.WON else BetStatus.LOST) else it }
        val g = LockedBets.stats(graded)
        assertEquals(1, g.graded)
        assertEquals(0.50, g.paid, 1e-9)
        assertEquals(1.30, g.profit, 1e-9)
        // A voided market refunds both sides: nothing made.
        val void = all.map { if (it.marketId == "m1") it.copy(status = BetStatus.VOID) else it }
        assertEquals(0.80, LockedBets.stats(void).profit, 1e-9)
        // Nothing locked: no numbers.
        val none = LockedBets.stats(listOf(single))
        assertEquals(0, none.markets)
        assertNull(none.roi)
        assertTrue(!none.any)
    }

    @Test
    fun `a period counts the locked markets its picks are in`() {
        val today = now - 3_600_000L
        val newPick = api("n1", "m5", "A", 1000, 4.00, created = today)
        val newLock = api("n2", "m5", "B", 1000, 5.00, lockFor = "n1", created = today)
        val everything = all + newPick + newLock
        val s = LockedBets.stats(everything.filter { it.createdAtMs >= today }, everything)
        assertEquals(1, s.lockedBets)
        assertEquals(1, s.bets)
        assertEquals(1.00, s.profit, 1e-9)
    }
}
