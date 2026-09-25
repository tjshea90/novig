package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.scanner.League
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
 * Pinnacle's prices through pinnapi.com (RESEARCH.md §11), an independent feed (not Pinnacle
 * itself, which closed its public API on 2025-07-23). The free trial key never expires but allows
 * **100 requests a day** (20/min, 100/hour). One request returns a whole sport's prematch board,
 * so NFL and NCAAF share one call; a sport fetched in the last [shareMs] is re-used rather than
 * re-requested. [KeyPool] counts every request per key (pinnapi sends no usage headers) and skips a
 * key before its daily/minute allowance runs out; a 429 rests the key for pinnapi's own
 * `retry_after_ms`.
 *
 * Response shape (their docs): `{events:[{event_id, league_name, starts, home, away,
 * periods:{num_0:{money_line:{home,away,draw?}, spreads:{"<hdp>":{hdp,home,away}},
 * totals:{"<pts>":{points,over,under}}}}}]}`, decimal odds, `hdp` is the home handicap.
 * **Not yet verified live:** no key existed on 2026-09-25. League names are matched loosely
 * for that reason (see [leagueNames]).
 */
class PinnapiClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val pool: KeyPool,
    private val baseUrl: String = "https://pinnapi.com/kit/v1",
    private val clock: () -> Long = System::currentTimeMillis,
    private val shareMs: Long = 60_000,
) : ReferenceSource {

    override val id = ID
    override val displayName = "Pinnacle"
    override val metered = true

    override fun supports(league: League) = league.pinnacleSportId != null

    private data class Board(val events: List<JsonObject>, val fetchedAtMs: Long)

    private val mutex = Mutex()
    private val boards = HashMap<Int, Board>()

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val sport = league.pinnacleSportId ?: return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val board = mutex.withLock { boardFor(sport) }
        return RefSnapshot(league.oddsApiSportKey, parse(board.events, league, board.fetchedAtMs), board.fetchedAtMs, provider = ID)
    }

    private suspend fun boardFor(sport: Int): Board {
        boards[sport]?.takeIf { clock() - it.fetchedAtMs < shareMs }?.let { return it }
        val url = "$baseUrl/markets".toHttpUrl().newBuilder()
            .addQueryParameter("sport_id", sport.toString())
            .addQueryParameter("event_type", "prematch")
            .build()
        val events = try {
            pool.execute(cost = 1) { key ->
                http.newCall(Request.Builder().url(url).header("x-portal-apikey", key).get().build()).await().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        // pinnapi says which window was hit and exactly how long to wait.
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
                        !response.isSuccessful -> throw ReferenceException("Pinnacle (pinnapi) HTTP ${response.code}")
                        else -> KeyAttemptResult.Success(
                            (json.parseToJsonElement(body).jsonObject["events"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty(),
                        )
                    }
                }
            }
        } catch (e: AllKeysExhaustedException) {
            throw ReferenceException(e.message ?: "Pinnacle (pinnapi) keys are used up")
        }
        return Board(events, clock()).also { boards[sport] = it }
    }

    companion object {
        const val ID = "pinnacle"

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
            val names = leagueNames(league)
            val named = events.filter { e -> e.str("league_name")?.trim()?.lowercase() in names }
            val pool = named.ifEmpty { events }
            return pool.mapNotNull { event(it, league, fetchedAtMs) }
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

            full["money_line"].obj()?.let { ml ->
                val h = ml.num("home")
                val a = ml.num("away")
                if (h != null && a != null && h > 1.0 && a > 1.0 && ml.num("draw") == null) {
                    markets += RefBookMarket(ID, "Pinnacle", LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, h, null), RefQuote(Side.AWAY, a, null)), updated)
                }
            }
            // Full game (num_0) and 1st half / first 5 innings (num_1), with every alternate line.
            for ((periodKey, period) in listOf("num_0" to 0, "num_1" to 1)) {
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
            // Team totals (full game): the main line per team, plus alternates when sent.
            for ((sideKey, subject) in listOf("home" to RefBookMarket.HOME, "away" to RefBookMarket.AWAY)) {
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
