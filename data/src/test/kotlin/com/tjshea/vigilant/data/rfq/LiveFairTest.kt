package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.stream.MarketLifecycle
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MK31: in-play parlays priced from Novig's own live book (research/review/CHANGES.md MK30-MK31). */
class LiveFairTest {
    private val now = 1_790_500_000_000L

    private fun market(id: String, ev: String, type: String = "MONEY", strike: Double? = null) =
        NovigMarket(id, ev, type, "OPEN", "desc", now - 3_600_000L, null as MarketFee?, listOf(NovigOutcome("$id-a", "A", "TBD"), NovigOutcome("$id-b", "B", "TBD")), strike)

    private fun book(id: String, bidA: Int?, bidB: Int?, at: Long = now - 1_000L, lc: MarketLifecycle? = null) = NovigBook(
        id, 1L, buildMap { bidA?.let { put("$id-a", listOf(BidLevel(it, 100L))) }; bidB?.let { put("$id-b", listOf(BidLevel(it, 100L))) } }, at, lifecycle = lc)

    private fun event(id: String, live: Boolean = true) = NovigEvent(id, "BASKETBALL", "NBA", if (live) NovigEvent.STATUS_LIVE else NovigEvent.STATUS_PREGAME, "Away @ Home", now - 3_600_000L)

    @Test fun theMiddleOfTheTwoBestBidsIsTheProbabilityAndTheGapBetweenThemTheSpread() {
        val (p, spread) = LiveFair.mid(0.55, 0.43)!!
        assertEquals("ask for A is 1 - 0.43 = 0.57; middle 0.56", 0.56, p, 1e-12)
        assertEquals(0.02, spread, 1e-12)
        assertNull("no bid on one side", LiveFair.mid(0.55, null))
        assertNull("a crossed book", LiveFair.mid(0.60, 0.45))
        assertNull("too wide to be a price (7 points)", LiveFair.mid(0.50, 0.43))
        assertNotNull("a market at the limit", LiveFair.mid(0.50, 0.46))
    }

    @Test fun onlyInPlayGameLinesWithAFreshTightBookArePriced() {
        val markets = listOf(market("m1", "e1"), market("m2", "e2"), market("m3", "e1", "TOTAL", 210.5), market("m4", "e1", "POINTS", 20.5), market("m5", "e1"), market("m6", "e1"))
        val books = mapOf(
            "m1" to book("m1", 550, 430), "m2" to book("m2", 550, 430), "m3" to book("m3", 480, 500),
            "m4" to book("m4", 480, 500), "m5" to book("m5", 550, 430, at = now - 60_000L), "m6" to book("m6", 550, 430, lc = MarketLifecycle("CLOSED", null, true, now)))
        val events = mapOf("e1" to event("e1"), "e2" to event("e2", live = false))
        val legs = LiveFair.build(markets, books, events, now = now)
        assertEquals("m1 and m3 only: m2 is a pregame game, m4 a prop, m5 is 60 s old, m6 closed", setOf("m1-a", "m1-b", "m3-a", "m3-b"), legs.keys)
        assertEquals(1.0, legs.getValue("m1-a").p + legs.getValue("m1-b").p, 1e-12)
        assertEquals(0.56, legs.getValue("m1-a").p, 1e-12)
        assertEquals(210.5, legs.getValue("m3-a").strike!!, 0.0)
        assertEquals(LiveFair.SOURCE, legs.getValue("m1-a").toLegFair().source)
        assertTrue(legs.getValue("m1-a").toLegFair().live)
    }

