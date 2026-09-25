package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OddsTest {

    @Test
    fun `american to decimal for a positive underdog price`() {
        assertEquals(3.0, Odds.americanToDecimal(200), 1e-9)
        assertEquals(2.0, Odds.americanToDecimal(100), 1e-9)
    }

    @Test
    fun `american to decimal for a negative favorite price`() {
        assertEquals(1.9090909091, Odds.americanToDecimal(-110), 1e-9)
        assertEquals(1.5, Odds.americanToDecimal(-200), 1e-9)
    }

    @Test
    fun `decimal to american round trips through american odds`() {
        assertEquals(200, Odds.decimalToAmerican(3.0))
        assertEquals(-110, Odds.decimalToAmerican(1.9090909091))
        assertEquals(-200, Odds.decimalToAmerican(1.5))
    }

    @Test
    fun `implied probability of even money is one half`() {
        assertEquals(0.5, Odds.impliedProbability(2.0), 1e-9)
    }

    @Test
    fun `implied probability rejects odds that are not greater than one`() {
        assertThrows(IllegalArgumentException::class.java) { Odds.impliedProbability(1.0) }
    }

    @Test
    fun `novig price to decimal odds inverts a probability quote`() {
        // Novig's own price format (RESEARCH.md §2): 0.524 means $0.524 staked to win $1.00.
        assertEquals(1.0 / 0.524, Odds.probabilityToDecimal(0.524), 1e-9)
    }

    @Test
    fun `novig price to decimal odds rejects values outside (0,1)`() {
        assertThrows(IllegalArgumentException::class.java) { Odds.probabilityToDecimal(0.0) }
        assertThrows(IllegalArgumentException::class.java) { Odds.probabilityToDecimal(1.0) }
    }

    @Test
    fun `novig prices convert to american odds the way the live book reads`() {
        // Verified 2026-09-25 on a live book: taking DAL at 0.385 is +160, BAL at 0.62 is -163.
        assertEquals(160, Odds.probabilityToAmerican(0.385))
        assertEquals(-163, Odds.probabilityToAmerican(0.62))
        assertEquals("+160", Odds.formatAmerican(160))
        assertEquals("-163", Odds.formatAmerican(-163))
        assertEquals("+100", Odds.formatAmerican(-100))
    }
}
