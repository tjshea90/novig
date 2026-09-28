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
 * Tj, 2026-09-26: "make sure if I close the cno scanner or the app that nothing is refreshing in
 * the background". CNO (list, books lane) runs only while its tab or a widget is watching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CnoWatchTest {

    private class Source(val clock: () -> Long) : CnoSource {
        val listReads = mutableListOf<Long>()
        val bookReads = mutableListOf<Long>()
        override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
            listReads += clock()
            return CnoSnapshot(url, listOf(CnoRow(0.03, event = "A @ B", market = "M", bet = "X Over 1.5", odds = 110, book = "Novig", gameUrl = "g?side_id=1")), clock(), cnoAgeSeconds = 1, filters = filters)
        }
        override suspend fun books(row: CnoRow): CnoBooksView {
            bookReads += clock()
            return CnoBooksView(row.bet, null, null, false, emptyList(), clock())
        }
    }

    @Test
    fun `nothing is read until something watches, and nothing after the last watcher closes`() = runTest {
        val source = Source { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val watch = CnoWatch()
        val config = MutableStateFlow(CnoConfig(true, "u", intervalSeconds = 5))
        val active = mutableListOf<Boolean>()
        val job = launch {
            watch.runWhileWatched(onActive = { active += it }) {
                launch { feed.watch(config) }
                launch { feed.keepBooksFresh(MutableStateFlow(listOf(CnoRow(0.03, event = "A @ B", market = "M", bet = "X Over 1.5", odds = 110, book = "Novig", gameUrl = "g?side_id=1")))) }
            }
        }
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(source.listReads.isEmpty())
        assertTrue(source.bookReads.isEmpty())

        // The CNO tab opens: reads start at once, every 5 s.
        watch.set("tab", true)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        val whileTab = source.listReads.size
        assertTrue(whileTab >= 4)
        assertEquals(1, source.bookReads.size)

        // Tj leaves for Novig with the floating widget up: the widget takes over, nothing restarts.
        watch.set("overlay", true)
        watch.set("tab", false)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(source.listReads.size > whileTab)
        assertEquals(listOf(false, true), active) // on once, never off in between

        // The widget is closed (or the phone locked, or Vigilant closed): no read, ever again.
        watch.set("overlay", false)
        runCurrent()
        val atClose = source.listReads.size to source.bookReads.size
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(atClose, source.listReads.size to source.bookReads.size)
        assertFalse(watch.watched)
        assertEquals(listOf(false, true, false), active)
        job.cancel()
    }

    /**
     * Tj, 2026-09-28: "Make an option in the app to pause all scanning". Held, nothing is read even with the CNO tab
     * open (a read in flight stops); let go, reads start again while someone still watches.
     */
    @Test
    fun `a hold stops every read even while watched, and letting go starts them again`() = runTest {
        val source = Source { currentTime }
        val feed = CnoFeed(source, clock = { currentTime })
        val watch = CnoWatch()
        val active = mutableListOf<Boolean>()
        val job = launch { watch.runWhileWatched(onActive = { active += it }) { launch { feed.watch(MutableStateFlow(CnoConfig(true, "u", intervalSeconds = 5))) } } }
        // Paused before anything opened (the app starts held until its settings load).
        watch.hold(true)
        watch.set("tab", true)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(source.listReads.isEmpty())

        watch.hold(false)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        val resumed = source.listReads.size
        assertTrue(resumed >= 2)

        // Paused with the tab still open: no read, however long.
        watch.hold(true)
        runCurrent()
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(resumed, source.listReads.size)
        assertTrue(watch.watched)
        assertEquals(listOf(false, true, false), active)
        job.cancel()
    }

    /**
     * A source that answers slowly and, like OkHttp, reports a cancelled read as a network error
     * (HTTP/2 "stream was reset: CANCEL", wrapped by CnoClient).
     */
    private class CutShortSource(val clock: () -> Long) : CnoSource {
        override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
            try {
                kotlinx.coroutines.delay(2_000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw CnoException("Couldn't reach CrazyNinjaOdds (stream was reset: CANCEL)")
            }
            return CnoSnapshot(url, emptyList(), clock())
        }
        override suspend fun books(row: CnoRow): CnoBooksView {
            try {
                kotlinx.coroutines.delay(2_000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw CnoException("Couldn't reach CrazyNinjaOdds (stream was reset: CANCEL)")
            }
            return CnoBooksView(row.bet, null, null, false, emptyList(), clock())
        }
    }

    @Test
    fun `a read cut short because the scanner closed is not a CNO error`() = runTest {
        val feed = CnoFeed(CutShortSource { currentTime }, clock = { currentTime })
        val watch = CnoWatch()
        val job = launch { watch.runWhileWatched { launch { feed.watch(MutableStateFlow(CnoConfig(true, "u", 5))) } } }
        watch.set("overlay", true)
        runCurrent()
        advanceTimeBy(1_000) // mid-read
        assertTrue(feed.state.value.refreshing)
        watch.set("overlay", false) // widget closed / phone locked
        runCurrent()
        assertEquals(null, feed.state.value.error)
        assertEquals(0, feed.state.value.errors)
        assertFalse(feed.state.value.refreshing)
        job.cancel()
    }

    @Test
    fun `a books read cut short is not a books error`() = runTest {
        val feed = CnoFeed(CutShortSource { currentTime }, clock = { currentTime })
        val row = CnoRow(0.03, event = "A @ B", market = "M", bet = "X Over 1.5", odds = 110, book = "Novig", gameUrl = "g?side_id=1")
        val job = launch { feed.loadBooks(row) }
        runCurrent()
        advanceTimeBy(1_000)
        job.cancel()
        runCurrent()
        assertEquals(null, feed.books.value[row.key]?.error)
        assertFalse(feed.books.value[row.key]?.loading ?: false)
    }
}
