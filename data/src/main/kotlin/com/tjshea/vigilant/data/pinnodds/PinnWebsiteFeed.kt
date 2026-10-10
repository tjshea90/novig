package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.JsonSplit
import com.tjshea.vigilant.data.scanner.PinnWebsiteSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

/** What a request to Pinnacle's website came back with. [code] 0 = no answer at all (the message says why). */
class WebsiteReply(val code: Int, val body: String, val message: String = "")

/** How the feed asks Pinnacle's website for a path under its base URL. The app's is [OkHttpWebsiteFetcher]; tests fake it. */
fun interface WebsiteFetcher {
    suspend fun get(path: String): WebsiteReply
}

class OkHttpWebsiteFetcher(private val http: OkHttpClient, private val key: () -> String, private val base: String = PinnWebsiteFeed.BASE) : WebsiteFetcher {
    override suspend fun get(path: String): WebsiteReply = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(base + path).header("x-api-key", key()).header("Accept", "application/json").header("Referer", "https://www.pinnacle.com/").header("User-Agent", "Mozilla/5.0 (Linux; Android 16)").build()
            http.newCall(req).execute().use { r -> WebsiteReply(r.code, if (r.isSuccessful) r.body?.string().orEmpty() else "") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WebsiteReply(0, "", e.message ?: e.javaClass.simpleName)
        }
    }
}

/** Counters for Settings and Diagnostics. */
data class WebsiteStats(
    val sinceMs: Long = 0L,
    val polls: Long = 0L,
    val failures: Long = 0L,
    val rateLimited: Long = 0L,
    val games: Int = 0,
    val frames: Long = 0L,
    val changes: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val avgLatencyMs: Long = 0L,
    val lastCycleMs: Long = 0L,
    val lastError: String? = null,
    val pollMs: Long = 0L,
)

/**
 * Pinnacle's own website feed as a [PinnFeedSource] (Tj, 2026-10-10: "build the Pinnacle website feed behind a switch"; RESEARCH.md §126-§127): the free replacement for the Pinnodds socket. Pinnacle's site is a
 * REST API (`guest.api.arcadia.pinnacle.com/0.1`, the key its own pages send every visitor) whose records are the very ones the socket pushes, so this POLLS it and turns each answer into the `live` frames
 * [PinnBook] already reads, and the rest of the engine (matcher, judge, trader, bid desk) cannot tell the two feeds apart. How it works:
 *  - **discovery** every [DISCOVER_MS]: each sport's matchup list (football, basketball, hockey, baseball; cut into elements by [JsonSplit] so a megabyte list never becomes a tree), keeping the live regular
 *    matchups, up to `maxGames`;
 *  - **polling** every `pollMs` for each live game: its markets (every cycle) and the game with its parent (the score and the clock; every second cycle). Only markets whose `version` rose, or whose status
 *    changed, are sent on, so the engine sees what the socket's `upd` frames would;
 *  - `liveMode` `danger_zone` becomes a `dz` frame (a volatility signal, no price), `both` a `both` frame, `live_delay` an `ld` frame; the period list is dropped (on the website it reads `closed` for a game in
 *    play whose markets are open: each market's own status closes it);
 *  - a game that stops being live is sent as a `del`.
 * It is gentle by design: at most [CONCURRENCY] requests at once, a 429 doubles the pause for a while, failures back off. [shadow] (the compare mode) applies the frames to a book of its own instead of sending
 * them on, so the app can time this feed against the socket without it driving anything.
 */
