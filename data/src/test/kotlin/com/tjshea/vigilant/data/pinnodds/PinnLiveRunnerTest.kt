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

/** The live engine end to end on fakes and virtual time: Pinnodds frames in, a Novig book that lags or has caught up, a decision out. No order can leave: the trader is in paper mode or has a fake. */
@OptIn(ExperimentalCoroutinesApi::class)
class PinnLiveRunnerTest {
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

    private class Rig(val scope: TestScope, val feed: Feed, val pinn: Pinn, val runner: PinnLiveRunner, val trader: PinnLiveTrader, val orders: NoOrders, val follows: DayJournal<LiveFollow>, val journal: DayJournal<LiveRecord>)

    private fun TestScope.rig(withKey: Boolean = true, bet: Boolean = false, markets: List<NovigMarket> = listOf(ml, farSpread), rules: LiveRules = LiveRules(trigger = LiveTrigger.MOVE)): Rig {
        val feed = Feed()
        val pinn = Pinn()
        val orders = NoOrders()
        val journal = DayJournal(tmp.newFolder("j" + System.nanoTime()), "pinn-live", LiveRecord.serializer()) { it.atMs }
        val follows = DayJournal(tmp.newFolder("f" + System.nanoTime()), "pinn-follow", LiveFollow.serializer()) { it.atMs }
        val trader = PinnLiveTrader(
            orders = orders, scope = backgroundScope, rules = { LiveTradeRules(enabled = true, bet = bet, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0) },
            gate = { null }, ownBids = { emptyList() }, journal = journal, onHalt = {}, logFills = { _, _, _ -> true }, clock = { base + currentTime }, dayStart = { base - 3_600_000L },
            pause = { kotlinx.coroutines.delay(it) },
        )
        val runner = PinnLiveRunner(
            scope = backgroundScope, source = Source(listOf(event), markets), newFeed = { l -> if (withKey) feed.also { it.listener = l } else null },
            openFeed = { cb -> pinn.also { it.onFrame = cb } }, trader = trader, config = { LiveConfig(rules, DevigMethod.MULTIPLICATIVE, setOf("NBA")) },
            followJournal = follows, clock = { base + currentTime }, discoverEveryMs = 30_000L, tickMs = 100L,
        )
        return Rig(this, feed, pinn, runner, trader, orders, follows, journal)
    }

    private fun Rig.send(text: String) { pinn.lastFrameAtMs = base + scope.currentTime; pinn.onFrame!!(text, base + scope.currentTime) }

    private fun bookOf(id: String, aBid: Int, bBid: Int, a: String, b: String) = NovigBook(id, 1, mapOf(a to listOf(BidLevel(aBid, 50_000)), b to listOf(BidLevel(bBid, 50_000))), base)

    /** Novig's moneyline: bid 0.46 on OKC and 0.50 on MIL, so OKC costs 0.50 and MIL costs 0.54: it still prices a game at about even. */
    private fun Rig.stale() { feed.books["ml"] = bookOf("ml", 460, 500, "ml-a", "ml-b") }

    private suspend fun Rig.pinnacleMoves() {
        send(live(markets = arrayOf(money(1, -110, -110), PinnTestFrames.spread(1, -31.5, -110, -110))))
        scope.advanceTimeBy(1_000); scope.runCurrent()
        // Pinnacle reprices to -150/+125: OKC's fair goes from 50% to about 57%.
        send(live(markets = arrayOf(money(2, -150, 125))))
    }

    @Test
    fun `Pinnacle moves and Novig lags - a bet is decided after the price settles, and followed up at 30 and 120 seconds`() = runTest {
        val r = rig()
        r.runner.start()
        runCurrent()
        r.stale()
        r.pinnacleMoves()
        advanceTimeBy(200); runCurrent()
        assertEquals("not yet: Pinnacle's price has not sat for 500 ms", 0, r.trader.records().size)
        advanceTimeBy(600); runCurrent()
        val rec = r.trader.records().single()
        assertEquals("PAPER", rec.outcome)
        assertEquals("Oklahoma City Thunder", rec.selection)
        assertEquals("Moneyline", rec.market)
        assertEquals("ml-a", rec.outcomeId)
        assertEquals(0.50, rec.ask, 1e-9)
        assertTrue("EV after the fee", rec.ev > 0.10)
        assertTrue(r.orders.placed.isEmpty())
        assertTrue("both books were asked for", r.feed.watched.containsAll(listOf("ml")))
        advanceTimeBy(125_000); runCurrent()
        assertEquals(listOf(30, 120), r.follows.readAll().map { it.offsetSec }.sorted())
        r.runner.stop()
    }

    @Test
    fun `a Novig price that has already caught up is not a bet`() = runTest {
        val r = rig()
        r.runner.start()
        runCurrent()
        // OKC already costs 0.58 (a bid of 0.42 on MIL): the lag has closed.
        r.feed.books["ml"] = bookOf("ml", 380, 420, "ml-a", "ml-b")
        r.pinnacleMoves()
        advanceTimeBy(1_500); runCurrent()
        assertTrue(r.trader.records().isEmpty())
        assertTrue((r.runner.status.value.skips[LiveSkip.EV] ?: 0) > 0)
        r.runner.stop()
    }

