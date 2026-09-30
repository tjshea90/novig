package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * More books' 1st-half lines (Tj, 2026-09-30, PARLAY_API.md §6.7): ParlayAPI's `/live/period_markets?period=1H` (2 credits a league, pregame
 * too despite "live" in its path) for Novig's 1st-half spread and total markets (`SPREAD_1H`, `TOTAL_1H`; Novig has no 1st-half moneyline to
 * price). One row per side, each with its own number: a spread's away row at −0.5 pairs with the same book's home row at +0.5, a total's
 * over with its under at the same number (checked in the real NFL answer). bet365, BetMGM, Caesars, DraftKings, Fanatics, FanDuel and
 * Pinnacle (with alternates) for football; each quote timed by its own age, so the scan's freshness rule drops a stale one.
 *
 * Only the sports whose "1H" is the same first half Novig lists: football and basketball. Baseball's first 5 innings and hockey's first
 * period aren't asked for until a probe shows how ParlayAPI names them (its sandbox answers every sport with the same made-up keys).
 */
class ParlayPeriodSource(private val client: TheOddsApiClient, private val json: Json) : ReferenceSource {

    init {
        require(client.feed == OddsFeed.PARLAY) { "ParlayAPI only" }
    }

    override val id = "parlay_1h"
    override val displayName = "ParlayAPI 1st half"
    override val metered = true

    /** It waits for Novig's board: a league whose games have no 1st-half line on Novig costs nothing. */
    override val needsCatalog = true

    override fun supports(league: League): Boolean = league.oddsApiListed && league.oddsApiSportKey in SPORTS

    override fun reuseMs(settings: ScanSettings): Long = settings.oddsApiReuseMs

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = odds(league, settings, ScanContext())

    override suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot {
        val sport = league.oddsApiSportKey
        val none = RefSnapshot(sport, emptyList(), System.currentTimeMillis(), provider = id)
        if (MarketFamily.FIRST_HALF !in settings.families) return none
        val events = context.novigEvents.filter { it.league == league.novigName }.mapTo(HashSet()) { it.eventId }
        if (context.novigMarkets.none { it.eventId in events && it.marketType in NOVIG_TYPES }) return none
        val answer = client.parlayGet("/sports/$sport/live/period_markets", listOf("period" to PERIOD), cost = COST, what = "${league.displayName} 1st half")
        val reply = answer.value
        if (reply.busy) throw ReferenceException("ParlayAPI's 1st-half lines are busy")
        if (!reply.ok) return none
        return RefSnapshot(sport, parse(reply.body, json, sport, System.currentTimeMillis()), System.currentTimeMillis(), answer.remaining, answer.used, id)
    }

    companion object {
        const val COST = 2
        const val PERIOD = "1H"

        /** Where ParlayAPI's "1H" is the first half Novig's `_1H` markets are. */
        val SPORTS = setOf("americanfootball_nfl", "americanfootball_ncaaf", "basketball_nba", "basketball_wnba", "basketball_ncaab")

        /** Novig's markets this prices. */
        val NOVIG_TYPES = setOf("SPREAD_1H", "TOTAL_1H")

        /** Two sides' games starting within this of each other are one game (sources write the time differently: ".000Z"). */
        private const val SAME_GAME_MS = 3 * 3_600_000L

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()

        private class Row(
            val book: String, val home: String, val away: String, val commence: Long, val market: String, val side: String,
            val line: Double?, val decimal: Double, val seenMs: Long?,
        )

        /** Half-point lines compared exactly: a spread's number and its mirror. */
        private fun key(v: Double): Int = (v * 2).roundToInt()

        /** A `/live/period_markets` answer's 1st-half spreads and totals, each book's two sides paired, as games. */
        fun parse(body: String, json: Json, sportKey: String, now: Long): List<RefEvent> {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val list = ((root as? JsonObject)?.get("results") ?: root) as? JsonArray ?: return emptyList()
            val rows = list.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                if (o.str("period_key")?.equals(PERIOD, ignoreCase = true) != true) return@mapNotNull null
                val market = o.str("market")?.lowercase() ?: return@mapNotNull null
                if (market != "spread" && market != "total") return@mapNotNull null
                val price = o.num("price") ?: return@mapNotNull null
                val decimal = if (abs(price) >= 100) Odds.americanToDecimal(price.roundToInt()) else price.takeIf { it > 1.0 } ?: return@mapNotNull null
                Row(
                    book = TheOddsApiClient.canonicalBook(o.str("source") ?: return@mapNotNull null),
                    home = o.str("home_team") ?: return@mapNotNull null,
                    away = o.str("away_team") ?: return@mapNotNull null,
                    commence = o.str("commence_time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null,
                    market = market, side = o.str("side")?.lowercase() ?: return@mapNotNull null,
                    line = o.num("line"), decimal = decimal,
                    seenMs = o.num("age_seconds")?.let { now - (it * 1000).toLong() } ?: o.num("timestamp_ms")?.toLong(),
                )
            }
            // One game per pair of teams (every source names them the same; their ids differ), whatever each wrote its start as.
            val games = rows.groupBy { "${it.home.lowercase()}|${it.away.lowercase()}" }.flatMap { (_, g) ->
                g.sortedBy { it.commence }.fold(ArrayList<ArrayList<Row>>()) { acc, r ->
                    val last = acc.lastOrNull()
                    if (last != null && r.commence - last.first().commence <= SAME_GAME_MS) last += r else acc += arrayListOf(r)
                    acc
                }
            }
            return games.map { g ->
                val first = g.first()
                val markets = ArrayList<RefBookMarket>()
                for ((book, own) in g.groupBy { it.book }) {
                    val seen = own.mapNotNull { it.seenMs }
                    fun market(kind: LineKind, a: Row, b: Row, sideA: Side, sideB: Side) = RefBookMarket(
                        bookKey = book, bookTitle = TheOddsApiClient.bookTitle(book), kind = kind,
                        quotes = listOf(RefQuote(sideA, a.decimal, a.line), RefQuote(sideB, b.decimal, b.line)),
                        lastUpdateMs = listOfNotNull(a.seenMs, b.seenMs).minOrNull() ?: seen.minOrNull(), period = 1,
                    )
                    // Spreads: the home side at +x with the away side at −x.
                    val homes = own.filter { it.market == "spread" && it.side == "home" && it.line != null }
                    val aways = own.filter { it.market == "spread" && it.side == "away" && it.line != null }
                    for (h in homes) {
                        val a = aways.firstOrNull { key(it.line!!) == -key(h.line!!) } ?: continue
                        markets += market(LineKind.SPREAD, h, a, Side.HOME, Side.AWAY)
                    }
                    // Totals: over and under at one number.
                    val overs = own.filter { it.market == "total" && it.side == "over" && it.line != null }
                    val unders = own.filter { it.market == "total" && it.side == "under" && it.line != null }
                    for (ov in overs) {
                        val u = unders.firstOrNull { key(it.line!!) == key(ov.line!!) } ?: continue
                        markets += market(LineKind.TOTAL, ov, u, Side.OVER, Side.UNDER)
                    }
                }
                RefEvent(
                    id = "p1h:${first.away}@${first.home}:${first.commence}", sportKey = sportKey, commenceMs = first.commence,
                    home = first.home, away = first.away,
                    markets = markets.distinctBy { Triple(it.bookKey, it.kind, it.line) },
                )
            }.filter { it.markets.isNotEmpty() }
        }
    }
}
