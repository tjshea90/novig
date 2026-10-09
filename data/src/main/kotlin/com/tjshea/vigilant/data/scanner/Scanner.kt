package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.MemoryGuard
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.PartialReferenceException
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceException
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.ScanContext
import com.tjshea.vigilant.data.reference.LineKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Where a scan is, for the progress line. */
data class ScanProgress(val step: String, val done: Int = 0, val total: Int = 0)

/** How one fair-odds provider did on the last scan. */
data class SourceReport(
    val id: String,
    val name: String,
    /** Leagues it answered, fresh this scan or re-used from an earlier one. */
    val fetched: Int,
    val reused: Int,
    /** Novig games it matched. */
    val matched: Int,
    val error: String?,
    /** Leagues a fallback source wasn't asked for (the API it backs up had already given them, RESEARCH.md §23), or whose credits are paced. */
    val standingBy: Int = 0,
    /** Leagues it was not asked for because the scan's window held nothing to read there ([com.tjshea.vigilant.data.reference.LowUsageSource]); not counted in [fetched]. */
    val skipped: Int = 0,
    /** Why a paced source held back this scan ([com.tjshea.vigilant.data.keys.CreditsHeldBackException]), for Diagnostics. */
    val heldBack: String? = null,
)

/** What a [Scanner.recheck] read: [read] books refreshed, [failed] not (shown as they were). */
data class RecheckReport(val result: ScanResult?, val read: Int, val failed: Int, val error: String?)

/** What happened on one scan, for the status line and banners. */
data class ScanReport(
    val result: ScanResult?,
    val errors: List<String>,
    /** Set when Novig throttled the scan: it stopped early for this many seconds. */
    val retryAfterSeconds: Int?,
    val booksFetched: Int,
    val booksNotModified: Int,
    /** Books Novig couldn't refresh this time, shown from the previous scan. */
    val booksFromCache: Int,
    val booksViaKey: Int,
    val novigCatalogAtMs: Long?,
    val sources: List<SourceReport>,
    val creditsRemaining: Int?,
    /** Early edges' books read a second time at the end of a long scan ([Scanner.REREAD_AFTER_MS]). */
    val booksReread: Int = 0,
    /** Books the connected key's websocket pushed (no request each; RESEARCH.md §27). Part of [booksFetched]. */
    val booksViaPush: Int = 0,
    /** Where the scan's time went (Settings › Betting & Novig account › Novig API key). Null for a scan that did nothing. */
    val timing: ScanTiming? = null,
    /** Planned lines not read because the scan ran long enough that their other books' odds would be too old to show. */
    val booksTooLate: Int = 0,
)

/**
 * Runs a scan when (and only when) Tj asks for one: the Scan button or pull-to-refresh (his rule,
 * 2026-09-25), or background auto-scan once he turns it on (2026-09-28). Nothing here runs on a
 * timer of its own. Per scan, all at once:
 *
 *  1. Novig catalog (events + markets): re-used for [catalogTtlMs] (3 min) if the leagues and
 *     window haven't changed. New alternate lines appear slowly; prices move fast.
 *  2. Fair odds from every enabled [ReferenceSource], providers in parallel, leagues one at a time
 *     within a provider. A metered provider (The Odds API credits, pinnapi's 100/day) is re-used
 *     for its [ReferenceSource.reuseMs]. A failed call keeps the previous snapshot while it's
 *     younger than the stale limit, so one hiccup doesn't blank the feed. Where two providers carry
 *     the same books, the second is only a fallback ([ReferenceSource.fallbackFor], RESEARCH.md §23):
 *     it waits for the first, is told what that one gave, and is called only for the rest.
 *  3. Plan: match games, choose which Novig markets to price (capped per game).
 *  4. Novig books for the plan, paced by [NovigSource.books] to stay under Novig's rate limits.
 *
 * Steps 3–4 don't wait for step 2 (Tj, 2026-09-25: "this app is very slow"). Novig's paced book
 * reads are the long pole, so they start the moment the board is in, planned from whatever fair
 * odds are known (the last scan's, until this scan's arrive), and re-planned each time a provider
 * answers. Books go most-promising first ([fetchOrder]): open bets, then lines that were +EV or
 * close to it last scan, then props and period lines, then main lines. Every few books the scan
 * re-prices and hands the result to `onPartial`, so the feed fills in while the scan runs.
 * Each market is read at most once per scan, and never more than [ScanSettings.maxBooksPerScan].
 *
 * [reprice] re-prices everything already fetched under new settings, with no network at all.
 */
