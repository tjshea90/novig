package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.trading.AutoBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-11: every bet rated 1 to 5 stars in half steps from coverage, agreement, edge, sharp book and consensus; the auto-bet takes a minimum. */
class BetRatingTest {
    private fun rate(books: Int, agreeing: Int, ev: Double, sharp: Boolean = false, against: Boolean = false, spread: Double? = null) =
        BetRating.rate(BetRating.Inputs(books, agreeing, ev, sharp, against, spread)).stars

    @Test fun `an excellent bet is five stars`() {
        assertEquals(5.0, rate(6, 6, 0.06, sharp = true, spread = 0.008), 0.0)
    }

    @Test fun `a thin weak bet is low and a middling one lands in the middle`() {
        assertTrue(rate(2, 2, 0.01) <= 2.0)
        assertTrue(rate(1, 1, 0.02) <= 1.5)
        val mid = rate(4, 3, 0.03)
        assertTrue("middling bet was $mid", mid in 2.5..3.5)
    }

    @Test fun `each ingredient moves the rating the right way`() {
        val base = rate(4, 3, 0.03, spread = 0.02)
        assertTrue(rate(4, 4, 0.03, spread = 0.02) > base)           // more agreement
        assertTrue(rate(8, 6, 0.03, spread = 0.02) > base)           // more books at the same share agreeing
        assertTrue(rate(4, 3, 0.05, spread = 0.02) > base)           // more edge
        assertTrue(rate(4, 3, 0.03, sharp = true, spread = 0.02) > base)  // a sharp book agrees
        assertTrue(rate(4, 3, 0.03, spread = 0.005) > base)          // tighter consensus
        assertTrue(rate(4, 3, 0.03, against = true, spread = 0.02) < base) // a sharp book against
    }

    @Test fun `ceilings hold however good the rest looks`() {
        assertTrue(rate(2, 2, 0.08, sharp = true, spread = 0.0) <= 3.0)
        assertTrue(rate(3, 3, 0.08, sharp = true, spread = 0.0) <= 4.0)
        assertTrue(rate(8, 8, 0.08, sharp = true, against = true, spread = 0.0) <= 2.5)
    }

    @Test fun `a huge edge is doubted and no edge is one star`() {
        assertTrue(rate(6, 6, 0.40, sharp = true, spread = 0.01) < rate(6, 6, 0.08, sharp = true, spread = 0.01))
        assertEquals(1.0, rate(6, 6, 0.0, sharp = true, spread = 0.0), 0.0)
        assertEquals(1.0, rate(6, 6, -0.02, sharp = true, spread = 0.0), 0.0)
    }

    @Test fun `ratings come in half steps from one to five with their words`() {
        var s = 0
        for (b in 0..8) for (a in 0..b) for (ev in listOf(0.0, 0.005, 0.02, 0.04, 0.07, 0.15, 0.3)) for (sh in listOf(false, true)) {
            val r = rate(b, a, ev, sh)
            assertTrue(r in 1.0..5.0)
            assertEquals(r, Math.round(r * 2) / 2.0, 0.0)
            s++
        }
        assertEquals("3½", BetRating.label(3.5))
        assertEquals("4", BetRating.label(4.0))
        assertEquals("★★★½", BetRating.glyphs(3.5))
        assertEquals("★★", BetRating.glyphs(2.0))
        assertEquals("1.5", BetRating.text(1.5))
    }

    @Test fun `spread is the standard deviation`() {
        assertNull(BetRating.spreadOf(listOf(0.5)))
        assertEquals(0.05, BetRating.spreadOf(listOf(0.45, 0.55))!!, 1e-12)
    }

    @Test fun `the auto-bet minimum refuses lower ratings and waits on unrated ones`() {
        val rules = AutoBet.rules(ScanSettings(vigGuards = ScanGuards(autoBetMinStars = 3.5)))
        assertEquals(3.5, rules.minStars, 0.0)
        assertNotNull(AutoBet.starsReason(rules, 3.0))
        assertNull(AutoBet.starsReason(rules, 3.5))
        assertNull(AutoBet.starsReason(rules, 5.0))
        assertNotNull(AutoBet.starsReason(rules, null))
        assertNull(AutoBet.starsReason(AutoBet.rules(ScanSettings()), 1.0))
        assertNull(AutoBet.starsReason(AutoBet.rules(ScanSettings()), null))
    }
}
