package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * PropLine's cross-book +EV scanner at Novig (Tj, 2026-10-16, TCL56: "cross book +ev scanner (make a section in the android app for this because I want to see what +ev bets it finds)").
 * Pro's `/events/{id}/ev?bookmakers=novig` gives, per line, a no-vig fair probability from the sharpest book that prices it (Pinnacle first) and Novig's EV% against it. The GitHub engine sweeps the
 * pregame games of the picked leagues ([PropLineEvSweep]) and publishes the +EV lines as `propline-ev.json.gz`; the phone lists them (the +EV tab's PropLine screen). A line is information, not an order: the
 * Bet sheet re-prices at Novig's live book before anything is placed, and a Novig price PropLine last read minutes ago (props: 15 to 45 minutes) says so in its age.
 */
@Serializable
data class PlEvRow(
    val sport: String,
    val league: String,
    val eventId: String,
    val home: String,
    val away: String,
    val startsAtMs: Long,
    val market: String,
    /** The player for a prop; blank for a game line. */
    val player: String,
    val point: Double? = null,
    val outcome: String,
    /** Novig's American price as PropLine read it. */
    val price: Int,
    val evPct: Double,
    /** The fair probability of this outcome (0-1). */
    val fairProb: Double,
    val fairSource: String,
    /** When PropLine last read Novig's price for this line. */
    val updatedAtMs: Long,
    /** When the sweep that found it ran (the journal's clock; a line is journaled each sweep it is found in). */
    val seenAtMs: Long = 0L,
    /** The same bet at every other book PropLine carries, read now (`/best-line`), best price first; empty = not read. */
    val others: List<PlBookPrice> = emptyList(),
    /** Of the books that price both sides of this line, how many say Novig's price is +EV by their own no-vig number, and whether Pinnacle does (null = Pinnacle does not price both sides). */
    val twoSided: Int = 0,
    val agree: Int = 0,
    val pinnacleAgrees: Boolean? = null,
    /** Vigilant's own scan of the same Novig line (null = the scan did not price it): its EV% now, its star rating, and the line itself ([com.tjshea.vigilant.data.cloud.CloudCodec] JSON) so the phone can open the Bet sheet. */
    val vigilantEv: Double? = null,
    val vigilantStars: Double? = null,
    val opp: String? = null,
    /** The line in [opp] was priced from PropLine's own fair (Vigilant's scan had the Novig market but no fair odds for it; Tj, 2026-10-17: a Bet button on every row). */
    val oppFromPropLine: Boolean = false,
    /**
     * Novig's price for the same line from the ENGINE's own read when the sweep found it (TCL75, Tj 2026-10-17: "confirm the novig price is still available"): American, when that read was made, the dollars
     * offered at it, and the fair Vigilant used. Null = the engine's scan did not price this line (availability unknown; PropLine's own `price` is minutes old).
     */
    val novigNowPrice: Int? = null,
    val novigNowAtMs: Long? = null,
    val novigNowDepth: Double? = null,
    val vigilantFair: Double? = null,
    /** How often the player went over/under this line recently (PropLine `/trends`, props only): "last 10: Over 7, Under 3 · last 20: Over 11, Under 9 · averages 5.9 (line 5.5)"; blank = not read. */
    val trend: String = "",
)

/** One book's price for a bet and when PropLine last saw it change hands at that book. */
@Serializable
data class PlBookPrice(val book: String, val price: Int, val updatedAtMs: Long)

@Serializable
data class PlEvFeed(
    val atMs: Long,
    val rows: List<PlEvRow>,
    /** Games looked at, requests spent, the plan, and why a sweep was partial. */
    val games: Int = 0,
    val requests: Int = 0,
    val tier: String = "",
    val note: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(f: PlEvFeed): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(json.encodeToString(serializer(), f).toByteArray()) } }.toByteArray()

        fun decode(bytes: ByteArray): PlEvFeed? = runCatching {
            json.decodeFromString(serializer(), GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) })
        }.getOrNull()
    }
}

object PropLineEv {
    /** Games starting within this many hours are swept. */
    const val WINDOW_HOURS = 24

    /** Under this EV% a line is not worth a row; over [MAX_EV] it is nearly always a stale or mismatched line (PropLine's own advice: about 10 is a sensible ceiling for alerts; the list keeps more and says so). */
    const val MIN_EV = 0.5
    const val MAX_EV = 30.0

