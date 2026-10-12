package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.JsonSplit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * BoltOdds messages (BOLTODDS_API.md §3) as Vigilant's models. Written from the docs' own examples and tolerant of what they do not pin down: a frame is one object OR an array of them (the docs' loop
 * iterates `for data in msg`), an empty object `{}` stands for "none" in the clear messages, a price may be a string or a number, and a price of `None`, `''`, null or anything that is not American odds
 * means "no odds available" (suspended, deleted). A message that cannot be read is dropped, never guessed at.
 */
object BoltParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Eastern time: BoltOdds' `info.when` and the game id's date and hour are US Eastern (BOLTODDS_API.md §3). */
    val EASTERN: ZoneId = ZoneId.of("America/New_York")

    private val WHEN_12H: DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd, hh:mm a").toFormatter(Locale.US)
    private val WHEN_24H: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm", Locale.US)

    /** The messages of one websocket frame: a single object or an array of them. Anything else is nothing. */
    fun frame(text: String, nowMs: Long): List<BoltMessage> {
        val root = try { json.parseToJsonElement(text) } catch (e: Exception) { return emptyList() }
        return when (root) {
            is JsonObject -> listOfNotNull(message(root, nowMs))
            is JsonArray -> root.mapNotNull { (it as? JsonObject)?.let { o -> message(o, nowMs) } }
            else -> emptyList()
        }
    }

    /** The games of a `/api/snapshot` reply (a JSON array of `initial_state` messages), one element at a time so a big answer is never one tree. Non-game elements are skipped. */
    fun snapshot(raw: String, nowMs: Long): List<BoltMessage.GameUpdate> {
        val parts = JsonSplit.elements(raw)
        val out = ArrayList<BoltMessage.GameUpdate>()
        if (parts != null) {
            for (i in 0 until parts.size) {
                val o = try { json.parseToJsonElement(parts.element(i)) as? JsonObject } catch (e: Exception) { null } ?: continue
                (message(o, nowMs, initial = true) as? BoltMessage.GameUpdate)?.let { out += it }
            }
            return out
        }
        val root = try { json.parseToJsonElement(raw) } catch (e: Exception) { return emptyList() }
        (root as? JsonArray)?.forEach { el -> ((el as? JsonObject)?.let { message(it, nowMs, initial = true) } as? BoltMessage.GameUpdate)?.let { out += it } }
        return out
    }

    fun message(o: JsonObject, nowMs: Long, initial: Boolean = false): BoltMessage? {
        val action = text(o["action"]) ?: return null
        val at = timestamp(text(o["timestamp"]), nowMs)
        val data = (o["data"] as? JsonObject)?.let { data(it, at) }
        return when (action) {
            "socket_connected" -> BoltMessage.SocketConnected
            "ping" -> BoltMessage.Ping
            "subscription_updated" -> BoltMessage.SubscriptionUpdated(text(o["message"]).orEmpty())
            "error" -> BoltMessage.Error(text(o["message"]) ?: text(o["detail"]) ?: "error")
            "game_update", "game_added", "initial_state" -> data?.takeIf { it.sportsbook != null && it.game != null }?.let { BoltMessage.GameUpdate(at, it, initial = initial || action == "initial_state") }
            "line_update" -> data?.takeIf { it.sportsbook != null && it.game != null }?.let { BoltMessage.LineUpdate(at, it) }
            "game_removed" -> data?.takeIf { it.sportsbook != null && it.game != null }?.let { BoltMessage.GameRemoved(at, it) }
            "sport_clear" -> data?.let { d -> if (d.sport != null && d.sportsbook != null) BoltMessage.SportClear(at, d.sport, d.sportsbook) else null }
            "book_clear" -> data?.sportsbook?.let { BoltMessage.BookClear(at, it) }
            else -> BoltMessage.Unknown(action)
        }
    }

    private fun data(d: JsonObject, at: Long): BoltData {
        val info = d["info"] as? JsonObject
        val outcomes = ArrayList<BoltOutcome>()
        val removed = ArrayList<String>()
        (d["outcomes"] as? JsonObject)?.forEach { (label, v) ->
            val o = v as? JsonObject ?: return@forEach
            val name = text(o["outcome_name"])
            val price = odds(o["odds"])
            if (name == null) return@forEach
            if (price == null) removed += label
            else outcomes += BoltOutcome(label, price, name, line(o["outcome_line"]), text(o["outcome_over_under"]), text(o["outcome_target"]), at)
        }
        return BoltData(
            sport = text(d["sport"]), sportsbook = text(d["sportsbook"]), game = text(d["game"]) ?: text(info?.get("game")),
            home = text(d["home_team"]), away = text(d["away_team"]), startMs = whenMs(text(info?.get("when"))),
            infoId = text(info?.get("id")), outcomes = outcomes, removed = removed,
        )
    }

    /** A JSON value as text: a string, or a number as printed; an object, an array and null are none ("{}" is how the clear messages say "nothing"). */
    private fun text(el: JsonElement?): String? {
        val p = el as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content.trim().takeIf { it.isNotEmpty() && it != "None" && it != "null" }
    }

    /** American odds from "-3333", "+900", "900" or a number; null for "", "None", null, 0 and anything inside +-99 (not a price). */
    fun odds(el: JsonElement?): Int? {
        val s = text(el) ?: return null
        val n = s.removePrefix("+").toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() } ?: return null
        val i = Math.round(n).toInt()
        return i.takeIf { it >= 100 || it <= -100 }
    }

    private fun line(el: JsonElement?): Double? = text(el)?.removePrefix("+")?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    /** The message's own UTC `timestamp` ("2025-07-23T19:46:10.023175+00:00"), else [nowMs]. */
    fun timestamp(s: String?, nowMs: Long): Long {
        if (s == null) return nowMs
        return try { OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (e: Exception) {
            try { java.time.Instant.parse(s).toEpochMilli() } catch (e2: Exception) { nowMs }
        }
    }

    /** `info.when` ("2025-07-23, 01:09 PM") as an instant, read as US Eastern time. */
    fun whenMs(s: String?): Long? {
        if (s == null) return null
        val t = s.trim()
        val local = try { LocalDateTime.parse(t, WHEN_12H) } catch (e: Exception) { try { LocalDateTime.parse(t, WHEN_24H) } catch (e2: Exception) { return null } }
        return local.atZone(EASTERN).toInstant().toEpochMilli()
    }
}
