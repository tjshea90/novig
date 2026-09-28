package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/**
 * Pinnacle's prices through an independent Pinnacle feed (Pinnacle itself closed its public API on
 * 2025-07-23): **PinnWire** first (pinnwire.com, RESEARCH.md §22: its free key includes Pinnacle's
 * player props), then **pinnapi** (pinnapi.com, RESEARCH.md §11: its trial key has no props). Both
 * serve the same `/kit/v1/markets` JSON and allow **100 requests a day** per free key (20/min). One
 * request returns a whole sport's prematch board, so NFL and NCAAF share one call; a sport fetched
 * in the last [shareMs] is re-used rather than re-requested. Each host's [KeyPool] counts every
 * request per key (neither sends usage headers) and skips a key before its allowance runs out; a
 * 429 rests the key for the feed's own `retry_after_ms`. A host with no usable key is skipped for
 * the next one.
 *
 * Response shape (verified live on PinnWire 2026-09-27): `{events:[{event_id, league_name, starts,
 * home, away, periods:{num_0:{money_line:{home,away,draw?}, spreads:{"<hdp>":{hdp,home,away}},
 * totals:{"<pts>":{points,over,under}}}}}]}`, decimal odds, `hdp` is the home handicap. With
 * `include_specials=1` (asked only when player props are priced), props arrive as extra rows:
 * `{parent_id, special:"DJ Moore Total Receptions", special_category:"Player Props",
 * special_units:"Receptions", special_markets:{num_0:[{type:"total", prices:[{name:"Over",
 * points:3.5, price:1.735}, …]}]}}`.
 */
