package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

    /** One event by id (its league and "Away @ Home" description), or null when Novig no longer lists it. */
    suspend fun event(eventId: String): NovigEvent? = null

    /** Drops what this source keeps only to save a request (the last books, for "not modified" answers): the heap is nearly full. */
    fun trimCaches() {}

    /** How many books this source keeps (Diagnostics' memory block). */
    fun cachedBooks(): Int = 0

    /**
     * The markets a scan will price, most important first: a source that can have them pushed (the
     * connected key's websocket) starts on them now. Nothing by default.
     */
    fun watch(marketIds: Collection<String>) {}

    /** Opens the live feed (if this source has one) without changing what it watches: its throttle refills while a scan's plan fills in. */
    fun openFeed() {}

    /** Books among [marketIds] this source holds current right now, with no request. None by default. COMPLETE books only (a full ladder); see [pushedTop]. */
    fun pushed(marketIds: Collection<String>): Map<String, NovigBook> = emptyMap()

    /**
     * Like [pushed], but also the markets the feed holds as their best bid only ([NovigBook.partial]): exact for the price to buy and for what is bid, never for depth. For screening a scan's
     * prices; a bet that is worth making asks for the complete book ([deepen]). Same as [pushed] for a source with no best-bid channel.
     */
    fun pushedTop(marketIds: Collection<String>): Map<String, NovigBook> = pushed(marketIds)

    /** Moves these markets to the live feed's full book (a no-op for a source with no best-bid channel). */
    fun deepen(marketIds: Collection<String>) {}

    /**
     * Makes the best bids the catalog carries for [marketIds] current for [pushedTop] (re-reads the catalog pages they came from when those are older
     * than a few seconds): a no-op for a source with no such catalog. Cheap: one request lists up to 5,000 markets' best bids.
     */
    suspend fun refreshTops(marketIds: Collection<String>) {}

    /** How many markets the live feed can hold at once: what a scan may hand it. */
    fun watchRoom(): Int = com.tjshea.vigilant.data.novig.stream.NovigStream.MAX_MARKETS

    /** Why pushed books stopped, if they did at or after [sinceMs] (the scan says so once). */
    fun pushProblem(sinceMs: Long): String? = null

    /**
     * Why the connected key isn't reading Novig's prices right now ([at]), when an earlier refusal put this source on the public routes for a
     * while: a scan that starts then never tries the key, so it says why itself (Tj, 2026-10-02: "the novig scanning was going very slow").
     */
    fun keyDown(at: Long): String? = null

    /** The key route's recent stand-downs (when, for how long, why), oldest first: what Diagnostics says about a slow stretch. Empty from a source with no key. */
    fun keyStanddowns(): List<KeyStanddown> = emptyList()

    /**
     * How many books a scan asks for at a time, between re-plans: enough that every request slot stays busy
     * ([DEFAULT_BATCH] on the public routes; more with a key, which has more in flight).
     */
    fun batchSize(): Int = DEFAULT_BATCH

    companion object {
        const val DEFAULT_BATCH = 8
    }
}

/** The key route stood down at [atMs] for [forMs] because of [why] ("HTTP 451 ANONYMIZED_NETWORK"): the reads in between went to the public routes. */
data class KeyStanddown(val atMs: Long, val forMs: Long, val why: String)

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
    /** Books the key's websocket already held current: served with no request (counted in [fetched] too). */
    val viaPush: Int = 0,
    /** Requests Novig refused (a 429, or its edge's 403), retried or not: each pauses the reads and slows them. */
    val refused: Int = 0,
    /** What paced this batch: each route's pace and the key route's refusals; null from a source with no pacing of its own. */
    val pace: ReadPace? = null,
    /**
     * Books whose market Novig no longer lists (a 404 on the book: its game just started, or the market closed). Neither a failure nor a verdict on the
     * key, and never served from the cache: a closed market's last price isn't a price.
     */
    val gone: Int = 0,
)

/**
 * What paced Novig reads (Tj, 2026-10-04: "the vigilant scanner slows down significantly when it is scanning novig prices, maybe down to 2 per second"):
 * each route's pace in reads a second when the reads began, the lowest a refusal took it to (null: none did), and when they ended, and the 429s the
 * key route drew (the public route's are [BookBatch.refused]). One batch's, or a scan's ([merge]).
 */
data class ReadPace(
    val publicStart: Double,
    val publicLow: Double?,
    val publicEnd: Double,
    val keyedStart: Double,
    val keyedLow: Double?,
    val keyedEnd: Double,
    val keyedRefused: Int,
) {
    /** This, then [next]: where it began, the lowest either reached, where [next] ended, the refusals added. */
    fun merge(next: ReadPace) = ReadPace(
        publicStart, listOfNotNull(publicLow, next.publicLow).minOrNull(), next.publicEnd,
        keyedStart, listOfNotNull(keyedLow, next.keyedLow).minOrNull(), next.keyedEnd, keyedRefused + next.keyedRefused,
    )
}

