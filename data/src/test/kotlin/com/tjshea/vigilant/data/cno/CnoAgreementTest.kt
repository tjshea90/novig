package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's green check (Tj, 2026-09-26: "where several books agree on the fair value price,
 * but only … if it doesn't slow down the scanning a lot"): the rule, and the slow background lane
 * that reads the top bets' books.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CnoAgreementTest {

    private fun row(i: Int, odds: Int = 120) =
        CnoRow(0.05, event = "E$i", market = "Player Receptions", bet = "P$i Under 69.5", odds = odds, book = "Novig", gameUrl = "https://x/game.aspx?side_id=$i")

    private class Source(val clock: () -> Long) : CnoSource {
        val bookTimes = mutableListOf<Pair<String, Long>>()
        var fail = false
        override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, emptyList(), clock())
        override suspend fun books(row: CnoRow): CnoBooksView {
            bookTimes += row.key to clock()
            if (fail) throw CnoException("down")
            return CnoBooksView(row.bet, null, null, false, listOf(CnoBookPrice("PN", -110, null, -110, null)), clock())
        }
    }

    private fun price(code: String, mine: Int, theirs: Int) = CnoBookPrice(code, mine, null, theirs, null)

    @Test
    fun `green check needs three two-sided books that each alone make the price +EV`() {
        // Novig +120 (45.5% implied). Four books near 47-48% fair: all four agree.
        val agree = CnoBooksView("X Under 1.5", "X Over 1.5", null, false, listOf(
            price("PN", 104, -118), price("PX", -107, -129), price("KI", -103, -126), price("DK", 100, -122), price("NV", 120, -140),
        ), 1L)
        val c = CnoBooks.check(agree, 120)
        assertEquals(4, c.agreeing)
        assertEquals(CnoBooks.Verdict.CONFIRMED, c.verdict)
        assertTrue(CnoBooks.agrees(agree, row(1), live = false, listReadAtMs = 0L))
    }

    @Test
    fun `books whose consensus says +EV but only two of which do alone are split, not green`() {
        // Three books: two near 47.5% fair, one near 44%. The consensus (~46.4%) beats Novig's 45.5%,
        // but only two of the three books do on their own.
        val split = CnoBooksView("X Under 1.5", "X Over 1.5", null, false, listOf(
            price("PN", 104, -118), price("PX", -107, -129), price("KI", 118, -138),
        ), 1L)
        val c = CnoBooks.check(split, 120)
        assertEquals(3, c.twoSided)
        assertEquals(2, c.agreeing)
        assertTrue(c.ev!! > 0)
        assertEquals(CnoBooks.Verdict.SPLIT, c.verdict)
        assertFalse(CnoBooks.agrees(split, row(1), live = false, listReadAtMs = 0L))
    }

    @Test
    fun `the check uses the list's price when the list is newer than the game page`() {
        val v = CnoBooksView("X Under 1.5", "X Over 1.5", null, false, listOf(
            price("PN", 104, -118), price("PX", -107, -129), price("KI", -103, -126), price("NV", 120, -140),
        ), fetchedAtMs = 1_000L)
        // The game page said +120 (+EV); the newer list says Novig moved to -110: no longer +EV.
        assertTrue(CnoBooks.agrees(v, row(1, odds = -110), live = false, listReadAtMs = 500L))
        assertFalse(CnoBooks.agrees(v, row(1, odds = -110), live = false, listReadAtMs = 2_000L))
    }

    @Test
    fun `the lane reads the top bets' books one at a time, 2 s apart, then waits for them to go stale`() = runTest {
        val source = Source { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val rows = MutableStateFlow(List(3) { row(it) })
        val job = launch { feed.keepBooksFresh(rows) }
        runCurrent()
        advanceTimeBy(10_000)
        assertEquals(listOf(0L, 2_000L, 4_000L), source.bookTimes.map { it.second })
        // Nothing more until the first goes stale (5 minutes after it was read).
        advanceTimeBy(CnoFeed.AGREE_TTL_MS - 11_000)
        assertEquals(3, source.bookTimes.size)
        advanceTimeBy(2_000)
        assertEquals(4, source.bookTimes.size)
        job.cancel()
    }

    @Test
    fun `only the top bets are read, and nothing is read once the lane stops (scanner closed)`() = runTest {
        val source = Source { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val rows = MutableStateFlow(List(CnoFeed.AGREE_TOP + 5) { row(it) })
        val job = launch { feed.keepBooksFresh(rows) }
        runCurrent()
        advanceTimeBy(60_000)
        assertEquals(CnoFeed.AGREE_TOP, source.bookTimes.map { it.first }.distinct().size)
        job.cancel()
        val before = source.bookTimes.size
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(before, source.bookTimes.size)
    }

    @Test
    fun `the lane waits while the list is being read or CNO asked for a pause, and a failed bet waits 2 minutes`() = runTest {
        val source = Source { currentTime }
        source.fail = true
        val feed = CnoFeed(source, clock = { currentTime })
        val rows = MutableStateFlow(listOf(row(1)))
        val job = launch { feed.keepBooksFresh(rows) }
        runCurrent()
        assertEquals(1, source.bookTimes.size)
        advanceTimeBy(CnoFeed.AGREE_RETRY_MS - 1)
        assertEquals(1, source.bookTimes.size)
        advanceTimeBy(2)
        assertEquals(2, source.bookTimes.size)
        job.cancel()
    }

    @Test
    fun `a busy answer while reading books pauses list reads too`() = runTest {
        val busy = object : CnoSource {
            override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, emptyList(), currentTime)
            override suspend fun books(row: CnoRow): CnoBooksView = throw CnoException("busy", retryAfterSeconds = 120)
        }
        val feed = CnoFeed(busy, clock = { currentTime })
        feed.loadBooks(row(1))
        assertTrue(feed.waitForGapMs() >= 119_000L)
        assertFalse(feed.refresh("u"))
    }

    @Test
    fun `CNO's desktop link is turned into the app link that opens Novig on that exact bet`() {
        assertEquals("novigapp://events/01a0d152-14b3-7363-b579-9a927b2ed3ca/cno", CnoFeed.appLink("https://novig.com/events/01a0d152-14b3-7363-b579-9a927b2ed3ca/cno"))
        assertEquals("novigapp://events/abc/cno", CnoFeed.appLink("novigapp://events/abc/cno"))
        assertEquals("https://other.example/x", CnoFeed.appLink("https://other.example/x"))
    }
}
