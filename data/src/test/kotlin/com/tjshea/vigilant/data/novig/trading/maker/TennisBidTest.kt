package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.LongRunBids
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-11: "for the bids it should still put up bids that are positive EV for tennis. Right now it won't put up a single bid even though I think it is finding tennis odds". His Diagnostics: Quick & likely
 * narrows bids to props and team totals and to lines five books price; tennis needs 6 books (Tj, 2026-10-31) with Pinnacle among them. With the Tennis switch on, tennis lines are judged by the plain rules.
 */
class TennisBidTest {
    private val now = 1_800_000_000_000L
    private val start = now + 3 * 3_600_000L

    private fun line(league: String, kind: BetKind, marketType: String, books: Int = 1, fair: Double = 0.52, offer: Double = 0.60) = MakerLine(
        market = NovigMarket(
            marketId = "m-$league", eventId = "ev", marketType = marketType, status = "OPEN", description = marketType, startsTs = start, fee = MarketFee.GAME,
            outcomes = listOf(NovigOutcome("o-a", "Player A", "TBD"), NovigOutcome("o-b", "Player B", "TBD")),
        ),
        outcomeId = "o-a", startsTs = start, league = league, eventName = "A @ B", marketLabel = marketType, selection = "Player A", kind = kind,
        fair = fair, fairAsOfMs = now - 30_000, fairOld = false, books = books, fairBooks = listOf("Pinnacle"), bookFairs = List(6) { fair }, sharpFairs = listOf(fair),
        offer = offer, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    private fun quick(tennis: Boolean) = ScanSettings(maker = true, makerFocus = BidFocus.QUICK_LIKELY, leagues = if (tennis) setOf("NFL", "ATP", "WTA") else setOf("NFL"), makerSharpVeto = false)

    @Test fun withTennisOnAQuickAndLikelyTennisMoneylineWithPinnacleAloneGetsABid() {
        val r = MakerRules.of(quick(true))
        val d = MakerQuote.decide(line("ATP", BetKind.MONEYLINE, "MONEY"), r, now)
        assertTrue(d.toString(), d is MakerDecision.Post)
        // the same line in an NFL game is still not a Quick & likely kind
        assertTrue(MakerQuote.decide(line("NFL", BetKind.MONEYLINE, "MONEY", books = 6), r, now) is MakerDecision.Skip)
    }

    @Test fun tennisSpreadsAndTotalsAreBidToo() {
        val r = MakerRules.of(quick(true))
        for ((k, t) in listOf(BetKind.SPREAD to "SPREAD", BetKind.TOTAL to "TOTAL")) assertTrue(k.name, MakerQuote.decide(line("WTA", k, t), r, now) is MakerDecision.Post)
    }

    @Test fun withTennisOffNothingChangesAndATennisLineStaysSkipped() {
        val r = MakerRules.of(quick(false))
        assertEquals(null, r.tennis)
        assertTrue(MakerQuote.decide(line("ATP", BetKind.MONEYLINE, "MONEY"), r, now) is MakerDecision.Skip)
    }

    @Test fun tennisStillNeedsASharpBookPricingBothSidesAndItsMarginAndPriceWindow() {
        val r = MakerRules.of(quick(true))
        val noSharp = line("ATP", BetKind.MONEYLINE, "MONEY").copy(sharpFairs = emptyList())
        assertTrue((MakerQuote.decide(noSharp, r, now) as MakerDecision.Skip).why.contains("sharp", true))
        // no edge: a fair of 0.52 with Novig offering 0.50 is already under it
        assertTrue(MakerQuote.decide(line("ATP", BetKind.MONEYLINE, "MONEY", offer = 0.50), r, now) is MakerDecision.Skip)
        val p = MakerQuote.decide(line("ATP", BetKind.MONEYLINE, "MONEY"), r, now) as MakerDecision.Post
        assertTrue("the bid is under the fair by the margin", p.price < 0.52 / (1.0 + r.tennis!!.margin) + 1e-9)
    }

    @Test fun theLeanBackgroundScanKeepsTheGameLineBoardsWhileTennisIsOn() {
        val on = quick(true).copy(makerLongRun = true)
        assertTrue(MarketFamily.MONEYLINE in LongRunBids.keep(on) && MarketFamily.SPREAD in LongRunBids.keep(on) && MarketFamily.TOTAL in LongRunBids.keep(on))
        assertTrue(LongRunBids.leanApplies(on))
        val off = quick(false).copy(makerLongRun = true)
        assertFalse(MarketFamily.MONEYLINE in LongRunBids.keep(off))
    }
}
