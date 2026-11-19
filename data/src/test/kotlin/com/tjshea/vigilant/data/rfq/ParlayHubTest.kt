package com.tjshea.vigilant.data.rfq

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MK15 (Tj 2026-11-19: "a parlay hub ... legs already won ... when each parlay is expected to be final ... expected return if the remaining open parlays are lost by the buyer"). */
class ParlayHubTest {
    private val utc = ZoneId.of("UTC")
    private val h = 3_600_000L
    // Thursday 2026-11-19 20:00 UTC
    private val now = 1_795_118_400_000L

    private fun leg(id: String, label: String, startsInH: Double, p: Double, result: String? = null, league: String = "NBA", desc: String = "A @ B") =
        BookLeg(outcomeId = id, label = label, desc = desc, league = league, type = "MONEY", startsMs = now + (startsInH * h).toLong(), p = p, result = result)

    /** A $10 stake at a 0.50 price: our collateral is 10 x 0.5 / 0.5 = $10. */
    private fun parlay(id: String, legs: List<BookLeg>, fair: Double? = 0.4, wager: Double = 10.0, price: Double = 0.5, settled: Boolean = false, result: String? = null, settledAgoH: Double = 1.0) =
        BookParlay(id, filledMs = now - 5 * h, price = price, wager = wager, collateral = wager * (1 - price) / price, fair = fair, settled = settled, result = result, settledMs = if (settled) now - (settledAgoH * h).toLong() else null, nLegs = legs.size, legs = legs)

    private val book = ParlayBook(
        atMs = now, mode = "SHADOW",
        parlays = listOf(
            // A: three legs, the first already won (our odds 0.8), two to play: the buyer's chance is 0.4 / 0.8 = 0.5; expected = 20 x 0.5 - 10 = 0
            parlay("A", listOf(leg("a1", "Moneyline Houston", -1.0, 0.8, "WIN"), leg("a2", "Total Over 44.5", 2.0, 0.6), leg("a3", "Moneyline Spurs", 5.0, 0.83, league = "NFL"))),
            // B: the buyer already lost a leg: the stake is ours once Novig settles it
            parlay("B", listOf(leg("b1", "Spread Suns +5.5", -3.0, 0.5, "LOSS"), leg("b2", "Moneyline Heat", 1.0, 0.7)), fair = 0.35),
            // C: settled, the buyer lost: we kept $10.  D: settled, the buyer won: we paid the $10 collateral.  E: voided
            parlay("C", listOf(leg("c1", "x", -9.0, 0.5, "LOSS"), leg("c2", "y", -9.0, 0.5, "WIN")), settled = true, result = "loss", settledAgoH = 2.0),
            parlay("D", listOf(leg("d1", "x", -30.0, 0.5, "WIN"), leg("d2", "y", -30.0, 0.5, "WIN")), settled = true, result = "win", settledAgoH = 30.0),
            parlay("E", listOf(leg("e1", "x", -30.0, 0.5, "PUSH"), leg("e2", "y", -30.0, 0.5, "WIN")), settled = true, result = "push", settledAgoH = 40.0),
        ),
    )

    private val hub = ParlayHub.of(book, null, now, utc)

    @Test fun theMoneyIsFromTheMakersSide() {
        val s = hub.summary
        assertEquals("C kept 10, D paid 10, E void: 0", 0.0, s.realizedPnl, 1e-9)
        assertEquals("only C settled inside 24 h", 10.0, s.pnl24h, 1e-9)
        assertEquals(3, s.settled); assertEquals(1, s.makerWon); assertEquals(1, s.makerLost)
        assertEquals(2, s.open)
        assertEquals("A and B each hold $10", 20.0, s.collateral, 1e-9)
        assertEquals("every open buyer loses: we keep both $10 stakes", 20.0, s.ifAllBuyersLose, 1e-9)
        assertEquals("only A can still be won by its buyer; B's buyer is dead", -10.0, s.worstCase, 1e-9)
        assertEquals(10.0, s.lockedIn, 1e-9); assertEquals(1, s.lockedInCount)
    }

