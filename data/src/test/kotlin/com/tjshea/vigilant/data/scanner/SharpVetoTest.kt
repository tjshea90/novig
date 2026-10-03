package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02 17:01Z: "sharp veto instead of requirement. Only skip a bet if the sharpest book for that market says it is not +ev. This must separate types of
 * bets by which books are sharpest for those bet types." RESEARCH.md §66.
 */
class SharpVetoTest {

    private fun view(vararg p: CnoBookPrice) = CnoBooksView(bet = "x", prices = p.toList(), fetchedAtMs = 0L)

    @Test
    fun `each kind of bet is told from CNO's and Novig's names`() {
        assertEquals(BetKind.PROP, BetKind.of("Player Receiving Yards", "Juwan Johnson Under 39.5"))
        assertEquals(BetKind.MONEYLINE, BetKind.of("Moneyline", "New Orleans Saints"))
        assertEquals(BetKind.SPREAD, BetKind.of("Point Spread", "Atlanta Falcons +3.5"))
        assertEquals(BetKind.TOTAL, BetKind.of("Total Points", "Over 47.5"))
        assertEquals(BetKind.TEAM_TOTAL, BetKind.of("Team Total Points", "Atlanta Falcons Over 21.5"))
        assertEquals(BetKind.PERIOD, BetKind.of("1st Half Total", "Over 23.5"))
        assertEquals(BetKind.OTHER, BetKind.of("Futures", "Kansas City Chiefs to win the Super Bowl"))
    }

    @Test
    fun `a prop the grader can't grade is still a prop, a quarter is a period line, and a set spread is the whole match`() {
        // Found 2026-10-02 ~17:40Z: these came out OTHER, so the Volume preset (props, moneylines, spreads) never auto-bet them and the veto asked Pinnacle.
        for ((market, bet) in listOf(
            "Player Interceptions" to "Patrick Mahomes Over 0.5", "Player Sacks" to "Micah Parsons Over 0.5", "Player Field Goals" to "Harrison Butker Over 1.5",
            "Player Singles" to "Luis Arraez Over 0.5", "Player Outs" to "Gerrit Cole Over 17.5", "Player Blocked Shots" to "Adam Fox Over 1.5",
            "Player Shots On Target" to "Erling Haaland Over 1.5",
        )) assertEquals(market, BetKind.PROP, BetKind.of(market, bet))
        for ((market, bet) in listOf(
            "1st Quarter Spread" to "Kansas City Chiefs -0.5", "1st Quarter Total" to "Over 10.5", "1st Period Total Goals" to "Over 1.5",
            "1st 5 Innings Moneyline" to "New York Yankees", "2nd Half Spread" to "Kansas City Chiefs -1.5", "1st Set Winner" to "Novak Djokovic",
        )) assertEquals(market, BetKind.PERIOD, BetKind.of(market, bet))
        assertEquals(BetKind.SPREAD, BetKind.of("Set Spread", "Novak Djokovic -1.5"))
        assertEquals(BetKind.TOTAL, BetKind.of("Total Sets", "Over 3.5"))
        assertEquals(BetKind.OTHER, BetKind.of("Moneyline 3-Way", "Draw"))
        assertEquals(BetKind.SPREAD, BetKind.of("Alternate Spread", "Kansas City Chiefs"))
        // And the veto asks the prop books about them.
        assertEquals(SharpVeto.ranking(BetKind.PROP, SharpVeto.Sport.FOOTBALL), SharpVeto.ranking(BetKind.of("Player Sacks", "Micah Parsons Over 0.5"), SharpVeto.Sport.FOOTBALL))
    }

    @Test
    fun `each league is its sport, college apart`() {
        assertEquals(SharpVeto.Sport.FOOTBALL, SharpVeto.sportOf("NFL"))
        assertEquals(SharpVeto.Sport.COLLEGE_FOOTBALL, SharpVeto.sportOf("NCAAF"))
        assertEquals(SharpVeto.Sport.BASEBALL, SharpVeto.sportOf("MLB"))
        assertEquals(SharpVeto.Sport.BASKETBALL, SharpVeto.sportOf("NBA"))
        assertEquals(SharpVeto.Sport.COLLEGE_BASKETBALL, SharpVeto.sportOf("NCAAB"))
        assertEquals(SharpVeto.Sport.HOCKEY, SharpVeto.sportOf("NHL"))
        assertEquals(SharpVeto.Sport.SOCCER, SharpVeto.sportOf("EPL"))
        assertEquals(SharpVeto.Sport.TENNIS, SharpVeto.sportOf("ATP"))
        assertEquals(SharpVeto.Sport.OTHER, SharpVeto.sportOf("Curling"))
    }

