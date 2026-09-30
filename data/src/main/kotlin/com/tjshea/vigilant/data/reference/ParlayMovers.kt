package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.tracker.BetGrader
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import kotlin.math.abs

/**
 * One game whose Pinnacle moneyline moved (ParlayAPI's `/v1/meta/movers`, PARLAY_API.md §6.3): each side's first and last price in the
 * window, and how many probability points each side's implied chance moved (+: toward that side, its price shortened).
 */
data class Mover(
    val sportKey: String,
    val home: String,
    val away: String,
    val commenceMs: Long,
    val homeFirst: Int?,
    val homeLast: Int?,
    val awayFirst: Int?,
    val awayLast: Int?,
    val homePp: Double,
    val awayPp: Double,
    val snapshots: Int? = null,
) {
    /** The side the money moved toward: its chance went up. */
    val steamHome: Boolean get() = homePp >= awayPp

    val size: Double get() = maxOf(abs(homePp), abs(awayPp))
}

/** A league's movers as last read, and the window they cover. */
data class MoversBoard(val sportKey: String, val windowMinutes: Int, val readAtMs: Long, val movers: List<Mover>)

/**
 * A team bet's game moved at Pinnacle (Tj, 2026-09-30, PARLAY_API.md §6.3): [pp] probability points toward the bet's side (negative: against
 * it), from [first] to [last] (American) over the board's last [windowMinutes]. Toward the side bet is closing-line value in the making.
 */
data class LineMove(val team: String, val pp: Double, val first: Int?, val last: Int?, val windowMinutes: Int) {
    val toward: Boolean get() = pp > 0
}

/**
 * ParlayAPI's biggest Pinnacle moneyline moves per league (`GET /v1/meta/movers`): public, free, cached 90 s on their side, so read at most
 * every [REUSE_MS] a league. Moves under 1 probability point are left out by ParlayAPI as noise; games under way are left out
 * (`pre_game_only`).
 */
