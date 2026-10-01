package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.match.PlayerNames
import kotlin.math.abs

/**
 * ParlayAPI's tennis answer (`tennis_atp`, `tennis_wta`) made into one event per singles match with every line in its own unit (Tj,
 * 2026-10-01: "Build tennis through parlayapi"; PARLAY_API.md §6.11, read live that day):
 *
 * - **Pinnacle's match event holds SET lines** (±1.5 sets, 2.5 sets); its games lines come as a second event named "<Player> (Games)".
 *   Every other book (bet365, Caesars, DraftKings, BetMGM, ProphetX) puts **games** lines in the match event, BetMGM and DraftKings at
 *   ±1.5 games too, so a line's size alone can't say which it is. A sets spread pricing Novig's games spread would be a fake edge: set lines
 *   go to [RefBookMarket.PERIOD_SETS] (Novig's SET_SPREAD and TOTAL_SETS), games lines stay the full match (Novig's SPREAD and TOTAL).
 * - **One match is often several events** (28 of 157 that day, no shared id): the "(Games)" twin, and FanDuel or ProphetX listing it at a
 *   start time of their own up to 3 hours off. Merged by the two players, or the planner (one event per feed) would keep only one of them.
 * - **Doubles** ride in the same answer ("Evan King / Brandon Nakashima"): dropped, a pair can name a singles player.
 */
object ParlayTennis {

    /** What ParlayAPI adds to both names of the event holding Pinnacle's games lines. */
    const val GAMES_SUFFIX = " (Games)"

    /** Books whose match event lists set lines when no total says otherwise (verified for Pinnacle only, every one of 128 lines). */
    private val SET_LINE_BOOKS = setOf("pinnacle")

    /** A best-of-5 match has at most 5 sets: a total below this is sets, at or above it games (the fewest games a match can have is 12). */
    private const val SETS_TOTAL_BELOW = 6.0

    /** The widest a sets spread can be (best of 5). */
    private const val MAX_SETS_SPREAD = 2.5

    /** [events] with doubles dropped, each line put in its unit, and every listing of one match merged into one event ([maxGapMs] apart at most). */
    fun normalize(events: List<RefEvent>, maxGapMs: Long): List<RefEvent> =
        merge(events.filterNot(::doubles).map(::unitsOf), maxGapMs)

    private fun doubles(e: RefEvent): Boolean = '/' in e.home || '/' in e.away

    fun isGamesEvent(e: RefEvent): Boolean = e.home.endsWith(GAMES_SUFFIX) || e.away.endsWith(GAMES_SUFFIX)

    private fun player(name: String): String = name.removeSuffix(GAMES_SUFFIX).trim()

    /** One event with its names cleaned and each spread and total in sets ([RefBookMarket.PERIOD_SETS]) or games (period 0); a line that can't be told is dropped. */
    internal fun unitsOf(e: RefEvent): RefEvent {
        val games = isGamesEvent(e)
        val markets = e.markets.groupBy { it.bookKey }.flatMap { (book, list) ->
            val sets = !games && setsIn(book, list)
            list.mapNotNull { m ->
                when (m.kind) {
                    // A "(Games)" event's own winner would be a games-won race, not the match: no book sends one, none is kept.
                    LineKind.MONEYLINE -> m.takeIf { !games && m.period == 0 }
                    LineKind.SPREAD, LineKind.TOTAL -> if (m.period != 0) null else if (sets) asSets(m) else asGames(m)
                    else -> null
                }
            }
        }
        // Pinnacle's alternates repeat its main set line: one line once per book.
        return e.copy(home = player(e.home), away = player(e.away), markets = markets.distinctBy(::identity))
    }

    /** Whether [book]'s spreads and totals in a match event are sets: its total says so when it has one, else the book's known habit. */
    private fun setsIn(book: String, list: List<RefBookMarket>): Boolean {
        val totals = list.filter { it.kind == LineKind.TOTAL && it.period == 0 }.mapNotNull { it.line }
        if (totals.isNotEmpty()) return totals.all { it < SETS_TOTAL_BELOW }
        return book in SET_LINE_BOOKS
    }

    private fun asSets(m: RefBookMarket): RefBookMarket? {
        val line = m.line ?: return null
        val fits = when (m.kind) {
            LineKind.SPREAD -> abs(line) <= MAX_SETS_SPREAD
            else -> line < SETS_TOTAL_BELOW
        }
        return if (fits) m.copy(period = RefBookMarket.PERIOD_SETS) else null
    }

    private fun asGames(m: RefBookMarket): RefBookMarket? {
        val line = m.line ?: return null
        return if (m.kind == LineKind.TOTAL && line < SETS_TOTAL_BELOW) null else m
    }

    /**
     * Every listing of one match as one event: the same two players (either way round) starting within [maxGapMs]. Pinnacle's match
     * event leads (its id, start and orientation), then the listing with the most lines; the others' lines are turned to match it.
     */
    private fun merge(events: List<RefEvent>, maxGapMs: Long): List<RefEvent> {
        val order = events.sortedWith(
            compareByDescending<RefEvent> { e -> !isGamesEvent(e) && e.markets.any { it.bookKey == "pinnacle" } }
                .thenByDescending { it.markets.size },
        )
        val merged = ArrayList<RefEvent>()
        for (e in order) {
            val at = merged.indexOfFirst { m -> abs(m.commenceMs - e.commenceMs) <= maxGapMs && (same(m, e) || swapped(m, e)) }
            if (at < 0) {
                merged += e
                continue
            }
            val base = merged[at]
            val lines = if (same(base, e)) e.markets else e.markets.map { it.flipped() }
            val seen = base.markets.mapTo(HashSet(), ::identity)
            merged[at] = base.copy(markets = base.markets + lines.filter { seen.add(identity(it)) })
        }
        // Back in the answer's order, so a snapshot reads the same way every time.
        val rank = events.withIndex().associate { (i, e) -> e.id to i }
        return merged.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }

    private fun same(a: RefEvent, b: RefEvent) = PlayerNames.same(a.home, b.home) && PlayerNames.same(a.away, b.away)
    private fun swapped(a: RefEvent, b: RefEvent) = PlayerNames.same(a.home, b.away) && PlayerNames.same(a.away, b.home)

    private fun identity(m: RefBookMarket) = listOf(m.bookKey, m.kind, m.period, m.subject, m.line)
}
