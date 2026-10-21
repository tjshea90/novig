package com.tjshea.vigilant.engine

import com.tjshea.vigilant.engine.TennisModel.Config
import com.tjshea.vigilant.engine.TennisModel.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CH47f: the point-by-point tennis model, checked against known values and the symmetries any correct model has. */
class TennisModelTest {
    @Test fun `a game from love-all is the known 0·7357 at a point probability of 0·6 and one half at one half`() {
        assertEquals(0.7357, TennisModel.gameWin(0.6), 1e-3)
        assertEquals(0.5, TennisModel.gameWin(0.5), 1e-9)
        assertEquals(0.5, TennisModel.gameFrom(0.5, 3, 3), 1e-9)   // deuce at an even point
        assertEquals(1.0, TennisModel.gameFrom(0.6, 4, 2), 1e-12)
        // The recursion from love-all equals the closed form.
        assertEquals(TennisModel.gameWin(0.63), TennisModel.gameFrom(0.63, 0, 0), 1e-9)
    }

    @Test fun `a tiebreak and a set between equal players are even, and a stronger server wins more of both`() {
        assertEquals(0.5, TennisModel.tiebreakWin(0.65, 0.65, aFirst = true), 1e-6)
        assertEquals(0.5, TennisModel.setWin(0.65, 0.65, 0, 0, aServes = true), 1e-6)
        assertTrue(TennisModel.setWin(0.68, 0.62, 0, 0, true) > 0.5)
        assertTrue(TennisModel.tiebreakWin(0.68, 0.62, true) > 0.5)
        // 6-0 up in a set is nearly won, 5-5 is a toss-up for equals.
        assertTrue(TennisModel.setWin(0.65, 0.65, 5, 0, true) > 0.99)
        assertEquals(0.5, TennisModel.setWin(0.65, 0.65, 5, 5, true), 0.03)
    }

    @Test fun `a match is even at the start between equal players and settled by a won deciding set`() {
        assertEquals(0.5, TennisModel.matchWin(0.65, 0.65, State()), 1e-6)
        assertEquals(1.0, TennisModel.matchWin(0.65, 0.65, State(setsA = 2)), 1e-12)
        assertEquals(0.0, TennisModel.matchWin(0.65, 0.65, State(setsB = 2)), 1e-12)
        // A set up is worth about three quarters between equals in best of three; two sets up of five is nearly won.
        assertEquals(0.75, TennisModel.matchWin(0.65, 0.65, State(setsA = 1)), 0.03)
        assertTrue(TennisModel.matchWin(0.65, 0.65, State(setsA = 2), Config(bestOf = 5)) > 0.8)
    }

    @Test fun `the live probability moves the right way with games, points and the serve`() {
        val base = State(setsA = 0, setsB = 0, gamesA = 3, gamesB = 3, aServes = true)
        val a = TennisModel.matchWin(0.65, 0.65, base)
        assertTrue(TennisModel.matchWin(0.65, 0.65, base.copy(gamesA = 4)) > a)
        assertTrue(TennisModel.matchWin(0.65, 0.65, base.copy(gamesB = 4)) < a)
        assertTrue(TennisModel.matchWin(0.65, 0.65, base.copy(pointsA = 3, pointsB = 0)) > a)
        assertTrue(TennisModel.matchWin(0.65, 0.65, base.copy(pointsA = 0, pointsB = 3)) < a)
        // Mirror symmetry: swapping the players swaps the answer.
        val m = TennisModel.matchWin(0.68, 0.61, State(setsA = 1, gamesA = 2, gamesB = 4, pointsA = 1, pointsB = 2, aServes = false))
        val mirror = TennisModel.matchWin(0.61, 0.68, State(setsB = 1, gamesB = 2, gamesA = 4, pointsB = 1, pointsA = 2, aServes = true))
        assertEquals(1.0, m + mirror, 0.02)   // the later-set serve order is averaged, so the mirror is exact only to a fraction of a point
    }

    @Test fun `calibrate finds serve strengths that reproduce the pre-match probability`() {
        for (target in listOf(0.2, 0.35, 0.5, 0.65, 0.8, 0.9)) {
            val (pA, pB) = TennisModel.calibrate(target, 0.62)
            assertEquals(target, TennisModel.matchWin(pA, pB, State()), 0.005)
            assertEquals(0.62, (pA + pB) / 2, 1e-9)
        }
    }
}
