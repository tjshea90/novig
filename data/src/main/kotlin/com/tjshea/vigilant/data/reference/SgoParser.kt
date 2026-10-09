package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.match.PlayerNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import kotlin.math.abs

/** One book's price on one side of one SGO odd, main line or alternate: every value SGO sends as a string, read once. */
data class SgoLine(
    val american: Double?,
    val spread: Double?,
    val overUnder: Double?,
    val available: Boolean,
    val updatedMs: Long?,
    val main: Boolean,
) {
    /** The line number this price is on (spread or over/under); null for a moneyline. */
    val point: Double? get() = spread ?: overUnder
}

/** A book's open and close values for one odd (only with `includeOpenCloseOdds=true`). */
data class SgoOpenClose(val openOdds: Double?, val closeOdds: Double?, val openPoint: Double?, val closePoint: Double?)

/** One side of one SGO market ("odd"): who it is for, and every book's lines on it. */
data class SgoOdd(
    val oddId: String,
    val statId: String,
    val entityId: String,
    val periodId: String,
    val betType: String,
    val sideId: String,
    val opposingOddId: String?,
    val byBook: Map<String, List<SgoLine>>,
    val openClose: Map<String, SgoOpenClose>,
    /** The finished result for this side ("1"/"0" for a winner, the stat for an over/under), when SGO has graded the event. */
    val score: String?,
    val fairAmerican: Double?,
)

data class SgoPlayer(val id: String, val name: String)

/** One game as SGO sends it, with the parts Vigilant reads. */
data class SgoEvent(
    val eventId: String,
    val leagueId: String,
    val sportId: String,
    val startsMs: Long?,
    val home: String,
    val away: String,
    val homeAbbr: String,
    val awayAbbr: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val live: Boolean,
    val started: Boolean,
    val ended: Boolean,
    val finalized: Boolean,
    val cancelled: Boolean,
    val periodId: String,
    val displayShort: String,
    val odds: List<SgoOdd>,
    val players: Map<String, SgoPlayer>,
    /** `results.<periodID>.<statEntityID>.<statID>` as numbers: the score by period, and with `expandResults` every player's stats. */
    val results: Map<String, Map<String, Map<String, Double>>> = emptyMap(),
    /** The game is postponed or delayed (SGO's `status.delayed`). */
    val delayed: Boolean = false,
)

/** A page of events: the next cursor, and the plan's `notice` when it filtered something out. */
data class SgoPage(val events: List<SgoEvent>, val nextCursor: String?, val notice: String?)

/**
 * Reads SportsGameOdds' `/v2/events` answer (SPORTSGAMEODDS_API.md §3). Built from the OpenAPI schema and its published examples, and defensive about all of it: every field optional, each
 * event and each odd read on its own so one odd shape never costs the page (the rule PropLine's parser learned on Tj's phone).
 */
object SgoParser {
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    fun page(raw: String): SgoPage {
        val root = (runCatching { lenient.parseToJsonElement(raw) }.getOrNull() as? JsonObject) ?: return SgoPage(emptyList(), null, null)
        val data = root["data"] as? JsonArray ?: return SgoPage(emptyList(), root.str("nextCursor"), root.str("notice"))
        return SgoPage(data.mapNotNull { runCatching { event(it) }.getOrNull() }, root.str("nextCursor"), root.str("notice"))
    }

    fun event(e: JsonElement): SgoEvent? {
        val o = e as? JsonObject ?: return null
        val id = o.str("eventID") ?: return null
        val status = o.obj("status")
        val teams = o.obj("teams")
        val home = teams.obj("home")
        val away = teams.obj("away")
        val odds = o.obj("odds")?.values?.mapNotNull { runCatching { odd(it) }.getOrNull() }.orEmpty()
        val players = o.obj("players")?.entries?.mapNotNull { (k, v) ->
            val p = v as? JsonObject ?: return@mapNotNull null
            val name = p.str("name") ?: listOfNotNull(p.str("firstName"), p.str("lastName")).joinToString(" ").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            k to SgoPlayer(p.str("playerID") ?: k, name)
        }?.toMap().orEmpty()
        // A game with no teams can't be matched to anything.
        if (name(home).isBlank() || name(away).isBlank()) return null
        return SgoEvent(
            eventId = id,
            leagueId = o.str("leagueID").orEmpty(),
            sportId = o.str("sportID").orEmpty(),
            startsMs = ms(status.str("startsAt")),
            home = name(home), away = name(away),
            homeAbbr = home.obj("names").str("short").orEmpty(), awayAbbr = away.obj("names").str("short").orEmpty(),
            homeScore = home.num("score")?.toInt(), awayScore = away.num("score")?.toInt(),
            live = status.bool("live") == true, started = status.bool("started") == true, ended = status.bool("ended") == true,
            finalized = status.bool("finalized") == true, cancelled = status.bool("cancelled") == true,
            periodId = status.str("currentPeriodID").orEmpty(), displayShort = status.str("displayShort").orEmpty(),
            odds = odds, players = players, results = results(o.obj("results")), delayed = status.bool("delayed") == true,
        )
    }

