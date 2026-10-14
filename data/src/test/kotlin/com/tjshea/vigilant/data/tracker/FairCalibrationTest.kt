package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The calibration table (Tj, 2026-10-14): a fair line that runs high for prop Overs shows as a gap with a z, per kind and side. */
class FairCalibrationTest {
    private fun bet(i: Int, selection: String, won: Boolean, fair: Double = 0.49, market: String = "Receiving Yards", bid: Boolean = false) = TrackedBet(
        id = "b$i", createdAtMs = i.toLong(), league = "NFL", eventName = "A @ B", startsTs = 1L, marketLabel = market, selection = selection, marketId = "m$i", outcomeId = "o$i",
        price = 0.47, cost = 0.47, fairAtBet = fair, evPercentAtBet = 0.04, stake = 1.0, status = if (won) BetStatus.WON else BetStatus.LOST, closeFair = 0.48,
        maker = bid,
    )

    @Test
    fun `prop overs that win 40 percent against a 49 percent fair are a negative gap with a large z, and unders that match are not`() {
        val overs = (1..100).map { bet(it, "Brock Bowers Over 4.5", it <= 40) }
        val unders = (101..200).map { bet(it, "Brock Bowers Under 4.5", it <= 150 - 0 && (it - 100) <= 49) }
        val rows = FairCalibration.rows(overs + unders)
        val o = rows.first { it.label.endsWith("over") }
        assertEquals(100, o.n)
        assertEquals(-0.09, o.gap, 1e-9)
        assertTrue("z ${o.z}", o.z < -1.5)
        assertEquals(0.48, o.close!!, 1e-9)
        val u = rows.first { it.label.endsWith("under") }
        assertEquals(0.0, u.gap, 1e-9)
        val text = FairCalibration.lines(overs + unders).joinToString("\n")
        assertTrue(text.contains("bets · Player props · over: n 100"))
    }

    @Test
    fun `a group under twenty bets is not shown and nothing settled says so`() {
        assertTrue(FairCalibration.rows((1..10).map { bet(it, "Over 4.5", true) }).isEmpty())
        assertEquals(listOf("not enough settled bets yet"), FairCalibration.lines(emptyList()))
    }
}
