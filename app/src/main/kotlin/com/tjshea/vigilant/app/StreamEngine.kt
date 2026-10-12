package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The streaming engine (Tj, 2026-10-12: "real time streaming of odds on both for positive EV scanning instead of scanning at intervals ... as soon as bets come up on novig matching the criteria, it auto bets it rapidly").
 *
 * Nothing here prices a bet. A push on Novig's socket (a book changed) or on BoltOdds' (its board changed) only SIGNALS; the signal starts one background cycle at once ([AutoScanner.cycle] with the scan forced), which is
 * exactly the cycle that would have run at the next interval: Vigilant's scan, then the auto-bet's pass over its result, then the bid desk's pass from the scan's end. So every rule, limit and guard of the
 * interval path applies unchanged. What differs is when it runs, and what the scan reads: a streaming pass asks only sources that cost nothing per read ([AppContainer.streamSources]: the BoltOdds board held in memory),
 * Novig's books come from the socket already, so a pass spends no API credits and can run every few seconds. The normal interval cycles go on beside it and read everything as before.
 *
 * Signals are coalesced: any number of pushes while a pass runs or waits become one more pass, started no sooner than [ScanSettings.streamGapSeconds] after the last one began ([StreamPacer]).
 */
class StreamEngine(
    private val c: AppContainer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Status(
        val running: Boolean = false,
        val passes: Long = 0,
        val signals: Long = 0,
        val novigSignals: Long = 0,
        val boltSignals: Long = 0,
        val busySkips: Long = 0,
        val lastPassMs: Long? = null,
        val lastPassAtMs: Long? = null,
        val lastTrigger: String? = null,
        val why: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val signal = Channel<String>(Channel.CONFLATED)
    private var job: Job? = null
    private var watcher: Job? = null
    private var view: com.tjshea.vigilant.data.novig.stream.SharedStreamView? = null

    @Volatile private var lastStartMs = 0L

    /** Follows the switch: starts the engine when [s] asks for it and a way to be signalled exists, stops it otherwise. Idempotent. */
    @Synchronized
    fun tick(s: ScanSettings) {
        val want = s.gates.streamScan && !s.paused && !s.killed
        if (want && job?.isActive != true) start()
        if (!want && job?.isActive == true) stop(if (s.gates.streamScan) "paused" else null)
    }

    private fun start() {
        _status.update { it.copy(running = true, why = null) }
        val listener = BookListener { _, _, _ ->
            _status.update { st -> st.copy(signals = st.signals + 1, novigSignals = st.novigSignals + 1) }
            signal.trySend("Novig")
        }
        view = com.tjshea.vigilant.data.novig.stream.SharedStreamView({ c.novig.stream }, listener).also { it.open() }
        // BoltOdds' board counts its changes; reading the counters is free, so they are looked at a few times a second.
        watcher = scope.launch {
            var seen = boltSeq()
            while (isActive) {
                delay(WATCH_MS)
                val now = boltSeq()
                if (now != seen) {
                    seen = now
                    _status.update { st -> st.copy(signals = st.signals + 1, boltSignals = st.boltSignals + 1) }
                    signal.trySend("BoltOdds")
                }
                // A view bound to an old stream (the key was reconnected) is bound again by asking it for the stream.
                view?.open()
            }
        }
        job = scope.launch {
            while (isActive) {
                val why = signal.receive()
                runCatching { pass(why) }.onFailure {
                    if (it is CancellationException) throw it
                    c.eventLog.warn("STREAM", "a streaming pass failed: ${it.javaClass.simpleName} ${it.message ?: ""}".trim())
                    delay(RETRY_MS)
                }
            }
        }
    }

    private fun boltSeq(): Long = c.boltFeed.lines.board.changeSeq + c.boltFeed.props.board.changeSeq

    @Synchronized
    fun stop(why: String? = null) {
        job?.cancel(); job = null
        watcher?.cancel(); watcher = null
        view?.close(); view = null
        _status.update { it.copy(running = false, why = why) }
    }

    private suspend fun pass(trigger: String) {
        val s = c.currentSettings()
        if (!s.gates.streamScan || s.paused || s.killed) return
        // The least time between two passes: the wait is the rest of the gap, and a push meanwhile only makes it one more pass.
        val wait = StreamPacer.waitMs(clock(), lastStartMs, s.gates.streamGapSeconds * 1000L)
        if (wait > 0) delay(wait)
        // A cycle already running (the interval's, or a scan Tj started) is not interrupted: the signal is kept for when it ends.
        if (c.autoScan.running || c.runner.state.value.scanning) {
            _status.update { it.copy(busySkips = it.busySkips + 1) }
            delay(BUSY_RETRY_MS)
            signal.trySend(trigger)
            return
        }
        if (!c.boltActive(s)) {
            _status.update { it.copy(why = "BoltOdds is not on (Settings › APIs & keys): there is nothing free to stream") }
            delay(RETRY_MS)
            return
        }
        lastStartMs = clock()
        c.streamPass = true
        val ran = try {
            c.autoScan.cycle(forceVigilant = true)
        } finally {
            c.streamPass = false
        }
        val took = clock() - lastStartMs
        _status.update { it.copy(passes = it.passes + 1, lastPassMs = took, lastPassAtMs = clock(), lastTrigger = trigger, why = if (ran) null else "the cycle did not run (auto-scan is off, paused, or Check odds now holds the focus)") }
        c.perf.add("stream.pass.ms", took.toDouble())
        if (_status.value.passes % LOG_EVERY == 1L) c.eventLog.info("STREAM", "streaming pass #${_status.value.passes} (${trigger}) took $took ms${if (!ran) ", the cycle did not run" else ""}")
    }

    companion object {
        const val WATCH_MS = 250L
        const val RETRY_MS = 5_000L
        const val BUSY_RETRY_MS = 500L
        const val LOG_EVERY = 20L
    }
}

/** When the next streaming pass may start (pure): [gapMs] after the last began, never sooner. */
object StreamPacer {
    fun waitMs(nowMs: Long, lastStartMs: Long, gapMs: Long): Long = (lastStartMs + gapMs.coerceAtLeast(MIN_GAP_MS) - nowMs).coerceAtLeast(0L)

    /** The floor of the gap whatever is set: a pass reads the whole board, and Novig's allowance is 4 tokens a second. */
    const val MIN_GAP_MS = 1_000L
}
