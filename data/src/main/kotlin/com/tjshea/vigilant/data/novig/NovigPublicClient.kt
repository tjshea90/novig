package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Everything the scanner needs from Novig. The public REST client implements it; tests fake it. */
interface NovigSource {
    suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long? = null): List<NovigEvent>
    suspend fun markets(
        leagues: Collection<String>,
        marketTypes: Collection<String>,
        eventStatuses: Collection<String>,
        startsBefore: Long? = null,
    ): List<NovigMarket>
    /** Fetches every book in [marketIds]. [onProgress] gets (done, total) as books arrive. */
    suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)? = null): BookBatch
    suspend fun market(marketId: String): NovigMarket?
}

/** The result of fetching many books at once. A failure on some books never discards the rest. */
data class BookBatch(
    val books: Map<String, NovigBook>,
    /** Books served from the ETag cache because Novig answered 304 Not Modified. */
    val notModified: Int,
    val fetched: Int,
    val failed: Int,
    /** Set when Novig throttled us. The caller should slow down for this many seconds. */
    val retryAfterSeconds: Int? = null,
    val lastError: String? = null,
    /** Books that couldn't be refreshed this time and are shown from the last scan instead. */
    val fromCache: Int = 0,
    /** Books read through the connected key's own rate limit rather than the shared public one. */
    val viaKey: Int = 0,
    /** Set when the key route failed and this scan fell back to the public routes. */
    val keyProblem: String? = null,
)

class NovigHttpException(val code: Int, message: String, val retryAfterSeconds: Int? = null) : IOException(message)

/**
 * Novig's REST routes (NOVIG_API.md §5), verified live 2026-09-25. Public routes need no key.
 *
 * Rate limits (Tj's phone got `429`s from v0.5.0's 15-second polling):
 *  - Every public request goes through one [RateGate]: ~[publicRate]/s, bursts of [publicBurst],
 *    [publicConcurrency] books at a time. Measured edge limit: ~40–100 fast requests, then
 *    `429 Retry-After: 1`, per IP, and a carrier IP may be shared (NOVIG_API.md §5.1).
 *  - A `429` pauses every request for its Retry-After and halves the rate for a minute. Each book
 *    gets two paced retries; a long Retry-After or an edge `403` stops the batch, and every book
 *    not refreshed is served from the last scan's copy.
 *  - With a key connected ([keyed]), books come from the signed route instead, which draws on the
 *    key's own `read` bucket (64 burst, 16/s refill) rather than the shared public edge. If the key
 *    route fails (VPN, location check, revoked key), the scan falls back to the public routes and
 *    says why.
 *
 * Battery/data notes for the Moto G target:
 *  - OkHttp negotiates gzip on its own: a full NCAAF game-line catalog is ~2.3MB raw, ~375KB gzipped.
 *  - Books use `If-None-Match` with the ETag Novig returns (`"<marketId>-<seq>"`), so an unchanged
 *    book costs a header-only 304 and no JSON parsing.
 */
class NovigPublicClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.novig.com",
    private val clock: () -> Long = System::currentTimeMillis,
    publicRate: Double = 4.0,
    publicBurst: Int = 10,
    private val publicConcurrency: Int = 2,
    keyedRate: Double = 8.0,
    keyedBurst: Int = 16,
    private val keyedConcurrency: Int = 4,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : NovigSource {

    private val publicGate = RateGate(publicRate, publicBurst, clock, sleep)
    private val keyedGate = RateGate(keyedRate, keyedBurst, clock, sleep)

    /** Signs book requests with the connected read-only key. Null = public routes only. */
    @Volatile
    var keyed: NovigSignedClient? = null

    /** After the key route fails, stay on public routes this long before trying it again. */
    private val keyedRetryMs = 10 * 60_000L

    @Volatile
    private var keyedDownUntil = 0L

    private data class CachedBook(val etag: String?, val book: NovigBook)

    /** Bounded LRU of the last books seen, keyed by market ID. */
    private val bookCache: MutableMap<String, CachedBook> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedBook>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBook>) = size > MAX_CACHED_BOOKS
        },
    )

    override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?): List<NovigEvent> =
        paged("/v3/public/catalog/events", buildMap {
            if (leagues.isNotEmpty()) put("league", leagues.joinToString(","))
            if (statuses.isNotEmpty()) put("status", statuses.joinToString(","))
            startsBefore?.let { put("startsBefore", it.toString()) }
            put("limit", "1000")
        }) { body -> json.decodeFromString(EventPageDto.serializer(), body).let { it.items.map(EventDto::toDomain) to it.next } }

    override suspend fun markets(
        leagues: Collection<String>,
        marketTypes: Collection<String>,
        eventStatuses: Collection<String>,
        startsBefore: Long?,
    ): List<NovigMarket> =
        paged("/v3/public/catalog/markets", buildMap {
            if (leagues.isNotEmpty()) put("league", leagues.joinToString(","))
            if (marketTypes.isNotEmpty()) put("marketType", marketTypes.joinToString(","))
            if (eventStatuses.isNotEmpty()) put("eventStatus", eventStatuses.joinToString(","))
            startsBefore?.let { put("startsBefore", it.toString()) }
            put("limit", "5000")
        }) { body -> json.decodeFromString(MarketPageDto.serializer(), body).let { it.items.map(MarketDto::toDomain) to it.next } }

    override suspend fun market(marketId: String): NovigMarket? {
        val request = Request.Builder().url("$baseUrl/v3/public/catalog/markets/$marketId").get().build()
        publicGate.acquire()
        http.newCall(request).await().use { response ->
            if (response.code == 404) return null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw httpError(response.code, body, response.header("Retry-After"))
            return json.decodeFromString(MarketDto.serializer(), body).toDomain()
        }
    }

    /** The last book seen for [marketId], without any network. */
    fun cached(marketId: String): NovigBook? = bookCache[marketId]?.book

    override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch = coroutineScope {
        val ids = marketIds.distinct()
        val signer = keyed?.takeIf { clock() >= keyedDownUntil }
        val useKey = AtomicReference(signer)
        val keyProblem = AtomicReference<String?>(null)
        val gate = Semaphore(if (signer != null) keyedConcurrency else publicConcurrency)
        val stop = AtomicReference<NovigHttpException?>(null)
        val throttleHits = AtomicInteger(0)
        val done = AtomicInteger(0)
        onProgress?.invoke(0, ids.size)
        val results = ids.map { id ->
            async {
                gate.withPermit {
                    var retries = 0
                    val outcome: BookFetch = run {
                        while (true) {
                            // Once Novig says slow down for long, stop sending. Serve whatever is cached.
                            if (stop.get() != null) return@run BookFetch.Skipped(id)
                            val key = useKey.get()
                            val rate = if (key != null) keyedGate else publicGate
                            rate.acquire()
                            try {
                                return@run fetchBook(id, key)
                            } catch (e: NovigApiException) {
                                // The key route refused (VPN, stale location check, revoked key):
                                // finish this scan on the public routes and say why once.
                                if (e.status == 429) {
                                    keyedGate.pause(clock() + 1000L)
                                    keyedGate.slowDown()
                                    if (retries++ < 2) continue
                                    return@run BookFetch.Failed(id, e.advice)
                                }
                                if (useKey.getAndSet(null) != null) {
                                    keyedDownUntil = clock() + keyedRetryMs
                                    keyProblem.compareAndSet(null, e.advice)
                                }
                                continue
                            } catch (e: NovigHttpException) {
                                val retryAfter = e.retryAfterSeconds ?: 1
                                // Measured live 2026-09-25: the edge answers a burst with 429 and
                                // Retry-After: 1. Pause everyone, halve the pace, retry this book
                                // twice; anything still missing is served from the last scan.
                                if (e.code == 429 && retries < 2 && retryAfter <= SHORT_RETRY_SECONDS && throttleHits.incrementAndGet() <= MAX_SHORT_RETRIES) {
                                    retries++
                                    rate.pause(clock() + retryAfter * 1000L)
                                    rate.slowDown()
                                    continue
                                }
                                if (e.code == 429 || e.code == 403) stop.compareAndSet(null, e)
                                return@run BookFetch.Failed(id, e.message ?: "HTTP ${e.code}")
                            } catch (e: IOException) {
                                return@run BookFetch.Failed(id, e.message ?: e.javaClass.simpleName)
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        BookFetch.Skipped(id)
                    }
                    onProgress?.invoke(done.incrementAndGet(), ids.size)
                    outcome
                }
            }
        }.map { it.await() }

        val books = HashMap<String, NovigBook>()
        var notModified = 0
        var fetched = 0
        var failed = 0
        var fromCache = 0
        var viaKey = 0
        var lastError: String? = null
        for (r in results) {
            when (r) {
                is BookFetch.Fresh -> { books[r.book.marketId] = r.book; fetched++; if (r.viaKey) viaKey++ }
                is BookFetch.NotModified -> { books[r.book.marketId] = r.book; notModified++; if (r.viaKey) viaKey++ }
                is BookFetch.Failed -> {
                    failed++
                    lastError = r.reason
                    bookCache[r.marketId]?.let { books[r.marketId] = it.book; fromCache++ }
                }
                is BookFetch.Skipped -> bookCache[r.marketId]?.let { books[r.marketId] = it.book; fromCache++ }
            }
        }
        val t = stop.get()
        BookBatch(
            books, notModified, fetched, failed,
            retryAfterSeconds = t?.retryAfterSeconds ?: t?.let { 10 },
            lastError = lastError ?: t?.message,
            fromCache = fromCache,
            viaKey = viaKey,
            keyProblem = keyProblem.get(),
        )
    }

    private sealed interface BookFetch {
        data class Fresh(val book: NovigBook, val viaKey: Boolean) : BookFetch
        data class NotModified(val book: NovigBook, val viaKey: Boolean) : BookFetch
        data class Failed(val marketId: String, val reason: String) : BookFetch
        data class Skipped(val marketId: String) : BookFetch
    }

    private suspend fun fetchBook(marketId: String, key: NovigSignedClient?): BookFetch {
        val cached = bookCache[marketId]
        // The signed route reads the same book from the key's own `read` bucket. If-None-Match
        // isn't part of the NOVIG-V3 signature, so it's added after signing.
        val base = key?.signedRequest("GET", "/v3/catalog/markets/$marketId/book")?.newBuilder()
            ?: Request.Builder().url("$baseUrl/v3/public/catalog/markets/$marketId/book").get()
        val request = base.apply { cached?.etag?.let { header("If-None-Match", it) } }.build()
        http.newCall(request).await().use { response ->
            if (response.code == 304 && cached != null) {
                val refreshed = cached.book.copy(fetchedAtMs = clock())
                bookCache[marketId] = cached.copy(book = refreshed)
                return BookFetch.NotModified(refreshed, key != null)
            }
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (key != null && response.code != 429 && !(response.code == 403 && body.trimStart().startsWith("<"))) {
                    val err = runCatching { json.decodeFromString(ErrorDto.serializer(), body) }.getOrNull()
                    throw NovigApiException(response.code, err?.code, err?.message ?: body.take(120).takeIf { !it.trimStart().startsWith("<") })
                }
                throw httpError(response.code, body, response.header("Retry-After"))
            }
            val book = json.decodeFromString(BookDto.serializer(), body).toDomain(clock())
            bookCache[marketId] = CachedBook(response.header("ETag"), book)
            return BookFetch.Fresh(book, key != null)
        }
    }

    private suspend fun <T> paged(
        path: String,
        params: Map<String, String>,
        parse: (String) -> Pair<List<T>, String?>,
    ): List<T> {
        val all = ArrayList<T>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val url = "$baseUrl$path".toHttpUrl().newBuilder().apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
                after?.let { addQueryParameter("after", it) }
            }.build()
            publicGate.acquire()
            http.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, body, response.header("Retry-After"))
                val (items, next) = parse(body)
                all += items
                after = next
            }
            if (after == null) return all
        }
        return all
    }

    private fun httpError(code: Int, body: String, retryAfter: String?): NovigHttpException {
        val retry = retryAfter?.trim()?.toIntOrNull()
        val message = when {
            code == 429 -> "Novig is rate-limiting this device (HTTP 429)" + (retry?.let { ", retry in ${it}s" } ?: "")
            // The edge refuses with an HTML body and no error code (NOVIG_API.md §11).
            code == 403 && body.trimStart().startsWith("<") -> "Novig's edge blocked the request (HTTP 403). Slowing down."
            else -> "Novig HTTP $code" + errorCode(body)?.let { " ($it)" }.orEmpty()
        }
        return NovigHttpException(code, message, retry ?: if (code == 403) 30 else null)
    }

    private fun errorCode(body: String): String? =
        runCatching { json.decodeFromString(ErrorDto.serializer(), body).code }.getOrNull()

    companion object {
        const val MAX_CACHED_BOOKS = 3000
        const val SHORT_RETRY_SECONDS = 5
        const val MAX_SHORT_RETRIES = 8
        const val MAX_PAGES = 20
    }
}

