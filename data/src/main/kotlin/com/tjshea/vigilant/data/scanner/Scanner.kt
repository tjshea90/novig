package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RefreshKind {
    /** Pull-to-refresh: reference odds for every selected sport, the Novig catalog, and books. */
    FULL,

    /** The live loop: books every tick; catalog and reference only when due or missing. */
    AUTO,
}

/** What happened on one refresh, for the status line. */
data class RefreshReport(
    val result: ScanResult?,
    val errors: List<String>,
    /** Novig asked us to slow down: skip book refreshes for this many seconds. */
    val retryAfterSeconds: Int?,
    val booksFetched: Int,
    val booksNotModified: Int,
    val novigCatalogAtMs: Long?,
    val referenceAtMs: Map<String, Long>,
    val creditsRemaining: Int?,
    val hasReferenceSource: Boolean,
)

/**
 * Holds everything fetched so far and decides what to refetch. The UI calls [refresh] on a timer
 * while the screen is visible, and [reprice] when a setting changes. Timing rules:
 *
 *  - Novig books: every AUTO tick (the caller's timer, default 15s). ETags make unchanged books cheap.
 *  - Novig catalog (events + markets): every [catalogTtlMs] (3 min), or right away when the league
 *    set or window changes. New alternate lines appear slowly; books move fast.
 *  - Reference odds: they cost Odds API credits. Fetched on FULL, when a sport has never been
 *    fetched, or when the user turned on auto-refresh and the interval has passed. A failed sport
 *    waits [referenceRetryMs] before an AUTO tick may retry it, so a dead key can't burn a retry
 *    every 15 seconds.
 */
