package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.live.Fetched
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.burst.Ladders
import com.tjshea.vigilant.data.pinnodds.DayJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the lab recorder is doing, for Settings and Diagnostics. */
data class LabStatus(
    val running: Boolean = false,
    val sinceMs: Long? = null,
    val leagues: Set<String> = emptySet(),
    val games: Int = 0,
    val cycles: Long = 0L,
    val ladders: String? = null,
    val tail: Int = 0,
    val alt: Int = 0,
    val covers: Int = 0,
    val withState: Int = 0,
    val problem: String? = null,
    val lastCycleMs: Long? = null,
)

/**
 * The paper-only lab (Tj, 2026-10-09: "Build all three. This app is in 100% research and development"; RESEARCH.md §120.6). While a game of the picked leagues is live on Novig it reads the totals, spread
 * and moneyline books of up to [MAX_GAMES] games every [cycleMs], and from each snapshot: (1) [LadderScan] looks for covers and says how thin the ladder is, (2) [TailScan] looks for far strikes the game
 * has all but decided (the game state from ESPN's scoreboard, [LabClock]), (3) [AltLineScan] lays outside books' alternate-line prices ([altQuotes]) against Novig's alternate strikes. Every would-be bet is a [LabRecord]
 * in the journal, and once the game is settled [grade] reads Novig's own market for the result. **It places no order and is given no way to**: [NovigSource] is read-only here, there is no trading client,
 * and nothing outside the journal changes.
 */
