package com.tjshea.vigilant.data.scanner

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-28: "Make an option in the app to pause all scanning". One saved switch; the scanner and auto-scan
 * choices are kept for when it resumes.
 */
class PauseScanningTest {

    @Test
    fun `scanning isn't paused by default, and a file saved before the switch existed loads unpaused`() {
        assertFalse(ScanSettings().paused)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = json.encodeToString(ScanSettings.serializer(), ScanSettings()).replace("\"paused\":false,", "")
        assertFalse(old.contains("paused"))
        assertFalse(json.decodeFromString(ScanSettings.serializer(), old).paused)
        // Saved and read back paused.
        val saved = json.encodeToString(ScanSettings.serializer(), ScanSettings(paused = true))
        assertTrue(json.decodeFromString(ScanSettings.serializer(), saved).paused)
    }

    @Test
    fun `while paused background auto-scan does nothing, and resuming brings back the choice`() {
        val on = ScanSettings(autoScan = AutoScanMode.BOTH)
        assertEquals(AutoScanMode.BOTH, on.activeAutoScan)
        val paused = on.copy(pausedByHand = true)
        assertEquals(AutoScanMode.OFF, paused.activeAutoScan)
        assertEquals(AutoScanMode.BOTH, paused.autoScan)
        assertEquals(AutoScanMode.BOTH, paused.copy(pausedByHand = false).activeAutoScan)
        // The scanner choice is untouched too.
        assertEquals(ScannerMode.BOTH, paused.scanner)
    }
}
