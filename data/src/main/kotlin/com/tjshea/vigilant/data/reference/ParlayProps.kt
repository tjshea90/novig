package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.CreditsHeldBackException
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ParlayAPI's player props (Tj, 2026-09-30, on its $5 Starter plan: "take full advantage of the paid API"): `/v1/sports/{sport}/props`
 * returns every book's over/under for every player in a league for 3 credits a call, where The Odds API's format sells them one prop type
 * per game per credit (a Sunday's NFL props there cost ~60 credits; here 3). Pinnacle, DraftKings, FanDuel, Caesars, Bovada and ProphetX,
 * each row timed by the age ParlayAPI measured for it, so the scan's freshness rule (RESEARCH.md §24) applies to each book's own quote.
 */
class ParlayPropsSource(
    private val client: TheOddsApiClient,
    /** Where each row's `injury` report goes (PARLAY_API.md §6.1): free with every answer. */
    private val injuries: InjuryIndex? = null,
) : ReferenceSource {

    init {
        require(client.feed == OddsFeed.PARLAY) { "ParlayAPI only" }
    }

    override val id = client.feed.propsId
    override val displayName = "ParlayAPI props"
    override val metered = true
    override val propsOnly = true
    override val extraPropTypes: Set<String> get() = PropStats.BOOK_ONLY_TYPES

    override fun supports(league: League): Boolean = league.oddsApiListed && PropStats.parlayMarkets(league.oddsApiSportKey).isNotEmpty()

    override fun reuseMs(settings: ScanSettings): Long = settings.oddsApiReuseMs

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val sport = league.oddsApiSportKey
        if (MarketFamily.PLAYER_PROPS !in settings.families) return RefSnapshot(sport, emptyList(), System.currentTimeMillis(), provider = id)
        // One flat price whatever is asked: every stat Vigilant prices for the sport (the market filter also keeps the books'
        // milestone ladders and alternates from crowding the reply: measured 2026-09-30, one call held every two-sided line).
        val markets = PropStats.parlayMarkets(sport).map { it.first }.distinct()
        val events = ArrayList<RefEvent>()
        val hurt = ArrayList<Injury>()
        var remaining: Int? = null
        var used: Int? = null
        var offset = 0
        for (page in 0 until ParlayProps.MAX_PAGES) {
            val answer = try {
                client.bulkProps(sport, markets, ParlayProps.BOOKS, offset)
            } catch (e: CreditsHeldBackException) {
                // A later page held back: keep what the first pages gave.
                if (page == 0) throw e else break
            }
            remaining = answer.remaining ?: remaining
            used = answer.used ?: used
            events += answer.value.events
            hurt += answer.value.injuries
            if (answer.value.rows < ParlayProps.PAGE) break
            offset += answer.value.rows
        }
        injuries?.record(sport, hurt)
        // A player whose books straddle a page boundary comes back in two parts: one game, all its lines.
        val merged = events.groupBy { it.id }.values.map { parts -> parts.first().copy(markets = parts.flatMap { it.markets }) }
        val snap = RefSnapshot(sport, merged, System.currentTimeMillis(), remaining, used, id)
        return client.quality?.let { ParlaySourceQuality.without(snap, it.unsafeBooks()) } ?: snap
    }
}

object ParlayProps {
    /** Credits per call, whatever the markets or books. */
    const val COST = 3

    /** Rows a page (their maximum). */
    const val PAGE = 10_000

    /** Pages read at most per league: a whole NFL Sunday fits in one with these books. */
    const val MAX_PAGES = 3

    /** Real sportsbooks with real two-sided prices (the pick'em apps and Novig itself left out). Pinnacle is the sharp one. */
    val BOOKS = listOf("pinnacle", "draftkings", "fanduel", "caesars", "bovada", "prophetx")

    /** One page: [rows] rows came back (a full page means there may be more), grouped into games; each player's injury report once. */
    class Page(val rows: Int, val events: List<RefEvent>, val injuries: List<Injury> = emptyList())

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()

