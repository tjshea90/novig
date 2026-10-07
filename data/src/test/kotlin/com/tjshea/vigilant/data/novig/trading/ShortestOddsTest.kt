package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.trading.maker.MakerQuote
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-07: "anywhere there is a longest odds setting in the app, make a shortest odds setting as well. Make sure the settings do what they say."
 * Every longest-odds setting (the +EV feed, CrazyNinjaOdds' list, the auto-bet, bids) has a shortest one now, with one meaning: negative = nothing shorter than it
 * (−200), positive = underdogs at least that long (+110), 0 = no limit.
 */
class ShortestOddsTest {

    @Test
    fun `a negative limit skips shorter favourites only, a positive one skips every favourite and every shorter underdog`() {
        assertFalse(AutoBet.tooShort(-200, -200))
        assertFalse(AutoBet.tooShort(-200, -150))
        assertTrue(AutoBet.tooShort(-200, -201))
        assertFalse(AutoBet.tooShort(-200, 300))
        assertTrue(AutoBet.tooShort(110, -150))
        assertTrue(AutoBet.tooShort(110, 109))
        assertFalse(AutoBet.tooShort(110, 110))
        assertFalse(AutoBet.tooShort(110, 400))
        assertFalse(AutoBet.tooShort(0, -5000))
    }

    @Test
    fun `a typed limit is read as the nearest valid one`() {
        assertEquals(-100, AutoBet.normalizeMinOdds(-50))
        assertEquals(100, AutoBet.normalizeMinOdds(50))
        assertEquals(0, AutoBet.normalizeMinOdds(0))
        assertEquals(-250, AutoBet.normalizeMinOdds(-250))
        assertEquals(-200, AutoBet.rules(ScanSettings(autoBetMinOdds = -200)).minOdds)
        assertEquals(110, AutoBet.rules(ScanSettings(autoBetMinOdds = 110)).minOdds)
    }

    @Test
    fun `the auto-bet skips a price shorter than the limit and says so without the price`() {
        val r = AutoBet.rules(ScanSettings(autoBetMinOdds = -200))
        assertNull(AutoBet.judge(r, 0.04, check(), -200))
        assertEquals("its odds are shorter than your ${com.tjshea.vigilant.engine.Odds.formatAmerican(-200)} limit", AutoBet.judge(r, 0.04, check(), -250))
        val under = AutoBet.rules(ScanSettings(autoBetMinOdds = 120))
        assertNotNull(AutoBet.judge(under, 0.04, check(), -110))
        assertNotNull(AutoBet.judge(under, 0.04, check(), 119))
        assertNull(AutoBet.judge(under, 0.04, check(), 120))
    }

    private fun check() = com.tjshea.vigilant.data.cno.CnoBooks.Check(4, 0, 0.51, 4, 100, 0.05, com.tjshea.vigilant.data.cno.CnoBooks.Verdict.CONFIRMED)

    @Test
    fun `the feed hides a price outside either odds limit`() {
        val s = ScanSettings(maxOdds = 150, minOdds = -200)
        fun cost(american: Int) = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(american)
        assertTrue(s.withinOdds(cost(-200)))
        assertTrue(s.withinOdds(cost(-110)))
        assertTrue(s.withinOdds(cost(150)))
        assertFalse(s.withinOdds(cost(-250)))
        assertFalse(s.withinOdds(cost(160)))
        // Underdogs only: +100 and longer.
        val dogs = ScanSettings(maxOdds = 300, minOdds = 100)
        assertFalse(dogs.withinOdds(cost(-110)))
        assertTrue(dogs.withinOdds(cost(100)))
        assertTrue(dogs.withinOdds(cost(250)))
        // None by default.
        assertTrue(ScanSettings().withinMinOdds(0.95))
    }

    @Test
    fun `a bid priced over the shortest odds' price is skipped, one at or under it is posted`() {
        val rules = MakerRules.of(ScanSettings(makerMinOdds = -200)).copy(minPrice = 0.01, maxPrice = 0.99)
        // −200 pays $0.50 on $1: a bid at 66.7¢ is the shortest allowed.
        assertEquals(1.0 / 1.5, MakerRules.priceAtShortest(-200), 1e-12)
        assertNotNull(MakerQuote.outsideWindow(0.70, rules))
        assertNull(MakerQuote.outsideWindow(0.66, rules))
        // Underdogs only: nothing over 50¢.
        val dogs = MakerRules.of(ScanSettings(makerMinOdds = 100)).copy(minPrice = 0.01, maxPrice = 0.99)
        assertNotNull(MakerQuote.outsideWindow(0.52, dogs))
        assertNull(MakerQuote.outsideWindow(0.50, dogs))
        // No limit: the price window alone decides.
        assertNull(MakerQuote.outsideWindow(0.90, MakerRules.of(ScanSettings()).copy(minPrice = 0.01, maxPrice = 0.99)))
    }
}
