package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OddsPapi v5 (ODDSPAPI_API.md): one HTTP client for `https://v5.oddspapi.io/en`, Tj's keys rotated by [pool] like every other provider's. The key goes in the `X-API-Key` header (the docs prefer it to
 * `?apiKey=`). Two limits are documented and both are kept under: odds endpoints (`/fixtures/odds`, `/fixtures/odds/main`, parlay, sgp, futures) 10 requests a second, everything else 100 a minute, so
 * odds calls are spaced [oddsGapMs] apart and the rest [otherGapMs], each on its own gate and one at a time. A 429 carries `Retry-After` / `retryAfterSec` and rests that key; 401 refuses the key
 * (`missing_api_key`, `invalid_api_key`); 403 `channel_not_allowed` is this call's failure only (a valid key not entitled to that endpoint: it must not refuse the key for the calls it can make); a 5xx
 * or a transient failure is retried ONCE after a pause. Nothing here has met a live key: Settings › OddsPapi › Test key checks it.
 */
class OddsPapiClient(
    private val http: OkHttpClient,
    private val pool: KeyPool,
    private val baseUrl: String = "https://v5.oddspapi.io/en",
    private val clock: () -> Long = System::currentTimeMillis,
    private val oddsGapMs: Long = 110,
    private val otherGapMs: Long = 700,
    private val retryDelayMs: () -> Long = { 1_500L + (Math.random() * 1_500L).toLong() },
) {
    private val oddsGate = Mutex()
    private val otherGate = Mutex()
    private var lastOddsAt = 0L
    private var lastOtherAt = 0L

    @Volatile private var failures = 0
    @Volatile private var lastOkMs = 0L

    /** Requests made (Diagnostics). */
    @Volatile var requests = 0
        private set

    /** The last `X-RateLimit-Remaining` the service sent, and the endpoint class it was for. */
    @Volatile var lastRemaining: String? = null
        private set

    /** What each source last read, by league (Diagnostics): games, markets, books, how old the prices were. */
    val lastReads = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun health(now: Long = clock()): String =
        if (lastOkMs == 0L) "no answer yet" else if (down(now)) "DOWN (the replaced feeds are back)" else "answering (last good answer ${(now - lastOkMs) / 1000L}s ago, $requests requests)"

    /**
     * Whether OddsPapi has stopped answering: two failures in a row and nothing good for [DOWN_AFTER_MS]. While it is down the feeds Vigilant stood down for it are asked again, so an outage costs
     * no scan; one good answer ends it.
     */
    fun down(now: Long = clock()): Boolean = failures >= 2 && now - lastOkMs > DOWN_AFTER_MS

    /** The answer body of `GET [path]` with [params]; throws [ReferenceException] when the service refuses, errs or is unreadable. */
    suspend fun get(path: String, params: List<Pair<String, String>> = emptyList()): String = try {
        requests++
        getWithRetry(path, params).also { failures = 0; lastOkMs = clock() }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        failures++
        throw e
    }

    suspend fun bookmakers(): List<OpBookInfo> = OpParser.bookmakers(get("/bookmakers"))
    suspend fun tournaments(sportId: Int): List<OpTournament> = OpParser.tournaments(get("/tournaments", listOf("sportId" to sportId.toString())))
    suspend fun markets(sportId: Int): List<OpMarket> = OpParser.markets(get("/markets", listOf("sportId" to sportId.toString())))

    /** The names of [playerIds] ("Last, First" as OddsPapi has them), batched. */
    suspend fun players(playerIds: Collection<Long>): Map<Long, String> {
        val out = HashMap<Long, String>()
        for (chunk in playerIds.distinct().chunked(PLAYERS_PER_CALL)) {
            val raw = get("/players", listOf("playerIds" to chunk.joinToString(",")))
            val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) }.getOrNull()
            val rows = (root as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                ?: ((root as? kotlinx.serialization.json.JsonObject)?.get("data") as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.orEmpty()
            for (r in rows) {
                val id = (r["playerId"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: continue
                val name = (r["playerName"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
                out[id] = name
            }
        }
        return out
    }

    private fun isOdds(path: String) = path.startsWith("/fixtures/odds") && !path.startsWith("/fixtures/odds/clv") && !path.startsWith("/fixtures/odds/historical") || path.startsWith("/futures/odds")

    private suspend fun getWithRetry(path: String, params: List<Pair<String, String>>, canRetry: Boolean = true): String {
        val odds = isOdds(path)
        (if (odds) oddsGate else otherGate).withLock {
            val gap = if (odds) oddsGapMs else otherGapMs
            val last = if (odds) lastOddsAt else lastOtherAt
            val wait = last + gap - clock()
            if (wait > 0) delay(wait)
            if (odds) lastOddsAt = clock() else lastOtherAt = clock()
        }
        val failure: ReferenceException
        try {
            return pool.execute(cost = 1) { key ->
                val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                http.newCall(Request.Builder().url(url).header("X-API-Key", key).header("Accept", "application/json").get().build()).await().use { response ->
                    val body = response.body?.string().orEmpty()
                    response.header("X-RateLimit-Remaining")?.let { lastRemaining = it }
                    val retryMs = response.header("Retry-After")?.trim()?.toDoubleOrNull()?.let { (it * 1000.0).toLong() } ?: OpParser.retryAfterMs(body)
                    val code = OpParser.errorCode(body).orEmpty()
                    when {
                        response.code == 401 -> KeyAttemptResult.Invalid("HTTP 401 ${code.ifEmpty { "key refused" }}")
                        response.code == 403 -> throw ReferenceException("OddsPapi HTTP 403: ${code.ifEmpty { "your key is not entitled to this endpoint" }}")
                        response.code == 429 && (code.contains("quota", true) || code.contains("exhaust", true) || code.contains("monthly", true) || code.contains("credit", true)) ->
                            KeyAttemptResult.Depleted("quota used up ($code)", retryMs)
                        response.code == 429 -> KeyAttemptResult.RateLimited(retryMs ?: 1_000L, "rate limit (HTTP 429)")
                        response.code in 500..599 -> throw ReferenceException("OddsPapi HTTP ${response.code}")
                        !response.isSuccessful -> throw ReferenceException("OddsPapi HTTP ${response.code}: ${code.ifEmpty { "refused the request" }}")
                        body.isBlank() -> throw ReferenceException("OddsPapi sent an empty reply")
                        else -> KeyAttemptResult.Success(body)
                    }
                }
            }
        } catch (e: ReferenceException) {
            failure = e
        }
        // A 403 is final (the plan does not include it); the rest are tried once more.
        if (!canRetry || failure.message?.contains("HTTP 403") == true) throw failure
        delay(retryDelayMs())
        return getWithRetry(path, params, canRetry = false)
    }

    companion object {
        /** OddsPapi counts as down when it has failed twice running and answered nothing for this long. */
        const val DOWN_AFTER_MS = 120_000L
        const val PLAYERS_PER_CALL = 100
    }
}
