package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudKellyTest {
    private fun inputs(
        fair: Double = 0.55, cost: Double = 0.50, cash: Double = 1_000.0, atRisk: Double = 0.0, gameAtRisk: Double = 0.0, dayStart: Double = 1_000.0,
        ageMs: Long? = 60_000L, agreeing: Int = 4, sharp: Boolean = true, stars: Double? = 4.0,
        kelly: Double = 0.25, betCap: Double = 0.05, gameCap: Double = 0.10, walletUse: Double = 0.9, dayStop: Double = 0.40,
    ) = CloudKelly.Inputs(fair, cost, cash, atRisk, gameAtRisk, dayStart, ageMs, agreeing, sharp, stars, kelly, betCap, gameCap, walletUse, dayStop)

    private fun dollars(i: CloudKelly.Inputs) = (CloudKelly.stake(i) as CloudKelly.Stake.Amount).dollars
    private fun skip(i: CloudKelly.Inputs) = (CloudKelly.stake(i) as CloudKelly.Stake.Skip).reason

    @Test
    fun `a clean edge is staked at the fractional Kelly of the bankroll`() {
        // full Kelly (0.55-0.50)/(1-0.50) = 10% of $1000; a quarter of it = $25, under the 5% ($50) bet cap
        assertEquals(25.0, dollars(inputs()), 0.01)
    }

    @Test
    fun `no sharp book agreeing keeps only 70 percent of the edge`() {
        assertEquals(17.5, dollars(inputs(sharp = false)), 0.01)
    }

    @Test
    fun `old fair odds, few books and low stars each shrink the stake and they multiply`() {
        val base = dollars(inputs())
        assertTrue(dollars(inputs(ageMs = 10 * 60_000L)) < base)
        assertTrue(dollars(inputs(agreeing = 2)) < base)
        assertTrue(dollars(inputs(stars = 2.0)) < base)
        // every factor at its worst (0.6 x 0.6 x 0.7) is floored at 0.3
        assertEquals(0.3, CloudKelly.confidence(20 * 60_000L, 1, 1.0), 1e-9)
        assertTrue(CloudKelly.confidence(null, 0, null) >= 0.3)
    }

    @Test
    fun `the per-bet cap holds a big edge`() {
        // a huge edge would want far more than 5%
        assertEquals(50.0, dollars(inputs(fair = 0.9, cost = 0.5, kelly = 1.0)), 0.01)
    }

    @Test
    fun `the game cap counts what the game already has at risk`() {
        // 10% of the $1000 bankroll is $100; $90 already there leaves $10
        assertEquals(10.0, dollars(inputs(cash = 910.0, atRisk = 90.0, gameAtRisk = 90.0)), 0.01)
        assertTrue(skip(inputs(cash = 900.0, atRisk = 100.0, gameAtRisk = 100.0)).contains("game"))
    }

    @Test
    fun `the wallet-use cap and the cash both limit the total at risk`() {
        assertTrue(skip(inputs(cash = 100.0, atRisk = 900.0, walletUse = 0.9)).contains("wallet-use"))
        assertEquals(30.0, dollars(inputs(cash = 30.0, atRisk = 970.0, walletUse = 1.0, kelly = 1.0, betCap = 0.5, gameCap = 0.5, fair = 0.9)), 0.01)
    }

    @Test
    fun `a day down half the stop halves the stakes and the stop ends trading for the day`() {
        // down 25% of the day's start is over half of the 40% stop: half the fraction
        assertEquals(0.5, dollars(inputs(cash = 750.0, dayStart = 1_000.0)) / dollars(inputs(cash = 1_000.0, dayStart = 1_000.0)) * 1_000.0 / 750.0, 0.01)
        assertTrue(skip(inputs(cash = 590.0, dayStart = 1_000.0)).contains("loss stop"))
        // exactly at the stop
        assertTrue(skip(inputs(cash = 600.0, dayStart = 1_000.0)).contains("loss stop"))
    }

    @Test
    fun `no edge after the price, a price of one or an empty bankroll is skipped`() {
        assertTrue(skip(inputs(fair = 0.49, cost = 0.50)).contains("edge"))
        assertTrue(skip(inputs(cost = 1.0)).contains("price"))
        assertTrue(skip(inputs(cash = 0.0, atRisk = 0.0)).contains("empty"))
    }

    @Test
    fun `a stake under a cent is skipped and never rounded up`() {
        assertTrue(skip(inputs(cash = 0.05, atRisk = 0.0, kelly = 0.01, betCap = 0.01)).contains("under a cent"))
    }
}
