package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live games only mode (Tj, 2026-10-11): on switches the rest off, off puts every switch back exactly and turns Include live games off. */
class LiveOnlyTest {
    private fun busy() = ApiEntry.entries.fold(
        ScanSettings(
            scanner = ScannerMode.BOTH, autoScan = AutoScanMode.BOTH, maker = true, autoBet = true, burstRecorder = true, feedRace = true,
            altLab = true, researchMode = true, burstTrade = true, includeLive = false,
        ),
    ) { s, e -> e.setOn(s, true) }

    @Test fun `on leaves only Pinnodds and Novig running`() {
        val n = LiveOnly.enter(busy())
        assertTrue(n.liveOnly && n.includeLive)
        ApiEntry.entries.filter { it != ApiEntry.PINNODDS }.forEach { assertFalse("${it.name} off", it.isOn(n)) }
        assertTrue(ApiEntry.PINNODDS.isOn(n))
        assertFalse(n.cnoOn)
        assertEquals(AutoScanMode.OFF, n.autoScan)
        assertFalse(n.maker || n.autoBet || n.burstRecorder || n.feedRace || n.altLab || n.researchMode || n.burstTrade)
    }

    @Test fun `off brings back what was on, and turns Include live games off`() {
        val before = busy().copy(includeLive = true)
        val back = LiveOnly.leave(LiveOnly.enter(before))
        assertEquals(before.copy(includeLive = false), back)
        assertFalse(back.liveOnly)
    }

    @Test fun `an API that was off stays off, and the Pinnacle gates come back as they were`() {
        val before = ScanSettings(usePinnacle = false, sgoPro = true, scanner = ScannerMode.CNO).let { it.copy(gates = it.gates.copy(usePinnwire = true)) }
        assertEquals(before, LiveOnly.leave(LiveOnly.enter(before)))
    }

    @Test fun `entering twice or leaving when off changes nothing`() {
        val on = LiveOnly.enter(busy())
        assertEquals(on, LiveOnly.enter(on))
        assertEquals(busy(), LiveOnly.leave(busy()))
    }
}
