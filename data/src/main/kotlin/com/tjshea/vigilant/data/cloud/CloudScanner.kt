package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.OddsScanner
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.RecheckReport
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings

/** The newest list the GitHub engine published, as the phone holds it. Written by the phone's poller, read by [CloudScanner]. */
class CloudLatest {
    @Volatile var feed: CloudFeed? = null
    /** When this phone fetched [feed] (the phone's clock). */
    @Volatile var fetchedAtMs: Long = 0L
    /** The engine's last heartbeat, when it has been heard. */
    @Volatile var status: CloudStatus? = null
    /** The last time this phone's read of the status SUCCEEDED (a changed answer or GitHub's "not modified"), its own clock: how old the whole screen could be ([DashboardModel.LINK_MS]). 0 = never. */
    @Volatile var statusOkAtMs: Long = 0L
    /** Why nothing is arriving ("no GitHub token saved", "GitHub's rate limit"...), null when it is. */
    @Volatile var why: String? = null

    fun put(f: CloudFeed, now: Long) { feed = f; fetchedAtMs = now }
}

/**
 * The scan when the phone does not scan (Settings › Cloud scan on; Tj, 2026-10-14): a "scan" that adopts the list the GitHub engine published, so the Feed, the sheets, the auto-bet and every
 * other reader of a scan's result work on it exactly as on a scan of their own. It reads no API and makes no request: the engine did that, on GitHub's computers.
 *
 * What the engine prices is the pregame +EV list (the settings the phone wrote, its own filter), so [ScanResult.opportunities] holds those rows only. A reader that needs every priced line
 * (the bid desk, which bids below the ask) cannot work from it and keeps a scan of its own (see [com.tjshea.vigilant.data.scanner.ScanSettings.cloudScan]).
 */
class CloudScanner(
    private val latest: CloudLatest,
    private val clock: () -> Long = System::currentTimeMillis,
) : OddsScanner {

    private fun result(): ScanResult? = latest.feed?.let { CloudCodec.asScanResult(it.rows, it.stats, it.computedAtMs) }

    override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): ScanReport {
        val f = latest.feed
        val r = result()
        val errors = buildList {
            val st0 = latest.status
            val firstScan = f == null || (f.rows.isEmpty() && st0 != null && st0.passes == 0 && !st0.idle && clock() - st0.heartbeatMs < 240_000L)
            if (firstScan) {
                val st = st0
                val running = st != null && !st.idle && clock() - st.heartbeatMs < 240_000L
                add(
                    when {
                        latest.why != null -> "Cloud scan: no list has arrived from the GitHub engine yet (${latest.why})"
                        running -> "Cloud scan: the GitHub engine is running its first scan (${(clock() - st!!.startedAtMs) / 60_000L} min so far). A league is listed only when every feed has answered it, so with many leagues, props and days ahead the first list takes several minutes; later lists update every 20 s."
                        st != null && st.idle -> "Cloud scan: the GitHub engine is switched off (it stops when the switch is off); turn Scan on GitHub on again"
                        else -> "Cloud scan: no list has arrived from the GitHub engine yet (waiting for it to start; Settings › Cloud scan › Start the engine now)"
                    },
                )
            }
            else if (f != null) {
                val age = (clock() - f.computedAtMs) / 1000L
                if (age > STALE_SECONDS) add("Cloud scan: the list is ${age / 60} min old" + (latest.why?.let { " ($it)" } ?: " (is the engine running?)"))
                if (f.unreadable > 0) add("Cloud scan: ${f.unreadable} rows could not be read (an engine newer than this app?)")
            }
        }
        return ScanReport(
            result = r, errors = errors, retryAfterSeconds = null, booksFetched = 0, booksNotModified = 0, booksFromCache = 0, booksViaKey = 0,
            novigCatalogAtMs = f?.computedAtMs, sources = emptyList(), creditsRemaining = null,
        )
    }

    override suspend fun reprice(settings: ScanSettings): ScanResult? = result()

    override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = emptySet()

    override suspend fun recheck(settings: ScanSettings, marketIds: Collection<String>, onProgress: (Int, Int) -> Unit): RecheckReport = RecheckReport(result(), 0, 0, null)

    companion object {
        /** A list older than this is called old in the scan's report; it is still shown (the Feed shows its age and every row is re-read at the tap). */
        const val STALE_SECONDS = 180L
    }
}

/**
 * The scanner the app holds: its own, or the cloud's, chosen per call by the settings it is given. Everything that is not a scan or a re-pricing of the listed result (re-checking open bets,
 * the streaming pass, diagnostics) stays with [local], so switching the cloud scan on or off loses nothing.
 */
class SwitchingScanner(private val local: OddsScanner, private val cloud: OddsScanner) : OddsScanner by local {
    private fun pick(s: ScanSettings) = if (s.cloudDrives) cloud else local

    override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): ScanReport =
        pick(settings).scan(settings, sources, pinned, onProgress, onPartial)

    override suspend fun reprice(settings: ScanSettings): ScanResult? = pick(settings).reprice(settings)

    override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = pick(settings).unscannedLeagues(settings)
}
