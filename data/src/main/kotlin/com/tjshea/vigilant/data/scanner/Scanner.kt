package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
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
)

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
)

/**
 * Runs a scan when (and only when) Tj asks for one: the Scan button or pull-to-refresh (his rule,
 * 2026-09-25). Nothing here runs on a timer. Per scan, all at once:
 *
 *  1. Novig catalog (events + markets): re-used for [catalogTtlMs] (3 min) if the leagues and
 *     window haven't changed. New alternate lines appear slowly; prices move fast.
 *  2. Fair odds from every enabled [ReferenceSource], providers in parallel, leagues one at a time
 *     within a provider. A metered provider (The Odds API credits, pinnapi's 100/day) is re-used
 *     for its [ReferenceSource.reuseMs]. A failed call keeps the previous snapshot while it's
 *     younger than the stale limit, so one hiccup doesn't blank the feed.
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
) {
    private data class Catalog(
        val leagues: Set<String>,
        val includeLive: Boolean,
        val daysAhead: Int,
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
    private var creditsRemaining: Int? = null
    /** Plans cached by what went into them: key true = only fair odds young enough to show. */
    private val plans = HashMap<Boolean, Pair<Any, Plan>>()
    private var books: Map<String, NovigBook> = emptyMap()
    private var pinned: Set<String> = emptySet()

    /** Each market's best EV on the last scan, to read the likeliest +EV lines first next time. */
    private var lastEv: Map<String, Double> = emptyMap()

    suspend fun scan(
        settings: ScanSettings,
        sources: List<ReferenceSource>,
        /** Markets always priced past the per-game line cap: the ones Tj has open bets on. */
        pinned: Set<String> = emptySet(),
        onProgress: (ScanProgress) -> Unit = {},
        /**
         * Everything priced so far, each time another batch of Novig prices lands. Its feed lists
         * only prices read this scan ([ScanResult.freshSinceMs]); the final result is the report's.
         */
        onPartial: (ScanResult) -> Unit = {},
    ): ScanReport = mutex.withLock {
        this.pinned = pinned
        val now = clock()
        val errors = ArrayList<String>()
        val leagues = settings.selectedLeagues
        if (leagues.isEmpty()) return@withLock report(null, errors, null, null, emptyList())

        val ordered = sources.sortedBy { SOURCE_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        val progress = Progress(ordered.sumOf { s -> leagues.count { s.supports(it) } } + 1, onProgress)
        progress.emit()
        val pump = BookPump(settings, now, progress, onPartial)

        val sourceReports = coroutineScope {
            val catalogJob = async {
                refreshCatalog(settings, catalogTypes(settings, ordered), now, errors)
                progress.fairDone()
            }
            val jobs = ordered.map { source ->
                async {
                    // A source that picks its games from Novig's board waits for the board.
                    val context = if (source.needsCatalog) {
                        catalogJob.await()
                        catalog?.let { ScanContext(it.events, it.markets, now) } ?: ScanContext(now = now)
                    } else {
                        null
                    }
                    fetchSource(source, leagues, settings, now, errors, context) {
                        progress.fairDone()
                        pump.wake()
                    }
                }
            }
            // Novig's prices start as soon as the board is in, alongside the fair odds.
            val pumpJob = async {
                catalogJob.await()
                pump.run()
            }
            val reports = jobs.awaitAll()
            pump.fairOddsDone()
            pumpJob.await()
            reports
        }

        val currentPlan = catalog?.let { planFor(it, settings, now) }
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
        pump.keyProblem?.let { errors += "Novig key: $it Used public prices this scan." }
        if (pump.failed > 0 && pump.lastError != null) {
            errors += "Novig prices: ${pump.lastError}" + if (pump.fromCache > 0) " (${pump.fromCache} shown from the last scan)" else ""
        } else if (pump.lastError != null) {
            errors += "Novig prices: ${pump.lastError}"
        }
        progress.finish()

        val result = currentPlan?.let { Pricing.price(it, books, settings, now) }
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
    ) {
        val requested = LinkedHashSet<String>()
        val fresh = HashMap<String, NovigBook>()
        var fetched = 0
        var notModified = 0
        var fromCache = 0
        var viaKey = 0
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
                // Read before planning: a provider answering mid-plan still wakes the next pass.
                val lastPass = fairDone
                val plan = planFor(cat, settings, now)
                val pending = plan.markets.filter { it.market.marketId !in requested }
                if (pending.isEmpty()) {
                    if (lastPass) return
                    signal.receive()
                    continue
                }
                val chunk = fetchOrder(pending, settings).take(minOf(CHUNK, cap - requested.size))
                val ids = chunk.map { it.market.marketId }
                requested += ids
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
                    failed += batch.failed
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

        /** Everything priced so far. Its feed shows only prices read this scan. */
        private fun publish(cat: Catalog) {
            val shown = planFor(cat, settings, now, youngFairOnly = true)
            val merged = HashMap(books)
            merged.putAll(fresh)
            onPartial(Pricing.price(shown, merged, settings, now).copy(freshSinceMs = now))
        }
    }

    /**
     * Which unread markets to read first. Open bets, then last scan's +EV lines by EV, then its near
     * misses, then lines never priced (props, period lines and team totals before main lines: the
     * derivative markets are where exchange prices lag most), then lines that were well below zero,
     * and last the games no fair source covers (they can't be +EV). Soonest games first within each.
     */
    private fun fetchOrder(pending: List<PlannedMarket>, settings: ScanSettings): List<PlannedMarket> {
        fun group(p: PlannedMarket): Int {
            val id = p.market.marketId
            if (id in pinned) return 0
            if (p.lineKey == null) return 6
            val ev = lastEv[id]
            return when {
                ev == null -> if (p.kind == LineKind.PLAYER_PROP || p.kind == LineKind.TEAM_TOTAL || (p.lineKey.period) != 0) 3 else 4
                ev >= settings.minEvPercent -> 1
                ev >= NEAR_MISS_EV -> 2
                else -> 5
            }
        }
        return pending.sortedWith(
            compareBy<PlannedMarket>({ group(it) }, { -(lastEv[it.market.marketId] ?: 0.0) }, { it.event.startsTs }),
        )
    }

    /** Re-price what's already fetched under new settings. No network. Null before the first scan. */
    suspend fun reprice(settings: ScanSettings): ScanResult? = mutex.withLock {
        val cat = catalog ?: return@withLock null
        if (settings.leagues.isEmpty()) return@withLock null
        val now = clock()
        Pricing.price(planFor(cat, settings, now), books, settings, now)
    }

    /** Leagues selected now that the last scan didn't load: they need a scan to show anything. */
    suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = mutex.withLock {
        settings.leagues - (catalog?.leagues ?: emptySet())
    }

    /**
     * The Novig market types a scan needs. Prop stats only the sportsbooks price (pitcher outs,
     * kicking points, …) are hundreds of markets a day, fetched only when that source is on.
     */
    private fun catalogTypes(settings: ScanSettings, sources: List<ReferenceSource>): Set<String> {
        val types = settings.novigMarketTypes.toSet()
        return if (sources.any { it.id == "oddsapi_props" }) types else types - PropStats.BOOK_ONLY_TYPES
    }

    private suspend fun refreshCatalog(settings: ScanSettings, types: Set<String>, now: Long, errors: MutableList<String>) {
        val c = catalog
        val fresh = c != null && c.leagues == settings.leagues && c.includeLive == settings.includeLive &&
            c.daysAhead == settings.daysAhead && c.types.containsAll(types) && now - c.fetchedAtMs < catalogTtlMs
        if (fresh) return
        try {
            val statuses = buildList {
                add(NovigEvent.STATUS_PREGAME)
                if (settings.includeLive) add(NovigEvent.STATUS_LIVE)
            }
            val leagues = settings.leagues.toList()
            // One extra day of slack past the horizon; Planner applies the exact cut.
            val before = now + (settings.daysAhead.coerceAtLeast(1) + 1) * 86_400_000L
            val events = novig.events(leagues, statuses, before)
            // Only the families being priced: player props alone are thousands of markets a week.
            // Main lines always come along (cheap), so turning those on and off re-prices from cache.
            val wanted = types + MAIN_TYPES
            val markets = novig.markets(leagues, wanted.toList(), statuses, before)
            catalog = Catalog(settings.leagues, settings.includeLive, settings.daysAhead, wanted, events, markets, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.addSync("Novig board: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun fetchSource(
        source: ReferenceSource,
        leagues: List<League>,
        settings: ScanSettings,
        now: Long,
        errors: MutableList<String>,
        context: ScanContext?,
        onCall: () -> Unit,
    ): SourceReport {
        var fetched = 0
        var reused = 0
        var error: String? = null
        val requestKey = requestKey(source, settings)
        val reuseMs = source.reuseMs(settings)
        for (league in leagues) {
            if (!source.supports(league)) continue
            val key = "${source.id}|${league.novigName}"
            val have = synchronized(references) { references[key] }
            if (have != null && have.requestKey == requestKey && reuseMs > 0 && now - have.snapshot.fetchedAtMs < reuseMs) {
                reused++
                onCall()
                continue
            }
            if (error != null && source.metered) {
                // A metered provider that just refused (limit, bad key) will refuse the next league too.
                onCall()
                continue
            }
            try {
                // Stamped with our own clock: re-use windows (credits) must never depend on what
                // time a provider claims it answered.
                val snap = (if (context != null) source.odds(league, settings, context) else source.odds(league, settings))
                    .copy(fetchedAtMs = now, provider = source.id)
                synchronized(references) { references[key] = Cached(snap, requestKey) }
                snap.creditsRemaining?.let { creditsRemaining = it }
                fetched++
            } catch (e: CancellationException) {
                throw e
            } catch (e: PartialReferenceException) {
                // What came back before the failure is kept; the failure is still reported.
                synchronized(references) { references[key] = Cached(e.partial.copy(fetchedAtMs = now, provider = source.id), requestKey) }
                e.partial.creditsRemaining?.let { creditsRemaining = it }
                fetched++
                val message = e.message ?: source.displayName
                if (error == null) errors.addSync(message)
                error = message
            } catch (e: Exception) {
                val message = when (e) {
                    is AllKeysExhaustedException, is ReferenceException -> e.message ?: source.displayName
                    else -> "${source.displayName} ${league.displayName}: ${e.message ?: e.javaClass.simpleName}"
                }
                if (error == null) errors.addSync(message)
                error = message
                // Keep a recent previous answer; drop one too old to call fair.
                synchronized(references) {
                    val old = references[key]
                    if (old != null && now - old.snapshot.fetchedAtMs > settings.staleReferenceMinutes * 60_000L) references.remove(key)
                }
            }
            onCall()
        }
        return SourceReport(source.id, source.displayName, fetched, reused, 0, error)
    }

    /** What a snapshot was asked for; a different ask can't re-use it. */
    private fun requestKey(source: ReferenceSource, settings: ScanSettings): String = when (source.id) {
        "oddsapi" -> "${settings.referenceBooks.sorted()}|${settings.families.sorted()}"
        "oddsapi_props" -> "${settings.referenceBooks.sorted()}|${settings.bookPropSet}|${settings.bookPropCreditsPerScan}|${settings.bookPropHours}"
        else -> "${settings.families.sorted()}|${settings.exchangeMaxSpread}|${settings.daysAhead}"
    }

    /**
     * The plan for [cat] under [settings], from every fair line known. [youngFairOnly] leaves out
     * snapshots older than the stale limit: a line fetched an hour ago may still say which Novig
     * books are worth reading first, but it must never price what the feed shows mid-scan.
     */
    private fun planFor(cat: Catalog, settings: ScanSettings, now: Long, youngFairOnly: Boolean = false): Plan {
        val enabled = settings.enabledSources
        val maxAge = settings.staleReferenceMinutes * 60_000L
        val refs = synchronized(references) {
            settings.selectedLeagues.flatMap { l ->
                SOURCE_ORDER.filter { it in enabled }.mapNotNull { id -> references["$id|${l.novigName}"]?.snapshot }
            }
        }.filter { !youngFairOnly || now - it.fetchedAtMs <= maxAge }
        // The Odds API's books follow the reference-book picker, even between scans.
        val books = settings.referenceBooks.toSet()
        val inputs = listOf(
            System.identityHashCode(cat), refs.map { System.identityHashCode(it) }, books,
            settings.leagues, settings.families, settings.includeLive, settings.daysAhead, settings.linesPerGame,
            settings.propsPerGame, settings.maxBooksPerScan, pinned, now / 60_000L,
        )
        plans[youngFairOnly]?.let { (key, plan) -> if (key == inputs) return plan }
        val filtered = refs.map { snap ->
            if (snap.provider != "oddsapi" && snap.provider != "oddsapi_props") snap
            else snap.copy(events = snap.events.map { e -> e.copy(markets = e.markets.filter { it.bookKey in books }) })
        }
        return Planner.plan(cat.events, cat.markets, filtered, settings, now, pinned).also { plans[youngFairOnly] = inputs to it }
    }

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

    companion object {
        /** Merge order: when two feeds carry the same book, the earlier one's quote is priced. */
        val SOURCE_ORDER = listOf("pinnacle", "polymarket", "kalshi", "oddsapi", "oddsapi_props")

        private val MAIN_TYPES = (MarketFamily.MONEYLINE.novigTypes + MarketFamily.SPREAD.novigTypes + MarketFamily.TOTAL.novigTypes).toSet()

        /** Books read between partial results: small enough that the feed moves every ~2 seconds. */
        const val CHUNK = 8

        /** A line this close below zero last scan is worth re-reading early: a tick can flip it. */
        const val NEAR_MISS_EV = -0.02
    }
}
