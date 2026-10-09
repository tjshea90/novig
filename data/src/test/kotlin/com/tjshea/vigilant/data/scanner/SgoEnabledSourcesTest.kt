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
