package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.reference.SxBetConvert
import com.tjshea.vigilant.data.scanner.Leagues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TCL118: the live fair from SX's exchange book, on the shapes the real engine journal and the real markets showed (research/sxbet/MAPPING_2026-10-18.md). */
class SxLiveBuildTest {
    private val nfl = Leagues.byNovigName("NFL")!!
    private val now = 1_000_000L

    private fun market(hash: String, type: Int, one: String, two: String, line: Double? = null, main: Boolean? = null) =
        SxBetConvert.Market(marketHash = hash, status = "ACTIVE", outcomeOneName = one, outcomeTwoName = two, teamOneName = "Dallas Cowboys", teamTwoName = "Philadelphia Eagles", type = type, gameTime = 1_700_000_000L, line = line,
            sportXeventId = "L1", sportId = 8, leagueId = 243, leagueLabel = "NFL", mainLine = main)

    /** A taker book with asks [a1] and [a2] and $200 of depth each. */
    private fun book(hash: String, a1: Double, a2: Double, at: Long = now - 1_000L) = SxBetConvert.Book(hash, SxBetConvert.Level(a1, 200.0), SxBetConvert.Level(a2, 200.0), at)

    private val info = SxLiveBuild.EventInfo(
        "L1", nfl, "Dallas Cowboys", "Philadelphia Eagles", 1_700_000_000_000L,
        listOf(
            market("ml", 226, "Dallas Cowboys", "Philadelphia Eagles"),
            market("sp", 342, "Dallas Cowboys +2.5", "Philadelphia Eagles -2.5", line = 2.5, main = true),
            market("sp2", 342, "Dallas Cowboys +9.5", "Philadelphia Eagles -9.5", line = 9.5),
            market("tot", 28, "Over 49.5", "Under 49.5", line = 49.5, main = true),
        ),
    )
    private val books = mapOf(
        "ml" to book("ml", 0.41, 0.60), "sp" to book("sp", 0.51, 0.50), "sp2" to book("sp2", 0.80, 0.22), "tot" to book("tot", 0.50, 0.52),
    )
    private fun score(changed: Long = now - 60_000L, finished: Boolean = false) = SxScore("L1", 8, 7, 3, "2nd Quarter", "300", now, changed, finished)

    @Test
    fun `the fair is the mid of the exchange spread and sums to one`() {
        val g = SxLiveBuild.game(info, books::get, score(), 2, now - 500L, now)!!
        val ml = g.lines.single { it.type == PinnLineType.MONEYLINE }
        // asks 0.41 (Dallas, outcome one) and 0.60 (Philadelphia): mid for Dallas = (1 + 0.41 - 0.60) / 2 = 0.405
        assertEquals(0.405, ml.fair.getValue(PinnSide.AWAY), 1e-9)
        assertEquals(0.595, ml.fair.getValue(PinnSide.HOME), 1e-9)
        assertEquals(1.0, ml.fair.values.sum(), 1e-9)
        assertEquals("away = SX teamOne, home = teamTwo (the matcher tries both orders)", "Philadelphia Eagles", g.home)
    }

    @Test
    fun `a spread is the main line, quoted as the HOME handicap`() {
        val g = SxLiveBuild.game(info, books::get, score(), 2, now - 500L, now)!!
        val sp = g.lines.single { it.type == PinnLineType.SPREAD }
        assertEquals("the main +2.5 line, not the +9.5 alternate", "sp", sp.marketHash)
        // outcome one is Dallas (away) at +2.5, so Philadelphia (home) is -2.5
        assertEquals(-2.5, sp.points!!, 1e-9)
        val tot = g.lines.single { it.type == PinnLineType.TOTAL }
        assertEquals(49.5, tot.points!!, 1e-9)
        assertEquals(0.49, tot.fair.getValue(PinnSide.OVER), 1e-9)
    }

    @Test
    fun `a price set before the last score change is not trusted`() {
        // the score moved 500 ms ago; every book was pushed 1 s ago, before it
        val g = SxLiveBuild.game(info, books::get, score(changed = now - 500L), 2, now - 500L, now)
        assertNull("no line until the exchange re-quotes after the score", g)
        val after = books.mapValues { (h, b) -> b.copy(atMs = now - 100L) }
        assertNotNull("a push after the score restores the lines", SxLiveBuild.game(info, after::get, score(changed = now - 500L), 2, now - 100L, now))
    }

    @Test
    fun `a game that is not in progress has no line`() {
        assertNull(SxLiveBuild.game(info, books::get, score(), 3, now, now))
        assertNull(SxLiveBuild.game(info, books::get, score(), 6, now, now))
        assertNull("a finished score with no fixture status", SxLiveBuild.game(info, books::get, score(finished = true), null, now, now))
        assertNotNull("a live unfinished score with no fixture status", SxLiveBuild.game(info, books::get, score(), null, now, now))
    }

    @Test
    fun `an old book stands only while the connection is heard, and a thin or crossed book is no price`() {
        val old = books.mapValues { (_, b) -> b.copy(atMs = now - 120_000L) }
        assertNotNull("quiet market, live connection", SxLiveBuild.game(info, old::get, score(changed = now - 300_000L), 2, now - 2_000L, now))
        assertNull("quiet market, connection silent for a minute", SxLiveBuild.game(info, old::get, score(changed = now - 300_000L), 2, now - 60_000L, now))
        val thin = mapOf("ml" to SxBetConvert.Book("ml", SxBetConvert.Level(0.41, 5.0), SxBetConvert.Level(0.60, 200.0), now - 1_000L))
        assertNull("one side only $5 deep", SxLiveBuild.game(info, thin::get, score(), 2, now, now))
        val crossed = mapOf("ml" to book("ml", 0.40, 0.55))
        assertNull("asks summing to 0.95", SxLiveBuild.game(info, crossed::get, score(), 2, now, now))
    }

    @Test
    fun `a nearly settled side is not offered`() {
        val lopsided = mapOf("ml" to book("ml", 0.97, 0.05))
        assertNull(SxLiveBuild.game(info, lopsided::get, score(), 2, now, now))
        assertTrue(SxLiveBuild.mid(book("x", 0.50, 0.52))!!.first < 0.5)
    }
}
