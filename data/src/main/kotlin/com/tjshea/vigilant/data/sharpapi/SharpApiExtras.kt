package com.tjshea.vigilant.data.sharpapi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** One book's closing price for one side (`/odds/closing`, SHARPAPI.md §3): the price at the moment the book turned the game live, with SharpAPI's power-devigged fair. Journaled for the CLV studies. */
@Serializable
data class SaClosing(
    val atMs: Long,
    val eventId: String,
    val league: String,
    val home: String,
    val away: String,
    val startMs: Long?,
    val book: String,
    val marketType: String,
    val selectionType: String,
    val selection: String,
    val player: String? = null,
    val line: Double? = null,
    val decimal: Double,
    val noVig: Double? = null,
    val closingProbability: Double? = null,
    val capturedAt: String? = null,
    val trigger: String? = null,
)

/** One row of SharpAPI's own +EV list at Novig (`/opportunities/ev?sportsbook=novig`): a cross-check of Vigilant's own list, journaled to be graded beside it. */
@Serializable
data class SaEv(
    val seenAtMs: Long,
    val eventId: String?,
    val league: String,
    val home: String?,
    val away: String?,
    val startMs: Long?,
    val marketType: String,
    val selection: String,
    val line: Double? = null,
    val player: String? = null,
    val decimal: Double,
    val evPercent: Double?,
    val fair: Double?,
    val sharpBook: String?,
    val crossRefs: Int?,
    val quality: String? = null,
    val warnings: List<String> = emptyList(),
    val oldestOddsAgeSeconds: Double? = null,
    val kellyPercent: Double? = null,
    val live: Boolean = false,
)

/** The reads beyond the odds store: closing lines, SharpAPI's +EV at Novig, the day's request count. */
object SharpApiExtras {
    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    /** Every captured closing price of [eventId] (Pinnacle's and every other book's), or empty when nothing was captured (not started, or older than SharpAPI's 48 h). */
    suspend fun closings(client: SharpApiClient, eventId: String, now: Long): List<SaClosing> = parseClosings(client.get("/odds/closing", listOf("event_id" to eventId)), now)

    fun parseClosings(body: JsonObject, now: Long): List<SaClosing> {
        val d = body["data"] as? JsonObject ?: return emptyList()
        val event = d.s("event_id") ?: return emptyList()
        val books = d["books"] as? JsonObject ?: return emptyList()
        val out = ArrayList<SaClosing>()
        for ((book, v) in books) {
            val b = v as? JsonObject ?: continue
            for (e in (b["odds"] as? JsonArray).orEmpty()) {
                val o = e as? JsonObject ?: continue
                val dec = o.d("odds_decimal")?.takeIf { it > 1.0 } ?: continue
                out += SaClosing(
                    now, event, d.s("league").orEmpty(), d.s("home_team").orEmpty(), d.s("away_team").orEmpty(), SaRow.time(d.s("event_start_time")), book,
                    o.s("market_type").orEmpty(), o.s("selection_type").orEmpty(), o.s("selection").orEmpty(), o.s("player_name"), o.d("line"), dec,
                    o.d("no_vig_probability"), o.d("closing_probability"), b.s("captured_at"), b.s("capture_trigger"),
                )
            }
        }
        return out
    }

    /** SharpAPI's +EV list at Novig, pregame, every page (200 a page, at most [maxPages]). */
    suspend fun evAtNovig(client: SharpApiClient, now: Long, maxPages: Int = 5): List<SaEv> {
        val out = ArrayList<SaEv>()
        var offset = 0
        repeat(maxPages) {
            val page = client.get("/opportunities/ev", listOf("sportsbook" to "novig", "is_live" to "false", "limit" to "200", "offset" to offset.toString()))
            val rows = parseEv(page, now)
            out += rows
            val more = ((page["pagination"] as? JsonObject)?.get("has_more") as? JsonPrimitive)?.booleanOrNull == true
            if (!more || rows.isEmpty()) return out
            offset += 200
        }
        return out
    }

    fun parseEv(body: JsonObject, now: Long): List<SaEv> = (body["data"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val dec = o.d("odds_decimal")?.takeIf { it > 1.0 } ?: return@mapNotNull null
        SaEv(
            now, o.s("event_id"), o.s("league").orEmpty(), o.s("home_team"), o.s("away_team"), SaRow.time(o.s("start_time")), o.s("market_type").orEmpty(),
            o.s("display_selection") ?: o.s("selection").orEmpty(), o.d("line"), o.s("player_name"), dec, o.d("ev_percentage"), o.d("fair_probability"), o.s("sharp_book"),
            (o["cross_ref_count"] as? JsonPrimitive)?.intOrNull, o.s("quality_tier"),
            (o["warnings"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }, o.d("oldest_odds_age_seconds"), o.d("kelly_percent"),
            (o["is_live"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }

    /** Requests made with the key so far today (UTC), from `/account/usage`; null when it did not answer. */
    suspend fun requestsToday(client: SharpApiClient): Int? = runCatching {
        ((client.usage()["data"] as? JsonObject)?.get("requests_today") as? JsonPrimitive)?.intOrNull
    }.getOrNull()
}
