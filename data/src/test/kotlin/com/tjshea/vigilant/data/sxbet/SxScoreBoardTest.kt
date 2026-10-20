package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.reference.Leagues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SxScoreBoardTest {
    private val info = SxLiveBuild.EventInfo("L1", Leagues.byNovigName("MLB")!!, "Cleveland Guardians", "Tampa Bay Rays", 0L, emptyList())
    private fun score(one: Int?, two: Int?, period: String? = "5th Inning", time: String? = "-1", finished: Boolean = false) =
        SxScore("L1", 3, one, two, period, time, atMs = 1_000L, finished = finished)

    @Test fun `scores follow the team names whichever order SX lists them in`() {
        val a = SxScoreBoard.forMatch(info, score(4, 1), home = "Tampa Bay Rays", away = "Cleveland Guardians")!!
        assertEquals(1, a.homeScore); assertEquals(4, a.awayScore)
        val b = SxScoreBoard.forMatch(info, score(4, 1), home = "Cleveland Guardians", away = "Tampa Bay Rays")!!   // Novig lists them the other way round
        assertEquals(4, b.homeScore); assertEquals(1, b.awayScore)
        assertTrue(a.live); assertFalse(a.done)
    }

    @Test fun `a game that matches neither order, or a score not yet sent, gives nothing`() {
        assertNull(SxScoreBoard.forMatch(info, score(4, 1), home = "Boston Red Sox", away = "New York Yankees"))
        assertNull(SxScoreBoard.forMatch(info, score(null, 1), home = "Tampa Bay Rays", away = "Cleveland Guardians"))
    }

    @Test fun `periods and clocks are read from SX's labels`() {
        assertEquals(5, SxScoreBoard.periodOf("5th Inning"))
        assertEquals(3, SxScoreBoard.periodOf("3rd Quarter"))
        assertEquals(99, SxScoreBoard.periodOf("Overtime"))
        assertEquals(0, SxScoreBoard.periodOf(null))
        assertEquals(443.0, SxScoreBoard.clockOf("7:23"), 1e-9)
        assertEquals(0.0, SxScoreBoard.clockOf("-1"), 1e-9)
        assertEquals(0.0, SxScoreBoard.clockOf("garbage"), 1e-9)
    }

    @Test fun `a finished game is done`() = assertTrue(SxScoreBoard.forMatch(info, score(4, 1, finished = true), "Tampa Bay Rays", "Cleveland Guardians")!!.done)
}
