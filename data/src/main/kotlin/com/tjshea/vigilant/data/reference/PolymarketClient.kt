package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
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

        val all = ArrayList<MarketDto>()
        for (page in 0 until MAX_PAGES) {
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
            val items = http.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                usage?.countKeyless(QuotaPolicy.POLYMARKET, calls = 1, throttled = if (response.code == 429) 1 else 0)
                if (!response.isSuccessful) throw ReferenceException("Polymarket HTTP ${response.code}")
                json.decodeFromString(ListSerializer(MarketDto.serializer()), body)
            }
            all += items
            if (items.size < PAGE_SIZE) break
        }
        return RefSnapshot(league.oddsApiSportKey, parse(all, league, settings.exchangeMaxSpread, now, json), now, provider = id)
    }

    companion object {
        const val BOOK_KEY = "polymarket"
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 8

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
    )
}
