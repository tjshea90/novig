package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 *  - the timer ([watch]) runs only while the CNO tab or a widget is on screen ([CnoWatch]; the caller
 *    cancels it otherwise), backs off after failed reads (5 s doubling to 2 minutes) and honors
 *    Retry-After.
 */
class CnoFeed(
    private val source: CnoSource,
    private val store: JsonFileStore<CnoCache>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Bets' Novig links, kept between launches (a line's link never changes). */
    private val linkStore: JsonFileStore<CnoLinks>? = null,
) {
    private val _state = MutableStateFlow(CnoState())
    val state: StateFlow<CnoState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** When the list was last written to disk (guarded by [mutex]). */
    private var savedAtMs = Long.MIN_VALUE / 2

    /** Shows the list saved by the last read, before any network. */
    suspend fun load() {
        linkStore?.let { runCatching { it.read().links }.getOrNull() }?.forEach { (k, v) -> novigLinks.putIfAbsent(k, v) }
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
            // A pause CNO asked for while this read ran (a books or link request refused) stands.
            _state.update { it.copy(snapshot = snap, refreshing = false, error = null, errors = 0, pausedUntilMs = it.pausedUntilMs?.takeIf { p -> p > clock() }) }
            // The disk copy only has to survive a restart: write it when the list changed or a
            // minute has passed, not on every 5-second read (flash wear, battery).
            val changed = previous == null || previous.rows != snap.rows || previous.url != snap.url || previous.filters != snap.filters
            val disk = store
            if (disk != null && (changed || now - savedAtMs >= SAVE_EVERY_MS)) {
                if (runCatching { disk.update { CnoCache(snap) } }.isSuccess) savedAtMs = now
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.update { it.copy(refreshing = false) }
            throw e
        } catch (e: Exception) {
            // Cut short because nobody is looking any more (the tab or widget closed, the phone
            // locked): the network layer reports that as a failed read, but it isn't one. Showing
            // it was the "CNO error" Tj saw (2026-09-26).
            if (!currentCoroutineContext().isActive) {
                _state.update { it.copy(refreshing = false) }
                throw kotlinx.coroutines.CancellationException("CNO read cancelled").apply { initCause(e) }
            }
            val message = (e as? CnoException)?.message ?: "Couldn't read CrazyNinjaOdds (${e.message ?: e.javaClass.simpleName})"
            val retry = (e as? CnoException)?.retryAfterSeconds
            _state.update {
                it.copy(
                    refreshing = false,
                    error = message,
                    errors = it.errors + 1,
                    pausedUntilMs = maxOf(retry?.let { sec -> clock() + sec * 1000L } ?: 0L, it.pausedUntilMs ?: 0L).takeIf { p -> p > clock() },
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
     * the CNO tab or a widget is on screen ([CnoWatch]). A config change (a new link, new filters, a new
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

    /** When each row's books were last read successfully, and last tried at all (guarded by [booksMutex]). */
    private val booksReadAt = HashMap<String, Long>()
    private val booksTriedAt = HashMap<String, Long>()

    /**
     * Loads every book's price for [row], unless it was loaded under [maxAgeMs] ago (a tap: a
     * minute; the agreement lane: [AGREE_TTL_MS]). One at a time: a second tap waits for the first.
     */
    suspend fun loadBooks(row: CnoRow, force: Boolean = false, maxAgeMs: Long = BOOKS_TTL_MS) = booksMutex.withLock {
        val key = row.key
        val now = clock()
        val fresh = booksReadAt[key]?.let { now - it < maxAgeMs } == true && _books.value[key]?.view != null
        if (fresh && !force) return@withLock
        val triedBefore = booksTriedAt[key]
        booksTriedAt[key] = now
        _books.update { it + (key to (it[key] ?: CnoBooksState()).copy(loading = true, error = null)) }
        val result = try {
            Result.success(source.books(row))
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cut short (the scanner closed, or a new list moved the lane on): not a failed try.
            if (triedBefore == null) booksTriedAt.remove(key) else booksTriedAt[key] = triedBefore
            _books.update { it + (key to (it[key] ?: CnoBooksState()).copy(loading = false)) }
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                // Cut short, not failed (see refresh).
                if (triedBefore == null) booksTriedAt.remove(key) else booksTriedAt[key] = triedBefore
                _books.update { it + (key to (it[key] ?: CnoBooksState()).copy(loading = false)) }
                throw kotlinx.coroutines.CancellationException("CNO books read cancelled").apply { initCause(e) }
            }
            Result.failure(e)
        }
        result.onSuccess { booksReadAt[key] = clock() }
        // CNO asked for a pause (busy, or refusing): no read of any kind until it's over.
        (result.exceptionOrNull() as? CnoException)?.retryAfterSeconds?.let { sec ->
            _state.update { it.copy(pausedUntilMs = maxOf(it.pausedUntilMs ?: 0L, clock() + sec * 1000L)) }
        }
        _books.update {
            it + (key to CnoBooksState(
                loading = false,
                view = result.getOrNull() ?: it[key]?.view,
                error = result.exceptionOrNull()?.let { e -> e.message ?: "Couldn't read CNO's books" }
                    ?: if (result.getOrNull() == null) "This bet isn't on CNO's game page any more" else null,
            ))
        }
        // A long session sees hundreds of bets come and go: keep the newest [BOOKS_KEEP].
        if (_books.value.size > BOOKS_KEEP) {
            val drop = booksTriedAt.entries.sortedBy { it.value }.take(_books.value.size - BOOKS_KEEP).map { it.key }.toSet() - key
            drop.forEach { booksReadAt.remove(it); booksTriedAt.remove(it) }
            _books.update { it - drop }
        }
    }

    // ---- The green check: the top bets' books, read slowly in the background ---------------

    /** When the agreement lane last started a read. */
    @Volatile
    private var laneReadAtMs = Long.MIN_VALUE / 2

    /** Whether [row]'s books are due for the agreement lane: never read, stale, or a failed try long enough ago. */
    private fun booksDueInMs(row: CnoRow, now: Long): Long {
        val read = booksReadAt[row.key]
        val tried = booksTriedAt[row.key]
        val readDue = read?.let { it + AGREE_TTL_MS - now } ?: 0L
        // The last try failed (tried after the last good read): wait before trying again.
        val retryDue = if (tried != null && (read == null || tried > read)) tried + AGREE_RETRY_MS - now else 0L
        return maxOf(0L, readDue, retryDue)
    }

    /**
     * Keeps the books of the top [AGREE_TOP] bets in [rows] (best first) no older than
     * [AGREE_TTL_MS], for the widget's green check (Tj, 2026-09-26: only "if it doesn't slow down
     * the scanning a lot"). So it stays out of the list's way: one bet's game page at a time,
     * [AGREE_GAP_MS] apart, never while a list read is running or CNO asked for a pause. Runs
     * until cancelled; the caller runs it only alongside [watch].
     */
    suspend fun keepBooksFresh(rows: Flow<List<CnoRow>>, top: Int = AGREE_TOP) {
        // Only which bets are on top matters here, not their prices or order: a refresh that just
        // re-prices the list doesn't cut a read short.
        rows.map { it.take(top) }.distinctUntilChanged { a, b -> a.mapTo(HashSet()) { it.key } == b.mapTo(HashSet()) { it.key } }.collectLatest { top ->
            if (top.isEmpty()) awaitCancellation()
            while (true) {
                val now = clock()
                val due = booksMutex.withLock { top.map { it to booksDueInMs(it, now) } }
                val (next, wait) = due.minByOrNull { it.second } ?: awaitCancellation()
                if (wait > 0) {
                    delay(wait)
                    continue
                }
                // The list first: wait out a running read or a pause CNO asked for; and keep the
                // gap between two of these even when a new list restarted this loop.
                val s = _state.value
                val pause = s.pausedUntilMs?.let { it - clock() } ?: 0L
                val gap = laneReadAtMs + AGREE_GAP_MS - clock()
                // CNO's list failing (unreachable, busy): don't pile more requests on; wait for it.
                val failing = if (s.error != null) LANE_WAIT_ON_ERROR_MS else 0L
                if (s.refreshing || pause > 0 || gap > 0 || failing > 0) {
                    delay(maxOf(pause, gap, failing, if (s.refreshing) 500L else 0L, 1L))
                    continue
                }
                laneReadAtMs = clock()
                loadBooks(next, maxAgeMs = AGREE_TTL_MS)
            }
        }
    }

    private val novigLinks = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val linkTriedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val linkSaveMutex = Mutex()

    @Volatile
    private var linkLaneAtMs = Long.MIN_VALUE / 2

    /** [row]'s Novig link if it's already known: no network, so a tap opens the bet at once. */
    fun cachedLink(row: CnoRow): String? = row.betUrl?.let { novigLinks[it] }

    /**
     * The Novig app link for [row]: `novigapp://events/<outcome id>/cno`, which opens Novig with
     * that exact bet in its bet slip (RESEARCH.md §20). Cached, and kept on disk: a line's link
     * doesn't change. Null when CNO couldn't say.
     */
    suspend fun novigLink(row: CnoRow): String? {
        val key = row.betUrl ?: return null
        novigLinks[key]?.let { return it }
        val link = try {
            source.novigLink(row)?.let(::appLink)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) throw kotlinx.coroutines.CancellationException("cancelled").apply { initCause(e) }
            // CNO asked for a pause (busy, or refusing): every lane waits it out, the list included.
            (e as? CnoException)?.retryAfterSeconds?.let { sec ->
                _state.update { it.copy(pausedUntilMs = maxOf(it.pausedUntilMs ?: 0L, clock() + sec * 1000L)) }
            }
            null
        } ?: return null
        novigLinks[key] = link
        saveLinks()
        return link
    }

    private suspend fun saveLinks() {
        val disk = linkStore ?: return
        linkSaveMutex.withLock {
            // The newest [LINKS_KEEP]: a season of lines would otherwise pile up.
            val all = novigLinks.toMap()
            val kept = if (all.size <= LINKS_KEEP) all else all.entries.toList().takeLast(LINKS_KEEP).associate { it.key to it.value }
            runCatching { disk.update { CnoLinks(kept) } }
        }
    }

    /**
     * Looks up the Novig links of the top [top] bets in [rows] ahead of any tap (Tj, 2026-09-27:
     * "sometimes they pull up the novig bet slip, but sometimes they don't": the link used to be
     * asked of CNO on the tap, which failed whenever CNO was slow or out of reach). One small
     * request per new bet, [LINK_GAP_MS] apart, after the list's reads and never while CNO is
     * failing or asked for a pause. Runs until cancelled; the caller runs it alongside [watch].
     */
    suspend fun keepLinksFresh(rows: Flow<List<CnoRow>>, top: Int = LINKS_TOP) {
        rows.map { list -> list.take(top).filter { it.betUrl != null } }
            .distinctUntilChanged { a, b -> a.mapTo(HashSet()) { it.key } == b.mapTo(HashSet()) { it.key } }
            .collectLatest { list ->
                while (true) {
                    val now = clock()
                    val missing = list.filter { novigLinks[it.betUrl!!] == null }
                    if (missing.isEmpty()) awaitCancellation()
                    val next = missing.firstOrNull { r -> linkTriedAt[r.betUrl!!]?.let { now - it >= LINK_RETRY_MS } ?: true }
                    if (next == null) {
                        val soonest = missing.mapNotNull { linkTriedAt[it.betUrl!!] }.minOrNull() ?: now
                        delay((soonest + LINK_RETRY_MS - now).coerceAtLeast(1_000L))
                        continue
                    }
                    val s = _state.value
                    val pause = s.pausedUntilMs?.let { it - clock() } ?: 0L
                    val gap = linkLaneAtMs + LINK_GAP_MS - clock()
                    val failing = if (s.error != null) LANE_WAIT_ON_ERROR_MS else 0L
                    if (s.refreshing || pause > 0 || gap > 0 || failing > 0) {
                        delay(maxOf(pause, gap, failing, if (s.refreshing) 500L else 0L, 1L))
                        continue
                    }
                    linkLaneAtMs = clock()
                    linkTriedAt[next.betUrl!!] = clock()
                    novigLink(next)
                }
            }
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

        /** Books kept in memory for this many bets at most. */
        const val BOOKS_KEEP = 150

        /**
         * The green check looks at this many of the best bets (Tj's "only bets the books agree on"
         * widens it to [AGREE_TOP_ONLY_AGREED])…
         */
        const val AGREE_TOP = 10
        const val AGREE_TOP_ONLY_AGREED = 20

        /** …re-reading each one's books after this long (a bet's consensus moves slowly)… */
        const val AGREE_TTL_MS = 10 * 60_000L

        /** …a failed one after this long… */
        const val AGREE_RETRY_MS = 2 * 60_000L

        /**
         * …and waits this long between two of them: each is two requests (CNO's game page, then
         * its table), and CNO is one small shared server (Tj, 2026-09-27: "cno is restricting or
         * slowing me down").
         */
        const val AGREE_GAP_MS = 4_000L

        /** While CNO's list is failing, the books and link lanes look again this often. */
        const val LANE_WAIT_ON_ERROR_MS = 5_000L

        /** Bets whose Novig links are looked up ahead of a tap… */
        const val LINKS_TOP = 15

        /** …one every this long… */
        const val LINK_GAP_MS = 3_000L

        /** …a failed one again after this long. */
        const val LINK_RETRY_MS = 60_000L

        /** Links kept on disk. */
        const val LINKS_KEEP = 400

        private val NOVIG_WEB_BET = Regex("""^https://(?:www\.)?novig\.(?:com|us)/(events/[^?#]+)""", RegexOption.IGNORE_CASE)

        /**
         * CNO's link in the form Novig's app opens: CNO hands a desktop browser
         * `https://novig.com/events/<outcome>/cno` and a phone `novigapp://events/<outcome>/cno`;
         * only the second is certain to open the app. Anything else passes through.
         */
        fun appLink(link: String): String =
            NOVIG_WEB_BET.find(link.trim())?.let { "novigapp://" + it.groupValues[1] } ?: link.trim()

        /** Refresh choices in Settings (seconds; [REALTIME]; 0 = only when tapped). */
        val REFRESH_CHOICES = listOf(REALTIME, 5, 15, 30, 60, 0)

        /** After [errors] failed reads in a row: 5 s, 10 s, 20 s … up to 2 minutes, never faster than the interval. */
        fun errorBackoffMs(errors: Int, intervalSeconds: Int): Long {
            val doubling = 5_000L shl (errors - 1).coerceIn(0, 5)
            return minOf(120_000L, maxOf(doubling, intervalSeconds.coerceAtLeast(0) * 1000L))
        }
    }
}