class Scanner(
    private val novig: NovigSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val catalogTtlMs: Long = 3 * 60_000L,
    /** Milliseconds on a steady clock, for [ScanTiming] only (the scan's own time is [clock]). */
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000L },
    /**
     * A scanner that prices only the `pinned` markets (Tj's open bets, Tj 2026-09-29: "update the EV for every single open bet"), never the
     * board: its catalog is cut to those markets and their games, so sources that pick games from the board ask for those games alone;
     * it never tells Novig's live feed what to watch (the feed scanner's subscription stays as it is) and doesn't re-read early edges.
     * Kept as its own instance, so it can't touch the feed scanner's catalog, books or fair-odds snapshots.
     */
    private val betsOnly: Boolean = false,
    /** A plan this big is priced for a partial result at most every [publishMinMs] (tests use small ones). */
    private val bigPlanMarkets: Int = BIG_PLAN_MARKETS,
    private val publishMinMs: Long = PUBLISH_MIN_MS,
    /** The live feed is handed its markets once the plan is in, or this long into a scan ([BookPump.feedStream]). */
    private val streamHoldMs: Long = STREAM_HOLD_MS,
    /** How many markets the live feed holds at once: more unread than this and there's no reason to wait. */
    private val streamRoom: Int = com.tjshea.vigilant.data.novig.stream.NovigStream.MAX_MARKETS,
) : OddsScanner {
    private data class Catalog(
        val leagues: Set<String>,
        val includeLive: Boolean,
        /** How many hours past now the markets were read for ([catalogHours]). */
        val hours: Int,
        val types: Set<String>,
        val events: List<NovigEvent>,
        val markets: List<NovigMarket>,
        val fetchedAtMs: Long,
    )

    private data class Cached(val snapshot: RefSnapshot, val requestKey: String)

    private val mutex = Mutex()
    private var catalog: Catalog? = null

    /** Latest snapshot per `"$sourceId|$league"`. */
    private val references = HashMap<String, Cached>()

    /** The `"$sourceId|$league"` keys answered this scan (fetched, re-used or partly): what a fallback may lean on. */
    private val answered = HashSet<String>()
    private var creditsRemaining: Int? = null
    /** Plans cached by what went into them: key true = only fair odds young enough to show. */
    private val plans = HashMap<Boolean, Pair<Any, Plan>>()
    private var books: Map<String, NovigBook> = emptyMap()
    private var pinned: Set<String> = emptySet()

    /**
     * The markets Novig's board listed open at the last scan's catalog (a bets-only scan's is cut to its bets): what says whether a bet's
     * market is still there when nothing prices it.
     */
    val listed: Set<String> get() = catalog?.markets?.mapTo(HashSet()) { it.marketId } ?: emptySet()

    /** Each market's best EV on the last scan, to read the likeliest +EV lines first next time. */
    private var lastEv: Map<String, Double> = emptyMap()

    /**
     * Lines the last scan left unread because it ran past the time their other books' odds could be shown
     * ([ScanReport.booksTooLate]): read before the other never-priced lines this time, so back-to-back scans with no
     * limit cover the whole time window between them instead of re-reading the same first part.
     */
    private var leftLastScan: Set<String> = emptySet()

    /** Each plan's fair lines, worked out once however many times it's priced ([FairMemo]). */
    private val fairMemo = FairMemo()

    override suspend fun scan(
        requested: ScanSettings,
        offered: List<ReferenceSource>,
        /** Markets always priced past the per-game line cap: the ones Tj has open bets on. */
        pinned: Set<String>,
        onProgress: (ScanProgress) -> Unit,
        /**
         * Everything priced so far, each time another batch of Novig prices lands. Its feed lists
         * only prices read this scan ([ScanResult.freshSinceMs]); the final result is the report's.
         */
        onPartial: (ScanResult) -> Unit,
    ): ScanReport = mutex.withLock {
        // The settings as the scan reads them ([ScanSettings.effective]): applied here, once.
        val settings = requested.effective(forBets = betsOnly)
        Freshness.sgoMode = settings.sgoPro
        Freshness.sgoMaxAgeMs = settings.sgoMaxAgeMinutes.coerceIn(10, 30) * 60_000L
        val sources = readable(offered, settings)
        this.pinned = pinned
        // What a bets-only pass fetched was asked for these bets' games alone: never re-used for the next pass's bets (a board that
        // can't be read leaves no catalog at all, not the last pass's).
        if (betsOnly) {
            synchronized(references) { references.clear() }
            catalog = null
            books = emptyMap()
        }
        val now = clock()
        val t0 = elapsed()
        val errors = ArrayList<String>()
        val leagues = settings.selectedLeagues
        if (leagues.isEmpty()) return@withLock report(null, errors, null, null, emptyList())

        val ordered = sources.sortedBy { SOURCE_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        dropUnusedReferences(ordered, leagues)
        val progress = Progress(ordered.sumOf { s -> leagues.count { s.supports(it) } } + 1, onProgress)
        progress.emit()
        // "source|league" for every source that has answered (or failed, or stood by) for a league this scan.
        val settled = java.util.Collections.synchronizedSet(HashSet<String>())
        val pump = BookPump(settings, now, progress, onPartial, ordered, settled, t0)
        val boardAt = java.util.concurrent.atomic.AtomicLong(0)
        val sourceMs = java.util.Collections.synchronizedList(ArrayList<Pair<String, Long>>())
        var fairAt = 0L

        synchronized(answered) { answered.clear() }
        val sourceReports = coroutineScope {
            val catalogJob = async {
                refreshCatalog(settings, catalogTypes(settings, ordered), now, errors)
                boardAt.set(elapsed() - t0)
                progress.fairDone()
            }
            // First choices start at once; a fallback (RESEARCH.md §23) waits for the source it backs up.
            val jobs = HashMap<String, kotlinx.coroutines.Deferred<SourceReport>>()
            for (source in ordered.sortedBy { if (it.fallbackFor == null) 0 else 1 }) {
                val first = source.fallbackFor?.let { jobs[it] }
                jobs[source.id] = async {
                    first?.await()
                    // A source that picks its games from Novig's board waits for the board, and so
                    // does a fallback (it's told which of Novig's games the first source covered).
                    val context = if (source.needsCatalog || first != null) {
                        catalogJob.await()
                        val board = catalog?.let { ScanContext(it.events, it.markets, now) } ?: ScanContext(now = now)
                        if (first != null) covering(source.fallbackFor!!, leagues, board) else board
                    } else {
                        null
                    }
                    // A slow source's game lines for every league before any of its props (Kalshi, [ReferenceSource.linesFirst]).
                    if (source.linesFirst && context == null) {
                        linesFirst(source, leagues, settings, now) { league ->
                            settled += "${source.id}|${league.novigName}|lines"
                            pump.wake()
                        }
                    }
                    val report = fetchSource(source, leagues, settings, now, errors, context, fallback = first != null) { league ->
                        settled += "${source.id}|${league.novigName}"
                        progress.fairDone()
                        pump.wake()
                    }
                    // When this source finished, for Settings' timing line: the slowest one sets when bets first show.
                    if (report.fetched + report.reused > 0) sourceMs += source.displayName to (elapsed() - t0)
                    report
                }
            }
            // Novig's prices start as soon as the board is in, alongside the fair odds.
            val pumpJob = async {
                catalogJob.await()
                pump.run()
            }
            val reports = ordered.map { jobs.getValue(it.id) }.awaitAll()
            fairAt = elapsed() - t0
            pump.fairOddsDone()
            pumpJob.await()
            // A long scan's first edges were read minutes before its last books: read them again.
            if (!betsOnly) pump.rereadEarlyEdges()
            if (pump.readFrom != null) pump.readTo = elapsed() - t0
            reports
        }

        // Priced only from fair odds young enough to bet on, like the partial results. An old
        // snapshot can survive a scan (a metered provider that ran out skips its later leagues),
        // and it still steers which books get read, but it never prices the feed.
        val currentPlan = catalog?.let { planFor(it, settings, now, youngFairOnly = true, headroomMs = Freshness.MIN_SHOWN_MS) }
        if (currentPlan != null) {
            // Anything planned but not read this scan (the per-scan cap, or Novig asked us to stop)
            // keeps the last scan's price, counted as such.
            val merged = HashMap<String, NovigBook>()
            for (id in currentPlan.marketIds) {
                val fresh = pump.fresh[id]
                if (fresh != null) {
                    merged[id] = fresh
                } else {
                    books[id]?.let { merged[id] = it; if (id !in pump.requested) pump.fromCache++ }
                }
            }
            books = merged
        }
        // Refused in this scan, or earlier (a scan starting while the key is set aside never tries it, and was only slow with no word why).
        val keyProblem = pump.keyProblem ?: novig.keyDown(now)
        keyProblem?.let { errors += "Novig key: $it $PUBLIC_PRICES" }
        // The key's live feed failing only costs speed: its REST route read the rest.
        if (keyProblem == null) novig.pushProblem(now)?.let { errors += "Novig live feed: $it Prices were read one by one instead." }
        if (pump.failed > 0 && pump.lastError != null) {
            errors += "Novig prices: ${pump.lastError}" + if (pump.fromCache > 0) " (${pump.fromCache} shown from the last scan)" else ""
        } else if (pump.lastError != null) {
            errors += "Novig prices: ${pump.lastError}"
        }
        progress.finish()

        leftLastScan = pump.tooLate.toSet()
        val result = currentPlan?.let { Pricing.price(it, books, settings, now, fairMemo) }
        result?.let { r ->
            lastEv = r.opportunities.mapNotNull { o -> o.evPercent?.let { o.market.marketId to it } }
                .groupBy({ it.first }, { it.second }).mapValues { (_, evs) -> evs.max() }
        }
        val reports = sourceReports.map { r -> r.copy(matched = currentPlan?.events?.count { r.id in it.providers } ?: 0) }
        ScanReport(
            result = result,
            errors = errors,
            retryAfterSeconds = pump.retryAfter,
            booksFetched = pump.fetched,
            booksNotModified = pump.notModified,
            booksFromCache = pump.fromCache,
            booksViaKey = pump.viaKey,
            novigCatalogAtMs = catalog?.fetchedAtMs,
            sources = reports,
            creditsRemaining = creditsRemaining,
            booksReread = pump.reread,
            booksViaPush = pump.viaPush,
            booksTooLate = pump.tooLate.size,
            timing = ScanTiming(
                boardAtMs = boardAt.get(),
                fairAtMs = fairAt,
                novigFromMs = pump.readFrom,
                novigToMs = pump.readTo,
                firstBetAtMs = pump.firstBetAt ?: result?.takeIf { it.feed(settings).isNotEmpty() }?.let { elapsed() - t0 },
                totalMs = elapsed() - t0,
                refused = pump.refused,
                leftTooLate = pump.tooLate.size,
                sourceMs = synchronized(sourceMs) { sourceMs.toList() },
                liveFeedAtMs = pump.streamAt,
                liveFeedAsked = pump.streamAsked,
                // The feed's whole worth is what it holds: asked for 2,000 at 4 s and holding 315 at the end is the number that says it was slow (Tj's
                // v0.58.2 file: 315 pushed of 2,000 asked in a 299 s scan, REST-bound at 14.5 a second).
                liveFeedHeld = pump.streamAt?.let { novig.pushed(pump.streamIds).size },
                pace = pump.pace,
            ),
        )
    }

    /** One combined progress line: fair odds (calls answered) and Novig prices (books read). */
    private class Progress(private val fairTotal: Int, private val out: (ScanProgress) -> Unit) {
        private val fair = AtomicInteger(0)
        @Volatile var booksDone = 0
        @Volatile var booksTotal = 0
        @Volatile var reading = false

        fun fairDone() {
            fair.incrementAndGet()
            emit()
        }

        @Synchronized
        fun emit() {
            val f = fair.get()
            out(
                when {
                    !reading -> ScanProgress("Novig board and fair odds", f, fairTotal)
                    f < fairTotal -> ScanProgress("Fair odds $f/$fairTotal · Novig prices", booksDone, booksTotal)
                    else -> ScanProgress("Novig prices", booksDone, booksTotal)
                },
            )
        }

        fun finish() {
            if (reading) emit()
        }

        /** The last pass: [done] of [total] early edges read again. */
        fun rereading(done: Int, total: Int) {
            out(ScanProgress("Rechecking the first edges", done, total))
        }
    }

    /**
     * Reads Novig's books while the fair odds are still arriving: plans from what's known, reads the
     * most promising unread markets a few at a time, publishes a partial result, and re-plans. Ends
     * when every provider has answered and nothing planned is left unread (or the cap is reached, or
     * Novig asked us to stop).
     */
    private inner class BookPump(
        private val settings: ScanSettings,
        private val now: Long,
        private val progress: Progress,
        private val onPartial: (ScanResult) -> Unit,
        /** The scan's fair-odds sources, and which of them have answered each league ("source|league"). */
        private val sources: List<ReferenceSource> = emptyList(),
        private val settled: Set<String> = emptySet(),
        /** The scan's start on [elapsed], for [ScanTiming]. */
        private val startedAt: Long = elapsed(),
    ) {
        /** [ScanTiming]: when the first price was asked for and the last came in, and the first bet shown. */
        var readFrom: Long? = null
        var readTo: Long? = null
        var firstBetAt: Long? = null

        /** Prices Novig refused this scan (each pauses and slows the rest). */
        var refused = 0

        /** The scan ended early because the app's heap was nearly full ([MemoryGuard.critical]). */
        var memoryStopped = false

        /** When [publish] last priced the whole plan, on [elapsed] (a big plan isn't priced again for every batch). */
        private var lastPublishAt = Long.MIN_VALUE / 2

        /** Lines left unread because the scan ran past the time their other books' odds could still be shown. */
        val tooLate = HashSet<String>()

        /** Whether [pm], priced now, would stay listed [Freshness.MIN_SHOWN_MS] on fair odds read as the scan began. */
        private fun canStillShow(pm: PlannedMarket): Boolean {
            val t = clock()
            return t - now <= Freshness.maxAgeMs(pm.event.startsTs, t) - Freshness.MIN_SHOWN_MS
        }

        val requested = LinkedHashSet<String>()
        val fresh = HashMap<String, NovigBook>()
        var fetched = 0
        var notModified = 0
        var fromCache = 0
        var viaKey = 0
        /** Books the key's websocket pushed: read with no request. */
        var viaPush = 0
        /**
         * When this scan handed the live feed its markets ([feedStream]), on [elapsed] from [startedAt], and how many; null until it did.
         * The feed takes ONE big subscribe per ~2 minutes (its 512-token bucket refills at 4 a second), so it goes once per scan.
         */
        var streamAt: Long? = null
        var streamAsked = 0

        /** The markets the feed was asked for ([streamAsked] of them), to count how many it held at the scan's end. */
        var streamIds: Set<String> = emptySet()

        /** What paced the batches' reads, merged ([com.tjshea.vigilant.data.novig.ReadPace.merge]); null until a batch reported. */
        var pace: com.tjshea.vigilant.data.novig.ReadPace? = null
        var failed = 0
        var retryAfter: Int? = null
        var lastError: String? = null
        var keyProblem: String? = null

        private val signal = Channel<Unit>(Channel.CONFLATED)

        @Volatile
        private var fairDone = false

        /** A provider answered: there may be new lines to read. */
        fun wake() {
            signal.trySend(Unit)
        }

        fun fairOddsDone() {
            fairDone = true
            signal.trySend(Unit)
        }

        suspend fun run() {
            val cat = catalog ?: return
            val cap = settings.maxBooksPerScan.coerceAtLeast(1)
            while (requested.size < cap && retryAfter == null) {
                // The app's heap is fixed (Tj's Diagnostics, 2026-10-01: an OutOfMemoryError at 256 MB mid-scan): drop what can be rebuilt as it
                // fills, and when it is still nearly full after a collection, end the scan with what's read instead of crashing the app.
                if (MemoryGuard.pressing()) {
                    novig.trimCaches()
                    previewCache = null
                }
                if (MemoryGuard.critical()) {
                    lastError = "stopped reading early because the app's memory was nearly full (${MemoryGuard.text()}). Lower \"Novig prices per scan\" or pick fewer leagues in Settings."
                    memoryStopped = true
                    return
                }
                // Read before planning: a provider answering mid-plan still wakes the next pass.
                val lastPass = fairDone
                val plan = planFor(cat, settings, now)
                val preview = preview(plan, settings, now)
                // A line whose other books' odds (read as the scan began) couldn't stay listed a couple of minutes once
                // priced is left for the next scan: a long scan (a big budget, public routes) never shows a bet already
                // on its way out (Tj, 2026-09-28: "consider if increasing this number could be beneficial or dangerous").
                // A game that kicked off during a long scan has no pregame book any more (Novig answers 404: Tj's v0.59.1 file, every kickoff): not read.
                val t = clock()
                val pending = plan.markets.filter { it.market.marketId !in requested && it.market.marketId !in tooLate }
                    .filter { pm -> settings.includeLive || pm.event.startsTs > t - Planner.STARTED_GRACE_MS }
                    .filter { pm -> canStillShow(pm).also { ok -> if (!ok) tooLate += pm.market.marketId } }
                // A bets-only pass would replace the feed scanner's subscription with a few dozen markets.
                if (!betsOnly) feedStream(plan, pending, preview, lastPass, cap)
                if (pending.isEmpty()) {
                    if (lastPass) return
                    signal.receive()
                    continue
                }
                // Books the websocket already holds cost no request: all of them at once.
                val pushed = novig.pushed(pending.map { it.market.marketId })
                if (pushed.isNotEmpty()) {
                    val ids = fetchOrder(pending.filter { it.market.marketId in pushed }, settings, preview)
                        .take(cap - requested.size).map { it.market.marketId }
                    requested += ids
                    ids.forEach { id -> fresh[id] = pushed.getValue(id) }
                    if (readFrom == null) readFrom = elapsed() - startedAt
                    fetched += ids.size
                    viaPush += ids.size
                    progress.reading = true
                    progress.booksDone += ids.size
                    progress.booksTotal = minOf(cap, requested.size + pending.size - ids.size)
                    progress.emit()
                    publish(cat)
                    continue
                }
                val chunk = fetchOrder(pending, settings, preview).take(minOf(novig.batchSize().coerceAtLeast(1), cap - requested.size))
                val ids = chunk.map { it.market.marketId }
                requested += ids
                if (readFrom == null) readFrom = elapsed() - startedAt
                progress.reading = true
                progress.booksTotal = minOf(cap, requested.size + pending.size - ids.size)
                val base = progress.booksDone
                progress.emit()
                try {
                    val batch = novig.books(ids) { d, _ ->
                        progress.booksDone = base + d
                        progress.emit()
                    }
                    fresh.putAll(batch.books)
                    fetched += batch.fetched
                    notModified += batch.notModified
                    fromCache += batch.fromCache
                    viaKey += batch.viaKey
                    viaPush += batch.viaPush
                    failed += batch.failed
                    refused += batch.refused
                    batch.pace?.let { p -> pace = pace?.merge(p) ?: p }
                    batch.lastError?.let { lastError = it }
                    if (keyProblem == null) keyProblem = batch.keyProblem
                    // Novig asked us to stop for a while: the rest keep the last scan's prices.
                    batch.retryAfterSeconds?.let { retryAfter = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    return
                }
                progress.booksDone = base + ids.size
                publish(cat)
            }
        }

        /**
         * The key's live feed (RESEARCH.md §27, §63): opened at the first plan so its bucket refills while the fair odds come in, then handed
         * its markets ONCE, when the plan has filled in (every fair source answered, or more unread lines than it can hold, or
         * [STREAM_HOLD_MS] gone). Its one bulk subscribe is worth up to 2,000 books with no request, and the next takes ~2 minutes, so it
         * isn't spent on the first source's few hundred lines (Tj's 2026-10-02 Diagnostics: 113 of 4,588 prices by live feed) nor on lines
         * the requests already read. What it already holds for this plan goes first (dropping it costs tokens and a still-current book),
         * then the unread lines in reading order.
         */
        private fun feedStream(plan: Plan, pending: List<PlannedMarket>, preview: Map<String, Double>, lastPass: Boolean, cap: Int) {
            if (streamAt != null) return
            novig.openFeed()
            val waited = elapsed() - startedAt
            if (!lastPass && pending.size < streamRoom && waited < streamHoldMs && waiting().isNotEmpty()) return
            val held = novig.pushed(plan.marketIds).keys
            val unread = fetchOrder(pending, settings, preview).take((cap - requested.size).coerceAtLeast(0)).map { it.market.marketId }.filter { it !in held }
            val ids = plan.markets.map { it.market.marketId }.filter { it in held } + unread
            streamAt = waited
            streamAsked = minOf(ids.size, streamRoom)
            streamIds = ids.take(streamAsked).toHashSet()
            if (ids.isNotEmpty()) novig.watch(ids)
        }

        /**
         * Novig reads again (at most [MAX_REREAD]) the feed's bets whose price was read over
         * [REREAD_AFTER_MS] ago, best EV first: in a scan of hundreds of books the first edges are
         * minutes old by the end (Tj, 2026-09-28: up to 1,200 prices a scan, and alerts that open the
         * bet at once). Nothing when Novig asked to stop, or no edge is that old.
         */
        suspend fun rereadEarlyEdges() {
            val cat = catalog ?: return
            if (retryAfter != null || fresh.isEmpty()) return
            val cutoff = clock() - REREAD_AFTER_MS
            val shown = planFor(cat, settings, now, youngFairOnly = true, headroomMs = Freshness.MIN_SHOWN_MS)
            val merged = HashMap(books).apply { putAll(fresh) }
            val ids = Pricing.price(shown, merged, settings, now, fairMemo).opportunities
                .filter { o ->
                    val ev = o.evPercent ?: return@filter false
                    val read = fresh[o.market.marketId]?.fetchedAtMs ?: return@filter false
                    ev >= settings.minEvPercent && ev <= settings.maxEvPercent && read < cutoff
                }
                .sortedByDescending { it.evPercent }
                .map { it.market.marketId }
                .distinct()
                .take(MAX_REREAD)
            if (ids.isEmpty()) return
            progress.rereading(0, ids.size)
            try {
                val batch = novig.books(ids) { d, t -> progress.rereading(d, t) }
                // Only books actually read again replace the first read (a failed one keeps it).
                for ((id, book) in batch.books) if (book.fetchedAtMs >= cutoff) fresh[id] = book
                reread += batch.fetched + batch.notModified
                refused += batch.refused
                batch.retryAfterSeconds?.let { retryAfter = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The first reads stand; the feed still shows only this scan's prices.
            }
        }

        /** Books read a second time by [rereadEarlyEdges]. */
        var reread = 0

        /**
         * Everything priced so far. Its feed shows only prices read this scan, and only for leagues whose fair
         * odds are complete: a line priced from the first source to answer can move when the next one does,
         * and an edge that shows and then vanishes is worse than one that shows a few seconds later (Tj,
         * 2026-09-28: "found several positive EV bets while scanning but they quickly disappeared"; live, a
         * Mystics moneyline showed at 19 s from Polymarket alone and left when Kalshi answered).
         */
        private fun publish(cat: Catalog) {
            val shown = planFor(cat, settings, now, youngFairOnly = true, headroomMs = Freshness.MIN_SHOWN_MS)
            // Every partial prices the WHOLE plan again and builds a complete new result: with no limits that is thousands of markets after every
            // batch of ~30 books, the old result still alive beside it (Tj's Diagnostics, 2026-10-01: an OutOfMemoryError at 256 MB). A big plan
            // publishes at most every [PUBLISH_MIN_MS]; the books it skips are in the next one, and the final result is always priced in full.
            if (shown.markets.size >= bigPlanMarkets) {
                val t = elapsed()
                if (t - lastPublishAt < publishMinMs) return
                lastPublishAt = t
            }
            val merged = HashMap(books)
            merged.putAll(fresh)
            val partial = Pricing.price(shown, merged, settings, now, fairMemo).copy(freshSinceMs = now, waitingFor = waiting())
            if (firstBetAt == null && partial.feed(settings).isNotEmpty()) firstBetAt = elapsed() - startedAt
            onPartial(partial)
        }

        /**
         * [ScanResult.waitingFor]: each league's game lines until its sources answered (a [ReferenceSource.linesFirst]
         * source: its game lines), its props until every source did.
         */
        private fun waiting(): Set<String> {
            val done = synchronized(settled) { settled.toSet() }
            val out = HashSet<String>()
            for (league in settings.selectedLeagues) {
                val mine = sources.filter { it.supports(league) }
                fun ready(list: List<ReferenceSource>, lines: Boolean = false) = list.all {
                    "${it.id}|${league.novigName}" in done || (lines && it.linesFirst && "${it.id}|${league.novigName}|lines" in done)
                }
                if (!ready(mine.filter { !it.propsOnly }, lines = true)) {
                    out += ScanResult.waitKey(league.novigName, props = false)
                    out += ScanResult.waitKey(league.novigName, props = true)
                } else if (!ready(mine)) {
                    out += ScanResult.waitKey(league.novigName, props = true)
                }
            }
            return out
        }
    }

    /**
     * Which unread markets to read first. Open bets, then the likeliest +EV lines by EV, then near
     * misses and the lines the last scan ran out of time for, then lines never priced (props, period lines and team totals before main lines: the
     * derivative markets are where exchange prices lag most), then the budget's filler lines never
     * priced ([PlannedMarket.spare]), then lines well below zero, and last the games no fair source
     * covers (they can't be +EV). Soonest games first within each.
     *
     * A line's EV here is its [preview] (this scan's fair line at Novig's price as PropLine relayed it
     * seconds ago) when there is one, else last scan's: so a missing, failed, late or old relay simply
     * leaves the original order (Tj, 2026-09-27: "if there is any failure or delay, … fallback to the
     * original novig read"). Either way it only orders reads: the feed is priced from Novig's books.
     */
    private fun fetchOrder(pending: List<PlannedMarket>, settings: ScanSettings, preview: Map<String, Double> = emptyMap()): List<PlannedMarket> {
        fun ev(id: String): Double? = preview[id] ?: lastEv[id]
        fun group(p: PlannedMarket): Int {
            val id = p.market.marketId
            if (id in pinned) return 0
            if (p.lineKey == null) return 7
            val ev = ev(id)
            // Left too late last scan: first of the never-priced lines now.
            if (ev == null && id in leftLastScan) return 2
            return when {
                ev == null && p.spare -> 5
                ev == null -> if (p.kind == LineKind.PLAYER_PROP || p.kind == LineKind.TEAM_TOTAL || (p.lineKey.period) != 0) 3 else 4
                ev >= settings.minEvPercent -> 1
                ev >= NEAR_MISS_EV -> 2
                else -> 6
            }
        }
        return pending.sortedWith(
            compareBy<PlannedMarket>({ group(it) }, { -(ev(it.market.marketId) ?: 0.0) }, { it.event.startsTs }),
        )
    }

    /** The last [preview], re-used while its plan and relays are the same objects. */
    private var previewCache: Triple<Plan, List<RefSnapshot>, Map<String, Double>>? = null

    /**
     * Each planned market's best EV at Novig's own prices as PropLine relayed them ([RefSnapshot.novig],
     * the same request as its books: RESEARCH.md §23.6), against [plan]'s fair lines: stand-in books
     * built from those prices, priced exactly like real ones. Only relays fetched in the last
     * [NOVIG_PREVIEW_MAX_AGE_MS] count. Empty (the original read order) when there's none or anything
     * goes wrong.
     */
    private fun preview(plan: Plan, settings: ScanSettings, now: Long): Map<String, Double> {
        val relays = synchronized(references) {
            settings.selectedLeagues.flatMap { l -> NOVIG_RELAYS.mapNotNull { id -> references["$id|${l.novigName}"]?.snapshot } }
        }.filter { it.novig.isNotEmpty() && now - it.fetchedAtMs <= NOVIG_PREVIEW_MAX_AGE_MS }
        if (relays.isEmpty()) return emptyMap()
        previewCache?.let { (p, r, v) -> if (p === plan && r.size == relays.size && r.indices.all { r[it] === relays[it] }) return v }
        val result = runCatching { previewOf(plan, relays.map { it.copy(events = it.novig) }, settings, now) }.getOrDefault(emptyMap())
        previewCache = Triple(plan, relays, result)
        return result
    }

    private fun previewOf(plan: Plan, relays: List<RefSnapshot>, settings: ScanSettings, now: Long): Map<String, Double> {
        val ours = plan.events.associateBy { it.event.eventId }
        val relayed = Planner.matchEvents(plan.events.map { it.event }, relays).associateBy { it.event.eventId }
        val books = HashMap<String, NovigBook>()
        for (m in plan.markets) {
            val key = m.lineKey ?: continue
            if (m.outcomes.size != 2) continue
            val relay = relayed[m.event.eventId] ?: continue
            val relayEvent = relay.refEvent ?: continue
            val mine = ours[m.event.eventId] ?: continue
            // Both feeds' home/away oriented like the plan's before comparing lines.
            val quotes = if (relay.refSwapped != mine.refSwapped) relayEvent.markets.map { it.flipped() } else relayEvent.markets
            val q = quotes.firstOrNull { key.matches(it) } ?: continue
            val bids = HashMap<String, List<com.tjshea.vigilant.data.novig.BidLevel>>()
            for ((i, o) in m.outcomes.withIndex()) {
                val side = (o.target as? OutcomeTarget.Is)?.side ?: continue
                val decimal = q.quotes.firstOrNull { it.side == side }?.decimalOdds ?: continue
                // Taking this outcome at price P = a resting bid of 1 − P on the other one.
                val milli = ((1.0 - 1.0 / decimal) * 1000).roundToInt()
                if (milli in 1..999) bids[m.outcomes[1 - i].outcome.outcomeId] = listOf(com.tjshea.vigilant.data.novig.BidLevel(milli, PREVIEW_CONTRACTS))
            }
            if (bids.isNotEmpty()) books[m.market.marketId] = NovigBook(m.market.marketId, 0, bids, now)
        }
        if (books.isEmpty()) return emptyMap()
        return Pricing.price(plan, books, settings, now, fairMemo).opportunities
            .filter { it.market.marketId in books }
            .mapNotNull { o -> o.evPercent?.let { o.market.marketId to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, evs) -> evs.max() }
    }

    /**
     * Re-reads just [marketIds]' Novig books (at most [MAX_RECHECK], paced like a scan) and
     * re-prices against the last scan's fair odds. Seconds instead of a full scan: for checking
     * that the feed's edges are still there right before betting. Null result before any scan.
     */
    override suspend fun recheck(
        requested: ScanSettings,
        marketIds: Collection<String>,
        onProgress: (Int, Int) -> Unit,
    ): RecheckReport = mutex.withLock {
        val settings = requested.effective(forBets = betsOnly)
        val cat = catalog ?: return@withLock RecheckReport(null, 0, 0, null)
        val ids = marketIds.distinct().take(MAX_RECHECK)
        val now = clock()
        var error: String? = null
        var read = 0
        var failed = 0
        if (ids.isNotEmpty()) {
            try {
                val batch = novig.books(ids) { d, t -> onProgress(d, t) }
                books = HashMap(books).apply { putAll(batch.books) }
                read = batch.fetched + batch.notModified
                failed = batch.failed
                error = batch.lastError ?: batch.keyProblem
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = ids.size
                error = e.message ?: e.javaClass.simpleName
            }
        }
        // Fair odds' age is judged now: a recheck long after the scan finds them too old (RESEARCH.md §24).
        val plan = planFor(cat, settings, now, youngFairOnly = true)
        RecheckReport(Pricing.price(plan, books, settings, now, fairMemo), read, failed, error)
    }

    /** Re-price what's already fetched under new settings. No network. Null before the first scan. */
    override suspend fun reprice(requested: ScanSettings): ScanResult? = mutex.withLock {
        val settings = requested.effective(forBets = betsOnly)
        val cat = catalog ?: return@withLock null
        if (settings.leagues.isEmpty()) return@withLock null
        val now = clock()
        Pricing.price(planFor(cat, settings, now, youngFairOnly = true), books, settings, now, fairMemo)
    }


    /** Leagues selected now that the last scan didn't load: they need a scan to show anything. */
    override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = mutex.withLock {
        settings.leagues - (catalog?.leagues ?: emptySet())
    }

    /**
     * The Novig market types a scan needs. Prop stats only the sportsbooks price (pitcher outs,
     * kicking points, …) are hundreds of markets a day, fetched only when that source is on.
     */
    private fun catalogTypes(settings: ScanSettings, sources: List<ReferenceSource>): Set<String> {
        val priced = sources.flatMapTo(HashSet()) { it.extraPropTypes }
        return settings.novigMarketTypes.toSet() - (PropStats.BOOK_ONLY_TYPES - priced)
    }

    /**
     * How far ahead Novig's markets are read: "Days ahead" (a day's slack is added), or in low-usage bids only the window ([ScanSettings.scanWindowHours]): the scan prices
     * nothing past it, so the prop markets of the next week are not read for it (RESEARCH.md §92).
     */
    private fun catalogHours(settings: ScanSettings): Int = if (settings.lowUsageScan) settings.scanWindowHours else settings.daysAhead.coerceAtLeast(1) * 24

    private suspend fun refreshCatalog(settings: ScanSettings, types: Set<String>, now: Long, errors: MutableList<String>) {
        val c = catalog
        // A bets-only catalog is cut to this pass's bets, so it's never re-used for another.
        val fresh = !betsOnly && c != null && c.leagues == settings.leagues && c.includeLive == settings.includeLive &&
            c.hours == catalogHours(settings) && c.types.containsAll(types) && now - c.fetchedAtMs < catalogTtlMs
        if (fresh) return
        try {
            // DELAYED is tradable too (docs: api/concepts/event-lifecycle): a game held before its start.
            val statuses = buildList {
                add(NovigEvent.STATUS_PREGAME)
                add(NovigEvent.STATUS_DELAYED)
                if (settings.includeLive) add(NovigEvent.STATUS_LIVE)
            }
            val leagues = settings.leagues.toList()
            // One extra day of slack past the horizon; Planner applies the exact cut.
            val before = now + (catalogHours(settings) + 24) * 3_600_000L
            // Every event, not just the window: one small request, and the scan can then say how many games
            // start past "Days ahead" (Tj, 2026-09-28: "There are way more than 7 total games"). Their
            // markets (the big part) are only read inside the window.
            var events = novig.events(leagues, statuses, null)
            // Only the families being priced: player props alone are thousands of markets a week.
            // Main lines always come along (cheap), so turning those on and off re-prices from cache.
            val wanted = types + MAIN_TYPES
            var markets = novig.markets(leagues, wanted.toList(), statuses, before)
            if (betsOnly) {
                markets = markets.filter { it.marketId in pinned }
                val games = markets.mapTo(HashSet()) { it.eventId }
                events = events.filter { it.eventId in games }
            }
            catalog = Catalog(settings.leagues, settings.includeLive, catalogHours(settings), wanted, events, markets, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.addSync("Novig board: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * The sources a scan may read: all that were offered, except in low-usage bids (a source the settings don't switch on, for the feeds that don't carry a picked sharp prop book:
     * RESEARCH.md §92).
     */
    private fun readable(offered: List<ReferenceSource>, settings: ScanSettings): List<ReferenceSource> =
        if (settings.lowUsageScan) offered.filter { it.id in settings.enabledSources } else offered

    /**
     * What source [firstId] gave this scan, as a fallback's [ScanContext]: the leagues it answered
     * (fresh or re-used) and, per Novig game it matched, which lines and prop stats it priced.
     */
    private fun covering(firstId: String, leagues: List<League>, board: ScanContext): ScanContext {
        val covered = HashMap<String, MutableSet<String>>()
        val answeredLeagues = HashSet<String>()
        for (league in leagues) {
            val key = "$firstId|${league.novigName}"
            if (synchronized(answered) { key !in answered }) continue
            answeredLeagues += league.novigName
            val snap = synchronized(references) { references[key]?.snapshot } ?: continue
            val games = board.novigEvents.filter { it.league == league.novigName }
            for (m in Planner.matchEvents(games, listOf(snap))) {
                val ref = m.refEvent ?: continue
                covered.getOrPut(m.event.eventId) { HashSet() } += ref.markets.map { it.coverage }
            }
        }
        return board.copy(covered = covered, firstAnswered = answeredLeagues)
    }

    private suspend fun fetchSource(
        source: ReferenceSource,
        leagues: List<League>,
        settings: ScanSettings,
        now: Long,
        errors: MutableList<String>,
        context: ScanContext?,
        fallback: Boolean = false,
        /** Once per league this source supports, whatever came of it. */
        onCall: (League) -> Unit,
    ): SourceReport {
        var fetched = 0
        var reused = 0
        var standingBy = 0
        var skipped = 0
        var error: String? = null
        var heldBack: String? = null
        val requestKey = requestKey(source, settings)
        // Never longer than a couple of minutes, whatever the feed or Settings say (RESEARCH.md §24).
        val reuseMs = minOf(source.reuseMs(settings), Freshness.MAX_REUSE_MS)
        for (league in leagues) {
            if (!source.supports(league)) continue
            val key = "${source.id}|${league.novigName}"
            val have = synchronized(references) { references[key] }
            // A skipped league (nothing to read in the window) is never re-used: a game may come inside the window before the next scan.
            if (have != null && have.requestKey == requestKey && reuseMs > 0 && now - have.snapshot.fetchedAtMs < reuseMs && !have.snapshot.skipped) {
                reused++
                synchronized(answered) { answered += key }
                onCall(league)
                continue
            }
            if (error != null && source.metered) {
                // A metered provider that just refused (limit, bad key) will refuse the next league too.
                onCall(league)
                continue
            }
            try {
                // A fallback whose first choice already gave this league stands by: nothing spent.
                // Its own last answer is dropped (it was too old to re-use, or asked differently), so
                // quotes older than the first choice's never price beside them.
                if (fallback && context != null && !source.needed(league, settings, context)) {
                    synchronized(references) { references.remove(key) }
                    standingBy++
                    onCall(league)
                    continue
                }
                // Stamped with our own clock: re-use windows (credits) must never depend on what
                // time a provider claims it answered.
                val snap = (if (context != null) source.odds(league, settings, context) else source.odds(league, settings))
                    .copy(fetchedAtMs = now, provider = source.id).seenBy(now)
                synchronized(references) { references[key] = Cached(snap, requestKey) }
                synchronized(answered) { answered += key }
                snap.creditsRemaining?.let { creditsRemaining = it }
                if (snap.skipped) skipped++ else fetched++
            } catch (e: CancellationException) {
                throw e
            } catch (e: PartialReferenceException) {
                // What came back before the failure is kept; the failure is still reported.
                synchronized(references) { references[key] = Cached(e.partial.copy(fetchedAtMs = now, provider = source.id).seenBy(now), requestKey) }
                // A fallback is told what did come back, and covers the rest.
                synchronized(answered) { answered += key }
                e.partial.creditsRemaining?.let { creditsRemaining = it }
                fetched++
                val message = e.message ?: source.displayName
                if (error == null) errors.addSync(message)
                error = message
            } catch (e: com.tjshea.vigilant.data.keys.CreditsHeldBackException) {
                // Its credits are paced (ParlayAPI: a day's share, the last few kept for closing lines): the other feeds price this
                // league; its last answer stays until it's too old to (Freshness), as with any feed between refreshes.
                standingBy++
                heldBack = e.message
            } catch (e: Exception) {
                val message = when (e) {
                    is AllKeysExhaustedException, is ReferenceException -> e.message ?: source.displayName
                    else -> "${source.displayName} ${league.displayName}: ${com.tjshea.vigilant.data.reference.readableError(e)}"
                }
                if (error == null) errors.addSync(message)
                error = message
                // Keep a recent previous answer; drop one too old to call fair.
                synchronized(references) {
                    val old = references[key]
                    if (old != null && now - old.snapshot.fetchedAtMs > settings.staleReferenceMinutes * 60_000L) references.remove(key)
                }
            }
            onCall(league)
        }
        return SourceReport(source.id, source.displayName, fetched, reused, 0, error, standingBy, skipped, heldBack)
    }

    /**
     * Every league's game lines from a [ReferenceSource.linesFirst] source, before [fetchSource] asks it for the rest:
     * each answer stands for the league until the full one lands, and [onLines] says it's in. Nothing for a league
     * whose last answer is still re-usable; a failure leaves the league to [fetchSource], which reports it.
     */
    private suspend fun linesFirst(source: ReferenceSource, leagues: List<League>, settings: ScanSettings, now: Long, onLines: (League) -> Unit) {
        val requestKey = requestKey(source, settings)
        val reuseMs = minOf(source.reuseMs(settings), Freshness.MAX_REUSE_MS)
        for (league in leagues) {
            if (!source.supports(league)) continue
            val key = "${source.id}|${league.novigName}"
            val have = synchronized(references) { references[key] }
            if (have != null && have.requestKey == requestKey && reuseMs > 0 && now - have.snapshot.fetchedAtMs < reuseMs) continue
            val snap = try {
                source.lines(league, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            synchronized(references) { references[key] = Cached(snap.copy(fetchedAtMs = now, provider = source.id).seenBy(now), requestKey) }
            onLines(league)
        }
    }

    /** What a snapshot was asked for; a different ask can't re-use it. */
    private fun requestKey(source: ReferenceSource, settings: ScanSettings): String = when (source.id) {
        "oddsapi" -> "${settings.referenceBooks.sorted()}|${settings.families.sorted()}"
        "parlay" -> "${settings.families.sorted()}|${settings.scanWindowHours}"
        // Low-usage bids ask ParlayAPI for the picked books alone: an answer read with the usual fourteen is not the same ask.
        "parlay_props" -> "${MarketFamily.PLAYER_PROPS in settings.families}|${if (settings.lowUsageScan) LowUsageBids.parlayBooks(settings).sorted() else ""}"
        "parlay_1h" -> (MarketFamily.FIRST_HALF in settings.families).toString()
        "oddsapi_props" -> "${settings.referenceBooks.sorted()}|${settings.bookPropSet}|${settings.bookPropCreditsPerScan}|${settings.bookPropWindowHours}"
        "propline" -> "${settings.referenceBooks.sorted()}|${settings.families.sorted()}"
        "propline_props" -> "${settings.referenceBooks.sorted()}|${settings.bookPropWindowHours}|${settings.propLineGamesPerScan}"
        else -> "${settings.families.sorted()}|${settings.exchangeMaxSpread}|${settings.daysAhead}"
    }

    /**
     * The plan for [cat] under [settings], from every fair line known. [youngFairOnly] leaves out
     * snapshots too old to price with: older than the stale limit and than that provider's own
     * re-use window. A line fetched an hour ago may still say which Novig books are worth reading
     * first, but it must never price what the feed shows. Age is judged as of [fairAsOf] (the
     * scan's own time when re-pricing later); which games are still pregame, as of [now].
     */
    private fun planFor(
        cat: Catalog,
        settings: ScanSettings,
        now: Long,
        youngFairOnly: Boolean = false,
        fairAsOf: Long = now,
        /** With [youngFairOnly]: quotes must also stay fresh this much longer ([Freshness.MIN_SHOWN_MS] for a scan's own feed). */
        headroomMs: Long = 0L,
    ): Plan {
        val enabled = settings.enabledSources
        val refs = synchronized(references) {
            settings.selectedLeagues.flatMap { l ->
                SOURCE_ORDER.filter { it in enabled }.mapNotNull { id -> references["$id|${l.novigName}"]?.snapshot }
            }
        }.filter { !youngFairOnly || fairAsOf - it.fetchedAtMs <= Freshness.FAR_OFF_AGE_MS }
        // The sportsbook feeds' books follow the reference-book picker, even between scans.
        val books = settings.referenceBooks.toSet()
        val inputs = listOf(
            System.identityHashCode(cat), refs.map { System.identityHashCode(it) }, books,
            settings.leagues, settings.families, settings.includeLive, settings.scanWindowHours, settings.linesPerGame,
            settings.propsPerGame, settings.maxBooksPerScan, settings.fillBudget, pinned, now / 60_000L, fairAsOf / 60_000L, headroomMs, settings.lowUsageScan,
        )
        plans[youngFairOnly]?.let { (key, plan) -> if (key == inputs) return plan }
        val filtered = refs.map { snap ->
            // Pricing: only book prices their feed saw in the last few minutes (RESEARCH.md §24). Reading
            // order may still lean on older ones; they never price.
            // How old depends on how far off the game is (Freshness.maxAgeMs; a date-only start counts as near);
            // each quote carries a time (seenBy).
            // A game left with no fresh line is dropped, as if the feed hadn't answered: Novig's board still lists it, unpriced.
            val fresh = if (!youngFairOnly) snap else snap.copy(
                events = snap.events.map { e -> e.copy(markets = e.markets.filter { Freshness.fresh(it.lastUpdateMs, fairAsOf + headroomMs, e.commenceMs.takeIf { e.etDate == null }) }) }
                    .filter { it.markets.isNotEmpty() },
            )
            if (fresh.provider !in PICKED_BOOK_FEEDS) fresh
            else fresh.copy(events = fresh.events.map { e -> e.copy(markets = e.markets.filter { it.bookKey in books }) })
        }
        return Planner.plan(cat.events, cat.markets, filtered, settings, now, pinned).also { plans[youngFairOnly] = inputs to it }
    }

    /**
     * Every quote's "last seen" as its feed said, never later than [now] (a feed's clock can run ahead),
     * and [now] where it didn't say: what the freshness rule measures ([Freshness]).
     */
    private fun RefSnapshot.seenBy(now: Long): RefSnapshot = copy(
        events = events.map { e ->
            if (e.markets.all { it.lastUpdateMs != null && it.lastUpdateMs <= now }) e
            else e.copy(markets = e.markets.map { m -> if (m.lastUpdateMs != null && m.lastUpdateMs <= now) m else m.copy(lastUpdateMs = minOf(m.lastUpdateMs ?: now, now)) })
        },
    )

    private fun report(result: ScanResult?, errors: List<String>, retryAfter: Int?, credits: Int?, sources: List<SourceReport>) = ScanReport(
        result = result,
        errors = errors,
        retryAfterSeconds = retryAfter,
        booksFetched = 0,
        booksNotModified = 0,
        booksFromCache = 0,
        booksViaKey = 0,
        novigCatalogAtMs = catalog?.fetchedAtMs,
        sources = sources,
        creditsRemaining = credits ?: creditsRemaining,
    )

    private fun MutableList<String>.addSync(s: String) = synchronized(this) { add(s) }

    /**
     * The parsed boards of a league or a source no longer picked are never priced or read again, yet each is thousands of lines held for the
     * life of the process (Tj's Diagnostics, 2026-10-01: an OutOfMemoryError at 256 MB with seven leagues picked and more having been): a
     * scan starts by letting them go.
     */
    private fun dropUnusedReferences(sources: List<ReferenceSource>, leagues: List<League>) {
        val wanted = HashSet<String>()
        for (source in sources) for (league in leagues) if (source.supports(league)) wanted += "${source.id}|${league.novigName}"
        synchronized(references) { references.keys.retainAll(wanted) }
    }

    override fun trimForBackground(): Int {
        // A scan or a re-price holds the lock: they're using all of it, and a scan running means Vigilant isn't idle.
        if (!mutex.tryLock()) return 0
        try {
            val now = clock()
            // Older than any game's freshness limit: never priced again (re-pricing and scans take only younger boards), only re-fetched.
            val dropped = synchronized(references) {
                val old = references.filterValues { now - it.snapshot.fetchedAtMs > Freshness.FAR_OFF_AGE_MS }.keys
                references.keys.removeAll(old)
                old.size
            }
            previewCache = null
            plans.clear()
            fairMemo.clear()
            novig.trimCaches()
            return dropped
        } finally {
            mutex.unlock()
        }
    }

    /** What the scanner keeps between scans, for Diagnostics' memory block (counts, not bytes). */
    data class Holdings(val snapshots: Int, val events: Int, val bookMarkets: Int, val books: Int, val cachedBooks: Int)

    fun holdings(): Holdings {
        val snaps = synchronized(references) { references.values.map { it.snapshot } }
        return Holdings(
            snapshots = snaps.size,
            events = snaps.sumOf { it.events.size },
            bookMarkets = snaps.sumOf { s -> s.events.sumOf { it.markets.size } },
            books = books.size,
            cachedBooks = novig.cachedBooks(),
        )
    }

    companion object {
        /** A plan this big is priced for a partial result at most every [PUBLISH_MIN_MS]. */
        const val BIG_PLAN_MARKETS = 400

        /** What a scan read without the key costs (NovigPublicClient: the public routes' 4-6 a second against the key's 14, and no live feed). */
        const val PUBLIC_PRICES = "This scan read Novig's public prices instead: up to 6 a second against the key's 14, and no live feed, so it was slower. The key is tried again every 10 minutes."

        const val PUBLISH_MIN_MS = 2_000L

        /**
         * The longest a scan holds the live feed's one bulk subscribe for its plan to fill in: past the ~8 s the feed's bucket needs after
         * connecting, and about when the slowest free fair source answers (Kalshi ~27 s, NOVIG_API.md §14.5).
         */
        const val STREAM_HOLD_MS = 30_000L

        /**
         * Merge order: when two feeds carry the same book, the earlier one's quote is priced. ParlayAPI ahead of PropLine (Tj, 2026-09-30,
         * "prioritize its use if it can do anything better"): its books are polled every few seconds and each quote carries its measured
         * age, where PropLine runs ~20 s behind; between ParlayAPI's paced refreshes its quotes age out (Freshness) and PropLine's price.
         */
        val SOURCE_ORDER = listOf("pinnacle", "polymarket", "kalshi", "sgo", "sgo-props", "parlay", "parlay_1h", "propline", "oddsapi", "parlay_props", "propline_props", "oddsapi_props")

        /** Sportsbook feeds whose books follow the reference-book picker in Settings. */
        private val PICKED_BOOK_FEEDS = setOf("oddsapi", "oddsapi_props", "propline", "propline_props")

        private val MAIN_TYPES = (MarketFamily.MONEYLINE.novigTypes + MarketFamily.SPREAD.novigTypes + MarketFamily.TOTAL.novigTypes).toSet()

        /**
         * Books read between partial results on the public routes ([NovigSource.batchSize]): small enough that the
         * feed moves every ~2 seconds. A key reads 30 at a time (10 in flight): about 2 seconds at its pace too.
         */
        const val CHUNK = NovigSource.DEFAULT_BATCH

        /** Most books one recheck reads: about seven seconds on Novig's public routes. */
        const val MAX_RECHECK = 40

        /** A line this close below zero last scan is worth re-reading early: a tick can flip it. */
        const val NEAR_MISS_EV = -0.02

        /** A feed bet whose Novig price was read longer ago than this when the scan ends is read again. */
        const val REREAD_AFTER_MS = 60_000L

        /** Most books the end-of-scan re-read takes: about seven seconds on Novig's public routes. */
        const val MAX_REREAD = 40

        /** Feeds that relay Novig's own prices ([RefSnapshot.novig]). */
        private val NOVIG_RELAYS = listOf(com.tjshea.vigilant.data.reference.PropLineClient.ID, com.tjshea.vigilant.data.reference.PropLinePropsSource.ID)

        /** A relay of Novig's prices older than this orders nothing (the original order stands). */
        const val NOVIG_PREVIEW_MAX_AGE_MS = 3 * 60_000L

        /** Stand-in depth for a previewed line: its size is unknown, and only its EV is used. */
        private const val PREVIEW_CONTRACTS = 1_000L
    }
}
