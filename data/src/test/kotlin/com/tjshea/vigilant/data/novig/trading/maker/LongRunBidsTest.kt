package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.BidSource
import com.tjshea.vigilant.data.scanner.LongRunBids
import com.tjshea.vigilant.data.scanner.LowUsageBids
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.GameRef
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The long-run saver (Tj, 2026-10-08: "leave the app on and background auto bid for hours … worried about api usage running out too fast … without sacrificing accuracy or
 * quality of bids"; RESEARCH.md §119): the lean background scan, the slow pace while every game is far off, and the order that puts far games' bids behind nearer ones.
 */
class LongRunBidsTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private val quick = ScanSettings(maker = true, makerFocus = BidFocus.QUICK_LIKELY)

    // ---- the lean background scan ---------------------------------------------------------------------------------------------

    @Test
    fun `quick and likely bids keep only the families a bid can go on - props and team totals - whatever else Tj has on`() {
        assertEquals(setOf(MarketFamily.PLAYER_PROPS, MarketFamily.TEAM_TOTAL), LongRunBids.keep(quick))
        // Tj's own families still bound it: no team totals read when he reads none.
        assertEquals(setOf(MarketFamily.PLAYER_PROPS), LongRunBids.keep(quick.copy(families = setOf(MarketFamily.PLAYER_PROPS, MarketFamily.MONEYLINE))))
        // And his kinds: a team-total bid he switched off is not a reason to read the board.
        assertEquals(setOf(MarketFamily.PLAYER_PROPS), LongRunBids.keep(quick.copy(makerKinds = setOf(BetKind.PROP, BetKind.MONEYLINE))))
    }

    @Test
    fun `the lean scan applies only to Quick and likely bids priced by Vigilant's own scan with the saver on`() {
        assertTrue(LongRunBids.leanApplies(quick))
        assertTrue(LongRunBids.leanApplies(quick.copy(maker = false, makerRecommend = true)))
        assertFalse("switch off", LongRunBids.leanApplies(quick.copy(makerLongRun = false)))
        assertFalse("bids off", LongRunBids.leanApplies(quick.copy(maker = false, makerRecommend = false)))
        assertFalse("All bids bid on game lines too", LongRunBids.leanApplies(quick.copy(makerFocus = BidFocus.ALL)))
        assertFalse("low usage has its own scan", LongRunBids.leanApplies(quick.copy(makerFocus = BidFocus.LOW_USAGE)))
        assertFalse("CNO prices them", LongRunBids.leanApplies(quick.copy(makerSource = BidSource.CNO)))
        assertFalse("nothing to narrow to", LongRunBids.leanApplies(quick.copy(makerKinds = setOf(BetKind.MONEYLINE, BetKind.SPREAD))))
    }

    @Test
    fun `the lean profile drops the game-line boards and the window follows what a bid could be posted on`() {
        val lean = quick.copy(leanScan = true, trapEarlyHours = 12, startsWithinHours = 24)
        val read = lean.effective()
        assertEquals(setOf(MarketFamily.PLAYER_PROPS, MarketFamily.TEAM_TOTAL), read.families)
        // The feeds buy a game-line board only for these families (TheOddsApiClient.marketsFor), so none is bought.
        assertTrue(com.tjshea.vigilant.data.reference.TheOddsApiClient.marketsFor(read.families, com.tjshea.vigilant.data.reference.OddsFeed.PARLAY).isEmpty())
        assertEquals("the trap guard's hours, never past Tj's reach", 12, read.scanWindowHours)
        // Nothing else of Tj's is touched: the fair, the books, the leagues.
        assertEquals(lean.fairSource, read.fairSource)
        assertEquals(lean.sharpBooks, read.sharpBooks)
        assertEquals(lean.leagues, read.leagues)
    }

    @Test
    fun `Tj's own scan and a bets-only pass are never narrowed`() {
        // The flag is set only by the background scan: without it the settings are read as they are ...
        assertEquals(MarketFamily.entries.toSet(), quick.effective().families)
        // ... and a bets-only pass (Check odds now) ignores it, as low usage's does.
        assertEquals(MarketFamily.entries.toSet(), quick.copy(leanScan = true).effective(forBets = true).families)
    }

    @Test
    fun `the flag is never saved - a settings file cannot switch the lean scan on`() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val saved = json.encodeToString(ScanSettings.serializer(), quick.copy(leanScan = true))
        assertFalse(saved.contains("leanScan"))
        assertFalse(json.decodeFromString(ScanSettings.serializer(), saved).leanScan)
        // An old file with no switch of this kind reads as on: the saver is the default.
        assertTrue(json.decodeFromString(ScanSettings.serializer(), "{}").makerLongRun)
    }

    // ---- the pace -----------------------------------------------------------------------------------------------------------

    private fun gap(s: ScanSettings, vararg startsInHours: Double) = LongRunBids.gapSeconds(s, startsInHours.map { now + (it * hour).toLong() }, now)

    @Test
    fun `while every game is over 3 hours away the scan waits 8 minutes, otherwise the usual 4`() {
        assertEquals(ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS, gap(quick, 2.0, 9.0))
        assertEquals(LowUsageBids.FAR_GAP_SECONDS, gap(quick, 9.0, 20.0))
        assertEquals("no game at all", LowUsageBids.FAR_GAP_SECONDS, gap(quick))
        // A game just past 3 h is already near by one far gap's reach, as in low usage.
        assertEquals(ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS, gap(quick, 3.1, 20.0))
        // A game inside the stop window (no bid goes up on it) does not keep the pace short.
        assertEquals(LowUsageBids.FAR_GAP_SECONDS, gap(quick, 0.1, 20.0))
    }

    @Test
    fun `the pace stays the usual one when nothing is known, when the saver is off, and in the modes that set their own`() {
        assertEquals(ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS, LongRunBids.gapSeconds(quick, null, now))
        assertEquals(ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS, gap(quick.copy(makerLongRun = false), 20.0))
        assertEquals("bids off", ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS, gap(quick.copy(maker = false, makerRecommend = false), 20.0))
        assertEquals("CNO prices the bids", ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS, gap(quick.copy(makerSource = BidSource.CNO), 20.0))
        // Low usage keeps its own pace ([ScanSettings.vigilantGapSeconds]); the saver adds nothing to it.
        val low = quick.copy(makerFocus = BidFocus.LOW_USAGE)
        assertEquals(low.vigilantGapSeconds, gap(low, 20.0))
    }

    @Test
    fun `the slow pace never gives a bid a quote older than the freshness rule allows`() {
        // 8 minutes plus the re-post window is the 10 minutes a far game's quote may be old (Freshness.FAR_OFF_AGE_MS): the gap is not longer than that rule less the window.
        assertEquals((com.tjshea.vigilant.data.scanner.Freshness.FAR_OFF_AGE_MS - MakerRules.REFRESH_BEFORE_MS) / 1_000L, LowUsageBids.FAR_GAP_SECONDS.toLong())
        assertTrue(gap(quick, 20.0) <= LowUsageBids.FAR_GAP_SECONDS)
    }

    // ---- far games' bids go up last, come down first ---------------------------------------------------------------------------

    private fun line(id: String, startsTs: Long) = MakerLine(
        market = NovigMarket(
            marketId = "m-$id", eventId = "ev-$id", marketType = "RECEIVING_YARDS", status = "OPEN", description = "RECEIVING_YARDS", startsTs = startsTs, fee = MarketFee.GAME,
            outcomes = listOf(NovigOutcome("$id-over", "Over 50.5", "TBD"), NovigOutcome("$id-under", "Under 50.5", "TBD")),
        ),
        outcomeId = "$id-over", startsTs = startsTs, league = "NFL", eventName = "A @ B", marketLabel = "RECEIVING_YARDS", selection = "Player Over 50.5", kind = BetKind.PROP,
        fair = 0.50, fairAsOfMs = now - 30_000, fairOld = false, books = 8, sharpFairs = listOf(0.50), offer = 0.60, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    private fun post(id: String, inHours: Double, price: Double = 0.48, ev: Double = 0.04) =
        MakerDecision.Post(line(id, now + (inHours * hour).toLong()), price, 1_000, ev)

    private val rules = MakerRules.of(quick).copy(maxBids = 10, maxDollars = 1_000.0)

    @Test
    fun `a bid on a game 12 hours or more out goes up after every nearer bid, whatever else ranks it`() {
        // The far bid is the likeliest fill and has the most edge; the near ones still go first.
        val far = post("far", 20.0, price = 0.40, ev = 0.09)
        val near = post("near", 2.0, price = 0.55, ev = 0.03)
        val mid = post("mid", 11.5, price = 0.50, ev = 0.03)
        val order = listOf(far, mid, near).sortedWith(MakerPlan.priority(rules, now)).map { it.line.outcomeId }
        assertEquals(listOf("near-over", "mid-over", "far-over"), order)
        // The same without Quick & likely (the plain popular order and the not-popular one).
        for (r in listOf(rules.copy(quick = false), rules.copy(quick = false, popularFirst = false))) {
            assertEquals(listOf("near-over", "mid-over", "far-over"), listOf(far, mid, near).sortedWith(MakerPlan.priority(r, now)).map { it.line.outcomeId })
        }
        assertEquals(0, MakerPlan.farOf(now + 11 * hour + 59 * 60_000L, now))
        assertEquals(1, MakerPlan.farOf(now + 12 * hour, now))
        assertEquals("an unknown start is near", 0, MakerPlan.farOf(null, now))
    }

    @Test
    fun `leading their side still comes before the game's distance`() {
        val farLeader = post("far", 20.0)
        val nearBehind = MakerDecision.Post(line("near", now + 2 * hour).copy(bestBid = 0.49), 0.48, 1_000, 0.04)
        assertEquals(listOf("far-over", "near-over"), listOf(nearBehind, farLeader).sortedWith(MakerPlan.priority(rules, now)).map { it.line.outcomeId })
    }

    @Test
    fun `when the wallet holds one bid the near game's gets the room, and a far bid up already gives way to it`() {
        val r = rules
        val far = post("far", 20.0, ev = 0.09)
        val near = post("near", 2.0, ev = 0.03)
        // Money for one 480-dollar... one bid (1,000 contracts at 0.48 = $480): the near one goes up.
        val one = MakerPlan.plan(listOf(far, near), emptyList(), r, now, budget = 480.0)
        assertEquals(listOf("near-over"), one.places.map { it.line.outcomeId })
        // Both rest, then the wallet falls short for one: the far one comes down.
        fun rest(id: String, inHours: Double) = RestingBid(
            "o-$id", "m-$id", "$id-over", 0.48, 1_000, 0, now + 20 * 60_000L, evAtFair = 0.04, leads = true,
            game = GameRef("ev-$id", "A @ B", now + (inHours * hour).toLong(), "NFL"),
        )
        val trim = MakerPlan.plan(listOf(far, near), listOf(rest("far", 20.0), rest("near", 2.0)), r, now, budget = -480.0)
        assertEquals(listOf("o-far"), trim.cancels.map { it.first.orderId })
        assertEquals(listOf("o-near"), trim.kept.map { it.orderId })
    }
}
