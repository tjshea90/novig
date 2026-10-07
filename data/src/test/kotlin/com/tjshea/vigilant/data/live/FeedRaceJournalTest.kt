package com.tjshea.vigilant.data.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeedRaceJournalTest {
    private val day = 24 * 3_600_000L
    private val t0 = 1_800_000_000_000L - 1_800_000_000_000L % day + 3_600_000L   // 01:00 UTC of some day

    private fun dir() = java.nio.file.Files.createTempDirectory("race").toFile().also { it.deleteOnExit() }

    @Test
    fun `readings, Novig trades and odds written come back as they were, only from the time asked`() {
        val j = FeedRaceJournal(dir(), clock = { t0 + 5_000 })
        j.append(
            scores = listOf(FeedRace.Sighting("sofa", "g1", "Zhizhen Zhang", "Tomas Machac", 103, 105, t0 + 1_000, init = false, live = true, rttMs = 270), FeedRace.Sighting("poly", "g1", "Zhizhen Zhang", "Tomas Machac", 0, 0, t0, init = true)),
            trades = listOf(FeedRace.NovigTick("Tomas Machac @ Zhizhen Zhang", "m1", "o1", 0.535, 120, t0 + 2_000)),
            odds = listOf(FeedRace.OddsTick("Shanghai: Zhizhen Zhang vs Tomas Machac", "Zhizhen Zhang", 0.59, t0 + 3_000, serverMs = t0 + 2_900), FeedRace.OddsTick("x: A vs B", "A", 0.4, t0 + 3_100)),
        )
        val all = j.read(0)
        assertEquals(listOf("poly", "sofa"), all.scores.map { it.src })
        val sofa = all.scores.single { it.src == "sofa" }
        assertEquals(103 to 105, sofa.h to sofa.a); assertEquals(270L, sofa.rttMs); assertFalse(sofa.init)
        assertTrue(all.scores.single { it.src == "poly" }.init)
        assertEquals(0.535, all.trades.single().price, 1e-12); assertEquals(120L, all.trades.single().qty)
        assertEquals(t0 + 2_900, all.odds.first().serverMs); assertNull(all.odds.last().serverMs)
        val later = j.read(t0 + 2_500)
        assertTrue(later.scores.isEmpty()); assertTrue(later.trades.isEmpty()); assertEquals(2, later.odds.size)
    }

    @Test
    fun `one file a day appended to, days older than a week dropped, a torn line skipped`() {
        val d = dir()
        var now = t0
        val j = FeedRaceJournal(d, clock = { now })
        j.append(trades = listOf(FeedRace.NovigTick("A @ B", "m", "o", 0.5, 1, t0)))
        now = t0 + 10 * day
        j.append(trades = listOf(FeedRace.NovigTick("A @ B", "m", "o", 0.6, 1, now)))
        assertEquals(1, d.listFiles()!!.size)   // the old day was dropped by the newer write
        File(d, d.listFiles()!!.single().name).appendText("{\"k\":\"n\",\"ev\":\"A @ B\",\"mk\"")   // a write cut off
        assertEquals(1, j.read(now - day).trades.size)
        // The same day again appends to the same file.
        j.append(trades = listOf(FeedRace.NovigTick("A @ B", "m", "o", 0.7, 1, now + 1_000)))
        assertEquals(1, d.listFiles()!!.size)
    }
}
