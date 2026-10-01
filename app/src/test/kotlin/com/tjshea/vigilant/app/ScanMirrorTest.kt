package com.tjshea.vigilant.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-30: "Both times it was scanning vigilant and I tried to switch tabs, which got very laggy then crashed". A scan publishes after
 * every Novig price it reads; the screen takes its newest state at most every [SCAN_MIRROR_MS], and never misses the last one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanMirrorTest {

    @Test
    fun `a scan's 1,500 ticks reach the screen as a few states a second, and the last one always does`() = runTest {
        val runs = MutableStateFlow(0)
        val seen = ArrayList<Int>()
        val follower = launch { followThrottled(runs, SCAN_MIRROR_MS) { seen += it } }
        // 1,500 prices read at 14 a second: about 107 seconds of ticks.
        repeat(1_500) { i ->
            delay(1_000L / 14)
            runs.value = i + 1
        }
        delay(2 * SCAN_MIRROR_MS)
        follower.cancel()
        advanceUntilIdle()
        // At most one state per SCAN_MIRROR_MS (plus the one it started with), instead of one per price.
        val limit = (1_500L * (1_000L / 14)) / SCAN_MIRROR_MS + 2
        assertTrue("${seen.size} states for 1,500 ticks", seen.size <= limit)
        assertTrue("${seen.size}", seen.size >= limit / 2)
        // The scan's last state got through, and they came in order.
        assertEquals(1_500, seen.last())
        assertEquals(seen.sorted(), seen)
    }

    @Test
    fun `a single change after a quiet spell is shown at once`() = runTest {
        val runs = MutableStateFlow("idle")
        val seen = ArrayList<Pair<Long, String>>()
        val follower = launch { followThrottled(runs, SCAN_MIRROR_MS) { seen += testScheduler.currentTime to it } }
        delay(5_000)
        runs.value = "scan done"
        delay(10)
        follower.cancel()
        assertEquals(listOf(0L to "idle", 5_000L to "scan done"), seen)
    }

    /** Tj, 2026-10-01: "I used the check odds now function and the list of open bets got very laggy": the Tracker saves every 5 bets. */
    @Test
    fun `a Check odds now's saves reach the screen a few times a second, the last one always`() = runTest {
        val saves = kotlinx.coroutines.flow.MutableSharedFlow<Int>(replay = 1)
        val seen = ArrayList<Int>()
        val follower = launch { followThrottled(saves, TRACKER_MIRROR_MS) { seen += it } }
        // 168 open bets saved 5 at a time while pages are read three at a time: a save every ~150 ms for ~5 s, then the merge.
        repeat(34) { i ->
            delay(150)
            saves.emit(i + 1)
        }
        delay(2 * TRACKER_MIRROR_MS)
        follower.cancel()
        assertEquals(34, seen.last())
        assertTrue("${seen.size} of 34 saves shown", seen.size <= (34 * 150L) / TRACKER_MIRROR_MS + 2)
        assertEquals(seen.sorted(), seen)
    }
}
