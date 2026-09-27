package com.tjshea.vigilant.data.book

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.reference.PartialReferenceException
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.PropLineProps
import com.tjshea.vigilant.data.reference.PropLinePropsSource
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceException
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.ScanContext
import com.tjshea.vigilant.data.reference.OddsApiPropsSource
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.reference.readableError
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.OddsScanner
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.RecheckReport
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.scanner.SourceReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Vigilant's scan for a sportsbook's posted odds (Vigilant MGM: BetMGM), on Tj's tap only, like
 * Novig's [Scanner]. **No request is made just for the book**: its prices ride in the calls that
 * already fetch the other books for the fair line. The book is simply asked for alongside them
 * (PropLine's league board and per-game props, The Odds API as their fallback), then split off:
 * its quotes become the board ([BookBoard]) and never price their own fair line.
 *
 * Per scan:
 *  1. Game lines, providers in parallel (Pinnacle, Polymarket, Kalshi, PropLine; The Odds API only
 *     where PropLine couldn't answer, RESEARCH.md §23). Same re-use (≤2 min), failure and freshness
 *     rules as [Scanner] (RESEARCH.md §24).
 *  2. The book's board: its games and lines out of PropLine's (else The Odds API's) league boards.
 *  3. Player props for the book's games (PropLine per game, soonest first; The Odds API props only
 *     where PropLine didn't price), which bring the book's own props in the same requests.
 *  4. Plan and price with the same [Planner] matching and [Pricing] as Novig: the book's price is its
 *     posted odds, exact, with no fee. Results stream as each provider answers.
 *
 * [recheck] re-reads the book's game lines (one request per league, PropLine) and re-prices; the
 * other feeds' answers stand, inside the same 5-minute limit.
 */
class SportsbookScanner(
    val book: Sportsbook,
    private val clock: () -> Long = System::currentTimeMillis,
) : OddsScanner {

    private data class Cached(val snapshot: RefSnapshot, val requestKey: String)

    private val mutex = Mutex()

    /** Latest snapshot per `"$sourceId|$league"`. */
    private val references = HashMap<String, Cached>()

    /** The `"$sourceId|$league"` keys answered this scan. */
    private val answered = HashSet<String>()
    private var creditsRemaining: Int? = null

    /** The leagues and sources the last scan ran with (recheck re-reads through the same feeds). */
    private var lastLeagues: Set<String> = emptySet()
    private var lastSources: List<ReferenceSource> = emptyList()
    private var scannedAtMs: Long? = null

    override suspend fun scan(
        settings: ScanSettings,
        sources: List<ReferenceSource>,
        pinned: Set<String>,
        onProgress: (ScanProgress) -> Unit,
        onPartial: (ScanResult) -> Unit,
    ): ScanReport = mutex.withLock {
        val now = clock()
        val errors = ArrayList<String>()
        val leagues = settings.selectedLeagues
        if (leagues.isEmpty()) return@withLock report(null, errors, emptyList(), 0)
        val asked = forBook(settings)
        val ordered = sources.sortedBy { Scanner.SOURCE_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        val total = ordered.sumOf { s -> leagues.count { s.supports(it) } }
        val done = AtomicInteger(0)
        fun progress() = onProgress(ScanProgress("${book.displayName} and fair odds", done.get(), total))
        progress()
        val publishLock = Any()
        fun publish() {
            val r = runCatching { priced(settings, now, now) }.getOrNull() ?: return
            synchronized(publishLock) { onPartial(r.copy(freshSinceMs = now)) }
        }

        synchronized(answered) { answered.clear() }
        val sourceReports = coroutineScope {
            val jobs = HashMap<String, Deferred<SourceReport>>()
            // Game lines: first choices at once; a fallback after the source it backs up, told what that one gave.
            val lineSources = ordered.filter { !it.needsCatalog }
            for (source in lineSources.sortedBy { if (it.fallbackFor == null) 0 else 1 }) {
                val first = source.fallbackFor?.let { jobs[it] }
                jobs[source.id] = async {
                    first?.await()
                    val context = if (first != null) covering(source.fallbackFor!!, leagues, ScanContext(board(asked, now).events, now = now)) else null
                    fetch(source, leagues, asked, now, errors, context, fallback = first != null) { done.incrementAndGet(); progress() }
                        .also { publish() }
                }
            }
            // Props need the book's games: they start once every feed that carries its game lines has answered.
            val boardJobs = lineSources.filter { it.id in BOARD_LINE_FEEDS }.mapNotNull { jobs[it.id] }
            for (source in ordered.filter { it.needsCatalog }.sortedBy { if (it.fallbackFor == null) 0 else 1 }) {
                val first = source.fallbackFor?.let { jobs[it] }
                jobs[source.id] = async {
                    boardJobs.awaitAll()
                    first?.await()
                    val base = propContext(board(asked, now), asked, now)
                    val context = if (first != null) covering(source.fallbackFor!!, leagues, base) else base
                    fetch(source, leagues, asked, now, errors, context, fallback = first != null) { done.incrementAndGet(); progress() }
                        .also { publish() }
                }
            }
            ordered.map { jobs.getValue(it.id) }.awaitAll()
        }

        lastLeagues = settings.leagues
        lastSources = sources
        scannedAtMs = now
        val board = board(asked, now)
        val priceable = board.takeIf { it.games.isNotEmpty() }?.plan(fair(settings, now), settings, now, youngOnly = true)
        val result = priceable?.let { BookBoard.withBookAges(Pricing.price(it.plan, it.books, settings, now), it.seenAt) }
        val reports = sourceReports.map { r -> r.copy(matched = priceable?.plan?.events?.count { r.id in it.providers } ?: 0) }
        report(result, errors, reports, result?.stats?.marketsPriced ?: 0)
    }

    override suspend fun recheck(
        settings: ScanSettings,
        marketIds: Collection<String>,
        onProgress: (Int, Int) -> Unit,
    ): RecheckReport = mutex.withLock {
        if (scannedAtMs == null) return@withLock RecheckReport(null, 0, 0, null)
        val now = clock()
        val asked = forBook(settings)
        val before = board(asked, now)
        val ids = marketIds.distinct()
        val leagueNames = ids.mapNotNullTo(LinkedHashSet()) { id -> before.game(id.substringBefore('|'))?.league?.novigName }
        val leagues = settings.selectedLeagues.filter { it.novigName in leagueNames }
        // The feed that gave the book's lines last time, PropLine first: one request per league.
        val feeds = lastSources.filter { it.id in BOARD_LINE_FEEDS }
            .sortedBy { BOARD_LINE_FEEDS.indexOf(it.id) }
            .filter { s -> leagues.any { l -> synchronized(references) { references["${s.id}|${l.novigName}"] } != null } }
        var error: String? = null
        val readLeagues = HashSet<String>()
        var i = 0
        for (league in leagues) {
            onProgress(i++, leagues.size)
            for (source in feeds) {
                if (!source.supports(league)) continue
                val key = "${source.id}|${league.novigName}"
                try {
                    val snap = source.odds(league, asked).copy(fetchedAtMs = now, provider = source.id).seenBy(now)
                    synchronized(references) { references[key] = Cached(snap, requestKey(source, asked)) }
                    snap.creditsRemaining?.let { creditsRemaining = it }
                    readLeagues += league.novigName
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = (e as? PartialReferenceException)?.message ?: "${source.displayName} ${league.displayName}: ${readableError(e)}"
                }
            }
        }
        onProgress(leagues.size, leagues.size)
        val result = priced(settings, now, now)
        val after = board(asked, now)
        // A line counts as read when its league was re-read and the book still lists it.
        val readIds = ids.filter { id ->
            val game = after.game(id.substringBefore('|')) ?: return@filter false
            game.league.novigName in readLeagues && game.lines.containsKey(id.substringAfter('|'))
        }
        RecheckReport(result, readIds.size, ids.size - readIds.size, error)
    }

    override suspend fun reprice(settings: ScanSettings): ScanResult? = mutex.withLock {
        if (scannedAtMs == null || settings.leagues.isEmpty()) return@withLock null
        val now = clock()
        priced(settings, now, now)
    }

    override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = mutex.withLock { settings.leagues - lastLeagues }

    /**
     * The settings every feed is asked with: the book first among the reference books, so it's always
     * asked for (The Odds API names at most 10 books per region: the book is never the one dropped).
     */
    fun forBook(settings: ScanSettings): ScanSettings =
        settings.copy(referenceBooks = listOf(book.feedKey) + settings.referenceBooks.filter { it != book.feedKey })

    /** The book's board from what's been fetched (every answer, however old: [BookBoard.plan] ages lines out). */
    private fun board(asked: ScanSettings, now: Long): BookBoard {
        val snaps = synchronized(references) {
            asked.selectedLeagues.flatMap { l -> BOARD_FEEDS.mapNotNull { id -> references["$id|${l.novigName}"]?.snapshot } }
        }
        return BookBoard.build(book, snaps, now, now)
    }

    /** Everything priced from what's been fetched, as of [now]; fair lines judged at [fairAsOf]. Null with no board. */
    private fun priced(settings: ScanSettings, now: Long, fairAsOf: Long): ScanResult? {
        val asked = forBook(settings)
        val board = board(asked, now)
        if (board.games.isEmpty()) return null
        val priceable = board.plan(fair(settings, fairAsOf), settings, now, youngOnly = true)
        return BookBoard.withBookAges(Pricing.price(priceable.plan, priceable.books, settings, now), priceable.seenAt)
    }

    /**
     * The fair-line feeds as of [fairAsOf]: every switched-on provider's answer young enough to price,
     * each quote seen within [Freshness.MAX_QUOTE_AGE_MS], the sportsbook feeds cut to the books Tj
     * picked, and **the book itself never among them**.
     */
    private fun fair(settings: ScanSettings, fairAsOf: Long): List<RefSnapshot> {
        val enabled = settings.enabledSources
        val picked = settings.referenceBooks.toSet() - book.feedKey
        val refs = synchronized(references) {
            settings.selectedLeagues.flatMap { l ->
                Scanner.SOURCE_ORDER.filter { it in enabled }.mapNotNull { id -> references["$id|${l.novigName}"]?.snapshot }
            }
        }.filter { fairAsOf - it.fetchedAtMs <= Freshness.MAX_QUOTE_AGE_MS }
        return refs.map { snap ->
            snap.copy(
                events = snap.events.map { e ->
                    e.copy(
                        markets = e.markets.filter {
                            it.bookKey != book.feedKey && Freshness.fresh(it.lastUpdateMs, fairAsOf) &&
                                (snap.provider !in PICKED_BOOK_FEEDS || it.bookKey in picked)
                        },
                    )
                },
            )
        }
    }

    /**
     * What the props feeds choose from: the book's pregame games and, for each, every prop stat the
     * feeds can price and the settings include (one PropLine request per game asks for all of them).
     */
    private fun propContext(board: BookBoard, settings: ScanSettings, now: Long): ScanContext {
        val stats = PropLineProps.STATS.filter { it in settings.novigMarketTypes }
        val markets = if (stats.isEmpty()) emptyList() else board.games.flatMap { g ->
            stats.map { stat -> NovigMarket("${g.event.eventId}|want|$stat", g.event.eventId, stat, "OPEN", stat, g.event.startsTs, BookBoard.NO_FEE, emptyList()) }
        }
        return ScanContext(board.events, markets, now)
    }

    /** What [firstId] gave this scan, per board game, for its fallback (like [Scanner]'s). */
    private fun covering(firstId: String, leagues: List<League>, base: ScanContext): ScanContext {
        val covered = HashMap<String, MutableSet<String>>()
        val answeredLeagues = HashSet<String>()
        for (league in leagues) {
            val key = "$firstId|${league.novigName}"
            if (synchronized(answered) { key !in answered }) continue
            answeredLeagues += league.novigName
            val snap = synchronized(references) { references[key]?.snapshot } ?: continue
            val games = base.novigEvents.filter { it.league == league.novigName }
            for (m in Planner.matchEvents(games, listOf(snap))) {
                val ref = m.refEvent ?: continue
                covered.getOrPut(m.event.eventId) { HashSet() } += ref.markets.map { it.coverage }
            }
        }
        return base.copy(covered = covered, firstAnswered = answeredLeagues)
    }

    /** One provider across the leagues: re-use, fallback standby, partial answers and failures as [Scanner] does. */
    private suspend fun fetch(
        source: ReferenceSource,
        leagues: List<League>,
        settings: ScanSettings,
        now: Long,
        errors: MutableList<String>,
        context: ScanContext?,
        fallback: Boolean,
        onCall: () -> Unit,
    ): SourceReport {
        var fetched = 0
        var reused = 0
        var standingBy = 0
        var error: String? = null
        val requestKey = requestKey(source, settings)
        val reuseMs = minOf(source.reuseMs(settings), Freshness.MAX_REUSE_MS)
        for (league in leagues) {
            if (!source.supports(league)) continue
            val key = "${source.id}|${league.novigName}"
            val have = synchronized(references) { references[key] }
            if (have != null && have.requestKey == requestKey && reuseMs > 0 && now - have.snapshot.fetchedAtMs < reuseMs) {
                reused++
                synchronized(answered) { answered += key }
                onCall()
                continue
            }
            if (error != null && source.metered) {
                onCall()
                continue
            }
            try {
                if (fallback && context != null && !source.needed(league, settings, context)) {
                    synchronized(references) { references.remove(key) }
                    standingBy++
                    onCall()
                    continue
                }
                val snap = (if (context != null) source.odds(league, settings, context) else source.odds(league, settings))
                    .copy(fetchedAtMs = now, provider = source.id).seenBy(now)
                synchronized(references) { references[key] = Cached(snap, requestKey) }
                synchronized(answered) { answered += key }
                snap.creditsRemaining?.let { creditsRemaining = it }
                fetched++
            } catch (e: CancellationException) {
                throw e
            } catch (e: PartialReferenceException) {
                synchronized(references) { references[key] = Cached(e.partial.copy(fetchedAtMs = now, provider = source.id).seenBy(now), requestKey) }
                synchronized(answered) { answered += key }
                e.partial.creditsRemaining?.let { creditsRemaining = it }
                fetched++
                val message = e.message ?: source.displayName
                if (error == null) synchronized(errors) { errors += message }
                error = message
            } catch (e: Exception) {
                val message = when (e) {
                    is AllKeysExhaustedException, is ReferenceException -> e.message ?: source.displayName
                    else -> "${source.displayName} ${league.displayName}: ${readableError(e)}"
                }
                if (error == null) synchronized(errors) { errors += message }
                error = message
                synchronized(references) {
                    val old = references[key]
                    if (old != null && now - old.snapshot.fetchedAtMs > settings.staleReferenceMinutes * 60_000L) references.remove(key)
                }
            }
            onCall()
        }
        return SourceReport(source.id, source.displayName, fetched, reused, 0, error, standingBy)
    }

    private fun requestKey(source: ReferenceSource, settings: ScanSettings): String = when (source.id) {
        TheOddsApiClient.ID -> "${settings.referenceBooks.sorted()}|${settings.families.sorted()}"
        OddsApiPropsSource.ID -> "${settings.referenceBooks.sorted()}|${settings.bookPropSet}|${settings.bookPropCreditsPerScan}|${settings.bookPropHours}"
        PropLineClient.ID -> "${settings.referenceBooks.sorted()}|${settings.families.sorted()}"
        PropLinePropsSource.ID -> "${settings.referenceBooks.sorted()}|${settings.bookPropHours}"
        else -> "${settings.families.sorted()}|${settings.exchangeMaxSpread}|${settings.daysAhead}"
    }

    /** Every quote's "last seen" as its feed said, never later than [now]; [now] where it didn't say ([Freshness]). */
    private fun RefSnapshot.seenBy(now: Long): RefSnapshot = copy(
        events = events.map { e ->
            if (e.markets.all { it.lastUpdateMs != null && it.lastUpdateMs <= now }) e
            else e.copy(markets = e.markets.map { m -> if (m.lastUpdateMs != null && m.lastUpdateMs <= now) m else m.copy(lastUpdateMs = minOf(m.lastUpdateMs ?: now, now)) })
        },
    )

    private fun report(result: ScanResult?, errors: List<String>, sources: List<SourceReport>, lines: Int) = ScanReport(
        result = result,
        errors = errors,
        retryAfterSeconds = null,
        booksFetched = lines,
        booksNotModified = 0,
        booksFromCache = 0,
        booksViaKey = 0,
        novigCatalogAtMs = scannedAtMs,
        sources = sources,
        creditsRemaining = creditsRemaining,
    )

    companion object {
        /** Feeds that carry the book's game lines, in priority order. */
        val BOARD_LINE_FEEDS = listOf(PropLineClient.ID, TheOddsApiClient.ID)

        /** Every feed the board is built from: game lines first, then the per-game props. */
        val BOARD_FEEDS = BOARD_LINE_FEEDS + listOf(PropLinePropsSource.ID, OddsApiPropsSource.ID)

        /** Sportsbook feeds whose books follow the reference-book picker in Settings. */
        private val PICKED_BOOK_FEEDS = setOf(TheOddsApiClient.ID, OddsApiPropsSource.ID, PropLineClient.ID, PropLinePropsSource.ID)
    }
}
