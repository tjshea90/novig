package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tj, 2026-10-10: "separate the auto bid and auto bet trap guards ... at a different number". */
class TrapGuardSplitTest {

    @Test
    fun `saved settings keep what the one shared number was, for both, until Tj changes either`() {
        val old = ScanSettings(trapEarlyHours = 12, schema = 13).copy(trapBidHours = 6)
        val moved = old.migrate()
        assertEquals(14, moved.schema)
        assertEquals(12, moved.trapEarlyHours)
        assertEquals(12, moved.trapBidHours)
        // Already migrated: a bids number of his own is never overwritten again.
        assertEquals(3, moved.copy(trapBidHours = 3).migrate().trapBidHours)
    }

    @Test
    fun `the bids read their own hours, the auto-bet and alerts theirs`() {
        val s = ScanSettings(trapEarlyHours = 24, trapBidHours = 6)
        assertEquals(6, MakerRules.of(s).earlyHours)
        assertEquals(24, s.trapEarlyHours)
        assertEquals(0, MakerRules.of(s.copy(trapBidHours = 0)).earlyHours)
        assertEquals(24, MakerRules.of(s.copy(trapBidHours = 0)).let { s.trapEarlyHours })
        // Low API usage reads as far as the BIDS' hours, never the auto-bet's.
        assertEquals(6, LowUsageBids.windowHours(s.copy(scanWindowHours = 48)))
        assertEquals(24, LowUsageBids.windowHours(s.copy(trapBidHours = 24, trapEarlyHours = 6, scanWindowHours = 48)))
    }
}
