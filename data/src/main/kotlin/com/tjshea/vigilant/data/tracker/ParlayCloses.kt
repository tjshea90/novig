package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.reference.ParlayMarkets
import com.tjshea.vigilant.data.reference.ParlayTennis
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
 * before the start, devigged across its two sides. What a key's plan can reach back to is its own (free 48 hours, $5 a week, $20 a month: /v1/meta/limits), and nothing older is asked; a
 * league-day's answers are kept for [KEEP_MS], so the 3-hourly look doesn't buy them twice. Calls go through the same [KeyPool] as ParlayAPI's
 * scans (metered, key 1 first, a spent key skipped), and scans leave each key's last credits to these ([OddsFeed.PARLAY]'s reserve).
 */
class ParlayCloses(
    private val http: OkHttpClient,
    private val pool: KeyPool,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val base: String = "https://parlay-api.com/v1",
    private val clock: () -> Long = System::currentTimeMillis,
    /** How many days back the key's plan reaches ([ParlayAccount.historyDays]: free 2, Starter 7, Pro 30); nothing older is asked. */
    private val historyDays: () -> Int = { DEFAULT_HISTORY_DAYS },
) : CloseSource {

    /** Requests made (tests, Diagnostics). */
    @Volatile
    var requests = 0
        private set

    private class Kept(val atMs: Long, val value: JsonElement?)

    // Shared with the scan study's grading (which runs beside the Tracker's own): two looks at once mustn't corrupt it.
    private val kept = java.util.concurrent.ConcurrentHashMap<String, Kept>()

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
                // A bet with no league on record is one of the ✓ marks imported before the Tracker kept bets (BetTracker.importPlaced).
                out[b.id] = CloseLookup.None(if (b.league.isBlank()) "No league on record (a ✓ mark imported before the Tracker)" else "ParlayAPI has no key for ${b.league}")
                continue
            }
            val pick = BetGrader.pickOf(b)
            // Past the plan's history (ParlayAPI's /v1/meta/limits: free 48 hours, Starter 7 days, Pro 30): it answers HISTORICAL_LIMIT.
            val days = historyDays()
            if (clock() - b.startsTs > days * 86_400_000L) {
                out[b.id] = CloseLookup.None("Older than your ParlayAPI plan's $days-day history")
                continue
            }
            out[b.id] = when {
                pick is BetGrader.Pick.Prop -> prop(b, pick, sport)
                EspnCloses.gameLine(pick) || setsLine(pick, sport) -> gameLine(b, pick!!, sport)
                else -> CloseLookup.None("ParlayAPI keeps full-game lines and player props")
            }
        }
        return out
    }

    // ---- props ------------------------------------------------------------------------------------------------------

    private suspend fun prop(b: TrackedBet, pick: BetGrader.Pick.Prop, sport: String): CloseLookup {
        if (pick.stat !in PropStats.parlayStats(sport)) return CloseLookup.None("ParlayAPI has no ${PropStats.displayName(pick.stat).lowercase()} closes")
        return fromFile(b, sport) { root -> parseProp(root, pick, sport, b.startsTs) }
    }

    /**
     * The day's closes file (props and Pinnacle's spreads, totals and moneylines, 1 credit per 1,000 rows), read with [parse]: one file per
     * UTC day, and an evening game in the US can sit under either its UTC date or its US date.
     */
    private suspend fun fromFile(b: TrackedBet, sport: String, parse: (JsonElement) -> CloseLookup): CloseLookup {
        val start = Instant.ofEpochMilli(b.startsTs)
        val dates = listOf(start.atZone(ZoneOffset.UTC).toLocalDate(), start.atZone(US_EAST).toLocalDate()).distinct()
        var last: CloseLookup = CloseLookup.Later("ParlayAPI didn't answer")
        for (date in dates) {
            val root = get("/historical/closing-lines.json", listOf("date" to date.toString(), "sport_key" to sport, "source" to "pinnacle", "limit" to "10000"), cost = 1)
                ?: return CloseLookup.Later("ParlayAPI didn't answer")
            last = parse(root)
            if (last is CloseLookup.Found) return last
        }
        return last
    }

    // ---- game lines -------------------------------------------------------------------------------------------------

    /** A tennis set spread or total sets: Pinnacle's set lines are in the closes file too (PARLAY_API.md §6.11). */
    private fun setsLine(pick: BetGrader.Pick?, sport: String): Boolean = sport.startsWith(TENNIS) && when (pick) {
        is BetGrader.Pick.Spread -> pick.period == BetGrader.Period.SETS
        is BetGrader.Pick.Total -> pick.period == BetGrader.Period.SETS
        else -> false
    }

    private suspend fun gameLine(b: TrackedBet, pick: BetGrader.Pick, sport: String): CloseLookup {
        // Spreads and totals: Pinnacle's in the day's closes file. Moneylines: its price at the start from `closing-lines` (5 credits a
        // league for every game in the window, the truest close there is), else the file's.
        if (pick !is BetGrader.Pick.Moneyline) return fromFile(b, sport) { root -> parseFileGameLine(root, b, pick) }
        // daysFrom reaches back whole days, never past the plan's history (asked beyond it, the answer is 403 HISTORICAL_LIMIT).
        val back = (clock() - b.startsTs) / 86_400_000L + 1
        val most = historyDays().coerceAtMost(MAX_DAYS).toLong()
        if (back <= most) {
            val root = get("/sports/$sport/closing-lines", listOf("bookmakers" to "pinnacle", "daysFrom" to back.coerceIn(1, most).toString(), "oddsFormat" to "american"), cost = 5)
                ?: return CloseLookup.Later("ParlayAPI didn't answer")
            val found = parseGameLine(root, b, pick)
            if (found is CloseLookup.Found) return found
        }
        return fromFile(b, sport) { root -> parseFileGameLine(root, b, pick) }
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

        private const val TENNIS = "tennis_"

        /** The most days back `/sports/{sport}/closing-lines` is asked for (a plan's own history can be shorter: [historyDays]). */
        const val MAX_DAYS = 30

        /** Until a key's plan is known: Starter's 7 days (Tj's plan, 2026-09-30). */
        const val DEFAULT_HISTORY_DAYS = 7

        /** A closes-file price taken longer than this before the start isn't the close. */
        const val CLOSE_WITHIN_MS = 2 * 60 * 60_000L

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

        /**
         * A closes-file row's price is a close only when it was taken within [CLOSE_WITHIN_MS] of the start (Tj's key, 2026-09-30: MLB's
         * file held Pinnacle props last seen 15 hours before first pitch, the board's spreads and totals 75 minutes before). Null: fine.
         */
        private fun tooEarly(r: JsonObject, startsTs: Long): String? {
            val at = r.str("snapshot_time")?.let(::ms) ?: return null
            val before = startsTs - at
            return if (before > CLOSE_WITHIN_MS) "Pinnacle's last price was ${before / 3_600_000L}h before the start: not a close" else null
        }

        /**
         * The game among [games] that is the bet's ([mHome] vs [mAway], starting about [startsTs]): the best [TeamMatcher.gameScore], the nearest start among
         * equal ones (a doubleheader's own game; the same pairing in two spellings). Null when none fits, or when two DIFFERENT pairings fit equally well:
         * a close from a guess could be another game's (Tj's scan-study file, 2026-10-03: Washington State -117 "closed" at +272). Order [games] so the
         * preferred one of equal starts comes first.
         */
        private fun <G> bestGame(mHome: String, mAway: String, startsTs: Long, games: List<G>, home: (G) -> String, away: (G) -> String, start: (G) -> Long?): G? {
            val scored = games.mapNotNull { g -> TeamMatcher.gameScore(mHome, mAway, home(g), away(g)).takeIf { it > 0.0 }?.let { g to it } }
            val top = scored.maxOfOrNull { it.second } ?: return null
            val tied = scored.filter { it.second >= top - 1e-9 }.map { it.first }
            val nearest = tied.minByOrNull { g -> abs((start(g) ?: startsTs) - startsTs) } ?: return null
            // The others at that score must be this game under another name (or its other start), not a different pairing.
            val same = tied.all { g ->
                TeamMatcher.similarity(home(nearest), home(g)) >= SAME_NAME && TeamMatcher.similarity(away(nearest), away(g)) >= SAME_NAME
            }
            return nearest.takeIf { same }
        }

        /** Two spellings of one team: the shorter name's words all found in the longer ([TeamMatcher.similarity]). */
        private const val SAME_NAME = 0.8

        /** [pick]'s Pinnacle close in a closes file: its player, its stat (each book's name for it, [ParlayMarkets]), its exact line, its game's start. */
        fun parseProp(root: JsonElement, pick: BetGrader.Pick.Prop, sportKey: String, startsTs: Long): CloseLookup {
            val rows = rowsOf(root).filter { r ->
                (r.str("source") ?: r.str("bookmaker") ?: "pinnacle").equals("pinnacle", true) &&
                    PlayerNames.same(r.str("player_name") ?: r.str("player"), pick.player) &&
                    (r.str("commence_time")?.let(::ms)?.let { abs(it - startsTs) <= START_GAP_MS } ?: true) &&
                    ParlayMarkets.statOf(sportKey, r.str("market_key").orEmpty(), r.str("market_label") ?: r.str("market")) == pick.stat
            }
            if (rows.isEmpty()) return CloseLookup.None("Pinnacle's close for this prop isn't in ParlayAPI's file")
            val exact = rows.filter { r -> r.num("line")?.let { abs(it - pick.line) < 1e-6 } == true }.maxByOrNull { it.str("snapshot_time")?.let(::ms) ?: 0L }
                ?: return CloseLookup.None("Pinnacle closed this prop at ${rows.mapNotNull { it.num("line") }.distinct().joinToString(" / ")}, not your ${pick.line}")
            tooEarly(exact, startsTs)?.let { return CloseLookup.None(it) }
            val over = side(exact, "over_price", "over_implied_prob") ?: return CloseLookup.None("Pinnacle's close has no over price")
            val under = side(exact, "under_price", "under_implied_prob") ?: return CloseLookup.None("Pinnacle's close has no under price")
            return CloseLookup.Found(if (pick.over) fair(over, under) else fair(under, over), "ParlayAPI · Pinnacle close")
        }

        /**
         * [pick]'s Pinnacle close from the day's closes file, whose game-line rows name a side each: a moneyline row per team (`player_name` the
         * team, `over_price` its price), a spread row per team and number, a total row with both sides.
         */
        fun parseFileGameLine(root: JsonElement, b: TrackedBet, pick: BetGrader.Pick): CloseLookup {
            val m = NovigText.parseMatchup(b.eventName) ?: return CloseLookup.None("Couldn't read the teams")
            // Tennis (PARLAY_API.md §6.11, the file read 2026-10-01): a match's own rows are Pinnacle's SET lines (spreads ±1.5, totals
            // 2.5, and `spreads_sets`/`totals_sets` copies); its games lines are rows of a "<Player> (Games)" match. A bet is closed only
            // from rows in its own unit: a games −1.5 must never take the sets −1.5's close.
            val tennis = sportKeyOf(b)?.startsWith(TENNIS) == true
            fun named(r: JsonObject, key: String) = r.str(key).orEmpty().removeSuffix(ParlayTennis.GAMES_SUFFIX)
            fun gamesRow(r: JsonObject) = r.str("home_team").orEmpty().endsWith(ParlayTennis.GAMES_SUFFIX)
            val inSets = (pick as? BetGrader.Pick.Spread)?.period == BetGrader.Period.SETS || (pick as? BetGrader.Pick.Total)?.period == BetGrader.Period.SETS
            val linePick = pick is BetGrader.Pick.Spread || pick is BetGrader.Pick.Total
            val near = rowsOf(root).filter { r ->
                (r.str("source") ?: "pinnacle").equals("pinnacle", true) &&
                    (r.str("commence_time")?.let(::ms)?.let { abs(it - b.startsTs) <= START_GAP_MS } ?: false) &&
                    (!tennis || gamesRow(r) == (linePick && !inSets))
            }
            // The rows of ONE game: the best fit among the games the file has near the start (never rows of several games, never the latest of them).
            val games = near.groupBy { Triple(named(it, "home_team"), named(it, "away_team"), it.str("commence_time")) }
            val game = bestGame(m.home, m.away, b.startsTs, games.keys.toList(), { it.first }, { it.second }, { it.third?.let(::ms) })
                ?: return CloseLookup.None("Not in ParlayAPI's closes file")
            val rows = games.getValue(game)
            fun of(vararg keys: String) = rows.filter { it.str("market_key") in keys }
            // A row's team is the side of this game it fits best (two "X State" teams both share a word with the bet's team).
            val pickTeam = when (pick) {
                is BetGrader.Pick.Moneyline -> pick.team
                is BetGrader.Pick.Spread -> pick.team
                else -> ""
            }
            val want = TeamMatcher.whichOf(pickTeam, game.first, game.second)
            fun side(r: JsonObject) = TeamMatcher.whichOf(named(r, "player_name"), game.first, game.second)
            fun team(r: JsonObject, @Suppress("UNUSED_PARAMETER") name: String) = want != 0 && side(r) == want
            fun otherTeam(r: JsonObject) = want != 0 && side(r) == 3 - want
            fun latest(rs: List<JsonObject>) = rs.maxByOrNull { it.str("snapshot_time")?.let(::ms) ?: 0L }
            fun pair(mine: JsonObject?, other: JsonObject?, what: String): CloseLookup {
                if (mine == null || other == null) return CloseLookup.None("No Pinnacle $what close for this game")
                tooEarly(mine, b.startsTs)?.let { return CloseLookup.None(it) }
                val p = implied(mine.num("over_price")) ?: return CloseLookup.None("ParlayAPI's close has no price")
                val q = implied(other.num("over_price")) ?: return CloseLookup.None("ParlayAPI's close has no price")
                return CloseLookup.Found(fair(p, q), "ParlayAPI · Pinnacle close")
            }
            return when (pick) {
                is BetGrader.Pick.Moneyline -> {
                    if (want == 0) return CloseLookup.None("Couldn't tell which team ${pick.team} is in ParlayAPI's game")
                    val ml = of("moneyline", "h2h")
                    val mine = latest(ml.filter { team(it, pick.team) })
                    val other = latest(ml.filter { otherTeam(it) })
                    pair(mine, other, "moneyline")
                }
                is BetGrader.Pick.Spread -> {
                    if (want == 0) return CloseLookup.None("Couldn't tell which team ${pick.team} is in ParlayAPI's game")
                    val sp = of("spreads", "alternate_spreads", "spreads_sets")
                    val mine = latest(sp.filter { team(it, pick.team) && it.num("line")?.let { l -> abs(l - pick.line) < 1e-6 } == true })
                    val other = latest(sp.filter { otherTeam(it) && it.num("line")?.let { l -> abs(l + pick.line) < 1e-6 } == true })
                    if (mine == null && sp.any { team(it, pick.team) }) return CloseLookup.None("Pinnacle didn't close your ${pick.line}")
                    pair(mine, other, "spread")
                }
                is BetGrader.Pick.Total -> {
                    val row = latest(of("totals", "alternate_totals", "totals_sets").filter { it.num("line")?.let { l -> abs(l - pick.line) < 1e-6 } == true })
                        ?: return CloseLookup.None("Pinnacle didn't close the total at your ${pick.line}")
                    tooEarly(row, b.startsTs)?.let { return CloseLookup.None(it) }
                    val over = implied(row.num("over_price")) ?: return CloseLookup.None("ParlayAPI's close has no price")
                    val under = implied(row.num("under_price")) ?: return CloseLookup.None("ParlayAPI's close has no price")
                    CloseLookup.Found(if (pick.over) fair(over, under) else fair(under, over), "ParlayAPI · Pinnacle close")
                }
                else -> CloseLookup.None("ParlayAPI keeps full-game lines and player props")
            }
        }

        /**
         * [pick]'s Pinnacle close from `closing-lines`: flat rows, one per game and market, `home_odds`/`away_odds` at the start (Tj's key,
         * 2026-09-30: `{"home_team":…,"away_team":…,"market_key":"h2h","home_odds":-122,"away_odds":111,"last_update":<the start>}`), or,
         * should it ever answer so, The Odds API's event shape.
         */
        fun parseGameLine(root: JsonElement, b: TrackedBet, pick: BetGrader.Pick): CloseLookup {
            val m = NovigText.parseMatchup(b.eventName) ?: return CloseLookup.None("Couldn't read the teams")
            val flat = ((root.obj()?.get("data") ?: root.obj()?.get("rows") ?: root) as? JsonArray).orEmpty().mapNotNull { it.obj() }.filter { it.containsKey("home_odds") }
            if (flat.isNotEmpty()) {
                if (pick !is BetGrader.Pick.Moneyline) return CloseLookup.None("ParlayAPI's closing lines carry moneylines")
                // The row of the game with the best fit (the latest update first among equal starts), not the latest row of every game that shares a word
                // with it: Tj's scan-study file, 2026-10-03, a Washington State -117 "closed" at +272, another State game's moneyline.
                val row = bestGame(
                    m.home, m.away, b.startsTs,
                    flat.filter { r ->
                        r.str("market_key").let { it == null || it == "h2h" } &&
                            (r.str("commence_time")?.let(::ms)?.let { abs(it - b.startsTs) <= START_GAP_MS } ?: false)
                    }.sortedByDescending { it.str("last_update")?.let(::ms) ?: 0L },
                    { it.str("home_team").orEmpty() }, { it.str("away_team").orEmpty() }, { it.str("commence_time")?.let(::ms) },
                ) ?: return CloseLookup.None("Not in ParlayAPI's closing lines")
                val home = implied(row.num("home_odds")) ?: return CloseLookup.None("ParlayAPI's close has no price")
                val away = implied(row.num("away_odds")) ?: return CloseLookup.None("ParlayAPI's close has no price")
                if (row.num("draw_odds") != null) return CloseLookup.None("A three-way close (with a draw) isn't Novig's two-way line")
                val homeSide = when (TeamMatcher.whichOf(pick.team, row.str("home_team").orEmpty(), row.str("away_team").orEmpty())) {
                    1 -> true
                    2 -> false
                    else -> return CloseLookup.None("Couldn't tell which team ${pick.team} is in ParlayAPI's game")
                }
                return CloseLookup.Found(if (homeSide) fair(home, away) else fair(away, home), "ParlayAPI · Pinnacle close")
            }
            val events = ((root.obj()?.get("data") ?: root.obj()?.get("events") ?: root) as? JsonArray).orEmpty().mapNotNull { it.obj() }
            val game = bestGame(
                m.home, m.away, b.startsTs, events.filter { e -> e.str("commence_time")?.let(::ms)?.let { abs(it - b.startsTs) <= START_GAP_MS } ?: false },
                { it.str("home_team").orEmpty() }, { it.str("away_team").orEmpty() }, { it.str("commence_time")?.let(::ms) },
            ) ?: return CloseLookup.None("Not in ParlayAPI's closing lines")
            val book = game["bookmakers"].arr().mapNotNull { it.obj() }.firstOrNull { it.str("key") == "pinnacle" }
                ?: return CloseLookup.None("No Pinnacle close for this game")
            fun market(key: String) = book["markets"].arr().mapNotNull { it.obj() }.firstOrNull { it.str("key") == key }?.get("outcomes").arr().mapNotNull { it.obj() }
            // The outcome that is the bet's team: the one of the game's two sides it fits best, never the first that shares a word ("Washington State" /
            // "Fresno State").
            fun mineOf(outs: List<JsonObject>, team: String): JsonObject? {
                val home = game.str("home_team").orEmpty()
                val away = game.str("away_team").orEmpty()
                val want = TeamMatcher.whichOf(team, home, away).takeIf { it != 0 } ?: return null
                return outs.singleOrNull { TeamMatcher.whichOf(it.str("name").orEmpty(), home, away) == want }
            }
            return when (pick) {
                is BetGrader.Pick.Moneyline -> {
                    val outs = market("h2h")
                    if (outs.size != 2) return CloseLookup.None("No two-way Pinnacle moneyline close")
                    val mine = mineOf(outs, pick.team) ?: return CloseLookup.None("Couldn't tell which team this is")
                    val other = outs.first { it !== mine }
                    found(mine, other)
                }
                is BetGrader.Pick.Spread -> {
                    val outs = market("spreads")
                    val mine = mineOf(outs, pick.team) ?: return CloseLookup.None("No Pinnacle spread close for this team")
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
