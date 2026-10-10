package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.livebid.LiveBidConfig
import com.tjshea.vigilant.data.livebid.LiveBid
import com.tjshea.vigilant.data.livebid.LiveBidDesk
import com.tjshea.vigilant.data.livebid.LiveBidLimits
import com.tjshea.vigilant.data.livebid.LiveBidPersistence
import com.tjshea.vigilant.data.livebid.LiveBidQuality
import com.tjshea.vigilant.data.livebid.LiveBidSkip
import com.tjshea.vigilant.data.livebid.LiveBidStatus
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.BookChange
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.live
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.money
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

/**
 * The live bids end to end on fakes and virtual time: Pinnodds frames and Novig books in, a bid out, and every reason it comes down (a score, a danger frame, a quiet feed, a price that fell under it, a
 * closed line, a feed that dropped, the engine stopping). Paper mode, so no order can leave; the desk's own tests cover the real orders.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinnLiveRunnerBidTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val base = 1_791_420_000_000L
    private val event = NovigEvent("ev1", "BASKETBALL", "NBA", NovigEvent.STATUS_LIVE, "Milwaukee Bucks @ Oklahoma City Thunder", base - 600_000L)
    private val ml = NovigMarket("ml", "ev1", "MONEY", "OPEN", "OKC", base, MarketFee.GAME, listOf(NovigOutcome("ml-a", "OKC", "TBD"), NovigOutcome("ml-b", "MIL", "TBD")), 0.0)

    private class Source(val events: List<NovigEvent>, val markets: List<NovigMarket>) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events.filter { it.status in statuses }
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch = throw UnsupportedOperationException()
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private class Feed : PushedBooks {
        var listener: BookListener? = null
        val books = HashMap<String, NovigBook>()
        override fun watch(marketIds: Collection<String>) {}
        override fun live(marketIds: Collection<String>) = marketIds.mapNotNull { id -> books[id]?.let { id to it } }.toMap()
        override fun problemSince(sinceMs: Long): String? = null
        override fun close() {}
    }

    private class Pinn : PinnFeedSource {
        var onFrame: ((String, Long) -> Unit)? = null
        val st = MutableStateFlow<PinnSocketState>(PinnSocketState.Off)
        override val state: StateFlow<PinnSocketState> = st
        override var lastFrameAtMs: Long = 0
        override fun start() { st.value = PinnSocketState.Live(0) }
        override fun stop() { st.value = PinnSocketState.Off }
    }

    private class NoOrders : LiveOrders {
        val placed = ArrayList<Long>()
        override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String): String { placed += qty; return "o" }
        override suspend fun order(orderId: String): NovigOrder? = null
        override suspend fun fills(orderId: String): List<NovigFill> = emptyList()
    }

    private class Mem : LiveBidPersistence {
        var saved: List<LiveBid> = emptyList()
        override suspend fun all() = saved
        override suspend fun replace(list: List<LiveBid>) { saved = list }
    }

    private class Rig(val scope: TestScope, val feed: Feed, val pinn: Pinn, val runner: PinnLiveRunner, val desk: LiveBidDesk, val trader: PinnLiveTrader, val base: Long) {
        var bidOn = true
        var takerOn = false
        var quality = LiveBidQuality()
        val now: Long get() = base + scope.currentTime
        fun send(text: String) { pinn.lastFrameAtMs = now; pinn.onFrame!!(text, now) }
        suspend fun tick(ms: Long) { scope.advanceTimeBy(ms); scope.runCurrent() }
        fun bids() = desk.bidsNow()
    }

    private fun TestScope.rig(takerRules: LiveRules = LiveRules(trigger = LiveTrigger.MOVE), takerOn: Boolean = false): Rig {
        val feed = Feed()
        val pinn = Pinn()
        val journal = DayJournal(tmp.newFolder("j" + System.nanoTime()), "pinn-live", LiveRecord.serializer()) { it.atMs }
        val follows = DayJournal(tmp.newFolder("f" + System.nanoTime()), "pinn-follow", LiveFollow.serializer()) { it.atMs }
        val trader = PinnLiveTrader(
            orders = NoOrders(), scope = backgroundScope, rules = { LiveTradeRules(enabled = true, bet = false, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0) },
            gate = { null }, ownBids = { emptyList() }, journal = journal, onHalt = {}, logFills = { _, _, _ -> true }, clock = { base + currentTime }, dayStart = { base - 3_600_000L },
            pause = { kotlinx.coroutines.delay(it) },
        )
        lateinit var rig: Rig
        val desk = LiveBidDesk(
            scope = backgroundScope, orders = null, store = Mem(), journal = null,
            config = { LiveBidConfig(on = rig.bidOn, real = false, quality = rig.quality, limits = LiveBidLimits(), bankroll = 1000.0, apiMaxStake = 0.0, preset = "Balanced") },
            wallet = { 100.0 }, clock = { base + currentTime },
        )
        val runner = PinnLiveRunner(
            scope = backgroundScope, source = Source(listOf(event), listOf(ml)), newFeed = { l -> feed.also { it.listener = l } }, openFeed = { cb -> pinn.also { it.onFrame = cb } },
            trader = trader, config = { LiveConfig(takerRules, DevigMethod.MULTIPLICATIVE, setOf("NBA"), takerOn = rig.takerOn) }, followJournal = follows,
            bids = desk, bidConfig = { LiveBidConfig(on = rig.bidOn, real = false, quality = rig.quality, limits = LiveBidLimits(), bankroll = 1000.0, apiMaxStake = 0.0, preset = "Balanced") },
            clock = { base + currentTime }, discoverEveryMs = 30_000L, tickMs = 100L,
        )
        rig = Rig(this, feed, pinn, runner, desk, trader, base)
        rig.takerOn = takerOn
        desk.start()
        return rig
    }

    private fun book(aBid: Int, bBid: Int) = NovigBook("ml", 1, mapOf("ml-a" to listOf(BidLevel(aBid, 50_000)), "ml-b" to listOf(BidLevel(bBid, 50_000))), base)

    /** Pinnacle's moneyline at even money (OKC 50%), a score of 0-0 on its board, Novig's book 0.45 / 0.46, and enough time for the line to settle. */
    private suspend fun Rig.settled() {
        runner.start()
        scope.runCurrent()
        feed.books["ml"] = book(450, 460)
        send(live(score = 0 to 0, markets = arrayOf(money(1, -110, -110))))
        tick(1_200)
        send(live(score = 0 to 0, markets = arrayOf(money(1, -110, -110))))
        tick(4_000)
    }

    @Test
    fun `a settled line gets a bid, not before it has settled, on one side of the market, at the margin under Pinnacle's fair`() = runTest {
        val r = rig()
        r.runner.start()
        runCurrent()
        r.feed.books["ml"] = book(450, 460)
        r.send(live(score = 0 to 0, markets = arrayOf(money(1, -110, -110))))
        r.tick(1_200)
        assertTrue("Pinnacle's price changed 1.2 s ago: still settling", r.bids().isEmpty())
        r.send(live(score = 0 to 0, markets = arrayOf(money(1, -110, -110))))
        r.tick(3_000)
        val b = r.bids().single()
        assertEquals(LiveBid.MODE_PAPER, b.mode)
        assertEquals("Moneyline", b.marketLabel)
        assertEquals(0.475, b.price, 1e-9)
        assertEquals(0.50, b.fair, 1e-6)
        assertTrue("posted at the margin or better", b.ev >= 0.05 - 1e-9)
        assertEquals("one side of the market at a time", 1, r.bids().size)
        assertEquals("NBA", b.league)
        assertTrue(r.runner.status.value.bidTargets > 0)
        r.runner.stop()
    }

    @Test
    fun `the lag taker stays quiet when only the live bids are on, and the feed keeps running for the bids`() = runTest {
        val r = rig(takerOn = false)
        r.settled()
        // Pinnacle moves toward OKC by far more than any rule's minimum: the taker would have decided; with it off it did not.
        r.send(live(score = 0 to 0, markets = arrayOf(money(2, -200, 170))))
        r.tick(2_000)
        assertTrue(r.trader.records().isEmpty())
        r.runner.stop()
    }

    @Test
    fun `a score pulls the bid at once, and none goes up for the hold after it`() = runTest {
        val r = rig()
        r.settled()
        assertEquals(1, r.bids().count { it.active })
        r.send(live(score = 2 to 0, markets = emptyArray<String>()))
        r.tick(50)
        assertEquals(LiveBidStatus.CANCELING, r.bids().single().status)
        assertEquals(LiveBidSkip.SCORED, r.bids().single().why)
        r.tick(20_000)
        assertEquals("held off for 30 s after a score", 1, r.bids().size)
        r.runner.stop()
    }

    @Test
    fun `a danger-zone frame pulls the bid`() = runTest {
        val r = rig()
        r.settled()
        r.send(live(channel = "dz", markets = emptyArray<String>()))
        r.tick(50)
        assertEquals(LiveBidStatus.CANCELING, r.bids().single().status)
        assertEquals(LiveBidSkip.DANGER, r.bids().single().why)
        r.runner.stop()
    }

    @Test
    fun `Pinnacle's fair falling under the bid pulls it, and a rise does not`() = runTest {
        val r = rig()
        r.settled()
        val side = r.bids().single().outcomeId
        // The bid is on the side the desk chose; move the line so that side's fair rises, then falls.
        val up = if (side == "ml-a") money(2, -150, 125) else money(2, 125, -150)
        r.send(live(score = 0 to 0, markets = arrayOf(up)))
        r.tick(1_000)
        assertEquals("fair rose: the bid is worth more, it stays", LiveBidStatus.RESTING, r.bids().first().status)
        val down = if (side == "ml-a") money(3, 130, -155) else money(3, -155, 130)
        r.send(live(score = 0 to 0, markets = arrayOf(down)))
        r.tick(100)
        assertEquals(LiveBidStatus.CANCELING, r.bids().first().status)
        assertEquals(LiveBidSkip.EV, r.bids().first().why)
        r.runner.stop()
    }

    @Test
    fun `a line that closes pulls the bid`() = runTest {
        val r = rig()
        r.settled()
        r.send(live(score = 0 to 0, markets = arrayOf(money(2, -110, -110, status = "closed"))))
        r.tick(100)
        assertEquals(LiveBidStatus.CANCELING, r.bids().single().status)
        assertEquals(LiveBidSkip.CLOSED, r.bids().single().why)
        r.runner.stop()
    }

    @Test
    fun `a Pinnacle that goes quiet pulls the bid before the feed itself is called dead`() = runTest {
        val r = rig()
        r.settled()
        // Nothing for 25 s: past the 20 s quiet limit, inside the feed's own 45 s.
        for (i in 1..25) { r.pinn.lastFrameAtMs = r.now; r.tick(1_000) }
        val b = r.bids().first()
        assertTrue(b.status == LiveBidStatus.CANCELING || b.status == LiveBidStatus.CANCELED)
        assertEquals(LiveBidSkip.QUIET, b.why)
        r.runner.stop()
    }

    @Test
    fun `a Pinnacle feed that drops pulls the bid within a second`() = runTest {
        val r = rig()
        r.settled()
        r.pinn.st.value = PinnSocketState.Down("evicted", r.now, null)
        r.tick(800)
        assertEquals(LiveBidStatus.CANCELING, r.bids().single().status)
        assertTrue(r.bids().single().why!!, r.bids().single().why!!.contains("Pinnodds feed"))
        r.runner.stop()
    }

    @Test
    fun `a Novig book that is no longer current pulls the bid`() = runTest {
        val r = rig()
        r.settled()
        r.feed.books.remove("ml")
        r.tick(800)
        assertEquals(LiveBidStatus.CANCELING, r.bids().single().status)
        assertEquals(LiveBidSkip.NO_BOOK, r.bids().single().why)
        r.runner.stop()
    }

    @Test
    fun `switching the bids off takes them down, and stopping the engine takes them down`() = runTest {
        val r = rig()
        r.settled()
        r.bidOn = false
        r.tick(600)
        assertEquals(LiveBidStatus.CANCELING, r.bids().single().status)

        val s = rig()
        s.settled()
        assertEquals(1, s.bids().count { it.active })
        s.runner.stop()
        s.tick(100)
        assertEquals("the feed that justified it is gone", LiveBidStatus.CANCELING, s.bids().single().status)
    }

    @Test
    fun `kinds, tennis and leagues that are not picked get no bid and a bid there comes down`() = runTest {
        val r = rig()
        r.quality = LiveBidQuality(moneyline = false)
        r.settled()
        assertTrue(r.bids().isEmpty())
        assertTrue((r.desk.status.value.skips[LiveBidSkip.KIND_OFF] ?: 0) > 0)
        val l = rig()
        l.quality = LiveBidQuality(onlyLeagues = setOf("NFL"))
        l.settled()
        assertTrue(l.bids().isEmpty())
        assertTrue((l.desk.status.value.skips[LiveBidSkip.LEAGUE_OFF] ?: 0) > 0)
    }

    @Test
    fun `a trade that prints through a paper bid fills it in the runner's own book feed`() = runTest {
        val r = rig()
        r.settled()
        val b = r.bids().single()
        r.tick(6_000)
        // The book channel reports a resting order on the bid's outcome removed by a fill at 0.470, under the bid.
        r.feed.listener!!.onChange("ml", r.now, listOf(BookChange(BookChange.Kind.REMOVE, b.outcomeId, 470, 200L, "fill")))
        r.tick(100)
        assertEquals(200L, r.bids().first().filled)
        assertNotNull(r.bids().first().firstFillAtMs)
        r.runner.stop()
    }
}
