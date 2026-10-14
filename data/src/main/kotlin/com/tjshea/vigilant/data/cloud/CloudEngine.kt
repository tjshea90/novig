package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.LiveOnlyMode
import com.tjshea.vigilant.data.scanner.OddsScanner
import com.tjshea.vigilant.data.scanner.Opportunity
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
 *  2. **The phone's word is final.** A new settings revision during a scan stops it and starts again under the new settings (checked every [configEveryMs]).
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
    private val configEveryMs: Long = 8_000L,
    private val idleExitMs: Long = 10 * 60_000L,
    private val heartbeatEveryMs: Long = 25_000L,
    private val sourceNames: (ScanSettings) -> List<String> = { s -> sources(s).map { it.id } },
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

    /** Runs until [untilMs] (the job's time limit) or until the phone has had the engine off for [idleExitMs]. */
    suspend fun run(untilMs: Long, startedAtMs: Long = clock()): End {
        var offSince = 0L
        var lastCfg: CloudConfig? = null
        var lastBeat = 0L
        var lastFeed: CloudFeed? = null
        while (currentScopeActive() && clock() < untilMs) {
            val cfg = runCatching { config() }.getOrNull() ?: lastCfg
            lastCfg = cfg
            if (cfg == null || !cfg.on) {
                if (offSince == 0L) offSince = clock()
                if (clock() - lastBeat >= heartbeatEveryMs) {
                    problem = publish(null, status(cfg?.rev ?: 0L, startedAtMs, untilMs, emptyList(), idle = true, feed = lastFeed))
                    lastBeat = clock()
                }
                if (clock() - offSince >= idleExitMs) return End.IDLE
                pause(10_000L)
                continue
            }
            offSince = 0L
            val s = forCloud(cfg.settings)
            val feed = scanOnce(cfg, s, startedAtMs, untilMs)
            if (feed != null) lastFeed = feed
            lastBeat = clock()
            pause((cfg.passGapSeconds.coerceIn(5, 600) * 1000L))
        }
        return End.TIME_UP
    }

    private suspend fun currentScopeActive(): Boolean = currentCoroutineContext().isActive

    /** One scan under [s], publishing partial lists as it goes; returns the final list, or null when the scan was cut short (a newer revision) or failed. */
    private suspend fun scanOnce(cfg: CloudConfig, s: ScanSettings, startedAtMs: Long, untilMs: Long): CloudFeed? = supervisorScope {
        val t0 = clock()
        val partial = AtomicReference<ScanResult?>(null)
        val feedSources = sources(s)
        val names = sourceNames(s)
        val job = async { scanner.scan(s, feedSources, emptySet(), onProgress = {}, onPartial = { partial.set(it) }) }
        var lastPublishedAt = 0L
        var lastConfigAt = t0
        var restart = false
        while (job.isActive) {
            pause(1_000L)
            val now = clock()
            if (now - lastPublishedAt >= partialEveryMs) {
                lastPublishedAt = now
                partial.getAndSet(null)?.let { r ->
                    val rows = r.feed(s)
                    val feed = CloudFeed(r.computedAtMs, cfg.rev, r.stats, rows)
                    onFeed(rows, now)
                    problem = publish(feed, status(cfg.rev, startedAtMs, untilMs, names, idle = false, feed = feed, scanMs = now - t0))
                }
            }
            if (now - lastConfigAt >= configEveryMs) {
                lastConfigAt = now
                val newer = runCatching { config() }.getOrNull()
                if (newer != null && newer.rev != cfg.rev) { restart = true; job.cancel(CancellationException("settings changed")); break }
            }
        }
        if (restart) { log("settings changed mid-scan: starting over"); return@supervisorScope null }
        val report = try {
            job.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = "scan failed: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
            log(problem!!)
            publish(null, status(cfg.rev, startedAtMs, untilMs, names, idle = false, feed = null))
            return@supervisorScope null
        }
        val result = report.result
        if (result == null) {
            problem = report.errors.firstOrNull()?.take(160) ?: "the scan returned nothing"
            log("no result: $problem")
            publish(null, status(cfg.rev, startedAtMs, untilMs, names, idle = false, feed = null))
            return@supervisorScope null
        }
        val rows = result.feed(s)
        val feed = CloudFeed(result.computedAtMs, cfg.rev, result.stats, rows)
        passes++
        lastRows = rows.size
        lastScanMs = clock() - t0
        onFeed(rows, clock())
        problem = publish(feed, status(cfg.rev, startedAtMs, untilMs, names, idle = false, feed = feed, scanMs = lastScanMs))
        log("pass $passes: ${rows.size} bets listed of ${result.stats.outcomesWithFair} priced, ${lastScanMs / 1000}s" + (problem?.let { ", publish problem: $it" } ?: ""))
        feed
    }

    private fun status(rev: Long, startedAtMs: Long, untilMs: Long, names: List<String>, idle: Boolean, feed: CloudFeed?, scanMs: Long = lastScanMs) = CloudStatus(
        engine = engine, runId = runId, computedAtMs = feed?.computedAtMs ?: 0L, startedAtMs = startedAtMs, heartbeatMs = clock(), scanMs = scanMs,
        rows = feed?.rows?.size ?: lastRows, configRev = rev, sources = names, passes = passes, problem = problem, idle = idle, handoverAtMs = untilMs,
    )

    companion object {
        /** The settings the cloud scans under: the phone's, with the parts that only make sense on the phone switched off. */
        fun forCloud(s: ScanSettings): ScanSettings = s.copy(scanner = ScannerMode.VIGILANT, includeLive = false, gates = s.gates.copy(liveOnly = LiveOnlyMode()))
    }
}
