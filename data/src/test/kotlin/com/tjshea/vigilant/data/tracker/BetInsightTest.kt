package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The open bet's sheet (Tj, 2026-09-29): the odds bet at against the fair odds now, the difference between
 * them, and every book's price for the same bet.
 */
class BetInsightTest {

    private fun bet(
        american: Int = 150,
        cost: Double = 0.4,
        fairAtBet: Double? = 0.42,
        ev: Double? = 0.05,
        nowFair: Double? = null,
        books: List<BookLine> = emptyList(),
        book: String = "Novig",
    ) = TrackedBet(
        "b", 0, "NFL", "A @ B", 1_000, "Player Receptions", "Dalton Schultz Over 5.5", "m", "o", price = cost, cost = cost,
        fairAtBet = fairAtBet, evPercentAtBet = ev, stake = 10.0, american = american, nowFair = nowFair, books = books, book = book,
        booksAtMs = 500L, nowAmerican = 145,
    )

    private val books = listOf(
        BookLine("Pinnacle", 140, -160), BookLine("DraftKings", 135, -165), BookLine("FanDuel", 145, -170),
        BookLine("BetMGM", 150, null), BookLine("Novig", 150, -180), BookLine("PrizePicks (flex)", 120, -140),
    )

    @Test
    fun `the price bet at against the fair price now, and how far the market moved`() {
        val i = BetInsight.of(bet(nowFair = 0.44, fairAtBet = 0.42))
        assertEquals(150, i.betOdds)
        assertEquals(0.4, i.cost, 0.0)
        assertEquals(0.44 / 0.4 - 1.0, i.evNow!!, 1e-12)
        assertEquals(0.04, i.edgePoints!!, 1e-12) // 44% fair against a 40% cost
        assertEquals(0.02, i.fairMove!!, 1e-12) // the market moved toward the bet
        assertEquals(127, i.breakEvenOdds) // +127 is where 44% breaks even: the bet's +150 clears it
        assertEquals(145, i.priceNow)
        assertEquals(0.05, i.evAtBet!!, 0.0)
    }

    @Test
    fun `every book's own devigged fair and EV against the price bet, only two-sided books counted, the bet's own book first`() {
        val i = BetInsight.of(bet(books = books))
        assertEquals("Novig", i.books.first().name)
        assertTrue(i.books.first().isOwn)
        assertFalse(i.books.first().counted) // the judged book never prices its own fair line
        val counted = i.books.filter { it.counted }.map { it.name }
        assertEquals(listOf("DraftKings", "FanDuel", "Pinnacle"), counted)
        // A one-sided book and a pick'em app are listed but not counted, and have no fair.
        assertNull(i.books.first { it.name == "BetMGM" }.fair)
        assertFalse(i.books.first { it.name.startsWith("PrizePicks") }.counted)
        val pin = i.books.first { it.name == "Pinnacle" }
        assertEquals(pin.fair!! / 0.4 - 1.0, pin.ev!!, 1e-12)
        // With no fair line read yet, the books on hand make one: the lower of their mean and median.
        val fairs = i.books.filter { it.counted }.map { it.fair!! }
        assertEquals(minOf(fairs.average(), fairs.sorted()[1]), i.fairNow!!, 1e-12)
        assertEquals(3, i.booksBehind)
        assertEquals(500L, i.booksAtMs)
    }

    @Test
    fun `a bet imported without an EV, or with nothing read, shows what it has`() {
        val i = BetInsight.of(bet(fairAtBet = null, ev = null))
        assertNull(i.fairAtBet)
        assertNull(i.fairNow)
        assertNull(i.evNow)
        assertNull(i.fairMove)
        assertNull(i.edgePoints)
        assertNull(i.breakEvenOdds)
        assertNull(i.booksBehind)
        assertTrue(i.books.isEmpty())
        assertEquals(150, i.betOdds)
    }

    @Test
    fun `the last read's fair line wins over the books on hand`() {
        val i = BetInsight.of(bet(nowFair = 0.5, books = books))
        assertEquals(0.5, i.fairNow!!, 0.0)
    }
}
