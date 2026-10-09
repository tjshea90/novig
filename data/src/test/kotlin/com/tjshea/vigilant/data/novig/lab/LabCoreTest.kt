package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.data.novig.burst.Ladders
import com.tjshea.vigilant.data.pinnodds.PinnEvent
import com.tjshea.vigilant.data.pinnodds.PinnLine
import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lab's pure core (RESEARCH.md §120.6): ladder covers and thin ladders, the tail model and scan, the alternate-line scan, the ESPN clock, the paper report. */
class LabCoreTest {
    // ---- fixtures: a total's market "tN" at strike s, outcomes Under (tN-a) and Over (tN-b) ---------------------------------

    private fun total(id: String, strike: Double, fee: MarketFee = MarketFee.GAME) = NovigMarket(
        id, "ev", "TOTAL", "OPEN", "d", 0L, fee,
        listOf(NovigOutcome("$id-a", "Under $strike", "TBD"), NovigOutcome("$id-b", "Over $strike", "TBD")), strike,
    )

    /** [overBid]/[underBid]: the best resting bid (price in thousandths, contracts) on Over / Under. Buying Over costs 1 - the Under bid. */
    private fun book(id: String, overBid: Pair<Int, Long>? = null, underBid: Pair<Int, Long>? = null) = NovigBook(
        id, 1L,
        mapOf("$id-a" to listOfNotNull(underBid?.let { BidLevel(it.first, it.second) }), "$id-b" to listOfNotNull(overBid?.let { BidLevel(it.first, it.second) })),
        0L,
    )

    private fun point(id: String, strike: Double, overBid: Pair<Int, Long>? = null, underBid: Pair<Int, Long>? = null, fee: MarketFee = MarketFee.GAME) =
        LadderPoint(Ladders.line(total(id, strike, fee))!!, book(id, overBid, underBid))

    /** A liquid strike whose middle price for Over is [mid]: bids [mid]-0.02 for Over and (1-mid)-0.02 for Under, so each ask is 0.02 over the middle. */
    private fun liquid(id: String, strike: Double, mid: Double) =
        point(id, strike, overBid = ((mid - 0.02) * 1000).toInt() to 5_000L, underBid = (((1 - mid) - 0.02) * 1000).toInt() to 5_000L)

    // ---- LadderScan -----------------------------------------------------------------------------------------------------------

    @Test
    fun `Over at the lower strike and Under at the higher that cost under a dollar are a cover, net of both live fees`() {
        // Buy Over 52.5 at 0.45 (the Under bid is 0.55) and Under 53.5 at 0.40 (the Over bid is 0.60): 0.85 for at least $1.
        val ladder = listOf(point("t1", 52.5, underBid = 550 to 1_000L), point("t2", 53.5, overBid = 600 to 1_000L))
        val r = LadderScan.scan(ladder, live = true).single()
        assertEquals(1, r.covers.size)
        val c = r.covers.single()
        assertEquals(0.85, c.cost, 1e-9)
        assertEquals(1.0 - 0.85 - 0.03 * 0.45 * 0.55 - 0.03 * 0.40 * 0.60, c.net, 1e-9)
        assertEquals(1_000L, c.contracts)
        assertTrue(LadderScan.summary(listOf(r)).contains("1 cover"))
    }

    @Test
    fun `a pair costing 98 and a half cents pays before the game (no fee) and not in play (the fee is more than the cent)`() {
        val ladder = listOf(point("t1", 52.5, underBid = 550 to 1_000L), point("t2", 53.5, overBid = 540 to 1_000L))   // 0.45 + 0.46 = 0.91
        assertEquals(1, LadderScan.scan(ladder, live = false).single().covers.size)
        val tight = listOf(point("t1", 52.5, underBid = 515 to 1_000L), point("t2", 53.5, overBid = 500 to 1_000L))   // 0.485 + 0.50 = 0.985
        assertEquals("pregame: a cent and a half clear of the fee-free floor", 1, LadderScan.scan(tight, live = false).single().covers.size)
        assertEquals("in play: the fees (about 1.5 cents) take it under the floor", 0, LadderScan.scan(tight, live = true).single().covers.size)
    }