class Scanner(
    private val novig: NovigSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val catalogTtlMs: Long = 3 * 60_000L,
    private val referenceRetryMs: Long = 10 * 60_000L,
) {
    private data class Catalog(val leagues: Set<String>, val includeLive: Boolean, val daysAhead: Int, val events: List<NovigEvent>, val markets: List<NovigMarket>, val fetchedAtMs: Long)

    private val mutex = Mutex()
    private var catalog: Catalog? = null
    private val references = HashMap<String, RefSnapshot>()
    private val referenceFailures = HashMap<String, Long>()
    private var creditsRemaining: Int? = null
    private var plan: Plan? = null
    private var planInputs: Any? = null
    private var books: Map<String, NovigBook> = emptyMap()
    private var lastResult: ScanResult? = null

    suspend fun refresh(settings: ScanSettings, kind: RefreshKind, reference: ReferenceSource?): RefreshReport = mutex.withLock {
        val now = clock()
        val errors = ArrayList<String>()

        // 1. Reference odds (credits).
        if (reference != null) {
            for (league in settings.selectedLeagues) {
                val sport = league.oddsApiSportKey
                val have = references[sport]
                val failedAt = referenceFailures[sport]
                val due = when (kind) {
                    RefreshKind.FULL -> true
                    RefreshKind.AUTO -> when {
                        have == null -> failedAt == null || now - failedAt >= referenceRetryMs
                        settings.referenceRefreshMinutes > 0 -> now - have.fetchedAtMs >= settings.referenceRefreshMinutes * 60_000L
                        else -> false
                    }
                }
                if (!due) continue
                try {
                    // Stamp with our own clock: the auto-refresh interval (credits) must never
                    // depend on what time a provider claims it answered.
                    val snap = reference.odds(sport, settings.referenceBooks).copy(fetchedAtMs = now)
                    references[sport] = snap
                    referenceFailures.remove(sport)
                    snap.creditsRemaining?.let { creditsRemaining = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AllKeysExhaustedException) {
                    referenceFailures[sport] = now
                    errors += e.message ?: "Every Odds API key is used up"
                    break // every sport would fail the same way
                } catch (e: Exception) {
                    referenceFailures[sport] = now
                    errors += "${league.displayName} fair odds: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }

        // 2. Novig catalog.
        val c = catalog
        val catalogDue = kind == RefreshKind.FULL || c == null || c.leagues != settings.leagues ||
            c.includeLive != settings.includeLive || c.daysAhead != settings.daysAhead || now - c.fetchedAtMs >= catalogTtlMs
        if (catalogDue && settings.leagues.isNotEmpty()) {
            try {
                val statuses = buildList {
                    add(NovigEvent.STATUS_PREGAME)
                    if (settings.includeLive) add(NovigEvent.STATUS_LIVE)
                }
                val leagues = settings.leagues.toList()
                // One extra day of slack past the horizon; Planner applies the exact cut.
                val before = now + (settings.daysAhead.coerceAtLeast(1) + 1) * 86_400_000L
                val events = novig.events(leagues, statuses, before)
                val markets = novig.markets(leagues, MarketFamily.entries.flatMap { it.novigTypes }, statuses, before)
                catalog = Catalog(settings.leagues, settings.includeLive, settings.daysAhead, events, markets, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "Novig catalog: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        // 3. Plan (only rebuilt when its inputs changed).
        val cat = catalog
        val currentPlan = if (cat != null && settings.leagues.isNotEmpty()) planFor(cat, settings, now) else null

        // 4. Books for the planned markets.
        var retryAfter: Int? = null
        var fetched = 0
        var notModified = 0
        if (currentPlan != null && currentPlan.markets.isNotEmpty()) {
            novig.focus(currentPlan.markets.mapTo(HashSet()) { it.event.eventId })
            try {
                val batch = novig.books(currentPlan.marketIds)
                books = batch.books
                fetched = batch.fetched
                notModified = batch.notModified
                retryAfter = batch.retryAfterSeconds
                if (batch.failed > 0 && batch.lastError != null) errors += "Novig books: ${batch.lastError}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "Novig books: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val result = currentPlan?.let { Pricing.price(it, books, settings, now) }
        if (result != null) lastResult = result
        report(result, errors, retryAfter, fetched, notModified, reference != null)
    }

    /** Re-price what's already fetched under new settings. No network. */
    suspend fun reprice(settings: ScanSettings): ScanResult? = mutex.withLock {
        val cat = catalog ?: return@withLock null
        if (cat.leagues != settings.leagues || cat.includeLive != settings.includeLive || cat.daysAhead != settings.daysAhead) {
            return@withLock lastResult
        }
        val p = planFor(cat, settings, clock())
        Pricing.price(p, books, settings, clock()).also { lastResult = it }
    }

    private fun planFor(cat: Catalog, settings: ScanSettings, now: Long): Plan {
        val refs = settings.selectedLeagues.mapNotNull { l -> references[l.oddsApiSportKey]?.let { l.oddsApiSportKey to it } }.toMap()
        // Identity of every input the plan depends on. The time horizon moves slowly, so it's
        // bucketed to the hour; otherwise the plan would rebuild on every tick for nothing.
        val inputs = listOf(
            System.identityHashCode(cat), refs.values.map { System.identityHashCode(it) },
            settings.leagues, settings.families, settings.includeLive, settings.daysAhead, now / 3_600_000L,
        )
        val existing = plan
        if (existing != null && inputs == planInputs) return existing
        return Planner.plan(cat.events, cat.markets, refs, settings, now).also {
            plan = it
            planInputs = inputs
        }
    }

    private fun report(result: ScanResult?, errors: List<String>, retryAfter: Int?, fetched: Int, notModified: Int, hasRef: Boolean) =
        RefreshReport(
            result = result,
            errors = errors,
            retryAfterSeconds = retryAfter,
            booksFetched = fetched,
            booksNotModified = notModified,
            novigCatalogAtMs = catalog?.fetchedAtMs,
            referenceAtMs = references.mapValues { it.value.fetchedAtMs },
            creditsRemaining = creditsRemaining,
            hasReferenceSource = hasRef,
        )
}
