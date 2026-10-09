package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.SgoConvert
import com.tjshea.vigilant.data.reference.SgoEvent
import com.tjshea.vigilant.data.reference.SgoOdd
import com.tjshea.vigilant.data.reference.SgoParser
import com.tjshea.vigilant.data.reference.SgoProps
import com.tjshea.vigilant.data.reference.SportsGameOddsClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.engine.Devig
import kotlin.math.abs

/**
 * Closing lines from SportsGameOdds Pro (Tj, 2026-10-09: "clv values and closing lines including clv for any non graded bets already in the app, historical odds and clv"; SPORTSGAMEODDS_API.md §3):
 * `/events?includeOpenCloseOdds=true` gives each book's price at the event's start (`closeOdds`, `closeSpread`, `closeOverUnder`), and Pro reaches back to 2020, so a bet placed weeks ago is
 * measured the same as one from tonight. The close is Pinnacle's (Circa's when Pinnacle has none), devigged across the bet's two sides, and only at the exact number the bet was on: a
 * spread or total that closed elsewhere is no close for it. One read covers every bet of a league and window; a read is kept [KEEP_MS] so the 3-hourly look does not ask again.
 */
class SgoCloses(
    private val client: SportsGameOddsClient,
    private val hasKey: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : CloseSource {
    override val id: String get() = ID

    /** Settings' SportsGameOdds Pro switch: off, nothing is asked. */
    @Volatile var enabled: Boolean = false
    override val active: Boolean get() = enabled && hasKey()

    private class Kept(val atMs: Long, val events: List<SgoEvent>)
    private val kept = java.util.concurrent.ConcurrentHashMap<String, Kept>()

    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
        if (!active) return emptyMap()
        val out = HashMap<String, CloseLookup>()
        // A league's window: every bet of that league starting within a day of each other shares one read.
        val groups = bets.groupBy { b -> leagueIdOf(b)?.let { it to (b.startsTs / WINDOW_MS) } }
        for ((key, group) in groups) {
            if (key == null) { group.forEach { out[it.id] = CloseLookup.None("SportsGameOdds has no feed for ${it.league.ifBlank { "this league" }}") }; continue }
            val picks = group.associateWith { BetGrader.pickOf(it) }
            val events = try {
                read(key.first, group, picks.values.filterNotNull())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                group.forEach { out[it.id] = CloseLookup.Later("SportsGameOdds didn't answer") }
                continue
            }
            for (b in group) out[b.id] = lookup(b, picks[b], events)
        }
        return out
    }

    private suspend fun read(leagueId: String, bets: List<TrackedBet>, picks: List<BetGrader.Pick>): List<SgoEvent> {
        val ids = oddIds(picks, SgoConvert.Sport.of(leagueId))
        val from = bets.minOf { it.startsTs } - GAP_MS
        val to = bets.maxOf { it.startsTs } + GAP_MS
        val cacheKey = "$leagueId|$from|$to|${ids.hashCode()}"
        kept[cacheKey]?.takeIf { clock() - it.atMs < KEEP_MS }?.let { return it.events }
        val params = listOf("leagueID" to leagueId, "startsAfter" to from.toString(), "startsBefore" to to.toString(), "includeOpenCloseOdds" to "true", "limit" to SportsGameOddsClient.PAGE.toString(), "oddID" to ids.joinToString(","))
        val events = client.eventsAll(params, maxPages = 4).events
        kept[cacheKey] = Kept(clock(), events)
        return events
    }

    private fun lookup(b: TrackedBet, pick: BetGrader.Pick?, events: List<SgoEvent>): CloseLookup {
        if (pick == null) return CloseLookup.None("Couldn't read this bet's market")
        val m = NovigText.parseMatchup(b.eventName) ?: return CloseLookup.None("Couldn't read the teams")
        if (pick is BetGrader.Pick.FirstSet) return CloseLookup.None("SportsGameOdds keeps full-game and 1st-half lines")
        val game = ParlayCloses.bestGame(
            m.home, m.away, b.startsTs, events.filter { e -> e.startsMs?.let { abs(it - b.startsTs) <= GAP_MS } == true },
            { it.home }, { it.away }, { it.startsMs },
        ) ?: return CloseLookup.None("Not in SportsGameOdds")
        if (!game.started && !game.ended && !game.finalized) return CloseLookup.Later("The game has not started")
        // Pinnacle's close first, Circa's if Pinnacle has none; when neither has one, the reason is Pinnacle's (the book Tj's CLV is measured against).
        var first: CloseLookup? = null
        for (book in BOOKS) {
            val r = closeAt(game, pick, book)
            if (r is CloseLookup.Found) return r
            if (first == null) first = r
        }
        return first ?: CloseLookup.None("No close")
    }

    private fun closeAt(g: SgoEvent, pick: BetGrader.Pick, book: String): CloseLookup {
        fun find(stat: String, entity: String, period: String, bet: String, side: String): SgoOdd? =
            g.odds.firstOrNull { it.statId == stat && it.entityId == entity && it.periodId == period && it.betType == bet && it.sideId == side }
        fun fair(mine: SgoOdd?, other: SgoOdd?, point: (SgoOdd) -> Double?, wantPoint: Double?, what: String): CloseLookup {
            val a = mine?.openClose?.get(book) ?: return CloseLookup.None("SportsGameOdds has no $book $what close")
            val b = other?.openClose?.get(book) ?: return CloseLookup.None("SportsGameOdds has no $book $what close")
            if (wantPoint != null) {
                val closed = a.closePoint ?: return CloseLookup.None("No line on $book's close")
                if (abs(closed - wantPoint) > 1e-6) return CloseLookup.None("${book.replaceFirstChar { it.uppercase() }} closed at ${trim(closed)}, not your ${trim(wantPoint)}")
            }
            val p = implied(a.closeOdds) ?: return CloseLookup.None("No $book close price")
            val q = implied(b.closeOdds) ?: return CloseLookup.None("No $book close price")
            return CloseLookup.Found(Devig.multiplicative(listOf(p, q))[0], "SportsGameOdds · ${book.replaceFirstChar { it.uppercase() }} close")
        }
        return when (pick) {
            is BetGrader.Pick.Moneyline -> {
                val side = TeamMatcher.whichOf(pick.team, g.home, g.away).takeIf { it != 0 } ?: return CloseLookup.None("Couldn't tell which team ${pick.team} is")
                val mine = find("points", sideName(side), "game", "ml", sideName(side))
                val other = find("points", sideName(3 - side), "game", "ml", sideName(3 - side))
                fair(mine, other, { null }, null, "moneyline")
            }
            is BetGrader.Pick.Spread -> {
                val side = TeamMatcher.whichOf(pick.team, g.home, g.away).takeIf { it != 0 } ?: return CloseLookup.None("Couldn't tell which team ${pick.team} is")
                val period = periodOf(pick.period) ?: return CloseLookup.None("SportsGameOdds keeps full-game and 1st-half lines")
                fair(find("points", sideName(side), period, "sp", sideName(side)), find("points", sideName(3 - side), period, "sp", sideName(3 - side)), { null }, pick.line, "spread")
            }
            is BetGrader.Pick.Total -> {
                val period = periodOf(pick.period) ?: return CloseLookup.None("SportsGameOdds keeps full-game and 1st-half lines")
                val over = find("points", "all", period, "ou", "over")
                val under = find("points", "all", period, "ou", "under")
                if (pick.over) fair(over, under, { null }, pick.line, "total") else fair(under, over, { null }, pick.line, "total")
            }
            is BetGrader.Pick.TeamTotal -> {
                val side = TeamMatcher.whichOf(pick.team, g.home, g.away).takeIf { it != 0 } ?: return CloseLookup.None("Couldn't tell which team ${pick.team} is")
                val over = find("points", sideName(side), "game", "ou", "over")
                val under = find("points", sideName(side), "game", "ou", "under")
                if (pick.over) fair(over, under, { null }, pick.line, "team total") else fair(under, over, { null }, pick.line, "team total")
            }
            is BetGrader.Pick.Prop -> {
                val sport = SgoConvert.Sport.of(g.leagueId)
                val statId = SgoProps.statIds(sport).firstOrNull { SgoProps.novigStat(sport, it) == pick.stat } ?: return CloseLookup.None("SportsGameOdds has no ${pick.stat.lowercase().replace('_', ' ')} lines")
                val player = g.players.values.firstOrNull { PlayerNames.same(it.name, pick.player) } ?: return CloseLookup.None("Player not in SportsGameOdds' game")
                val over = find(statId, player.id, "game", "ou", "over")
                val under = find(statId, player.id, "game", "ou", "under")
                if (pick.over) fair(over, under, { null }, pick.line, "prop") else fair(under, over, { null }, pick.line, "prop")
            }
            is BetGrader.Pick.FirstSet -> CloseLookup.None("SportsGameOdds keeps full-game and 1st-half lines")
        }
    }

    companion object {
        const val ID = "sgo"

        /** Pinnacle's close first, then Circa's: the two sharpest. */
        val BOOKS = listOf("pinnacle", "circa")

        /** Bets of one league starting within a window this wide share a read. */
        const val WINDOW_MS = 24 * 3_600_000L

        /** A close's game must start within this of the bet's. */
        const val GAP_MS = 3 * 3_600_000L

        /** A read is kept this long (the closes are fixed once a game started). */
        const val KEEP_MS = 30 * 60_000L

        private fun sideName(side: Int) = if (side == 1) "home" else "away"
        private fun periodOf(p: BetGrader.Period): String? = when (p) {
            BetGrader.Period.GAME -> "game"
            BetGrader.Period.FIRST_HALF -> "1h"
            else -> null
        }
        private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

        private fun implied(american: Double?): Double? = SgoParser.decimal(american)?.let { 1.0 / it }

        fun leagueIdOf(b: TrackedBet): String? {
            val league = Leagues.ALL.firstOrNull { it.novigName.equals(b.league, true) || it.displayName.equals(b.league, true) } ?: return null
            return com.tjshea.vigilant.data.reference.SgoBooks.leagueId(league)
        }

        /** The oddIDs a read of these [picks] needs: only the markets the bets are on. */
        fun oddIds(picks: List<BetGrader.Pick>, sport: SgoConvert.Sport): List<String> {
            val ids = LinkedHashSet<String>()
            for (p in picks) when (p) {
                is BetGrader.Pick.Moneyline -> { ids += "points-home-game-ml-home"; ids += "points-away-game-ml-away" }
                is BetGrader.Pick.Spread -> { val per = if (p.period == BetGrader.Period.FIRST_HALF) "1h" else "game"; ids += "points-home-$per-sp-home"; ids += "points-away-$per-sp-away" }
                is BetGrader.Pick.Total -> { val per = if (p.period == BetGrader.Period.FIRST_HALF) "1h" else "game"; ids += "points-all-$per-ou-over"; ids += "points-all-$per-ou-under" }
                is BetGrader.Pick.TeamTotal -> for (t in listOf("home", "away")) { ids += "points-$t-game-ou-over"; ids += "points-$t-game-ou-under" }
                is BetGrader.Pick.Prop -> SgoProps.statIds(sport).firstOrNull { SgoProps.novigStat(sport, it) == p.stat }?.let { s ->
                    ids += "$s-PLAYER_ID-game-ou-over"; ids += "$s-PLAYER_ID-game-ou-under"
                }
                is BetGrader.Pick.FirstSet -> {}
            }
            return ids.ifEmpty { setOf("points-home-game-ml-home") }.toList()
        }
    }
}