    @Test
    fun `a normal ladder has no cover, unpriced sides are counted, and one lone priced line is flagged`() {
        val normal = listOf(liquid("t1", 36.5, 0.62), liquid("t2", 37.5, 0.52), liquid("t3", 38.5, 0.43))
        val r = LadderScan.scan(normal, live = true).single()
        assertTrue(r.covers.isEmpty())
        assertEquals(6, r.pricedSides)
        assertEquals(0, r.unpricedSides)
        assertFalse(r.isolated)
        // The screenshot's shape: only Under 53.5 has a price (someone bids 10 cents for the Over); the strikes either side have no seller.
        val lone = listOf(point("a", 52.5), point("b", 53.5, overBid = 100 to 500L), point("c", 54.5))
        val l = LadderScan.scan(lone, live = true).single()
        assertTrue(l.isolated)
        assertEquals(1, l.pricedSides)
        assertEquals(5, l.unpricedSides)
        assertTrue(l.covers.isEmpty())
    }

    // ---- TailModel -----------------------------------------------------------------------------------------------------------

    @Test
    fun `the normal CDF and the negative binomial tail are right at known points`() {
        assertEquals(0.5, TailModel.phi(0.0), 1e-6)
        assertEquals(0.975, TailModel.phi(1.96), 1e-3)
        assertEquals(0.025, TailModel.phi(-1.96), 1e-3)
        // P(at least one goal) for a mean of 3 and shape 12: 1 - (12/15)^12.
        assertEquals(1.0 - Math.pow(12.0 / 15.0, 12.0), TailModel.pTotalOver(TailSport.HOCKEY, 3.5, 3, 0.5, 6.0), 1e-9)
    }

    @Test
    fun `the chance of going over falls as the strike rises, is certain once the score is past it, and falls with the time left`() {
        val f = TailSport.FOOTBALL
        val a = TailModel.pTotalOver(f, 44.5, 17, 0.5, 38.0)
        val b = TailModel.pTotalOver(f, 53.5, 17, 0.5, 38.0)
        assertTrue(a > b)
        assertEquals(1.0, TailModel.pTotalOver(f, 16.5, 17, 0.5, 38.0), 0.0)
        assertTrue("the same strike is less likely to be reached with less time left", TailModel.pTotalOver(f, 53.5, 17, 0.25, 38.0) < b)
        assertTrue("Tj's screenshot: Over 53.5 at the half with 17 on the board is a few percent", b in 0.02..0.09)
        assertTrue(TailModel.pTotalOver(f, 53.5, 17, 0.5, 38.0, sdMult = 1.25, centreShift = 1.5) > b)
    }

    @Test
    fun `the centre is where the ladder's prices cross a half`() {
        assertEquals(37.9, TailModel.centre(listOf(35.5 to 0.70, 38.5 to 0.45))!!, 1e-9)
        assertNull(TailModel.centre(listOf(35.5 to 0.30, 38.5 to 0.20)))
        assertNull(TailModel.centre(emptyList()))
    }

    // ---- TailScan --------------------------------------------------------------------------------------------------------------

    private fun footballLadder() = listOf(
        liquid("t1", 36.5, 0.62), liquid("t2", 37.5, 0.52), liquid("t3", 38.5, 0.43),
        point("far", 53.5, overBid = 100 to 500L),    // someone bids 10 cents for the Over: the Under costs 90
        point("dead", 52.5), point("dead2", 54.5),
    )

    @Test
    fun `at the half with 17 on the board the 90 cent Under 53 point 5 is a would-be bet on best-estimate rules and not on the conservative ones`() {
        val state = GameState(TailSport.FOOTBALL, 7, 10, 0.5)
        val explore = TailScan.scan(footballLadder(), state, { null }, TailRules.EXPLORE)
        val c = explore.single { it.strike == 53.5 }
        assertEquals("UNDER", c.side)
        assertEquals(0.90, c.ask, 1e-9)
        assertTrue("fair ${c.fair}", c.fair in 0.92..0.96)
        assertEquals("explore", c.rule)
        assertTrue(c.edge >= 0.03)
        assertTrue("the conservative rules want a fair of 90% after widening the spread and moving the centre: not here", TailScan.scan(footballLadder(), state, { null }).none { it.strike == 53.5 })
    }

