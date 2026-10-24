package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.tjshea.vigilant.data.scanner.BetRating
import com.tjshea.vigilant.data.scanner.ProfitProfile
import org.junit.Test

/**
 * Tj, 2026-10-11: "When I select auto bet with a minimum star rating, make the app ignore all the other auto bet filters that interfere ... auto bet all of the scanned bets with at least that
 * rating, regardless of any other settings. But if I turn off the minimum star rating filter, I want it to return back to enforcing all of the other auto bet settings."
 * With a minimum on, [AutoBet.judge], [AutoBet.judgeScan] and [AutoBet.judgeVigilant] apply none of the selection filters (the stars check itself is [AutoBet.starsReason], which the lanes call next).
 */
class AutoBetStarsTest {
    /** Every selection filter set so that an ordinary +4% bet at +110 on a prop fails it. */
    private val strict = ScanSettings(
        autoBetMinEv = 0.10, autoBetFavouriteExtraEv = 0.05, autoBetBooks = 6, autoBetTwoSided = 6, autoBetAllAgree = true, autoBetMaxOdds = 100, autoBetMinOdds = 150,
        autoBetKinds = setOf(BetKind.MONEYLINE), autoBetStake = AutoBetStake.ONE_DOLLAR,
    )
    private val off = AutoBet.rules(strict)
    private val on = AutoBet.rules(strict.copy(vigGuards = strict.vigGuards.copy(autoBetMinStars = 3.0)))

    private fun check(twoSided: Int = 2, agreeing: Int = 1, ev: Double? = 0.04) = CnoBooks.Check(twoSided, 0, 0.51, agreeing, 110, ev, CnoBooks.Verdict.CONFIRMED)

    @Test
    fun `a minimum rating turns the selection filters off, and no minimum turns them all back on`() {
        assertFalse(off.byStars)
        assertTrue(on.byStars)
        val prop = BetKind.PROP
        // CrazyNinjaOdds' lane: refused by the edge, odds, kinds, books and agreement in turn with no minimum, passed with one.
        assertNotNull(AutoBet.judge(off, 0.04, check(), 110, prop))
        assertNull(AutoBet.judge(on, 0.04, check(), 110, prop))
        // Vigilant's lane and tennis.
        assertNotNull(AutoBet.judgeVigilant(off, 0.04, 110, prop, twoSided = 2, agreeing = 1))
        assertNull(AutoBet.judgeVigilant(on, 0.04, 110, prop, twoSided = 2, agreeing = 1))
        assertNotNull(AutoBet.judgeScan(off, 0.04, 110, prop))
        assertNull(AutoBet.judgeScan(on, 0.04, 110, prop))
        // Each filter on its own, with the others loose: still refused with no minimum, ignored with one.
        val loose = ScanSettings(autoBetMinEv = 0.01, autoBetBooks = 2, autoBetTwoSided = 1)
        fun both(f: (ScanSettings) -> ScanSettings, ev: Double = 0.04, odds: Int = 110, kind: BetKind = BetKind.MONEYLINE, two: Int = 5, agree: Int = 5) {
            val s = f(loose)
            val r0 = AutoBet.rules(s)
            val r1 = AutoBet.rules(s.copy(vigGuards = s.vigGuards.copy(autoBetMinStars = 2.0)))
            assertNotNull("off: $s", AutoBet.judgeVigilant(r0, ev, odds, kind, two, agree))
            assertNull("on: $s", AutoBet.judgeVigilant(r1, ev, odds, kind, two, agree))
        }
        both({ it.copy(autoBetMinEv = 0.09) })
        both({ it.copy(autoBetMaxOdds = 100) })
        both({ it.copy(autoBetMinOdds = 150) })
        both({ it.copy(autoBetKinds = setOf(BetKind.SPREAD)) })
        both({ it.copy(autoBetTwoSided = 6) })
        both({ it.copy(autoBetBooks = 6) }, two = 6, agree = 5)
        both({ it.copy(autoBetAllAgree = true) }, two = 6, agree = 5)
        both({ it.copy(autoBetFavouriteExtraEv = 0.10) }, odds = -150)
        // The Over-prop bar is a selection filter too: it binds with no minimum and is ignored while a rating decides.
        val overs = loose.copy(vigGuards = loose.vigGuards.copy(autoBetOverPropExtraEv = 0.05))
        val o0 = AutoBet.rules(overs)
        val o1 = AutoBet.rules(overs.copy(vigGuards = overs.vigGuards.copy(autoBetMinStars = 2.0)))
        assertNotNull(AutoBet.judgeVigilant(o0, 0.04, 110, BetKind.PROP, 5, 5, overProp = true))
        assertNull(AutoBet.judgeVigilant(o1, 0.04, 110, BetKind.PROP, 5, 5, overProp = true))
        assertNull(AutoBet.judge(o1, 0.04, check(), 110, BetKind.PROP, overProp = true))
    }

