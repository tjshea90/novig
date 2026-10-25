package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * How fresh PropLine's copy of each book is (`GET /v1/freshness`, free, no key; PSL, Tj 2026-10-25: "implement the bookmaker freshness endpoints and show in the app and engine how fresh the odds are
 * and filter out stale odds"). Per book, split into game lines and props (docs: "a book's prop board can go dark behind perfectly fresh game lines"): seconds since the last write, PropLine's own stale
 * flag, and the age of the oldest market still on the board. The engine reads it every minute; the table goes into `status.json` ([CloudStatus.propLineFresh]) for the phone, and a class PropLine has
 * written nothing for in [PropLineLive.OUTAGE_S] or more is taken out of that book's PropLine quotes ([PropLineLive.staleBooks]). Each quote keeps its own age check as well ([com.tjshea.vigilant.data.scanner.Freshness]).
 */
class PropLineFreshness(private val http: OkHttpClient, private val json: Json, private val url: String = URL) {
    @Volatile var last: List<Row> = emptyList()
        private set
    @Volatile var readAtMs: Long = 0L
        private set
    @Volatile var error: String? = null
        private set

    suspend fun refresh(now: Long = System.currentTimeMillis()): List<Row>? {
        val text = try {
            http.newCall(Request.Builder().url(url).get().build()).await().use { r -> if (!r.isSuccessful) { error = "HTTP ${r.code}"; return null }; r.body?.string().orEmpty() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message?.take(80) ?: e.javaClass.simpleName; return null
        }
        val rows = parse(text, json) ?: run { error = "unreadable answer"; return null }
        last = rows; readAtMs = now; error = null
        return rows
    }

    /** The stale map [PropLineLive] uses: only an outage (a class unwritten for [PropLineLive.OUTAGE_S] or more, and flagged stale by PropLine). */
    fun outages(rows: List<Row> = last): Map<String, Pair<Boolean, Boolean>> =
        rows.associate { r -> r.book to ((r.linesStale && (r.linesAgeS ?: 0) >= PropLineLive.OUTAGE_S) to (r.propsStale && (r.propsAgeS ?: 0) >= PropLineLive.OUTAGE_S)) }
            .filterValues { it.first || it.second }

    /** One book's freshness, in the app's book names. Ages in seconds; null = the class has no markets. */
    @Serializable
    data class Row(
        val book: String,
        val linesAgeS: Int? = null,
        val propsAgeS: Int? = null,
        val linesStale: Boolean = false,
        val propsStale: Boolean = false,
        /** The oldest market still on the active board, seconds (PropLine's `worst_staleness_seconds`). */
        val worstAgeS: Int? = null,
        val markets: Int = 0,
        val suspended: Int = 0,
    )

    companion object {
        const val URL = "https://api.prop-line.com/v1/freshness"
        const val EVERY_MS = 60_000L

        fun parse(text: String, json: Json): List<Row>? {
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            val books = root["bookmakers"] as? JsonArray ?: return null
            return books.mapNotNull { b ->
                val o = b as? JsonObject ?: return@mapNotNull null
                val key = (o["key"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val classes = o["market_classes"] as? JsonObject
                fun cls(name: String) = classes?.get(name) as? JsonObject
                fun age(c: JsonObject?) = (c?.get("staleness_seconds") as? JsonPrimitive)?.doubleOrNull?.toInt()
                fun stale(c: JsonObject?) = (c?.get("is_stale") as? JsonPrimitive)?.booleanOrNull == true
                val lines = cls("game_lines")
                val props = cls("props")
                Row(
                    book = PropLineClient.bookKey(key),
                    linesAgeS = if (classes == null) age(o) else age(lines),
                    propsAgeS = age(props),
                    linesStale = if (classes == null) stale(o) else stale(lines),
                    propsStale = stale(props),
                    worstAgeS = (o["worst_staleness_seconds"] as? JsonPrimitive)?.doubleOrNull?.toInt(),
                    markets = (o["market_count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                    suspended = (o["suspended_market_count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                )
            }
        }
    }
}
