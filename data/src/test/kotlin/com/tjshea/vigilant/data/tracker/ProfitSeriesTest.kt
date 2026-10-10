package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The profit graph's numbers (Tj, 2026-10-10): the same bets and money as the Tracker's Profit, by time, with the ranges a stock chart has. */
class ProfitSeriesTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDate.of(y, m, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun bet(id: String, t: Long, status: BetStatus, stake: Double = 1.0) =
        TrackedBet(id, 0, "NFL", "A @ B", t, "Moneyline", "A", "m", "o", 0.5, 0.5, 0.52, 0.04, stake, status, source = BetTracker.SOURCE_CNO)

    @Test
    fun `the curve is the Tracker's profit by time - settled bets only, voids and open ones never, and the last point is Profit`() {
        val bets = listOf(
            bet("late", at(2026, 10, 9), BetStatus.WON), bet("early", at(2026, 10, 7), BetStatus.LOST),
            bet("open", at(2026, 10, 10), BetStatus.PENDING), bet("void", at(2026, 10, 8), BetStatus.VOID),
        )
        val p = ProfitSeries.points(bets)
        assertEquals(listOf(at(2026, 10, 7), at(2026, 10, 9)), p.map { it.tMs })
        assertEquals(BetTracker.stats(bets).profitAll, p.last().cum, 1e-12)
        assertEquals(0, ProfitSeries.points(emptyList()).size)
    }

    @Test
    fun `value at a time is the last bet before it, and a window adds up the bets in it`() {
        val bets = (1..5).map { bet("b$it", at(2026, 10, it), if (it % 2 == 0) BetStatus.LOST else BetStatus.WON) }
        val p = ProfitSeries.points(bets)
        assertEquals(0.0, ProfitSeries.valueAt(p, at(2026, 9, 30)), 1e-12)
        assertEquals(p[1].cum, ProfitSeries.valueAt(p, at(2026, 10, 2, 13)), 1e-12)
        val w = ProfitSeries.window(p, at(2026, 10, 2), at(2026, 10, 4))
        assertEquals(3, w.bets); assertEquals(3.0, w.staked, 1e-12)
        assertEquals(p[1].profit + p[2].profit + p[3].profit, w.profit, 1e-12)
        assertEquals(w.profit / 3.0, w.roi!!, 1e-12)
        assertEquals(0, ProfitSeries.window(p, 1, 2).bets)
        assertEquals(null, ProfitSeries.window(p, 1, 2).roi)
        assertEquals(2, ProfitSeries.firstAtOrAfter(p, at(2026, 10, 2, 13)))
    }

    @Test
    fun `the ranges are calendar days on the phone, this week starts Monday, all time starts at the first bet`() {
        val now = at(2026, 10, 10, 15)    // a Saturday
        val first = at(2026, 8, 1)
        val (tFrom, tTo) = ProfitSeries.Range.TODAY.bounds(now, first, zone)
        assertEquals(at(2026, 10, 10, 0), tFrom); assertEquals(now, tTo)
        val (yFrom, yTo) = ProfitSeries.Range.YESTERDAY.bounds(now, first, zone)
        assertEquals(at(2026, 10, 9, 0), yFrom); assertEquals(at(2026, 10, 10, 0) - 1, yTo)
        assertEquals(at(2026, 10, 9, 0), ProfitSeries.Range.TWO_DAYS.bounds(now, first, zone).first)
        assertEquals(at(2026, 10, 8, 0), ProfitSeries.Range.THREE_DAYS.bounds(now, first, zone).first)
        assertEquals(at(2026, 10, 5, 0), ProfitSeries.Range.THIS_WEEK.bounds(now, first, zone).first)   // Monday Oct 5
        assertEquals(now - 7 * ProfitSeries.DAY_MS, ProfitSeries.Range.WEEK.bounds(now, first, zone).first)
        assertEquals(now - 30 * ProfitSeries.DAY_MS, ProfitSeries.Range.MONTH.bounds(now, first, zone).first)
        assertEquals(first, ProfitSeries.Range.ALL.bounds(now, first, zone).first)
        // With no bets before today, All time still shows at least a day.
        assertTrue(ProfitSeries.Range.ALL.bounds(now, now, zone).first <= now - ProfitSeries.DAY_MS)
        assertNotNull(ProfitSeries.Range.entries.firstOrNull { it.label == "This week" })
    }
}
