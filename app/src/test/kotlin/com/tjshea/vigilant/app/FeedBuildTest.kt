package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tj, 2026-10-03, with the v0.56.1 Diagnostics: "it got so laggy I almost couldn't use it and I pressed pause and even that took a while to register".
 * Android's own record: the app "not responding", the main thread in [UiState.feedOf] (every row of the feed checked against every bet Tj has, a dozen
 * regexes each). Every scanner switch, Pause, a scan's end, a recheck and each ✓ mark built the whole feed on the main thread, inside `_state.update`;
 * eight scanner switches in forty seconds queued eight rebuilds ahead of his Pause. The feed is built off it ([publishResult], [refeed], [reindex]).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FeedBuildTest {

    private val base = SampleScan.state()
    private val result = base.result!!

    /** A list that notes which threads walk it, and holds each walk until [hold] opens (the screen's other work happens meanwhile). */
    private class Watched(private val inner: List<Opportunity>, private val hold: CountDownLatch? = null) : java.util.AbstractList<Opportunity>() {
        val threads = CopyOnWriteArrayList<Thread>()
        val started = CountDownLatch(1)
        override val size: Int get() = inner.size
        override fun get(index: Int): Opportunity = inner[index]
        override fun iterator(): MutableIterator<Opportunity> {
            threads += Thread.currentThread()
            started.countDown()
            hold?.await(10, TimeUnit.SECONDS)
            return inner.toMutableList().iterator()
        }
    }

    private fun empty() = base.copy(result = null, feed = emptyList())

    @Test
    fun `a scan's result is shown with its feed, built on another thread than the caller's`() = runBlocking {
        val watched = Watched(result.opportunities)
        val shown = result.copy(opportunities = watched)
        val flow = MutableStateFlow(empty())
        flow.publishResult(shown) { it.copy(status = it.status.copy(rechecking = true)) }
        assertTrue(watched.threads.isNotEmpty())
        assertTrue("built on the caller's thread", watched.threads.none { it === Thread.currentThread() })
        assertSame(shown, flow.value.result)
        assertEquals(base.feedOf(result).map { it.key }, flow.value.feed.map { it.key })
        assertTrue(flow.value.feed.isNotEmpty())
        // What the caller changes in the same swap arrives with it.
        assertTrue(flow.value.status.rechecking)
    }

    @Test
    fun `settings changed while a feed was being built - it is built again for the current ones, never shown for the old`() = runBlocking {
        val hold = CountDownLatch(1)
        val watched = Watched(result.opportunities, hold)
        val flow = MutableStateFlow(empty())
        val job = launch(Dispatchers.Default) { flow.publishResult(result.copy(opportunities = watched)) }
        assertTrue(watched.started.await(10, TimeUnit.SECONDS))
        // Tj raises the minimum edge above every bet while the first build is still going.
        flow.update { it.copy(settings = it.settings.copy(minEvPercent = 0.9)) }
        hold.countDown()
        job.join()
        assertEquals("the old settings' feed was shown", emptyList<Opportunity>(), flow.value.feed)
        assertEquals(0.9, flow.value.settings.minEvPercent, 1e-9)
        assertTrue("built again for the new settings", watched.threads.size >= 2)
    }

    @Test
    fun `a newer result arriving while the feed of the old one was built is never overwritten by it`() = runBlocking {
        val hold = CountDownLatch(1)
        val watched = Watched(result.opportunities, hold)
        val flow = MutableStateFlow(base.copy(result = result.copy(opportunities = watched), feed = emptyList()))
        val job = launch(Dispatchers.Default) { flow.refeed() }
        assertTrue(watched.started.await(10, TimeUnit.SECONDS))
        // A newer scan result (nothing priced in it) is shown meanwhile.
        val newer = result.copy(opportunities = emptyList(), computedAtMs = result.computedAtMs + 60_000)
        flow.update { it.copy(result = newer, feed = emptyList()) }
        hold.countDown()
        job.join()
        assertSame(newer, flow.value.result)
        assertEquals("the older result's feed was put under the newer result", emptyList<Opportunity>(), flow.value.feed)
    }

    @Test
    fun `a bet just tracked leaves the feed in the same swap that shows it, the index and feed built off the caller's thread`() = runBlocking {
        val watched = Watched(result.opportunities)
        val flow = MutableStateFlow(base.copy(result = result.copy(opportunities = watched), feed = base.feedOf(result)))
        val o = flow.value.feed.first()
        val bet = TrackedBet(
            id = "t", createdAtMs = System.currentTimeMillis(), league = o.event.league, eventName = o.event.description, startsTs = System.currentTimeMillis() + 3 * 3_600_000L,
            marketLabel = o.marketLabel, selection = o.selection, marketId = o.market.marketId, outcomeId = o.outcome.outcomeId, price = 0.5, cost = 0.5,
            fairAtBet = null, evPercentAtBet = null, stake = 1.0,
        )
        flow.reindex { it.copy(bets = it.bets + bet) }
        assertTrue(flow.value.bets.contains(bet))
        assertFalse(flow.value.feed.any { it.key == o.key })
        assertFalse(flow.value.placedIndex.isEmpty)
        assertNotNull(flow.value.feed.firstOrNull())
        assertTrue(watched.threads.isNotEmpty() && watched.threads.none { it === Thread.currentThread() })
    }

    // ---- source pins: no screen update builds a feed itself any more -------------------------------------------------------------

    private fun source(path: String) = File("src/main/kotlin/com/tjshea/vigilant/app/$path").readText()

    @Test
    fun `the state holder never builds a feed inside a state update, and a settings change shows at once and doesn't wait for a re-pricing`() {
        val vm = source("MainViewModel.kt")
        // Only the definition and indexed() mention it: every other build goes through FeedBuild.kt (off the main thread).
        assertEquals("feedOf( is called from ${Regex("""feedOf\(""").findAll(vm).count()} places", 2, Regex("""feedOf\(""").findAll(vm).count())
        assertFalse(vm.contains(".indexed()"))
        assertFalse(vm.contains("n.feedOf("))
        assertFalse(vm.contains("s.feedOf(r)"))
        assertTrue(vm.contains("_state.reindex { it.copy(placed = b.bets) }"))
        assertTrue(vm.contains("_state.reindex { it.copy(bets = bets) }"))
        // applySettings: the settings are swapped in first, side effects (a scan stops on Pause) next, then the feed; the re-pricing is its own job.
        val apply = vm.substringAfter("private suspend fun applySettings").substringBefore("private var repricing")
        assertTrue(apply.indexOf("it.copy(settings = next)") in 0 until apply.indexOf("c.runner.stop()"))
        assertTrue(apply.indexOf("c.runner.stop()") < apply.indexOf("_state.refeed()"))
        assertTrue(apply.contains("rescheduleReprice()"))
        assertFalse("applySettings waits for a re-pricing, which waits for a running scan", apply.contains("repriceNow("))
        assertTrue(vm.contains("repricing?.cancel()"))
    }
}
