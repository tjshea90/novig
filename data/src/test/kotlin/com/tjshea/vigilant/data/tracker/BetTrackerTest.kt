package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BetTrackerTest {

    @get:Rule val tmp = TemporaryFolder()
    private var now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0)

    private fun scan(dalBid: Int = 380, balBid: Int = 615, pinDal: Double = 2.45): ScanResult {
        val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
        val market = NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
            listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD")))
        val refs = mapOf("americanfootball_nfl" to RefSnapshot("americanfootball_nfl",
            TheOddsApiClient.parseEvents(Fixtures.oddsApi.replace("\"price\":2.45", "\"price\":$pinDal"), Json { ignoreUnknownKeys = true }), now))
        val plan = Planner.plan(listOf(event), listOf(market), refs, settings, now)
        val book = NovigBook(Fixtures.ML_MARKET, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(dalBid, 1000)), Fixtures.ML_BAL to listOf(BidLevel(balBid, 1000))), now)
        return Pricing.price(plan, mapOf(Fixtures.ML_MARKET to book), settings, now)
    }

    private fun dal(r: ScanResult) = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }

    @Test
    fun `tracks, settles and computes profit at Novig's price`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        val bet = t.track(dal(scan()), stake = 38.5)!!
        assertEquals(0.385, bet.cost, 1e-12)
        t.settle(bet.id, BetStatus.WON)
        val won = t.all().single()
        assertEquals(38.5 * (1 / 0.385 - 1), won.profit!!, 1e-9) // $61.50
        val s = BetTracker.stats(t.all())
        assertEquals(1, s.settled)
        assertEquals(won.profit!! / 38.5, s.roi!!, 1e-12)
    }

    @Test
    fun `closing fair keeps updating until kickoff, then freezes`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        t.track(dal(scan()), stake = 10.0)
        t.observe(scan(pinDal = 2.30)) // line moved toward DAL: we beat the close
        val clv1 = t.all().single().clvPercent!!
        now = Fixtures.START_MS + 1
        t.observe(scan(pinDal = 3.00)) // after kickoff: ignored
        assertEquals(clv1, t.all().single().clvPercent!!, 0.0)
        assertEquals(1.0, BetTracker.stats(t.all()).beatClosePercent!!, 0.0)
    }

    @Test
    fun `an opportunity with no price can't be tracked`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        val noPrice = dal(scan()).copy(quote = null)
        assertNull(t.track(noPrice, 5.0))
    }

    @Test
    fun `an unchanged scan doesn't rewrite the bets file`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        t.track(dal(scan()), stake = 10.0)
        assertEquals(true, t.observe(scan()))
        // Streaming re-prices every 2s: identical results must not touch the disk again.
        assertEquals(false, t.observe(scan()))
        assertEquals(true, t.observe(scan(pinDal = 2.30)))
    }

    @Test
    fun `a voided bet counts toward nothing but the bet count`() {
        fun bet(id: String, ev: Double, status: BetStatus, closing: Double?) = TrackedBet(
            id, 0, "NFL", "A @ B", Fixtures.START_MS, "Moneyline", "A", "m", "o",
            price = 0.5, cost = 0.5, fairAtBet = 0.5 * (1 + ev), evPercentAtBet = ev, stake = 10.0, status = status, closingFair = closing,
        )
        val bets = listOf(bet("1", 0.03, BetStatus.WON, 0.52), bet("2", 0.05, BetStatus.VOID, 0.40))
        val s = BetTracker.stats(bets)
        assertEquals(2, s.bets)
        assertEquals(0.03, s.averageEv!!, 1e-12)
        assertEquals(0.52 / 0.5 - 1, s.averageClv!!, 1e-12)
        assertEquals(1.0, s.beatClosePercent!!, 1e-12)
    }

    // ---- Tj, 2026-09-27: every ✓ is a bet in the Tracker -------------------------------------

    private val cnoRow = com.tjshea.vigilant.data.cno.CnoRow(
        0.05, 2_000_000L, "Football", "NFL", "Houston Texans @ Indianapolis Colts", "Player Receptions", "Dalton Schultz Over 5.5",
        141, 60.0, "Novig", 120, 0.4375, 8, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=9", betUrl = "https://crazyninjaodds.com/d?l=9",
    )

    @Test
    fun `a CNO check logs a $1 bet for good, a second check replaces it, Undo removes it`() = kotlinx.coroutines.test.runTest {
        val t = BetTracker(File(tmp.root, "t.json"), clock = { 1_000_000L })
        val bet = t.logCno(cnoRow, ev = 0.05, live = false, placedKey = "cno:k")
        assertEquals(1.0, bet.stake, 0.0)
        assertEquals(BetTracker.SOURCE_CNO, bet.source)
        assertEquals(141, bet.american)
        assertEquals(1 / 2.41, bet.cost, 1e-9) // pregame: no fee
        assertEquals(0.4375, bet.fairAtBet!!, 1e-9)
        assertEquals(cnoRow.gameUrl, bet.gameUrl)
        t.logCno(cnoRow, ev = 0.05, live = false, placedKey = "cno:k")
        assertEquals(1, t.all().size)
        t.untrack("cno:k")
        assertTrue(t.all().isEmpty())
        // A settled bet stays whatever happens to its mark.
        val kept = t.logCno(cnoRow, 0.05, false, "cno:k")
        t.settle(kept.id, BetStatus.WON)
        t.untrack("cno:k")
        assertEquals(1, t.all().size)
    }

    @Test
    fun `old checks still in placed json move into the Tracker once, removed ones don't`() = kotlinx.coroutines.test.runTest {
        val t = BetTracker(File(tmp.root, "t2.json"), clock = { 1_000_000L })
        val marks = listOf(
            PlacedBet("cno:a", "Dalton Schultz Over 5.5", "Player Receptions · Houston Texans @ Indianapolis Colts", odds = "+141", placedAtMs = 500L, startsAtMs = 900L),
            PlacedBet("cno:b", "Ohio -33.5", "Point Spread · Stonehill @ Ohio", odds = "−108", placedAtMs = 600L),
            PlacedBet("cno:c", "Removed Over 1.5", "x · y", odds = "+100", placedAtMs = 700L, hidden = true),
        )
        assertEquals(2, t.importPlaced(marks))
        assertEquals(0, t.importPlaced(marks)) // once
        val a = t.all().first { it.placedKey == "cno:a" }
        assertEquals("Player Receptions", a.marketLabel)
        assertEquals("Houston Texans @ Indianapolis Colts", a.eventName)
        assertEquals(141, a.american)
        assertEquals(null, a.evPercentAtBet)
        assertTrue(a.imported)
        assertEquals(-108, t.all().first { it.placedKey == "cno:b" }.american)
        // Stats leave unknown EVs out rather than count them as 0.
        assertEquals(null, BetTracker.stats(t.all()).averageEv)
    }

    @Test
    fun `a Novig fair-market settlement pays its value, and tracker files from before read as they were`() {
        val b = TrackedBet("x", 0, "NFL", "A @ B", 0, "M", "S", "m", "o", 0.5, 0.5, 0.5, 0.0, 10.0, BetStatus.FMV, settleValue = 0.6)
        assertEquals(10.0 * (0.6 / 0.5 - 1), b.profit!!, 1e-9)
        val old = kotlinx.serialization.json.Json.decodeFromString(TrackedBet.serializer(),
            """{"id":"1","createdAtMs":1,"league":"NFL","eventName":"A @ B","startsTs":2,"marketLabel":"M","selection":"S","marketId":"m","outcomeId":"o","price":0.5,"cost":0.5,"fairAtBet":0.52,"evPercentAtBet":0.04,"stake":5.0}""")
        assertEquals(BetTracker.SOURCE_VIGILANT, old.source)
        assertEquals(0.04, old.evPercentAtBet!!, 1e-9)
    }

    /** Tj, 2026-09-27: "do not count any bets that are outliers (currently + or - over 6% ev) … Ignore them completely." */
    @Test
    fun `bets over 6% EV either way are left out of every stat`() {
        fun b(id: String, ev: Double?, st: BetStatus, source: String = BetTracker.SOURCE_CNO) = TrackedBet(
            id, 0, "NFL", "A @ B", 0, "Moneyline", "A", "m", "o", 0.5, 0.5, ev?.let { 0.5 * (1 + it) }, ev, 1.0, st,
            closingFair = 0.51, source = source,
        )
        val normal = listOf(b("1", 0.03, BetStatus.WON), b("2", 0.05, BetStatus.LOST), b("3", 0.02, BetStatus.PENDING), b("4", null, BetStatus.WON))
        val outliers = listOf(
            b("big win", 0.25, BetStatus.WON), b("big loss", 0.061, BetStatus.LOST),
            b("negative", -0.07, BetStatus.WON), b("open", 0.40, BetStatus.PENDING, BetTracker.SOURCE_VIGILANT),
        )
        val s = BetTracker.stats(normal + outliers)
        assertEquals(BetTracker.stats(normal).copy(outliers = 4), s)
        assertEquals(4, s.bets)
        assertEquals(2, s.won) // "4" (no EV on record) counts; the 25% and −7% wins don't
        assertEquals(1, s.lost)
        assertEquals(1, s.pending)
        assertEquals(1.0, s.profit, 1e-12)
        assertEquals(3.0, s.staked, 1e-12)
        assertEquals((0.03 + 0.05 + 0.02) / 3, s.averageEv!!, 1e-12)
        // Exactly 6% either way is not "over": it counts.
        assertEquals(0, BetTracker.stats(listOf(b("a", 0.06, BetStatus.WON), b("b", -0.06, BetStatus.LOST))).outliers)
        assertTrue(b("c", 0.0601, BetStatus.WON).isOutlier)
        assertTrue(!b("d", null, BetStatus.WON).isOutlier)
    }

    @Test
    fun `stats count wins and losses (pushes aside) for the win rate`() {
        fun b(id: String, st: BetStatus) = TrackedBet(id, 0, "NFL", "A @ B", 0, "Moneyline", "A", "m", "o", 0.5, 0.5, null, null, 1.0, st)
        val s = BetTracker.stats(listOf(b("1", BetStatus.WON), b("2", BetStatus.WON), b("3", BetStatus.LOST), b("4", BetStatus.PUSH), b("5", BetStatus.PENDING)))
        assertEquals(2, s.won)
        assertEquals(1, s.lost)
        assertEquals(1, s.pushed)
        assertEquals(2.0 / 3.0, s.winRate!!, 1e-12)
        assertEquals(1.0, s.profit, 1e-12)
        assertNull(BetTracker.stats(emptyList()).winRate)
    }
}
