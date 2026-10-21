package com.tjshea.vigilant.data.sharpapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * SharpAPI's WebSocket stream (Tj, 2026-10-21, SS: "I added websocket streaming and game state support"; SHARPAPI.md §5): `wss://ws.sharpapi.io`, channels `odds` and `gamestate`, filtered to the leagues the
 * store holds. The per-book `initial` snapshot and every `odds:update` / `odds:locked` go into [SharpApiFeed.streamRows] (merged by row id), `odds:removed` into [SharpApiFeed.streamRemove], the game state into
 * [SharpApiScores]. While it is live the store's REST delta is only a minute's reconcile.
 *
 * Rules from the docs: ONE stream per key and a newer connection displaces the older (close 4001 "displaced"): only the engine opens it (the phone never does), and a displaced engine waits [DISPLACED_WAIT_MS]
 * before trying again rather than kicking whoever took the slot; a 4001 that is not a displacement is a refused key (no retry until the key changes); 4003 means entitlements changed (reconnect); anything else
 * reconnects with back-off (1 s doubling to 30 s), passing the last `global_seq` as `from_seq` so the server can replay what was missed (best effort; it re-snapshots when it cannot). The key goes in the
 * `Sec-WebSocket-Protocol` header (`sharpapi.key.<key>`), never in the URL; a `ping` every [PING_MS]; a connection silent for [SILENT_MS] (the heartbeat is every 30 s) is dropped and reopened.
 */
