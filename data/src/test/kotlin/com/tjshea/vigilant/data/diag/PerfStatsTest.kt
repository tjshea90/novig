package com.tjshea.vigilant.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerfStatsTest {

    @Test
    fun `the cold start is the first one noted, and only for a process the screen started`() {
        val p = PerfStats()
        p.noteColdStart(3_600_000L, 20_000L)
        assertNull("a process started long ago by a service is not a cold start", p.coldStartMs)
        p.noteColdStart(20_000L, 20_000L)
        assertNull(p.coldStartMs)
        p.noteColdStart(1_400L, 20_000L)
        assertEquals(1_400L, p.coldStartMs)
        p.noteColdStart(900L, 20_000L)
        assertEquals("the first one stays", 1_400L, p.coldStartMs)
    }

    @Test
    fun `named timings are summarised, newest 200 kept, and an unknown name is empty`() {
        val p = PerfStats()
        (1..250).forEach { p.add("cycle.ms", it.toDouble()) }
        val s = p.summary("cycle.ms")
        assertEquals(PerfStats.CAP, s.count)
        assertEquals(250.0, s.max, 0.0)
        assertEquals(0, p.summary("nothing").count)
        assertEquals(listOf("cycle.ms"), p.summaries().keys.toList())
    }
}