    @Test
    fun `no tail bets before the half is near, in the last seconds, or when the centre is under the score`() {
        assertTrue(TailScan.scan(footballLadder(), GameState(TailSport.FOOTBALL, 7, 10, 0.9), { null }, TailRules.EXPLORE).isEmpty())
        assertTrue(TailScan.scan(footballLadder(), GameState(TailSport.FOOTBALL, 7, 10, 0.01), { null }, TailRules.EXPLORE).isEmpty())
        assertTrue("a ladder centred at about 38 with 45 points scored is a wrong score", TailScan.scan(footballLadder(), GameState(TailSport.FOOTBALL, 25, 20, 0.5), { null }, TailRules.EXPLORE).isEmpty())
    }

    @Test
    fun `a margin ladder's far line is judged from the lead now`() {
        // ATL leads by 14 late; the ladder is centred near +14 (liquid lines) and the far -3.5 line (ATL margin over +3.5 ... ) is priced as a long shot.
        fun ml(id: String, thr: Double, mid: Double) = LadderPoint(
            Ladders.line(NovigMarket(id, "ev", "SPREAD", "OPEN", "d", 0L, MarketFee.GAME, listOf(NovigOutcome("$id-a", "ATL ${if (thr >= 0) "-" else "+"}${Math.abs(thr)}", "TBD"), NovigOutcome("$id-b", "NO ${if (thr >= 0) "+" else "-"}${Math.abs(thr)}", "TBD")), thr))!!,
            NovigBook(id, 1L, mapOf("$id-a" to listOf(BidLevel(((mid - 0.02) * 1000).toInt(), 5_000L)), "$id-b" to listOf(BidLevel((((1 - mid) - 0.02) * 1000).toInt(), 5_000L))), 0L),
        )
        val ladder = listOf(ml("m1", 12.5, 0.58), ml("m2", 14.5, 0.46), ml("m3", 16.5, 0.34))
        val out = TailScan.scan(ladder, GameState(TailSport.FOOTBALL, 24, 10, 0.25), { ref -> if (ref == "ATL") 14 else null }, TailRules.EXPLORE)
        assertTrue("liquid lines are not tail bets", out.isEmpty())
        assertTrue("a ladder whose team can't be matched to the score is skipped", TailScan.scan(ladder, GameState(TailSport.FOOTBALL, 24, 10, 0.25), { null }, TailRules.EXPLORE).isEmpty())
    }

    // ---- AltLineScan ------------------------------------------------------------------------------------------------------------

    private fun quote(book: String, strike: Double, over: Double, under: Double, seenAt: Long?) = AltQuote(book, book, LadderKind.TOTAL, "", strike, over, under, seenAt)

    @Test
    fun `two fresh books at the same strike that agree make a fair the Novig ask is judged against - one book does not, a stale one does not, a different strike does not`() {
        val now = 1_000_000L
        val points = listOf(point("alt", 55.5, underBid = 600 to 1_000L))   // buying Over 55.5 costs 0.40
        val two = listOf(quote("pinnacle", 55.5, 1.80, 2.05, now - 5_000L), quote("fanduel", 55.5, 1.82, 2.02, now - 8_000L))
        val c = AltLineScan.scan(points, two, now, now + 5 * 3_600_000L, live = true).single()
        assertEquals("OVER", c.side)
        assertEquals(0.40, c.ask, 1e-9)
        assertEquals(2, c.books)
        assertTrue(c.fair in 0.50..0.56)
        assertTrue(c.edge > 0.3)
        assertTrue("one book is not enough by default", AltLineScan.scan(points, two.take(1), now, null, live = true).isEmpty())
        assertEquals("one book, when the study asks for it", 1, AltLineScan.scan(points, two.take(1), now, null, live = true, rules = AltRules(minBooks = 1)).size)
        val stale = two.map { it.copy(seenAtMs = now - 11 * 60_000L) }
        assertTrue("older than the app's freshness limit", AltLineScan.scan(points, stale, now, now + 5 * 3_600_000L, live = true).isEmpty() && AltLineScan.scan(points, stale, now, null, live = true).isEmpty())
        assertTrue(AltLineScan.scan(points, two.map { it.copy(threshold = 56.5) }, now, null, live = true).isEmpty())
    }

