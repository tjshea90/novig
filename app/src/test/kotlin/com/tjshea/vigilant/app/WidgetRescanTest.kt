package com.tjshea.vigilant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vigilant's scan again by itself while the widget is open (Tj, 2026-09-27), only when asked for. */
class WidgetRescanTest {

    private val min = 60_000L

    @Test
    fun `off means never, never scanned means now, otherwise N minutes after the last scan`() {
        assertNull(WidgetRescan.dueInMs(0, null, null, 0L))
        assertEquals(0L, WidgetRescan.dueInMs(10, null, null, 5 * min))
        assertEquals(7 * min, WidgetRescan.dueInMs(10, lastScanMs = 3 * min, lastStartedMs = null, now = 6 * min))
        assertEquals(0L, WidgetRescan.dueInMs(10, lastScanMs = 0L, lastStartedMs = null, now = 11 * min))
    }

    @Test
    fun `a scan that failed (no new result) still waits a full interval from when it started`() {
        // Last good scan 30 min ago; a rescan started 1 min ago failed: next one in 9 minutes, not now.
        assertEquals(9 * min, WidgetRescan.dueInMs(10, lastScanMs = 0L, lastStartedMs = 29 * min, now = 30 * min))
    }

    @Test
    fun `switching Vigilant's scan on from the widget scans at once only when the last scan is old or missing`() {
        assertTrue(WidgetRescan.scanOnSwitch(null, 0L))
        assertFalse(WidgetRescan.scanOnSwitch(lastScanMs = 0L, now = 2 * min))
        assertTrue(WidgetRescan.scanOnSwitch(lastScanMs = 0L, now = 6 * min))
    }
}
