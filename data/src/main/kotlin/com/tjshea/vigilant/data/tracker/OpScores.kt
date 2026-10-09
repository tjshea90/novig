package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.reference.OddsPapiClient
import com.tjshea.vigilant.data.reference.OddsPapiFeed
import com.tjshea.vigilant.data.reference.OpBooks
import com.tjshea.vigilant.data.reference.OpFixture
import com.tjshea.vigilant.data.reference.OpParser
import com.tjshea.vigilant.data.scanner.Leagues
import java.time.LocalDate
import java.time.ZoneId

/**
 * Final scores for grading from OddsPapi (Tj, 2026-10-09: "grading bets as win and loss"; ODDSPAPI_API.md §4): `/fixtures` for the league's tournament and the Eastern-time day gives each game's
 * `status` (2 finished, 3 cancelled) and `scores`: `result` (everything, overtime included, as Novig settles) and `p1`, `p2`… per period. Participant 1 is [GameScore.home] (a game is matched to a bet by
 * team names, not by side). A finished game with no `result` score is not called final. A prop's box score is the free feeds' (OddsPapi sells prices, not player stat lines): [players] says "unknown"
 * and the chain asks ESPN / MLB. A read is kept [KEEP_MS], so a pass reads a league's day once.
 */
class OpScores(
    private val client: OddsPapiClient,
    private val feed: OddsPapiFeed,
    private val hasKey: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScoreSource {
    @Volatile var enabled: Boolean = false

    private class Kept(val atMs: Long, val games: List<OpFixture>)
    private val days = java.util.concurrent.ConcurrentHashMap<String, Kept>()

    override fun covers(league: String): Boolean = enabled && hasKey() && leagueOf(league) != null

    override suspend fun games(league: String, date: LocalDate): List<GameScore>? {
        val l = leagueOf(league) ?: return null
        if (!enabled || !hasKey()) return null
        val from = date.atStartOfDay(ET).toInstant().epochSecond
        val to = date.plusDays(1).atStartOfDay(ET).toInstant().epochSecond
        val key = "${l.novigName}|$date"
        val fixtures = days[key]?.takeIf { clock() - it.atMs < KEEP_MS }?.games ?: try {
            val tournament = feed.tournamentFor(l) ?: return null
            OpParser.fixtures(client.get("/fixtures", listOf("tournamentId" to tournament.id.toString(), "startTimeFrom" to from.toString(), "startTimeTo" to to.toString())))
                .also { days[key] = Kept(clock(), it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return fixtures.mapNotNull { score(l.novigName, it) }
    }

    override suspend fun players(game: GameScore): List<PlayerLine>? = null

    private fun score(league: String, f: OpFixture): GameScore? {
        val start = f.startMs ?: return null
        val result = f.scores["result"]
        val periods = f.scores.filterKeys { PERIOD.matches(it) || it == "overtime" }.entries.sortedBy { (k, _) -> if (k == "overtime") Int.MAX_VALUE else k.drop(1).toInt() }
        val hasResult = result?.first != null && result.second != null
        return GameScore(
            id = PREFIX + f.id, league = league, home = f.p1, away = f.p2, startMs = start,
            final = f.finished && hasResult, called = f.cancelled,
            homeScore = result?.first, awayScore = result?.second,
            homePeriods = periods.map { it.value.first ?: 0 }, awayPeriods = periods.map { it.value.second ?: 0 },
            calledReason = if (f.cancelled) "Cancelled" else null,
        )
    }

    companion object {
        const val PREFIX = "op:"
        const val KEEP_MS = 5 * 60_000L
        private val ET = ZoneId.of("America/New_York")
        private val PERIOD = Regex("p\\d{1,2}")

        fun leagueOf(league: String) = Leagues.ALL.firstOrNull { it.novigName.equals(league, true) || it.displayName.equals(league, true) }?.takeIf { OpBooks.supports(it) }
    }
}
