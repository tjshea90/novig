package com.tjshea.vigilant.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ParlayAPI's movers board ("Pinnacle moved toward/against", PARLAY_API.md §6.3) is read only while Vigilant is on screen (full tests
 * 2026-09-30): at once and every few minutes there, never off screen (no timer wakes the Moto G in a pocket), and at once when Vigilant comes
 * back, so the notes are never minutes stale on return.
 */
class ScreenPacingTest {

    private val every = 180_000L

    @Test
    fun `reads at once and on the interval while on screen, nothing off screen, and at once on return`() = runTest {
        val sports = MutableStateFlow(listOf("baseball_mlb"))
        val screen = MutableStateFlow(true)
        val calls = ArrayList<Pair<Long, List<String>>>()
        val job = launch { refreshWhileOnScreen(sports, screen, every) { calls += currentTime to it } }
        runCurrent()
        assertEquals(listOf(0L to listOf("baseball_mlb")), calls)
        advanceTimeBy(every + 1); runCurrent()
        assertEquals(2, calls.size)
        // Off screen for an hour: not one read.
        screen.value = false; runCurrent()
        advanceTimeBy(3_600_000L); runCurrent()
        assertEquals(2, calls.size)
        // Back on screen: read at once, not at the next tick.
        val back = currentTime
        screen.value = true; runCurrent()
        assertEquals(back to listOf("baseball_mlb"), calls.last())
        // New leagues: read at once too.
        sports.value = listOf("baseball_mlb", "americanfootball_nfl"); runCurrent()
        assertEquals(listOf("baseball_mlb", "americanfootball_nfl"), calls.last().second)
        job.cancel()
    }

    @Test
    fun `no leagues (or ParlayAPI off) drops what was read, once, on or off screen`() = runTest {
        val sports = MutableStateFlow(emptyList<String>())
        val screen = MutableStateFlow(false)
        val calls = ArrayList<List<String>>()
        val job = launch { refreshWhileOnScreen(sports, screen, every) { calls += it } }
        runCurrent()
        advanceTimeBy(10 * every); runCurrent()
        assertEquals(listOf(emptyList<String>()), calls)
        job.cancel()
    }
}
