package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RfqLadderTest {
    private val now = 1_800_000_000_000L
    private fun rung(strike: Double, p: Double, books: Int = 5, asOf: Long = now) = RfqLadder.Rung(strike, p, books, asOf)

    @Test fun probitAndCdfAreInverseAndMatchKnownValues() {
        assertEquals(0.0, RfqLadder.probit(0.5), 1e-9)
        assertEquals(1.959964, RfqLadder.probit(0.975), 1e-5)
        for (p in listOf(0.03, 0.2, 0.5, 0.77, 0.97)) assertEquals(p, RfqLadder.cdf(RfqLadder.probit(p)), 1e-6)
    }

    @Test fun aRungBetweenTwoPostedOnesIsInterpolatedAndStaysBetweenThem() {
        val rungs = listOf(rung(10.5, 0.9), rung(49.5, 0.5), rung(80.5, 0.15))
        val mid = RfqLadder.interpolate(rungs, 29.5)!!
        assertTrue(mid.overProb < 0.9 && mid.overProb > 0.5)
        val closer = RfqLadder.interpolate(rungs, 20.5)!!
        assertTrue("a lower line is likelier to go over", closer.overProb > mid.overProb)
    }

    @Test fun nothingOutsideThePostedRangeAndNothingFromARisingLadder() {
        val rungs = listOf(rung(30.5, 0.7), rung(50.5, 0.4))
        assertNull(RfqLadder.interpolate(rungs, 20.5))
        assertNull(RfqLadder.interpolate(rungs, 60.5))
        assertNull(RfqLadder.interpolate(listOf(rung(30.5, 0.4), rung(50.5, 0.7)), 40.5))
        assertNull(RfqLadder.interpolate(listOf(rung(30.5, 0.4)), 40.5))
    }

    @Test fun theOlderRungsAgeAndTheFewerBooksCarryOver() {
        val r = RfqLadder.interpolate(listOf(rung(10.5, 0.9, books = 6, asOf = now - 1000), rung(50.5, 0.4, books = 3, asOf = now - 9000)), 30.5)!!
        assertEquals(3, r.books); assertEquals(now - 9000, r.asOfMs)
    }

    @Test fun theGroupIgnoresTheLine() {
        assertEquals(RfqLadder.group("e1", "RECEIVING_YARDS", "George Kittle 53.5 RECEIVING_YARDS", 53.5), RfqLadder.group("e1", "RECEIVING_YARDS", "George Kittle 24.5 RECEIVING_YARDS", 24.5))
    }

    @Test fun fairsPricesAnUnpostedRungFromTheSamePlayerAndSidesAddUp() {
        val g = RfqLadder.group("e1", "RECEIVING_YARDS", "George Kittle 30.5 RECEIVING_YARDS", 30.5)
        val targets = mapOf(
            "over" to RfqLadder.Target(g, 30.5, true, "e1", "RECEIVING_YARDS", now + 3_600_000L),
            "under" to RfqLadder.Target(g, 30.5, false, "e1", "RECEIVING_YARDS", now + 3_600_000L),
        )
        val rungs = mapOf(g to listOf(rung(10.5, 0.9), rung(50.5, 0.4)))
        val f = RfqLadder.Fairs({ null }, { targets }, { rungs })
        val o = f.leg("over")!!; val u = f.leg("under")!!
        assertEquals(1.0, o.probability + u.probability, 1e-9)
        assertEquals("RECEIVING_YARDS", o.marketType)
        assertNull(f.leg("nobody"))
        val direct = LegFair("over", "e1", "x", 0.123, 5, now, now, false, "RECEIVING_YARDS")
        assertEquals(0.123, RfqLadder.Fairs({ direct }, { targets }, { rungs }).leg("over")!!.probability, 0.0)
        assertNotNull(f.leg("under"))
    }

    // ---- MK21: extrapolation outside the posted range ----
    private val postedPoints = listOf(rung(25.5, 0.52, books = 6, asOf = now - 2_000), rung(26.5, 0.46, books = 4, asOf = now - 5_000))

    @Test fun aRungOutsideThePostedRangeIsExtrapolatedFromTheRungNearestEvenAndFallsWithTheLine() {
        val base = postedPoints[0]
        val lower = RfqLadder.extrapolate(postedPoints, 19.5, "POINTS")!!
        val higher = RfqLadder.extrapolate(postedPoints, 31.5, "POINTS")!!
        assertTrue("a lower line is likelier to go over (was ${lower.overProb})", lower.overProb > base.overProb)
        assertTrue("a higher line is less likely (was ${higher.overProb})", higher.overProb < base.overProb && higher.overProb > 0.0)
        // the formula: probit(p at L) + beta x (L - K) / sqrt(L), beta 0.515 for NBA points
        val units = (25.5 - 19.5) / Math.sqrt(25.5)
        assertEquals(RfqLadder.cdf(RfqLadder.probit(0.52) + 0.515 * units), lower.overProb, 1e-12)
        assertEquals("the base rung's companies and age carry over", 6, lower.books); assertEquals(now - 2_000, lower.asOfMs)
    }

    @Test fun nothingIsExtrapolatedInsideTheRangeWithoutASlopeOrBeyondTheCheckedDistance() {
        assertNull("inside the posted range is interpolate's", RfqLadder.extrapolate(postedPoints.map { it.copy(strike = it.strike) } + RfqLadder.Rung(30.5, 0.3, 5, now), 28.5, "POINTS"))
        assertNull("no slope for the stat", RfqLadder.extrapolate(postedPoints, 19.5, "TACKLES_ASSISTS"))
        assertNull("no posted rung", RfqLadder.extrapolate(emptyList(), 19.5, "POINTS"))
        assertNull("farther than the checked range (3 units: 14.6 points at a base of 25.5)", RfqLadder.extrapolate(postedPoints, 5.5, "POINTS"))
        assertNotNull(RfqLadder.extrapolate(postedPoints, 12.5, "POINTS"))
    }

    @Test fun fairsExtrapolatesOnlyWhenSwitchedOnAndOnlyAMidSide() {
        val g = RfqLadder.group("e1", "POINTS", "Jalen Brunson 25.5 POINTS", 25.5)
        fun target(strike: Double, over: Boolean) = RfqLadder.Target(g, strike, over, "e1", "POINTS", now + 3_600_000L)
        val targets = mapOf("o19" to target(19.5, true), "u19" to target(19.5, false), "o38" to target(38.5, true), "o31" to target(31.5, true), "o31u" to target(31.5, false))
        val rungs = mapOf(g to postedPoints)
        assertNull("off: as before", RfqLadder.Fairs({ null }, { targets }, { rungs }).leg("o19"))
        val f = RfqLadder.Fairs({ null }, { targets }, { rungs }, { true })
        val o = f.leg("o19")!!; val u = f.leg("u19")!!
        assertTrue(o.extrapolated && u.extrapolated && o.label.startsWith("extrapolated"))
        assertEquals("the two sides add up", 1.0, o.probability + u.probability, 1e-9)
        assertEquals("POINTS", o.marketType)
        assertNull("Over 38.5 is a long shot (the model puts it near 10%): not priced", f.leg("o38"))
        val mid = f.leg("o31")!!
        assertTrue(mid.probability in RfqLadder.EXTRAP_MIN_SIDE..RfqLadder.EXTRAP_MAX_SIDE)
        assertNull("a side the model puts over 92% is not priced either", RfqLadder.Fairs({ null }, { mapOf("x" to target(11.5, true)) }, { rungs }, { true }).leg("x"))
        // a posted rung is never replaced by the model: a direct fair, or an interpolated rung, comes first
        val inside = mapOf("in" to target(25.9, true)); val three = mapOf(g to postedPoints + rung(30.5, 0.30))
        assertTrue(!RfqLadder.Fairs({ null }, { inside }, { three }, { true }).leg("in")!!.extrapolated)
    }

    @Test fun theSlopesCoverTheStatsMeasuredAndAreAllPositive() {
        for (s in listOf("POINTS", "REBOUNDS", "ASSISTS", "THREE_POINTERS_MADE", "POINTS_REBOUNDS_ASSISTS", "SHOTS_ON_GOAL", "RECEIVING_YARDS", "RUSHING_YARDS", "PASSING_YARDS", "RECEPTIONS")) assertTrue(s, (RfqLadder.SLOPES[s] ?: 0.0) > 0.0)
    }

    @Test fun aLadderPricedLegCarriesWhatTheParlayHubNeedsToGradeIt() {
        val g = RfqLadder.group("e1", "POINTS", "Jalen Brunson 25.5 POINTS", 25.5)
        val t = RfqLadder.Target(g, 19.5, true, "e1", "POINTS", now + 3_600_000L, "jalen brunson", "Over 19.5", "Knicks @ Spurs", "m-19")
        val f = RfqLadder.Fairs({ null }, { mapOf("o" to t) }, { mapOf(g to postedPoints) }, { true }).leg("o")!!
        assertEquals("Over 19.5", f.outcomeName); assertEquals("Knicks @ Spurs", f.eventDesc); assertEquals("m-19", f.marketId)
        assertTrue(f.extrapolated)
    }
}
