package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.scanner.BetKind
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/**
 * Which games the CNO scanner looks at (Tj, 2026-10-07: "for the cno only scanner, right now I can't filter sports leagues at all. Make sure the cno scanner has plenty of filters
 * just like vigilant scanner"; RESEARCH.md §85). Part of [CnoFilters], so everything that already moves the filters (the feed's "read again now", the saved list, the scan study,
 * the widget, alerts and the auto-bet, which all start from the screened list) carries it. **Every field's default is "everything"**: a fresh install and every saved settings file
 * behave exactly as before. Presets neither carry nor reset it (it is WHAT Tj looks at, not a quality rule).
 *
 * - [leagues]: CNO's league LABELS ("NHL", "MLS (USA)"); empty = every league. Labels, not ids, so a renumbered dropdown cannot break a saved pick.
 * - [kinds]: kinds of bet ([BetKind]) kept; empty = every kind.
 * - [hideLive]: leave out games already under way (CNO's live rows carry Novig's taker fee its EV doesn't; the auto-bet never bets them anyway).
 * - [minLiquidity]: fewest dollars available at Novig's price (0 = none): a thin bet cannot take a stake.
 * - [include] / [exclude]: words (teams, players, markets), comma separated: keep only bets with ANY of the first, drop bets with ANY of the second; case and accents ignored.
 * - [propsPerGame]: at most this many player props from one game (best edge first; 0 = no cap).
 */
@Serializable
data class CnoScope(
    val leagues: Set<String> = emptySet(),
    val kinds: Set<BetKind> = emptySet(),
    val hideLive: Boolean = false,
    val minLiquidity: Int = 0,
    val include: String = "",
    val exclude: String = "",
    val propsPerGame: Int = 0,
) {
    val isDefault: Boolean get() = this == CnoScope()

    /** Whether a row in [rowLeague] passes the league pick. */
    fun allows(rowLeague: String): Boolean = leagues.isEmpty() || leagues.any { CnoLeagues.same(it, rowLeague) }

    /** The leagues picked as the chips hold them: a label CNO lists keeps its table spelling; one it doesn't (a renamed league) is kept as saved, so it can still be removed. */
    fun pickedLabels(): List<String> = leagues.map { CnoLeagues.byLabel(it)?.label ?: it }

    /** [label] picked or un-picked; a pick that becomes every league collapses to "all" (empty), and un-ticking the last one returns to "all": there is no "none" here. */
    fun toggleLeague(label: String): CnoScope {
        val key = CnoLeagues.byLabel(label)?.label ?: label.trim()
        val next = if (leagues.any { CnoLeagues.same(it, key) }) leagues.filterNot { CnoLeagues.same(it, key) }.toSet() else leagues + key
        return copy(leagues = normalised(next))
    }

    /** Tapping a sport's heading: all of its leagues picked, or (when they all are) cleared. */
    fun toggleSport(sport: CnoLeagues.Sport): CnoScope {
        val of = CnoLeagues.of(sport).map { it.label }
        val all = of.all { l -> leagues.any { CnoLeagues.same(it, l) } }
        val next = if (all) leagues.filterNot { p -> of.any { CnoLeagues.same(it, p) } }.toSet() else leagues + of
        return copy(leagues = normalised(next))
    }

    private fun normalised(set: Set<String>): Set<String> =
        if (set.isEmpty() || CnoLeagues.ALL.all { l -> set.any { CnoLeagues.same(it, l.label) } } && set.size == CnoLeagues.ALL.size) emptySet() else set

    fun toggleKind(kind: BetKind): CnoScope {
        val next = if (kind in kinds) kinds - kind else kinds + kind
        return copy(kinds = if (next.size == BetKind.entries.size) emptySet() else next)
    }

    /** Whether a bet of [kind] passes the kinds picked. */
    fun allowsKind(kind: BetKind): Boolean = kinds.isEmpty() || kind in kinds

    /** Whether any filter here can only be applied by the app, to the rows CNO sent (so CNO's own row limit may have cut the list before it ran). */
    val appOnly: Boolean get() = kinds.isNotEmpty() || hideLive || include.isNotBlank() || exclude.isNotBlank() || propsPerGame > 0

    /** The words in [include] or [exclude] as terms: split on commas, lower-cased, accents folded. */
    fun terms(text: String): List<String> = text.split(',').map { fold(it) }.filter { it.isNotEmpty() }

    /** The scope in words, for Settings, Diagnostics and the tab; empty for the default (nothing to say). */
    fun summary(): String = buildList {
        if (leagues.isNotEmpty()) add(pickedLabels().joinToString(", "))
        if (kinds.isNotEmpty()) add(kinds.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase(Locale.US) })
        if (hideLive) add("pregame only")
        if (minLiquidity > 0) add("at least \$$minLiquidity available")
        if (include.isNotBlank()) add("only \"${include.trim()}\"")
        if (exclude.isNotBlank()) add("without \"${exclude.trim()}\"")
        if (propsPerGame > 0) add("at most $propsPerGame props a game")
    }.joinToString(" · ")

    /**
     * What to ask CNO for under this scope (pure). [ids] resolves a league label to the id the page's League dropdown takes (the page's own options first, the built-in table when it
     * gave none; null = not a rendered option, so it cannot be posted); [sportIds] the same for a sport label. [rows] is the list's row limit.
     *  - nothing picked: nothing posted;
     *  - one league: its id (the form is AND-ed on nothing else), exact;
     *  - every league of one sport: the sport's id, exact; some of one sport's leagues: the sport's id and the app does the rest;
     *  - leagues of several sports, or a label the page doesn't list: nothing posted, the app filters, and the row limit is widened.
     * [appOnly] filters also widen it ([WIDE_ROWS]): CNO cuts at its limit, best edge first, BEFORE the app drops anything.
     */
    fun plan(rows: Int, ids: (String) -> Int?, sportIds: (CnoLeagues.Sport) -> Int?): Plan {
        val picked = pickedLabels()
        var leagueId: Int? = null
        var sportId: Int? = null
        var exact = true
        if (picked.isNotEmpty()) {
            val known = picked.map { CnoLeagues.byLabel(it) }
            if (known.any { it == null }) exact = false
            else if (picked.size == 1) {
                leagueId = ids(picked.single())
                if (leagueId == null) exact = false
            } else {
                val sports = known.map { it!!.sport }.toSet()
                if (sports.size == 1) {
                    sportId = sportIds(sports.single())
                    exact = sportId != null && picked.size == CnoLeagues.of(sports.single()).size
                } else exact = false
            }
        }
        val appFilters = appOnly || (picked.isNotEmpty() && !exact)
        val ask = if (appFilters) maxOf(rows, WIDE_ROWS) else rows
        return Plan(leagueId, sportId, ask, appFilters)
    }

    /** What [plan] decided: the ids to post (null = leave the form as it was), how many rows to ask for, and whether the app still has filtering to do. */
    data class Plan(val leagueId: Int?, val sportId: Int?, val askRows: Int, val appFilters: Boolean)

    companion object {
        /** The row limit asked when the app has to filter what CNO sent (the wide read steps 1000, 500, 200; the list asks for less: a reply is about 75 KB a hundred rows). */
        const val WIDE_ROWS = 300

        /** Lower-case with accents removed: "José Ramírez" is "jose ramirez". */
        fun fold(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.US)
    }
}
