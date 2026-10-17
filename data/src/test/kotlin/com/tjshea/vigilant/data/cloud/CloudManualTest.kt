package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class CloudManualTest {
    private fun et(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, d, h, m, 0, 0, EngineHours.ZONE).toInstant().toEpochMilli()

    @Test
    fun `a run opens the window outside its hours and lapses by itself`() {
        val night = et(17, 3)
        val (run, stop) = CloudManual.runFields(night)
        assertEquals(0L, stop)
        assertTrue(run > night && run <= night + CloudManual.RUN_MAX_MS + 60_000L)
        val c = CloudConfig(manualRunUntilMs = run)
        assertFalse(CloudManual.open(true, CloudConfig(), night))
        assertTrue(CloudManual.open(true, c, night))
        assertTrue("the hours alone take over at 08:00", CloudManual.open(true, CloudConfig(manualRunUntilMs = run), et(17, 8)))
        assertFalse("and the run has lapsed by 14:00 with the hours off again only if they were closed", run > et(17, 8) + 120_000L)
    }

    @Test
    fun `a run inside the hours changes nothing, and with the hours switch off the engine is always open`() {
        assertEquals(0L to 0L, CloudManual.runFields(et(17, 14)))
        assertTrue(CloudManual.open(false, CloudConfig(), et(17, 3)))
    }

    @Test
    fun `a stop inside the hours lasts until the next scheduled opening, never longer`() {
        val noon = et(17, 12)
        assertEquals(et(18, 8), CloudManual.stopUntil(noon))
        assertEquals("after midnight the same morning's 08:00", et(17, 8), CloudManual.stopUntil(et(17, 0, 30)))
        assertEquals("closed now: a stop only ends a manual run", 0L, CloudManual.stopUntil(et(17, 3)))
        val c = CloudConfig(manualStopUntilMs = CloudManual.stopFields(noon).second)
        assertTrue(CloudManual.stopActive(c, noon))
        assertTrue(CloudManual.stopActive(c, et(18, 7, 59)))
        assertFalse("the schedule resumes at 08:00", CloudManual.stopActive(c, et(18, 8)))
    }

    @Test
    fun `run clears a stop and stop clears a run`() {
        assertEquals(0L, CloudManual.runFields(et(17, 3)).second)
        assertEquals(0L, CloudManual.stopFields(et(17, 12)).first)
    }

    @Test
    fun `the words say what is happening and nothing when the schedule decides`() {
        assertNull(CloudManual.words(CloudConfig(), et(17, 12)))
        assertTrue(CloudManual.words(CloudConfig(manualStopUntilMs = et(18, 8)), et(17, 12))!!.contains("8:00 am ET"))
        assertTrue(CloudManual.words(CloudConfig(manualRunUntilMs = et(17, 15)), et(17, 3))!!.startsWith("Running by your request"))
    }
}
