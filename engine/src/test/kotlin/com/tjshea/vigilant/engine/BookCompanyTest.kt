package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-11: books of one parent company are one vote in the fair price and are listed once; a book whose odds look wrong (an extreme outlier, or the favourite on the other side)
 * is left out of the fair price.
 */
class BookCompanyTest {

    private fun book(key: String, vararg american: Int, at: Long? = null) =
        BookPrices(key, key.replaceFirstChar { it.uppercase() }, american.map { Odds.americanToDecimal(it) }, at)

    private val settings = FairSettings(source = FairSource.MARKET_AVERAGE, method = DevigMethod.MULTIPLICATIVE, minBooks = 1, oneBookPerCompany = true, dropBadOdds = true)

    @Test
    fun `sister sites are one company`() {
        assertEquals(BookCompany.of("fanduel"), BookCompany.of("fanduel_yourway"))
        assertEquals(BookCompany.of("hardrockbet"), BookCompany.of("HR-FL"))
        assertEquals(BookCompany.of("bet365"), BookCompany.of("bet365.de"))
        assertEquals(BookCompany.of("williamhill_us"), BookCompany.of("caesars"))
        assertEquals(BookCompany.of("betonlineag"), BookCompany.of("lowvig"))
        assertEquals(BookCompany.of("Hard Rock Bet"), BookCompany.of("hardrockbet"))
        // Tj, 2026-10-22: Bodog is Ozoon since 2026-02 (same sportsbook), Bovada is its US brand; Borgata runs on BetMGM.
        assertEquals(BookCompany.of("bovada"), BookCompany.of("Ozoon"))
        assertEquals(BookCompany.of("bovada"), BookCompany.of("bodog"))
        assertEquals(BookCompany.of("betmgm"), BookCompany.of("Borgata Online"))
        // Researched 2026-10-11 (RESEARCH.md §130): Flutter, Evoke, Entain, Penn, Fanatics+PointsBet US, Kambi's BetRivers/betPARX, Super Group.
        assertEquals(BookCompany.of("fanduel"), BookCompany.of("sportsbet_au"))
        assertEquals(BookCompany.of("williamhill"), BookCompany.of("mrgreen"))
        assertEquals(BookCompany.of("ladbrokes"), BookCompany.of("eurobet"))
        assertEquals(BookCompany.of("espnbet"), BookCompany.of("thescorebet"))
        assertEquals(BookCompany.of("fanatics"), BookCompany.of("pointsbetus"))
        assertEquals(BookCompany.of("betrivers"), BookCompany.of("betparx"))
        assertEquals(BookCompany.of("betrivers"), BookCompany.of("betrivers_ca"))
        assertEquals(BookCompany.of("unibet"), BookCompany.of("unibet_nl"))
        assertEquals(BookCompany.of("betway"), BookCompany.of("betway_mz"))
    }

    @Test
    fun `unconfirmed or separate owners are not merged`() {
        assertNotEquals(BookCompany.of("pointsbetau"), BookCompany.of("fanatics"))
        assertNotEquals(BookCompany.of("wynnbet"), BookCompany.of("windcreek"))
        assertNotEquals(BookCompany.of("bookmakereu"), BookCompany.of("bovada"))
        assertNotEquals(BookCompany.of("betmgm"), BookCompany.of("caesars"))
    }

    @Test
    fun `different companies and unknown books stay apart`() {
        assertNotEquals(BookCompany.of("draftkings"), BookCompany.of("fanduel"))
        assertNotEquals(BookCompany.of("williamhill"), BookCompany.of("williamhill_us"))
        assertNotEquals(BookCompany.of("betfair_ex_eu"), BookCompany.of("betfairsportsbook"))
        assertNotEquals(BookCompany.of("mystery1"), BookCompany.of("mystery2"))
        assertEquals("mystery1", BookCompany.of("mystery1"))
    }

    @Test
    fun `a company counts once in the fair line`() {
        // Three Hard Rock clones at one price must not outvote DraftKings and FanDuel.
        val books = listOf(
            book("hardrockbet", -120, 100), book("hardrock", -120, 100), book("HR-NJ", -120, 100),
            book("draftkings", -150, 130), book("fanduel", -150, 130),
        )
        val once = FairValue.compute(books, settings)!!
        assertEquals(3, once.perBook.size)
        val counted = FairValue.compute(books, settings.copy(oneBookPerCompany = false, dropBadOdds = false))!!
        assertEquals(5, counted.perBook.size)
        assertTrue("with clones counted the line leans to the clones' price", counted.probabilities[0] < once.probabilities[0])
    }

