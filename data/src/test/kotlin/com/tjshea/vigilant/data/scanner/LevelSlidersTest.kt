package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 1-100 sliders (Tj, 2026-10-11): every step moves each rule one way, every level stays positive-EV, and a level matches itself until a rule is edited. */
class LevelSlidersTest {
    private val levels = 1..100

    @Test fun `bid rules only loosen as the level rises`() {
        val s = ScanSettings()
        val a = levels.map { BidSlider.apply(s, it) }
        for ((x, y) in a.zipWithNext()) {
            assertTrue(y.makerMargin <= x.makerMargin + 1e-12)
            assertTrue(y.makerMinBooks <= x.makerMinBooks)
            assertTrue(y.trapBidHours >= x.trapBidHours)
            assertTrue(y.makerTtlMinutes >= x.makerTtlMinutes)
            assertTrue(y.makerStopMinutes <= x.makerStopMinutes)
            assertTrue(y.makerKinds.size >= x.makerKinds.size)
            assertTrue((y.makerMaxOdds == 0) || (x.makerMaxOdds != 0 && y.makerMaxOdds >= x.makerMaxOdds))
            assertTrue(!(x.makerBothSides && !y.makerBothSides))
            assertTrue(!(x.makerObscureFill && !y.makerObscureFill))
        }
    }

    @Test fun `every bid level is positive EV, sharp-vetoed, and reaches 12 hours or more`() {
        val s = ScanSettings()
        for (l in levels) {
            val n = BidSlider.apply(s, l)
            assertTrue(n.makerMargin >= BidSlider.MIN_MARGIN - 1e-12)
            assertTrue(n.makerMinBooks >= BidSlider.MIN_AGREEING && n.makerSharpVeto && n.makerAnchorSharp && n.makerGuard)
            assertTrue(n.trapBidHours >= 12)
            assertTrue(n.scanWindowHours >= n.trapBidHours && n.bookPropWindowHours >= n.trapBidHours)
        }
        assertEquals(12, BidSlider.apply(s, 1).trapBidHours)
        // Never past 24 h: bids posted 24+ h out filled 2 times in 2,698 in Tj's 2026-10-11 export.
        assertEquals(24, BidSlider.apply(s, 100).trapBidHours)
        assertTrue(levels.all { BidSlider.apply(s, it).trapBidHours in 12..24 })
        assertEquals(0.08, BidSlider.apply(s, 1).makerMargin, 1e-9)
    }

    @Test fun `auto-bet rules only loosen as the level rises and never go under 1_5 percent edge`() {
        val a = levels.map { BetSlider.apply(ScanSettings(), it) }
        for ((x, y) in a.zipWithNext()) {
            assertTrue(y.autoBetMinEv <= x.autoBetMinEv + 1e-12)
            assertTrue(y.sharpVetoMinEv <= x.sharpVetoMinEv + 1e-12)
            assertTrue(y.autoBetBooks <= x.autoBetBooks)
            assertTrue(y.autoBetTwoSided <= x.autoBetTwoSided)
            assertTrue(y.trapEarlyHours >= x.trapEarlyHours)
            assertTrue(y.autoBetKinds.size >= x.autoBetKinds.size)
            assertTrue((y.autoBetMaxOdds == 0) || (x.autoBetMaxOdds != 0 && y.autoBetMaxOdds >= x.autoBetMaxOdds))
            assertTrue((y.autoBetMinOdds == 0) || (x.autoBetMinOdds != 0 && y.autoBetMinOdds <= x.autoBetMinOdds))
            assertTrue(y.autoBetFavouriteExtraEv <= x.autoBetFavouriteExtraEv + 1e-12)
        }
        a.forEach { assertTrue(it.autoBetMinEv >= BetSlider.MIN_EV - 1e-12 && it.autoBetBooks >= 2 && it.sharpVetoMinEv > 0.0) }
    }

    @Test fun `no level bets or bids a longshot - bets stop at +150, bids at +200`() {
        for (l in levels) {
            assertTrue(BetSlider.apply(ScanSettings(), l).autoBetMaxOdds in 1..150)
            assertTrue(BidSlider.apply(ScanSettings(), l).makerMaxOdds in 1..200)
        }
    }

