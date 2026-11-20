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
