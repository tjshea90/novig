package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** SGO answered 504: the query was too heavy. The caller can ask again with less (no alternate lines, fewer parameters). */
class SgoTooHeavyException(message: String) : ReferenceException(message)

/** What `/v2/account/usage` says about a key: the per-interval request and object limits and how much of each is used. */
data class SgoUsage(val intervals: List<Interval>) {
    data class Interval(val name: String, val maxRequests: Long?, val maxObjects: Long?, val requests: Long?, val objects: Long?, val endsAtMs: Long?)

    /** One line for the key test and Diagnostics: the figures that matter on Pro (requests a minute, objects a month). */
    fun summary(): String {
        fun Interval?.part(label: String, unit: String): String? = this?.let { i ->
            val cap = if (unit == "requests") i.maxRequests else i.maxObjects
            val used = if (unit == "requests") i.requests else i.objects
            "$label ${used ?: "?"}/${cap ?: "unlimited"} $unit"
        }
        return listOfNotNull(
            intervals.firstOrNull { it.name == "per-minute" }.part("this minute", "requests"),
            intervals.firstOrNull { it.name == "per-month" }.part("this month", "objects"),
        ).joinToString(" · ").ifEmpty { "no limits listed" }
    }
}

/**
 * SportsGameOdds (SPORTSGAMEODDS_API.md): one HTTP client for `/v2`, with Tj's keys rotated by [pool] like every other provider's. A Pro key allows 300 requests a minute and unlimited objects, so the
 * client spaces calls [minIntervalMs] apart (default 220 ms: 270 a minute, under the cap, one request at a time) instead of counting credits. An answer that isn't JSON, a 5xx, or a transient
 * failure is retried ONCE after 2-5 s (SGO asks for exactly that, not a loop); a 504 is reported as [SgoTooHeavyException]; 401/403 mark the key refused; 429 rests the key until the minute rolls.
 */
class SportsGameOddsClient(
    private val http: OkHttpClient,
    private val pool: KeyPool,
    private val json: Json,
    private val baseUrl: String = "https://api.sportsgameodds.com/v2",
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = 220,
    private val retryDelayMs: () -> Long = { 2_000L + (Math.random() * 3_000L).toLong() },
) {
    private val spacing = Mutex()
    private var lastCallAt = 0L

    /** One page of `/events` for [params] (the caller's filters), with the plan's `notice` if it filtered anything. */
    suspend fun events(params: List<Pair<String, String>>): SgoPage = SgoParser.page(get("/events", params))

    /** Every page of [params] up to [maxPages], newest cursor first-class: stops at the last page. */
    suspend fun eventsAll(params: List<Pair<String, String>>, maxPages: Int = MAX_PAGES): SgoPages {
        val out = ArrayList<SgoEvent>()
        var cursor: String? = null
        var notice: String? = null
        var pages = 0
        while (pages < maxPages) {
            val page = events(if (cursor == null) params else params + ("cursor" to cursor))
            pages++
            out += page.events
            notice = page.notice ?: notice
            cursor = page.nextCursor?.takeIf { it.isNotBlank() } ?: break
        }
        return SgoPages(out, notice, pages)
    }

    /** `/account/usage`: the key's limits and what it has used (free: it does not count as an object). */
    suspend fun usage(): SgoUsage? {
        val raw = get("/account/usage", emptyList())
        val data = ((runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject)?.get("data") as? JsonObject)?.get("rateLimits") as? JsonObject ?: return null
        return SgoUsage(
            data.entries.mapNotNull { (name, v) ->
                val o = v as? JsonObject ?: return@mapNotNull null
                SgoUsage.Interval(name, o.long("maxRequestsPerInterval"), o.long("maxEntitiesPerInterval"), o.long("currentIntervalRequests"), o.long("currentIntervalEntities"), SgoParser.ms(o.str("currentIntervalEndTime")))
            },
        )
    }

    /** The reply body as SGO sent it, for a sample saved to Downloads (Settings › SportsGameOdds › Test key). */
    suspend fun raw(path: String, params: List<Pair<String, String>>): String = get(path, params)

    class SgoPages(val events: List<SgoEvent>, val notice: String?, val pages: Int)

    private suspend fun get(path: String, params: List<Pair<String, String>>, canRetry: Boolean = true): String {
        spacing.withLock {
            val wait = lastCallAt + minIntervalMs - clock()
            if (wait > 0) delay(wait)
            lastCallAt = clock()
        }
        val failure: ReferenceException
        try {
            return pool.execute(cost = 1) { key ->
                val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                http.newCall(Request.Builder().url(url).header("x-api-key", key).header("Accept", "application/json").get().build()).await().use { response ->
                    val body = response.body?.string().orEmpty()
                    val retryMs = response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                    when {
                        response.code == 401 -> KeyAttemptResult.Invalid("HTTP 401, key refused")
                        response.code == 403 -> KeyAttemptResult.Invalid("HTTP 403: ${errorOf(body) ?: "that needs a paid plan or an active subscription"}")
                        response.code == 429 && body.contains("month", ignoreCase = true) && body.contains("object", ignoreCase = true) ->
                            KeyAttemptResult.Depleted("monthly objects used up", retryMs)
                        response.code == 429 -> KeyAttemptResult.RateLimited(retryMs ?: 15_000L, "rate limit (HTTP 429)")
                        response.code == 504 -> throw SgoTooHeavyException("SportsGameOdds timed out (HTTP 504): query too heavy")
                        response.code in 500..599 -> throw ReferenceException("SportsGameOdds HTTP ${response.code}")
                        !response.isSuccessful -> throw ReferenceException("SportsGameOdds HTTP ${response.code}: ${errorOf(body) ?: "refused the request"}")
                        else -> {
                            // A body with no `success` is the rare transient answer SGO's docs say to treat as a 500.
                            if (!body.contains("\"success\"")) throw ReferenceException("SportsGameOdds sent a reply with no result")
                            if (body.contains("\"success\":false") || body.contains("\"success\": false")) throw ReferenceException("SportsGameOdds: ${errorOf(body) ?: "error"}")
                            KeyAttemptResult.Success(body)
                        }
                    }
                }
            }
        } catch (e: SgoTooHeavyException) {
            throw e
        } catch (e: ReferenceException) {
            failure = e
        }
        if (!canRetry) throw failure
        delay(retryDelayMs())
        return get(path, params, canRetry = false)
    }

    private fun errorOf(body: String): String? = (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject)?.str("error")

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

    companion object {
        /** Pages read per league at most (each page is one request of the 300 a minute). */
        const val MAX_PAGES = 6

        /** Events a page asks for; SGO caps the real number at 25-100 by query and answers with the cap. */
        const val PAGE = 100
    }
}