class SharpApiStream(
    private val http: OkHttpClient,
    private val key: () -> String,
    private val feed: SharpApiFeed,
    private val scores: SharpApiScores,
    private val scope: CoroutineScope,
    private val url: String = URL,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var ws: WebSocket? = null
    @Volatile private var wanted = false
    @Volatile private var leagues: Set<String> = emptySet()
    @Volatile private var gamestate = true
    @Volatile private var lastSeq: String? = null
    @Volatile private var refusedKey: String? = null
    @Volatile private var retryAtMs = 0L
    @Volatile private var backoffMs = 1_000L
    private var keeper: Job? = null

    @Volatile var live = false
        private set
    @Volatile var lastFrameMs = 0L
        private set
    @Volatile var connects = 0L
        private set
    @Volatile var frames = 0L
        private set
    @Volatile var lastClose: String? = null
        private set
    @Volatile var snapshotRows = 0L
        private set
    @Volatile var gamestateRefused = false
        private set

    fun health(now: Long = clock()): String = when {
        !wanted -> "off"
        refusedKey != null && refusedKey == key() -> "the key was refused (${lastClose ?: "4001"})"
        live -> "live (last frame ${(now - lastFrameMs) / 1000}s ago, ${frames} frames, ${connects} connections)"
        now < retryAtMs -> "reconnecting in ${(retryAtMs - now) / 1000}s (${lastClose ?: "closed"})"
        else -> "connecting" + (lastClose?.let { " (last close: $it)" } ?: "")
    }

    /**
     * What the stream should be: on or off, the leagues it carries, and whether to ask for game state. Called on every scan's source list (cheap); the keeper loop does the connecting. A change of leagues while
     * connected is sent as a `filter` (no reconnect; a new league's first rows come from its REST read).
     */
    @Synchronized
    fun want(on: Boolean, leagues: Set<String>, gamestate: Boolean = true) {
        wanted = on && key().isNotBlank()
        this.gamestate = gamestate
        if (!wanted) { close("switched off"); return }
        if (leagues != this.leagues) {
            this.leagues = leagues
            ws?.let { sendFilter(it, leagues) }
            feed.streamLeagues = if (live) leagues else emptySet()
        }
        if (keeper?.isActive != true) keeper = scope.launch { keep() }
    }

    @Synchronized
    fun close(why: String = "closed") {
        wanted = false
        keeper?.cancel(); keeper = null
        ws?.close(1000, why); ws = null
        live = false
        feed.streamLeagues = emptySet()
        feed.streamHeardAtMs = 0L
    }

    private suspend fun keep() {
        var sincePing = 0L
        while (scope.isActive && wanted) {
            val now = clock()
            val k = key()
            if (ws == null && now >= retryAtMs && (refusedKey == null || refusedKey != k) && leagues.isNotEmpty()) open(k)
            if (ws != null && live && now - lastFrameMs > SILENT_MS) {
                lastClose = "silent for ${(now - lastFrameMs) / 1000}s"
                drop(); retryAtMs = now + backoffMs
            }
            sincePing += TICK_MS
            if (sincePing >= PING_MS) { sincePing = 0L; ws?.send("""{"type":"ping"}""") }
            delay(TICK_MS)
        }
    }

    @Synchronized
    private fun open(k: String) {
        val channels = if (gamestate && !gamestateRefused) "odds,gamestate" else "odds"
        val q = buildString {
            append(url).append("?channels=").append(channels)
            if (leagues.isNotEmpty()) append("&league=").append(leagues.sorted().joinToString(","))
            lastSeq?.let { append("&from_seq=").append(it) }
        }
        val req = Request.Builder().url(q).header("Sec-WebSocket-Protocol", "sharpapi-v1, sharpapi.key.$k").build()
        connects++
        ws = http.newWebSocket(req, Listener(k))
    }

    @Synchronized
    private fun drop() {
        ws?.cancel(); ws = null
        live = false
        feed.streamLeagues = emptySet()
    }

    private fun sendFilter(socket: WebSocket, leagues: Set<String>) {
        val msg = buildJsonObject {
            put("type", "filter")
            put("filters", buildJsonObject { put("leagues", buildJsonArray { leagues.sorted().forEach { add(JsonPrimitive(it)) } }) })
        }
        socket.send(msg.toString())
    }

    private inner class Listener(private val k: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            lastFrameMs = clock()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== ws) return
            val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            handle(o)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closed(webSocket, code, reason)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val code = response?.code
            if (code == 401) { refusedKey = k; closed(webSocket, 4001, "HTTP 401 at upgrade") } else closed(webSocket, 1006, t.message ?: t.javaClass.simpleName)
        }
    }

    /** One frame from the server (internal for the tests). */
    internal fun handle(o: JsonObject) {
        val now = clock()
        frames++
        lastFrameMs = now
        feed.streamHeardAtMs = now
        (o["global_seq"] as? JsonPrimitive)?.content?.let { lastSeq = it }
        when ((o["type"] as? JsonPrimitive)?.content) {
            "connected" -> {
                live = true
                backoffMs = 1_000L
                feed.streamLeagues = leagues
                val gs = (o["features"] as? JsonObject)?.get("game_state")
                if (gs is JsonPrimitive && gs.content == "false") gamestateRefused = true
                log("SharpAPI stream: connected (${(o["stream_id"] as? JsonPrimitive)?.content}, resumed ${(o["resumed"] as? JsonPrimitive)?.content ?: "no"})")
            }
            "initial", "odds:update", "odds:locked" -> (o["data"] as? JsonArray)?.let { d ->
                feed.streamRows(d)
                if ((o["type"] as? JsonPrimitive)?.content == "initial") snapshotRows += d.size
            }
            "odds:removed" -> (o["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.let(feed::streamRemove)
            "gamestate:snapshot" -> scores.apply(SharpApiScores.rowsOf(o["data"]), replace = true)
            "gamestate:update", "gamestate:final" -> scores.apply(SharpApiScores.rowsOf(o["data"]))
            "gamestate:removed" -> {
                val d = o["data"]
                val ids = (d as? JsonArray ?: (d as? JsonObject)?.get("event_ids") as? JsonArray ?: o["event_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                scores.remove(ids)
            }
            "error" -> {
                val code = (o["code"] as? JsonPrimitive)?.content
                if (code != null && "gamestate" in text(o)) gamestateRefused = true
                log("SharpAPI stream error: ${code ?: "?"} ${(o["message"] as? JsonPrimitive)?.content?.take(120).orEmpty()}")
            }
        }
    }

    private fun text(o: JsonObject) = o.toString()

    @Synchronized
    private fun closed(webSocket: WebSocket, code: Int, reason: String) {
        if (webSocket !== ws) return
        ws = null
        live = false
        feed.streamLeagues = emptySet()
        lastClose = "$code ${reason.take(80)}".trim()
        val now = clock()
        when {
            code == 1000 && !wanted -> {}
            code == 4001 && reason.contains("displaced", ignoreCase = true) -> { retryAtMs = now + DISPLACED_WAIT_MS; log("SharpAPI stream: displaced by a newer connection on the same key; waiting ${DISPLACED_WAIT_MS / 60_000} min") }
            code == 4001 -> { refusedKey = key(); log("SharpAPI stream: the key was refused ($reason)") }
            else -> { retryAtMs = now + backoffMs; backoffMs = minOf(backoffMs * 2, 30_000L) }
        }
    }

    companion object {
        const val URL = "wss://ws.sharpapi.io"
        const val PING_MS = 25_000L
        const val TICK_MS = 1_000L
        const val SILENT_MS = 90_000L
        const val DISPLACED_WAIT_MS = 5 * 60_000L
    }
}
