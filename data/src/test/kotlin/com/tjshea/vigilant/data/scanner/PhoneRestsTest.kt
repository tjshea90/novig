package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PB9 (Tj, 2026-10-25): "If I swipe it closed and don't have it open, the GitHub engine should still run the system by itself ... I want no battery or CPU or RAM wasted when the app isn't open."
 * While the engine scans and trades, the phone's background auto-scan runs nothing and holds no wake lock; with the engine off or not trading, the phone works as before.
 */
class PhoneRestsTest {
    private val phone = ScanSettings(autoScan = AutoScanMode.BOTH, autoScanSeconds = 30, autoScanKeepAwake = true)
    private fun engine(mode: CloudTradeMode) = phone.copy(gates = phone.gates.copy(cloudScan = true, cloudTrading = mode))

    @Test
    fun `the engine scanning and trading rests the phone's service and wake lock`() {
        for (mode in listOf(CloudTradeMode.PAPER, CloudTradeMode.LIVE)) {
            val s = engine(mode)
            assertTrue(s.phoneRests)
            assertEquals(AutoScanMode.OFF, s.phoneAutoScan)
            assertFalse(KeepAwake.active(s))
            // The setting itself is kept: switching the engine off brings the phone's background scan back as Tj set it.
            assertEquals(AutoScanMode.BOTH, s.autoScan)
        }
    }

    @Test
    fun `with the engine off, not trading, paused or STOP ALL the phone runs as before`() {
        assertFalse(phone.phoneRests)
        assertEquals(phone.activeAutoScan, phone.phoneAutoScan)
        assertTrue(KeepAwake.active(phone))
        assertFalse(engine(CloudTradeMode.OFF).phoneRests)
        assertFalse(engine(CloudTradeMode.LIVE).copy(pausedByHand = true).phoneRests)
        assertFalse(engine(CloudTradeMode.LIVE).copy(killed = true).phoneRests)
    }
}