    @Test fun aStartedGameIsPricedOnlyFromTheLiveBookAndOtherwiseStaysDeclined() {
        val scanLeg = LegFair("x", "e1", "scan", 0.5, 5, now, now - 1_000L, false, "MONEY")             // the scan's pregame fair of a game that has since started
        val inner = LegFairs { if (it == "x") scanLeg else null }
        val b = LiveFair.Book(); b.replace(LiveFair.build(listOf(market("m1", "e1")), mapOf("m1" to book("m1", 550, 430)), mapOf("e1" to event("e1")), now = now), now)
        val f = LiveFair.Fairs(inner, { b }, { true }, { now })
        assertEquals("a live price wins", LiveFair.SOURCE, f.leg("m1-a")!!.source)
        val flagged = f.leg("x")!!
        assertTrue("started with no live price: marked in play, no live source", flagged.live && flagged.source != LiveFair.SOURCE)
        assertEquals("a game that has not started is the scan's", scanLeg.copy(startsMs = now + 3_600_000L), LiveFair.Fairs({ scanLeg.copy(startsMs = now + 3_600_000L) }, { b }, { true }, { now }).leg("x"))
        assertEquals("switched off: exactly the inner fair", scanLeg, LiveFair.Fairs(inner, { b }, { false }, { now }).leg("x"))
    }

    // ---- the pricer ----
    private val cfg = RfqConfig(legCalibration = false, allHighMaxLegs = 0, mode = RfqConfig.Mode.LIVE, liveParlays = RfqConfig.Mode.LIVE, margin = 0.015, minReturn = 0.004, liveMinReturn = 0.004, minPoints = 0.002, fairErrorRel = 0.006,
        maxLegs = 100, maxStake = 12.0, maxCollateral = 75.0, maxOpenCollateral = 200.0, maxFair = 0.6, maxLegFair = 0.9, minLeadMs = 60_000L, liveMaxCollateral = 0.0)
    private fun live(id: String, ev: String, p: Double, ageMs: Long = 1_000L, type: String = "MONEY", source: String = LiveFair.SOURCE) =
        LegFair(id, ev, "live", p, 1, now - ageMs, now - 600_000L, true, type, source = source)
    private fun pre(id: String, ev: String, p: Double) = LegFair(id, ev, "pre", p, 5, now - 30_000L, now + 3 * 3_600_000L, false, "MONEY")
    private fun fairs(vararg l: LegFair) = LegFairs { id -> l.firstOrNull { it.outcomeId == id } }
    private fun created(vararg ids: String, minWager: Double = 10.0) = RfqProtocol.Created(now, "rfq", ids.toList(), now + 3_000L, minWager)

