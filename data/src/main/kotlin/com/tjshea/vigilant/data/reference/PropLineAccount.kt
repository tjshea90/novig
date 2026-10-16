package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Each PropLine key's own plan, read from the quota headers of one cheap call (Tj, 2026-10-16, TCL56: "When I send it to GitHub, have GitHub refresh it and find the paid key and prioritize its use").
 * PropLine puts `X-Daily-Limit` (the plan's requests a day: Free 1,000, Hobby 5,000, Pro 25,000, Streaming Lite 250,000, Streaming 1,000,000), `X-Daily-Used`, `X-Daily-Remaining` and
 * `X-Daily-Reset` (unix seconds) on every authenticated answer, and `GET /v1/sports` is the lightest authenticated read (one request of the day's allowance). The figures go straight into the
 * meter ([UsageMeter.recordBalance]); a key whose limit is above [FREE_LIMIT] is then found by [com.tjshea.vigilant.data.keys.KeyPool] and used first. Read at most every [REFRESH_MS] a key unless forced.
 */
class PropLineAccount(
    private val http: OkHttpClient,
    private val keys: () -> List<String>,
    private val meter: UsageMeter,
    private val base: String = "https://api.prop-line.com/v1",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What one key's answer said. */
    data class Check(val limit: Int?, val used: Int?, val remaining: Int?, val resetAtMs: Long?, val tier: String, val valid: Boolean, val reason: String? = null, val readAtMs: Long = 0L)

    private val checkedAt = HashMap<String, Long>()

    @Volatile
    var last: Map<String, Check> = emptyMap()
        private set

    /** Reads every key's plan ([force]: even one read in the last [minAgeMs]). Returns how many answered; never throws. */
    suspend fun refresh(force: Boolean = false, minAgeMs: Long = REFRESH_MS): Int {
        var answered = 0
        for (key in keys()) {
            val now = clock()
            if (!force && (checkedAt[key]?.let { now - it < minAgeMs } == true)) continue
            checkedAt[key] = now
            val c = try {
                read(key, now)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            answered++
            last = last + (key to c)
            meter.recordBalance(
                QuotaPolicy.PROPLINE, key, c.remaining, c.limit, c.used, c.resetAtMs,
                exhausted = c.valid && c.remaining == 0, inactive = !c.valid, note = if (c.valid) "plan: ${c.tier}" else c.reason,
            )
        }
        return answered
    }

    private suspend fun read(key: String, now: Long): Check? {
        http.newCall(Request.Builder().url("$base/sports").header("X-API-Key", key).get().build()).await().use { r ->
            if (r.code == 401) return Check(null, null, null, null, "rejected", valid = false, reason = "HTTP 401, key refused", readAtMs = now)
            val limit = r.header("X-Daily-Limit")?.trim()?.toIntOrNull()
            val used = r.header("X-Daily-Used")?.trim()?.toIntOrNull()
            val remaining = r.header("X-Daily-Remaining")?.trim()?.toIntOrNull()
            val reset = r.header("X-Daily-Reset")?.trim()?.toLongOrNull()?.times(1000)
            if (limit == null && remaining == null) return null   // 5xx or no quota headers: nothing learned
            return Check(limit ?: if (remaining != null && used != null) remaining + used else null, used, remaining, reset, tierOf(limit), valid = true, readAtMs = now)
        }
    }

    companion object {
        const val REFRESH_MS = 50 * 60_000L
        const val FREE_LIMIT = 1_000

        /** PropLine's plan for a daily limit (docs, 2026-10-16). Unknown figures say the number. */
        fun tierOf(limit: Int?): String = when {
            limit == null -> "?"
            limit <= 1_000 -> "free"
            limit <= 5_000 -> "hobby"
            limit <= 25_000 -> "pro"
            limit <= 250_000 -> "streaming-lite"
            limit <= 1_000_000 -> "streaming"
            else -> "enterprise"
        }

        /** Hobby and above: history, closing lines, +EV, best line, results and CLV answer with data instead of a redacted shell. */
        fun isPaid(limit: Int?): Boolean = limit != null && limit > FREE_LIMIT
    }
}
