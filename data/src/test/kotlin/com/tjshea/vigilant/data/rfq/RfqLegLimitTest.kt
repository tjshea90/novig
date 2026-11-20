package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PF1: the profit profile quotes two-leg parlays only; longer ones decline but are PRICED, so the journal and the graded study keep our fair beside the market's price for them. */
class RfqLegLimitTest {
    private val now = 1_790_500_000_000L
    private fun leg(id: String, event: String, p: Double) = LegFair(id, event, "Moneyline $id", p, 5, now - 60_000L, now + 3 * 3_600_000L, false, "MONEY", league = "NBA")
    private val cfg = RfqConfig(legCalibration = false, mode = RfqConfig.Mode.LIVE, maxLegs = 2, maxCollateral = 80.0, maxOpenCollateral = 150.0, maxFair = 0.99, maxLegFair = 0.99, longshotsOnly = false, onlyToday = false)
    private fun created(vararg ids: String) = RfqProtocol.Created(now, "r", ids.toList(), now + 3_000L, 10.0)
    private val legs = (1..14).associate { "l$it" to leg("l$it", "e$it", 0.6) }

    @Test fun twoLegsAreQuotedAndThreeAreDeclinedButPriced() {
        val two = RfqPricer.decide(created("l1", "l2"), now, cfg, LegFairs { legs[it] }, 0.0)
        assertTrue(two is RfqDecision.Quote)
        val three = RfqPricer.decide(created("l1", "l2", "l3"), now, cfg, LegFairs { legs[it] }, 0.0) as RfqDecision.Decline
        assertTrue(three.reason, three.reason.contains("3 legs (this maker prices 2 to 2"))
        assertEquals("the decline carries the plain product for the evidence", 0.6 * 0.6 * 0.6, three.fair!!, 1e-12)
        assertEquals(3, three.legs.size)
    }

    @Test fun overTwelveLegsDeclineWithoutAPriceAndTheOldLimitStillWorksForALoosenedConfig() {
        val long = RfqPricer.decide(created(*(1..13).map { "l$it" }.toTypedArray()), now, cfg, LegFairs { legs[it] }, 0.0) as RfqDecision.Decline
        assertNull(long.fair)
        assertTrue(RfqPricer.decide(created("l1", "l2", "l3"), now, cfg.copy(maxLegs = 3, allHighMaxLegs = 0, manyLegsNeedLongshot = 0.0), LegFairs { legs[it] }, 0.0) is RfqDecision.Quote)
        assertNotNull(RfqPricer.decide(created("l1"), now, cfg, LegFairs { legs[it] }, 0.0))
    }

    @Test fun aLegBeyondTheMaximumLeadIsDeclinedAndZeroSwitchesItOff() {
        val far = mapOf("n" to leg("n", "e1", 0.6), "f" to LegFair("f", "e2", "Moneyline f", 0.6, 5, now - 60_000L, now + 10 * 3_600_000L, false, "MONEY", league = "NBA"))
        val capped = cfg.copy(maxLeadMs = 8 * 3_600_000L)
        val d = RfqPricer.decide(created("n", "f"), now, capped, LegFairs { far[it] }, 0.0) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("8 h away"))
        assertTrue(RfqPricer.decide(created("n", "f"), now, cfg, LegFairs { far[it] }, 0.0) is RfqDecision.Quote)       // maxLeadMs = 0: off
        assertTrue(RfqPricer.decide(created("n", "f"), now, capped.copy(maxLeadMs = 12 * 3_600_000L), LegFairs { far[it] }, 0.0) is RfqDecision.Quote)
    }
}
