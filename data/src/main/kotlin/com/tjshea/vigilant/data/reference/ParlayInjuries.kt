package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime

/** How much a player's injury status matters to a bet on him: [RED] he's not playing, [AMBER] he may not. */
enum class InjuryLevel { NONE, AMBER, RED }

/**
 * One player's injury report as ParlayAPI relays ESPN's (Tj, 2026-09-30, PARLAY_API.md §6.1): the `injury` object on every `/props` row,
 * or a row of `/v1/sports/{s}/injuries`. [comment] is ESPN's note ("Haulcy (ankle) was a full participant in Thursday's practice.").
 */
data class Injury(
    val player: String,
    val status: String,
    val team: String? = null,
    val teamAbbr: String? = null,
    val position: String? = null,
    val bodyPart: String? = null,
    val side: String? = null,
    val detail: String? = null,
    val comment: String? = null,
    val expectedReturn: String? = null,
    val reportedAtMs: Long? = null,
) {
    val level: InjuryLevel get() = levelOf(status)

    /** The tag a card shows ("OUT", "IR", "DOUBTFUL", "QUESTIONABLE"), or null for a player who's active. */
    val tag: String? get() = tagOf(status)

    /** Everything known, for the tap: "Doubtful · Right Hamstring (Strain) · back 2026-10-04 · <ESPN's note>". */
    val details: String
        get() = buildList {
            add(status)
            listOfNotNull(side, bodyPart).joinToString(" ").takeIf { it.isNotBlank() }?.let { add(it + (detail?.let { d -> " ($d)" } ?: "")) }
            expectedReturn?.let { add("expected back $it") }
            // "doubtful" as the whole note says nothing the status doesn't.
            comment?.takeIf { !it.trim().trimEnd('.').equals(status, ignoreCase = true) }?.let { add(it) }
        }.joinToString(" · ")

    companion object {
        /** A status to how much it matters. Unknown ones that aren't "Active" read as amber: better a tag to check than none. */
        fun levelOf(status: String): InjuryLevel {
            val s = status.trim().lowercase()
            return when {
                s.isEmpty() || s == "active" || s == "healthy" -> InjuryLevel.NONE
                "injured reserve" in s || OUT.containsMatchIn(s) || IL.containsMatchIn(s) || "suspen" in s || "inactive" in s ||
                    "physically unable" in s || s == "pup" || "non-football" in s -> InjuryLevel.RED
                else -> InjuryLevel.AMBER
            }
        }

        fun tagOf(status: String): String? {
            val s = status.trim().lowercase()
            return when (levelOf(status)) {
                InjuryLevel.NONE -> null
                else -> when {
                    "injured reserve" in s -> "IR"
                    IL.containsMatchIn(s) -> "IL"
                    "suspen" in s -> "SUSPENDED"
                    OUT.containsMatchIn(s) || "inactive" in s -> "OUT"
                    "doubtful" in s -> "DOUBTFUL"
                    "questionable" in s -> "QUESTIONABLE"
                    "day-to-day" in s || "day to day" in s -> "DAY-TO-DAY"
                    else -> status.trim().uppercase().take(14)
                }
            }
        }

        private val OUT = Regex("\\bout\\b")

        /** MLB's injured lists: "10-Day IL", "60-Day-IL", "7-Day IL". */
        private val IL = Regex("\\bil\\b|injured list")
    }
}

/**
 * Every injury report seen lately, by sport and player ([InjuryIndex.book]'s value): immutable, so a list's tags are read from one
 * consistent picture. [find] matches a player's name as [PlayerNames.same] does, and his team against the game's when it's known.
 */
class InjuryBook internal constructor(internal val sports: Map<String, Sport>) {
    internal class Entry(val injury: Injury, val seenAtMs: Long)

    internal class Sport(
        val byKey: Map<String, List<Entry>>,
        /** Players listed by the last word of their name, for the looser [PlayerNames.same] match. */
        val bySurname: Map<String, List<Entry>>,
        /** Players asked /injuries about who weren't in its answer, and when: nothing to report (healthy, or not listed). */
        val absent: Map<String, Long>,
    )

    /** How many players' reports are kept (Diagnostics). */
    val size: Int get() = sports.values.sumOf { s -> s.byKey.values.sumOf { it.size } }