    @Test
    fun `the sharpest books differ by kind of bet and sport`() {
        // Props: the exchanges first, then the US books that originate prop numbers; never Pinnacle or Circa.
        assertEquals(listOf("KI", "PX", "FD", "CZR"), SharpVeto.ranking(BetKind.PROP, SharpVeto.Sport.FOOTBALL))
        assertEquals(listOf("KI", "PX", "DK", "FD"), SharpVeto.ranking(BetKind.PROP, SharpVeto.Sport.BASEBALL))
        // Game lines: Pinnacle, then Circa; Circa first in college; Pinnacle alone in soccer and tennis.
        for (kind in listOf(BetKind.MONEYLINE, BetKind.SPREAD, BetKind.TOTAL, BetKind.TEAM_TOTAL, BetKind.PERIOD)) {
            assertEquals(listOf("PN", "CS"), SharpVeto.ranking(kind, SharpVeto.Sport.FOOTBALL))
            assertEquals(listOf("CS", "PN"), SharpVeto.ranking(kind, SharpVeto.Sport.COLLEGE_BASKETBALL))
            assertEquals(listOf("PN"), SharpVeto.ranking(kind, SharpVeto.Sport.SOCCER))
        }
    }

    @Test
    fun `the sharpest book on the page decides, and only it`() {
        // Tj's screenshot, Juwan Johnson Under 39.5 at Novig +113: Kalshi -107/-121 (48.4%) says +EV; ProphetX would too; BetMGM isn't a prop sharp.
        val page = view(
            CnoBookPrice("PX", odds = -107, otherOdds = -124), CnoBookPrice("KI", odds = -107, otherOdds = -121),
            CnoBookPrice("MGM", odds = -115, otherOdds = -115), CnoBookPrice("NV", odds = 113, otherOdds = -127),
        )
        val ok = SharpVeto.judge(page, "NFL", "Player Receiving Yards", "Juwan Johnson Under 39.5", 113, false, 0.0)
        assertEquals(SharpVeto.Verdict.PASSED, ok.verdict)
        assertEquals("KI", ok.book)
        assertTrue(ok.ev!! > 0.0)
        assertNull(ok.reason)
        // Novig at -110 instead: Kalshi's own fair (48.4%) makes it -7.6%: vetoed, though ProphetX is lower in the ranking and BetMGM says nothing.
        val no = SharpVeto.judge(page, "NFL", "Player Receiving Yards", "Juwan Johnson Under 39.5", -110, false, 0.0)
        assertEquals(SharpVeto.Verdict.VETOED, no.verdict)
        assertEquals("KI", no.book)
        assertEquals("Kalshi, the sharpest book for player props, says it isn't +EV at Novig's price", no.reason)
        assertTrue(no.detail, no.detail.startsWith("Kalshi -"))
        // Without Kalshi, ProphetX is the sharpest there.
        val px = SharpVeto.judge(view(CnoBookPrice("PX", odds = -107, otherOdds = -124), CnoBookPrice("KI", odds = -107)), BetKind.PROP, SharpVeto.Sport.FOOTBALL, 113, false, 0.0)
        assertEquals("PX", px.book)
    }

    @Test
    fun `no sharp book pricing both sides is no veto, and Pinnacle doesn't judge a prop`() {
        val pinnacleOnly = view(CnoBookPrice("PN", odds = -150, otherOdds = 120), CnoBookPrice("KI", odds = -107))
        val r = SharpVeto.judge(pinnacleOnly, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 113, false, 0.0)
        assertEquals(SharpVeto.Verdict.NO_SHARP, r.verdict)
        assertFalse(r.vetoed)
        assertNull(r.reason)
        assertEquals(SharpVeto.Verdict.NO_SHARP, SharpVeto.judge(null, BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false, 0.0).verdict)
        // The same Pinnacle prices decide a spread: -150/+120 makes this side ~58%, so Novig's +113 passes; the other way round (+120/-150, ~43%) is a veto.
        val spread = SharpVeto.judge(pinnacleOnly, BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false, 0.0)
        assertEquals(SharpVeto.Verdict.PASSED, spread.verdict)
        val against = SharpVeto.judge(view(CnoBookPrice("PN", odds = 120, otherOdds = -150)), BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false, 0.0)
        assertEquals(SharpVeto.Verdict.VETOED, against.verdict)
        assertEquals("Pinnacle, the sharpest book for spreads, says it isn't +EV at Novig's price", against.reason)
    }

