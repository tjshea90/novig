package com.tjshea.vigilant.data.scanner

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SafeStartTest {
    private val loud = ScanSettings(researchMode = true, altLab = true, feedRace = true, burstRecorder = true, autoBet = true, maker = true)

    @Test fun researchIsOffAtEveryStartButBidsStayWithoutACrash() {
        val s = loud.safeStart(crashed = false)
        assertFalse(s.researchMode || s.altLab || s.feedRace || s.burstRecorder)
        assertTrue(s.autoBet && s.maker)
    }

    @Test fun afterACrashTheBidsAndAutoBetAreOffToo() {
        val s = loud.safeStart(crashed = true)
        assertFalse(s.researchMode || s.autoBet || s.maker)
    }
}
