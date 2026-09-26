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
    /** When the last read started (good or not): the pacing clock. */
    val lastAttemptMs: Long? = null,
    /** CNO asked for a pause (HTTP 429/503/403): no automatic read before this. */
    val pausedUntilMs: Long? = null,
)

/** Which view to keep current, and how often. */
data class CnoConfig(val enabled: Boolean, val url: String, val intervalSeconds: Int)

/**
 * CrazyNinjaOdds' +EV list, kept current while someone is looking (RESEARCH.md §18).
 *
 * Pacing is the point of this class: CNO is one person's free site, and its robots.txt asks for
 * 30 seconds between requests. So no read ever starts within [MIN_GAP_MS] of the last, whether
 * a tap or the timer asked for it; the timer ([watch]) runs only while the app or its mini window
 * is on screen (the caller cancels it otherwise), backs off after errors, and honors Retry-After.
 * CNO's own data updates about once a minute, which is why 60 s is the default.
 */
class CnoFeed(
    private val source: CnoSource,
    private val store: JsonFileStore<CnoCache>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(CnoState())
    val state: StateFlow<CnoState> = _state.asStateFlow()

    private val mutex = Mutex()

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
     * Reads [url] now, unless the last read started under 30 seconds ago (or CNO asked for a
     * pause). Returns whether a read happened; its outcome is in [state].
     */
    suspend fun refresh(url: String): Boolean = mutex.withLock {
        val now = clock()
        if (waitForGapMs(now) > 0) return@withLock false
        _state.update { it.copy(refreshing = true, lastAttemptMs = now) }
        try {
            val snap = source.fetch(url)
            _state.update { it.copy(snapshot = snap, refreshing = false, error = null, pausedUntilMs = null) }
            store?.let { s -> runCatching { s.update { CnoCache(snap) } } }
        } catch (e: CnoException) {
            _state.update {
                it.copy(
                    refreshing = false,
                    error = e.message,
                    pausedUntilMs = e.retryAfterSeconds?.let { sec -> clock() + sec * 1000L },
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.update { it.copy(refreshing = false) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(refreshing = false, error = "Couldn't read CrazyNinjaOdds (${e.message ?: e.javaClass.simpleName})") }
        }
        true
    }

    /**
     * How long until the timer should read [config]'s view, or null for never (off, or manual
     * refresh only). A view not yet read is due at once (still paced).
     */
    fun dueInMs(config: CnoConfig, now: Long = clock()): Long? {
        if (!config.enabled) return null
        val s = _state.value
        val gap = waitForGapMs(now)
        // A view never read (first launch, or a new link): now. Unless it just failed: then back off.
        if (s.snapshot?.url != config.url && s.error == null) return gap
        if (config.intervalSeconds <= 0) return null
        val interval = config.intervalSeconds * 1000L
        val next = if (s.error != null) {
            (s.lastAttemptMs ?: now) + maxOf(interval, ERROR_BACKOFF_MS)
        } else {
            (s.snapshot?.fetchedAtMs ?: now) + interval
        }
        return maxOf(gap, next - now, 0L)
    }

    /**
     * Keeps the configured view current until cancelled: the caller runs this only while
     * Vigilant or its mini window is on screen. A config change (a new link, a new interval)
     * takes effect at once.
     */
    suspend fun watch(config: Flow<CnoConfig>) {
        config.distinctUntilChanged().collectLatest { c ->
            while (true) {
                val wait = dueInMs(c) ?: awaitCancellation()
                if (wait > 0) delay(wait)
                // False means a tap got there first; the next pass waits out the gap.
                if (!refresh(c.url)) delay(1_000)
            }
        }
    }

    companion object {
        /** CNO's robots.txt `Crawl-delay: 30`. */
        const val MIN_GAP_MS = 30_000L

        /** After a failed read, wait at least this long before the timer tries again. */
        const val ERROR_BACKOFF_MS = 120_000L

        /** Automatic refresh choices in Settings (seconds; 0 = only when tapped). */
        val REFRESH_CHOICES = listOf(30, 60, 120, 300, 0)
    }
}
