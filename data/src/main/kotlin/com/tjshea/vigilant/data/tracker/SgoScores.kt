package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.reference.SgoBooks
import com.tjshea.vigilant.data.reference.SgoConvert
import com.tjshea.vigilant.data.reference.SgoEvent
import com.tjshea.vigilant.data.reference.SgoProps
import com.tjshea.vigilant.data.reference.SportsGameOddsClient
import com.tjshea.vigilant.data.scanner.Leagues
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Final scores for grading from SportsGameOdds Pro (Tj, 2026-10-09: "grading bets as win and loss"; SPORTSGAMEODDS_API.md §3): `teams.*.score` (overtime included, as Novig settles), the
 * score by period from `results`, and, for a prop, the player's stat from `results.game.<playerID>` (`expandResults`). A game is final only when SGO says `finalized`; a cancelled one is
 * [GameScore.called]. A read is kept [KEEP_MS], so a pass reads a league's day once.
 */
class SgoScores(
    private val client: SportsGameOddsClient,
    private val hasKey: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScoreSource {
    @Volatile var enabled: Boolean = false

    private class Kept(val atMs: Long, val events: List<SgoEvent>)
    private val days = java.util.concurrent.ConcurrentHashMap<String, Kept>()
    private val boxes = java.util.concurrent.ConcurrentHashMap<String, Kept>()

    override fun covers(league: String): Boolean = enabled && hasKey() && leagueId(league) != null

    override suspend fun games(league: String, date: LocalDate): List<GameScore>? {
        val id = leagueId(league) ?: return null
        if (!enabled || !hasKey()) return null
        val from = date.atStartOfDay(ET).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(ET).toInstant().toEpochMilli()
        val key = "$id|$date"
        val events = days[key]?.takeIf { clock() - it.atMs < KEEP_MS }?.events ?: try {
            client.eventsAll(listOf("leagueID" to id, "startsAfter" to from.toString(), "startsBefore" to to.toString(), "oddID" to "points-home-game-ml-home", "limit" to SportsGameOddsClient.PAGE.toString()), maxPages = 3).events
                .also { days[key] = Kept(clock(), it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return events.mapNotNull { score(league, it) }
    }

    override suspend fun players(game: GameScore): List<PlayerLine>? {
        if (!enabled || !hasKey() || !game.id.startsWith(PREFIX)) return null
        val eventId = game.id.removePrefix(PREFIX)
        val e = boxes[eventId]?.takeIf { clock() - it.atMs < KEEP_MS }?.events?.firstOrNull() ?: try {
            client.eventsAll(listOf("eventID" to eventId, "expandResults" to "true", "oddID" to "points-home-game-ml-home")).events.firstOrNull()?.also { boxes[eventId] = Kept(clock(), listOf(it)) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        val sport = SgoConvert.Sport.of(e.leagueId)
        val game = e.results["game"] ?: return null
        val back = SgoProps.statIds(sport).mapNotNull { s -> SgoProps.novigStat(sport, s)?.let { n -> s to n } }.toMap()
        return game.mapNotNull { (entity, stats) ->
            if (entity == "home" || entity == "away" || entity == "all") return@mapNotNull null
            val name = e.players[entity]?.name ?: SgoProps.nameFromId(entity) ?: return@mapNotNull null
            val mapped = stats.mapNotNull { (k, v) -> back[k]?.let { it to v } }.toMap()
            // SGO lists EVERY player of the game with every stat, a player who never played at zero across the board (measured 2026-10-09: 72 players, all stats, for an NFL game). A line of nothing but
            // zeros is "not in the box score", as a football box score treats him: the grader waits or leaves it to Tj instead of calling an Under on a player who did not play (Novig voids that bet).
            if (mapped.isEmpty() || stats.values.none { it != 0.0 }) null else PlayerLine(name, mapped)
        }
    }

    private fun score(league: String, e: SgoEvent): GameScore? {
        val start = e.startsMs ?: return null
        val periods = e.results.filterKeys { it in PERIODS }.toSortedMap(compareBy { PERIODS.indexOf(it) })
        return GameScore(
            id = PREFIX + e.eventId, league = league, home = e.home, away = e.away, startMs = start,
            final = e.finalized && !e.cancelled, called = e.cancelled || (e.delayed && !e.started),
            homeScore = e.homeScore, awayScore = e.awayScore,
            homePeriods = periods.values.map { (it["home"]?.get("points") ?: 0.0).toInt() },
            awayPeriods = periods.values.map { (it["away"]?.get("points") ?: 0.0).toInt() },
            calledReason = if (e.cancelled) "Cancelled" else if (e.delayed && !e.started) "Postponed" else null,
        )
    }

    companion object {
        const val PREFIX = "sgo:"
        const val KEEP_MS = 5 * 60_000L
        private val ET = ZoneId.of("America/New_York")
        private val PERIODS = listOf("1q", "2q", "3q", "4q", "1p", "2p", "3p", "1i", "2i", "3i", "4i", "5i", "6i", "7i", "8i", "9i", "ot")

        fun leagueId(league: String): String? =
            Leagues.ALL.firstOrNull { it.novigName.equals(league, true) || it.displayName.equals(league, true) }?.let { SgoBooks.leagueId(it) }
    }
}

/**
 * The scores Tj's bets are graded from: [primary] (SportsGameOdds Pro, when it is on) first, then [secondary] (ESPN, MLB: free). A prop's box score is the free feed's when it has the game (it also knows who
 * was inactive); SportsGameOdds' own stat line is the fallback. Nothing is graded from a game only one feed knows unless that feed says it is final.
 */
class ChainedScores(private val primary: ScoreSource, private val secondary: ScoreSource) : ScoreSource {
    override fun covers(league: String) = primary.covers(league) || secondary.covers(league)

    override suspend fun games(league: String, date: LocalDate): List<GameScore>? {
        val p = if (primary.covers(league)) primary.games(league, date) else null
        if (!p.isNullOrEmpty()) return p
        return secondary.games(league, date) ?: p
    }

    override suspend fun players(game: GameScore): List<PlayerLine>? {
        if (!game.id.startsWith(SgoScores.PREFIX)) return secondary.players(game) ?: primary.players(game)
        val date = Instant.ofEpochMilli(game.startMs).atZone(ZoneId.of("America/New_York")).toLocalDate()
        val twin = secondary.games(game.league, date)?.firstOrNull { g ->
            abs(g.startMs - game.startMs) <= 4 * 3_600_000L && TeamMatcher.whichOf(game.home, g.home, g.away) == 1 && TeamMatcher.whichOf(game.away, g.home, g.away) == 2
        }
        return twin?.let { secondary.players(it) } ?: primary.players(game)
    }
}
