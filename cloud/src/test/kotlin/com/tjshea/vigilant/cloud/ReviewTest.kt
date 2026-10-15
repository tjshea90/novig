package com.tjshea.vigilant.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The daily review (ReviewMain.kt) runs the app's own study report over the journals the phone saved to `phone-data`; the fixture is the first 400 lines of a real day (21 bets, their looks and closes). */
class ReviewTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fixture(): File = File(checkNotNull(javaClass.classLoader.getResource("review")) { "review fixture missing" }.toURI())

    @Test
    fun `the digest is the study's own summary and splits over the saved journals, without the bets' own lines`() {
        val d = Review.digest(fixture(), tmp.newFolder("work"), now = 1_792_100_000_000L)
        assertTrue(d, d.startsWith("VIGILANT DAILY REVIEW"))
        assertTrue("one day file read: $d".take(400), d.lineSequence().first().contains("1 study day files"))
        assertTrue("bets were logged: ${d.lineSequence().first()}", Regex("· (\\d+) bets logged").find(d.lineSequence().first())!!.groupValues[1].toInt() >= 15)
        assertTrue(d.contains("== READ ME FIRST (for Claude) =="))
        assertTrue(d.contains("== SUMMARY"))
        assertTrue(d.contains("== SPLITS"))
        assertFalse("the bets' own lines stay in phone-data", d.contains("<<<JSONL") || d.contains("== EVERY BET"))
        assertTrue("capped for a reader", d.length <= Review.MAX_CHARS + 400)
    }

    @Test
    fun `with no study files it says so and does not fail`() {
        val d = Review.digest(tmp.newFolder("empty"), tmp.newFolder("work2"), now = 1_792_100_000_000L)
        assertTrue(d, d.contains("No scan-study files on the phone-data branch yet"))
        assertEquals(1, d.lineSequence().count { it.startsWith("VIGILANT DAILY REVIEW") })
    }
}
