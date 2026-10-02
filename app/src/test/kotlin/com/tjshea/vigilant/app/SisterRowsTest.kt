package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.BookTableText
import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02 16:05Z, with a screenshot of CNO's bet sheet (Juwan Johnson Under 39.5, "4 of 4 books agree", rows BetMGM and BetMGM (ON) both
 * -115/-115, 50.0%): "notice betmgm and betmgm (on). Is the app still double counting these?" No: the check counts one vote a company (v0.44.2).
 * The table listed both rows with their own fair, which read like two votes; the second now says "same co." and the footnote names them.
 */
class SisterRowsTest {

    /** The sheet in Tj's screenshot, in its order. */
    private val prices = listOf(
        CnoBookPrice("PX", odds = -107, available = 342.0, otherOdds = -124, otherAvailable = 10.0),
        CnoBookPrice("KI", odds = -107, available = 352.0, otherOdds = -121, otherAvailable = 1250.0),
        CnoBookPrice("NV", odds = 113, available = 173.0, otherOdds = -127, otherAvailable = 990.0),
        CnoBookPrice("MGM", odds = -115, otherOdds = -115),
        CnoBookPrice("MGM-ON", odds = -115, otherOdds = -115),
        CnoBookPrice("BR", odds = -113, otherOdds = -118),
        CnoBookPrice("FD", otherOdds = -122),
        CnoBookPrice("DK", otherOdds = -130),
        CnoBookPrice("CZR", otherOdds = -125),
        CnoBookPrice("BB", otherOdds = -122),
        CnoBookPrice("TSB", otherOdds = -135),
        CnoBookPrice("B365", otherOdds = 105),
        CnoBookPrice("HR-IN", otherOdds = -125),
        CnoBookPrice("HR-FL", otherOdds = -125),
        CnoBookPrice("HR-IL", otherOdds = -125),
        CnoBookPrice("HR-OH", otherOdds = -125),
    )

    @Test
    fun `the screenshot's numbers count BetMGM once - four books, fair 48_9 percent, +104`() {
        val c = CnoBooks.check(CnoBooksView(bet = "Juwan Johnson Under 39.5", otherBet = "Juwan Johnson Over 39.5", prices = prices, fetchedAtMs = 0L), listOdds = 113)
        // ProphetX, Kalshi, BetMGM (both sites as one) and BetRivers: "4 of 4 books agree".
        assertEquals(4, c.twoSided)
        assertEquals(4, c.agreeing)
        // Counted twice it would be 5 books and the lower of mean 49.2% / median 49.4%: +103, not the sheet's +104 (48.9%).
        assertEquals(0.489, c.fairProbability!!, 0.0005)
        assertEquals(CnoBooks.Verdict.CONFIRMED, c.verdict)
    }

    @Test
    fun `the table shows a company's sites as one vote`() {
        val cells = BookTableText.fairCells(prices, CnoBooks.NOVIG)
        assertEquals(listOf("48.1%", "48.4%", "judged", "50.0%", "same co.", "49.4%"), cells.take(6))
        assertTrue(cells.drop(6).all { it == "—" })
        val note = BookTableText.footnote(prices, CnoBooks.NOVIG)
        assertTrue(note, note.endsWith(" One company's sites count once, at their average: BetMGM and BetMGM (ON)."))
        // Hard Rock's four sites price one side only here: not counted, so not named.
        assertFalse(note, note.contains("Hard Rock"))
    }

    @Test
    fun `sister sites at different prices show their average on the first, the number the check uses`() {
        val p = listOf(
            CnoBookPrice("HR-FL", odds = -110, otherOdds = -110), CnoBookPrice("DK", odds = -120, otherOdds = 100), CnoBookPrice("HR-IN", odds = -130, otherOdds = 110),
        )
        val cells = BookTableText.fairCells(p, CnoBooks.NOVIG)
        val avg = (CnoBooks.fairFor(-110, -110)!! + CnoBooks.fairFor(-130, 110)!!) / 2
        assertEquals(com.tjshea.vigilant.app.ui.Format.percent(avg), cells[0])
        assertEquals("same co.", cells[2])
        assertEquals(
            "Fair = that book's odds devigged worst case. Only books pricing both sides count; Novig is the price being judged.",
            BookTableText.footnote(p.take(2), CnoBooks.NOVIG),
        )
    }
}