class ParlayMovers(
    private val http: OkHttpClient,
    private val json: Json,
    private val base: String = OddsFeed.PARLAY.base,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val state = MutableStateFlow<Map<String, MoversBoard>>(emptyMap())

    /** Each league's last board, by sport key. */
    val boards: StateFlow<Map<String, MoversBoard>> = state.asStateFlow()

    private val readAt = HashMap<String, Long>()

    /** Calls made (tests, Diagnostics). */
    @Volatile
    var requests: Int = 0
        private set

    /** Reads [sports]' boards not read in the last [REUSE_MS]; drops the boards of leagues no longer asked about. Never throws. */
    suspend fun refresh(sports: Collection<String>) {
        val wanted = sports.toSet()
        state.update { it.filterKeys { k -> k in wanted } }
        for (sport in wanted) {
            val now = clock()
            val due = synchronized(readAt) { (readAt[sport]?.let { now - it >= REUSE_MS } != false).also { if (it) readAt[sport] = now } }
            if (!due) continue
            requests++
            val board = try {
                read(sport)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            state.update { it + (sport to board) }
        }
    }

    private suspend fun read(sport: String): MoversBoard? {
        val url = "$base/meta/movers".toHttpUrl().newBuilder()
            .addQueryParameter("sport_key", sport)
            .addQueryParameter("window_minutes", WINDOW_MINUTES.toString())
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("pre_game_only", "true")
            .build()
        val reply = try {
            http.newCall(Request.Builder().url(url).get().build()).awaitText()
        } catch (e: IOException) {
            return null
        }
        if (!reply.isSuccessful) return null
        return parse(reply.body, json, sport, clock())
    }

    companion object {
        /** A league's board is read again after this at the soonest (ParlayAPI caches it 90 s). */
        const val REUSE_MS = 90_000L

        /** How often the screens' boards are refreshed while Vigilant is on screen. */
        const val EVERY_MS = 3 * 60_000L

        /** The window asked for: the longest ParlayAPI allows (6 hours), so a bet placed this afternoon sees its whole move. */
        const val WINDOW_MINUTES = 360

        const val LIMIT = 25

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()

        /** A `/v1/meta/movers` answer; null when it isn't one. */
        fun parse(body: String, json: Json, sportKey: String, now: Long): MoversBoard? {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val list = root["movers"] as? JsonArray ?: return null
            val movers = list.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                if (o.str("sport_key")?.let { it != sportKey } == true) return@mapNotNull null
                Mover(
                    sportKey = sportKey,
                    home = o.str("home_team") ?: return@mapNotNull null,
                    away = o.str("away_team") ?: return@mapNotNull null,
                    commenceMs = o.str("commence_time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null,
                    homeFirst = o.num("home_ml_first")?.toInt(), homeLast = o.num("home_ml_last")?.toInt(),
                    awayFirst = o.num("away_ml_first")?.toInt(), awayLast = o.num("away_ml_last")?.toInt(),
                    homePp = o.num("home_prob_delta_pp") ?: return@mapNotNull null,
                    awayPp = o.num("away_prob_delta_pp") ?: return@mapNotNull null,
                    snapshots = o.num("snapshots_seen")?.toInt(),
                )
            }
            return MoversBoard(sportKey, root.num("window_minutes")?.toInt() ?: WINDOW_MINUTES, now, movers.sortedByDescending { it.size })
        }
    }
}

/**
 * Which listed and open bets get a "Pinnacle moved toward/against" note (PARLAY_API.md §6.3): team bets (moneyline and full-game spread)
 * whose game is on a movers board. A moneyline move says nothing sure about a total or a player, so those get none. Pure; keys as
 * [InjuryTags]'.
 */
object LineMoves {
    /** A game a bet's game must start within this of to be the same one. */
    private const val START_GAP_MS = 12 * 3_600_000L

    private data class Want(val key: String, val sportKey: String, val event: String, val startsMs: Long?, val team: String)

    private fun sportOf(league: String): String? = Leagues.byNovigName(league.trim())?.takeIf { it.oddsApiListed }?.oddsApiSportKey

    /** "Dallas Cowboys", "DAL +3.5", "Ohio -33.5": the team, without its number. */
    private fun teamOf(selection: String): String = Picks.split(selection.trim()).first.trim()

    /** The move of [team]'s side in the board's game of [event] (both teams matched, start within 12 h), or null. */
    fun moveFor(board: MoversBoard, event: String, startsMs: Long?, team: String): LineMove? {
        val sides = NovigText.parseMatchup(event)?.let { listOf(it.away, it.home) } ?: Picks.sides(event).takeIf { it.size == 2 } ?: return null
        // Full names on both sides (Novig's, CNO's and ParlayAPI's games all name them so): every word of one in the other.
        fun same(a: String, b: String) = a.equals(b, ignoreCase = true) || TeamMatcher.similarity(a, b) >= 0.999
        val mover = board.movers.firstOrNull { mv ->
            (startsMs == null || abs(mv.commenceMs - startsMs) <= START_GAP_MS) &&
                ((same(sides[0], mv.away) && same(sides[1], mv.home)) || (same(sides[0], mv.home) && same(sides[1], mv.away)))
        } ?: return null
        // The side bet may be an abbreviation ("DAL +3.5"): the matcher that names Novig's outcomes decides.
        val away = TeamMatcher.labelIsAway(teamOf(team), mover.away, mover.home) ?: return null
        return if (!away) LineMove(mover.home, mover.homePp, mover.homeFirst, mover.homeLast, board.windowMinutes)
        else LineMove(mover.away, mover.awayPp, mover.awayFirst, mover.awayLast, board.windowMinutes)
    }

    /** Each team bet among [feed], [cnoRows] and open [bets] whose game moved, by its key. */
    fun notes(boards: Map<String, MoversBoard>, feed: List<Opportunity>, cnoRows: List<CnoRow>, bets: List<TrackedBet>, now: Long): Map<String, LineMove> {
        if (boards.isEmpty()) return emptyMap()
        val wants = ArrayList<Want>()
        for (o in feed) {
            if (o.kind != LineKind.MONEYLINE && o.kind != LineKind.SPREAD) continue
            if (o.lineKey?.period?.let { it != 0 } == true) continue
            wants += Want(o.key, o.league.oddsApiSportKey, o.event.description, o.event.startsTs, teamOf(o.selection))
        }
        for (r in cnoRows) {
            val market = r.market.lowercase()
            val team = market == "moneyline" || market == "point spread" || market == "spread" || market == "run line" || market == "puck line"
            if (!team) continue
            val sport = sportOf(r.league) ?: continue
            wants += Want(InjuryTags.cnoKey(r), sport, r.event, r.startsAtMs, teamOf(r.bet))
        }
        for (b in bets) {
            if (b.status != BetStatus.PENDING || now >= b.startsTs) continue
            val team = when (val p = BetGrader.pickOf(b)) {
                is BetGrader.Pick.Moneyline -> p.team
                is BetGrader.Pick.Spread -> p.team.takeIf { p.period == BetGrader.Period.GAME }
                else -> null
            } ?: continue
            val sport = sportOf(b.league) ?: continue
            wants += Want(InjuryTags.betKey(b), sport, b.eventName, b.startsTs, team)
        }
        val out = HashMap<String, LineMove>()
        for (w in wants) {
            val board = boards[w.sportKey] ?: continue
            moveFor(board, w.event, w.startsMs, w.team)?.let { out[w.key] = it }
        }
        return out
    }
}
