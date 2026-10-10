package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.livebid.LiveBid
import com.tjshea.vigilant.data.livebid.LiveBidConfig
import com.tjshea.vigilant.data.livebid.LiveBidDesk
import com.tjshea.vigilant.data.livebid.LiveBidLimits
import com.tjshea.vigilant.data.livebid.LiveBidPersistence
import com.tjshea.vigilant.data.livebid.LiveBidQuality
import com.tjshea.vigilant.data.novig.*
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RealFramesReplayScratch {
    @get:Rule val tmp = TemporaryFolder()

    private class Source(val events: List<NovigEvent>, val markets: List<NovigMarket>) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events.filter { it.status in statuses }
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch = throw UnsupportedOperationException()
        override suspend fun market(marketId: String): NovigMarket? = null
    }
    private class Feed(val markets: List<NovigMarket>, val base: Long) : PushedBooks {
        override fun watch(marketIds: Collection<String>) {}
        override fun live(marketIds: Collection<String>) = marketIds.mapNotNull { id ->
            val m = markets.firstOrNull { it.marketId == id } ?: return@mapNotNull null
            id to NovigBook(id, 1, m.outcomes.associate { it.outcomeId to listOf(BidLevel(450, 50_000)) }, base)
        }.toMap()
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
        override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String): String = "o"
        override suspend fun order(orderId: String): NovigOrder? = null
        override suspend fun fills(orderId: String): List<NovigFill> = emptyList()
    }
    private class Mem : LiveBidPersistence {
        var saved: List<LiveBid> = emptyList()
        override suspend fun all() = saved
        override suspend fun replace(list: List<LiveBid>) { saved = list }
    }

    @Test
    fun replay() = runTest {
        val lines = File("/tmp/claude-0/-home-user-novig/e24cce0e-cdce-51ce-b7d7-f01dce035d93/scratchpad/t/raw1.ndjson").readLines().mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val frames = lines.filter { it["m"] != null && (it["m"] as? JsonObject)?.get("type")?.jsonPrimitive?.content in setOf("live", "snapshot") }
        val firstT = frames.first()["t"]!!.jsonPrimitive.long
        // matchups seen live
        val seen = LinkedHashMap<Long, JsonObject>()
        fun note(rec: JsonObject) {
            if (rec["type"]?.jsonPrimitive?.content != "matchup" || rec["isLive"]?.jsonPrimitive?.booleanOrNull != true) return
            val parts = rec["participants"] as? JsonArray ?: return
            if (parts.size != 2) return
            seen[rec["id"]!!.jsonPrimitive.long] = rec
        }
        for (f in frames) {
            val m = f["m"] as JsonObject
            if (m["type"]?.jsonPrimitive?.content == "snapshot") (m["events"] as? JsonArray)?.forEach { note(it.jsonObject) } else (m["rec"] as? JsonObject)?.let(::note)
        }
        val events = ArrayList<NovigEvent>(); val markets = ArrayList<NovigMarket>()
        for ((id, rec) in seen) {
            val parts = rec["participants"] as JsonArray
            val home = parts.map { it.jsonObject }.firstOrNull { it["alignment"]?.jsonPrimitive?.content == "home" }?.get("name")?.jsonPrimitive?.content ?: continue
            val away = parts.map { it.jsonObject }.firstOrNull { it["alignment"]?.jsonPrimitive?.content == "away" }?.get("name")?.jsonPrimitive?.content ?: continue
            val start = java.time.Instant.parse(rec["startTime"]!!.jsonPrimitive.content).toEpochMilli()
            events += NovigEvent("n$id", "X", "NBA", NovigEvent.STATUS_LIVE, "$away @ $home", start)
            markets += NovigMarket("m$id", "n$id", "MONEY", "OPEN", home, start, MarketFee.GAME, listOf(NovigOutcome("m$id-h", home, "TBD"), NovigOutcome("m$id-a", away, "TBD")), 0.0)
        }
        println("REPLAY novig events built: ${events.size} from ${seen.size} live matchups, frames ${frames.size}")
        val base = firstT
        val feed = Feed(markets, base)
        val pinn = Pinn()
        val journal = DayJournal(tmp.newFolder("j"), "pinn-live", LiveRecord.serializer()) { it.atMs }
        val follows = DayJournal(tmp.newFolder("f"), "pinn-follow", LiveFollow.serializer()) { it.atMs }
        val trader = PinnLiveTrader(
            orders = NoOrders(), scope = backgroundScope, rules = { LiveTradeRules(enabled = false, bet = false, stake = 2.0, maxPerGame = 6.0, maxPerDay = 25.0, haltLoss = 10.0) },
            gate = { null }, ownBids = { emptyList() }, journal = journal, onHalt = {}, logFills = { _, _, _ -> true }, clock = { base + currentTime }, dayStart = { base - 3_600_000L }, pause = { kotlinx.coroutines.delay(it) },
        )
        val cfg = { LiveBidConfig(on = true, real = false, quality = LiveBidQuality(), limits = LiveBidLimits(), bankroll = 1000.0, apiMaxStake = 0.0, preset = "Balanced") }
        val desk = LiveBidDesk(scope = backgroundScope, orders = null, store = Mem(), journal = null, config = cfg, wallet = { 100.0 }, clock = { base + currentTime })
        val runner = PinnLiveRunner(
            scope = backgroundScope, source = Source(events, markets), newFeed = { _: BookListener -> feed }, openFeed = { cb -> pinn.also { it.onFrame = cb } },
            trader = trader, config = { LiveConfig(LiveRules(trigger = LiveTrigger.MOVE), DevigMethod.WORST_CASE, setOf("NBA"), takerOn = false) }, followJournal = follows,
            bids = desk, bidConfig = cfg, clock = { base + currentTime }, discoverEveryMs = 30_000L, tickMs = 100L,
        )
        desk.start()
        runner.start()
        runCurrent()
        var n = 0
        for (f in frames) {
            val t = f["t"]!!.jsonPrimitive.long
            val delta = (t - base) - currentTime
            if (delta > 0) { advanceTimeBy(delta); runCurrent() }
            pinn.lastFrameAtMs = base + currentTime
            pinn.onFrame!!((f["m"] as JsonObject).toString(), base + currentTime)
            runCurrent()
            n++
            if (n % 500 == 0) { val st = runner.status.value; println("REPLAY after $n frames: running=${runner.running} matched=${st.matched} targets=${st.targets} bidTargets=${st.bidTargets} problem=${st.problem} bids=${desk.bidsNow().size} skips=${desk.status.value.skips}") }
        }
        advanceTimeBy(2_000); runCurrent()
        val st = runner.status.value
        println("REPLAY END: running=${runner.running} pinnLive=${st.pinnLive} matched=${st.matched} targets=${st.targets} watched=${st.watched} bidTargets=${st.bidTargets} problem=${st.problem}")
        println("REPLAY desk: bids=${desk.bidsNow().size} active=${desk.status.value.active} posted=${desk.status.value.posted} skips=${desk.status.value.skips} problem=${desk.status.value.problem}")
        desk.bidsNow().take(5).forEach { println("REPLAY bid ${it.selection} ${it.price} fair ${it.fair} ${it.status}") }
        runner.stop()
    }
}
