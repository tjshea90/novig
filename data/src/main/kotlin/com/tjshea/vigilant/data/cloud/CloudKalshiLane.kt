package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.kalshitrade.BorrowedSource
import com.tjshea.vigilant.data.kalshitrade.KalshiArb
import com.tjshea.vigilant.data.kalshitrade.KalshiAsNovig
import com.tjshea.vigilant.data.kalshitrade.KalshiAutoBettor
import com.tjshea.vigilant.data.kalshitrade.KalshiAutoMode
import com.tjshea.vigilant.data.kalshitrade.KalshiBetPlacer
import com.tjshea.vigilant.data.kalshitrade.KalshiBidRunner
import com.tjshea.vigilant.data.kalshitrade.KalshiEngineArb
import com.tjshea.vigilant.data.kalshitrade.KalshiEngineConfig
import com.tjshea.vigilant.data.kalshitrade.KalshiEngineRow
import com.tjshea.vigilant.data.kalshitrade.KalshiEngineStatus
import com.tjshea.vigilant.data.kalshitrade.KalshiMirror
import com.tjshea.vigilant.data.kalshitrade.KalshiOutcomes
import com.tjshea.vigilant.data.kalshitrade.KalshiScanner
import com.tjshea.vigilant.data.kalshitrade.KalshiSettler
import com.tjshea.vigilant.data.kalshitrade.KalshiTradingClient
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerStore
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.util.Locale

/**
 * The GitHub engine's Kalshi lane (KV9, Tj 2026-10-20: "the app and GitHub engine should adopt as many features as possible ... so that there is no double work"). After each Novig scan the engine has just
 * finished, the lane prices Kalshi from the boards that scan HOLDS (no second API call for the same league: [BorrowedSource]), runs the Kalshi auto-bet and bid desk under the Kalshi config the phone wrote
 * (`kalshi.json`), lists the sure profits between that Novig scan and Kalshi, and publishes `kalshi-status.json`. It never touches the Novig lanes: it runs in a coroutine of its own, one pass at a time, and a
 * failure is a line in the log. Switch (phone only, default OFF): OFF does nothing and takes the bids down; PAPER records what it would bet; LIVE orders with the Kalshi key.
 *
 * The engine does not place arbitrage orders (the second leg needs both venues' accounts in one place); it lists them for the phone.
 */
