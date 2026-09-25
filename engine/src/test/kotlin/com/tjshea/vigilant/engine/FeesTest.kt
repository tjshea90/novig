package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FeesTest {

    @Test
    fun `game markets charge no taker fee pregame`() {
        assertEquals(0.0, Fees.takerFee(0.5, MarketFee.GAME, eventLive = false), 0.0)
    }

    @Test
    fun `game markets charge c P (1-P) once live - matches Novig's worked example`() {
        // Novig docs: 10,000 contracts @ 0.50 on the game schedule = $0.75.
        val perDollar = Fees.takerFee(0.5, MarketFee.GAME, eventLive = true)
        assertEquals(0.75, perDollar * 10_000 * EvMath.CONTRACT_PAYOUT_DOLLARS, 1e-9)
        // 10,000 @ 0.30 = $0.63.
        assertEquals(0.63, Fees.takerFee(0.3, MarketFee.GAME, true) * 10_000 * 0.01, 1e-9)
    }

    @Test
    fun `NFL MLB NCAAF futures charge pregame too - the case the old Fees object missed`() {
        // Novig docs: 10,000 @ 0.50 futures = $1.50, charged ALWAYS.
        val perDollar = Fees.takerFee(0.5, MarketFee.FUTURES, eventLive = false)
        assertEquals(1.5, perDollar * 10_000 * 0.01, 1e-9)
    }
}
