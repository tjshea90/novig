package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.novig.NovigParlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** MK12: what the other makers charge, learned from Novig's own keyless parlay price (research/novig_parlay_maker_2026-11-19.md). */
class RfqMarketTest {
    private val cfg = RfqConfig(mode = RfqConfig.Mode.SHADOW, minReturn = 0.004, minPoints = 0.007, probeMinSamples = 40)

    private fun leg(event: String, type: String = "MONEY", league: String = "NBA") = LegFair("o$event$type", event, "l", 0.5, 5, 0L, 0L, false, type, league = league)

    @Test fun aClassIsLegsKindOfLegLeagueAndTheSizeOfTheFair() {
        assertEquals("2|gl|NBA|mid", RfqMarketModel.classOf(listOf(leg("e1"), leg("e2")), 0.2))
        assertEquals("3|sg|NFL|mid", RfqMarketModel.classOf(listOf(leg("e1", "SPREAD", "NFL"), leg("e1", "TOTAL", "NFL"), leg("e2", "MONEY", "NFL")), 0.3))
        assertEquals("2|prop|NBA|lo", RfqMarketModel.classOf(listOf(leg("e1"), leg("e2", "POINTS")), 0.05))
        assertEquals("2|psg|NBA|hi", RfqMarketModel.classOf(listOf(leg("e1", "POINTS"), leg("e1", "ASSISTS")), 0.6))
        assertEquals("5+|gl|NBA|lo", RfqMarketModel.classOf(List(6) { leg("e$it") }, 0.01))
        assertEquals("first-half lines are game lines", "2|gl|NHL|mid", RfqMarketModel.classOf(listOf(leg("e1", "SPREAD_1H", "NHL"), leg("e2", "TOTAL_1H", "NHL")), 0.3))
        assertEquals("legs from two leagues", "2|gl|multi|mid", RfqMarketModel.classOf(listOf(leg("e1", "MONEY", "NBA"), leg("e2", "MONEY", "ATP")), 0.3))
        assertEquals("2|gl|*|mid", RfqMarketModel.coarse("2|gl|NBA|mid"))
    }

    @Test fun aThinLeagueFallsBackOnTheWholeKindOfParlay() {
        val m = RfqMarketModel()
        // ATP: 10 answers, the market pays 2 points over our fair; other leagues of the same kind: 100 answers at 0.2 points
        repeat(10) { m.observe("2|gl|ATP|mid", 0.30, 0.32, 1L) }; repeat(100) { m.observe("2|gl|NHL|mid", 0.30, 0.302, 1L) }
        assertEquals(10, m.samples("2|gl|ATP|mid")); assertEquals(110, m.samples("2|gl|*|mid"))
        val cfg = RfqConfig(minReturn = 0.0, minPoints = 0.0, probeMinSamples = 40)
        // ATP has too few answers of its own, so it prices from the kind of parlay across leagues: just under the 0.2 points the pack pays
        val c = m.best("2|gl|ATP|mid", 0.30, 2, 0.0, 0.0005, cfg)!!
        assertTrue("falls back on the pooled answers (was $c)", c < 0.0025)
        // with 40 of its own the league speaks for itself
        repeat(40) { m.observe("2|gl|ATP|mid", 0.30, 0.32, 1L) }
        assertTrue(m.best("2|gl|ATP|mid", 0.30, 2, 0.0, 0.0005, cfg)!! > 0.015)
    }

