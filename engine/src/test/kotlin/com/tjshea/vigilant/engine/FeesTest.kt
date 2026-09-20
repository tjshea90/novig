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
        // 0.03 * 0.5 * 0.5 = 0.0075
        assertEquals(FeeResult.Known(0.0075), fee)
    }

    @Test
    fun `live straight taker fee is smaller at extreme prices than at even money`() {
        val evenMoney = Fees.liveStraightTakerFee(0.5)
        val extreme = Fees.liveStraightTakerFee(0.1)
        assertTrue(extreme < evenMoney)
    }

    @Test
    fun `parlay fee is explicitly unknown, never silently zero`() {
        val fee = Fees.estimate(NovigQuote(price = 0.5, isMaker = false, context = TradeContext.PARLAY))
        assertTrue(fee is FeeResult.Unknown)
    }
}
