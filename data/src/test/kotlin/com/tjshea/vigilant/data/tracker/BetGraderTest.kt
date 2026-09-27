package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.tracker.BetGrader.Period
import com.tjshea.vigilant.data.tracker.BetGrader.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Grading bets from final scores (the 2026-09-27 full test's settle fix). The market wordings are
 * Vigilant's own and CRAZYNINJAODDS' REAL ones, read from CNO's list on 2026-09-27.
 */
class BetGraderTest {

    private fun bet(market: String, selection: String, event: String = "New York Mets @ Washington Nationals", league: String = "MLB", start: Long = METS_START) = TrackedBet(
        id = "b", createdAtMs = 0, league = league, eventName = event, startsTs = start, marketLabel = market, selection = selection,
        marketId = "", outcomeId = "", price = 0.5, cost = 0.5, fairAtBet = null, evPercentAtBet = null, stake = 1.0,
    )

    /** Mets 7 @ Nationals 1, 2026-09-26 (MLB's own line score). */
    private val mets = GameScore(
        "822678", "MLB", home = "Washington Nationals", away = "New York Mets", startMs = METS_START, final = true, called = false,
        homeScore = 1, awayScore = 7, homePeriods = listOf(0, 0, 1, 0, 0, 0, 0, 0, 0), awayPeriods = listOf(0, 0, 0, 0, 0, 0, 4, 1, 2),
    )

    /** Falcons 35 @ Packers 14 (ESPN). */
    private val falcons = GameScore(
        "401872948", "NFL", home = "Green Bay Packers", away = "Atlanta Falcons", startMs = FALCONS_START, final = true, called = false,
        homeScore = 14, awayScore = 35, homePeriods = listOf(7, 0, 0, 7), awayPeriods = listOf(7, 10, 7, 11),
    )

    @Test
    fun `CNO's real market names read as picks`() {
        assertEquals(Pick.Prop("Ja'Marr Chase", "RECEPTIONS", false, 6.5), BetGrader.pickOf(bet("Player Receptions", "Ja'Marr Chase Under 6.5")))
        assertEquals(Pick.Total(false, 37.5, Period.GAME), BetGrader.pickOf(bet("Total Points", "Under 37.5")))
        assertEquals(Pick.Spread("Washington Commanders", 6.5, Period.GAME), BetGrader.pickOf(bet("Point Spread", "Washington Commanders +6.5")))
        assertEquals(Pick.Prop("Jonathan Taylor", "TOUCHDOWNS", false, 0.5), BetGrader.pickOf(bet("Player Touchdowns", "Jonathan Taylor Under 0.5")))
        assertEquals(Pick.TeamTotal("Tampa Bay Rays", false, 3.5), BetGrader.pickOf(bet("Team Total Runs", "Tampa Bay Rays Under 3.5")))
        assertEquals(Pick.TeamTotal("Baltimore Ravens", false, 27.5), BetGrader.pickOf(bet("Team Total Points", "Baltimore Ravens Under 27.5")))
        assertEquals(Pick.Prop("Jahmyr Gibbs", "LONGEST_RUSH", false, 17.5), BetGrader.pickOf(bet("Player Longest Rush", "Jahmyr Gibbs Under 17.5")))
        assertEquals(Pick.Prop("Bo Nix", "PASSING_TOUCHDOWNS", false, 1.5), BetGrader.pickOf(bet("Player Passing Touchdowns", "Bo Nix Under 1.5")))
        assertEquals(Pick.Prop("Aaron Rodgers", "PASSING_ATTEMPTS", true, 34.5), BetGrader.pickOf(bet("Player Passing Attempts", "Aaron Rodgers Over 34.5")))
        assertEquals(Pick.Prop("Jacoby Brissett", "PASSING_COMPLETIONS", true, 22.5), BetGrader.pickOf(bet("Player Passing Completions", "Jacoby Brissett Over 22.5")))
        assertEquals(Pick.Prop("Jacob Misiorowski", "PITCHER_STRIKEOUTS", false, 6.5), BetGrader.pickOf(bet("Player Pitching Strikeouts", "Jacob Misiorowski Under 6.5")))
        assertEquals(Pick.Prop("Justin Herbert", "INTERCEPTIONS_THROWN", false, 0.5), BetGrader.pickOf(bet("Player Passing Interceptions", "Justin Herbert Under 0.5")))
        assertEquals(Pick.Total(true, 4.5, Period.FIRST_HALF), BetGrader.pickOf(bet("1st 5 Innings Total Runs", "Over 4.5")))
        assertEquals(Pick.Prop("Josh Downs", "LONGEST_RECEPTION", true, 19.5), BetGrader.pickOf(bet("Player Longest Reception", "Josh Downs Over 19.5")))
        assertEquals(Pick.Prop("Justin Herbert", "PASSING_YARDS", false, 224.5), BetGrader.pickOf(bet("Player Passing Yards", "Justin Herbert Under 224.5")))
        assertEquals(Pick.Moneyline("Home Team"), BetGrader.pickOf(bet("Moneyline", "Home Team")))
    }

    @Test
    fun `Vigilant's own market names read as picks`() {
        assertEquals(Pick.Spread("Dallas Cowboys", -3.5, Period.GAME), BetGrader.pickOf(bet("Spread", "Dallas Cowboys -3.5")))
        assertEquals(Pick.Spread("New York Mets", -0.5, Period.FIRST_HALF), BetGrader.pickOf(bet("F5 Spread", "New York Mets -0.5")))
        assertEquals(Pick.Total(false, 0.5, Period.FIRST_INNING), BetGrader.pickOf(bet("1st Inning Total", "Under 0.5")))
        assertEquals(Pick.Total(true, 20.5, Period.FIRST_HALF), BetGrader.pickOf(bet("1H Total", "Over 20.5")))
        assertEquals(Pick.TeamTotal("Los Angeles Rams", true, 22.5), BetGrader.pickOf(bet("Team Total", "Los Angeles Rams Over 22.5")))
        assertEquals(Pick.Prop("Patrick Mahomes", "PASSING_YARDS", true, 233.5), BetGrader.pickOf(bet("Passing Yards", "Patrick Mahomes Over 233.5")))
        assertEquals(Pick.Prop("Bo Bichette", "TOTAL_BASES", true, 1.5), BetGrader.pickOf(bet("Total Bases", "Bo Bichette Over 1.5")))
        assertEquals(Pick.Prop("Aaron Judge", "HITS_RUNS_RBIS", true, 1.5), BetGrader.pickOf(bet("Hits + Runs + RBIs", "Aaron Judge Over 1.5")))
        assertEquals(Pick.Prop("Breanna Stewart", "THREE_POINTERS_MADE", false, 1.5), BetGrader.pickOf(bet("Three Pointers Made", "Breanna Stewart Under 1.5")))
    }

    @Test
    fun `what can't be read for certain is left to a tap`() {
        assertNull(BetGrader.pickOf(bet("Moneyline 3-way", "Draw Yes")))
        assertNull(BetGrader.pickOf(bet("1st Quarter Spread", "Dallas Cowboys -0.5")))
        assertNull(BetGrader.pickOf(bet("Some Novelty Market", "Something Over 1.5")))
        assertNull(BetGrader.pickOf(bet("Point Spread", "Dallas Cowboys")))
    }

    @Test
    fun `game lines grade from the final and the line score`() {
        fun g(market: String, selection: String) = BetGrader.pickOf(bet(market, selection))!!.let { BetGrader.grade(it, mets) }
        assertEquals(BetStatus.WON, g("Moneyline", "New York Mets"))
        assertEquals(BetStatus.LOST, g("Moneyline", "Washington Nationals"))
        assertEquals(BetStatus.LOST, g("Run Line", "Washington Nationals +1.5"))
        assertEquals(BetStatus.WON, g("Run Line", "New York Mets -1.5"))
        assertEquals(BetStatus.LOST, g("Total Runs", "Over 8.5"))
        assertEquals(BetStatus.PUSH, g("Total", "Over 8"))
        assertEquals(BetStatus.WON, g("Team Total Runs", "New York Mets Over 3.5"))
        // First five innings: Nationals 1, Mets 0.
        assertEquals(BetStatus.WON, g("1st 5 Innings Total Runs", "Under 4.5"))
        assertEquals(BetStatus.LOST, g("F5 Spread", "New York Mets -0.5"))
        // No run in the 1st: NRFI (Under 0.5) wins.
        assertEquals(BetStatus.WON, g("1st Inning Total", "Under 0.5"))
        // Football halves are two quarters: Falcons 17, Packers 7.
        val half = BetGrader.pickOf(bet("1st Half Point Spread", "Green Bay Packers +10.5", "Atlanta Falcons @ Green Bay Packers", "NFL"))!!
        assertEquals(BetStatus.WON, BetGrader.grade(half, falcons))
        val short = BetGrader.pickOf(bet("1st Half Point Spread", "Green Bay Packers +9.5", "Atlanta Falcons @ Green Bay Packers", "NFL"))!!
        assertEquals(BetStatus.LOST, BetGrader.grade(short, falcons))
    }

    @Test
    fun `nothing is graded before the game is final`() {
        val live = mets.copy(final = false)
        assertNull(BetGrader.grade(Pick.Moneyline("New York Mets"), live))
    }

    @Test
    fun `props grade from the box score, and a player not in it waits`() {
        val box = listOf(
            PlayerLine("Bijan Robinson", mapOf("RUSHING_YARDS" to 194.0, "TOUCHDOWNS" to 2.0)),
            PlayerLine("Drake London", mapOf("RECEIVING_YARDS" to 194.0, "TOUCHDOWNS" to 0.0)),
        )
        fun g(market: String, selection: String) =
            BetGrader.pickOf(bet(market, selection, "Atlanta Falcons @ Green Bay Packers", "NFL"))!!.let { BetGrader.grade(it, falcons, box) }
        assertEquals(BetStatus.WON, g("Player Rushing Yards", "Bijan Robinson Over 150.5"))
        assertEquals(BetStatus.LOST, g("Player Touchdowns", "Bijan Robinson Under 0.5"))
        assertEquals(BetStatus.WON, g("Player Touchdowns", "Drake London Under 0.5"))
        // "B. Robinson" is the one Robinson in the box score.
        assertEquals(BetStatus.WON, g("Player Rushing Yards", "B. Robinson Over 99.5"))
        assertNull(g("Player Rushing Yards", "Kyle Pitts Over 9.5"))
    }

    @Test
    fun `the game is found by both teams, and a doubleheader by the nearer start`() {
        val game2 = mets.copy(id = "g2", startMs = METS_START + 5 * 3_600_000L)
        val other = mets.copy(id = "x", home = "Detroit Tigers", away = "Pittsburgh Pirates")
        assertEquals("822678", BetGrader.gameOf(bet("Moneyline", "New York Mets"), listOf(other, game2, mets))?.id)
        assertEquals("g2", BetGrader.gameOf(bet("Moneyline", "New York Mets", start = METS_START + 5 * 3_600_000L), listOf(mets, game2))?.id)
        assertNull(BetGrader.gameOf(bet("Moneyline", "New York Mets", event = "Chicago Cubs @ Boston Red Sox"), listOf(mets)))
    }

    companion object {
        val METS_START = java.time.Instant.parse("2026-09-26T16:35:00Z").toEpochMilli()
        val FALCONS_START = java.time.Instant.parse("2026-09-25T00:15:00Z").toEpochMilli()
    }
}
