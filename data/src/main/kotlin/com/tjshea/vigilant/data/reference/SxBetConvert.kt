package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal

/**
 * SX Bet (SXBET_API.md): a peer-to-peer exchange whose books are the sharp-ish price of a real market. Pure parsing and building, no I/O.
 *
 * Odds are implied probabilities scaled by 10^20, as strings, in the MAKER's frame on every route and channel Vigilant reads (the docs' own rule: a maker resting at `p` offers the taker
 * `1 - p` on the other side, `takerOdds = 1 - p`). So the maker level on outcome two is what a taker pays to BUY outcome one, and the maker level on outcome one is the ask for outcome two:
 * [takerBook] does that conversion once, for REST and the socket alike (the `showTakerPerspective` flag is not used, so no unverified server behaviour is relied on). An exchange with an
 * uncrossed book has `ask1 + ask2 >= 1`; a pair that sums below 1 (a crossed read, or a frame mix-up) is dropped by [pair], never priced.
 */
object SxBetConvert {
    const val BOOK_KEY = "sxbet"
    private val SCALE = BigDecimal("1e20")

    /** Taker stake (USDC) the top level must hold before the quote is believed: a thin level is too easy to push around. */
    const val MIN_TOP_USDC = 50.0

    /** A pair of asks may sum to at most this much over 1 (a wider spread is no fair price). */
    const val MAX_OVERROUND = 0.06

    private val TEAM_ML = setOf(226)
    private val SOLO_ML = setOf(52, 226)
    private val TEAM_TOTAL = setOf(28)
    private val SOLO_TOTAL = setOf(166, 29)
    private val TEAM_SPREAD = setOf(342)
    private val SOLO_SPREAD = setOf(201)

    @Serializable
    data class Market(
        val marketHash: String = "",
        val status: String? = null,
        val outcomeOneName: String = "",
        val outcomeTwoName: String = "",
        val teamOneName: String = "",
        val teamTwoName: String = "",
        val type: Int = 0,
        val gameTime: Long = 0,
        val line: Double? = null,
        val sportXeventId: String = "",
        val sportId: Int = 0,
        val leagueId: Int = 0,
        val leagueLabel: String? = null,
        val mainLine: Boolean? = null,
    )

    /** One top-of-book level: the implied probability a taker pays and the taker stake that clears it, in dollars. */
    data class Level(val prob: Double, val sizeUsdc: Double)

    /** A market's top of book as a TAKER sees it: [one] is the ask for outcome one, [two] for outcome two; [atMs] is when it was read or pushed (0 = unknown). */
    data class Book(val marketHash: String, val one: Level?, val two: Level?, val atMs: Long = 0L)

    fun toProb(raw: String?): Double? = raw?.toBigDecimalOrNull()?.divide(SCALE)?.toDouble()

