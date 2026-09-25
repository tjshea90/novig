package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.ReferenceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
class ScanRunner(private val scanner: Scanner, private val scope: CoroutineScope) {
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
                        // A failed scan leaves the last good result up rather than a half-read one.
                        result = done?.result ?: s.result?.takeIf { !it.partial },
                        report = done ?: s.report,
                        finished = s.finished + 1,
                    )
                }
                afterScan(done)
            }
        }
        return true
    }

    val running: Boolean get() = job?.isActive == true
}
