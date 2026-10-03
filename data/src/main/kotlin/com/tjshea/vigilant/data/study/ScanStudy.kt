package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.AtBets
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.CloseBackfill
import com.tjshea.vigilant.data.tracker.CloseSource
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.PlacedIndex
import com.tjshea.vigilant.data.tracker.ScoreSource
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.abs

/**
 * The scan study (Tj, 2026-10-03): every bet a CNO or Vigilant scan lists is logged the moment it first appears, with everything the app knows about it
 * (the record as placed, [AtBets]: odds, type, EV, books and how many agree, minutes to the start, the sharp veto …), watched while it stays listed (each
 * change of price, EV or books, and when it drops off the list), graded and closed after its game with the app's own code ([BetSettler], [CloseBackfill]),
 * so that Settings › Tools › Share scan study with Claude can hand Claude every bet the scanners found, not only the ones Tj took, to find what beats the
 * close and profits.
 *
 * It costs the scans nothing: it reads what a scan already produced (the CNO list, the book pages the green check read, Novig's live prices, Vigilant's
 * result) and makes no request of its own while scanning; grading and the close lookups run beside the Tracker's own (the 3-hourly worker) through the
 * same score feed and close sources, and a bet Tj placed himself takes its result and close from the Tracker's bet (nothing looked up twice). Everything is
 * appended to a day's journal ([StudyJournal]) off the screen's thread, in one write every [FLUSH_MS]; any failure is kept and tried again, never thrown at a
 * scan.
 */
