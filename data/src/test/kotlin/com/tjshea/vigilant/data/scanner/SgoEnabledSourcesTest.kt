package com.tjshea.vigilant.data.scanner

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Found live (2026-10-09): the scan read SGO but priced nothing from it because "sgo" was not an enabled source, so the plan never saw its snapshot. */
class SgoEnabledSourcesTest {
    @Test fun sgoPro_puts_both_sgo_sources_in_the_plan_and_the_order() {
        val on = ScanSettings(sgoPro = true).enabledSources
        assertTrue("sgo" in on && "sgo-props" in on)
        assertTrue("sgo" in Scanner.SOURCE_ORDER && "sgo-props" in Scanner.SOURCE_ORDER)
    }

    @Test fun toggle_off_leaves_the_set_exactly_as_it_was() {
        val off = ScanSettings(sgoPro = false).enabledSources
        assertFalse(off.any { it.startsWith("sgo") })
        assertEquals(ScanSettings().enabledSources, off)
    }
}

class SgoBidPaceTest {
    private val bids = ScanSettings(maker = true, sgoPro = true, makerLongRun = true)

    @Test fun sgo_pro_scans_every_two_minutes_and_never_slows_for_far_games() {
        assertEquals(120, bids.vigilantGapSeconds)
        assertEquals(120, LongRunBids.gapSeconds(bids, listOf(System.currentTimeMillis() + 20 * 3_600_000L), System.currentTimeMillis()))
        assertEquals(120, ScanSettings.vigilantEverySeconds(60, bids.vigilantGapSeconds))
    }

    @Test fun off_the_pace_is_the_old_four_minutes() {
        assertEquals(ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS, bids.copy(sgoPro = false).vigilantGapSeconds)
    }
}
