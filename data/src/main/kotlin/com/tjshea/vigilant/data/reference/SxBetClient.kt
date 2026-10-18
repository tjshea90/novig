package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * SX Bet's REST API (SXBET_API.md; docs read 2026-10-18). Markets, sports and scores are PUBLIC (500 requests a minute per IP, shared by `/markets`, `/leagues`, `/live-scores`...);
 * the books need an API key in `x-sx-api-key` (scope "Market data: read" only; Vigilant never trades on SX Bet): `GET /orders-v3/odds/best` takes 100 market hashes per call at 5,500 a minute.
 * With no key the source gives nothing (markets without prices are not a fair line). A 429 stands the source down for its `Retry-After`.
 */
class SxBetClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val key: () -> String,
    private val usage: UsageMeter? = null,
    private val baseUrl: String = "https://api.sx.bet",
    private val clock: () -> Long = System::currentTimeMillis,
    /** The socket switch (Settings › APIs › Sockets): on = keep the WebSocket and read books from it; off = REST every scan. */
    private val socketOn: () -> Boolean = { false },
    private val scope: kotlinx.coroutines.CoroutineScope? = null,
    private val wsUrl: String = "wss://realtime.sx.bet/connection/websocket",
) : ReferenceSource {
    override val id = SxBetConvert.BOOK_KEY
    override val displayName = "SX Bet"

    /** The real-time socket (null without a scope). Opened by [odds] while the switch is on and a key is saved; closed when either goes. */
    val stream: com.tjshea.vigilant.data.sxbet.SxBetStream? = scope?.let {
        com.tjshea.vigilant.data.sxbet.SxBetStream(http, json, key, it, clock, baseUrl, wsUrl, onSubscribed = { reseed() })
    }

    /** After every (re)subscription the channel has no history: read the watched markets once over REST and lay them on the board. */
    private suspend fun reseed() {
        val s = stream ?: return
        s.seed(books(s.watchedHashes(), viaStream = false).values)
    }

    @Volatile private var restUntil = 0L
    @Volatile private var failures = 0
    @Volatile private var lastOkMs = 0L

    @Volatile var lastError: String? = null
        private set

    /** What each league last read (Diagnostics). */
    val lastReads = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun hasKey() = key().isNotBlank()
    fun down(now: Long = clock()) = now < restUntil || (failures >= 3 && now - lastOkMs > 120_000L)
    fun health(now: Long = clock()) = when {
        !hasKey() -> "no key (markets are public, prices need a read-only key)"
        now < restUntil -> "resting after HTTP 429 for ${(restUntil - now) / 1000}s"
        lastOkMs == 0L -> "no answer yet"
        else -> "answering (last good answer ${(now - lastOkMs) / 1000}s ago)"
    }

    override fun supports(league: League) = league.sxLeagueIds.isNotEmpty() || league.tennis

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        fun empty() = RefSnapshot(league.oddsApiSportKey, emptyList(), now, provider = id)
        if (!hasKey() || down(now)) { stream?.close(); return empty() }
        if (stream != null) { if (socketOn()) stream.open() else stream.close() }
        return try {
            val horizon = now + (settings.daysAhead.coerceAtLeast(1) + 1) * 86_400_000L
            val markets = markets(league).filter { it.gameTime * 1000L in (now - 3_600_000L)..horizon }
            stream?.watch(markets.map { it.marketHash })
            val books = books(markets.map { it.marketHash })
            failures = 0; lastOkMs = clock(); lastError = null
            val events = SxBetConvert.events(markets, books, league, clock())
            lastReads[league.novigName] = "${markets.size} markets, ${books.size} books, ${events.size} games"
            RefSnapshot(league.oddsApiSportKey, events, clock(), provider = id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failures++; lastError = (e.message ?: e.javaClass.simpleName).take(200)
            throw if (e is ReferenceException) e else ReferenceException("SX Bet: $lastError")
        }
    }

    /** Every active market of [league], paged (the public route). */
    suspend fun markets(league: League): List<SxBetConvert.Market> {
        val out = ArrayList<SxBetConvert.Market>()
        val queries: List<List<Pair<String, String>>> =
            if (league.sxLeagueIds.isNotEmpty()) league.sxLeagueIds.map { listOf("leagueId" to it.toString()) } else listOf(listOf("sportIds" to "6"))
        for (q in queries) {
            var pageKey: String? = null
            var pages = 0
            do {
                val url = "$baseUrl/markets/active".toHttpUrl().newBuilder().apply {
                    q.forEach { (k, v) -> addQueryParameter(k, v) }
                    addQueryParameter("pageSize", "100")
                    pageKey?.let { addQueryParameter("paginationKey", it) }
                }.build()
                val (list, next) = SxBetConvert.parseMarkets(get(url.toString()), json)
                out += list
                pageKey = next
                pages++
            } while (pageKey != null && pages < MAX_PAGES)
        }
        return if (league.tennis) out.filter { SxBetConvert.tourOf(it.leagueLabel) == league.novigName } else out
    }

    /**
     * Every market of one SX game (the public `eventId` filter, 100 a page): what the live feed reads for a game that has just gone live (TCL118). Empty when SX has no such game.
     */
    suspend fun marketsOfEvent(eventId: String): List<SxBetConvert.Market> {
        val out = ArrayList<SxBetConvert.Market>()
        var pageKey: String? = null
        var pages = 0
        do {
            val url = "$baseUrl/markets/active".toHttpUrl().newBuilder().apply {
                addQueryParameter("eventId", eventId)
                addQueryParameter("pageSize", "100")
                pageKey?.let { addQueryParameter("paginationKey", it) }
            }.build()
            val (list, next) = SxBetConvert.parseMarkets(get(url.toString()), json)
            out += list
            pageKey = next
            pages++
        } while (pageKey != null && pages < MAX_PAGES)
        return out
    }

    /** Top of book (converted to the taker's asks, [SxBetConvert.takerBook]) for [hashes], 100 per call, a few calls at once. */
    suspend fun books(hashes: List<String>, viaStream: Boolean = true): Map<String, SxBetConvert.Book> {
        // While the socket is live its board is current for every market it has heard; the ones it has not are read once and laid on it (no change since the subscription = still true).
        val s = stream?.takeIf { viaStream && socketOn() && it.live }
        if (s != null) {
            val now = clock()
            val have = hashes.distinct().mapNotNull { h -> s.books[h]?.let { b -> s.currentAt(b, now) } }.associateBy { it.marketHash }
            val missing = hashes.distinct().filter { it !in have }
            if (missing.isEmpty()) return have
            val read = books(missing, viaStream = false)
            s.seed(read.values)
            return have + read
        }
        val gate = Semaphore(3)
        val parts = coroutineScope {
            hashes.distinct().chunked(100).map { chunk ->
                async {
                    gate.withPermit {
                        val url = "$baseUrl/orders-v3/odds/best".toHttpUrl().newBuilder()
                            .addQueryParameter("marketHashes", chunk.joinToString(",")).build()
                        SxBetConvert.parseBest(get(url.toString(), withKey = true), json, clock())
                    }
                }
            }.awaitAll()
        }
        return parts.flatten().associateBy { it.marketHash }
    }

    /** One GET. Throws [ReferenceException] on a refusal; a 429 rests the whole source for its Retry-After. */
    suspend fun get(url: String, withKey: Boolean = false): String {
        val req = Request.Builder().url(url).header("Accept", "application/json").apply { if (withKey) header("x-sx-api-key", key().trim()) }.get().build()
        return http.newCall(req).await().use { r ->
            val body = r.body?.string().orEmpty()
            usage?.countKeyless(QuotaPolicy.SXBET, calls = 1, throttled = if (r.code == 429) 1 else 0)
            when {
                r.code == 429 -> {
                    val wait = (r.header("Retry-After")?.trim()?.toDoubleOrNull() ?: 30.0).coerceIn(1.0, 300.0)
                    restUntil = clock() + (wait * 1000).toLong()
                    throw ReferenceException("SX Bet HTTP 429, resting ${wait.toInt()}s")
                }
                r.code == 401 || r.code == 403 -> throw ReferenceException("SX Bet HTTP ${r.code}: key refused or missing the Market data: read scope")
                !r.isSuccessful -> throw ReferenceException("SX Bet HTTP ${r.code}")
                else -> body
            }
        }
    }

    companion object {
        const val MAX_PAGES = 10
    }
}
