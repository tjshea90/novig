package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.live.Fetched
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.lab.LiveConfirm
import com.tjshea.vigilant.data.novig.lab.LiveLightBidRules
import com.tjshea.vigilant.data.novig.lab.LiveLightRunner
import com.tjshea.vigilant.data.novig.lab.TailTaker
import com.tjshea.vigilant.data.novig.stream.NovigStream
import com.tjshea.vigilant.data.novig.stream.SharedStreamView
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.livebid.LiveBidConfig
import com.tjshea.vigilant.data.livebid.LiveBidDesk
import com.tjshea.vigilant.data.livebid.LiveBidEvent
import com.tjshea.vigilant.data.livebid.LiveBidLimits
import com.tjshea.vigilant.data.livebid.LiveBidOrders
import com.tjshea.vigilant.data.livebid.LiveBidQuality
import com.tjshea.vigilant.data.livebid.LiveBidStore
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.pinnodds.LiveOrders
import com.tjshea.vigilant.data.pinnodds.LiveOwnBid
import com.tjshea.vigilant.data.pinnodds.LiveRecord
import com.tjshea.vigilant.data.pinnodds.LiveTradeRules
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.RepeatGuard
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the engine's live lane says about itself in the heartbeat. */
data class LiveLaneStatus(val active: Boolean, val note: String, val stopped: String = "", val atMs: Long = 0L)

/** The engine's view of its live lane: a pass under the phone's latest settings, an orderly stop. */
interface EngineLive {
    suspend fun pass(s: ScanSettings, now: Long): LiveLaneStatus?
    suspend fun stop(why: String)
}

/** Several lanes as one (the engine has one live slot): active if any is, the notes and stops joined. */
class CompositeLive(private val lanes: List<EngineLive>) : EngineLive {
    override suspend fun pass(s: ScanSettings, now: Long): LiveLaneStatus? {
        val all = lanes.mapNotNull { runCatching { it.pass(s, now) }.onFailure { e -> if (e is CancellationException) throw e }.getOrNull() }
        if (all.isEmpty()) return null
        return LiveLaneStatus(all.any { it.active }, all.joinToString(" | ") { it.note }, all.map { it.stopped }.filter { it.isNotBlank() }.joinToString("; "), now)
    }

    override suspend fun stop(why: String) { for (l in lanes) runCatching { l.stop(why) }.onFailure { if (it is CancellationException) throw it } }
}

