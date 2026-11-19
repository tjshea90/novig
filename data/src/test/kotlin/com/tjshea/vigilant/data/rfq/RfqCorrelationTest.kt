package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.asin

class RfqCorrelationTest {
    @Test fun theNormalCdfAndItsInverseAreAccurate() {
        assertEquals(0.5, Gauss.cdf(0.0), 1e-15)
        assertEquals(0.8413447460685429, Gauss.cdf(1.0), 1e-12)
        assertEquals(0.9750021048517795, Gauss.cdf(1.96), 1e-12)
        assertEquals(0.0013498980316301, Gauss.cdf(-3.0), 1e-12)
        assertEquals(1.0, Gauss.cdf(40.0), 0.0)
        for (p in listOf(0.001, 0.02, 0.1, 0.3, 0.5, 0.77, 0.95, 0.999)) assertEquals(p, Gauss.cdf(Gauss.inv(p)), 1e-12)
        assertEquals(1.959963984540054, Gauss.inv(0.975), 1e-9)
    }

    @Test fun theBivariateCdfMatchesTheClosedFormAtZeroAndAMonteCarloElsewhere() {
        // at (0, 0): 1/4 + asin(rho) / (2 pi)
        for (rho in listOf(-0.9, -0.5, -0.1, 0.0, 0.3, 0.707, 0.95)) assertEquals(0.25 + asin(rho) / (2 * PI), Gauss.bivariate(0.0, 0.0, rho), 1e-9)
        // independent at any point
        assertEquals(Gauss.cdf(0.4) * Gauss.cdf(-1.1), Gauss.bivariate(0.4, -1.1, 0.0), 1e-12)
        // a Monte Carlo at an asymmetric point
        val rnd = Random(11); val n = 3_000_000; var hit = 0
        val rho = 0.6; val s = Math.sqrt(1 - rho * rho)
        repeat(n) { val x = rnd.nextGaussian(); val y = rho * x + s * rnd.nextGaussian(); if (x <= 0.5 && y <= -0.3) hit++ }
        assertEquals(hit.toDouble() / n, Gauss.bivariate(0.5, -0.3, rho), 1.5e-3)
        // limits: a certain leg leaves the other unchanged
        assertEquals(Gauss.cdf(0.2), Gauss.bivariate(9.0, 0.2, 0.7), 1e-9)
    }

    @Test fun theTrivariateCdfMatchesTheOrthantFormula() {
        val r12 = 0.5; val r13 = -0.2; val r23 = 0.3
        val closed = 0.125 + (asin(r12) + asin(r13) + asin(r23)) / (4 * PI)
        assertEquals(closed, Gauss.trivariate(0.0, 0.0, 0.0, r12, r13, r23), 1e-8)
        assertEquals(0.125, Gauss.trivariate(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), 1e-9)
    }

    private fun leg(kind: LineKind, side: Side, p: Double, away: Boolean = false, period: Int = 0, homeLine: Double? = null) = GameLeg(kind, side, away, period, p, homeLine)

    @Test fun aSideAndTheTotalAreNearlyIndependentButATeamTotalAndTheTotalAreNot() {
        val nfl = SameGame.Sport(1.0, 0.0)                                                          // an NFL-like game with no favourite tilt
        val spread = leg(LineKind.SPREAD, Side.HOME, 0.5, homeLine = -3.5); val over = leg(LineKind.TOTAL, Side.OVER, 0.5)
        assertEquals(0.0, SameGame.correlation(spread, over, nfl, true)!!, 1e-12)
        assertEquals(1.0, SameGame.lift(listOf(spread, over), nfl)!!, 1e-9)
        // the home team's total with the game total: corr 1/sqrt(2) when the margin and total are equally spread
        val tt = leg(LineKind.TEAM_TOTAL, Side.OVER, 0.5)
        assertEquals(1.0 / Math.sqrt(2.0), SameGame.correlation(tt, over, nfl)!!, 1e-12)
        // two such 50% legs: 1/4 + asin(0.7071) / (2 pi) = 3/8, a lift of 1.5 (Novig's market charged +47% on a side and a team total)
        assertEquals(1.5, SameGame.lift(listOf(tt, over), nfl)!!, 1e-6)
        // the same team's total and its spread: also 0.707; the OTHER team's total and that spread: -0.707
        assertEquals(1.0 / Math.sqrt(2.0), SameGame.correlation(tt, spread, nfl)!!, 1e-12)
        assertEquals(-1.0 / Math.sqrt(2.0), SameGame.correlation(leg(LineKind.TEAM_TOTAL, Side.OVER, 0.5, away = true), spread, nfl)!!, 1e-12)
        // a home spread with the away moneyline contradicts (rho -1 clamps), a home moneyline with the home spread is the same event (rho +1 clamps): neither is a normal pair, Novig refuses both
        assertTrue(SameGame.correlation(spread, leg(LineKind.MONEYLINE, Side.HOME, 0.5), nfl)!! > 0.98)
    }