    @Test fun theWinShareIsTheShareOfAnswersOurQuoteWouldHaveBeaten() {
        val m = RfqMarketModel()
        // the market's best price over our fair, in points of pot: 0.2, 0.4, 0.6, 0.8, 1.0, and one parlay nobody quoted
        for (g in listOf(0.002, 0.004, 0.006, 0.008, 0.010)) m.observe("c", 0.20, 0.20 + g, 1_000L)
        m.observe("c", 0.20, null, 1_000L)
        assertEquals(1.0, m.winShare("c", 0.0)!!, 1e-12)                                // at the fair we beat everyone (and the parlay nobody quoted)
        assertEquals(5.0 / 6.0, m.winShare("c", 0.003)!!, 1e-12)                        // 0.4, 0.6, 0.8, 1.0 are above 0.3; 0.2 is not; plus the unquoted
        assertEquals(1.0 / 6.0, m.winShare("c", 0.010)!!, 1e-12)                        // only the unquoted one: a TIE is not a win (the lowest price wins)
        assertNull(m.winShare("none", 0.0))
        val s = m.summary().single()
        assertEquals(6, s.n); assertEquals(1.0 / 6.0, s.noQuote, 1e-12); assertEquals(0.6, s.median!!, 1e-9)
    }

    @Test fun observationsAgeOutAndTheWindowIsBounded() {
        val m = RfqMarketModel(keep = 50, maxAgeMs = 1_000L)
        repeat(80) { m.observe("c", 0.2, 0.21, it.toLong() * 10L) }
        assertEquals("at most 50, and all inside the age window", 50, m.samples("c"))
        m.observe("c", 0.2, 0.21, 100_000L)
        assertEquals("a late observation ages the old ones out", 1, m.samples("c"))
    }

    @Test fun theBestMarginIsTheOneThatMaximisesWinShareTimesNetEdge() {
        val m = RfqMarketModel()
        // a market that pays 0.5 points over our fair 40% of the time and 1.5 points the other 60%
        repeat(40) { m.observe("c", 0.30, 0.305, 1L) }; repeat(50) { m.observe("c", 0.30, 0.315, 1L) }; repeat(10) { m.observe("c", 0.30, 0.316, 1L) }
        // one day to the games: below 0.5 points the net edge is negative (allowance + the cost of the collateral); between 0.5 and 1.5 the win share is 0.6; the score peaks just under the 1.5 points the market tolerates
        val c = m.best("c", 0.30, 2, 1.0, 0.0005, cfg)!!
        assertTrue("the margin lands one step under the 1.5 points the market tolerates 60% of the time (was $c)", c in 0.0140..0.0150)
        assertNull("fewer than probeMinSamples answers: no model price", RfqMarketModel().also { it.observe("c", 0.3, 0.31, 1L) }.best("c", 0.3, 2, 1.0, 0.0, cfg))
        // never below the floor it is given
        assertTrue(m.best("c", 0.30, 2, 1.0, 0.01, cfg)!! >= 0.01)
        // a market that quotes below our fair every time cannot be beaten: no price
        val low = RfqMarketModel(); repeat(60) { low.observe("c", 0.30, 0.29, 1L) }
        assertNull(low.best("c", 0.30, 2, 1.0, 0.0005, cfg))
    }

    @Test fun thePricerQuotesTheModelsPriceOnlyWhenTheConfigSaysSoAndNeverBelowTheFloors() {
        val m = RfqMarketModel()
        val legs = listOf(leg("e1"), leg("e2"))
        // the class of a 2-leg game-line parlay with fair 0.25
        val cls = RfqMarketModel.classOf(legs, 0.25)
        repeat(100) { m.observe(cls, 0.25, 0.25 + 0.02, 1L) }                           // the market pays two points over our fair every time
        val now = 1_790_500_000_000L
        val lf = LegFairs { id -> LegFair(id, if (id == "a") "e1" else "e2", "l", 0.5, 5, now - 60_000L, now + 3 * 3_600_000L, false, "MONEY") }
        val created = RfqProtocol.Created(now, "r", listOf("a", "b"), now + 3_000L, 10.0)
        val live = RfqConfig(mode = RfqConfig.Mode.LIVE, margin = 0.0, minReturn = 0.0, minPoints = 0.005, maxLegs = 3, maxCollateral = 80.0, maxOpenCollateral = 150.0)
        val off = RfqPricer.decide(created, now, live, lf, 0.0, m) as RfqDecision.Quote
        assertEquals("OFF: the rule's price", off.rulePrice, off.price, 0.0)
        assertNotNull(off.modelPrice)
        assertTrue("the model prices just under what the market pays (two points over our fair): ${off.modelPrice}", off.modelPrice!! in 0.2650..0.2700)
        val on = RfqPricer.decide(created, now, live.copy(marketPricing = true), lf, 0.0, m) as RfqDecision.Quote
        assertEquals(off.modelPrice!!, on.price, 1e-12)
        assertTrue("it never prices below the floors", on.price >= on.rulePrice)
    }

