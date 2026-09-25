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

    @Test
    fun `steady success ramps the pace up to its ceiling, and one refusal starts it over`() = runTest {
        val g = RateGate(4.0, 10, clock = { now }, sleep = { sleeps += it; now += it }, maxRate = 6.0, rampEvery = 10, rampStep = 0.5)
        assertEquals(4.0, g.currentRate, 0.0)
        repeat(10) { g.success() }
        assertEquals(4.5, g.currentRate, 0.0)
        repeat(100) { g.success() }
        assertEquals(6.0, g.currentRate, 0.0) // never past the ceiling
        g.slowDown()
        assertEquals(3.0, g.currentRate, 0.0) // half of where it was
        repeat(50) { g.success() }
        assertEquals(3.0, g.currentRate, 0.0) // no ramping while slowed down
        now += 61_000
        assertEquals(4.0, g.currentRate, 0.0) // back to the starting pace, not the old ceiling
    }

    @Test
    fun `a gate without a ceiling never speeds up`() = runTest {
        val g = gate(4.0, 10)
        repeat(500) { g.success() }
        assertEquals(4.0, g.currentRate, 0.0)
    }

    @Test
    fun `a quiet spell forgets the ramp`() = runTest {
        val g = RateGate(4.0, 10, clock = { now }, sleep = { sleeps += it; now += it }, maxRate = 6.0, rampEvery = 1, rampStep = 1.0)
        g.acquire()
        repeat(2) { g.success() }
        assertEquals(6.0, g.currentRate, 0.0)
        now += 6 * 60_000
        g.acquire()
        assertEquals(4.0, g.currentRate, 0.0)
    }
}
