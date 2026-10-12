package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live fair from BoltOdds' soft books (Tj, 2026-10-12): a quorum of companies, fresh, agreeing; else no line. */
class BoltLiveConsensusTest {
    private val now = 1_800_000_000_000L
    private val start = now - 20 * 60_000L

    private fun out(label: String, odds: Int, name: String, target: String? = null, line: Double? = null, ou: String? = null, at: Long = now - 1_000L) =
        label to BoltOutcome(label, odds, name, line, ou, target, at)

    /** A book's live game: home ML, spread -3.5 and total 220.5 priced by [ml] (home American odds, away the mirror image at ~4.5% hold). */
    private fun book(slug: String, ml: Int, live: Boolean = true, updated: Long = now - 1_000L, spreadHome: Double = -3.5, total: Double = 220.5): BoltBookGame {
        val away = -ml + if (ml < 0) 9 else -9   // roughly the other side with a hold
        val outcomes = mapOf(
            out("$slug ml home", ml, "Moneyline", "Boston Celtics"),
            out("$slug ml away", if (ml < 0) 100 + (-ml - 100) * 0 + 120 else -140, "Moneyline", "Miami Heat"),
            out("$slug sp home", -110, "Spread", "Boston Celtics", spreadHome),
            out("$slug sp away", -110, "Spread", "Miami Heat", -spreadHome),
            out("$slug o", -110, "Total", null, total, "Over"),
            out("$slug u", -110, "Total", null, total, "Under"),
        )
        return BoltBookGame("NBA", slug, "Miami Heat vs Boston Celtics, 2026-10-12, 01", "Boston Celtics", "Miami Heat", start, outcomes, updated, live)
    }

    // five different companies
    private val slugs = listOf("draftkings", "fanduel", "caesars", "hardrock", "betrivers")

    private fun games(vararg extra: BoltBookGame) = slugs.map { book(it, -150) } + extra

    @Test fun `a quorum of fresh companies makes a moneyline, a main spread and a main total`() {
        val g = BoltLiveConsensus.build(games(), now).single()
        assertEquals("NBA", g.league)
        assertEquals(3, g.sportId)
        val ml = g.lines.single { it.type == PinnLineType.MONEYLINE }
        assertEquals(5, ml.voters)
        assertEquals(1.0, ml.fair.getValue(PinnSide.HOME) + ml.fair.getValue(PinnSide.AWAY), 1e-9)
        assertTrue(ml.fair.getValue(PinnSide.HOME) > 0.5)
        assertEquals(-3.5, g.lines.single { it.type == PinnLineType.SPREAD }.points!!, 0.0)
        assertEquals(220.5, g.lines.single { it.type == PinnLineType.TOTAL }.points!!, 0.0)
        assertEquals(0.5, g.lines.single { it.type == PinnLineType.TOTAL }.fair.getValue(PinnSide.OVER), 1e-9)
    }

    @Test fun `fewer companies than the quorum make no line`() {
        assertTrue(BoltLiveConsensus.build(slugs.take(3).map { book(it, -150) }, now).isEmpty())
    }

    @Test fun `books of one company are one vote`() {
        // bwin, partysports and betmgm are one desk (BookCompany): with draftkings and fanduel that is three companies, under a quorum of four.
        val g = listOf("draftkings", "fanduel", "bwin", "partysports", "betmgm").map { book(it, -150) }
        assertTrue(BoltLiveConsensus.build(g, now).isEmpty())
        assertEquals(4, BoltLiveConsensus.build(g + book("caesars", -150), now).single().lines.first().voters)
    }

    @Test fun `a book that stopped pushing is out and the quorum is lost`() {
        val g = slugs.mapIndexed { i, s -> book(s, -150, updated = if (i < 2) now - 30_000L else now - 1_000L) }
        assertTrue("three fresh companies are under the quorum of four", BoltLiveConsensus.build(g, now).isEmpty())
        assertEquals(5, BoltLiveConsensus.build(g, now, BoltLiveConsensus.Rules(maxAgeMs = 60_000L)).single().lines.first().voters)
    }

    @Test fun `a game that is not live is not read`() {
        assertTrue(BoltLiveConsensus.build(slugs.map { book(it, -150, live = false) }, now).isEmpty())
    }

    @Test fun `companies that disagree by more than the cap make no moneyline`() {
        val g = slugs.mapIndexed { i, s -> book(s, if (i == 0) -400 else -150) }
        val lines = BoltLiveConsensus.build(g, now).singleOrNull()?.lines.orEmpty()
        assertNull(lines.firstOrNull { it.type == PinnLineType.MONEYLINE })
        // The spread and the total, which they agree on, stand.
        assertTrue(lines.any { it.type == PinnLineType.SPREAD })
    }

    @Test fun `the main spread is the number the most companies quote`() {
        val g = slugs.mapIndexed { i, s -> book(s, -150, spreadHome = if (i < 4) -3.5 else -4.5) }
        assertEquals(-3.5, BoltLiveConsensus.build(g, now).single().lines.single { it.type == PinnLineType.SPREAD }.points!!, 0.0)
    }

    @Test fun `five or more votes drop the highest and lowest`() {
        val g = slugs.mapIndexed { i, s -> book(s, when (i) { 0 -> -170; 4 -> -135; else -> -150 }) }
        val trimmed = BoltLiveConsensus.build(g, now).single().lines.first { it.type == PinnLineType.MONEYLINE }.fair.getValue(PinnSide.HOME)
        val plain = BoltLiveConsensus.build(g.take(4), now, BoltLiveConsensus.Rules(minCompanies = 4)).single().lines.first { it.type == PinnLineType.MONEYLINE }.fair.getValue(PinnSide.HOME)
        assertTrue(trimmed > 0.5 && plain > 0.5)
        assertEquals(3, BoltLiveConsensus.build(g, now).single().lines.first { it.type == PinnLineType.MONEYLINE }.books.size - 2)
    }

    @Test fun `the american price of a probability round-trips`() {
        assertEquals(-150.0, BoltLiveConsensus.american(0.6), 0.01)
        assertEquals(150.0, BoltLiveConsensus.american(0.4), 0.01)
    }
}
