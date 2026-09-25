package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Collections

/** Everything the scanner needs from Novig. The public REST client implements it; tests fake it. */
interface NovigSource {
    suspend fun events(leagues: Collection<String>, statuses: Collection<String>): List<NovigEvent>
    suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>): List<NovigMarket>
    suspend fun books(marketIds: Collection<String>): BookBatch
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
)

class NovigHttpException(val code: Int, message: String, val retryAfterSeconds: Int? = null) : IOException(message)

/**
 * Novig's public, no-key, no-signature REST routes (NOVIG_API.md §5), verified live 2026-09-25.
 *
 * Battery/data notes for the Moto G target:
 *  - OkHttp negotiates gzip on its own: a full NCAAF game-line catalog is ~2.3MB raw, ~375KB gzipped.
 *  - Books use `If-None-Match` with the ETag Novig returns (`"<marketId>-<seq>"`), so an unchanged
 *    book costs a header-only 304 and no JSON parsing.
 *  - Book requests run [maxConcurrent] at a time. The edge throttles per IP, and a 429 stops the
 *    batch early, keeping the cached copy of every book not yet refreshed.
 */
class NovigPublicClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.novig.com",
    private val maxConcurrent: Int = 4,
    private val clock: () -> Long = System::currentTimeMillis,
) : NovigSource {

    private data class CachedBook(val etag: String?, val book: NovigBook)

    /** Bounded LRU of the last books seen, keyed by market ID. */
    private val bookCache: MutableMap<String, CachedBook> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedBook>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBook>) = size > MAX_CACHED_BOOKS
        },
    )

    override suspend fun events(leagues: Collection<String>, statuses: Collection<String>): List<NovigEvent> =
        paged("/v3/public/catalog/events", buildMap {
            if (leagues.isNotEmpty()) put("league", leagues.joinToString(","))
            if (statuses.isNotEmpty()) put("status", statuses.joinToString(","))
            put("limit", "1000")
        }) { body -> json.decodeFromString(EventPageDto.serializer(), body).let { it.items.map(EventDto::toDomain) to it.next } }

    override suspend fun markets(
        leagues: Collection<String>,
        marketTypes: Collection<String>,
        eventStatuses: Collection<String>,
    ): List<NovigMarket> =
        paged("/v3/public/catalog/markets", buildMap {
            if (leagues.isNotEmpty()) put("league", leagues.joinToString(","))
            if (marketTypes.isNotEmpty()) put("marketType", marketTypes.joinToString(","))
            if (eventStatuses.isNotEmpty()) put("eventStatus", eventStatuses.joinToString(","))
            put("limit", "5000")
        }) { body -> json.decodeFromString(MarketPageDto.serializer(), body).let { it.items.map(MarketDto::toDomain) to it.next } }

    override suspend fun market(marketId: String): NovigMarket? {
        val request = Request.Builder().url("$baseUrl/v3/public/catalog/markets/$marketId").get().build()
        http.newCall(request).await().use { response ->
            if (response.code == 404) return null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw httpError(response.code, body, response.header("Retry-After"))
            return json.decodeFromString(MarketDto.serializer(), body).toDomain()
        }
    }

    override suspend fun books(marketIds: Collection<String>): BookBatch = coroutineScope {
        val gate = Semaphore(maxConcurrent)
        val throttled = java.util.concurrent.atomic.AtomicReference<NovigHttpException?>(null)
        val results = marketIds.distinct().map { id ->
            async {
                gate.withPermit {
                    // Once Novig says slow down, stop sending. Serve whatever is cached instead.
                    if (throttled.get() != null) return@withPermit BookFetch.Skipped(id)
                    try {
                        fetchBook(id)
                    } catch (e: NovigHttpException) {
                        if (e.code == 429 || e.code == 403) throttled.compareAndSet(null, e)
                        BookFetch.Failed(id, e.message ?: "HTTP ${e.code}")
                    } catch (e: IOException) {
                        BookFetch.Failed(id, e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        }.map { it.await() }

        val books = HashMap<String, NovigBook>()
        var notModified = 0
        var fetched = 0
        var failed = 0
        var lastError: String? = null
        for (r in results) {
            when (r) {
                is BookFetch.Fresh -> { books[r.book.marketId] = r.book; fetched++ }
                is BookFetch.NotModified -> { books[r.book.marketId] = r.book; notModified++ }
                is BookFetch.Failed -> { failed++; lastError = r.reason; bookCache[r.marketId]?.let { books[r.marketId] = it.book } }
                is BookFetch.Skipped -> bookCache[r.marketId]?.let { books[r.marketId] = it.book }
            }
        }
        val t = throttled.get()
        BookBatch(books, notModified, fetched, failed, t?.retryAfterSeconds ?: t?.let { 10 }, lastError ?: t?.message)
    }

    private sealed interface BookFetch {
        data class Fresh(val book: NovigBook) : BookFetch
        data class NotModified(val book: NovigBook) : BookFetch
        data class Failed(val marketId: String, val reason: String) : BookFetch
        data class Skipped(val marketId: String) : BookFetch
    }

    private suspend fun fetchBook(marketId: String): BookFetch {
        val cached = bookCache[marketId]
        val request = Request.Builder()
            .url("$baseUrl/v3/public/catalog/markets/$marketId/book")
            .apply { cached?.etag?.let { header("If-None-Match", it) } }
            .get()
            .build()
        http.newCall(request).await().use { response ->
            if (response.code == 304 && cached != null) {
                val refreshed = cached.book.copy(fetchedAtMs = clock())
                bookCache[marketId] = cached.copy(book = refreshed)
                return BookFetch.NotModified(refreshed)
            }
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw httpError(response.code, body, response.header("Retry-After"))
            val book = json.decodeFromString(BookDto.serializer(), body).toDomain(clock())
            bookCache[marketId] = CachedBook(response.header("ETag"), book)
            return BookFetch.Fresh(book)
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
