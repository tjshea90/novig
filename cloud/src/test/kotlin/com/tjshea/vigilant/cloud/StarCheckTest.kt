package com.tjshea.vigilant.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The star rating check over the engine's lines (FX, 2026-10-23): the CSV is read the way tools/lines_dataset.py writes it, and games, not bets, are the unit of the intervals. */
class StarCheckTest {

    private val head = "id,day,first_seen_utc,starts_utc,min_to_start,league,sport,kind,market,selection,event,shown,not_shown_why,american,cost,ev,fair,fair_american,fair_method," +
        "fair_age_s,novig_age_s,avail_usd,books_two_sided,books_agreeing,agree_share,sharp_in_fair,sharp_agrees,sharp_verdict,stars,fair_spread,n_fair_books,n_looks,ev_max,ev_last," +
        "last_listed_min_to_start,gone,status,profit_units,close_fair,close_via,clv,clv_note,market_id,outcome_id"

    private fun line(id: String, event: String, ev: Double, books: Int, agreeing: Int, sharp: String, status: String, profit: String, clv: String, kind: String = "TOTAL", sel: String = "Under 8.5") =
        "$id,2026-10-20,,,120,MLB,BASEBALL,$kind,Total Runs,$sel,\"$event\",1,,+100,0.5,$ev,0.52,,blend,60,5,100,$books,$agreeing,,1,$sharp,,3.0,0.01,$books,1,,,,,$status,$profit,0.53,SGO,$clv,,m,o"

    @Test
    fun `reads quoted fields, keeps only +EV lines once, and maps the sharp column`() {
        val ls = StarCheck.parse(sequenceOf(head,
            line("a", "Cubs @ Sox, game 1", 0.03, 5, 5, "1", "WON", "1.0", "0.04"),
            line("a", "Cubs @ Sox, game 1", 0.03, 5, 5, "1", "WON", "1.0", "0.04"),
            line("b", "Cubs @ Sox, game 1", -0.01, 5, 0, "0", "LOST", "-1.0", "-0.02"),
            line("c", "Mets @ Nats", 0.05, 2, 2, "", "PENDING", "", "")))
        assertEquals(listOf("a", "a", "c"), ls.map { it.id })
        assertEquals("Cubs @ Sox, game 1", ls[0].event)
        assertEquals(true, ls[0].sharp)
        assertNull(ls[2].sharp)
        assertEquals(0.04, ls[0].clv!!, 1e-9)
        assertTrue(ls[0].settled)
        assertTrue(!ls[2].settled)
        assertEquals(120L, ls[0].minToStart)
    }

    @Test
    fun `an interval counts games, so lines of one game are averaged first`() {
        // Ten lines of one game and nothing else: one game, no interval.
        val one = StarCheck.stat(List(10) { "g1" to 0.02 })
        assertEquals(1, one.games)
        assertNull(one.half)
        // Five games: an interval, around the mean of the game means (not of the lines).
        val five = StarCheck.stat(listOf("g1" to 0.10, "g1" to 0.10, "g1" to 0.10, "g2" to 0.0, "g3" to 0.0, "g4" to 0.0, "g5" to 0.0))
        assertEquals(5, five.games)
        assertEquals(0.02, five.mean!!, 1e-9)
        assertTrue(five.half!! > 0.0)
    }

    @Test
    fun `the report re-rates every line and prints each input on its own`() {
        val rows = (1..30).map { i -> line("x$i", "Game $i", 0.01 + i * 0.002, 1 + i % 6, i % 6, if (i % 3 == 0) "1" else "", if (i % 2 == 0) "WON" else "LOST", if (i % 2 == 0) "0.9" else "-1.0", "0.0${i % 5}") }
        val text = StarCheck.report(StarCheck.parse(sequenceOf(head) + rows.asSequence()), profile = null)
        assertTrue(text, text.contains("STAR RATING CHECK") && text.contains("30 +EV lines"))
        for (title in listOf("Stars now (re-rated)", "Stars as logged", "Companies pricing both sides", "Sharp book", "Lead time", "History + lead points", "CLV per star")) assertTrue(title, text.contains(title))
        assertEquals("Game totals", StarCheck.kindLabel("TOTAL"))
        // A line logged before the rating's inputs were recorded is left out of the tables (it would be rated on missing data), and counted.
        val old = "y1,2026-10-15,,,120,NBA,BASKETBALL,PROP,Points,Over 20.5,\"Old game\",1,,+100,0.5,0.03,0.52,,blend,60,5,100,,,,0,,,,,0,1,,,,,WON,1.0,0.53,SGO,0.05,,m,o"
        val withOld = StarCheck.report(StarCheck.parse(sequenceOf(head, old) + rows.asSequence()), profile = null)
        assertTrue(withOld, withOld.contains("30 +EV lines") && withOld.contains("Left out: 1 lines"))
        assertEquals("Other", StarCheck.kindLabel("SOMETHING"))
        // Every variant is scored, on all lines and on each half.
        assertTrue(text, text.contains("Weights side by side") && StarCheck.VARIANTS.all { text.contains(it.first) })
    }

    @Test
    fun `the slope of a clean line is found with a large t`() {
        val (b, t) = StarCheck.slope((1..20).map { it.toDouble() to 0.01 * it + if (it % 2 == 0) 0.001 else -0.001 })!!
        assertEquals(0.01, b, 1e-3)
        assertTrue(t > 10)
    }
}
