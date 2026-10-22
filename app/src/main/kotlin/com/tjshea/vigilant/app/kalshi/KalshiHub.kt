package com.tjshea.vigilant.app.kalshi

import com.tjshea.vigilant.app.MISS_RESTORE_MS
import com.tjshea.vigilant.app.AppContainer
import com.tjshea.vigilant.app.ApiBetTargets
import com.tjshea.vigilant.data.book.BookRef
import com.tjshea.vigilant.data.kalshitrade.ArbExecutor
import com.tjshea.vigilant.data.kalshitrade.ArbOpportunity
import com.tjshea.vigilant.data.kalshitrade.ArbOutcome
import com.tjshea.vigilant.data.kalshitrade.KalshiArb
import com.tjshea.vigilant.data.kalshitrade.KalshiAutoBettor
import com.tjshea.vigilant.data.kalshitrade.KalshiAutoMode
import com.tjshea.vigilant.data.kalshitrade.KalshiBetPlacer
import com.tjshea.vigilant.data.kalshitrade.KalshiBetTargets
import com.tjshea.vigilant.data.kalshitrade.KalshiAsNovig
import com.tjshea.vigilant.data.kalshitrade.KalshiBidRunner
import com.tjshea.vigilant.data.kalshitrade.KalshiBidStatus
import com.tjshea.vigilant.data.kalshitrade.KalshiOutcomes
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerStore
import com.tjshea.vigilant.data.kalshitrade.KalshiConfig
import com.tjshea.vigilant.data.kalshitrade.KalshiFees
import com.tjshea.vigilant.data.kalshitrade.KalshiScanner
import com.tjshea.vigilant.data.kalshitrade.KalshiSettler
import com.tjshea.vigilant.data.kalshitrade.KalshiTradingClient
import com.tjshea.vigilant.data.kalshitrade.LegResult
import com.tjshea.vigilant.data.kalshitrade.VenueMode
import com.tjshea.vigilant.data.kalshitrade.BorrowedSource
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.novig.trading.PlanResult
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanRunner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Everything the Kalshi tab shows. */
data class KalshiUi(
    val venues: VenueMode = VenueMode(),
    val config: KalshiConfig = KalshiConfig(),
    val hasKey: Boolean = false,
    val scanning: Boolean = false,
    val progress: String? = null,
    val result: ScanResult? = null,
    val feed: List<Opportunity> = emptyList(),
    val scannedAtMs: Long? = null,
    val errors: List<String> = emptyList(),
    val balance: Double? = null,
    val portfolioValue: Double? = null,
    val balanceNote: String? = null,
    val bets: List<TrackedBet> = emptyList(),
    val paperBets: List<TrackedBet> = emptyList(),
    val arbs: List<ArbOpportunity> = emptyList(),
    val arbNote: String? = null,
    val arbBusy: Boolean = false,
    val autoNote: String? = null,
    val bidStatus: KalshiBidStatus? = null,
    val engine: com.tjshea.vigilant.data.kalshitrade.KalshiEngineStatus? = null,
    val engineNote: String? = null,
    /** The list on screen is the GitHub engine's (not this phone's own scan). */
    val engineFeed: Boolean = false,
    val bids: List<MakerBid> = emptyList(),
    val message: String? = null,
)

/**
 * The Kalshi side of Vigilant (KV, Tj 2026-10-20). One object holds its settings, its scan ([KalshiScanner] on the app's own [ScanRunner]), its orders ([KalshiBetPlacer]), its auto-bet
 * ([KalshiAutoBettor]), its record of bets (own file, never mixed into Novig's), the Novig x Kalshi arbitrage and the venue switches. It reads the Novig side (its scan result, its book reads, its
 * placer) and never writes to it, except an arbitrage's Novig leg, which is an ordinary Novig order through the Novig placer under the Novig limits.
 */
class KalshiHub(private val c: AppContainer) {
    private val files = c.app.filesDir
    val configStore = JsonFileStore(File(files, "kalshi_config.json"), KalshiConfig.serializer(), { KalshiConfig() }, c.json)
    val venueStore = JsonFileStore(File(files, "venues.json"), VenueMode.serializer(), { VenueMode() }, c.json)
    val tracker = BetTracker(File(files, "kalshi_bets.json"), ownBook = "Kalshi")
    val paperTracker = BetTracker(File(files, "kalshi_paper.json"), ownBook = "Kalshi")