    /**
     * [player]'s report in [sportKey], seen within [InjuryIndex.KEEP_MS] of [now]. With [teams] (the game's two teams, or his own), a
     * report naming another team is someone else of the same name (two Josh Allens); one naming no team is taken.
     */
    fun find(sportKey: String, player: String, teams: List<String> = emptyList(), now: Long): Injury? {
        val sport = sports[sportKey] ?: return null
        val key = PlayerNames.key(player)
        val fresh = { e: Entry -> now - e.seenAtMs <= InjuryIndex.KEEP_MS }
        val exact = sport.byKey[key].orEmpty().filter(fresh)
        val named = exact.ifEmpty {
            sport.bySurname[surname(key)].orEmpty().filter { fresh(it) && PlayerNames.same(it.injury.player, player) }
        }
        if (named.isEmpty()) return null
        if (teams.isEmpty()) return named.maxByOrNull { it.seenAtMs }?.injury
        val onTeam = named.filter { e -> e.injury.onAnyOf(teams) }
        return (onTeam.ifEmpty { named.filter { it.injury.team == null && it.injury.teamAbbr == null } }).maxByOrNull { it.seenAtMs }?.injury
    }

    /** Whether [player] needs no /injuries read: a report was seen, or /injuries was asked and didn't list him, within [InjuryIndex.KEEP_MS]. */
    fun covers(sportKey: String, player: String, now: Long): Boolean {
        val sport = sports[sportKey] ?: return false
        val key = PlayerNames.key(player)
        if (sport.absent[key]?.let { now - it <= InjuryIndex.KEEP_MS } == true) return true
        return find(sportKey, player, emptyList(), now) != null
    }

    companion object {
        val EMPTY = InjuryBook(emptyMap())

        internal fun surname(key: String): String = key.substringAfterLast(' ')

        /**
         * Whether this report's team is one of [teams]: the same abbreviation, or every word of the shorter full name in the other (never
         * a half match: "Los Angeles Rams" isn't the Chargers, "Boston Red Sox" isn't the White Sox).
         */
        private fun Injury.onAnyOf(teams: List<String>): Boolean {
            if (team == null && teamAbbr == null) return false
            return teams.any { t ->
                teamAbbr?.equals(t.trim(), ignoreCase = true) == true || team?.equals(t.trim(), ignoreCase = true) == true ||
                    (team != null && TeamMatcher.similarity(team, t) >= 0.999)
            }
        }
    }
}

/**
 * The players' injury reports Vigilant has seen (PARLAY_API.md §6.1): filled for free from every `/props` answer ([ParlayProps.parse]
 * keeps each row's `injury`), and from `/v1/sports/{s}/injuries` ([ParlayInjuries]) for listed or open prop bets whose players no props
 * answer covered. Thread-safe; [book] is the current picture for the screens.
 */
class InjuryIndex(private val clock: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow(InjuryBook.EMPTY)
    val book: StateFlow<InjuryBook> = state.asStateFlow()

    /**
     * [injuries] seen in [sportKey] now. [asked]: the players an /injuries read was for; those it didn't list are marked as having nothing
     * to report, so they aren't asked about again for [KEEP_MS].
     */
    fun record(sportKey: String, injuries: Collection<Injury>, asked: Collection<String> = emptyList()) {
        if (injuries.isEmpty() && asked.isEmpty()) return
        val now = clock()
        state.update { old ->
            val sports = HashMap(old.sports)
            val prev = sports[sportKey]
            val byKey = HashMap<String, List<InjuryBook.Entry>>()
            // Old reports kept while fresh; a newer report of the same player (and team) replaces his.
            prev?.byKey?.forEach { (k, list) -> list.filter { now - it.seenAtMs <= KEEP_MS }.takeIf { it.isNotEmpty() }?.let { byKey[k] = it } }
            for (inj in injuries) {
                val k = PlayerNames.key(inj.player)
                if (k.isBlank()) continue
                val others = byKey[k].orEmpty().filter { !sameTeam(it.injury, inj) }
                byKey[k] = others + InjuryBook.Entry(inj, now)
            }
            val absent = HashMap(prev?.absent.orEmpty().filterValues { now - it <= KEEP_MS })
            for (p in asked) {
                val k = PlayerNames.key(p)
                if (k.isNotBlank() && k !in byKey) absent[k] = now
            }
            val bySurname = byKey.values.flatten().groupBy { InjuryBook.surname(PlayerNames.key(it.injury.player)) }
            sports[sportKey] = InjuryBook.Sport(byKey, bySurname, absent)
            InjuryBook(sports)
        }
    }

    private fun sameTeam(a: Injury, b: Injury): Boolean =
        (a.teamAbbr ?: a.team).equals(b.teamAbbr ?: b.team, ignoreCase = true)

    companion object {
        /** A report older than this isn't shown (statuses change on practice days; every scan and /injuries read renews them). */
        const val KEEP_MS = 6 * 3_600_000L
    }
}