// ---- wire format (OpenAPI 3.1 spec, NOVIG_API.md §5) -------------------------------------------

@Serializable
private data class ErrorDto(val code: String? = null, val message: String? = null)

@Serializable
private data class EventPageDto(val items: List<EventDto> = emptyList(), val next: String? = null)

@Serializable
private data class EventDto(
    val eventId: String,
    val sport: String = "",
    val league: String = "",
    val status: String = "",
    val description: String = "",
    val startsTs: Long = 0,
) {
    fun toDomain() = NovigEvent(eventId, sport, league, status, description, startsTs)
}

@Serializable
private data class MarketPageDto(val items: List<MarketDto> = emptyList(), val next: String? = null)

@Serializable
private data class FeeDto(val coefficient: String? = null, val makerCredit: String? = null, val charged: String? = null) {
    fun toDomain(): MarketFee? {
        val c = coefficient?.toDoubleOrNull() ?: return null
        val m = makerCredit?.toDoubleOrNull() ?: 0.0
        val charge = when (charged) {
            "ALWAYS" -> FeeCharge.ALWAYS
            "WHEN_LIVE" -> FeeCharge.WHEN_LIVE
            else -> return null
        }
        return MarketFee(c, m, charge)
    }
}

@Serializable
private data class OutcomeDto(val outcomeId: String, val name: String = "", val status: String = "TBD")