    /** One `/ev` answer into rows. [redacted] = a free key (every number nulled): no rows, the caller says why. */
    class Parsed(val rows: List<PlEvRow>, val redacted: Boolean)

    fun parse(raw: String, json: Json, sport: String, league: String, eventId: String, home: String, away: String, startsAtMs: Long, now: Long): Parsed {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return Parsed(emptyList(), false)
        if ((root["redacted"] as? JsonPrimitive)?.booleanOrNull == true) return Parsed(emptyList(), true)
        val out = ArrayList<PlEvRow>()
        for (line in (root["lines"] as? JsonArray).orEmpty()) {
            val l = line as? JsonObject ?: continue
            val market = (l["market_key"] as? JsonPrimitive)?.contentOrNull ?: continue
            val player = (l["description"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val point = (l["point"] as? JsonPrimitive)?.doubleOrNull
            val source = (l["fair_source"] as? JsonPrimitive)?.contentOrNull ?: ""
            val probs = l["fair_probs"] as? JsonObject ?: continue
            for (o in (l["outcomes"] as? JsonArray).orEmpty()) {
                val oc = o as? JsonObject ?: continue
                if ((oc["book"] as? JsonPrimitive)?.contentOrNull != PropLineClient.NOVIG) continue
                if ((oc["is_plus_ev"] as? JsonPrimitive)?.booleanOrNull != true) continue
                val ev = (oc["ev_pct"] as? JsonPrimitive)?.doubleOrNull ?: continue
                if (ev < MIN_EV || ev > MAX_EV) continue
                val name = (oc["name"] as? JsonPrimitive)?.contentOrNull ?: continue
                val price = (oc["price"] as? JsonPrimitive)?.intOrNull ?: continue
                val fair = (probs[name] as? JsonPrimitive)?.doubleOrNull ?: continue
                val upd = (oc["last_update"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now
                out += PlEvRow(sport, league, eventId, home, away, startsAtMs, market, player, (oc["point"] as? JsonPrimitive)?.doubleOrNull ?: point, name, price, ev, fair, source, upd, now)
            }
        }
        return Parsed(out, false)
    }
}

/** PropLine's `/best-line` (every book's price for every line of a game) read for the lines Novig's +EV rows are on, and the cross-check it gives. */
object PropLineBestLine {
    /** line -> outcome name -> every book's price. */
    class Board(val lines: List<Line>)
    class Line(val market: String, val player: String, val point: Double?, val sides: Map<String, List<PlBookPrice>>)

    fun parse(raw: String, json: Json, now: Long): Board {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return Board(emptyList())
        if ((root["redacted"] as? JsonPrimitive)?.booleanOrNull == true) return Board(emptyList())
        val out = ArrayList<Line>()
        for (l in (root["lines"] as? JsonArray).orEmpty()) {
            val o = l as? JsonObject ?: continue
            val market = (o["market_key"] as? JsonPrimitive)?.contentOrNull ?: continue
            val sides = LinkedHashMap<String, List<PlBookPrice>>()
            for ((name, v) in (o["sides"] as? JsonObject).orEmpty()) {
                val prices = ((v as? JsonObject)?.get("all_prices") as? JsonArray).orEmpty().mapNotNull { p ->
                    val po = p as? JsonObject ?: return@mapNotNull null
                    val book = (po["book"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val price = (po["price"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                    val upd = (po["last_update"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now
                    PlBookPrice(book, price, upd)
                }
                if (prices.isNotEmpty()) sides[name] = prices
            }
            if (sides.isNotEmpty()) out += Line(market, (o["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (o["point"] as? JsonPrimitive)?.doubleOrNull, sides)
        }
        return Board(out)
    }

    private fun toks(s: String): List<String> = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.isNotEmpty() }
    private fun sameName(a: String, b: String): Boolean {
        val x = toks(a); val y = toks(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x.toSet().containsAll(y) || y.toSet().containsAll(x) || x.last() == y.last())
    }

    private fun implied(american: Int): Double = if (american > 0) 100.0 / (american + 100.0) else -american / (-american + 100.0)

    /** [row] with the other books' prices for its outcome, and how many of the books that quote both sides say Novig's price is +EV by their own devigged number. */
    fun attach(row: PlEvRow, board: Board): PlEvRow {
        val spread = row.market == "spreads"
        val line = board.lines.firstOrNull { l ->
            l.market == row.market && (if (row.player.isBlank()) l.player.isBlank() else sameName(l.player, row.player)) &&
                (l.point == null && row.point == null || (l.point != null && row.point != null && (if (spread) kotlin.math.abs(kotlin.math.abs(l.point) - kotlin.math.abs(row.point)) < 1e-9 else kotlin.math.abs(l.point - row.point) < 1e-9)))
        } ?: return row
        val ours = line.sides.keys.firstOrNull { it.equals(row.outcome, true) } ?: line.sides.keys.firstOrNull { sameName(it, row.outcome) } ?: return row
        val theirs = line.sides.keys.filter { it != ours }
        val mine = line.sides.getValue(ours).filter { it.book != PropLineClient.NOVIG }
        if (mine.isEmpty()) return row
        val novigCost = implied(row.price)
        var two = 0; var agree = 0; var pinn: Boolean? = null
        for (p in mine) {
            val opp = theirs.mapNotNull { n -> line.sides.getValue(n).firstOrNull { it.book == p.book }?.price }
            if (opp.isEmpty()) continue
            val a = implied(p.price); val b = opp.sumOf { implied(it) }
            val fair = a / (a + b)
            two++
            val good = fair > novigCost
            if (good) agree++
            if (p.book == "pinnacle") pinn = good
        }
        return row.copy(others = mine.sortedByDescending { it.price }.take(16), twoSided = two, agree = agree, pinnacleAgrees = pinn)
    }
}

/** Finds the line in Vigilant's own scan that is the same Novig bet as a PropLine +EV row, so the phone can show Vigilant's view and open the Bet sheet on it. */
object PropLineMatch {
    private fun toks(s: String): List<String> = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.isNotEmpty() }
    private fun sameTeam(a: String, b: String): Boolean {
        val x = toks(a); val y = toks(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x.toSet().containsAll(y) || y.toSet().containsAll(x) || x.last() == y.last())
    }

    fun find(row: PlEvRow, all: List<com.tjshea.vigilant.data.scanner.Opportunity>, requireQuote: Boolean = true): com.tjshea.vigilant.data.scanner.Opportunity? {
        val stat = PropLineProps.MARKETS[row.market]
        return all.firstOrNull { o ->
            if (o.league.displayName != row.league || (requireQuote && o.quote == null)) return@firstOrNull false
            if (kotlin.math.abs(o.event.startsTs - row.startsAtMs) > 3 * 3_600_000L) return@firstOrNull false
            val teams = o.event.description.split(" @ ")
            if (teams.size != 2 || !((sameTeam(teams[0], row.away) && sameTeam(teams[1], row.home)) || (sameTeam(teams[0], row.home) && sameTeam(teams[1], row.away)))) return@firstOrNull false
            val side = (o.target as? com.tjshea.vigilant.data.scanner.OutcomeTarget.Is)?.side
            val line = o.lineKey?.line
            when (row.market) {
                "h2h" -> o.kind == com.tjshea.vigilant.data.reference.LineKind.MONEYLINE && o.lineKey?.period == 0 && sameTeam(o.selection, row.outcome)
                "totals" -> o.kind == com.tjshea.vigilant.data.reference.LineKind.TOTAL && o.lineKey?.period == 0 && line != null && row.point != null && kotlin.math.abs(line - row.point) < 1e-9 &&
                    side == (if (row.outcome.equals("Over", true)) com.tjshea.vigilant.data.reference.Side.OVER else com.tjshea.vigilant.data.reference.Side.UNDER)
                "spreads" -> o.kind == com.tjshea.vigilant.data.reference.LineKind.SPREAD && o.lineKey?.period == 0 && row.point != null &&
                    sameTeam(o.selection.replace(Regex("\\s*[+-]?\\d+(\\.\\d+)?$"), ""), row.outcome) &&
                    Regex("([+-]?\\d+(\\.\\d+)?)$").find(o.selection)?.value?.toDoubleOrNull()?.let { kotlin.math.abs(it - row.point) < 1e-9 } == true
                else -> stat != null && o.kind == com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP && o.market.marketType == stat && row.player.isNotBlank() &&
                    com.tjshea.vigilant.data.match.PlayerNames.same(o.lineKey?.subject, row.player) && line != null && row.point != null && kotlin.math.abs(line - row.point) < 1e-9 &&
                    side == (if (row.outcome.equals("Over", true)) com.tjshea.vigilant.data.reference.Side.OVER else com.tjshea.vigilant.data.reference.Side.UNDER)
            }
        }
    }

    /** [row] with Vigilant's own view of the same Novig bet and the line itself for the phone's Bet sheet. */
    fun attach(row: PlEvRow, all: List<com.tjshea.vigilant.data.scanner.Opportunity>): PlEvRow {
        val o = find(row, all) ?: return row
        return row.copy(
            vigilantEv = o.evPercent, vigilantStars = o.rating?.stars,
            novigNowPrice = o.quote?.let { com.tjshea.vigilant.engine.Odds.probabilityToAmerican(it.price.coerceIn(0.001, 0.999)) }, novigNowAtMs = o.bookFetchedAtMs,
            novigNowDepth = o.depth?.dollarCost, vigilantFair = o.fairProbability,
            opp = runCatching { com.tjshea.vigilant.data.cloud.CloudCodec.encode(o).toString() }.getOrNull(),
        )
    }
    /**
     * A row Vigilant's scan did not price (it has the Novig market but no fair odds for it) made bettable from PropLine's own fair, cut by the same prop haircut the screen ranks on: the Bet sheet gets the
     * line, the +EV quote at Novig's first price, the depth and the Kelly stake, and re-prices at Novig's live book at the tap like every other Bet button. No ladder or a live game = no button.
     */
    fun attachUnpriced(row: PlEvRow, unpriced: List<com.tjshea.vigilant.data.scanner.Opportunity>, settings: ScanSettings, now: Long): PlEvRow {
        if (row.opp != null || unpriced.isEmpty()) return row
        val o = find(row, unpriced, requireQuote = false) ?: return row
        val fee = o.market.fee ?: return row
        if (o.ladder.isEmpty() || o.event.isLive) return row
        val cut = if (settings.propHaircut) com.tjshea.vigilant.data.scanner.PropHaircut.points(com.tjshea.vigilant.data.scanner.BetKind.of(o.marketLabel, o.selection), o.selection) else 0.0
        val p = (row.fairProb - cut).coerceIn(0.001, 0.999)
        val quote = com.tjshea.vigilant.engine.EvMath.quote(p, o.ladder.first().price, fee, false)
        val depth = com.tjshea.vigilant.engine.EvMath.positiveDepth(o.ladder, p, fee, false)
        val stake = if (quote.evPercent > 0) com.tjshea.vigilant.engine.EvMath.suggestedStake(quote, settings.bankroll, settings.kellyMultiplier, depth?.dollarCost, (o.event.startsTs - now) / 3_600_000.0) else null
        val priced = o.copy(fairProbability = p, quote = quote, depth = depth, suggestedStake = stake, fairAsOfMs = now)
        val enc = runCatching { com.tjshea.vigilant.data.cloud.CloudCodec.encode(priced).toString() }.getOrNull() ?: return row
        return row.copy(
            opp = enc, oppFromPropLine = true, novigNowPrice = com.tjshea.vigilant.engine.Odds.probabilityToAmerican(quote.price.coerceIn(0.001, 0.999)),
            novigNowAtMs = o.bookFetchedAtMs, novigNowDepth = depth?.dollarCost,
        )
    }
}

/** The one-line hit-rate summary of a player's `/trends` answer for the side and line of a bet. The shape is read defensively (research/propline_vs_vigilant_2026-10-16.md: the probe saves a real sample for TCL65c). */
object PropLineTrends {
    fun summarize(raw: String, json: Json, side: String): String {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return ""
        val node = find(root) ?: return ""
        val parts = ArrayList<String>()
        for (w in listOf("last_5", "last_10", "last_20")) {
            val o = (node[w] as? kotlinx.serialization.json.JsonObject) ?: continue
            val over = o["over"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() } ?: continue
            val under = (o["under"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: continue
            val mine = if (side.equals("Under", true)) under else over
            parts += "${w.removePrefix("last_").let { "last $it" }}: $mine of ${over + under}"
        }
        if (parts.isEmpty()) return ""
        val avg = (node["avg_actual"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
        val line = (node["recent_line"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
        return "${side.ifBlank { "Over" }} hit " + parts.joinToString(" · ") + if (avg != null) " · averages ${"%.1f".format(java.util.Locale.US, avg)}" + (line?.let { " (recent line ${"%.1f".format(java.util.Locale.US, it)})" } ?: "") else ""
    }

    /** The first object anywhere in the answer that carries a `last_10` or `last_5` window (the market's trend). */
    private fun find(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonObject? = when (e) {
        is kotlinx.serialization.json.JsonObject -> if (e.containsKey("last_10") || e.containsKey("last_5")) e else e.values.firstNotNullOfOrNull { find(it) }
        is kotlinx.serialization.json.JsonArray -> e.firstNotNullOfOrNull { find(it) }
        else -> null
    }
}

/** The engine's sweep: the picked leagues' pregame games within [PropLineEv.WINDOW_HOURS], one `/ev` request each, six at a time (PropLine allows 20 in flight; its client paces the starts). */
class PropLineEvSweep(private val client: PropLineClient, private val json: Json = Json { ignoreUnknownKeys = true }) {
    private val trendCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
    private companion object { const val TREND_ROWS = 40 }
    suspend fun sweep(settings: ScanSettings, now: Long, scanned: List<com.tjshea.vigilant.data.scanner.Opportunity> = emptyList(), unpriced: List<com.tjshea.vigilant.data.scanner.Opportunity> = emptyList()): PlEvFeed {
        val plan = client.plan() ?: return PlEvFeed(now, emptyList(), tier = "free", note = "needs a paid PropLine key (Hobby or Pro): the free key's +EV answers are redacted")
        var requests = 0
        var games = 0
        var note = ""
        val rows = ArrayList<PlEvRow>()
        val horizon = now + PropLineEv.WINDOW_HOURS * 3_600_000L
        val leagues = settings.leagues.mapNotNull { Leagues.byNovigName(it) }.filter { it.oddsApiSportKey in PropLineClient.SPORTS }.distinctBy { it.oddsApiSportKey }
        val gate = Semaphore(6)
        for (league in leagues) {
            val sport = league.oddsApiSportKey
            val events = try {
                requests++
                client.events(sport)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                note = "${league.displayName}: ${e.message?.take(80) ?: e.javaClass.simpleName}"
                continue
            }
            val soon = events.filter { it.commenceMs in (now + 1)..horizon }
            games += soon.size
            val results = coroutineScope {
                soon.map { ev ->
                    async {
                        gate.withPermit {
                            try {
                                requests++
                                val plId = ev.id.removePrefix(PropLinePropsSource.PREFIX)
                                val raw = client.evRaw(sport, plId) ?: return@withPermit null
                                val parsed = PropLineEv.parse(raw, json, sport, league.displayName, plId, ev.home, ev.away, ev.commenceMs, now)
                                if (parsed.rows.isEmpty()) return@withPermit parsed
                                // The same bets at every other book now (one more request for the game), and Vigilant's own view of each Novig line.
                                requests++
                                val board = runCatching { client.bestLineRaw(sport, plId, parsed.rows.map { it.market }.distinct())?.let { PropLineBestLine.parse(it, json, now) } }.getOrNull()
                                PropLineEv.Parsed(parsed.rows.map { r -> PropLineMatch.attachUnpriced(PropLineMatch.attach(board?.let { b -> PropLineBestLine.attach(r, b) } ?: r, scanned), unpriced, settings, now) }, false)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                note = "${league.displayName}: ${e.message?.take(80) ?: e.javaClass.simpleName}"
                                null
                            }
                        }
                    }
                }.map { it.await() }
            }
            for (p in results) if (p != null) rows += p.rows
        }
        val sorted = rows.sortedByDescending { it.evPct }
        // Hit rates for the props nearest the top (one request per player and market, kept an hour): a line that also hit most nights recently is worth more than one that did not.
        val want = sorted.filter { it.player.isNotBlank() && it.price <= 300 }.take(TREND_ROWS)
        val withTrend = HashMap<String, String>()
        coroutineScope {
            want.map { r ->
                async {
                    gate.withPermit {
                        val key = "${r.sport}|${r.player}|${r.market}|${r.outcome}"
                        val cached = trendCache[key]?.takeIf { now - it.first < 3_600_000L }
                        val text = cached?.second ?: try {
                            requests++
                            client.trendsRaw(r.sport, r.player, r.market)?.let { PropLineTrends.summarize(it, json, r.outcome) }.orEmpty().also { trendCache[key] = now to it }
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { "" }
                        if (text.isNotBlank()) synchronized(withTrend) { withTrend["${r.eventId}|${r.market}|${r.player}|${r.point}|${r.outcome}"] = text }
                    }
                }
            }.forEach { it.await() }
        }
        val out = if (withTrend.isEmpty()) sorted else sorted.map { r -> withTrend["${r.eventId}|${r.market}|${r.player}|${r.point}|${r.outcome}"]?.let { r.copy(trend = it) } ?: r }
        return PlEvFeed(now, out, games, requests, plan.tier, note)
    }
}
