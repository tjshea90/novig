package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the CNO list shows. */
data class CnoState(
    /** The last good read (kept through errors, and across launches). */
    val snapshot: CnoSnapshot? = null,
    val refreshing: Boolean = false,
    /** Why the last read failed; cleared by the next good one. */
    val error: String? = null,
    /** Failed reads in a row (the back-off grows with it). */
    val errors: Int = 0,
    /** When the last read started (good or not): the pacing clock. */
    val lastAttemptMs: Long? = null,
    /** CNO asked for a pause (HTTP 429/503/403): no read before this. */
    val pausedUntilMs: Long? = null,
)

/** Which view to keep current, with which filters, and how often ([CnoFeed.REALTIME], seconds, or 0 = taps only). */
data class CnoConfig(
    val enabled: Boolean,
    val url: String,
    val intervalSeconds: Int,
    val filters: CnoFilters = CnoFilters(),
)

/** A bet's books as the sheet and the widget show them: loading, loaded, or why not. */
data class CnoBooksState(
    val loading: Boolean = false,
    val view: CnoBooksView? = null,
    val error: String? = null,
)

/**
 * CrazyNinjaOdds' +EV list, kept current while someone is looking (RESEARCH.md §18–19).
 *
 * CNO publishes new odds every 13–33 s, irregularly. So:
 *  - no read starts within [MIN_GAP_MS] (3 s) of the last, whether a tap or the timer asked;
 *  - "real time" ([REALTIME]) waits until CNO's next update is plausible ([CNO_MIN_UPDATE_MS]
 *    after its last one, from its "Last Updated" line), then reads every 3 s until one lands:
 *    new odds within ~3 s of CNO publishing them, without reading the same list over and over;
 *  - fixed intervals (5 s, 15 s, …) read on the clock;
 *  - the timer ([watch]) runs only while the app or its mini window is on screen (the caller
 *    cancels it otherwise), backs off after failed reads (5 s doubling to 2 minutes) and honors
 *    Retry-After.
 */
