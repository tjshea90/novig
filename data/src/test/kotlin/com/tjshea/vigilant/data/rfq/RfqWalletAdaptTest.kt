package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PW1 (Tj 2026-11-20: "Later the wallet will have over 100 dollars, so the parlay engine should be able to adapt and use the full wallet and price the most profitable parlays using available money in the wallet"):
 * while the wallet's cash is plentiful every allowed parlay is quoted; as the share locked in open parlays rises only the parlays that return most per dollar-day are, and they ask a little more; dollar-sized rules
 * (the prop reserves, the count of parlays on one outcome) follow the wallet.
 */
class RfqWalletAdaptTest {
    private val now = 1_790_500_000_000L
    private fun leg(id: String, event: String, p: Double, startsInH: Double = 3.0, type: String = "MONEY") =
        LegFair(id, event, "Moneyline $id", p, 5, now - 60_000L, now + (startsInH * 3_600_000.0).toLong(), false, type, league = "NBA")
    private val cfg = RfqConfig(legCalibration = false, mode = RfqConfig.Mode.LIVE, maxLegs = 2, walletCollateral = true, maxFair = 0.9, maxLegFair = 0.9, longshotsOnly = false, onlyToday = false, minLeadMs = 60_000L,
        adaptiveCash = true, scarcityStartShare = 0.35, scarcityScoreHigh = 8.0, scarcityMarginPoints = 0.004, margin = 0.0, minReturn = 0.0, liveMinReturn = 0.0, minPoints = 0.0, fairErrorRel = 0.0)
    private fun created(vararg ids: String) = RfqProtocol.Created(now, "r", ids.toList(), now + 3_000L, 10.0)

    // fair 0.36, 3 h to the start: lock 6.5 h, score = 24 / (6.5 x (1 - 0.36)) = 5.8
    private val slow = mapOf("a" to leg("a", "e1", 0.6), "b" to leg("b", "e2", 0.6))
    // fair 0.48, 1 h to the start: lock 4.5 h, score = 24 / (4.5 x 0.52) = 10.3
    private val quick = mapOf("c" to leg("c", "e3", 0.6, startsInH = 1.0), "d" to leg("d", "e4", 0.8, startsInH = 1.0))

    private fun decide(c: RfqProtocol.Created, legs: Map<String, LegFair>, util: Double?, conf: RfqConfig = cfg, wallet: Double = 500.0) =
        RfqPricer.decide(c, now, conf, LegFairs { legs[it] }, 0.0, null, wallet, wallet, util)

    @Test fun withPlentyOfCashEveryAllowedParlayIsQuotedAtTheFloor() {
        val none = decide(created("a", "b"), slow, null) as RfqDecision.Quote
        val low = decide(created("a", "b"), slow, 0.20) as RfqDecision.Quote
        val edge = decide(created("a", "b"), slow, 0.35) as RfqDecision.Quote
        assertEquals(none.price, low.price, 1e-12); assertEquals(none.price, edge.price, 1e-12)
        assertEquals("the floor: fair rounded up to the grid", 0.36, none.price, 0.0011)
    }

    @Test fun asTheWalletFillsTheSlowLowReturnParlayIsTurnedAwayAndTheQuickOneIsKeptAtAHigherPrice() {
        val mid = decide(created("a", "b"), slow, 0.80) as RfqDecision.Quote       // ramp 0.69: bar 5.5, score 5.8: still quoted, 0.28 point higher
        val floor = (decide(created("a", "b"), slow, 0.20) as RfqDecision.Quote).price
        assertTrue("asks a little more: ${mid.price} vs $floor", mid.price in (floor + 0.002)..(floor + 0.004))
        val full = decide(created("a", "b"), slow, 0.95) as RfqDecision.Decline       // ramp 0.92: bar 7.4, score 5.8
        assertTrue(full.reason, full.reason.contains("95% locked") && full.reason.contains("scores 5.8"))
        assertTrue("the decline keeps our fair for the journal", full.fair != null)
        val q = decide(created("c", "d"), quick, 0.95) as RfqDecision.Quote         // score 10.3 beats the bar 7.4
        val qFloor = (decide(created("c", "d"), quick, 0.10) as RfqDecision.Quote).price
        assertTrue("at the full ramp it asks up to 0.4 point more: ${q.price} vs $qFloor", q.price in (qFloor + 0.003)..(qFloor + 0.0041))
    }

    @Test fun theSwitchOffAndAnUnreadWalletChangeNothing() {
        assertTrue(decide(created("a", "b"), slow, 0.99, cfg.copy(adaptiveCash = false)) is RfqDecision.Quote)
        assertTrue("no utilization (the balance is not read): no gate", decide(created("a", "b"), slow, null) is RfqDecision.Quote)
    }

    @Test fun thePropReserveFollowsTheWalletWhenItIsGivenAsAShare() {
        val legs = mapOf("g" to leg("g", "e1", 0.6), "p" to leg("p", "e2", 0.6, type = "POINTS"))
        val c = cfg.copy(adaptiveCash = false, allowProps = true, propPremium = 0.0, propCashReserve = 90.0)
        // $110 of free cash: a dollar reserve of $90 leaves $20 for a $10 stake at fair 0.36 ($17.8 of collateral); 60% of a $200 wallet is $120 and leaves nothing
        val dollars = RfqPricer.decide(created("g", "p"), now, c, LegFairs { legs[it] }, 0.0, null, 110.0, 200.0, null)
        assertTrue("dollar reserve: ${dollars}", dollars is RfqDecision.Quote)
        val share = RfqPricer.decide(created("g", "p"), now, c.copy(propCashReserveShare = 0.6), LegFairs { legs[it] }, 0.0, null, 110.0, 200.0, null)
        assertTrue("share reserve ($120 of a $200 wallet): ${share}", share is RfqDecision.Decline && (share as RfqDecision.Decline).reason.contains("cash is short"))
        val big = RfqPricer.decide(created("g", "p"), now, c.copy(propCashReserveShare = 0.6), LegFairs { legs[it] }, 0.0, null, 110.0, 100.0, null)
        assertTrue("a $100 wallet reserves $60 and quotes with $110 free: $big", big is RfqDecision.Quote)
    }

    @Test fun theCountOfParlaysOnOneOutcomeGrowsWithTheWallet() {
        val legs = mapOf("a" to leg("a", "e1", 0.5), "b" to leg("b", "e2", 0.4))
        fun quotes(step: Double): Boolean {
            val sent = ArrayList<String>()
            val c = cfg.copy(adaptiveCash = false, maxParlaysPerOutcome = 2, outcomeCountWalletStep = step, maxSameParlay = 1, walletLegShare = 1.0, minFair = 0.03, minBooks = 2)
            val m = RfqMaker({ c }, { id -> legs[id] }, { 0.0 }, { sent += it; true }, { }, { _, _ -> }, { now }, cash = { 1_000.0 },
                openSets = { listOf(listOf("a", "x1"), listOf("a", "x2"), listOf("a", "x3")) })        // "a" is already in three open parlays
            m.onEvent(created("a", "b"))
            return sent.isNotEmpty()
        }
        assertFalse("fixed limit of 2: the fourth parlay on 'a' is not quoted", quotes(0.0))
        assertTrue("a $1,000 wallet at one more per $500 allows 4", quotes(500.0))
        assertFalse("one more per $1,000 gives a limit of 3 and three are held", quotes(1_000.0))
    }
}
