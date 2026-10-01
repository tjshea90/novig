package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02: "keep it alive robustly to keep auto bet on and scanning even if the phone is idle and the screen is turned off and locked".
 * The rules for when the service holds the CPU awake and how far off its safety alarm is (RESEARCH.md §59).
 */
class KeepAwakeTest {

    private val on = ScanSettings(autoScan = AutoScanMode.CNO, autoScanSeconds = 5)

    @Test
    fun `keep awake is on by default, and holds the CPU for every interval under 9 minutes and none at 9 minutes or more`() {
        assertTrue(ScanSettings().autoScanKeepAwake)
        // The choices: 5 s up to 5 min keep the CPU awake; 10 min and slower rely on an alarm (Doze lets one through about every 9 minutes).
        assertEquals(
            listOf(true, true, true, true, true, true, false, false, false, false),
            ScanSettings.AUTO_SCAN_SECONDS_CHOICES.map { KeepAwake.active(on.copy(autoScanSeconds = it)) },
        )
        assertTrue(KeepAwake.active(on.copy(autoScanSeconds = 539)))
        assertFalse(KeepAwake.active(on.copy(autoScanSeconds = 540)))
    }

    @Test
    fun `nothing is held while auto-scan runs nothing, or the switch is off`() {
        assertFalse(KeepAwake.active(on.copy(autoScan = AutoScanMode.OFF)))
        // Paused, or a scanner choice that leaves the background nothing to read: activeAutoScan is OFF, so no lock for nothing.
        assertFalse(KeepAwake.active(on.copy(paused = true)))
        assertFalse(KeepAwake.active(on.copy(scanner = ScannerMode.VIGILANT)))
        assertFalse(KeepAwake.active(on.copy(autoScanKeepAwake = false)))
        assertTrue(KeepAwake.active(on.copy(autoScan = AutoScanMode.BOTH, scanner = ScannerMode.BOTH)))
    }

    @Test
    fun `the safety alarm is three intervals off but never under three minutes, and always after the next cycle`() {
        assertEquals(3 * 60_000L, KeepAwake.watchdogDelayMs(5))
        assertEquals(3 * 60_000L, KeepAwake.watchdogDelayMs(60))
        assertEquals(9 * 60_000L, KeepAwake.watchdogDelayMs(180))
        assertEquals(15 * 60_000L, KeepAwake.watchdogDelayMs(300))
        for (seconds in ScanSettings.AUTO_SCAN_SECONDS_CHOICES) {
            assertTrue("$seconds s", KeepAwake.watchdogDelayMs(seconds) > seconds * 1_000L)
        }
        assertEquals(1_000_000L + 180_000L, KeepAwake.watchdogAtMs(1_000_000L, 5))
    }

    @Test
    fun `the safety alarm is moved on only after a third of its delay has gone, never every few seconds`() {
        val now = 10_000_000L
        val delay = KeepAwake.watchdogDelayMs(5)
        assertTrue(KeepAwake.rearmDue(null, now, 5))
        // Just armed: nothing to do at a 5 s cadence.
        assertFalse(KeepAwake.rearmDue(now + delay, now + 5_000L, 5))
        assertFalse(KeepAwake.rearmDue(now + delay, now + delay / 3 - 1, 5))
        // A third gone (a minute at the 3 minute delay): moved on.
        assertTrue(KeepAwake.rearmDue(now + delay, now + delay / 3 + 1, 5))
        // Overdue (the loop stalled and woke): armed again.
        assertTrue(KeepAwake.rearmDue(now - 1, now, 5))
    }

    @Test
    fun `the CPU lock has a timeout so a dead service can't hold it for ever, and is renewed well before it ends`() {
        assertEquals(15 * 60_000L, KeepAwake.LOCK_TIMEOUT_MS)
        assertTrue(KeepAwake.LOCK_RENEW_BEFORE_MS < KeepAwake.LOCK_TIMEOUT_MS)
        val now = 5_000_000L
        assertTrue(KeepAwake.lockRenewDue(null, now))
        assertFalse(KeepAwake.lockRenewDue(now + KeepAwake.LOCK_TIMEOUT_MS, now))
        assertFalse(KeepAwake.lockRenewDue(now + KeepAwake.LOCK_RENEW_BEFORE_MS + 1, now))
        assertTrue(KeepAwake.lockRenewDue(now + KeepAwake.LOCK_RENEW_BEFORE_MS - 1, now))
        // The loop wakes at least every 30 s, so a renewal can't be missed by the five minutes it has.
        assertNotEquals(0L, KeepAwake.LOCK_RENEW_BEFORE_MS)
    }
}
