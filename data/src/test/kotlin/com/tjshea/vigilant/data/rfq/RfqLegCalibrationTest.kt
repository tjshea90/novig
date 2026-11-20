package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RfqLegCalibrationTest {
    @Test fun longshotsAreCutFavouritesInTheMiddleRaisedAndTheEndsLeftAlone() {
        assertEquals(0.20 * 0.90, RfqLegCalibration.adjust(0.20), 1e-9)
        assertTrue(RfqLegCalibration.adjust(0.30) < 0.30)
        assertEquals(0.50 * RfqLegCalibration.ratio(0.50), RfqLegCalibration.adjust(0.50), 1e-9)
        assertTrue(RfqLegCalibration.adjust(0.65) > 0.65 && RfqLegCalibration.adjust(0.65) < 0.65 * 1.03)
        assertEquals(0.92, RfqLegCalibration.adjust(0.92), 1e-9)
    }

    @Test fun theRatioIsContinuousAndNeverMovesAProbabilityOutOfRange() {
        var last = RfqLegCalibration.ratio(0.0)
        var p = 0.0
        while (p <= 1.0) {
            val r = RfqLegCalibration.ratio(p)
            assertTrue("jump at $p", Math.abs(r - last) < 0.02)
            val a = RfqLegCalibration.adjust(p)
            assertTrue(a in 0.001..0.999)
            last = r; p += 0.005
        }
    }
}

class RfqAllHighLegsTest {
    private val now = 1_800_000_000_000L
    private fun leg(id: String, event: String, p: Double) = LegFair(id, event, "l$id", p, 5, now - 60_000L, now + 3 * 3_600_000L, false, "MONEY")
    private val cfg = RfqConfig(legCalibration = false, mode = RfqConfig.Mode.LIVE, margin = 0.0, minReturn = 0.0, liveMinReturn = 0.0, minPoints = 0.0, longshotsOnly = false, maxFair = 0.99, maxLegFair = 0.99, maxLegCollateral = 0.0)
    private fun decide(vararg l: LegFair, c: RfqConfig = cfg) = RfqPricer.decide(RfqProtocol.Created(now, "r", l.map { it.outcomeId }, now + 3_000L, 10.0), now, c, LegFairs { id -> l.firstOrNull { it.outcomeId == id } }, 0.0)

    private fun why(d: RfqDecision) = (d as? RfqDecision.Decline)?.reason ?: "quote"

    @Test fun threeLegsThatAreAllFavouritesAreDeclinedButTwoLegsAndMixedParlaysAreNot() {
        val d = decide(leg("a", "e1", 0.7), leg("b", "e2", 0.65), leg("c", "e3", 0.6)) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("favourites"))
        assertTrue(why(decide(leg("a", "e1", 0.7), leg("b", "e2", 0.65))), !why(decide(leg("a", "e1", 0.7), leg("b", "e2", 0.65))).contains("favourites"))
        assertTrue(why(decide(leg("a", "e1", 0.7), leg("b", "e2", 0.65), leg("c", "e3", 0.4))), !why(decide(leg("a", "e1", 0.7), leg("b", "e2", 0.65), leg("c", "e3", 0.4))).contains("favourites"))
        assertTrue(!why(decide(leg("a", "e1", 0.7), leg("b", "e2", 0.65), leg("c", "e3", 0.6), c = cfg.copy(allHighMaxLegs = 0))).contains("favourites"))
    }
}
