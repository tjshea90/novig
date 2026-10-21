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
 * Unabated Data API, Free tier (UNABATED_API.md): `https://data.unabated.com`, key in `X-Api-Key`. The Free tier allows ONE odds request every five seconds per key, shared by every odds endpoint
 * (docs: guides/errors-and-limits "one scheduler per key"), so every `/market/{league}/{type}/odds` call goes through ONE gate [oddsGapMs] apart (5.2 s: a little over the limit, because the server counts
 * from its own clock). Discovery calls (`/league`, `/bettype`, `/period-type`, `/market/.../sources|teams|people`) do not use the odds interval and are only spaced [otherGapMs] apart. Per the docs: a `429`
 * (too early) or `503` (the delayed snapshot is briefly unavailable) is retried after its `Retry-After`, at most [MAX_ATTEMPTS] times, never with a different key or a live dataset; `401` refuses the key;
 * `403` (a Free key on an endpoint it does not include) is this call's failure only; HTTP 200 with `success:false` is a failure ([UnabatedParser.envelope]).
 */
class UnabatedClient(
    private val http: OkHttpClient,
    private val keys: () -> List<String>,
    meter: com.tjshea.vigilant.data.keys.UsageMeter,
    private val baseUrl: String = "https://data.unabated.com",
    private val clock: () -> Long = System::currentTimeMillis,
    private val oddsGapMs: Long = 5_200,
    private val otherGapMs: Long = 400,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val gate = Mutex()
    private val nextOddsAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var nextOtherAt = 0L

    /**
     * Every key has its OWN five-second odds interval (the Free limit is per key), so more keys are more speed: the pool is offered the keys soonest-ready first (Tj's order breaks ties), a key that is
     * refused (401), rate limited (429 with another key to try) or spent is skipped to the next, and a call waits only for the key it was given.
     */
    private val pool = KeyPool(com.tjshea.vigilant.data.keys.QuotaPolicy.UNABATED, { keys().sortedBy { nextOddsAt[it] ?: 0L } }, meter)

    /** Reserves the key's next odds slot and returns how long to wait for it. */
    private suspend fun reserve(key: String): Long = gate.withLock {
        val now = clock()
        val at = maxOf(now, nextOddsAt[key] ?: 0L)
        nextOddsAt[key] = at + oddsGapMs
        at - now
    }

    @Volatile private var failures = 0
    @Volatile private var lastFailMs = 0L
    @Volatile private var lastOkMs = 0L

    /** Requests made (Diagnostics). */
    @Volatile var requests = 0
        private set

    /** The last failure, as one line, for Diagnostics. */
    @Volatile var lastError: String? = null
        private set

    /** What each board last read, by league and kind (Diagnostics): games, markets, books, how old the prices were. */
    val lastReads = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** How many keys are saved: each has its own interval, so more keys read boards more often ([UnabatedFeed]). */
    fun keyCount(): Int = keys().size

    fun health(now: Long = clock()): String =
        if (lastOkMs == 0L) "no answer yet" else if (down(now)) "DOWN (the other feeds carry on)" else "answering (last good answer ${(now - lastOkMs) / 1000L}s ago, $requests requests)"

    /** Two failures in a row and nothing good for [DOWN_AFTER_MS]: the source gives nothing until one call works again. */
    fun down(now: Long = clock()): Boolean = failures >= 2 && now - lastOkMs > DOWN_AFTER_MS && now - lastFailMs < RETRY_AFTER_MS

    /** Forgets the failures so the next call is made at once (the API manager's restart, XO1: a client that is "down" waits [RETRY_AFTER_MS] before it asks again). Touches nothing else. */
    fun restart() { failures = 0; lastFailMs = 0L }

    suspend fun leagues() = UnabatedParser.leagues(get("/league"))
    suspend fun betTypes() = UnabatedParser.betTypes(get("/bettype"))
    suspend fun periods(league: String) = UnabatedParser.periods(get("/period-type/$league"))
    suspend fun sources(league: String, type: String) = UnabatedParser.sources(get("/market/$league/$type/sources"))
    suspend fun teams(league: String, type: String) = UnabatedParser.teams(get("/market/$league/$type/teams"))
    suspend fun people(league: String, type: String) = UnabatedParser.people(get("/market/$league/$type/people"))

    /** The raw odds board of [type] (`straight`, `props`, `futures`) for [league]; [betTypeIds] narrows it (repeated `betTypeId` parameters, docs: guides/odds). */
    suspend fun oddsRaw(league: String, type: String, betTypeIds: List<Int> = emptyList()): String =
        get("/market/$league/$type/odds", betTypeIds.map { "betTypeId" to it.toString() })

    /** The reply body of `GET [path]`; throws [ReferenceException] on a refusal, a failure or an unreadable reply. */
    suspend fun get(path: String, params: List<Pair<String, String>> = emptyList()): String = try {
        requests++
        attempt(path, params).also { failures = 0; lastOkMs = clock(); lastError = null }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: UnabatedFailure) {
        // Unabated ANSWERED, saying it cannot build this board ("Unexpected error while retrieving odds for the provided filter"): that is not an outage, so it never marks the whole API down.
        lastError = (e.message ?: "failure").take(200)
        lastOkMs = clock()
        throw e
    } catch (e: Exception) {
        failures++; lastFailMs = clock()
        lastError = (e.message ?: e.javaClass.simpleName).take(200)
        throw e
    }

    private fun isOdds(path: String) = path.endsWith("/odds")

    private suspend fun attempt(path: String, params: List<Pair<String, String>>): String {
        var last: ReferenceException? = null
        repeat(MAX_ATTEMPTS) { n ->
            val odds = isOdds(path)
            if (!odds) gate.withLock {
                val wait = nextOtherAt - clock()
                if (wait > 0) sleep(wait)
                nextOtherAt = clock() + otherGapMs
            }
            var retryMs: Long? = null
            try {
                return pool.execute(cost = 1) { key ->
                    // Reserved before sending, so a slow answer never lets the next call on this key go early.
                    if (odds) reserve(key).let { if (it > 0) sleep(it) }
                    val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                    http.newCall(Request.Builder().url(url).header("X-Api-Key", key).header("Accept", "application/json").get().build()).await().use { response ->
                        val body = response.body?.string().orEmpty()
                        val retryAfter = response.header("Retry-After")?.trim()?.toDoubleOrNull()?.let { (it * 1000.0).toLong() }
                        when {
                            response.code == 401 -> KeyAttemptResult.Invalid("HTTP 401, key refused (Free API access can be revoked)")
                            response.code == 403 -> throw ReferenceException("Unabated HTTP 403: ${messageOf(body) ?: "the Free API does not include this"}")
                            // Too early on THIS key: with another key to try, rest this one and move on; with only one, wait its Retry-After and ask again.
                            response.code == 429 && keys().size > 1 -> {
                                val wait = (retryAfter ?: oddsGapMs).coerceIn(1_000L, 30_000L)
                                nextOddsAt[key] = maxOf(nextOddsAt[key] ?: 0L, clock() + wait)
                                KeyAttemptResult.RateLimited(wait, "one odds request per 5 s (HTTP 429)")
                            }
                            response.code == 429 || response.code == 503 -> { retryMs = (retryAfter ?: if (response.code == 503) 5_000L else 5_200L).coerceIn(1_000L, 30_000L); throw ReferenceException("Unabated HTTP ${response.code}: slow down / snapshot unavailable") }
                            response.code in 500..599 -> throw ReferenceException("Unabated HTTP ${response.code}")
                            !response.isSuccessful -> throw ReferenceException("Unabated HTTP ${response.code}: ${messageOf(body) ?: "refused the request"}")
                            body.isBlank() -> throw ReferenceException("Unabated sent an empty reply")
                            // HTTP 200 with success:false is a failure whose messages say why (checked in the envelope's tail/head, not by parsing the body).
                            Regex("\"success\"\\s*:\\s*false").containsMatchIn(body.takeLast(600) + body.take(300)) -> { UnabatedParser.board(body, false); throw UnabatedFailure("Unabated: the request could not complete") }
                            else -> KeyAttemptResult.Success(body)
                        }
                    }
                }
            } catch (e: UnabatedFailure) {
                throw e
            } catch (e: com.tjshea.vigilant.data.keys.AllKeysExhaustedException) {
                last = ReferenceException("Unabated: ${e.message ?: "every key is rate limited, spent or refused"}")
                if (n == MAX_ATTEMPTS - 1) throw last!!
                sleep(oddsGapMs)
            } catch (e: ReferenceException) {
                last = e
                if (e.message?.contains("HTTP 403") == true || n == MAX_ATTEMPTS - 1) throw e
                // 429 / 503 wait their Retry-After; any other failure waits a short pause once.
                sleep(retryMs ?: 2_000L)
            }
        }
        throw last ?: ReferenceException("Unabated: no attempt made")
    }

    private fun messageOf(body: String): String? =
        Regex("\"message\"\\s*:\\s*\"([^\"]{1,200})\"").find(body)?.groupValues?.get(1)

    companion object {
        const val DOWN_AFTER_MS = 120_000L

        /** A source that is down is asked again once this long has passed since its last failure (the next scan): a feed that came back reconnects by itself, and a failure here starts the rest over. */
        const val RETRY_AFTER_MS = 60_000L
        const val MAX_ATTEMPTS = 3
    }
}
