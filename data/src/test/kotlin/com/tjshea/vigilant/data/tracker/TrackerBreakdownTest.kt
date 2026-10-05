package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.tracker.TrackerBreakdown.By
import org.junit.Assert.assertEquals
import org.junit.Test

/** "The goal is to see how well my positive EV bets profit with vigilant" (Tj, 2026-09-29). */
class TrackerBreakdownTest {

    private fun bet(
        id: String, league: String = "NFL", market: String = "Moneyline", selection: String = "Dallas Cowboys",
        ev: Double? = 0.03, status: BetStatus = BetStatus.WON, american: Int = 100, source: String = BetTracker.SOURCE_CNO,
    ): TrackedBet {
        val cost = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(american)
        return TrackedBet(
            id, 0, league, "Baltimore Ravens @ Dallas Cowboys", 0, market, selection, "m", "o", cost, cost,
            ev?.let { cost * (1 + it) }, ev, 1.0, status, american = american, source = source,
        )
    }

    @Test
    fun `bets split by league, with each group's own record and profit, the most-settled first`() {
        val rows = TrackerBreakdown.of(
            listOf(bet("1", "NFL"), bet("2", "NFL", status = BetStatus.LOST), bet("3", "MLB"), bet("4", "", status = BetStatus.PENDING)),
            By.LEAGUE,
        )
        assertEquals(listOf("NFL", "MLB", "Unknown"), rows.map { it.label })
        val nfl = rows.first()
        assertEquals(1, nfl.stats.won)
        assertEquals(1, nfl.stats.lost)
        assertEquals(0.0, nfl.stats.profit, 1e-12) // +$1 at even money, -$1
        assertEquals(1, rows.last().stats.pending)
    }

    @Test
    fun `the kind of market is read the way grading reads it`() {
        fun kind(market: String, selection: String) = TrackerBreakdown.marketOf(bet("x", market = market, selection = selection))
        assertEquals("Moneyline", kind("Moneyline", "Dallas Cowboys"))
        assertEquals("Spread", kind("Point Spread", "Dallas Cowboys -3.5"))
        assertEquals("1st half / set spread", kind("F5 Spread", "New York Mets -0.5"))
        assertEquals("Total", kind("Total Points", "Over 47.5"))
        assertEquals("Total", kind("Alternate Total", "Over 47.5"))
        assertEquals("Team total", kind("Team Total Points", "Dallas Cowboys Over 24.5"))
        assertEquals("Player props", kind("Player Receptions", "Dalton Schultz Over 5.5"))
        assertEquals("Player props", kind("Player Shots on Goal", "Matt Boldy Over 2.5"))
        assertEquals("Other", kind("Some Novelty Market", "Something Over 1.5"))
    }

    @Test
    fun `edge and price bands, and outliers stay out of every row`() {
        assertEquals("Under 1%", TrackerBreakdown.evBand(0.005))
        assertEquals("2–3%", TrackerBreakdown.evBand(0.025))
        assertEquals("4% and up", TrackerBreakdown.evBand(0.055))
        assertEquals("No EV on record", TrackerBreakdown.evBand(null))
        assertEquals("Heavy favorite (−200 or shorter)", TrackerBreakdown.oddsBand(-250))
        assertEquals("Even money (−110 to +110)", TrackerBreakdown.oddsBand(-110))
        assertEquals("Underdog (+110 to +200)", TrackerBreakdown.oddsBand(150))
        assertEquals("Long shot (over +200)", TrackerBreakdown.oddsBand(350))
        val rows = TrackerBreakdown.of(listOf(bet("a", ev = 0.03), bet("b", ev = 0.25), bet("c", ev = null)), By.EV)
        // The 25% bet is an outlier: it's in no row at all.
        assertEquals(setOf("3–4%", "No EV on record"), rows.map { it.label }.toSet())
        assertEquals(2, rows.sumOf { it.stats.bets })
    }

    @Test
    fun `scanner split names them the way the Tracker does`() {
        val rows = TrackerBreakdown.of(listOf(bet("1"), bet("2", source = BetTracker.SOURCE_VIGILANT), bet("3", source = BetTracker.SOURCE_VIGILANT)), By.SCANNER)
        assertEquals(listOf("Vigilant", "CNO"), rows.map { it.label })
    }

    @Test
    fun `bets split by how long before the start they were placed, nearest to the start first whatever the counts (RESEARCH 82)`() {
        val start = 1_000_000_000_000L
        fun at(id: String, hoursBefore: Double, status: BetStatus = BetStatus.WON) =
            bet(id, status = status).copy(createdAtMs = start - (hoursBefore * 3_600_000).toLong(), startsTs = start)
        val rows = TrackerBreakdown.of(
            listOf(at("a", 30.0), at("b", 30.0), at("c", 1.0, BetStatus.LOST), at("d", 10.0), at("e", 0.1), at("f", 3.0)),
            By.LEAD,
        )
        // 24 h or more has the most settled bets (2) and still comes last.
        assertEquals(listOf("under 30 min", "30 min-2 h", "2-6 h", "6-24 h", "24 h or more"), rows.map { it.label })
        assertEquals(2, rows.last().stats.won)
        assertEquals("Time to start", By.LEAD.label)
    }
}