    @Test fun collegeFootballFavouritesCoverWithTheOverMoreOftenAndOnlyThere() {
        val cfb = SameGame.SPORTS.getValue("NCAAF"); val nba = SameGame.SPORTS.getValue("NBA")
        // home favoured by 7: the favourite covering and the Over go together (1.12 of the independent rate on 858 games, 1.117 measured)
        val homeCover = leg(LineKind.SPREAD, Side.HOME, 0.5, homeLine = -7.0); val over = leg(LineKind.TOTAL, Side.OVER, 0.5)
        val lift = SameGame.lift(listOf(homeCover, over), cfb)!!
        assertTrue("favourite + over lifts a college football price about 11% (was $lift)", lift in 1.09..1.14)
        // the underdog covering with the Over is the mirror: below independent
        assertTrue(SameGame.lift(listOf(leg(LineKind.SPREAD, Side.AWAY, 0.5, homeLine = -7.0), over), cfb)!! in 0.86..0.91)
        // the AWAY team favoured: the sign follows the favourite, not the home team
        assertEquals(lift, SameGame.lift(listOf(leg(LineKind.SPREAD, Side.AWAY, 0.5, homeLine = 7.0), over), cfb)!!, 1e-9)
        // a moneyline names the favourite by its probability
        assertEquals(true, SameGame.homeFavorite(listOf(leg(LineKind.MONEYLINE, Side.HOME, 0.7))))
        assertEquals(false, SameGame.homeFavorite(listOf(leg(LineKind.MONEYLINE, Side.AWAY, 0.7))))
        assertNull(SameGame.homeFavorite(listOf(leg(LineKind.TOTAL, Side.OVER, 0.5))))
        // the NBA has no such tilt
        assertEquals(1.0, SameGame.lift(listOf(homeCover, over), nba)!!, 1e-9)
    }

    @Test fun basketballsTotalIsWiderThanItsMarginSoATeamTotalFollowsItMore() {
        val nba = SameGame.SPORTS.getValue("NBA"); val nfl = SameGame.SPORTS.getValue("NFL")
        val tt = leg(LineKind.TEAM_TOTAL, Side.OVER, 0.5); val over = leg(LineKind.TOTAL, Side.OVER, 0.5); val side = leg(LineKind.SPREAD, Side.HOME, 0.5)
        assertTrue(SameGame.correlation(tt, over, nba)!! > SameGame.correlation(tt, over, nfl)!!)
        assertTrue(SameGame.correlation(tt, side, nba)!! < SameGame.correlation(tt, side, nfl)!!)
    }

    @Test fun aFirstHalfLineMovesWithTheFullGameLine() {
        val nba = SameGame.SPORTS.getValue("NBA")
        val h1 = leg(LineKind.TOTAL, Side.OVER, 0.5, period = 1); val full = leg(LineKind.TOTAL, Side.OVER, 0.5)
        assertEquals(Math.sqrt(0.5), SameGame.correlation(h1, full, nba)!!, 1e-12)          // a Brownian half: corr sqrt(1/2)
        assertTrue(SameGame.lift(listOf(h1, full), nba)!! > 1.3)
        // the first-half spread with the full game total: independent when the margin and total are
        assertEquals(0.0, SameGame.correlation(leg(LineKind.SPREAD, Side.HOME, 0.5, period = 1), full, nba)!!, 1e-12)
    }

    @Test fun threeLegsOfOneGameUseTheTrivariateAndAPropIsNotModelled() {
        val nfl = SameGame.SPORTS.getValue("NFL")
        val legs = listOf(leg(LineKind.SPREAD, Side.HOME, 0.55), leg(LineKind.TOTAL, Side.OVER, 0.48), leg(LineKind.TEAM_TOTAL, Side.OVER, 0.5, away = true))
        val j = SameGame.joint(legs, nfl)!!
        val ind = 0.55 * 0.48 * 0.5
        assertTrue("the away team's total over is helped by the Over and hurt by the home side covering: a lift near 1 either way, never wild (was ${j / ind})", j / ind in 0.8..1.4)
        assertNull(SameGame.joint(legs + legs.first(), nfl))                                        // four legs: not priced
        assertNull(SameGame.joint(listOf(legs[0], leg(LineKind.PLAYER_PROP, Side.OVER, 0.5)), nfl))
        assertNotNull(SameGame.joint(listOf(legs[0], legs[1]), nfl))
    }

    @Test fun theJointNeverExceedsItsSmallestLegOrFallsBelowTheFrechetBound() {
        val nba = SameGame.SPORTS.getValue("NBA")
        for (pa in listOf(0.1, 0.3, 0.5, 0.8)) for (pb in listOf(0.1, 0.4, 0.5, 0.9)) {
            val j = SameGame.joint(listOf(leg(LineKind.TEAM_TOTAL, Side.OVER, pa), leg(LineKind.TOTAL, Side.OVER, pb)), nba)!!
            assertTrue(j <= minOf(pa, pb) + 1e-9); assertTrue(j >= maxOf(0.0, pa + pb - 1.0) - 1e-9)
        }
    }
}
