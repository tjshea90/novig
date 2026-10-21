package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.RateGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** SharpAPI refused or failed a request ([code] is its `error.code`, SHARPAPI.md §1); [retryAtMs] when a 429 said when to come back. */
class SharpApiException(val status: Int, val code: String, message: String, val retryAtMs: Long? = null) : Exception(message) {
    /** The key itself was refused (missing, unknown, expired, disabled): nothing will work until Tj fixes it. */
    val keyRefused: Boolean get() = status == 401
}

/**
 * SharpAPI's REST API (SHARPAPI.md; docs read 2026-10-21). One OkHttp client for every call (a fresh TLS handshake costs 150-200 ms, a kept connection ~40 ms), at most [perMinute] requests a minute
 * ([RateGate], under the Sharp plan's 1,000 so a refresh by hand and the account reads never meet a 429) and [inFlight] at once (`concurrent_request_cap`). A 429 rests every call until its `Retry-After`; a 401
 * marks the key refused until it changes. Every call is counted on the usage meter (the plan has no daily cap: the meter shows the day's count against nothing).
 */
class SharpApiClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val key: () -> String,
    private val usage: UsageMeter? = null,
    private val baseUrl: String = BASE,
    private val clock: () -> Long = System::currentTimeMillis,
    perMinute: Int = PER_MINUTE,
    inFlight: Int = IN_FLIGHT,
    private val gate: RateGate = RateGate(ratePerSecond = perMinute / 60.0, burst = 10, maxRate = perMinute / 60.0, minRate = 2.0),
) {
    private val slots = Semaphore(inFlight)

    @Volatile private var restUntil = 0L
    @Volatile private var refusedKey: String? = null
    @Volatile private var failures = 0
    @Volatile var lastOkMs = 0L
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var calls = 0L
        private set
    /** The minute's window as the last answer said it (`X-RateLimit-Remaining` / `-Limit`). */
    @Volatile var rateRemaining: Int? = null
        private set
    @Volatile var rateLimit: Int? = null
        private set

    fun hasKey(): Boolean = key().isNotBlank()

    /** The saved key was refused by SharpAPI (401) and has not changed since. */
    fun keyRefused(): Boolean = refusedKey != null && refusedKey == key()

    fun down(now: Long = clock()): Boolean = !hasKey() || keyRefused() || now < restUntil || (failures >= 5 && now - lastOkMs > 120_000L)

    /** Forgets the failures and any rest so the next call goes at once (the API manager's restart). */
    fun restart() { failures = 0; restUntil = 0L; refusedKey = null }

    fun health(now: Long = clock()): String = when {
        !hasKey() -> "no key"
        keyRefused() -> "the key was refused ($lastError)"
        now < restUntil -> "resting after HTTP 429 for ${(restUntil - now) / 1000}s"
        lastOkMs == 0L -> "no answer yet"
        else -> "answering (last good answer ${(now - lastOkMs) / 1000}s ago, ${calls} calls)"
    }

    /** One GET of [path] (under `/api/v1`) with [params]; the parsed body. Throws [SharpApiException] for an error answer. */
    suspend fun get(path: String, params: List<Pair<String, String>> = emptyList()): JsonObject = withContext(Dispatchers.IO) {
        val k = key()
        if (k.isBlank()) throw SharpApiException(401, "missing_api_key", "no SharpAPI key saved")
        if (keyRefused()) throw SharpApiException(401, "invalid_api_key", "SharpAPI refused this key: $lastError")
        val wait = restUntil - clock()
        if (wait > 0) throw SharpApiException(429, "rate_limited", "resting after a 429 for ${wait / 1000}s", restUntil)
        val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { params.forEach { (n, v) -> addQueryParameter(n, v) } }.build()
        slots.withPermit {
            gate.acquire()
            val req = Request.Builder().url(url).header("X-API-Key", k).header("Accept", "application/json").build()
            calls++
            val (code, body, headers) = try {
                http.newCall(req).execute().use { r -> Triple(r.code, r.body?.string().orEmpty(), r.headers) }
            } catch (e: java.io.IOException) {
                failures++
                lastError = "network: ${e.message ?: e.javaClass.simpleName}"
                throw SharpApiException(0, "network", lastError!!)
            }
            headers["X-RateLimit-Remaining"]?.toIntOrNull()?.let { rateRemaining = it }
            headers["X-RateLimit-Limit"]?.toIntOrNull()?.let { rateLimit = it }
            usage?.let { runCatching { it.recordCall(QuotaPolicy.SHARPAPI, k, 1) } }
            if (code in 200..299) {
                failures = 0
                lastOkMs = clock()
                lastError = null
                return@withPermit runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw SharpApiException(code, "unreadable", "SharpAPI's answer could not be read") }
            }
            val err = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
            val errCode = err?.get("code")?.let { runCatching { (it as kotlinx.serialization.json.JsonPrimitive).content }.getOrNull() } ?: "http_$code"
            val message = err?.get("message")?.let { runCatching { (it as kotlinx.serialization.json.JsonPrimitive).content }.getOrNull() } ?: "HTTP $code"
            lastError = "$errCode: ${message.take(160)}"
            when (code) {
                401 -> { refusedKey = k; usage?.let { runCatching { it.recordRejected(QuotaPolicy.SHARPAPI, k, lastError!!) } } }
                429 -> {
                    val secs = headers["Retry-After"]?.toLongOrNull() ?: 5L
                    restUntil = clock() + secs.coerceIn(1L, 120L) * 1000L
                    gate.slowDown()
                }
                in 500..599 -> failures++
            }
            throw SharpApiException(code, errCode, "SharpAPI: $lastError", if (code == 429) restUntil else null)
        }
    }

    /** The account's tier, rate limit and features (`GET /account`). */
    suspend fun account(): JsonObject = get("/account")

    /** The day's request count and the minute's window (`GET /account/usage`). */
    suspend fun usage(): JsonObject = get("/account/usage")

    companion object {
        const val BASE = "https://api.sharpapi.io/api/v1"
        /** The Sharp plan allows 1,000 a minute; 900 leaves room for a Test by hand and the account reads. */
        const val PER_MINUTE = 900
        const val IN_FLIGHT = 4
    }
}