class PinnapiClient(
    private val http: OkHttpClient,
    private val json: Json,
    /** The feeds to try, in order: the first with a usable key answers. */
    private val hosts: List<Host>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val shareMs: Long = 60_000,
) : ReferenceSource {

    /** One Pinnacle feed: where it is, how it takes a key, and whether its keys get player props. */
    class Host(
        val name: String,
        val pool: KeyPool,
        val baseUrl: String,
        val authHeader: String,
        val props: Boolean,
    )

    /** pinnapi alone (the app before v0.16.0, and the tests written for it). */
    constructor(
        http: OkHttpClient,
        json: Json,
        pool: KeyPool,
        baseUrl: String = PINNAPI_URL,
        clock: () -> Long = System::currentTimeMillis,
        shareMs: Long = 60_000,
    ) : this(http, json, listOf(pinnapi(pool, baseUrl)), clock, shareMs)

    override val id = ID
    override val displayName = "Pinnacle"
    override val metered = true
    override val extraPropTypes: Set<String> get() = if (hosts.any { it.props }) PinnacleProps.STATS else emptySet()

    override fun supports(league: League) = league.pinnacleSportId != null

    private data class Board(val events: List<JsonObject>, val fetchedAtMs: Long)

    private val mutex = Mutex()

    /** By sport and whether player props were asked for. */
    private val boards = HashMap<Pair<Int, Boolean>, Board>()

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val sport = league.pinnacleSportId ?: return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        // Only sports whose props Pinnacle is read for ask for its specials (tennis has none mapped).
        val props = MarketFamily.PLAYER_PROPS in settings.families && PinnacleProps.hasProps(sport)
        val board = mutex.withLock { boardFor(sport, props) }
        return RefSnapshot(league.oddsApiSportKey, parse(board.events, league, board.fetchedAtMs), board.fetchedAtMs, provider = ID)
    }

    private suspend fun boardFor(sport: Int, props: Boolean): Board {
        boards[sport to props]?.takeIf { clock() - it.fetchedAtMs < shareMs }?.let { return it }
        var problem: String? = null
        for (host in hosts) {
            if (host.pool.keyCount() == 0) continue
            val events = try {
                fetch(host, sport, props && host.props)
            } catch (e: AllKeysExhaustedException) {
                // This feed's keys are spent (or refused): the next feed may still have some. The pool rests
                // them until their limit resets, so later scans go straight to the next feed, then back.
                if (problem == null) problem = e.message
                continue
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Any other failure (a 5xx, an unexpected status, a dropped connection) is no reason to go without
                // Pinnacle this scan either (Tj, 2026-09-28: "When pinnwire api usage runs out, automatically switch to
                // pinnapi until the usage resets").
                if (problem == null) problem = e.message ?: "Pinnacle (${host.name}): ${e.javaClass.simpleName}"
                continue
            }
            return Board(events, clock()).also { boards[sport to props] = it }
        }
        throw ReferenceException(problem ?: "No Pinnacle key. Add a free PinnWire or pinnapi key in Settings.")
    }

    private suspend fun fetch(host: Host, sport: Int, specials: Boolean): List<JsonObject> {
        val url = "${host.baseUrl}/markets".toHttpUrl().newBuilder()
            .addQueryParameter("sport_id", sport.toString())
            .addQueryParameter("event_type", "prematch")
            .apply { if (specials) addQueryParameter("include_specials", "1") }
            .build()
        return host.pool.execute(cost = 1) { key ->
            http.newCall(Request.Builder().url(url).header(host.authHeader, key).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    // The feed says which window was hit and exactly how long to wait.
                    response.code == 429 -> {
                        val err = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                        val window = (err?.get("window") as? JsonPrimitive)?.content
                        val waitMs = (err?.get("retry_after_ms") as? JsonPrimitive)?.longOrNull
                            ?: response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                            ?: 60_000L
                        if (window == "day" || window == "hour") {
                            KeyAttemptResult.Depleted(if (window == "day") "daily limit reached" else "hourly limit reached", waitMs)
                        } else {
                            KeyAttemptResult.RateLimited(waitMs, "per-minute limit")
                        }
                    }
                    response.code == 401 || response.code == 403 -> KeyAttemptResult.Invalid("HTTP ${response.code}, key refused")
                    !response.isSuccessful -> throw ReferenceException("Pinnacle (${host.name}) HTTP ${response.code}")
                    else -> KeyAttemptResult.Success(
                        (json.parseToJsonElement(body).jsonObject["events"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty(),
                    )
                }
            }
        }
    }

    companion object {
        const val ID = "pinnacle"
        const val PINNAPI_URL = "https://pinnapi.com/kit/v1"
        const val PINNWIRE_URL = "https://pinnwire.com/kit/v1"

        fun pinnapi(pool: KeyPool, baseUrl: String = PINNAPI_URL) = Host("pinnapi", pool, baseUrl, "x-portal-apikey", props = false)
        fun pinnwire(pool: KeyPool, baseUrl: String = PINNWIRE_URL) = Host("PinnWire", pool, baseUrl, "x-api-key", props = true)

        /**
         * Pinnacle's league names for each Novig league (their public naming). Compared
         * case-insensitively. If none of a sport's events carry a listed name, every event of the
         * sport is offered to the matcher instead: it still needs both teams and the start time to
         * agree, so a naming surprise costs nothing but a little CPU.
         */
        fun leagueNames(league: League): Set<String> = when (league.novigName) {
            "NFL" -> setOf("nfl")
            "NCAAF" -> setOf("ncaa", "ncaaf", "ncaa football")
            "NBA" -> setOf("nba")
            "NCAAB" -> setOf("ncaa", "ncaab", "ncaa basketball")
            "WNBA" -> setOf("wnba")
            "MLB" -> setOf("mlb")
            "NHL" -> setOf("nhl")
            "UFC" -> setOf("ufc")
            "Boxing" -> setOf("boxing", "boxing matches")
            else -> emptySet()
        }

        /**
         * Tennis leagues are per tournament ("ATP Beijing", "ATP Challenger Shanghai", "WTA Wuhan"): the
         * tour's name starts them. Compared case-insensitively.
         */
        fun leaguePrefixes(league: League): Set<String> = when (league.novigName) {
            "ATP" -> setOf("atp")
            "WTA" -> setOf("wta")
            else -> emptySet()
        }

        private fun inLeague(leagueName: String?, names: Set<String>, prefixes: Set<String>): Boolean {
            val n = leagueName?.trim()?.lowercase() ?: return false
            return n in names || prefixes.any { n == it || n.startsWith("$it ") || n.startsWith("$it-") }
        }

        /**
         * A tennis row that isn't one singles match's own lines: Pinnacle lists set betting as a match of its
         * own with "(Sets)" on each name, and doubles as "A / B". Either would price the wrong Novig market.
         */
        private fun tennisSideLine(e: JsonObject): Boolean =
            listOf("home", "away").any { k -> e.str(k)?.let { '(' in it || '/' in it } ?: true }

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
        private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

        /** `{points, over, under}` -> (points, over, under) when all three are real prices. */
        private fun ou(o: JsonObject?, pointsKey: String): Triple<Double, Double, Double>? {
            o ?: return null
            val pts = o.num(pointsKey) ?: return null
            val over = o.num("over") ?: return null
            val under = o.num("under") ?: return null
            return if (over > 1.0 && under > 1.0) Triple(pts, over, under) else null
        }

        fun parse(events: List<JsonObject>, league: League, fetchedAtMs: Long): List<RefEvent> {
            // Specials (player props) are rows of their own, pointing at their game by parent_id.
            val (specials, games) = events.partition { it.containsKey("parent_id") && it["parent_id"] !is kotlinx.serialization.json.JsonNull }
            val names = leagueNames(league)
            val prefixes = leaguePrefixes(league)
            val singles = if (league.tennis) games.filterNot(::tennisSideLine) else games
            val named = singles.filter { e -> inLeague(e.str("league_name"), names, prefixes) }
            val pool = named.ifEmpty { singles }
            val props = if (specials.isEmpty()) emptyMap() else specials.groupBy { (it["parent_id"] as? JsonPrimitive)?.content }
            return pool.mapNotNull { e ->
                val event = event(e, league, fetchedAtMs) ?: return@mapNotNull null
                val own = props[(e["event_id"] as? JsonPrimitive)?.content].orEmpty()
                if (own.isEmpty()) event else event.copy(markets = event.markets + own.mapNotNull { playerProp(it, league, fetchedAtMs) })
            }
        }

        /**
         * One Pinnacle player prop ("DJ Moore Total Receptions", Over/Under 3.5) as a two-way line,
         * when its stat is one Novig lists ([PinnacleProps]). Anything else (game props, ladders,
         * one-sided lines) is left out.
         */
        fun playerProp(row: JsonObject, league: League, fetchedAtMs: Long): RefBookMarket? {
            if (row.str("special_category") != "Player Props") return null
            val units = row.str("special_units")?.trim() ?: return null
            val stat = PinnacleProps.stat(league.pinnacleSportId, units) ?: return null
            val player = PinnacleProps.player(row.str("special") ?: return null, units) ?: return null
            val market = (row["special_markets"].obj()?.get("num_0") as? JsonArray)?.firstOrNull().obj() ?: return null
            if (market.str("type") != "total") return null
            val prices = (market["prices"] as? JsonArray)?.mapNotNull { it.obj() } ?: return null
            if (prices.size != 2) return null
            val over = prices.singleOrNull { it.str("name").equals("Over", true) } ?: return null
            val under = prices.singleOrNull { it.str("name").equals("Under", true) } ?: return null
            val point = over.num("points") ?: return null
            if (under.num("points") != point) return null
            val o = over.num("price") ?: return null
            val u = under.num("price") ?: return null
            if (!(o > 1.0 && u > 1.0 && o.isFinite() && u.isFinite())) return null
            return RefBookMarket(
                ID, "Pinnacle", LineKind.PLAYER_PROP,
                listOf(RefQuote(Side.OVER, o, point), RefQuote(Side.UNDER, u, point)),
                fetchedAtMs, subject = player, stat = stat,
            )
        }

        private fun event(e: JsonObject, league: League, fetchedAtMs: Long): RefEvent? {
            val home = e.str("home")?.takeIf { it.isNotBlank() } ?: return null
            val away = e.str("away")?.takeIf { it.isNotBlank() } ?: return null
            val starts = e.str("starts")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null
            val id = (e["event_id"] as? JsonPrimitive)?.content ?: return null
            val full = e["periods"].obj()?.get("num_0").obj() ?: return null
            // Stamped with the fetch time: the price is Pinnacle's current one as of that call,
            // however long ago it last moved.
            val updated = fetchedAtMs
            val markets = ArrayList<RefBookMarket>()

            // Tennis: the 1st set's winner too (period 1, two-way). Other sports' period-1 moneylines stay
            // out: a half can end level, and Novig prices none of them (RESEARCH.md §13).
            val mlPeriods = if (league.tennis) listOf("num_0" to 0, "num_1" to 1) else listOf("num_0" to 0)
            for ((periodKey, period) in mlPeriods) {
                val ml = e["periods"].obj()?.get(periodKey).obj()?.get("money_line").obj() ?: continue
                val h = ml.num("home")
                val a = ml.num("away")
                if (h != null && a != null && h > 1.0 && a > 1.0 && ml.num("draw") == null) {
                    markets += RefBookMarket(ID, "Pinnacle", LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, h, null), RefQuote(Side.AWAY, a, null)), updated, period)
                }
            }
            // A tennis match's lines are in games. Totals under 6 can only be sets (best of 3 or 5): then
            // that row's spreads and totals aren't the games lines Novig lists, and are left out.
            val setsNotGames = league.tennis && full["totals"].obj()?.values?.any { v -> (v.obj()?.num("points") ?: 99.0) < 6.0 } == true
            // Full game (num_0) and 1st half / first 5 innings (num_1), with every alternate line.
            for ((periodKey, period) in listOf("num_0" to 0, "num_1" to 1)) {
                if (setsNotGames) break
                val p = e["periods"].obj()?.get(periodKey).obj() ?: continue
                p["spreads"].obj()?.values?.forEach { v ->
                    val sp = v.obj() ?: return@forEach
                    val hdp = sp.num("hdp") ?: return@forEach
                    val h = sp.num("home") ?: return@forEach
                    val a = sp.num("away") ?: return@forEach
                    if (h > 1.0 && a > 1.0) {
                        markets += RefBookMarket(ID, "Pinnacle", LineKind.SPREAD, listOf(RefQuote(Side.HOME, h, hdp), RefQuote(Side.AWAY, a, -hdp)), updated, period)
                    }
                }
                p["totals"].obj()?.values?.forEach { v ->
                    ou(v.obj(), "points")?.let { (pts, o, u) ->
                        markets += RefBookMarket(ID, "Pinnacle", LineKind.TOTAL, listOf(RefQuote(Side.OVER, o, pts), RefQuote(Side.UNDER, u, pts)), updated, period)
                    }
                }
            }
            // Team totals (full game): the main line per team, plus alternates when sent. In tennis, each
            // player's games won (Novig's PLAYER_GAMES_WON).
            for ((sideKey, subject) in listOf("home" to RefBookMarket.HOME, "away" to RefBookMarket.AWAY)) {
                if (setsNotGames) break
                val lines = listOfNotNull(full["team_total"].obj()?.get(sideKey).obj()) +
                    full["team_totals"].obj()?.get(sideKey).obj()?.values?.mapNotNull { it.obj() }.orEmpty()
                lines.mapNotNull { ou(it, "points") }.distinctBy { it.first }.forEach { (pts, o, u) ->
                    markets += RefBookMarket(ID, "Pinnacle", LineKind.TEAM_TOTAL, listOf(RefQuote(Side.OVER, o, pts), RefQuote(Side.UNDER, u, pts)), updated, 0, subject)
                }
            }
            return RefEvent("pin:$id", league.oddsApiSportKey, starts, home = home, away = away, markets = markets)
        }
    }
}

