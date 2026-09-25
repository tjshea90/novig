package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scan notifications' words (the scan runs in the background while Tj is in another app). */
class ScanTextTest {

    @Test
    fun `progress reads like the status line, with what's been found so far`() {
        assertEquals("Starting…", ScanText.progress(null, 0))
        assertEquals("Novig prices 45/300 · 3 +EV so far", ScanText.progress(ScanProgress("Novig prices", 45, 300), 3))
        assertEquals("Novig board and fair odds", ScanText.progress(ScanProgress("Novig board and fair odds"), 0))
    }

    @Test
    fun `the done note counts the bets and names the best one`() {
        val s = SampleScan.state()
        val (title, text) = ScanText.done(s.feed, s.settings.minEvPercent, emptyList())
        assertTrue(title, s.feed.size > 1 && title == "Scan done: ${s.feed.size} +EV bets")
        val best = s.feed.maxBy { it.evPercent!! }
        assertTrue(text, text.startsWith("Best: ${best.selection}"))

        val (none, why) = ScanText.done(emptyList(), 0.01, listOf("Kalshi is limiting requests right now"))
        assertEquals("Scan done: no +EV right now", none)
        assertEquals("Nothing at or above +1.0% EV.\nKalshi is limiting requests right now", why)
    }
}
