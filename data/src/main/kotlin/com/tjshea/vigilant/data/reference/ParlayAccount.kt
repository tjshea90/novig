package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.keys.CreditHeaders
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

/**
 * Each ParlayAPI key's own account, read for free (Tj, 2026-09-30: "It allows the API key to tell the app how many credits I have left. Add
 * this to the app so the meter is accurate"). `GET /v1/usage` first ("Check your API usage and remaining credits", no credit cost): credits
 * used, left and total for the credits month, and that month's `period_start`/`period_end` (the 1st, UTC: checked with Tj's key; the
 * key check's `subscription.period_end_iso` is Stripe's billing date, not when the credits reset). When that can't be read,
 * `GET /v1/meta/api-key-check` (also free): the same counts plus `valid` and `reason` (`credit_exhausted`, `key_inactive`). What it says goes
 * straight into the meter ([UsageMeter.recordBalance]). Read when a scan starts, when Settings › API usage or Diagnostics opens, and when a
 * key is added; at most every [REFRESH_MS] a key unless asked.
 */
class ParlayAccount(
    private val http: OkHttpClient,
    private val keys: () -> List<String>,
    private val meter: UsageMeter,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val base: String = OddsFeed.PARLAY.base,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What one key's check said, for Diagnostics. */
    data class Check(
        val remaining: Int?, val limit: Int?, val used: Int?, val resetAtMs: Long?, val tier: String?, val valid: Boolean?, val reason: String?,
        /** When the credits month began (`period_start`), when the answer said. */
        val periodStartMs: Long? = null,
        /** Which endpoint answered: "usage" or "meta/api-key-check". */
        val source: String? = null,
    )

    private val checkedAt = HashMap<String, Long>()

    /** One day of ParlayAPI's own usage log (`day` is its UTC date, "2026-09-30"). */
    data class Day(val day: String, val credits: Int, val requests: Int)

    /** An endpoint's share of the window ("props:baseball_mlb"). */
    data class Endpoint(val endpoint: String, val credits: Int, val requests: Int)

    /**
     * Where the credits went (Tj, 2026-09-30, PARLAY_API.md §6.2): `GET /v1/meta/usage?days=30` (free), every key's added together. Only
     * its `daily_breakdown` and `top_endpoints` are read: its `credits_*` totals read 0 (broken on their side).
     */
    data class History(val days: List<Day>, val top: List<Endpoint>, val windowDays: Int, val readAtMs: Long) {
        val total: Int get() = days.sumOf { it.credits }
    }

    private val histories = HashMap<String, History>()
    private val historyState = kotlinx.coroutines.flow.MutableStateFlow<History?>(null)

    /** The keys' usage log, added together, as last read (null until one answers). */
    val history: kotlinx.coroutines.flow.StateFlow<History?> = historyState

    /** The last answer per key (this process), for Diagnostics. */
    @Volatile
    var last: Map<String, Check> = emptyMap()
        private set

    /** Reads every key's account ([force]: even one read in the last [REFRESH_MS]). Returns how many answered; never throws. */
    suspend fun refresh(force: Boolean = false): Int {
        var answered = 0
        for (key in keys()) {
            val now = clock()
            if (!force && (checkedAt[key]?.let { now - it < REFRESH_MS } == true)) continue
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
            // Where the credits went, day by day (free, same cadence).
            readHistory(key)?.let { h ->
                histories[key] = h
                historyState.value = combine(histories.filterKeys { it in keys() }.values)
            }
            val reason = check.reason?.lowercase().orEmpty()
            meter.recordBalance(
                QuotaPolicy.PARLAY, key, check.remaining, check.limit, check.used, check.resetAtMs, periodStartMs = check.periodStartMs,
                exhausted = check.valid == false && reason.contains("exhaust"),
                inactive = check.valid == false && (reason.contains("inactive") || reason.contains("invalid") || reason.contains("revoked")),
                note = check.tier?.let { "plan: $it" },
            )
        }
        return answered
    }

    private suspend fun readHistory(key: String): History? {
        val url = "$base/meta/usage".toHttpUrl().newBuilder().addQueryParameter("days", HISTORY_DAYS_ASKED.toString()).build()
        val reply = try {
            http.newCall(Request.Builder().url(url).get().header("X-API-Key", key).build()).awaitText()
        } catch (e: IOException) {
            return null
        }
        if (!reply.isSuccessful) return null
        return runCatching { parseHistory(json.parseToJsonElement(reply.body), clock()) }.getOrNull()
    }

    /** `/v1/usage`, else the key check (an invalid or spent key answers there with its reason). */
    private suspend fun read(key: String): Check? = get(key, "usage")?.copy(source = "usage") ?: get(key, "meta/api-key-check")?.copy(source = "meta/api-key-check")

    private suspend fun get(key: String, path: String): Check? {
        val url = "$base/$path".toHttpUrl()
        val reply = try {
            http.newCall(Request.Builder().url(url).get().header("X-API-Key", key).build()).awaitText()
        } catch (e: IOException) {
            return null
        }
        if (!reply.isSuccessful) return null
        val root = runCatching { json.parseToJsonElement(reply.body) }.getOrNull() as? JsonObject ?: return null
        return parse(root, CreditHeaders.read({ reply.headers[it] }, clock()), clock()).takeIf { it.remaining != null || it.valid != null }
    }

    /**
     * How many days back the best plan among the keys reaches for closing lines (ParlayAPI's /v1/meta/limits `historical_hours`: free 48
     * hours, Starter 7 days, Pro 30, Business 90), from the last check; Starter's 7 until a check says.
     */
    fun historyDays(): Int = last.values.mapNotNull { HISTORY_DAYS[it.tier?.lowercase()] }.maxOrNull() ?: 7

    companion object {
        /** Days of usage log asked for (the chart's width). */
        const val HISTORY_DAYS_ASKED = 30

        /** Several keys' logs as one: each day's and each endpoint's credits added up. */
        fun combine(all: Collection<History>): History? {
            if (all.isEmpty()) return null
            val days = all.flatMap { it.days }.groupBy { it.day }.map { (d, l) -> Day(d, l.sumOf { it.credits }, l.sumOf { it.requests }) }.sortedBy { it.day }
            val top = all.flatMap { it.top }.groupBy { it.endpoint }.map { (e, l) -> Endpoint(e, l.sumOf { it.credits }, l.sumOf { it.requests }) }
                .sortedByDescending { it.credits }
            return History(days, top, all.maxOf { it.windowDays }, all.maxOf { it.readAtMs })
        }

        /** A `/v1/meta/usage` answer's day-by-day log and top endpoints. */
        fun parseHistory(root: JsonElement, now: Long): History? {
            val o = root as? JsonObject ?: return null
            fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.toInt()
            fun JsonObject.text(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
            val days = (o["daily_breakdown"] as? JsonArray)?.mapNotNull { e ->
                val d = e as? JsonObject ?: return@mapNotNull null
                Day(d.text("day")?.take(10) ?: return@mapNotNull null, d.int("credits") ?: 0, d.int("requests") ?: 0)
            } ?: return null
            val top = (o["top_endpoints"] as? JsonArray)?.mapNotNull { e ->
                val d = e as? JsonObject ?: return@mapNotNull null
                Endpoint(d.text("endpoint") ?: return@mapNotNull null, d.int("credits") ?: 0, d.int("requests") ?: 0)
            }.orEmpty()
            return History(days.sortedBy { it.day }, top.sortedByDescending { it.credits }, o.int("window_days") ?: HISTORY_DAYS_ASKED, now)
        }

        private val HISTORY_DAYS = mapOf("free" to 2, "starter" to 7, "pro" to 30, "business" to 90, "enterprise" to 365, "scale" to 3650)

        /** A key's account is read again after this at the soonest, unless asked (free, and ParlayAPI has no per-second cap on paid plans). */
        const val REFRESH_MS = 60_000L

        private val REMAINING = listOf("credits_remaining", "remaining_credits", "credits_left", "credit_headroom", "headroom", "remaining")
        // credits_total first: the plan's month plus any credits granted on top (/v1/usage's plan.credits_per_month is the plan alone).
        private val LIMIT = listOf("credits_total", "credits_total_this_period", "monthly_credits", "credits_per_month", "credit_limit", "credits_limit", "monthly_limit", "allowance", "quota", "limit")
        private val USED = listOf("credits_used", "credits_used_this_period", "used_credits", "credits_used_this_month", "used_this_month", "used")
        // Never subscription.period_end_iso (the billing date): the credits reset on their own month's end.
        private val RESET = listOf("period_end", "reset_at", "resets_at", "credits_reset_at", "current_period_end", "next_reset", "reset")
        private val START = listOf("period_start", "current_period_start")
        private val TIER = listOf("tier", "plan", "plan_name")

        /** Every named value in [e], nested objects included, by lower-case name (the first one found wins). */
        private fun flatten(e: JsonElement, out: MutableMap<String, JsonPrimitive> = LinkedHashMap()): Map<String, JsonPrimitive> {
            when (e) {
                is JsonObject -> for ((k, v) in e) {
                    if (v is JsonPrimitive && v !is JsonNull) out.putIfAbsent(k.lowercase(), v) else flatten(v, out)
                }
                is JsonArray -> Unit // lists (a daily breakdown) aren't the account's totals
                else -> Unit
            }
            return out
        }

        private fun number(v: JsonPrimitive?): Double? = v?.content?.trim()?.toDoubleOrNull()

        /** An ISO time or epoch seconds/milliseconds, only when it's in the future. */
        private fun time(v: JsonPrimitive?, now: Long): Long? = instant(v)?.takeIf { it > now }

        private fun instant(v: JsonPrimitive?): Long? {
            val s = v?.content?.trim() ?: return null
            val ms = s.toDoubleOrNull()?.toLong()?.let { if (it > 100_000_000_000L) it else it * 1000 }
                ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
                ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            return ms
        }

        /** A /v1/usage or api-key-check answer (and its reply's headers) to the key's figures; field names read loosely, the headers win for what they carry. */
        fun parse(root: JsonElement, headers: CreditHeaders, now: Long): Check {
            val f = flatten(root)
            fun first(names: List<String>) = names.firstNotNullOfOrNull { f[it] }
            val remaining = headers.remaining ?: number(first(REMAINING))?.toInt()
            // "limit" alone can be a per-second cap: only a monthly-sized number is the allowance.
            val limit = number(first(LIMIT))?.toInt()?.takeIf { it >= 1_000 }
            val used = number(first(USED))?.toInt()
            val reset = headers.resetAtMs ?: time(first(RESET), now)
            val valid = f["valid"]?.content?.toBooleanStrictOrNull()
            val start = instant(first(START))?.takeIf { s -> s <= now && (reset == null || s < reset) }
            return Check(remaining, limit, used, reset, first(TIER)?.content, valid, f["reason"]?.content, start)
        }
    }
}
