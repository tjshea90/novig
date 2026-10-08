package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.BidSource
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Switching on bids priced from CrazyNinjaOdds (Tj, 2026-10-07; RESEARCH.md §114) turns on what they need - not Vigilant's scan - and a side held by a bet by hand stays held. */
class CnoBidSetupTest {

    private val cno = ScanSettings(makerSource = BidSource.CNO, scanner = ScannerMode.VIGILANT, autoScan = AutoScanMode.OFF, autoScanSeconds = 600, pausedByHand = true)

    @Test
    fun `bids from CNO turn on CNO's scanner and CNO's background scan, never Vigilant's`() {
        val c = MakerSetup.set(cno, BidMode.AUTOMATIC)
        assertEquals(ScannerMode.CNO, c.settings.scanner)
        assertEquals(AutoScanMode.CNO, c.settings.autoScan)
        assertEquals(MakerSetup.MAX_INTERVAL_SECONDS, c.settings.autoScanSeconds)
        assertFalse(c.settings.paused)
        assertTrue(c.settings.maker)
        assertFalse(c.settings.autoScansVigilant)
        assertTrue(c.settings.bidsFromCno)
        assertTrue(c.turnedOn.any { it.contains("CNO only") })
        assertTrue(c.turnedOn.any { it.contains("background scan on CrazyNinjaOdds") })
        // Nothing here starts the auto-bet.
        assertFalse(c.startsAutoBet)
    }

    @Test
    fun `Tj's own choices are kept - both scanners stay, 15 seconds stays, a CNO + Vigilant scan stays`() {
        val mine = ScanSettings(makerSource = BidSource.CNO, scanner = ScannerMode.BOTH, autoScan = AutoScanMode.BOTH, autoScanSeconds = 15)
        val c = MakerSetup.set(mine, BidMode.RECOMMEND)
        assertEquals(ScannerMode.BOTH, c.settings.scanner)
        assertEquals(AutoScanMode.BOTH, c.settings.autoScan)
        assertEquals(15, c.settings.autoScanSeconds)
        assertTrue(c.turnedOn.isEmpty())
        // Vigilant's way is what it was: Vigilant's scanner is turned ON for it.
        val vig = MakerSetup.set(ScanSettings(scanner = ScannerMode.CNO, autoScan = AutoScanMode.CNO), BidMode.AUTOMATIC)
        assertEquals(ScannerMode.BOTH, vig.settings.scanner)
        assertEquals(AutoScanMode.BOTH, vig.settings.autoScan)
    }

    @Test
    fun `what still blocks - bids from CNO need no Vigilant league`() {
        // Vigilant's scan needs a league; CNO's scope is the CNO scanner's.
        assertNull(MakerSetup.missing(cno.copy(leagues = emptySet()), bettingSetUp = true))
        assertTrue(MakerSetup.missing(ScanSettings(leagues = emptySet()), bettingSetUp = true)!!.contains("league"))
    }

    // ---- a side held by a bet by hand -----------------------------------------------------------------------------------------

    private fun bet(id: String, outcome: String, bid: Boolean, status: BetStatus = BetStatus.PENDING) = TrackedBet(
        id = "$id-0000-0000", createdAtMs = 1L, league = "NFL", eventName = "A @ B", startsTs = 2L, marketLabel = "Player Receptions", selection = "Joe Over 4.5", marketId = "m",
        outcomeId = outcome, price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 1.0, status = status, source = BetTracker.SOURCE_VIGILANT, american = 100,
        atBet = if (bid) AtBet(atMs = 1L, how = AtBet.HOW_BID, scanner = BetTracker.SOURCE_VIGILANT, league = "NFL", kind = "PROP", minutesToStart = 60, american = 100, ev = 0.04) else null,
        maker = bid,
    )

    @Test
    fun `a bet by hand holds its side even with a bid resting there - a bid's own fill holds it only once the bid is over`() {
        val bets = listOf(bet("a", "o1", bid = false), bet("b", "o2", bid = true), bet("c", "o3", bid = true), bet("d", "o4", bid = false, status = BetStatus.WON))
        val bidsUpOn = setOf("o1", "o2")
        // o1: a taker bet with a bid up: held (the bid comes down). o2: the bid's own part-fill with the bid up: not held (the bid stays). o3: a bid that filled and is over: held. o4: settled.
        assertEquals(setOf("o1", "o3"), MakerDesk.held(bets, bidsUpOn))
    }
}
