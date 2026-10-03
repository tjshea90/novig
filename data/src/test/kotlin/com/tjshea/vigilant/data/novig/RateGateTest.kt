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
        // The slow-down over: a step under the 2/s that was refused too, not straight back to 4/s.
        now += 61_000
        assertEquals(1.5, g.currentRate, 0.0)
        // Ten clean minutes later the ceiling is forgotten and steady success climbs back to the starting pace.
        now += 10 * 60_000
        repeat(40 * 6) { g.success() }
        assertEquals(4.0, g.currentRate, 0.0)
    }

    /**
     * Tj's v0.52.0 file: Novig's public edge answered 429 about once a minute (1,163 in all), each time a minute's slow-down ended and the full pace
     * came back. The pace that was refused is now remembered for ten minutes: the gate climbs back to a step under it and stays there.
     */
    @Test
    fun `after a refusal the pace climbs back to a step under the refused one and stays there for ten minutes`() = runTest {
        val g = RateGate(4.0, 10, clock = { now }, sleep = { sleeps += it; now += it }, maxRate = 6.0, rampEvery = 10, rampStep = 0.5)
        repeat(30) { g.success() }
        assertEquals(5.5, g.currentRate, 0.0)
        g.slowDown()
        now += 61_000
        assertEquals(4.0, g.currentRate, 0.0)
        repeat(100) { g.success() }
        assertEquals(5.0, g.currentRate, 0.0)
        now += 10 * 60_000
        repeat(100) { g.success() }
        assertEquals(6.0, g.currentRate, 0.0)
    }

    /**
     * Tj, 2026-09-28: "now it is reading the API very slow". With a key, 6 (now 10) books are in flight at once; when
     * Novig refuses that burst, every one of them comes back 429 together. Each used to halve the pace again:
     * 14.4/s → 7.2 → 3.6 → 1.8 → 1/s for a whole minute, from one refusal.
     */
    @Test
    fun `refusals arriving together from one burst halve the pace once, a later one halves it again`() = runTest {
        val g = gate(14.4, 44)
        repeat(6) { g.slowDown() }
        assertEquals(7.2, g.currentRate, 1e-9)
        now += 400
        repeat(4) { g.slowDown() }
        assertEquals(7.2, g.currentRate, 1e-9)
        // A refusal a second or more after the last slow-down is a new one.
        now += 1_000
        g.slowDown()
        assertEquals(3.6, g.currentRate, 1e-9)
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
        repeat(100) { g.success() }
        assertEquals(5.5, g.currentRate, 0.0) // and for ten minutes no higher than a step under the 6/s that was refused
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
