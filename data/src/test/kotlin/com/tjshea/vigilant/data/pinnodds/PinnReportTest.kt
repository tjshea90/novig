package com.tjshea.vigilant.data.pinnodds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnReportTest {
    private fun rec(id: String, fair: Double = 0.57, ask: Double = 0.50, mode: String = "PAPER", outcome: String = "PAPER") = LiveRecord(
        id, 1_791_420_000_000L, mode, outcome, "NBA", "A @ B", "ev", "m", "o", "Moneyline", "B", "HOME", "s;0;m", fair, ask, ask, 0.0075, fair / (ask + 0.0075) - 1, 0.05,
    )

    @Test
    fun `the same ask is re-judged against Pinnacle's fair 30 and 120 seconds later`() {
        val a = rec("a")
        val b = rec("b")
        val f = listOf(
            LiveFollow("a", 1, 30, fair = 0.58, ask = 0.55), LiveFollow("a", 1, 120, fair = 0.59, ask = 0.58),
            LiveFollow("b", 1, 30, fair = 0.50, ask = 0.50), LiveFollow("b", 1, 120, fair = 0.49, ask = 0.50),
        )
        val s = PinnReport.stats(listOf(a, b), f)
        assertEquals(2, s.n30)
        assertEquals(((0.58 / 0.5075 - 1) + (0.50 / 0.5075 - 1)) / 2, s.avgEv30!!, 1e-9)
        assertEquals(((0.59 / 0.5075 - 1) + (0.49 / 0.5075 - 1)) / 2, s.avgEv120!!, 1e-9)
        assertEquals("only a's later fair stayed up", 0.5, s.held120!!, 0.0)
        assertEquals("a's ask rose toward the fair, b's did not", 0.5, s.novigFollowed120!!, 0.0)
    }

    @Test
    fun `a line that closed or a missing reading is not counted, and no follow-ups gives n a`() {
        val a = rec("a")
        val s = PinnReport.stats(listOf(a), listOf(LiveFollow("a", 1, 30, fair = null, closed = true)))
        assertEquals(0, s.n30)
        assertNull(s.avgEv30)
        assertNull(s.held120)
        val lines = PinnReport.lines(listOf(a), emptyList())
        assertTrue(lines.first().startsWith("Paper decisions: 1 decisions"))
        assertTrue(lines.first().contains("n/a"))
    }

    @Test
    fun `real bets get an orders line with misses and fills`() {
        val bets = listOf(rec("1", mode = "BET", outcome = "FILLED").copy(filled = 100, paid = 0.5, feePaid = 0.01, sendToEndMs = 300), rec("2", mode = "BET", outcome = "MISSED"))
        val lines = PinnReport.lines(bets, emptyList())
        assertTrue(lines.any { it.startsWith("Orders: 2 sent · 1 filled · 1 missed") })
        assertTrue(lines.any { it.startsWith("Spent: $0.50 on 100 contracts") })
        assertNotNull(lines.firstOrNull { it.startsWith("Last decisions:") })
        assertEquals(listOf("No decisions yet."), PinnReport.lines(emptyList(), emptyList()))
    }

    @Test
    fun `orders are split by time since the last score so a post-score pause shows up`() {
        val near = rec("n1", mode = "BET", outcome = "MISSED").copy(scoreAgeMs = 4_000, eventStatus = "OPEN_INGAME", sendToEndMs = 900)
        val near2 = rec("n2", mode = "BET", outcome = "MISSED").copy(scoreAgeMs = 9_000, eventStatus = "OPEN_INGAME", sendToEndMs = 1_100)
        val far = rec("f1", mode = "BET", outcome = "FILLED").copy(scoreAgeMs = 70_000, eventStatus = "OPEN_INGAME", sendToEndMs = 300, filled = 5, paid = 2.0)
        val none = rec("x1", mode = "BET", outcome = "FILLED").copy(scoreAgeMs = -1, eventStatus = "DELAYED", sendToEndMs = 400, filled = 5, paid = 2.0)
        val lines = PinnReport.lines(listOf(near, near2, far, none), emptyList())
        assertTrue(lines.any { it.contains("within 20 s of a score: 2 sent · 0 filled · 2 missed") })
        assertTrue(lines.any { it.contains("20 s to 2 min after a score: 1 sent · 1 filled") })
        assertTrue(lines.any { it.contains("no score in the last 2 min: 1 sent · 1 filled") })
        assertTrue(lines.any { it.contains("Novig status DELAYED: 1 sent") })
    }

    @Test
    fun `the post-score study says how big the gap is and how soon Novig's ask moves`() {
        fun p(off: Int, askH: Double, fairH: Double = 0.57) = ReopenProbe(1_000L + off * 1000L, 1_000L, off, "ev", "NBA", "m", fairHome = fairH, fairAway = 1 - fairH, askHome = askH, askAway = 1 - askH + 0.02)
        val rows = listOf(p(0, 0.50), p(1, 0.50), p(3, 0.50), p(8, 0.56), p(20, 0.57))
        val lines = ReopenStudy.lines(rows)
        assertTrue(lines.toString(), lines.first().startsWith("After a score (1 moneyline readings of 1 games"))
        assertTrue(lines.toString(), lines.any { it.contains("+ 0 s: 1 read · best gap median +7.0 pts") })
        assertTrue(lines.toString(), lines.any { it.contains("+ 3 s") && it.contains("Novig's ask had moved in 0%") })
        assertTrue(lines.toString(), lines.any { it.contains("+ 8 s") && it.contains("Novig's ask had moved in 100%") })
        assertTrue(ReopenStudy.lines(emptyList()).single().startsWith("No score has been probed yet"))
    }
}
