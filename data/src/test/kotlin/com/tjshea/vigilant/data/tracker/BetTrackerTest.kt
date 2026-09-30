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
        t.observe(scan(pinDal = 2.30)) // a day out, the line moved toward DAL
        // A read a day before the start isn't the close (ClosingLine): no CLV in the stats yet, even once the game has started.
        assertNull(BetTracker.stats(t.all(), Fixtures.START_MS + 1).beatClosePercent)
        now = Fixtures.START_MS - 5 * 60_000L
        t.observe(scan(pinDal = 2.30)) // 5 minutes before kickoff: the close, and we beat it
        val clv1 = t.all().single().clvPercent!!
        now = Fixtures.START_MS + 1
        t.observe(scan(pinDal = 3.00)) // after kickoff: ignored
        assertEquals(clv1, t.all().single().clvPercent!!, 0.0)
        assertEquals(1.0, BetTracker.stats(t.all(), now).beatClosePercent!!, 0.0)
        assertEquals(clv1, BetTracker.stats(t.all(), now).averageClv!!, 1e-12)
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
    fun `a scan that changes nothing still moves an open bet's age on once the last read is a minute old`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        t.track(dal(scan()), stake = 10.0)
        t.observe(scan())
        val first = t.all().single()
        assertEquals(BetTracker.VIA_VIGILANT, first.nowVia)
        assertEquals(now, first.nowAtMs)
        // Inside the minute: nothing to write. After it: the same fair line, read again, so the card's "now" is honest.
        now += 30_000
        assertEquals(false, t.observe(scan()))
        assertEquals(first.nowAtMs, t.all().single().nowAtMs)
        now += 31_000
        assertEquals(true, t.observe(scan()))
        assertEquals(now, t.all().single().nowAtMs)
        assertEquals(first.nowEv, t.all().single().nowEv)
    }

    @Test
    fun `a pricing pass changes only the open pregame bets it was asked about, and a reason never replaces a number`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        val a = t.track(dal(scan()), stake = 10.0)!!
        val b = t.track(dal(scan()), stake = 5.0)!!
        val c = t.track(dal(scan()), stake = 5.0)!!
        t.settle(c.id, BetStatus.WON)
        val result = scan(pinDal = 2.30)
        // b is left out of the ask; c is settled.
        val applied = t.applyPricing(result, listOf(a.id, c.id), mapOf(a.id to "nope", c.id to "nope"))
        assertEquals(BetTracker.Applied(1, 0), applied)
        val by = t.all().associateBy { it.id }
        assertEquals(dal(result).fairProbability!! / a.cost - 1.0, by.getValue(a.id).nowEv!!, 1e-12)
        assertNull(by.getValue(b.id).nowEv)
        assertNull(by.getValue(c.id).nowEv)
        assertNull(by.getValue(c.id).nowNote)
        // A pass that doesn't price it leaves the number and adds the reason; a later pricing clears the reason.
        val ev = by.getValue(a.id).nowEv
        assertEquals(BetTracker.Applied(0, 1), t.applyPricing(null, listOf(a.id), mapOf(a.id to "no fair-odds source lists this game")))
        assertEquals(ev, t.all().first { it.id == a.id }.nowEv)
        assertEquals("no fair-odds source lists this game", t.all().first { it.id == a.id }.nowNote)
        t.applyPricing(result, listOf(a.id))
        assertNull(t.all().first { it.id == a.id }.nowNote)
        // Once the game has started there's nothing to price.
        now = Fixtures.START_MS + 1
        assertEquals(BetTracker.Applied(0, 0), t.applyPricing(result, listOf(a.id)))
    }

    @Test
    fun `a voided bet counts toward nothing but the bet count`() {
        fun bet(id: String, ev: Double, status: BetStatus, closing: Double?) = TrackedBet(
            id, 0, "NFL", "A @ B", Fixtures.START_MS, "Moneyline", "A", "m", "o",
            price = 0.5, cost = 0.5, fairAtBet = 0.5 * (1 + ev), evPercentAtBet = ev, stake = 10.0, status = status, closingFair = closing,
            closingSeenAtMs = Fixtures.START_MS - 60_000L,
        )
        val bets = listOf(bet("1", 0.03, BetStatus.WON, 0.52), bet("2", 0.05, BetStatus.VOID, 0.40))
        val s = BetTracker.stats(bets, Fixtures.START_MS + 1)
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
        assertEquals(BetTracker.stats(normal).copy(outliers = 4, profitAll = s.profitAll), s)
        // The bankroll's real result counts the outliers too: +1 -1 +1 on top of the normal bets' +1.
        assertEquals(2.0, s.profitAll, 1e-12)
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

    // ---- Tj, 2026-09-29: "make sure that the bet tracking system is properly keeping track of accurate stats" ----

    private fun stakeBet(id: String, ev: Double?, st: BetStatus, stake: Double = 10.0, cost: Double = 0.5) = TrackedBet(
        id, 0, "NFL", "A @ B", 0, "Moneyline", "A", "m", "o", cost, cost, ev?.let { cost * (1 + it) }, ev, stake, st,
    )

    @Test
    fun `Expected and Profit are the same bets, open bets have their own numbers, voids and pushes stay out`() {
        val s = BetTracker.stats(
            listOf(
                stakeBet("w", 0.04, BetStatus.WON), stakeBet("l", 0.04, BetStatus.LOST),
                stakeBet("open", 0.05, BetStatus.PENDING, stake = 20.0),
                stakeBet("void", 0.05, BetStatus.VOID), stakeBet("push", 0.03, BetStatus.PUSH),
            ),
        )
        assertEquals(2, s.settledWithEv) // won and lost only: a push is refunded, a void never happened
        assertEquals(0.8, s.expectedProfit, 1e-12) // 10 x 4% x 2 bets, not the open bet's $1 or the push's
        assertEquals(0.0, s.profitWithEv, 1e-12)
        assertEquals(-0.8, s.vsExpected, 1e-12)
        assertEquals(20.0, s.openStaked, 1e-12)
        assertEquals(20.0, s.openToWin, 1e-12) // $20 at even money pays $20
        assertEquals(1.0, s.openExpected, 1e-12)
        assertEquals(1, s.voided)
        assertEquals(1, s.pending)
        assertEquals(1, s.pushed)
        assertEquals(30.0, s.staked, 1e-12) // won + lost + push (a refunded stake was still at risk)
        assertNull(s.luck) // two bets say nothing about luck
    }

    @Test
    fun `luck is how far the result sits from the promise in standard deviations`() {
        val bets = (1..20).map { stakeBet("b$it", 0.04, if (it % 2 == 0) BetStatus.WON else BetStatus.LOST) }
        val s = BetTracker.stats(bets)
        assertEquals(20, s.settledWithEv)
        assertEquals(8.0, s.expectedProfit, 1e-9)
        assertEquals(0.0, s.profitWithEv, 1e-9)
        // Each bet: stake^2 x p(1-p) / cost^2 = 100 x 0.52 x 0.48 / 0.25 = 99.84.
        assertEquals(Math.sqrt(20 * 99.84), s.expectedSd, 1e-9)
        assertEquals(-8.0 / Math.sqrt(20 * 99.84), s.luck!!, 1e-9)
        // A bet with no EV on record (imported) isn't part of "expected vs actual", but its profit is in Profit.
        val imported = BetTracker.stats(bets + stakeBet("imp", null, BetStatus.WON))
        assertEquals(20, imported.settledWithEv)
        assertEquals(10.0, imported.profit, 1e-9)
        assertEquals(0.0, imported.profitWithEv, 1e-9)
    }

    @Test
    fun `editMany changes several bets in one save and skips the ones that are gone`() = runTest {
        val t = BetTracker(File(tmp.root, "many.json"), clock = { now })
        val a = t.logCno(cnoRow, 0.05, false, "cno:a")
        val b = t.logCno(cnoRow.copy(bet = "Other Over 1.5", gameUrl = "https://crazyninjaodds.com/site/browse/game.aspx?side_id=10"), 0.05, false, "cno:b")
        t.editMany(mapOf(a.id to { x -> x.copy(stake = 5.0) }, b.id to { x -> x.copy(stake = 7.0) }, "gone" to { x -> x.copy(stake = 99.0) }))
        assertEquals(listOf(5.0, 7.0), t.all().map { it.stake })
        t.editMany(emptyMap())
        assertEquals(2, t.all().size)
    }

    @Test
    fun `correcting the price a bet was filled at moves its cost, EV and profit, keeping a live bet's fee`() = runTest {
        val t = BetTracker(File(tmp.root, "price.json"), clock = { now })
        val bet = t.logCno(cnoRow, 0.05, live = false, placedKey = "cno:p", stake = 10.0)
        t.setPrice(bet.id, 150)
        val moved = t.all().single()
        assertEquals(150, moved.american)
        assertEquals(0.4, moved.cost, 1e-9)
        assertEquals(moved.fairAtBet!! / 0.4 - 1.0, moved.evPercentAtBet!!, 1e-9)
        t.settle(moved.id, BetStatus.WON)
        assertEquals(10.0 * (1 / 0.4 - 1), t.all().single().profit!!, 1e-9)
        // Odds inside ±100 aren't a price.
        t.setPrice(moved.id, 50)
        assertEquals(150, t.all().single().american)
        // A live bet keeps paying the taker fee at the new price.
        val live = t.logCno(cnoRow, 0.05, live = true, placedKey = "cno:live")
        assertTrue(live.cost > live.price)
        t.setPrice(live.id, 120)
        val liveMoved = t.all().first { it.id == live.id }
        assertTrue(liveMoved.cost > liveMoved.price + 1e-9)
    }

    @Test
    fun `undoing a tapped result turns auto-grading off until it's turned back on`() = runTest {
        val t = BetTracker(File(tmp.root, "regrade.json"), clock = { now })
        val bet = t.logCno(cnoRow, 0.05, false, "cno:r")
        t.settle(bet.id, BetStatus.WON)
        assertEquals("Marked won by you", t.all().single().gradeNote)
        t.settle(bet.id, BetStatus.PENDING)
        val undone = t.all().single()
        assertTrue(undone.autoGradeOff)
        assertNull(undone.gradeNote)
        t.regrade(bet.id)
        assertEquals(false, t.all().single().autoGradeOff)
        assertNull(t.all().single().settledBy)
        // A settled bet isn't touched by regrade.
        t.settle(bet.id, BetStatus.LOST)
        t.regrade(bet.id)
        assertEquals(BetSettler.BY_YOU, t.all().single().settledBy)
    }
}
