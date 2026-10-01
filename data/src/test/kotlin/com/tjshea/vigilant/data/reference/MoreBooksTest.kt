package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * More sportsbooks where they make fair odds better (Tj, 2026-09-30: "can I add more sports books to scan on vigilant either for cno scanner
 * or vigilant scanner? Can parlayapi do it? Would it make the app more accurate? If so, add sports books to each scanner"; RESEARCH.md §46):
 * PropLine reads Hard Rock, Bovada and Fliff too (free per book), The Odds API still asks 10 at most (its credits), and no line is
 * counted twice (LowVig is BetOnline's line).
 */
class MoreBooksTest {

    @Test
    fun `PropLine reads ten independent books by default, Hard Rock, Bovada and Fliff among them, LowVig not`() {
        assertEquals(
            listOf("pinnacle", "betonlineag", "draftkings", "fanduel", "betmgm", "betrivers", "hardrock", "bovada", "fliff", "fanatics"),
            PropLineClient.books(ScanSettings().referenceBooks),
        )
        assertFalse("lowvig" in ScanSettings().referenceBooks)
        // Kambi's line under a second name never joins BetRivers'.
        assertFalse(ScanSettings().referenceBooks.any { it == "betparx" || it == "unibet" })
    }

    @Test
    fun `The Odds API still asks at most ten books, the sharp ones first`() {
        val asked = ScanSettings().referenceBooks.filter { it != "novig" }.distinct().take(TheOddsApiClient.MAX_BOOKMAKERS_ONE_REGION)
        assertEquals(10, asked.size)
        assertEquals(listOf("pinnacle", "betonlineag"), asked.take(2))
        assertTrue(ScanSettings().referenceBooks.size > TheOddsApiClient.MAX_BOOKMAKERS_ONE_REGION)
        // Every default book is one Settings can show and untick.
        assertTrue(ScanSettings().referenceBooks.all { it in TheOddsApiClient.KNOWN_BOOKMAKERS })
    }

    @Test
    fun `settings saved before get the new books once, lose LowVig only beside BetOnline, and keep what Tj picked`() {
        val oldDefault = listOf("pinnacle", "betonlineag", "lowvig", "draftkings", "fanduel", "betmgm", "williamhill_us", "espnbet", "fanatics", "betrivers")
        val moved = ScanSettings(referenceBooks = oldDefault, schema = 10).migrate()
        assertEquals(12, moved.schema)
        assertFalse("lowvig" in moved.referenceBooks)
        assertTrue(moved.referenceBooks.containsAll(listOf("hardrockbet", "bovada", "fliff")))
        assertTrue(moved.referenceBooks.containsAll(oldDefault - "lowvig"))
        // LowVig picked without BetOnline is its own line there: kept.
        val lowvigOnly = ScanSettings(referenceBooks = listOf("pinnacle", "lowvig"), schema = 10).migrate()
        assertTrue("lowvig" in lowvigOnly.referenceBooks)
        // Once moved, a book Tj unticks stays off.
        val unticked = moved.copy(referenceBooks = moved.referenceBooks - "fliff").migrate()
        assertFalse("fliff" in unticked.referenceBooks)
    }

    @Test
    fun `the book once called ESPN BET goes by its name now`() {
        assertEquals("theScore Bet", TheOddsApiClient.bookTitle("espnbet"))
        assertEquals("Fliff", TheOddsApiClient.bookTitle("fliff"))
    }
}
