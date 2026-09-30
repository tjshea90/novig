package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.engine.Devig
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Pinnacle's closing lines from ParlayAPI (Tj, 2026-09-30, RESEARCH.md §43), the sharpest close there is, asked first when Tj has a ParlayAPI
 * key: player props from its daily closing-lines file (`/v1/historical/closing-lines.json?date=&sport_key=&source=pinnacle`, 1 credit per 1,000
 * rows) and game lines from `/v1/sports/{sport}/closing-lines?bookmakers=pinnacle&daysFrom=` (5 credits a league), each the book's last price
 * before the start, devigged across its two sides. What a key's plan can reach back to is its own (free 48 hours, $5 a week, $20 a month); a
 * league-day's answers are kept for [KEEP_MS], so the 3-hourly look doesn't buy them twice. Calls go through the same [KeyPool] as ParlayAPI's
 * scans (metered, key 1 first, a spent key skipped), and scans leave each key's last credits to these ([OddsFeed.PARLAY]'s reserve).
 */
class ParlayCloses(
    private val http: OkHttpClient,
    private val pool: KeyPool,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val base: String = "https://parlay-api.com/v1",
    private val clock: () -> Long = System::currentTimeMillis,
) : CloseSource {

    /** Requests made (tests, Diagnostics). */
    @Volatile
    var requests = 0
        private set

    private class Kept(val atMs: Long, val value: JsonElement?)

    private val kept = HashMap<String, Kept>()

    override val id: String get() = ID

    /** Settings' ParlayAPI switch: off, nothing is spent on closes either. */
    @Volatile
    var enabled: Boolean = true

    /** Asked only when ParlayAPI is on and Tj has a key. */
    override val active: Boolean get() = enabled && pool.keyCount() > 0

    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
        if (!active) return emptyMap()
        val out = HashMap<String, CloseLookup>()
        for (b in bets) {
            val sport = sportKeyOf(b)
            if (sport == null) {
                out[b.id] = CloseLookup.None("ParlayAPI has no key for ${b.league.ifBlank { "this league" }}")
                continue
            }
            val pick = BetGrader.pickOf(b)
            out[b.id] = when {
                pick is BetGrader.Pick.Prop -> prop(b, pick, sport)
                EspnCloses.gameLine(pick) -> gameLine(b, pick!!, sport)
                else -> CloseLookup.None("ParlayAPI keeps full-game lines and player props")
            }
        }
        return out
    }

    // ---- props ------------------------------------------------------------------------------------------------------

    private suspend fun prop(b: TrackedBet, pick: BetGrader.Pick.Prop, sport: String): CloseLookup {
        val markets = PropStats.oddsApiMarketsFor(sport, pick.stat)
        if (markets.isEmpty()) return CloseLookup.None("ParlayAPI has no ${PropStats.displayName(pick.stat).lowercase()} closes")
        // One file per UTC day; an evening game in the US can sit under either its UTC date or its US date.
        val start = Instant.ofEpochMilli(b.startsTs)
        val dates = listOf(start.atZone(ZoneOffset.UTC).toLocalDate(), start.atZone(US_EAST).toLocalDate()).distinct()
        var last: CloseLookup = CloseLookup.Later("ParlayAPI didn't answer")
        for (date in dates) {
            val root = get("/historical/closing-lines.json", listOf("date" to date.toString(), "sport_key" to sport, "source" to "pinnacle", "limit" to "10000"), cost = 1)
                ?: return CloseLookup.Later("ParlayAPI didn't answer")
            last = parseProp(root, pick, markets, b.startsTs)
            if (last is CloseLookup.Found) return last
        }
        return last
    }

    // ---- game lines -------------------------------------------------------------------------------------------------

    private suspend fun gameLine(b: TrackedBet, pick: BetGrader.Pick, sport: String): CloseLookup {
        // Their API takes 1..30 days back; a game longer ago than that is past what a game-line call can reach.
        val back = (clock() - b.startsTs) / 86_400_000L + 1
        if (back > MAX_DAYS) return CloseLookup.None("Older than ParlayAPI's $MAX_DAYS-day closing-lines window")
        val days = back.coerceIn(1, MAX_DAYS.toLong())
        val root = get("/sports/$sport/closing-lines", listOf("bookmakers" to "pinnacle", "daysFrom" to days.toString(), "oddsFormat" to "american"), cost = 5)
            ?: return CloseLookup.Later("ParlayAPI didn't answer")
        return parseGameLine(root, b, pick)
    }

    /** [cost]: what the call is expected to cost (the server's own figure is recorded when it sends one). Null: ask again later. */
    private suspend fun get(path: String, params: List<Pair<String, String>>, cost: Int): JsonElement? {
        val cacheKey = path + params
        kept[cacheKey]?.takeIf { clock() - it.atMs < KEEP_MS }?.let { return it.value }
        val value = try {
            pool.execute(cost) { key ->
                // ParlayAPI's best practices: the key in the X-API-Key header, not the URL; one retry a second later for a 502/503/504 or a
                // dropped connection, none for 4xx.
                val url = (base + path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                val request = Request.Builder().url(url).get().header("X-API-Key", key).build()
                requests++
                var reply = try {
                    http.newCall(request).awaitText()
                } catch (e: IOException) {
                    kotlinx.coroutines.delay(RETRY_AFTER_MS)
                    requests++
                    http.newCall(request).awaitText()
                }
                if (reply.code in 502..504) {
                    kotlinx.coroutines.delay(RETRY_AFTER_MS)
                    requests++
                    reply = http.newCall(request).awaitText()
                }
                val credits = com.tjshea.vigilant.data.keys.CreditHeaders.read({ reply.headers[it] }, clock())
                val body = reply.body
                when {
                    reply.isSuccessful -> KeyAttemptResult.Success(json.parseToJsonElement(body), credits.cost ?: reply.headers["X-Export-Credits"]?.toDoubleOrNull()?.toInt(), credits.remaining, credits.used, credits.resetAtMs)
                    // Past the plan's history window, or no such sport: an answer (nothing to find), not a failure.
                    reply.code == 404 || (reply.code == 403 && body.contains("HISTORICAL_LIMIT", ignoreCase = true)) ->
                        KeyAttemptResult.Success(JsonArray(emptyList()), credits.cost ?: 0, credits.remaining, credits.used, credits.resetAtMs)
                    reply.code == 429 -> KeyAttemptResult.RateLimited(reply.headers["Retry-After"]?.toLongOrNull()?.times(1000) ?: 2_000, "HTTP 429")
                    (reply.code == 401 || reply.code == 403) &&
                        (body.contains("credit_limit_exceeded") || body.contains("quota", ignoreCase = true) || body.contains("OUT_OF_USAGE_CREDITS")) ->
                        KeyAttemptResult.Depleted("monthly credits used up")
                    reply.code == 401 || reply.code == 403 -> KeyAttemptResult.Invalid("HTTP ${reply.code}" + credits.requestId?.let { " (request $it)" }.orEmpty())
                    else -> throw IOException("HTTP ${reply.code}" + credits.requestId?.let { " (request $it)" }.orEmpty())
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: AllKeysExhaustedException) {
            null
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
        if (value != null) kept[cacheKey] = Kept(clock(), value)
        return value
    }

    companion object {
        const val ID = "parlay"

        /** ParlayAPI's best practices: a 502 or a dropped connection is retried once, this much later. */
        const val RETRY_AFTER_MS = 1_000L

        /** A league-day's closes are kept this long: the file itself is cached 6 hours on their side. */
        const val KEEP_MS = 6 * 60 * 60_000L

        /** A close's game must start within this of the bet's. */
        private const val START_GAP_MS = 3 * 60 * 60_000L

        /** The most days back `/sports/{sport}/closing-lines` takes. */
        const val MAX_DAYS = 30

        private val US_EAST = java.time.ZoneId.of("America/New_York")

        private fun JsonElement?.obj() = this as? JsonObject
        private fun JsonElement?.arr() = (this as? JsonArray).orEmpty()
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.toDoubleOrNull()

        /** The Odds API sport key of the bet's league (ParlayAPI uses the same keys). */
        fun sportKeyOf(b: TrackedBet): String? =
            Leagues.ALL.firstOrNull { it.novigName.equals(b.league, true) || it.displayName.equals(b.league, true) }?.oddsApiSportKey?.takeIf { it.isNotBlank() }

        private fun ms(s: String?): Long? = s?.let { runCatching { Instant.parse(if (it.endsWith("Z") || it.contains('+')) it else it + "Z").toEpochMilli() }.getOrNull() }

        /** A price to its vigged implied probability: American (-110, +120), or decimal (1.91) if a feed ever sends that. */
        private fun implied(v: Double?): Double? = when {
            v == null -> null
            abs(v) >= 100 -> 1.0 / Odds.americanToDecimal(Math.round(v).toInt())
            v > 1.0 && v < 100 -> 1.0 / v
            else -> null
        }

        /** A row's side: its price, else the implied probability it carries (0..1, or a percent). */
        private fun side(r: JsonObject, price: String, prob: String): Double? =
            implied(r.num(price)) ?: r.num(prob)?.let { if (it > 1.0) it / 100 else it }?.takeIf { it > 0.0 && it < 1.0 }

        private fun fair(mine: Double, other: Double) = Devig.multiplicative(listOf(mine, other))[0]

        /** Rows of ParlayAPI's closing-lines file: the whole answer may be the list, or `{rows: [...]}`. */
        private fun rowsOf(root: JsonElement): List<JsonObject> = ((root.obj()?.get("rows") ?: root) as? JsonArray).orEmpty().mapNotNull { it.obj() }

        /** [pick]'s Pinnacle close in a closing-lines file: its player, its market, its exact line, its game's start. */
        fun parseProp(root: JsonElement, pick: BetGrader.Pick.Prop, markets: Collection<String>, startsTs: Long): CloseLookup {
            val rows = rowsOf(root).filter { r ->
                r.str("market_key") in markets && (r.str("source") ?: r.str("bookmaker") ?: "pinnacle").equals("pinnacle", true) &&
                    PlayerNames.same(r.str("player_name") ?: r.str("player"), pick.player) &&
                    (r.str("commence_time")?.let(::ms)?.let { abs(it - startsTs) <= START_GAP_MS } ?: true)
            }
            if (rows.isEmpty()) return CloseLookup.None("Pinnacle's close for this prop isn't in ParlayAPI's file")
            val exact = rows.firstOrNull { r -> r.num("line")?.let { abs(it - pick.line) < 1e-6 } == true }
                ?: return CloseLookup.None("Pinnacle closed this prop at ${rows.mapNotNull { it.num("line") }.distinct().joinToString(" / ")}, not your ${pick.line}")
            val over = side(exact, "over_price", "over_implied_prob") ?: return CloseLookup.None("Pinnacle's close has no over price")
            val under = side(exact, "under_price", "under_implied_prob") ?: return CloseLookup.None("Pinnacle's close has no under price")
            return CloseLookup.Found(if (pick.over) fair(over, under) else fair(under, over), "ParlayAPI · Pinnacle close")
        }

        /** [pick]'s Pinnacle close among ParlayAPI's closing-lines events (The Odds API's event shape). */
        fun parseGameLine(root: JsonElement, b: TrackedBet, pick: BetGrader.Pick): CloseLookup {
            val m = NovigText.parseMatchup(b.eventName) ?: return CloseLookup.None("Couldn't read the teams")
            val events = ((root.obj()?.get("data") ?: root.obj()?.get("events") ?: root) as? JsonArray).orEmpty().mapNotNull { it.obj() }
            val game = events.filter { e -> e.str("commence_time")?.let(::ms)?.let { abs(it - b.startsTs) <= START_GAP_MS } ?: false }
                .maxByOrNull { e -> TeamMatcher.similarity(m.home, e.str("home_team").orEmpty()) + TeamMatcher.similarity(m.away, e.str("away_team").orEmpty()) }
                ?.takeIf { e -> TeamMatcher.similarity(m.home, e.str("home_team").orEmpty()) >= 0.5 && TeamMatcher.similarity(m.away, e.str("away_team").orEmpty()) >= 0.5 }
                ?: return CloseLookup.None("Not in ParlayAPI's closing lines")
            val book = game["bookmakers"].arr().mapNotNull { it.obj() }.firstOrNull { it.str("key") == "pinnacle" }
                ?: return CloseLookup.None("No Pinnacle close for this game")
            fun market(key: String) = book["markets"].arr().mapNotNull { it.obj() }.firstOrNull { it.str("key") == key }?.get("outcomes").arr().mapNotNull { it.obj() }
            fun team(o: JsonObject, name: String) = TeamMatcher.similarity(name, o.str("name").orEmpty()) >= 0.5
            return when (pick) {
                is BetGrader.Pick.Moneyline -> {
                    val outs = market("h2h")
                    if (outs.size != 2) return CloseLookup.None("No two-way Pinnacle moneyline close")
                    val mine = outs.firstOrNull { team(it, pick.team) } ?: return CloseLookup.None("Couldn't tell which team this is")
                    val other = outs.first { it !== mine }
                    found(mine, other)
                }
                is BetGrader.Pick.Spread -> {
                    val outs = market("spreads")
                    val mine = outs.firstOrNull { team(it, pick.team) } ?: return CloseLookup.None("No Pinnacle spread close for this team")
                    val other = outs.firstOrNull { it !== mine } ?: return CloseLookup.None("No Pinnacle spread close for this game")
                    val point = mine.num("point") ?: return CloseLookup.None("No spread on Pinnacle's close")
                    if (abs(point - pick.line) > 1e-6) return CloseLookup.None("Pinnacle closed at ${point}, not your ${pick.line}")
                    found(mine, other)
                }
                is BetGrader.Pick.Total -> {
                    val outs = market("totals")
                    val over = outs.firstOrNull { it.str("name").equals("Over", true) } ?: return CloseLookup.None("No Pinnacle total close")
                    val under = outs.firstOrNull { it.str("name").equals("Under", true) } ?: return CloseLookup.None("No Pinnacle total close")
                    val point = over.num("point") ?: return CloseLookup.None("No total on Pinnacle's close")
                    if (abs(point - pick.line) > 1e-6) return CloseLookup.None("Pinnacle's total closed at ${point}, not your ${pick.line}")
                    if (pick.over) found(over, under) else found(under, over)
                }
                else -> CloseLookup.None("ParlayAPI keeps full-game lines and player props")
            }
        }

        private fun found(mine: JsonObject, other: JsonObject): CloseLookup {
            val p = implied(mine.num("price")) ?: return CloseLookup.None("ParlayAPI's close has no price")
            val q = implied(other.num("price")) ?: return CloseLookup.None("ParlayAPI's close has no price")
            return CloseLookup.Found(fair(p, q), "ParlayAPI · Pinnacle close")
        }
    }
}
