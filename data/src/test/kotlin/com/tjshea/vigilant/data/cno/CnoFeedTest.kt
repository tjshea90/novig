package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
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

/** CNO's list stays current only while watched, never faster than CNO's robots.txt asks. */
@OptIn(ExperimentalCoroutinesApi::class)
class CnoFeedTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Records each read's time; fails on demand. */
    private class FakeSource(val clock: () -> Long) : CnoSource {
        val reads = mutableListOf<Pair<String, Long>>()
        var failWith: CnoException? = null
        var rows = 1
        override suspend fun fetch(url: String): CnoSnapshot {
            reads += url to clock()
            failWith?.let { throw it }
            return CnoSnapshot(url, List(rows) { CnoRow(0.05, event = "E$it", market = "M", bet = "B", odds = 110, book = "Novig") }, clock(), cnoAgeSeconds = 10)
        }
    }

    @Test
    fun `taps closer than 30 seconds apart read once`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        assertTrue(feed.refresh("u"))
        advanceTimeBy(10_000)
        assertFalse(feed.refresh("u"))
        advanceTimeBy(19_000)
        assertFalse(feed.refresh("u"))
        advanceTimeBy(1_000)
        assertTrue(feed.refresh("u"))
        assertEquals(listOf(0L, 30_000L), source.reads.map { it.second })
    }

    @Test
    fun `watching reads at once, then on the interval, and nothing after the watch stops`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val config = MutableStateFlow(CnoConfig(enabled = true, url = "u", intervalSeconds = 60))
        val job = launch { feed.watch(config) }
        runCurrent()
        advanceTimeBy(150_000)
        runCurrent()
        assertEquals(listOf(0L, 60_000L, 120_000L), source.reads.map { it.second })
        job.cancel() // Vigilant left the screen
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(3, source.reads.size)
    }

    @Test
    fun `the interval can't go under 30 seconds, and manual-only never reads by itself`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val config = MutableStateFlow(CnoConfig(true, "u", intervalSeconds = 5))
        val job = launch { feed.watch(config) }
        advanceTimeBy(95_000)
        runCurrent()
        assertEquals(listOf(0L, 30_000L, 60_000L, 90_000L), source.reads.map { it.second })

        config.value = CnoConfig(true, "u", intervalSeconds = 0)
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(4, source.reads.size)
        job.cancel()
    }

    @Test
    fun `a new link is read as soon as the gap allows, off reads nothing`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val config = MutableStateFlow(CnoConfig(true, "u1", 300))
        val job = launch { feed.watch(config) }
        runCurrent()
        advanceTimeBy(5_000)
        config.value = CnoConfig(true, "u2", 300)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf("u1" to 0L, "u2" to 30_000L), source.reads)

        config.value = CnoConfig(false, "u2", 30)
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(2, source.reads.size)
        job.cancel()
    }

    @Test
    fun `a failed read keeps the last list, shows why, and backs off`() = runTest {
        val source = FakeSource { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val config = MutableStateFlow(CnoConfig(true, "u", 30))
        val job = launch { feed.watch(config) }
        runCurrent()
        assertNotNull(feed.state.value.snapshot)
        source.failWith = CnoException("CrazyNinjaOdds answered HTTP 500")
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals("CrazyNinjaOdds answered HTTP 500", feed.state.value.error)
        assertEquals(1, feed.state.value.snapshot!!.rows.size) // the last good list stays up
        // Next try waits the error back-off (2 minutes), not the 30-second interval.
        advanceTimeBy(119_000)
        runCurrent()
        assertEquals(2, source.reads.size)
        source.failWith = null
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, source.reads.size)
        assertNull(feed.state.value.error)
        job.cancel()
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
    }
}
