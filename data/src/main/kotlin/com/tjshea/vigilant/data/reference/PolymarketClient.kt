package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Polymarket's public Gamma API (RESEARCH.md §11): free, no key, and deep on US game lines
 * (NFL moneylines carry $100k+ at a 1¢ spread). Its limit is 300 `/markets` requests per 10s,
 * and over-limit requests are queued rather than refused, so a manual scan's 1–5 pages can't
 * get Vigilant in trouble.
 *
 * Each market is a two-sided order book on `outcomes[0]`: buying it costs `bestAsk`, buying
 * `outcomes[1]` costs `1 - bestBid`. [ExchangeQuote] turns that into book-style odds, and thin or
 * wide markets are dropped rather than trusted as a fair price.
 */
class PolymarketClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://gamma-api.polymarket.com",
    /** The CLOB, whose `/books` is the live order book (Gamma's `bestBid`/`bestAsk` are a cached copy of it). */
    private val clobUrl: String = "https://clob.polymarket.com",
    private val clock: () -> Long = System::currentTimeMillis,
    private val usage: UsageMeter? = null,
) : ReferenceSource {

    override val id = BOOK_KEY
    override val displayName = "Polymarket"

    override fun supports(league: League) = league.polymarketTag != null

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val tag = league.polymarketTag ?: return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = id)
        val types = settings.families.mapNotNull { FAMILY_TYPES[it] }.sorted()
        if (types.isEmpty()) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = id)
        val now = clock()
        // A game's markets end at kickoff, so "ends inside the scan window" is "starts inside it".
        val from = Instant.ofEpochMilli(now).toString()
        val to = Instant.ofEpochMilli(now + (settings.daysAhead.coerceAtLeast(1) + 1) * 86_400_000L).toString()

        suspend fun readPage(page: Int): List<MarketDto> {
            val url = "$baseUrl/markets".toHttpUrl().newBuilder().apply {
                addQueryParameter("closed", "false")
                addQueryParameter("tag_id", tag.toString())
                // Repeated, not comma-joined: the comma form returns nothing (verified 2026-09-25).
                types.forEach { addQueryParameter("sports_market_types", it) }
                addQueryParameter("end_date_min", from)
                addQueryParameter("end_date_max", to)
                addQueryParameter("limit", PAGE_SIZE.toString())
                addQueryParameter("offset", (page * PAGE_SIZE).toString())
            }.build()
            return http.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                usage?.countKeyless(QuotaPolicy.POLYMARKET, calls = 1, throttled = if (response.code == 429) 1 else 0)
                if (!response.isSuccessful) throw ReferenceException("Polymarket HTTP ${response.code}")
                json.decodeFromString(ListSerializer(MarketDto.serializer()), body)
            }
        }

        // The first page alone (most leagues fit in it); a full one means more, so the rest come [WAVE] pages at a time instead of
        // one after another (an NFL week is ~8 pages of ~0.7 s each), in order, stopping at the first page that isn't full.
        val all = ArrayList<MarketDto>()
        var page = 0
        while (page < MAX_PAGES) {
            val wave = (page until minOf(MAX_PAGES, page + if (page == 0) 1 else WAVE)).toList()
            val items = coroutineScope { wave.map { async { readPage(it) } }.awaitAll() }
            var last = false
            for (got in items) {
                all += got
                if (got.size < PAGE_SIZE) { last = true; break }
            }
            if (last) break
            page += wave.size
        }
        // Gamma lists the games; their prices are re-read live from the CLOB's books (one POST per [BOOKS_CHUNK] markets, 500 a 10 s allowed: AF, 2026-10-22). A market the CLOB does not
        // answer for keeps Gamma's prices, as before; the snapshot is stamped when the books were read.
        val live = runCatching { liveTops(all) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyMap())
        val priced = all.map { m -> firstToken(m, json)?.let { live[it] }?.let { (bid, ask) -> m.copy(bestBid = bid, bestAsk = ask) } ?: m }
        val at = if (live.isEmpty()) now else clock()
        return RefSnapshot(league.oddsApiSportKey, parse(priced, league, settings.exchangeMaxSpread, at, json), at, provider = id)
    }

    /** Each listed market's first outcome token -> its live (best bid, best ask), from `POST /books`. A token whose book has no bid or no ask is left out. */
    private suspend fun liveTops(markets: List<MarketDto>): Map<String, Pair<Double, Double>> {
        val tokens = markets.mapNotNull { firstToken(it, json) }.distinct()
        if (tokens.isEmpty()) return emptyMap()
        val out = HashMap<String, Pair<Double, Double>>()
        for (part in tokens.chunked(BOOKS_CHUNK)) {
            val body = kotlinx.serialization.json.buildJsonArray { part.forEach { t -> add(kotlinx.serialization.json.buildJsonObject { put("token_id", kotlinx.serialization.json.JsonPrimitive(t)) }) } }.toString()
            val request = Request.Builder().url("$clobUrl/books").post(body.toRequestBody("application/json".toMediaType())).build()
            val text = http.newCall(request).await().use { response ->
                usage?.countKeyless(QuotaPolicy.POLYMARKET, calls = 1, throttled = if (response.code == 429) 1 else 0)
                if (!response.isSuccessful) return out
                response.body?.string().orEmpty()
            }
            out += parseBooks(text, json)
        }
        return out
    }

    companion object {
        const val BOOK_KEY = "polymarket"

        /** Tokens per `POST /books`. */
        const val BOOKS_CHUNK = 100

        /** A market's first outcome token: `clobTokenIds` (a JSON array encoded as a string) or, for a Protocol V2 market, `positionIds`. */
        internal fun firstToken(m: MarketDto, json: Json): String? {
            val raw = m.clobTokenIds ?: m.positionIds ?: return null
            return runCatching { json.decodeFromString(ListSerializer(kotlinx.serialization.serializer<String>()), raw).firstOrNull() }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        /** `POST /books`' answer: token -> (best bid, best ask), each side's best level whatever order the levels come in. */
        internal fun parseBooks(text: String, json: Json): Map<String, Pair<Double, Double>> {
            val arr = runCatching { json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonArray }.getOrNull() ?: return emptyMap()
            val out = HashMap<String, Pair<Double, Double>>()
            for (e in arr) {
                val o = e as? kotlinx.serialization.json.JsonObject ?: continue
                val token = (o["asset_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
                fun prices(k: String) = (o[k] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { l ->
                    ((l as? kotlinx.serialization.json.JsonObject)?.get("price") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                }.filter { it > 0.0 && it < 1.0 }
                val bid = prices("bids").maxOrNull() ?: continue
                val ask = prices("asks").minOrNull() ?: continue
                if (ask > bid) out[token] = bid to ask
            }
            return out
        }
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 8

        /** Pages read at once once the first one came back full. */
        const val WAVE = 3

        /** Below this much resting liquidity a quote is too easy to push around to call fair. */
        const val MIN_LIQUIDITY = 1_000.0

        private val FAMILY_TYPES = mapOf(MarketFamily.MONEYLINE to "moneyline", MarketFamily.SPREAD to "spreads", MarketFamily.TOTAL to "totals")

        /** Gamma's `gameStartTime` looks like "2026-09-27 20:25:00+00". */
        private val GAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")

        internal fun parseGameTime(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            return runCatching { OffsetDateTime.parse(s.trim(), GAME_TIME).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { Instant.parse(s.trim()).toEpochMilli() }.getOrNull()
        }

        fun parseMarkets(body: String, json: Json): List<MarketDto> = json.decodeFromString(ListSerializer(MarketDto.serializer()), body)

        /**
         * Groups markets into games by their event slug. Moneyline outcomes are listed away team
         * first for US leagues (Gamma's `/sports` ordering), which fixes home and away; a game
         * without a moneyline takes the order of its first spread's outcomes, and the planner
         * checks both orientations anyway.
         */
        fun parse(markets: List<MarketDto>, league: League, maxSpread: Double, now: Long, json: Json): List<RefEvent> {
            val byGame = markets.filter { it.active != false && it.closed != true && it.acceptingOrders != false }
                .groupBy { it.events.firstOrNull()?.slug ?: it.slug ?: it.id }
            return byGame.mapNotNull { (slug, list) -> game(slug, list, league, maxSpread, now, json) }
        }

        private fun game(slug: String, list: List<MarketDto>, league: League, maxSpread: Double, now: Long, json: Json): RefEvent? {
            val start = list.firstNotNullOfOrNull { parseGameTime(it.gameStartTime) } ?: return null
            val teamsSource = list.firstOrNull { it.sportsMarketType == "moneyline" } ?: list.firstOrNull { it.sportsMarketType == "spreads" } ?: return null
            val names = outcomes(teamsSource, json) ?: return null
            val (away, home) = names
            val markets = list.mapNotNull { m -> market(m, away, home, maxSpread, now, json) }
            return RefEvent(id = "pm:$slug", sportKey = league.oddsApiSportKey, commenceMs = start, home = home, away = away, markets = markets)
        }

        private fun outcomes(m: MarketDto, json: Json): Pair<String, String>? {
            val list = runCatching { json.decodeFromString(ListSerializer(String.serializer()), m.outcomes ?: return null) }.getOrNull() ?: return null
            return if (list.size == 2) list[0] to list[1] else null
        }

        private fun market(m: MarketDto, away: String, home: String, maxSpread: Double, now: Long, json: Json): RefBookMarket? {
            if ((m.liquidityNum ?: 0.0) < MIN_LIQUIDITY) return null
            val (first, second) = outcomes(m, json) ?: return null
            val (buyFirst, buySecond) = ExchangeQuote.toDecimal(m.bestBid, m.bestAsk, maxSpread) ?: return null
            fun sideOf(name: String): Side? = when (name) {
                away -> Side.AWAY
                home -> Side.HOME
                else -> null
            }
            val quotes = when (m.sportsMarketType) {
                "moneyline" -> {
                    val s1 = sideOf(first) ?: return null
                    val s2 = sideOf(second) ?: return null
                    if (s1 == s2) return null
                    listOf(RefQuote(s1, buyFirst, null), RefQuote(s2, buySecond, null))
                }
                "spreads" -> {
                    val line = m.line ?: return null
                    if (!isHalfPoint(line)) return null // a whole-number line can push; exchanges don't refund
                    val s1 = sideOf(first) ?: return null
                    val s2 = sideOf(second) ?: return null
                    if (s1 == s2) return null
                    listOf(RefQuote(s1, buyFirst, line), RefQuote(s2, buySecond, -line))
                }
                "totals" -> {
                    val line = m.line ?: return null
                    if (!isHalfPoint(line)) return null
                    val firstOver = when {
                        first.equals("Over", true) && second.equals("Under", true) -> true
                        first.equals("Under", true) && second.equals("Over", true) -> false
                        else -> return null
                    }
                    listOf(
                        RefQuote(if (firstOver) Side.OVER else Side.UNDER, buyFirst, line),
                        RefQuote(if (firstOver) Side.UNDER else Side.OVER, buySecond, line),
                    )
                }
                else -> return null
            }
            val kind = when (m.sportsMarketType) {
                "moneyline" -> LineKind.MONEYLINE
                "spreads" -> LineKind.SPREAD
                else -> LineKind.TOTAL
            }
            return RefBookMarket(BOOK_KEY, "Polymarket", kind, quotes, now)
        }

        internal fun isHalfPoint(line: Double): Boolean = abs(abs(line % 1.0) - 0.5) < 1e-9
    }

    @Serializable
    data class EventRefDto(val slug: String? = null)

    @Serializable
    data class MarketDto(
        val id: String = "",
        val slug: String? = null,
        val question: String? = null,
        /** A JSON array encoded as a string, e.g. `"[\"Ravens\", \"Cowboys\"]"`. */
        val outcomes: String? = null,
        val bestBid: Double? = null,
        val bestAsk: Double? = null,
        val line: Double? = null,
        val sportsMarketType: String? = null,
        val gameStartTime: String? = null,
        val liquidityNum: Double? = null,
        val active: Boolean? = null,
        val closed: Boolean? = null,
        val acceptingOrders: Boolean? = null,
        val events: List<EventRefDto> = emptyList(),
        /** The outcomes' CLOB token ids, a JSON array encoded as a string (CTF markets). */
        val clobTokenIds: String? = null,
        /** A Protocol V2 market's outcome ids (Gamma, 2026-10 docs), as Gamma sends them: kept as text. */
        @kotlinx.serialization.Serializable(with = JsonAsText::class) val positionIds: String? = null,
    )

    /** Reads any JSON value (an array or an encoded string) as the text of a JSON array. */
    object JsonAsText : kotlinx.serialization.KSerializer<String?> {
        override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("JsonAsText", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
        override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String? {
            val el = (decoder as? kotlinx.serialization.json.JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeString()
            return when (el) {
                is kotlinx.serialization.json.JsonPrimitive -> if (el is kotlinx.serialization.json.JsonNull) null else el.content
                else -> el.toString()
            }
        }
        override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String?) { encoder.encodeString(value.orEmpty()) }
    }
}
