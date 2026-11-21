package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** UW4: `quoteBelowFairPoints` puts a PREGAME quote that many points under our own fair (the market's best price sits about a point under it); 0 leaves the floors as they were. */
class RfqBelowFairTest {
    private val now = 1_790_500_000_000L
    private fun leg(id: String, event: String, p: Double) = LegFair(id, event, "Moneyline $id", p, 5, now - 60_000L, now + 3 * 3_600_000L, false, "MONEY", league = "NBA")
    private val cfg = RfqConfig(legCalibration = false, mode = RfqConfig.Mode.LIVE, maxLegs = 2, maxCollateral = 80.0, maxOpenCollateral = 150.0, maxFair = 0.99, maxLegFair = 0.99, longshotsOnly = false, onlyToday = false,
        margin = 0.001, minReturn = 0.001, liveMinReturn = 0.001, minPoints = 0.0005, fairErrorRel = 0.001)
    private fun created() = RfqProtocol.Created(now, "r", listOf("a", "b"), now + 3_000L, 10.0)
    private val legs = mapOf("a" to leg("a", "e1", 0.6), "b" to leg("b", "e2", 0.6))
    private fun quote(c: RfqConfig) = RfqPricer.decide(created(), now, c, LegFairs { legs[it] }, 0.0) as RfqDecision.Quote

    @Test fun offByDefaultTheFloorsDecideAndTheQuoteSitsJustOverFair() {
        val q = quote(cfg)
        assertEquals(0.36, q.fair, 1e-12)
        assertTrue("price ${q.price}", q.price > q.fair && q.price - q.fair < 0.0025)
    }

    @Test fun aSettingPutsTheQuoteThatManyPointsUnderTheFairRoundedUp() {
        assertEquals(0.355, quote(cfg.copy(quoteBelowFairPoints = 0.005)).price, 1e-9)
        assertEquals(0.350, quote(cfg.copy(quoteBelowFairPoints = 0.01)).price, 1e-9)
        assertEquals("rounded UP to Novig's thousandth: 0.36 - 0.0125 = 0.3475", 0.348, quote(cfg.copy(quoteBelowFairPoints = 0.0125)).price, 1e-9)
    }

    @Test fun aSettingFarTooBigEndsAtNovigsFloorPriceAndTheStakeRulesDeclineItNeverAnInvalidPrice() {
        val d = RfqPricer.decide(created(), now, cfg.copy(quoteBelowFairPoints = 0.5), LegFairs { legs[it] }, 0.0)
        assertTrue(d.toString(), d is RfqDecision.Decline && !d.reason.contains("outside Novig"))
    }
}
