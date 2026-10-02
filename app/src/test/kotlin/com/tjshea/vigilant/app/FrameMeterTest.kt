package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.diag.FrameStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tj, 2026-10-02: "When I scan with vigilant scanner, the entire app becomes laggy still." His phone measures it from now on: every frame the
 * screen draws is timed by Android and filed by what was running, and Diagnostics says how many were slow during a scan against with nothing running.
 */
class FrameMeterTest {

    private fun stats(scanSlow: Int, quietSlow: Int, n: Int = 1000): Map<String, FrameStats.Bucket> {
        val f = FrameStats()
        repeat(n) { i -> f.add(FrameStats.SCAN, if (i < scanSlow) 40.0 else 6.0, 8.3) }
        repeat(n) { i -> f.add(FrameStats.QUIET, if (i < quietSlow) 40.0 else 6.0, 8.3) }
        return f.snapshot()
    }

    @Test
    fun `Diagnostics lists the frames by what was running, the scan first`() {
        val lines = DiagnosticsFile.frameLines(stats(scanSlow = 120, quietSlow = 10))
        assertTrue(lines[0], lines[0].startsWith("Screen frames this run, by what was running"))
        assertTrue(lines[1], lines[1].startsWith("  a Vigilant scan running: 1000 frames · 12.0% slow (120) · 0 frozen"))
        assertTrue(lines[2], lines[2].startsWith("  none of those running: 1000 frames · 1.0% slow (10)"))
        assertEquals(listOf("Screen frames: none timed yet this run (timed while Vigilant is in front)."), DiagnosticsFile.frameLines(emptyMap()))
    }

    @Test
    fun `a scan that makes the screen stutter is a finding, one that doesn't isn't`() {
        val f = Advisor.frameFinding(stats(scanSlow = 120, quietSlow = 10))!!
        assertEquals("perf:frames:scan", f.key)
        assertEquals("OPTIMIZE", f.kind)
        assertTrue(f.title, f.title.startsWith("The screen stutters with a Vigilant scan running: 12.0% of its frames are slow, 1.0% with nothing running"))
        // As smooth as with nothing running, or too few frames to say: nothing.
        assertNull(Advisor.frameFinding(stats(scanSlow = 60, quietSlow = 50)))
        assertNull(Advisor.frameFinding(stats(scanSlow = 20, quietSlow = 0)))
        assertNull(Advisor.frameFinding(stats(scanSlow = 100, quietSlow = 0, n = 200)))
    }

    @Test
    fun `the meter runs while the screen is in front, off the main thread, and reaches Diagnostics`() {
        val main = File("src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt").readText()
        assertTrue(main.substringAfter("override fun onResume()").substringBefore("override fun onPause()").contains("frameMeter.start()"))
        assertTrue(main.substringAfter("override fun onPause()").substringBefore("override fun onStart()").contains("frameMeter.stop()"))
        val meter = File("src/main/kotlin/com/tjshea/vigilant/app/FrameMeter.kt").readText()
        assertTrue(meter.contains("addOnFrameMetricsAvailableListener(listener, Handler(t.looper))"))
        assertTrue(meter.contains("c.frames.add(FrameStats.activity(c.runner.running, c.focus.active(), c.cno.state.value.refreshing), total, deadline)"))
        assertTrue(File("src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt").readText().contains("frames = c.frames.snapshot(),"))
        assertTrue(File("src/main/kotlin/com/tjshea/vigilant/app/DiagnosticsFile.kt").readText().contains("frameLines(x.frames).forEach(o::appendLine)"))
    }
}
