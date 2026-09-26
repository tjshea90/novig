package com.tjshea.vigilant.data.cno

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** Reading CrazyNinjaOdds' page, its delta replies and its +EV table (RESEARCH.md §18.1). */
class CnoPageTest {

    private val base = CnoView.DEFAULT.toHttpUrl()

    @Test
    fun `the form posts what a browser would - hidden state, chosen options, ticked boxes only, no buttons`() {
        val form = CnoPage.form(CnoFixtures.page())
        val fields = form.fields.toMap()
        assertEquals("state+one==", fields["__VIEWSTATE"]) // &#43; decoded
        assertEquals("valid/one", fields["__EVENTVALIDATION"])
        assertEquals("17", fields.entries.first { it.key.endsWith("DropDownListSportsbookSite_All") }.value)
        assertEquals("0", fields.entries.first { it.key.endsWith("DropDownListSport") }.value) // none selected → first option
        assertEquals("on", fields.entries.first { it.key.endsWith("CheckBoxIsMain") }.value)
        assertTrue(fields.keys.none { it.endsWith("CheckBoxIsLive") }) // unticked: not sent
        assertEquals("", fields.entries.first { it.key.endsWith("TextBoxMinimumOdds") }.value)
        assertEquals("0%", fields.entries.first { it.key.endsWith("TextBoxMinimumEVPercentage") }.value)
        assertTrue(fields["Text1"]!!.trim() == "textbox_str")
        assertTrue(fields.keys.none { it.contains("ButtonUpdate") || it == "ButtonRefresh" })

        assertEquals(CnoFixtures.SCRIPT_MANAGER, form.scriptManager)
        assertEquals(CnoFixtures.GRID_PANEL, form.gridPanel)
        assertEquals(CnoFixtures.TIMER, form.timer)
        assertEquals(CnoFixtures.BUTTON to "Update", form.button)
        assertEquals("./positive-ev.aspx?site_id=17&books_min=3", form.action)
    }

    @Test
    fun `a page without the AJAX pieces is reported as changed, not as an empty list`() {
        try {
            CnoPage.form("<html><form action='x'><input name='a' value='b'></form></html>")
            fail("expected CnoException")
        } catch (e: CnoException) {
            assertTrue(e.message!!.contains("page changed"))
        }
    }

    @Test
    fun `delta lengths count UTF-16 units including CRLF, so accents and line breaks don't shift records`() {
        val content = "a\r\nJosé Ramírez\r\n|pipe inside|"
        val body = CnoFixtures.delta(Triple("updatePanel", "p1", content), Triple("hiddenField", "__VIEWSTATE", "v|2"))
        val records = CnoPage.delta(body)
        assertEquals(2, records.size)
        assertEquals(content, records[0].content)
        assertEquals("v|2", records[1].content)
        try {
            CnoPage.delta("999|updatePanel|p|short|")
            fail("a cut-off reply must not parse")
        } catch (e: CnoException) {
            assertTrue(e.message!!.contains("cut-off"))
        }
    }

    @Test
    fun `rows read EV, start time, prices with dollars available, fair odds, books and links`() {
        val table = CnoPage.table(CnoFixtures.grid(), base)
        assertEquals("LW-WC", table.evLabel)
        assertNull(table.note)
        assertEquals(3, table.rows.size)
        val a = table.rows[0]
        assertEquals(0.1159, a.ev, 1e-9)
        assertEquals(Instant.parse("2026-09-27T20:05:00Z").toEpochMilli(), a.startsAtMs)
        assertEquals("NFL", a.league)
        assertEquals("Away Team @ Home Team", a.event)
        assertEquals("Player Receptions", a.market)
        assertEquals("Joe Receiver Over 4.5", a.bet)
        assertEquals(135, a.odds)
        assertEquals(12.0, a.available!!, 0.0)
        assertEquals("Novig", a.book)
        assertEquals(118, a.fairOdds)
        assertEquals(0.4587, a.fairProbability!!, 1e-9)
        assertEquals(5, a.books)
        assertEquals("https://crazyninjaodds.com/site/browse/game.aspx?game_id=9&market_id=8&side_id=1&devig_method=0", a.gameUrl)
        assertEquals("https://crazyninjaodds.com/site/redirect/deeplink.aspx?line_id=1", a.betUrl)

        val b = table.rows[1]
        assertEquals(-110, b.odds)
        assertEquals(1250.0, b.available!!, 0.0) // "$1,250"
        assertEquals("José Ramírez Under 0.5", b.bet)
        assertEquals(Instant.parse("2026-09-28T00:15:00Z").toEpochMilli(), b.startsAtMs) // 12:15 AM

        val c = table.rows[2]
        assertEquals(100, c.odds) // EVEN
        assertNull(c.available)
        assertEquals("Pinnacle", c.book)
        assertTrue(table.rows.map { it.key }.toSet().size == 3)
    }

    @Test
    fun `columns are found by name, so a reordered table still reads right`() {
        val headers = """<th>Books</th><th>Odds</th><th>Bet Name</th><th>Market</th><th>Event</th><th>Sportsbook</th><th>UW-MC EV%</th>"""
        val row = """<tr data-fairpercentage="0.4"><td>7</td><td>+150 ($40)</td><td>Over 44.5</td><td>Total Points</td><td>A @ B</td><td>Novig</td><td>3.00%</td></tr>"""
        val table = CnoPage.table(CnoFixtures.grid(listOf(row), headers), base)
        val r = table.rows.single()
        assertEquals(150, r.odds)
        assertEquals(40.0, r.available!!, 0.0)
        assertEquals("Over 44.5", r.bet)
        assertEquals(0.03, r.ev, 1e-9)
        assertEquals(7, r.books)
        assertEquals("UW-MC", table.evLabel)
        assertNull(r.startsAtMs)
    }

    @Test
    fun `an empty table and CNO's red message come through, a missing column is an error`() {
        val empty = CnoPage.table(CnoFixtures.grid(rows = emptyList(), note = "No results. Try loosening your filters."), base)
        assertTrue(empty.rows.isEmpty())
        assertEquals("No results. Try loosening your filters.", empty.note)
        try {
            CnoPage.table(CnoFixtures.grid(headers = "<th>EV%</th><th>Something</th>"), base)
            fail("expected CnoException")
        } catch (e: CnoException) {
            assertTrue(e.message!!.contains("columns changed"))
        }
    }

    @Test
    fun `CNO's last-updated line becomes an age in seconds`() {
        assertEquals(27, CnoPage.lastUpdatedSeconds(CnoFixtures.info("27 seconds ago")))
        assertEquals(120, CnoPage.lastUpdatedSeconds(CnoFixtures.info("2 minutes ago")))
        assertEquals(60, CnoPage.lastUpdatedSeconds(CnoFixtures.info("a minute ago")))
        assertEquals(3600, CnoPage.lastUpdatedSeconds(CnoFixtures.info("1 hour ago")))
        assertNull(CnoPage.lastUpdatedSeconds("Last Updated: Loading..."))
    }

    @Test
    fun `odds cells`() {
        assertEquals(335, CnoPage.parseOdds("+335 (\$4)"))
        assertEquals(-133, CnoPage.parseOdds("-133 (\$35)"))
        assertEquals(-133, CnoPage.parseOdds("−133"))
        assertEquals(100, CnoPage.parseOdds("EVEN"))
        assertNull(CnoPage.parseOdds("N/A"))
    }
}