    @Test
    fun `books that disagree about the strike are not a fair price`() {
        val now = 1_000_000L
        val points = listOf(point("alt", 55.5, underBid = 600 to 1_000L))
        val split = listOf(quote("a", 55.5, 1.60, 2.40, now), quote("b", 55.5, 2.40, 1.60, now))
        assertTrue(AltLineScan.scan(points, split, now, null, live = true).isEmpty())
    }


    // ---- Pinnacle's alternate lines as quotes ---------------------------------------------------------------------------------

    private fun pinnLine(e: PinnEvent, key: String, type: PinnLineType, pts: Double, prices: Map<PinnSide, Double>, alt: Boolean = true, open: Boolean = true, period: Int = 0) {
        val l = PinnLine(e.id, key, period, type, alt)
        l.points = pts; l.american = prices; l.open = open
        e.lines[key] = l
    }

    @Test
    fun `Pinnacle's open full-game alternate totals and spreads become quotes in Novig's convention, both teams' views, swapped when the teams are`() {
        val e = PinnEvent(1L).also { it.lastFrameAtMs = 5_000L }
        pinnLine(e, "s;0;ou;55.5", PinnLineType.TOTAL, 55.5, mapOf(PinnSide.OVER to 150.0, PinnSide.UNDER to -180.0))
        pinnLine(e, "s;0;s;-4.5", PinnLineType.SPREAD, -4.5, mapOf(PinnSide.HOME to -110.0, PinnSide.AWAY to -110.0))
        pinnLine(e, "s;0;ou;40.5", PinnLineType.TOTAL, 40.5, mapOf(PinnSide.OVER to -120.0, PinnSide.UNDER to 100.0), open = false)
        pinnLine(e, "s;1;ou;20.5", PinnLineType.TOTAL, 20.5, mapOf(PinnSide.OVER to -120.0, PinnSide.UNDER to 100.0), period = 1)
        val q = PinnAltQuotes.quotes(e, swapped = false)
        assertEquals("one total + the spread from both teams' sides; the closed line and the half are left out", 3, q.size)
        val total = q.single { it.kind == LadderKind.TOTAL }
        assertEquals(55.5, total.threshold, 0.0)
        assertEquals(2.5, total.yesDecimal, 1e-9)
        assertEquals(1.0 + 100.0 / 180.0, total.noDecimal, 1e-9)
        assertEquals(5_000L, total.seenAtMs)
        val home = q.single { it.refSide == "HOME" }
        val away = q.single { it.refSide == "AWAY" }
        assertEquals("home -4.5 covers when the home margin is over 4.5", 4.5, home.threshold, 0.0)
        assertEquals("away +4.5 covers when the away margin is over -4.5", -4.5, away.threshold, 0.0)
        assertEquals(setOf("AWAY", "HOME"), PinnAltQuotes.quotes(e, swapped = true).filter { it.kind == LadderKind.MARGIN }.map { it.refSide }.toSet())
        assertEquals("swapped: Pinnacle's home line is Novig's AWAY team's", 4.5, PinnAltQuotes.quotes(e, swapped = true).single { it.refSide == "AWAY" }.threshold, 0.0)
        assertNull(PinnAltQuotes.decimal(50.0))
    }

