package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import kotlin.math.abs

/**
 * Every bet Tj already has, from any scanner, as one index the whole app hides by (Tj, 2026-09-27:
 * "hides bets I already placed throughout the whole app regardless of scanner"). A bet counts as
 * his once it's marked ✓ placed or ✕ removed in the widget or the CNO tab ([PlacedBet]), or tracked
 * from a bet card ([TrackedBet]). A listed bet is the same one when any of these agree:
 *
 *  - its list key (`cno:<row>` or `<market>/<outcome>`), or a mark's alias for it;
 *  - Novig's outcome id (Vigilant's bets carry it; CNO's once its Novig link is known);
 *  - the same game, market, side and line, read with [BetGrader]'s rules, starting within
 *    [SAME_GAME_MS] of each other ([SAME_BASEBALL_GAME_MS] in baseball, where the feeds agree to the
 *    minute and a doubleheader's second game is a different bet), so yesterday's game in the same
 *    series doesn't count. With a start time missing on either side, only a mark from the last
 *    [UNKNOWN_START_MS] counts.
 */
class PlacedIndex private constructor(
    private val keys: Set<String>,
    private val outcomes: Set<String>,
    private val identities: Map<String, List<Mark>>,
    private val now: Long,
) {
    /** One placed or tracked bet's game, for the same-bet check. */
    private class Mark(val startsTs: Long?, val placedAtMs: Long, val league: String)

    /** The games in [identities]: a listed bet's wording is read only when its game is one of these. */
    private val games: Set<String> = identities.keys.mapTo(HashSet()) { it.substringBefore('|') }

    val isEmpty: Boolean get() = keys.isEmpty() && outcomes.isEmpty() && identities.isEmpty()

    /** Whether a listed bet is one Tj already has. */
    fun has(
        key: String? = null,
        aliases: Collection<String> = emptyList(),
        outcomeId: String? = null,
        event: String = "",
        market: String = "",
        selection: String = "",
        startsTs: Long? = null,
        league: String = "",
    ): Boolean {
        if (isEmpty) return false
        if (key != null && key in keys) return true
        if (aliases.any { it in keys }) return true
        if (!outcomeId.isNullOrEmpty() && outcomeId in outcomes) return true
        val game = gameKey(event) ?: return false
        if (game !in games) return false
        val id = pickKey(market, selection)?.let { "$game|$it" } ?: return false
        val marks = identities[id] ?: return false
        return marks.any { m ->
            val s = m.startsTs
            when {
                s == null || startsTs == null -> now - m.placedAtMs <= UNKNOWN_START_MS
                isBaseball(league) || isBaseball(m.league) -> abs(s - startsTs) <= SAME_BASEBALL_GAME_MS
                else -> abs(s - startsTs) <= SAME_GAME_MS
            }
        }
    }

    /** Whether Vigilant's own [o] is a bet Tj already has. */
    fun has(o: com.tjshea.vigilant.data.scanner.Opportunity): Boolean = has(
        key = o.key, outcomeId = o.outcome.outcomeId, event = o.event.description, market = o.marketLabel,
        selection = o.selection, startsTs = o.event.startsTs, league = o.event.league,
    )

    /** [list] without the bets Tj already has. */
    fun visible(list: List<com.tjshea.vigilant.data.scanner.Opportunity>): List<com.tjshea.vigilant.data.scanner.Opportunity> =
        if (isEmpty) list else list.filterNot(::has)

    companion object {
        /**
         * Two listings of one bet start within this of each other: football feeds can disagree on a
         * kickoff by hours (the matcher allows 36), but not by a day, which is the next game.
         */
        const val SAME_GAME_MS = 12 * 60 * 60_000L

        /** Baseball: the feeds agree to the minute, and a doubleheader's second game starts 3+ hours after the first. */
        const val SAME_BASEBALL_GAME_MS = 2 * 60 * 60_000L

        /** A mark with no start time to compare stands for the same bet only this long after it was placed. */
        const val UNKNOWN_START_MS = 24 * 60 * 60_000L

        internal fun isBaseball(league: String) = league.equals("MLB", ignoreCase = true)

        /** Tracked bets on games that started longer ago than this can't be on any list. */
        private const val TRACKED_WINDOW_MS = 36 * 60 * 60_000L

        val EMPTY = PlacedIndex(emptySet(), emptySet(), emptyMap(), 0L)

        fun of(placed: List<PlacedBet>, tracked: List<TrackedBet>, now: Long): PlacedIndex {
            val keys = HashSet<String>()
            val outcomes = HashSet<String>()
            val identities = HashMap<String, MutableList<Mark>>()
            fun add(id: String?, mark: Mark) {
                if (id != null) identities.getOrPut(id) { ArrayList() } += mark
            }
            for (p in placed) {
                keys += p.keys
                p.outcomeId?.takeIf { it.isNotEmpty() }?.let { outcomes += it }
                val (market, event) = if (p.event.isNotEmpty()) p.market to p.event else fromDetail(p.detail)
                add(identity(event, market, p.title), Mark(p.startsAtMs, p.placedAtMs, p.league))
            }
            for (b in tracked) {
                if (now - b.startsTs > TRACKED_WINDOW_MS) continue
                b.placedKey?.let { keys += it }
                if (b.marketId.isNotEmpty() && b.outcomeId.isNotEmpty()) keys += "${b.marketId}/${b.outcomeId}"
                b.outcomeId.takeIf { it.isNotEmpty() }?.let { outcomes += it }
                add(identity(b.eventName, b.marketLabel, b.selection), Mark(b.startsTs, b.createdAtMs, b.league))
            }
            return if (keys.isEmpty() && outcomes.isEmpty() && identities.isEmpty()) EMPTY else PlacedIndex(keys, outcomes, identities, now)
        }

        /** A mark saved before v0.16.2 kept "Player Receptions · Houston Texans @ Indianapolis Colts[ · Book]". */
        private fun fromDetail(detail: String): Pair<String, String> {
            val parts = detail.split(" · ")
            return parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
        }

        /**
         * One bet as a comparable string: the game's two teams, then what [BetGrader] reads the
         * market and selection as ("prop|lamar jackson|PASSING_YARDS|o|225.5"). Null when either
         * can't be read for certain (such a bet is matched by key or outcome only).
         */
        fun identity(event: String, market: String, selection: String): String? {
            val game = gameKey(event) ?: return null
            return pickKey(market, selection)?.let { "$game|$it" }
        }

        /**
         * "baltimore ravens@dallas cowboys" (never contains '|'). Each matchup is read once ([gameKeys]): the same listing is asked about on every
         * screen update, by every listed bet (Tj's Diagnostics 2026-10-03, v0.56.1: the main thread was in here when Android ended the app).
         */
        internal fun gameKey(event: String): String? = kept(gameKeys, event) {
            NovigText.parseMatchup(event)?.let { "${team(it.away)}@${team(it.home)}" }
        }

        private fun pickKey(market: String, selection: String): String? = kept(pickKeys, market + '\u0000' + selection) { readPickKey(market, selection) }

        /** [compute]'s answer for [key], kept (an unreadable one too, as ""); the map starts over past [KEEP_LIMIT] entries (a day's listings are far fewer). */
        private inline fun kept(map: java.util.concurrent.ConcurrentHashMap<String, String>, key: String, compute: () -> String?): String? {
            map[key]?.let { return it.ifEmpty { null } }
            if (map.size > KEEP_LIMIT) map.clear()
            return compute().also { map[key] = it ?: "" }
        }

        private val gameKeys = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val pickKeys = java.util.concurrent.ConcurrentHashMap<String, String>()
        private const val KEEP_LIMIT = 20_000

        private fun readPickKey(market: String, selection: String): String? {
            val pick = BetGrader.pickOf(market, selection) ?: return null
            fun ou(over: Boolean) = if (over) "o" else "u"
            return when (pick) {
                is BetGrader.Pick.Moneyline -> "ml|${team(pick.team)}"
                is BetGrader.Pick.Spread -> "sp|${pick.period}|${team(pick.team)}|${pick.line}"
                is BetGrader.Pick.Total -> "tot|${pick.period}|${ou(pick.over)}|${pick.line}"
                is BetGrader.Pick.TeamTotal -> "tt|${team(pick.team)}|${ou(pick.over)}|${pick.line}"
                is BetGrader.Pick.Prop -> "prop|${PlayerNames.key(pick.player)}|${pick.stat}|${ou(pick.over)}|${pick.line}"
                is BetGrader.Pick.FirstSet -> "fs|${team(pick.player)}"
            }
        }

        private fun team(name: String) = TeamMatcher.tokens(name).joinToString(" ")
    }
}
