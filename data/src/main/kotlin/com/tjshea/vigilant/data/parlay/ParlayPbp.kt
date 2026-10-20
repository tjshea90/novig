package com.tjshea.vigilant.data.parlay

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.lab.AltScore
import com.tjshea.vigilant.data.novig.lab.EspnGame
import com.tjshea.vigilant.data.novig.lab.LabClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * ParlayAPI's live play-by-play stream as a live-score source (CH47, Tj 2026-10-20: "it also offers live point by point stream ... this may be better than the ESPN live feed I already use").
 *
 * The real frames (probe 2026-10-20, Release `parlay-samples`, `sse_basketball_nba.txt`): an `initial_state` event with `matches[]`, then `pbp_event` frames. A match is
 * `{match_id, sport_key, primary_source ("espn_nba", "espn_nhl", ...), last_event_at_ms, team_or_player_a, team_or_player_b, score_a, score_b, in_play, last_event_age_seconds,
 *  state: {clock "15:23", period 1, description, scoring_play}}`. For the US leagues the primary source IS ESPN (its play feed pushed, not polled), so this is the same truth a few seconds sooner;
 * a game whose primary source is not an ESPN one (tennis, soccer) is not used as a score here until its clock semantics are verified from a real frame (the board keeps it, [scoreFor] skips it).
 * Pure parsing and matching: the connection is [ParlayPbpStream].
 */
object ParlayPbp {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** One match's latest state. [atMs] is when its newest event happened (the provider's stamp), [seenMs] when this app learned it. */
    data class Match(
        val id: String, val sport: String, val source: String, val a: String, val b: String, val scoreA: Int, val scoreB: Int,
        val period: Int, val clockSec: Double?, val inPlay: Boolean, val finished: Boolean, val atMs: Long, val seenMs: Long,
    )

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.contentOrNull?.toIntOrNull() ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
    private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    /** One match object (an `initial_state` entry or a `pbp_event` frame carrying the same keys), or null when it lacks an id, both names or both scores. */
    fun parseMatch(o: JsonObject, seenMs: Long, previous: Match? = null): Match? {
        val id = o["match_id"].str() ?: return null
        val a = o["team_or_player_a"].str() ?: previous?.a ?: return null
        val b = o["team_or_player_b"].str() ?: previous?.b ?: return null
        val sa = o["score_a"].int() ?: previous?.scoreA ?: return null
        val sb = o["score_b"].int() ?: previous?.scoreB ?: return null
        val st = o["state"].obj()
        val clock = st?.get("clock")?.let { c -> (c as? JsonPrimitive)?.contentOrNull?.let { LabClock.clockSeconds(it) ?: it.toDoubleOrNull() } } ?: previous?.clockSec
        val period = st?.get("period").int() ?: o["period"].int() ?: previous?.period ?: 0
        val at = o["last_event_at_ms"].long() ?: o["captured_at_ms"].long() ?: seenMs
        val desc = st?.get("description").str() ?: o["description"].str() ?: ""
        val finished = desc.contains("End of Game", true) || desc.contains("Final", true) && !desc.contains("Semifinal", true) || (o["finished_at_ms"].long() != null)
        val inPlay = (o["in_play"] as? JsonPrimitive)?.booleanOrNull ?: previous?.inPlay ?: true
        return Match(
            id = id, sport = o["sport_key"].str() ?: previous?.sport ?: "", source = o["primary_source"].str() ?: o["source"].str() ?: previous?.source ?: "",
            a = a, b = b, scoreA = sa, scoreB = sb, period = period, clockSec = clock, inPlay = inPlay && !finished, finished = finished, atMs = at, seenMs = seenMs,
        )
    }

    /** The matches of an `initial_state` payload (`{"matches": [...]}`). */
    fun parseInitial(data: String, seenMs: Long): List<Match> {
        val root = runCatching { json.parseToJsonElement(data) }.getOrNull().obj() ?: return emptyList()
        return (root["matches"] as? JsonArray)?.mapNotNull { it.obj()?.let { o -> parseMatch(o, seenMs) } }.orEmpty()
    }

    /** A `pbp_event` payload: one match's newest event (merged over [previous] when the frame carries only the changed keys). */
    fun parseEvent(data: String, seenMs: Long, previous: (String) -> Match?): Match? {
        val o = runCatching { json.parseToJsonElement(data) }.getOrNull().obj() ?: return null
        val id = o["match_id"].str() ?: return null
        return parseMatch(o, seenMs, previous(id))
    }

    /** The match as Novig's [home] and [away] see it (names matched in both orders: the provider's a/b order is not a rule), or null when they match in neither or both orders. */
    fun asGame(m: Match, home: String, away: String): EspnGame? {
        val same = TeamMatcher.similarity(m.a, home) >= 0.5 && TeamMatcher.similarity(m.b, away) >= 0.5
        val swapped = TeamMatcher.similarity(m.b, home) >= 0.5 && TeamMatcher.similarity(m.a, away) >= 0.5
        if (same == swapped) return null
        val homeScore = if (same) m.scoreA else m.scoreB
        val awayScore = if (same) m.scoreB else m.scoreA
        return EspnGame(home = home, homeAbbr = "", away = away, awayAbbr = "", homeScore = homeScore, awayScore = awayScore, live = m.inPlay, period = m.period, clockSec = m.clockSec ?: 0.0, done = m.finished, id = m.id)
    }

    const val SOURCE = "ParlayAPI"

    /** True for the leagues' matches whose primary source is an ESPN play feed (clock and period are ESPN's own, the way the lane already reads them). */
    fun clockTrusted(m: Match): Boolean = m.source.startsWith("espn_") && m.clockSec != null

    /**
     * A score for [home] v [away] from [matches], or null: only a match with a trusted clock and a live state counts. Its age is [heardMs], when the stream last spoke (a frame or a keep-alive), not the
     * time of the match's newest event: a quiet stretch of a game is a current score, and a stream that went silent is an old one.
     */
    fun scoreFor(matches: Collection<Match>, home: String, away: String, heardMs: Long): AltScore? {
        for (m in matches) {
            if (!clockTrusted(m) || !m.inPlay) continue
            val g = asGame(m, home, away) ?: continue
            return AltScore(g, heardMs, SOURCE)
        }
        return null
    }
}

/** The matches of the streams read so far, by match id, per sport. */
class ParlayPbpBoard {
    private val matches = ConcurrentHashMap<String, ParlayPbp.Match>()

    /** When each sport's stream last spoke (any frame or keep-alive), ms. */
    val heard = ConcurrentHashMap<String, Long>()

    fun put(m: ParlayPbp.Match) { matches[m.id] = m }
    fun get(id: String): ParlayPbp.Match? = matches[id]
    fun all(sport: String): List<ParlayPbp.Match> = matches.values.filter { it.sport == sport }
    fun size() = matches.size

    /** Matches nobody has spoken about for [maxAgeMs] are dropped (a finished game stays only until it is stale). */
    fun prune(nowMs: Long, maxAgeMs: Long) { matches.values.removeIf { nowMs - maxOf(it.atMs, it.seenMs) > maxAgeMs } }
}

/** Novig's league -> ParlayAPI sport key, for the leagues the live lane watches. */
object ParlayPbpSports {
    val KEYS = mapOf(
        "NFL" to "americanfootball_nfl", "NCAAF" to "americanfootball_ncaaf", "NBA" to "basketball_nba", "WNBA" to "basketball_wnba",
        "NCAAB" to "basketball_ncaab", "NHL" to "icehockey_nhl", "MLB" to "baseball_mlb",
    )
}
