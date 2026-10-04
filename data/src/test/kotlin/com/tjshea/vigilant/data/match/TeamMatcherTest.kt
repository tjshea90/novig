package com.tjshea.vigilant.data.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamMatcherTest {

    @Test
    fun `novig short names match odds api full names`() {
        assertEquals(1.0, TeamMatcher.similarity("Alabama", "Alabama Crimson Tide"), 0.0)
        assertEquals(1.0, TeamMatcher.similarity("Mississippi State", "Mississippi State Bulldogs"), 0.0)
        assertEquals(1.0, TeamMatcher.similarity("Newcastle United FC", "Newcastle United"), 0.0)
        assertEquals(1.0, TeamMatcher.similarity("L. Hernandez", "Luis Hernandez"), 0.0)
        assertEquals(1.0, TeamMatcher.similarity("Los Angeles Clippers", "LA Clippers"), 0.0)
        assertEquals(0.0, TeamMatcher.similarity("Alabama", "Ole Miss Rebels"), 0.0)
    }

    @Test
    fun `abbreviations resolve to the right side of the matchup`() {
        // (away, home) exactly as Novig's events read, with the outcome labels its books use.
        assertEquals(true, TeamMatcher.firstLabelIsAway("BAL", "DAL", "Baltimore Ravens", "Dallas Cowboys"))
        assertEquals(false, TeamMatcher.firstLabelIsAway("DAL", "BAL", "Baltimore Ravens", "Dallas Cowboys"))
        assertEquals(false, TeamMatcher.firstLabelIsAway("NO", "ATL", "Atlanta Falcons", "New Orleans Saints"))
        assertEquals(false, TeamMatcher.firstLabelIsAway("MSST", "ALA", "Alabama", "Mississippi State"))
        assertEquals(true, TeamMatcher.firstLabelIsAway("DRKE", "DAV", "Drake", "Davidson"))
        assertEquals(true, TeamMatcher.firstLabelIsAway("JAX", "CIN", "Jacksonville Jaguars", "Cincinnati Bengals"))
        assertEquals(false, TeamMatcher.firstLabelIsAway("ATH", "HOU", "Houston Astros", "Oakland Athletics"))
    }

    @Test
    fun `school abbreviations with a U resolve - both live misses from 2026-09-25`() {
        assertEquals(false, TeamMatcher.firstLabelIsAway("UK", "USA", "South Alabama", "Kentucky"))
        assertEquals(false, TeamMatcher.firstLabelIsAway("NMSU", "UNM", "New Mexico", "New Mexico State"))
    }

    @Test
    fun `same-city teams are told apart by initials`() {
        assertEquals(true, TeamMatcher.firstLabelIsAway("LAR", "LAC", "Los Angeles Rams", "Los Angeles Chargers"))
        assertEquals(false, TeamMatcher.firstLabelIsAway("NYJ", "NYG", "New York Giants", "New York Jets"))
        assertEquals(true, TeamMatcher.firstLabelIsAway("CHC", "CWS", "Chicago Cubs", "Chicago White Sox"))
    }

    @Test
    fun `fighters and players match by surname and initial`() {
        assertEquals(false, TeamMatcher.firstLabelIsAway("L. Hernandez", "S. Dumas", "Sedriques Dumas", "Luis Hernandez"))
        assertEquals(true, TeamMatcher.labelIsAway("Newcastle", "Newcastle United FC", "Coventry City FC"))
    }

    @Test
    fun `refuses to guess when neither label fits`() {
        assertNull(TeamMatcher.firstLabelIsAway("XYZ", "QQQ", "Baltimore Ravens", "Dallas Cowboys"))
        assertTrue(TeamMatcher.abbreviationScore("Luis Hernandez", "Luis Hernandez") == 0)
    }

    @Test
    fun `a school qualifier on one side means a different school`() {
        // Full test 2026-09-25: "Texas" used to score 1.0 against "Texas Tech Red Raiders".
        assertTrue(TeamMatcher.similarity("Texas", "Texas Tech Red Raiders") < 0.5)
        assertTrue(TeamMatcher.similarity("Kansas", "Kansas State Wildcats") < 0.5)
        assertTrue(TeamMatcher.similarity("Kansas State", "Kansas Jayhawks") < 0.5)
        assertTrue(TeamMatcher.similarity("Texas", "Texas A&M Aggies") < 0.5)
        assertEquals(1.0, TeamMatcher.similarity("Texas A&M", "Texas A&M Aggies"), 0.0)
        assertEquals(1.0, TeamMatcher.similarity("Mississippi State", "Mississippi State Bulldogs"), 0.0)
        assertEquals(1.0, TeamMatcher.similarity("Texas", "Texas Longhorns"), 0.0)
    }

    @Test
    fun `closeness prefers the name with fewer extra words`() {
        assertTrue(TeamMatcher.closeness("Miami Florida", "Miami Hurricanes") > TeamMatcher.closeness("Miami Florida", "Miami (OH) RedHawks"))
    }

    @Test
    fun `a name is tokenized once and re-used, since a plan compares it thousands of times`() {
        val first = TeamMatcher.tokens("Mississippi State Bulldogs")
        assertTrue(first === TeamMatcher.tokens("Mississippi State Bulldogs"))
        assertEquals(listOf("mississippi", "st", "bulldogs"), first)
    }

    /**
     * Tj's scan-study file, 2026-10-03: a Washington State moneyline "closed" at another State game's price. Two names that share only a school word ("State" is the
     * token `st`) score 0.5, so a pairing needs one team that really matches, as the scan's planner already demands (1.5 together).
     */
    @Test
    fun `two games that share only the word State are not the same game, and one team really matching is enough`() {
        assertEquals(0.0, TeamMatcher.gameScore("Washington State", "Fresno State", "Oregon State Beavers", "Idaho State Bengals"), 0.0)
        assertEquals(2.0, TeamMatcher.gameScore("Washington State", "Fresno State", "Washington State Cougars", "Fresno State Bulldogs"), 0.0)
        // One team exact, the other only a partial name ("Miami (FL)" / "Miami Hurricanes"): the same game.
        assertEquals(1.5, TeamMatcher.gameScore("Miami (FL)", "Clemson", "Miami Hurricanes", "Clemson Tigers"), 0.0)
        // Home and away reversed are not this order's game (callers that accept a swap ask both ways).
        assertEquals(0.0, TeamMatcher.gameScore("Washington State", "Fresno State", "Fresno State Bulldogs", "Washington State Cougars"), 0.0)
        // One team the same, the other sharing only the word: another game of that team's pairing would need the same team twice, but the score alone says 1.5.
        assertEquals(1.5, TeamMatcher.gameScore("Washington State", "Fresno State", "Oregon State Beavers", "Fresno State Bulldogs"), 0.0)
    }

    @Test
    fun `a team is the side of a game it fits best - never the first that shares a word, and none when two fit alike`() {
        assertEquals(1, TeamMatcher.whichOf("Washington State", "Washington State Cougars", "Fresno State Bulldogs"))
        assertEquals(2, TeamMatcher.whichOf("Fresno State", "Washington State Cougars", "Fresno State Bulldogs"))
        // Both sides share "State" with it and neither is it: no answer, not the first.
        assertEquals(0, TeamMatcher.whichOf("Washington State", "Oregon State", "Idaho State"))
        assertEquals(0, TeamMatcher.whichOf("Alabama", "Ole Miss Rebels", "Georgia Bulldogs"))
        assertEquals(2, TeamMatcher.whichOf("Miami (FL)", "Clemson Tigers", "Miami Hurricanes"))
    }
}
