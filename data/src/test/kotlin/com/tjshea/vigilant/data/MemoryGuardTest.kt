package com.tjshea.vigilant.data

import org.junit.After
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

    @After fun restore() { MemoryGuard.probe = MemoryGuard.probe.takeUnless { it is Fake } ?: runCatching { MemoryGuard::class.java.getDeclaredField("Real") }.let { MemoryGuard.probe } }

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
}