/** The trading client as the live taker's and the live bid desk's order door (the phone's `pinnOrders` / `liveBidOrders`, for the runner). */
class TradingLiveOrders(private val tc: NovigTradingClient) : LiveOrders, LiveBidOrders {
    override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String) = tc.placeOrder(outcomeId, price, qty, "IOC", clientId)
    override suspend fun place(outcomeId: String, price: Double, qty: Long, ttlMs: Long, clientId: String) = tc.placeOrder(outcomeId, price, qty, "PO", clientId, ttlMs)
    override suspend fun order(orderId: String): NovigOrder? = tc.order(orderId)
    override suspend fun fills(orderId: String): List<NovigFill> = tc.fills(orderId)
    override suspend fun open(): List<NovigOrder> = tc.orders("OPEN")

    override suspend fun cancel(orderIds: List<String>): Map<String, String?> {
        if (orderIds.size > 1 && tc.batchOrders) {
            try {
                val r = tc.cancelOrdersBatch(orderIds)
                return orderIds.associateWith { id -> if (id in r.canceled) "OPEN" else r.notCanceled[id]?.takeIf { it == "FILLED" } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One by one below.
            }
        }
        val out = HashMap<String, String?>()
        var failure: Exception? = null
        for (id in orderIds) {
            try {
                out[id] = tc.cancelOrder(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (failure == null) failure = e
            }
        }
        failure?.let { throw it }
        return out
    }

    override suspend fun find(clientId: String, outcomeId: String): NovigOrder? {
        for (status in listOf("OPEN", "PENDING", "FILLED", "CANCELED", "REJECTED")) {
            val hit = try {
                tc.orders(status, outcomeId = outcomeId).firstOrNull { it.clientId == clientId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (hit != null) return hit
        }
        return null
    }
}

/**
 * The GitHub engine's live lane (Tj, 2026-10-17, TCL109: "I no longer have pinnodds. move all of the live betting features to GitHub so I can run them just like regular from my phone but the processing is on GitHub").
 *
 * It is the phone's Live light lane on the runner, nothing rewritten: [LiveLightRunner] (Novig's pushed live books through the engine's stream, ESPN's score, BoltOdds' soft-book average as the second opinion),
 * the [TailTaker] it hands offers to and Live light's own [LiveBidDesk], all under the phone's mirrored `tailLive` switches and limits and the engine's trading key. Differences from the phone:
 *  - it runs only while the phone says the engine drives ([ScanSettings.engineLive]); the phone's own copy of the lane stands down then (one decision, one order);
 *  - REAL orders need the engine's trading switch LIVE and `tailLive.bet`; otherwise it decides and journals and sends nothing (PAPER);
 *  - a halt (the day's loss, a refused key) stops the lane's orders until Tj resumes it from the phone (the phone's `tailLive.halted` clears the halt, as it does on the phone) and tells him;
 *  - every fill is a [TradeEntry] in the engine's ledger (note starts `live`), so the Kelly caps, the grader and the phone's screens see one book. Its client ids are the TailTaker's and the desk's own.
 */
class CloudLiveLane(
    private val scope: CoroutineScope,
    private val source: NovigSource,
    private val stream: () -> NovigStream?,
    private val fetch: suspend (String) -> Fetched?,
    private val tc: NovigTradingClient?,
    private val port: CloudOrderPort?,
    private val dir: File,
    /** The one tracker of the engine's live fills, shared with [CloudPinnLane] (two copies of one file would go stale). */
    private val tracker: BetTracker = BetTracker(File(dir.also { it.mkdirs() }, "live-tracker.json")),
    private val confirm: (ScanSettings) -> LiveConfirm? = { null },
    /** A fill as a ledger entry, the markets the engine's auto-bet holds, and a line for Tj. */
    private val onEntry: suspend (TradeEntry) -> Unit = {},
    private val tell: suspend (String, String) -> Unit = { _, _ -> },
    private val journalLine: (String) -> Unit = {},
    private val log: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : EngineLive {
    @Volatile private var settings: ScanSettings = ScanSettings()
    @Volatile private var haltedWhy: String? = null
    @Volatile private var up = false
    private val orders = tc?.let { TradingLiveOrders(it) }
    private val tailJournal = DayJournal(File(dir, "tail"), "tail-live", LiveRecord.serializer()) { it.atMs }

    private fun rules(): LiveTradeRules {
        val s = settings
        val t = s.tailLive
        return LiveTradeRules(
            enabled = s.engineLive && t.on, bet = realOrders(s),
            stake = if (s.apiMaxStake > 0.0) minOf(t.stake, s.apiMaxStake) else t.stake,
            maxPerGame = t.maxGame, maxPerDay = t.maxDay, haltLoss = t.haltLoss, halted = haltedWhy ?: t.halted,
        )
    }

    private suspend fun gate(): String? {
        val s = settings
        val stake = rules().stake
        val cash = port?.cash()
        return when {
            s.killed -> "STOP ALL is on"
            s.paused -> "scanning is paused"
            port == null -> "no trading key on GitHub"
            port.blocked != null -> "Novig refused the engine's key: ${port.blocked}"
            cash == null -> "wallet not read yet"
            cash < stake * 1.2 -> "wallet too low"
            else -> null
        }
    }

    private suspend fun lossToday(): Double {
        val from = ApiBetPlacer.localMidnight(clock())
        val net = tracker.all().filter { it.source == BetTracker.SOURCE_TAIL && (it.settledAtMs ?: 0L) >= from }.sumOf { it.profit ?: 0.0 }
        return (-net).coerceAtLeast(0.0)
    }

    private fun halt(why: String) {
        if (haltedWhy != null) return
        haltedWhy = why
        log("live lane halted: $why")
        scope.launch { withContext(NonCancellable) { runCatching { tell("Live tail betting stopped", why.take(300)) } } }
    }

    private suspend fun record(bet: TrackedBet?, note: String) {
        if (bet == null) return
        runCatching { onEntry(entry(bet, note)) }
    }

    private fun ownBids() = bidDesk.bidsNow().filter { it.active }.map { LiveOwnBid(it.marketId, it.outcomeId, it.price) }

    private val taker: TailTaker? = orders?.let { o ->
        TailTaker(
            orders = o, scope = scope, rules = ::rules, minEdge = { settings.tailLive.minEdge }, gate = ::gate, ownBids = ::ownBids, journal = tailJournal, onHalt = ::halt,
            logFills = { target, orderId, fills -> tracker.logApi(target, orderId, fills).also { record(it, "live tail bet") } != null },
            lossToday = ::lossToday,
        )
    }

    private fun bidConfig(): LiveBidConfig {
        val s = settings
        val t = s.tailLive
        val limits = LiveBidLimits(
            stakeMode = AutoBetStake.CUSTOM, customStake = t.stake, minStake = 1.0, maxStake = t.stake, maxBids = 6, maxBidsPerGame = 3,
            maxPerGame = t.maxGame, maxPerDay = t.maxDay, haltLoss = t.haltLoss, walletReserve = 5.0,
        )
        val quality = LiveBidQuality(margin = LIGHT_BID_MARGIN, minPrice = 0.30, maxPrice = 0.95, ttlSec = 20)
        return LiveBidConfig(
            on = bidsOn(s), real = realOrders(s), quality = quality, limits = limits, bankroll = s.bankroll, apiMaxStake = s.apiMaxStake, preset = "Live light (engine)",
            halted = haltedWhy ?: t.halted, blockedWhy = if (realOrders(s) && (port == null || port.blocked != null)) "no usable trading key on GitHub" else null,
        )
    }

    private val bidDesk: LiveBidDesk = LiveBidDesk(
        scope = scope, orders = orders, store = LiveBidStore(File(dir, "light-bids.json")),
        journal = DayJournal(File(dir, "bids"), "lightbid", LiveBidEvent.serializer()) { it.atMs },
        config = ::bidConfig, wallet = { lastCash },
        spentToday = { 0.0 }, dayLimit = { settings.apiMaxPerDay.takeIf { it > 0.0 } ?: Double.MAX_VALUE }, lossToday = ::lossToday,
        logFills = { target, orderId, fills -> tracker.logMakerFills(target, orderId, fills).also { record(it, "live light bid") }?.id },
        onHalt = ::halt, version = "github-engine",
        repeat = { settings.gates.repeatRules() },
        heldBets = { RepeatGuard.fromBets(tracker.all()) },
    )

    @Volatile private var lastCash: Double? = null

    private val light: LiveLightRunner = LiveLightRunner(
        scope = scope, source = source, journal = journalLine,
        newView = { l -> SharedStreamView({ stream() }, l) },
        fetch = fetch, onOffer = { taker?.offer(it) }, leagues = { LEAGUES },
        bids = { if (bidsOn(settings)) bidDesk else null },
        bidRules = { LiveLightBidRules(margin = LIGHT_BID_MARGIN) },
        confirm = { confirm(settings) },
    )

    private var haltSeen = false
    private var line = ""

    override suspend fun pass(s: ScanSettings, now: Long): LiveLaneStatus? {
        settings = s
        if (!s.engineLive || !s.tailLive.on) {
            if (up) stop("the engine's live tail lane is switched off on the phone")
            return null
        }
        up = true
        lastCash = port?.cash() ?: lastCash
        // A halt the phone clears (Resume) clears here too.
        if (s.tailLive.halted == null && haltedWhy != null && haltSeen) { haltedWhy = null; taker?.resumed(); bidDesk.resumed(); haltSeen = false }
        if (haltedWhy != null) haltSeen = true
        if (stream() == null) return LiveLaneStatus(true, "waiting for Novig's live feed (the engine's read key or websocket is not up)", haltedWhy.orEmpty(), now)
        if (!light.running) light.start()
        if (bidsOn(s)) runCatching { bidDesk.start() } else if (bidDesk.bidsNow().any { it.active }) bidDesk.stopAll("Live light bids switched off")
        val st = light.status.value
        val real = realOrders(s)
        line = "${if (real) "REAL" else "paper"} · ${st.games} live games, ${st.withScore} with a score, ${st.pushes} pushes" +
            (if (bidsOn(s)) " · bids ${bidDesk.bidsNow().count { it.active }} up" else "") + (st.problem?.let { " · ${it.take(80)}" } ?: "")
        return LiveLaneStatus(true, line, haltedWhy.orEmpty(), now)
    }

    override suspend fun stop(why: String) {
        up = false
        if (light.running) light.stop()
        runCatching { bidDesk.stopAll(why) }.onFailure { if (it is CancellationException) throw it }
        runCatching { bidDesk.stop() }
        log("live lane: down ($why)")
    }

    companion object {
        const val LIGHT_BID_MARGIN = 0.07
        val LEAGUES = setOf("NFL", "NCAAF", "NBA", "WNBA", "NCAAB", "NHL", "MLB")

        fun bidsOn(s: ScanSettings) = s.engineLive && s.tailLive.on && s.tailLive.light && s.tailLive.bids
        fun realOrders(s: ScanSettings) = s.tailLive.bet && s.gates.cloudTrading == CloudTradeMode.LIVE

        /** A live fill as the engine's ledger entry (id = the tracker's bet id, so a grown fill updates the entry; the grader settles it like any bet). */
        fun entry(bet: TrackedBet, note: String): TradeEntry {
            val contracts = bet.contracts ?: 0L
            return TradeEntry(
                id = "live-" + bet.id, atMs = bet.createdAtMs, mode = "LIVE", marketId = bet.marketId, outcomeId = bet.outcomeId, eventId = "", event = bet.eventName, league = bet.league,
                kind = "live", marketLabel = bet.marketLabel, selection = bet.selection, startsTs = bet.startsTs, contracts = contracts, avgPrice = bet.price, cost = bet.stake, fee = bet.fee ?: 0.0,
                fair = bet.fairAtBet ?: 0.0, evPercent = bet.evPercentAtBet ?: 0.0, stars = null, fairAgeS = null, books = 0, note = note, orderId = bet.orderId,
            )
        }
    }
}
