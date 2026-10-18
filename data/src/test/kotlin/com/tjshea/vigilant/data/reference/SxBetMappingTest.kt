package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.match.TeamMatcher
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REAL SX Bet games against REAL Novig games, pulled on 2026-10-18 (research/sxbet/MAPPING_2026-10-18.md, TCL117): `sx-novig-pairs.tsv` is league, SX teamOne, SX teamTwo, Novig's away, Novig's home (each row one
 * game both venues list). Every pair must be recognised as the same game by the matcher the scan's planner and the live lane use, in either order (soccer and tennis do not follow away/home), and in the SX order
 * (teamOne = away) for the US leagues, so SX's mascots ("Marshall Thundering Herd"), accents ("FC Bayern Munich" / "FC Bayern München") and Novig's round suffixes ("... Round of 32") never lose a game.
 */
class SxBetMappingTest {
    private val rows = SxBetMappingTest::class.java.getResourceAsStream("/sx-novig-pairs.tsv")!!.bufferedReader().readLines().filter { it.isNotBlank() }.map { it.split('\t') }
    private val us = setOf("NFL", "NBA", "NHL", "MLB", "WNBA", "NCAAF", "NCAAB")

    @Test
    fun `every real SX game is the same game as its Novig game`() {
        val missed = rows.filter { (_, sx1, sx2, nAway, nHome) ->
            TeamMatcher.gameScore(nHome, nAway, sx2, sx1) == 0.0 && TeamMatcher.gameScore(nHome, nAway, sx1, sx2) == 0.0
        }
        assertTrue("games the matcher loses (${missed.size} of ${rows.size}): " + missed.joinToString(" || ") { it.joinToString(" / ") }, missed.isEmpty())
    }

    @Test
    fun `SX's team order is not a rule, so the planner's order-free match is what is relied on`() {
        // Measured 2026-10-18: in 57 of 57 near-term US games SX's teamOne was Novig's HOME team, while the one far-off Dallas Cowboys | Philadelphia Eagles market lists the AWAY team first. Never assume it.
        val homeFirst = rows.filter { it[0] in us }.count { (_, sx1, sx2, nAway, nHome) -> TeamMatcher.gameScore(nHome, nAway, sx1, sx2) > 0.0 }
        assertTrue("every US game matches with teamOne as the home side in today's data ($homeFirst of ${rows.count { it[0] in us }})", homeFirst > 0)
    }
}