    private val trading = KalshiTradingClient(c.http, c.json, { c.kalshiSigner() })
    private val scanner = KalshiScanner.create()
    private val runner = ScanRunner(scanner, c.appScope)
    private val orderLock = Mutex()

    private val _ui = MutableStateFlow(KalshiUi())
    val ui: StateFlow<KalshiUi> = _ui.asStateFlow()

    private var loop: Job? = null
    private var lastScanStart = 0L
    private var lastSettleAt = 0L
    private var lastEnginePoll = 0L

    private fun cfg(): KalshiConfig = _ui.value.config

    /** The Kalshi scan's settings: its own money rules and filters, the fair-odds side (APIs, books, method) mirrored from the Novig settings ([KalshiMirror]). */
    private fun effective(c0: KalshiConfig): ScanSettings = com.tjshea.vigilant.data.kalshitrade.KalshiMirror.fairOdds(c0.forScan(), c.settingsStore.flow.value)

    private val placer = KalshiBetPlacer(
        trading, tracker,
        limits = { cfg().scan.let { BetLimits.manual(it.apiMaxStake, it.apiMaxPerDay, it.apiMaxPerGame) } },
        paused = { cfg().scan.paused },
        lock = orderLock,
        repeatRules = { cfg().scan.gates.repeatRules() },
    )
    private val settler = KalshiSettler(trading, tracker)
    private val auto = KalshiAutoBettor(
        placer, tracker, paperTracker,
        wallet = { if (trading.ready) trading.balance().cash else null },
        unavailable = { o -> com.tjshea.vigilant.data.reference.InjuryTags.wantOf(o)?.let { w -> com.tjshea.vigilant.data.reference.InjuryTags.tags(c.injuries.book.value, listOf(w), System.currentTimeMillis())[w.key] }?.let { com.tjshea.vigilant.data.reference.PlayerOut.forTag(it) } },
    )

    private val outcomes = KalshiOutcomes()
    private val bidStore = MakerStore(File(files, "kalshi_maker.json"))
    private val bidDesk = MakerDesk(KalshiAsNovig(trading, outcomes), tracker, bidStore, lock = orderLock, cancelStrays = false)
    private val bidRunner = KalshiBidRunner(
        bidDesk, outcomes, cash = { if (trading.ready) trading.balance().cash else null },
        onFills = { bets, _ -> bets.forEach { KalshiNotes.bidFilled(c.app, it) } },
        unavailable = { o -> com.tjshea.vigilant.data.reference.InjuryTags.wantOf(o)?.let { w -> com.tjshea.vigilant.data.reference.InjuryTags.tags(c.injuries.book.value, listOf(w), System.currentTimeMillis())[w.key] }?.let { com.tjshea.vigilant.data.reference.PlayerOut.forTag(it) } },
        tell = { title, text -> c.eventLog.warn("KALSHI", "$title: $text") },
        log = { c.eventLog.info("KALSHI", it) },
    )
    private var lastBidPass = 0L

    init {
        c.appScope.launch {
            val cfg = configStore.read()
            val venues = venueStore.read()
            _ui.update { it.copy(config = cfg, venues = venues, hasKey = c.kalshiSigner() != null, bets = tracker.all(), paperBets = paperTracker.all()) }
            start()
        }
        c.appScope.launch { runner.state.collect { run -> publish(run) } }
    }

    // ---- venues ---------------------------------------------------------------------------------------------------

    /**
     * Switches the venues. Kalshi off means no scan, no auto-bet and no order from this side. Novig off hides the Novig tabs and pauses the phone's Novig scanning and auto-bet the way the
     * Pause button does (and remembers it did, so switching Novig back on resumes only what this switch paused); the GitHub engine is controlled in its own settings.
     */
    fun setVenues(novig: Boolean? = null, kalshi: Boolean? = null) {
        c.appScope.launch {
            val before = venueStore.read()
            val next = before.copy(novig = novig ?: before.novig, kalshi = kalshi ?: before.kalshi)
            var paused = next.novigPausedByVenue
            if (before.novig && !next.novig) {
                val alreadyPaused = c.settingsStore.read().pausedByHand
                if (!alreadyPaused) { c.settingsStore.update { it.copy(pausedByHand = true) }; paused = true }
            } else if (!before.novig && next.novig && next.novigPausedByVenue) {
                c.settingsStore.update { it.copy(pausedByHand = false) }
                paused = false
            }
            val saved = venueStore.update { next.copy(novigPausedByVenue = paused) }
            _ui.update { it.copy(venues = saved) }
            if (!saved.kalshi) { runCatching { runner.stop() }; runCatching { bidRunner.stop("Kalshi was switched off") } }
            c.eventLog.info("KALSHI", "venues: Novig ${if (saved.novig) "on" else "off"}, Kalshi ${if (saved.kalshi) "on" else "off"}")
        }
    }

