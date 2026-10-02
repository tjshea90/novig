package com.tjshea.vigilant.data.diag

import org.junit.Assert.assertEquals
import org.junit.Test

/** The frame meter's counts (Tj, 2026-10-02: "When I scan with vigilant scanner, the entire app becomes laggy still"). */
class FrameStatsTest {

    @Test
    fun `slow is over twice the deadline, frozen over 700 ms, each kept by what was running`() {
        val f = FrameStats()
        // 120 Hz: an 8.3 ms deadline.
        f.add(FrameStats.SCAN, 8.0, 8.3)
        f.add(FrameStats.SCAN, 16.6, 8.3) // just under twice: not slow
        f.add(FrameStats.SCAN, 16.7, 8.3) // over twice: slow
        f.add(FrameStats.SCAN, 701.0, 8.3) // slow and frozen
        f.add(FrameStats.QUIET, 7.0, 8.3)
        val s = f.snapshot()
        val scan = s.getValue(FrameStats.SCAN)
        assertEquals(4L, scan.frames)
        assertEquals(2L, scan.slow)
        assertEquals(1L, scan.frozen)
        assertEquals(0.5, scan.slowShare, 1e-9)
        assertEquals(701.0, scan.durations.max, 1e-9)
        assertEquals(1L, s.getValue(FrameStats.QUIET).frames)
        assertEquals(0L, s.getValue(FrameStats.QUIET).slow)
    }

    @Test
    fun `a frame is filed under the most telling thing running`() {
        assertEquals(FrameStats.SCAN, FrameStats.activity(scanning = true, checking = true, cnoReading = true))
        assertEquals(FrameStats.CHECK, FrameStats.activity(scanning = false, checking = true, cnoReading = true))
        assertEquals(FrameStats.CNO, FrameStats.activity(scanning = false, checking = false, cnoReading = true))
        assertEquals(FrameStats.QUIET, FrameStats.activity(scanning = false, checking = false, cnoReading = false))
    }
}
