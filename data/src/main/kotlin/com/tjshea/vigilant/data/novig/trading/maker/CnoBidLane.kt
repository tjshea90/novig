package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoException
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigLive
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the background cycle reads from CrazyNinjaOdds for bids priced from it ([ScanSettings.bidsFromCno], Tj, 2026-10-07; RESEARCH.md §113-§114), and the lines [MakerRunner]'s
 * pass judges from it. One owner, one lifetime: [step] is called by the cycle (under its lock, inside its time budget) and nothing here launches work of its own; [lines] is
 * called by a pass (under the runner's pass lock) and by the Bids tab (no request at all).
 *
 * The cycle's list read ([CnoFeed.refresh]) has already happened. [step] then:
 *  1. asks for the wide read (CNO's list with its filters opened: sides at 0% EV and up, not only the taker list's 1%+; the study's own read, shared; paced by [CnoFeed.readWide]);
 *  2. picks the rows a bid could go on ([CnoBidCandidates]);
 *  3. reads the game pages that are due: the games with a bid up every [BID_REREAD_MS] (a resting bid is hurt exactly when the fair moves, §70.3), the other candidates when
 *     their page is over [CANDIDATE_REREAD_MS] old, at most [MAX_PAGES_PER_STEP] a step, each [PAGE_GAP_MS] after the last, none while CNO asked for a pause.
 *
 * [lines] resolves each judged row on Novig (market, outcome, the book NOW) and builds the [MakerLine]s ([CnoMakerLines]). A bid whose game isn't a candidate any more is still judged:
 * its row is the one it was posted from, or a stand-in made from the bid.
 */
class CnoBidLane(
    private val cno: CnoFeed,
    /** The Novig market, outcome and current book of each row, read now ([NovigLive.targetsNow]). */
    private val novig: suspend (List<CnoRow>) -> Map<String, NovigLive.Resolved>,
    /** The CNO view the cycle reads ([com.tjshea.vigilant.app.UiState.cnoUrl]). */
    private val urlFor: (ScanSettings) -> String,
    /** Asks the injury feeds about the prop players of these rows (a no-op when none is on). */
    private val askInjuries: (List<CnoRow>) -> Unit = {},
    private val count: (String, Long) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
    private val pageGapMs: Long = PAGE_GAP_MS,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    /** What the Bids tab, Diagnostics and the health checks show about the lane. */
    data class Status(
        val lastStepMs: Long? = null,
        /** Rows in the lists the last step saw, rows picked for bids, pages held, pages read by the last step. */
        val rows: Int = 0,
        val candidates: Int = 0,
        val pages: Int = 0,
        val pagesRead: Int = 0,
        /** The last step's page reads that failed, and why the last one did. */
        val failed: Int = 0,
        val lastError: String? = null,
        /** CNO's own "Last Updated" for the newest list, and how many lists in a row said nothing of it ([count] "cno.bid.listage.unknown" has the total). */
        val listAgeSec: Int? = null,
        val listAgeUnknown: Boolean = false,
        /** The oldest page behind a line now, in seconds since CNO updated it. */
        val oldestPageSec: Int? = null,
        /** Why the lane stops every bid from CNO now, or null. */
        val stop: String? = null,
        /** Why rows were left without a bid, reason → how many (the pass's own skips are in the decisions). */
        val skipped: Map<String, Int> = emptyMap(),
        val wideRows: Int = 0,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Rows seen on the lists, by [pageKey], with when. Replaced whole (copy on write); read by [lines] with no lock. */
    private class Known(val row: CnoRow, val seenMs: Long, val current: Boolean)

    /** A game page read (or tried): the view, or null when the page didn't list the bet. */
    private class Held(val view: CnoBooksView?, val readAtMs: Long, val triedAtMs: Long)

    @Volatile private var known: Map<String, Known> = emptyMap()
    @Volatile private var held: Map<String, Held> = emptyMap()
    @Volatile private var picked: List<CnoRow> = emptyList()
    @Volatile private var resolved: Map<String, NovigLive.Resolved> = emptyMap()

    private val stepLock = Mutex()

    /** What a pass gets: the lines, why every CNO bid must come down instead ([stop]), the list's data time, and why rows gave no line. */
    data class LineSet(val lines: List<MakerLine>, val stop: String?, val listAtMs: Long?, val skipped: Map<String, Int>)

    // ---- the cycle's reads ---------------------------------------------------------------------------------------------------

    /**
     * Reads what bids from CNO need, inside the cycle ([bids]: the desk's bids, whose games are re-read first). Never throws except to cancel; a read that fails is a count and a
     * line in [Status], and the lines it would have made are old by the time the limit passes (the bids then come down).
     */
    suspend fun step(s: ScanSettings, bids: List<MakerBid>) = stepLock.withLock {
        val now = clock()
        val rules = MakerRules.of(s)
        try {
            cno.readWide(urlFor(s), s.cnoFilters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            count("cno.bid.wide.failed", 1L)
        }
        val snap = cno.state.value.snapshot
        val wide = cno.wide.value.snapshot
        val seen = LinkedHashMap<String, Known>()
        // Rows the lists carry now, the wide read's first (more of them), the list's over them (the newer).
        for (r in wide?.rows.orEmpty()) seen[pageKey(r)] = Known(r, now, current = true)
        for (r in snap?.rows.orEmpty()) seen[pageKey(r)] = Known(r, now, current = true)
        val current = seen.values.map { it.row }
        // Rows seen before stay a while (a row that leaves the list is still a game to judge a bid on); they no longer vouch for CNO's fair of the row.
        for ((k, v) in known) if (k !in seen && now - v.seenMs <= KNOWN_KEEP_MS) seen[k] = Known(v.row, v.seenMs, current = false)
        val bidRows = activeBidRows(bids, seen)
        for (r in bidRows) seen.putIfAbsent(pageKey(r), Known(r, now, current = false))
        known = seen
        picked = CnoBidCandidates.pick(current, s.cnoFilters, rules, now)
        // The bet's books the auto-bet or the green check read lately are as good as ours.
        val fromTaps = cno.books.value
        val adopted = HashMap(held)
        for (r in picked + bidRows) {
            val view = fromTaps[r.key]?.view ?: continue
            val have = adopted[pageKey(r)]
            if (have == null || (have.view == null || view.fetchedAtMs > have.view.fetchedAtMs)) adopted[pageKey(r)] = Held(view, view.fetchedAtMs, view.fetchedAtMs)
        }
        held = adopted
        var read = 0
        var failed = 0
        var lastError: String? = null
        val due = (bidRows.filter { isDue(it, BID_REREAD_MS, now) } + picked.filter { isDue(it, CANDIDATE_REREAD_MS, now) }).distinctBy { pageKey(it) }.take(MAX_PAGES_PER_STEP)
        for ((i, row) in due.withIndex()) {
            if ((cno.state.value.pausedUntilMs ?: 0L) > clock()) break
            if (i > 0) pause(pageGapMs)
            try {
                val view = cno.readBooks(row)
                held = held + (pageKey(row) to Held(view, clock(), clock()))
                read++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                lastError = (e as? CnoException)?.message ?: e.message ?: e.javaClass.simpleName
                held = held + (pageKey(row) to Held(held[pageKey(row)]?.view, held[pageKey(row)]?.readAtMs ?: 0L, clock()))
                count("cno.bid.page.failed", 1L)
                // CNO asked for a pause ([CnoFeed.readBooks] noted it) or the net is down: no more this step.
                if (e is CnoException && e.retryAfterSeconds != null) break
                if (failed >= 2) break
            }
        }
        if (read > 0) count("cno.bid.page.read", read.toLong())
        // The injury feeds are asked about the players of the rows that may get a bid.
        runCatching { askInjuries(picked + bidRows) }.onFailure { if (it is CancellationException) throw it }
        // Held pages for rows nobody wants any more are let go.
        val wanted = (picked + bidRows).mapTo(HashSet()) { pageKey(it) }
        held = held.filter { (k, h) -> k in wanted || now - h.readAtMs <= KNOWN_KEEP_MS }
        if (snap != null && snap.cnoAgeSeconds == null) count("cno.bid.listage.unknown", 1L)
        _status.update {
            it.copy(
                lastStepMs = now, rows = current.size, candidates = picked.size, pages = held.values.count { h -> h.view != null }, pagesRead = read, failed = failed,
                lastError = lastError ?: it.lastError.takeIf { failed > 0 }, listAgeSec = snap?.cnoAgeSeconds, listAgeUnknown = snap != null && snap.cnoAgeSeconds == null,
                stop = stopReason(now), wideRows = wide?.rows?.size ?: 0,
            )
        }
    }

    private fun isDue(row: CnoRow, rereadMs: Long, now: Long): Boolean {
        val h = held[pageKey(row)] ?: return true
        // A page tried and failed a moment ago waits before another try (one request a minute for a page that errors).
        if (now - h.triedAtMs < RETRY_MS && h.triedAtMs > h.readAtMs) return false
        return now - h.readAtMs >= rereadMs
    }

    /**
     * The row each of Vigilant's CNO bids was posted from (still known; its page holds the bid's side and its complement, so the listed row serves either), or a stand-in made from
     * the bid. One row a page: two bids on the two sides of one line are one read.
     */
    private fun activeBidRows(bids: List<MakerBid>, seen: Map<String, Known>): List<CnoRow> = bids.filter { it.active && it.source == CnoMakerLines.SOURCE }.map { b ->
        seen.values.firstOrNull { k ->
            k.row.gameUrl != null && k.row.gameUrl == b.gameUrl && (sameSide(k.row.bet, b.selection) || com.tjshea.vigilant.data.cno.CnoBooks.complement(k.row.bet, b.selection))
        }?.row ?: standIn(b)
    }.distinctBy { it.key }

    private fun sameSide(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    // ---- the pass's lines ----------------------------------------------------------------------------------------------------

    /**
     * Why every bid from CNO must come down now, or null: CNO asked for a pause, its list was never read, doesn't say how old it is, hasn't updated for 10 minutes
     * ([CnoChecks.stuck]) or failed [ERRORS_STOP] reads in a row. Age of the data behind each line is judged line by line ([CnoMakerLines]).
     */
    fun stopReason(now: Long): String? {
        val st = cno.state.value
        st.pausedUntilMs?.takeIf { it > now }?.let { return "CrazyNinjaOdds asked for a pause (${st.lastPause ?: "busy"}): bids priced from it come down" }
        val snap = st.snapshot ?: return "CrazyNinjaOdds hasn't been read yet: no bid priced from it"
        if (snap.cnoAgeSeconds == null) return "CrazyNinjaOdds' list doesn't say how old its odds are: bids priced from it wait"
        if (CnoChecks.stuck(snap, now)) return "CrazyNinjaOdds hasn't updated its odds for over 10 minutes: bids priced from it come down"
        if (st.errors >= ERRORS_STOP) return "CrazyNinjaOdds couldn't be read ${st.errors} times in a row: bids priced from it come down"
        return null
    }

    /**
     * The lines of every row a bid could go on, and of every side a bid is up on, as of [now]. [readNovig]: Novig's books are read now (a pass) rather than the last read's (the
     * Bids tab). [unavailable]: why a row's player must not be bid on.
     */
    suspend fun lines(s: ScanSettings, now: Long, bids: List<MakerBid>, readNovig: Boolean, unavailable: (CnoRow) -> String? = { null }): LineSet {
        val stop = stopReason(now)
        val snap = cno.state.value.snapshot
        val listAt = snap?.takeIf { it.cnoAgeSeconds != null }?.dataAtMs
        val seen = known
        val bidRows = activeBidRows(bids, seen)
        val rows = (picked + bidRows).distinctBy { it.key }.filter { held[pageKey(it)]?.view != null }
        val got = if (readNovig && rows.isNotEmpty()) novig(rows).also { resolved = it } else resolved
        val missed = rows.count { it.key !in got }
        val pages = ArrayList<CnoMakerLines.Page>()
        val covered = HashSet<String>()
        // Candidates first; a bid's stand-in only for a side no candidate page covers.
        for (r in rows) {
            val t = got[r.key] ?: continue
            val isBidRow = r in bidRows && r !in picked
            if (isBidRow && (t.outcomeId in covered || t.market.otherOutcome(t.outcomeId)?.outcomeId in covered)) continue
            val current = seen[pageKey(r)]?.current == true
            val row = if (current) r else r.copy(fairProbability = null, fairOdds = null)
            pages += CnoMakerLines.Page(row, t.outcomeId, t.market, t.book, held[pageKey(r)]?.view, listAt)
            if (!isBidRow) { covered += t.outcomeId; t.market.otherOutcome(t.outcomeId)?.outcomeId?.let { covered += it } }
        }
        val built = CnoMakerLines.from(pages, s, now, unavailable)
        val skipped = LinkedHashMap(built.skipped)
        if (missed > 0) skipped.merge("Novig doesn't list this exact bet (or its book couldn't be read)", missed, Int::plus)
        val oldest = built.lines.mapNotNull { it.pageAtMs }.minOrNull()
        _status.update { it.copy(skipped = skipped, oldestPageSec = oldest?.let { o -> ((now - o) / 1000L).coerceAtLeast(0L).toInt() }, stop = stop, listAgeSec = snap?.cnoAgeSeconds) }
        return LineSet(built.lines, stop, listAt, skipped)
    }

    companion object {
        /** A game with a bid up has its page read again after this long: a resting bid is hurt when the fair moves (§70.3, §113). */
        const val BID_REREAD_MS = 60_000L

        /** A candidate's page is read again after this long (a candidate whose page was read is judged on it until then). */
        const val CANDIDATE_REREAD_MS = 3 * 60_000L

        /** A page that failed waits this long before another try. */
        const val RETRY_MS = 60_000L

        /** The most pages one step reads (two requests each, [PAGE_GAP_MS] apart: about 6 s of a 15-second cycle). */
        const val MAX_PAGES_PER_STEP = 3

        /** The wait between two pages in a step, on top of the client's own pace (CNO's robots.txt asks for far more; the app reads far more than that already, §20). */
        const val PAGE_GAP_MS = 1_500L

        /** Rows seen on a list stay known this long after they leave it. */
        const val KNOWN_KEEP_MS = 10 * 60_000L

        /** CNO reads that failed in a row before every bid from it comes down. */
        const val ERRORS_STOP = 3

        /** A row's page: its game link without CNO's devig choice (the page is the same whichever book's row it is). */
        fun pageKey(row: CnoRow): String = row.gameUrl?.replace(Regex("[&?]devig_method=\\d+"), "") ?: "${row.event}|${row.market}|${row.bet}"

        /** A row standing in for the bet [b] was posted on, when the list that showed it has let it go: the page and the Novig market are found from it as from any row. */
        fun standIn(b: MakerBid): CnoRow = CnoRow(
            ev = 0.0, startsAtMs = b.startsTs, league = b.league, event = b.eventName, market = b.marketLabel, bet = b.selection, odds = 0, book = "Novig", gameUrl = b.gameUrl,
        )
    }
}