    @Test fun legsAlreadyWonRaiseTheBuyersChanceAndLegsLostKillTheParlay() {
        val a = hub.open.single { it.rfqId == "A" }
        assertEquals(0.5, a.buyerChance!!, 1e-9)
        assertEquals("expected profit at 50%: (10+10) x 0.5 - 10", 0.0, a.expected!!, 1e-9)
        assertEquals(ParlayHub.Status.BUYER_AHEAD, a.status)
        assertEquals("1 of 3 legs won · 2 still to play", a.progress)
        val b = hub.open.single { it.rfqId == "B" }
        assertEquals(0.0, b.buyerChance!!, 1e-12); assertEquals(10.0, b.expected!!, 1e-9)
        assertEquals(ParlayHub.Status.BUYER_DEAD, b.status)
        assertTrue(b.statusText, b.statusText.contains("keep their \$10.00"))
        assertEquals("expected on the open ones: A 0 + B 10", 10.0, hub.summary.expectedOpen, 1e-9)
        assertEquals("realized 0 + expected 10", 10.0, hub.summary.expectedTotal, 1e-9)
    }

    @Test fun legsAreUpcomingInPlayOrDecidedAndTheFinishTimeIsTheLastLegsStartPlusTheGame() {
        val a = hub.open.single { it.rfqId == "A" }
        assertEquals(listOf(ParlayHub.LegState.WON, ParlayHub.LegState.UPCOMING, ParlayHub.LegState.UPCOMING), a.legs.map { it.state })
        // the last leg is the NFL game 5 h from now: 3.6 h long
        assertEquals(now + (5 * h) + (3.6 * h).toLong(), a.expectedFinalMs)
        assertTrue(a.lastLegText, a.lastLegText.startsWith("Last leg: Moneyline Spurs, starts "))
        assertTrue(a.finalText, a.finalText.startsWith("Final about ") && a.finalText.contains("(in 8 h 36 min)"))
        val b = hub.open.single { it.rfqId == "B" }
        assertEquals("a started leg with no result is in play", ParlayHub.LegState.UPCOMING, b.legs[1].state)
        val inPlay = ParlayHub.legViews(parlay("X", listOf(leg("x", "t", -1.0, 0.5))), now, utc).single()
        assertEquals(ParlayHub.LegState.IN_PLAY, inPlay.state); assertTrue(inPlay.stateText, inPlay.stateText.startsWith("In play since "))
        val over = ParlayHub.legViews(parlay("X", listOf(leg("x", "t", -4.0, 0.5))), now, utc).single()
        assertEquals("a game that must be over waits for Novig", "Game over, waiting for Novig's result", over.stateText)
        assertEquals("the hub sorts the soonest finish first, and the summary names the last", hub.open.map { it.rfqId }, listOf("B", "A"))
        assertEquals(a.expectedFinalMs, hub.summary.lastFinalMs)
    }

    @Test fun closedParlaysSayWhoWonAndByHowMuch() {
        val c = hub.closed.single { it.rfqId == "C" }
        assertEquals(10.0, c.pnl, 1e-9); assertEquals(true, c.won); assertTrue(c.result, c.result.startsWith("The buyer lost: we kept \$10.00"))
        val d = hub.closed.single { it.rfqId == "D" }
        assertEquals(-10.0, d.pnl, 1e-9); assertEquals(false, d.won); assertTrue(d.result, d.result.startsWith("The buyer won: we paid \$10.00"))
        val e = hub.closed.single { it.rfqId == "E" }
        assertEquals(0.0, e.pnl, 1e-9); assertNull(e.won)
        assertEquals("newest settled first", listOf("C", "D", "E"), hub.closed.map { it.rfqId })
        assertEquals("a fair-market-value void pays the buyer its value", 10.0 * 2 * (1 - 0.25) - 10.0, ParlayHub.settledPnl(parlay("F", emptyList(), settled = true, result = "fmv").copy(fmv = 0.25))!!, 1e-9)
    }