/**
 * Pinnacle's player-prop names (its `special_units`, per sport) for the stats Novig lists, as read
 * live from PinnWire on 2026-09-27 (RESEARCH.md §22). Only names seen live are mapped: an unmapped
 * prop is skipped, never guessed.
 */
object PinnacleProps {
    private val FOOTBALL = mapOf(
        "Passing Yards" to "PASSING_YARDS",
        "Rushing Yards" to "RUSHING_YARDS",
        "Receiving Yards" to "RECEIVING_YARDS",
        "Receptions" to "RECEPTIONS",
        "Touchdown Passes" to "PASSING_TOUCHDOWNS",
        // Rushing + receiving touchdowns: Over 0.5 is "anytime touchdown", Novig's Over 0.5 TOUCHDOWNS.
        "Touchdowns" to "TOUCHDOWNS",
        "Pass Completions" to "PASSING_COMPLETIONS",
        "Pass Attempts" to "PASSING_ATTEMPTS",
        "Rush Attempts" to "RUSHING_ATTEMPTS",
        "Interceptions" to "INTERCEPTIONS_THROWN",
        "Field Goals" to "FIELD_GOALS_MADE",
    )
    private val BASEBALL = mapOf(
        "Home Runs" to "HOME_RUNS",
        "Bases" to "TOTAL_BASES",
        // Pinnacle lists strikeouts for starting pitchers only.
        "Strikeouts" to "PITCHER_STRIKEOUTS",
    )
    private val BASKETBALL = mapOf(
        "Points" to "POINTS",
        "Rebounds" to "REBOUNDS",
        "Assists" to "ASSISTS",
        "Threes Made" to "THREE_POINTERS_MADE",
    )

    /** Pinnacle `sport_id` -> its prop names. */
    private val BY_SPORT = mapOf(5 to FOOTBALL, 6 to BASEBALL, 3 to BASKETBALL)

    /** Whether any of [sportId]'s Pinnacle props price a Novig stat (else its specials aren't asked for). */
    fun hasProps(sportId: Int): Boolean = sportId in BY_SPORT

    /** Every Novig stat Pinnacle's props can price. */
    val STATS: Set<String> = BY_SPORT.values.flatMap { it.values }.toSet()

    fun stat(sportId: Int?, units: String): String? = BY_SPORT[sportId]?.get(units)

    /** "DJ Moore Total Receptions" (units "Receptions") -> "DJ Moore"; "Bo Bichette Total Bases" -> "Bo Bichette". */
    fun player(special: String, units: String): String? {
        val s = special.trim()
        val base = if (s.endsWith(units)) s.removeSuffix(units).trim() else s.substringBeforeLast(" Total ", "").trim()
        val name = base.removeSuffix(" Total").trim()
        return name.takeIf { it.isNotEmpty() && it != s && !it.endsWith(" Total") }
    }
}
