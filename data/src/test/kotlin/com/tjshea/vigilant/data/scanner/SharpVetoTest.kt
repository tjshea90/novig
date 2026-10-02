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
        val ok = SharpVeto.judge(page, "NFL", "Player Receiving Yards", "Juwan Johnson Under 39.5", 113, false)
        assertEquals(SharpVeto.Verdict.PASSED, ok.verdict)
        assertEquals("KI", ok.book)
        assertTrue(ok.ev!! > 0.0)
        assertNull(ok.reason)
        // Novig at -110 instead: Kalshi's own fair (48.4%) makes it -7.6%: vetoed, though ProphetX is lower in the ranking and BetMGM says nothing.
        val no = SharpVeto.judge(page, "NFL", "Player Receiving Yards", "Juwan Johnson Under 39.5", -110, false)
        assertEquals(SharpVeto.Verdict.VETOED, no.verdict)
        assertEquals("KI", no.book)
        assertEquals("Kalshi, the sharpest book for player props, says it isn't +EV at Novig's price", no.reason)
        assertTrue(no.detail, no.detail.startsWith("Kalshi -"))
        // Without Kalshi, ProphetX is the sharpest there.
        val px = SharpVeto.judge(view(CnoBookPrice("PX", odds = -107, otherOdds = -124), CnoBookPrice("KI", odds = -107)), BetKind.PROP, SharpVeto.Sport.FOOTBALL, 113, false)
        assertEquals("PX", px.book)
    }

    @Test
    fun `no sharp book pricing both sides is no veto, and Pinnacle doesn't judge a prop`() {
        val pinnacleOnly = view(CnoBookPrice("PN", odds = -150, otherOdds = 120), CnoBookPrice("KI", odds = -107))
        val r = SharpVeto.judge(pinnacleOnly, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 113, false)
        assertEquals(SharpVeto.Verdict.NO_SHARP, r.verdict)
        assertFalse(r.vetoed)
        assertNull(r.reason)
        assertEquals(SharpVeto.Verdict.NO_SHARP, SharpVeto.judge(null, BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false).verdict)
        // The same Pinnacle prices decide a spread: -150/+120 makes this side ~58%, so Novig's +113 passes; the other way round (+120/-150, ~43%) is a veto.
        val spread = SharpVeto.judge(pinnacleOnly, BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false)
        assertEquals(SharpVeto.Verdict.PASSED, spread.verdict)
        val against = SharpVeto.judge(view(CnoBookPrice("PN", odds = 120, otherOdds = -150)), BetKind.SPREAD, SharpVeto.Sport.FOOTBALL, 113, false)
        assertEquals(SharpVeto.Verdict.VETOED, against.verdict)
        assertEquals("Pinnacle, the sharpest book for spreads, says it isn't +EV at Novig's price", against.reason)
    }

    @Test
    fun `college game lines ask Circa before Pinnacle, and a sister site counts as its company`() {
        val page = view(CnoBookPrice("PN", odds = 120, otherOdds = -150), CnoBookPrice("CS", odds = -105, otherOdds = -115))
        assertEquals("CS", SharpVeto.judge(page, "NCAAF", "Point Spread", "Ohio State -7.5", 113, false).book)
        assertEquals("PN", SharpVeto.judge(page, "NFL", "Point Spread", "Kansas City Chiefs -7.5", 113, false).book)
        val yourWay = view(CnoBookPrice("FDYW", odds = -110, otherOdds = -110))
        assertEquals("FD", SharpVeto.judge(yourWay, BetKind.PROP, SharpVeto.Sport.BASKETBALL, 113, false).book)
    }

    @Test
    fun `a live game's taker fee comes off the sharp book's edge`() {
        // Kalshi -105/-105: 50% fair. +103 pregame is +EV; the same price live, with Novig's taker fee, isn't.
        val page = view(CnoBookPrice("KI", odds = -105, otherOdds = -105))
        assertEquals(SharpVeto.Verdict.PASSED, SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 103, live = false).verdict)
        assertEquals(SharpVeto.Verdict.VETOED, SharpVeto.judge(page, BetKind.PROP, SharpVeto.Sport.FOOTBALL, 103, live = true).verdict)
    }
}