class CloudKalshiLane(
    private val scope: CoroutineScope,
    private val readConfig: suspend () -> KalshiEngineConfig?,
    private val trading: KalshiTradingClient,
    private val hasKey: () -> Boolean,
    private val venue: ReferenceSource,
    /** The fair-odds sources the engine builds for [ScanSettings] (the Kalshi venue is added here). */
    private val sources: (ScanSettings) -> List<ReferenceSource>,
    /** The Novig scan's held board for a source and league (null = none). */
    private val novigHeld: (sourceId: String, leagueName: String) -> RefSnapshot?,
    /** Why a bet's player cannot be bet on (the engine's injury reports), or null. */
    private val unavailable: (Opportunity) -> String?,
    private val dir: File,
    /** Puts a small file on `cloud-feed` with the next push. */
    private val publish: (name: String, text: String) -> Unit,
    private val tell: suspend (title: String, text: String) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val outcomes = KalshiOutcomes()
    private val lock = Mutex()
    val tracker = BetTracker(File(dir, "kalshi-bets.json"), clock, ownBook = "Kalshi")
    private val paperTracker = BetTracker(File(dir, "kalshi-paper.json"), clock, ownBook = "Kalshi")
    private val bidStore = MakerStore(File(dir, "kalshi-maker.json"))
    private val orderLock = Mutex()
    private val placer = KalshiBetPlacer(
        trading, tracker, limits = { BetLimits(10.0, 50.0, maxPerGame = 25.0) }, clock = clock, lock = orderLock,
    )
    private val bidDesk = MakerDesk(KalshiAsNovig(trading, outcomes, clock), tracker, bidStore, lock = orderLock, clock = clock, cancelStrays = false)
    private val bidRunner = KalshiBidRunner(bidDesk, outcomes, cash = { if (hasKey()) trading.balance().cash else null }, unavailable = unavailable, tell = tell, clock = clock, log = log)
    private val scanner = KalshiScanner.create(clock)
    private val settler = KalshiSettler(trading, tracker, clock)
    private var bidsUp = false
    @Volatile private var last = KalshiEngineStatus()
    @Volatile private var lastConfig: KalshiEngineConfig? = null

    /** What the lane says about itself now. */
    fun status(): KalshiEngineStatus = last

    /**
     * Called by the engine's `onResult` after a finished Novig scan (with the settings it ran under): starts a Kalshi pass on its own coroutine unless one is running, and returns at once so the Novig
     * list is never delayed.
     */
    fun onNovigScan(novig: ScanResult, phone: ScanSettings) {
        scope.launch { runOnce(novig, phone) }
    }

    /** One Kalshi pass now, unless one is running (false then). The engine calls it through [onNovigScan]; the tests call it directly. */
    suspend fun runOnce(novig: ScanResult, phone: ScanSettings): Boolean {
        if (!lock.tryLock()) return false
        try {
            pass(novig, phone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("kalshi lane failed: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
            last = last.copy(atMs = clock(), note = "the Kalshi pass failed: ${e.message?.take(120) ?: e.javaClass.simpleName}")
            publishStatus()
        } finally {
            lock.unlock()
        }
        return true
    }

    private fun publishStatus() = runCatching { publish(KalshiEngineStatus.FILE, KalshiEngineStatus.text(last)) }

    private suspend fun pass(novig: ScanResult, phone: ScanSettings) {
        val now = clock()
        val ec = readConfig()
        lastConfig = ec
        if (ec == null || ec.mode == KalshiAutoMode.OFF) {
            if (bidsUp) { bidRunner.stop("the engine's Kalshi switch is off"); bidsUp = false }
            if (last.mode != KalshiAutoMode.OFF || last.note.isEmpty()) {
                last = KalshiEngineStatus(atMs = now, mode = KalshiAutoMode.OFF, rev = ec?.rev ?: 0L, note = "off (the phone's Kalshi tab › Setup › GitHub engine)", keyOnGitHub = hasKey())
                publishStatus()
            }
            return
        }
        val cfg = ec.config
        // STOP ALL is Tj's alone and stops this lane too: every bid comes down and nothing is bet.
        if (phone.killed) {
            if (bidsUp) { bidRunner.stop("STOP ALL"); bidsUp = false }
            last = KalshiEngineStatus(atMs = now, mode = ec.mode, rev = ec.rev, note = "stopped by STOP ALL", stopped = "STOP ALL", keyOnGitHub = hasKey())
            publishStatus()
            return
        }
        val live = ec.mode == KalshiAutoMode.LIVE && hasKey() && cfg.tradingEnabled
        val s = com.tjshea.vigilant.data.cloud.CloudEngine.forCloud(KalshiMirror.fairOdds(cfg.forScan(), phone))
        val t0 = clock()
        val srcs = sources(s).map { src -> BorrowedSource(src, novigHeld, { true }, clock, borrowProps = true) } + venue
        val report = scanner.scan(s, srcs, emptySet())
        val result = report.result
        if (result == null) {
            last = last.copy(atMs = clock(), mode = ec.mode, rev = ec.rev, note = "no Kalshi result: ${report.errors.firstOrNull()?.take(140) ?: "nothing came back"}")
            publishStatus()
            return
        }
        val scanMs = clock() - t0
        // Bets: the same auto-bet rules, paper or real.
        var autoNote = ""
        var placed = 0
        var paper = 0
        if (cfg.autoMode != KalshiAutoMode.OFF) {
            val auto = KalshiAutoBettor(
                placer, tracker, paperTracker, wallet = { if (hasKey()) trading.balance().cash else null }, clock = clock,
                unavailable = unavailable,
            )
            val r = auto.run(s.copy(autoBet = true), result, paper = !live)
            placed = r.placed.size
            paper = r.paper.size
            autoNote = "looked at ${r.looked}, ${r.passed} passed" + (if (placed > 0) ", placed $placed" else "") + (if (paper > 0) ", would place $paper" else "") +
                (r.stopped?.let { " · stopped: $it" }.orEmpty()) + r.skipped.entries.sortedByDescending { it.value }.take(2).joinToString("") { " · ${it.value}× ${it.key}" }
            if (auto.lostOrder != null) tell("Kalshi order unconfirmed", auto.lostOrder!!.take(300))
        }
        // Bids (real orders only).
        var bidNote = ""
        if (live && cfg.scan.maker) {
            val st = bidRunner.pass(s.copy(maker = true), result, if (s.paused) "Scanning is paused" else null, clock())
            bidsUp = true
            bidNote = st?.note.orEmpty()
        } else if (bidsUp) {
            bidRunner.stop(if (live) "Kalshi bids are switched off" else "the engine's Kalshi switch is not LIVE")
            bidsUp = false
            bidNote = "bids down"
        }
        if (live) runCatching { settler.run() }
        // Sure profits between this Novig scan and the Kalshi board the scan just read.
        val arbs = runCatching { KalshiArb.find(novig, scanner.heldSnapshots(KalshiClient.VENUE_ID), cfg.arbSettings, clock()) }.getOrDefault(emptyList())
        val rows = result.feed(cfg.scan).filter { !it.isLive }.take(MAX_ROWS)
        val cash = if (hasKey()) runCatching { trading.balance().cash }.getOrNull() else null
        last = KalshiEngineStatus(
            atMs = clock(), mode = ec.mode, rev = ec.rev, scanMs = scanMs, scannedAtMs = result.computedAtMs,
            note = "${rows.size} bets listed of ${result.stats.outcomesWithFair} priced" + (if (live) " · LIVE" else " · paper") + (if (arbs.isNotEmpty()) " · ${arbs.size} sure profits" else ""),
            listed = result.feed(cfg.scan).size, priced = result.stats.outcomesWithFair,
            rows = rows.mapNotNull { o ->
                val q = o.quote ?: return@mapNotNull null
                KalshiEngineRow(o.selection, o.event.description, o.league.displayName, o.marketLabel, o.event.startsTs, q.evPercent, q.price, o.fairProbability ?: 0.0, o.rating?.stars)
            },
            arbs = arbs.take(MAX_ARBS).map { a -> KalshiEngineArb(a.novig.selection, a.kalshi.selection, a.event.description, a.event.league, a.profitPerDollar, a.maxPayout, a.safe) },
            cash = cash, autoNote = autoNote, bidNote = bidNote, betsPlaced = placed, betsPaper = paper, keyOnGitHub = hasKey(),
        )
        publishStatus()
        // The rows themselves, so the phone's +EV list and Bet sheet read them instead of scanning Kalshi on the phone (Tj, 2026-10-20: "all the kalshi bet scanning on GitHub, it is lagging the screen").
        runCatching { publish(FEED_FILE, kotlinx.serialization.json.JsonArray(result.feed(cfg.scan).filter { !it.isLive }.take(FEED_ROWS).map { CloudCodec.encode(it) }).toString()) }
        // The carry-over for the next job: the bets and bids, the same way the desk's files ride.
        runCatching { publish("kalshi-bets.json", kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tjshea.vigilant.data.tracker.TrackedBet.serializer()), tracker.all())) }
        runCatching { publish("kalshi-paper.json", kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tjshea.vigilant.data.tracker.TrackedBet.serializer()), paperTracker.all())) }
        runCatching { publish("kalshi-maker.json", kotlinx.serialization.json.Json { encodeDefaults = false }.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tjshea.vigilant.data.novig.trading.maker.MakerBid.serializer()), bidStore.all())) }
        log("kalshi lane: ${last.note}" + (autoNote.takeIf { it.isNotEmpty() }?.let { " · auto: $it" }.orEmpty()) + (bidNote.takeIf { it.isNotEmpty() }?.let { " · bids: $it" }.orEmpty()))
    }

    /** The engine's hours or the job are over: every Kalshi bid comes down. */
    suspend fun stop(why: String) {
        if (bidsUp) {
            runCatching { bidRunner.stop(why) }
            bidsUp = false
        }
    }

    companion object {
        const val MAX_ROWS = 40
        const val FEED_ROWS = 100
        const val FEED_FILE = "kalshi-feed.json"
        const val MAX_ARBS = 20
    }
}
