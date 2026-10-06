package com.tjshea.vigilant.data.novig.burst

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The score-burst recorder's pure core (RESEARCH.md §83-§84, §95): the ladders, the cover's cost after the in-play fee, the windows, the paper trade, the delays, the
 * journal and the verdict. The numbers in the cover tests are the §84.2 burst of 01:25:43 (moneyline YES at 0.539 against the −1.5 spread NOT, here at 0.435 so the net is clear of the noise) and a calm pair.
 */
class BurstCoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun market(id: String, type: String, a: String, b: String, strike: Double? = null, event: String = "ev", fee: MarketFee? = MarketFee.GAME) =
        NovigMarket(id, event, type, "OPEN", "d", 0L, fee, listOf(NovigOutcome("$id-a", a, "TBD"), NovigOutcome("$id-b", b, "TBD")), strike)

    private fun book(id: String, aBids: List<Pair<Int, Long>> = emptyList(), bBids: List<Pair<Int, Long>> = emptyList()) = NovigBook(
        id, 1L,
        mapOf("$id-a" to aBids.map { BidLevel(it.first, it.second) }, "$id-b" to bBids.map { BidLevel(it.first, it.second) }),
        0L,
    )

    // ---- ladders ------------------------------------------------------------------------------------------------------------

    @Test
    fun `a moneyline is the margin of the alphabetically first team over zero, a spread X minus k is over k, X plus k is under it, a total's Over s is over s`() {
        val ml = Ladders.line(market("ml", "MONEY", "NO", "ATL", strike = 0.0))!!
        assertEquals(0.0, ml.threshold, 0.0)
        assertEquals("ml-b", ml.yesOutcomeId)   // ATL sorts before NO
        assertEquals("ev|M|ATL", ml.ladderKey)
        val minus = Ladders.line(market("s1", "SPREAD", "ATL -1.5", "NO +1.5", strike = 1.5))!!
        assertEquals(1.5, minus.threshold, 0.0)
        assertEquals("s1-a", minus.yesOutcomeId)
        assertEquals(ml.ladderKey, minus.ladderKey)
        val plus = Ladders.line(market("s2", "SPREAD", "NO -2.5", "ATL +2.5", strike = -2.5))!!
        assertEquals(-2.5, plus.threshold, 0.0)
        assertEquals("s2-b", plus.yesOutcomeId)
        val total = Ladders.line(market("t", "TOTAL", "Under 47.5", "Over 47.5", strike = 47.5))!!
        assertEquals(47.5, total.threshold, 0.0)
        assertEquals("t-b", total.yesOutcomeId)
        assertEquals("ev|T", total.ladderKey)
        assertEquals("a total's number is not a margin", LadderKind.TOTAL, total.kind)
    }

    @Test
    fun `a market that is not a game line, has no readable fee, or names a side it can't read is not a line - and a spread named another way is a ladder of its own`() {
        assertNull(Ladders.line(market("p", "PASSING_YARDS", "Over 200.5", "Under 200.5")))
        assertNull(Ladders.line(market("ml", "MONEY", "ATL", "NO", fee = null)))
        assertNull(Ladders.line(market("s", "SPREAD", "ATL", "NO +1.5")))
        assertNull(Ladders.line(market("s", "SPREAD", "ATL +1.5", "ATL -1.5")))
        // Full names against abbreviations: different reference teams, so never crossed (a sign error would be a false cover).
        val abbr = Ladders.line(market("ml", "MONEY", "ATL", "NO"))!!
        val full = Ladders.line(market("s", "SPREAD", "Atlanta Falcons -1.5", "New Orleans Saints +1.5", strike = 1.5))!!
        assertTrue(abbr.ladderKey != full.ladderKey)
        assertNull(CoverMath.cover(abbr, book("ml", bBids = listOf(560 to 1000L)), full, book("s", aBids = listOf(470 to 1000L))))
    }

    // ---- covers -------------------------------------------------------------------------------------------------------------

    private val ml = Ladders.line(market("ml", "MONEY", "ATL", "NO"))!!
    private val spread = Ladders.line(market("s1", "SPREAD", "ATL -1.5", "NO +1.5", strike = 1.5))!!
    private val spread2 = Ladders.line(market("s2", "SPREAD", "ATL -2.5", "NO +2.5", strike = 2.5))!!

    @Test
    fun `a cover is YES at the lower line plus NOT at the higher, each at 1 minus the best bid on the other side, net of both in-play fees`() {
        // ML: the best bid on NO is 0.461 => YES costs 0.539. Spread -1.5: the best bid on ATL -1.5 (its YES) is 0.565 => NOT costs 0.435.
        val c = CoverMath.cover(ml, book("ml", bBids = listOf(461 to 40_000L)), spread, book("s1", aBids = listOf(565 to 25_000L)))!!
        assertEquals(0.539, c.yes.price, 1e-9)
        assertEquals(0.435, c.no.price, 1e-9)
        assertEquals(25_000L, c.contracts)
        val fees = 0.03 * 0.539 * 0.461 + 0.03 * 0.435 * 0.565
        assertEquals(1.0 - 0.974 - fees, c.net, 1e-9)
        assertEquals("dollars: net x contracts x 1 cent", c.net * 25_000 * 0.01, c.dollars, 1e-9)
        assertTrue("this burst paid", c.net > 0.003)
        assertEquals("ml>s1", c.key)
    }

    @Test
    fun `a calm pair costs more than a dollar - the cover is not there`() {
        // Median of calm moneyline against the +1.5 spread: 1.030.
        val c = CoverMath.cover(ml, book("ml", bBids = listOf(480 to 1000L)), spread, book("s1", aBids = listOf(490 to 1000L)))!!
        assertTrue(c.cost > 1.0)
        assertTrue(c.net < 0.0)
    }

    @Test
    fun `no cover when the lines are not in order, on another ladder, or a side has nothing to buy`() {
        val b1 = book("ml", bBids = listOf(461 to 1000L))
        val b2 = book("s1", aBids = listOf(555 to 1000L))
        assertNull("lower must be below higher", CoverMath.cover(spread, b2, ml, b1))
        assertNull(CoverMath.cover(ml, b1, ml, b1))
        val total = Ladders.line(market("t", "TOTAL", "Over 47.5", "Under 47.5", strike = 47.5))!!
        assertNull("another ladder", CoverMath.cover(ml, b1, total, book("t", aBids = listOf(555 to 1000L))))
        assertNull("nothing bid on the other side", CoverMath.cover(ml, book("ml"), spread, b2))
        assertNull(CoverMath.cover(ml, null, spread, b2))
        assertNull("a bid at 1.000 or 0 is not a price", CoverMath.leg(book("ml", bBids = listOf(1000 to 10L)), ml, yes = true))
    }

    // ---- windows ------------------------------------------------------------------------------------------------------------

    private class Books(val map: MutableMap<String, NovigBook> = HashMap()) : (String) -> NovigBook? {
        override fun invoke(id: String) = map[id]
    }

    private val lines = listOf(ml, spread, spread2)

    @Test
    fun `a window opens when a book change makes a pair pay, stays open while it pays, and closes after the grace with its true length`() {
        val w = CoverWindows(minNet = 0.003, minContracts = 100, graceMs = 150)
        val books = Books()
        books.map["s1"] = book("s1", aBids = listOf(565 to 25_000L))      // NOT at -1.5 costs 0.435
        books.map["s2"] = book("s2", aBids = listOf(300 to 25_000L))      // far out: NOT at -2.5 costs 0.700, nothing pays against it
        books.map["ml"] = book("ml", bBids = listOf(420 to 40_000L))      // calm: YES 0.580 + 0.435 = 1.015
        assertTrue(w.onBook(lines, books, ml, 1_000).isEmpty())
        assertEquals(0, w.openCount)
        // The moneyline re-quotes stale: the NO side is bid at 0.461 => YES 0.539.
        books.map["ml"] = book("ml", bBids = listOf(461 to 40_000L))
        val opened = w.onBook(lines, books, ml, 2_000)
        assertEquals(1, opened.size)
        assertEquals("ml>s1", opened.single().first.key)
        assertEquals(2_000L, opened.single().openedMs)
        // Still paying at the next push.
        assertTrue(w.onBook(lines, books, spread, 2_300).isEmpty())
        assertEquals(1, w.openCount)
        assertEquals(2, w.window("ml>s1")!!.updates)
        // The makers fix the line: it stops paying at 2.9 s; not closed until the grace has passed.
        books.map["ml"] = book("ml", bBids = listOf(420 to 40_000L))
        w.onBook(lines, books, ml, 2_900)
        assertTrue(w.sweep(3_000).isEmpty())
        val closed = w.sweep(3_100)
        assertEquals(1, closed.size)
        assertEquals("closed when it stopped paying", 2_900L, closed.single().closedMs)
        assertEquals(900L, closed.single().durationMs)
        assertEquals(0, w.openCount)
    }

    @Test
    fun `a cover that stops paying and pays again inside the grace is one window - a partial fill is a remove then an add`() {
        val w = CoverWindows(graceMs = 150)
        val books = Books()
        books.map["s1"] = book("s1", aBids = listOf(565 to 25_000L))
        books.map["ml"] = book("ml", bBids = listOf(461 to 40_000L))
        assertEquals(1, w.onBook(lines, books, ml, 1_000).size)
        books.map["ml"] = book("ml")                                      // the order is removed ...
        w.onBook(lines, books, ml, 1_100)
        books.map["ml"] = book("ml", bBids = listOf(461 to 30_000L))      // ... and the rest added back
        assertTrue(w.onBook(lines, books, ml, 1_120).isEmpty())
        assertTrue(w.sweep(2_000).isEmpty())
        assertEquals(1, w.openCount)
    }

    @Test
    fun `a sliver of a contract, or a net under the noise, is not a window`() {
        val books = Books()
        books.map["s1"] = book("s1", aBids = listOf(565 to 25_000L))
        books.map["ml"] = book("ml", bBids = listOf(461 to 50L))           // 50 contracts is 50 cents of payout
        assertTrue(CoverWindows(minContracts = 100).onBook(lines, books, ml, 1).isEmpty())
        books.map["ml"] = book("ml", bBids = listOf(461 to 40_000L))
        assertTrue("a net of a cent is not 5 cents", CoverWindows(minNet = 0.05).onBook(lines, books, ml, 1).isEmpty())
        assertEquals(1, CoverWindows(minNet = 0.003).onBook(lines, books, ml, 1).size)
    }

    @Test
    fun `closeAll ends everything open - when the game ends or the feed drops`() {
        val w = CoverWindows()
        val books = Books()
        books.map["s1"] = book("s1", aBids = listOf(565 to 25_000L))
        books.map["ml"] = book("ml", bBids = listOf(461 to 40_000L))
        w.onBook(lines, books, ml, 1_000)
        val closed = w.closeAll(5_000)
        assertEquals(1, closed.size)
        assertEquals(4_000L, closed.single().durationMs)
        assertEquals(0, w.openCount)
    }

    // ---- the paper trade ----------------------------------------------------------------------------------------------------

    private val open = CoverMath.cover(ml, book("ml", bBids = listOf(461 to 40_000L)), spread, book("s1", aBids = listOf(565 to 25_000L)))!!

    @Test
    fun `both legs still on offer at the seen price or better fill - the profit is the cover's, on the thinner leg, capped by Tj's limit`() {
        val f = PaperTrader.trade(open, open.yes, open.no, capDollars = 0.0)
        assertEquals(PaperOutcome.BOTH, f.outcome)
        assertEquals(25_000L, f.contracts)
        assertEquals(open.dollars, f.pnl, 1e-9)
        // A $10 limit a leg: 10 / (0.539 x 0.01) = 1,855 contracts on the dearer leg.
        val capped = PaperTrader.trade(open, open.yes, open.no, capDollars = 10.0)
        assertEquals(1_855L, capped.contracts)
        assertEquals(open.net * 1_855 * 0.01, capped.pnl, 1e-9)
        // A better price fills at it.
        val better = PaperTrader.trade(open, Leg(0.530, 25_000L), open.no, 0.0)
        assertTrue(better.pnl > f.pnl)
    }

    @Test
    fun `one leg gone is a naked leg that loses its fee and the penalty, a price moved against the order is no fill, both gone is a miss`() {
        val one = PaperTrader.trade(open, open.yes, null, 0.0)
        assertEquals(PaperOutcome.ONE_LEG, one.outcome)
        val fee = 0.03 * 0.539 * 0.461
        assertEquals("the leg's own depth: 40,000", -(fee + PaperTrader.NAKED_PENALTY) * 40_000 * 0.01, one.pnl, 1e-9)
        val worse = PaperTrader.trade(open, Leg(0.545, 25_000L), open.no, 0.0)
        assertEquals("the yes leg moved against the order: only the no leg fills", PaperOutcome.ONE_LEG, worse.outcome)
        assertEquals(PaperOutcome.MISSED, PaperTrader.trade(open, null, null, 0.0).outcome)
        assertEquals(0.0, PaperTrader.trade(open, null, null, 0.0).pnl, 0.0)
        assertEquals(PaperOutcome.MISSED, PaperTrader.trade(open, Leg(0.60, 10L), Leg(0.60, 10L), 0.0).outcome)
    }

    // ---- delays -------------------------------------------------------------------------------------------------------------

    @Test
    fun `until measured the delays are the defaults and the report says so - then the median and the 95th of what was measured`() {
        val m = LatencyModel()
        val d = m.profiles()
        assertEquals(listOf(0L, 150 / 2 + 15 + 150L, 300 / 2 + 15 + 300L), d.map { it.totalMs })
        assertTrue(m.note(), m.note().contains("ASSUMED"))
        repeat(100) { m.addRoundTrip(100L + it) }
        repeat(100) { m.addPushDelay(50L + it) }
        val measured = m.profiles()
        // median rtt 150, 95th 195; median push 100, 95th 145.
        assertEquals(150L / 2 + 15 + 100, measured[1].totalMs)
        assertEquals(195L / 2 + 15 + 145, measured[2].totalMs)
        assertTrue(m.note(), m.note().contains("measured, 100 signed echoes") && m.note().contains("measured, 100 fills"))
        m.addRoundTrip(0); m.addRoundTrip(99_999); m.addPushDelay(-5)
        assertEquals("a nonsense sample is not kept", 100, m.roundTrips())
        assertEquals(100, m.pushDelays())
    }

    // ---- the journal and the verdict ----------------------------------------------------------------------------------------

    private fun result(profile: String, delay: Long, outcome: PaperOutcome, pnl: Double, capped: Double = pnl) =
        ProfileResult(profile, delay, outcome.name, 100, pnl, 100, capped)

    private fun window(game: String, league: String, outcomeSlow: PaperOutcome, pnlSlow: Double, ms: Long = 1000L) = WindowRecord(
        league, game, game, "ML ATL YES / Spr ATL -1.5 NOT", 1_790_000_000_000L, ms, 0.5, 0.45, 5000, 0.02, 0.03, 6000, 3,
        listOf(result(LatencyModel.OPTIMISTIC, 0, PaperOutcome.BOTH, 1.0), result(LatencyModel.TYPICAL, 200, outcomeSlow, pnlSlow), result(LatencyModel.SLOW, 400, outcomeSlow, pnlSlow)),
    )

    @Test
    fun `the journal keeps what it is given, skips a torn line, and a day is the Eastern date`() {
        val j = BurstJournal(tmp.newFolder("burst"))
        j.append(1_790_000_000_000L, listOf(window("g1", "NFL", PaperOutcome.BOTH, 0.4), GameRecord("NFL", "g1", "A @ B", 1_000L, 7_201_000L, 56, 9_000L)))
        // A crash left half a line: skipped, and the next append starts on a new one.
        java.io.File(tmp.root, "burst/" + j.file(BurstJournal.dayOf(1_790_000_000_000L)).name).appendText("{\"k\":\"w\",\"league\":\"NF")
        j.append(1_790_000_000_000L, listOf(window("g2", "NFL", PaperOutcome.MISSED, 0.0)))
        val all = j.readAll()
        assertEquals(3, all.size)
        assertEquals(2, all.filterIsInstance<WindowRecord>().size)
        assertEquals(1, all.filterIsInstance<GameRecord>().size)
        assertEquals("2026-09-21".length, BurstJournal.dayOf(1_790_000_000_000L).toString().length)
        assertEquals(1, j.days().size)
    }

    @Test
    fun `not enough games or windows is not a verdict`() {
        val few = listOf(window("g1", "NFL", PaperOutcome.BOTH, 1.0), window("g1", "NFL", PaperOutcome.BOTH, 1.0))
        assertEquals(BurstVerdict.NEEDS_DATA, BurstStudy.verdict(BurstStudy.summarize(few).last()))
    }

    private fun many(games: Int, perGame: Int, outcome: PaperOutcome, pnl: Double): List<BurstLine> =
        (1..games).flatMap { g -> (1..perGame).map { window("g$g", "NFL", outcome, pnl) } + GameRecord("NFL", "g$g", "g$g", 0L, 3_600_000L, 50, 1L) }

    @Test
    fun `windows that close before an order arrives, or that lose on paper, are a no`() {
        assertEquals(BurstVerdict.NOT_CATCHABLE, BurstStudy.verdict(BurstStudy.summarize(many(4, 5, PaperOutcome.MISSED, 0.0)).last()))
        assertEquals("caught but a loss on paper", BurstVerdict.NOT_CATCHABLE, BurstStudy.verdict(BurstStudy.summarize(many(4, 5, PaperOutcome.BOTH, -0.1)).last()))
    }

    @Test
    fun `a positive paper result that is pennies at Tj's limits is too small, and one that holds in most games is worth a test`() {
        assertEquals(BurstVerdict.TOO_SMALL, BurstStudy.verdict(BurstStudy.summarize(many(4, 5, PaperOutcome.BOTH, 0.02)).last()))   // 10 cents a game
        assertEquals(BurstVerdict.WORTH_A_TEST, BurstStudy.verdict(BurstStudy.summarize(many(4, 5, PaperOutcome.BOTH, 0.30)).last()))  // $1.50 a game
        // Positive in only half the games: not worth it, whatever the total.
        val half = many(2, 5, PaperOutcome.BOTH, 0.30) + (3..4).flatMap { g -> (1..5).map { window("g$g", "NFL", PaperOutcome.BOTH, -0.01) } + GameRecord("NFL", "g$g", "g$g", 0L, 3_600_000L, 50, 1L) }
        assertEquals(BurstVerdict.TOO_SMALL, BurstStudy.verdict(BurstStudy.summarize(half).last()))
    }

    @Test
    fun `the report says what the paper trade is, names the delays, the verdict, and that it cannot prove a profit`() {
        val text = BurstStudy.report(many(4, 5, PaperOutcome.BOTH, 0.30), "round trip: ASSUMED 150 ms (0 measured so far); push delay: ASSUMED 150 ms (0 measured so far)", 10.0)
        assertTrue(text, text.contains("no orders") && text.contains("NFL: 4 games") && text.contains("ALL: 4 games"))
        assertTrue(text, text.contains("VERDICT: positive on paper at your delays"))
        assertTrue(text, text.contains("WHAT THIS PROVES") && text.contains("cannot prove a profit") && text.contains("a rival's speed"))
        assertTrue(text, text.contains("ASSUMED 150 ms"))
        assertTrue(BurstStudy.report(emptyList(), "", 0.0).contains("nothing recorded yet"))
    }

    @Test
    fun `a window record round-trips through the journal's JSON with its results`() {
        val j = BurstJournal(tmp.newFolder("rt"))
        val w = window("g1", "NBA", PaperOutcome.ONE_LEG, -0.05)
        j.append(1_790_000_000_000L, listOf(w))
        val back = j.readAll().single() as WindowRecord
        assertEquals(w, back)
        assertNotNull(back.results.firstOrNull { it.profile == LatencyModel.SLOW })
        assertFalse(back.results.isEmpty())
    }
}
