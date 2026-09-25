package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceException
import com.tjshea.vigilant.data.reference.ReferenceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
 * 2026-09-25). Nothing here runs on a timer. Per scan:
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
 * [reprice] re-prices everything already fetched under new settings, with no network at all.
 */
class Scanner(
    private val novig: NovigSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val catalogTtlMs: Long = 3 * 60_000L,
) {
    private data class Catalog(val leagues: Set<String>, val includeLive: Boolean, val daysAhead: Int, val events: List<NovigEvent>, val markets: List<NovigMarket>, val fetchedAtMs: Long)

    private data class Cached(val snapshot: RefSnapshot, val requestKey: String)

    private val mutex = Mutex()
    private var catalog: Catalog? = null

    /** Latest snapshot per `"$sourceId|$league"`. */
    private val references = HashMap<String, Cached>()
    private var creditsRemaining: Int? = null
    private var plan: Plan? = null
    private var planInputs: Any? = null
    private var books: Map<String, NovigBook> = emptyMap()
    private var pinned: Set<String> = emptySet()

    suspend fun scan(
        settings: ScanSettings,
        sources: List<ReferenceSource>,
        /** Markets always priced past the per-game line cap: the ones Tj has open bets on. */
        pinned: Set<String> = emptySet(),
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanReport = mutex.withLock {
        this.pinned = pinned
        val now = clock()
        val errors = ArrayList<String>()
        val leagues = settings.selectedLeagues
        if (leagues.isEmpty()) return@withLock report(null, errors, null, null, emptyList())

        val ordered = sources.sortedBy { SOURCE_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        val calls = ordered.sumOf { s -> leagues.count { s.supports(it) } } + 1
        val done = AtomicInteger(0)
        onProgress(ScanProgress("Novig board and fair odds", 0, calls))

        val sourceReports = coroutineScope {
            val catalogJob = async {
                refreshCatalog(settings, now, errors)
                onProgress(ScanProgress("Novig board and fair odds", done.incrementAndGet(), calls))
            }
            val jobs = ordered.map { source ->
                async {
                    fetchSource(source, leagues, settings, now, errors) {
                        onProgress(ScanProgress("Novig board and fair odds", done.incrementAndGet(), calls))
                    }
                }
            }
            catalogJob.await()
            jobs.map { it.await() }
        }

        val cat = catalog
        val currentPlan = if (cat != null) planFor(cat, settings, now) else null

        var fetched = 0
        var notModified = 0
        var fromCache = 0
        var viaKey = 0
        var retryAfter: Int? = null
        if (currentPlan != null && currentPlan.markets.isNotEmpty()) {
            try {
                val batch = novig.books(currentPlan.marketIds) { d, t -> onProgress(ScanProgress("Novig prices", d, t)) }
                books = batch.books
                fetched = batch.fetched
                notModified = batch.notModified
                fromCache = batch.fromCache
                viaKey = batch.viaKey
                retryAfter = batch.retryAfterSeconds
                batch.keyProblem?.let { errors += "Novig key: $it Used public prices this scan." }
                if (batch.failed > 0 && batch.lastError != null) {
                    errors += "Novig prices: ${batch.lastError}" +
                        if (fromCache > 0) " ($fromCache shown from the last scan)" else ""
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "Novig prices: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val result = currentPlan?.let { Pricing.price(it, books, settings, now) }
        val reports = sourceReports.map { r -> r.copy(matched = currentPlan?.events?.count { r.id in it.providers } ?: 0) }
        ScanReport(
            result = result,
            errors = errors,
            retryAfterSeconds = retryAfter,
            booksFetched = fetched,
            booksNotModified = notModified,
            booksFromCache = fromCache,
            booksViaKey = viaKey,
            novigCatalogAtMs = catalog?.fetchedAtMs,
            sources = reports,
            creditsRemaining = creditsRemaining,
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

    private suspend fun refreshCatalog(settings: ScanSettings, now: Long, errors: MutableList<String>) {
        val c = catalog
        val fresh = c != null && c.leagues == settings.leagues && c.includeLive == settings.includeLive &&
            c.daysAhead == settings.daysAhead && now - c.fetchedAtMs < catalogTtlMs
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
            // Every family, so switching one on later re-prices from cache instead of refetching.
            val markets = novig.markets(leagues, MarketFamily.entries.flatMap { it.novigTypes }, statuses, before)
            catalog = Catalog(settings.leagues, settings.includeLive, settings.daysAhead, events, markets, now)
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
                val snap = source.odds(league, settings).copy(fetchedAtMs = now, provider = source.id)
                synchronized(references) { references[key] = Cached(snap, requestKey) }
                snap.creditsRemaining?.let { creditsRemaining = it }
                fetched++
            } catch (e: CancellationException) {
                throw e
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
        else -> "${settings.families.sorted()}|${settings.exchangeMaxSpread}|${settings.daysAhead}"
    }

    private fun planFor(cat: Catalog, settings: ScanSettings, now: Long): Plan {
        val enabled = settings.enabledSources
        val refs = synchronized(references) {
            settings.selectedLeagues.flatMap { l ->
                SOURCE_ORDER.filter { it in enabled }.mapNotNull { id -> references["$id|${l.novigName}"]?.snapshot }
            }
        }
        // The Odds API's books follow the reference-book picker, even between scans.
        val books = settings.referenceBooks.toSet()
        val inputs = listOf(
            System.identityHashCode(cat), refs.map { System.identityHashCode(it) }, books,
            settings.leagues, settings.families, settings.includeLive, settings.daysAhead, settings.linesPerGame,
            pinned, now / 60_000L,
        )
        val existing = plan
        if (existing != null && inputs == planInputs) return existing
        val filtered = refs.map { snap ->
            if (snap.provider != "oddsapi") snap
            else snap.copy(events = snap.events.map { e -> e.copy(markets = e.markets.filter { it.bookKey in books }) })
        }
        return Planner.plan(cat.events, cat.markets, filtered, settings, now, pinned).also {
            plan = it
            planInputs = inputs
        }
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
        val SOURCE_ORDER = listOf("pinnacle", "polymarket", "kalshi", "oddsapi")
    }
}
