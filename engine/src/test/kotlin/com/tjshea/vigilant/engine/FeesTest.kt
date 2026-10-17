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
    fun `live NCAAF MLB and NHL pay 0 06 from Eastern midnight 2026-10-08, everything else and everything earlier 0 03`() {
        // Novig docs: "Beginning Thursday, October 8, 2026, a 0.06 live taker fee applies to every NCAAF, MLB and NHL market."
        val after = MarketFee.RAISED_FROM_MS + 1
        for (league in listOf("NCAAF", "MLB", "NHL", "mlb")) assertEquals(0.06, MarketFee.gameFor(league, after).coefficient, 0.0)
        for (league in listOf("NFL", "NBA", "WNBA", "NCAAB", "ATP", "UFC", null)) assertEquals(0.03, MarketFee.gameFor(league, after).coefficient, 0.0)
        // From Eastern midnight 2026-10-15 every league pays 0.06 live (changelog "Live Taker Fee Rises to 0.06").
        assertEquals(java.time.ZonedDateTime.of(2026, 10, 15, 0, 0, 0, 0, java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli(), MarketFee.ALL_RAISED_FROM_MS)
        for (league in listOf("NFL", "NBA", "ATP", null)) assertEquals(0.06, MarketFee.gameFor(league, MarketFee.ALL_RAISED_FROM_MS).coefficient, 0.0)
        assertEquals(0.03, MarketFee.gameFor("NFL", MarketFee.ALL_RAISED_FROM_MS - 1).coefficient, 0.0)
        // A bet placed the day before kept the old schedule.
        assertEquals(0.03, MarketFee.gameFor("MLB", MarketFee.RAISED_FROM_MS - 1).coefficient, 0.0)
        // The instant is Eastern midnight of Oct 8, 2026 (EDT, UTC-4).
        assertEquals(java.time.ZonedDateTime.of(2026, 10, 8, 0, 0, 0, 0, java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli(), MarketFee.RAISED_FROM_MS)
        // The worked example at 0.06: 10,000 contracts @ 0.50 = $1.50; pregame is still free.
        assertEquals(1.5, Fees.takerFee(0.5, MarketFee.gameFor("MLB", after), true) * 10_000 * 0.01, 1e-9)
        assertEquals(0.0, Fees.takerFee(0.5, MarketFee.gameFor("MLB", after), false), 0.0)
    }

    @Test
    fun `NFL MLB NCAAF futures charge pregame too - the case the old Fees object missed`() {
        // Novig docs: 10,000 @ 0.50 futures = $1.50, charged ALWAYS.
        val perDollar = Fees.takerFee(0.5, MarketFee.FUTURES, eventLive = false)
        assertEquals(1.5, perDollar * 10_000 * 0.01, 1e-9)
    }
}
