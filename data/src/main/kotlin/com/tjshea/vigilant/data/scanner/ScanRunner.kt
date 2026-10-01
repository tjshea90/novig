package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.ReferenceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A scan in flight, or the last one finished: what the screen and the scan notification show. */
data class ScanRun(
    val scanning: Boolean = false,
    val progress: ScanProgress? = null,
    /** Priced so far: partial while [scanning] (it grows as Novig's prices land), then final. */
    val result: ScanResult? = null,
    /** The last finished scan's report. */
    val report: ScanReport? = null,
    /** The settings the current (or last) scan ran with. */
    val settings: ScanSettings? = null,
    /** How many scans have finished, so a watcher can tell a new report from one it has seen. */
    val finished: Int = 0,
)

/**
 * Owns the running scan for the life of the app process, not the screen: Tj starts a scan and
 * switches apps (2026-09-25), so the scan can't live in anything the screen tears down. [scope] is
 * the app's own; the foreground service keeps the process running while [ScanRun.scanning].
 */
class ScanRunner(private val scanner: OddsScanner, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ScanRun())
    val state: StateFlow<ScanRun> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Starts a scan unless one is already running (returns false then). [afterScan] runs when it
     * ends, however it ends, still inside [scope]: bookkeeping that must happen even with no screen.
     */
    @Synchronized
    fun start(
        settings: ScanSettings,
        sources: List<ReferenceSource>,
        pinned: Set<String> = emptySet(),
        afterScan: suspend (ScanReport?) -> Unit = {},
    ): Boolean {
        if (job?.isActive == true) return false
        // CNO only: Vigilant's scan and every API behind it are asleep, whoever asks (a tap, the widget, a background cycle: Tj, 2026-09-29).
        if (!settings.vigilantOn) return false
        // Shown again if this scan ends without a result of its own (Novig's board failed). Held SOFTLY: it is a whole scan's priced lines
        // and the reference boards behind them, kept alive beside the new scan's own, and the heap is a fixed 256-512 MB (Tj's Diagnostics,
        // 2026-10-01: an OutOfMemoryError mid-scan). Under pressure Android lets it go, and a failed scan then shows no old result.
        val before = java.lang.ref.SoftReference(_state.value.result?.takeIf { !it.partial })
        _state.update { it.copy(scanning = true, progress = ScanProgress("Starting"), settings = settings) }
        job = scope.launch {
            var report: ScanReport? = null
            try {
                report = scanner.scan(
                    settings, sources, pinned,
                    onProgress = { p -> _state.update { it.copy(progress = p) } },
                    onPartial = { r -> _state.update { it.copy(result = r) } },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report = ScanReport(
                    result = null, errors = listOf("Scan failed: ${e.message ?: e.javaClass.simpleName}"), retryAfterSeconds = null,
                    booksFetched = 0, booksNotModified = 0, booksFromCache = 0, booksViaKey = 0, novigCatalogAtMs = null,
                    sources = emptyList(), creditsRemaining = null,
                )
            } finally {
                val done = report
                _state.update { s ->
                    s.copy(
                        scanning = false,
                        progress = null,
                        // A scan that failed leaves the last good result up, not a half-read one.
                        result = done?.result ?: before.get(),
                        report = done ?: s.report,
                        finished = s.finished + 1,
                    )
                }
                withContext(NonCancellable) { afterScan(done) }
            }
        }
        return true
    }

    val running: Boolean get() = job?.isActive == true

    /**
     * Stops the scan running now, if any (scanning paused; Tj, 2026-09-28). It ends like a failed one: the last
     * finished result stays up, not a half-read one, and [start]'s afterScan still runs.
     */
    @Synchronized
    fun stop() {
        job?.cancel()
    }
}
