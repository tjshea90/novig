package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.tracker.BetGrader.Period
import com.tjshea.vigilant.data.tracker.BetGrader.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun `props grade from the box score`() {
        val box = padded(
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
        // A football box score lists only players with a stat: Kyle Pitts isn't in it, so he had none (0 rushing yards).
        assertEquals(BetStatus.LOST, g("Player Rushing Yards", "Kyle Pitts Over 9.5"))
        assertEquals(BetStatus.WON, g("Player Rushing Yards", "Kyle Pitts Under 9.5"))
        // Drake London's line has no rushing group: 0 rushing yards, he still played.
        assertEquals(BetStatus.WON, g("Player Rushing Yards", "Drake London Under 0.5"))
        // ...but a longest play with no play isn't a market to grade.
        assertNull(g("Player Longest Rush", "Drake London Under 5.5"))
    }

    @Test
    fun `the game is found by both teams, and a doubleheader by the nearer start`() {
        val game2 = mets.copy(id = "g2", startMs = METS_START + 5 * 3_600_000L)
        val other = mets.copy(id = "x", home = "Detroit Tigers", away = "Pittsburgh Pirates")
        assertEquals("822678", BetGrader.gameOf(bet("Moneyline", "New York Mets"), listOf(other, game2, mets))?.id)
        assertEquals("g2", BetGrader.gameOf(bet("Moneyline", "New York Mets", start = METS_START + 5 * 3_600_000L), listOf(mets, game2))?.id)
        assertNull(BetGrader.gameOf(bet("Moneyline", "New York Mets", event = "Chicago Cubs @ Boston Red Sox"), listOf(mets)))
    }

    // ---- Tj, 2026-09-29: "make sure every bet is properly graded win or loss after the event is final" ----

    /** Alexandre Muller d. Luka Pavlovic 6-4 6-7(6-8) 6-3 (ESPN, Chengdu Open 2026-09-22): Muller home, sets 2-1, games 18-14. */
    private val muller = GameScore(
        "186127", "ATP", home = "Alexandre Muller", away = "Luka Pavlovic", startMs = TENNIS_START, final = true, called = false,
        homeScore = 2, awayScore = 1, homePeriods = listOf(6, 6, 6), awayPeriods = listOf(4, 7, 3),
    )

    private fun tennis(market: String, selection: String, event: String = "Luka Pavlovic @ Alexandre Muller") =
        BetGrader.pickOf(bet(market, selection, event, "ATP", TENNIS_START))!!

    @Test
    fun `tennis markets read as picks`() {
        assertEquals(Pick.Spread("Daniil Medvedev", -3.5, Period.GAME), BetGrader.pickOf(bet("Games Spread", "Daniil Medvedev -3.5")))
        assertEquals(Pick.Total(true, 21.5, Period.GAME), BetGrader.pickOf(bet("Total Games", "Over 21.5")))
        assertEquals(Pick.TeamTotal("Roman Safiullin", true, 12.5), BetGrader.pickOf(bet("Games Won", "Roman Safiullin Over 12.5")))
        assertEquals(Pick.FirstSet("Daniil Medvedev"), BetGrader.pickOf(bet("1st Set Winner", "Daniil Medvedev")))
        assertEquals(Pick.FirstSet("Daniil Medvedev"), BetGrader.pickOf(bet("1st Set Moneyline", "Daniil Medvedev")))
        assertEquals(Pick.Spread("Daniil Medvedev", -1.5, Period.SETS), BetGrader.pickOf(bet("Set Spread", "Daniil Medvedev -1.5")))
        assertEquals(Pick.Total(false, 2.5, Period.SETS), BetGrader.pickOf(bet("Total Sets", "Under 2.5")))
        assertEquals(Pick.Moneyline("Daniil Medvedev"), BetGrader.pickOf(bet("Moneyline", "Daniil Medvedev")))
    }

    @Test
    fun `a tennis match grades from its sets and games`() {
        fun g(market: String, selection: String) = BetGrader.grade(tennis(market, selection), muller)
        assertEquals(BetStatus.WON, g("Moneyline", "Alexandre Muller"))
        assertEquals(BetStatus.LOST, g("Moneyline", "Luka Pavlovic"))
        // Games 18-14: the spread and the total are in games, not sets.
        assertEquals(BetStatus.LOST, g("Games Spread", "Luka Pavlovic +3.5"))
        assertEquals(BetStatus.WON, g("Games Spread", "Luka Pavlovic +4.5"))
        assertEquals(BetStatus.WON, g("Games Spread", "Alexandre Muller -3.5"))
        assertEquals(BetStatus.WON, g("Total Games", "Over 31.5"))
        assertEquals(BetStatus.PUSH, g("Total Games", "Under 32"))
        assertEquals(BetStatus.WON, g("Games Won", "Alexandre Muller Over 17.5"))
        assertEquals(BetStatus.WON, g("Games Won", "Luka Pavlovic Under 14.5"))
        // Sets 2-1.
        assertEquals(BetStatus.WON, g("Set Spread", "Luka Pavlovic +1.5"))
        assertEquals(BetStatus.LOST, g("Set Spread", "Luka Pavlovic -1.5"))
        assertEquals(BetStatus.WON, g("Total Sets", "Over 2.5"))
        // The first set: Muller 6-4.
        assertEquals(BetStatus.WON, g("1st Set Winner", "Alexandre Muller"))
        assertEquals(BetStatus.LOST, g("1st Set Winner", "Luka Pavlovic"))
    }

    @Test
    fun `a tennis match is found whichever way Novig ordered the two players, a team game only in its order`() {
        // ESPN has Muller at home; Novig wrote "Muller @ Pavlovic".
        val swapped = bet("Moneyline", "Alexandre Muller", "Alexandre Muller @ Luka Pavlovic", "ATP", TENNIS_START)
        assertEquals("186127", BetGrader.gameOf(swapped, listOf(muller))?.id)
        // The order of play is loose: a start hours off is still the match (never in a team sport).
        assertEquals("186127", BetGrader.gameOf(swapped.copy(startsTs = TENNIS_START + 20 * 3_600_000L), listOf(muller))?.id)
        assertNull(BetGrader.gameOf(bet("Moneyline", "New York Mets", "Washington Nationals @ New York Mets"), listOf(mets)))
        assertNull(BetGrader.gameOf(bet("Moneyline", "New York Mets", start = METS_START + 13 * 3_600_000L), listOf(mets)))
    }

    @Test
    fun `alternate lines, game totals and combo props read as picks`() {
        assertEquals(Pick.Total(true, 47.5, Period.GAME), BetGrader.pickOf(bet("Alternate Total", "Over 47.5")))
        assertEquals(Pick.Total(false, 8.5, Period.GAME), BetGrader.pickOf(bet("Game Total", "Under 8.5")))
        assertEquals(Pick.Total(true, 220.5, Period.GAME), BetGrader.pickOf(bet("Total Points (Incl. Overtime)", "Over 220.5")))
        assertEquals(Pick.Spread("Boston Celtics", -4.5, Period.GAME), BetGrader.pickOf(bet("Alternate Point Spread", "Boston Celtics -4.5")))
        fun prop(market: String, selection: String) = BetGrader.pickOf(bet(market, selection)) as? Pick.Prop
        assertEquals("SHOTS_ON_GOAL", prop("Player Shots on Goal", "Matt Boldy Over 2.5")?.stat)
        assertEquals("SAVES", prop("Player Saves", "Filip Gustavsson Over 24.5")?.stat)
        assertEquals("TACKLES_ASSISTS", prop("Player Tackles + Assists", "Roquan Smith Over 8.5")?.stat)
        assertEquals("POINTS_REBOUNDS", prop("Player Points + Rebounds", "James Harden Over 30.5")?.stat)
        assertEquals("POINTS_ASSISTS", prop("Player Points + Assists", "James Harden Over 30.5")?.stat)
        assertEquals("REBOUNDS_ASSISTS", prop("Player Rebounds + Assists", "James Harden Over 8.5")?.stat)
        assertEquals("POINTS_REBOUNDS_ASSISTS", prop("Player Points + Rebounds + Assists", "James Harden Over 36.5")?.stat)
        assertEquals("STEALS", prop("Player Steals", "James Harden Over 1.5")?.stat)
        assertEquals("BLOCKS", prop("Player Blocks", "Jarrett Allen Over 1.5")?.stat)
        assertEquals("TURNOVERS", prop("Player Turnovers", "James Harden Under 3.5")?.stat)
        assertEquals("POINTS", prop("Player Points", "Matt Boldy Over 0.5")?.stat)
        assertEquals(Pick.Prop("Matt Boldy", "PLAYER_GOALS", true, 0.5), prop("Anytime Goalscorer", "Matt Boldy Yes"))
        assertEquals(Pick.Prop("Nikola Jokic", "DOUBLE_DOUBLE", false, 0.5), prop("Player Double Double", "Nikola Jokic No"))
    }

    @Test
    fun `hockey and basketball props grade from the box score, and say why one can't be`() {
        val box = padded(
            PlayerLine("Matt Boldy", mapOf("PLAYER_GOALS" to 1.0, "ASSISTS" to 1.0, "POINTS" to 2.0, "SHOTS_ON_GOAL" to 3.0)),
            PlayerLine("Filip Gustavsson", mapOf("SAVES" to 20.0)),
        )
        val wild = GameScore("1", "NHL", home = "Detroit Red Wings", away = "Minnesota Wild", startMs = METS_START, final = true, called = false, homeScore = 4, awayScore = 5)
        fun g(market: String, selection: String) =
            BetGrader.gradeDetailed(BetGrader.pickOf(bet(market, selection, "Minnesota Wild @ Detroit Red Wings", "NHL"))!!, wild, box)
        assertEquals(BetStatus.WON, (g("Player Shots on Goal", "Matt Boldy Over 2.5") as BetGrader.Grade.Result).status)
        assertEquals("Matt Boldy: 3 Shots On Goal", (g("Player Shots on Goal", "Matt Boldy Over 2.5") as BetGrader.Grade.Result).evidence)
        assertEquals(BetStatus.LOST, (g("Player Saves", "Filip Gustavsson Over 24.5") as BetGrader.Grade.Result).status)
        assertEquals(BetStatus.PUSH, (g("Player Points", "Matt Boldy Over 2") as BetGrader.Grade.Result).status)
        // A hockey box score lists everyone who dressed: a goalie who isn't in it didn't play, and Novig refunds the bet.
        val talbot = g("Player Saves", "Cam Talbot Over 20.5") as BetGrader.Grade.Result
        assertEquals(BetStatus.VOID, talbot.status)
        assertEquals("Cam Talbot didn't play (not in the box score): counted as a void, \$0 (Novig may settle it at a fair value instead)", talbot.evidence)
        // A stat the box score doesn't carry says so and stays for a tap.
        assertEquals(true, (g("Player Steals", "Matt Boldy Over 0.5") as BetGrader.Grade.Manual).reason.contains("no Steals"))
        // A name one letter off a player in it is a spelling, not a scratch: also left to a tap.
        assertEquals(true, (g("Player Saves", "Filip Gustavson Over 20.5") as BetGrader.Grade.Manual).reason.contains("under that name"))
        // Before the game is over: waiting, not manual.
        val live = BetGrader.gradeDetailed(Pick.Moneyline("Minnesota Wild"), wild.copy(final = false))
        assertEquals(BetGrader.Grade.Waiting("The game isn't over yet"), live)
    }

    @Test
    fun `a graded bet says what it rests on`() {
        fun ev(market: String, selection: String) = (BetGrader.gradeDetailed(BetGrader.pickOf(bet(market, selection))!!, mets) as BetGrader.Grade.Result).evidence
        assertEquals("Final: New York Mets 7, Washington Nationals 1", ev("Moneyline", "New York Mets"))
        assertEquals("Final: New York Mets 7, Washington Nationals 1 (total 8)", ev("Total Runs", "Over 8"))
        assertEquals("First half: New York Mets 0, Washington Nationals 1", ev("F5 Spread", "New York Mets -0.5"))
        val t = (BetGrader.gradeDetailed(tennis("Moneyline", "Alexandre Muller"), muller) as BetGrader.Grade.Result).evidence
        assertEquals("Final: Alexandre Muller 2-1 Luka Pavlovic (sets), games 6-4 6-7 6-3", t)
    }

    @Test
    fun `a market that can't be read says why`() {
        assertEquals("3-way (draw) markets aren't graded automatically", BetGrader.whyNot("Moneyline 3-way", "Draw"))
        assertEquals("Quarter, period and second-half markets aren't graded automatically", BetGrader.whyNot("1st Quarter Spread", "Dallas Cowboys -0.5"))
        assertEquals(true, BetGrader.whyNot("First Touchdown Scorer", "Jonathan Taylor Yes").contains("play-by-play"))
        assertEquals(true, BetGrader.whyNot("Some Novelty Market", "Something Over 1.5").startsWith("Couldn't read \"Some Novelty Market\""))
    }

    /**
     * Tj, 2026-10-03 (Diagnostics v0.56.1: "so laggy I almost couldn't use it", then Android ended the app): its main thread was
     * re-reading every listed bet's wording (PlacedIndex.has, then pickOf's regexes) on every screen update. A wording is read once and
     * its answer kept: asking again gives back the same answer, not a fresh read.
     */
    @Test
    fun `a bet's wording is read once and its answer kept`() {
        val first = BetGrader.pickOf("Player Receptions", "Brock Bowers Under 4.5")
        assertNotNull(first)
        assertSame(first, BetGrader.pickOf("Player Receptions", "Brock Bowers Under 4.5"))
        assertSame(BetGrader.pickOf("Spread", "Dallas Cowboys -3.5"), BetGrader.pickOf("Spread", "Dallas Cowboys -3.5"))
        // A different wording is its own read.
        assertEquals(Pick.Spread("Dallas Cowboys", 3.5, Period.GAME), BetGrader.pickOf("Spread", "Dallas Cowboys +3.5"))
    }

    @Test
    fun `asking again reads nothing, an unreadable wording included`() {
        val before = BetGrader.readCount.get()
        repeat(5) { BetGrader.pickOf("Player Receptions", "A Memo Tester Under 6.5") }
        repeat(5) { assertNull(BetGrader.pickOf("Some Novelty Market 7", "Something Over 1.5")) }
        assertEquals("two wordings, each read once", 2L, BetGrader.readCount.get() - before)
    }

    companion object {
        /** [lines] among enough other players for the box score to count as posted (a real one lists dozens). */
        fun padded(vararg lines: PlayerLine): List<PlayerLine> =
            lines.toList() + (1..12).map { PlayerLine("Filler$it Bench$it", mapOf("HITS" to 0.0)) }

        val METS_START = java.time.Instant.parse("2026-09-26T16:35:00Z").toEpochMilli()
        val FALCONS_START = java.time.Instant.parse("2026-09-25T00:15:00Z").toEpochMilli()
        val TENNIS_START = java.time.Instant.parse("2026-09-22T05:00:00Z").toEpochMilli()
    }
}
