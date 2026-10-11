package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.BookSides
import com.tjshea.vigilant.engine.Odds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tj, 2026-10-11: the bet's sheet lists each book with its odds for both sides of the bet. */
class BookSidesTest {
    private fun dec(vararg a: Int) = a.map { Odds.americanToDecimal(it) }

    @Test
    fun `both sides of a two way line`() {
        val odds = dec(-110, -105)
        assertEquals("-110", BookSides.thisSide(odds, 0))
        assertEquals("-105", BookSides.otherSides(odds, 0))
        assertEquals("-105", BookSides.thisSide(odds, 1))
        assertEquals("-110", BookSides.otherSides(odds, 1))
    }

    @Test
    fun `a three way line lists the other two`() {
        assertEquals("+250 / +210", BookSides.otherSides(dec(150, 250, 210), 0))
    }

    @Test
    fun `no side index gives nothing`() {
        assertNull(BookSides.thisSide(dec(-110, -110), null))
        assertNull(BookSides.otherSides(dec(-110, -110), null))
    }
}
