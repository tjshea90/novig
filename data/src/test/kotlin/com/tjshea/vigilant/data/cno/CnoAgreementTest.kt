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

    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()

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
    fun `the lane reads the top bets' books one at a time, 4 s apart, then waits for them to go stale`() = runTest {
        val source = Source { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val rows = MutableStateFlow(List(3) { row(it) })
        val job = launch { feed.keepBooksFresh(rows) }
        runCurrent()
        advanceTimeBy(3 * CnoFeed.AGREE_GAP_MS)
        val gap = CnoFeed.AGREE_GAP_MS
        assertEquals(listOf(0L, gap, 2 * gap), source.bookTimes.map { it.second })
        // Nothing more until the first goes stale (10 minutes after it was read).
        advanceTimeBy(CnoFeed.AGREE_TTL_MS - 3 * gap - 1_000)
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

    /** Books that take [readMs] to arrive, counting reads started and finished. */
    private class SlowSource(val clock: () -> Long, val readMs: Long) : CnoSource {
        val started = mutableListOf<Pair<String, Long>>()
        var finished = 0
        override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, emptyList(), clock())
        override suspend fun books(row: CnoRow): CnoBooksView {
            started += row.key to clock()
            kotlinx.coroutines.delay(readMs)
            finished++
            return CnoBooksView(row.bet, null, null, false, emptyList(), clock())
        }
    }

    @Test
    fun `a refresh that only re-prices the list doesn't cut a books read short`() = runTest {
        val source = SlowSource({ currentTime }, readMs = 1_500)
        val feed = CnoFeed(source, clock = { currentTime })
        val rows = MutableStateFlow(listOf(row(1), row(2)))
        val job = launch { feed.keepBooksFresh(rows) }
        runCurrent()
        advanceTimeBy(500)
        rows.value = listOf(row(2, odds = 125), row(1, odds = 130)) // same bets, new prices and order
        advanceTimeBy(10_000)
        assertEquals(2, source.started.size)
        assertEquals(2, source.finished)
        job.cancel()
    }

    @Test
    fun `a books read cut short isn't a failure, so it's tried again at once, not in 2 minutes`() = runTest {
        val source = SlowSource({ currentTime }, readMs = 1_500)
        val feed = CnoFeed(source, clock = { currentTime })
        val rows = MutableStateFlow(listOf(row(1)))
        val job = launch { feed.keepBooksFresh(rows) }
        runCurrent()
        advanceTimeBy(500)
        rows.value = listOf(row(9)) // another bet took the top spot mid-read: row 1's read is cancelled
        advanceTimeBy(5_000)
        rows.value = listOf(row(1)) // back on top
        advanceTimeBy(5_000)
        assertEquals(listOf(row(1).key, row(9).key, row(1).key), source.started.map { it.first })
        assertTrue(source.started.last().second < 20_000L)
        job.cancel()
    }

    @Test
    fun `while CNO's list is failing, the books lane waits instead of piling on`() = runTest {
        val source = Source { currentTime }
        val failing = object : CnoSource by source {
            override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot = throw CnoException("Couldn't reach CrazyNinjaOdds (timeout)")
        }
        val feed = CnoFeed(failing, clock = { currentTime })
        feed.refresh("u") // fails: the list shows an error
        val rows = MutableStateFlow(List(3) { row(it) })
        val job = launch { feed.keepBooksFresh(rows) }
        runCurrent()
        advanceTimeBy(60_000)
        assertTrue(source.bookTimes.isEmpty())
        job.cancel()
    }

    /** Links as CNO's deeplink hands them over, counting requests. */
    private class LinkSource(val clock: () -> Long) : CnoSource {
        val asked = mutableListOf<Pair<String, Long>>()
        override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, emptyList(), clock())
        override suspend fun novigLink(row: CnoRow): String {
            asked += row.key to clock()
            return "novigapp://events/outcome-${row.bet}/cno"
        }
    }

    private fun linkRow(i: Int) = row(i).copy(betUrl = "https://crazyninjaodds.com/site/redirect/deeplink.aspx?line_id=$i")

    @Test
    fun `bet links are looked up ahead of a tap, once each, paced, and a tap then needs no network`() = runTest {
        val source = LinkSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val rows = MutableStateFlow(List(3) { linkRow(it) })
        assertEquals(null, feed.cachedLink(linkRow(0)))
        val job = launch { feed.keepLinksFresh(rows) }
        runCurrent()
        advanceTimeBy(3 * CnoFeed.LINK_GAP_MS)
        val gap = CnoFeed.LINK_GAP_MS
        assertEquals(listOf(0L, gap, 2 * gap), source.asked.map { it.second })
        assertEquals("novigapp://events/outcome-P0 Under 69.5/cno", feed.cachedLink(linkRow(0)))
        // A refresh of the same bets asks for nothing again.
        rows.value = List(3) { linkRow(it).copy(odds = 125) }
        advanceTimeBy(60_000)
        assertEquals(3, source.asked.size)
        job.cancel()
        // The tap: already known.
        assertEquals("novigapp://events/outcome-P2 Under 69.5/cno", feed.novigLink(linkRow(2)))
        assertEquals(3, source.asked.size)
    }

    @Test
    fun `bet links survive a restart`() = runTest {
        val file = java.io.File(tmp.root, "cno_links.json")
        fun store() = com.tjshea.vigilant.data.store.JsonFileStore(file, CnoLinks.serializer(), { CnoLinks() })
        val first = CnoFeed(LinkSource { currentTime }, clock = { currentTime }, linkStore = store())
        first.novigLink(linkRow(7))
        val second = CnoFeed(LinkSource { currentTime }, clock = { currentTime }, linkStore = store())
        second.load()
        assertEquals("novigapp://events/outcome-P7 Under 69.5/cno", second.cachedLink(linkRow(7)))
    }

    @Test
    fun `a busy or refused answer to a link lookup pauses every CNO read, the list's too`() = runTest {
        val refusing = object : CnoSource {
            override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, emptyList(), currentTime)
            override suspend fun novigLink(row: CnoRow): String = throw CnoException("refused", retryAfterSeconds = 600)
        }
        val feed = CnoFeed(refusing, clock = { currentTime })
        assertEquals(null, feed.novigLink(linkRow(1)))
        assertTrue(feed.waitForGapMs() >= 599_000L)
        assertFalse(feed.refresh("u"))
    }

    /** A list read that ends (failed or not) after a books read was refused mid-way: how long until the next read. */
    private suspend fun kotlinx.coroutines.test.TestScope.waitAfterPauseDuringListRead(listFails: Boolean): Long {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val source = object : CnoSource {
            override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
                gate.await()
                if (listFails) throw CnoException("Couldn't reach CrazyNinjaOdds (it didn't answer in time)")
                return CnoSnapshot(url, emptyList(), currentTime)
            }
            override suspend fun books(row: CnoRow): CnoBooksView = throw CnoException("busy", retryAfterSeconds = 120)
        }
        val feed = CnoFeed(source, clock = { currentTime })
        val read = launch { feed.refresh("u") }
        runCurrent()
        feed.loadBooks(row(1)) // refused while the list read runs: CNO asks for 2 minutes
        gate.complete(Unit)
        read.join()
        return feed.waitForGapMs()
    }

    @Test
    fun `a pause CNO asks for during a list read isn't wiped out when that read ends`() = runTest {
        assertTrue(waitAfterPauseDuringListRead(listFails = true) >= 119_000L)
        assertTrue(waitAfterPauseDuringListRead(listFails = false) >= 119_000L)
    }
}
