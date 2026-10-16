package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.scanner.Leagues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Final scores and box scores from PropLine (Tj, 2026-10-16: "prop grading support ... from propline", TCL67). `/scores` (free) lists a sport's games with their score and `completed`/`status`;
 * `/events/{id}/stats` (free, book-agnostic) is the official box score as `{player_name, stat_type, stat_value}` rows once the game is final. The stat names are mapped to Novig's ([PropLineStatMap]).
 * It is the last link of the grading chain (after ESPN/MLB, SGO and OddsPapi): it fills a game or a player the others do not have, and gives a second box score. A read is kept [KEEP_MS].
 */
class PropLineScores(
    private val client: PropLineClient,
    private val hasKey: () -> Boolean,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: () -> Long = System::currentTimeMillis,
) : ScoreSource {
    @Volatile var enabled: Boolean = false

    private class Kept<T>(val atMs: Long, val value: T)
    private val days = java.util.concurrent.ConcurrentHashMap<String, Kept<List<GameScore>>>()
    private val boxes = java.util.concurrent.ConcurrentHashMap<String, Kept<List<PlayerLine>>>()

    override fun covers(league: String): Boolean = enabled && hasKey() && sportKey(league) != null

    override suspend fun games(league: String, date: LocalDate): List<GameScore>? {
        val sport = sportKey(league) ?: return null
        if (!enabled || !hasKey()) return null
        val key = "$sport|$date"
        val all = days[key]?.takeIf { clock() - it.atMs < KEEP_MS }?.value ?: try {
            parseGames(client.scoresRaw(sport) ?: return null, json, league).also { days[key] = Kept(clock(), it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return all.filter { Instant.ofEpochMilli(it.startMs).atZone(ET).toLocalDate() == date }
    }

    override suspend fun players(game: GameScore): List<PlayerLine>? {
        if (!enabled || !hasKey() || !game.id.startsWith(PREFIX)) return null
        val sport = sportKey(game.league) ?: return null
        val id = game.id.removePrefix(PREFIX)
        boxes[id]?.takeIf { clock() - it.atMs < KEEP_MS }?.let { return it.value }
        return try {
            parseBox(client.statsRaw(sport, id) ?: return null, json, sport)?.also { boxes[id] = Kept(clock(), it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val PREFIX = "pl:"
        const val KEEP_MS = 5 * 60_000L
        private val ET = ZoneId.of("America/New_York")
        private val CALLED = setOf("postponed", "cancelled", "canceled", "retired", "walkover", "abandoned", "suspended")

        fun sportKey(league: String): String? =
            Leagues.ALL.firstOrNull { it.novigName.equals(league, true) || it.displayName.equals(league, true) }?.oddsApiSportKey?.takeIf { it in PropLineClient.SPORTS }

        private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull
        private fun JsonElement?.int() = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

        /** `/scores` rows as games: final only when `completed` (or status `final`); a postponed or cancelled game is [GameScore.called]. */
        fun parseGames(raw: String, json: Json, league: String): List<GameScore> {
            val arr = json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
            return arr.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o["id"].str() ?: return@mapNotNull null
                val start = o["commence_time"].str()?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
                val status = o["status"].str()?.lowercase()
                val final = (o["completed"] as? JsonPrimitive)?.contentOrNull == "true" || status == "final"
                val called = status in CALLED
                GameScore(
                    id = PREFIX + id, league = league, home = o["home_team"].str().orEmpty(), away = o["away_team"].str().orEmpty(), startMs = start,
                    final = final && !called, called = called, homeScore = o["home_score"].int(), awayScore = o["away_score"].int(),
                    calledReason = if (called) status!!.replaceFirstChar { it.uppercase() } else null,
                )
            }
        }

        /** `/events/{id}/stats`: one [PlayerLine] per player, stats in Novig's names; null until the game is final (a partial live box score must not grade a bet). */
        fun parseBox(raw: String, json: Json, sportKey: String): List<PlayerLine>? {
            val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
            val status = root["status"].str()?.lowercase()
            if (status != null && status != "final") return null
            val rows = root["stats"] as? JsonArray ?: return null
            val by = LinkedHashMap<String, HashMap<String, Double>>()
            for (r in rows) {
                val o = r as? JsonObject ?: continue
                val name = o["player_name"].str() ?: continue
                val stat = PropLineStatMap.novig(sportKey, o["stat_type"].str() ?: continue) ?: continue
                val v = (o["stat_value"] as? JsonPrimitive)?.doubleOrNull ?: continue
                by.getOrPut(name) { HashMap() }[stat] = v
            }
            return by.map { (n, s) -> PlayerLine(n, s) }
        }
    }
}

/** PropLine's box-score `stat_type`s as Novig's stat names (PropLine's stat vocabulary: research/propline/llms-full_2026-10-16.txt, "Player stats"). A stat without a Novig market is left out. */
object PropLineStatMap {
    private val MLB = mapOf(
        "hits" to "HITS", "total_bases" to "TOTAL_BASES", "strikeouts" to "PITCHER_STRIKEOUTS", "hits_runs_rbis" to "HITS_RUNS_RBIS", "home_runs" to "HOME_RUNS", "rbis" to "RBIS", "runs" to "RUNS",
        "stolen_bases" to "STOLEN_BASES", "batter_strikeouts" to "BATTING_STRIKEOUTS", "walks" to "BATTING_WALKS", "hits_allowed" to "HITS_ALLOWED", "earned_runs" to "EARNED_RUNS", "outs" to "PITCHER_OUTS",
    )
    private val BASKETBALL = mapOf(
        "points" to "POINTS", "rebounds" to "REBOUNDS", "assists" to "ASSISTS", "threes" to "THREE_POINTERS_MADE", "steals" to "STEALS", "blocks" to "BLOCKS", "points_rebounds" to "POINTS_REBOUNDS",
        "points_assists" to "POINTS_ASSISTS", "rebounds_assists" to "REBOUNDS_ASSISTS", "points_rebounds_assists" to "POINTS_REBOUNDS_ASSISTS",
    )
    private val NHL = mapOf("goals" to "PLAYER_GOALS", "points_nhl" to "POINTS", "shots_on_goal" to "SHOTS_ON_GOAL", "power_play_points" to "POWER_PLAY_POINTS", "saves" to "SAVES")
    private val FOOTBALL = mapOf(
        "passing_yards" to "PASSING_YARDS", "passing_tds" to "PASSING_TOUCHDOWNS", "passing_attempts" to "PASSING_ATTEMPTS", "passing_completions" to "PASSING_COMPLETIONS", "interceptions" to "INTERCEPTIONS_THROWN",
        "longest_completion" to "LONGEST_COMPLETION", "rushing_yards" to "RUSHING_YARDS", "rushing_attempts" to "RUSHING_ATTEMPTS", "longest_rush" to "LONGEST_RUSH", "receiving_yards" to "RECEIVING_YARDS",
        "receptions" to "RECEPTIONS", "longest_reception" to "LONGEST_RECEPTION", "pass_rush_yds" to "PASSING_AND_RUSHING_YARDS", "rush_reception_yds" to "RUSHING_AND_RECEIVING_YARDS",
        "field_goals_made" to "FIELD_GOALS_MADE", "kicking_points" to "KICKING_POINTS", "anytime_td" to "TOUCHDOWNS",
    )

    fun novig(sportKey: String, statType: String): String? = when {
        sportKey.startsWith("baseball") -> MLB[statType]
        sportKey.startsWith("basketball") -> BASKETBALL[statType]
        sportKey.contains("hockey") -> NHL[statType]
        sportKey.contains("football") -> FOOTBALL[statType]
        else -> null
    }
}
