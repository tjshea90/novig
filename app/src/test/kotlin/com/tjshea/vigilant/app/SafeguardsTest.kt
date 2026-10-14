package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The safeguards list (Tj, 2026-10-14): every protection, its state, one button for all. */
class SafeguardsTest {
    private fun bare(): ScanSettings {
        var s = ScanSettings()
        s = Safeguards.ALL.fold(s) { a, i -> i.set(a, false) }
        return s
    }

    @Test fun `each safeguard can be switched off and on, and on reads back as on`() {
        for (i in Safeguards.ALL) {
            val off = i.set(bare(), false)
            assertFalse("${i.key} off", i.isOn(off))
            assertTrue("${i.key} on", i.isOn(i.set(off, true)))
        }
    }

    @Test fun `all on turns every one on, keeps a number he set, and changes nothing else`() {
        val base = bare().copy(apiMaxPerGame = 0.0, trapEarlyHours = 0, bankroll = 321.0)
        assertEquals(Safeguards.ALL.size, Safeguards.off(base).size)
        val on = Safeguards.allOn(base)
        assertTrue(Safeguards.off(on).isEmpty())
        assertEquals(321.0, on.bankroll, 0.0)
        assertEquals(25.0, on.apiMaxPerGame, 0.0)
        assertTrue(on.trapEarlyHours > 0)
        val custom = Safeguards.allOn(base.copy(apiMaxPerGame = 0.0)).copy(apiMaxPerGame = 50.0, sharpAutoBet = SharpMode.CONFIRM)
        assertEquals("a limit and a mode already on stay as set", 50.0, Safeguards.allOn(custom).apiMaxPerGame, 0.0)
        assertEquals(SharpMode.CONFIRM, Safeguards.allOn(custom).sharpAutoBet)
    }

    @Test fun `a migrated install has the new safeguards on`() {
        val s = ScanSettings(schema = 17).migrate()
        assertTrue(Safeguards.ALL.first { it.key == "propHaircut" }.isOn(s))
        assertTrue(Safeguards.ALL.first { it.key == "profitProven" }.isOn(s))
    }
}
