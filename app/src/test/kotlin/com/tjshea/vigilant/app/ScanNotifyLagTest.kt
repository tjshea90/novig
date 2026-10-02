package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tj, 2026-10-02 (v0.44.1): "When I scan with vigilant scanner, the entire app becomes laggy still." The scan's notification was built on the
 * main thread twice a second, and each time it screened every priced side (8,660 in his last scan) and rebuilt the placed-bets index from his
 * 430 tracked bets: ~2.6 ms median and 19 ms at the 90th percentile on a server core (GC), several times that on a Moto G, on whatever tab was
 * open. Now the count is made once per new result, off the main thread.
 */
class ScanNotifyLagTest {

    private val result = SampleScan.result()
    private val settings = SampleScan.settings

    @Test
    fun `the found count is worked out once per new result, not once per progress tick`() {
        var counted = 0
        val found = FoundCount { r, s -> counted++; r.feed(s).size }
        val run = ScanRun(scanning = true, progress = ScanProgress("Novig prices"), result = result, settings = settings)
        val n = result.feed(settings).size
        repeat(20) { i -> assertEquals(n, found.of(run.copy(progress = ScanProgress("Novig prices", i, 4588)))) }
        assertEquals("20 ticks with the same result: one count", 1, counted)
        // A new partial result, or new settings: counted again.
        found.of(run.copy(result = result.copy(computedAtMs = result.computedAtMs + 1)))
        assertEquals(2, counted)
        found.of(run.copy(result = result.copy(computedAtMs = result.computedAtMs + 1), settings = settings.copy(minEvPercent = 0.02)))
        assertEquals(3, counted)
        // No result yet: nothing to count.
        assertEquals(0, found.of(ScanRun(scanning = true)))
        assertEquals(3, counted)
    }

    @Test
    fun `the service watches the scan off the main thread and builds no count there`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/ScanService.kt").readText()
        val watch = src.substringAfter("watch = scope.launch {").substringBefore("return START_NOT_STICKY")
        // The progress (and its count) is built upstream of flowOn(Default); only the "ended?" check runs on the main thread.
        assertTrue(watch.indexOf(".onEach { if (it.scanning) updateProgress(it) }") in 0 until watch.indexOf(".flowOn(Dispatchers.Default)"))
        assertTrue(watch.indexOf(".flowOn(Dispatchers.Default)") < watch.indexOf(".first { !it.scanning }"))
        assertTrue(watch.contains("withContext(Dispatchers.Default) { doneNotificationFor(ended, onScreen) }"))
        // The notification itself takes the count; it never screens the feed.
        val build = src.substringAfter("private fun progressNotification(").substringBefore(".build()")
        assertFalse(build.contains(".feed("))
        assertTrue(src.contains("progressNotification(run, found.of(run))"))
    }
}