    @Test fun anEmptyOrMissingBookSaysSoAndNothingElse() {
        val none = ParlayHub.of(null, null, now, utc)
        assertTrue(none.empty); assertTrue(none.note.startsWith("Waiting for the engine"))
        val empty = ParlayHub.of(ParlayBook(), null, now, utc)
        assertTrue(empty.empty); assertTrue(empty.note.startsWith("No parlay has been filled yet"))
        assertEquals("No parlays yet", ParlayHub.headline(empty.summary))
        assertEquals("+\$0.00 so far on 3 settled · 2 open".replace("+", ""), ParlayHub.headline(hub.summary).replace("+", "").replace("-", ""))
    }

    @Test fun timesReadInPlainWords() {
        assertEquals("Today 9:40 PM", ParlayHub.clock(now + (1.667 * h).toLong(), now, utc))
        assertEquals("Tomorrow 1:00 AM", ParlayHub.clock(now + 5 * h, now, utc))
        assertEquals("Sat 8:00 PM", ParlayHub.clock(now + 48 * h, now, utc))
        assertEquals("Nov 28, 8:00 PM", ParlayHub.clock(now + 9 * 24 * h, now, utc))
        assertEquals("in 5 h 20 min", ParlayHub.relative(now + 5 * h + 20 * 60_000L, now))
        assertEquals("in 40 min", ParlayHub.relative(now + 40 * 60_000L, now))
        assertEquals("3 h ago", ParlayHub.relative(now - 3 * h, now))
        assertEquals("now", ParlayHub.relative(now + 10_000L, now))
    }

    // ---- the engine side: the book from the ledger, the fills' legs and Novig's grades ----
    private fun exec(id: String, settled: Boolean = false, result: String? = null, ids: List<String>) =
        RfqLedger.Exec(rfqId = id, price = 0.5, cost = 10.0, userCost = 10.0, settled = settled, result = result, fmv = null, ts = now - 5 * h, settledTs = if (settled) now - h else null, outcomeIds = ids)

    private fun fill(id: String, vararg legs: FillLeg) = FillRecord(id, now - 5 * h, 0.5, 0.4, 10.0, legs.toList())
    private fun fl(id: String, market: String, startsInH: Double, p: Double = 0.6) = FillLeg(id, market, "Leg $id", "A @ B", "NBA", "MONEY", now + (startsInH * h).toLong(), p)

    @Test fun theBookJoinsTheLedgerToTheLegsAndKeepsResultsOnceSeen() {
        val fills = listOf(fill("r1", fl("o1", "m1", -2.0), fl("o2", "m2", 3.0)), fill("r2", fl("o3", "m3", -9.0))).associateBy { it.rfqId }
        val execs = listOf(exec("r1", ids = listOf("o1", "o2")), exec("r2", settled = true, result = "loss", ids = listOf("o3")))
        val b1 = ParlayBook.build(execs, fills, { if (it == "o1") "WIN" else null }, null, now, "SHADOW")
        val r1 = b1.parlays.single { it.rfqId == "r1" }
        assertEquals(listOf("WIN", null), r1.legs.map { it.result }); assertEquals("Leg o1", r1.legs[0].label); assertEquals(0.4, r1.fair!!, 1e-12)
        assertTrue(b1.parlays.single { it.rfqId == "r2" }.settled)
        // Novig no longer lists the graded market: the result already seen is kept; a parlay the ledger page dropped stays as it was
        val b2 = ParlayBook.build(listOf(exec("r1", ids = listOf("o1", "o2"))), fills, { null }, b1, now + h, "SHADOW")
        assertEquals(listOf("WIN", null), b2.parlays.single { it.rfqId == "r1" }.legs.map { it.result })
        assertEquals(listOf("r1", "r2"), b2.parlays.map { it.rfqId }.sorted())
        // with no record of the legs the parlay still appears, its legs named by position
        val bare = ParlayBook.build(listOf(exec("r9", ids = listOf("x", "y"))), emptyMap(), { null }, null, now, "LIVE")
        assertEquals(listOf("Leg 1", "Leg 2"), bare.parlays.single().legs.map { it.label })
        assertEquals("a result known from outside Novig's catalog (the seed) is used", "LOSS", ParlayBook.build(listOf(exec("r1", ids = listOf("o1"))), mapOf("r1" to fill("r1", fl("o1", "m1", -1.0).copy(result = "LOSS"))), { null }, null, now, "SHADOW").parlays.single().legs.single().result)
    }

