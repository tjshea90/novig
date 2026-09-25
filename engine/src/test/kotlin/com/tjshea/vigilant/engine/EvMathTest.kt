package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvMathTest {

    @Test
    fun `ev percent is fair over cost minus one`() {
        val q = EvMath.quote(fairProbability = 0.42, price = 0.385, fee = MarketFee.GAME, eventLive = false)
        assertEquals(0.0, q.fee, 0.0)
        assertEquals(0.42 / 0.385 - 1.0, q.evPercent, 1e-12)
        assertEquals(0.035, q.evPerDollarPayout, 1e-12)
    }

    @Test
    fun `kelly for a binary contract is (p - c) over (1 - c)`() {
        val q = EvMath.quote(0.42, 0.385, MarketFee.GAME, false)
        assertEquals((0.42 - 0.385) / (1 - 0.385), q.kellyFraction, 1e-12)
        // Cross-check against the textbook b-odds form: f = (bp - q) / b with b = 1/c - 1.
        val b = 1 / 0.385 - 1
        assertEquals((b * 0.42 - 0.58) / b, q.kellyFraction, 1e-12)
    }

    @Test
    fun `a negative edge has zero kelly, never a negative stake`() {
        val q = EvMath.quote(0.30, 0.385, MarketFee.GAME, false)
        assertTrue(q.evPercent < 0)
        assertEquals(0.0, q.kellyFraction, 0.0)
    }

    @Test
    fun `a pregame edge can vanish once the live taker fee applies`() {
        val pre = EvMath.quote(0.505, 0.50, MarketFee.GAME, eventLive = false)
        val live = EvMath.quote(0.505, 0.50, MarketFee.GAME, eventLive = true)
        assertTrue(pre.evPercent > 0)
        assertTrue(live.evPercent < 0) // 0.03 * 0.25 = 0.0075 fee > 0.005 edge
    }

    @Test
    fun `positive depth walks the ladder and stops at the first level below the threshold`() {
        val levels = listOf(
            TakeLevel(0.40, 1_000),   // +5% at fair 0.42
            TakeLevel(0.41, 2_000),   // +2.4%
            TakeLevel(0.43, 50_000),  // negative
        )
        val d = EvMath.positiveDepth(levels, 0.42, MarketFee.GAME, eventLive = false, minEvPercent = 0.0)
        assertEquals(3_000L, d.contracts)
        assertEquals(1_000 * 0.40 * 0.01 + 2_000 * 0.41 * 0.01, d.dollarCost, 1e-9)
        assertEquals(1_000 * 0.02 * 0.01 + 2_000 * 0.01 * 0.01, d.dollarEv, 1e-9)
        assertEquals(0.41, d.worstPrice!!, 0.0)
    }

    @Test
    fun `suggested stake is fractional kelly capped at fillable size`() {
        val q = EvMath.quote(0.42, 0.385, MarketFee.GAME, false)
        val uncapped = EvMath.suggestedStake(q, bankroll = 1_000.0, kellyMultiplier = 0.25, maxFillable = null)
        assertEquals(1_000 * 0.25 * q.kellyFraction, uncapped, 1e-9)
        assertEquals(5.0, EvMath.suggestedStake(q, 1_000.0, 0.25, maxFillable = 5.0), 0.0)
    }
}
