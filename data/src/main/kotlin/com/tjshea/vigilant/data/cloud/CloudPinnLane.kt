package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.boltodds.BoltLiveConsensus
import com.tjshea.vigilant.data.livebid.LiveBidConfig
import com.tjshea.vigilant.data.livebid.LiveBidDesk
import com.tjshea.vigilant.data.livebid.LiveBidEvent
import com.tjshea.vigilant.data.livebid.LiveBidStore
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.NovigStream
import com.tjshea.vigilant.data.novig.stream.SharedStreamView
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.pinnodds.LiveConfig
import com.tjshea.vigilant.data.pinnodds.LiveFollow
import com.tjshea.vigilant.data.pinnodds.LiveOwnBid
import com.tjshea.vigilant.data.pinnodds.LiveRecord
import com.tjshea.vigilant.data.pinnodds.LiveRules
import com.tjshea.vigilant.data.pinnodds.LiveTradeRules
import com.tjshea.vigilant.data.pinnodds.LiveTrigger
import com.tjshea.vigilant.data.pinnodds.PinnLiveRunner
import com.tjshea.vigilant.data.pinnodds.PinnLiveTrader
import com.tjshea.vigilant.data.boltodds.BoltLiveFeed
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.RepeatGuard
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.EvMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The GitHub engine's live taker and live bids (Tj, 2026-10-17, TCL109: "I no longer have pinnodds. move all of the live betting features to GitHub"). It is the phone's [PinnLiveRunner] on the runner with the
 * BoltOdds soft-book average as its price feed (the phone's `PinnFeedChoice.BOLT`: [BoltLiveFeed] speaks the Pinnodds frames), so the matcher, the 5 s rule, the taker fee, the lag trader ([PinnLiveTrader]) and the
 * live bid desk ([LiveBidDesk]) run unchanged, over the engine's Novig stream and trading key. Differences from the phone:
 *  - the feed is always BoltOdds (the engine has no Pinnodds, by Tj's word the account is gone); the edge floor is the phone's own [BOLT_LIVE_MIN_EV] and the settle time its [BOLT_LIVE_SETTLE_MS];
 *  - REAL taker orders need the engine's trading LIVE, the phone's "Place real bets" and `gates.boltLiveReal` (a soft-book consensus trails the truth: paper until Tj allows real); real bids need LIVE and `liveBidReal`;
 *  - the Feed's live-games list ([LiveConfig.scanOn]) stays on the phone (it is a screen, not an order path);
 *  - every fill is a [TradeEntry] in the engine's ledger (note starts `live`).
 */
class CloudPinnLane(
    private val scope: CoroutineScope,
    private val source: NovigSource,
    private val stream: () -> NovigStream?,
    private val feed: (settings: () -> ScanSettings, sink: (String, Long) -> Unit, rules: () -> BoltLiveConsensus.Rules) -> BoltLiveFeed,
    private val orders: TradingLiveOrders?,
    private val port: CloudOrderPort?,
    private val dir: File,
    private val tracker: BetTracker,
    private val onEntry: suspend (TradeEntry) -> Unit = {},
    private val tell: suspend (String, String) -> Unit = { _, _ -> },
    private val log: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : EngineLive {
    @Volatile private var settings: ScanSettings = ScanSettings()
    @Volatile private var takerHalt: String? = null
    @Volatile private var bidHalt: String? = null
    @Volatile private var lastCash: Double? = null
    @Volatile private var up = false
    private var takerSeen = false
    private var bidSeen = false

    private suspend fun record(bet: TrackedBet?, note: String) {
        if (bet != null) runCatching { onEntry(CloudLiveLane.entry(bet, note)) }
    }

    private fun halted(what: String, why: String, set: (String) -> Unit, current: () -> String?) {
        if (current() != null) return
        set(why)
        log("$what halted: $why")
        scope.launch { withContext(NonCancellable) { runCatching { tell("$what stopped", why.take(300)) } } }
    }

    private fun takerRules(): LiveTradeRules {
        val s = settings
        return LiveTradeRules(
            enabled = takerOn(s), bet = takerReal(s), stake = if (s.apiMaxStake > 0.0) minOf(s.pinnLiveStake, s.apiMaxStake) else s.pinnLiveStake,
            maxPerGame = s.pinnLiveMaxGame, maxPerDay = s.pinnLiveMaxDay, haltLoss = s.pinnLiveHaltLoss, halted = takerHalt ?: s.pinnLiveHalted,
        )
    }

    private suspend fun gate(real: Boolean, stake: Double): String? {
        val s = settings
        val cash = port?.cash()
        return when {
            s.killed -> "STOP ALL is on"
            s.paused -> "scanning is paused"
            !real -> null
            port == null -> "no trading key on GitHub"
            port.blocked != null -> "Novig refused the engine's key: ${port.blocked}"
            cash == null -> "wallet not read yet"
            cash < stake * 1.2 -> "wallet too low"
            else -> null
        }
    }

    private suspend fun lossToday(source: String): Double {
        val from = ApiBetPlacer.localMidnight(clock())
        val net = tracker.all().filter { it.source == source && (it.settledAtMs ?: 0L) >= from }.sumOf { it.profit ?: 0.0 }
        return (-net).coerceAtLeast(0.0)
    }

    private val bidDesk: LiveBidDesk = LiveBidDesk(
        scope = scope, orders = orders, store = LiveBidStore(File(dir.also { it.mkdirs() }, "live-bids.json")),
        journal = DayJournal(File(dir, "bids"), "livebid", LiveBidEvent.serializer()) { it.atMs },
        config = ::bidConfig, wallet = { lastCash }, spentToday = { 0.0 }, dayLimit = { settings.apiMaxPerDay.takeIf { it > 0.0 } ?: Double.MAX_VALUE },
        lossToday = { lossToday(BetTracker.SOURCE_LIVEBID) },
        logFills = { target, orderId, fills -> tracker.logMakerFills(target, orderId, fills).also { record(it, "live bid") }?.id },
        onHalt = { why -> halted("Live bids", why, { bidHalt = it }, { bidHalt }) }, version = "github-engine",
        repeat = { settings.gates.repeatRules() }, heldBets = { RepeatGuard.fromBets(tracker.all()) },
    )

    private fun bidConfig(): LiveBidConfig {
        val s = settings
        return LiveBidConfig(
            on = bidsOn(s), real = bidsReal(s), quality = s.liveBidQuality, limits = s.liveBidLimits.effective(lastCash), bankroll = s.bankroll, apiMaxStake = s.apiMaxStake, preset = s.liveBidPresetName.ifBlank { "engine" },
            halted = bidHalt ?: s.liveBidHalted, blockedWhy = if (bidsReal(s) && (port == null || port.blocked != null)) "no usable trading key on GitHub" else null,
        )
    }

    private val trader: PinnLiveTrader? = orders?.let { o ->
        PinnLiveTrader(
            orders = o, scope = scope, rules = ::takerRules, gate = { gate(takerReal(settings), takerRules().stake) },
            ownBids = { bidDesk.bidsNow().filter { it.active }.map { LiveOwnBid(it.marketId, it.outcomeId, it.price) } },
            journal = DayJournal(File(dir, "pinn"), "pinn-live", LiveRecord.serializer()) { it.atMs },
            onHalt = { why -> halted("Live betting", why, { takerHalt = it }, { takerHalt }) },
            logFills = { target, orderId, fills -> tracker.logApi(target, orderId, fills).also { record(it, "live lag bet") } != null },
            lossToday = { lossToday(BetTracker.SOURCE_PINNODDS) },
            repeat = { settings.gates.repeatRules() },
            held = {
                RepeatGuard.fromBets(tracker.all()) + bidDesk.bidsNow().filter { it.active && it.restingDollars > 0.0 }
                    .map { RepeatGuard.Pos(it.marketId, it.outcomeId, it.restingDollars, (it.contracts - it.filled).coerceAtLeast(0) * EvMath.CONTRACT_PAYOUT_DOLLARS, count = 0) }
            },
        )
    }

    private fun boltRules(): BoltLiveConsensus.Rules {
        val g = settings.gates
        return BoltLiveConsensus.Rules(
            minCompanies = g.boltLiveMinCompanies.coerceIn(2, 10), maxAgeMs = g.boltLiveMaxAgeSeconds.coerceIn(2, 60) * 1000L, maxSpread = g.boltLiveMaxSpreadPoints.coerceIn(1, 15) / 100.0, method = settings.pinnLiveDevig,
        )
    }

    private fun config(): LiveConfig {
        val s = settings
        // A soft-book consensus: no scores to wait for, a floor under the edge, a settle time, no Pinnacle-specific margin or limit rules (the phone's own BOLT branch).
        val rules = LiveRules(
            minEv = maxOf(s.pinnLiveMinEv, BOLT_LIVE_MIN_EV), minMove = s.pinnLiveMinMove, pregame = false,
            trigger = if (s.pinnLiveTrigger == LiveTrigger.SCORE) LiveTrigger.MOVE else s.pinnLiveTrigger,
            holdoffMs = s.pinnLiveHoldoffSeconds * 1000L, settleMs = BOLT_LIVE_SETTLE_MS, maxOverround = 1.0, minPinnLimit = 0.0,
        )
        return LiveConfig(rules = rules, method = s.pinnLiveDevig, leagues = Leagues.ALL.map { it.novigName }.toSet() + PinnLiveRunner.EXTRA_LEAGUES, takerOn = takerOn(s), scanOn = false, scanSettings = s)
    }

    private val runner: PinnLiveRunner? = trader?.let { t ->
        PinnLiveRunner(
            scope = scope, source = source, newFeed = { l -> SharedStreamView({ stream() }, l) },
            openFeed = { onFrame -> feed({ settings }, onFrame, ::boltRules) },
            trader = t, config = ::config, followJournal = DayJournal(File(dir, "pinn"), "pinn-follow", LiveFollow.serializer()) { it.atMs },
            bids = bidDesk, bidConfig = ::bidConfig,
        )
    }

    override suspend fun pass(s: ScanSettings, now: Long): LiveLaneStatus? {
        settings = s
        if (!wanted(s)) {
            if (up) stop("the engine's live taker and live bids are switched off on the phone")
            return null
        }
        up = true
        lastCash = port?.cash() ?: lastCash
        if (s.pinnLiveHalted == null && takerHalt != null && takerSeen) { takerHalt = null; trader?.resumed(); takerSeen = false }
        if (takerHalt != null) takerSeen = true
        if (s.liveBidHalted == null && bidHalt != null && bidSeen) { bidHalt = null; bidDesk.resumed(); bidSeen = false }
        if (bidHalt != null) bidSeen = true
        val halt = listOfNotNull(takerHalt, bidHalt).joinToString("; ")
        val r = runner ?: return LiveLaneStatus(true, "no trading key on GitHub: the live taker and bids cannot run", halt, now)
        if (stream() == null) return LiveLaneStatus(true, "waiting for Novig's live feed (the engine's read key or websocket is not up)", halt, now)
        if (!r.running) r.start()
        if (bidsOn(s)) runCatching { bidDesk.start() }
        val st = r.status.value
        val note = buildString {
            append("live taker ${if (takerOn(s)) (if (takerReal(s)) "REAL" else "paper") else "off"} · live bids ${if (bidsOn(s)) (if (bidsReal(s)) "REAL" else "paper") else "off"}")
            append(" · soft-book feed ${st.socket}")
            if (bidsOn(s)) append(" · ${bidDesk.bidsNow().count { it.active }} bids up")
        }
        return LiveLaneStatus(true, note, halt, now)
    }

    override suspend fun stop(why: String) {
        up = false
        runner?.let { if (it.running) it.stop(why) }
        runCatching { bidDesk.stopAll(why) }.onFailure { if (it is CancellationException) throw it }
        runCatching { bidDesk.stop() }
        log("live taker/bids: down ($why)")
    }

    companion object {
        /** The phone's own floor for the soft-book average (a consensus trails the truth; a thin edge on it is noise) and its settle time. */
        const val BOLT_LIVE_MIN_EV = 0.04
        const val BOLT_LIVE_SETTLE_MS = 1_500L

        fun takerOn(s: ScanSettings) = s.engineLive && s.pinnLive
        fun takerReal(s: ScanSettings) = takerOn(s) && s.pinnLiveBet && s.gates.cloudTrading == CloudTradeMode.LIVE && s.gates.boltLiveReal
        fun bidsOn(s: ScanSettings) = s.engineLive && s.liveBid
        fun bidsReal(s: ScanSettings) = bidsOn(s) && s.liveBidReal && s.gates.cloudTrading == CloudTradeMode.LIVE
        fun wanted(s: ScanSettings) = takerOn(s) || bidsOn(s)
    }
}
