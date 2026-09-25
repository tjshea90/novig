package com.tjshea.vigilant.data.novig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every string here was seen on Novig's live public API on 2026-09-25. */
class NovigTextTest {

    @Test
    fun `event descriptions split into away and home`() {
        assertEquals(Matchup("Baltimore Ravens", "Dallas Cowboys"), NovigText.parseMatchup("Baltimore Ravens @ Dallas Cowboys"))
        assertEquals(Matchup("Alabama", "Mississippi State"), NovigText.parseMatchup("Alabama @ Mississippi State"))
        assertNull(NovigText.parseMatchup("Super Bowl Winner"))
    }

    @Test
    fun `spread outcomes split into label and signed point`() {
        assertEquals("DAL" to 20.5, NovigText.parseSpreadOutcome("DAL +20.5"))
        assertEquals("BAL" to -20.5, NovigText.parseSpreadOutcome("BAL -20.5"))
        assertEquals("Coventry City" to 1.5, NovigText.parseSpreadOutcome("Coventry City +1.5"))
        assertEquals("V. Kopriva" to 0.5, NovigText.parseSpreadOutcome("V. Kopriva +0.5"))
        assertNull(NovigText.parseSpreadOutcome("DAL"))
    }

    @Test
    fun `total outcomes parse over and under`() {
        assertEquals(true to 10.5, NovigText.parseTotalOutcome("Over 10.5"))
        assertEquals(false to 47.0, NovigText.parseTotalOutcome("Under 47"))
        assertNull(NovigText.parseTotalOutcome("Yes"))
    }

    @Test
    fun `team totals and props name their team or player in the description`() {
        assertEquals("Patrick Mahomes", NovigText.subjectOf("Patrick Mahomes 233.5 PASSING_YARDS", "PASSING_YARDS"))
        assertEquals("Los Angeles Rams", NovigText.subjectOf("Los Angeles Rams 22.5 TEAM_TOTAL", "TEAM_TOTAL"))
        assertEquals("CJ Donaldson Jr.", NovigText.subjectOf("CJ Donaldson Jr. 0.5 TOUCHDOWNS", "TOUCHDOWNS"))
        assertNull(NovigText.subjectOf("CJ Donaldson Jr. FIRST_TOUCHDOWN_SCORER", "FIRST_TOUCHDOWN_SCORER"))
    }

    @Test
    fun `prices parse exactly whether padded or not`() {
        assertEquals(380, NovigText.priceMilli("0.38"))
        assertEquals(380, NovigText.priceMilli("0.380"))
        assertEquals(615, NovigText.priceMilli("0.615"))
        assertEquals(1, NovigText.priceMilli("0.001"))
        assertNull(NovigText.priceMilli("1.000"))
        assertNull(NovigText.priceMilli("abc"))
    }
}
