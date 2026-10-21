package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.lab.AltScore
import com.tjshea.vigilant.data.novig.lab.EspnGame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.ConcurrentHashMap

/** One game's live state from SharpAPI's game-state channel (SHARPAPI.md §5): scores merged across the books covering it. */
data class SaGameState(
    val eventId: String,
    val sport: String,
    val league: String,
    val home: String,
    val away: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val period: String?,
    val periodNumber: Int?,
    val clock: String?,
    val live: Boolean,
    val final: Boolean,
    val winner: String?,
    val bookCount: Int?,
    val stale: Boolean,
    val atMs: Long,
)

/**
 * SharpAPI's live game state (Tj, 2026-10-21, SS: "I added websocket streaming and game state support"), kept from the stream's `gamestate:snapshot` / `gamestate:update` (merged by event id) and
 * `gamestate:removed`, or from `GET /gamestate` when the stream is not up. It is a score source for the engine's live lane beside ParlayAPI's play-by-play and SX Bet ([scoreFor]); finished rows are kept
 * the carry window for the record.
 */
class SharpApiScores(private val clock: () -> Long = System::currentTimeMillis) {
    private val games = ConcurrentHashMap<String, SaGameState>()

    @Volatile var heardAtMs = 0L
        private set
    @Volatile var frames = 0L
        private set

    fun size(): Int = games.size
    fun live(): Int = games.values.count { it.live && !it.final }
    fun all(): List<SaGameState> = games.values.toList()

    /** Rows of a snapshot or an update (a list of event rows each with its `event_id`): merged in. [replace] = a full slate (the REST read, the stream's snapshot): what it does not name is gone. */
    fun apply(rows: List<JsonObject>, replace: Boolean = false) {
        val now = clock()
        val seen = HashSet<String>()
        for (o in rows) {
            val id = o.s("event_id") ?: continue
            val prev = games[id]
            val g = parse(id, o, prev, now) ?: continue
            games[id] = g
            seen += id
        }
        if (replace) games.keys.removeIf { it !in seen }
        heardAtMs = now
        frames++
    }

    fun remove(ids: Collection<String>) { ids.forEach { games.remove(it) }; heardAtMs = clock() }

    /** The REST answer (`{data: {sport: {event_id: row}}}`) as rows with their `event_id`. */
    fun applyRest(body: JsonObject) {
        val data = body["data"] as? JsonObject ?: return
        val rows = ArrayList<JsonObject>()
        for ((_, sport) in data) for ((id, row) in (sport as? JsonObject).orEmpty()) (row as? JsonObject)?.let { rows += JsonObject(it + ("event_id" to JsonPrimitive(id))) }
        apply(rows, replace = true)
    }

    /**
     * The live score of [home] v [away] in Novig's [league] as the live lane reads scores ([AltScore]), or null: a live, fresh row whose teams match. Its age is when SharpAPI last spoke about game state
     * ([heardAtMs]: a quiet stretch of a game is a current score; a silent channel ages it out).
     */
    fun scoreFor(league: String, home: String, away: String): AltScore? {
        val slug = SharpApiBooks.leagueSlugOf(league) ?: return null
        for (g in games.values) {
            if (!g.live || g.final || g.stale || g.league != slug) continue
            val hs = g.homeScore ?: continue
            val aws = g.awayScore ?: continue
            val straight = TeamMatcher.similarity(home, g.home) >= 0.5 && TeamMatcher.similarity(away, g.away) >= 0.5
            val swapped = TeamMatcher.similarity(home, g.away) >= 0.5 && TeamMatcher.similarity(away, g.home) >= 0.5
            if (!straight && !swapped) continue
            val clockSec = g.clock?.let(::seconds) ?: 0.0
            val game = if (straight) EspnGame(g.home, "", g.away, "", hs, aws, true, g.periodNumber ?: 0, clockSec, id = g.eventId)
            else EspnGame(g.away, "", g.home, "", aws, hs, true, g.periodNumber ?: 0, clockSec, id = g.eventId)
            return AltScore(game, heardAtMs, SOURCE)
        }
        return null
    }

    companion object {
        const val SOURCE = "SharpAPI"

        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
        private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
        private fun JsonObject.b(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

        /** "5:42" -> 342 s; null when it is not a clock. */
        fun seconds(clock: String): Double? {
            val p = clock.trim().split(':')
            if (p.size != 2) return null
            val m = p[0].toIntOrNull() ?: return null
            val s = p[1].toDoubleOrNull() ?: return null
            return m * 60.0 + s
        }

        /** A row onto what was held (a stream update may carry only what changed). Set-scored finals carry sets, not points. */
        fun parse(id: String, o: JsonObject, prev: SaGameState?, now: Long): SaGameState? {
            val final = o.s("status") == "final" || (prev?.final == true && o.s("status") == null)
            val home = o.s("home_team") ?: prev?.home ?: return null
            val away = o.s("away_team") ?: prev?.away ?: return null
            return SaGameState(
                eventId = id, sport = o.s("sport") ?: prev?.sport.orEmpty(), league = o.s("league") ?: prev?.league.orEmpty(), home = home, away = away,
                homeScore = o.i("home_score") ?: o.i("sets_home")?.takeIf { final } ?: prev?.homeScore,
                awayScore = o.i("away_score") ?: o.i("sets_away")?.takeIf { final } ?: prev?.awayScore,
                period = o.s("game_period") ?: prev?.period, periodNumber = o.i("period_number") ?: prev?.periodNumber, clock = o.s("game_clock") ?: prev?.clock,
                live = o.b("is_live") ?: prev?.live ?: false, final = final, winner = o.s("winner") ?: prev?.winner, bookCount = o.i("book_count") ?: prev?.bookCount,
                stale = o.b("stale") == true || o.b("aggregator_stale") == true, atMs = now,
            )
        }

        /** Rows of a stream frame's `data` (a list) as objects. */
        fun rowsOf(data: kotlinx.serialization.json.JsonElement?): List<JsonObject> = (data as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }
}
