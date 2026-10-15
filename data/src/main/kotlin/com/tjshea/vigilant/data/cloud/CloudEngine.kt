package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.LiveOnlyMode
import com.tjshea.vigilant.data.scanner.OddsScanner
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 * Nothing in here places an order or holds a trading key.
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
     * while the phone has Vigilant-priced bids on ([ScanSettings.bidsFromCloud]).
     */
    private val bidLines: (suspend (ScanResult, ScanSettings, Long) -> List<com.tjshea.vigilant.data.novig.trading.maker.BidCandidates.BidLine>)? = null,
    /** Publishes the candidate lines; null = the phone no longer prices bids from them, take the file away. */
    private val publishBids: suspend (CloudBids?) -> Unit = {},
    /** While a scan runs, a new candidate list goes out at most this often (a list is thousands of lines; a rule change sends one at once). */
    private val bidsEveryMs: Long = 45_000L,
) {
    enum class End { TIME_UP, IDLE }

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
    private val progress = AtomicReference<ScanProgress?>(null)
    @Volatile private var bidsAt = 0L
    @Volatile private var bidsCount = 0
    @Volatile private var bidsOn = false
    @Volatile private var lastPublished: CloudFeed? = null
    @Volatile private var lastAges = CloudCurrency.UNKNOWN

    /** Runs until [untilMs] (the job's time limit) or until the phone has had the engine off for [idleExitMs]. */
    suspend fun run(untilMs: Long, startedAtMs: Long = clock()): End {
        var offSince = 0L
        var lastCfg: CloudConfig? = null
        var lastBeat = 0L
        while (currentScopeActive() && clock() < untilMs) {
            val cfg = runCatching { config() }.getOrNull() ?: lastCfg
            lastCfg = cfg
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
            val restarted = scanOnce(cfg, startedAtMs, untilMs)
            lastBeat = clock()
            // A scan cut short by the phone's word (a refresh, new settings) starts again at once; one that ran to its end waits the gap.
            if (!restarted) pause((cfg.passGapSeconds.coerceIn(5, 600) * 1000L))
        }
        return End.TIME_UP
    }

    private suspend fun currentScopeActive(): Boolean = currentCoroutineContext().isActive

    /**
     * One scan under the phone's settings, publishing partial lists as it goes. Returns true when it was cut short because the phone asked for something (a refresh, settings that change what is read, off), so the loop
     * starts the next one without waiting.
     */
    private suspend fun scanOnce(cfg0: CloudConfig, startedAtMs: Long, untilMs: Long): Boolean = supervisorScope {
        val t0 = clock()
        var cfg = cfg0
        // The settings the scan READS under stay as they were when it began; the list is filtered under the newest ones (a raised minimum edge shows at once, no scan is thrown away for it).
        val scanSettings = forCloud(cfg0.settings)
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
        var lastStatusAt = t0
        var restartReason: String? = null
        // The first status goes out at once: it is the engine's acknowledgement of the request.
        problem = publish(null, status(cfg.rev, startedAtMs, untilMs, names, idle = false))
        while (job.isActive) {
            pause(1_000L)
            val now = clock()
            if (now - lastPublishedAt >= partialEveryMs) {
                lastPublishedAt = now
                partial.getAndSet(null)?.let { r ->
                    val rows = r.feed(filterSettings)
                    val feed = CloudFeed(now, cfg.rev, r.stats, rows)
                    onFeed(rows, now)
                    lastPublished = feed; lastAges = CloudCurrency.of(rows, now)
                    lastResult = r
                    // The bid desk's candidates ride with the list, at most every [bidsEveryMs] (before the status, so it says what was sent).
                    if (now - lastBidsAt >= bidsEveryMs) { lastBidsAt = now; sendBids(r, cfg.rev, latest, now) }
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
                val newer = runCatching { config() }.getOrNull()
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
                            // A bid rule changed: the candidates are made again from what is held, at once, with no scan.
                            val k = CloudScanKey.key(newer.settings, CloudScanKey.Kind.BIDS)
                            if (k != bidKey) { bidKey = k; lastResult?.let { r -> lastBidsAt = now; sendBids(r, cfg.rev, latest, now) } }
                        }
                    }
                }
            }
        }
        if (restartReason != null) {
            log("scan cut short ($restartReason) after ${(clock() - t0) / 1000}s: starting over")
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
        scanNote = describe(report)
        log("scan report: $scanNote")
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
        problem = publish(feed, status(cfg.rev, startedAtMs, untilMs, names, idle = false, scanMs = lastScanMs))
        log("pass $passes: ${rows.size} bets listed of ${result.stats.outcomesWithFair} priced, ${lastScanMs / 1000}s" + (problem?.let { ", publish problem: $it" } ?: ""))
        false
    }

    private fun status(rev: Long, startedAtMs: Long, untilMs: Long, names: List<String>, idle: Boolean, scanMs: Long = lastScanMs): CloudStatus {
        val p = progress.get()
        val feed = lastPublished
        return CloudStatus(
            engine = engine, runId = runId, computedAtMs = feed?.computedAtMs ?: 0L, startedAtMs = startedAtMs, heartbeatMs = clock(), scanMs = scanMs,
            rows = feed?.rows?.size ?: lastRows, configRev = rev, sources = names, passes = passes, problem = problem, idle = idle, handoverAtMs = untilMs,
            scanning = scanning && !idle, scanStartedAtMs = scanStartedAt, progressStep = if (scanning) p?.step.orEmpty() else "", progressDone = if (scanning) p?.done ?: 0 else 0, progressTotal = if (scanning) p?.total ?: 0 else 0,
            ackedRefreshAtMs = handledRefresh, scanRefreshAtMs = scanForRefresh, lastScanStartedAtMs = lastScanStartedAt, lastScanEndedAtMs = lastScanEndedAt, lastScanForRefreshAtMs = lastScanForRefresh,
            novigAgeMedianS = lastAges.novigMedianS, novigAgeMaxS = lastAges.novigMaxS, fairAgeMedianS = lastAges.fairMedianS, fairAgeMaxS = lastAges.fairMaxS,
            pricedConfigRev = pricedRev, settingsPending = settingsPending, scanNote = scanNote, bidsAtMs = bidsAt, bidsCount = bidsCount,
        )
    }

    /**
     * Publishes the bid desk's candidate lines of [result] when the phone has Vigilant-priced bids on; once, null when it stops (so the file does not linger). A failure is a line in the log: the
     * Feed's list does not depend on it.
     */
    private suspend fun sendBids(result: ScanResult, rev: Long, settings: ScanSettings, now: Long) {
        val make = bidLines ?: return
        val s = forCloud(settings)
        if (!s.bidsFromCloud) {
            if (bidsOn) { bidsOn = false; bidsAt = 0L; bidsCount = 0; runCatching { publishBids(null) } }
            return
        }
        runCatching {
            val lines = make(result, s, now)
            publishBids(CloudBids(now, result.partial, rev, lines))
            bidsOn = true; bidsAt = now; bidsCount = lines.size
        }.onFailure { if (it is CancellationException) throw it; log("candidate lines failed: ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
    }

    /** A scan's own account of itself, short: what Novig gave, whether it throttled, and what each feed said (errors included). Never a key. */
    private fun describe(r: com.tjshea.vigilant.data.scanner.ScanReport): String = buildString {
        append("Novig books: ${r.booksFetched} read, ${r.booksNotModified} unchanged, ${r.booksFromCache} from cache, ${r.booksTooLate} left out as too late")
        r.retryAfterSeconds?.let { append("; Novig asked us to slow down ${it}s") }
        if (r.sources.isNotEmpty()) append("; feeds: " + r.sources.joinToString(", ") { "${it.id} ${it.fetched}+${it.reused}" + (it.error?.let { e -> " ERR ${e.take(50)}" } ?: "") })
        if (r.errors.isNotEmpty()) append("; errors: " + r.errors.take(3).joinToString(" | ") { it.take(80) })
    }.take(700)

    companion object {
        /** The settings the cloud scans under: the phone's, with the parts that only make sense on the phone switched off. */
        fun forCloud(s: ScanSettings): ScanSettings = s.copy(scanner = ScannerMode.VIGILANT, includeLive = false, gates = s.gates.copy(liveOnly = LiveOnlyMode()))
    }
}
