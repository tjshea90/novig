package com.tjshea.vigilant.data.tracker

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj's 2026-09-29 screenshot: three open bets on finished games that "couldn't be graded" (KC Concepcion's rushing yards,
 * Erick All Jr.'s receptions, Kade Anderson's earned runs). Graded here from ESPN's REAL box scores of those two games
 * (Panthers @ Browns and Bengals @ Steelers, week 2026-09-27, saved and trimmed to what's parsed) and CNO's real market names.
 */
class RealBoxGradingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun box(name: String) = FreeScores.parseEspnBox(json.parseToJsonElement(javaClass.classLoader!!.getResource(name)!!.readText()))

    private val browns by lazy { box("espn-nfl-panthers-browns.json") }
    private val steelers by lazy { box("espn-nfl-bengals-steelers.json") }

    private fun game(away: String, home: String, id: String) = GameScore(
        id, "NFL", home = home, away = away, startMs = BetGraderTest.FALCONS_START, final = true, called = false, homeScore = 20, awayScore = 17,
    )

    private val brownsGame = game("Carolina Panthers", "Cleveland Browns", "401872949")
    private val steelersGame = game("Cincinnati Bengals", "Pittsburgh Steelers", "401872950")

    private fun bet(market: String, selection: String, event: String) = TrackedBet(
        id = "b", createdAtMs = 0, league = "NFL", eventName = event, startsTs = BetGraderTest.FALCONS_START, marketLabel = market, selection = selection,
        marketId = "", outcomeId = "", price = 0.5, cost = 0.5, fairAtBet = null, evPercentAtBet = null, stake = 1.0,
    )

    private fun grade(market: String, selection: String, game: GameScore, box: List<PlayerLine>, event: String) =
        BetGrader.gradeDetailed(BetGrader.pickOf(bet(market, selection, event))!!, game, box)

    @Test
    fun `a receiver with no carries has zero rushing yards - KC Concepcion Over 5_5 lost`() {
        val g = grade("Player Rushing Yards", "KC Concepcion Over 5.5", brownsGame, browns, "Carolina Panthers @ Cleveland Browns") as BetGrader.Grade.Result
        assertEquals(BetStatus.LOST, g.status)
        assertEquals("KC Concepcion: no Rushing Yards recorded (counted as 0)", g.evidence)
        // The Under of the same line wins; his receiving line (2 for 9) still reads.
        assertEquals(BetStatus.WON, (grade("Player Rushing Yards", "KC Concepcion Under 5.5", brownsGame, browns, "Carolina Panthers @ Cleveland Browns") as BetGrader.Grade.Result).status)
        val rec = grade("Player Receiving Yards", "KC Concepcion Over 8.5", brownsGame, browns, "Carolina Panthers @ Cleveland Browns") as BetGrader.Grade.Result
        assertEquals(BetStatus.WON, rec.status)
        assertEquals("KC Concepcion: 9 Receiving Yards", rec.evidence)
    }

    @Test
    fun `a player who played and caught nothing is not in a football box score - Erick All Jr Under 0_5 receptions won`() {
        assertTrue(steelers.none { it.name.contains("All") && it.name.startsWith("Erick") })
        val g = grade("Player Receptions", "Erick All Jr. Under 0.5", steelersGame, steelers, "Cincinnati Bengals @ Pittsburgh Steelers") as BetGrader.Grade.Result
        assertEquals(BetStatus.WON, g.status)
        assertEquals("Erick All Jr.: no Receptions recorded (no line in the box score, counted as 0)", g.evidence)
        assertEquals(BetStatus.LOST, (grade("Player Receptions", "Erick All Jr. Over 0.5", steelersGame, steelers, "Cincinnati Bengals @ Pittsburgh Steelers") as BetGrader.Grade.Result).status)
    }

    @Test
    fun `a player the game's injury report has out did not play, so his bet is void`() {
        val young = steelers.single { it.name == "Colbie Young" }
        assertTrue(young.inactive)
        val g = grade("Player Receiving Yards", "Colbie Young Over 9.5", steelersGame, steelers, "Cincinnati Bengals @ Pittsburgh Steelers") as BetGrader.Grade.Result
        assertEquals(BetStatus.VOID, g.status)
        assertEquals("Colbie Young was ruled out and didn't play: counted as a void, \$0 (Novig may settle it at a fair value instead)", g.evidence)
        // Questionable players who did play are graded from their lines, and a questionable one with no line isn't "out".
        assertTrue(steelers.none { it.name == "Jalen Davis" && it.inactive })
    }

    @Test
    fun `injured players never crowd real ones - a played player keeps his line`() {
        val chase = steelers.single { it.name == "Ja'Marr Chase" }
        assertEquals(false, chase.inactive)
        assertEquals(98.0, chase.stats.getValue("RECEIVING_YARDS"), 0.0)
        assertEquals(1, steelers.count { it.name == "Ja'Marr Chase" })
    }

    @Test
    fun `a name one letter off someone in the box score waits for a tap instead of being counted zero`() {
        val g = grade("Player Receiving Yards", "Tee Higgens Over 60.5", steelersGame, steelers, "Cincinnati Bengals @ Pittsburgh Steelers")
        assertTrue((g as BetGrader.Grade.Manual).reason.contains("under that name"))
    }

    @Test
    fun `a box score with almost nobody in it is not posted yet`() {
        val g = grade("Player Receiving Yards", "Ja'Marr Chase Over 60.5", steelersGame, listOf(PlayerLine("Ja'Marr Chase", mapOf("RECEIVING_YARDS" to 98.0))), "Cincinnati Bengals @ Pittsburgh Steelers")
        assertEquals(BetGrader.Grade.Waiting("The box score isn't fully posted yet"), g)
    }

    @Test
    fun `CNO's real baseball market names read as Novig's stats`() {
        assertEquals("EARNED_RUNS", BetGrader.statOf("Player Earned Runs Allowed"))
        assertEquals("EARNED_RUNS", BetGrader.statOf("Player Earned Runs"))
        assertEquals("PITCHER_OUTS", BetGrader.statOf("Player Outs Recorded"))
        assertEquals("PITCHER_OUTS", BetGrader.statOf("Player Pitching Outs"))
        assertEquals("WALKS", BetGrader.statOf("Player Walks Allowed"))
        assertEquals("HITS_ALLOWED", BetGrader.statOf("Player Hits Allowed"))
        assertEquals("RBIS", BetGrader.statOf("Player Runs Batted In"))
        assertEquals("TOTAL_BASES", BetGrader.statOf("Player Total Bases"))
        assertEquals("PITCHER_STRIKEOUTS", BetGrader.statOf("Player Strikeouts"))
        assertEquals("BATTING_STRIKEOUTS", BetGrader.statOf("Player Batter Strikeouts"))
    }

    @Test
    fun `wordings with a filler word still read, and ones that stay ambiguous stay unread`() {
        assertEquals("POINTS", BetGrader.statOf("Player Points Scored"))
        assertEquals("REBOUNDS", BetGrader.statOf("Player Total Rebounds"))
        assertEquals("THREE_POINTERS_MADE", BetGrader.statOf("Player 3-Pointers Made"))
        assertEquals("RECEIVING_YARDS", BetGrader.statOf("Player Reception Yards"))
        // Rushing + Receiving is its own stat, never plain rushing.
        assertEquals("RUSHING_AND_RECEIVING_YARDS", BetGrader.statOf("Player Rushing + Receiving Yards"))
        assertEquals(null, BetGrader.statOf("Player Novelty Thing"))
    }

    @Test
    fun `Kade Anderson Over 1_5 earned runs grades from an MLB pitching line`() {
        val mets = GameScore("1", "MLB", "Athletics", "Seattle Mariners", BetGraderTest.METS_START, true, false, 3, 5)
        val b = bet("Player Earned Runs Allowed", "Kade Anderson Over 1.5", "Seattle Mariners @ Athletics").copy(league = "MLB", startsTs = BetGraderTest.METS_START)
        val line = PlayerLine("Kade Anderson", mapOf("EARNED_RUNS" to 3.0, "PITCHER_OUTS" to 15.0))
        val g = BetGrader.gradeDetailed(BetGrader.pickOf(b)!!, mets, BetGraderTest.padded(line)) as BetGrader.Grade.Result
        assertEquals(BetStatus.WON, g.status)
        assertEquals("Kade Anderson: 3 Earned Runs", g.evidence)
    }
}
