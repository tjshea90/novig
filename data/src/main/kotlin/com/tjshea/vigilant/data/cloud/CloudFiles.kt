package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * What the phone tells the GitHub engine (Tj, 2026-10-14: "I just control the settings from the phone"). Written by the app to `settings.json` on the `cloud-config` branch whenever a setting changes
 * or the switch moves; read by the engine before every pass. [rev] only ever grows (the phone's clock in ms at the write), so the engine can say which settings a feed was priced under.
 * [on] false = the engine finishes its job and starts nothing (the phone is asleep and so is the cloud: no scan, no API call, no minute spent).
 */
@Serializable
data class CloudConfig(
    val rev: Long = 0L,
    val on: Boolean = false,
    val settings: ScanSettings = ScanSettings(),
    /** Which phone wrote it (a label for the logs). */
    val device: String = "",
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun parse(text: String): CloudConfig? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(c: CloudConfig): String = json.encodeToString(serializer(), c)
    }
}

/**
 * The engine's heartbeat (`status.json` on the `cloud-feed` branch, small so the phone can poll it every few seconds for free with an ETag). [computedAtMs] is when the newest feed's scan finished;
 * the phone fetches `feed.json.gz` only when it changes. [heartbeatMs] moves every pass even when nothing changed, so a dead engine is told from a quiet one.
 */
@Serializable
data class CloudStatus(
    val version: Int = 1,
    val engine: String = "",
    val runId: String = "",
    val computedAtMs: Long = 0L,
    val startedAtMs: Long = 0L,
    val heartbeatMs: Long = 0L,
    val scanMs: Long = 0L,
    val rows: Int = 0,
    val feedBytes: Int = 0,
    val configRev: Long = 0L,
    val sources: List<String> = emptyList(),
    val passes: Int = 0,
    val problem: String? = null,
    /** The engine is idle on purpose (the phone switched it off). */
    val idle: Boolean = false,
    /** When this job will hand over to the next (ms): the phone shows it, nothing depends on it. */
    val handoverAtMs: Long = 0L,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun parse(text: String): CloudStatus? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(s: CloudStatus): String = json.encodeToString(serializer(), s)
    }
}

/** One finished scan's list as the engine publishes it (`feed.json.gz`). */
class CloudFeed(val computedAtMs: Long, val configRev: Long, val stats: ScanStats, val rows: List<Opportunity>, val unreadable: Int = 0) {
    companion object {
        const val VERSION = 1

        fun encode(feed: CloudFeed): ByteArray {
            val j = buildJsonObject {
                put("v", VERSION); put("at", feed.computedAtMs); put("rev", feed.configRev)
                put("stats", buildJsonObject {
                    put("ne", feed.stats.novigEvents); put("me", feed.stats.matchedEvents); put("mp", feed.stats.marketsPriced)
                    put("ow", feed.stats.outcomesWithFair); put("pe", feed.stats.positiveEv); put("lg", feed.stats.laterGames)
                })
                put("rows", JsonArray(feed.rows.map(CloudCodec::encode)))
            }
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(Json.encodeToString(JsonObject.serializer(), j).toByteArray(Charsets.UTF_8)) }
            return out.toByteArray()
        }

        /** The feed in [gz], or null when the bytes are not a feed this version understands. */
        fun decode(gz: ByteArray): CloudFeed? = runCatching {
            val text = GZIPInputStream(gz.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            val j = Json.parseToJsonElement(text).jsonObject
            val s = j["stats"]!!.jsonObject
            val (rows, bad) = CloudCodec.decodeRows(j["rows"]!!.jsonArray)
            CloudFeed(
                j["at"]!!.jsonPrimitive.long, j["rev"]?.jsonPrimitive?.long ?: 0L,
                ScanStats(s["ne"]!!.jsonPrimitive.int, s["me"]!!.jsonPrimitive.int, s["mp"]!!.jsonPrimitive.int, s["ow"]!!.jsonPrimitive.int, s["pe"]!!.jsonPrimitive.int, s["lg"]?.jsonPrimitive?.int ?: 0),
                rows, bad,
            )
        }.getOrNull()
    }
}