    @Test fun onlyStartedOpenLegsWithAMarketAreAskedAbout() {
        val fills = listOf(fill("r1", fl("o1", "m1", -2.0), fl("o2", "m2", 3.0), fl("o4", "", -2.0)), fill("r2", fl("o3", "m3", -9.0))).associateBy { it.rfqId }
        val execs = listOf(exec("r1", ids = listOf("o1", "o2", "o4")), exec("r2", settled = true, result = "loss", ids = listOf("o3")))
        val b = ParlayBook.build(execs, fills, { null }, null, now, "SHADOW")
        assertEquals("not the unstarted o2, not the market-less o4, not the settled parlay's o3", listOf("o1"), ParlayBook.legsToGrade(b, fills, now).map { it.outcomeId })
        val graded = ParlayBook.build(execs, fills, { if (it == "o1") "LOSS" else null }, b, now, "SHADOW")
        assertTrue("a leg already graded is not asked about again", ParlayBook.legsToGrade(graded, fills, now).isEmpty())
    }

    @Test fun novigsOutcomeStatusesAreLegResults() {
        assertEquals("WIN", ParlayBook.gradeOf("WIN")); assertEquals("LOSS", ParlayBook.gradeOf("loss")); assertEquals("PUSH", ParlayBook.gradeOf("PUSH"))
        assertEquals("a fair-market-value void is a push for the leg", "PUSH", ParlayBook.gradeOf("0.4350"))
        assertNull(ParlayBook.gradeOf("TBD")); assertNull(ParlayBook.gradeOf(null)); assertNull(ParlayBook.gradeOf(""))
    }

    @Test fun theFilesRoundTripAndAnUnreadableOneIsEmpty() {
        val round = ParlayBook.parse(ParlayBook.encode(book))
        assertEquals(book, round)
        assertNull(ParlayBook.parse("not json")); assertNull(ParlayBook.parse(null))
        val f = listOf(fill("r1", fl("o1", "m1", 1.0)))
        assertEquals(f, FillBook.decode(FillBook.encode(f))); assertTrue(FillBook.decode("garbage").isEmpty())
        // a record with legs is never replaced by one without; the older record of two with legs is kept
        val merged = FillBook.merge(f, listOf(fill("r1"), fill("r2", fl("o9", "m9", 1.0))))
        assertEquals(listOf("r1", "r2"), merged.map { it.rfqId }); assertEquals(1, merged[0].legs.size)
    }

    @Test fun theSeedOfTheParlaysFilledBeforeTheEngineKeptTheirLegsLoads() {
        val seed = FillBook.decode(FillBook::class.java.getResourceAsStream("/rfq_fills_seed.json")?.bufferedReader()?.readText())
        assertTrue("16 fills of 11-18/19 with their legs", seed.size >= 16)
        assertTrue(seed.all { it.legs.size in 2..4 && it.price in 0.3..0.9 && it.fair > 0.0 && it.wager > 0.0 })
        assertNotNull(seed.first().legs.first().desc.takeIf { it.contains(" @ ") })
        assertTrue("some started legs carry a result graded from final scores", seed.any { r -> r.legs.any { it.result != null } })
    }
}