@Serializable
private data class MarketDto(
    val marketId: String,
    val eventId: String = "",
    val marketType: String = "",
    val status: String = "",
    val description: String = "",
    val startsTs: Long = 0,
    val fee: FeeDto? = null,
    val outcomes: List<OutcomeDto> = emptyList(),
) {
    fun toDomain() = NovigMarket(
        marketId = marketId,
        eventId = eventId,
        marketType = marketType,
        status = status,
        description = description,
        startsTs = startsTs,
        fee = fee?.toDomain(),
        outcomes = outcomes.map { NovigOutcome(it.outcomeId, it.name, it.status) },
    )
}

@Serializable
private data class RestingOrderDto(val orderId: String = "", val price: String, val qty: Long)

@Serializable
private data class BookDto(
    val marketId: String,
    val seq: Long = 0,
    val orders: Map<String, List<RestingOrderDto>> = emptyMap(),
) {
    fun toDomain(now: Long) = NovigBook(
        marketId = marketId,
        seq = seq,
        bidsByOutcome = orders.mapValues { (_, list) -> aggregate(list) },
        fetchedAtMs = now,
    )

    /** Collapses individual orders into price levels, best (highest) bid first. */
    private fun aggregate(orders: List<RestingOrderDto>): List<BidLevel> {
        val byPrice = HashMap<Int, Long>()
        for (o in orders) {
            val milli = NovigText.priceMilli(o.price) ?: continue
            if (o.qty > 0) byPrice[milli] = (byPrice[milli] ?: 0L) + o.qty
        }
        return byPrice.entries.sortedByDescending { it.key }.map { BidLevel(it.key, it.value) }
    }
}
