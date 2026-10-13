package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyInTest {
    private val h = 3_600_000L
    private fun bet(id: String, placed: Long, settled: Long?, stake: Double, cost: Double, status: BetStatus) =
        TrackedBet(id, placed, "NFL", "A @ B", placed + 10 * h, "Moneyline", "A", "m$id", "o$id", 0.5, cost, 0.52, 0.04, stake, status, settledAtMs = settled, source = BetTracker.SOURCE_CNO)

    @Test fun `winnings that are bet again are not a second deposit`() {
        // $200 in, wins at a price of 0.9091 (profit $20), the $220 is bet again and wins (profit $22): $420 staked, $200 put in.
        val a = bet("a", 0, h, 200.0, 1 / 1.1, BetStatus.WON)
        val b = bet("b", 2 * h, 3 * h, 220.0, 1 / 1.1, BetStatus.WON)
        val r = MoneyIn.compute(listOf(a, b))
        assertEquals(200.0, r.deposited, 1e-9)
        assertTrue(r.estimated)
        assertEquals(42.0, r.profit, 1e-6)
        assertEquals(420.0, r.turnover, 1e-9)
        assertEquals(0.21, r.percent!!, 1e-6)
        assertEquals(2.1, r.turns!!, 1e-9)
        assertEquals("the pot is worth what went in plus what was won", 242.0, r.worthNow, 1e-6)
    }

    @Test fun `a loss that is followed by new money needs a second deposit and the percent is on both`() {
        val a = bet("a", 0, h, 100.0, 0.5, BetStatus.LOST)           // the whole $100 is gone
        val b = bet("b", 2 * h, 3 * h, 100.0, 0.5, BetStatus.WON)    // a fresh $100 had to come in; it doubles
        val r = MoneyIn.compute(listOf(a, b))
        assertEquals(200.0, r.deposited, 1e-9)
        assertEquals(0.0, r.profit, 1e-9)
        assertEquals(0.0, r.percent!!, 1e-9)
    }

    @Test fun `bets placed before the first one pays out each need their own money`() {
        val a = bet("a", 0, 5 * h, 100.0, 0.5, BetStatus.WON)
        val b = bet("b", h, 5 * h, 100.0, 0.5, BetStatus.LOST)
        val r = MoneyIn.compute(listOf(a, b))
        assertEquals("both were out at once", 200.0, r.deposited, 1e-9)
    }

    @Test fun `Tj's own deposit figure replaces the estimate, open bets and voids stay out of the profit`() {
        val a = bet("a", 0, h, 50.0, 0.5, BetStatus.WON)
        val open = bet("o", 2 * h, null, 30.0, 0.5, BetStatus.PENDING)
        val void = bet("v", 3 * h, null, 40.0, 0.5, BetStatus.VOID)
        val r = MoneyIn.compute(listOf(a, open, void), given = 500.0)
        assertEquals(500.0, r.deposited, 1e-9)
        assertFalse(r.estimated)
        assertEquals(50.0, r.profit, 1e-9)
        assertEquals(0.1, r.percent!!, 1e-9)
    }

    @Test fun `no bets means no percent`() {
        val r = MoneyIn.compute(emptyList())
        assertNull(r.percent)
        assertEquals(0.0, r.deposited, 0.0)
    }
}
