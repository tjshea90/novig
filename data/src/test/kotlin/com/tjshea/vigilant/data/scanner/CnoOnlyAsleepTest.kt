package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.ReferenceSource
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-29: "Ensure that if I have cno only turned on in the settings that it doesn't scan vigilant in the background and waste api usage."
 * The scanner choice is the master switch: CNO only puts Vigilant's scan and every API behind it to sleep, in the app and in the background.
 */
class CnoOnlyAsleepTest {

    @Test
    fun `what a background cycle reads follows the scanner choice, never more than it allows`() {
        fun s(scanner: ScannerMode, auto: AutoScanMode, paused: Boolean = false) = ScanSettings(scanner = scanner, autoScan = auto, pausedByHand = paused)
        // Both scanners on: auto-scan does what it says.
        assertTrue(s(ScannerMode.BOTH, AutoScanMode.CNO).autoScansCno)
        assertFalse(s(ScannerMode.BOTH, AutoScanMode.CNO).autoScansVigilant)
        assertTrue(s(ScannerMode.BOTH, AutoScanMode.BOTH).autoScansCno && s(ScannerMode.BOTH, AutoScanMode.BOTH).autoScansVigilant)
        // CNO only: Vigilant's scan is never run in the background, whatever auto-scan says (this is Tj's rule).
        for (auto in AutoScanMode.entries) assertFalse("CNO only + $auto", s(ScannerMode.CNO, auto).autoScansVigilant)
        assertTrue(s(ScannerMode.CNO, AutoScanMode.BOTH).autoScansCno)
        assertEquals(AutoScanMode.BOTH, s(ScannerMode.CNO, AutoScanMode.BOTH).activeAutoScan) // still runs: for CNO
        // Vigilant only: CNO is asleep the same way, so a CNO-only auto-scan has nothing to do and stops.
        assertFalse(s(ScannerMode.VIGILANT, AutoScanMode.CNO).autoScansCno)
        assertEquals(AutoScanMode.OFF, s(ScannerMode.VIGILANT, AutoScanMode.CNO).activeAutoScan)
        assertTrue(s(ScannerMode.VIGILANT, AutoScanMode.BOTH).autoScansVigilant)
        assertFalse(s(ScannerMode.VIGILANT, AutoScanMode.BOTH).autoScansCno)
        // Off, or paused: nothing.
        for (scanner in ScannerMode.entries) {
            assertEquals(AutoScanMode.OFF, s(scanner, AutoScanMode.OFF).activeAutoScan)
            assertEquals(AutoScanMode.OFF, s(scanner, AutoScanMode.BOTH, paused = true).activeAutoScan)
            assertFalse(s(scanner, AutoScanMode.BOTH, paused = true).autoScansVigilant)
        }
    }

    /** A scanner that only counts what it is asked to scan. */
    private class CountingScanner : OddsScanner {
        var scans = 0
        override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): ScanReport {
            scans++
            return ScanReport(null, emptyList(), null, 0, 0, 0, 0, null, emptyList(), null)
        }
        override suspend fun recheck(settings: ScanSettings, marketIds: Collection<String>, onProgress: (Int, Int) -> Unit) = RecheckReport(null, 0, 0, null)
        override suspend fun reprice(settings: ScanSettings): ScanResult? = null
        override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = emptySet()
    }

    @Test
    fun `the runner never starts a Vigilant scan with the scanner on CNO only, whoever asks`() = runTest {
        val scanner = CountingScanner()
        val runner = ScanRunner(scanner, this)
        val cnoOnly = ScanSettings(scanner = ScannerMode.CNO)
        assertFalse(runner.start(cnoOnly, emptyList()))
        assertFalse(runner.start(cnoOnly, emptyList(), pinned = setOf("a-market")) { })
        advanceUntilIdle()
        assertEquals(0, scanner.scans)
        assertFalse(runner.running)
        assertEquals(0, runner.state.value.finished)
        // Vigilant on (both, or Vigilant only): the same call scans.
        assertTrue(runner.start(ScanSettings(scanner = ScannerMode.BOTH), emptyList()))
        advanceUntilIdle()
        assertEquals(1, scanner.scans)
        assertTrue(runner.start(ScanSettings(scanner = ScannerMode.VIGILANT), emptyList()))
        advanceUntilIdle()
        assertEquals(2, scanner.scans)
    }
}