    @Test fun inShadowAnInPlayParlayIsPricedAndNeverQuoted() {
        val f = fairs(live("a", "e1", 0.6), pre("b", "e2", 0.6))
        val d = RfqPricer.decide(created("a", "b"), now, cfg.copy(liveParlays = RfqConfig.Mode.SHADOW), f, 0.0) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("in-play") && d.reason.contains("shadow"))
        assertEquals("the plain product is the evidence", 0.36, d.fair!!, 1e-12)
    }

    @Test fun whenSwitchedLiveItQuotesAboveTheFairByThePremiumAndPointsFloors() {
        val f = fairs(live("a", "e1", 0.6), pre("b", "e2", 0.6))
        val q = RfqPricer.decide(created("a", "b"), now, cfg, f, 0.0) as RfqDecision.Quote
        assertEquals(0.36, q.fair, 1e-12)
        assertTrue("fair x 1.05 = 0.378, fair + 0.02 = 0.38 (was ${q.price})", q.price >= 0.38 - 1e-9)
        assertTrue("never below the usual rule's price", q.price >= q.rulePrice)
    }

    @Test fun aParlayOverTheLiveWinChanceCapIsNotQuoted() {
        val f = fairs(live("a", "e1", 0.8), pre("b", "e2", 0.7))      // 56%: under the 60% general cap, over the 50% live cap
        val d = RfqPricer.decide(created("a", "b"), now, cfg, f, 0.0) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("live cap"))
        assertTrue("the same parlay with no live leg is quoted", RfqPricer.decide(created("a", "b"), now, cfg, fairs(pre("a", "e1", 0.8), pre("b", "e2", 0.7)), 0.0) is RfqDecision.Quote)
    }

    @Test fun anInPlayLegIsDeclinedWhenItsPriceIsStaleItsSourceIsNotTheLiveBookOrItIsAProp() {
        val ok = fairs(live("a", "e1", 0.6), pre("b", "e2", 0.6))
        assertTrue(RfqPricer.decide(created("a", "b"), now, cfg, ok, 0.0) is RfqDecision.Quote)
        assertTrue((RfqPricer.decide(created("a", "b"), now, cfg, fairs(live("a", "e1", 0.6, ageMs = 9_000L), pre("b", "e2", 0.6)), 0.0) as RfqDecision.Decline).reason.contains("s old"))
        assertTrue((RfqPricer.decide(created("a", "b"), now, cfg, fairs(live("a", "e1", 0.6, source = ""), pre("b", "e2", 0.6)), 0.0) as RfqDecision.Decline).reason.contains("in play"))
        assertTrue((RfqPricer.decide(created("a", "b"), now, cfg, fairs(live("a", "e1", 0.6, type = "POINTS"), pre("b", "e2", 0.6)), 0.0) as RfqDecision.Decline).reason.contains("prop leg is in play"))
        assertTrue((RfqPricer.decide(created("a", "b"), now, cfg.copy(liveParlays = RfqConfig.Mode.OFF), ok, 0.0) as RfqDecision.Decline).reason.contains("in play"))
    }

    @Test fun twoLegsOfOneGameInPlayAreNotModelledAndTheLiveCollateralCapBinds() {
        val same = fairs(live("a", "e1", 0.7), live("b", "e1", 0.7))
        assertTrue((RfqPricer.decide(created("a", "b"), now, cfg, same, 0.0) as RfqDecision.Decline).reason.contains("one game"))
        val f = fairs(live("a", "e1", 0.6), pre("b", "e2", 0.6))
        // collateral of a $10 stake at a 0.38 price is 10 x 0.62 / 0.38 = $16.3: a $10 cap passes nothing
        val d = RfqPricer.decide(created("a", "b"), now, cfg.copy(liveMaxCollateral = 10.0), f, 0.0) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("caps allow"))
    }

    @Test fun aLegTheBookHasNoPriceForIsNotedAsDemandAndForgottenAfterTheWindow() {
        val b = LiveFair.Book()
        val f = LiveFair.Fairs({ null }, { b }, { true }, { now })
        assertNull(f.leg("unknown-1")); assertNull(f.leg("unknown-2"))
        b.demand["old"] = now - 20 * 60_000L
        assertEquals(setOf("unknown-1", "unknown-2"), b.recentDemand(now, 10 * 60_000L).toSet())
        assertNull("the old one was forgotten", b.demand["old"])
    }

    @Test fun aNearlyDecidedInPlayLegIsPricedButAPregameOneOverTheLegCapIsNot() {
        val inPlay = fairs(live("a", "e1", 0.97), pre("b", "e2", 0.45))
        val q = RfqPricer.decide(created("a", "b"), now, cfg, inPlay, 0.0) as RfqDecision.Quote
        assertEquals(0.97 * 0.45, q.fair, 1e-12)
        assertTrue("a 99.5% live leg is over the live leg cap", (RfqPricer.decide(created("a", "b"), now, cfg, fairs(live("a", "e1", 0.995), pre("b", "e2", 0.45)), 0.0) as RfqDecision.Decline).reason.contains("too likely"))
        assertTrue("a 97% pregame leg still is", (RfqPricer.decide(created("a", "b"), now, cfg, fairs(pre("a", "e1", 0.97), pre("b", "e2", 0.45)), 0.0) as RfqDecision.Decline).reason.contains("too likely"))
    }

    @Test fun aPregameParlayOfThreeOrMoreLegsNeedsALongshotAmongThem() {
        val allFav = fairs(pre("a", "e1", 0.7), pre("b", "e2", 0.7), pre("c", "e3", 0.7))
        val d = RfqPricer.decide(created("a", "b", "c"), now, cfg.copy(maxFair = 0.99), allFav, 0.0) as RfqDecision.Decline
        assertTrue(d.reason, d.reason.contains("every leg a favourite"))
        assertEquals("the plain product is the evidence", 0.343, d.fair!!, 1e-9)
        val mixed = fairs(pre("a", "e1", 0.7), pre("b", "e2", 0.7), pre("c", "e3", 0.4))
        assertTrue(RfqPricer.decide(created("a", "b", "c"), now, cfg, mixed, 0.0) is RfqDecision.Quote)
        assertTrue("two favourites are quoted", RfqPricer.decide(created("a", "b"), now, cfg, fairs(pre("a", "e1", 0.7), pre("b", "e2", 0.7)), 0.0) is RfqDecision.Quote)
        assertTrue("switched off", RfqPricer.decide(created("a", "b", "c"), now, cfg.copy(maxFair = 0.99, manyLegsNeedLongshot = 0.0), allFav, 0.0) is RfqDecision.Quote)
    }

    // ---- MK37: in-play pairs of ONE game go through the same-game copula ----
    private fun gameMarket(id: String, type: String, o1: String, o2: String, strike: Double?, desc: String = "desc") =
        NovigMarket(id, "g1", type, "OPEN", desc, now - 3_600_000L, null as MarketFee?, listOf(NovigOutcome("$id-a", o1, "TBD"), NovigOutcome("$id-b", o2, "TBD")), strike)
    private val gameEvent = NovigEvent("g1", "BASKETBALL", "NCAAB", NovigEvent.STATUS_LIVE, "Kansas Jayhawks @ Houston Cougars", now - 3_600_000L)

    @Test fun aLiveMoneylineSpreadTotalAndTeamTotalAreReadWithTheirSideAndLine() {
        val money = LiveFair.structure(gameMarket("m", "MONEY", "KAN", "HOU", 0.0), gameEvent)!!
        assertEquals(LineKind.MONEYLINE, money.kind); assertEquals(Side.AWAY, money.sideA); assertEquals(Side.HOME, money.sideB)
        val spread = LiveFair.structure(gameMarket("s", "SPREAD", "HOU -3.5", "KAN +3.5", -3.5), gameEvent)!!
        assertEquals(LineKind.SPREAD, spread.kind); assertEquals(Side.HOME, spread.sideA); assertEquals(Side.AWAY, spread.sideB); assertEquals("the HOME handicap", -3.5, spread.homeLine!!, 0.0)
        assertNull("Novig's own strike disagrees with the names: not read", LiveFair.structure(gameMarket("s", "SPREAD", "HOU -3.5", "KAN +3.5", 3.5), gameEvent))
        val total = LiveFair.structure(gameMarket("t", "TOTAL", "Under 150.5", "Over 150.5", 150.5), gameEvent)!!
        assertEquals(LineKind.TOTAL, total.kind); assertEquals(Side.UNDER, total.sideA); assertEquals(Side.OVER, total.sideB)
        assertNull("two different numbers", LiveFair.structure(gameMarket("t", "TOTAL", "Under 150.5", "Over 151.5", null), gameEvent))
        val team = LiveFair.structure(gameMarket("tt", "TEAM_TOTAL", "Over 71.5", "Under 71.5", 71.5, desc = "Houston Cougars 71.5 TEAM_TOTAL"), gameEvent)!!
        assertEquals(LineKind.TEAM_TOTAL, team.kind); assertEquals(Side.OVER, team.sideA); assertEquals("Houston is the home side", false, team.awayTeam)
        assertTrue(LiveFair.structure(gameMarket("tt", "TEAM_TOTAL", "Over 71.5", "Under 71.5", 71.5, desc = "Kansas Jayhawks 71.5 TEAM_TOTAL"), gameEvent)!!.awayTeam)
        assertNull("a team the event does not name", LiveFair.structure(gameMarket("m", "MONEY", "ZZZ", "QQQ", 0.0), gameEvent))
    }

    @Test fun theBuiltLegsCarryWhatTheCopulaNeeds() {
        val markets = listOf(gameMarket("m", "MONEY", "KAN", "HOU", 0.0), gameMarket("t", "TOTAL", "Over 150.5", "Under 150.5", 150.5))
        val books = mapOf("m" to book("m", 400, 580), "t" to book("t", 500, 500))
        val legs = LiveFair.build(markets, books, mapOf("g1" to gameEvent), now = now)
        val away = legs.getValue("m-a").toLegFair(); val home = legs.getValue("m-b").toLegFair()
        assertEquals(Side.AWAY, away.side); assertEquals(Side.HOME, home.side); assertEquals(LineKind.MONEYLINE, home.kind)
        assertEquals(0.41, away.probability, 1e-9); assertEquals(0.59, home.probability, 1e-9)
        assertEquals(Side.OVER, legs.getValue("t-a").toLegFair().side); assertEquals(LineKind.TOTAL, legs.getValue("t-b").toLegFair().kind)
        assertEquals(0, home.period)
    }

    private fun liveGame(id: String, p: Double, kind: LineKind?, side: Side?, type: String, homeLine: Double? = null) =
        LegFair(id, "g1", "live", p, 1, now - 1_000L, now - 600_000L, true, type, kind = kind, side = side, homeLine = homeLine, league = "NCAAB", source = LiveFair.SOURCE)

    @Test fun anInPlayMoneylineAndTotalOfOneGameAreQuotedAtTheCopulaJointNotDeclined() {
        val f = fairs(liveGame("a", 0.6, LineKind.MONEYLINE, Side.HOME, "MONEY"), liveGame("b", 0.5, LineKind.TOTAL, Side.OVER, "TOTAL"))
        val q = RfqPricer.decide(created("a", "b"), now, cfg, f, 0.0) as RfqDecision.Quote
        assertEquals("nearly independent (NCAAB rho -0.014): 0.30 give or take the 2% premium", 0.30, q.fair, 0.01)
        assertTrue("quoted above the fair (price ${q.price} fair ${q.fair})", q.price > q.fair)
        // a spread with the total, the other commonest pair
        val s = fairs(liveGame("a", 0.55, LineKind.SPREAD, Side.AWAY, "SPREAD", homeLine = -3.5), liveGame("b", 0.5, LineKind.TOTAL, Side.UNDER, "TOTAL"))
        assertTrue(RfqPricer.decide(created("a", "b"), now, cfg, s, 0.0) is RfqDecision.Quote)
    }

    @Test fun anInPlayPairOfTheSameLineKindOrOneNobodyReadStillDeclinesAsUnmodelled() {
        val dup = fairs(liveGame("a", 0.6, LineKind.MONEYLINE, Side.HOME, "MONEY"), liveGame("b", 0.6, LineKind.SPREAD, Side.HOME, "SPREAD", homeLine = -3.5))
        assertTrue((RfqPricer.decide(created("a", "b"), now, cfg, dup, 0.0) as RfqDecision.Decline).reason.contains("one game"))
        val unread = fairs(liveGame("a", 0.6, null, null, "MONEY"), liveGame("b", 0.5, LineKind.TOTAL, Side.OVER, "TOTAL"))
        assertTrue((RfqPricer.decide(created("a", "b"), now, cfg, unread, 0.0) as RfqDecision.Decline).reason.contains("one game"))
        val stale = fairs(liveGame("a", 0.6, LineKind.MONEYLINE, Side.HOME, "MONEY").copy(asOfMs = now - 9_000L), liveGame("b", 0.5, LineKind.TOTAL, Side.OVER, "TOTAL"))
        assertTrue("the live freshness rule still applies to a same-game pair", (RfqPricer.decide(created("a", "b"), now, cfg, stale, 0.0) as RfqDecision.Decline).reason.contains("s old"))
    }

    @Test fun aBookTheStreamHoldsIsCurrentEvenWhenItsLastChangeIsOld() {
        val markets = listOf(market("m1", "e1"))
        val quiet = mapOf("m1" to book("m1", 550, 430, at = now - 120_000L))
        assertTrue("by the book's own stamp it is too old", LiveFair.build(markets, quiet, mapOf("e1" to event("e1")), now = now).isEmpty())
        val legs = LiveFair.build(markets, quiet, mapOf("e1" to event("e1")), now = now, streamCurrent = true)
        assertEquals("a stream-held book is current as of now", now, legs.getValue("m1-a").asOfMs)
    }
}
