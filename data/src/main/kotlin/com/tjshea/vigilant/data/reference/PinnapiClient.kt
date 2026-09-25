package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
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
 * re-requested, and a 429 parks the client until pinnapi's `retry_after_ms` instead of retrying.
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
    private val key: String,
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
    private var blockedUntil = 0L
    private var blockedReason: String? = null

    /** Requests actually sent this session (the free key allows 100 a day). */
    @Volatile
    var requestsSent = 0
        private set

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val sport = league.pinnacleSportId ?: return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val board = mutex.withLock { boardFor(sport) }
        return RefSnapshot(league.oddsApiSportKey, parse(board.events, league), board.fetchedAtMs, provider = ID)
    }

    private suspend fun boardFor(sport: Int): Board {
        val now = clock()
        boards[sport]?.takeIf { now - it.fetchedAtMs < shareMs }?.let { return it }
        if (now < blockedUntil) {
            throw ReferenceException(blockedReason ?: "Pinnacle (pinnapi) asked to wait ${((blockedUntil - now) / 60_000).coerceAtLeast(1)} min")
        }
        val url = "$baseUrl/markets".toHttpUrl().newBuilder()
            .addQueryParameter("sport_id", sport.toString())
            .addQueryParameter("event_type", "prematch")
            .build()
        requestsSent++
        http.newCall(Request.Builder().url(url).header("x-portal-apikey", key).get().build()).await().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.code == 429 -> {
                    val waitMs = retryAfterMs(body) ?: response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000) ?: 3_600_000L
                    blockedUntil = clock() + waitMs
                    blockedReason = "Pinnacle (pinnapi) limit reached: the free key allows 100 requests a day. Try again in ${formatWait(waitMs)}."
                    throw ReferenceException(blockedReason!!)
                }
                response.code == 401 || response.code == 403 ->
                    throw ReferenceException("Pinnacle (pinnapi) rejected the key (HTTP ${response.code}). Check it in Settings.")
                !response.isSuccessful -> throw ReferenceException("Pinnacle (pinnapi) HTTP ${response.code}")
            }
            val events = (json.parseToJsonElement(body).jsonObject["events"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            return Board(events, clock()).also { boards[sport] = it }
        }
    }

    private fun retryAfterMs(body: String): Long? =
        runCatching { (json.parseToJsonElement(body).jsonObject["retry_after_ms"] as? JsonPrimitive)?.longOrNull }.getOrNull()

    private fun formatWait(ms: Long): String = when {
        ms >= 3_600_000 -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
        else -> "${(ms / 60_000).coerceAtLeast(1)} min"
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
            "CFL" -> setOf("cfl")
            "NBA" -> setOf("nba")
            "NCAAB" -> setOf("ncaa", "ncaab", "ncaa basketball")
            "WNBA" -> setOf("wnba")
            "MLB" -> setOf("mlb")
            "KBO" -> setOf("korea - kbo league", "kbo")
            "NPB" -> setOf("japan - npb", "npb")
            "NHL" -> setOf("nhl")
            "UFC" -> setOf("ufc")
            "Boxing" -> setOf("boxing", "boxing matches")
            "EPL" -> setOf("england - premier league")
            "MLS" -> setOf("usa - major league soccer")
            "La Liga" -> setOf("spain - la liga")
            "Bundesliga" -> setOf("germany - bundesliga")
            "Serie A" -> setOf("italy - serie a")
            "Ligue 1" -> setOf("france - ligue 1")
            "Champions League" -> setOf("uefa - champions league")
            "Europa League" -> setOf("uefa - europa league")
            else -> emptySet()
        }

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
        private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

        fun parse(events: List<JsonObject>, league: League): List<RefEvent> {
            val names = leagueNames(league)
            val named = events.filter { e -> e.str("league_name")?.trim()?.lowercase() in names }
            val pool = named.ifEmpty { events }
            return pool.mapNotNull { event(it, league) }
        }

        private fun event(e: JsonObject, league: League): RefEvent? {
            val home = e.str("home")?.takeIf { it.isNotBlank() } ?: return null
            val away = e.str("away")?.takeIf { it.isNotBlank() } ?: return null
            val starts = e.str("starts")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null
            val id = (e["event_id"] as? JsonPrimitive)?.content ?: return null
            val full = e["periods"].obj()?.get("num_0").obj() ?: return null
            val markets = ArrayList<RefBookMarket>()

            full["money_line"].obj()?.let { ml ->
                val h = ml.num("home")
                val a = ml.num("away")
                val d = ml.num("draw")
                if (h != null && a != null && h > 1.0 && a > 1.0) {
                    val quotes = buildList {
                        add(RefQuote(Side.HOME, h, null))
                        add(RefQuote(Side.AWAY, a, null))
                        if (d != null && d > 1.0) add(RefQuote(Side.DRAW, d, null))
                    }
                    markets += RefBookMarket(ID, "Pinnacle", LineKind.MONEYLINE, quotes, null)
                }
            }
            full["spreads"].obj()?.values?.forEach { v ->
                val sp = v.obj() ?: return@forEach
                val hdp = sp.num("hdp") ?: return@forEach
                val h = sp.num("home") ?: return@forEach
                val a = sp.num("away") ?: return@forEach
                if (h > 1.0 && a > 1.0) {
                    markets += RefBookMarket(ID, "Pinnacle", LineKind.SPREAD, listOf(RefQuote(Side.HOME, h, hdp), RefQuote(Side.AWAY, a, -hdp)), null)
                }
            }
            full["totals"].obj()?.values?.forEach { v ->
                val t = v.obj() ?: return@forEach
                val pts = t.num("points") ?: return@forEach
                val o = t.num("over") ?: return@forEach
                val u = t.num("under") ?: return@forEach
                if (o > 1.0 && u > 1.0) {
                    markets += RefBookMarket(ID, "Pinnacle", LineKind.TOTAL, listOf(RefQuote(Side.OVER, o, pts), RefQuote(Side.UNDER, u, pts)), null)
                }
            }
            return RefEvent("pin:$id", league.oddsApiSportKey, starts, home = home, away = away, markets = markets)
        }
    }
}
