package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.ReferenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** `get_info`: the exact spellings a subscription filter takes (BOLTODDS_API.md §2, §4). */
data class BoltInfo(val sports: List<String>, val sportsbooks: List<String>)

/** One entry of `get_games`. */
data class BoltGameInfo(val game: String, val sport: String, val whenText: String?, val universalId: String?)

/**
 * BoltOdds' REST side (BOLTODDS_API.md §4, §5): `https://spro.agency/api/...?key=`. The odds themselves come over the websocket ([BoltOddsStream]); these calls name what can be subscribed to (`get_info`,
 * `get_games`, `get_markets`), read the initial state (`snapshot`) and, for Test key, the optional `get_parlays` and scores `.../user` endpoints. Limits (docs): 12 GETs a minute per IP, 3 at once; the snapshot 6 a
 * minute per IP and a per-key daily token quota it does not publish; the client keeps under both with two gates and asks for the snapshot once per connection.
 *
 * Failures: an invalid or missing key answers HTTP 403 `{"detail":"Invalid API token"}` (checked 2026-10-12), which refuses the key and moves to the next; HTTP 429 rests the key for its `Retry-After`; any
 * other refusal or failure is this call's alone. A key never appears in a message: [redact] cuts every saved key out of anything shown.
 */
class BoltOddsClient(
    private val http: OkHttpClient,
    private val keys: () -> List<String>,
    private val meter: UsageMeter,
    val baseUrl: String = "https://spro.agency/api",
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    /** One GET every [getGapMs] with [getBurst] allowed at once (12 a minute, 3 in quick succession), per IP: shared by every key. */
    private val getGapMs: Long = 5_200L,
    private val getBurst: Int = 3,
    /** The snapshot's own gap (6 a minute). */
    private val snapshotGapMs: Long = 10_500L,
) {
    private val pool = KeyPool(QuotaPolicy.BOLTODDS, keys, meter)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val gate = Mutex()
    private var getTokens = getBurst.toDouble()
    private var getAt = clock()
    private var nextSnapshotAt = 0L

    @Volatile private var failures = 0
    @Volatile private var lastFailMs = 0L
    @Volatile var lastOkMs = 0L
        private set

    /** Requests made (Diagnostics). */
    @Volatile var requests = 0
        private set

    /** The last failure, as one line, for Diagnostics. */
    @Volatile var lastError: String? = null
        private set

    /** How many keys are saved. */
    fun keyCount(): Int = keys().size

    /** The first saved key the meter has not refused or rested, or null: the key a socket connects with. */
    suspend fun nextKey(): String? = meter.pick(QuotaPolicy.BOLTODDS, keys(), 1)

    /** A socket's key was refused (close 1008 "Invalid token"): rests until the next day, other keys carry on. */
    suspend fun refuseKey(key: String, why: String) = meter.recordRejected(QuotaPolicy.BOLTODDS, key, why.take(120))

    fun health(now: Long = clock()): String =
        if (lastOkMs == 0L) "no answer yet" else if (down(now)) "DOWN (REST: ${lastError ?: "failing"})" else "answering (last good answer ${(now - lastOkMs) / 1000L}s ago, $requests requests)"

    /** Two failures in a row and nothing good for two minutes. */
    fun down(now: Long = clock()): Boolean = failures >= 2 && now - lastOkMs > DOWN_AFTER_MS && now - lastFailMs < RETRY_AFTER_MS

    /** Forgets the failures so the next call is made at once (the API manager's restart, XO1: a client that is "down" waits [RETRY_AFTER_MS] before it asks again). Touches nothing else. */
    fun restart() { failures = 0; lastFailMs = 0L }

    suspend fun info(): BoltInfo {
        val root = parse(get("/get_info"))
        fun list(k: String) = ((root[k] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        return BoltInfo(list("sports"), list("sportsbooks"))
    }

    suspend fun games(): Map<String, BoltGameInfo> {
        val root = parse(get("/get_games"))
        return root.entries.mapNotNull { (k, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            fun s(n: String) = (o[n] as? JsonPrimitive)?.takeIf { it.isString }?.content
            BoltGameInfo(s("game") ?: k, s("sport") ?: return@mapNotNull null, s("when"), s("universal_id")).let { k to it }
        }.toMap()
    }

    /** `get_markets` for [sports] and [books]: sportsbook slug -> sport -> market names. Comma-separated, as the docs say (a market name may contain a comma, a sport or book name does not). */
    suspend fun markets(sports: Collection<String>, books: Collection<String>): Map<String, Map<String, List<String>>> {
        val params = buildList {
            if (sports.isNotEmpty()) add("sports" to sports.joinToString(","))
            if (books.isNotEmpty()) add("sportsbooks" to books.joinToString(","))
        }
        val root = parse(get("/get_markets", params))
        return root.mapValues { (_, v) ->
            (v as? JsonObject)?.mapValues { (_, l) -> ((l as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } } ?: emptyMap()
        }
    }

    /**
     * The initial state for a subscription's filters, as raw JSON text (a JSON array of `initial_state` messages: [BoltParser.snapshot] reads it one game at a time). The filters go as repeated parameters
     * (`sports=NBA&sports=MLB`), the form of the docs' own `requests.get(URL, params=filters)`: a market name may contain a comma, so a comma-joined list would split it.
     */
    suspend fun snapshotRaw(filters: BoltFilters): String {
        val params = buildList {
            filters.sports.forEach { add("sports" to it) }
            filters.sportsbooks.forEach { add("sportsbooks" to it) }
            filters.games.forEach { add("games" to it) }
            filters.markets.forEach { add("markets" to it) }
        }
        return get("/snapshot", params, snapshot = true)
    }

    /**
     * The snapshot for [filters], read as a STREAM: [onElement] gets each game's JSON text as it arrives and the body is never held whole (a whole sport's lines and props is tens of megabytes). Same gates and
     * key rotation as [get]; returns how many games came. An error object in place of the array ("Invalid API token") refuses the key or fails the call, as with any GET.
     */
    suspend fun snapshotStream(filters: BoltFilters, onElement: (String) -> Unit): Int {
        requests++
        val params = buildList {
            filters.sports.forEach { add("sports" to it) }
            filters.sportsbooks.forEach { add("sportsbooks" to it) }
            filters.games.forEach { add("games" to it) }
            filters.markets.forEach { add("markets" to it) }
        }
        try {
            val n = pool.execute(cost = 1) { key ->
                acquire(snapshot = true)
                val response = http.newCall(Request.Builder().url(url("/snapshot", key, params)).header("Accept", "application/json").get().build()).await()
                response.use { r ->
                    when {
                        r.code == 401 || r.code == 403 -> {
                            val detail = detailOf(r.body?.string().orEmpty())
                            if (r.code == 401 || detail?.contains("token", ignoreCase = true) == true) KeyAttemptResult.Invalid("HTTP ${r.code}: ${detail ?: "key refused"}")
                            else throw BoltOddsException("BoltOdds HTTP 403: ${detail ?: "your plan does not include the snapshot"}")
                        }
                        r.code == 429 -> {
                            val wait = (r.header("Retry-After")?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 15_000L).coerceIn(1_000L, 120_000L)
                            if (keys().size > 1) KeyAttemptResult.RateLimited(wait, "BoltOdds snapshot limit (HTTP 429)") else throw BoltOddsException("BoltOdds snapshot HTTP 429 (6 a minute per address, plus a daily token quota)${detailOf(r.body?.string().orEmpty())?.let { ": $it" } ?: ""}${r.header("Retry-After")?.let { " · retry-after $it s" } ?: ""}")
                        }
                        !r.isSuccessful -> throw BoltOddsException("BoltOdds snapshot HTTP ${r.code}")
                        else -> {
                            val job = currentCoroutineContext()[Job]
                            val res = withContext(Dispatchers.IO) {
                                BoltJsonArray.forEachElement(r.body!!.charStream()) { text ->
                                    if (job != null && !job.isActive) throw CancellationException("snapshot cancelled")
                                    onElement(text)
                                }
                            }
                            res.notAnArray?.let { throw BoltOddsException("BoltOdds snapshot: ${redact(it).take(160)}") }
                            KeyAttemptResult.Success(res.elements)
                        }
                    }
                }
            }
            failures = 0; lastOkMs = clock(); lastError = null
            return n
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.tjshea.vigilant.data.keys.AllKeysExhaustedException) {
            failures++; lastFailMs = clock()
            lastError = redact(e.message ?: "every key is refused or resting").take(200)
            throw BoltOddsException("BoltOdds: $lastError")
        } catch (e: Exception) {
            failures++; lastFailMs = clock()
            lastError = redact(e.message ?: e.javaClass.simpleName).take(200)
            throw e
        }
    }

    /** `get_parlays` for one sportsbook slug (raw text; Pro). Not used by the scan: Test key reads it for the sample. */
    suspend fun parlaysRaw(book: String): String = get("/get_parlays", listOf("sportsbooks" to book))

    /** An optional endpoint's raw reply for the sample (`/playbyplay/user`, `/livescores/user`): its HTTP code and body, never an exception for a plan that lacks it. */
    suspend fun probe(path: String): Pair<Int, String> {
        val key = nextKey() ?: return 0 to "no usable key"
        acquire(snapshot = false)
        val r = http.newCall(Request.Builder().url(url(path, key, emptyList())).get().build()).awaitText()
        return r.code to redact(r.body.take(600))
    }

    private fun url(path: String, key: String, params: List<Pair<String, String>>) =
        "$baseUrl$path".toHttpUrl().newBuilder().addQueryParameter("key", key).apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()

    /** Waits for a token of the GET bucket (and, for the snapshot, its own gap). */
    private suspend fun acquire(snapshot: Boolean) {
        val wait = gate.withLock {
            val now = clock()
            getTokens = minOf(getBurst.toDouble(), getTokens + (now - getAt).coerceAtLeast(0) / getGapMs.toDouble())
            getAt = now
            var w = 0L
            if (snapshot) {
                w = (nextSnapshotAt - now).coerceAtLeast(0L)
                nextSnapshotAt = now + w + snapshotGapMs
            }
            if (getTokens < 1.0) w = maxOf(w, ((1.0 - getTokens) * getGapMs).toLong())
            getTokens -= 1.0
            w
        }
        if (wait > 0) sleep(wait)
    }

    private suspend fun get(path: String, params: List<Pair<String, String>> = emptyList(), snapshot: Boolean = false): String = try {
        requests++
        attempt(path, params, snapshot).also { failures = 0; lastOkMs = clock(); lastError = null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failures++; lastFailMs = clock()
        lastError = redact(e.message ?: e.javaClass.simpleName).take(200)
        throw e
    }

    private suspend fun attempt(path: String, params: List<Pair<String, String>>, snapshot: Boolean): String {
        var last: ReferenceException? = null
        repeat(MAX_ATTEMPTS) { n ->
            var retryMs: Long? = null
            try {
                return pool.execute(cost = 1) { key ->
                    acquire(snapshot)
                    val r = http.newCall(Request.Builder().url(url(path, key, params)).header("Accept", "application/json").get().build()).awaitText()
                    val detail = detailOf(r.body)
                    when {
                        r.code == 401 || (r.code == 403 && detail?.contains("token", ignoreCase = true) == true) -> KeyAttemptResult.Invalid("HTTP ${r.code}: ${detail ?: "key refused"}")
                        r.code == 429 -> {
                            val wait = (r.header("Retry-After")?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 10_000L).coerceIn(1_000L, 60_000L)
                            if (keys().size > 1) KeyAttemptResult.RateLimited(wait, "BoltOdds rate limit (HTTP 429)") else { retryMs = wait; throw BoltOddsException("BoltOdds HTTP 429: slow down") }
                        }
                        r.code == 403 -> throw BoltOddsException("BoltOdds HTTP 403: ${detail ?: "your plan does not include this"}")
                        r.code in 500..599 -> throw BoltOddsException("BoltOdds HTTP ${r.code}")
                        !r.isSuccessful -> throw BoltOddsException("BoltOdds HTTP ${r.code}: ${detail ?: "refused the request"}")
                        r.body.isBlank() -> throw BoltOddsException("BoltOdds sent an empty reply")
                        else -> KeyAttemptResult.Success(r.body)
                    }
                }
            } catch (e: com.tjshea.vigilant.data.keys.AllKeysExhaustedException) {
                throw BoltOddsException("BoltOdds: ${redact(e.message ?: "every key is refused or resting")}")
            } catch (e: BoltOddsException) {
                last = e
                if (e.message?.contains("HTTP 403") == true || n == MAX_ATTEMPTS - 1) throw e
                sleep(retryMs ?: 2_000L)
            }
        }
        throw last ?: BoltOddsException("BoltOdds: no attempt made")
    }

    private fun parse(text: String): JsonObject = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { throw BoltOddsException("BoltOdds sent a reply Vigilant couldn't read") }

    private fun detailOf(body: String): String? =
        Regex("\"detail\"\\s*:\\s*\"([^\"]{1,160})\"").find(body)?.groupValues?.get(1)

    /** [text] with every saved key replaced: nothing a user can see or share carries one. */
    fun redact(text: String): String = keys().filter { it.length >= 6 }.fold(text) { acc, k -> acc.replace(k, "…" + k.takeLast(4)) }

    companion object {
        const val DOWN_AFTER_MS = 120_000L

        /** A source that is down is asked again once this long has passed since its last failure (the next scan): a feed that came back reconnects by itself, and a failure here starts the rest over. */
        const val RETRY_AFTER_MS = 60_000L
        const val MAX_ATTEMPTS = 3
    }
}

class BoltOddsException(message: String) : ReferenceException(message)

/** The filters of a subscription or a snapshot (BOLTODDS_API.md §2); an empty list = all of that kind. */
data class BoltFilters(
    val sports: List<String> = emptyList(),
    val sportsbooks: List<String> = emptyList(),
    val games: List<String> = emptyList(),
    val markets: List<String> = emptyList(),
)