    /** American (-110, +120) or decimal (1.91) to decimal odds; null when it's neither. */
    fun decimal(v: Double?): Double? = when {
        v == null || !v.isFinite() -> null
        abs(v) >= 100 -> Odds.americanToDecimal(v.roundToInt())
        v > 1.0 && v < 100 -> v
        else -> null
    }

    /**
     * A `/props` answer: the rows (a list, or `{props|data|rows: [...]}`), one per book per player per line, into games. A row needs its game
     * (`event_id`, teams, start), player, a market Vigilant prices, a line and both sides' prices; a first-quarter or first-half line
     * (`period` other than FULL) isn't the full-game prop and is left out.
     */
    fun parse(body: String, json: Json, sportKey: String, now: Long): Page {
        val root = json.parseToJsonElement(body)
        val rows = ((root.obj()?.let { it["props"] ?: it["data"] ?: it["rows"] } ?: root) as? JsonArray).orEmpty().mapNotNull { it.obj() }
        class Game(val id: String, val commence: Long, val home: String, val away: String) {
            val markets = ArrayList<RefBookMarket>()
        }
        val games = LinkedHashMap<String, Game>()
        val injuries = LinkedHashMap<String, Injury>()
        for (r in rows) {
            // Every row names its player's injury status (null: none reported), whatever its market or period.
            (r.str("player") ?: r.str("player_name"))?.trim()?.let { p ->
                val key = p + "|" + (r.str("home_team") ?: "")
                if (key !in injuries) ParlayInjuries.fromPropsRow(r, p)?.let { injuries[key] = it }
            }
            val period = r.str("period")
            if (period != null && !period.equals("FULL", true)) continue
            val marketKey = r.str("market_key") ?: continue
            // Each book's own name for the market ("player_rec_yds", "player_receiving_yards"): read as words ([ParlayMarkets]).
            val stat = ParlayMarkets.statOf(sportKey, marketKey, r.str("market") ?: r.str("market_label")) ?: continue
            val player = (r.str("player") ?: r.str("player_name") ?: r.str("description"))?.trim() ?: continue
            val yesNo = marketKey in PropStats.YES_NO || "anytime" in marketKey
            val line = r.num("line") ?: r.num("point") ?: if (yesNo) 0.5 else continue
            val over = decimal(r.num("over_price")) ?: continue
            val under = decimal(r.num("under_price")) ?: continue
            val book = TheOddsApiClient.canonicalBook(r.str("bookmaker") ?: r.str("source") ?: continue)
            val eventId = r.str("event_id") ?: r.str("eventId") ?: r.str("id") ?: continue
            val home = r.str("home_team") ?: continue
            val away = r.str("away_team") ?: continue
            val commence = r.str("commence_time")?.let { TheOddsApiClient.parseIsoMs(it) } ?: continue
            val seen = r.num("age_seconds")?.let { now - (it * 1000).toLong() }
                ?: r.str("last_update")?.let { TheOddsApiClient.parseIsoMs(it) }
                ?: r.num("last_update_ms")?.toLong()
            val game = games.getOrPut(eventId) { Game(eventId, commence, home, away) }
            game.markets += RefBookMarket(
                bookKey = book,
                bookTitle = TheOddsApiClient.bookTitle(book),
                kind = LineKind.PLAYER_PROP,
                quotes = listOf(RefQuote(Side.OVER, over, line), RefQuote(Side.UNDER, under, line)),
                lastUpdateMs = seen,
                subject = player,
                stat = stat,
            )
        }
        return Page(
            rows.size,
            games.values.map { g -> RefEvent(g.id, sportKey, g.commence, g.home, g.away, g.markets.distinctBy { Triple(it.bookKey, it.subject, it.stat to it.line) }) },
            injuries.values.toList(),
        )
    }
}
