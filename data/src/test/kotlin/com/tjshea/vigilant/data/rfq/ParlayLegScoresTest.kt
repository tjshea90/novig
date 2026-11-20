package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.tracker.GameScore
import com.tjshea.vigilant.data.tracker.PlayerLine
import com.tjshea.vigilant.data.tracker.ScoreSource
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.tjshea.vigilant.data.rfq.ParlayBook
import org.junit.Test

class ParlayLegScoresTest {
    private val start = 1_800_000_000_000L
    private val now = start + 5 * 3_600_000L
    private fun game(final: Boolean = true, home: Int = 100, away: Int = 90) = GameScore("g1", "NBA", "Sacramento Kings", "Phoenix Suns", start, final, false, home, away)
    private fun source(g: GameScore) = object : ScoreSource {
        override suspend fun games(league: String, date: LocalDate) = listOf(g)
        override suspend fun players(game: GameScore): List<PlayerLine>? = null
        override fun covers(league: String) = true
    }
    private fun leg(type: String, label: String) = FillLeg("o1", "m1", label, "Phoenix Suns @ Sacramento Kings", "NBA", type, start, 0.7)

    @Test fun aSpreadMoneylineAndTotalAreGradedFromTheFinalScore() = runTest {
        // Suns (away) lost 90-100: +5.5 covers (WIN), +12.5 covers, -2.5 does not.
        assertEquals("WIN", ParlayLegScores.grade(leg("SPREAD", "Spread Phoenix Suns +12.5"), source(game()), now))
        assertEquals("LOSS", ParlayLegScores.grade(leg("SPREAD", "Spread Phoenix Suns -2.5"), source(game()), now))
        assertEquals("LOSS", ParlayLegScores.grade(leg("MONEY", "Moneyline Phoenix Suns"), source(game()), now))
        assertEquals("WIN", ParlayLegScores.grade(leg("MONEY", "Moneyline Sacramento Kings"), source(game()), now))
        assertEquals("WIN", ParlayLegScores.grade(leg("TOTAL", "Total Over 185.5"), source(game()), now))
        assertEquals("LOSS", ParlayLegScores.grade(leg("TOTAL", "Total Over 200.5"), source(game()), now))
    }

    @Test fun nothingBeforeTheGameIsFinalOrForAProp() = runTest {
        assertNull(ParlayLegScores.grade(leg("SPREAD", "Spread Phoenix Suns +12.5"), source(game(final = false)), now))
        assertNull(ParlayLegScores.grade(leg("SPREAD", "Spread Phoenix Suns +12.5"), source(game()), start + 3_600_000L))
        assertNull(ParlayLegScores.grade(leg("RECEIVING_YARDS", "Receiving Yards George Kittle Over 49.5"), source(game()), now))
    }

    @Test fun legsToScoreTakesFinishedLegsWithNoResultEvenWithoutAMarketId() {
        val old = BookLeg("a", "Spread Phoenix Suns +5.5", "Phoenix Suns @ Sacramento Kings", "NBA", "SPREAD", start, 0.7, null)
        val done = old.copy(outcomeId = "b", result = "WIN")
        val fresh = old.copy(outcomeId = "c", startsMs = now - 3_600_000L)
        val parlay = BookParlay(legs = listOf(old, done, fresh))
        val got = ParlayBook.legsToScore(ParlayBook(parlays = listOf(parlay)), now)
        assertEquals(listOf("a"), got.map { it.outcomeId })
    }
}