    fun update(transform: (KalshiConfig) -> KalshiConfig) {
        c.appScope.launch {
            val before = configStore.read()
            val saved = configStore.update(transform)
            _ui.update { it.copy(config = saved) }
            // The GitHub engine follows this config while its Kalshi switch is not OFF (and once more when it goes OFF, so it takes its bids down).
            if (saved.engineMode != KalshiAutoMode.OFF || before.engineMode != KalshiAutoMode.OFF) pushEngineConfig(saved)
        }
    }

    private var pushJob: Job? = null

    /** Writes `kalshi.json` for the engine, at most once every few seconds (the chips are tapped one after another). */
    private fun pushEngineConfig(cfg: KalshiConfig) {
        pushJob?.cancel()
        pushJob = c.appScope.launch {
            delay(PUSH_SETTLE_MS)
            val why = c.cloud.putKalshiConfig(com.tjshea.vigilant.data.kalshitrade.KalshiEngineConfig.text(com.tjshea.vigilant.data.kalshitrade.KalshiEngineConfig(System.currentTimeMillis(), cfg.engineMode, cfg, System.currentTimeMillis())))
            _ui.update { it.copy(engineNote = if (why == null) "Sent to GitHub ${java.time.LocalTime.now().withNano(0)}" else "Not sent to GitHub: $why") }
        }
    }

    /** Sets what the GitHub engine does for Kalshi: OFF, PAPER (records, orders nothing) or LIVE (orders with the Kalshi key on GitHub). */
    fun setEngineMode(mode: KalshiAutoMode) {
        update { it.copy(engineMode = mode) }
        c.appScope.launch { delay(PUSH_SETTLE_MS + 2_000L); pollEngine() }
    }

    /** Reads the engine's Kalshi status once. */
    suspend fun pollEngine() {
        if (!c.cloud.kalshiCanReachGitHub()) return
        val st = runCatching { c.cloud.readKalshiStatus() }.getOrNull()
        if (st != null) _ui.update { it.copy(engine = st) }
    }

    // ---- scan -----------------------------------------------------------------------------------------------------

    private fun sources(settings: ScanSettings): List<ReferenceSource> {
        val novigScanner = c.localScanner as? Scanner
        val fair = c.referenceSources(settings, background = false, scan = true).map { src ->
            if (novigScanner == null) src
            else BorrowedSource(src, { id, league -> novigScanner.heldSnapshot(id, league) }, { s -> c.settingsStore.flow.value?.let { n -> n.referenceBooks == s.referenceBooks && n.families == s.families } == true })
        }
        return fair + c.kalshiVenueSource()
    }

    /** Scans Kalshi now (Tj's tap, or the loop). */
    fun scanNow() {
        val ui = _ui.value
        if (!ui.venues.kalshi) return
        val settings = effective(ui.config)
        if (settings.leagues.isEmpty()) { say("Pick at least one league (Kalshi tab › Settings)."); return }
        lastScanStart = System.currentTimeMillis()
        // The GitHub engine's Kalshi lane is the scan when it is on and fresh: the phone only reads its list (Tj, 2026-10-20). Its own scan is the fallback.
        if (ui.config.engineMode != KalshiAutoMode.OFF && c.cloud.kalshiCanReachGitHub()) {
            c.appScope.launch {
                if (adoptEngineList()) return@launch
                // Tj, 2026-10-22 ("laggy after I turn kalshi scanning on"): an engine that is alive but has not finished its first scan (the ten minutes after a restart) is waited for; this phone scans all of Kalshi itself only when the engine is down.
                val st = c.cloud.latest.status
                if (st != null && !st.idle && System.currentTimeMillis() - st.heartbeatMs <= com.tjshea.vigilant.data.cloud.CloudCno.ALIVE_MS) _ui.update { it.copy(scanning = false, progress = "The GitHub engine is still reading Kalshi; its list appears here when it is ready.") }
                else scanLocally(settings)
            }
            return
        }
        scanLocally(settings)
    }