class PinnWebsiteFeed(
    private val fetcher: WebsiteFetcher,
    private val scope: CoroutineScope,
    private val onFrame: (text: String, atMs: Long) -> Unit,
    private val config: () -> PinnWebsiteSettings,
    private val shadow: PinnBook? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : PinnFeedSource {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<PinnSocketState>(PinnSocketState.Off)
    override val state: StateFlow<PinnSocketState> = _state.asStateFlow()
    private val _stats = MutableStateFlow(WebsiteStats())
    val stats: StateFlow<WebsiteStats> = _stats.asStateFlow()

    @Volatile override var lastFrameAtMs: Long = 0L
        private set

    @Volatile private var job: Job? = null
    val running: Boolean get() = job?.isActive == true

    /** A live game being followed: its sport, the last full record, the market versions already sent, and how many polls it has had. */
    internal class Game(val id: Long, val sport: Sport) {
        var rec: JsonObject? = null
        val sent = HashMap<String, Pair<Long, String>>()
        var polls = 0
        var lastMode: String = "live_delay"
    }

    enum class Sport(val arcadia: Int, val pinnodds: Int) { FOOTBALL(15, 5), BASKETBALL(4, 3), HOCKEY(19, 4), BASEBALL(3, 6) }

    internal val games = HashMap<Long, Game>()
    private var latencySum = 0L
    private var latencyN = 0L
    private var polls = 0L
    private var failures = 0L
    private var rateLimited = 0L
    private var frames = 0L
    private var changes = 0L
    private var lastError: String? = null
    private var slowUntilMs = 0L
    private var since = 0L

    @Synchronized
    override fun start() {
        if (running) return
        since = clock()
        _state.value = PinnSocketState.Connecting
        job = scope.launch { loop() }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        _state.value = PinnSocketState.Off
    }

    private suspend fun loop() {
        var fails = 0
        var lastDiscover = 0L
        while (currentCoroutineContext().isActive) {
            val t0 = clock()
            val cfg = config()
            try {
                if (t0 - lastDiscover >= DISCOVER_MS || games.isEmpty() && t0 - lastDiscover >= 3_000L) {
                    lastDiscover = t0
                    discover(cfg.maxGames.coerceIn(1, 80))
                }
                val ok = pollOnce(t0)
                if (ok) {
                    fails = 0
                    if (_state.value !is PinnSocketState.Live) _state.value = PinnSocketState.Live(clock())
                } else if (++fails >= 3) {
                    _state.value = PinnSocketState.Down(lastError ?: "Pinnacle's website did not answer", clock(), clock() + backoff(fails))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                if (++fails >= 3) _state.value = PinnSocketState.Down(lastError!!, clock(), clock() + backoff(fails))
            }
            publish(clock() - t0)
            val gap = if (fails >= 3) backoff(fails) else maxOf(cfg.pollMs.coerceIn(500, 30_000).toLong() * (if (clock() < slowUntilMs) 2 else 1) - (clock() - t0), MIN_GAP_MS)
            pause(gap)
        }
    }

    private fun backoff(fails: Int): Long = minOf(30_000L, 1_000L shl (fails - 3).coerceIn(0, 5))

    // ---- discovery ------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Reads each sport's matchup list and follows the live regular matchups (at most [max]); games no longer live are dropped with a `del`. */
    internal suspend fun discover(max: Int) {
        val live = LinkedHashMap<Long, Sport>()
        for (sport in Sport.entries) {
            val r = fetcher.get("/sports/${sport.arcadia}/matchups?withSpecials=false")
            note(r)
            if (r.code != 200) continue
            val els = JsonSplit.elements(r.body) ?: continue
            for (i in 0 until els.size) {
                val text = els.element(i)
                if (!LIVE_TRUE.containsMatchIn(text)) continue
                val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
                if (str(o, "type") != "matchup" || str(o, "units") != "Regular" || (o["isLive"] as? JsonPrimitive)?.booleanOrNull != true) continue
                val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: continue
                if (live.size < max) live[id] = sport
            }
        }
        val gone = games.keys.filter { it !in live }
        for (id in gone) { send(delFrame(games.getValue(id)), clock()); games.remove(id) }
        for ((id, sport) in live) games.getOrPut(id) { Game(id, sport) }
    }

    // ---- one polling cycle ----------------------------------------------------------------------------------------------------------------------------------------------------

    /** Polls every followed game once; true when at least one answer came back (or there was nothing to poll). */
    internal suspend fun pollOnce(now: Long): Boolean {
        val list = games.values.toList()
        if (list.isEmpty()) return lastError == null || polls > 0
        val gate = Semaphore(CONCURRENCY)
        val oks = coroutineScope { list.map { g -> async { gate.withPermit { pollGame(g) } } }.awaitAll() }
        return oks.any { it }
    }

    private suspend fun pollGame(g: Game): Boolean {
        val needRec = g.rec == null || g.polls % 2 == 0
        g.polls++
        var recOut: JsonObject? = null
        if (needRec) {
            val r = fetcher.get("/matchups/${g.id}/related")
            note(r)
            if (r.code == 200) {
                val arr = runCatching { json.parseToJsonElement(r.body) as? JsonArray }.getOrNull()
                val me = arr?.firstOrNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.longOrNull == g.id } as? JsonObject
                if (me != null) {
                    if ((me["isLive"] as? JsonPrimitive)?.booleanOrNull == false) { send(delFrame(g), clock()); games.remove(g.id); return true }
                    recOut = JsonObject(me.filterKeys { it != "periods" })
                    g.rec = recOut
                    g.lastMode = str(me, "liveMode") ?: "live_delay"
                }
            }
        }
        val mode = g.lastMode
        // A danger-zone game: the signal, not the prices.
        if (mode == "danger_zone") {
            val base = recOut ?: g.rec ?: return true
            send(frame(g, "dz", JsonObject(base + ("markets" to JsonArray(emptyList())))), clock())
            return true
        }
        val r = fetcher.get("/matchups/${g.id}/markets/related/straight")
        note(r)
        if (r.code != 200) return recOut != null
        val arr = runCatching { json.parseToJsonElement(r.body) as? JsonArray }.getOrNull() ?: return recOut != null
        val changed = ArrayList<JsonObject>()
        for (el in arr) {
            val m = el as? JsonObject ?: continue
            if ((m["matchupId"] as? JsonPrimitive)?.longOrNull != g.id) continue
            val key = str(m, "key") ?: continue
            val version = (m["version"] as? JsonPrimitive)?.longOrNull ?: continue
            val status = str(m, "status") ?: continue
            val before = g.sent[key]
            if (before == null || version > before.first || status != before.second) {
                g.sent[key] = version to status
                changed += m
            }
        }
        changes += changed.size
        if (recOut == null && changed.isEmpty()) return true
        val rec = JsonObject((recOut ?: JsonObject(mapOf("id" to JsonPrimitive(g.id), "isLive" to JsonPrimitive(true)))) + ("markets" to JsonArray(changed)))
        send(frame(g, if (mode == "both") "both" else "ld", rec), clock())
        return true
    }

    // ---- frames -----------------------------------------------------------------------------------------------------------------------------------------------------------------

    private fun frame(g: Game, channel: String, rec: JsonObject): JsonObject = buildJsonObject {
        put("type", "live")
        put("sport_id", g.sport.pinnodds)
        put("topic", "matchups/reg/sp/${g.sport.arcadia}/live/$channel")
        put("op", "upd")
        put("ts", clock())
        put("rec", rec)
    }

    private fun delFrame(g: Game): JsonObject = frame(g, "ld", JsonObject(mapOf("id" to JsonPrimitive(g.id)))).let { f -> JsonObject(f + ("op" to JsonPrimitive("del"))) }

    private fun send(frame: JsonObject, atMs: Long) {
        frames++
        val book = shadow
        if (book != null) { runCatching { book.apply(frame, atMs) }; return }
        onFrame(frame.toString(), atMs)
    }

    // ---- bookkeeping ------------------------------------------------------------------------------------------------------------------------------------------------------------

    private fun note(r: WebsiteReply) {
        polls++
        when {
            r.code == 200 -> { lastFrameAtMs = clock() }
            r.code == 429 -> { rateLimited++; failures++; slowUntilMs = clock() + SLOW_MS; lastError = "Pinnacle's website said too many requests (429): polling slows for a while." }
            r.code == 401 || r.code == 403 -> { failures++; lastError = "Pinnacle's website refused the request (${r.code}): its public key may have changed (Settings › Pinnodds live › Pinnacle website › key)." }
            r.code == 0 -> { failures++; lastError = "No answer from Pinnacle's website (${r.message})." }
            else -> { failures++; lastError = "Pinnacle's website answered ${r.code}." }
        }
    }

    internal fun publishForTest() = publish(0L)

    private fun publish(cycleMs: Long) {
        latencySum += cycleMs
        latencyN++
        _stats.value = WebsiteStats(
            sinceMs = since, polls = polls, failures = failures, rateLimited = rateLimited, games = games.size, frames = frames, changes = changes, lastLatencyMs = cycleMs,
            avgLatencyMs = if (latencyN > 0) latencySum / latencyN else 0L, lastCycleMs = cycleMs, lastError = lastError, pollMs = config().pollMs.toLong(),
        )
    }

    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val BASE = "https://guest.api.arcadia.pinnacle.com/0.1"

        /**
         * The key Pinnacle's own site (pinnacle.com) sends with every request any visitor's browser makes: public, not an account's secret and not Vigilant's (measured 2026-10-10; RESEARCH.md §22.2, §126).
         * Settings can override it if Pinnacle ever changes it.
         */
        const val PUBLIC_SITE_KEY = "CmX2KcMrXuFmNg6YFbmTxE0y9CIrOi0R"

        const val DISCOVER_MS = 10_000L
        const val CONCURRENCY = 4
        const val MIN_GAP_MS = 200L
        const val SLOW_MS = 30_000L
        private val LIVE_TRUE = Regex("\"isLive\"\\s*:\\s*true")
    }
}
