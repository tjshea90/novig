package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import kotlin.math.abs

/**
 * ESPN's schedule game <-> Novig's event (NC1). ESPN names a team "Texas A&M-Commerce Lions" (school "Texas A&M-Commerce"), Novig "Texas A&M Commerce": the same word-by-word match the scanner
 * uses for every other feed ([TeamMatcher.gameScore]), tried on ESPN's school name and its full name, in either home/away order (a neutral-site game is listed with the teams swapped by some feeds),
 * and a start within [START_SLACK_MS]. Each event is taken by at most one game, the best fit first, so two similar names ("Miami" / "Miami (OH)") never share one.
 */
object NcaabMatch {
    /** ESPN and Novig list the same tip-off within minutes; a delay is moved by either. */
    const val START_SLACK_MS = 3 * 3_600_000L

    data class Found(val event: NovigEvent, val score: Double, val swapped: Boolean)

    fun score(game: SlateGame, event: NovigEvent): Found? {
        val m = event.matchup ?: return null
        if (abs(game.t - event.startsTs) > START_SLACK_MS) return null
        val straight = maxOf(
            TeamMatcher.gameScore(game.h.n, game.a.n, m.home, m.away),
            TeamMatcher.gameScore(game.h.dn, game.a.dn, m.home, m.away),
        )
        val swapped = maxOf(
            TeamMatcher.gameScore(game.h.n, game.a.n, m.away, m.home),
            TeamMatcher.gameScore(game.h.dn, game.a.dn, m.away, m.home),
        )
        // The start time breaks a tie between two fits (a doubleheader of the same teams is not played in college basketball, but a rematch a day apart is).
        val timeBonus = 0.2 * (1.0 - abs(game.t - event.startsTs).toDouble() / START_SLACK_MS)
        return when {
            straight <= 0.0 && swapped <= 0.0 -> null
            straight >= swapped -> Found(event, straight + timeBonus, false)
            else -> Found(event, swapped + timeBonus, true)
        }
    }

    /** Game id -> its Novig event, one event per game and one game per event. */
    fun match(games: List<SlateGame>, events: List<NovigEvent>): Map<String, Found> {
        val candidates = ArrayList<Pair<SlateGame, Found>>()
        for (g in games) for (e in events) score(g, e)?.let { candidates += g to it }
        val used = HashSet<String>()
        val out = LinkedHashMap<String, Found>()
        for ((g, f) in candidates.sortedByDescending { it.second.score }) {
            if (g.id in out || f.event.eventId in used) continue
            out[g.id] = f
            used += f.event.eventId
        }
        return out
    }
}
