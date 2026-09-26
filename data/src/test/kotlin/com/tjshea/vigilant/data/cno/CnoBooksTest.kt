package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.engine.Devig
import com.tjshea.vigilant.engine.Odds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every book's price for a CNO bet, and Vigilant's own worst-case check of it (RESEARCH.md §19). */
class CnoBooksTest {

    private fun view(withPinnacle: Boolean = true) = CnoBooks.parse(CnoFixtures.gameGrid(withPinnacle), sideId = "102", bet = "Joe Receiver Under 69.5", fetchedAtMs = 1L)!!

    @Test
    fun `the bet's row is found by its side id, its other side next to it, with every book's prices`() {
        val v = view()
        assertEquals("Joe Receiver Under 69.5", v.bet)
        assertEquals("Joe Receiver Over 69.5", v.otherBet)
        assertEquals(109, v.cnoFair)
        assertFalse(v.cnoFairOneWay)
        val px = v.prices.first { it.code == "PX" }
        assertEquals(-107, px.odds)
        assertEquals(53.0, px.available!!, 0.0)
        assertEquals(-129, px.otherOdds)
        assertEquals(112.0, px.otherAvailable!!, 0.0)
        assertEquals("ProphetX", px.name)
        val fd = v.prices.first { it.code == "FD" }
        assertNull(fd.odds) // FanDuel lists only the Over
        assertEquals(-125, fd.otherOdds)
        assertFalse(fd.twoSided)
        assertEquals(13662.0, v.prices.first { it.code == "KI" }.otherAvailable!!, 0.0)
    }

    @Test
    fun `a bet that isn't on the page is null, and a name match works without a side id`() {
        assertNull(CnoBooks.parse(CnoFixtures.gameGrid(), sideId = "999", bet = "Someone Else Over 1.5", fetchedAtMs = 1L))
        assertEquals("Joe Receiver Over 69.5", CnoBooks.parse(CnoFixtures.gameGrid(), sideId = null, bet = "Joe Receiver Over 69.5", fetchedAtMs = 1L)!!.otherBet?.let { "Joe Receiver Over 69.5" })
    }

    @Test
    fun `the two sides of a line are recognized, different lines aren't`() {
        assertTrue(CnoBooks.complement("Joe Receiver Over 69.5", "Joe Receiver Under 69.5"))
        assertFalse(CnoBooks.complement("Joe Receiver Over 69.5", "Joe Receiver Under 79.5"))
        assertFalse(CnoBooks.complement("Joe Receiver Over 69.5", "Joe Receiver Over 69.5"))
        assertFalse(CnoBooks.complement("Joe Receiver Over 69.5", "Sam Runner Under 69.5"))
        assertTrue(CnoBooks.complement("Over 47.5", "Under 47.5"))
        assertTrue(CnoBooks.complement("Charlotte FC No", "Charlotte FC Yes"))
        assertTrue(CnoBooks.complement("Kansas City Chiefs -10.5", "Miami Dolphins +10.5"))
        assertFalse(CnoBooks.complement("Kansas City Chiefs -10.5", "Miami Dolphins -10.5"))
        assertFalse(CnoBooks.complement("Kansas City Chiefs -10.5", "Miami Dolphins +3.5"))
    }

    @Test
    fun `Vigilant's check devigs only books with both sides, leaves Novig and pick'em out, and takes the worse of mean and median`() {
        val c = CnoBooks.check(view(), listOdds = 120)
        // Two-sided (Novig aside): Pinnacle, ProphetX, Kalshi. One side only: FanDuel, DraftKings
        // (PrizePicks isn't counted at all: pick'em lines aren't odds).
        assertEquals(3, c.twoSided)
        assertEquals(2, c.oneSided)
        val fairs = listOf(-118 to 104, -107 to -129, -103 to -126).map { (mine, theirs) ->
            Devig.worstCase(listOf(1 / Odds.americanToDecimal(mine), 1 / Odds.americanToDecimal(theirs)))[0]
        }
        val median = fairs.sorted()[1]
        assertEquals(minOf(fairs.average(), median), c.fairProbability!!, 1e-12)
        assertEquals(120, c.novigOdds)
        assertEquals(c.fairProbability!! * 2.2 - 1, c.ev!!, 1e-12)
        assertEquals(CnoBooks.Verdict.CONFIRMED, c.verdict)
    }

    @Test
    fun `with only two books pricing both sides the bet is called thin, whatever its EV`() {
        val c = CnoBooks.check(view(withPinnacle = false), listOdds = 120)
        assertEquals(2, c.twoSided)
        assertEquals(CnoBooks.Verdict.THIN, c.verdict)
    }

    @Test
    fun `when the books say Novig's price is too short, the edge isn't confirmed`() {
        val v = view().let { it.copy(prices = it.prices.map { p -> if (p.code == "NV") p.copy(odds = -110) else p }) }
        val c = CnoBooks.check(v, listOdds = 120)
        assertEquals(-110, c.novigOdds) // the game page's Novig price wins over the list's
        assertTrue(c.ev!! < 0)
        assertEquals(CnoBooks.Verdict.NOT_CONFIRMED, c.verdict)
    }

    @Test
    fun `no book with both sides means no check, and a crossed exchange quote is normalized, not devigged`() {
        val oneSided = CnoBooksView("X Over 1.5", null, null, false, listOf(CnoBookPrice("DK", -150, null, null, null)), 1L)
        assertEquals(CnoBooks.Verdict.NO_DATA, CnoBooks.check(oneSided, 120).verdict)
        // +105 / +105 implies 97.6%: no vig to remove, so each side is simply 50%.
        assertEquals(0.5, CnoBooks.fairFor(105, 105)!!, 1e-12)
    }

    @Test
    fun `live bets are judged net of Novig's taker fee`() {
        val pre = CnoBooks.evAt(0.5, 110, live = false)
        val live = CnoBooks.evAt(0.5, 110, live = true)
        assertEquals(0.5 * 2.1 - 1, pre, 1e-12)
        assertTrue(live < pre)
    }
}
