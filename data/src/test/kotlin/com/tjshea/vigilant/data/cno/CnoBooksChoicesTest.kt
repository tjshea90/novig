package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-09-28: "For the fewest books behind the fair price filter, add options for 1 and 2 books. Remove any option over 4 books". */
class CnoBooksChoicesTest {

    @Test
    fun `fewest books offers 1 to 4, nothing more, and a new install starts at 4`() {
        assertEquals(listOf(1, 2, 3, 4), ScanSettings.CNO_MIN_BOOKS_CHOICES)
        assertEquals(4, CnoFilters().minBooks)
        assertTrue(ScanSettings().migrate().cnoFilters.minBooks in ScanSettings.CNO_MIN_BOOKS_CHOICES)
    }

    @Test
    fun `a saved 5 or more becomes 4 once, and 1 to 4 stay`() {
        for (old in listOf(5, 6, 8, 10)) {
            val s = ScanSettings(cnoFilters = CnoFilters(minBooks = old), schema = 8).migrate()
            assertEquals("from $old", 4, s.cnoFilters.minBooks)
            assertTrue(s.schema >= 9)
        }
        for (kept in 1..4) assertEquals(kept, ScanSettings(cnoFilters = CnoFilters(minBooks = kept), schema = 8).migrate().cnoFilters.minBooks)
    }

    @Test
    fun `1 or 2 books let thin markets through the app's own check, 4 keeps them out`() {
        val row = CnoRow(0.05, event = "A @ B", market = "Point Spread", bet = "A -3.5", odds = 110, book = "Novig", books = 2)
        val snap = CnoSnapshot("https://crazyninjaodds.com/x", listOf(row), fetchedAtMs = 0L)
        for (n in listOf(1, 2)) assertEquals("$n+ books", 1, CnoChecks.screen(snap, CnoFilters(minBooks = n), 0L).picks.size)
        val four = CnoChecks.screen(snap, CnoFilters(minBooks = 4), 0L)
        assertEquals(0, four.picks.size)
        assertEquals(mapOf(CnoChecks.Reason.BOOKS to 1), four.hidden)
    }
}
