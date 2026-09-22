package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeesTest {

    @Test
    fun `pregame straight trades are free for a taker`() {
        val fee = Fees.estimate(NovigQuote(price = 0.5, isMaker = false, context = TradeContext.PREGAME_STRAIGHT))
        assertEquals(FeeResult.Known(0.0), fee)
    }

    @Test
    fun `maker side is always free regardless of context`() {
        val fee = Fees.estimate(NovigQuote(price = 0.5, isMaker = true, context = TradeContext.LIVE_STRAIGHT))
        assertEquals(FeeResult.Known(0.0), fee)
    }

    @Test
    fun `live straight taker fee matches the documented formula`() {
        val fee = Fees.estimate(NovigQuote(price = 0.5, isMaker = false, context = TradeContext.LIVE_STRAIGHT))
        check(fee is FeeResult.Known)
        // 0.03 * 0.5 * 0.5 = 0.0075
        assertEquals(0.0075, fee.amountPerDollarStaked, 1e-9)
    }

    @Test
    fun `live straight taker fee is smaller at extreme prices than at even money`() {
        val evenMoney = Fees.liveStraightTakerFee(0.5)
        val extreme = Fees.liveStraightTakerFee(0.1)
        assertTrue(extreme < evenMoney)
    }

    @Test
    fun `parlay taker fee matches the documented formula`() {
        val fee = Fees.estimate(NovigQuote(price = 0.5, isMaker = false, context = TradeContext.PARLAY))
        check(fee is FeeResult.Known)
        // 0.10 * 0.5 * 0.5 = 0.025
        assertEquals(0.025, fee.amountPerDollarStaked, 1e-9)
    }

    @Test
    fun `parlay taker fee is more expensive than live straight taker fee at the same price`() {
        val parlay = Fees.parlayTakerFee(0.5)
        val liveStraight = Fees.liveStraightTakerFee(0.5)
        assertTrue(parlay > liveStraight)
    }

    @Test
    fun `parlay maker side is still free`() {
        val fee = Fees.estimate(NovigQuote(price = 0.5, isMaker = true, context = TradeContext.PARLAY))
        assertEquals(FeeResult.Known(0.0), fee)
    }
}
