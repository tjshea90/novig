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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The streaming engine (Tj, 2026-10-12: "real time streaming of odds on both for positive EV scanning instead of scanning at intervals ... as soon as bets come up on novig matching the criteria, it auto bets it rapidly";
 * then "match game and prop odds so they can compare odds changes in real time").
 *
 * The standing match is the scanner's own: its catalog (Novig's games and markets) matched to every feed's events by the planner, kept as the cached plan, and its priced slots, one per Novig market, that are
 * recomputed only when that market's book (or the fair line behind it) is replaced. This engine adds the two triggers and nothing else; it never prices or orders anything itself:
 *  - **A Novig book moved** (the socket pushed a market): that market alone is re-read from the feed's held books and re-priced against the fair line already held ([OddsScanner.streamUpdate] with the market's id, a few milliseconds),
 *    and the auto-bet judges just those lines ([AutoScanner.streamHandOff]). This is how a stale Novig price is picked off.
 *  - **BoltOdds' board changed**: its boards are read again (memory), the plan is matched again and every held Novig book is re-priced, no more often than the gap setting; the auto-bet judges the result.
 * Both end with the bid desk's pass. A streaming update reads no request from anything: BoltOdds' boards and Novig's pushed books are already in memory. Before the scanner has a catalog (no scan yet) one full cycle is run
 * to make it ([AutoScanner.cycle]); the interval cycles go on beside the engine and re-read everything (new games and markets arrive that way).
 *
 * Live games are not in this path (a live fair needs a sharp book that is fresh to 5 seconds: the Pinnodds + Novig lane, which keeps its own standing match of every live game).
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
        /** Novig-push updates (one market re-priced), BoltOdds refreshes (boards read and matched again) and full cycles that made the catalog. */
        val fastPasses: Long = 0,
        val refreshPasses: Long = 0,
        val bootstrapCycles: Long = 0,
        /** Lines handed to the auto-bet in all, and the slowest update. */
        val handedOff: Long = 0,
        val slowestMs: Long = 0,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val signal = Channel<String>(Channel.CONFLATED)
    private var job: Job? = null
    private var watcher: Job? = null
    private var view: com.tjshea.vigilant.data.novig.stream.SharedStreamView? = null

    /** Novig markets whose book moved since the last update, and whether BoltOdds' board changed. */
    private val dirty = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val boltDirty = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile private var lastBoltRefreshMs = 0L
    @Volatile private var lastFastMs = 0L

    /** Follows the switch: starts the engine when [s] asks for it and a way to be signalled exists, stops it otherwise. Idempotent. */
    @Synchronized
    fun tick(s: ScanSettings) {
        val want = s.gates.streamScan && !s.paused && !s.killed
        if (want && job?.isActive != true) start()
        if (!want && job?.isActive == true) stop(if (s.gates.streamScan) "paused" else null)
    }

    private fun start() {
        _status.update { it.copy(running = true, why = null) }
        dirty.clear()
        boltDirty.set(true)
        val listener = BookListener { marketId, _, _ ->
            dirty += marketId
            _status.update { st -> st.copy(signals = st.signals + 1, novigSignals = st.novigSignals + 1) }
            signal.trySend("Novig")
        }
        view = com.tjshea.vigilant.data.novig.stream.SharedStreamView({ c.novig.stream as? com.tjshea.vigilant.data.novig.stream.NovigStream }, listener).also { it.open() }
        // BoltOdds' board counts its changes; reading the counters is free, so they are looked at a few times a second.
        watcher = scope.launch {
            var seen = boltSeq()
            while (isActive) {
                delay(WATCH_MS)
                val now = boltSeq()
                if (now != seen) {
                    seen = now
                    boltDirty.set(true)
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
                // A burst of pushes is one update.
                delay(DEBOUNCE_MS)
                runCatching { pass(why) }.onFailure {
                    if (it is CancellationException) throw it
                    c.eventLog.warn("STREAM", "a streaming update failed: ${it.javaClass.simpleName} ${it.message ?: ""}".trim())
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
        dirty.clear()
        _status.update { it.copy(running = false, why = why) }
    }

    private suspend fun pass(trigger: String) {
        val s = c.currentSettings()
        if (!s.gates.streamScan || s.paused || s.killed) return
        // A cycle already running (the interval's) or a scan Tj started is not interrupted: the pushes are kept for when it ends.
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
        val t0 = clock()
        val boltWait = StreamPacer.waitMs(t0, lastBoltRefreshMs, s.gates.streamGapSeconds * 1000L)
        val refresh = boltDirty.get() && boltWait == 0L
        val ids: Set<String> = if (refresh) emptySet() else drain()
        if (!refresh && ids.isEmpty()) {
            // Only BoltOdds changed and its gap has not passed: come back when it has.
            if (boltDirty.get()) { delay(boltWait.coerceAtLeast(50L)); signal.trySend("BoltOdds") }
            return
        }
        // At most one fast update per FAST_GAP_MS: a market that moved meanwhile stays dirty for the next.
        val fastWait = StreamPacer.waitFastMs(t0, lastFastMs)
        if (!refresh && fastWait > 0) { dirty += ids; delay(fastWait); signal.trySend(trigger); return }
        val result = if (refresh) {
            boltDirty.set(false)
            lastBoltRefreshMs = t0
            drain()
            c.runner.stream(s, c.streamSources(s), null)
        } else {
            lastFastMs = t0
            c.runner.stream(s, emptyList(), ids)
        }
        if (result == null) {
            // No catalog yet (no scan has run since the app started): one full cycle makes it; the next update is a stream one.
            if (!c.runner.running) {
                c.streamPass = true
                try { c.autoScan.cycle(forceVigilant = true) } finally { c.streamPass = false }
                _status.update { it.copy(bootstrapCycles = it.bootstrapCycles + 1, why = null) }
            }
            return
        }
        val handed = c.autoScan.streamHandOff(s, result, if (refresh) null else ids)
        val took = clock() - t0
        _status.update {
            it.copy(
                passes = it.passes + 1, lastPassMs = took, lastPassAtMs = clock(), lastTrigger = if (refresh) "BoltOdds" else "Novig", why = null,
                fastPasses = it.fastPasses + if (refresh) 0 else 1, refreshPasses = it.refreshPasses + if (refresh) 1 else 0,
                handedOff = it.handedOff + handed, slowestMs = maxOf(it.slowestMs, took),
            )
        }
        c.perf.add(if (refresh) "stream.refresh.ms" else "stream.fast.ms", took.toDouble())
        if (_status.value.passes % LOG_EVERY == 1L) c.eventLog.info("STREAM", "streaming update #${_status.value.passes} (${if (refresh) "BoltOdds refresh, all lines" else "${ids.size} Novig line${if (ids.size == 1) "" else "s"}"}) took $took ms, $handed lines judged")
    }

    private fun drain(): Set<String> = HashSet<String>().also { out -> dirty.removeAll(dirty.toList().also { out += it }.toSet()) }

    companion object {
        const val WATCH_MS = 250L
        const val DEBOUNCE_MS = 100L
        const val RETRY_MS = 5_000L
        const val BUSY_RETRY_MS = 500L
        const val LOG_EVERY = 20L
    }
}

/** When the next update may start (pure). */
object StreamPacer {
    /** The rest of the gap after the last BoltOdds refresh began, never less than [MIN_GAP_MS] apart. */
    fun waitMs(nowMs: Long, lastStartMs: Long, gapMs: Long): Long = (lastStartMs + gapMs.coerceAtLeast(MIN_GAP_MS) - nowMs).coerceAtLeast(0L)

    /** The rest of [FAST_GAP_MS] after the last Novig-push update began. */
    fun waitFastMs(nowMs: Long, lastStartMs: Long): Long = (lastStartMs + FAST_GAP_MS - nowMs).coerceAtLeast(0L)

    /** The floor of the BoltOdds refresh gap whatever is set: it matches and prices the whole board. */
    const val MIN_GAP_MS = 1_000L

    /** One Novig-push update at most every 300 ms (a re-price of the lines that moved takes a few milliseconds; the rest is the order's own work). */
    const val FAST_GAP_MS = 300L
}
