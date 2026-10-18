package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.SlipStake
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tj, 2026-10-07 ("redundant … settings"): the Bet sheet's own starting amount was folded into "My amount"; what was saved keeps working. */
class SettingsMergeTest {

    @Test
    fun `a saved Bet sheet amount becomes My amount when the sheet was the one using it`() {
        // Off and Kelly: the Bet sheet's amount (apiBetStake) was the one in use.
        assertEquals(10.0, ScanSettings(slipStake = SlipStake.OFF, apiBetStake = 10.0, slipCustomStake = 5.0, schema = 12).migrate().slipCustomStake, 0.0)
        assertEquals(2.0, ScanSettings(slipStake = SlipStake.KELLY, apiBetStake = 2.0, slipCustomStake = 5.0, schema = 12).migrate().slipCustomStake, 0.0)
        // My amount chosen: the saved My amount was already the one; the sheet's old amount is dropped.
        assertEquals(7.5, ScanSettings(slipStake = SlipStake.CUSTOM, apiBetStake = 10.0, slipCustomStake = 7.5, schema = 12).migrate().slipCustomStake, 0.0)
        // $1: nothing to carry over.
        assertEquals(5.0, ScanSettings(slipStake = SlipStake.ONE_DOLLAR, apiBetStake = 10.0, slipCustomStake = 5.0, schema = 12).migrate().slipCustomStake, 0.0)
        // Once migrated, it isn't done again.
        val once = ScanSettings(slipStake = SlipStake.OFF, apiBetStake = 10.0, slipCustomStake = 5.0, schema = 12).migrate()
        assertEquals(22, once.schema)
        assertEquals(10.0, once.copy(apiBetStake = 99.0).migrate().slipCustomStake, 0.0)
    }

    @Test fun `the fair line uses the conservative worst case by default, saved settings move to it once, and a later choice stays`() {
        assertEquals(com.tjshea.vigilant.engine.DevigMethod.WORST_CASE, ScanSettings().devigMethod)
        assertEquals(com.tjshea.vigilant.engine.DevigMethod.WORST_CASE, ScanSettings().fairSettings().method)
        assertEquals(com.tjshea.vigilant.engine.DevigMethod.WORST_CASE, com.tjshea.vigilant.engine.FairSettings().method)
        val old = ScanSettings(devigMethod = com.tjshea.vigilant.engine.DevigMethod.POWER, schema = 16).migrate()
        assertEquals(com.tjshea.vigilant.engine.DevigMethod.WORST_CASE, old.devigMethod)
        assertEquals(22, old.schema)
        // Tj picks Power afterwards: the file is current, so it is left alone.
        val picked = old.copy(devigMethod = com.tjshea.vigilant.engine.DevigMethod.POWER).migrate()
        assertEquals(com.tjshea.vigilant.engine.DevigMethod.POWER, picked.devigMethod)
    }
}
