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
        val saved = json.encodeToString(ScanSettings.serializer(), ScanSettings(pausedByHand = true))
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

    /**
     * Tj, 2026-10-05: "a stop button kill switch … immediately stops all scanning, all auto betting, all auto bidding, and all background scan. If I press this,
     * everything remains off, even if I close the app and open it again, until I press resume."
     */
    @Test
    fun `the kill switch holds everything the Pause button holds, is saved, and keeps Tj's switches for when he resumes`() {
        val running = ScanSettings(
            autoScan = AutoScanMode.BOTH, autoBet = true, autoLock = true, maker = true, scanner = ScannerMode.BOTH, autoScanSeconds = 30,
        )
        // Running before: every background part is on.
        assertTrue(running.autoBetsNow && running.autoLocksNow && running.makerNow && running.autoScansCno && running.autoScansVigilant)
        assertEquals(AutoScanMode.BOTH, running.activeAutoScan)
        val killed = running.copy(killed = true, killedAtMs = 1_000L)
        // Killed: paused (so every scan, read, bet and bid that waits for a pause waits), and each background part says off.
        assertTrue(killed.paused)
        assertFalse(killed.pausedByHand)
        assertFalse(killed.autoBetsNow)
        assertFalse(killed.autoLocksNow)
        assertFalse(killed.makerNow)
        assertFalse(killed.autoScansCno)
        assertFalse(killed.autoScansVigilant)
        assertEquals(AutoScanMode.OFF, killed.activeAutoScan)
        // What Tj switched on is still on: Resume brings it all back, exactly as it was.
        assertTrue(killed.autoBet && killed.maker && killed.autoLock)
        assertEquals(running, killed.copy(killed = false, killedAtMs = null))
        assertTrue(killed.copy(killed = false, killedAtMs = null).autoBetsNow)
    }

    @Test
    fun `Pause cannot undo the kill switch, and the kill switch outlives a save and a reload`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val killed = ScanSettings(killed = true, killedAtMs = 5L, pausedByHand = true)
        // Resuming from Pause (the ▶ button, a pull to refresh, "Resume scanning") only clears the hand pause: still held.
        val resumed = killed.copy(pausedByHand = false)
        assertTrue(resumed.paused)
        assertTrue(resumed.killed)
        // The app closed and opened again reads the same file.
        val back = json.decodeFromString(ScanSettings.serializer(), json.encodeToString(ScanSettings.serializer(), killed))
        assertEquals(killed, back)
        assertTrue(back.killed && back.paused && back.pausedByHand)
        assertEquals(5L, back.killedAtMs)
        // A file from before the kill switch loads running; a file saved with only the old key `paused` still pauses.
        val old = json.encodeToString(ScanSettings.serializer(), ScanSettings()).replace("\"killed\":false,", "").replace(Regex("\"killedAtMs\":[^,]*,"), "")
        assertFalse(old.contains("killed"))
        assertFalse(json.decodeFromString(ScanSettings.serializer(), old).killed)
        assertTrue(json.decodeFromString(ScanSettings.serializer(), """{"paused":true}""").paused)
        assertFalse(json.decodeFromString(ScanSettings.serializer(), """{"paused":true}""").killed)
        // Only an explicit Resume (killed = false) lets it go.
        assertFalse(killed.copy(killed = false, killedAtMs = null, pausedByHand = false).paused)
    }
}