    private fun results(r: JsonObject?): Map<String, Map<String, Map<String, Double>>> {
        if (r == null) return emptyMap()
        val out = LinkedHashMap<String, Map<String, Map<String, Double>>>()
        for ((period, v) in r) {
            val byEntity = v as? JsonObject ?: continue
            val m = LinkedHashMap<String, Map<String, Double>>()
            for ((entity, stats) in byEntity) {
                val so = stats as? JsonObject ?: continue
                val d = so.entries.mapNotNull { (k, x) -> (x as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.let { k to it } }.toMap()
                if (d.isNotEmpty()) m[entity] = d
            }
            if (m.isNotEmpty()) out[period] = m
        }
        return out
    }

    private fun name(team: JsonObject?): String {
        val n = team.obj("names")
        return n.str("long") ?: n.str("medium") ?: n.str("short") ?: ""
    }

    fun odd(e: JsonElement): SgoOdd? {
        val o = e as? JsonObject ?: return null
        val oddId = o.str("oddID") ?: return null
        val parts = oddId.split('-')
        // {statID}-{statEntityID}-{periodID}-{betTypeID}-{sideID}; a statID or entity may itself hold no '-', so the five parts are exact.
        val stat = o.str("statID") ?: parts.getOrNull(0).orEmpty()
        val entity = o.str("statEntityID") ?: parts.getOrNull(1).orEmpty()
        val period = o.str("periodID") ?: parts.getOrNull(2).orEmpty()
        val bet = o.str("betTypeID") ?: parts.getOrNull(3).orEmpty()
        val side = o.str("sideID") ?: parts.getOrNull(4).orEmpty()
        val byBook = LinkedHashMap<String, List<SgoLine>>()
        val openClose = LinkedHashMap<String, SgoOpenClose>()
        o.obj("byBookmaker")?.forEach { (book, v) ->
            val b = v as? JsonObject ?: return@forEach
            val lines = ArrayList<SgoLine>()
            lines += line(b, main = b.bool("isMainLine") ?: true)
            (b["altLines"] as? JsonArray)?.forEach { a -> (a as? JsonObject)?.let { lines += line(it, main = false) } }
            byBook[book] = lines
            if (b.containsKey("closeOdds") || b.containsKey("openOdds") || b.containsKey("closeSpread") || b.containsKey("closeOverUnder")) {
                openClose[book] = SgoOpenClose(
                    american(b.str("openOdds")), american(b.str("closeOdds")),
                    number(b.str("openSpread")) ?: number(b.str("openOverUnder")), number(b.str("closeSpread")) ?: number(b.str("closeOverUnder")),
                )
            }
        }
        return SgoOdd(oddId, stat, entity, period, bet, side, o.str("opposingOddID"), byBook, openClose, o.str("score"), american(o.str("fairOdds")))
    }

    private fun line(b: JsonObject, main: Boolean) = SgoLine(
        american = american(b.str("odds")), spread = number(b.str("spread")), overUnder = number(b.str("overUnder")),
        available = b.bool("available") != false, updatedMs = ms(b.str("lastUpdatedAt")), main = main,
    )

    /** "-110" / "+133" / "EVEN" / "100" -> the American price; null when it is not a price. */
    fun american(s: String?): Double? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (t.equals("even", true)) return 100.0
        val v = t.removePrefix("+").toDoubleOrNull() ?: return null
        return v.takeIf { it.isFinite() && abs(it) >= 100.0 }
    }

    /** American to decimal: +150 -> 2.5, -200 -> 1.5. */
    fun decimal(american: Double?): Double? = when {
        american == null -> null
        american >= 100.0 -> 1.0 + american / 100.0
        american <= -100.0 -> 1.0 + 100.0 / -american
        else -> null
    }

    /** "+1.5" / "-3" / "224.5" / "+0" -> the number. */
    fun number(s: String?): Double? = s?.trim()?.removePrefix("+")?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** An ISO instant ("2025-12-12T14:59:33.774Z") or epoch milliseconds, as milliseconds. */
    fun ms(s: String?): Long? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        t.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000L else it }
        return runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
    }

    /** SGO's player display name, comparable with Novig's ([PlayerNames]). */
    fun playerKey(name: String): String = PlayerNames.key(name)

    private fun JsonObject?.obj(k: String): JsonObject? = this?.get(k) as? JsonObject
    private fun JsonObject?.str(k: String): String? = (this?.get(k) as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull != null }?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject?.num(k: String): Double? = (this?.get(k) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    private fun JsonObject?.bool(k: String): Boolean? = (this?.get(k) as? JsonPrimitive)?.contentOrNull?.let { if (it == "true") true else if (it == "false") false else null }
}
