package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.ProfitProfile
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UW2 (Tj 2026-11-21: "Turn on the Auto bid engine for profitable bid types"): the engine's OWN settled bids (trades.json, 1,698 of them) lost 36 dollars, all of it in one type: NCAAB game-total OVER bids
 * (208 settled, ROI -32.4%, lost on 9 of 11 days). Those are not posted while "Profit-proven only" is on; the Unders (break-even), other leagues and other kinds are untouched, and off changes nothing.
 */
class EngineBidLoserTest {
    private val now = 1_800_000_000_000L
    private val start = now + 3 * 3_600_000L

    private fun line(selection: String, league: String = "NCAAB", kind: BetKind = BetKind.TOTAL, marketType: String = "TOTAL") = MakerLine(
        market = NovigMarket(
            marketId = "m-$league-$selection", eventId = "ev", marketType = marketType, status = "OPEN", description = marketType, startsTs = start, fee = MarketFee.GAME,
            outcomes = listOf(NovigOutcome("o-a", selection, "TBD"), NovigOutcome("o-b", "other", "TBD")),
        ),
        outcomeId = "o-a", startsTs = start, league = league, eventName = "A @ B", marketLabel = marketType, selection = selection, kind = kind,
        fair = 0.52, fairAsOfMs = now - 30_000, fairOld = false, books = 6, fairBooks = listOf("Pinnacle", "DraftKings", "FanDuel", "BetMGM", "Caesars", "Circa"), bookFairs = List(6) { 0.52 }, sharpFairs = listOf(0.52),
        offer = 0.60, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    private fun rules(profitProven: Boolean) = MakerRules.of(
        ScanSettings(maker = true, leagues = setOf("NCAAB", "NBA"), makerKinds = BetKind.entries.toSet(), makerFocus = BidFocus.ALL, makerSharpVeto = false, gates = ApiGates(profitProven = profitProven)),
    )

    @Test fun anNcaabGameTotalOverBidIsNotPostedWhileProfitProvenOnlyIsOnAndTheUnderIs() {
        val r = rules(true)
        val over = MakerQuote.decide(line("Over 141.5"), r, now)
        assertTrue(over.toString(), over is MakerDecision.Skip)
        assertTrue((over as MakerDecision.Skip).why, over.why.contains("the engine's own results") && over.why.contains("NCAAB game-total Over bids") && over.why.contains("-32.4%"))
        assertTrue("the Under of the same market is break-even and stays", MakerQuote.decide(line("Under 141.5"), r, now) is MakerDecision.Post)
        assertTrue("the same Over in another league is untouched", MakerQuote.decide(line("Over 221.5", league = "NBA"), r, now) is MakerDecision.Post)
    }

    @Test fun withProfitProvenOnlyOffNothingChanges() {
        assertTrue(MakerQuote.decide(line("Over 141.5"), rules(false), now) is MakerDecision.Post)
    }

    @Test fun theBlockListKeysOnLeagueKindAndSideAndNothingElse() {
        assertNotNull(ProfitProfile.engineBidLoser("NCAAB", "Game totals", "Over 141.5"))
        assertNull(ProfitProfile.engineBidLoser("NCAAB", "Game totals", "Under 141.5"))
        assertNull(ProfitProfile.engineBidLoser("NCAAB", "Spreads", "Duke -3.5"))
        assertNull(ProfitProfile.engineBidLoser("NBA", "Game totals", "Over 221.5"))
        assertNull(ProfitProfile.engineBidLoser("NCAAB", "Team totals", "Duke Over 71.5"))
        assertEquals("one proven type so far", 1, ProfitProfile.ENGINE_BID_LOSERS.size)
    }
}
