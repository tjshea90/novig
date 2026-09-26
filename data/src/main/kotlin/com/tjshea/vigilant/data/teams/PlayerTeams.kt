package com.tjshea.vigilant.data.teams

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** One team as ESPN lists it, with every name a scanner might use for it. */
@Serializable
data class EspnTeam(val id: String, val abbreviation: String, val names: List<String>)

@Serializable
data class LeagueTeams(val teams: List<EspnTeam>, val fetchedAtMs: Long)

/** A team's players' names ([abbreviation] is what the widget prints: "HOU"). */
@Serializable
data class TeamRoster(val abbreviation: String, val players: List<String>, val fetchedAtMs: Long)

/** Everything read from ESPN so far: teams per league (by ESPN path), rosters by "path|team id". */
@Serializable
data class TeamsCache(
    val leagues: Map<String, LeagueTeams> = emptyMap(),
    val rosters: Map<String, TeamRoster> = emptyMap(),
) {
    /**
     * The team of [player] in [event] ("Houston Texans @ Indianapolis Colts") of [league] as CNO
     * names it ("NFL"), or null when it isn't known: the league or a team isn't read yet, or the
     * name is on neither roster (or, rarely, on both).
     */
    fun teamOf(league: String, event: String, player: String): String? {
        val path = PlayerTeams.espnPath(league) ?: return null
        val teams = leagues[path]?.teams ?: return null
        val found = PlayerTeams.sides(event).mapNotNull { PlayerTeams.findTeam(it, teams) }
            .mapNotNull { t -> rosters["$path|${t.id}"]?.takeIf { r -> r.players.any { PlayerNames.same(it, player) } } }
        return found.singleOrNull()?.abbreviation
    }
}

/**
 * Player teams for the CNO widget (Tj, 2026-09-26: "d. Schultz (hou). That way I know what team
 * to look for in the novig app"). Neither CNO's list nor Novig's catalog says which team a
 * player is on; ESPN's free site API does (RESEARCH.md §20): one teams list per league (a week)
 * and two rosters per game (a day), ~10–30 KB each gzipped. Read only for the games of player
 * bets on the list, one request at a time, and only while the CNO scanner is on screen (the
 * caller runs [keepFresh] alongside the list's own watch). A failed read just means no tag.
 */
