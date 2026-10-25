package com.tjshea.vigilant.data.parlay

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.ParlayMarkets
import com.tjshea.vigilant.data.reference.ParlayProps
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * What ParlayAPI's Business odds stream has said (PB11, Tj 2026-10-25: "full usage of the sse and/or websocket"; research/PARLAYAPI_BUSINESS_2026-10-25.md §2): every row of `/v1/sse/odds/{sport}` kept
 * by game, book, market, side and line, at its newest price, dated by when ParlayAPI last confirmed it at the book (`last_update`, `last_observed_ms`: a re-verified price is current even when it has not
 * moved for hours). Pure state, fed by [ParlayStream]; laid over ParlayAPI's REST boards by [overlayEvent] (the engine's real-time list, [com.tjshea.vigilant.data.cloud.RealtimeList]).
 *
 * The REST board names games by a hash and the stream by "date_Home_Away": rows are matched by the two team names (as ParlayAPI spells them in both) and the side's line. Only quotes ParlayAPI sent
 * ([RefBookMarket.source] `parlay...`) are touched: where PropLine sends the same book, the fresher of the two prices it ([com.tjshea.vigilant.data.scanner.Pricing.chosenMarkets]).
 */
class ParlayLive(private val clock: () -> Long = System::currentTimeMillis) {
    /** One side's newest price and when ParlayAPI last confirmed it. */
    data class Price(val decimal: Double, val confirmedAtMs: Long, val heardAtMs: Long)

    private val prices = ConcurrentHashMap<String, Price>()
    /** When each league's stream has been connected without a break since (0 = not connected): a price unchanged since then has not moved. */
    private val connectedSince = ConcurrentHashMap<String, Long>()
    /** When ParlayAPI last polled each book (the app's key), and whether it calls the book's prices stale, from `/v1/meta/source-health`. */
    @Volatile var polls: Map<String, Pair<Long, Boolean>> = emptyMap()
    @Volatile var confirmed = 0L
        private set

    fun connected(sport: String, since: Long = clock()) { connectedSince.putIfAbsent(sport, since); heard() }
    fun disconnected(sport: String) { connectedSince.remove(sport) }

    /** One `/v1/meta/source-health` answer: each source's last poll ([now] minus `poll_age_s`) and `prices_stale`. */
    fun onHealth(body: JsonObject, now: Long = clock()) {
        val at = body.num("generated_at_ms")?.toLong() ?: now
        val out = HashMap<String, Pair<Long, Boolean>>()
        for (e in (body["sources"] as? kotlinx.serialization.json.JsonArray).orEmpty()) {
            val o = e as? JsonObject ?: continue
            val book = TheOddsApiClient.canonicalBook(o.str("source") ?: continue)
            val age = o.num("poll_age_s") ?: o.num("age_s") ?: continue
            out[book] = (at - (age * 1000).toLong()) to (o["prices_stale"].let { it is JsonPrimitive && it.content == "true" })
        }
        if (out.isNotEmpty()) polls = out
    }

    @Volatile var rows = 0L
        private set
    @Volatile var applied = 0L
        private set
    @Volatile var lastFrameMs = 0L
        private set
    /** Rows by kind that could not be read (a shape Vigilant does not price): counted, never guessed at. */
    @Volatile var unread = 0L
        private set

    fun heard(now: Long = clock()) { lastFrameMs = now }

    /** The stream spoke within [SILENT_MS] (it sends a heartbeat every 5 s). */
    fun live(now: Long = clock()): Boolean = now - lastFrameMs < SILENT_MS

    fun size(): Int = prices.size

    /** One streamed row (`initial_state` or `odds_update`): its sides into the store. */
    fun onRow(sport: String, r: JsonObject, now: Long = clock()) {
        heard(now)
        rows++
        val home = r.str("home_team") ?: return
        val away = r.str("away_team") ?: return
        val book = TheOddsApiClient.canonicalBook(r.str("bookmaker") ?: return)
        if (r["available"].let { it is JsonPrimitive && it.content == "false" }) return
        val at = listOfNotNull(r.num("last_update"), r.num("last_observed_ms"), r.num("collector_seen_ms")).maxOrNull()?.toLong() ?: return
        val market = r.str("market_key") ?: return
        val game = gameKey(home, away)
        fun put(kind: LineKind, subject: String?, stat: String?, side: Side, point: Double?, american: Double?) {
            val d = ParlayProps.decimal(american) ?: return
            val k = sideKey(game, book, kind, subject, stat, side, point)
            val old = prices[k]
            if (old == null || at >= old.confirmedAtMs) prices[k] = Price(d, at, now)
        }
        val player = r.str("player")
        when {
            market == "h2h" || market == "moneyline" -> {
                put(LineKind.MONEYLINE, null, null, Side.HOME, null, r.num("home_ml"))
                put(LineKind.MONEYLINE, null, null, Side.AWAY, null, r.num("away_ml"))
            }
            market == "spreads" -> {
                val team = r.str("team") ?: return
                val side = sideOf(team, home, away) ?: return
                put(LineKind.SPREAD, null, null, side, r.num("line") ?: return, r.num("price") ?: r.num("over_price"))
            }
            market == "totals" && (r.str("kind") == "game" || player == null || player.equals("Total", true)) -> {
                val line = r.num("line") ?: r.num("total") ?: return
                put(LineKind.TOTAL, null, null, Side.OVER, line, r.num("over_price"))
                put(LineKind.TOTAL, null, null, Side.UNDER, line, r.num("under_price"))
            }
            market == "team_totals" -> {
                val side = sideOf(r.str("team") ?: return, home, away) ?: return
                val subject = if (side == Side.HOME) RefBookMarket.HOME else RefBookMarket.AWAY
                val line = r.num("line") ?: return
                put(LineKind.TEAM_TOTAL, subject, null, Side.OVER, line, r.num("over_price"))
                put(LineKind.TEAM_TOTAL, subject, null, Side.UNDER, line, r.num("under_price"))
            }
            r.str("kind") == "prop" && player != null -> {
                val stat = ParlayMarkets.statOf(sport, market, r.str("market")) ?: run { unread++; return }
                val line = r.num("line") ?: return
                put(LineKind.PLAYER_PROP, PlayerNames.key(player), stat, Side.OVER, line, r.num("over_price"))
                put(LineKind.PLAYER_PROP, PlayerNames.key(player), stat, Side.UNDER, line, r.num("under_price"))
            }
            else -> unread++
        }
    }

