package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** CNO's list stays current only while watched, paced, and as soon as CNO publishes in "real time". */
@OptIn(ExperimentalCoroutinesApi::class)
class CnoFeedTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Records each read's time and filters; fails on demand; says CNO's data is [ageSeconds] old. */
    private class FakeSource(val clock: () -> Long) : CnoSource {
        val reads = mutableListOf<Pair<String, Long>>()
        val filtersSeen = mutableListOf<CnoFilters>()
        var failWith: Exception? = null
        var rows = 1
        var ageSeconds = 10
        var bookReads = 0
        override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
            reads += url to clock()
            filtersSeen += filters
            failWith?.let { throw it }
            return CnoSnapshot(url, List(rows) { CnoRow(0.05, event = "E$it", market = "M", bet = "B", odds = 110, book = "Novig") }, clock(), cnoAgeSeconds = ageSeconds, filters = filters)
        }
        override suspend fun books(row: CnoRow): CnoBooksView {
            bookReads++
            return CnoBooksView(row.bet, null, null, false, listOf(CnoBookPrice("PN", -110, null, -110, null)), clock())
        }
    }

    private fun times(source: FakeSource) = source.reads.map { it.second }

    @Test
    fun `taps closer than 3 seconds apart read once`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        assertTrue(feed.refresh("u"))
        advanceTimeBy(2_000)
        assertFalse(feed.refresh("u"))
        advanceTimeBy(1_000)
        assertTrue(feed.refresh("u"))
        assertEquals(listOf(0L, 3_000L), times(source))
    }

    @Test
    fun `watching reads at once, then on the interval, and nothing after the watch stops`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val job = launch { feed.watch(MutableStateFlow(CnoConfig(enabled = true, url = "u", intervalSeconds = 15))) }
        runCurrent()
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(listOf(0L, 15_000L, 30_000L), times(source))
        job.cancel() // Vigilant left the screen
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(3, source.reads.size)
    }

    @Test
    fun `5 second refresh reads every 5 seconds, taps-only never reads by itself`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val config = MutableStateFlow(CnoConfig(true, "u", intervalSeconds = 5))
        val job = launch { feed.watch(config) }
        advanceTimeBy(16_000)
        runCurrent()
        assertEquals(listOf(0L, 5_000L, 10_000L, 15_000L), times(source))

        config.value = CnoConfig(true, "u", intervalSeconds = 0)
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(4, source.reads.size)
        job.cancel()
    }

    @Test
    fun `real time waits for CNO's next update, then reads every 3 seconds until it lands`() = runTest {
        val source = FakeSource { currentTime }
        source.ageSeconds = 1 // CNO just published
        val feed = CnoFeed(source, clock = { currentTime })
        val job = launch { feed.watch(MutableStateFlow(CnoConfig(true, "u", CnoFeed.REALTIME))) }
        runCurrent()
        // Data from t = -1 s: CNO's next update can't come before 11 s; poll every 3 s from there.
        source.ageSeconds = 30 // nothing new on the next reads
        advanceTimeBy(18_000)
        runCurrent()
        assertEquals(listOf(0L, 11_000L), times(source).take(2))
        assertEquals(listOf(0L, 11_000L, 14_000L, 17_000L), times(source))
        job.cancel()
    }

    @Test
    fun `real time slows to every 30 seconds when CNO has stopped updating`() = runTest {
        val source = FakeSource { currentTime }
        source.ageSeconds = 15 * 60 // "Last Updated: 15 minutes ago": CNO's updater is down
        val feed = CnoFeed(source, clock = { currentTime })
        val job = launch { feed.watch(MutableStateFlow(CnoConfig(true, "u", CnoFeed.REALTIME))) }
        advanceTimeBy(65_000)
        runCurrent()
        assertEquals(listOf(0L, 30_000L, 60_000L), times(source))
        job.cancel()
    }

    @Test
    fun `new filters or a new link are read at once and carried to CNO`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val config = MutableStateFlow(CnoConfig(true, "u1", 300))
        val job = launch { feed.watch(config) }
        runCurrent()
        advanceTimeBy(5_000)
        config.value = CnoConfig(true, "u1", 300, CnoFilters(maxOdds = 120))
        runCurrent()
        advanceTimeBy(5_000)
        config.value = CnoConfig(true, "u2", 300, CnoFilters(maxOdds = 120))
        runCurrent()
        assertEquals(listOf("u1" to 0L, "u1" to 5_000L, "u2" to 10_000L), source.reads)
        assertEquals(120, source.filtersSeen.last().maxOdds)
        assertEquals(120, feed.state.value.snapshot!!.filters!!.maxOdds)

        config.value = CnoConfig(false, "u2", 30)
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(3, source.reads.size)
        job.cancel()
    }

    @Test
    fun `failed reads keep the last list, show why, and back off 5, 10, 20 seconds`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val job = launch { feed.watch(MutableStateFlow(CnoConfig(true, "u", 5))) }
        runCurrent()
        assertNotNull(feed.state.value.snapshot)
        source.failWith = CnoException("CrazyNinjaOdds answered HTTP 500")
        advanceTimeBy(40_000)
        runCurrent()
        // 5 s: fails; +5 s; +10 s; +20 s would be at 40 s.
        assertEquals(listOf(0L, 5_000L, 10_000L, 20_000L, 40_000L), times(source))
        assertEquals("CrazyNinjaOdds answered HTTP 500", feed.state.value.error)
        assertEquals(1, feed.state.value.snapshot!!.rows.size) // the last good list stays up
        source.failWith = null
        advanceTimeBy(40_000)
        runCurrent()
        assertNull(feed.state.value.error)
        assertEquals(0, feed.state.value.errors)
        job.cancel()
    }

    @Test
    fun `a network error that isn't CNO's still shows and backs off`() = runTest {
        val source = FakeSource { currentTime }
        source.failWith = java.io.IOException("reset")
        val feed = CnoFeed(source, clock = { currentTime })
        feed.refresh("u")
        assertTrue(feed.state.value.error!!.contains("reset"))
        assertEquals(1, feed.state.value.errors)
    }

    @Test
    fun `a Retry-After pause holds taps and the timer alike`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        source.failWith = CnoException("busy", retryAfterSeconds = 600)
        feed.refresh("u")
        source.failWith = null
        advanceTimeBy(300_000)
        assertFalse(feed.refresh("u"))
        advanceTimeBy(300_000)
        assertTrue(feed.refresh("u"))
        assertEquals(2, source.reads.size)
    }

    @Test
    fun `a bet's books are read once a minute at most, and on demand again`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val row = CnoRow(0.05, event = "E", market = "M", bet = "B", odds = 110, book = "Novig", gameUrl = "https://x/game.aspx?side_id=1")
        feed.loadBooks(row)
        feed.loadBooks(row)
        assertEquals(1, source.bookReads)
        assertEquals("PN", feed.books.value[row.key]!!.view!!.prices.single().code)
        advanceTimeBy(CnoFeed.BOOKS_TTL_MS)
        feed.loadBooks(row)
        assertEquals(2, source.bookReads)
        feed.loadBooks(row, force = true)
        assertEquals(3, source.bookReads)
    }

    @Test
    fun `the back-off never goes faster than the interval and tops out at 2 minutes`() {
        assertEquals(5_000L, CnoFeed.errorBackoffMs(1, CnoFeed.REALTIME))
        assertEquals(20_000L, CnoFeed.errorBackoffMs(3, 5))
        assertEquals(60_000L, CnoFeed.errorBackoffMs(1, 60))
        assertEquals(120_000L, CnoFeed.errorBackoffMs(9, 5))
    }

    @Test
    fun `the last list is saved and shown on the next launch before any read`() = runTest {
        val file = tmp.newFile("cno.json").also { it.delete() }
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val source = FakeSource { currentTime }
        source.rows = 4
        CnoFeed(source, JsonFileStore(file, CnoCache.serializer(), { CnoCache() }, json), clock = { currentTime }).refresh("u")

        val next = CnoFeed(FakeSource { currentTime }, JsonFileStore(file, CnoCache.serializer(), { CnoCache() }, json), clock = { currentTime })
        next.load()
        assertEquals(4, next.state.value.snapshot!!.rows.size)
        assertEquals("u", next.state.value.snapshot!!.url)
        assertEquals(CnoFilters(), next.state.value.snapshot!!.filters)
    }

    @Test
    fun `a stuck CNO is looked at every 30 seconds on a 5 second refresh too`() = runTest {
        val source = FakeSource { currentTime }
        source.ageSeconds = 15 * 60
        val feed = CnoFeed(source, clock = { currentTime })
        val job = launch { feed.watch(MutableStateFlow(CnoConfig(true, "u", 5))) }
        advanceTimeBy(65_000)
        runCurrent()
        assertEquals(listOf(0L, 30_000L, 60_000L), times(source))
        job.cancel()
    }

    @Test
    fun `an unchanged list isn't rewritten to disk on every read, only once a minute`() = runTest {
        val file = tmp.newFile("cno2.json").also { it.delete() }
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, JsonFileStore(file, CnoCache.serializer(), { CnoCache() }, json), clock = { currentTime })
        feed.refresh("u")
        assertTrue(file.exists())
        file.delete()
        advanceTimeBy(5_000)
        feed.refresh("u") // same rows: not written
        assertFalse(file.exists())
        source.rows = 2
        advanceTimeBy(5_000)
        feed.refresh("u") // new rows: written at once
        assertTrue(file.exists())
        file.delete()
        advanceTimeBy(CnoFeed.SAVE_EVERY_MS)
        feed.refresh("u") // unchanged, but a minute has passed
        assertTrue(file.exists())
    }
}