    @Test
    fun `the sharp member of a company is the one kept`() {
        val books = listOf(book("betonlineag", -110, -110, at = 9), book("lowvig", -108, -108, at = 1))
        val sharp = settings.copy(sharpBooks = setOf("lowvig"))
        val line = FairValue.compute(books, sharp)!!
        assertEquals(listOf("lowvig"), line.perBook.map { it.book.bookKey })
    }

    @Test
    fun `a book with the favourite on the wrong side is left out`() {
        val books = listOf(
            book("draftkings", -200, 170), book("fanduel", -190, 160), book("betrivers", -210, 175), book("espnbet", 170, -200),
        )
        val line = FairValue.compute(books, settings)!!
        assertEquals(setOf("draftkings", "fanduel", "betrivers"), line.perBook.map { it.book.bookKey }.toSet())
        assertEquals(listOf("espnbet"), line.excluded.map { it.bookKey })
        assertTrue(line.probabilities[0] > 0.6)
    }

    @Test
    fun `an extreme outlier is left out but ordinary disagreement is kept`() {
        val ordinary = listOf(book("draftkings", -150, 130), book("fanduel", -160, 135), book("betrivers", -140, 120), book("espnbet", -155, 130))
        assertTrue(FairValue.compute(ordinary, settings)!!.excluded.isEmpty())
        val withStale = ordinary + book("bovada", 250, -300 )
        val line = FairValue.compute(withStale, settings)!!
        assertEquals(listOf("bovada"), line.excluded.map { it.bookKey })
    }

    @Test
    fun `two books that contradict each other keep only the sharp one, or none`() {
        val a = book("pinnacle", -200, 170)
        val b = book("draftkings", 170, -200)
        val sharp = FairValue.compute(listOf(a, b), settings.copy(sharpBooks = setOf("pinnacle")))!!
        assertEquals(listOf("pinnacle"), sharp.perBook.map { it.book.bookKey })
        assertNull(FairValue.compute(listOf(book("fanduel", -200, 170), b), settings))
    }

    @Test
    fun `off, nothing is consolidated or screened`() {
        val books = listOf(book("hardrockbet", -120, 100), book("hardrock", -120, 100), book("draftkings", 170, -200), book("fanduel", -200, 170))
        val line = FairValue.compute(books, FairSettings(minBooks = 1))!!
        assertEquals(4, line.perBook.size)
        assertFalse(line.excluded.isNotEmpty())
    }

    /** Tj, 2026-10-11: "6 of 6 books agree" on Casper Ruud +0.5, all at +100 / -134: six owners, one Kambi trading desk. */
    @Test
    fun `books on Kambi's one trading desk are one company whoever owns them`() {
        val desk = listOf("Bally Bet", "betparx", "casumo", "grosvenor", "tabtouch", "unibet", "betrivers", "sugarhouse", "Unibet (US)", "coolbet", "superbet", "livescorebet", "virginbet", "leovegas", "unibet_nl", "betrivers_ca")
        assertEquals(1, desk.map { BookCompany.of(it) }.distinct().size)
        // Other operators stay apart.
        assertNotEquals(BookCompany.of("unibet"), BookCompany.of("draftkings"))
        assertNotEquals(BookCompany.of("ballybet"), BookCompany.of("fanduel"))
        // Six identical Kambi quotes count once in the fair line.
        val six = desk.take(6).map { book(it, 100, -134) } + book("draftkings", 105, -135) + book("fanduel", 105, -135)
        val line = FairValue.compute(six, settings)!!
        assertEquals(3, line.perBook.size)
    }

    /** Tj, 2026-10-12: Dalton Kincaid Over 56.5, bwin / partysports / BetMGM / sportsinteraction all -120 / -111 with the same hold, counted as "4 of 4 books agree". One Entain desk, one vote. */
    @Test
    fun `Entain's brands and its BetMGM joint venture are one company, so the four identical rows are one book`() {
        val all = listOf("bwin", "partysports", "BetMGM", "sportsinteraction", "BetMGM (ON)", "betmgm_on", "Sports Interaction", "Party Sports", "si").map(BookCompany::of).toSet()
        assertEquals(1, all.size)
        assertEquals(BookCompany.of("ladbrokes"), BookCompany.of("bwin"))
        assertNotEquals(BookCompany.of("bwin"), BookCompany.of("fanduel"))
    }
}
