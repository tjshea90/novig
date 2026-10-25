package com.tjshea.vigilant.data.parlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ParlayAPI's Business odds stream on the engine (PB11, Tj 2026-10-25: "I want the GitHub engine to run 24/7 connected to parlayapi business ... full usage of the sse and/or websocket";
 * research/PARLAYAPI_BUSINESS_2026-10-25.md §2): one `GET /v1/sse/odds/{sport}` per scanned sport (`kinds=game,prop`, the key in the X-API-Key header, never in a URL), every row into [live]; beside it
 * `GET /v1/sse/hot/{sport}` (PS1, 2026-10-25: the same rows from ParlayAPI's fast-cadence books, Pinnacle and FanDuel, sub-3 s), into the same store, where the newer confirmation of a side wins.
 * Streams are tier-gated, not credit-charged: holding them costs nothing. A refused tier (402, or 401/403) stops that key until it changes; any other end reconnects after 1, 2, 4 ... 30 s
 * (ParlayAPI's advice), back to 1 s after a connection that lasted a minute.
 */
class ParlayStream(
    private val http: OkHttpClient,
    private val json: Json,
    /** The key of the biggest plan (Business), or null. */
    private val key: () -> String?,
    val live: ParlayLive,
    private val scope: CoroutineScope,
    private val baseUrl: String = "https://parlay-api.com",
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    @Volatile private var refusedKey: String? = null
    /** The hot route refused this key (a plan or connection cap): the odds streams carry on alone. */
    @Volatile private var hotRefusedKey: String? = null
    @Volatile var connects = 0L
        private set
    private val lastEnd = ConcurrentHashMap<String, String>()

    /** The sports (The Odds API's keys, as ParlayAPI names them) to hold a stream for; any other is closed. Cheap: called with every scan's source list. */
    @Synchronized
    fun want(on: Boolean, sports: Set<String>) {
        if (!on || key().isNullOrBlank()) { close(); return }
        val hot = hotRefusedKey == null || hotRefusedKey != key()
        val routes = sports.map { ODDS to it } + (if (hot) sports.map { HOT to it } else emptyList())
        val names = routes.associateBy { (r, s) -> "$r|$s" }
        for (n in jobs.keys - names.keys) jobs.remove(n)?.cancel()
        for ((n, rs) in names) if (jobs[n]?.isActive != true) jobs[n] = scope.launch(Dispatchers.IO) { run(rs.first, rs.second) }
        if (health?.isActive != true && jobs.isNotEmpty()) health = scope.launch(Dispatchers.IO) { healthLoop() }
    }

    @Synchronized
    fun close() { jobs.values.forEach { it.cancel() }; jobs.clear(); health?.cancel(); health = null }

    fun health(now: Long = clock()): String = when {
        jobs.isEmpty() -> "off"
        refusedKey != null && refusedKey == key() -> "the key's plan has no streams (Business and up)"
        else -> jobs.keys.groupBy({ it.substringBefore('|') }, { it.substringAfter('|') }).entries.sortedBy { it.key }.joinToString(" + ") { (r, ss) -> "$r ${ss.sorted().joinToString(",")}" } +
            (if (hotRefusedKey != null && hotRefusedKey == key()) " (hot route refused)" else "") + ", $connects connections, " + (if (live.live(now)) "live" else "silent") +
            (lastEnd.entries.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · last end: ") { "${it.key} ${it.value}" } ?: "") + " · " + live.describe(now)
    }

    private suspend fun run(route: String, sport: String) {
        var wait = BACKOFF_MS
        val name = if (route == ODDS) sport else "$route:$sport"
        while (scope.isActive) {
            val k = key()
            if (k.isNullOrBlank() || k == refusedKey) { delay(60_000L); continue }
            if (route == HOT && k == hotRefusedKey) return
            val opened = clock()
            try {
                connect(route, sport, k)
                lastEnd[name] = "closed by ParlayAPI after ${(clock() - opened) / 1000}s"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Refused) {
                lastEnd[name] = "HTTP ${e.code}"
                if (route == HOT && (e.code == 401 || e.code == 402 || e.code == 403 || e.code == 429)) { hotRefusedKey = k; log("ParlayAPI hot stream $sport refused (HTTP ${e.code}): the odds streams carry on alone"); return }
                if (e.code == 401 || e.code == 402 || e.code == 403) { refusedKey = k; log("ParlayAPI stream $sport refused (HTTP ${e.code}): the key's plan has no streams; waiting for another key"); continue }
            } catch (e: Exception) {
                lastEnd[name] = "${e.javaClass.simpleName}: ${e.message?.take(80)}"
            }
            wait = if (clock() - opened >= STABLE_MS) BACKOFF_MS else minOf(wait * 2, MAX_BACKOFF_MS)
            delay(wait)
        }
    }

    class Refused(val code: Int) : IOException("HTTP $code")

    private suspend fun connect(route: String, sport: String, apiKey: String) = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/sse/$route/$sport?kinds=game,prop&heartbeat_s=5&max_age_s=600&limit=1000"
        val req = Request.Builder().url(url).header("X-API-Key", apiKey).header("Accept", "text/event-stream").build()
        // A stream is one call that never ends: no whole-call deadline (the shared client's cut every stream after 30 s, 35 reconnects in 3 minutes on the first runner), only the silent-read one.
        val call = http.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).readTimeout(SILENT_READ_MS, TimeUnit.MILLISECONDS).build().newCall(req)
        val cancel = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw Refused(resp.code)
                connects++
                // Only the odds stream carries every book: "connected since" (what confirms an unchanged price) is its alone.
                if (route == ODDS) live.connected(sport) else live.heard()
                val src = (resp.body ?: throw IOException("empty body")).source()
                val data = StringBuilder()
                while (true) {
                    ensureActive()
                    val line = src.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> { if (data.isNotEmpty()) accept(sport, data.toString()); data.setLength(0) }
                        line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.removePrefix("data:").trimStart()) }
                        else -> {}
                    }
                }
            }
        } finally {
            if (route == ODDS) live.disconnected(sport)
            cancel?.dispose()
        }
    }

    /** ParlayAPI's per-book poll times (`/v1/meta/source-health`, no credits), every [HEALTH_EVERY_MS] while a stream is wanted: what confirms an unchanged price ([ParlayLive.onHealth]). */
    private suspend fun healthLoop() {
        while (scope.isActive && jobs.isNotEmpty()) {
            val k = key()
            if (!k.isNullOrBlank()) runCatching {
                withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url("$baseUrl/v1/meta/source-health").header("X-API-Key", k).build()).execute().use { r ->
                        if (r.isSuccessful) (json.parseToJsonElement(r.body!!.string()) as? JsonObject)?.let { live.onHealth(it) }
                    }
                }
            }
            delay(HEALTH_EVERY_MS)
        }
    }
    private var health: Job? = null

    /** One complete SSE message: rows of `initial_state` / `odds_update` into the store; heartbeats and status frames keep it marked alive. */
    internal fun accept(sport: String, data: String) {
        val now = clock()
        val o = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: return
        live.heard(now)
        val type = (o["type"] as? JsonPrimitive)?.content
        if (type != "initial_state" && type != "odds_update") return
        for (r in (o["data"] as? JsonArray).orEmpty()) (r as? JsonObject)?.let { runCatching { live.onRow(sport, it, now) } }
    }

    companion object {
        /** The routes under `/v1/sse/`: every book's rows, and the fast-cadence books' (Pinnacle, FanDuel). */
        const val ODDS = "odds"
        const val HOT = "hot"
        const val BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        /** A connection that lasted this long was a good one: the next wait starts again at [BACKOFF_MS]. */
        const val STABLE_MS = 60_000L
        /** Heartbeats come every 5 s: a read waiting this long means the connection is dead. */
        const val SILENT_READ_MS = 45_000L
        /** ParlayAPI's book poll times are read this often (a free `/meta` route). */
        const val HEALTH_EVERY_MS = 30_000L
    }
}
