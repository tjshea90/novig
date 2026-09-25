package com.tjshea.vigilant.data.novig

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RateGateTest {

    private var now = 0L
    private val sleeps = ArrayList<Long>()
    private fun gate(rate: Double, burst: Int) = RateGate(rate, burst, clock = { now }, sleep = { sleeps += it; now += it })

    @Test
    fun `a burst goes out at once, then requests are spaced at the rate`() = runTest {
        val g = gate(4.0, 10)
        repeat(10) { g.acquire() }
        assertEquals(0L, now)
        repeat(4) { g.acquire() }
        assertEquals(1_000L, now) // four more at 4/s = one second
    }

    @Test
    fun `a pause holds everything until the server's time`() = runTest {
        val g = gate(4.0, 10)
        g.pause(2_000)
        g.acquire()
        assertEquals(2_000L, now)
    }

    @Test
    fun `after a refusal the rate halves and bursts stop, then recovers`() = runTest {
        val g = gate(4.0, 10)
        g.slowDown()
        assertEquals(2.0, g.currentRate, 0.0)
        repeat(3) { g.acquire() }
        // One token left from the capped bucket, then 2/s: 0.5s apart.
        assertEquals(1_000L, now)
        g.slowDown()
        assertEquals(1.0, g.currentRate, 0.0)
        now += 61_000
        assertEquals(4.0, g.currentRate, 0.0)
    }
}
