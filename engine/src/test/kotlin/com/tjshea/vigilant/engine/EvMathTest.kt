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

    @Test
    fun `prices snap down to Novig's grid`() {
        assertEquals(0.003, PriceGrid.floor(0.0039)!!, 1e-12)
        assertEquals(0.050, PriceGrid.floor(0.0549)!!, 1e-12) // no 0.051-0.054 on the grid
        assertEquals(0.055, PriceGrid.floor(0.0551)!!, 1e-12)
        assertEquals(0.395, PriceGrid.floor(0.3999)!!, 1e-12)
        assertEquals(0.400, PriceGrid.floor(0.4)!!, 1e-12)
        assertEquals(0.945, PriceGrid.floor(0.9499)!!, 1e-12)
        assertEquals(0.951, PriceGrid.floor(0.9515)!!, 1e-12)
        assertEquals(null, PriceGrid.floor(0.0005))
    }

    @Test
    fun `a maker bid is the highest grid price that still clears the EV target`() {
        // Fair 45%: a 2% target allows 44.12¢, so the bid is 44¢ (grid) for +2.27% EV.
        val bid = EvMath.makerBid(0.45, 0.02)!!
        assertEquals(0.44, bid.price, 1e-12)
        assertEquals(0.45 / 0.44 - 1, bid.evPercent, 1e-12)
        assertTrue(bid.evPercent >= 0.02)
        // Longshot end of the grid keeps its 0.001 steps.
        assertEquals(0.039, EvMath.makerBid(0.041, 0.03)!!.price, 1e-12)
        assertEquals(null, EvMath.makerBid(1.0, 0.02))
    }

    /**
     * Tj, 2026-10-01: "make sure it is accurately calculating Kelly values when I input my total bankroll and select kelly. The math must be
     * accurate. I think Kelly values change depending on the odds of the bet." Worked by hand: full Kelly = (b·p − q) / b with b the net
     * odds of the price paid; the stake is bankroll × fraction × that.
     */
    @Test
    fun `kelly stakes worked by hand change with the odds, the edge and the fee`() {
        // Even money (+100, price 0.50), fair 55%: b = 1, f = (1×0.55 − 0.45)/1 = 10%; ¼ Kelly of $1,000 = $25.00.
        val even = EvQuote(fairProbability = 0.55, price = 0.50, fee = 0.0)
        assertEquals(0.10, even.kellyFraction, 1e-12)
        assertEquals(25.00, EvMath.suggestedStake(even, 1_000.0, 0.25, null), 1e-9)
        // The same 5-point edge at +233 (price 0.30), fair 35%: b = 0.7/0.3 = 2.333…, f = (2.333×0.35 − 0.65)/2.333 = 7.142857%; ¼ Kelly = $17.86.
        val long = EvQuote(0.35, 0.30, 0.0)
        assertEquals(0.05 / 0.70, long.kellyFraction, 1e-12)
        assertEquals(17.857142857, EvMath.suggestedStake(long, 1_000.0, 0.25, null), 1e-6)
        // A favorite at −300 (price 0.75), fair 80%: b = 0.333…, f = (0.333×0.8 − 0.2)/0.333 = 20%; ¼ Kelly = $50.00.
        val fav = EvQuote(0.80, 0.75, 0.0)
        assertEquals(0.20, fav.kellyFraction, 1e-12)
        assertEquals(50.00, EvMath.suggestedStake(fav, 1_000.0, 0.25, null), 1e-9)
        // A 1¢ fee (a live game) makes the price 0.51: b = 0.49/0.51, f = (0.55 − 0.51)/0.49 = 8.163%; ¼ Kelly = $20.41.
        val fee = EvQuote(0.55, 0.50, 0.01)
        assertEquals(0.04 / 0.49, fee.kellyFraction, 1e-12)
        assertEquals(20.408163265, EvMath.suggestedStake(fee, 1_000.0, 0.25, null), 1e-6)
        // Half the bankroll, half the stake; full Kelly 4× the quarter; never more than Novig has for sale at +EV.
        assertEquals(12.50, EvMath.suggestedStake(even, 500.0, 0.25, null), 1e-9)
        assertEquals(100.00, EvMath.suggestedStake(even, 1_000.0, 1.0, null), 1e-9)
        assertEquals(8.40, EvMath.suggestedStake(even, 1_000.0, 0.25, maxFillable = 8.40), 1e-9)
        // No edge (fair 50% at 0.50), or a negative one: nothing.
        assertEquals(0.0, EvMath.suggestedStake(EvQuote(0.50, 0.50, 0.0), 1_000.0, 0.25, null), 0.0)
        assertEquals(0.0, EvMath.suggestedStake(EvQuote(0.45, 0.50, 0.0), 1_000.0, 0.25, null), 0.0)
    }
}
