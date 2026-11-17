package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.novig.lab.AltScore
import com.tjshea.vigilant.data.novig.lab.EspnGame
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.TeamMatcher
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * PropLine's live game state, pushed on the second Streaming Lite socket (PLS3, Tj 2026-11-17: "Novig has its own socket ... can that socket be used better for something else").
 * The docs (2026-11-16): `game_state` (opt-in, since 2026-11-10) is one delivery per game each time the score, status, period, last play or situation changes (a clock-only change is not sent), read from each
 * league's own live feed about every 5 seconds, for MLB, NBA, NHL, NFL and NCAAF: `event_id`, `sport_key`, `home_team`, `away_team`, `status` (`upcoming`, `in_progress`, `final`, ...), `home_score`,
 * `away_score`, `period` ("Top 7th" / "Q3" / "P2"), `period_number`, `clock` ("6:02", null for MLB), `situation`, `last_play`, `source_updated_at`, `updated_at`.
 * It is a score source for the live lane beside ParlayAPI's play-by-play, SharpAPI's game state and SX Bet ([scoreFor]); SharpAPI's and ParlayAPI's scores need plans that lapsed.
 *
 * A quiet stretch of a game is a current score (no delivery without a change), so a score's age is when the SOCKET last spoke ([heardAtMs]: pings every 5 s, [PropLineStream]), and a live row nobody
 * has changed for [STALE_MS] is not believed (the league's feed stopped).
 */
class PropLineGameState(private val clock: () -> Long = System::currentTimeMillis) {
    data class Game(
        val eventId: String,
        /** The app's sport key (`basketball_nba`, `americanfootball_nfl`...). */
        val sport: String,
        val home: String,
        val away: String,
        val status: String,
        val homeScore: Int?,
        val awayScore: Int?,
        val periodNumber: Int?,
        val clockSec: Double?,
        val atMs: Long,
    ) {
        val live: Boolean get() = status == "in_progress"
        val final: Boolean get() = status == "final"
    }

    private val games = ConcurrentHashMap<String, Game>()

    /** When the socket last spoke (any frame); 0 while it is closed. */
    @Volatile var heardAtMs = 0L
        private set

    /** `game_state` deliveries applied. */
    @Volatile var frames = 0L
        private set

    fun heard(now: Long = clock()) { heardAtMs = now }
    fun onClosed() { heardAtMs = 0L }

    fun size(): Int = games.size
    fun live(): Int = games.values.count { it.live }
    fun all(): List<Game> = games.values.toList()

    /** One `game_state` payload. Returns false when it is not a game this can use (no id, no teams). */
    fun apply(d: JsonObject, now: Long = clock()): Boolean {
        val id = d.str("event_id") ?: return false
        val sport = d.str("sport_key")?.let(PropLineLive::oddsApiSport) ?: return false
        val home = d.str("home_team") ?: return false
        val away = d.str("away_team") ?: return false
        val prev = games[id]
        val g = Game(
            eventId = id, sport = sport, home = home, away = away,
            status = d.str("status") ?: prev?.status ?: "upcoming",
            homeScore = d.int("home_score") ?: prev?.homeScore, awayScore = d.int("away_score") ?: prev?.awayScore,
            periodNumber = d.int("period_number") ?: prev?.periodNumber,
            // A clock-only change is not sent: the last clock is as old as the last delivery, and a null clock (MLB) stays null.
            clockSec = d.str("clock")?.let(::seconds),
            atMs = now,
        )
        games[id] = g
        heard(now)
        frames++
        if (games.size > MAX_GAMES) prune(now)
        return true
    }

    /** Rows of finished or silent games leave (a final is kept [FINAL_KEEP_MS] for the record). */
    fun prune(now: Long = clock()) {
        games.values.removeIf { g -> now - g.atMs > (if (g.final) FINAL_KEEP_MS else DAY_MS) }
    }

    /**
     * The live score of [home] v [away] in Novig's [league] ("NBA", "NFL"...) as the live lane reads scores, or null: a live, recently changed row of that league whose teams match in one order only.
     * Its age is [heardAtMs] (the socket), so a stream that went silent ages every score out.
     */
    fun scoreFor(league: String, home: String, away: String, now: Long = clock()): AltScore? {
        val sport = League.entries.firstOrNull { it.novigName == league }?.oddsApiSportKey ?: return null
        if (heardAtMs <= 0L) return null
        for (g in games.values) {
            if (g.sport != sport || !g.live || now - g.atMs > STALE_MS) continue
            val hs = g.homeScore ?: continue
            val aws = g.awayScore ?: continue
            val straight = TeamMatcher.similarity(home, g.home) >= 0.5 && TeamMatcher.similarity(away, g.away) >= 0.5
            val swapped = TeamMatcher.similarity(home, g.away) >= 0.5 && TeamMatcher.similarity(away, g.home) >= 0.5
            if (straight == swapped) continue
            val clockSec = g.clockSec ?: 0.0
            val period = g.periodNumber ?: 0
            val game = if (straight) EspnGame(g.home, "", g.away, "", hs, aws, true, period, clockSec, id = g.eventId)
            else EspnGame(g.away, "", g.home, "", aws, hs, true, period, clockSec, id = g.eventId)
            return AltScore(game, heardAtMs, SOURCE)
        }
        return null
    }

    fun describe(now: Long = clock()): String =
        if (frames == 0L) "game state: nothing heard yet"
        else "game state: ${live()} games in play of ${games.size} held, $frames pushes, last socket word ${if (heardAtMs <= 0L) "never (closed)" else "${((now - heardAtMs) / 1000).coerceAtLeast(0)} s ago"}"

    companion object {
        const val SOURCE = "PropLine"
        /** A game in play that nobody has changed for this long is not believed: its league feed stopped. */
        const val STALE_MS = 10 * 60_000L
        const val FINAL_KEEP_MS = 30 * 60_000L
        const val DAY_MS = 24 * 3_600_000L
        const val MAX_GAMES = 500

        /** "6:02" -> 362 s, "0:48.3" -> 48.3 s; null when it is not a clock. */
        fun seconds(clock: String): Double? {
            val parts = clock.trim().split(":")
            if (parts.size !in 2..3) return null
            var total = 0.0
            for (p in parts) total = total * 60 + (p.toDoubleOrNull() ?: return null)
            return total
        }

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotBlank() }
        private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() }
    }
}
