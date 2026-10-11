package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.JsonSplit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** One sportsbook's price for one side of an Unabated group (UNABATED_API.md §2). Alternates are separate [UnLine]s with the same [sourceId]. */
data class UnLine(val sourceId: Long, val points: Double?, val price: Int, val modifiedMs: Long?, val alternate: Boolean)

data class UnSide(val sideIndex: Int, val sideName: String, val teamId: Long?, val personId: Long?, val lines: List<UnLine>)

/** One market group of the board: an event's bet type in one period, pregame or live. */
data class UnGroup(
    val eventId: Long,
    val live: Boolean,
    val startMs: Long?,
    val statusId: Int,
    val betTypeId: Int,
    val eventName: String,
    /** The `periodTypes` key ("game"…) and its id. */
    val periodKey: String,
    val periodTypeId: Int?,
    /** eventTeams by key "0" (away) and "1" (home): their team ids. */
    val awayTeamId: Long?,
    val homeTeamId: Long?,
    val sides: List<UnSide>,
)

data class UnBoard(val groups: List<UnGroup>, val lastUpdatedMs: Long?)

data class UnBetType(val id: Int, val name: String, val description: String, val hasPoints: Boolean?)
data class UnSource(val id: Long, val name: String, val active: Boolean, val statusId: Int?)
data class UnLeague(val id: Long, val name: String, val fullName: String)
data class UnPeriod(val id: Int, val name: String, val abbreviation: String)

/** The error an Unabated answer carried: `success:false`, a plain `message`, or a framework validation body. */
class UnabatedFailure(message: String) : ReferenceException(message)

/**
 * Reads Unabated's answers (UNABATED_API.md §2). Written from the docs and the OpenAPI examples, defensive about all of it: every field optional, an unreadable group is skipped (never the whole
 * board), and the board is walked one level at a time with [JsonSplit.members] so only ONE group is ever a tree (a props board is megabytes; a tree costs eight times that in heap).
 */
object UnabatedParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonObject.str(k: String): String? = this[k].prim()?.takeIf { it.isString || it.contentOrNull != null }?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject.lng(k: String): Long? = this[k].prim()?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.int(k: String): Int? = lng(k)?.toInt()
    private fun JsonObject.dbl(k: String): Double? = this[k].prim()?.doubleOrNull
    private fun JsonObject.bool(k: String): Boolean? = this[k].prim()?.booleanOrNull

    /** Epoch ms of an ISO-8601 timestamp, or null. */
    fun iso(s: String?): Long? = s?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    /**
     * Throws [UnabatedFailure] when [raw] is `success:false` or an error body; otherwise the root object. The docs: "HTTP success with success:false = the operation could not complete: read messages;
     * do not assume data exists".
     */
    fun envelope(raw: String): JsonObject {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull().obj() ?: throw UnabatedFailure("Unabated sent a reply Vigilant couldn't read")
        val ok = root.bool("success")
        if (ok == false) {
            val msgs = (root["messages"] as? JsonArray).orEmpty().mapNotNull { it.prim()?.contentOrNull }.filter { it.isNotBlank() }
            throw UnabatedFailure("Unabated: ${msgs.joinToString("; ").ifEmpty { "the request could not complete" }}")
        }
        if (ok == null && root["data"] == null) throw UnabatedFailure("Unabated: ${root.str("message") ?: "unexpected reply"}")
        return root
    }

    private fun rows(raw: String): List<JsonObject> = ((envelope(raw)["data"]) as? JsonArray).orEmpty().mapNotNull { it.obj() }

    fun leagues(raw: String): List<UnLeague> = rows(raw).mapNotNull { r -> r.lng("id")?.let { UnLeague(it, r.str("name").orEmpty(), r.str("fullName").orEmpty()) } }

    fun betTypes(raw: String): List<UnBetType> = rows(raw).mapNotNull { r ->
        val id = r.int("id") ?: r.int("betTypeId") ?: return@mapNotNull null
        UnBetType(id, r.str("name").orEmpty(), r.str("description").orEmpty(), r.bool("hasPoints"))
    }

    fun periods(raw: String): List<UnPeriod> = rows(raw).mapNotNull { r -> r.int("id")?.let { UnPeriod(it, r.str("name").orEmpty(), r.str("abbreviation").orEmpty()) } }

    fun sources(raw: String): List<UnSource> = rows(raw).mapNotNull { r ->
        r.lng("id")?.let { UnSource(it, r.str("name").orEmpty(), r.bool("isActive") ?: true, r.int("statusId")) }
    }

    /** `/market/{league}/{type}/teams`: team id -> name (a dictionary keyed by id). */
    fun teams(raw: String): Map<Long, String> = dictionary(raw) { o -> o.str("name") }

    /** `/market/{league}/props/people`: person id -> "First Last" (preferred name when sent). */
    fun people(raw: String): Map<Long, String> = dictionary(raw) { o ->
        val first = o.str("preferredName")?.takeIf { it.isNotBlank() } ?: o.str("firstName").orEmpty()
        listOf(first, o.str("lastName").orEmpty()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    }

    private fun dictionary(raw: String, name: (JsonObject) -> String?): Map<Long, String> {
        val data = envelope(raw)["data"]
        val out = HashMap<Long, String>()
        when (data) {
            is JsonObject -> data.forEach { (k, v) -> val o = v.obj() ?: return@forEach; val id = k.toLongOrNull() ?: o.lng("id") ?: return@forEach; name(o)?.let { out[id] = it } }
            is JsonArray -> data.forEach { v -> val o = v.obj() ?: return@forEach; val id = o.lng("id") ?: return@forEach; name(o)?.let { out[id] = it } }
            else -> {}
        }
        return out
    }

    /**
     * The odds board: `data.odds` -> league -> `periodTypes` -> period -> `pregame` / `live` -> group. [includeLive] keeps the live groups too. A line that is not `statusId` 1 (available), is
     * `disabled`, or has no price is left out; its alternates are separate [UnLine]s.
     */
    fun board(raw: String, includeLive: Boolean): UnBoard {
        envelope(raw)   // throws on success:false
        val top = JsonSplit.members(raw) ?: return UnBoard(emptyList(), null)
        val dataText = top.firstOrNull { it.first == "data" }?.second ?: return UnBoard(emptyList(), null)
        val data = JsonSplit.members(dataText) ?: return UnBoard(emptyList(), null)
        val last = data.firstOrNull { it.first == "lastUpdated" }?.second?.trim()?.toDoubleOrNull()?.toLong()?.let { if (it in 1..9_999_999_999L) it * 1000L else it }
        val out = ArrayList<UnGroup>()
        val odds = data.firstOrNull { it.first == "odds" }?.second?.let { JsonSplit.members(it) }.orEmpty()
        for ((_, leagueText) in odds) {
            val periodTypes = JsonSplit.members(leagueText)?.firstOrNull { it.first == "periodTypes" }?.second?.let { JsonSplit.members(it) }.orEmpty()
            for ((periodKey, periodText) in periodTypes) {
                val period = JsonSplit.members(periodText).orEmpty()
                val periodId = period.firstOrNull { it.first == "periodTypeId" }?.second?.trim()?.toIntOrNull()
                for ((phase, phaseText) in period) {
                    val live = phase == "live"
                    if ((phase != "pregame" && !live) || (live && !includeLive)) continue
                    for ((_, groupText) in JsonSplit.members(phaseText).orEmpty()) {
                        val o = runCatching { json.parseToJsonElement(groupText) }.getOrNull().obj() ?: continue
                        group(o, live, periodKey, periodId)?.let { out += it }
                    }
                }
            }
        }
        return UnBoard(out, last)
    }

    private fun group(o: JsonObject, live: Boolean, periodKey: String, periodId: Int?): UnGroup? {
        val eventId = o.lng("eventId") ?: return null
        val teams = o["eventTeams"].obj()
        val sides = (o["sides"].obj() ?: return null).values.mapNotNull { side(it.obj() ?: return@mapNotNull null) }
        if (sides.isEmpty()) return null
        return UnGroup(
            eventId, o.bool("isLive") ?: live, iso(o.str("eventStart")), o.int("statusId") ?: 1, o.int("betTypeId") ?: return null, o.str("eventName").orEmpty(), periodKey, periodId,
            teams?.get("0").obj()?.lng("teamId"), teams?.get("1").obj()?.lng("teamId"), sides,
        )
    }

    private fun side(o: JsonObject): UnSide? {
        val lines = ArrayList<UnLine>()
        (o["marketSourceLines"].obj() ?: return null).values.forEach { v ->
            val l = v.obj() ?: return@forEach
            val source = l.lng("marketSourceId") ?: return@forEach
            val modified = iso(l.str("modifiedOn"))
            val ok = (l.int("statusId") ?: 1) == 1 && l.bool("disabled") != true
            val price = l.int("price")
            if (ok && price != null && price != 0) lines += UnLine(source, l.dbl("points"), price, modified, alternate = false)
            (l["alternateLines"] as? JsonArray).orEmpty().forEach { a ->
                val x = a.obj() ?: return@forEach
                val p = x.int("price") ?: return@forEach
                if ((x.int("statusId") ?: 1) != 1 || p == 0) return@forEach
                lines += UnLine(x.lng("marketSourceId") ?: source, x.dbl("points"), p, iso(x.str("modifiedOn")) ?: modified, alternate = true)
            }
        }
        return UnSide(o.int("sideIndex") ?: return null, o.str("sideName").orEmpty(), o.lng("teamId"), o.lng("personId"), lines)
    }
}