class ScanStudy(
    private val journal: StudyJournal,
    private val clock: () -> Long = System::currentTimeMillis,
    private val version: () -> String? = { null },
    private val flushEveryMs: Long = FLUSH_MS,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val settleLock = Mutex()

    /** A bet being watched in memory (the journal has the rest): what was last logged for it from each scanner, and whether each lists it now. */
    private class Active(val id: String, val day: LocalDate, val startsTs: Long, val league: String, val identity: String?) {
        val last = HashMap<String, Sight>()
        val lastAt = HashMap<String, Long>()
        val listed = HashSet<String>()
        var row: CnoRow? = null
        var viewAtMs = 0L
        var checked = false
        var vig = false
        var idsKnown = false
    }

    private val active = HashMap<String, Active>()
    private val byIdentity = HashMap<String, MutableList<Active>>()
    private val pending = LinkedHashMap<LocalDate, MutableList<Line>>()
    private var pendingCount = 0
    private var lastFlushMs = clock()
    private var lastCnoAt = Long.MIN_VALUE
    private var lastCnoBaseline: Pair<String, Any?>? = null
    private var lastVigAt = Long.MIN_VALUE
    private var lastVigBaseline: Any? = null
    private var hydrated = false

    /** Counts since this process started, for Diagnostics. */
    @Volatile
    var betsLogged = 0L
        private set

    @Volatile
    var sightsLogged = 0L
        private set

    @Volatile
    var lastLoggedAtMs: Long? = null
        private set

    /** The last thing that went wrong (a write the disk refused, a grading pass that failed), and when. */
    @Volatile
    var lastProblem: String? = null
        private set

    // ---- what a scan lists ----------------------------------------------------------------------------------------------

    /**
     * One CNO read ([snap]), as the app has it: each pregame row is a bet first listed (logged with its record as placed) or one already watched (logged
     * again when its price, EV or books moved, or after [HEARTBEAT_MS]); a bet the same list listed last time and doesn't now is logged as gone. [books] are
     * the game pages the green check read, [live] Novig's own price now for the top rows, [links] the Novig outcomes found so far. Returns the bets first
     * listed. The same read twice, or a list saved on disk and shown before this launch's first read, logs nothing.
     */
    suspend fun observeCno(snap: CnoSnapshot, s: ScanSettings, books: Map<String, CnoBooksState>, live: Map<String, LivePrice>, links: Map<String, String>): Int =
        mutex.withLock {
            if (!s.scanStudy) return@withLock 0
            val now = clock()
            if (snap.fetchedAtMs == lastCnoAt) return@withLock 0
            lastCnoAt = snap.fetchedAtMs
            if (now - snap.fetchedAtMs > MAX_SCAN_AGE_MS) return@withLock 0
            hydrate(now)
            prune(now)
            // A changed view or filters change what a list holds without the bets having gone: nothing is called gone across it.
            val baseline = snap.url to snap.filters
            val same = baseline == lastCnoBaseline
            lastCnoBaseline = baseline
            if (!same) active.values.forEach { it.listed.remove(Sight.CNO) }
            val filters = snap.filters ?: s.cnoFilters
            val seen = HashSet<String>()
            var created = 0
            for (row in snap.rows) {
                val starts = row.startsAtMs ?: continue
                if (starts <= now) continue
                val identity = PlacedIndex.identity(row.event, row.market, row.bet)
                val fallback = "c|${row.key}"
                val lp = live[row.key]?.takeIf { now - it.atMs <= LIVE_FRESH_MS }
                val view = books[row.key]?.view?.takeIf { now - it.fetchedAtMs <= VIEW_FRESH_MS }
                val priced = if (lp != null) row.copy(odds = lp.american) else row
                val ev = lp?.ev ?: row.ev
                val outcome = lp?.outcomeId ?: CnoFeed.outcomeIdOf(links[CnoFeed.linkKey(row)])
                val sight = Sight(Sight.CNO, o = priced.odds, ev = ev, f = CnoChecks.fairProbability(row), b = row.books, a = lp?.available ?: row.available)
                var a = find(identity, row.league, starts, fallback)
                if (a == null) {
                    val atBet = AtBets.cno(row, false, view, null, lp, snap.dataAtMs, s, now, AtBet.HOW_STUDY, BetTracker.SOURCE_CNO, version())
                    val bet = BetTracker.cnoBet(
                        priced, ev, false, null, STUDY_STAKE, lp?.marketId.orEmpty(), outcome.orEmpty(), BetTracker.SOURCE_CNO, atBet, idOf(identity, fallback, starts), now,
                    )
                    a = register(bet, identity, CnoChecks.rejection(row, filters, now)?.name, now, checked = view != null)
                    created++
                }
                seen += a.id
                a.listed += Sight.CNO
                a.row = row
                record(a, sight, now)
                if (!a.idsKnown && outcome != null) {
                    queue(a, Line(Line.IDS, a.id, now, m = lp?.marketId, o = outcome))
                    a.idsKnown = true
                }
            }
            if (same) sweepGone(Sight.CNO, Sight.GONE_CNO, seen, now)
            maybeFlush(now)
            created
        }

    /**
     * One finished Vigilant scan ([result]): the +EV bets its feed lists, the same way. A bet CNO listed too is the same bet (one record, looks from both);
     * Vigilant's own record of it (its fair line's method and books) is kept beside CNO's. Returns the bets first listed.
     */
    suspend fun observeVigilant(result: ScanResult, s: ScanSettings): Int = mutex.withLock {
        if (!s.scanStudy || result.partial) return@withLock 0
        val now = clock()
        if (result.computedAtMs == lastVigAt) return@withLock 0
        lastVigAt = result.computedAtMs
        hydrate(now)
        prune(now)
        val baseline = Triple(s.leagues, s.minEvPercent, s.maxEvPercent)
        val same = baseline == lastVigBaseline
        lastVigBaseline = baseline
        if (!same) active.values.forEach { it.listed.remove(Sight.VIGILANT) }
        val seen = HashSet<String>()
        var created = 0
        for (o in result.feed(s)) {
            val q = o.quote ?: continue
            val fair = o.fairProbability ?: continue
            if (o.isLive || o.event.startsTs <= now) continue
            val starts = o.event.startsTs
            val identity = PlacedIndex.identity(o.event.description, o.marketLabel, o.selection)
            val fallback = "v|${o.key}"
            val sight = Sight(
                Sight.VIGILANT, o = Odds.probabilityToAmerican(q.price.coerceIn(0.001, 0.999)), ev = q.evPercent, f = fair,
                b = o.fair?.booksUsed?.size, a = o.depth?.dollarCost,
            )
            var a = find(identity, o.league.displayName, starts, fallback)
            if (a == null) {
                val atBet = AtBets.opportunity(o, s, now, AtBet.HOW_STUDY, version())
                val bet = BetTracker.opportunityBet(o, STUDY_STAKE, null, atBet, idOf(identity, fallback, starts), now) ?: continue
                a = register(bet, identity, null, now, checked = false)
                a.vig = true
                created++
            } else if (!a.vig) {
                queue(a, Line(Line.VIG, a.id, now, a = AtBets.opportunity(o, s, now, AtBet.HOW_STUDY, version())))
                a.vig = true
            }
            seen += a.id
            a.listed += Sight.VIGILANT
            record(a, sight, now)
            if (!a.idsKnown) {
                queue(a, Line(Line.IDS, a.id, now, m = o.market.marketId, o = o.outcome.outcomeId))
                a.idsKnown = true
            }
        }
        if (same) sweepGone(Sight.VIGILANT, Sight.GONE_VIGILANT, seen, now)
        maybeFlush(now)
        created
    }

    /**
     * The game pages the green check read since: for each watched bet whose page is newer than the last one logged, the app's own book check (how many
     * books price both sides, how many agree, its EV, the sharp veto) is logged as a look, and the first one goes on the bet's record as placed. Reads
     * nothing: only what [books] already holds. Returns the checks logged.
     */
    suspend fun observeBooks(books: Map<String, CnoBooksState>, s: ScanSettings, live: Map<String, LivePrice>): Int = mutex.withLock {
        if (!s.scanStudy) return@withLock 0
        val now = clock()
        var n = 0
        for (a in active.values) {
            val row = a.row ?: continue
            if (Sight.CNO !in a.listed || a.startsTs <= now) continue
            val view = books[row.key]?.view ?: continue
            if (view.fetchedAtMs <= a.viewAtMs || now - view.fetchedAtMs > VIEW_FRESH_MS) continue
            a.viewAtMs = view.fetchedAtMs
            val lp = live[row.key]?.takeIf { now - it.atMs <= LIVE_FRESH_MS }
            val check = AtBets.cno(row, false, view, null, lp, null, s, now, AtBet.HOW_STUDY, BetTracker.SOURCE_CNO, version())
            if (!a.checked) {
                queue(a, Line(Line.CHECK, a.id, now, a = check))
                a.checked = true
            }
            val sight = Sight(Sight.CHECK, o = lp?.american ?: row.odds, g = check.agreeing, n = check.twoSided, ce = check.checkEv, sv = check.sharpVerdict)
            val prev = a.last[Sight.CHECK]
            val at = a.lastAt[Sight.CHECK]
            if (prev == null || at == null || now - at >= HEARTBEAT_MS || prev.g != sight.g || prev.n != sight.n || prev.sv != sight.sv ||
                abs((prev.ce ?: 0.0) - (sight.ce ?: 0.0)) >= CHECK_EV_STEP
            ) {
                a.last[Sight.CHECK] = sight
                a.lastAt[Sight.CHECK] = now
                queue(a, Line(Line.SIGHT, a.id, now, s = sight))
                sightsLogged++
                lastLoggedAtMs = now
                n++
            }
        }
        maybeFlush(now)
        n
    }

    /** Writes whatever is waiting to the journal now. */
    suspend fun flush() = mutex.withLock { flushLocked(clock()) }

    // ---- the in-memory index ---------------------------------------------------------------------------------------------

    private fun find(identity: String?, league: String, starts: Long, fallback: String): Active? {
        if (identity == null) return active[idOf(null, fallback, starts)]
        val tolerance = if (league.equals("MLB", ignoreCase = true)) PlacedIndex.SAME_BASEBALL_GAME_MS else PlacedIndex.SAME_GAME_MS
        return byIdentity[identity]?.firstOrNull { abs(it.startsTs - starts) <= tolerance }
    }

    /** A bet's id: the line and its start, so the same bet is the same id in any process and a restart can't make a second record of it. */
    private fun idOf(identity: String?, fallback: String, starts: Long): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${identity ?: fallback}|$starts".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(ID_LENGTH)
    }

    private fun register(bet: TrackedBet, identity: String?, screen: String?, now: Long, checked: Boolean): Active {
        val a = Active(bet.id, FreeScores.etDate(bet.startsTs), bet.startsTs, bet.league, identity)
        a.checked = checked
        a.idsKnown = bet.outcomeId.isNotEmpty()
        active[a.id] = a
        identity?.let { byIdentity.getOrPut(it) { ArrayList() } += a }
        queue(a, Line(Line.BET, a.id, now, b = bet, sc = screen))
        betsLogged++
        return a
    }

    private fun queue(a: Active, line: Line) {
        pending.getOrPut(a.day) { ArrayList() } += line
        pendingCount++
    }

    private fun record(a: Active, sight: Sight, now: Long) {
        val src = sight.k
        if (!worthLogging(a.last[src], a.lastAt[src], sight, now)) return
        a.last[src] = sight
        a.lastAt[src] = now
        queue(a, Line(Line.SIGHT, a.id, now, s = sight))
        sightsLogged++
        lastLoggedAtMs = now
    }

    /** Bets [src] listed last time and doesn't now (the list the same view and filters gave): logged as gone with how long before the start. */
    private fun sweepGone(src: String, gone: String, seen: Set<String>, now: Long) {
        for (a in active.values) {
            if (src !in a.listed || a.id in seen) continue
            a.listed.remove(src)
            a.last.remove(src)
            a.lastAt.remove(src)
            if (a.startsTs > now) {
                queue(a, Line(Line.SIGHT, a.id, now, s = Sight(gone)))
                sightsLogged++
                lastLoggedAtMs = now
            }
        }
    }

    /** Games that started long ago leave the index (their lines are in the journal). */
    private fun prune(now: Long) {
        val old = active.values.filter { it.startsTs < now - PRUNE_AFTER_MS }
        for (a in old) {
            active.remove(a.id)
            a.identity?.let { id -> byIdentity[id]?.let { list -> list.remove(a); if (list.isEmpty()) byIdentity.remove(id) } }
        }
    }

    /** After a restart: the bets already logged for the games still to come, so a bet the list still shows isn't logged a second time. */
    private suspend fun hydrate(now: Long) {
        if (hydrated) return
        hydrated = true
        val today = FreeScores.etDate(now)
        try {
            withContext(io) {
                for (day in (-1L..3L).map { today.plusDays(it) }) {
                    for ((id, sb) in journal.fold(day)) {
                        val b = sb.bet
                        if (b.startsTs < now - PRUNE_AFTER_MS || id in active) continue
                        val identity = PlacedIndex.identity(b.eventName, b.marketLabel, b.selection)
                        val a = Active(id, day, b.startsTs, b.league, identity)
                        a.checked = b.atBet?.let { it.checkAtMs != null || it.twoSided != null } == true
                        a.vig = sb.vig != null
                        a.idsKnown = b.outcomeId.isNotEmpty()
                        for ((t, sg) in sb.sights) {
                            if (Sight.isListing(sg.k) || sg.k == Sight.CHECK) {
                                a.last[sg.k] = sg
                                a.lastAt[sg.k] = t
                            }
                        }
                        active[id] = a
                        identity?.let { byIdentity.getOrPut(it) { ArrayList() } += a }
                    }
                }
            }
        } catch (e: CancellationException) {
            hydrated = false
            throw e
        } catch (e: Exception) {
            lastProblem = "reading the journal: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private suspend fun maybeFlush(now: Long) {
        if (pendingCount > 0 && now - lastFlushMs >= flushEveryMs) flushLocked(now)
    }

    private suspend fun flushLocked(now: Long) {
        lastFlushMs = now
        if (pendingCount == 0) return
        for ((day, lines) in pending.entries.toList()) {
            try {
                withContext(io) { journal.append(day, lines) }
                pendingCount -= lines.size
                pending.remove(day)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kept for the next flush; never thrown at a scan.
                lastProblem = "writing the journal: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        // A disk that stays refused must not grow memory without end: the oldest waiting lines go.
        while (pendingCount > MAX_PENDING && pending.isNotEmpty()) {
            val first = pending.entries.first()
            pendingCount -= first.value.size
            pending.remove(first.key)
        }
    }

    // ---- results ---------------------------------------------------------------------------------------------------------

    /** What a settle pass did: bets asked about, graded, closes found, and results copied from the Tracker's own bets. */
    data class SettleReport(val looked: Int, val graded: Int, val closed: Int, val copied: Int, val days: Int)

    /**
     * Grades and finds the closes of the logged bets whose games have started, with the app's own code, writing what it finds to the journal: first from
     * Tj's own bets on the same lines ([tracked]: their result, their close read before the start, Novig's close; nothing is looked up for those), then
     * the rest through [BetSettler] (final scores, box scores) and [CloseBackfill] ([sources]) run on a scratch Tracker file in [scratch] that is deleted
     * after. Bets whose result or close is still to come are asked again on the next pass (the 3-hourly worker), the backfill's own retry gaps kept (a look is
     * written to the journal). Never throws except for cancellation: a pass that fails is noted ([lastProblem]) and tried again.
     */
    suspend fun settle(scores: ScoreSource, sources: List<CloseSource>, tracked: List<TrackedBet>, scratch: File, heavyOk: Boolean = true): SettleReport = settleLock.withLock {
        flush()
        val now = clock()
        val today = FreeScores.etDate(now)
        val index = trackedIndex(tracked)
        var looked = 0
        var graded = 0
        var closed = 0
        var copied = 0
        var daysDone = 0
        try {
            for (day in journal.days().filter { it <= today && it >= today.minusDays(SETTLE_DAYS) }.reversed()) {
                val folded = withContext(io) { journal.fold(day) }
                val work = folded.values.filter { needsWork(it.bet, now) }
                if (work.isEmpty()) continue
                daysDone++
                looked += work.size
                val lines = ArrayList<Line>()
                val rest = ArrayList<StudyBet>()
                for (sb in work) {
                    val own = matching(sb.bet, index)
                    val r = own?.let { copyOf(sb.bet, it, now) }
                    if (r != null) {
                        lines += Line(Line.RES, sb.id, now, r = r)
                        sb.applyResult(now, r)
                        copied++
                    }
                    if (needsWork(sb.bet, now)) rest += sb
                }
                if (rest.isNotEmpty()) {
                    val found = harness(rest, scores, sources, heavyOk, scratch, now)
                    val byId = rest.associateBy { it.id }
                    for (l in found) {
                        val before = byId.getValue(l.id).bet
                        val after = StudyBet.applied(before, l.r!!)
                        if (before.status == BetStatus.PENDING && after.status != BetStatus.PENDING) graded++
                        if (ClosingLine.closeOf(before, now) == null && after.closeFair != null) closed++
                    }
                    lines += found
                }
                if (lines.isNotEmpty()) withContext(io) { journal.append(day, lines) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastProblem = "grading: ${e.message ?: e.javaClass.simpleName}"
        }
        SettleReport(looked, graded, closed, copied, daysDone)
    }

    /** The study bets [work] through the Tracker's own grading and close lookups, on a scratch file; the changes as result lines. */
    private suspend fun harness(work: List<StudyBet>, scores: ScoreSource, sources: List<CloseSource>, heavyOk: Boolean, scratch: File, now: Long): List<Line> {
        scratch.mkdirs()
        val file = File.createTempFile("study-grading", ".json", scratch)
        try {
            val tracker = BetTracker(file, clock)
            tracker.addAll(work.map { it.bet })
            val settler = BetSettler(tracker, scores, clock)
            for (pass in 1..MAX_PASSES) {
                val r = settler.run()
                if (r.asked == 0 || r.stopped) break
            }
            val backfill = CloseBackfill(tracker, sources, clock)
            for (pass in 1..MAX_PASSES) {
                if (backfill.run(heavyOk = heavyOk).looked == 0) break
            }
            val after = tracker.all().associateBy { it.id }
            return work.mapNotNull { sb -> after[sb.id]?.let { StudyBet.diff(sb.bet, it) }?.let { Line(Line.RES, sb.id, now, r = it) } }
        } finally {
            file.delete()
            File(file.path + ".tmp").delete()
        }
    }

    private fun trackedIndex(tracked: List<TrackedBet>): Map<String, List<TrackedBet>> = tracked.filter { !it.isLock && it.createdAtMs < it.startsTs }
        .groupBy { PlacedIndex.identity(it.eventName, it.marketLabel, it.selection) ?: "" }.filterKeys { it.isNotEmpty() }

    /** Tj's own bet on the same line (his Tracker's), for a result and a close already found. */
    private fun matching(b: TrackedBet, index: Map<String, List<TrackedBet>>): TrackedBet? {
        val identity = PlacedIndex.identity(b.eventName, b.marketLabel, b.selection) ?: return null
        val tolerance = if (b.league.equals("MLB", ignoreCase = true)) PlacedIndex.SAME_BASEBALL_GAME_MS else PlacedIndex.SAME_GAME_MS
        return index[identity]?.firstOrNull { abs(it.startsTs - b.startsTs) <= tolerance }
    }

    companion object {
        /** A watched bet's price is logged again at least this often while it's listed… */
        const val HEARTBEAT_MS = 5 * 60_000L

        /** …and on a change no more often than this (a price flickering every read is one look a minute). */
        const val MIN_GAP_MS = 60_000L

        /** A change of this much EV (a quarter of a point) or of the fair (a fifth of a point) is a change. */
        const val EV_STEP = 0.0025
        const val FAIR_STEP = 0.002
        const val CHECK_EV_STEP = 0.005

        /** Waiting lines are written together at most this often. */
        const val FLUSH_MS = 10_000L

        /** The most lines held when the disk refuses them. */
        const val MAX_PENDING = 20_000

        /** A list read longer ago than this is a saved one shown before the launch's first read, not a scan. */
        const val MAX_SCAN_AGE_MS = 90_000L

        /** Novig's live price and a game page are used only this fresh. */
        const val LIVE_FRESH_MS = 60_000L
        const val VIEW_FRESH_MS = 5 * 60_000L

        /** A game started this long ago leaves the in-memory index. */
        const val PRUNE_AFTER_MS = 3 * 60 * 60_000L

        /** Days back from today that grading and closes look at (the score feeds give up at 30 days, ParlayAPI's history at 7). */
        const val SETTLE_DAYS = 10L

        /** Passes of the grader and the close backfill in one settle (each takes up to 200 and 120 bets). */
        const val MAX_PASSES = 8

        /** A study bet is one unit. */
        const val STUDY_STAKE = 1.0

        const val ID_LENGTH = 12

        /** Whether a look is worth a line: the first, or a change, or the heartbeat. [prev] was logged at [prevAt]. */
        fun worthLogging(prev: Sight?, prevAt: Long?, cur: Sight, now: Long): Boolean {
            if (prev == null || prevAt == null) return true
            val gap = now - prevAt
            if (gap >= HEARTBEAT_MS) return true
            if (gap < MIN_GAP_MS) return false
            return cur.o != prev.o || cur.b != prev.b || abs((cur.ev ?: 0.0) - (prev.ev ?: 0.0)) >= EV_STEP || abs((cur.f ?: 0.0) - (prev.f ?: 0.0)) >= FAIR_STEP
        }

        /** Whether a started bet still has a result or a close to look for. */
        fun needsWork(b: TrackedBet, now: Long): Boolean {
            val age = now - b.startsTs
            if (age < CloseBackfill.AFTER_START_MS) return false
            val grade = b.status == BetStatus.PENDING && b.settledBy != BetSettler.BY_YOU && age >= BetSettler.AFTER_START_MS && age <= BetSettler.GIVE_UP_MS
            val close = !b.closeFinal && ClosingLine.closeOf(b, now) == null && age <= CloseBackfill.GIVE_UP_MS && b.createdAtMs < b.startsTs
            return grade || close
        }

        /** What Tj's own Tracker bet [own] on the same line already has that [study] still lacks: its result, its close, Novig's close. Null when nothing. */
        fun copyOf(study: TrackedBet, own: TrackedBet, now: Long): StudyResult? {
            val settled = own.status != BetStatus.PENDING && study.status == BetStatus.PENDING
            val close = if (study.closeFair == null && study.closingFair == null) ClosingLine.closeOf(own, now) else null
            val novig = own.novigClose.takeIf { study.novigClose == null }
            if (!settled && close == null && novig == null) return null
            return StudyResult(
                status = own.status.takeIf { settled }, settledAtMs = own.settledAtMs.takeIf { settled }, settledBy = own.settledBy.takeIf { settled },
                settleValue = own.settleValue.takeIf { settled }, gradeNote = own.gradeNote.takeIf { settled }, gradeAtMs = own.gradeAtMs.takeIf { settled },
                gradeManual = false,
                closeFair = close?.first, closeVia = close?.let { "Tracker · " + ClosingLine.sourceLabel(it.second) }, closeFinal = close != null,
                novigClose = novig, novigCloseAtMs = own.novigCloseAtMs.takeIf { novig != null },
                from = "tracker",
            )
        }
    }

    // ---- what Diagnostics says ----------------------------------------------------------------------------------------------

    /** The study at a glance: how many bets are logged, graded and closed in the last [days] days, the journal's size, and the last look. */
    data class Overview(val days: Int, val bets: Int, val graded: Int, val closed: Int, val bytes: Long, val lastLoggedAtMs: Long?, val loggedThisRun: Long)

    suspend fun overview(now: Long = clock(), days: Int = 14): Overview = withContext(io) {
        val today = FreeScores.etDate(now)
        var bets = 0
        var graded = 0
        var closed = 0
        var n = 0
        for (day in journal.days().filter { it >= today.minusDays(days.toLong()) }) {
            n++
            for (sb in journal.fold(day).values) {
                bets++
                if (sb.bet.status != BetStatus.PENDING) graded++
                if (ClosingLine.closeOf(sb.bet, now) != null || sb.bet.closeFair != null) closed++
            }
        }
        Overview(n, bets, graded, closed, journal.bytes(), lastLoggedAtMs, betsLogged)
    }
}
