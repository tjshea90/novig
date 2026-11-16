package com.tjshea.vigilant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTraceTest {
    @Test fun cleanStopSaysNothing() {
        assertNull(LaunchTrace.summarize(listOf("process 1000", "screen-create 2000", "on-screen 3000", "stopped 9000")))
    }

    @Test fun nothingSaysNothing() {
        assertNull(LaunchTrace.summarize(emptyList()))
        assertNull(LaunchTrace.summarize(listOf("garbage")))
    }

    @Test fun endedDuringStartupIsNamed() {
        val s = LaunchTrace.summarize(listOf("process 1000", "screen-create 2000"))
        assertNotNull(s)
        assertTrue(s!!, s.contains("during startup") && s.contains("lived 1 s"))
    }

    @Test fun endedOnScreenIsNamed() {
        val s = LaunchTrace.summarize(listOf("process 0", "screen-create 1000", "on-screen 20000"))
        assertEquals("the process was ended while Vigilant was on screen (lived 20 s after it started): closed by Android or crashed without a record", s)
    }

    @Test fun reopenedThenStoppedIsClean() {
        assertNull(LaunchTrace.summarize(listOf("process 0", "on-screen 1000", "stopped 2000", "on-screen 3000", "stopped 4000")))
    }
}
