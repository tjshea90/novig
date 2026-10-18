package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Each PinnWire key's own account, read for free (Tj, 2026-10-18, TCL119: "make sure the API usage for each spent key is truly ran out instead of guessing by the apps own request count").
 * PinnWire's docs (llms-full.txt, `GET /v1/me`): "Authenticated account/dashboard read that does not consume REST quota ... plan limits, recent usage". Its markets answers carry no usage headers, so
 * until now the meter COUNTED the app's own requests against an assumed 100 a day and rested a key at 100 even when PinnWire had used fewer (another device, the website, a key shared with another
 * tool) or more. What `/v1/me` says goes straight into the meter ([UsageMeter.recordBalance]); a key is spent only when PinnWire says so (this read, or a 429 with `window: day`), never from our
 * own count ([QuotaPolicy.ownCountIsGuess]).
 *
 * The exact field names of `/v1/me` have not been seen on a live key (the docs give no sample): [parse] reads the numbers loosely by name (a remaining/left figure, else a daily limit minus a daily
 * used figure) and keeps every number it saw in [Check.figures] so Settings › APIs › Test key and Diagnostics show what the answer held. If it finds no usable figure the key stays "unknown" (usable, with
 * 429 as the only stop) rather than guessed.
 */
class PinnWireAccount(
    private val http: OkHttpClient,
    private val keys: () -> List<String>,
    private val meter: UsageMeter,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val base: String = BASE,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Check(val remaining: Int?, val limit: Int?, val used: Int?, val valid: Boolean?, val figures: Map<String, Double>, val readAtMs: Long = 0L)

    private val checkedAt = HashMap<String, Long>()

    @Volatile
    var last: Map<String, Check> = emptyMap()
        private set

    /** Reads every key's account ([force]: even one read in the last [minAgeMs]). Returns how many answered; never throws. */
    suspend fun refresh(force: Boolean = false, minAgeMs: Long = REFRESH_MS): Int {
        var answered = 0
        for (key in keys()) {
            val now = clock()
            if (!force && checkedAt[key]?.let { now - it < minAgeMs } == true) continue
            checkedAt[key] = now
            val check = try {
                read(key)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            answered++
            last = last + (key to check)
            record(key, check)
        }
        return answered
    }

    /** One key now (the Test key button); the check, or null when PinnWire could not be read. */
    suspend fun readOne(key: String): Check? = runCatching { read(key) }.getOrNull()?.also { last = last + (key to it); record(key, it) }

    private suspend fun record(key: String, check: Check) {
        if (check.valid == false) { meter.recordBalance(QuotaPolicy.PINNWIRE, key, null, inactive = true); return }
        if (check.remaining != null) meter.recordBalance(QuotaPolicy.PINNWIRE, key, check.remaining, check.limit, check.used, exhausted = check.remaining <= 0, note = null)
    }

    private suspend fun read(key: String): Check? {
        val reply = try {
            http.newCall(Request.Builder().url("$base/me").get().header("x-api-key", key).build()).awaitText()
        } catch (e: IOException) {
            return null
        }
        if (reply.code == 401 || reply.code == 403) return Check(null, null, null, false, emptyMap(), clock())
        if (!reply.isSuccessful) return null
        val root = runCatching { json.parseToJsonElement(reply.body) }.getOrNull() as? JsonObject ?: return null
        return parse(root, clock())
    }

    companion object {
        const val BASE = "https://pinnwire.com/v1"
        const val REFRESH_MS = 10 * 60_000L

        /** Every number in the answer by its dotted path, the account's e-mail and other text left out (this goes into Diagnostics and a public repo). */
        fun figures(root: JsonElement): Map<String, Double> {
            val out = LinkedHashMap<String, Double>()
            fun walk(path: String, e: JsonElement, depth: Int) {
                if (depth > 6 || out.size >= 60) return
                when (e) {
                    is JsonObject -> e.forEach { (k, v) -> walk(if (path.isEmpty()) k else "$path.$k", v, depth + 1) }
                    is JsonArray -> e.take(3).forEachIndexed { i, v -> walk("$path[$i]", v, depth + 1) }
                    is JsonPrimitive -> if (!e.isString) e.doubleOrNull?.let { out[path] = it }
                    else -> {}
                }
            }
            walk("", root, 0)
            return out
        }

        /** The day's remaining, limit and used out of whatever [figures] the answer held; null fields when it did not say. */
        fun parse(root: JsonObject, now: Long): Check {
            val f = figures(root)
            fun named(vararg words: String, within: (String) -> Boolean = { true }) =
                f.entries.firstOrNull { (k, _) -> val l = k.lowercase(); within(l) && words.any { l.endsWith(it) || l.contains(".$it") || l.contains("_$it") } }?.value?.toInt()
            // Only the daily REST figures: the per-minute limit and the WebSocket/SSE fields are other allowances.
            val daily = { p: String -> !p.contains("minute") && !p.contains("per_min") && !p.contains("ws") && !p.contains("sse") && !p.contains("referral") && !p.contains("payment") }
            val remaining = named("remaining", "left", within = daily)
            val limit = named("limit", "per_day", "daily_limit", "quota", "day", within = { p -> daily(p) && !p.contains("used") && !p.contains("remaining") })
            val used = named("used", "today", "requests_today", "count", within = { p -> daily(p) && !p.contains("limit") && !p.contains("remaining") && (p.contains("usage") || p.contains("today") || p.contains("used") || p.contains("day")) })
            val left = remaining ?: if (limit != null && used != null) (limit - used).coerceAtLeast(0) else null
            return Check(left, limit ?: if (remaining != null && used != null) remaining + used else null, used, null, f, now)
        }
    }
}
