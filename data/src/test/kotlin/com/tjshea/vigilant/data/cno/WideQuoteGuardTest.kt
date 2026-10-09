package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.FairValue
import com.tjshea.vigilant.engine.WideQuotes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-09: ProphetX at -140 / -113 on Over/Under 46.5 (11.4% hold, $250 and $555 offered) was counted as a view of the price next to DraftKings -112 / -110. "Discount any quote from any
 * exchange or book if the quote is too wide ... on by default."
 */
class WideQuoteGuardTest {
    @After fun restore() { WideQuotes.enabled = true }

    private fun price(code: String, mine: Int, theirs: Int) = CnoBookPrice(code, mine, null, theirs, null)

    /** The screenshot's page: ProphetX, DraftKings, Hard Rock (four states, one company), Fliff; Novig is the price being judged. */
    private fun page() = CnoBooksView(
        "Over 46.5", "Under 46.5", 103, false,
        listOf(
            price("PX", -140, -113), price("NV", 108, -113), price("DK", -112, -110),
            price("HR-IN", -110, -110), price("HR-FL", -110, -110), price("HR-IL", -110, -110), price("HR-OH", -110, -110), price("FL", -115, -120),
        ),
        fetchedAtMs = 1L,
    )

    @Test fun theWideProphetXQuoteIsLeftOutOfTheFairLineByDefault() {
        val on = CnoBooks.check(page(), 108)
        assertEquals("DraftKings, Hard Rock (one company) and Fliff: three votes", 3, on.twoSided)
        assertEquals(0.4987, on.fairProbability!!, 0.0005)
        assertEquals(0.037, on.ev!!, 0.002)
        WideQuotes.enabled = false
        val off = CnoBooks.check(page(), 108)
        assertEquals("with the guard off ProphetX votes too, as before", 4, off.twoSided)
        assertEquals(0.5010, off.fairProbability!!, 0.0005)
        assertEquals(0.0422, off.ev!!, 0.002)
    }

    @Test fun anExchangeGetsTheStricterLimitAndABookTheLooserOne() {
        // -135 / -135: 57.4% x 2 = 14.9% hold: too wide for anyone. -125 / -125: 11.1%: too wide for an exchange, fine for a book. -118 / -118: 8.3%: fine for both.
        assertNull(CnoBooks.fairFor(-135, -135))
        assertNull(CnoBooks.fairFor(-135, -135, "DK"))
        assertNotNull(CnoBooks.fairFor(-125, -125, "DK"))
        assertNull(CnoBooks.fairFor(-125, -125, "PX"))
        assertNull(CnoBooks.fairFor(-125, -125, "KI"))
        assertNull(CnoBooks.fairFor(-125, -125, "ST-NJ"))
        assertNotNull(CnoBooks.fairFor(-118, -118, "PX"))
        assertNotNull("a real ProphetX prop page sits near 8%: counted", CnoBooks.fairFor(-107, -129, "PX"))
        assertNotNull("an ordinary -110 / -110", CnoBooks.fairFor(-110, -110, "PX"))
        assertEquals(0.5, CnoBooks.fairFor(-110, -110, "DK")!!, 1e-9)
        assertTrue(CnoBooks.isExchange("PX") && CnoBooks.isExchange("KI") && CnoBooks.isExchange("NV") && CnoBooks.isExchange("ST-CO") && !CnoBooks.isExchange("DK"))
    }

    @Test fun offMeansEveryQuoteCountsAsBefore() {
        WideQuotes.enabled = false
        assertNotNull(CnoBooks.fairFor(-135, -135, "PX"))
        assertFalse(CnoBooks.isWide(price("PX", -140, -113)))
    }

    @Test fun aWideQuoteIsFlaggedAndAnOrdinaryOneIsNot() {
        assertTrue(CnoBooks.isWide(price("PX", -140, -113)))
        assertFalse(CnoBooks.isWide(price("DK", -112, -110)))
        assertFalse(CnoBooks.isWide(CnoBookPrice("DK", -112, null, null, null)))   // one-sided: not "wide", just one-sided
    }

    @Test fun theVigilantScansOwnFairLineDropsTheSameQuotes() {
        fun d(american: Int) = com.tjshea.vigilant.engine.Odds.americanToDecimal(american)
        val books = listOf(
            BookPrices("prophetx", "ProphetX", listOf(d(-140), d(-113))),
            BookPrices("draftkings", "DraftKings", listOf(d(-112), d(-110))),
            BookPrices("hardrockbet", "Hard Rock", listOf(d(-110), d(-110))),
            BookPrices("fliff", "Fliff", listOf(d(-115), d(-120))),
        )
        val base = FairSettings(source = FairSource.MARKET_AVERAGE)
        val on = FairValue.compute(books, base.copy(wideGuard = true))!!
        assertEquals(listOf("DraftKings", "Hard Rock", "Fliff"), on.perBook.map { it.book.bookTitle })
        val off = FairValue.compute(books, base)!!
        assertEquals("the engine's own default is unchanged", 4, off.perBook.size)
        // a normal wide-ish book (Fliff, 8%) is a book, not an exchange: kept
        assertTrue(on.perBook.any { it.book.bookKey == "fliff" })
    }

    @Test fun theGuardIsOnByDefaultInSettingsAndReachesTheFairSettings() {
        assertTrue(ScanSettings().ignoreWideQuotes)
        assertTrue(ScanSettings().fairSettings().wideGuard)
        assertFalse(ScanSettings(ignoreWideQuotes = false).fairSettings().wideGuard)
    }
}
