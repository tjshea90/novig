package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.novig.trading.maker.BidCandidates.BidLine
import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerLine
import com.tjshea.vigilant.data.novig.trading.maker.MakerStore
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The GitHub engine's bid desk (Tj, 2026-10-16, TCL31): the phone's desk on the runner. The decisions are the unchanged [MakerDesk]'s (MakerTest); what these prove is the engine's wiring around it: bids go up
 * only while the engine trades LIVE with bids on, carry the engine's client-id prefix, come down at once when switched off or killed, skip a market the engine's auto-bet holds, and become ledger entries.
 */
class CloudBidRunnerTest {
    private var now = 1_800_000_000_000L
    private val start get() = now + 5 * 3_600_000L

    private class FakeTrading : NovigTradingClient(
        NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }),
        Json { ignoreUnknownKeys = true },
    ) {
        val orders = LinkedHashMap<String, NovigOrder>()
        val fillsBy = HashMap<String, MutableList<NovigFill>>()
        val placed = ArrayList<Pair<String, String>>()
        val ttls = ArrayList<Long?>()
        var n = 0

        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            placed += outcomeId to clientId
            ttls += ttlMs
            val id = "o${++n}"
            orders[id] = NovigOrder(id, clientId, outcomeId.substringBefore('-'), outcomeId, price, qty, qty, tif, "OPEN", 0L, null)
            return id
        }
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = orders.values.filter { it.status == status && (outcomeId == null || it.outcomeId == outcomeId) }
        override suspend fun order(orderId: String): NovigOrder? = orders[orderId]
        override suspend fun fills(orderId: String?, limit: Int): List<NovigFill> = fillsBy[orderId].orEmpty()
        override suspend fun fillsStartingAfter(startsAfterMs: Long, limit: Int): List<NovigFill> = fillsBy.values.flatten()
        override suspend fun cancelOrder(orderId: String): String? {
            val o = orders[orderId] ?: return null
            if (o.status == "OPEN") orders[orderId] = o.copy(status = "CANCELED")
            return "OPEN"
        }
        override suspend fun cancelOrders(marketId: String?, eventId: String?): Int {
            val open = orders.values.filter { it.status == "OPEN" }
            open.forEach { orders[it.orderId] = it.copy(status = "CANCELED") }
            return open.size
        }
        fun fill(orderId: String, qty: Long, at: Long) {
            val o = orders.getValue(orderId)
            val left = o.remaining - qty
            orders[orderId] = o.copy(remaining = left, status = if (left <= 0) "FILLED" else "OPEN")
            fillsBy.getOrPut(orderId) { ArrayList() } += NovigFill("f-$orderId-${fillsBy[orderId]?.size ?: 0}", orderId, o.clientId, o.marketId, o.outcomeId, qty, qty * o.price * 0.01, false, 0.0, at)
        }
    }

    private object NoBooks : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?): List<NovigEvent> = emptyList()
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?): List<NovigMarket> = emptyList()
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(emptyMap(), 0, 0, 0)
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private object Cash : CloudOrderPort {
        override suspend fun cash(): Double? = 100.0
        override val blocked: String? = null
        override suspend fun place(entry: TradeEntry, limitPrice: Double): CloudOrderPort.Placed = error("the bid desk never uses the IOC port")
        override suspend fun cancelResting() {}
    }

    private fun market(id: String) = NovigMarket(
        marketId = id, eventId = "ev-$id", marketType = "RECEIVING_YARDS", status = "OPEN", description = "Player Receiving Yards", startsTs = start,
        fee = MarketFee.GAME, outcomes = listOf(NovigOutcome("$id-over", "Over 50.5", "TBD"), NovigOutcome("$id-under", "Under 50.5", "TBD")),
    )

    private fun candidate(id: String): BidLine {
        val m = market(id)
        val l = MakerLine(
            market = m, outcomeId = "$id-over", startsTs = start, league = "NFL", eventName = "A @ B", marketLabel = "Player Receiving Yards", selection = "Player Over 50.5", kind = BetKind.PROP,
            fair = 0.52, fairAsOfMs = now - 30_000, fairOld = false, books = 5, offer = 0.55, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
            sharpFairs = listOf(0.52), bookAtMs = now,
        )
        return BidLine(l, start)
    }

    private val deployOn = ScanSettings(gates = ApiGates(cloudScan = true, cloudTrading = CloudTradeMode.LIVE))

    /** One bid per side (the wallet-deployment second rung off) so the counts below are about the wiring. */
    private val live = deployOn.copy(vigGuards = deployOn.vigGuards.copy(bidDeploy = false))

    private class Rig(val trading: FakeTrading, val runner: CloudBidRunner, val store: MakerStore, val filled: MutableList<Pair<List<TrackedBet>, Int>>, val held: MutableSet<String>)

    private fun rig(): Rig {
        val trading = FakeTrading()
        val store = MakerStore(File.createTempFile("cloud-maker", ".json").also { it.delete() })
        val tracker = BetTracker(File.createTempFile("cloud-bets", ".json").also { it.delete() }, clock = { now })
        val desk = MakerDesk(trading, tracker, store, clock = { now }, dayStart = { now - 3_600_000L }, pause = {}, cancelStrays = false, newClientId = MakerDesk::engineClientId)
        val filled = ArrayList<Pair<List<TrackedBet>, Int>>()
        val held = HashSet<String>()
        val runner = CloudBidRunner(desk, CloudBidLane(NoBooks, clock = { now }), Cash, tradeMarkets = { held }, onFills = { bets, all -> filled += bets to all.size }, clock = { now })
        return Rig(trading, runner, store, filled, held)
    }

    private fun put(r: Rig, vararg ids: String) = r.runner.put(CloudBids(now, false, 1, ids.map(::candidate)))

    @Test
    fun `with engine trading live and bids on the engine posts the bid with its own client-id prefix`() = runBlocking {
        val r = rig()
        assertTrue(live.engineBids)
        put(r, "m1")
        val st = r.runner.pass(live, now)
        assertNotNull(st)
        assertTrue(st!!.active)
        assertEquals(1, r.trading.placed.size)
        assertTrue("every engine bid is recognisable: ${r.trading.placed[0].second}", r.trading.placed[0].second.startsWith(MakerDesk.ENGINE_PREFIX))
        assertTrue("and still a plain UUID", NovigTradingClient.isUuid(r.trading.placed[0].second))
        assertEquals(1, st.resting)
        assertTrue(st.note, st.note.contains("1 bids resting"))
    }

    @Test
    fun `an engine bid never rests on Novig longer than the engine's own cap, so a dead engine leaves nothing stale`() = runBlocking {
        val r = rig()
        put(r, "m1")
        r.runner.pass(live, now)
        assertEquals(1, r.trading.ttls.size)
        val ttl = r.trading.ttls[0]!!
        assertTrue("ttl $ttl must be within ${CloudBidRunner.ENGINE_BID_TTL_MS}", ttl <= CloudBidRunner.ENGINE_BID_TTL_MS)
    }

    @Test
    fun `with the wallet under its target the engine posts the phone's deeper second bid on the side too, at a lower price`() = runBlocking {
        val r = rig()
        put(r, "m1")
        r.runner.pass(deployOn, now)
        val prices = r.store.all().map { it.price to it.rung }.sortedBy { it.second }
        assertEquals(prices.toString(), listOf(0, 1), prices.map { it.second })
        assertTrue(prices.toString(), prices[1].first < prices[0].first)
    }

    @Test
    fun `bids off, trading paper, or the cloud scan off means no bid and whatever is up comes down`() = runBlocking {
        val r = rig()
        put(r, "m1")
        r.runner.pass(live, now)
        assertEquals(1, r.store.all().count { it.active })
        for (off in listOf(live.copy(gates = live.gates.copy(cloudBids = false)), live.copy(gates = live.gates.copy(cloudTrading = CloudTradeMode.PAPER)))) {
            assertFalse(off.engineBids)
            assertNull(r.runner.pass(off, now))
        }
        assertEquals("the bid is cancelled once the engine stops bidding", 0, r.store.all().count { it.active && it.status.name == "RESTING" })
        assertEquals("CANCELED", r.trading.orders.values.single().status)
    }

    @Test
    fun `the kill switch and Pause stop the desk and take every bid down`() = runBlocking {
        val r = rig()
        put(r, "m1")
        r.runner.pass(live, now)
        now += 30_000
        put(r, "m1")
        val st = r.runner.pass(live.copy(killed = true), now)
        assertEquals("killed means engineBids is false: the desk stops by itself", null, st)
        assertEquals("CANCELED", r.trading.orders.values.single().status)
    }

    @Test
    fun `a market the engine's auto-bet holds gets no bid and a bid already there comes down`() = runBlocking {
        val r = rig()
        put(r, "m1", "m2")
        r.held += "m2"
        r.runner.pass(live, now)
        assertEquals(listOf("m1-over"), r.trading.placed.map { it.first })
        now += 30_000
        r.held += "m1"
        put(r, "m1", "m2")
        r.runner.pass(live, now)
        assertEquals("CANCELED", r.trading.orders.values.single().status)
    }

    @Test
    fun `a fill is handed on and becomes a ledger entry keyed by the bid`() = runBlocking {
        val r = rig()
        put(r, "m1")
        r.runner.pass(live, now)
        val order = r.trading.orders.values.single()
        r.trading.fill(order.orderId, 4, now)
        now += 30_000
        put(r, "m1")
        r.runner.pass(live, now)
        assertEquals(1, r.filled.size)
        val bid = r.store.all().single()
        val entry = CloudBidRunner.toEntry(bid, r.filled[0].first.single(), now)
        assertEquals(bid.clientId, entry.id)
        assertEquals("LIVE", entry.mode)
        assertTrue(entry.note.startsWith("bid"))
        assertEquals(4L, entry.contracts)
        assertEquals("m1", entry.marketId)
        assertEquals(order.price, entry.avgPrice, 1e-9)
        assertTrue(entry.cost > 0.0)
    }

    @Test
    fun `stop takes the bids down and the heartbeat then says the desk is not live`() = runBlocking {
        val r = rig()
        put(r, "m1")
        r.runner.pass(live, now)
        r.runner.stop("the engine is ending")
        assertEquals("CANCELED", r.trading.orders.values.single().status)
        assertFalse(r.runner.status()!!.active)
        val st = CloudStatus(heartbeatMs = now, deskActive = true)
        assertTrue(st.deskLive(now))
        assertFalse("a silent engine hands the bids back to the phone", st.deskLive(now + CloudStatus.DESK_STALE_MS + 1))
        assertFalse(CloudStatus(heartbeatMs = now, deskActive = false).deskLive(now))
    }

    @Test
    fun `engine switches alone decide where bids run`() {
        assertFalse(ScanSettings().engineBids)
        assertFalse(live.copy(gates = live.gates.copy(cloudScan = false)).engineBids)
        assertFalse(live.copy(pausedByHand = true).engineBids)
        assertTrue("the phone's own maker switch is not needed: the engine does it", !live.maker && live.engineBids)
        assertTrue("the engine runs the bid profile", CloudEngine.forCloud(live).maker)
        assertFalse("a phone with the engine not bidding is unchanged", CloudEngine.forCloud(ScanSettings()).maker)
    }

    @Test
    fun `desk file keeps active and recently filled bids and reads back`() = runBlocking {
        val r = rig()
        put(r, "m1")
        r.runner.pass(live, now)
        val f = DeskFile(now, "note", "", DeskFile.trim(r.store.all(), now))
        val back = DeskFile.parse(DeskFile.text(f))!!
        assertEquals(1, back.bids.size)
        assertEquals(f.bids, back.bids)
    }
}
