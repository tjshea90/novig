package com.tjshea.vigilant.data.novig.burst

import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.BookChange
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The score-burst recorder end to end on a fake feed and virtual time (RESEARCH.md §95): a stale line, a window, the paper trades at the three delays judged on the books as
 * the phone saw them, the two probes, a game ending, no key, and a stop. Nothing here can place an order: the recorder is given no way to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BurstRecorderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val base = 1_790_000_000_000L

    private fun market(id: String, type: String, a: String, b: String, strike: Double?) =
        NovigMarket(id, "ev1", type, "OPEN", "d", 0L, MarketFee.GAME, listOf(NovigOutcome("$id-a", a, "TBD"), NovigOutcome("$id-b", b, "TBD")), strike)

    private val ml = market("ml", "MONEY", "ATL", "NO", 0.0)
    private val sp = market("s1", "SPREAD", "ATL -1.5", "NO +1.5", 1.5)
    private val event = NovigEvent("ev1", "FOOTBALL", "NFL", NovigEvent.STATUS_LIVE, "Atlanta Falcons @ New Orleans Saints", base)

    private class FakeSource(var live: List<NovigEvent>, val markets: List<NovigMarket>) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = live
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = if (live.isEmpty()) emptyList() else markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch = throw UnsupportedOperationException("the recorder never reads books by request")
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private class FakeFeed : PushedBooks {
        var listener: BookListener? = null
        val watched = LinkedHashSet<String>()
        var closed = false
        override fun watch(marketIds: Collection<String>) { watched += marketIds }
        override fun live(marketIds: Collection<String>): Map<String, NovigBook> = emptyMap()
        override fun problemSince(sinceMs: Long): String? = null
        override fun close() { closed = true }
    }

    private class Rig(val scope: TestScope, val feed: FakeFeed, val source: FakeSource, val journal: BurstJournal, val recorder: BurstRecorder, val tradesFor: MutableList<TrapGuard.Trade>, val echoMs: Long)

    private fun TestScope.rig(echoMs: Long = 40L, withFeed: Boolean = true, tickMs: Long = 1_000_000L, probeEveryMs: Long = 1_000_000L): Rig {
        val feed = FakeFeed()
        val source = FakeSource(listOf(event), listOf(ml, sp))
        val journal = BurstJournal(tmp.newFolder("j" + System.nanoTime()))
        val trades = ArrayList<TrapGuard.Trade>()
        val rec = BurstRecorder(
            scope = backgroundScope, source = source, newFeed = { l -> if (withFeed) feed.also { it.listener = l } else null },
            echo = { delay(echoMs) }, trades = { trades.toList() }, journal = journal, clock = { base + currentTime },
            discoverEveryMs = 60_000L, echoEveryMs = 1_000_000L, probeEveryMs = probeEveryMs, tickMs = tickMs, ioContext = EmptyCoroutineContext,
        )
        return Rig(this, feed, source, journal, rec, trades, echoMs)
    }

    private fun Rig.push(marketId: String, vararg changes: BookChange) = feed.listener!!.onChange(marketId, base + scope.currentTime, changes.toList())
    private fun add(outcome: String, milli: Int, qty: Long) = BookChange(BookChange.Kind.ADD, outcome, milli, qty)
    private fun remove(outcome: String, milli: Int, qty: Long, reason: String = "cancel") = BookChange(BookChange.Kind.REMOVE, outcome, milli, qty, reason)
    private val clear = BookChange(BookChange.Kind.CLEAR)

    /** Both books quiet: YES at the moneyline costs 0.580 (a bid of 0.420 on NO), NOT at -1.5 costs 0.435 (a bid of 0.565 on ATL -1.5): 1.015, no cover. */
    private fun Rig.calm() {
        push("ml", clear, add("ml-a", 400, 40_000), add("ml-b", 420, 40_000))
        push("s1", clear, add("s1-a", 565, 25_000), add("s1-b", 400, 25_000))
    }

    @Test
    fun `a stale line is a window, and the paper trade at each delay is judged on the books as the phone saw them`() = runTest {
        val r = rig()
        r.recorder.start(setOf("NFL"), capDollars = 10.0)
        runCurrent()
        assertEquals("both lines are watched", setOf("ml", "s1"), r.feed.watched)
        r.calm()
        advanceTimeBy(1_000); runCurrent()
        // t = 1,000: the moneyline's NO side is re-bid at 0.461 (YES now costs 0.539): the cover pays.
        r.push("ml", remove("ml-b", 420, 40_000), add("ml-b", 461, 40_000))
        runCurrent()
        println("DEBUG status=${r.recorder.status.value} watched=${r.feed.watched}")
        assertEquals(1, r.recorder.status.value.open)
        // t = 1,300: the makers fix the spread: the cover is gone.
        advanceTimeBy(300); runCurrent()
        r.push("s1", remove("s1-a", 565, 25_000), add("s1-a", 500, 25_000))
        advanceTimeBy(5_000); runCurrent()
        // The tick is too slow to have done it: close it with a push after the grace.
        r.push("ml", add("ml-a", 380, 100))
        advanceTimeBy(1_000); runCurrent()
        r.recorder.stop()
        advanceUntilIdle()
        val ws = r.journal.readAll().filterIsInstance<WindowRecord>()
        assertEquals(1, ws.size)
        val w = ws.single()
        assertEquals("NFL", w.league)
        assertEquals("ML ATL YES / Spr ATL -1.5 NOT", w.pair)
        assertEquals(0.539, w.yesAsk, 1e-9)
        assertEquals(0.435, w.noAsk, 1e-9)
        assertEquals(25_000L, w.contracts)
        val byProfile = w.results.associateBy { it.profile }
        assertEquals("a perfect taker gets both legs", PaperOutcome.BOTH.name, byProfile.getValue(LatencyModel.OPTIMISTIC).outcome)
        // The typical delay is 240 ms with the defaults: due at t = 1,240, before the spread was fixed at 1,300: the books it sees are the ones before that push.
        assertEquals(240L, byProfile.getValue(LatencyModel.TYPICAL).delayMs)
        assertEquals(PaperOutcome.BOTH.name, byProfile.getValue(LatencyModel.TYPICAL).outcome)
        // The slow delay is 465 ms: due at 1,465, after it: the cover is gone, and the NO leg is gone but the YES leg is still there at its price: a naked leg.
        assertEquals(465L, byProfile.getValue(LatencyModel.SLOW).delayMs)
        assertEquals(PaperOutcome.ONE_LEG.name, byProfile.getValue(LatencyModel.SLOW).outcome)
        assertTrue(byProfile.getValue(LatencyModel.SLOW).pnl < 0.0)
        // Tj's $10 limit a leg: 10 / (0.539 x 0.01) = 1,855 contracts on the dearer leg, against 25,000 on offer.
        assertEquals(1_855L, byProfile.getValue(LatencyModel.OPTIMISTIC).cappedContracts)
        assertTrue(byProfile.getValue(LatencyModel.OPTIMISTIC).cappedPnl < byProfile.getValue(LatencyModel.OPTIMISTIC).pnl)
        assertEquals("the window was open from 1,000 to 1,300", 300L, w.durationMs)
        assertEquals(1_790_000_001_000L, w.openedMs)
    }

    @Test
    fun `a calm game records no window, and a window that would not pay after fees is not one`() = runTest {
        val r = rig()
        r.recorder.start(setOf("NFL"), 0.0)
        runCurrent()
        r.calm()
        advanceTimeBy(60_000); runCurrent()
        r.push("ml", add("ml-b", 440, 10))      // a better bid but the cover still costs more than a dollar
        r.recorder.stop(); advanceUntilIdle()
        assertEquals(0, r.journal.readAll().filterIsInstance<WindowRecord>().size)
        assertEquals(0, r.recorder.status.value.windows)
    }

    @Test
    fun `the echo probe measures the signed round trip, and a fill the books showed against the same trade's engine time measures the push delay`() = runTest {
        val r = rig(echoMs = 40L, probeEveryMs = 100L)
        r.recorder.start(setOf("NFL"), 0.0)
        runCurrent()
        assertEquals("the signed echo's round trip", 1, r.recorder.latency.roundTrips())
        r.calm()
        advanceTimeBy(500); runCurrent()
        // A fill: an order of 1,000 at 0.461 on the NO side is removed (reason fill) at t = 500, and Novig's own trades say it traded at t = 350.
        r.push("ml", add("ml-b", 461, 1_000))
        r.push("ml", remove("ml-b", 461, 1_000, reason = "fill"))
        r.tradesFor += TrapGuard.Trade("ml-b", 0.461, 1_000, base + 350)
        advanceTimeBy(1_000); runCurrent()
        assertEquals("one push-delay sample", 1, r.recorder.latency.pushDelays())
        assertTrue(r.recorder.latency.note(), r.recorder.latency.note().contains("ASSUMED"))
        // The same trade listed again is not a second sample.
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, r.recorder.latency.pushDelays())
        r.recorder.stop(); advanceUntilIdle()
    }

    @Test
    fun `a game that leaves the live list is written with its coverage and its open windows are closed`() = runTest {
        val r = rig(tickMs = 50L)
        r.recorder.start(setOf("NFL"), 0.0)
        runCurrent()
        r.calm()
        advanceTimeBy(500); runCurrent()
        r.push("ml", remove("ml-b", 420, 40_000), add("ml-b", 461, 40_000))
        advanceTimeBy(2_000); runCurrent()
        assertEquals(1, r.recorder.status.value.open)
        r.source.live = emptyList()
        advanceTimeBy(61_000); runCurrent()
        val all = r.journal.readAll()
        assertEquals(1, all.filterIsInstance<GameRecord>().size)
        val g = all.filterIsInstance<GameRecord>().single()
        assertEquals("NFL", g.league)
        assertEquals(2, g.lines)
        assertTrue("two snapshots and a delta were pushed", g.updates >= 3)
        assertEquals("the open window was closed and written when the game left", 1, all.filterIsInstance<WindowRecord>().size)
        assertEquals(0, r.recorder.status.value.games)
        r.recorder.stop(); advanceUntilIdle()
    }

    @Test
    fun `without a connected key it says so and records nothing`() = runTest {
        val r = rig(withFeed = false)
        r.recorder.start(setOf("NFL"), 0.0)
        runCurrent()
        val s = r.recorder.status.value
        assertFalse(s.running)
        assertTrue(s.problem.orEmpty(), s.problem.orEmpty().contains("No Novig key"))
        assertTrue(r.journal.readAll().isEmpty())
    }

    @Test
    fun `stop closes the connection and writes what was seen, and a second start begins clean`() = runTest {
        val r = rig(tickMs = 50L)
        r.recorder.start(setOf("NFL"), 0.0)
        runCurrent()
        r.calm()
        advanceTimeBy(500); runCurrent()
        r.push("ml", remove("ml-b", 420, 40_000), add("ml-b", 461, 40_000))
        advanceTimeBy(500); runCurrent()
        r.recorder.stop("stopped by the test")
        advanceUntilIdle()
        println("DEBUG2 closed=${r.feed.closed} running=${r.recorder.running} status=${r.recorder.status.value} journal=${r.journal.readAll().size}")
        assertTrue(r.feed.closed)
        assertEquals(1, r.journal.readAll().filterIsInstance<WindowRecord>().size)
        assertEquals(1, r.journal.readAll().filterIsInstance<GameRecord>().size)
        assertFalse(r.recorder.running)
        assertNotNull(r.recorder.status.value.problem)
        // Starting again: counters from zero.
        r.feed.closed = false
        r.recorder.start(setOf("NFL"), 0.0)
        runCurrent()
        assertEquals(0, r.recorder.status.value.windows)
        r.recorder.stop(); advanceUntilIdle()
    }

    @Test
    fun `the replayed book follows add, partial fill and gap exactly`() {
        val b = ReplayBook("m")
        assertNull("nothing before a snapshot", b.book(0))
        b.apply(listOf(BookChange(BookChange.Kind.CLEAR), BookChange(BookChange.Kind.ADD, "o", 500, 100), BookChange(BookChange.Kind.ADD, "o", 500, 50), BookChange(BookChange.Kind.ADD, "o", 480, 10)))
        assertEquals(listOf(500 to 150L, 480 to 10L), b.book(0)!!.bidsByOutcome.getValue("o").map { it.priceMilli to it.contracts })
        // A partial fill is a remove of the order then an add of the rest at the same price.
        b.apply(listOf(BookChange(BookChange.Kind.REMOVE, "o", 500, 100, "fill"), BookChange(BookChange.Kind.ADD, "o", 500, 40)))
        assertEquals(90L, b.book(0)!!.bidsByOutcome.getValue("o").first().contracts)
        b.apply(listOf(BookChange(BookChange.Kind.STALE)))
        assertNull("a gap: the book is wrong until the next snapshot", b.book(0))
        b.apply(listOf(BookChange(BookChange.Kind.CLEAR), BookChange(BookChange.Kind.ADD, "o", 470, 7)))
        assertEquals(listOf(470 to 7L), b.book(0)!!.bidsByOutcome.getValue("o").map { it.priceMilli to it.contracts })
    }
}
