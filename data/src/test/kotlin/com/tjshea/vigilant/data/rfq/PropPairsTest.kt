package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MK20: two player props of one game priced by the measured ratio (research/prop_pairs_2026-11-19.md), shadow first. */
class PropPairsTest {
    private val now = 1_790_500_000_000L

    private fun prop(id: String, stat: String, player: String, over: Boolean, p: Double, line: String = "20.5", event: String = "e1", type: String = stat.uppercase().replace(' ', '_')) =
        LegFair(id, event, "$stat $player ${if (over) "Over" else "Under"} $line", p, 5, now - 60_000L, now + 3 * 3_600_000L, false, type, league = "NBA")

    private fun pair(a: LegFair, b: LegFair) = listOf(a, b)

    @Test fun aPropLabelGivesItsPlayerAndSide() {
        assertEquals(PropPairs.Parsed("nico collins", true), PropPairs.parse(prop("a", "Receiving Yards", "Nico Collins", true, 0.5)))
        assertEquals(PropPairs.Parsed("karl-anthony towns", false), PropPairs.parse(prop("a", "Points", "Karl-Anthony Towns", false, 0.5)))
        assertEquals(PropPairs.Parsed("ka'imi fairbairn", true), PropPairs.parse(prop("a", "Field Goals Made", "Ka'imi Fairbairn", true, 0.5)))
        assertNull("a game line is not a prop", PropPairs.parse(LegFair("a", "e1", "Total Over 159.5", 0.5, 5, now, now, false, "TOTAL")))
        assertNull("a label with no known stat prefix", PropPairs.parse(LegFair("a", "e1", "ladder x|RECEIVING_YARDS|Tank Dell RECEIVING_YARDS Over 19.5", 0.5, 5, now, now, false, "RECEIVING_YARDS")))
        assertNull("a combined-yards label that does not start with the type's words", PropPairs.parse(LegFair("a", "e1", "Rushing + Receiving Yards David Montgomery Over 69.5", 0.5, 5, now, now, false, "RUSHING_AND_RECEIVING_YARDS")))
    }

    @Test fun differentPlayersGetTheUpperBoundRatiosOfTheirSides() {
        fun joint(ao: Boolean, bo: Boolean) = PropPairs.joint(pair(prop("a", "Points", "A One", ao, 0.5), prop("b", "Rebounds", "B Two", bo, 0.4)))!!
        assertEquals(0.2 * 1.55, joint(true, true), 1e-12)
        assertEquals(0.2 * 1.05, joint(true, false), 1e-12)
        assertEquals(0.2 * 1.15, joint(false, false), 1e-12)
    }

    @Test fun theSamePlayersStatsAreTiedMuchHarder() {
        fun joint(ao: Boolean, bo: Boolean) = PropPairs.joint(pair(prop("a", "Points", "A One", ao, 0.35), prop("b", "Assists", "A One", bo, 0.3)))!!
        assertEquals(0.105 * 2.5, joint(true, true), 1e-12)
        assertEquals(0.105 * 2.0, joint(false, false), 1e-12)
        assertEquals(0.105 * 1.6, joint(true, false), 1e-12)
        assertTrue("the same player is dearer than two players on every side", joint(true, true) > 0.105 * 1.55)
    }

    @Test fun aJointNeverExceedsTheSmallerLegAndNeverTheCapOf999() {
        // 0.7 x 0.7 x 2.5 = 1.225: the largest a joint can be is the smaller leg, 0.7
        assertEquals(0.7, PropPairs.joint(pair(prop("a", "Points", "A One", true, 0.7), prop("b", "Assists", "A One", true, 0.7)))!!, 1e-12)
        assertTrue(PropPairs.joint(pair(prop("a", "Points", "A One", true, 0.9995), prop("b", "Assists", "A One", true, 0.9995)))!! <= 0.999)
    }

    @Test fun whatTheModelDoesNotCoverDeclinesAsBefore() {
        val a = prop("a", "Points", "A One", true, 0.5)
        assertNull("three legs of one game", PropPairs.joint(listOf(a, prop("b", "Rebounds", "B Two", true, 0.5), prop("c", "Assists", "C Three", true, 0.5))))
        assertNull("two lines of one market of one player", PropPairs.joint(pair(a, prop("b", "Points", "A One", true, 0.4, line = "25.5"))))
        assertNull("a prop with a game line", PropPairs.joint(pair(a, LegFair("b", "e1", "Total Over 159.5", 0.5, 5, now, now, false, "TOTAL"))))
        assertNull("one leg", PropPairs.joint(listOf(a)))
        assertEquals("the same player's different stats are covered", true, PropPairs.joint(pair(a, prop("b", "Assists", "A One", false, 0.5))) != null)
    }

    private val base = RfqConfig(mode = RfqConfig.Mode.LIVE, margin = 0.0, minReturn = 0.0, liveMinReturn = 0.0, minPoints = 0.005, maxLegs = 3, propPremium = 0.0, allowProps = true,
        maxCollateral = 80.0, maxOpenCollateral = 150.0, maxFair = 0.99, maxLegFair = 0.99)
    private val created = RfqProtocol.Created(now, "r", listOf("a", "b"), now + 3_000L, 10.0)

    @Test fun inShadowThePricerOnlyRecordsTheModelsFairAndQuotesNothing() {
        val legs = mapOf("a" to prop("a", "Points", "A One", true, 0.5), "b" to prop("b", "Rebounds", "B Two", true, 0.4))
        val d = RfqPricer.decide(created, now, base, LegFairs { legs[it] }, 0.0) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("shadow"))
        assertEquals("the decline carries the modelled fair for the evidence", 0.2 * 1.55, d.fair!!, 1e-12)
    }

    @Test fun whenSwitchedLiveItQuotesAboveTheModelsFairAndOffItDeclinesAsBefore() {
        val legs = mapOf("a" to prop("a", "Points", "A One", true, 0.5), "b" to prop("b", "Rebounds", "B Two", true, 0.4))
        val q = RfqPricer.decide(created, now, base.copy(propPairsLive = true), LegFairs { legs[it] }, 0.0) as RfqDecision.Quote
        assertEquals(0.2 * 1.55, q.fair, 1e-12)
        assertTrue("the points floor still applies on top of the model's fair", q.price >= q.fair + 0.005 - 1e-9)
        val off = RfqPricer.decide(created, now, base.copy(propPairs = false, propPairsLive = true), LegFairs { legs[it] }, 0.0) as RfqDecision.Decline
        assertTrue(off.reason.contains("correlation unmodelled"))
        assertEquals("the independent product, as before", 0.2, off.fair!!, 1e-12)
    }
}
