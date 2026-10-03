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
    /** The last pause CNO asked for, and why ("CrazyNinjaOdds is busy (HTTP 429)"): kept after it's over, for Diagnostics. */
    val lastPause: String? = null,
    val lastPauseAtMs: Long? = null,
)

/** The scan study's wide read ([CnoFeed.readWide]): the last good one, and how the reads are going. Never shown in the app: only the study and Diagnostics read it. */
data class CnoWideState(
    val snapshot: CnoSnapshot? = null,
    /** Why the last wide read failed (or what CNO said when it sent nothing); cleared by the next good one. */
    val error: String? = null,
    /** Failed reads in a row (the wait before the next grows with it). */
    val errors: Int = 0,
    val lastAttemptMs: Long? = null,
    /** Good reads, and the rows the last one had beyond the rows the app's list carried then (what the study sees that the app doesn't). */
    val reads: Long = 0,
    /** The row count asked for now: [CnoFeed.WIDE_ROW_STEPS] steps down when CNO refuses the larger. */
    val rowsAsked: Int = CnoSource.WIDE_ROWS,
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
    /**
     * Novig's own public catalog: a bet's exact bet-slip link, or null when it can't name exactly
     * one outcome ([NovigBetFinder]). Asked before CNO, so links don't depend on CNO answering
     * (Tj, 2026-09-27: "make it so vigilant can open the bet in novig even if it can't reach cno
     * servers"), and CNO is spared those requests.
     */
    private val catalog: (suspend (CnoRow) -> String?)? = null,
) {
    private val _state = MutableStateFlow(CnoState())
    val state: StateFlow<CnoState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** When the list was last written to disk (guarded by [mutex]). */
    private var savedAtMs = Long.MIN_VALUE / 2

    /** Shows the list saved by the last read, before any network. */
    suspend fun load() {
        linkStore?.let { runCatching { it.read().links }.getOrNull() }?.forEach { (k, v) -> novigLinks.putIfAbsent(k, v) }
        publishLinks()
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
                    lastPause = if (retry != null) "the list: $message" else it.lastPause,
                    lastPauseAtMs = if (retry != null) clock() else it.lastPauseAtMs,
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

    // ---- The scan study's wide read ----------------------------------------------------------------

    private val _wide = MutableStateFlow(CnoWideState())

    /** The wide read's state: its last rows and how the reads are going. Nothing in the app's list, alerts, auto-bet or widget reads it. */
    val wide: StateFlow<CnoWideState> = _wide.asStateFlow()

    private val wideMutex = Mutex()

    /** Milliseconds until a wide read may start (0 = now): [WIDE_GAP_MS] after the last, longer after failures, never inside a pause CNO asked for. */
    fun waitForWideMs(now: Long = clock()): Long {
        val w = _wide.value
        val gap = w.lastAttemptMs?.let { it + wideGapMs(w.errors) - now } ?: 0L
        val pause = _state.value.pausedUntilMs?.let { it - now } ?: 0L
        return maxOf(0L, gap, pause)
    }

    /**
     * The scan study's second read of [url] (Tj, 2026-10-03: "log all cno finds on every scan … even if these bets don't meet my criteria"): CNO's numeric filters
     * opened right up ([CnoSource.fetchWide]), kept in [wide] and nowhere else, so the list ([state]) and everything built on it see exactly what they did before.
     * It goes after a list read, never faster than every [WIDE_GAP_MS] (CNO's robots.txt asks for 30 s between pages), only when CNO's odds have moved since the last
     * one, never while the list is failing or CNO asked for a pause, and it pauses every lane when CNO asks (as any read does). A failure is [CnoWideState.error], never
     * the list's. Returns whether a read happened.
     */
    suspend fun readWide(url: String, filters: CnoFilters): Boolean = wideMutex.withLock {
        val now = clock()
        val narrow = _state.value
        if (waitForWideMs(now) > 0 || narrow.error != null) return@withLock false
        // CNO's odds haven't moved since the last wide read: the same rows again.
        val newest = narrow.snapshot?.takeIf { it.url == url }
        val last = _wide.value.snapshot
        if (newest != null && last != null && last.url == url && newest.dataAtMs <= last.dataAtMs) return@withLock false
        val asked = _wide.value.rowsAsked
        _wide.update { it.copy(lastAttemptMs = now) }
        try {
            val snap = source.fetchWide(url, filters, asked) ?: return@withLock false
            // CNO answered with its red message and no table: the row count (or a filter) was refused; try fewer next time.
            if (snap.rows.isEmpty() && snap.note != null) {
                _wide.update { it.copy(error = "CrazyNinjaOdds: ${snap.note}", errors = it.errors + 1, rowsAsked = nextRows(asked)) }
            } else {
                _wide.update { it.copy(snapshot = snap, error = null, errors = 0, reads = it.reads + 1) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) throw kotlinx.coroutines.CancellationException("CNO wide read cancelled").apply { initCause(e) }
            val message = (e as? CnoException)?.message ?: "Couldn't read CrazyNinjaOdds (${e.message ?: e.javaClass.simpleName})"
            (e as? CnoException)?.let { pauseFor(it, "the study's wide read") }
            _wide.update { it.copy(error = message, errors = it.errors + 1, rowsAsked = if ((e as? CnoException)?.retryAfterSeconds == null) nextRows(asked) else asked) }
        }
        true
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
        (result.exceptionOrNull() as? CnoException)?.let { e -> pauseFor(e, "a bet's books") }
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

    /**
     * One bet's books read now for the Tracker's "Check odds now" ([CnoSource.booksBulk]): several run at once, so it doesn't queue
     * behind [booksMutex] (one read at a time is what made a hundred bets take five minutes), and the answer isn't kept in [books]
     * (that's for the cards someone asked about). A read CNO answers with a pause (busy, refusing) pauses every read, as
     * [loadBooks] does. Null when the page couldn't be read.
     */
    /**
     * [row]'s game page for Check odds now: null when CNO answered but its page doesn't list the bet (a line that moved, a prop pulled);
     * throws when CNO didn't answer (after noting any pause it asked for), so the two are told apart ([BetRecheck]).
     */
    suspend fun readBooks(row: CnoRow): CnoBooksView? = try {
        source.booksBulk(row)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        (e as? CnoException)?.let { pauseFor(it, "Check odds now") }
        throw e
    }

    /** CNO asked for a pause ([CnoException.retryAfterSeconds]): every lane waits it out, and Diagnostics keeps what was asked, by which read. */
    private fun pauseFor(e: CnoException, what: String) {
        val sec = e.retryAfterSeconds ?: return
        _state.update {
            it.copy(
                pausedUntilMs = maxOf(it.pausedUntilMs ?: 0L, clock() + sec * 1000L),
                lastPause = "$what: ${e.message ?: "paused"} (${sec}s)", lastPauseAtMs = clock(),
            )
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

    /** Links by [linkKey], oldest first (so the file keeps the newest [LINKS_KEEP]); taps and lanes share it. */
    private val novigLinks: MutableMap<String, String> = java.util.Collections.synchronizedMap(LinkedHashMap())

    private val _links = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * Every known bet link by [linkKey], for showing a bet both scanners list once (by its Novig
     * outcome, [outcomeIdOf]). Updated as links are found.
     */
    val links: StateFlow<Map<String, String>> = _links.asStateFlow()

    private fun publishLinks() {
        _links.value = synchronized(novigLinks) { LinkedHashMap(novigLinks) }
    }
    private val linkTriedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val linkSaveMutex = Mutex()

    @Volatile
    private var linkLaneAtMs = Long.MIN_VALUE / 2

    /** [row]'s Novig link if it's already known: no network, so a tap opens the bet at once. */
    fun cachedLink(row: CnoRow): String? = novigLinks[linkKey(row)]


    /** Keeps a link found elsewhere (a tap's lookup in Novig's catalog) like one this feed found. */
    suspend fun rememberLink(row: CnoRow, link: String) {
        if (novigLinks.put(linkKey(row), link) != link) saveLinks()
    }

    /** When each row was last looked up in Novig's catalog (a miss is tried again after [LINK_RETRY_MS]). */
    private val catalogTriedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * [row]'s exact link from Novig's catalog ([catalog]), kept like CNO's; null when the catalog
     * can't say. Never touches CNO.
     */
    suspend fun catalogLink(row: CnoRow): String? {
        val find = catalog ?: return null
        novigLinks[linkKey(row)]?.let { return it }
        val link = try {
            find(row)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        novigLinks[linkKey(row)] = link
        saveLinks()
        return link
    }

    /**
     * The Novig app link for [row]: `novigapp://events/<outcome id>/cno`, which opens Novig with
     * that exact bet in its bet slip (RESEARCH.md §20). Cached, and kept on disk: a line's link
     * doesn't change. Null when CNO couldn't say.
     */
    suspend fun novigLink(row: CnoRow): String? {
        row.betUrl ?: return null
        val key = linkKey(row)
        novigLinks[key]?.let { return it }
        val link = try {
            source.novigLink(row)?.let(::appLink)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) throw kotlinx.coroutines.CancellationException("cancelled").apply { initCause(e) }
            // CNO asked for a pause (busy, or refusing): every lane waits it out, the list included.
            (e as? CnoException)?.let { pauseFor(it, "a bet slip link") }
            null
        } ?: return null
        novigLinks[key] = link
        saveLinks()
        return link
    }

    private suspend fun saveLinks() {
        publishLinks()
        val disk = linkStore ?: return
        linkSaveMutex.withLock {
            // The newest [LINKS_KEEP]: a season of lines would otherwise pile up.
            val all = synchronized(novigLinks) { LinkedHashMap(novigLinks) }
            val kept = if (all.size <= LINKS_KEEP) all else all.entries.toList().takeLast(LINKS_KEEP).associate { it.key to it.value }
            runCatching { disk.update { CnoLinks(kept) } }
        }
    }

    /**
     * Looks up the Novig links of the top [top] bets in [rows] ahead of any tap (Tj, 2026-09-27:
     * "sometimes they pull up the novig bet slip, but sometimes they don't"; then "make it so
     * vigilant can open the bet in novig even if it can't reach cno servers"). Novig's own catalog
     * first ([catalog]: two public Novig reads per game, kept five minutes, [CATALOG_GAP_MS] apart),
     * then, only for bets the catalog can't pin to one outcome, CNO's link: one small request per
     * bet, [LINK_GAP_MS] apart, after the list's reads and never while CNO is failing or asked for
     * a pause. Runs until cancelled; the caller runs it alongside [watch].
     */
    suspend fun keepLinksFresh(rows: Flow<List<CnoRow>>, top: Int = LINKS_TOP) {
        rows.map { list -> list.take(top) }
            .distinctUntilChanged { a, b -> a.mapTo(HashSet()) { it.key } == b.mapTo(HashSet()) { it.key } }
            .collectLatest { list ->
                while (true) {
                    val now = clock()
                    val missing = list.filter { novigLinks[linkKey(it)] == null }
                    if (missing.isEmpty()) awaitCancellation()
                    // 1. Novig's catalog, which doesn't depend on CNO at all.
                    val fromCatalog = if (catalog == null) null else missing.firstOrNull { r -> catalogTriedAt[linkKey(r)]?.let { now - it >= LINK_RETRY_MS } ?: true }
                    if (fromCatalog != null) {
                        catalogTriedAt[linkKey(fromCatalog)] = now
                        catalogLink(fromCatalog)
                        delay(CATALOG_GAP_MS)
                        continue
                    }
                    // 2. CNO, for what the catalog couldn't name (and every bet when there's no catalog).
                    val forCno = missing.filter { it.betUrl != null }
                    val next = forCno.firstOrNull { r -> linkTriedAt[r.betUrl!!]?.let { now - it >= LINK_RETRY_MS } ?: true }
                    if (next == null) {
                        val tried = forCno.mapNotNull { linkTriedAt[it.betUrl!!] } + missing.mapNotNull { catalogTriedAt[linkKey(it)] }
                        val soonest = tried.minOrNull() ?: now
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

        /** The least time between two of the scan study's wide reads (and the first wait after a failure, doubling to [WIDE_MAX_GAP_MS]). */
        const val WIDE_GAP_MS = 30_000L
        const val WIDE_MAX_GAP_MS = 10 * 60_000L

        /** The row counts the wide read asks for, largest first: the next one down when CNO answers with an error. */
        val WIDE_ROW_STEPS = listOf(CnoSource.WIDE_ROWS, 500, 200)

        fun nextRows(asked: Int): Int = WIDE_ROW_STEPS.firstOrNull { it < asked } ?: WIDE_ROW_STEPS.last()

        /** The wait before a wide read after [errors] failed ones in a row. */
        fun wideGapMs(errors: Int): Long = minOf(WIDE_MAX_GAP_MS, WIDE_GAP_MS shl errors.coerceIn(0, 5))

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

        /**
         * …re-reading each one's books after this long: under the 5 minutes a book page may be
         * compared at all ([com.tjshea.vigilant.data.scanner.Freshness], RESEARCH.md §24), with a minute
         * to spare for a slow read…
         */
        const val AGREE_TTL_MS = 4 * 60_000L

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

        /** The listed bets whose links are looked up ahead of a tap (most come from Novig's catalog, cheap). */
        const val LINKS_TOP = 30

        /** CNO's link for one the catalog can't name: one every this long… */
        const val LINK_GAP_MS = 3_000L

        /** …a failed one again after this long. */
        const val LINK_RETRY_MS = 60_000L

        /** Links kept on disk. */
        const val LINKS_KEEP = 400

        /** Between two lookups in Novig's catalog (most cost nothing: the game's markets are kept). */
        const val CATALOG_GAP_MS = 300L

        /** Where [row]'s link is kept: CNO's deeplink (one per line, whatever the price), else the row. */
        fun linkKey(row: CnoRow): String = row.betUrl ?: "row:${row.key}"

        /** The Novig outcome a bet link opens (`novigapp://events/<id>[/cno]`), or null for a game link. */
        fun outcomeIdOf(link: String?): String? =
            link?.takeIf { it.startsWith("novigapp://events/") }?.removePrefix("novigapp://events/")?.substringBefore('/')?.takeIf { it.isNotEmpty() }

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
