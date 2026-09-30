package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One of ParlayAPI's own +EV plays at Novig (`/best-bets`, PARLAY_API.md §6.5): a player prop, ParlayAPI's no-vig fair price for it and the
 * Novig price ParlayAPI listed. Its answer has no event id, start, player or line fields: they're read from its `bet` text
 * ("Carson Kelly Over 0.5 Home Runs (Chicago Cubs @ San Diego Padres)"). [alert]: one of its `edge_alerts` ("far better than the rest of the
 * market … verify it is still live"), with its [caveat].
 */
data class ParlayPlay(
    val sportKey: String,
    /** Novig's league name ("MLB"). */
    val league: String,
    val player: String,
    val over: Boolean,
    val line: Double,
    /** Its words for the stat ("Home Runs", "Batter Home Runs", "Rbis"). */
    val statLabel: String,
    /** The Novig stat it is, when Vigilant knows it ([ParlayMarkets.statOf]). */
    val stat: String?,
    val away: String,
    val home: String,
    val marketKey: String?,
    /** ParlayAPI's fair price for this side (American); on an edge alert (which names none), the one its edge implies. */
    val fairAmerican: Int?,
    /** The Novig price ParlayAPI listed (American): often far off Novig's own book (§5), so re-read before it's shown. */
    val listedAmerican: Int,
    val edgePp: Double? = null,
    val verdict: String? = null,
    val booksCompared: Int? = null,
    val alert: Boolean = false,
    val caveat: String? = null,
) {
    val event: String get() = "$away @ $home"
    val bet: String get() = "$player ${if (over) "Over" else "Under"} ${fmt(line)}"

    /** CNO's way of naming the market ("Player Home Runs"), which Novig's catalog matcher reads. */
    val market: String get() = "Player " + (stat?.let(PropStats::displayName) ?: statLabel)

    /**
     * As a CNO row, so Novig's catalog finds the exact outcome ([com.tjshea.vigilant.data.cno.NovigBetFinder]) and Novig's order book prices
     * it ([com.tjshea.vigilant.data.cno.NovigLive]) exactly as they do CNO's bets. [startsAtMs] once Novig's catalog has said.
     */
    fun row(startsAtMs: Long? = null): CnoRow = CnoRow(
        ev = 0.0, startsAtMs = startsAtMs, sport = "", league = league, event = event, market = market, bet = bet,
        odds = listedAmerican, available = null, book = "Novig",
        fairOdds = fairAmerican, fairProbability = fairAmerican?.let { 1.0 / Odds.americanToDecimal(it) }, books = booksCompared,
    )

    /** Its key in placed.json and the Tracker (the ✓ / ✕ marks), apart from CNO's and Vigilant's own. */
    val key: String get() = KEY_PREFIX + row().key

    companion object {
        const val KEY_PREFIX = "parlay:"

        private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}

/** A league's `/best-bets` answer: its plays (and edge alerts), ParlayAPI's one-line summary, when it was read. */
data class ParlayBoard(val sportKey: String, val plays: List<ParlayPlay>, val summary: String?, val readAtMs: Long)

/**
 * ParlayAPI's own +EV list at Novig (Tj, 2026-09-30, PARLAY_API.md §6.5): `GET /v1/sports/{s}/best-bets?books=novig`, 10 credits a league,
 * player props only, read only when Tj taps (never on a timer). Through [TheOddsApiClient.parlayGet]: the key pool, the day's pace and the
 * meter apply (its body's `credits.monthly_remaining` recorded). Its plays are only candidates: each is re-priced from Novig's own book
 * before it's shown (the screens do that with Novig's catalog and book).
 */
class ParlayBestBets(
    private val client: TheOddsApiClient,
    private val json: Json,
    private val active: suspend () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Calls made (tests, Diagnostics). */
    @Volatile
    var requests: Int = 0
        private set

    /** [league]'s board, or null when ParlayAPI is off. Throws when it can't be read (the screen says why). */
    suspend fun read(league: League): ParlayBoard? {
        if (!active() || !supports(league)) return null
        requests++
        val reply = client.parlayGet(
            "/sports/${league.oddsApiSportKey}/best-bets",
            listOf(
                "books" to "novig",
                // Wide: every candidate is re-priced at Novig and held to Tj's own minimum EV, and a wider net costs the same 10 credits.
                "min_edge" to MIN_EDGE.toString(), "min_books" to MIN_BOOKS.toString(), "limit" to LIMIT.toString(),
            ),
            cost = COST, what = "${league.displayName} best bets",
        ).value
        if (reply.busy) throw ReferenceException("ParlayAPI's board is busy: try again in a minute")
        if (!reply.ok) return ParlayBoard(league.oddsApiSportKey, emptyList(), null, clock())
        return parse(reply.body, json, league, clock()) ?: throw ReferenceException("ParlayAPI's answer couldn't be read")
    }

    companion object {
        const val COST = 10

        /** ParlayAPI's minimum edge asked for (probability points): low, since Vigilant judges each play at Novig's real price. */
        const val MIN_EDGE = 1.0

        /** Books that must price a play: three (four left the NFL board empty on 2026-09-30). */
        const val MIN_BOOKS = 3

        const val LIMIT = 50

        /** A league it can list: player props there that Vigilant prices from ParlayAPI. */
        fun supports(league: League): Boolean = league.oddsApiListed && PropStats.parlayMarkets(league.oddsApiSportKey).isNotEmpty()

        /** "<Player> Over|Under <line> <Stat label> (<Away> @ <Home>)". */
        private val BET = Regex("""^(.+?)\s+(Over|Under)\s+([0-9]+(?:\.[0-9]+)?)\s+(.+?)\s*\((.+?)\s+@\s+(.+)\)\s*$""", RegexOption.IGNORE_CASE)

        /** The pieces of a `bet` text: player, over, line, stat label, away, home; null when it isn't one. */
        fun parseBet(text: String): List<String>? = BET.matchEntire(text.trim())?.groupValues?.drop(1)

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()

        /** A `/best-bets` answer for [league]; plays whose `bet` can't be read, or that aren't at Novig, are left out. */
        fun parse(body: String, json: Json, league: League, now: Long): ParlayBoard? {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val sport = league.oddsApiSportKey
            fun play(o: JsonObject, alert: Boolean): ParlayPlay? {
                val p = parseBet(o.str("bet") ?: return null) ?: return null
                val label = p[3]
                val book = (if (alert) o.str("book") else o.str("best_book")) ?: "novig"
                if (!book.equals("novig", ignoreCase = true)) return null
                val price = (if (alert) o.num("price") else o.num("best_price"))?.toInt() ?: return null
                val key = o.str("market_key")
                val stat = ParlayMarkets.statOf(sport, key ?: label.lowercase().replace(' ', '_'), label)
                val edge = o.num("edge_pct") ?: o.num("apparent_edge_pct")
                // An edge alert names no fair price; its edge is probability points over the price's own (§5), which gives one.
                val fair = o.num("fair_price")?.toInt() ?: edge?.let { e ->
                    (1.0 / Odds.americanToDecimal(price) + e / 100.0).takeIf { it in 0.001..0.999 }?.let(Odds::probabilityToAmerican)
                }
                return ParlayPlay(
                    sportKey = sport, league = league.novigName, player = p[0], over = p[1].equals("over", true), line = p[2].toDoubleOrNull() ?: return null,
                    statLabel = label, stat = stat, away = p[4], home = p[5], marketKey = key,
                    fairAmerican = fair, listedAmerican = price,
                    edgePp = edge, verdict = o.str("verdict"), booksCompared = o.num("books_compared")?.toInt(),
                    alert = alert, caveat = o.str("caveat"),
                )
            }
            val plays = (root["best_bets"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> play(o, false) } }
            val alerts = (root["edge_alerts"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> play(o, true) } }
                // A play and an alert on the same bet: the play (it has a fair price).
                .filter { a -> plays.none { it.bet == a.bet && it.event == a.event } }
            return ParlayBoard(sport, plays + alerts, root.str("summary"), now)
        }
    }
}

/**
 * A ParlayAPI play at Novig's price now (PARLAY_API.md §6.5): its row at that price, Vigilant's EV there against ParlayAPI's fair price (Novig's
 * taker fee taken out on a live game), the dollars at it, when Novig's book was read; [found] false when Novig's catalog has no such bet.
 */
data class ParlayPick(
    val play: ParlayPlay,
    val row: CnoRow,
    val ev: Double?,
    val available: Double?,
    val novigAtMs: Long?,
    val found: Boolean,
    /** Novig's market and outcome for it, once its catalog has found it: Vigilant's own fair odds are read for them (TASKS.md P2). */
    val marketId: String? = null,
    val outcomeId: String? = null,
) {
    val key: String get() = play.key

    companion object {
        /** [plays] (as [rows], their starts filled in from Novig's catalog) priced from Novig's books [live] (by row key). */
        fun priced(plays: List<ParlayPlay>, rows: List<CnoRow>, live: Map<String, com.tjshea.vigilant.data.cno.LivePrice>): List<ParlayPick> =
            plays.zip(rows).map { (p, r) ->
                val l = live[r.key]
                if (l == null) ParlayPick(p, r, null, null, null, found = false)
                else ParlayPick(p, r.copy(odds = l.american, available = l.available, ev = l.ev ?: 0.0), l.ev, l.available, l.atMs, found = true, l.marketId, l.outcomeId)
            }
    }
}
