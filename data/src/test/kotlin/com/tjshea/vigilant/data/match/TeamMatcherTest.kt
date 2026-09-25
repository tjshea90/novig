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
}
