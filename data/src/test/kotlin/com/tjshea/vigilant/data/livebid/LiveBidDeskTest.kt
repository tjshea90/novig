package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.stream.BookChange
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live bid desk on a fake Novig and virtual time: what goes up, what comes down and when, and that nothing is ever sent that the limits, the wallet or a stop forbid. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveBidDeskTest {
    private val base = 1_791_420_000_000L

    private class Placed(val orderId: String, val outcomeId: String, val price: Double, val qty: Long, val ttlMs: Long, val clientId: String)

    private class FakeOrders : LiveBidOrders {
        val placed = ArrayList<Placed>()
        val cancels = ArrayList<List<String>>()
        val open = ArrayList<NovigOrder>()
        val records = HashMap<String, NovigOrder>()
        val fillsBy = HashMap<String, List<NovigFill>>()
        var failure: Exception? = null
        var placeDelayMs = 0L
        var found: NovigOrder? = null
        var cancelStatus: String? = "OPEN"
        private var n = 0
        override suspend fun place(outcomeId: String, price: Double, qty: Long, ttlMs: Long, clientId: String): String {
            if (placeDelayMs > 0) delay(placeDelayMs)
            failure?.let { failure = null; throw it }
            val id = "o${++n}"
            placed += Placed(id, outcomeId, price, qty, ttlMs, clientId)
            return id
        }
        override suspend fun cancel(orderIds: List<String>): Map<String, String?> { cancels += orderIds; return orderIds.associateWith { cancelStatus } }
        override suspend fun open(): List<NovigOrder> = open.toList()
        override suspend fun find(clientId: String, outcomeId: String): NovigOrder? = found
        override suspend fun order(orderId: String): NovigOrder? = records[orderId]
        override suspend fun fills(orderId: String): List<NovigFill> = fillsBy[orderId].orEmpty()
        var fillsOfCalls = 0
        override suspend fun fillsOf(orderIds: Collection<String>, startsAfterMs: Long): Map<String, List<NovigFill>> { fillsOfCalls++; return orderIds.associateWith { fillsBy[it].orEmpty() } }

        /** The order is resting on Novig's book with [remaining] contracts left. */
        fun rest(p: Placed, remaining: Long = p.qty, at: Long = 0L) {
            open.removeAll { it.orderId == p.orderId }
            open += NovigOrder(p.orderId, p.clientId, "m-${p.outcomeId}", p.outcomeId, p.price, p.qty, remaining, "PO", "OPEN", at, null)
        }
        fun drop(p: Placed) { open.removeAll { it.orderId == p.orderId } }
    }

    private class Mem : LiveBidPersistence {
        var saved: List<LiveBid> = emptyList()
        override suspend fun all() = saved
        override suspend fun replace(list: List<LiveBid>) { saved = list }
    }

    private class Rig(val scope: TestScope, val desk: LiveBidDesk, val fake: FakeOrders, val mem: Mem, val logged: ArrayList<Triple<BetTarget, String, List<NovigFill>>>, val halts: ArrayList<String>) {
        var cfg: LiveBidConfig = LiveBidConfig.OFF
        var wallet: Double? = 100.0
        var otherResting = 0.0
        var loss = 0.0
        val now: Long get() = BASE + scope.currentTime
        suspend fun tick(ms: Long) { scope.advanceTimeBy(ms); scope.runCurrent() }
        fun bids() = desk.bidsNow()
        fun only(): LiveBid = desk.bidsNow().single()

        /** Vouches for [outcomes] every half second for [ms] (the runner does this at least twice a second). */
        suspend fun hold(ms: Long, vararg outcomes: String) {
            var left = ms
            while (left > 0) { outcomes.forEach { desk.keepAlive(it, now) }; tick(500); left -= 500 }
        }
        companion object { const val BASE = 1_791_420_000_000L }
    }

    private fun TestScope.rig(real: Boolean = true, quality: LiveBidQuality = LiveBidQuality(), limits: LiveBidLimits = LiveBidLimits(), withOrders: Boolean = true): Rig {
        val fake = FakeOrders()
        val mem = Mem()
        val logged = ArrayList<Triple<BetTarget, String, List<NovigFill>>>()
        val halts = ArrayList<String>()
        lateinit var rig: Rig
        val desk = LiveBidDesk(
            scope = backgroundScope, orders = if (withOrders) fake else null, store = mem, journal = null,
            config = { rig.cfg }, wallet = { rig.wallet }, otherRestingDollars = { rig.otherResting }, spentToday = { 0.0 }, dayLimit = { 1_000.0 }, lossToday = { rig.loss },
            logFills = { t, id, f -> logged += Triple(t, id, f); "bet1" }, onHalt = { halts += it }, clock = { Rig.BASE + currentTime },
        )
        rig = Rig(this, desk, fake, mem, logged, halts)
        rig.cfg = LiveBidConfig(on = true, real = real, quality = quality, limits = limits, bankroll = 1000.0, apiMaxStake = 0.0, preset = "Balanced")
        desk.start()
        runCurrent()
        return rig
    }

    private fun want(at: Long, outcome: String = "oa", market: String = "m-oa", event: String = "e1", price: Double = 0.475, fair: Double = 0.50) = LiveBidWant(
        atMs = at, outcomeId = outcome, marketId = market, eventId = event, pinnEventId = 1L, league = "NBA", eventName = "Milwaukee Bucks @ Oklahoma City Thunder", startsTs = at - 600_000L,
        marketLabel = "Moneyline", selection = "Oklahoma City Thunder", fee = MarketFee.GAME, score = "10-8", clock = "Q1 5:00",
        verdict = LiveBidVerdict.Post(price, fair, fair / price - 1.0, 0.0, 0.45, 0.52, 0.485, true, 0.06, 3000.0),
    )

    private fun fill(id: String, orderId: String, qty: Long, price: Double = 0.475, ts: Long = 0L, outcome: String = "oa") =
        NovigFill(id, orderId, null, "m-$outcome", outcome, qty, qty * price * 0.01, false, 0.0, ts)

    // ---- paper ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `paper - a justified bid is recorded and nothing is sent`() = runTest {
        val r = rig(real = false)
        r.desk.want(want(r.now))
        r.tick(300)
        val b = r.only()
        assertEquals(LiveBid.MODE_PAPER, b.mode)
        assertEquals(LiveBidStatus.RESTING, b.status)
        assertEquals(0.475, b.price, 1e-9)
        assertEquals("a paper bid is fillable only after the delay a real one takes", b.postedAtMs + LiveBidDesk.PAPER_LATENCY_MS, b.activeFromMs)
        assertTrue(r.fake.placed.isEmpty())
        assertEquals("paper", r.desk.status.value.mode)
        assertEquals(1, r.desk.status.value.active)
    }

    @Test
    fun `paper - a trade at the price fills it after the delay and a trade through it is strict, and the rest is pulled`() = runTest {
        val r = rig(real = false)
        r.desk.want(want(r.now))
        r.tick(300)
        val oc = "oa"
        // Before the delay a trade cannot fill it.
        r.desk.onBook("m-oa", r.now, listOf(BookChange(BookChange.Kind.REMOVE, oc, 470, 100L, "fill")))
        assertEquals(0L, r.only().filled)
        r.hold(5_500, oc)
        // A trade at a better price for the seller (0.480 > 0.475) does not reach it.
        r.desk.onBook("m-oa", r.now, listOf(BookChange(BookChange.Kind.REMOVE, oc, 480, 100L, "fill")))
        assertEquals(0L, r.only().filled)
        // A trade at 0.470, under the bid: filled, strictly through the price.
        r.desk.onBook("m-oa", r.now, listOf(BookChange(BookChange.Kind.REMOVE, oc, 470, 300L, "fill")))
        val b = r.only()
        assertEquals(300L, b.filled)
        assertTrue(b.strict)
        assertEquals(300 * 0.475 * 0.01, b.paid, 1e-9)
        assertEquals("a cancel (not a fill) is not a trade", 300L, run { r.desk.onBook("m-oa", r.now, listOf(BookChange(BookChange.Kind.REMOVE, oc, 470, 100L, "cancel"))); r.only().filled })
        r.tick(500)
        assertEquals("the unfilled rest is pulled after a partial fill", LiveBidStatus.CANCELING, r.only().status)
        assertTrue("paper fills never reach the Tracker", r.logged.isEmpty())
    }

    @Test
    fun `paper - a bid nobody vouches for is pulled by itself, and ends after the delay a real pull takes`() = runTest {
        val r = rig(real = false)
        r.desk.want(want(r.now))
        r.tick(300)
        r.tick(LiveBidDesk.CLAIM_TTL_MS + 500)
        assertEquals(LiveBidStatus.CANCELING, r.only().status)
        assertEquals(LiveBidSkip.CLAIM_GONE, r.only().why)
        r.tick(LiveBidDesk.PAPER_LATENCY_MS + 500)
        assertEquals(LiveBidStatus.CANCELED, r.only().status)
        assertEquals(1, r.desk.status.value.pulls[LiveBidSkip.CLAIM_GONE])
    }

    @Test
    fun `paper - a vouched-for bid stays until its ttl runs out and then ends`() = runTest {
        val r = rig(real = false, quality = LiveBidQuality(ttlSec = 20, refreshBeforeSec = 5))
        r.desk.want(want(r.now))
        r.tick(300)
        r.hold(15_000, "oa")
        assertEquals(LiveBidStatus.RESTING, r.bids().first().status)
        r.hold(7_000, "oa")
        assertEquals(LiveBidStatus.EXPIRED, r.bids().first().status)
    }

    // ---- real: going up ----------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `real - a bid is a post-only order with a ttl, sized an eighth Kelly and held to the cap, and its timings are kept`() = runTest {
        val r = rig(limits = LiveBidLimits(maxStake = 5.0))
        r.fake.placeDelayMs = 400
        r.desk.want(want(r.now))
        r.tick(300)
        assertEquals("the order is being sent", LiveBidStatus.SENDING, r.only().status)
        r.tick(300)
        val p = r.fake.placed.single()
        assertEquals("oa", p.outcomeId)
        assertEquals(0.475, p.price, 1e-9)
        assertEquals("$5 at 0.475", 1052L, p.qty)
        assertEquals("the rules' ttl is the order's whole life", 30_000L, p.ttlMs)
        assertEquals(LiveBidStatus.SENT, r.only().status)
        r.fake.rest(p, at = r.now)
        r.hold(1_200, "oa")
        val b = r.only()
        assertEquals(LiveBidStatus.RESTING, b.status)
        assertNotNull(b.ackMs)
        assertNotNull(b.openMs)
        assertTrue("accepted after the 400 ms the fake took", b.ackMs!! >= 400)
        assertTrue(r.desk.status.value.timing, r.desk.status.value.timing.contains("order accepted"))
        assertEquals(mapOf("oa" to mapOf(475 to 1052L)), r.desk.ownLevels())
        assertNotNull(r.desk.held("oa"))
        assertEquals(0.475, r.desk.held("oa")!!.price, 1e-9)
        assertTrue(r.desk.busy("m-oa"))
    }

    @Test
    fun `real - the wallet must cover a bid beside every bid already up, with the reserve kept`() = runTest {
        val r = rig(limits = LiveBidLimits(maxStake = 5.0, walletReserve = 5.0))
        r.wallet = 12.0   // 12 - 5 reserve = 7: one $5 bid fits, a second does not
        r.desk.want(want(r.now, "o1", "m1"))
        r.desk.want(want(r.now, "o2", "m2"))
        r.tick(500)
        assertEquals(1, r.fake.placed.size)
        r.wallet = null
        r.desk.want(want(r.now, "o3", "m3"))
        r.tick(500)
        assertEquals("no wallet reading: nothing goes up", 1, r.fake.placed.size)
        r.wallet = 100.0
        r.otherResting = 95.0   // the pregame bids' money is spoken for
        r.desk.want(want(r.now, "o4", "m4"))
        r.tick(500)
        assertEquals(1, r.fake.placed.size)
    }

    @Test
    fun `real - the most bids, the most per game and the most per game in dollars each hold`() = runTest {
        val wide = LiveBidLimits(maxStake = 2.0, minStake = 1.0, maxBids = 10, maxBidsPerGame = 10, maxPerGame = 100.0, maxPerDay = 100.0, walletReserve = 0.0)
        // Dollars in one game: two $2 bids would be $4 over a $3 limit.
        val dollars = rig(limits = wide.copy(maxPerGame = 3.0))
        dollars.wallet = 1000.0
        dollars.desk.want(want(dollars.now, "a1", "ma1", "eA"))
        dollars.desk.want(want(dollars.now, "a2", "ma2", "eA"))
        dollars.tick(800)
        assertEquals(1, dollars.fake.placed.size)
        assertTrue(dollars.desk.status.value.skips.keys.any { it.contains("the most for one game") })
        // Bids in one game.
        val perGame = rig(limits = wide.copy(maxBidsPerGame = 2))
        perGame.wallet = 1000.0
        listOf("a1", "a2", "a3").forEach { perGame.desk.want(want(perGame.now, it, "m$it", "eA")) }
        perGame.tick(800)
        assertEquals(2, perGame.fake.placed.size)
        // Bids in all.
        val all = rig(limits = wide.copy(maxBids = 2))
        all.wallet = 1000.0
        listOf("a1", "b1", "c1").forEach { all.desk.want(want(all.now, it, "m$it", "e$it")) }
        all.tick(800)
        assertEquals(2, all.fake.placed.size)
        assertTrue(all.desk.status.value.skips.keys.any { it.contains("the most bids up") })
        // Dollars in a day.
        val day = rig(limits = wide.copy(maxPerDay = 3.0))
        day.wallet = 1000.0
        listOf("a1", "b1").forEach { day.desk.want(want(day.now, it, "m$it", "e$it")) }
        day.tick(800)
        assertEquals(1, day.fake.placed.size)
    }

    @Test
    fun `real - the other side of a market is left alone unless both sides are asked for`() = runTest {
        val r = rig()
        r.desk.want(want(r.now, "oa", "m1"))
        r.desk.want(want(r.now, "ob", "m1"))
        r.tick(600)
        assertEquals(1, r.fake.placed.size)
        val both = rig(quality = LiveBidQuality(bothSides = true))
        both.desk.want(want(both.now, "oa", "m1"))
        both.desk.want(want(both.now, "ob", "m1"))
        both.tick(600)
        assertEquals(2, both.fake.placed.size)
    }

    @Test
    fun `real - a want older than a few seconds is not acted on`() = runTest {
        val r = rig()
        r.desk.want(want(r.now - 10_000L))
        r.tick(800)
        assertTrue(r.fake.placed.isEmpty())
        assertTrue(r.bids().isEmpty())
    }

    @Test
    fun `real - with no key a real bid never goes up`() = runTest {
        val r = rig(withOrders = false)
        r.desk.want(want(r.now))
        r.tick(800)
        assertTrue(r.bids().isEmpty())
    }

    // ---- real: coming down ---------------------------------------------------------------------------------------------------------------------------------------------------

    private suspend fun Rig.up(outcome: String = "oa", market: String = "m-oa", event: String = "e1"): Placed {
        desk.want(want(now, outcome, market, event))
        tick(500)
        val p = fake.placed.last { it.outcomeId == outcome }
        fake.rest(p, at = now)
        hold(1_200, outcome)
        return p
    }

    /** Several bids up at once, all vouched for while they rest (one at a time, an earlier one would go unvouched for long enough to be pulled). */
    private suspend fun Rig.upMany(vararg specs: Triple<String, String, String>): List<Placed> {
        specs.forEach { desk.want(want(now, it.first, it.second, it.third)) }
        tick(600)
        val ps = specs.map { s -> fake.placed.last { it.outcomeId == s.first } }
        ps.forEach { fake.rest(it, at = now) }
        hold(1_500, *specs.map { it.first }.toTypedArray())
        return ps
    }

    @Test
    fun `real - a pull sends the cancel at once, and the bid is pulled only when Novig's list no longer has it, with the time it took kept`() = runTest {
        val r = rig()
        val p = r.up()
        r.desk.pull("oa", LiveBidSkip.SCORED)
        r.tick(50)
        assertEquals("the cancel is sent from the call itself", listOf(listOf(p.orderId)), r.fake.cancels)
        assertEquals(LiveBidStatus.CANCELING, r.only().status)
        assertNull("a bid on its way down is not 'held': nothing re-posts over it", r.desk.held("oa"))
        r.tick(1_500)
        assertEquals("still on Novig's book: still being pulled", LiveBidStatus.CANCELING, r.only().status)
        r.fake.drop(p)
        r.tick(1_200)
        val b = r.only()
        assertEquals(LiveBidStatus.CANCELED, b.status)
        assertEquals(LiveBidSkip.SCORED, b.why)
        assertNotNull(b.pullMs)
        assertTrue(r.desk.status.value.timing, r.desk.status.value.timing.contains("pulled in"))
        assertTrue(r.logged.isEmpty())
    }

    @Test
    fun `real - a cancel that is not confirmed is sent again`() = runTest {
        val r = rig()
        r.up()
        r.desk.pull("oa", "test")
        r.tick(50)
        assertEquals(1, r.fake.cancels.size)
        r.tick(LiveBidDesk.CANCEL_RETRY_MS + 600)
        assertTrue("sent again while it is still there", r.fake.cancels.size >= 2)
    }

    @Test
    fun `real - a bid nobody vouches for comes down within seconds, and no longer vouched for means no new bid`() = runTest {
        val r = rig()
        val p = r.up()
        r.tick(LiveBidDesk.CLAIM_TTL_MS + 600)
        assertEquals(listOf(listOf(p.orderId)), r.fake.cancels.take(1))
        assertEquals(LiveBidSkip.CLAIM_GONE, r.only().why)
    }

    @Test
    fun `real - a pull that arrives while the order is still being sent cancels it the moment Novig answers`() = runTest {
        val r = rig()
        r.fake.placeDelayMs = 3_000
        r.desk.want(want(r.now))
        r.tick(600)
        assertEquals(LiveBidStatus.SENDING, r.only().status)
        r.desk.pull("oa", LiveBidSkip.DANGER)
        r.tick(2_600)
        assertEquals("the order was accepted and then cancelled", 1, r.fake.placed.size)
        assertEquals(listOf(r.fake.placed[0].orderId), r.fake.cancels.single())
        assertEquals(LiveBidStatus.CANCELING, r.only().status)
    }

    @Test
    fun `real - STOP ALL, a pause, switching off, a mode change and a halt each take every bid down and post nothing`() = runTest {
        val r = rig()
        val p = r.up()
        r.cfg = r.cfg.copy(blockedWhy = "STOP ALL is on")
        r.tick(600)
        assertEquals(listOf(listOf(p.orderId)), r.fake.cancels)
        assertEquals("STOP ALL is on", r.only().why)
        r.desk.want(want(r.now, "ob", "m-ob"))
        r.tick(800)
        assertEquals("nothing new while blocked", 1, r.fake.placed.size)

        val off = rig()
        val p2 = off.up()
        off.cfg = off.cfg.copy(on = false)
        off.tick(600)
        assertEquals(listOf(listOf(p2.orderId)), off.fake.cancels)

        val mode = rig()
        val p3 = mode.up()
        mode.cfg = mode.cfg.copy(real = false)
        mode.tick(600)
        assertEquals("a real bid does not stay up when the mode is paper", listOf(listOf(p3.orderId)), mode.fake.cancels)
        assertEquals("the mode changed", mode.only().why)

        val halted = rig()
        val p4 = halted.up()
        halted.cfg = halted.cfg.copy(halted = "a halt")
        halted.tick(600)
        assertEquals(listOf(listOf(p4.orderId)), halted.fake.cancels)
    }

    @Test
    fun `stopAll cancels every bid in one request and says how many were up`() = runTest {
        val r = rig(limits = LiveBidLimits(maxStake = 2.0, walletReserve = 0.0))
        r.wallet = 1000.0
        val (a, b) = r.upMany(Triple("a1", "ma1", "eA"), Triple("b1", "mb1", "eB"))
        val n = r.desk.stopAll("the live feed stopped")
        r.tick(100)
        assertEquals(2, n)
        assertEquals(1, r.fake.cancels.size)
        assertEquals(setOf(a.orderId, b.orderId), r.fake.cancels[0].toSet())
        assertTrue(r.bids().all { it.status == LiveBidStatus.CANCELING })
    }

    // ---- real: fills and endings ----------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `real - a fill is read, recorded in the Tracker as a Live bids bet, the rest is pulled, and the side cools off`() = runTest {
        val r = rig()
        val p = r.up()
        r.fake.rest(p, remaining = 500L)
        r.fake.fillsBy[p.orderId] = listOf(fill("f1", p.orderId, 552, ts = r.now))
        r.tick(1_200)
        val b = r.only()
        assertEquals(552L, b.filled)
        assertEquals(552 * 0.475 * 0.01, b.paid, 1e-9)
        assertEquals("bet1", b.betId)
        val (target, orderId, fills) = r.logged.single()
        assertEquals(p.orderId, orderId)
        assertEquals(BetTracker.SOURCE_LIVEBID, target.source)
        assertEquals("Moneyline", target.marketLabel)
        assertEquals(1, fills.size)
        assertEquals(listOf(p.orderId), r.fake.cancels.last())
        assertEquals("filled in part: the rest is pulled", b.why)
        r.fake.drop(p)
        r.tick(1_200)
        assertEquals(LiveBidStatus.CANCELED, r.only().status)
        r.desk.want(want(r.now))
        r.tick(800)
        assertEquals("30 s cool-off after a fill", 1, r.fake.placed.size)
        r.tick(31_000)
        r.desk.want(want(r.now))
        r.tick(800)
        assertEquals("then it can bid again", 2, r.fake.placed.size)
    }

    @Test
    fun `real - a bid that fills completely ends FILLED and is recorded`() = runTest {
        val r = rig()
        val p = r.up()
        r.fake.drop(p)
        r.fake.fillsBy[p.orderId] = listOf(fill("f1", p.orderId, 1052, ts = r.now))
        r.tick(1_200)
        assertEquals(LiveBidStatus.FILLED, r.only().status)
        assertEquals(1, r.logged.size)
        assertEquals(1052L, r.only().filled)
    }

    @Test
    fun `real - several bids that end together are read in one request, not one each`() = runTest {
        val r = rig(limits = LiveBidLimits(maxStake = 2.0, walletReserve = 0.0, maxBids = 10, maxBidsPerGame = 10, maxPerGame = 100.0, maxPerDay = 100.0))
        r.wallet = 1000.0
        val ps = r.upMany(Triple("a1", "ma1", "eA"), Triple("b1", "mb1", "eB"), Triple("c1", "mc1", "eC"))
        r.fake.fillsOfCalls = 0
        r.desk.stopAll("test")
        r.tick(100)
        ps.forEach { r.fake.drop(it) }
        r.tick(1_300)
        assertTrue(r.bids().all { it.status == LiveBidStatus.CANCELED })
        assertEquals("one fills read for all three", 1, r.fake.fillsOfCalls)
    }

    @Test
    fun `real - a fill that showed up late, after the bid was written down as pulled with none, is found and recorded`() = runTest {
        val r = rig()
        val p = r.up()
        r.desk.pull("oa", "test")
        r.tick(100)
        r.fake.drop(p)
        r.tick(1_300)
        assertEquals(LiveBidStatus.CANCELED, r.only().status)
        assertEquals(0L, r.only().filled)
        assertTrue(r.logged.isEmpty())
        // Novig's fills list catches up a few seconds after the order ended.
        r.fake.fillsBy[p.orderId] = listOf(fill("late1", p.orderId, 300, ts = r.now))
        r.tick(6_000)
        assertEquals(300L, r.only().filled)
        assertEquals(1, r.logged.size)
        assertEquals("bet1", r.only().betId)
        assertTrue(r.only().why!!.contains("late"))
        r.tick(20_000)
        assertEquals("recorded once", 1, r.logged.size)
    }

    @Test
    fun `real - the wallet counts the bid being replaced too, so a successor waits until the money covers both`() = runTest {
        val r = rig(quality = LiveBidQuality(ttlSec = 30, refreshBeforeSec = 8), limits = LiveBidLimits(maxStake = 5.0, walletReserve = 0.0))
        r.wallet = 9.5   // one bid is $5.00: two would be $9.99
        val first = r.up()
        r.hold(18_000, "oa")
        r.desk.want(want(r.now))
        r.hold(1_500, "oa")
        r.desk.want(want(r.now))
        r.hold(1_500, "oa")
        assertEquals("the wallet cannot cover both for a moment: the successor is not sent", 1, r.fake.placed.size)
        r.wallet = 50.0
        r.desk.want(want(r.now))
        r.hold(1_500, "oa")
        assertEquals(2, r.fake.placed.size)
        assertTrue(first.orderId != r.fake.placed[1].orderId)
    }

    @Test
    fun `real - a bid that runs out its ttl ends EXPIRED with no fill and nothing recorded`() = runTest {
        val r = rig(quality = LiveBidQuality(ttlSec = 20, refreshBeforeSec = 0))
        val p = r.up()
        r.hold(19_000, "oa")
        r.fake.drop(p)
        r.hold(1_500, "oa")
        assertEquals(LiveBidStatus.EXPIRED, r.bids().first().status)
        assertTrue(r.logged.isEmpty())
    }

    @Test
    fun `real - an order Novig has queued in play is waited for, not called over`() = runTest {
        val r = rig()
        r.desk.want(want(r.now))
        r.tick(500)
        val p = r.fake.placed.single()
        // Novig's record says PENDING (the in-play delay): not on the open list yet.
        r.fake.records[p.orderId] = NovigOrder(p.orderId, p.clientId, "m-oa", "oa", p.price, p.qty, p.qty, "PO", "PENDING", 0L, null)
        r.hold(4_000, "oa")
        assertEquals("still waiting, not ended", LiveBidStatus.SENT, r.only().status)
        r.fake.rest(p, at = r.now)
        r.hold(1_500, "oa")
        assertEquals(LiveBidStatus.RESTING, r.only().status)
        assertTrue("on the book after about 5 s", r.only().openMs!! >= 4_000)
    }

    @Test
    fun `real - an order Novig refused after accepting it ends REFUSED`() = runTest {
        val r = rig()
        r.desk.want(want(r.now))
        r.tick(500)
        val p = r.fake.placed.single()
        r.fake.records[p.orderId] = NovigOrder(p.orderId, p.clientId, "m-oa", "oa", p.price, p.qty, p.qty, "PO", "REJECTED", 0L, null)
        r.hold(2_000, "oa")
        assertEquals(LiveBidStatus.REFUSED, r.only().status)
        assertEquals(1, r.desk.status.value.refused)
    }

    // ---- real: when Novig says no ----------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `real - a network refusal stands everything down for ten minutes, a too-many-requests slows it, a market Novig will not trade in play is left alone`() = runTest {
        val r = rig()
        r.fake.failure = NovigApiException(451, "ANONYMIZED_NETWORK", "no")
        r.desk.want(want(r.now))
        r.tick(600)
        assertEquals(LiveBidStatus.REFUSED, r.only().status)
        assertNotNull(r.desk.status.value.problem)
        r.desk.want(want(r.now, "ob", "m-ob"))
        r.tick(800)
        assertEquals("stood down: no other bid is sent", 0, r.fake.placed.size)
        assertNotNull(r.desk.status.value.standDown)
        r.tick(LiveTradeStand + 5_000)
        r.desk.want(want(r.now, "ob", "m-ob"))
        r.tick(800)
        assertEquals("after ten minutes it tries again", 1, r.fake.placed.size)

        val slow = rig()
        slow.fake.failure = NovigApiException(429, "RATE_LIMIT_EXCEEDED", "slow")
        slow.desk.want(want(slow.now))
        slow.tick(600)
        slow.desk.want(want(slow.now, "ob", "m-ob"))
        slow.tick(800)
        assertEquals("backing off for ten seconds", 0, slow.fake.placed.size)
        slow.tick(11_000)
        slow.desk.want(want(slow.now, "ob", "m-ob"))
        slow.tick(800)
        assertEquals(1, slow.fake.placed.size)

        val market = rig()
        market.fake.failure = NovigApiException(400, "NOT_LIVE_TRADABLE", "no")
        market.desk.want(want(market.now, "oa", "m-bad"))
        market.tick(600)
        market.desk.want(want(market.now, "ob", "m-bad"))
        market.tick(800)
        assertEquals("that market is left alone", 0, market.fake.placed.size)
        market.desk.want(want(market.now, "oc", "m-good"))
        market.tick(800)
        assertEquals("others are not", 1, market.fake.placed.size)
    }

    private val LiveTradeStand = 10 * 60_000L

    @Test
    fun `real - an answer that never came is never re-sent, the order is looked for by its client id and taken down`() = runTest {
        val r = rig()
        r.fake.failure = java.io.IOException("connection reset")
        r.desk.want(want(r.now))
        r.tick(600)
        val lost = r.only()
        assertNotNull(lost.lostAtMs)
        assertEquals(0, r.fake.placed.size)
        // It was placed after all: Novig's lists have it, resting.
        val found = NovigOrder("o9", lost.clientId, "m-oa", "oa", 0.475, 1052, 1052, "PO", "OPEN", 0L, null)
        r.fake.found = found
        r.fake.open += found
        r.hold(3_000, "oa")
        val b = r.only()
        assertEquals("o9", b.orderId)
        assertNull(b.lostAtMs)
        assertEquals("and it is the one bid on the side: nothing was sent twice", 1, r.bids().size)
        r.desk.pull("oa", "test")
        r.tick(100)
        assertEquals(listOf("o9"), r.fake.cancels.single())
    }

    @Test
    fun `real - a lost order that no list ever shows is called gone after a while`() = runTest {
        val r = rig()
        r.fake.failure = java.io.IOException("connection reset")
        r.desk.want(want(r.now))
        r.tick(600)
        r.hold(LiveBidDesk.LOST_GIVE_UP_MS + 3_000, "oa")
        assertEquals(LiveBidStatus.LOST, r.bids().first().status)
    }

    // ---- real: the feature stops itself -------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `real - a run of picked-off fills halts everything and tells the app`() = runTest {
        val r = rig(quality = LiveBidQuality(pickOffWindow = 3, pickOffLimit = 2), limits = LiveBidLimits(maxStake = 2.0, walletReserve = 0.0, maxBids = 10, maxBidsPerGame = 10, maxPerGame = 100.0, maxPerDay = 100.0))
        r.wallet = 1000.0
        val ps = r.upMany(Triple("a1", "m-a1", "e-a1"), Triple("a2", "m-a2", "e-a2"), Triple("a3", "m-a3", "e-a3"))
        for (p in ps) {
            r.fake.drop(p)
            r.fake.fillsBy[p.orderId] = listOf(fill("f-${p.orderId}", p.orderId, 400, ts = r.now, outcome = p.outcomeId))
            r.desk.noteFair(p.outcomeId, 0.40, r.now)   // Pinnacle's fair fell under the price paid
        }
        r.hold(1_500, "a1", "a2", "a3")
        // 30 s on, the fair is read again: still under the price.
        for (i in 1..70) { ps.forEach { r.desk.noteFair(it.outcomeId, 0.40, r.now) }; r.tick(500) }
        assertEquals(1, r.halts.size)
        assertTrue(r.halts[0], r.halts[0].contains("picked off"))
        assertEquals(r.halts[0], r.desk.status.value.halted)
        r.desk.want(want(r.now, "z", "m-z", "e-z"))
        r.tick(800)
        assertEquals("nothing is posted while halted", 3, r.fake.placed.size)
        r.desk.resumed()
        r.tick(500)
        assertNull("Resume lifts it", r.desk.status.value.halted)
    }

    @Test
    fun `real - a pull that measures too slow halts everything`() = runTest {
        val r = rig(quality = LiveBidQuality(maxCancelSec = 1, coolOffSec = 0), limits = LiveBidLimits(maxStake = 1.0, walletReserve = 0.0, maxBids = 10, maxBidsPerGame = 10, maxPerGame = 100.0, maxPerDay = 100.0))
        r.wallet = 1000.0
        val ps = r.upMany(*(1..6).map { Triple("c$it", "m-c$it", "e-c$it") }.toTypedArray())
        r.desk.stopAll("test")
        r.tick(3_000)   // Novig keeps them on the book for 3 s after the cancel
        ps.forEach { r.fake.drop(it) }
        r.tick(1_300)
        assertEquals(1, r.halts.size)
        assertTrue(r.halts[0], r.halts[0].contains("pulling a bid takes"))
    }

    @Test
    fun `real - bids posted far faster than the limits explain halt the feature (a loop is caught before it costs money)`() = runTest {
        val r = rig(limits = LiveBidLimits(maxStake = 1.0, walletReserve = 0.0, maxBids = 2, maxBidsPerGame = 2, maxPerGame = 1_000.0, maxPerDay = 100_000.0))
        r.wallet = 100_000.0
        // Novig ends every order at once, so a new one is always wanted: 20 bids a minute is what two bids can honestly come to; this goes faster.
        for (i in 1..40) {
            r.desk.want(want(r.now, "x$i", "m$i", "e$i"))
            r.tick(300)
            val p = r.fake.placed.lastOrNull { it.outcomeId == "x$i" } ?: break
            r.fake.records[p.orderId] = NovigOrder(p.orderId, p.clientId, "m$i", "x$i", p.price, p.qty, p.qty, "PO", "CANCELED", 0L, null)
            r.tick(1_000)
            if (r.halts.isNotEmpty()) break
        }
        assertEquals(1, r.halts.size)
        assertTrue(r.halts[0], r.halts[0].contains("far faster"))
    }

    @Test
    fun `real - a bad day halts at the loss limit`() = runTest {
        val r = rig(limits = LiveBidLimits(haltLoss = 10.0))
        r.loss = 11.0
        r.tick(LiveBidDesk.LOSS_EVERY_MS + 500)
        assertEquals(1, r.halts.size)
        assertTrue(r.halts[0], r.halts[0].contains("lost"))
    }

    // ---- recovery and replacement --------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `a real bid left from a run that ended is taken down at the start, a paper one is ended`() = runTest {
        val left = LiveBid(
            clientId = "c1", orderId = "oX", mode = LiveBid.MODE_REAL, marketId = "m", eventId = "e", outcomeId = "oa", league = "NBA", eventName = "x", startsTs = 0L, marketLabel = "Moneyline",
            selection = "x", price = 0.475, contracts = 100, fair = 0.5, ev = 0.05, postedAtMs = base, expiresAtMs = base + 30_000L, status = LiveBidStatus.RESTING,
        )
        val paper = left.copy(clientId = "c2", orderId = null, mode = LiveBid.MODE_PAPER, outcomeId = "ob")
        val fake = FakeOrders()
        val mem = Mem().also { it.saved = listOf(left, paper) }
        val desk = LiveBidDesk(backgroundScope, fake, mem, null, { LiveBidConfig.OFF }, { 100.0 }, clock = { base + currentTime })
        desk.start()
        runCurrent()
        assertEquals(listOf(listOf("oX")), fake.cancels)
        assertEquals(LiveBidStatus.CANCELING, desk.bidsNow().first { it.clientId == "c1" }.status)
        assertEquals(LiveBidStatus.CANCELED, desk.bidsNow().first { it.clientId == "c2" }.status)
    }

    @Test
    fun `a bid near the end of its ttl is replaced before it ends, once, and the pair is counted`() = runTest {
        val r = rig(quality = LiveBidQuality(ttlSec = 30, refreshBeforeSec = 8, overlapRepost = true), limits = LiveBidLimits(maxStake = 2.0, walletReserve = 0.0, maxBidsPerGame = 5, maxBids = 5))
        r.wallet = 1000.0
        val first = r.up()
        r.hold(18_000, "oa")
        r.desk.want(want(r.now))
        r.hold(1_500, "oa")
        assertEquals("the replacement is not sent before the last 8 s of the bid's life", 1, r.fake.placed.size)
        r.desk.want(want(r.now))
        r.hold(1_500, "oa")
        assertEquals("the successor went up while the first is still resting", 2, r.fake.placed.size)
        assertEquals(2, r.bids().count { it.active })
        r.desk.want(want(r.now))
        r.tick(800)
        assertEquals("and only one", 2, r.fake.placed.size)
        assertTrue(first.orderId != r.fake.placed[1].orderId)
    }

    @Test
    fun `a refresh time longer than the bid's life cannot make every bid renew at once (it is held to half the life)`() = runTest {
        val r = rig(quality = LiveBidQuality(ttlSec = 12, refreshBeforeSec = 30, overlapRepost = true), limits = LiveBidLimits(maxStake = 2.0, walletReserve = 0.0, maxBidsPerGame = 5, maxBids = 5))
        r.wallet = 1000.0
        r.up()
        r.hold(3_000, "oa")
        r.desk.want(want(r.now))
        r.hold(1_500, "oa")
        assertEquals("not renewed in the first half of its life", 1, r.fake.placed.size)
        r.hold(2_500, "oa")
        r.desk.want(want(r.now))
        r.hold(1_500, "oa")
        assertEquals("renewed once, in the second half", 2, r.fake.placed.size)
    }

    @Test
    fun `without overlap the replacement waits for the old bid to end`() = runTest {
        val r = rig(quality = LiveBidQuality(ttlSec = 30, refreshBeforeSec = 8, overlapRepost = false, coolOffSec = 0), limits = LiveBidLimits(maxStake = 2.0, walletReserve = 0.0))
        r.wallet = 1000.0
        val first = r.up()
        r.hold(24_000, "oa")
        r.desk.want(want(r.now))
        r.tick(800)
        assertEquals(1, r.fake.placed.size)
        r.hold(6_000, "oa")
        r.fake.drop(first)
        r.desk.want(want(r.now))
        r.hold(2_500, "oa")
        assertEquals("the old bid ended: the next one goes up", 2, r.fake.placed.size)
    }

    @Test
    fun `no want is not a pull, a pull is`() = runTest {
        val r = rig()
        val p = r.up()
        r.desk.noWant("oa", LiveBidSkip.SETTLING)
        r.hold(2_000, "oa")
        assertTrue("a bid that is still vouched for is not touched by a want that went away", r.fake.cancels.isEmpty())
        assertEquals(1, r.desk.status.value.skips[LiveBidSkip.SETTLING])
        r.desk.pullEvent("e1", LiveBidSkip.NOT_LIVE)
        r.tick(100)
        assertEquals(listOf(listOf(p.orderId)), r.fake.cancels)
        assertFalse(r.desk.busy("nothing"))
    }
}