class PlayerTeams(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val store: JsonFileStore<TeamsCache>? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = "https://site.api.espn.com/apis/site/v2/sports/",
) {
    private val _state = MutableStateFlow(TeamsCache())
    val state: StateFlow<TeamsCache> = _state.asStateFlow()

    private val mutex = Mutex()

    /** Failed reads by URL, so a bad one is retried only after [RETRY_MS]. */
    private val failedAt = HashMap<String, Long>()

    /** Requests made (tests count them). */
    @Volatile
    var requests = 0
        private set

    /** The cache from the last run, before any network. */
    suspend fun load() {
        val cached = store?.let { runCatching { it.read() }.getOrNull() } ?: return
        _state.update { if (it.leagues.isEmpty() && it.rosters.isEmpty()) cached else it }
    }

    /**
     * Reads whatever [games] (league, event) need and isn't cached: each league's teams, then each
     * game's two rosters. Returns how many reads it made.
     */
    suspend fun fill(games: List<Game>): Int = mutex.withLock {
        var reads = 0
        val now = clock()
        val players = games.filter { espnPath(it.league) != null }
        if (players.isEmpty()) return@withLock 0
        for (path in players.mapNotNull { espnPath(it.league) }.distinct()) {
            val have = _state.value.leagues[path]
            if (have != null && now - have.fetchedAtMs < TEAMS_TTL_MS) continue
            val url = "$baseUrl$path/teams?limit=1000"
            if (!due(url, now)) continue
            val teams = get(url)?.let(::parseTeams)
            reads++
            if (teams != null && teams.isNotEmpty()) {
                _state.update { it.copy(leagues = it.leagues + (path to LeagueTeams(teams, clock()))) }
                save()
            } else {
                failedAt[url] = clock()
            }
            delay(GAP_MS)
        }
        val wanted = LinkedHashSet<Pair<String, EspnTeam>>()
        for (row in players) {
            val path = espnPath(row.league) ?: continue
            val teams = _state.value.leagues[path]?.teams ?: continue
            sides(row.event).mapNotNull { findTeam(it, teams) }.forEach { wanted += path to it }
        }
        for ((path, team) in wanted) {
            if (reads >= MAX_READS_PER_PASS) break
            val have = _state.value.rosters["$path|${team.id}"]
            if (have != null && now - have.fetchedAtMs < ROSTER_TTL_MS) continue
            val url = "$baseUrl$path/teams/${team.id}/roster"
            if (!due(url, now)) continue
            val players = get(url)?.let(::parseRoster)
            reads++
            if (players != null && players.isNotEmpty()) {
                _state.update { it.copy(rosters = it.rosters + ("$path|${team.id}" to TeamRoster(team.abbreviation, players, clock()))) }
                save()
            } else {
                failedAt[url] = clock()
            }
            delay(GAP_MS)
        }
        reads
    }

    /** Each read is kept at once (a few KB), so a pass cut short loses nothing. */
    private suspend fun save() {
        val disk = store ?: return
        val snapshot = _state.value
        runCatching { disk.update { snapshot } }
    }

    /**
     * Keeps the teams of [rows]' player bets filled until cancelled. Only a game not seen before
     * starts reads (the list's own refreshes don't), and a failed one is tried again after
     * [RETRY_MS]. The caller runs it only while the CNO scanner is on screen.
     */
    suspend fun keepFresh(rows: Flow<List<CnoRow>>) {
        rows.map(::gamesOf).distinctUntilChanged().collectLatest { games ->
            if (games.isEmpty()) return@collectLatest
            while (true) {
                fill(games)
                delay(RETRY_MS)
            }
        }
    }

    /** A game with a player bet on the list. */
    data class Game(val league: String, val event: String)

    private fun due(url: String, now: Long): Boolean = failedAt[url]?.let { now - it >= RETRY_MS } ?: true

    private suspend fun get(url: String): JsonElement? {
        requests++
        val request = Request.Builder().url(url).get().header("User-Agent", USER_AGENT).build()
        return try {
            http.newCall(request).await().use { r ->
                if (!r.isSuccessful) null else r.body?.string()?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** A league's team list is re-read after a week… */
        const val TEAMS_TTL_MS = 7 * 24 * 60 * 60_000L

        /** …a roster after a day… */
        const val ROSTER_TTL_MS = 24 * 60 * 60_000L

        /** …a failed read after half an hour. */
        const val RETRY_MS = 30 * 60_000L

        /** Pause between two reads. */
        const val GAP_MS = 300L

        /** At most this many reads per pass (a full slate is ~15 games = 30 rosters: two passes). */
        const val MAX_READS_PER_PASS = 20

        const val USER_AGENT = "Vigilant (Android)"

        /** ESPN's site-API path for a league as CNO names it, or null for leagues without rosters here. */
        fun espnPath(league: String): String? = when (league.trim().uppercase()) {
            "NFL" -> "football/nfl"
            "NCAAF" -> "football/college-football"
            "MLB" -> "baseball/mlb"
            "NBA" -> "basketball/nba"
            "WNBA" -> "basketball/wnba"
            "NHL" -> "hockey/nhl"
            "NCAAB" -> "basketball/mens-college-basketball"
            "NCAAW", "NCAAWB" -> "basketball/womens-college-basketball"
            "MLS", "MLS (USA)" -> "soccer/usa.1"
            "EPL", "PREMIER LEAGUE", "ENGLISH PREMIER LEAGUE" -> "soccer/eng.1"
            else -> null
        }

        /** The games of [rows]' player bets, in list order, once each. */
        fun gamesOf(rows: List<CnoRow>): List<Game> =
            rows.filter { isPlayerBet(it) && espnPath(it.league) != null }.map { Game(it.league, it.event) }.distinct()

        /** A player prop (CNO's market names start "Player …"), not a team or game bet. */
        fun isPlayerBet(row: CnoRow): Boolean =
            row.market.trim().startsWith("Player", ignoreCase = true) && Picks.split(row.bet).first.isNotBlank()

        /** The player a bet is on: "Dalton Schultz Over 5.5" → "Dalton Schultz". */
        fun playerOf(row: CnoRow): String? = if (isPlayerBet(row)) Picks.split(row.bet).first else null

        /** "Houston Texans @ Indianapolis Colts" → both teams' names (also "A vs B", "A vs. B", "A at B"). */
        fun sides(event: String): List<String> =
            event.split(Regex("""\s+(?:@|vs\.?|at|v)\s+""", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotEmpty() }.take(2)

        private fun norm(s: String) = s.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), " ").trim()

        /**
         * The team a scanner's name means: its full name ("Houston Texans") first, then its
         * school or city alone ("Idaho State"), or its short name, when only one team has it.
         */
        fun findTeam(name: String, teams: List<EspnTeam>): EspnTeam? {
            val n = norm(name)
            if (n.isEmpty()) return null
            teams.firstOrNull { t -> t.names.firstOrNull()?.let(::norm) == n }?.let { return it }
            val any = teams.filter { t -> t.names.any { norm(it) == n } }
            if (any.size == 1) return any.single()
            val abbr = teams.filter { it.abbreviation.equals(name.trim(), ignoreCase = true) }
            return abbr.singleOrNull()
        }

        /** ESPN's `/teams` reply: `sports[0].leagues[0].teams[].team`. */
        fun parseTeams(root: JsonElement): List<EspnTeam> = runCatching {
            root.jsonObject["sports"]!!.jsonArray.flatMap { sport ->
                sport.jsonObject["leagues"]!!.jsonArray.flatMap { league ->
                    league.jsonObject["teams"]!!.jsonArray.mapNotNull { entry ->
                        val t = entry.jsonObject["team"]?.jsonObject ?: return@mapNotNull null
                        val id = t.str("id") ?: return@mapNotNull null
                        val abbr = t.str("abbreviation") ?: return@mapNotNull null
                        // The full name first: it's the one tried before the looser ones.
                        val names = listOfNotNull(t.str("displayName"), t.str("location"), t.str("shortDisplayName"), t.str("nickname")).distinct()
                        EspnTeam(id, abbr, names)
                    }
                }
            }
        }.getOrDefault(emptyList())

        /** ESPN's `/teams/{id}/roster` reply: `athletes` flat, or grouped by position (`athletes[].items`). */
        fun parseRoster(root: JsonElement): List<String> = runCatching {
            val athletes = root.jsonObject["athletes"] as? JsonArray ?: return@runCatching emptyList()
            athletes.flatMap { a ->
                val o = a.jsonObject
                val items = o["items"] as? JsonArray
                if (items != null) items.mapNotNull { (it as? JsonObject)?.str("displayName") ?: (it as? JsonObject)?.str("fullName") }
                else listOfNotNull(o.str("displayName") ?: o.str("fullName"))
            }.distinct()
        }.getOrDefault(emptyList())

        private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