    /** Reads the engine's status and list; true when they are fresh (under 10 minutes) and now the list on screen. */
    private suspend fun adoptEngineList(): Boolean {
        _ui.update { it.copy(progress = "Reading the GitHub engine's Kalshi list") }
        val st = runCatching { c.cloud.readKalshiStatus() }.getOrNull()
        val rows = if (st != null && st.mode != KalshiAutoMode.OFF && System.currentTimeMillis() - st.atMs < ENGINE_FRESH_MS) runCatching { c.cloud.readKalshiFeed() }.getOrNull() else null
        if (st == null || rows == null) { _ui.update { it.copy(progress = null) }; return false }
        engineRows = rows
        _ui.update { u ->
            u.copy(engine = st, progress = null, scanning = false, scannedAtMs = st.scannedAtMs, errors = emptyList(), engineFeed = true,
                feed = rows.filter { o -> !o.isLive && o.key !in u.bets.mapNotNull { b -> b.placedKey } && o.key !in provisional })
        }
        return true
    }

    private fun scanLocally(settings: ScanSettings) {
        _ui.update { it.copy(engineFeed = false) }
        val started = runner.start(settings, sources(settings), emptySet()) { report ->
            val result = report?.result
            if (result != null) afterScan(result)
        }
        if (!started) say("A Kalshi scan is already running.")
    }

    private fun publish(run: com.tjshea.vigilant.data.scanner.ScanRun) {
        val cfg = cfg()
        val result = run.result
        _ui.update {
            it.copy(
                scanning = run.scanning,
                progress = run.progress?.let { p -> if (p.total > 0) "${p.step} ${p.done}/${p.total}" else p.step },
                result = result,
                feed = feedOf(result, cfg.scan, it.bets, it.engineFeed),
                scannedAtMs = result?.computedAtMs,
                errors = run.report?.errors.orEmpty(),
            )
        }
    }