class CnoFeed(
    private val source: CnoSource,
    private val store: JsonFileStore<CnoCache>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(CnoState())
    val state: StateFlow<CnoState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** When the list was last written to disk (guarded by [mutex]). */
    private var savedAtMs = Long.MIN_VALUE / 2

    /** Shows the list saved by the last read, before any network. */
    suspend fun load() {
        val cached = store?.let { runCatching { it.read().snapshot }.getOrNull() } ?: return
        _state.update { if (it.snapshot == null) it.copy(snapshot = cached) else it }
    }

    /** Milliseconds until a read may start (0 = now). */
    fun waitForGapMs(now: Long = clock()): Long {
        val s = _state.value
        val gap = s.lastAttemptMs?.let { it + MIN_GAP_MS - now } ?: 0L
        val pause = s.pausedUntilMs?.let { it - now } ?: 0L
        return maxOf(0L, gap, pause)
    }

    /**
     * Reads [url] with [filters] now, unless the last read started under 3 s ago (or CNO asked
     * for a pause). Returns whether a read happened; its outcome is in [state].
     */
    suspend fun refresh(url: String, filters: CnoFilters = CnoFilters()): Boolean = mutex.withLock {
        val now = clock()
        if (waitForGapMs(now) > 0) return@withLock false
        _state.update { it.copy(refreshing = true, lastAttemptMs = now) }
        try {
            val snap = source.fetch(url, filters)
            val previous = _state.value.snapshot
            _state.update { it.copy(snapshot = snap, refreshing = false, error = null, errors = 0, pausedUntilMs = null) }
            // The disk copy only has to survive a restart: write it when the list changed or a
            // minute has passed, not on every 5-second read (flash wear, battery).
            val changed = previous == null || previous.rows != snap.rows || previous.url != snap.url || previous.filters != snap.filters
            if (store != null && (changed || now - savedAtMs >= SAVE_EVERY_MS)) {
                if (runCatching { store.update { CnoCache(snap) } }.isSuccess) savedAtMs = now
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.update { it.copy(refreshing = false) }
            throw e
        } catch (e: Exception) {
            val message = (e as? CnoException)?.message ?: "Couldn't read CrazyNinjaOdds (${e.message ?: e.javaClass.simpleName})"
            val retry = (e as? CnoException)?.retryAfterSeconds
            _state.update {
                it.copy(
                    refreshing = false,
                    error = message,
                    errors = it.errors + 1,
                    pausedUntilMs = retry?.let { sec -> clock() + sec * 1000L },
                )
            }
        }
        true
    }

    /** Whether the saved list was read for this view and these filters. */
    fun matches(snapshot: CnoSnapshot?, config: CnoConfig): Boolean =
        snapshot != null && snapshot.url == config.url && snapshot.filters == config.filters

    /**
     * How long until the timer should read [config]'s view, or null for never (off, or taps
     * only). A view or filters not yet read are due at once (still paced).
     */
    fun dueInMs(config: CnoConfig, now: Long = clock()): Long? {
        if (!config.enabled) return null
        val s = _state.value
        val gap = waitForGapMs(now)
        val snap = s.snapshot
        // Never read with this link and these filters (first launch, or a change): now,
        // unless that read just failed: then the back-off below.
        if (!matches(snap, config) && s.error == null) return gap
        if (config.intervalSeconds == 0) return null
        val next = when {
            s.error != null -> (s.lastAttemptMs ?: now) + errorBackoffMs(s.errors, config.intervalSeconds)
            // CNO stuck (its updater down, per its FAQ): the same list again every few seconds is
            // only data spent; look every 30 s (or the interval, if that's longer) until it moves.
            snap != null && now - snap.dataAtMs > CnoChecks.STUCK_MS ->
                (s.lastAttemptMs ?: now) + maxOf(STUCK_POLL_MS, config.intervalSeconds.coerceAtLeast(0) * 1000L)
            config.intervalSeconds == REALTIME && snap != null -> maxOf(
                (s.lastAttemptMs ?: now) + REALTIME_POLL_MS,
                snap.dataAtMs + CNO_MIN_UPDATE_MS,
            )
            else -> (snap?.fetchedAtMs ?: now) + config.intervalSeconds.coerceAtLeast(1) * 1000L
        }
        return maxOf(gap, next - now, 0L)
    }

    /**
     * Keeps the configured view current until cancelled: the caller runs this only while
     * Vigilant or its mini window is on screen. A config change (a new link, new filters, a new
     * interval) takes effect at once.
     */
    suspend fun watch(config: Flow<CnoConfig>) {
        config.distinctUntilChanged().collectLatest { c ->
            while (true) {
                val wait = dueInMs(c) ?: awaitCancellation()
                if (wait > 0) delay(wait)
                // False means a tap got there first; the next pass waits out the gap.
                if (!refresh(c.url, c.filters)) delay(500)
            }
        }
    }

    // ---- A bet's books (CNO's game page), on tap --------------------------------------------

    private val _books = MutableStateFlow<Map<String, CnoBooksState>>(emptyMap())

    /** Books per row key, for rows someone asked about. */
    val books: StateFlow<Map<String, CnoBooksState>> = _books.asStateFlow()

    private val booksMutex = Mutex()
    private val booksReadAt = HashMap<String, Long>()

    /**
     * Loads every book's price for [row], unless it was loaded under [BOOKS_TTL_MS] ago. One at a
     * time: a second tap waits for the first.
     */
    suspend fun loadBooks(row: CnoRow, force: Boolean = false) = booksMutex.withLock {
        val key = row.key
        val now = clock()
        val fresh = booksReadAt[key]?.let { now - it < BOOKS_TTL_MS } == true && _books.value[key]?.view != null
        if (fresh && !force) return@withLock
        _books.update { it + (key to (it[key] ?: CnoBooksState()).copy(loading = true, error = null)) }
        val result = runCatching { source.books(row) }
        result.onSuccess { booksReadAt[key] = clock() }
        _books.update {
            it + (key to CnoBooksState(
                loading = false,
                view = result.getOrNull() ?: it[key]?.view,
                error = result.exceptionOrNull()?.let { e -> e.message ?: "Couldn't read CNO's books" }
                    ?: if (result.getOrNull() == null) "This bet isn't on CNO's game page any more" else null,
            ))
        }
    }

    private val novigLinks = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The Novig app link for [row]'s game, or null. Cached: a game's link doesn't change. */
    suspend fun novigLink(row: CnoRow): String? {
        val key = row.betUrl ?: return null
        novigLinks[key]?.let { return it }
        val link = runCatching { source.novigLink(row) }.getOrNull() ?: return null
        novigLinks[key] = link
        return link
    }

    companion object {
        /** The least time between two reads, taps included. */
        const val MIN_GAP_MS = 3_000L

        /** "Real time": poll this often while waiting for CNO's next update. */
        const val REALTIME_POLL_MS = 3_000L

        /** CNO never published two updates closer than ~13 s apart (RESEARCH.md §19); wait 12. */
        const val CNO_MIN_UPDATE_MS = 12_000L

        /** "Real time" while CNO has stopped updating. */
        const val STUCK_POLL_MS = 30_000L

        /** The refresh setting's value for "real time". */
        const val REALTIME = -1

        /** Write the list to disk at least this often while it's unchanged. */
        const val SAVE_EVERY_MS = 60_000L

        /** A bet's books are re-read after this long. */
        const val BOOKS_TTL_MS = 60_000L

        /** Refresh choices in Settings (seconds; [REALTIME]; 0 = only when tapped). */
        val REFRESH_CHOICES = listOf(REALTIME, 5, 15, 30, 60, 0)

        /** After [errors] failed reads in a row: 5 s, 10 s, 20 s … up to 2 minutes, never faster than the interval. */
        fun errorBackoffMs(errors: Int, intervalSeconds: Int): Long {
            val doubling = 5_000L shl (errors - 1).coerceIn(0, 5)
            return minOf(120_000L, maxOf(doubling, intervalSeconds.coerceAtLeast(0) * 1000L))
        }
    }
}
