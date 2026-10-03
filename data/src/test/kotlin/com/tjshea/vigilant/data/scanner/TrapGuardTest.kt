package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trap guard (Tj, 2026-10-03: "find these trap bets and avoid them"; RESEARCH.md §71): the early rule from Tj's own closes, and the game-line
 * move rule from Novig's own trades, read the way Novig's public /trades route reports them (the resting order's outcome and price).
 */
class TrapGuardTest {

    private val now = 1_800_000_000_000L
    private val h = 3_600_000L
    private val x = "over"
    private val y = "under"

    @Test
    fun `a game more than the guard's hours away is too early, one inside it or at it is not, and 0 is off`() {
        assertNotNull(TrapGuard.early(now + 6 * h + 1, now, 6))
        assertNull(TrapGuard.early(now + 6 * h, now, 6))
        assertNull(TrapGuard.early(now + 2 * h, now, 6))
        assertNull(TrapGuard.early(now + 48 * h, now, 0))
        assertNull(TrapGuard.early(null, now, 6))
        assertEquals(TrapGuard.earlyReason(12), TrapGuard.early(now + 13 * h, now, 12))
        assertEquals(6, ScanSettings().trapEarlyHours)
        assertTrue(ScanSettings().trapNovigMove)
    }

    @Test
    fun `a trade on our outcome is a taker buying the other side - its dollars are contracts times one minus the price (verified against Novig's file)`() {
        // Novig's /trades item {outcome LAR, price 0.515, qty 1030} is the file's TAKER on BUF at cost 4.9955 for 10.30 of payout.
        val m = TrapGuard.move(listOf(TrapGuard.Trade(x, 0.515, 1030, now - 60_000)), x, 0.50, now)
        assertEquals(4.9955, m.otherSideDollars, 1e-9)
        // A trade on the other outcome is a taker buying ours: no money on the other side.
        assertEquals(0.0, TrapGuard.move(listOf(TrapGuard.Trade(y, 0.485, 1030, now - 60_000)), x, 0.50, now).otherSideDollars, 1e-9)
    }

    @Test
    fun `the level is the median of the hour's trades as prices for our side, and needs three of them`() {
        val trades = listOf(
            TrapGuard.Trade(x, 0.52, 100, now - 50 * 60_000L), // our side at 0.52
            TrapGuard.Trade(y, 0.47, 100, now - 40 * 60_000L), // the other at 0.47 = ours at 0.53
            TrapGuard.Trade(x, 0.51, 100, now - 30 * 60_000L),
            TrapGuard.Trade(x, 0.10, 100, now - 2 * h), // over an hour old: left out
        )
        val m = TrapGuard.move(trades, x, 0.50, now)
        assertEquals(0.52, m.level!!, 1e-9)
        assertEquals(3, m.trades)
        assertEquals(0.02, m.under!!, 1e-9)
        assertNull(TrapGuard.move(trades.take(2), x, 0.50, now).level)
        assertFalse(TrapGuard.move(trades.take(2), x, 0.40, now).trap)
    }

    /** A game line whose price fell to [now] from a 0.55 level as the other side was bought for [dollars] in the last few minutes. */
    private fun moved(price: Double, dollars: Double): TrapGuard.Move {
        val level = List(5) { TrapGuard.Trade(y, 0.45, 50, now - (50 - it) * 60_000L) } // ours at 0.55
        val contracts = Math.round(dollars / ((1 - 0.47) * 0.01))
        return TrapGuard.move(level + TrapGuard.Trade(x, 0.47, contracts, now - 5 * 60_000L), x, price, now)
    }

    @Test
    fun `a game line 2 cents or more under its level with 100 dollars or more just bought on the other side is a trap - not one cent, not 50 dollars`() {
        val trap = moved(price = 0.52, dollars = 150.0)
        assertTrue(trap.trap)
        assertNotNull(TrapGuard.moveReason(BetKind.SPREAD, trap))
        assertNotNull(TrapGuard.moveReason(BetKind.MONEYLINE, trap))
        assertNotNull(TrapGuard.moveReason(BetKind.TOTAL, trap))
        assertFalse(moved(price = 0.535, dollars = 150.0).trap) // 1.5¢ under
        assertFalse(moved(price = 0.52, dollars = 50.0).trap) // too little money
        // Bought 20 minutes ago: outside the 15-minute window.
        val old = TrapGuard.move(List(5) { TrapGuard.Trade(y, 0.45, 50, now - (50 - it) * 60_000L) } + TrapGuard.Trade(x, 0.47, 50_000, now - 20 * 60_000L), x, 0.52, now)
        assertFalse(old.trap)
    }

    @Test
    fun `the move rule is for game lines only - on props and period lines the same move isn't a trap (Novig's 61 days)`() {
        val trap = moved(price = 0.52, dollars = 5_000.0)
        assertTrue(trap.trap)
        assertNull(TrapGuard.moveReason(BetKind.PROP, trap))
        assertNull(TrapGuard.moveReason(BetKind.PERIOD, trap))
        assertNull(TrapGuard.moveReason(BetKind.TEAM_TOTAL, trap))
        assertNull(TrapGuard.moveReason(null, trap))
        assertTrue(TrapGuard.describe(trap).startsWith("Novig level 0.550"))
    }
}
