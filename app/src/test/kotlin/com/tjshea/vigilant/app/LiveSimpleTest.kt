package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class LiveSimpleTest {
    @Test fun practiceRunsEveryLaneWithNoRealOrders() {
        val s = LiveSimple.set(ScanSettings(), LiveSimple.Mode.PRACTICE)
        assertEquals(LiveSimple.Mode.PRACTICE, LiveSimple.modeOf(s))
        assertTrue(s.tailLive.on && s.pinnLive && s.liveBid)
        assertFalse(s.tailLive.bet || s.pinnLiveBet || s.liveBidReal)
    }

    @Test fun realTurnsOrdersOnAndOffTurnsEverythingOff() {
        val r = LiveSimple.set(ScanSettings(), LiveSimple.Mode.REAL)
        assertEquals(LiveSimple.Mode.REAL, LiveSimple.modeOf(r))
        assertTrue(r.tailLive.bet && r.pinnLiveBet && r.liveBidReal)
        val o = LiveSimple.set(r, LiveSimple.Mode.OFF)
        assertEquals(LiveSimple.Mode.OFF, LiveSimple.modeOf(o))
        assertFalse(o.tailLive.on || o.pinnLive || o.liveBid || o.tailLive.bet)
        assertEquals(LiveSimple.Mode.PRACTICE, LiveSimple.modeOf(LiveSimple.set(o, LiveSimple.Mode.PRACTICE)))
    }
}