    private fun item(i: Int, cls: String, ids: List<String> = listOf("a$i", "b$i")) = RfqProbe.Item("r$i", ids, cls, 0.2, 0.21, 2, System.currentTimeMillis())

    @Test fun theProbeAsksTheLeastSampledClassFirstOncePerSetAndJournalsTheAnswer() = runBlocking {
        val model = RfqMarketModel(); repeat(50) { model.observe("busy", 0.2, 0.21, System.currentTimeMillis()) }
        val asked = Collections.synchronizedList(ArrayList<List<String>>()); val rows = Collections.synchronizedList(ArrayList<RfqRow>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val probe = RfqProbe(scope, model, { rows += it }, { true }, { ids -> asked += ids; NovigParlay.Result.Priced(NovigParlay.Quote(0.23, 0L, 0L)) }, gapMs = 5L, startDelayMs = 0L, workers = 2)
        probe.offer(item(1, "busy")); probe.offer(item(2, "rare"))
        probe.offer(item(2, "rare"))                                                     // the same legs again: once
        delay(400)
        scope.cancel()
        assertEquals("one probe per set of legs", 2, asked.size)
        assertEquals("the class with fewer answers goes first", listOf("a2", "b2"), asked.first())
        assertEquals(1, model.samples("rare")); assertEquals(51, model.samples("busy"))
        val row = rows.first { it.kind == "PROBE" }
        assertTrue(row.data.contains("\"st\":\"PRICED\"") && row.data.contains("\"mkt\":0.23") && row.data.contains("\"ours\":0.21"))
        assertTrue(probe.line().contains("2 sent, 2 priced"))
        // the same legs are not asked again soon (the probe's own RFQ comes back on the socket with the same legs)
        assertEquals(0, probe.queued())
    }

    @Test fun nobodyQuotingIsAnObservationAndATooManyRequestsAnswerPausesTheProbe() = runBlocking {
        val model = RfqMarketModel(); val rows = Collections.synchronizedList(ArrayList<RfqRow>()); var mode = 0; var calls = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val probe = RfqProbe(scope, model, { rows += it }, { true }, { calls++; if (mode == 0) NovigParlay.Result.NoPrice else NovigParlay.Result.Limited(30) }, gapMs = 5L, startDelayMs = 0L, workers = 1)
        probe.offer(item(1, "c")); delay(150)
        assertEquals(1, model.samples("c")); assertEquals(1.0, model.summary().single().noQuote, 0.0)
        mode = 1; probe.offer(item(2, "c")); delay(150)
        val after = calls; probe.offer(item(3, "c")); delay(150)
        scope.cancel()
        assertEquals("after a 429 it rests for the Retry-After", after, calls)
        assertEquals(1, model.samples("c"))                                              // the limited answer is no observation
    }

    @Test fun aDisabledProbeAsksNothing() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default); var calls = 0
        val probe = RfqProbe(scope, RfqMarketModel(), { }, { false }, { calls++; NovigParlay.Result.NoPrice }, gapMs = 5L, startDelayMs = 0L, workers = 1)
        probe.offer(item(1, "c")); delay(120); scope.cancel()
        assertEquals(0, calls); assertEquals(0, probe.queued())
    }
}