    private fun level(o: kotlinx.serialization.json.JsonElement?): Level? {
        val obj = (o as? JsonObject) ?: return null
        val p = toProb((obj["percentageOdds"] as? JsonPrimitive)?.contentOrNull) ?: return null
        val size = (obj["size"] as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull()?.divide(BigDecimal(1_000_000))?.toDouble() ?: 0.0
        return Level(p, size)
    }

    /** `GET /markets/active` reply: the markets and the key for the next page (null on the last). */
    fun parseMarkets(body: String, json: Json): Pair<List<Market>, String?> {
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject ?: return emptyList<Market>() to null
        val list = (data["markets"] as? JsonArray).orEmpty().mapNotNull { runCatching { json.decodeFromJsonElement(Market.serializer(), it) }.getOrNull() }
        return list to (data["nextKey"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    /** `GET /orders-v3/odds/best` (or one `best_odds_v3:global` publication, wrapped by the caller) -> rows. */
    fun parseBest(body: String, json: Json, atMs: Long = 0L): List<Book> {
        val root = json.parseToJsonElement(body).jsonObject
        if ((root["status"] as? JsonPrimitive)?.contentOrNull != "success") return emptyList()
        val rows = root["data"]?.jsonObject?.get("bestOdds")?.jsonArray ?: return emptyList()
        return rows.mapNotNull { bookOf(it, atMs) }
    }

    /** One best-odds row (REST `bestOdds[]` element or a `best_odds_v3:global` publication, both in the maker frame) as a taker's [Book]. */
    fun bookOf(e: kotlinx.serialization.json.JsonElement, atMs: Long = 0L): Book? {
        val o = e as? JsonObject ?: return null
        val h = (o["marketHash"] as? JsonPrimitive)?.contentOrNull ?: return null
        return takerBook(h, level(o["outcomeOne"]), level(o["outcomeTwo"]), atMs)
    }

    /**
     * Maker levels to taker asks: the maker on outcome two (probability p2, stake S2) is what a taker on outcome one bets against, at `1 - p2`; the taker's stake that clears it is
     * `S2 * (1 - p2) / p2` (the maker risks S2 to collect S2 / p2 in all).
     */
    fun takerBook(hash: String, makerOne: Level?, makerTwo: Level?, atMs: Long = 0L): Book {
        fun ask(m: Level?): Level? = m?.takeIf { it.prob > 0.0 && it.prob < 1.0 }?.let { Level(1.0 - it.prob, it.sizeUsdc * (1.0 - it.prob) / it.prob) }
        return Book(hash, ask(makerTwo), ask(makerOne), atMs)
    }

    /** Which market types make up a league's lines: the team sports play to a finish with overtime, the rest are one-on-one. */
    private fun solo(league: League) = league.tennis || league.novigName in setOf("UFC", "Boxing")

    /**
     * Builds one [RefEvent] per SX game from its markets and their books. Home/away: SX lists the away team first for US sports (verified on NFL markets,
     * 2026-10-18: "Dallas Cowboys | Philadelphia Eagles" for Dallas at Philadelphia); the planner tries both orientations anyway.
     */
    fun events(markets: List<Market>, books: Map<String, Book>, league: League, now: Long): List<RefEvent> {
        val solo = solo(league)
        val ml = if (solo) SOLO_ML else TEAM_ML
        val tot = if (solo) SOLO_TOTAL else TEAM_TOTAL
        val spr = if (solo) SOLO_SPREAD else TEAM_SPREAD
        return markets.filter { it.status == null || it.status == "ACTIVE" }.groupBy { it.sportXeventId }.mapNotNull { (id, list) ->
            val first = list.first()
            val away = first.teamOneName
            val home = first.teamTwoName
            if (id.isBlank() || away.isBlank() || home.isBlank() || first.gameTime <= 0) return@mapNotNull null
            val out = list.mapNotNull { m ->
                val b = books[m.marketHash] ?: return@mapNotNull null
                val (p1, p2) = pair(b) ?: return@mapNotNull null
                val d1 = 1.0 / p1
                val d2 = 1.0 / p2
                when (m.type) {
                    in ml -> {
                        val s1 = sideOf(m.outcomeOneName, away, home) ?: return@mapNotNull null
                        val s2 = sideOf(m.outcomeTwoName, away, home) ?: return@mapNotNull null
                        if (s1 == s2) return@mapNotNull null
                        RefBookMarket(BOOK_KEY, "SX Bet", LineKind.MONEYLINE, listOf(RefQuote(s1, d1, null), RefQuote(s2, d2, null)), b.atMs.takeIf { it > 0 } ?: now)
                    }
                    in spr -> {
                        val line = m.line ?: return@mapNotNull null
                        if (!isHalf(line)) return@mapNotNull null
                        // `line` is the first outcome's handicap ("Dallas Cowboys +2.5" has line 2.5, verified on a type-342 market).
                        val s1 = sideOf(m.outcomeOneName, away, home) ?: return@mapNotNull null
                        val s2 = sideOf(m.outcomeTwoName, away, home) ?: return@mapNotNull null
                        if (s1 == s2) return@mapNotNull null
                        RefBookMarket(BOOK_KEY, "SX Bet", LineKind.SPREAD, listOf(RefQuote(s1, d1, line), RefQuote(s2, d2, -line)), b.atMs.takeIf { it > 0 } ?: now)
                    }
                    in tot -> {
                        val line = m.line ?: return@mapNotNull null
                        if (!isHalf(line)) return@mapNotNull null
                        val over1 = m.outcomeOneName.startsWith("Over", true)
                        val over2 = m.outcomeTwoName.startsWith("Over", true)
                        if (over1 == over2) return@mapNotNull null
                        RefBookMarket(BOOK_KEY, "SX Bet", LineKind.TOTAL, listOf(RefQuote(if (over1) Side.OVER else Side.UNDER, d1, line), RefQuote(if (over1) Side.UNDER else Side.OVER, d2, line)), b.atMs.takeIf { it > 0 } ?: now)
                    }
                    else -> null
                }
            }
            if (out.isEmpty()) null else RefEvent("sx:$id", league.oddsApiSportKey, first.gameTime * 1000L, home, away, out)
        }
    }

    /** The two asks of a market, or null when either side is missing, thin, or the pair does not describe an uncrossed book. */
    fun pair(b: Book): Pair<Double, Double>? {
        val one = b.one ?: return null
        val two = b.two ?: return null
        if (one.sizeUsdc < MIN_TOP_USDC || two.sizeUsdc < MIN_TOP_USDC) return null
        val sum = one.prob + two.prob
        if (sum < 1.0 - 1e-6 || sum > 1.0 + MAX_OVERROUND) return null
        if (one.prob !in ExchangeQuote.MIN_PRICE..ExchangeQuote.MAX_PRICE || two.prob !in ExchangeQuote.MIN_PRICE..ExchangeQuote.MAX_PRICE) return null
        return one.prob to two.prob
    }

    private fun isHalf(line: Double) = kotlin.math.abs(kotlin.math.abs(line % 1.0) - 0.5) < 1e-9

    /** Which side an outcome name is for: a handicap name carries the team plus a number ("Dallas Cowboys +2.5"), a moneyline is the team. */
    private fun sideOf(name: String, away: String, home: String): Side? {
        val n = name.trim()
        val base = n.replace(Regex("\\s*[+-]\\d+(\\.\\d+)?$"), "").trim()
        return when {
            base.equals(away, true) -> Side.AWAY
            base.equals(home, true) -> Side.HOME
            else -> null
        }
    }
}
