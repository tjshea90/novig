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

    /** The unrounded points, so a small improvement shows even when the half-star rounding hides it. */
    private fun pts(books: Int, agreeing: Int, ev: Double, sharp: Boolean = false, against: Boolean = false, spread: Double? = null) =
        BetRating.rate(BetRating.Inputs(books, agreeing, ev, sharp, against, spread)).points

    @Test fun `each ingredient moves the rating the right way`() {
        val base = pts(4, 3, 0.03, spread = 0.02)
        assertTrue(pts(4, 4, 0.03, spread = 0.02) > base)           // more agreement
        assertTrue(pts(8, 6, 0.03, spread = 0.02) > base)           // more books at the same share agreeing
        assertTrue(pts(4, 3, 0.05, spread = 0.02) > base)           // more edge
        assertTrue(pts(4, 3, 0.03, sharp = true, spread = 0.02) > base)  // a sharp book agrees
        assertTrue(pts(4, 3, 0.03, spread = 0.005) > base)          // tighter consensus
        assertTrue(pts(4, 3, 0.03, against = true, spread = 0.02) < base) // a sharp book against
        assertTrue(rate(4, 3, 0.03, sharp = true, spread = 0.02) > rate(4, 3, 0.03, spread = 0.02))
        assertTrue(rate(4, 3, 0.03, against = true, spread = 0.02) < rate(4, 3, 0.03, spread = 0.02))
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

    @Test
    fun `under 2 percent EV a bet is at most 3 stars, however well the books agree`() {
        val strong = BetRating.Inputs(books = 5, agreeing = 5, ev = 0.019, sharpAgrees = true, sharpAgainst = false, spread = 0.005)
        assertEquals(3.0, BetRating.rate(strong).stars, 1e-9)
        assertTrue("the same bet at +2% keeps its rating", BetRating.rate(strong.copy(ev = 0.02)).stars >= 3.5)
        assertEquals("no edge is still 1", 1.0, BetRating.rate(strong.copy(ev = 0.0)).stars, 1e-9)
        // A bet already under 3 stars is untouched.
        val weak = BetRating.Inputs(books = 2, agreeing = 1, ev = 0.01, sharpAgrees = false, sharpAgainst = false)
        assertTrue(BetRating.rate(weak).stars <= 3.0)
    }

    @Test
    fun `SR is the shipped weighting, its parts total 100, and ST1 can still be scored beside it`() {
        val w = BetRating.CURRENT
        assertEquals(BetRating.Weights.SR, w)
        assertEquals(100.0, w.coverage + w.agreement + w.edge + w.sharp + w.consensus, 1e-9)
        assertEquals(100.0, BetRating.Weights.ST1.let { it.coverage + it.agreement + it.edge + it.sharp + it.consensus }, 1e-9)
        assertEquals(15.0, w.sharp, 0.0); assertEquals(30.0, w.edge, 0.0); assertEquals(25.0, w.agreement, 0.0); assertEquals(0.5, w.typeScale, 0.0)
        // A sharp book agreeing is worth (15 - 0.3 x 15) points over none pricing it under SR, (25 - 0.3 x 25) under ST1.
        val none = BetRating.Inputs(5, 4, 0.04, sharpAgrees = false, sharpAgainst = false, spread = 0.02)
        val sharp = none.copy(sharpAgrees = true)
        assertEquals(10.5, BetRating.rate(sharp).points - BetRating.rate(none).points, 1e-9)
        assertEquals(17.5, BetRating.rate(sharp, BetRating.Weights.ST1).points - BetRating.rate(none, BetRating.Weights.ST1).points, 1e-9)
        // The edge now carries more: +1 point of EV (under 6%) is worth 5 points under SR, 4.17 under ST1.
        assertEquals(5.0, BetRating.rate(none.copy(ev = 0.05)).points - BetRating.rate(none).points, 1e-9)
    }
}