    @Test
    fun `what the rating cannot see still holds - an edge too good to be true, a bet the books say is not plus EV`() {
        assertNotNull(AutoBet.tooGoodReason(0.16))
        assertNotNull("the cap applies in every lane", AutoBet.judge(on, 0.16, check(ev = 0.16), 110, BetKind.MONEYLINE))
        assertNotNull(AutoBet.judgeScan(on, 0.16, 110, BetKind.MONEYLINE))
        assertNotNull(AutoBet.judgeVigilant(on, 0.16, 110, BetKind.MONEYLINE, 5, 5))
        assertNull(AutoBet.tooGoodReason(0.15))
        assertNotNull(AutoBet.judge(on, 0.04, check(ev = 0.0), 110, BetKind.MONEYLINE))
        assertNotNull(AutoBet.judge(on, 0.04, check(ev = null), 110, BetKind.MONEYLINE))
    }

    @Test
    fun `the rating itself is still the bar, and the planner's floor drops to the noise floor only while it decides`() {
        assertNotNull(AutoBet.starsReason(on, 2.5))
        assertNull(AutoBet.starsReason(on, 3.0))
        assertNotNull("no rating yet waits", AutoBet.starsReason(on, null))
        assertNull(AutoBet.starsReason(off, 1.0))
        assertEquals(AutoBet.MIN_EV_FLOOR, on.readEv, 0.0)
        assertEquals(0.10, off.readEv, 0.0)
    }

    @Test
    fun `the dollar limits, pregame and the rest of the safety are not among the filters it switches off`() {
        // The kept safety is said in one place for the Auto-bet tab; the money limits are BetLimits', not the rules'.
        for (word in listOf("dollar limits", "pregame", "fresh", "+EV", "injured", "one bet a market")) assertTrue(word, AutoBet.starsKept.contains(word))
        assertEquals(10.0, AutoBet.rules(ScanSettings(autoBetMaxStake = 10.0, vigGuards = ScanSettings().vigGuards.copy(autoBetMinStars = 4.0))).maxStake, 0.0)
    }

    @Test
    fun `when Profit-proven only lowered the stars the reason names both numbers (Tj, 2026-10-13)`() {
        val min = com.tjshea.vigilant.data.scanner.BetRating.label(on.minStars)
        val same = AutoBet.starsReason(on, 2.5, shown = 2.5)!!
        assertEquals("its rating is under your $min-star minimum", same)
        val lowered = AutoBet.starsReason(on, 3.5.coerceAtMost(on.minStars - 0.5), shown = 4.5)!!
        assertTrue(lowered, lowered.contains("after Profit-proven only lowered the shown 4½★"))
        assertNull("passing is still passing", AutoBet.starsReason(on, 5.0, shown = 5.0))
    }

    @Test
    fun `history is inside the one rating, so the adjusted stars are the shown stars (ST1)`() {
        assertEquals(4.5, ProfitProfile.adjustedStars(true, 4.5, "NFL", "Player props", "A Over 1.5", 0.03, 1_000_000L, 0L)!!, 0.0)
        assertEquals(null, ProfitProfile.adjustedStars(true, null, "NFL", "Player props", "A Over 1.5", 0.03, 1_000_000L, 0L))
        val lost = ProfitProfile.typePointsOf("NFL", "Player props", "A Over 1.5", 1_000_000L, 0L)
        val won = ProfitProfile.typePointsOf("NFL", "Game totals", "Over 45.5", 1_000_000L, 0L)
        assertTrue("a losing type takes points off ($lost)", lost <= 0.0)
        assertTrue("points stay in range", lost >= -ProfitProfile.TYPE_POINTS && won <= ProfitProfile.TYPE_POINTS)
        // SR (2026-10-24): the type points count at BetRating.CURRENT.typeScale (half), so they move the points by exactly that and a typical bet's stars with them.
        val base = BetRating.Inputs(5, 5, 0.03, true, false, 0.01)
        val scale = BetRating.CURRENT.typeScale
        assertEquals(-15.0 * scale, BetRating.rate(base.copy(typePoints = -15.0)).points - BetRating.rate(base).points, 1e-9)
        assertTrue(BetRating.rate(base.copy(typePoints = -15.0)).stars < BetRating.rate(base).stars)
        assertTrue(BetRating.rate(base.copy(typePoints = 15.0)).stars >= BetRating.rate(base).stars)
    }

    @Test
    fun `two small books with no sharp book are at most two stars, and lead time moves the points (ST1)`() {
        val two = BetRating.Inputs(2, 2, 0.06, sharpAgrees = false, sharpAgainst = false, spread = 0.005)
        assertTrue(BetRating.rate(two).stars <= 2.0)
        assertTrue(BetRating.rate(two.copy(sharpAgrees = true)).stars > 2.0)
        assertTrue(BetRating.leadPoints(120) > BetRating.leadPoints(3000))
    }
}
