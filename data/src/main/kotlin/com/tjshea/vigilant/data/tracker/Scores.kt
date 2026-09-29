package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.awaitText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One game as a score feed reports it: teams, start, whether it's over, and the score by period. */
data class GameScore(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    val startMs: Long,
    /** Over and official: the score is final. */
    val final: Boolean,
    /** Postponed, cancelled or suspended: there's no result to settle with (yet). */
    val called: Boolean,
    val homeScore: Int?,
    val awayScore: Int?,
    /**
     * Runs, points or goals per quarter / inning / period, in order (overtime included). A tennis match
     * ([homeScore] and [awayScore] are its sets won) has the games of each set here, tiebreaks not counted.
     */
    val homePeriods: List<Int> = emptyList(),
    val awayPeriods: List<Int> = emptyList(),
    /** Why [called], when the feed says ("Retired", "Walkover", "Postponed"): what the Tracker tells Tj. */
    val calledReason: String? = null,
) {
    val tennis: Boolean get() = league == "ATP" || league == "WTA"
}

/** One player's box-score line, in Novig's stat names ("RECEIVING_YARDS" to 94.0). */
data class PlayerLine(val name: String, val stats: Map<String, Double>)

/** Final scores and box scores for settling bets. */
interface ScoreSource {
    /** [league]'s games on the Eastern-time date [date]; null when the feed couldn't be read. */
    suspend fun games(league: String, date: LocalDate): List<GameScore>?

    /** A finished game's player lines; null when they couldn't be read. */
    suspend fun players(game: GameScore): List<PlayerLine>?

    /** Whether this source has scores for [league] at all. */
    fun covers(league: String): Boolean
}

/**
 * Free score feeds, no key (the 2026-09-27 full test found Novig's public catalog drops a game and
 * its markets once it's over, so it can't settle anything; TASKS.md N7): **ESPN**'s public
 * scoreboard and box scores for football, basketball and hockey (the same site Vigilant already
 * reads rosters from), and **MLB's own Stats API** (statsapi.mlb.com) for baseball, whose box
 * score has every stat Novig lists props on (total bases, stolen bases, pitcher outs…) where
 * ESPN's doesn't. Every answer is kept a few minutes, so one pass reads each league's day once;
 * requests are at least [gapMs] apart.
 */