    @Test
    fun `a margin alternate is matched to a Novig ladder by the side the caller resolves, never by name`() {
        val now = 1_000_000L
        fun spread(id: String, thr: Double) = LadderPoint(
            Ladders.line(NovigMarket(id, "ev", "SPREAD", "OPEN", "d", 0L, MarketFee.GAME, listOf(NovigOutcome("$id-a", "TB -4.5", "TBD"), NovigOutcome("$id-b", "DAL +4.5", "TBD")), thr))!!,
            NovigBook(id, 1L, mapOf("$id-a" to emptyList(), "$id-b" to listOf(BidLevel(600, 1_000L))), 0L),   // buying TB -4.5 costs 0.40
        )
        val p = spread("sp", 4.5)   // Novig sorts the two names: the ladder's reference team is DAL (alphabetically first), so DAL +4.5 is "margin over -4.5" and TB -4.5 is its NOT
        fun q(book: String, side: String) = AltQuote(book, book, LadderKind.MARGIN, side, -4.5, 1.80, 2.05, now)
        val quotes = listOf(q("a", "HOME"), q("b", "HOME"))
        assertEquals("DAL is the home side here: TB -4.5 costs 0.40 against a fair near 0.47", 1, AltLineScan.scan(listOf(p), quotes, now, null, true, sideOf = { if (it == "DAL") "HOME" else null }).size)
        assertTrue("DAL resolved as the away side: those quotes are not its line", AltLineScan.scan(listOf(p), quotes, now, null, true, sideOf = { if (it == "DAL") "AWAY" else null }).isEmpty())
        assertTrue("no resolver: skipped", AltLineScan.scan(listOf(p), quotes, now, null, true).isEmpty())
    }

    // ---- LabClock + LabPaper ----------------------------------------------------------------------------------------------------

    @Test
    fun `ESPN's scoreboard gives both teams, the score, whether it is live and where it is, and the share of regulation left`() {
        val body = """{"events":[{"status":{"period":2,"displayClock":"0:00","type":{"state":"in"}},"competitions":[{"competitors":[
            {"homeAway":"home","score":"10","team":{"displayName":"Dallas Cowboys","abbreviation":"DAL"}},
            {"homeAway":"away","score":"7","team":{"displayName":"Tampa Bay Buccaneers","abbreviation":"TB"}}]}]}]}"""
        val g = LabClock.parseEspn(body).single()
        assertEquals("DAL", g.homeAbbr)
        assertEquals(10, g.homeScore)
        assertTrue(g.live)
        assertEquals(3, g.marginOf("DAL"))
        assertEquals(-3, g.marginOf("TB"))
        assertEquals("a team that is neither", null, g.marginOf("Seattle Seahawks"))
        assertEquals(0.5, LabClock.fractionLeft("NFL", g.period, g.clockSec)!!, 1e-9)
        assertEquals(0.375, LabClock.fractionLeft("NBA", 3, 360.0)!!, 1e-9)
        assertNull("overtime", LabClock.fractionLeft("NFL", 5, 300.0))
        assertNull("a clock past the length of a period", LabClock.fractionLeft("NFL", 1, 1_000.0))
        assertNull("soccer has no clock here", LabClock.fractionLeft("EPL", 1, 100.0))
        assertEquals(443.0, LabClock.clockSeconds("7:23")!!, 0.0)
        assertEquals(45.3, LabClock.clockSeconds("45.3")!!, 0.0)
        assertNull(LabClock.clockSeconds("--"))
    }

    @Test
    fun `the paper report counts would-be bets and grades them by the settled market`() {
        fun rec(id: String, ask: Double, kind: String = LabKind.TAIL) = LabRecord(id, 0L, kind, "e1", "A @ B", "NFL", "m$id", "o$id", "Total 53.5", "UNDER", 53.5, ask, 0.95, 0.05, 500L)
        val records = listOf(rec("1", 0.90), rec("2", 0.90), rec("3", 0.90), rec("c", 0.85, LabKind.COVER))
        val grades = listOf(LabGrade("1", 0L, "WIN"), LabGrade("2", 0L, "WIN"), LabGrade("3", 0L, "LOSS"))
        val lines = LabPaper.report(records, grades)
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("TAIL: 3 would-be bets on 1 game") && lines[0].contains("2-1 graded"))
        assertEquals((1 / 0.9 - 1) * 2 - 1.0, LabPaper.profitPerDollar(rec("1", 0.90), "WIN")!! * 2 + LabPaper.profitPerDollar(rec("3", 0.90), "LOSS")!!, 1e-9)
        assertEquals(0.0, LabPaper.profitPerDollar(rec("1", 0.90), "PUSH")!!, 0.0)
        assertEquals(0.5 / 0.9 - 1, LabPaper.profitPerDollar(rec("1", 0.90), "0.5")!!, 1e-9)
        assertNotNull(lines[1])
        assertEquals(listOf("no would-be bets recorded yet"), LabPaper.report(emptyList(), emptyList()))
    }
}
