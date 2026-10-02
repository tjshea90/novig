package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.tracker.BetInsight
import com.tjshea.vigilant.data.tracker.BookLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tj, 2026-10-02: "In the scanners, especially cno scanner, it is counting identical odds from sister sports books (for example, multiple hard rock
 * sports books just in different states). Investigate if this is smart to do, and if not, don't let vigilant double count odds from the same company
 * sports books." It isn't: one company's state sites price from one trading desk, so four of them are one opinion counted four times (RESEARCH.md
 * §46). CNO's book check now counts one vote a company, the average of its sites.
 */
class SisterBooksTest {

    private fun view(vararg p: CnoBookPrice) = CnoBooksView(bet = "Under 52.5", otherBet = "Over 52.5", prices = p.toList(), fetchedAtMs = 0L)

    @Test
    fun `a state's site is its company's, and FanDuel YourWay is FanDuel`() {
        assertEquals("HR", CnoBooks.company("HR-FL"))
        assertEquals("HR", CnoBooks.company("HR-IN"))
        assertEquals("ST", CnoBooks.company("ST-NJ"))
        assertEquals("MGM", CnoBooks.company("MGM-ON"))
        assertEquals("MGM", CnoBooks.company("MGM"))
        assertEquals("FD", CnoBooks.company("FDYW"))
        assertEquals("DK", CnoBooks.company("DK"))
        assertEquals("HR", CnoBooks.companyOfName("Hard Rock (IL)"))
        assertEquals("bookmaker x", CnoBooks.companyOfName("Bookmaker X"))
    }

    @Test
    fun `four Hard Rock state sites and DraftKings are two books, not five, and can't confirm a bet alone`() {
        // Hard Rock's four sites at one price, all +EV against Novig's +115; DraftKings says it isn't.
        val hr = listOf("HR-IN", "HR-FL", "HR-IL", "HR-OH").map { CnoBookPrice(it, odds = 105, otherOdds = -125) }
        val v = view(*(hr + CnoBookPrice("DK", odds = 130, otherOdds = -150) + CnoBookPrice("NV", odds = 115)).toTypedArray())
        val c = CnoBooks.check(v, listOdds = 115)
        assertEquals(2, c.twoSided)
        assertEquals("Hard Rock once", 1, c.agreeing)
        assertEquals("two companies are too few to confirm", CnoBooks.Verdict.THIN, c.verdict)
        // The consensus is Hard Rock's line and DraftKings', one each: not four parts Hard Rock.
        val hrFair = CnoBooks.fairFor(105, -125)!!
        val dkFair = CnoBooks.fairFor(130, -150)!!
        assertEquals(CnoBooks.consensus(listOf(hrFair, dkFair))!!, c.fairProbability!!, 1e-12)
    }

    @Test
    fun `sites of one company that differ are averaged into its one vote, and one-sided ones count once`() {
        val v = view(
            CnoBookPrice("ST-NJ", odds = 110, otherOdds = -130), CnoBookPrice("ST-CO", odds = 100, otherOdds = -120),
            CnoBookPrice("PN", odds = 102, otherOdds = -112), CnoBookPrice("CS", odds = 104, otherOdds = -114),
            CnoBookPrice("HR-FL", odds = 108), CnoBookPrice("HR-OH", odds = 108), CnoBookPrice("NV", odds = 115),
        )
        val c = CnoBooks.check(v, listOdds = 115)
        assertEquals(3, c.twoSided)
        assertEquals("Hard Rock's one-sided sites: one company", 1, c.oneSided)
        val st = (CnoBooks.fairFor(110, -130)!! + CnoBooks.fairFor(100, -120)!!) / 2
        val expected = CnoBooks.consensus(listOf(st, CnoBooks.fairFor(102, -112)!!, CnoBooks.fairFor(104, -114)!!))!!
        assertEquals(expected, c.fairProbability!!, 1e-12)
    }

    @Test
    fun `the Tracker's own read of a bet's books counts a company once too`() {
        val bet = TrackedBet(
            "b", 0L, "NCAAF", "Stanford @ Wake Forest", 10_000L, "Total", "Under 52.5", "m", "o", 0.465, 0.465, 0.48, 0.03, 1.0,
            american = 115,
            books = listOf(
                BookLine("Hard Rock", 105, -125), BookLine("Hard Rock (FL)", 105, -125), BookLine("Hard Rock (IL)", 105, -125),
                BookLine("DraftKings", -105, -115), BookLine("Novig", 115, -135),
            ),
        )
        val i = BetInsight.of(bet)
        assertEquals(2, i.booksBehind)
        assertEquals(CnoBooks.consensus(listOf(CnoBooks.fairFor(105, -125)!!, CnoBooks.fairFor(-105, -115)!!))!!, i.fairNow!!, 1e-12)
    }
}
