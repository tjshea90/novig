package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine's own scan notes (2026-10-15 03:51Z), on a runner Novig's region screen refused: pass 1 read 1,442 books and 84 hot re-reads; passes 2 and 3 read 1,277 and 1,154 hot re-reads and only 382 and 228 other
 * books. The hot refresh took the whole public route. [HotBudget] keeps it to a quarter of whatever the route can do.
 */
class HotBudgetTest {
    @Test
    fun `before any speed is known a step is small, then it follows the route`() {
        assertEquals(HotBudget.FIRST, HotBudget.reads(0.0, 45))
        assertEquals(HotBudget.FIRST, HotBudget.reads(Double.NaN, 45))
        assertEquals("the key's 16 a second: capped at the most", HotBudget.MAX, HotBudget.reads(16.0, 45))
        assertEquals("the public route's 1.5 a second: 16 books, about 11 s of every 56", 16, HotBudget.reads(1.5, 45))
        assertEquals("a crawl still refreshes the likeliest few", HotBudget.MIN, HotBudget.reads(0.2, 45))
    }

    @Test
    fun `a step is measured by the books it read and the time it took`() {
        assertEquals(1.5, HotBudget.rate(15, 10_000L)!!, 1e-9)
        assertNull(HotBudget.rate(0, 10_000L)); assertNull(HotBudget.rate(15, 0L))
    }

    @Test
    fun `whatever the route's speed, the hot refresh keeps well under half of the time, and the base scan gets the rest`() {
        for (routeRate in listOf(0.3, 0.8, 1.5, 2.0, 4.0, 8.0, 16.0, 30.0)) {
            // Simulate hotStep: a first step of FIRST books, then steps sized from the last one's measured speed, each starting [every] after the previous one ENDED (lastHotAt is set at the end).
            val every = 45
            var rate = 0.0
            var hotMs = 0.0
            var totalMs = 0.0
            repeat(40) {
                val n = HotBudget.reads(rate, every)
                val took = n / routeRate * 1000.0
                hotMs += took; totalMs += took + every * 1000.0
                rate = HotBudget.rate(n, took.toLong()) ?: rate
            }
            val share = hotMs / totalMs
            assertTrue("route $routeRate/s: the hot refresh used ${(share * 100).toInt()}% of the time", share < 0.40)
        }
    }

    @Test
    fun `the old fixed 120 books a step would have taken the whole of a public route`() {
        val oldShare = (120 / 1.5) / (120 / 1.5 + 0.0)   // 80 s of reading, the next step already due: no time left for anything else
        assertEquals(80.0, oldShare, 1e-9)
        val newStepSeconds = HotBudget.reads(1.5, 45) / 1.5
        assertTrue("a step is ${newStepSeconds}s now", newStepSeconds < 15.0)
    }
}
