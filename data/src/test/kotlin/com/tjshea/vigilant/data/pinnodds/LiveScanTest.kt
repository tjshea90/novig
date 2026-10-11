package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.live
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.money
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The Feed's real-time live lane end to end on fakes and virtual time (Tj, 2026-10-11): rows exist only while both feeds are fresh, die at 5 s, and nothing happens with the switch off. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveScanTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val base = 1_791_420_000_000L
    private val event = NovigEvent("ev1", "BASKETBALL", "NBA", NovigEvent.STATUS_LIVE, "Milwaukee Bucks @ Oklahoma City Thunder", base - 600_000L)
    private val ml = NovigMarket("ml", "ev1", "MONEY", "OPEN", "OKC", base, MarketFee.GAME, listOf(NovigOutcome("ml-a", "OKC", "TBD"), NovigOutcome("ml-b", "MIL", "TBD")), 0.0)
    private val farSpread = NovigMarket("far", "ev1", "SPREAD", "OPEN", "OKC -31.5", base, MarketFee.GAME, listOf(NovigOutcome("far-a", "OKC -31.5", "TBD"), NovigOutcome("far-b", "MIL +31.5", "TBD")), -31.5)

    private class Source(val events: List<NovigEvent>, val markets: List<NovigMarket>) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events.filter { it.status in statuses }
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch = throw UnsupportedOperationException()
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private class Feed : PushedBooks {
        var listener: BookListener? = null
        val watched = ArrayList<String>()
        val books = HashMap<String, NovigBook>()
        var problem: String? = null
        override fun watch(marketIds: Collection<String>) { watched.clear(); watched += marketIds }
        override fun live(marketIds: Collection<String>) = marketIds.mapNotNull { id -> books[id]?.let { id to it } }.toMap()
        override fun problemSince(sinceMs: Long) = problem
        override fun close() {}
    }

    private class Pinn : PinnFeedSource {
        var onFrame: ((String, Long) -> Unit)? = null
        val st = MutableStateFlow<PinnSocketState>(PinnSocketState.Off)
        override val state: StateFlow<PinnSocketState> = st
        override var lastFrameAtMs: Long = 0
        var started = false
        override fun start() { started = true; st.value = PinnSocketState.Live(0) }
        override fun stop() { started = false; st.value = PinnSocketState.Off }
    }

    private class NoOrders : LiveOrders {
        val placed = ArrayList<Long>()
        override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String): String { placed += qty; return "o" }
        override suspend fun order(orderId: String): NovigOrder? = null
        override suspend fun fills(orderId: String): List<NovigFill> = emptyList()
    }


    private class Harness(val runner: PinnLiveRunner, val feed: Feed, val pinn: Pinn, val board: LiveScanBoard)

    private fun TestScope.harness(scanOn: Boolean = true): Harness {
        val feed = Feed()
        val pinn = Pinn()
        val board = LiveScanBoard()
        val trader = PinnLiveTrader(
            orders = object : LiveOrders {
                override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String): String = error("the live lane never orders")
                override suspend fun order(orderId: String): NovigOrder? = null
                override suspend fun fills(orderId: String): List<NovigFill> = emptyList()
            },
            scope = backgroundScope, rules = { LiveTradeRules(enabled = false, bet = false, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0) },
            gate = { null }, ownBids = { emptyList() }, journal = DayJournal(tmp.newFolder("j"), "pinn-live", LiveRecord.serializer()) { it.atMs }, onHalt = {}, logFills = { _, _, _ -> true },
            clock = { base + currentTime }, dayStart = { base - 3_600_000L }, pause = { kotlinx.coroutines.delay(it) },
        )
        val runner = PinnLiveRunner(
            scope = backgroundScope, source = Source(listOf(event), listOf(ml)), newFeed = { l -> feed.also { it.listener = l } },
            openFeed = { cb -> pinn.also { it.onFrame = cb } }, trader = trader,
            config = { LiveConfig(LiveRules(), DevigMethod.MULTIPLICATIVE, setOf("NBA"), takerOn = false, scanOn = scanOn, scanSettings = ScanSettings()) },
            followJournal = DayJournal(tmp.newFolder("f"), "pinn-follow", LiveFollow.serializer()) { it.atMs }, clock = { base + currentTime }, discoverEveryMs = 30_000L, tickMs = 100L,
            scanBoard = board,
        )
        return Harness(runner, feed, pinn, board)
    }

    private fun Harness.frame(now: Long, text: String) { pinn.lastFrameAtMs = now; pinn.onFrame!!(text, now) }
    private fun Harness.novigPush(now: Long) { feed.listener!!.onChange("ml", now, emptyList()) }
    private fun Harness.lagging() { feed.books["ml"] = NovigBook("ml", 1, mapOf("ml-a" to listOf(BidLevel(460, 50_000)), "ml-b" to listOf(BidLevel(500, 50_000))), base) }

    @Test
    fun `both feeds fresh - the live market is on the board with Pinnacle's fair and the live fee in its EV`() = runTest {
        val h = harness()
        h.runner.start(); runCurrent()
        h.lagging()
        h.frame(base + currentTime, live(markets = arrayOf(money(2, -150, 125))))
        advanceTimeBy(1_500); runCurrent()
        h.novigPush(base + currentTime)
        advanceTimeBy(300); runCurrent()
        val rows = h.board.rows.value
        val okc = rows.single { it.outcome.outcomeId == "ml-a" }
        assertEquals("Oklahoma City Thunder", okc.selection)
        assertEquals(0.5, okc.quote!!.price, 1e-9)
        assertTrue("Pinnacle -150/+125 devigged is about 57%", okc.fairProbability!! in 0.55..0.59)
        assertTrue("positive after Novig's live taker fee", okc.evPercent!! > 0.05)
        assertEquals("the fee is the live one", com.tjshea.vigilant.engine.EvMath.quote(okc.fairProbability!!, 0.5, ml.fee!!, true).evPercent, okc.evPercent!!, 1e-9)
        assertTrue(h.board.status.value.on)
        assertEquals(1, h.board.status.value.games)
        // The Feed lists it under the same bars as any bet.
        val s = ScanSettings(leagues = setOf("NBA"), includeLive = true)
        assertTrue(LiveScanFeed.shown(rows, s, base + currentTime).isNotEmpty())
        assertTrue("switch off: nothing", LiveScanFeed.shown(rows, s.copy(includeLive = false), base + currentTime).isEmpty())
        // A +EV row the filters hide says why (Tj, 2026-10-11: the banner counted 2 +EV bets and the list showed none).
        assertTrue(LiveScanFeed.hiddenReasons(rows, s, base + currentTime).isEmpty())
        val other = LiveScanFeed.hiddenReasons(rows, s.copy(leagues = setOf("NFL")), base + currentTime)
        assertEquals(listOf("NBA is not one of your picked leagues"), other.map { it.first })
        assertTrue(other.single().second >= 1)
        val strict = LiveScanFeed.hiddenReasons(rows, s.copy(minEvPercent = 0.9, maxEvPercent = 1.0), base + currentTime)
        assertTrue(strict.single().first.startsWith("under your"))
        h.runner.stop()
    }

    @Test
    fun `a row is dropped once the older feed has been quiet for 5 seconds, and comes back with the next word`() = runTest {
        val h = harness()
        h.runner.start(); runCurrent()
        h.lagging()
        h.frame(base + currentTime, live(markets = arrayOf(money(2, -150, 125))))
        advanceTimeBy(1_500); runCurrent()
        h.novigPush(base + currentTime)
        advanceTimeBy(300); runCurrent()
        assertTrue(h.board.rows.value.isNotEmpty())
        // Novig keeps talking, Pinnacle goes quiet: the older feed decides.
        repeat(5) { advanceTimeBy(1_000); runCurrent(); h.novigPush(base + currentTime) }
        advanceTimeBy(300); runCurrent()
        assertTrue("5+ s since Pinnacle spoke: dropped", h.board.rows.value.isEmpty())
        assertTrue(h.board.status.value.dropped > 0)
        h.frame(base + currentTime, live(markets = arrayOf(money(3, -150, 125))))
        advanceTimeBy(800); runCurrent()
        h.novigPush(base + currentTime)
        advanceTimeBy(300); runCurrent()
        assertTrue("fresh again", h.board.rows.value.isNotEmpty())
        h.runner.stop()
    }

    @Test
    fun `Novig quiet for 5 seconds also drops the row, and a Novig feed problem empties the board with a reason`() = runTest {
        val h = harness()
        h.runner.start(); runCurrent()
        h.lagging()
        h.frame(base + currentTime, live(markets = arrayOf(money(2, -150, 125))))
        advanceTimeBy(1_500); runCurrent()
        h.novigPush(base + currentTime)
        advanceTimeBy(300); runCurrent()
        assertTrue(h.board.rows.value.isNotEmpty())
        repeat(5) { advanceTimeBy(1_000); runCurrent(); h.frame(base + currentTime, live(markets = arrayOf(money(3L + it, -150, 125)))) }
        advanceTimeBy(300); runCurrent()
        assertTrue(h.board.rows.value.isEmpty())
        h.novigPush(base + currentTime)
        advanceTimeBy(300); runCurrent()
        assertTrue(h.board.rows.value.isNotEmpty())
        h.feed.problem = "socket closed"
        advanceTimeBy(300); runCurrent()
        assertTrue(h.board.rows.value.isEmpty())
        assertNotNull(h.board.status.value.reason)
        h.runner.stop()
    }

    @Test
    fun `switch off - nothing is priced and the board stays empty`() = runTest {
        val h = harness(scanOn = false)
        h.runner.start(); runCurrent()
        h.lagging()
        h.frame(base + currentTime, live(markets = arrayOf(money(2, -150, 125))))
        advanceTimeBy(1_500); runCurrent()
        h.novigPush(base + currentTime)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(h.board.rows.value.isEmpty())
        assertTrue(!h.board.status.value.on)
        h.runner.stop()
    }
}
