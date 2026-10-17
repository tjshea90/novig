package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.ApiChain
import com.tjshea.vigilant.data.scanner.LiveOnlyMode
import com.tjshea.vigilant.data.scanner.OddsScanner
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.BetSlider
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * The scan loop that runs on a GitHub runner (Tj, 2026-10-14: "put vigilant scans on GitHub runners ... the phone only sets the settings and retrieves the results").
 *
 * It does what the phone's scan does, with the phone's code: reads the settings the phone wrote ([config]), builds the fair-odds feeds that are switched on ([sources]), runs the [scanner] (Novig's
 * public API, every feed, the pricing), takes the feed (`ScanResult.feed(settings)`: the same filter as the phone's) and hands it to [publish]. Four rules:
 *  1. **Progressive.** While a long scan runs, what is priced so far (a partial result: only Novig prices read since the scan began, only leagues whose feeds all answered) is published every
 *     [partialEveryMs], so the list fills as the scan goes.
 *  2. **The phone's word is final.** The settings are read again every [configEveryMs]: a switch-off stops at once; a refresh the phone asked for (pull to refresh, Scan) starts a fresh scan and is acknowledged in the
 *     heartbeat; a setting that changes what is READ ([CloudScanKey]) starts the scan again (at most once in [restartEveryMs]); a setting that only changes how the list is filtered (the minimum edge, odds limits, order) is
 *     applied to the next list at once without a scan, and phone-only settings (auto-bet, bids' rules, the widget) change nothing here.
 *  3. **Off means off.** `on = false` publishes an idle heartbeat and calls no API; after [idleExitMs] of it the job ends (nothing spends a minute while the phone is asleep).
 *  4. **Live games are never here.** The cloud list is pregame: the 5-second live lane needs the phone's own sockets, and a hop through GitHub is slower than five seconds.
 *
 * Nothing in here places an order or holds a trading key: the engine's trading ([CloudTrader], TCL20d) is handed in through [trade] and lives in the JVM program.
 */
class CloudEngine(
    private val scanner: OddsScanner,
    private val sources: (ScanSettings) -> List<ReferenceSource>,
    private val config: suspend () -> CloudConfig?,
    /** Publishes the list and its heartbeat; returns why not, or null. */
    private val publish: suspend (CloudFeed?, CloudStatus) -> String?,
    private val onFeed: (List<Opportunity>, Long) -> Unit = { _, _ -> },
    private val engine: String = "github-actions",
    private val runId: String = "",
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val log: (String) -> Unit = {},
    private val partialEveryMs: Long = 20_000L,
    private val configEveryMs: Long = 3_000L,
    private val idleExitMs: Long = 10 * 60_000L,
    private val heartbeatEveryMs: Long = 25_000L,
    /** While a scan runs, a status (its progress) goes out at least this often. */
    private val statusEveryMs: Long = 10_000L,
    /** A refresh asked for within this long of a scan's start is served by that scan (it is already fresh); later it starts a new one. */
    private val refreshMinAgeMs: Long = 8_000L,
    /** At most one restart for changed settings in this long. */
    private val restartEveryMs: Long = 30_000L,
    private val sourceNames: (ScanSettings) -> List<String> = { s -> sources(s).map { it.id } },
    /**
     * The bid desk's candidate lines of a priced board under the phone's mirrored settings ([com.tjshea.vigilant.data.novig.trading.maker.BidCandidates.from]); null = this engine makes none. Only called
     * while the phone has Vigilant-priced bids on ([ScanSettings.bidsNeedLines]).
     */
    private val bidLines: (suspend (ScanResult, ScanSettings, Long) -> List<com.tjshea.vigilant.data.novig.trading.maker.BidCandidates.BidLine>)? = null,
    /** Publishes the candidate lines; null = the phone no longer prices bids from them, take the file away. */
    private val publishBids: suspend (CloudBids?) -> Unit = {},
    /** While a scan runs, a new candidate list goes out at most this often (a list is thousands of lines; a rule change sends one at once). */
    private val bidsEveryMs: Long = 45_000L,
    /** Called as each scan begins, with the settings it reads under: the engine's keyed Novig client and its websocket follow the phone's switches ([CloudNovig.configure]). */
    private val prepare: (ScanSettings) -> Unit = {},
    /** One line on how Novig is being read now ([CloudNovig.route]). */
    private val novigRoute: () -> String = { "" },
    private val novigAddress: () -> String = { "" },
    /** The tunnel address of the live status server, or blank ([CloudLive]); read each time a status is built. */
    private val liveUrl: () -> String = { "" },
    /** Ends the job early when Novig refuses this runner's address, so the workflow draws another ([AddressWatch]); null = never. */
    private val addressWatch: AddressWatch? = null,
    /**
     * The engine's trading (TCL20d, [CloudTrader]): called with each list that is published (partial ones too: their Novig prices are the freshest) and the phone's latest settings; returns a few words for the status
     * (or null when trading is off). A failure is a line in the log and never stops the scan.
     */
    private val trade: (suspend (List<Opportunity>, ScanSettings, Long) -> TradeStatus?)? = null,
    /**
     * Called with each scan that ran to its end and the phone's latest settings (TCL20e): the engine's own scan study logs every bet the scan listed ([com.tjshea.vigilant.data.study.ScanStudy.observeVigilant]), so the
     * record of what was listed, at what price and edge, does not depend on the phone being awake. A failure is a line in the log.
     */
    private val onResult: suspend (ScanResult, ScanSettings) -> Unit = { _, _ -> },
    /**
     * Reads the trading wallet's cash with the phone's latest settings; null = not applicable (trading is not live) or unreadable. Under [WALLET_MIN] the engine sleeps (no scan, no feed call, no order) and asks
     * again every [walletEveryMs] or when the phone asks for a refresh (Tj, 2026-10-15: pause when the wallet is under 50 cents, do not waste API use, check in now and then).
     */
    private val walletProbe: (suspend (ScanSettings) -> Double?)? = null,
    private val walletEveryMs: Long = WALLET_EVERY_MS,
    private val walletRetryMs: Long = WALLET_RETRY_MS,
    /** Rests silent APIs to save usage and asks the phone to turn a dead one off (TCL28b, [ApiGovernor]). */
    private val governor: ApiGovernor = ApiGovernor(),
    /** The engine's usage ledger and the keys it holds per meter, for [ApiAdvisor]. */
    private val usageBook: () -> com.tjshea.vigilant.data.keys.UsageBook = { com.tjshea.vigilant.data.keys.UsageBook() },
    private val keysOf: (com.tjshea.vigilant.data.keys.QuotaPolicy) -> List<String> = { emptyList() },
    /** Where a settings request for the phone goes ([CloudOverrides]) and where a message for Tj goes ([CloudMessages]); a failure is a line in the log. */
    private val overrideSink: suspend (CloudOverrides.Request) -> Unit = {},
    private val messageSink: suspend (CloudMessages.Message) -> Unit = {},
    /** The engine's bid desk (TCL31, [CloudBidRunner]); null = this engine makes no bids. */
    private val bids: EngineBids? = null,
    /** How often the bid desk runs between scans' lists (it reads fills, moves and posts bids on the lines it holds). */
    private val deskEveryMs: Long = 25_000L,
) {
    /** What the engine's trading says about itself in the heartbeat. */
    data class TradeStatus(val mode: String, val note: String, val stopped: String, val changedAtMs: Long, val cash: Double? = null, val equity: Double? = null)

    /** TIME_UP: the job's time is over; IDLE: the phone switched the engine off; ADDRESS: Novig refused this runner's address, the next job is started at once; WINDOW: the engine's hours (08:00-01:00 Eastern, [EngineHours]) are over or not yet begun: no hand-over, the schedule starts the next engine when they open. */
    enum class End { TIME_UP, IDLE, ADDRESS, WINDOW, RESET }

    @Volatile var passes = 0
        private set
    @Volatile var lastRows = 0
        private set
    @Volatile var lastScanMs = 0L
        private set
    @Volatile var problem: String? = null
        private set

    // What the heartbeat says about scans and requests (CloudStatus): kept here so every status, whoever publishes it, tells the same story.
    @Volatile private var handledRefresh = 0L
    @Volatile private var scanning = false
    @Volatile private var scanStartedAt = 0L
    @Volatile private var scanForRefresh = 0L
    @Volatile private var lastScanStartedAt = 0L
    @Volatile private var lastScanEndedAt = 0L
    @Volatile private var lastScanForRefresh = 0L
    @Volatile private var lastRestartAt = 0L
    /** The settings revision the running scan was priced under, and whether the phone has since changed something that only the NEXT scan applies. */
    @Volatile private var pricedRev = 0L
    @Volatile private var settingsPending = false
    /** What the last finished scan said about itself (Novig reads, throttling, each feed's answer), a few hundred characters for the heartbeat. */
    @Volatile private var scanNote = ""
    @Volatile private var advice: List<ApiAdvisor.Advice> = emptyList()
    @Volatile private var apiCards: List<ApiScorecard.Card> = emptyList()
    @Volatile private var contribution: Map<String, Pair<Int, Int>> = emptyMap()
    private val announced = HashMap<String, String>()
    @Volatile private var feedLines: List<FeedLine> = emptyList()
    private val feedWatch = FeedWatch()
    @Volatile private var feedAlerts: List<String> = emptyList()
    @Volatile private var feedLinesAt = 0L
    private val progress = AtomicReference<ScanProgress?>(null)
    @Volatile private var bidsAt = 0L
    @Volatile private var bidsCount = 0
    @Volatile private var bidsOn = false
    /** The bid desk's last word, the phone's settings as the desk reads them, and one pass at a time. */
    @Volatile private var deskStatus: BidStatus? = null
    @Volatile private var phoneSettings: ScanSettings? = null
    private val deskLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var addressEnd = false
    @Volatile private var windowEnd = false
    @Volatile private var lastPublished: CloudFeed? = null
    @Volatile private var lastAges = CloudCurrency.UNKNOWN
    @Volatile private var tradeStatus: TradeStatus? = null
    /** The trading wallet's cash and equity as last read (a trading pass or the wallet probe), for the phone's wallet line; null = never read. */
    @Volatile private var walletCash: Double? = null
    @Volatile private var walletEquity: Double? = null
    @Volatile private var walletReadAt = 0L
    @Volatile private var walletSleep = ""
    @Volatile private var walletCheckedAt = 0L
    /** When the balance is asked for next: [walletEveryMs] after a read, but only [WALLET_RETRY_MS] after one that gave nothing (trading not live yet, or Novig did not answer), so the switch to LIVE is not left unread for 30 minutes (Tj, 2026-10-15: "it says 0"). */
    @Volatile private var walletNextAt = 0L
    private var walletRefreshSeen = 0L

    /** Runs until [untilMs] (the job's time limit) or until the phone has had the engine off for [idleExitMs]. The bid desk runs beside the scan loop and comes down with it. */
    suspend fun run(untilMs: Long, startedAtMs: Long = clock()): End = kotlinx.coroutines.coroutineScope {
        val desk = if (bids != null) launch { deskLoop() } else null
        try {
            runLoop(untilMs, startedAtMs)
        } finally {
            desk?.cancel()
            // Whatever ends the job (time, the phone's switch, the engine's hours, a refused address), the bids the engine has up come down: nothing rests on Novig for a desk that is no longer watching.
            if (bids != null) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { deskDown("the engine is ending", force = true) }
        }
    }

    /** The desk beside the scan: every [deskEveryMs] one pass on the lines it holds (fills read, bids moved and posted), under the phone's newest settings, only inside the engine's hours. */
    /** When the engine's own loops last turned over (the run loop and the scan wait loop each tick about once a second): a thread outside the coroutines watches it ([CloudMain] loop watchdog, TCL76d). */
    @Volatile var tickMs: Long = 0L

    /** The phone's config with an off that has reached its scheduled resume counted as on ([CloudManual.effective]). */
    private suspend fun readConfig(): CloudConfig? = config()?.let { CloudManual.effective(it, clock()) }

    /** The newest config the run loop read: the manual Run / Stop fields the other loops (the desk) also honour ([CloudManual]). */
    @Volatile private var manualCfg: CloudConfig? = null
    private fun manualOpen(s: ScanSettings) = CloudManual.open(s.cloudHours, manualCfg, clock())
    private fun manualStopped() = manualCfg?.let { CloudManual.stopActive(it, clock()) } == true

    private suspend fun deskLoop() {
        while (currentScopeActive()) {
            pause(deskEveryMs)
            val s = phoneSettings ?: continue
            if (!s.cloudDrives || !manualOpen(s) || manualStopped()) { deskDown("the engine is off or outside its hours"); continue }
            deskPass(s)
        }
    }

    private suspend fun deskPass(s: ScanSettings) {
        val b = bids ?: return
        deskLock.withLock {
            val st = try { b.pass(s, clock()) } catch (e: CancellationException) { throw e } catch (e: Exception) { log("bid desk failed: ${e.javaClass.simpleName}: ${e.message?.take(120)}"); null }
            deskStatus = st
        }
    }

    private suspend fun deskDown(why: String, force: Boolean = false) {
        val b = bids ?: return
        deskLock.withLock {
            if (force || deskStatus?.active == true) { runCatching { b.stop(why) }.onFailure { if (it is CancellationException) throw it }; log("bids: down ($why)") }
            deskStatus = null
        }
    }

    private suspend fun runLoop(untilMs: Long, startedAtMs: Long): End {
        var offSince = 0L
        var lastCfg: CloudConfig? = null
        var lastBeat = 0L
        var resetBaseline = -1L
        while (currentScopeActive() && clock() < untilMs) {
            tickMs = clock()
            val cfg = runCatching { readConfig() }.getOrNull() ?: lastCfg
            lastCfg = cfg
            cfg?.settings?.let { phoneSettings = it }
            cfg?.let { manualCfg = it }
            if (cfg != null) {
                if (resetBaseline < 0L) resetBaseline = cfg.resetAtMs
                if (cfg.resetAtMs > resetBaseline) {
                    log("reset requested from the phone: ending so a new runner starts")
                    problem = publish(null, status(cfg.rev, startedAtMs, untilMs, emptyList(), idle = true))
                    return End.RESET
                }
            }
            if (cfg == null || !cfg.on) {
                if (offSince == 0L) offSince = clock()
                if (clock() - lastBeat >= heartbeatEveryMs) {
                    problem = publish(null, status(cfg?.rev ?: 0L, startedAtMs, untilMs, emptyList(), idle = true))
                    lastBeat = clock()
                }
                if (clock() - offSince >= idleExitMs) return End.IDLE
                pause(10_000L)
                continue
            }
            offSince = 0L
            if (CloudManual.stopActive(cfg, clock())) {
                log("stopped by Tj until the next scheduled opening: ending")
                problem = publish(null, status(cfg.rev, startedAtMs, untilMs, emptyList(), idle = true))
                return End.WINDOW
            }
            if (!CloudManual.open(cfg.settings.cloudHours, cfg, clock())) {
                log("outside the engine's hours (08:00-01:00 Eastern): ending")
                problem = publish(null, status(cfg.rev, startedAtMs, untilMs, emptyList(), idle = true))
                return End.WINDOW
            }
            if (walletSleeping(cfg)) {
                if (clock() - lastBeat >= heartbeatEveryMs) {
                    problem = publish(null, status(cfg.rev, startedAtMs, untilMs, emptyList(), idle = true))
                    lastBeat = clock()
                }
                pause(10_000L)
                continue
            }
            val restarted = scanOnce(cfg, startedAtMs, untilMs)
            lastBeat = clock()
            if (windowEnd) {
                problem = publish(null, status(cfg.rev, startedAtMs, untilMs, emptyList(), idle = true))
                return End.WINDOW
            }
            if (addressEnd) {
                // the last word goes out before the job ends: the phone shows why, and the next job reads the streak from it
                problem = publish(null, status(cfg.rev, startedAtMs, untilMs, emptyList(), idle = false))
                return End.ADDRESS
            }
            // A scan cut short by the phone's word (a refresh, new settings) starts again at once; one that ran to its end waits the gap.
            if (!restarted) pause(if (novigWatch.active) novigWatch.backoffMs() else (cfg.passGapSeconds.coerceIn(5, 600) * 1000L))
        }
        return End.TIME_UP
    }

    /**
     * True while the live wallet is under [WALLET_MIN]. One balance read at the start, then every [walletEveryMs] while asleep (and every [walletEveryMs] while awake, so a drained wallet is noticed even with no
     * candidate bet), or at once when the phone asks for a refresh. An unreadable balance changes nothing: asleep stays asleep, awake stays awake.
     */
    private suspend fun walletSleeping(cfg: CloudConfig): Boolean {
        val probe = walletProbe ?: return false
        val t = clock()
        val asked = cfg.refreshAtMs > walletRefreshSeen
        if (walletCheckedAt != 0L && t < walletNextAt && !(asked && walletSleep.isNotEmpty())) return walletSleep.isNotEmpty()
        walletRefreshSeen = maxOf(walletRefreshSeen, cfg.refreshAtMs)
        walletCheckedAt = t
        val cash = runCatching { probe(cfg.settings) }.getOrNull()
        walletNextAt = t + if (cash == null) minOf(walletEveryMs, walletRetryMs) else walletEveryMs
        if (cash == null) return walletSleep.isNotEmpty()
        if (walletEquity == null || walletEquity!! < cash) walletEquity = cash
        walletCash = cash; walletReadAt = t
        val was = walletSleep.isNotEmpty()
        walletSleep = if (cash >= WALLET_MIN) "" else
            "the trading wallet has ${usd(cash)}, under ${usd(WALLET_MIN)}: no scans or feed calls until it is funded (balance re-read every ${walletEveryMs / 60_000} min, or when you pull to refresh)"
        if (was != walletSleep.isNotEmpty()) log(if (walletSleep.isEmpty()) "wallet funded again (${usd(cash)}): scanning resumes" else "wallet under ${usd(WALLET_MIN)}: the engine sleeps")
        return walletSleep.isNotEmpty()
    }

    private fun usd(x: Double) = String.format(java.util.Locale.US, "$%.2f", x)

    private suspend fun currentScopeActive(): Boolean = currentCoroutineContext().isActive

    /**
     * One scan under the phone's settings, publishing partial lists as it goes. Returns true when it was cut short because the phone asked for something (a refresh, settings that change what is read, off), so the loop
     * starts the next one without waiting.
     */
    private suspend fun scanOnce(cfg0: CloudConfig, startedAtMs: Long, untilMs: Long): Boolean = supervisorScope {
        val t0 = clock()
        var cfg = cfg0
        // The settings the scan READS under stay as they were when it began; the list is filtered under the newest ones (a raised minimum edge shows at once, no scan is thrown away for it).
        val scanSettings = governor.apply(forCloud(cfg0.settings), clock())
        runCatching { prepare(scanSettings) }.onFailure { log("could not prepare the scan's connections: ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
        val fetchKey = CloudScanKey.fetchKey(cfg0.settings)
        val scanKey = CloudScanKey.scanKey(cfg0.settings)
        var filterSettings = scanSettings
        pricedRev = cfg0.rev
        settingsPending = false
        val partial = AtomicReference<ScanResult?>(null)
        val feedSources = sources(scanSettings)
        val names = sourceNames(scanSettings)
        // This scan serves the newest refresh request the phone has made so far: acknowledged now.
        handledRefresh = maxOf(handledRefresh, cfg0.refreshAtMs)
        scanForRefresh = cfg0.refreshAtMs
        scanning = true
        scanStartedAt = t0
        progress.set(ScanProgress("starting"))
        val job = async { scanner.scan(scanSettings, feedSources, emptySet(), onProgress = { progress.set(it) }, onPartial = { partial.set(it) }) }
        var lastPublishedAt = 0L
        var lastBidsAt = 0L
        var lastResult: ScanResult? = null
        var latest = cfg0.settings
        var bidKey = CloudScanKey.key(cfg0.settings, CloudScanKey.Kind.BIDS)
        var lastConfigAt = t0
        var lastWatchLogAt = 0L
        var lastStatusAt = t0
        var restartReason: String? = null
        // The first status goes out at once: it is the engine's acknowledgement of the request.
        problem = publish(null, status(cfg.rev, startedAtMs, untilMs, names, idle = false))
        while (job.isActive) {
            pause(1_000L)
            val now = clock()
            tickMs = now
            // The engine's hours end: stop the scan; the job ends with End.WINDOW (nothing orders after this; TCL20d cancels what rests first).
            val mc = manualCfg ?: cfg
            if (!CloudManual.open(mc.settings.cloudHours, mc, now) || CloudManual.stopActive(mc, now)) {
                windowEnd = true
                restartReason = "engine hours over"
                log("the engine's hours (08:00-01:00 Eastern) are over: this job ends")
                job.cancel(CancellationException(restartReason))
                break
            }
            // Novig refuses this runner's address: stop, publish what there is, and let the workflow draw another runner (AddressWatch).
            if (addressWatch != null && now - lastWatchLogAt >= 60_000L && now - t0 >= 30_000L) { lastWatchLogAt = now; log(addressWatch.describe(now)) }
            if (addressWatch?.shouldHandOver(now) == true) {
                addressEnd = true
                restartReason = "Novig refused this runner's address"
                log("Novig refused this runner's address: handing over to a new runner (#${addressWatch.count} in a row)")
                job.cancel(CancellationException(restartReason))
                break
            }
            // A scan whose progress has not moved for PROGRESS_STALL_MS is frozen (Tj, 2026-10-17: "Stalled ... Novig prices 8378 of 11479" for minutes with nothing in the log): start it again (the Novig books
            // already read are cached, so the new scan goes on from there), at most once in PROGRESS_RESTART_GAP_MS so a Novig outage does not turn into a restart loop.
            if (progressChangedAt > 0L && now - progressChangedAt >= PROGRESS_STALL_MS && now - lastRestartAt >= PROGRESS_RESTART_GAP_MS) {
                restartReason = "no progress for ${(now - progressChangedAt) / 1000}s at ${progressSig ?: "?"}"
                log("scan frozen: $restartReason; Novig route: ${runCatching(novigRoute).getOrDefault("")}")
                job.cancel(CancellationException(restartReason))
                break
            }
            if (now - lastPublishedAt >= partialEveryMs) {
                lastPublishedAt = now
                partial.getAndSet(null)?.let { r ->
                    val rows = r.feed(filterSettings)
                    val feed = CloudFeed(now, cfg.rev, r.stats, rows)
                    onFeed(rows, now)
                    lastPublished = feed; lastAges = CloudCurrency.of(rows, now)
                    lastResult = r
                    // The bid desk's candidates ride with the list, at most every [bidsEveryMs] (before the status, so it says what was sent).
                    if (now - lastBidsAt >= bidsEveryMs) { lastBidsAt = now; if (withTimeoutOrNull(WATCH_STEP_MS) { sendBids(r, cfg.rev, latest, now) } == null) log("watch loop: the bid candidates took over ${WATCH_STEP_MS / 1000}s: skipped this round") }
                    // The mid-scan passes re-read the stale books too (2026-10-16: 64 of 79 listed bets were refused as "no Novig price read in the last 3 minutes" because only the end-of-scan pass did; a scan takes 5+ minutes).
                    sendTrades(withTimeoutOrNull(WATCH_STEP_MS) { freshForTrading(rows, latest, now) } ?: rows.also { log("watch loop: the re-read before judging took over ${WATCH_STEP_MS / 1000}s: the stale prices are refused") }, latest, now)
                    problem = publish(feed, status(cfg.rev, startedAtMs, untilMs, names, idle = false, scanMs = now - t0))
                    lastStatusAt = now
                }
            }
            // The heartbeat carries the scan's progress: a long first scan has no list for minutes (a league is listed only when every feed has answered it), and the phone shows how far it is.
            if (now - lastStatusAt >= statusEveryMs) {
                lastStatusAt = now
                problem = publish(null, status(cfg.rev, startedAtMs, untilMs, names, idle = false, scanMs = now - t0))
            }
            if (now - lastConfigAt >= configEveryMs) {
                lastConfigAt = now
                val newer = runCatching { readConfig() }.getOrNull()
                // Nothing to decide unless the phone wrote something new (a revision, a switch-off, a refresh): the settings are compared only then.
                if (newer != null && (newer.rev != cfg.rev || !newer.on || newer.refreshAtMs > handledRefresh)) {
                    when {
                        !newer.on -> { restartReason = "switched off"; job.cancel(CancellationException(restartReason)); break }
                        // A refresh asked for after this scan began wants a scan that began after it (the scan just begun already is one).
                        newer.refreshAtMs > handledRefresh && now - t0 >= refreshMinAgeMs -> { restartReason = "refresh requested"; job.cancel(CancellationException(restartReason)); break }
                        newer.refreshAtMs > handledRefresh -> { handledRefresh = newer.refreshAtMs; scanForRefresh = newer.refreshAtMs }
                        // Settings that change what is READ (a league, a feed, days ahead...) start the scan again, at most once in restartEveryMs; the rest (the minimum edge, odds limits, order, every
                        // phone-only setting) only change how the finished list is filtered.
                        CloudScanKey.fetchKey(newer.settings) != fetchKey && now - lastRestartAt >= restartEveryMs -> { restartReason = "settings changed"; job.cancel(CancellationException(restartReason)); break }
                        else -> if (newer.rev != cfg.rev) {
                            cfg = cfg.copy(rev = newer.rev)
                            filterSettings = CloudScanKey.withFilters(scanSettings, newer.settings)
                            settingsPending = CloudScanKey.scanKey(newer.settings) != scanKey
                            latest = newer.settings
                            phoneSettings = newer.settings
                            // A bid rule changed: the candidates are made again from what is held, at once, with no scan.
                            val k = CloudScanKey.key(newer.settings, CloudScanKey.Kind.BIDS)
                            if (k != bidKey) { bidKey = k; lastResult?.let { r -> lastBidsAt = now; sendBids(r, cfg.rev, latest, now) } }
                        }
                    }
                }
            }
        }
        if (restartReason != null) {
            log("scan cut short ($restartReason) after ${(clock() - t0) / 1000}s: " + if (addressEnd || windowEnd) "this job ends" else "starting over")
            if (restartReason != "switched off") lastRestartAt = clock()
            scanning = false
            return@supervisorScope restartReason != "switched off"
        }
        val report = try {
            job.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = "scan failed: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
            log(problem!!)
            scanning = false
            publish(null, status(cfg.rev, startedAtMs, untilMs, names, idle = false))
            return@supervisorScope false
        }
        scanning = false
        novigWatch.observe(report.errors, clock())?.let { ev -> novigOutage(ev) }
        scanNote = describe(report)
        log("scan report: $scanNote")
        feedLines = report.sources.map { FeedLine(it.id, it.name, it.fetched, it.reused, it.matched, it.standingBy, it.skipped, it.error?.take(120), it.heldBack?.take(120)) }
        feedLinesAt = clock()
        feedAlerts = feedWatch.observe(feedLines).also { if (it != feedAlerts && it.isNotEmpty()) log("silent feeds: " + it.joinToString(", ")) }
        contribution = runCatching { ApiScorecard.contribution(report.result, clock()) }.getOrDefault(emptyMap())
        govern(latest, feedLinesAt)
        val result = report.result
        if (result == null) {
            problem = report.errors.firstOrNull()?.take(160) ?: "the scan returned nothing"
            log("no result: $problem")
            publish(null, status(cfg.rev, startedAtMs, untilMs, names, idle = false))
            return@supervisorScope false
        }
        val done = clock()
        val rows = result.feed(filterSettings)
        val feed = CloudFeed(done, cfg.rev, result.stats, rows)
        passes++
        lastRows = rows.size
        lastScanMs = done - t0
        lastScanStartedAt = t0; lastScanEndedAt = done; lastScanForRefresh = scanForRefresh
        lastPublished = feed; lastAges = CloudCurrency.of(rows, done)
        onFeed(rows, done)
        sendBids(result, cfg.rev, latest, done)
        sendTrades(freshForTrading(rows, latest, done), latest, done)
        runCatching { onResult(result, forCloud(latest)) }.onFailure { if (it is CancellationException) throw it; log("scan study failed: ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
        problem = publish(feed, status(cfg.rev, startedAtMs, untilMs, names, idle = false, scanMs = lastScanMs))
        log("pass $passes: ${rows.size} bets listed of ${result.stats.outcomesWithFair} priced, ${lastScanMs / 1000}s" + (problem?.let { ", publish problem: $it" } ?: ""))
        if (bids != null) deskPass(latest)
        false
    }

    /** When the scan's progress (step and count) last CHANGED, for the stall check (the phone's STALLED chip, the watchdog): 0 when no scan is running. */
    private var progressSig: Pair<String?, Int?>? = null
    private var progressChangedAt = 0L

    private class StatusArgs(val rev: Long, val startedAtMs: Long, val untilMs: Long, val names: List<String>, val idle: Boolean)
    @Volatile private var lastStatusArgs: StatusArgs? = null

    /** The status as it is RIGHT NOW (heartbeat, scan progress, ticks), rebuilt from the engine's memory with the last arguments a published status used: what the live status server hands out every second (TCL80). */
    fun liveStatus(): CloudStatus? = lastStatusArgs?.let { runCatching { status(it.rev, it.startedAtMs, it.untilMs, it.names, it.idle) }.getOrNull() }

    private fun status(rev: Long, startedAtMs: Long, untilMs: Long, names: List<String>, idle: Boolean, scanMs: Long = lastScanMs): CloudStatus {
        lastStatusArgs = StatusArgs(rev, startedAtMs, untilMs, names, idle)
        val p = progress.get()
        if (scanning && !idle) {
            val sig = p?.step to p?.done
            if (sig != progressSig || progressChangedAt == 0L) { progressSig = sig; progressChangedAt = clock() }
        } else { progressSig = null; progressChangedAt = 0L }
        val feed = lastPublished
        return CloudStatus(
            engine = engine, runId = runId, computedAtMs = feed?.computedAtMs ?: 0L, startedAtMs = startedAtMs, heartbeatMs = clock(), scanMs = scanMs,
            rows = feed?.rows?.size ?: lastRows, configRev = rev, sources = names, passes = passes, problem = problem, idle = idle, handoverAtMs = untilMs,
            scanning = scanning && !idle, progressChangedAtMs = progressChangedAt, scanStartedAtMs = scanStartedAt, progressStep = if (scanning) p?.step.orEmpty() else "", progressDone = if (scanning) p?.done ?: 0 else 0, progressTotal = if (scanning) p?.total ?: 0 else 0,
            ackedRefreshAtMs = handledRefresh, scanRefreshAtMs = scanForRefresh, lastScanStartedAtMs = lastScanStartedAt, lastScanEndedAtMs = lastScanEndedAt, lastScanForRefreshAtMs = lastScanForRefresh,
            novigAgeMedianS = lastAges.novigMedianS, novigAgeMaxS = lastAges.novigMaxS, fairAgeMedianS = lastAges.fairMedianS, fairAgeMaxS = lastAges.fairMaxS,
            pricedConfigRev = pricedRev, settingsPending = settingsPending, scanNote = scanNote, bidsAtMs = bidsAt, bidsCount = bidsCount,
            novigRoute = runCatching(novigRoute).getOrDefault(""), novigAddress = runCatching(novigAddress).getOrDefault(""), liveUrl = runCatching(liveUrl).getOrDefault(""), ranking = runCatching { scanner.sourceRanking() }.getOrNull().orEmpty().take(1_500),
            addressHandovers = addressWatch?.count ?: 0, addressHandoverAtMs = addressWatch?.atMs ?: 0L,
            tradeMode = tradeStatus?.mode.orEmpty(), tradeNote = tradeStatus?.note.orEmpty().take(400), tradeStopped = tradeStatus?.stopped.orEmpty().take(200), tradesAtMs = tradeStatus?.changedAtMs ?: 0L,
            walletSleep = walletSleep, walletCheckedAtMs = walletCheckedAt, walletCash = walletCash ?: -1.0, walletEquity = walletEquity ?: -1.0, walletReadAtMs = walletReadAt,
            feedReport = feedLines, feedReportAtMs = feedLinesAt, feedAlerts = feedAlerts,
            advice = advice, apiCards = apiCards, governorOff = governor.askedOff().sorted(),
            deskActive = !idle && deskStatus?.active == true, deskNote = deskStatus?.note.orEmpty().take(500), deskResting = deskStatus?.resting ?: 0, deskRestingDollars = deskStatus?.restingDollars ?: 0.0,
            deskAtMs = deskStatus?.atMs ?: 0L, deskStopped = deskStatus?.stopped.orEmpty().take(200),
            resting = governor.resting(clock()).map { r -> "${ApiToggles.name(r.id)} until ${java.time.Instant.ofEpochMilli(r.untilMs).atZone(java.time.ZoneOffset.UTC).toLocalTime().withSecond(0).withNano(0)} UTC: ${r.reason}" },
        )
    }

    /**
     * Publishes the bid desk's candidate lines of [result] when the phone has Vigilant-priced bids on; once, null when it stops (so the file does not linger). A failure is a line in the log: the
     * Feed's list does not depend on it.
     */
    private suspend fun sendBids(result: ScanResult, rev: Long, settings: ScanSettings, now: Long) {
        val make = bidLines ?: return
        val s = forCloud(settings)
        // The engine's own desk takes the candidates straight (no file, no phone in the loop): TCL31.
        val desk = bids
        if (s.engineBids && desk != null) {
            runCatching {
                val lines = make(result, s, now)
                desk.put(CloudBids(now, result.partial, rev, lines))
                if (bidsOn) { bidsOn = false; publishBids(null) }
                bidsAt = now; bidsCount = lines.size
            }.onFailure { if (it is CancellationException) throw it; log("candidate lines failed: ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
            return
        }
        // The engine runs only while the phone's switch is on and not paused ([ScanSettings.cloudDrives]), so what is left to ask is whether the bid desk wants lines from Vigilant's scan.
        if (!s.bidsNeedLines) {
            if (bidsOn) { bidsOn = false; bidsAt = 0L; bidsCount = 0; runCatching { publishBids(null) } }
            return
        }
        runCatching {
            val lines = make(result, s, now)
            publishBids(CloudBids(now, result.partial, rev, lines))
            bidsOn = true; bidsAt = now; bidsCount = lines.size
        }.onFailure { if (it is CancellationException) throw it; log("candidate lines failed: ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
    }

    /**
     * The listed bets whose Novig book was read more than [TRADE_RECHECK_AFTER_MS] ago are read AGAIN now, before the trader judges them (Tj, 2026-10-15: "I haven't seen the GitHub engine do any auto bets at all"):
     * a scan reads ~12,000 books over 5 to 6 minutes, so by its end most listed bets carried a price older than the trader's 3-minute bar and 27 of 31 were refused as "no Novig price read in the last 3 minutes".
     * One recheck of at most 40 books (a couple of seconds on the key) makes their price and depth current; a bet whose edge has gone after the re-read leaves the list instead of being ordered on a stale price,
     * and a failed re-read changes nothing (the trader still refuses a stale price). Only while the engine trades (paper or live).
     */
    private suspend fun freshForTrading(rows: List<Opportunity>, settings: ScanSettings, now: Long): List<Opportunity> {
        if (trade == null || settings.gates.cloudTrading == com.tjshea.vigilant.data.scanner.CloudTradeMode.OFF || rows.isEmpty()) return rows
        val stale = rows.filter { now - (it.bookFetchedAtMs ?: 0L) > TRADE_RECHECK_AFTER_MS }
        if (stale.isEmpty()) return rows
        val ids = stale.map { it.market.marketId }.distinct().take(com.tjshea.vigilant.data.scanner.Scanner.MAX_RECHECK)
        // requoteListed, not recheck: recheck takes the scanner's mutex, which a running scan holds for its whole 5 to 7 minutes (2026-10-17: every mid-scan re-read waited it out, froze this loop and left 10 bets refused as "no price in 3 minutes").
        val fresh = try {
            scanner.requoteListed(forCloud(settings), stale.filter { it.market.marketId in ids }).associateBy { it.key }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("trading: the re-read before judging failed: ${e.javaClass.simpleName}")
            return rows
        }
        val staleKeys = stale.mapTo(HashSet()) { it.key }
        val out = rows.mapNotNull { r -> if (r.key in staleKeys) fresh[r.key] else r }
        log("trading: re-read ${ids.size} books before judging: ${out.count { it.key in staleKeys }} bets re-priced, ${stale.size - out.count { it.key in staleKeys }} left out (no fresher price)")
        return out
    }

    /** Novig's server errors across scans (TCL84): a short retry wait, one message when it lasts, one when it ends. */
    private val novigWatch = NovigErrorWatch()

    private suspend fun novigOutage(ev: NovigErrorWatch.Event) {
        val now = clock()
        when (ev) {
            NovigErrorWatch.Event.ALERT -> {
                log("ALERT: Novig has answered with server errors for ${novigWatch.outageMs(now) / 60_000L} min (${novigWatch.last}): retrying every ${novigWatch.backoffMs() / 1000}s")
                say(CloudMessages.Message("novig-5xx-$now", now, "engine", "alert", "Novig is failing (server errors)", "Novig's servers have answered with errors for ${novigWatch.outageMs(now) / 60_000L} minutes (${novigWatch.last}). The engine keeps retrying with a short wait and starts reading again by itself the moment Novig answers; the address and keys are fine. Nothing to do unless it lasts for hours.", 1, null))
            }
            NovigErrorWatch.Event.RECOVERED -> {
                log("Novig is answering again")
                say(CloudMessages.Message("novig-ok-$now", now, "engine", "info", "Novig is answering again", "The server errors have stopped and the engine is scanning normally.", 0, null))
            }
        }
    }

    /** Says something to Tj in the phone's Messages (words only: nothing that looks like a key goes out). */
    private suspend fun say(m: CloudMessages.Message) {
        if (CloudMessages.looksSecret(m.title + " " + m.body)) return
        runCatching { messageSink(m) }.onFailure { if (it is CancellationException) throw it; log("message to Tj not sent: ${it.javaClass.simpleName}") }
    }

    /**
     * After each finished scan (TCL28): the governor looks at what each API answered and rests or switches off one that is silent; the advisor works out what Tj should refill, buy, fix or switch off, and
     * anything new is said in the Messages once a day. [phone] is the phone's latest settings (what is switched on there), never the engine's resting copy.
     */
    private suspend fun govern(phone: ScanSettings, now: Long) {
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        for (e in governor.observe(feedLines, phone, now)) when (e) {
            is ApiGovernor.Event.Rested -> {
                log("API governor: resting ${ApiToggles.name(e.id)} for ${(e.untilMs - now) / 60_000L} min (silent, strike ${e.strikes})")
                say(CloudMessages.Message("gov-rest-${e.id}-$now", now, "engine", "info", "Resting ${ApiToggles.name(e.id)} to save usage", "It answered nothing for ${ApiGovernor.LIMIT} scans in a row, so the engine scans without it for ${(e.untilMs - now) / 3_600_000L.coerceAtLeast(1)} h, then asks it once more (strike ${e.strikes} of ${ApiGovernor.REST_MS.size}).", 0, null))
            }
            is ApiGovernor.Event.Resumed -> log("API governor: asking ${ApiToggles.name(e.id)} again")
            is ApiGovernor.Event.TurnedOff -> {
                log("API governor: asking the phone to turn ${ApiToggles.name(e.id)} off (silent after every rest)")
                runCatching { overrideSink(CloudOverrides.Request("gov-off-${e.id}-$now", now, "engine", "silent after every rest", mapOf(e.id to false))) }.onFailure { if (it is CancellationException) throw it; log("override not sent: ${it.javaClass.simpleName}") }
                say(CloudMessages.Message("gov-off-${e.id}-$now", now, "engine", "alert", "Switched ${ApiToggles.name(e.id)} off", "It stayed silent after three rests, so the engine asked the phone to turn it off (it was costing requests for nothing). Fix its key or plan, then switch it back on in Settings › APIs.", 1, "APIS"))
            }
        }
        // The smart manager (TCL61): paid plans win freshness ties, a paid monthly plan ahead of the calendar is asked as a second opinion, and the scorecard goes to the phone.
        val book = usageBook()
        val keyed = { id: String -> ApiToggles.policy(id)?.let { keysOf(it).isNotEmpty() } ?: true }
        ApiScorecard.observe(feedLines)
        val paid = ApiScorecard.paidProviders(book, keyed)
        val surplus = ApiScorecard.surplus(book, paid, now)
        ApiChain.paidProviders = paid.mapNotNull { ApiToggles.policy(it)?.id }.toSet()
        ApiChain.surplusProviders = surplus.mapNotNull { ApiToggles.policy(it)?.id }.toSet()
        apiCards = ApiScorecard.cards(phone, book, ApiChain.measures, feedLines, governor.resting(now).map { it.id }.toSet(), keyed, now, contribution)
        advice = ApiAdvisor.advise(book, feedLines, feedAlerts, phone, keysOf, now) + ApiAdvisor.manage(apiCards, book, keysOf, phone, now)
        for (a in advice) {
            if (announced[a.id] == day) continue
            announced[a.id] = day
            say(CloudMessages.Message("adv-${a.id}-$day", now, "engine", "recommendation", a.title, a.body, if (a.severity >= 2) 1 else 0, "APIS"))
        }
        announced.keys.retainAll(advice.map { it.id }.toSet())
    }

    /** Hands the published list to the engine's trading. It only ever runs on the engine's own time inside the scan loop; a failure is a line in the log. */
    private suspend fun sendTrades(rows: List<Opportunity>, settings: ScanSettings, now: Long) {
        val t = trade ?: return
        runCatching { t(rows, settings, now) }.onSuccess {
            tradeStatus = it ?: tradeStatus?.copy(mode = "off", note = "")
            // a pass that reached the wallet read says the cash and the equity (cash plus what is at risk); one that stopped earlier says 0 and 0, which is "not read", not "empty"
            if (it?.cash != null && (it.cash > 0.0 || (it.equity ?: 0.0) > 0.0)) { walletCash = it.cash; walletEquity = it.equity; walletReadAt = now }
        }
            .onFailure { if (it is CancellationException) throw it; log("trading pass failed: ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
    }

    /** A scan's own account of itself, short: what Novig gave, whether it throttled, and what each feed said (errors included). Never a key. */
    private fun describe(r: com.tjshea.vigilant.data.scanner.ScanReport): String = buildString {
        // Where the scan's time went (Tj, 2026-10-15: "see if there is any way to speed up scanning"): the board, each feed's finish, the Novig prices per second by route, what was left too late.
        // How far from +EV the priced lines are (the numbers that say whether re-reading the hopeless ones is worth a read): +EV, within 2%, 2-5%, 5-10%, further.
        r.result?.opportunities?.mapNotNull { it.evPercent }?.takeIf { it.isNotEmpty() }?.let { evs ->
            append("EV spread of ${evs.size} priced lines: +EV ${evs.count { it > 0 }} · 0 to -2% ${evs.count { it <= 0 && it > -0.02 }} · -2 to -5% ${evs.count { it <= -0.02 && it > -0.05 }} · -5 to -10% ${evs.count { it <= -0.05 && it > -0.10 }} · below -10% ${evs.count { it <= -0.10 }}; ")
        }
        r.timing?.let { t -> append(com.tjshea.vigilant.data.scanner.ScanTiming.text(t, r.booksFetched + r.booksNotModified, r.booksViaKey, r.booksViaPush)).append("; ") }
        append("Novig books: ${r.booksFetched} read, ${r.booksNotModified} unchanged, ${r.booksFromCache} from cache, ${r.booksTooLate} left out as too late")
        r.retryAfterSeconds?.let { append("; Novig asked us to slow down ${it}s") }
        if (r.sources.isNotEmpty()) append("; feeds: " + r.sources.joinToString(", ") { "${it.id} ${it.fetched}+${it.reused}" + (it.error?.let { e -> " ERR ${e.take(50)}" } ?: "") })
        if (r.errors.isNotEmpty()) append("; errors: " + r.errors.take(3).joinToString(" | ") { it.take(80) })
    }.take(1_200)

    companion object {
        /** A scan whose progress has not moved for this long is started again, at most once in the gap. */
        const val PROGRESS_STALL_MS = 150_000L
        /** A read-only step of the watch loop (the bid candidates, the re-read before judging) that takes longer is dropped for this round: 2026-10-17 a hung step froze the loop's tick and the stall watchdog restarted a healthy engine every 7.5 min. */
        const val WATCH_STEP_MS = 60_000L
        const val PROGRESS_RESTART_GAP_MS = 300_000L
        /** The trading wallet below this (Tj, 2026-10-15: "any amount under 50 cents") puts the engine to sleep; it is read again this often. */
        const val WALLET_MIN = 0.50
        const val WALLET_RETRY_MS = 60_000L
        /** A listed bet whose Novig book is older than this is read again before the trader judges it (the trader's own bar is 3 minutes, [CloudTrader.FRESH_PRICE_MS]). */
        const val TRADE_RECHECK_AFTER_MS = 45_000L
        const val WALLET_EVERY_MS = 30 * 60_000L

        /** The settings the cloud scans under: the phone's, with the parts that only make sense on the phone switched off. */
        fun forCloud(s: ScanSettings): ScanSettings = s.copy(
            scanner = ScannerMode.VIGILANT, includeLive = false,
            // BoltOdds is read from its sockets on the runner whatever the phone's socket switch says (Tj, 2026-10-17: "my boltodds works fine from my phone. see if it can be reset on GitHub"): with the switch off the engine fell
            // back to the REST snapshot, which Bolt caps at 6 a minute per address, and a shared runner address answered 429 on both keys for hours. The phone's own switch is untouched.
            gates = s.gates.copy(liveOnly = LiveOnlyMode(), boltSocket = true),
            // The engine posts the bids itself (TCL31): the scan then runs the bid profile (far lane, many, deployment) exactly as a phone with auto bids on does.
            maker = s.maker || s.engineBids, makerRecommend = s.makerRecommend && !s.engineBids,
            // The long scan keeps the soonest games' fair odds and likeliest Novig books young (Tj, 2026-10-15: "Is this speed too slow for pre game betting and bidding?"): see [com.tjshea.vigilant.data.scanner.ScanGuards.hotRefresh].
            vigGuards = s.vigGuards.copy(hotRefresh = true),
            // The bet slider's kinds are re-derived on the runner, so the engine takes up a change of the ladder (totals added 2026-10-17) without waiting for the phone to re-apply its slider.
            autoBetKinds = if (s.vigGuards.betLevel in 1..100) s.autoBetKinds + BetSlider.kinds(s.vigGuards.betLevel) else s.autoBetKinds,
        )
    }
}
