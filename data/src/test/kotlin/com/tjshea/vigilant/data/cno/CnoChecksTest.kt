package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.engine.Odds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's own screen over CNO's list: only rows that add up, with enough books, inside Tj's caps. */
class CnoChecksTest {

    private val now = 1_000_000_000L

    /** A consistent row: CNO's EV is exactly what its fair probability gives at [odds]. */
    private fun row(bet: String, odds: Int, fair: Double, books: Int = 8, startsAt: Long? = now + 3_600_000, oneWay: Boolean = false) =
        CnoRow(
            ev = fair * Odds.americanToDecimal(odds) - 1, startsAtMs = startsAt, event = "A @ B", market = "M", bet = bet,
            odds = odds, book = "Novig", fairProbability = fair, books = books, oneWay = oneWay, gameUrl = "https://x/g?side_id=$bet",
        )

    private fun snap(vararg rows: CnoRow) = CnoSnapshot("u", rows.toList(), now, cnoAgeSeconds = 20)

    @Test
    fun `good rows pass, best EV first`() {
        val a = row("A", 120, 0.48) // +5.6%
        val b = row("B", -110, 0.54) // +3.1%
        val s = CnoChecks.screen(snap(b, a), CnoFilters(), now)
        assertEquals(listOf("A", "B"), s.picks.map { it.row.bet })
        assertEquals(0, s.hiddenCount)
    }

    @Test
    fun `thin markets, longshots, one-way devigs, too-good-to-be-true and tiny edges are left out and counted`() {
        val s = CnoChecks.screen(
            snap(
                row("thin", 120, 0.48, books = 3),
                row("longshot", 250, 0.30),
                row("oneway", 110, 0.50, oneWay = true),
                row("stale", 140, 0.52), // +24.8%
                row("tiny", -110, 0.525), // +0.2%
                row("ok", 110, 0.50),
            ),
            CnoFilters(maxOdds = 150, minBooks = 5, minEv = 0.01),
            now,
        )
        assertEquals(listOf("ok"), s.picks.map { it.row.bet })
        assertEquals(
            mapOf(
                CnoChecks.Reason.BOOKS to 1, CnoChecks.Reason.ODDS to 1, CnoChecks.Reason.ONE_WAY to 1,
                CnoChecks.Reason.TOO_GOOD to 1, CnoChecks.Reason.EV to 1,
            ),
            s.hidden,
        )
    }

    @Test
    fun `+150 means negative odds and up to +150, and no cap means any odds`() {
        val rows = arrayOf(row("fav", -300, 0.77), row("even", 100, 0.52), row("cap", 150, 0.42), row("over", 155, 0.41))
        assertEquals(listOf("fav", "even", "cap").sorted(), CnoChecks.screen(snap(*rows), CnoFilters(maxOdds = 150, minEv = 0.0), now).picks.map { it.row.bet }.sorted())
        assertEquals(4, CnoChecks.screen(snap(*rows), CnoFilters(maxOdds = 0, minEv = 0.0), now).picks.size)
    }

    @Test
    fun `a row whose EV doesn't follow from its own fair odds is dropped as misread`() {
        val bad = row("bad", 120, 0.48).copy(ev = 0.09) // fair 48% at +120 is 5.6%, not 9%
        val s = CnoChecks.screen(snap(bad), CnoFilters(), now)
        assertTrue(s.picks.isEmpty())
        assertEquals(mapOf(CnoChecks.Reason.MISMATCH to 1), s.hidden)
    }

    @Test
    fun `a started game is judged net of Novig's taker fee, which can push it under the floor`() {
        val live = row("live", 110, 0.4880, startsAt = now - 60_000) // +2.5% before the fee
        val s = CnoChecks.screen(snap(live), CnoFilters(minEv = 0.0), now)
        val pick = s.picks.single()
        assertTrue(pick.live)
        assertTrue(pick.ev < live.ev)
        val floor = CnoChecks.screen(snap(live), CnoFilters(minEv = pick.ev + 0.001), now)
        assertTrue(floor.picks.isEmpty())
        assertEquals(mapOf(CnoChecks.Reason.EV to 1), floor.hidden)
    }

    @Test
    fun `CNO counts as stuck once its odds are over 10 minutes old`() {
        assertTrue(!CnoChecks.stuck(CnoSnapshot("u", emptyList(), now, cnoAgeSeconds = 599), now))
        assertTrue(CnoChecks.stuck(CnoSnapshot("u", emptyList(), now, cnoAgeSeconds = 601), now))
    }
}
