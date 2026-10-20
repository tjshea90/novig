package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.cloud.LiveBoard
import com.tjshea.vigilant.data.cloud.PaperLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaperBookTest {
    private fun rec(key: String = "shadow|o1", kind: String = "TOTAL", threshold: Double = 44.5, side: String = "UNDER", ref: String = "OKLA", ask: Double = 0.8, fair: Double = 0.85, tier: String = "shadow") =
        PaperRecord(key, tier, "e1", "Okla @ Miss St", "NCAAF", "Mississippi State", "Oklahoma", kind, ref, threshold, side, "Under $threshold", ask, 0.01, fair, 1_000L)

    private fun game(home: Int, away: Int, done: Boolean = true) = EspnGame("Mississippi State", "MSST", "Oklahoma", "OKLA", home, away, live = false, period = 4, clockSec = 0.0, done = done)

    @Test fun totalsGradeBothWays() {
        assertEquals("WIN", PaperGrade.result(rec(), game(20, 20)))
        assertEquals("LOSS", PaperGrade.result(rec(), game(30, 20)))
        assertEquals("WIN", PaperGrade.result(rec(side = "OVER"), game(30, 20)))
        assertEquals("PUSH", PaperGrade.result(rec(threshold = 40.0), game(20, 20)))
    }

    @Test fun marginsGradeByTheReferenceTeam() {
        val ml = rec(kind = "MARGIN", threshold = 0.0, side = "YES", ref = "OKLA")
        assertEquals("WIN", PaperGrade.result(ml, game(10, 17)))
        assertEquals("LOSS", PaperGrade.result(ml, game(21, 17)))
        assertEquals("PUSH", PaperGrade.result(ml, game(17, 17)))
        val spread = rec(kind = "MARGIN", threshold = 2.5, side = "NO", ref = "OKLA")   // NO: OKLA does not win by 3 or more
        assertEquals("WIN", PaperGrade.result(spread, game(17, 18)))
        assertEquals("LOSS", PaperGrade.result(spread, game(10, 17)))
    }

    @Test fun anUnfinishedGameIsNotGraded() = assertNull(PaperGrade.result(rec(), game(20, 20, done = false)))

    @Test fun profitAndStats() {
        val book = PaperBook()
        assertTrue(book.add(rec("shadow|a", ask = 0.8)))
        assertFalse(book.add(rec("shadow|a", ask = 0.5)))   // the first sighting is the bet
        book.add(rec("shadow|b", ask = 0.8))
        book.add(rec("score-event|c", tier = "score-event"))
        book.settle("shadow|a", "WIN"); book.settle("shadow|b", "LOSS")
        val s = book.stats().first { it.tier == "shadow" }
        assertEquals(1, s.wins); assertEquals(1, s.losses); assertEquals(0, s.open)
        assertEquals(0.19 - 0.81, book.all().sumOf { it.profit }, 1e-9)   // win 1-0.8-0.01, loss -(0.8+0.01)
        assertEquals(1, book.stats().first { it.tier == "score-event" }.open)
        book.settle("shadow|a", "LOSS")   // a settled bet stays settled
        assertEquals("WIN", book.all().first { it.key == "shadow|a" }.result)
    }

    @Test fun savesAndLoadsBack() {
        var saved = ""
        val a = PaperBook(save = { saved = it })
        a.add(rec()); a.flush(force = true)
        assertTrue(saved.isNotEmpty())
        assertEquals(1, PaperBook(load = { saved }).all().size)
    }

    @Test fun boardWords() {
        val lines = LiveBoard.paperLines(listOf(PaperLine("shadow", open = 2, wins = 8, losses = 2, expected = 0.8, roi = 0.05), PaperLine("score-event", open = 3)))
        assertTrue(lines[0].contains("8 won (80%") && lines[0].contains("+5.0%"), lines[0])
        assertTrue(lines[1].contains("none settled yet"), lines[1])
    }
}