    /** Keys of bets whose slip closed a moment ago and whose order has not been recorded (or has missed): off the list at the tap, back after [MISS_RESTORE_MS] if nothing went through (Tj, 2026-10-20, as the Novig lists do). */
    private val provisional = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** The Kalshi list: the scan's +EV rows, minus the ones already bet (the record's own keys, so a rescan or a restart never brings one back) or just sent. */
    private fun feedOf(result: ScanResult?, scan: com.tjshea.vigilant.data.scanner.ScanSettings, bets: List<TrackedBet>, engine: Boolean = false): List<Opportunity> {
        val bet = bets.mapNotNullTo(HashSet()) { it.placedKey }
        return (if (engine) engineRows else result?.feed(scan))?.filter { o -> !o.isLive && o.key !in bet && o.key !in provisional }.orEmpty()
    }

    @Volatile private var engineRows: List<Opportunity> = emptyList()

    private fun refeed() = _ui.update { it.copy(feed = feedOf(it.result, cfg().scan, it.bets, it.engineFeed)) }

    private suspend fun afterScan(result: ScanResult) {
        refreshArbs()
        when (cfg().autoMode) {
            KalshiAutoMode.OFF -> Unit
            KalshiAutoMode.PAPER -> runAuto(result, paper = true)
            KalshiAutoMode.LIVE -> if (cfg().tradingEnabled) runAuto(result, paper = false)
        }
        bidPass()
        refreshBets()
    }

    /** One pass of the Kalshi bid desk on the last Kalshi scan (bids need orders on AND the bids switch; with either off, whatever is up comes down once). */
    suspend fun bidPass() {
        lastBidPass = System.currentTimeMillis()
        val ui = _ui.value
        val s = effective(ui.config)
        val live = ui.config.tradingEnabled && ui.venues.kalshi && trading.ready
        val status = try {
            bidRunner.pass(if (live) s else s.copy(maker = false), ui.result, stopReason = if (s.paused) "Scanning is paused" else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KalshiBidStatus("Bid pass failed: ${e.message ?: e.javaClass.simpleName}")
        }
        val bids = runCatching { bidRunner.bids() }.getOrDefault(emptyList())
        // Bids on but the pass was told to stand down: say which of the conditions is missing instead of showing nothing (CH39, Tj 2026-10-20: "toggles on but no bids").
        val standDown = if (s.maker && !live) when {
            !ui.venues.kalshi -> "Kalshi is switched off (Where to bet)."
            !ui.config.tradingEnabled -> "\"Place orders on Kalshi\" is off (Kalshi › Setup)."
            !trading.ready -> "No Kalshi key is saved on this phone (Settings › Kalshi)."
            else -> null
        } else null
        val shown = standDown?.let { KalshiBidStatus("Bids can't post: $it", atMs = System.currentTimeMillis()) } ?: status ?: bidRunner.status
        _ui.update { it.copy(bidStatus = shown, bids = bids) }
    }

    /** Cancel every Kalshi bid now. */
    fun cancelBids() {
        c.appScope.launch {
            bidRunner.stop("Cancelled by you")
            _ui.update { it.copy(bids = emptyList(), bidStatus = bidRunner.status) }
            refreshBets()
        }
    }

    private suspend fun runAuto(result: ScanResult, paper: Boolean) {
        val settings = effective(cfg())
        val report = try {
            auto.run(settings, result, paper)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(autoNote = "Auto-bet failed: ${e.message ?: e.javaClass.simpleName}") }
            return
        }
        val note = buildString {
            append(if (paper) "Paper auto-bet: " else "Auto-bet: ")
            append("looked at ${report.looked}, ${report.passed} passed")
            if (report.placed.isNotEmpty()) append(", placed ${report.placed.size}")
            if (report.paper.isNotEmpty()) append(", would place ${report.paper.size}")
            report.stopped?.let { append(" · stopped: $it") }
            report.skipped.entries.sortedByDescending { it.value }.take(2).forEach { append(" · ${it.value}× ${it.key}") }
        }
        _ui.update { it.copy(autoNote = note) }
        if (report.placed.isNotEmpty()) c.eventLog.info("KALSHI", "auto-bet placed ${report.placed.size}")
        if (!paper) {
            report.placed.forEach { KalshiNotes.placed(c.app, it, "Kalshi auto-bet") }
            report.stopped?.let { KalshiNotes.failed(c.app, "Kalshi auto-bet stopped", it) }
        }
    }

    // ---- bets by hand ---------------------------------------------------------------------------------------------

    /** What [stake] would do now: the Bet sheet's numbers. */
    suspend fun plan(o: Opportunity, stake: Double, allowRepeat: Boolean = false): PlanResult {
        val target = KalshiBetTargets.of(o) ?: return PlanResult.Refused("This bet has no fair price.")
        return placer.plan(target, stake, allowRepeat)
    }

    /** Places the bet the sheet showed (its limit price is the ceiling). */
    suspend fun place(o: Opportunity, stake: Double, confirmedLimit: Double, allowRepeat: Boolean = false): PlaceResult {
        if (!cfg().tradingEnabled) return PlaceResult.Refused("Orders are off. Turn on \"Place orders on Kalshi\" in Kalshi › Settings first.")
        if (c.kalshiSigner() == null) return PlaceResult.Refused("No Kalshi key is saved (Settings › Kalshi).")
        val target = KalshiBetTargets.of(o) ?: return PlaceResult.Refused("This bet has no fair price.")
        val r = withContext(Dispatchers.IO) { placer.place(target, stake, confirmedLimit, allowRepeat) }
        refreshBets()
        refreshBalance()
        // The sheet closes at the tap, so the result comes as a notification (Tj, 2026-10-20, CH32c/d), as on the Novig side.
        when (r) {
            is PlaceResult.Placed -> KalshiNotes.placed(c.app, r.bet, "Kalshi bet")
            is PlaceResult.NotFilled -> KalshiNotes.failed(c.app, "Kalshi bet not placed: ${o.selection}", r.reason)
            is PlaceResult.Refused -> KalshiNotes.failed(c.app, "Kalshi bet not placed: ${o.selection}", r.reason)
            is PlaceResult.Failed -> KalshiNotes.failed(c.app, "Kalshi bet failed: ${o.selection}", r.message)
            is PlaceResult.Unconfirmed -> KalshiNotes.failed(c.app, "Kalshi bet unconfirmed (timed out): ${o.selection}", r.message)
        }
        return r
    }

    /** Places the bet in the app's own scope so the Bet sheet can close at the tap (Tj, 2026-10-20, CH32c); the result is a notification ([place]). */
    fun placeAsync(o: Opportunity, stake: Double, confirmedLimit: Double) {
        provisional += o.key
        refeed()
        c.appScope.launch {
            // Whatever the order did, the key stays off for MISS_RESTORE_MS; after that only a recorded bet keeps it off.
            launch { kotlinx.coroutines.delay(MISS_RESTORE_MS); provisional -= o.key; refeed() }
            runCatching { place(o, stake, confirmedLimit) }.onFailure { if (it is CancellationException) throw it; KalshiNotes.failed(c.app, "Kalshi bet failed: ${o.selection}", it.message ?: it.javaClass.simpleName) } }
    }

    suspend fun refreshBets() {
        runCatching { settler.run() }
        val bets = tracker.all()
        val paper = paperTracker.all()
        _ui.update { it.copy(bets = bets, paperBets = paper, feed = feedOf(it.result, cfg().scan, bets, it.engineFeed)) }
    }

    fun refreshBalance() {
        if (c.kalshiSigner() == null) { _ui.update { it.copy(hasKey = false, balance = null, balanceNote = "No Kalshi key saved") }; return }
        _ui.update { it.copy(hasKey = true, balance = null, balanceNote = "Checking the key…") }
        c.appScope.launch {
            val r = trading.ping()
            _ui.update {
                it.copy(
                    hasKey = true,
                    balance = r.getOrNull()?.cash, portfolioValue = r.getOrNull()?.portfolioValue,
                    balanceNote = r.exceptionOrNull()?.let { e -> if (e is com.tjshea.vigilant.data.kalshitrade.KalshiApiException && e.isAuth) "Kalshi refused the key: ${e.detail ?: e.code ?: e.status}" else "Couldn't read the balance: ${e.message}" },
                )
            }
        }
    }

    // ---- arbitrage ------------------------------------------------------------------------------------------------

    /**
     * Tj, 2026-10-22 ("why is this only checking 6 novig lines"): in cloud mode this phone holds only the rows the GitHub list sends, but the engine's own Kalshi pass compares ALL the Novig lines it priced.
     * When its status is fresh the note says so, with its count and what it found.
     */
    private fun engineArbNote(st: com.tjshea.vigilant.data.kalshitrade.KalshiEngineStatus?): String {
        if (st == null || st.mode == KalshiAutoMode.OFF || st.priced == 0 || System.currentTimeMillis() - st.atMs > ENGINE_FRESH_MS) return ""
        val found = if (st.arbs.isEmpty()) "found none" else "found ${st.arbs.size}: " + st.arbs.take(2).joinToString("; ") { "${it.selectionNovig} + ${it.selectionKalshi} (${"%.1f".format(it.profit * 100)}%)" }
        return " The GitHub engine checked all ${st.priced} Novig lines it priced and $found."
    }

    /** Lists the sure profits between the Novig scan on screen and Kalshi's lines held by the last Kalshi scan. */
    suspend fun refreshArbs() {
        val ui = _ui.value
        if (!(ui.venues.kalshi && ui.venues.novig)) { _ui.update { it.copy(arbs = emptyList(), arbNote = "Arbitrage needs both Novig and Kalshi switched on.") }; return }
        val novig = c.runner.state.value.result
        val kalshi = scanner.heldSnapshots(KalshiClient.VENUE_ID)
        if (novig == null) { _ui.update { it.copy(arbs = emptyList(), arbNote = "Scan Novig first (the +EV tab): arbitrage compares its prices with Kalshi's.") }; return }
        if (kalshi.isEmpty()) { _ui.update { it.copy(arbs = emptyList(), arbNote = "Scan Kalshi first.") }; return }
        val arbs = withContext(Dispatchers.Default) { runCatching { KalshiArb.find(novig, kalshi, cfg().arbSettings, System.currentTimeMillis()) }.getOrDefault(emptyList()) }
        _ui.update {
            it.copy(
                arbs = arbs,
                arbNote = if (arbs.isEmpty()) "No sure profit between Novig and Kalshi right now (checked ${novig.opportunities.size} Novig prices against ${kalshi.sumOf { s -> s.events.size }} Kalshi games)." + engineArbNote(ui.engine) else "${arbs.size} found",
            )
        }
    }

    fun findArbs() { c.appScope.launch { refreshArbs() } }

    /** Places both legs of [arb] (Novig, then Kalshi), sized by the settings, and says what came of it. */
    fun placeArb(arb: ArbOpportunity, paper: Boolean = false) {
        c.appScope.launch {
            _ui.update { it.copy(arbBusy = true) }
            val note = try { runArb(arb, paper) } catch (e: CancellationException) { throw e } catch (e: Exception) { "Arbitrage failed: ${e.message ?: e.javaClass.simpleName}" }
            _ui.update { it.copy(arbBusy = false, arbNote = note) }
            refreshBets()
            refreshBalance()
            refreshArbs()
        }
    }

    private suspend fun runArb(arb: ArbOpportunity, paper: Boolean): String {
        val cfg = cfg()
        if (!cfg.tradingEnabled && !paper) return "Orders are off (Kalshi › Settings › Place orders on Kalshi)."
        val novigPlacer = c.autoBetPlacer() ?: return "Betting on Novig isn't set up (Settings › Betting & Novig account)."
        val row = arb.novigRow ?: return "This arbitrage has no Novig row."
        if (paper) return "Paper arbitrage: would buy ${arb.novig.selection} at Novig (${percent(arb.novig.price)}) and ${arb.kalshi.selection} at Kalshi (${percent(arb.kalshi.price)}) for ${percent(arb.profitPerDollar)} per \$1 — nothing sent."
        val executor = ArbExecutor(
            refresh = { refreshOne(it) },
            novig = { a, payout, maxCost -> novigLeg(novigPlacer, row, a, payout, maxCost) },
            kalshi = { a, contracts, maxPrice -> kalshiLeg(a, contracts, maxPrice) },
        )
        val out = executor.run(arb, cfg.arbMaxPayout, minOf(cfg.arbMaxPayout, CLIP), cfg.arbSettings)
        c.eventLog.info("KALSHI", "arbitrage ${arb.key}: $out")
        when (out) {
            is ArbOutcome.Done -> KalshiNotes.placed(c.app, "Kalshi arbitrage", "Locked in ${money(out.profit)} on ${money(out.payout)} of payout")
            is ArbOutcome.Unhedged -> KalshiNotes.failed(c.app, "Kalshi arbitrage: ONE LEG ONLY", "${money(out.payout)} held on ${out.venue} alone (${out.reason}). Check both accounts.")
            is ArbOutcome.Unknown -> KalshiNotes.failed(c.app, "Kalshi arbitrage: an order's answer was lost", out.message)
            is ArbOutcome.Skipped -> {}
        }
        return when (out) {
            is ArbOutcome.Done -> "Locked in ${money(out.profit)} on ${money(out.payout)} of payout (cost ${money(out.cost)})."
            is ArbOutcome.Skipped -> "Not placed: ${out.reason}."
            is ArbOutcome.Unhedged -> "ONE LEG ONLY: ${money(out.payout)} of payout is held on ${out.venue} alone (${out.reason}). Check both accounts and close it out."
            is ArbOutcome.Unknown -> "An order's answer was lost: ${out.message}"
        }
    }

    private suspend fun refreshOne(a: ArbOpportunity): ArbOpportunity? {
        val now = System.currentTimeMillis()
        val row = a.novigRow ?: return null
        val book = c.freshBook(a.market.marketId) ?: return null
        val level = book.takeLadder(a.market, a.novigOutcomeId).minByOrNull { it.price } ?: return null
        val fee = a.market.fee ?: return null
        val q = com.tjshea.vigilant.engine.EvMath.quote(0.5, level.price, fee, row.isLive)
        val tops = runCatching { trading.tops(listOf(a.kalshiTicker)) }.getOrNull()?.get(a.kalshiTicker) ?: return null
        if (!tops.tradable) return null
        val kp = tops.priceFor(a.kalshiSide) ?: return null
        val ks = tops.sizeFor(a.kalshiSide) ?: return null
        return a.copy(
            novig = a.novig.copy(price = level.price, fee = q.fee, payoutAvailable = level.contracts * 0.01, readAtMs = now),
            kalshi = a.kalshi.copy(price = kp, fee = KalshiFees.takerPerContract(kp), payoutAvailable = ks, readAtMs = now),
        )
    }

    private suspend fun novigLeg(p: com.tjshea.vigilant.data.novig.trading.ApiBetPlacer, row: Opportunity, a: ArbOpportunity, payout: Double, maxCost: Double): LegResult {
        val base = ApiBetTargets.of(row) ?: return LegResult.None("the Novig row has no fair price")
        val fair = 1.0 - a.kalshi.cost
        val target = base.copy(fair = fair, fairAsOfMs = System.currentTimeMillis(), source = "arb", placedKey = null, atBet = null, basis = null, auto = true, sharpAgainst = false)
        val s = c.settingsStore.flow.value ?: return LegResult.None("settings aren't loaded")
        val limits = BetLimits(
            maxStake = payout * a.novig.cost + 1.0, maxPerDay = s.apiMaxPerDay, minEv = (fair / maxCost - 1.0).coerceAtLeast(0.0), maxPerGame = 0.0,
            minEvWhere = "Kalshi tab › Arbitrage minimum profit",
        )
        val stake = payout * a.novig.cost
        return when (val r = p.placeAuto(target, stake, limits, a.novig.price)) {
            is PlaceResult.Placed -> LegResult.Filled((r.bet.contracts ?: 0L) * 0.01, r.bet.stake)
            is PlaceResult.NotFilled -> LegResult.None(r.reason)
            is PlaceResult.Refused -> LegResult.None(r.reason)
            is PlaceResult.Failed -> LegResult.None(r.message)
            is PlaceResult.Unconfirmed -> LegResult.Unknown(r.message)
        }
    }

    private suspend fun kalshiLeg(a: ArbOpportunity, contracts: Long, maxPrice: Double): LegResult {
        val id = "kalshi|${a.kalshiTicker}|${a.kalshiSide}"
        val outcome = NovigOutcome("$id|OUT", a.kalshi.selection, "TBD", BookRef(null, "${a.kalshiTicker}|${a.kalshiSide}", null))
        val market = NovigMarket("kalshi|${a.kalshiTicker}", a.event.eventId, a.market.marketType, "OPEN", a.market.description, a.event.startsTs, KalshiFees.TAKER, listOf(outcome))
        val target = BetTarget(
            market = market, outcomeId = outcome.outcomeId, league = a.event.league, eventName = a.event.description, startsTs = a.event.startsTs, marketLabel = a.label,
            selection = a.kalshi.selection, fair = 1.0 - a.novig.cost, fairAsOfMs = System.currentTimeMillis(), source = "arb", auto = true, book = KalshiBetPlacer.BOOK,
        )
        return when (val r = placer.placeExact(target, contracts, maxPrice)) {
            is PlaceResult.Placed -> LegResult.Filled((r.bet.contracts ?: 0L) * 0.01, r.bet.stake)
            is PlaceResult.NotFilled -> LegResult.None(r.reason)
            is PlaceResult.Refused -> LegResult.None(r.reason)
            is PlaceResult.Failed -> LegResult.None(r.message)
            is PlaceResult.Unconfirmed -> LegResult.Unknown(r.message)
        }
    }

    // ---- loop -----------------------------------------------------------------------------------------------------

    private fun start() {
        loop?.cancel()
        loop = c.appScope.launch {
            while (true) {
                delay(TICK_MS)
                val ui = _ui.value
                val now = System.currentTimeMillis()
                _ui.update { it.copy(hasKey = c.kalshiSigner() != null) }
                if (!ui.venues.kalshi) continue
                if (ui.config.autoScan && !ui.scanning && ui.config.scan.leagues.isNotEmpty() && now - lastScanStart >= ui.config.autoScanSeconds * 1000L) {
                    runCatching { scanNow() }
                }
                if (ui.config.scan.maker && ui.config.tradingEnabled && !ui.scanning && now - lastBidPass >= BID_EVERY_MS) runCatching { bidPass() }
                if (ui.config.engineMode != KalshiAutoMode.OFF && now - lastEnginePoll >= ENGINE_POLL_MS) { lastEnginePoll = now; runCatching { pollEngine() } }
                if (now - lastSettleAt >= SETTLE_EVERY_MS && c.kalshiSigner() != null) {
                    lastSettleAt = now
                    runCatching { refreshBets() }
                    refreshBalance()
                }
            }
        }
    }

    fun say(text: String) { _ui.update { it.copy(message = text) } }
    fun messageShown() { _ui.update { it.copy(message = null) } }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)
    private fun percent(p: Double) = String.format(Locale.US, "%.1f%%", p * 100)

    companion object {
        const val TICK_MS = 5_000L
        const val SETTLE_EVERY_MS = 3 * 60_000L
        const val BID_EVERY_MS = 60_000L
        const val PUSH_SETTLE_MS = 4_000L
        const val ENGINE_FRESH_MS = 10 * 60_000L
        const val ENGINE_POLL_MS = 45_000L

        /** The most payout one arbitrage clip holds on a single venue while its other leg is placed. */
        const val CLIP = 10.0
    }
}
