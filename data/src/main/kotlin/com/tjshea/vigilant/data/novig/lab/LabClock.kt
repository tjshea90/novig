package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.match.TeamMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** One game of an ESPN scoreboard as the lab needs it: both teams (full name and abbreviation), the score, whether it is in play, and where in the game it is. */
data class EspnGame(
    val home: String,
    val homeAbbr: String,
    val away: String,
    val awayAbbr: String,
    val homeScore: Int,
    val awayScore: Int,
    val live: Boolean,
    val period: Int,
    val clockSec: Double,
) {
    /** The score as a lead for the team Novig calls [ref] (its abbreviation, or a name); null when [ref] matches neither side. */
    fun marginOf(ref: String): Int? = when {
        ref.equals(homeAbbr, true) -> homeScore - awayScore
        ref.equals(awayAbbr, true) -> awayScore - homeScore
        TeamMatcher.similarity(ref, home) >= 0.5 && TeamMatcher.similarity(ref, away) < 0.5 -> homeScore - awayScore
        TeamMatcher.similarity(ref, away) >= 0.5 && TeamMatcher.similarity(ref, home) < 0.5 -> awayScore - homeScore
        else -> null
    }
}

/**
 * Game state for the tail scanner from ESPN's free scoreboard (the same route the feed test already reads): pure parsing and the arithmetic of how much of a game is left. Regulation only:
 * overtime, soccer and anything whose clock can't be read give null, and the scanner then does nothing for that game (it never guesses a clock).
 */
object LabClock {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Novig's league -> ESPN's scoreboard path. */
    val ESPN_PATHS = mapOf(
        "NFL" to "football/nfl",
        "NCAAF" to "football/college-football",
        "NBA" to "basketball/nba",
        "WNBA" to "basketball/wnba",
        "NCAAB" to "basketball/mens-college-basketball",
        "NHL" to "hockey/nhl",
        "MLB" to "baseball/mlb",
    )

    fun sportOf(league: String): TailSport? = when (league) {
        "NFL", "NCAAF" -> TailSport.FOOTBALL
        "NBA", "WNBA", "NCAAB" -> TailSport.BASKETBALL
        "NHL" -> TailSport.HOCKEY
        "MLB" -> TailSport.BASEBALL
        else -> null
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    /** ESPN's `.../scoreboard`: every game with both teams and a score. */
    fun parseEspn(body: String): List<EspnGame> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull().obj() ?: return emptyList()
        val events = root["events"] as? JsonArray ?: return emptyList()
        return events.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val comp = (o["competitions"] as? JsonArray)?.firstOrNull().obj() ?: return@mapNotNull null
            val cs = (comp["competitors"] as? JsonArray)?.mapNotNull { it.obj() } ?: return@mapNotNull null
            val home = cs.firstOrNull { it["homeAway"].str() == "home" } ?: return@mapNotNull null
            val away = cs.firstOrNull { it["homeAway"].str() == "away" } ?: return@mapNotNull null
            val status = o["status"].obj() ?: comp["status"].obj()
            val state = status?.get("type").obj()?.get("state").str()
            EspnGame(
                home = home["team"].obj()?.get("displayName").str() ?: return@mapNotNull null,
                homeAbbr = home["team"].obj()?.get("abbreviation").str() ?: "",
                away = away["team"].obj()?.get("displayName").str() ?: return@mapNotNull null,
                awayAbbr = away["team"].obj()?.get("abbreviation").str() ?: "",
                homeScore = home["score"].int() ?: return@mapNotNull null,
                awayScore = away["score"].int() ?: return@mapNotNull null,
                live = state == "in",
                period = status?.get("period").int() ?: 0,
                clockSec = clockSeconds(status?.get("displayClock").str()) ?: 0.0,
            )
        }
    }

    /** "7:23" -> 443 seconds; "45.3" (under a minute, with tenths) -> 45.3; null when it is neither. */
    fun clockSeconds(display: String?): Double? {
        val s = display?.trim() ?: return null
        if (s.contains(':')) {
            val (m, sec) = s.split(':', limit = 2)
            return (m.toIntOrNull() ?: return null) * 60.0 + (sec.toDoubleOrNull() ?: return null)
        }
        return s.toDoubleOrNull()
    }

    /**
     * The share of regulation still to play (1 = not started, 0 = over), or null for overtime, an unknown league or a clock out of range. Baseball has no clock: an inning is a ninth of the game, and
     * which half it is isn't known, so the middle of the inning is used.
     */
    fun fractionLeft(league: String, period: Int, clockSec: Double): Double? {
        val (periods, length) = when (league) {
            "NFL", "NCAAF" -> 4 to 900.0
            "NBA" -> 4 to 720.0
            "WNBA" -> 4 to 600.0
            "NCAAB" -> 2 to 1200.0
            "NHL" -> 3 to 1200.0
            "MLB" -> return if (period in 1..9) ((9 - period) + 0.5) / 9.0 else null
            else -> return null
        }
        if (period !in 1..periods || clockSec < 0.0 || clockSec > length) return null
        return ((periods - period) * length + clockSec) / (periods * length)
    }
}
