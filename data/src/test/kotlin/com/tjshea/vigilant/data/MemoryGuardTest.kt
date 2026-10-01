package com.tjshea.vigilant.data

import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The heap watch (Tj's v0.38.0 Diagnostics, 2026-10-01: an OutOfMemoryError at the 256 MB limit mid-scan). */
class MemoryGuardTest {

    private class Fake(var used: Long, val max: Long = 100L * 1024 * 1024, val afterCollect: Long? = null) : MemoryGuard.Probe {
        var collections = 0
        override fun used() = used
        override fun max() = max
        override fun collect() { collections++; afterCollect?.let { used = it } }
    }

    private val mb = 1024L * 1024L

    @Before fun fresh() = MemoryGuard.useRealProbe()

    @After fun restore() = MemoryGuard.useRealProbe()

    @Test
    fun `the heap is a share of its limit, said in megabytes`() {
        MemoryGuard.probe = Fake(used = 142 * mb, max = 512 * mb)
        assertEquals(142L, MemoryGuard.usedMb())
        assertEquals(512L, MemoryGuard.maxMb())
        assertEquals(142.0 / 512.0, MemoryGuard.fraction(), 1e-12)
        assertEquals("heap 142 of 512 MB (28%)", MemoryGuard.text())
    }

    @Test
    fun `it presses at 75 percent and is critical only if 90 percent is still used after a collection`() {
        val low = Fake(50 * mb)
        MemoryGuard.probe = low
        assertFalse(MemoryGuard.pressing())
        assertFalse(MemoryGuard.critical(nowMs = 1_000_000L))
        assertEquals("no collection while the heap is fine", 0, low.collections)
        MemoryGuard.probe = Fake(76 * mb)
        assertTrue(MemoryGuard.pressing())
        assertFalse("pressing isn't critical", MemoryGuard.critical(nowMs = 2_000_000L))
        // 95% looks critical, but it was garbage: a collection takes it to 40% and the scan goes on.
        val garbage = Fake(95 * mb, afterCollect = 40 * mb)
        MemoryGuard.probe = garbage
        assertFalse(MemoryGuard.critical(nowMs = 3_000_000L))
        assertEquals(1, garbage.collections)
        // 95% that stays 95% after the collection is the real thing.
        val full = Fake(95 * mb, afterCollect = 95 * mb)
        MemoryGuard.probe = full
        assertTrue(MemoryGuard.critical(nowMs = 4_000_000L))
        assertEquals(1, full.collections)
    }

    @Test
    fun `a collection is forced at most every ten seconds`() {
        val full = Fake(95 * mb, afterCollect = 95 * mb)
        MemoryGuard.probe = full
        assertTrue(MemoryGuard.critical(nowMs = 10_000_000L))
        assertTrue(MemoryGuard.critical(nowMs = 10_003_000L))
        assertTrue(MemoryGuard.critical(nowMs = 10_009_000L))
        assertEquals(1, full.collections)
        assertTrue(MemoryGuard.critical(nowMs = 10_010_001L))
        assertEquals(2, full.collections)
    }

    /** The fallback result a scan keeps in case it fails is held softly: a whole priced scan beside the new one is what filled the heap. */
    @Test
    fun `the scan runner holds the last result softly while a new scan builds its own`() {
        val src = java.io.File("src/main/kotlin/com/tjshea/vigilant/data/scanner/ScanRunner.kt").readText()
        assertTrue(src.contains("val before = java.lang.ref.SoftReference(_state.value.result?.takeIf { !it.partial })"))
        assertTrue(src.contains("result = done?.result ?: before.get(),"))
        // And a big scan's partials: every one prices the whole plan, so the scanner spaces them out (StreamingScanTest has the behaviour).
        val scanner = java.io.File("src/main/kotlin/com/tjshea/vigilant/data/scanner/Scanner.kt").readText()
        assertTrue(scanner.contains("if (shown.markets.size >= bigPlanMarkets) {"))
        assertEquals(400, com.tjshea.vigilant.data.scanner.Scanner.BIG_PLAN_MARKETS)
        assertEquals(2_000L, com.tjshea.vigilant.data.scanner.Scanner.PUBLISH_MIN_MS)
    }
}