/**
 * `GET /v1/sports/{s}/injuries` (1 credit a league; PARLAY_API.md §6.1): ESPN's injury list for the five sports ParlayAPI covers, refreshed
 * by them about every 10 minutes, so read at most every [REUSE_MS] a sport (a failed read too). Asked only for listed or open prop bets
 * whose players no recent `/props` answer covered ([InjuryIndex.book]'s `covers`), and only while ParlayAPI is on with a key ([active]).
 */
class ParlayInjuries(
    private val client: TheOddsApiClient,
    private val index: InjuryIndex,
    private val json: Json,
    private val active: suspend () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val readAt = HashMap<String, Long>()
    private val mutex = Mutex()

    /** Calls made (tests, Diagnostics). */
    @Volatile
    var requests: Int = 0
        private set

    /** Reads [sportKey]'s list when [players] aren't covered, one read per [REUSE_MS]. True when a read answered. */
    suspend fun fill(sportKey: String, players: Collection<String>): Boolean {
        if (sportKey !in SPORTS || players.isEmpty() || !active()) return false
        val now = clock()
        mutex.withLock {
            if (readAt[sportKey]?.let { now - it < REUSE_MS } == true) return false
            readAt[sportKey] = now
        }
        requests++
        val reply = try {
            client.parlayGet("/sports/$sportKey/injuries", emptyList(), cost = COST, what = "$sportKey injuries").value
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return false // no key, credits held back, or no answer: tried again after REUSE_MS
        }
        if (!reply.ok) return false
        val list = runCatching { parse(reply.body, json, sportKey) }.getOrNull() ?: return false
        index.record(sportKey, list, asked = players)
        return true
    }

    companion object {
        const val COST = 1

        /** A sport's list is read again after this at the soonest. */
        const val REUSE_MS = 10 * 60_000L

        /** The sports /injuries serves (others answer 400). */
        val SPORTS = setOf("baseball_mlb", "basketball_nba", "basketball_wnba", "icehockey_nhl", "americanfootball_nfl")

        private fun JsonElement?.obj() = this as? JsonObject
        internal fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }

        internal fun time(s: String?): Long? = s?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                // ESPN's "2026-09-29T16:45Z" (no seconds).
                ?: runCatching { Instant.parse(it.replace(Regex("T(\\d\\d:\\d\\d)Z$"), "T$1:00Z")).toEpochMilli() }.getOrNull()
        }

        /** An /injuries answer's `results` into reports. */
        fun parse(body: String, json: Json, sportKey: String): List<Injury> {
            val root = json.parseToJsonElement(body)
            val rows = ((root.obj()?.let { it["results"] ?: it["injuries"] ?: it["data"] } ?: root) as? JsonArray).orEmpty()
            return rows.mapNotNull { r ->
                val o = r.obj() ?: return@mapNotNull null
                if (o.str("sport_key")?.let { it != sportKey } == true) return@mapNotNull null
                val player = o.str("athlete_name") ?: o.str("player") ?: return@mapNotNull null
                val status = o.str("status") ?: return@mapNotNull null
                Injury(
                    player = player, status = status, team = o.str("team"), teamAbbr = o.str("team_abbr"), position = o.str("position"),
                    bodyPart = o.str("body_part"), side = o.str("side"), detail = o.str("detail"),
                    comment = o.str("short_comment") ?: o.str("description"), expectedReturn = o.str("expected_return"),
                    reportedAtMs = time(o.str("date_reported") ?: o.str("date")),
                )
            }
        }

        /** A `/props` row's `injury` object (null when the row has none) for [player]. */
        fun fromPropsRow(row: JsonObject, player: String): Injury? {
            val o = row["injury"].obj() ?: return null
            val status = o.str("status") ?: return null
            return Injury(
                player = player, status = status, team = o.str("team"), teamAbbr = o.str("team_abbr"), position = o.str("position"),
                bodyPart = o.str("body_part"), side = o.str("side"), detail = o.str("detail"),
                comment = o.str("short_comment") ?: o.str("description"), expectedReturn = o.str("expected_return"),
                reportedAtMs = time(o.str("date") ?: o.str("date_reported")),
            )
        }
    }
}