    /**
     * [e] with every ParlayAPI quote moved to the stream's newest price for it, when the stream confirmed it more recently than the quote's own stamp. A market moves only when the stream holds every one
     * of its sides (a two-way price is never half old, half new); its stamp becomes the oldest of those confirmations.
     */
    fun overlayEvent(e: RefEvent, now: Long = clock()): RefEvent {
        if (prices.isEmpty()) return e
        prune(now)
        val game = gameKey(e.home, e.away)
        var changed = false
        val markets = e.markets.map { m ->
            if (m.source?.startsWith("parlay") != true || m.period != 0) return@map m
            val subject = if (m.kind == LineKind.PLAYER_PROP) m.subject?.let(PlayerNames::key) else m.subject
            val found = m.quotes.map { q -> prices[sideKey(game, m.bookKey, m.kind, subject, m.stat, q.side, q.point)] }
            if (found.any { it == null }) return@map confirm(e.sportKey, m, now)?.also { changed = true } ?: m
            val confirmed = found.minOf { it!!.confirmedAtMs }
            if (confirmed <= (m.lastUpdateMs ?: 0L)) return@map m
            changed = true
            applied++
            m.copy(quotes = m.quotes.mapIndexed { i, q -> q.copy(decimalOdds = found[i]!!.decimal) }, lastUpdateMs = confirmed)
        }
        return if (changed) e.copy(markets = markets) else e
    }

    /**
     * A quote the stream has said nothing about since its stamp, while the league's stream was connected the whole time: the price has not moved (the stream pushes every move), so it is current as of
     * ParlayAPI's last poll of the book (unless ParlayAPI calls that book's prices stale). Null when that cannot be said.
     */
    private fun confirm(sport: String, m: RefBookMarket, now: Long): RefBookMarket? {
        val stamp = m.lastUpdateMs ?: return null
        val since = connectedSince[sport] ?: return null
        if (since > stamp || !live(now)) return null
        val (polled, stale) = polls[m.bookKey] ?: return null
        if (stale || polled <= stamp || now - polled > POLL_MAX_MS) return null
        confirmed++
        return m.copy(lastUpdateMs = minOf(polled, now))
    }

    private var prunedAt = 0L
    private fun prune(now: Long) {
        if (now - prunedAt < 60_000L) return
        prunedAt = now
        prices.entries.removeIf { now - it.value.heardAtMs > KEEP_MS }
    }

    fun describe(now: Long = clock()): String =
        "${prices.size} sides held from $rows rows (${applied} markets moved onto the boards, $confirmed confirmed unchanged at ParlayAPI's last poll, ${polls.size} books' polls known, $unread rows of shapes not priced), last frame ${if (lastFrameMs == 0L) "never" else "${(now - lastFrameMs) / 1000}s ago"}"

    companion object {
        /** The stream sends a heartbeat every 5 s: past this it is taken for dead. */
        const val SILENT_MS = 30_000L
        /** A side not heard of for this long is forgotten (the stream drops a game line older than 10 minutes itself). */
        const val KEEP_MS = 30 * 60_000L
        /** A poll older than this confirms nothing (ParlayAPI polls a live book every few seconds to a minute). */
        const val POLL_MAX_MS = 5 * 60_000L

        fun gameKey(home: String, away: String) = norm(home) + "|" + norm(away)

        private fun norm(team: String) = team.lowercase().filter { it.isLetterOrDigit() }

        private fun sideOf(team: String, home: String, away: String): Side? = when (norm(team)) {
            norm(home) -> Side.HOME
            norm(away) -> Side.AWAY
            else -> null
        }

        private fun sideKey(game: String, book: String, kind: LineKind, subject: String?, stat: String?, side: Side, point: Double?): String =
            "$game|$book|$kind|${subject ?: ""}|${stat ?: ""}|$side|${point?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: ""}"

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
    }
}