    @Test
    fun `a Novig book that is not current, a feed that is down, and a missing key each stop it`() = runTest {
        val r = rig()
        r.runner.start()
        runCurrent()
        r.pinnacleMoves()
        advanceTimeBy(1_500); runCurrent()
        assertTrue("no pushed book: nothing to bet against", r.trader.records().isEmpty())
        assertTrue((r.runner.status.value.skips[LiveSkip.STALE_BOOK] ?: 0) > 0)
        r.runner.stop()

        val r2 = rig()
        r2.runner.start(); runCurrent()
        r2.stale()
        r2.pinnacleMoves()
        advanceTimeBy(100); runCurrent()
        r2.pinn.st.value = PinnSocketState.Down("evicted", base, null)
        advanceTimeBy(1_500); runCurrent()
        assertTrue(r2.trader.records().isEmpty())
        r2.runner.stop()

        val r3 = rig(withKey = false)
        r3.runner.start(); runCurrent()
        assertTrue(r3.runner.status.value.problem!!.contains("No Novig key"))
        assertTrue(!r3.runner.running || r3.runner.status.value.running == false)
    }

    @Test
    fun `only spreads and totals near Pinnacle's own line are held open`() = runTest {
        val r = rig(markets = listOf(ml, farSpread, NovigMarket("near", "ev1", "SPREAD", "OPEN", "OKC -30.5", base, MarketFee.GAME, listOf(NovigOutcome("near-a", "OKC -30.5", "TBD"), NovigOutcome("near-b", "MIL +30.5", "TBD")), -30.5)))
        r.runner.start(); runCurrent()
        r.send(live(markets = arrayOf(money(1, -110, -110), PinnTestFrames.spread(1, -31.5, -110, -110))))
        advanceTimeBy(6_000); runCurrent()
        assertEquals("the moneyline first, then the spreads by distance from Pinnacle's -31.5", listOf("ml", "far", "near"), r.feed.watched)
        r.runner.stop()
    }

    @Test
    fun `a danger zone frame stops a bet that was about to be made`() = runTest {
        val r = rig()
        r.runner.start(); runCurrent()
        r.stale()
        r.pinnacleMoves()
        advanceTimeBy(300); runCurrent()
        r.send(live(channel = "dz", markets = emptyArray<String>()))
        advanceTimeBy(1_000); runCurrent()
        assertTrue(r.trader.records().isEmpty())
        assertTrue((r.runner.status.value.skips[LiveSkip.VOLATILE] ?: 0) > 0)
        r.runner.stop()
    }

    @Test
    fun `a stale prematch matchup is never traded against a game already under way`() = runTest {
        val r = rig(rules = LiveRules(pregame = true, trigger = LiveTrigger.MOVE))
        r.runner.start(); runCurrent()
        r.stale()
        // Only a prematch (not live) Pinnacle matchup exists for the teams; the Novig game is live.
        r.send(live(isLive = false, channel = "pre", start = "2026-10-08T23:00:00Z", markets = arrayOf(money(1, -110, -110))))
        advanceTimeBy(1_000); runCurrent()
        r.send(live(isLive = false, channel = "pre", start = "2026-10-08T23:00:00Z", markets = arrayOf(money(2, -150, 125))))
        advanceTimeBy(6_000); runCurrent()
        assertTrue(r.trader.records().isEmpty())
        assertEquals(0, r.runner.status.value.matched)
        r.runner.stop()
    }

    @Test
    fun `a prematch frame is recognised from its topic, a live one is not, and pregame off skips them unparsed`() = runTest {
        assertTrue(PinnLiveRunner.isPrematchFrame(live(isLive = false, channel = "pre", markets = arrayOf(money(1, -110, -110)))))
        assertTrue(!PinnLiveRunner.isPrematchFrame(live(markets = arrayOf(money(1, -110, -110)))))
        assertTrue(!PinnLiveRunner.isPrematchFrame("""{"type":"ping","ts":1}"""))
        val r = rig()
        r.runner.start(); runCurrent()
        r.send(live(isLive = false, channel = "pre", start = "2026-10-08T23:00:00Z", markets = arrayOf(money(1, -110, -110))))
        advanceTimeBy(2_000); runCurrent()
        assertEquals("not read at all with pregame off", 0, r.runner.status.value.pinnEvents)
        r.runner.stop()
    }

    @Test
    fun `stop closes both feeds and the status says off`() = runTest {
        val r = rig()
        r.runner.start(); runCurrent()
        assertTrue(r.pinn.started)
        r.runner.stop()
        runCurrent()
        assertTrue(!r.pinn.started)
        assertEquals(false, r.runner.status.value.running)
        assertNotNull(r.runner.status.value)
    }
}