    @Test fun `the slider never changes the star minimum`() {
        for (stars in listOf(0.0, 3.5)) {
            val s = ScanSettings(vigGuards = ScanGuards(autoBetMinStars = stars))
            for (l in listOf(1, 50, 100)) assertEquals(stars, BetSlider.apply(s, l).autoBetMinStars, 0.0)
        }
    }

    @Test fun `a level matches itself, leaves the money alone, and reads custom after an edit`() {
        val base = ScanSettings(bankroll = 321.0, makerMaxDollars = 77.0, apiMaxStake = 9.0, makerMaxStake = 4.0, leagues = setOf("NBA"))
        for (l in listOf(1, 37, 100)) {
            val bid = BidSlider.apply(base, l)
            assertTrue(BidSlider.matches(bid, l))
            assertFalse(BidSlider.matches(bid.copy(makerMinBooks = bid.makerMinBooks + 1), l))
            assertEquals(base.bankroll, bid.bankroll, 0.0); assertEquals(77.0, bid.makerMaxDollars, 0.0); assertEquals(4.0, bid.makerMaxStake, 0.0)
            assertEquals(base.leagues, bid.leagues); assertEquals(base.autoBet, bid.autoBet); assertEquals(base.maker, bid.maker)
            val bet = BetSlider.apply(base, l)
            assertTrue(BetSlider.matches(bet, l))
            assertFalse(BetSlider.matches(bet.copy(autoBetBooks = 2 + (bet.autoBetBooks % 4)), l))
            assertEquals(base.autoBetStake, bet.autoBetStake); assertEquals(9.0, bet.apiMaxStake, 0.0)
        }
    }

    @Test fun `a short scan window is widened to the bids' horizon, a longer one is kept`() {
        val short = ScanSettings(startsWithinHours = 6, daysAhead = 1, bookPropHours = 6)
        val n = BidSlider.apply(short, 100)
        assertEquals(24, n.startsWithinHours); assertEquals(1, n.daysAhead); assertEquals(24, n.bookPropHours)
        val long = ScanSettings(startsWithinHours = 0, daysAhead = 7, bookPropHours = 72)
        assertEquals(long.daysAhead, BidSlider.apply(long, 100).daysAhead); assertEquals(72, BidSlider.apply(long, 100).bookPropHours)
    }

    @Test fun `more smaller bids keeps every EV rule of the level and only adds volume`() {
        val base = ScanSettings(bankroll = 185.0, apiMaxStake = 7.0, makerMaxDollars = 100.0)
        for (l in levels) {
            val plain = BidSlider.apply(base, l)
            val many = BidSlider.withMany(base, true, l)
            assertTrue(BidSlider.matches(many, l))
            // the rules that make a bid +EV are the level's own, unchanged
            assertEquals(plain.makerMargin, many.makerMargin, 0.0)
            assertEquals(plain.makerMinBooks, many.makerMinBooks)
            assertEquals(plain.makerSharpVeto, many.makerSharpVeto)
            assertEquals(plain.makerMaxOdds, many.makerMaxOdds)
            assertEquals(plain.trapBidHours, many.trapBidHours)
            assertTrue(many.makerMargin >= BidSlider.MIN_MARGIN - 1e-12)
            assertTrue(many.makerSharpVeto && many.makerAnchorSharp)
            // volume: 1/16 Kelly, hundreds of bids, every kind; money untouched
            assertEquals(AutoBetStake.SIXTEENTH_KELLY, many.makerStakeMode)
            assertTrue(many.makerMaxBids >= 100)
            assertEquals(BetKind.entries.toSet(), many.makerKinds)
            assertEquals(base.makerMaxDollars, many.makerMaxDollars, 0.0)
            assertEquals(base.bankroll, many.bankroll, 0.0)
            assertEquals(base.apiMaxStake, many.apiMaxStake, 0.0)
            assertTrue(many.makerMaxOdds in 1..BidSlider.LONGEST_ODDS)
        }
    }

    @Test fun `switching more smaller bids off puts the stake and count back`() {
        val on = BidSlider.withMany(ScanSettings(), true, 50)
        val off = BidSlider.withMany(on, false, 50)
        assertFalse(off.vigGuards.bidMany)
        assertEquals(AutoBetStake.QUARTER_KELLY, off.makerStakeMode)
        assertEquals(20, off.makerMaxBids)
        assertTrue(BidSlider.matches(off, 50))
    }
}