class FreeScores(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val espnBase: String = "https://site.api.espn.com/apis/site/v2/sports",
    private val mlbBase: String = "https://statsapi.mlb.com/api/v1",
    private val clock: () -> Long = System::currentTimeMillis,
    private val gapMs: Long = 300,
) : ScoreSource {

    override fun covers(league: String) = league.uppercase() == "MLB" || espnPath(league) != null

    private class Kept<T>(val value: T, val atMs: Long)

    private val mutex = Mutex()
    private val days = HashMap<String, Kept<List<GameScore>>>()
    private val boxes = HashMap<String, Kept<List<PlayerLine>>>()

    /** Requests made (tests count them). */
    @Volatile
    var requests = 0
        private set

    override suspend fun games(league: String, date: LocalDate): List<GameScore>? {
        val key = "${league.uppercase()}|$date"
        cachedOf(days, key)?.let { return it }
        val list = if (league.uppercase() == "MLB") mlbDay(date) else espnDay(league, date)
        if (list != null) keep(days, key, list)
        return list
    }

    override suspend fun players(game: GameScore): List<PlayerLine>? {
        val key = "${game.league}|${game.id}"
        cachedOf(boxes, key)?.let { return it }
        val list = if (game.league == "MLB") mlbBox(game.id) else espnBox(game)
        if (list != null) keep(boxes, key, list)
        return list
    }

    private suspend fun <T> cachedOf(map: HashMap<String, Kept<T>>, key: String): T? =
        mutex.withLock { map[key]?.takeIf { clock() - it.atMs < KEEP_MS }?.value }

    private suspend fun <T> keep(map: HashMap<String, Kept<T>>, key: String, value: T) = mutex.withLock {
        val now = clock()
        map.entries.removeAll { now - it.value.atMs >= KEEP_MS }
        map[key] = Kept(value, now)
    }

    // ---- ESPN --------------------------------------------------------------------------------

    private suspend fun espnDay(league: String, date: LocalDate): List<GameScore>? {
        val path = espnPath(league) ?: return null
        // College lists only featured games unless asked for every FBS / Division I game.
        val extra = when (league.uppercase()) {
            "NCAAF" -> "&groups=80&limit=400"
            "NCAAB" -> "&groups=50&limit=400"
            else -> ""
        }
        val root = get("$espnBase/$path/scoreboard?dates=${date.format(DAY)}$extra") ?: return null
        return if (league.uppercase() in TENNIS) parseEspnTennisDay(root, league.uppercase()) else parseEspnDay(root, league.uppercase())
    }

    private suspend fun espnBox(game: GameScore): List<PlayerLine>? {
        // A tennis match has no player box score here: its bets grade from the sets and games.
        if (game.tennis) return emptyList()
        val path = espnPath(game.league) ?: return null
        val root = get("$espnBase/$path/summary?event=${game.id}") ?: return null
        return parseEspnBox(root)
    }

    // ---- MLB Stats API ------------------------------------------------------------------------

    private suspend fun mlbDay(date: LocalDate): List<GameScore>? {
        val root = get("$mlbBase/schedule?sportId=1&date=$date&hydrate=linescore") ?: return null
        return parseMlbDay(root)
    }

    private suspend fun mlbBox(gamePk: String): List<PlayerLine>? {
        val root = get("$mlbBase/game/$gamePk/boxscore") ?: return null
        return parseMlbBox(root)
    }

    private val paceMutex = Mutex()
    private var lastGetMs = Long.MIN_VALUE / 2

    private suspend fun get(url: String): JsonElement? {
        paceMutex.withLock {
            val wait = lastGetMs + gapMs - clock()
            if (wait > 0) delay(wait)
            lastGetMs = clock()
        }
        requests++
        return try {
            val reply = http.newCall(Request.Builder().url(url).get().build()).awaitText()
            if (!reply.isSuccessful) null else withContext(Dispatchers.Default) { runCatching { json.parseToJsonElement(reply.body) }.getOrNull() }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** A day's scores and a box score are re-used this long. */
        const val KEEP_MS = 5 * 60_000L

        val ET: ZoneId = ZoneId.of("America/New_York")
        private val DAY = DateTimeFormatter.BASIC_ISO_DATE

        /** The Eastern-time date a game starting at [ms] is listed under. */
        fun etDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ET).toLocalDate()

        fun espnPath(league: String): String? = when (league.uppercase()) {
            "NFL" -> "football/nfl"
            "NCAAF" -> "football/college-football"
            "NBA" -> "basketball/nba"
            "WNBA" -> "basketball/wnba"
            "NCAAB" -> "basketball/mens-college-basketball"
            "NHL" -> "hockey/nhl"
            "ATP" -> "tennis/atp"
            "WTA" -> "tennis/wta"
            else -> null
        }

        private val TENNIS = setOf("ATP", "WTA")

        private fun JsonElement?.obj() = this as? JsonObject
        private fun JsonElement?.arr() = (this as? JsonArray).orEmpty()
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

        fun parseEspnDay(root: JsonElement, league: String): List<GameScore> = root.obj()?.get("events").arr().mapNotNull { e ->
            val ev = e.obj() ?: return@mapNotNull null
            val id = ev.str("id") ?: return@mapNotNull null
            val c = ev["competitions"].arr().firstOrNull().obj() ?: return@mapNotNull null
            val type = c["status"].obj()?.get("type").obj()
            val sides = c["competitors"].arr().mapNotNull { it.obj() }
            val home = sides.firstOrNull { it.str("homeAway") == "home" } ?: return@mapNotNull null
            val away = sides.firstOrNull { it.str("homeAway") == "away" } ?: return@mapNotNull null
            fun name(t: JsonObject) = t["team"].obj()?.str("displayName")
            fun periods(t: JsonObject) = t["linescores"].arr().mapNotNull { it.obj()?.num("value")?.toInt() }
            val status = type?.str("name").orEmpty()
            GameScore(
                id = id,
                league = league,
                home = name(home) ?: return@mapNotNull null,
                away = name(away) ?: return@mapNotNull null,
                startMs = ev.str("date")?.let(::isoMs) ?: return@mapNotNull null,
                final = (type?.get("completed") as? JsonPrimitive)?.booleanOrNull == true && type.str("state") == "post" && status in FINAL_ESPN,
                called = status in CALLED_ESPN,
                homeScore = home.num("score")?.toInt(),
                awayScore = away.num("score")?.toInt(),
                homePeriods = periods(home),
                awayPeriods = periods(away),
            )
        }

        /** ESPN's final statuses (overtime and shootouts included). */
        private val FINAL_ESPN = setOf("STATUS_FINAL", "STATUS_FINAL_OT", "STATUS_FINAL_SO", "STATUS_FINAL_PEN", "STATUS_END_OF_EXTRA_TIME")
        private val CALLED_ESPN = setOf("STATUS_POSTPONED", "STATUS_CANCELED", "STATUS_SUSPENDED", "STATUS_FORFEIT", "STATUS_ABANDONED")

        /**
         * ESPN's box score as Novig's stats. Football: passing, rushing, receiving and kicking lines
         * (anytime touchdowns = rushing + receiving + return touchdowns). Basketball: points,
         * rebounds, assists, threes.
         */
        fun parseEspnBox(root: JsonElement): List<PlayerLine> {
            val byPlayer = LinkedHashMap<String, HashMap<String, Double>>()
            for (team in root.obj()?.get("boxscore").obj()?.get("players").arr()) {
                for (group in team.obj()?.get("statistics").arr()) {
                    val g = group.obj() ?: continue
                    val keys = g["keys"].arr().mapNotNull { (it as? JsonPrimitive)?.content }
                    val kind = g.str("name")
                    for (a in g["athletes"].arr()) {
                        val ao = a.obj() ?: continue
                        val name = ao["athlete"].obj()?.str("displayName") ?: continue
                        val values = ao["stats"].arr().map { (it as? JsonPrimitive)?.content.orEmpty() }
                        if (values.isEmpty()) continue
                        val raw = keys.zip(values).toMap()
                        val stats = byPlayer.getOrPut(name) { HashMap() }
                        espnStats(kind, raw).forEach { (k, v) -> stats[k] = (stats[k] ?: 0.0) + v }
                    }
                }
            }
            return byPlayer.map { (name, stats) ->
                // Derived stats, from what the groups gave.
                stats["RUSHING_YARDS"]?.let { r -> stats["RUSHING_AND_RECEIVING_YARDS"] = r + (stats["RECEIVING_YARDS"] ?: 0.0) }
                    ?: stats["RECEIVING_YARDS"]?.let { stats["RUSHING_AND_RECEIVING_YARDS"] = it }
                stats["PASSING_YARDS"]?.let { p -> stats["PASSING_AND_RUSHING_YARDS"] = p + (stats["RUSHING_YARDS"] ?: 0.0) }
                if (stats.containsKey("POINTS")) {
                    stats["POINTS_REBOUNDS_ASSISTS"] = (stats["POINTS"] ?: 0.0) + (stats["REBOUNDS"] ?: 0.0) + (stats["ASSISTS"] ?: 0.0)
                }
                PlayerLine(name, stats)
            }
        }

        /** One ESPN stat group's numbers as Novig stats (touchdowns summed across groups by the caller). */
        private fun espnStats(group: String?, raw: Map<String, String>): Map<String, Double> {
            fun n(k: String) = raw[k]?.trim()?.toDoubleOrNull()
            fun made(k: String) = raw[k]?.substringBefore('/')?.substringBefore('-')?.trim()?.toDoubleOrNull()
            fun tried(k: String) = raw[k]?.substringAfter('/', "")?.trim()?.toDoubleOrNull()
            val out = HashMap<String, Double>()
            fun put(stat: String, v: Double?) { if (v != null) out[stat] = v }
            when (group) {
                "passing" -> {
                    put("PASSING_YARDS", n("passingYards"))
                    put("PASSING_TOUCHDOWNS", n("passingTouchdowns"))
                    put("INTERCEPTIONS_THROWN", n("interceptions"))
                    put("PASSING_COMPLETIONS", made("completions/passingAttempts"))
                    put("PASSING_ATTEMPTS", tried("completions/passingAttempts"))
                }
                "rushing" -> {
                    put("RUSHING_YARDS", n("rushingYards"))
                    put("RUSHING_ATTEMPTS", n("rushingAttempts"))
                    put("LONGEST_RUSH", n("longRushing"))
                    put("TOUCHDOWNS", n("rushingTouchdowns"))
                }
                "receiving" -> {
                    put("RECEPTIONS", n("receptions"))
                    put("RECEIVING_YARDS", n("receivingYards"))
                    put("LONGEST_RECEPTION", n("longReception"))
                    put("TOUCHDOWNS", n("receivingTouchdowns"))
                }
                "kickReturns" -> put("TOUCHDOWNS", n("kickReturnTouchdowns"))
                "puntReturns" -> put("TOUCHDOWNS", n("puntReturnTouchdowns"))
                "kicking" -> {
                    put("FIELD_GOALS_MADE", made("fieldGoalsMade/fieldGoalAttempts"))
                    put("KICKING_POINTS", n("totalKickingPoints"))
                }
                else -> {
                    // Basketball's one group (no name).
                    if (raw.containsKey("points")) {
                        put("POINTS", n("points"))
                        put("REBOUNDS", n("rebounds"))
                        put("ASSISTS", n("assists"))
                        put("THREE_POINTERS_MADE", made("threePointFieldGoalsMade-threePointFieldGoalsAttempted"))
                    }
                }
            }
            return out
        }

        fun parseMlbDay(root: JsonElement): List<GameScore> = root.obj()?.get("dates").arr().flatMap { d ->
            d.obj()?.get("games").arr().mapNotNull { g ->
                val o = g.obj() ?: return@mapNotNull null
                val id = o.str("gamePk") ?: return@mapNotNull null
                val status = o["status"].obj()
                val teams = o["teams"].obj() ?: return@mapNotNull null
                val home = teams["home"].obj() ?: return@mapNotNull null
                val away = teams["away"].obj() ?: return@mapNotNull null
                fun name(t: JsonObject) = t["team"].obj()?.str("name")
                val innings = o["linescore"].obj()?.get("innings").arr().mapNotNull { it.obj() }
                fun runs(side: String) = innings.map { it[side].obj()?.num("runs")?.toInt() ?: 0 }
                val detailed = status?.str("detailedState").orEmpty()
                GameScore(
                    id = id,
                    league = "MLB",
                    home = name(home) ?: return@mapNotNull null,
                    away = name(away) ?: return@mapNotNull null,
                    startMs = o.str("gameDate")?.let(::isoMs) ?: return@mapNotNull null,
                    final = status?.str("abstractGameState") == "Final" && detailed !in CALLED_MLB && !detailed.startsWith("Suspended"),
                    called = detailed in CALLED_MLB || detailed.startsWith("Suspended"),
                    homeScore = home.num("score")?.toInt(),
                    awayScore = away.num("score")?.toInt(),
                    homePeriods = runs("home"),
                    awayPeriods = runs("away"),
                )
            }
        }

        private val CALLED_MLB = setOf("Postponed", "Cancelled", "Canceled")

        /** MLB's box score as Novig's stats: batters' and pitchers' own lines. */
        fun parseMlbBox(root: JsonElement): List<PlayerLine> {
            val out = ArrayList<PlayerLine>()
            val teams = root.obj()?.get("teams").obj() ?: return out
            for (side in listOf("away", "home")) {
                val players = teams[side].obj()?.get("players").obj() ?: continue
                for ((_, p) in players) {
                    val po = p.obj() ?: continue
                    val name = po["person"].obj()?.str("fullName") ?: continue
                    val stats = HashMap<String, Double>()
                    val bat = po["stats"].obj()?.get("batting").obj()
                    // A batter's line only when he came to the plate (a bench player's is empty).
                    if (bat != null && ((bat.num("plateAppearances") ?: 0.0) > 0 || (bat.num("atBats") ?: 0.0) > 0)) {
                        fun b(k: String) = bat.num(k)
                        b("hits")?.let { stats["HITS"] = it }
                        b("totalBases")?.let { stats["TOTAL_BASES"] = it }
                        b("homeRuns")?.let { stats["HOME_RUNS"] = it }
                        b("rbi")?.let { stats["RBIS"] = it }
                        b("runs")?.let { stats["RUNS"] = it }
                        b("stolenBases")?.let { stats["STOLEN_BASES"] = it }
                        b("strikeOuts")?.let { stats["BATTING_STRIKEOUTS"] = it }
                        b("baseOnBalls")?.let { stats["BATTING_WALKS"] = it }
                        stats["HITS_RUNS_RBIS"] = (b("hits") ?: 0.0) + (b("runs") ?: 0.0) + (b("rbi") ?: 0.0)
                    }
                    val pitch = po["stats"].obj()?.get("pitching").obj()
                    if (pitch != null && pitch.containsKey("outs")) {
                        fun q(k: String) = pitch.num(k)
                        q("strikeOuts")?.let { stats["PITCHER_STRIKEOUTS"] = it }
                        q("hits")?.let { stats["HITS_ALLOWED"] = it }
                        q("earnedRuns")?.let { stats["EARNED_RUNS"] = it }
                        q("baseOnBalls")?.let { stats["WALKS"] = it }
                        q("outs")?.let { stats["PITCHER_OUTS"] = it }
                    }
                    if (stats.isNotEmpty()) out += PlayerLine(name, stats)
                }
            }
            return out
        }

        private fun isoMs(s: String): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            // ESPN writes "2026-09-25T00:15Z" (no seconds).
            ?: runCatching { java.time.OffsetDateTime.parse(s.replace(Regex("T(\\d{2}:\\d{2})Z$"), "T$1:00Z")).toInstant().toEpochMilli() }.getOrNull()
    }
}
