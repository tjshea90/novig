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
        assertNotNull(ProfitSeries.Range.entries.firstOrNull { it.long == "This week, from Monday" })
    }

    @Test
    fun `a slate runs from its first game to the last result, past midnight, and the next morning is a new slate`() {
        // Oct 7: games 7am..11pm (the last one is final after midnight); Oct 8 starts at 8am (9 h after the last start): a new slate.
        val bets = listOf(
            bet("a", at(2026, 10, 7, 7), BetStatus.WON, 10.0), bet("b", at(2026, 10, 7, 13), BetStatus.LOST, 10.0), bet("c", at(2026, 10, 7, 23), BetStatus.WON, 10.0),
            bet("d", at(2026, 10, 8, 8), BetStatus.LOST, 10.0),
        )
        val sl = ProfitSeries.slates(ProfitSeries.points(bets))
        assertEquals(2, sl.size)
        assertEquals(3, sl[0].bets)
        assertEquals(at(2026, 10, 7, 7), sl[0].startMs)
        assertEquals(at(2026, 10, 7, 23) + ProfitSeries.GAME_MS, sl[0].endMs)
        assertEquals(30.0, sl[0].staked, 1e-9)
    }

    @Test
    fun `the start number is the first slate's stake and never moves, and each day is measured against the day before`() {
        // bet() prices at 50 cents: a win pays the stake, a loss costs it.
        val bets = listOf(
            bet("a", at(2026, 10, 7, 12), BetStatus.WON, 50.0), bet("b", at(2026, 10, 7, 14), BetStatus.LOST, 75.0),   // day 1: staked 125, profit -25
            bet("c", at(2026, 10, 8, 12), BetStatus.WON, 30.0),                                                       // day 2: profit +30
        )
        val sl = ProfitSeries.slates(ProfitSeries.points(bets))
        assertEquals(125.0, ProfitSeries.startingBalance(sl)!!, 1e-9)
        assertEquals(-25.0 / 125.0, ProfitSeries.dayChange(sl, 0)!!, 1e-9)
        assertEquals(30.0 / 100.0, ProfitSeries.dayChange(sl, 1)!!, 1e-9)   // on 125 - 25 = 100, whatever was staked on day 2
    }

    @Test
    fun `today and yesterday are slates, not calendar days`() {
        val bets = listOf(bet("a", at(2026, 10, 7, 20), BetStatus.WON), bet("b", at(2026, 10, 7, 23), BetStatus.LOST), bet("c", at(2026, 10, 8, 8), BetStatus.WON))
        val sl = ProfitSeries.slates(ProfitSeries.points(bets))
        val now = at(2026, 10, 8, 9)   // Oct 8, 9am: the Oct 7 night slate ended at 3am; today's slate is Oct 8's
        assertEquals(at(2026, 10, 8, 8), ProfitSeries.Range.TODAY.bounds(now, 0L, zone, sl).first)
        assertEquals(at(2026, 10, 7, 20), ProfitSeries.Range.YESTERDAY.bounds(now, 0L, zone, sl).first)
        assertEquals(at(2026, 10, 7, 23) + ProfitSeries.GAME_MS, ProfitSeries.Range.YESTERDAY.bounds(now, 0L, zone, sl).second)
    }
}
