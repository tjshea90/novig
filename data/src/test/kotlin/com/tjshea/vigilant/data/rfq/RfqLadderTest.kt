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
}