class NovigHttpException(val code: Int, message: String, val retryAfterSeconds: Int? = null) : IOException(message)

/**
 * Novig's REST routes (NOVIG_API.md §5), verified live 2026-09-25. Public routes need no key.
 *
 * Rate limits (Tj's phone got `429`s from v0.5.0's 15-second polling):
 *  - Every public request goes through one [RateGate]: [publicRate]/s to start, bursts of
 *    [publicBurst], [publicConcurrency] books at a time. Measured edge limit: ~40–100 fast
 *    requests, then `429 Retry-After: 1`, per IP; a steady 10/s drew 429s, ~5/s never did, and a
 *    carrier IP may be shared (NOVIG_API.md §5.1). Clean runs raise the pace by 0.5/s every 40
 *    books up to [publicMaxRate] (6/s, well under the 10/s that failed); any refusal drops it back.
 *  - A `429` pauses every request for its Retry-After and halves the rate for a minute. Each book
 *    gets two paced retries; a long Retry-After or an edge `403` stops the batch, and every book
 *    not refreshed is served from the last scan's copy.
 *  - With a key connected ([keyed]), books come from the signed route instead, which draws on the
 *    key's own `read` bucket (64 burst, 16/s refill, documented) rather than the shared public
 *    edge, paced at [keyedRate]/s with bursts of [keyedBurst]: under both numbers. If the key
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
    publicMaxRate: Double = 6.0,
    publicBurst: Int = 10,
    // Three in flight: at a phone's ~300–500ms per request, two couldn't even reach 6/s.
    private val publicConcurrency: Int = 3,
    keyedRate: Double = 14.0,
    keyedBurst: Int = 40,
    // Ten in flight: at a phone's ~400-700 ms for a signed request, six topped out under the key's 14/s. Needs an
    // OkHttpClient that allows it ([com.tjshea.vigilant.data.vigilantHttpClient]; OkHttp's own default is 5 a host).
    private val keyedConcurrency: Int = com.tjshea.vigilant.data.NOVIG_KEYED_IN_FLIGHT,
    /** Pacing runs on real time even when [clock] is faked for timestamps. */
    private val rateClock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    /** Counts every request (and throttle) for the usage meter. */
    private val usage: UsageMeter? = null,
    /** Server errors in a row ([MAX_KEYED_SERVER_ERRORS]) and 404s in a row ([MAX_KEYED_NOT_FOUND]) on the key route before it stands down; tests lower them. */
    private val maxKeyedServerErrors: Int = MAX_KEYED_SERVER_ERRORS,
    private val maxKeyedNotFound: Int = MAX_KEYED_NOT_FOUND,
) : NovigSource {

    private val publicGate = RateGate(publicRate, publicBurst, rateClock, sleep, maxRate = publicMaxRate)

    /** The key's `read` bucket, modeled (docs: api/throttling); re-set from `GET /v3/limits` ([ensureLimits]). */
    @Volatile
    private var keyedGate = RateGate(keyedRate, keyedBurst, rateClock, sleep)

    /**
     * The key's own throttle schedule, read once per key from `GET /v3/limits` (free: 0 tokens) the first time
     * the key is used: the keyed pace and the websocket follow Novig's real numbers, not the documented defaults
     * (docs: "Model each throttle in your client … GET /v3/limits reports every bucket's numbers"). Null until
     * then, or when Novig didn't answer (the defaults stand).
     */
    @Volatile
    var limits: NovigLimits? = null
        private set

    /** The key [limits] was asked for (once per key per process, answered or not). */
    @Volatile
    private var limitsAskedFor: NovigSignedClient? = null

    /** Signs book requests with the connected read-only key. Null = public routes only. */
    @Volatile
    var keyed: NovigSignedClient? = null

    /**
     * The connected key's websocket ([com.tjshea.vigilant.data.novig.stream.NovigStream]): books it holds
     * are served with no request, the rest by REST. Null = none (no key).
     */
    @Volatile
    var stream: com.tjshea.vigilant.data.novig.stream.PushedBooks? = null

    /** Only while the key route itself is usable: a VPN or location refusal stops both. */
    private fun liveStream() = stream?.takeIf { keyed != null && clock() >= keyedDownUntil }

    override fun trimCaches() {
        bookCache.clear()
        catalogTops.clear()
        topQuery.clear()
    }

    override fun cachedBooks(): Int = bookCache.size

    override fun watch(marketIds: Collection<String>) {
        liveStream()?.watch(marketIds)
    }

    override fun openFeed() {
        liveStream()?.open()
    }

    override fun pushed(marketIds: Collection<String>): Map<String, NovigBook> =
        if (marketIds.isEmpty()) emptyMap() else liveStream()?.live(marketIds).orEmpty()

    override fun pushedTop(marketIds: Collection<String>): Map<String, NovigBook> {
        if (marketIds.isEmpty()) return emptyMap()
        val pushed = liveStream()?.liveTop(marketIds).orEmpty()
        if (catalogTops.isEmpty()) return pushed
        // The feed's push is newer than any catalog read; the catalog's best bids fill in the markets the feed does not hold yet, while they are young.
        val cut = clock() - CATALOG_TOP_MAX_AGE_MS
        val out = HashMap(pushed)
        for (id in marketIds) if (id !in out) catalogTops[id]?.takeIf { it.fetchedAtMs >= cut }?.let { out[id] = it }
        return out
    }

    override fun deepen(marketIds: Collection<String>) {
        if (marketIds.isNotEmpty()) liveStream()?.deepen(marketIds)
    }

    override fun watchRoom(): Int = liveStream()?.room() ?: com.tjshea.vigilant.data.novig.stream.NovigStream.MAX_MARKETS

    override fun pushProblem(sinceMs: Long): String? = stream?.problemSince(sinceMs)

    /** Three waves of the key's requests in flight; the public routes' 8 otherwise. */
    override fun batchSize(): Int =
        if (keyed != null && clock() >= keyedDownUntil) keyedConcurrency * 3 else NovigSource.DEFAULT_BATCH

    /** After the key route fails, stay on public routes this long before trying it again. */
    private val keyedRetryMs = 10 * 60_000L

    /**
     * How long the public routes stand in after the key route failed with [e]: Novig's verdict on the network's address (`451 ANONYMIZED_NETWORK`,
     * a restricted region) flaps as a carrier moves the phone between addresses (Tj's v0.52.0 file: 51 such refusals in bursts, each sending every
     * read to the public routes at a third of the pace for 10 minutes), so it's tried again after [NETWORK_RETRY_MS]; no connection at all after
     * [NO_CONNECTION_RETRY_MS]; anything else (a revoked key, a 423 lock) after [keyedRetryMs].
     */
    private fun keyRetryAfter(e: Throwable): Long = when {
        e is NovigApiException && e.networkRefusal -> NETWORK_RETRY_MS
        e !is NovigApiException && e is IOException -> NO_CONNECTION_RETRY_MS
        e is NovigApiException && e.status in 500..599 -> NO_CONNECTION_RETRY_MS
        else -> keyedRetryMs
    }

    /** Server errors in a row on the key route since one last read cleanly: [MAX_KEYED_SERVER_ERRORS] of them take it down ([standDown]). */
    private val keyedServerErrors = AtomicInteger(0)

    /**
     * 404s in a row on the key route since one last read cleanly: a game's every market closing together is a run of dozens, but [MAX_KEYED_NOT_FOUND] means
     * the route itself is gone, and then it stands down like any other refusal.
     */
    private val keyedNotFound = AtomicInteger(0)

    /** The key route's stand-downs, newest last (a short list for Diagnostics: why a scan went to the slower public routes and for how long). */
    private val standdownLog = Collections.synchronizedList(ArrayList<KeyStanddown>())

    override fun keyStanddowns(): List<KeyStanddown> = synchronized(standdownLog) { standdownLog.toList() }

    /** Told of each stand-down as it happens (the app writes it to its event log: Diagnostics' timeline keeps the last day's, this list only the process's). */
    @Volatile
    var onStanddown: ((KeyStanddown) -> Unit)? = null

    /** The key route (or its catalog) stands down for however long [keyRetryAfter] says [e] deserves: [set] gets when it is tried again, and it is logged. */
    private fun standDown(e: Throwable, set: (Long) -> Unit) {
        val now = clock()
        val forMs = keyRetryAfter(e)
        set(now + forMs)
        val why = (e as? NovigApiException)?.let { "HTTP ${it.status}" + (it.code?.let { c -> " $c" } ?: "") } ?: (e.message ?: e.javaClass.simpleName)
        val entry = KeyStanddown(now, forMs, why)
        synchronized(standdownLog) {
            standdownLog += entry
            while (standdownLog.size > MAX_STANDDOWNS) standdownLog.removeAt(0)
        }
        runCatching { onStanddown?.invoke(entry) }
    }

    @Volatile
    private var keyedDownUntil = 0L

    /** What the key's last refusal said ([keyDown]). */
    @Volatile
    private var keyDownWhy: String? = null

    override fun keyDown(at: Long): String? =
        keyDownWhy?.takeIf { keyed != null && (at < keyedDownUntil || at < keyedCatalogDownUntil) }

    private data class CachedBook(val etag: String?, val book: NovigBook)

    /** Bounded LRU of the last books seen, keyed by market ID. */
    private val bookCache: MutableMap<String, CachedBook> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedBook>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBook>) = size > MAX_CACHED_BOOKS
        },
    )

    override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?): List<NovigEvent> =
        catalog("/v3/catalog/events", "/v3/public/catalog/events", buildMap {
            if (leagues.isNotEmpty()) put("league", leagues.joinToString(","))
            if (statuses.isNotEmpty()) put("status", statuses.joinToString(","))
            startsBefore?.let { put("startsBefore", it.toString()) }
            put("limit", "1000")
        }) { body, _ -> json.decodeFromString(EventPageDto.serializer(), body).let { it.items.map(EventDto::toDomain) to it.next } }

    override suspend fun markets(
        leagues: Collection<String>,
        marketTypes: Collection<String>,
        eventStatuses: Collection<String>,
        startsBefore: Long?,
    ): List<NovigMarket> = marketsBy(buildMap {
        if (leagues.isNotEmpty()) put("league", leagues.joinToString(","))
        if (marketTypes.isNotEmpty()) put("marketType", marketTypes.joinToString(","))
        if (eventStatuses.isNotEmpty()) put("eventStatus", eventStatuses.joinToString(","))
        startsBefore?.let { put("startsBefore", it.toString()) }
        put("limit", "5000")
    })

    private suspend fun marketsBy(params: Map<String, String>): List<NovigMarket> {
        val readAt = clock()
        return catalog("/v3/catalog/markets", "/v3/public/catalog/markets", params) { body, signed ->
            val page = json.decodeFromString(MarketPageDto.serializer(), body)
            if (signed) keepTops(page.items, params, readAt)
            page.items.map(MarketDto::toDomain) to page.next
        }
    }

    /**
     * Each outcome's best resting bid as the catalog lists it (since 2026-10-20: `outcomes[].bid {price, qty}`, NOVIG_API.md §28), kept as a best-bid-only book ([NovigBook.partial]) stamped
     * at the read: what one request says of up to 5,000 markets, where a scan used to read ~9,600 books one by one (AF, 2026-10-22: 13,727 prices in 420 s). Only from the SIGNED route
     * (the public one is cached at Novig's edge, so its age is unknown), and only from a page that carries the field at all (absent = no bid rests, so a page with none anywhere says
     * nothing about the feature).
     */
    private fun keepTops(items: List<MarketDto>, params: Map<String, String>, readAt: Long) {
        if (items.none { m -> m.outcomes.any { it.bid != null } }) return
        for (m in items) {
            catalogTops[m.marketId] = m.topBook(readAt)
            topQuery[m.marketId] = params
        }
        topReadAt[params] = readAt
    }

    private val catalogTops = java.util.concurrent.ConcurrentHashMap<String, NovigBook>()
    private val topQuery = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private val topReadAt = java.util.concurrent.ConcurrentHashMap<Map<String, String>, Long>()
    private val topLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun refreshTops(marketIds: Collection<String>) {
        if (keyed == null || clock() < keyedDownUntil || clock() < keyedCatalogDownUntil) return
        val now = clock()
        val stale = marketIds.mapNotNull { topQuery[it] }.distinct().filter { q -> now - (topReadAt[q] ?: 0L) >= CATALOG_TOP_REFRESH_MS }
        if (stale.isEmpty()) return
        topLock.withLock {
            for (q in stale) {
                if (clock() - (topReadAt[q] ?: 0L) < CATALOG_TOP_REFRESH_MS) continue
                try { marketsBy(q) } catch (e: CancellationException) { throw e } catch (_: Exception) { topReadAt[q] = clock() }
            }
            // Never let the tops outgrow what a scan can use: a market not re-listed for a while has left the catalog.
            val cut = clock() - 10 * CATALOG_TOP_MAX_AGE_MS
            catalogTops.entries.removeIf { it.value.fetchedAtMs < cut }
            topQuery.keys.retainAll(catalogTops.keys)
        }
    }

    /** A market looked up one at a time, and when: the same market is asked for by the paper lab's grading, the CNO live prices and the settler, over and over (Tj's 2026-10-13 file: 1,186 of 7,780 public calls refused). */
    private class Looked(val at: Long, val market: NovigMarket?)
    private val lookedUp = object : LinkedHashMap<String, Looked>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Looked>?) = size > LOOKUP_KEEP
    }

    /**
     * One market by id, from the public route (the per-IP edge limit applies). A recent answer is re-used ([LOOKUP_TTL_MS]; a market Novig no longer lists, [LOOKUP_GONE_TTL_MS]), a 429 slows the
     * public pace and waits out its Retry-After like every other public read (it used to be thrown, with the pace unchanged, so the next lookups were refused too), and one retry follows.
     */
    override suspend fun market(marketId: String): NovigMarket? {
        synchronized(lookedUp) { lookedUp[marketId] }?.let { k -> if (clock() - k.at < (if (k.market == null) LOOKUP_GONE_TTL_MS else LOOKUP_TTL_MS)) return k.market }
        repeat(2) { attempt ->
            val request = Request.Builder().url("$baseUrl/v3/public/catalog/markets/$marketId").get().build()
            publicGate.acquire()
            http.newCall(request).await().use { response ->
                count(response.code)
                if (response.code == 404) { synchronized(lookedUp) { lookedUp[marketId] = Looked(clock(), null) }; return null }
                val body = response.body?.string().orEmpty()
                if (response.code == 429 && attempt == 0) {
                    val wait = response.header("Retry-After")?.trim()?.toLongOrNull() ?: 1L
                    publicGate.pause(rateClock() + wait * 1000L)
                    publicGate.slowDown()
                    return@use
                }
                if (!response.isSuccessful) {
                    if (response.code == 429) { publicGate.pause(rateClock() + (response.header("Retry-After")?.trim()?.toLongOrNull() ?: 1L) * 1000L); publicGate.slowDown() }
                    throw httpError(response.code, body, response.header("Retry-After"))
                }
                return json.decodeFromString(MarketDto.serializer(), body).toDomain().also { m -> synchronized(lookedUp) { lookedUp[marketId] = Looked(clock(), m) } }
            }
        }
        return null
    }

    override suspend fun event(eventId: String): NovigEvent? {
        val request = Request.Builder().url("$baseUrl/v3/public/catalog/events/$eventId").get().build()
        publicGate.acquire()
        http.newCall(request).await().use { response ->
            count(response.code)
            if (response.code == 404) return null
            val body = response.body?.string().orEmpty()
            if (response.code == 429) { publicGate.pause(rateClock() + (response.header("Retry-After")?.trim()?.toLongOrNull() ?: 1L) * 1000L); publicGate.slowDown() }
            if (!response.isSuccessful) throw httpError(response.code, body, response.header("Retry-After"))
            return json.decodeFromString(EventDto.serializer(), body).toDomain()
        }
    }

    /**
     * [marketId]'s newest trades, newest first (at most [limit]): what the trap guard reads before an auto-bet on a game line ([com.tjshea.vigilant.data.scanner.TrapGuard.move]).
     * Verified live 2026-10-03 against Novig's trade file (NOVIG_API.md §5): each item is the RESTING order's outcome and price, `qty` in contracts.
     * One public request, paced with the rest; a market Novig no longer lists is none.
     */
    suspend fun trades(marketId: String, limit: Int = TRADES_LIMIT): List<com.tjshea.vigilant.data.scanner.TrapGuard.Trade> {
        val request = Request.Builder().url("$baseUrl/v3/public/catalog/markets/$marketId/trades?limit=$limit").get().build()
        publicGate.acquire()
        http.newCall(request).await().use { response ->
            count(response.code)
            if (response.code == 404) return emptyList()
            val body = response.body?.string().orEmpty()
            if (response.code == 429) { publicGate.pause(rateClock() + (response.header("Retry-After")?.trim()?.toLongOrNull() ?: 1L) * 1000L); publicGate.slowDown() }
            if (!response.isSuccessful) throw httpError(response.code, body, response.header("Retry-After"))
            return json.decodeFromString(TradePageDto.serializer(), body).items.mapNotNull { it.toDomain() }
        }
    }

    /** The last book seen for [marketId], without any network. */
    fun cached(marketId: String): NovigBook? = bookCache[marketId]?.book

    /** After the key's catalog route fails, the public one is used this long. */
    @Volatile
    private var keyedCatalogDownUntil = 0L

    /**
     * Novig's board: through the connected key's signed catalog (its own `read` bucket, not the per-IP
     * public throttle a carrier's shared address splits with strangers; docs: api/throttling), else, or
     * if that fails, the public routes. Same parameters and pages either way.
     */
    private suspend fun <T> catalog(signedPath: String, publicPath: String, params: Map<String, String>, parse: (String, Boolean) -> Pair<List<T>, String?>): List<T> {
        val signer = keyed?.takeIf { clock() >= keyedDownUntil && clock() >= keyedCatalogDownUntil }
        if (signer != null) {
            ensureLimits(signer)
            try {
                return paged(signedPath, params, { parse(it, true) }, signer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                standDown(e) { keyedCatalogDownUntil = it }
                keyDownWhy = (e as? NovigApiException)?.brief ?: e.message ?: e.javaClass.simpleName
            }
        }
        return paged(publicPath, params, { parse(it, false) })
    }

    /**
     * Reads the key's throttle schedule once ([limits]) and paces by it: keyed reads at 90% of the `read`
     * bucket's refill with 70% of its capacity as burst (the edge also counts every request), and the
     * websocket told its `stream` bucket and watch cap. A failure keeps the documented defaults.
     */
    private suspend fun ensureLimits(signer: NovigSignedClient) {
        if (limitsAskedFor === signer) return
        limitsAskedFor = signer
        val l = try {
            http.newCall(signer.signedRequest("GET", "/v3/limits")).await().use { response ->
                count(response.code)
                if (!response.isSuccessful) return
                json.decodeFromString(LimitsDto.serializer(), response.body?.string().orEmpty()).toDomain() ?: return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        limits = l
        keyedGate = RateGate((l.readPerSec * 0.9).coerceAtLeast(1.0), (l.readCapacity * 0.7).toInt().coerceAtLeast(1), rateClock, sleep)
        stream?.tune(l.streamCapacity, l.streamPerSec, l.maxWatchedMarkets)
    }

    override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch = coroutineScope {
        val all = marketIds.distinct()
        // Books the websocket keeps current need no request; the rest are read one by one.
        // A read that only needs each side's best price (the Tracker's open bets) takes a market the feed holds as its best bid; every other read wants a complete ladder.
        val pushed = if (coroutineContext[NovigScreening] != null) pushedTop(all) else pushed(all)
        // Never a best-bid-only book into the cache: a failed read later falls back to it, and that is not a ladder.
        for ((id, book) in pushed) if (!book.partial) bookCache[id] = CachedBook(null, book.copy(lifecycle = null))
        val ids = all.filter { it !in pushed }
        NovigReads.add(coroutineContext[NovigReadTag]?.tag ?: "other", ids.size)
        val signer = keyed?.takeIf { clock() >= keyedDownUntil }
        if (signer != null && ids.isNotEmpty()) ensureLimits(signer)
        val publicStart = publicGate.currentRate
        val keyedStart = keyedGate.currentRate
        val keyedRefused = AtomicInteger(0)
        val useKey = AtomicReference(signer)
        val keyProblem = AtomicReference<String?>(null)
        val gate = Semaphore(if (signer != null) keyedConcurrency else publicConcurrency)
        val stop = AtomicReference<NovigHttpException?>(null)
        // Refusals, counted once per burst: every book in flight when Novig says no comes back refused together.
        val throttleHits = AtomicInteger(0)
        val lastRefusal = AtomicLong(Long.MIN_VALUE / 2)
        val refused = AtomicInteger(0)
        val done = AtomicInteger(pushed.size)
        onProgress?.invoke(pushed.size, all.size)
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
                                return@run fetchBook(id, key).also { rate.success(); if (key != null) { keyedServerErrors.set(0); keyedNotFound.set(0) } }
                            } catch (e: NovigApiException) {
                                // The key route refused (VPN, stale location check, revoked key):
                                // finish this scan on the public routes and say why once.
                                if (e.status == 429) {
                                    keyedGate.pause(rateClock() + 1000L)
                                    keyedGate.slowDown()
                                    if (retries++ < 2) continue
                                    return@run BookFetch.Failed(id, e.advice)
                                }
                                // One market's own answer is not Novig's verdict on the key (Tj's v0.59.1 file: a 404 for a market that closed as its game kicked
                                // off, at 13:12:11 and again at 16:01:07, sent the rest of the scan and every read for the next ten minutes to the public routes
                                // at 2-4 a second, the 429s of a carrier's shared address included): that book is gone, and the key route goes on.
                                if (e.status == 404 && keyedNotFound.incrementAndGet() < maxKeyedNotFound) return@run BookFetch.Gone(id)
                                // The same for one server error: only a run of them (Novig having a bad moment) takes the key route down, and briefly.
                                if (e.status in 500..599 && e.code != GEOLOCATION_DOWN && keyedServerErrors.incrementAndGet() < maxKeyedServerErrors) {
                                    return@run BookFetch.Failed(id, e.advice)
                                }
                                if (useKey.getAndSet(null) != null) {
                                    standDown(e) { keyedDownUntil = it }
                                    keyDownWhy = e.brief
                                    keyProblem.compareAndSet(null, e.brief)
                                }
                                continue
                            } catch (e: NovigHttpException) {
                                // The public route's 404 is the same news: the market is gone (its cached book is not served either).
                                if (e.code == 404) return@run BookFetch.Gone(id)
                                val retryAfter = e.retryAfterSeconds ?: 1
                                // Measured live 2026-09-25: the edge answers a burst with 429 and
                                // Retry-After: 1. Pause everyone, halve the pace, retry this book
                                // twice; anything still missing is served from the last scan. The
                                // refusals of one burst count once toward giving up.
                                if (e.code == 403) refused.incrementAndGet()
                                val hits = if (e.code != 429) 0 else {
                                    refused.incrementAndGet()
                                    // The key's own read bucket ran dry (not the shared public edge): counted apart, a scan says which route was refused.
                                    if (key != null) keyedRefused.incrementAndGet()
                                    val t = rateClock()
                                    if (t - lastRefusal.getAndSet(t) >= RateGate.SAME_BURST_MS) throttleHits.incrementAndGet() else throttleHits.get()
                                }
                                if (e.code == 429 && retries < 2 && retryAfter <= SHORT_RETRY_SECONDS && hits <= MAX_SHORT_RETRIES) {
                                    retries++
                                    rate.pause(rateClock() + retryAfter * 1000L)
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
                    onProgress?.invoke(done.incrementAndGet(), all.size)
                    outcome
                }
            }
        }.map { it.await() }

        val books = HashMap<String, NovigBook>(pushed)
        var notModified = 0
        var fetched = pushed.size
        var failed = 0
        var fromCache = 0
        var viaKey = 0
        var gone = 0
        var lastError: String? = null
        for (r in results) {
            when (r) {
                is BookFetch.Gone -> { gone++; bookCache.remove(r.marketId) }
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
            viaPush = pushed.size,
            refused = refused.get(),
            pace = ReadPace(
                publicStart, publicGate.takeLowRate(), publicGate.currentRate,
                keyedStart, keyedGate.takeLowRate(), keyedGate.currentRate, keyedRefused.get(),
            ),
            gone = gone,
        )
    }

    private sealed interface BookFetch {
        data class Fresh(val book: NovigBook, val viaKey: Boolean) : BookFetch
        data class NotModified(val book: NovigBook, val viaKey: Boolean) : BookFetch
        data class Failed(val marketId: String, val reason: String) : BookFetch
        data class Skipped(val marketId: String) : BookFetch
        data class Gone(val marketId: String) : BookFetch
    }

    private suspend fun fetchBook(marketId: String, key: NovigSignedClient?): BookFetch {
        val cached = bookCache[marketId]
        // The signed route reads the same book from the key's own `read` bucket. If-None-Match
        // isn't part of the NOVIG-V3 signature, so it's added after signing.
        val signed = key?.let {
            try {
                it.signedRequest("GET", "/v3/catalog/markets/$marketId/book")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The key can't sign on this phone (its Keystore entry is gone, e.g. after a
                // restore to a new phone). Treated like a refusal: this scan goes public.
                throw NovigApiException(0, "SIGNING_FAILED", KEY_CANT_SIGN)
            }
        }
        val base = signed?.newBuilder()
            ?: Request.Builder().url("$baseUrl/v3/public/catalog/markets/$marketId/book").get()
        val request = base.apply { cached?.etag?.let { header("If-None-Match", it) } }.build()
        http.newCall(request).await().use { response ->
            count(response.code)
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
        /** Signs each page with the key (the signed catalog), else the public route. */
        signer: NovigSignedClient? = null,
    ): List<T> {
        val all = ArrayList<T>()
        var after: String? = null
        var retries = 0
        var pages = 0
        while (pages < MAX_PAGES) {
            val query = (params + listOfNotNull(after?.let { "after" to it })).entries
                .joinToString("&") { (k, v) -> "${queryEncode(k)}=${queryEncode(v)}" }
            val gate = if (signer != null) keyedGate else publicGate
            gate.acquire()
            val request = signer?.signedRequest("GET", path, query) ?: Request.Builder().url("$baseUrl$path?$query").get().build()
            val page = http.newCall(request).await().use { response ->
                val body = response.body?.string().orEmpty()
                count(response.code)
                if (!response.isSuccessful) {
                    val e = httpError(response.code, body, response.header("Retry-After"))
                    // A burst limit (429, Retry-After of a second or so) on the board: wait it out
                    // and ask again, like a book, rather than fail the whole scan (2026-09-27 full test).
                    val wait = e.retryAfterSeconds ?: 1
                    if (e.code == 429 && wait <= SHORT_RETRY_SECONDS && retries < 2) {
                        retries++
                        gate.pause(rateClock() + wait * 1000L)
                        gate.slowDown()
                        return@use null
                    }
                    throw e
                }
                gate.success()
                parse(body)
            } ?: continue
            all += page.first
            after = page.second
            pages++
            if (after == null) return all
        }
        return all
    }

    private suspend fun count(code: Int) {
        if (code in 200..299) NovigHealth.okAtMs = System.currentTimeMillis()
        usage?.countKeyless(QuotaPolicy.NOVIG, calls = 1, throttled = if (code == 429 || code == 403) 1 else 0)
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
        /**
         * A query value as sent and as signed: everything but `A-Z a-z 0-9 - . _ ~` as `%XX` (NOVIG-V3's
         * canonical form, so the URL and the signature agree; an opaque `after` cursor may hold `+` or `/`).
         */
        fun queryEncode(s: String): String = buildString {
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                if ((c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code) || (c in '0'.code..'9'.code) || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) {
                    append(c.toChar())
                } else {
                    append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
                }
            }
        }

        const val KEY_CANT_SIGN = "The Novig key on this phone can't sign any more (normal after restoring to a new phone). " +
            "Disconnect it in Settings and connect it again."
        const val MAX_CACHED_BOOKS = 3000

        /** How many of a market's newest trades the trap guard reads: an hour of a busy game line (the route takes up to 500 and pages past that). */
        const val TRADES_LIMIT = 200

        /** Single-market lookups remembered: how many, for how long an answer is re-used, and a market Novig did not list. */
        const val LOOKUP_KEEP = 300
        const val LOOKUP_TTL_MS = 30_000L
        const val LOOKUP_GONE_TTL_MS = 120_000L

        /** The key route is tried again this soon after Novig refused the network's address ([keyRetryAfter]). */
        const val NETWORK_RETRY_MS = 2 * 60_000L

        /** … and this soon after a request that never reached Novig (no connection, a DNS failure). */
        const val NO_CONNECTION_RETRY_MS = 30_000L
        /** Server errors in a row on the key route before it stands down. */
        const val MAX_KEYED_SERVER_ERRORS = 6

        /** 404s in a row on the key route (no book read cleanly between) before it is taken for a dead route, not closed markets. */
        const val MAX_KEYED_NOT_FOUND = 200

        /** Novig's own code for its location screen being down (a verdict on the key route, not on one market). */
        const val GEOLOCATION_DOWN = "GEOLOCATION_SCREENING_UNAVAILABLE"

        /** Stand-downs kept for the Diagnostics file. */
        const val MAX_STANDDOWNS = 30
        const val SHORT_RETRY_SECONDS = 5
        const val MAX_SHORT_RETRIES = 8
        const val MAX_PAGES = 20

        /** A catalog best bid screens a scan's price this long after its read ([pushedTop]); a +EV one is read as a complete book before it is shown (Scanner.deepenEdges). */
        const val CATALOG_TOP_MAX_AGE_MS = 30_000L

        /** [refreshTops] re-reads a catalog query whose best bids are this old. */
        const val CATALOG_TOP_REFRESH_MS = 12_000L
    }
}

// ---- wire format (OpenAPI 3.1 spec, NOVIG_API.md §5) -------------------------------------------

@Serializable
private data class ErrorDto(val code: String? = null, val message: String? = null)

/** A key's throttle schedule (`GET /v3/limits`, docs: api/throttling): what Vigilant paces by. */
data class NovigLimits(
    val readCapacity: Int,
    val readPerSec: Double,
    val streamCapacity: Int,
    val streamPerSec: Double,
    val maxWatchedMarkets: Int,
)

@Serializable
private data class BucketDto(val capacity: Int? = null, val refillPerSec: Double? = null)

@Serializable
private data class LimitsDto(val read: BucketDto? = null, val stream: BucketDto? = null, val maxWatchedMarkets: Int? = null) {
    fun toDomain(): NovigLimits? {
        val r = read ?: return null
        val st = stream ?: return null
        return NovigLimits(
            readCapacity = r.capacity?.takeIf { it > 0 } ?: return null,
            readPerSec = r.refillPerSec?.takeIf { it > 0 } ?: return null,
            streamCapacity = st.capacity?.takeIf { it > 0 } ?: return null,
            streamPerSec = st.refillPerSec?.takeIf { it > 0 } ?: return null,
            maxWatchedMarkets = maxWatchedMarkets?.takeIf { it > 0 } ?: return null,
        )
    }
}

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

/** One market of Novig's catalog as the app models it (its fee, outcomes, …), or null if it can't be read. */
internal fun novigMarketOf(json: Json, element: kotlinx.serialization.json.JsonElement): NovigMarket? =
    runCatching { json.decodeFromJsonElement(MarketDto.serializer(), element).toDomain() }.getOrNull()

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
private data class BboDto(val price: String = "", val qty: Long = 0)

@Serializable
private data class OutcomeDto(
    val outcomeId: String,
    val name: String = "",
    val status: String = "TBD",
    /** The best resting bid on this outcome (catalog reads since 2026-10-20); absent when none rests. The ask on the other outcome is `1 - bid`. */
    val bid: BboDto? = null,
)

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
    /** The line, a decimal string; a spread's is the home side's handicap. Absent without a line. */
    val strike: String? = null,
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
        strike = strike?.toDoubleOrNull(),
    )

    /** The catalog's best bids as a best-bid-only book ([NovigBook.partial]): one level per outcome, none where no bid rests. */
    fun topBook(at: Long) = NovigBook(
        marketId = marketId,
        seq = 0L,
        bidsByOutcome = outcomes.associate { o ->
            o.outcomeId to listOfNotNull(o.bid?.let { b -> b.price.toDoubleOrNull()?.let { p -> Math.round(p * 1000).toInt() }?.takeIf { it in 1..999 && b.qty > 0 }?.let { BidLevel(it, b.qty) } })
        },
        fetchedAtMs = at,
        partial = true,
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

@Serializable
private data class TradePageDto(val items: List<TradeDto> = emptyList())

/** One trade as the public route gives it: the resting order's outcome and price (a string, "0.515"), [qty] contracts, [ts] epoch ms. */
@Serializable
private data class TradeDto(val tradeId: String = "", val outcomeId: String = "", val price: String = "", val qty: Long = 0, val ts: Long = 0) {
    fun toDomain(): com.tjshea.vigilant.data.scanner.TrapGuard.Trade? {
        val p = price.toDoubleOrNull()?.takeIf { it > 0.0 && it < 1.0 } ?: return null
        if (outcomeId.isBlank() || qty <= 0 || ts <= 0) return null
        return com.tjshea.vigilant.data.scanner.TrapGuard.Trade(outcomeId, p, qty, ts)
    }
}


/**
 * When Novig last answered a request successfully (Tj, 2026-10-14: "Make these errors refresh and disappear automatically as soon as they no longer apply"): an error a scan reported about Novig's prices stays on
 * screen only until a read after that scan succeeds, instead of until the next full scan ends.
 */
object NovigHealth {
    @Volatile var okAtMs: Long = 0L
}
