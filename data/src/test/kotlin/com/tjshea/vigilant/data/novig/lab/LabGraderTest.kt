package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.GameScore
import com.tjshea.vigilant.data.tracker.PlayerLine
import com.tjshea.vigilant.data.tracker.ScoreSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LabGraderTest {
    private val start = 1_800_000_000_000L

    private fun game(final: Boolean = true, home: Int = 6, away: Int = 4) =
        GameScore("g1", "NHL", "Vegas Golden Knights", "Toronto Maple Leafs", start, final, false, home, away)

    private class Feed(val games: List<GameScore>, val box: List<PlayerLine>? = null) : ScoreSource {
        override suspend fun games(league: String, date: LocalDate) = games
        override suspend fun players(game: GameScore) = box
        override fun covers(league: String) = true
    }

    private fun grade(feed: Feed, kind: String, selection: String, stat: String? = null, league: String = "NHL", event: String = "Toronto Maple Leafs @ Vegas Golden Knights") =
        runBlocking { LabGrader(feed).grade(LabGrader.Target(event, league, start, kind, selection, stat)) }

    @Test
    fun `live ladder sides grade from the final score, YES and NO`() {
        val f = Feed(listOf(game()))   // Vegas 6, Toronto 4: total 10, Vegas wins by 2
        assertEquals("WIN", grade(f, "TOTAL", "Total 9.5 YES"))
        assertEquals("LOSS", grade(f, "TOTAL", "Total 9.5 NO"))
        assertEquals("PUSH", grade(f, "TOTAL", "Total 10 YES"))
        assertEquals("LOSS", grade(f, "MONEYLINE", "ML TOR YES"))
        assertEquals("WIN", grade(f, "MONEYLINE", "ML TOR NO"))
        assertEquals("WIN", grade(f, "SPREAD", "Spr TOR +2.5 YES"))     // Toronto +2.5 loses by 2: covers
        assertEquals("LOSS", grade(f, "SPREAD", "Spr TOR +2.5 NO"))
        assertEquals("LOSS", grade(f, "SPREAD", "Spr TOR +1.5 YES"))
        assertEquals("PUSH", grade(f, "SPREAD", "Spr TOR +2 YES"))
        assertEquals("WIN", grade(f, "MONEYLINE", "ML VGK YES"))        // initials of the three words
    }

    @Test
    fun `pregame bids grade from the market kind and the selection`() {
        val f = Feed(listOf(game()))
        assertEquals("WIN", grade(f, "TOTAL", "Over 9.5"))
        assertEquals("LOSS", grade(f, "SPREAD", "Vegas Golden Knights -2.5"))
        assertEquals("WIN", grade(f, "MONEYLINE", "Vegas Golden Knights"))
        assertEquals("WIN", grade(f, "TEAM_TOTAL", "Vegas Golden Knights Over 5.5"))
        assertNull("a period market does not say which period", grade(f, "PERIOD", "Vegas Golden Knights -0.5"))
    }

    @Test
    fun `a prop grades from the box score with the stat type kept at the fill`() {
        val box = (1..9).map { PlayerLine("Player $it", mapOf("SHOTS_ON_GOAL" to 2.0)) } + PlayerLine("Auston Matthews", mapOf("SHOTS_ON_GOAL" to 5.0))
        val f = Feed(listOf(game()), box)
        assertEquals("WIN", grade(f, "PROP", "Auston Matthews Over 4.5", "SHOTS_ON_GOAL"))
        assertEquals("LOSS", grade(f, "PROP", "Auston Matthews Under 4.5", "SHOTS_ON_GOAL"))
        assertNull("no stat label, no guess", grade(f, "PROP", "Auston Matthews Over 4.5", null))
    }

    @Test
    fun `nothing is graded before the game is final or when no game is found`() {
        assertNull(grade(Feed(listOf(game(final = false))), "TOTAL", "Total 9.5 YES"))
        assertNull(grade(Feed(emptyList()), "TOTAL", "Total 9.5 YES"))
        assertNull("an unreadable abbreviation", grade(Feed(listOf(game())), "MONEYLINE", "ML ZZZ YES"))
    }

    @Test
    fun `a lab record grades by its label and side, a cover not at all`() {
        val g = LabGrader(Feed(listOf(game())))
        fun rec(kind: String, label: String, side: String) = LabRecord("r", start, kind, "e1", "Toronto Maple Leafs @ Vegas Golden Knights", "NHL", "m1", "o1", label, side, 9.5, 0.5, 0.6, 0.1, 1)
        assertEquals("LOSS", runBlocking { g.gradeRecord(rec(LabKind.TAIL, "Total 9.5", "UNDER")) })
        assertEquals("WIN", runBlocking { g.gradeRecord(rec(LabKind.TAIL, "Total 9.5", "OVER")) })
        assertEquals("WIN", runBlocking { g.gradeRecord(rec(LabKind.ALT, "Spr TOR +2.5", "YES")) })
        assertNull(runBlocking { g.gradeRecord(rec(LabKind.COVER, "Total 9.5 YES + Total 11.5 NOT", "COVER")) })
    }

    @Test
    fun `FreeScores keeps the Eastern date helper the grader uses`() {
        assertEquals(FreeScores.etDate(start), FreeScores.etDate(start))
    }
}