class LabRecorder(
    private val scope: CoroutineScope,
    private val source: NovigSource,
    private val fetch: suspend (url: String) -> Fetched?,
    private val altQuotes: suspend (NovigEvent) -> List<AltQuote>,
    private val journal: DayJournal<LabRecord>,
    private val gradeJournal: DayJournal<LabGrade>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cycleMs: Long = CYCLE_MS,
    private val gradeEvery: Int = GRADE_EVERY,
) {
    private val _status = MutableStateFlow(LabStatus())
    val status: StateFlow<LabStatus> = _status.asStateFlow()

    @Volatile private var job: Job? = null
    val running: Boolean get() = job?.isActive == true

    /** When each (kind, outcome) was last recorded, and at what ask: the same would-be bet is recorded again only after [REPEAT_MS] or a better price. */
    private val lastSeen = HashMap<String, Pair<Long, Double>>()
    private val espnCache = HashMap<String, Pair<Long, List<EspnGame>>>()
    private var cycle = 0

    @Synchronized
    fun start(leagues: Set<String>) {
        if (running) return
        _status.value = LabStatus(running = true, sinceMs = clock(), leagues = leagues)
        job = scope.launch {
            while (isActive) {
                try {
                    cycleOnce(leagues)
                    _status.value = _status.value.copy(problem = null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _status.value = _status.value.copy(problem = e.message ?: e.javaClass.simpleName)
                }
                delay(cycleMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _status.value = _status.value.copy(running = false)
    }

    /** One pass: read the live ladders, scan, record. Public so a test (or a "scan now") can drive it without the loop. */
    suspend fun cycleOnce(leagues: Set<String>) {
        val now = clock()
        val live = source.events(leagues, listOf(NovigEvent.STATUS_LIVE, NovigEvent.STATUS_DELAYED)).sortedBy { it.startsTs }.take(MAX_GAMES)
        if (live.isEmpty()) {
            _status.value = _status.value.copy(games = 0, cycles = _status.value.cycles + 1, lastCycleMs = now)
            return
        }
        val ids = live.map { it.eventId }.toSet()
        val markets = source.markets(leagues, Ladders.TYPES, listOf(NovigEvent.STATUS_LIVE, NovigEvent.STATUS_DELAYED))
            .filter { it.eventId in ids && it.isOpen }
        val books = source.books(markets.map { it.marketId }).books
        val reports = ArrayList<LadderReport>()
        var tail = 0
        var alt = 0
        var covers = 0
        var withState = 0
        for (ev in live) {
            val points = markets.filter { it.eventId == ev.eventId }.mapNotNull { m -> Ladders.line(m)?.let { LadderPoint(it, books[m.marketId]) } }
            if (points.isEmpty()) continue
            val rs = LadderScan.scan(points, live = true)
            reports += rs
            for (c in rs.flatMap { it.covers }) {
                covers++
                record(LabRecord(id(), now, LabKind.COVER, ev.eventId, ev.description, ev.league, c.lo.marketId, c.lo.yesOutcomeId, "${c.lo.label} YES + ${c.hi.label} NOT", "COVER", c.lo.threshold, c.cost, 1.0, c.net, c.contracts, 0, "hi=${c.hi.marketId}"))
            }
            val game = state(ev, now)
            val st = game?.let { g -> TailSport.let { LabClock.sportOf(ev.league) }?.let { sport -> LabClock.fractionLeft(ev.league, g.period, g.clockSec)?.let { GameState(sport, g.homeScore, g.awayScore, it) } } }
            if (st != null) {
                withState++
                for (c in TailScan.scan(points, st, { ref -> game.marginOf(ref) })) {
                    tail++
                    record(LabRecord(id(), now, LabKind.TAIL, ev.eventId, ev.description, ev.league, c.marketId, c.outcomeId, c.label, c.side, c.strike, c.ask, c.fair, c.edge, c.contracts, 0,
                        "score ${st.homeScore}-${st.awayScore}, ${"%.0f".format(java.util.Locale.US, st.fractionLeft * 100)}% left, centre ${"%.1f".format(java.util.Locale.US, c.centre)}"))
                }
            }
            val quotes = runCatching { altQuotes(ev) }.getOrDefault(emptyList())
            if (quotes.isNotEmpty()) for (c in AltLineScan.scan(points, quotes, now, ev.startsTs, live = true)) {
                alt++
                record(LabRecord(id(), now, LabKind.ALT, ev.eventId, ev.description, ev.league, c.marketId, c.outcomeId, c.label, c.side, c.strike, c.ask, c.fair, c.edge, c.contracts, c.books, "oldest quote ${c.oldestAgeSec}s"))
            }
        }
        cycle++
        val s = _status.value
        _status.value = s.copy(games = live.size, cycles = s.cycles + 1, ladders = LadderScan.summary(reports), withState = withState, tail = s.tail + tail, alt = s.alt + alt, covers = s.covers + covers, lastCycleMs = now)
        if (cycle % gradeEvery == 0) grade(now)
    }

    private var seq = 0L
    private fun id(): String = "${clock().toString(36)}-${(seq++).toString(36)}"

    /** Writes [r] unless the same would-be bet was written within [REPEAT_MS] at a price no better than a cent below. */
    private fun record(r: LabRecord) {
        val key = r.kind + "|" + r.outcomeId + "|" + r.note.substringBefore(",").takeIf { r.kind == LabKind.COVER }.orEmpty()
        val prev = lastSeen[key]
        if (prev != null && r.atMs - prev.first < REPEAT_MS && r.ask > prev.second - 0.01) return
        lastSeen[key] = r.atMs to r.ask
        if (lastSeen.size > 4_000) lastSeen.entries.removeAll { r.atMs - it.value.first > REPEAT_MS }
        journal.append(r)
    }

    /** ESPN's scoreboard for [ev]'s league (cached 15 s), then the game whose two teams match [ev]'s description. */
    private suspend fun state(ev: NovigEvent, now: Long): EspnGame? {
        val path = LabClock.ESPN_PATHS[ev.league] ?: return null
        val cached = espnCache[ev.league]?.takeIf { now - it.first < ESPN_TTL_MS }?.second
        val games = cached ?: (fetch("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard")?.let { LabClock.parseEspn(it.body) } ?: return null).also { espnCache[ev.league] = now to it }
        val m = ev.matchup ?: return null
        return games.filter { it.live }.firstOrNull { TeamMatcher.similarity(it.home, m.home) >= 0.5 && TeamMatcher.similarity(it.away, m.away) >= 0.5 }
    }

    /**
     * Reads the settled result of every would-be bet older than [GRADE_AFTER_MS] that has none yet (at most [GRADE_BATCH] markets a pass), from Novig's own market: an outcome that is no longer TBD
     * is WIN, LOSS, PUSH or a decimal payout. Covers are not graded (they pay whatever happens).
     */
    suspend fun grade(now: Long) {
        val graded = gradeJournal.readAll().map { it.id }.toHashSet()
        val todo = journal.readAll().filter { it.kind != LabKind.COVER && it.id !in graded && now - it.atMs > GRADE_AFTER_MS }
        for ((marketId, rs) in todo.groupBy { it.marketId }.entries.take(GRADE_BATCH)) {
            val m: NovigMarket = source.market(marketId) ?: continue
            for (r in rs) {
                val status = m.outcomes.firstOrNull { it.outcomeId == r.outcomeId }?.status?.trim()
                if (status.isNullOrEmpty() || status.equals("TBD", true)) continue
                gradeJournal.append(LabGrade(r.id, now, status))
            }
        }
    }

    /** Everything recorded and graded so far, for Diagnostics. */
    fun records(): List<LabRecord> = journal.readAll()
    fun grades(): List<LabGrade> = gradeJournal.readAll()

    companion object {
        const val MAX_GAMES = 4
        const val CYCLE_MS = 20_000L
        const val GRADE_EVERY = 15
        const val GRADE_AFTER_MS = 30 * 60_000L
        const val GRADE_BATCH = 20
        const val REPEAT_MS = 5 * 60_000L
        const val ESPN_TTL_MS = 15_000L
    }
}