    @Test
    fun `college game lines ask Circa before Pinnacle, and a sister site counts as its company`() {
        val page = view(CnoBookPrice("PN", odds = 120, otherOdds = -150), CnoBookPrice("CS", odds = -105, otherOdds = -115))
        assertEquals("CS", SharpVeto.judge(page, "NCAAF", "Point Spread", "Ohio State -7.5", 113, false, 0.0).book)
        assertEquals("PN", SharpVeto.judge(page, "NFL", "Point Spread", "Kansas City Chiefs -7.5", 113, false, 0.0).book)
        val yourWay = view(CnoBookPrice("FDYW", odds = -110, otherOdds = -110))
        assertEquals("FD", SharpVeto.judge(yourWay, BetKind.PROP, SharpVeto.Sport.BASKETBALL, 113, false, 0.0).book)
    }

    @Test
    fun `a live game's taker fee comes off the sharp book's edge`() {
        // Kalshi -105/-105: 50% fair. +103 pregame is +EV; the same price live, with Novig's taker fee, isn't.
        val page = view(CnoBookPrice("KI", odds = -105, otherOdds = -105))
        assertEquals(SharpVeto.Verdict.PASSED, SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 103, live = false, minEv = 0.0).verdict)
        assertEquals(SharpVeto.Verdict.VETOED, SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 103, live = true, minEv = 0.0).verdict)
    }

    @Test
    fun `the veto's bar: the sharpest book must give Novig's price at least the set edge (1% by default)`() {
        // RESEARCH.md §72: what a bet keeps at the close is about the sharp book's own edge; 0-1% of it was not distinguishable from nothing.
        assertEquals(0.01, ScanSettings().sharpVetoMinEv, 0.0)
        assertEquals(SharpVeto.DEFAULT_MIN_EV, ScanSettings().sharpVetoMinEv, 0.0)
        // Kalshi -105/-105: 50% fair. Novig +103 is +1.5% on it, +101 only +0.5%.
        val page = view(CnoBookPrice("KI", odds = -105, otherOdds = -105))
        val small = SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 101, false, minEv = 0.01)
        assertEquals(SharpVeto.Verdict.VETOED, small.verdict)
        assertEquals(0.005, small.ev!!, 1e-9)
        assertEquals(0.01, small.minEv, 0.0)
        assertEquals("Kalshi, the sharpest book for player props, gives Novig's price under the sharp veto's 1.0% edge", small.reason)
        // The old bar (any +EV) lets the same price through; +1.5% clears 1% but not 2%.
        assertEquals(SharpVeto.Verdict.PASSED, SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 101, false, minEv = 0.0).verdict)
        assertEquals(SharpVeto.Verdict.PASSED, SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 103, false, minEv = 0.01).verdict)
        val two = SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 103, false, minEv = 0.02)
        assertEquals(SharpVeto.Verdict.VETOED, two.verdict)
        assertEquals("Kalshi, the sharpest book for player props, gives Novig's price under the sharp veto's 2.0% edge", two.reason)
        // A sharp book that says it isn't +EV at all keeps the old words, whatever the bar.
        val against = SharpVeto.judge(view(CnoBookPrice("PN", odds = 120, otherOdds = -150)), BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false, minEv = 0.01)
        assertEquals("Pinnacle, the sharpest book for spreads, says it isn't +EV at Novig's price", against.reason)
        // No sharp book on the page is still no veto, with any bar; a negative bar is read as 0.
        assertEquals(SharpVeto.Verdict.NO_SHARP, SharpVeto.judge(null, BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false, minEv = 0.02).verdict)
        assertEquals(SharpVeto.Verdict.PASSED, SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 101, false, minEv = -0.05).verdict)
    }

    @Test
    fun `the bar is inclusive, and zero or less never passes`() {
        assertTrue(SharpVeto.passes(0.01, 0.01))
        assertFalse(SharpVeto.passes(0.0099, 0.01))
        assertTrue(SharpVeto.passes(0.0001, 0.0))
        assertFalse(SharpVeto.passes(0.0, 0.0))
        assertFalse(SharpVeto.passes(-0.01, 0.0))
        assertEquals(listOf(0.0, 0.005, 0.01, 0.015, 0.02), ScanSettings.SHARP_VETO_MIN_EV_CHOICES)
    }
}
