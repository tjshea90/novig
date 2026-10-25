package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * PropLine's push stream on the engine (PSL, Tj 2026-10-25: "I want the GitHub engine to run 24/7 connected to propline streaming lite ... make full use of the added limits, webhook and/or websocket.
 * It allows multiple connections"). research/PROPLINE_STREAMING_2026-10-25.md has the protocol from the docs.
 *
 * Streaming Lite allows TWO WebSocket connections, one subscription each, and the engine uses both:
 * - [Sub.BOARD]: `line_movement`, `line_added`, `line_pulled`, `market_suspended` for the scanned sports and the fair-line books, into [PropLineLive] (laid over the REST boards).
 * - [Sub.SIGNALS]: `steam` (props too), `resolution` and `ev` at Novig, handed to [tap] (the engine journals them for research and grading).
 * A runner has no public address, so no webhook is used (webhooks need an HTTPS endpoint PropLine can POST to).
 *
 * The subscriptions are PropLine objects (`POST /v1/webhooks` with `transport: "websocket"`): found by their event set among the account's subscriptions and reused (filters PATCHed when they
 * change), created only when missing; a subscription Tj made himself (any other event set or transport) is never touched. Every connection authenticates with its first frame, resumes from the last
 * sequence it processed (`since_seq`; the server keeps 30 minutes), dedupes on `seq`, and reconnects with back-off. Close codes (docs): 4401 key refused (stop until the key changes), 4403 plan or paused
 * subscription (re-activate once, then wait 30 min), 4404 subscription gone (made again), 4429 connection limit (an old job's socket still open during a handover: wait), 4400 bad auth (made again),
 * 4008 we fell 2 minutes behind (reconnect with our sequence: nothing lost). Push deliveries are not metered; the subscription calls are a handful of requests.
 */
class PropLineStream(
    private val http: OkHttpClient,
    private val json: Json,
    /** The key with the Streaming plan (the account read finds it), or null. */
    private val key: () -> String?,
    private val live: PropLineLive,
    private val scope: CoroutineScope,
    private val base: String = "https://api.prop-line.com/v1",
    private val wsUrl: String = WS_URL,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    enum class Sub(val events: List<String>) {
        BOARD(listOf("line_movement", "line_added", "line_pulled", "market_suspended")),
        SIGNALS(listOf("steam", "resolution", "ev")),
    }

    /** Every `steam`, `resolution` and `ev` push: (event type, sport, raw payload, when heard). */
    @Volatile var tap: ((String, String, String, Long) -> Unit)? = null

    @Volatile private var wanted = false
    @Volatile private var sports: Set<String> = emptySet()
    @Volatile private var books: Set<String> = emptySet()
    @Volatile private var refusedKey: String? = null
    private var keeper: Job? = null
    private val conns = Sub.entries.associateWith { Conn(it) }

    /** Events received by type, for the status. */
    val counts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** PropLine's queueing delay and the network's, in ms (the last few hundred events): `sent_at - created_at` and receipt minus `sent_at`. */
    private val queueMs = ArrayDeque<Long>()
    private val wireMs = ArrayDeque<Long>()

    fun live(): Boolean = live.live()

    /**
     * What the stream should be: on or off, the sports (the app's keys) and the books (PropLine's keys) it covers. Cheap: called with every scan's source list; the keeper loop connects and PATCHes.
     */
    @Synchronized
    fun want(on: Boolean, sports: Set<String>, books: Set<String>) {
        wanted = on && !key().isNullOrBlank() && sports.isNotEmpty()
        if (!wanted) { close(if (!on) "switched off" else "no Streaming key or no sport"); return }
        this.sports = sports.map(PropLineLive::nativeSport).toSortedSet()
        this.books = books.toSortedSet()
        if (keeper?.isActive != true) keeper = scope.launch { keep() }
    }

    @Synchronized
    fun close(why: String = "closed") {
        wanted = false
        keeper?.cancel(); keeper = null
        conns.values.forEach { it.shut(why) }
        live.reset()
    }

    fun health(now: Long = clock()): String = when {
        !wanted -> "off"
        refusedKey != null && refusedKey == key() -> "the key was refused"
        else -> conns.values.joinToString(" · ") { it.health(now) } + delays()
    }

    private fun delays(): String = synchronized(queueMs) {
        if (queueMs.isEmpty()) "" else " · delay p50 PropLine ${median(queueMs)} ms, network ${median(wireMs)} ms"
    }

    private fun median(q: ArrayDeque<Long>): Long = q.sorted()[q.size / 2]

    private suspend fun keep() {
        while (scope.isActive && wanted) {
            val now = clock()
            val k = key()
            if (k.isNullOrBlank() || (refusedKey != null && refusedKey == k)) { delay(TICK_MS); continue }
            for (c in conns.values) {
                try { c.step(k, now) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { c.failed("${e.javaClass.simpleName}: ${e.message?.take(100)}", now) }
            }
            delay(TICK_MS)
        }
    }

    /** One subscription and its socket. */
    private inner class Conn(val sub: Sub) {
        @Volatile var id: Long? = null
        @Volatile var ws: WebSocket? = null
        @Volatile var lastSeq: Long? = null
        @Volatile var ready = false
        @Volatile var lastFrameMs = 0L
        @Volatile var retryAtMs = 0L
        @Volatile var backoffMs = 2_000L
        @Volatile var lastClose: String? = null
        @Volatile var connects = 0L
        @Volatile var filterKey = ""
        @Volatile var patchedAtMs = 0L
        @Volatile var needSeq = false
        @Volatile var reactivated = false

        fun health(now: Long): String = sub.name.lowercase() + ": " + when {
            ws != null && ready -> "live (last frame ${(now - lastFrameMs) / 1000}s ago, $connects connections${lastClose?.let { "; last close $it" }.orEmpty()})"
            now < retryAtMs -> "reconnecting in ${(retryAtMs - now) / 1000}s (${lastClose ?: "closed"})"
            else -> "connecting" + (lastClose?.let { " (last close: $it)" } ?: "")
        }

        suspend fun step(k: String, now: Long) {
            val fk = filters().toString()
            if (id == null && now >= retryAtMs) {
                id = ensure(k)
                filterKey = fk
                patchedAtMs = now
            } else if (id != null && fk != filterKey && now - patchedAtMs >= PATCH_GAP_MS) {
                patchedAtMs = now
                if (patch(k, id!!, body(includeTransport = false))) filterKey = fk
            }
            val sid = id ?: return
            if (ws == null && now >= retryAtMs) open(k, sid)
            if (ws != null && ready && now - lastFrameMs > PropLineLive.SILENT_MS) {
                lastClose = "silent for ${(now - lastFrameMs) / 1000}s"
                drop(); retryAtMs = now + backoffMs
            }
        }

        fun failed(why: String, now: Long) {
            lastClose = why
            retryAtMs = now + backoffMs
            backoffMs = minOf(backoffMs * 2, 60_000L)
        }

        fun filters(): JsonObject = buildJsonObject {
            put("filter_sport_key", sports.joinToString(","))
            when (sub) {
                Sub.BOARD -> if (books.isNotEmpty()) put("filter_bookmaker_key", books.joinToString(","))
                // Novig's own grades and +EV; steam is a cross-book signal the book filter does not touch.
                Sub.SIGNALS -> {
                    put("filter_bookmaker_key", PropLineClient.NOVIG)
                    put("steam_props", true)
                    put("min_steam_score", MIN_STEAM)
                    put("min_ev_pct", 1)
                    put("max_ev_pct", MAX_EV)
                }
            }
        }

        fun body(includeTransport: Boolean): JsonObject = buildJsonObject {
            if (includeTransport) put("transport", "websocket")
            put("events", buildJsonArray { sub.events.forEach { add(JsonPrimitive(it)) } })
            for ((k, v) in filters()) put(k, v)
        }

        /** Our subscription for [sub]: an existing one with exactly these events on the websocket transport, or a new one. */
        private suspend fun ensure(k: String): Long? {
            val listed = list(k)
            val mine = listed?.firstOrNull { o ->
                val t = (o["transport"] as? JsonPrimitive)?.content
                val ev = (o["events"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet()
                t == "websocket" && ev == sub.events.toSet()
            }
            if (mine != null) {
                val sid = (mine["id"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
                val active = (mine["active"] as? JsonPrimitive)?.content != "false"
                if (!active || filters().any { (f, v) -> mine[f]?.toString()?.trim('"') != v.toString().trim('"') }) patch(k, sid, body(includeTransport = false).let { b -> if (!active) JsonObject(b + ("active" to JsonPrimitive(true))) else b })
                log("PropLine stream ${sub.name.lowercase()}: reusing subscription $sid")
                return sid
            }
            val r = send(k, Request.Builder().url("$base/webhooks").post(body(includeTransport = true).toString().toRequestBody(JSON_TYPE)))
            val sid = r?.let { (it["id"] as? JsonPrimitive)?.content?.toLongOrNull() }
            if (sid != null) log("PropLine stream ${sub.name.lowercase()}: subscription $sid made (${sub.events.joinToString(",")})") else failed("could not make the subscription", clock())
            return sid
        }

        private suspend fun list(k: String): List<JsonObject>? {
            val r: JsonElement = sendRaw(k, Request.Builder().url("$base/webhooks").get()) ?: return null
            val arr = r as? JsonArray ?: ((r as? JsonObject)?.let { it["webhooks"] ?: it["data"] ?: it["subscriptions"] } as? JsonArray) ?: return emptyList()
            return arr.mapNotNull { it as? JsonObject }
        }

        private suspend fun patch(k: String, sid: Long, b: JsonObject): Boolean =
            send(k, Request.Builder().url("$base/webhooks/$sid").patch(b.toString().toRequestBody(JSON_TYPE))) != null

        @Synchronized
        fun open(k: String, sid: Long) {
            connects++
            ready = false
            ws = http.newWebSocket(Request.Builder().url(wsUrl).build(), Listener(k, sid))
        }

        @Synchronized
        fun drop() {
            ws?.cancel(); ws = null
            ready = false
            if (sub == Sub.BOARD) live.onClosed()
        }

        @Synchronized
        fun shut(why: String) {
            ws?.close(1000, why); ws = null
            ready = false
        }

        inner class Listener(private val k: String, private val sid: Long) : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lastFrameMs = clock()
                val auth = buildJsonObject {
                    put("type", "auth"); put("api_key", k); put("webhook_id", sid)
                    (lastSeq ?: if (needSeq) 0L else null)?.let { put("since_seq", it) }
                }
                webSocket.send(auth.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return
                val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                handle(o)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                closed(webSocket, code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closed(webSocket, code, reason)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                closed(webSocket, if (response?.code == 401) 4401 else 1006, t.message ?: t.javaClass.simpleName)
        }

        /** One frame (internal through [handleFrame] for the tests). */
        fun handle(o: JsonObject) {
            val now = clock()
            lastFrameMs = now
            if (sub == Sub.BOARD) live.heard(now)
            when ((o["type"] as? JsonPrimitive)?.content) {
                "ready" -> {
                    val truncated = (o["truncated"] as? JsonPrimitive)?.content == "true"
                    val resumed = lastSeq != null
                    ready = true
                    backoffMs = 2_000L
                    if (sub == Sub.BOARD) { if (truncated) live.reset(); live.onReady(continuous = resumed && !truncated, now) }
                    if (lastSeq == null) (o["latest_seq"] as? JsonPrimitive)?.content?.toLongOrNull()?.let { lastSeq = it }
                    log("PropLine stream ${sub.name.lowercase()}: ready (subscription $id, " + (if (resumed) "resumed" else "new") + (if (truncated) ", events were lost: REST re-reads" else "") + ")")
                }
                "event" -> {
                    val seq = (o["seq"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (seq != null) { val last = lastSeq; if (last != null && seq <= last) return; lastSeq = seq }
                    val type = (o["event_type"] as? JsonPrimitive)?.content ?: return
                    counts.merge(type, 1L, Long::plus)
                    timing(o, now)
                    val d = o["data"] as? JsonObject ?: return
                    when (type) {
                        "line_movement", "line_added", "line_pulled" -> live.onLine(type, d, now)
                        "market_suspended" -> live.onSuspended(d, now)
                        "steam", "resolution", "ev" -> runCatching { tap?.invoke(type, (d["sport_key"] as? JsonPrimitive)?.content.orEmpty(), d.toString().take(6_000), now) }
                    }
                }
                "heartbeat" -> if (sub == Sub.BOARD) live.onHeartbeat(o["books"] as? JsonArray, now)
                "ping" -> {}
                "error" -> log("PropLine stream ${sub.name.lowercase()} error: ${o.toString().take(160)}")
            }
        }

        @Synchronized
        fun closed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== ws) return
            ws = null
            ready = false
            if (sub == Sub.BOARD) live.onClosed()
            lastClose = "$code ${reason.take(80)}".trim()
            val now = clock()
            if (code != 1000) log("PropLine stream ${sub.name.lowercase()}: closed $lastClose")
            when (code) {
                1000 -> if (wanted) retryAtMs = now + backoffMs
                4401 -> { refusedKey = key(); log("PropLine stream: the key was refused (4401); no stream until the key changes") }
                4403 -> { if (!reactivated) { reactivated = true; id = null; retryAtMs = now + 5_000L } else retryAtMs = now + 30 * 60_000L }
                4404, 4400 -> { if (code == 4400 && lastSeq == null) needSeq = true; id = null; lastSeq = null; retryAtMs = now + 5_000L }
                4429 -> retryAtMs = now + 20_000L
                else -> { retryAtMs = now + backoffMs; backoffMs = minOf(backoffMs * 2, 60_000L) }
            }
        }
    }

    private fun timing(o: JsonObject, now: Long) {
        val created = PropLineClient.ms((o["created_at"] as? JsonPrimitive)?.content) ?: return
        val sent = PropLineClient.ms((o["sent_at"] as? JsonPrimitive)?.content) ?: return
        synchronized(queueMs) {
            queueMs.addLast((sent - created).coerceAtLeast(0L)); wireMs.addLast((now - sent).coerceAtLeast(0L))
            while (queueMs.size > 400) { queueMs.removeFirst(); wireMs.removeFirst() }
        }
    }

    /** For the tests: one frame as if it came on [sub]'s socket. */
    internal fun handleFrame(sub: Sub, o: JsonObject) = conns.getValue(sub).handle(o)

    private suspend fun send(k: String, b: Request.Builder): JsonObject? = sendRaw(k, b) as? JsonObject

    private suspend fun sendRaw(k: String, b: Request.Builder): JsonElement? {
        http.newCall(b.header("X-API-Key", k).build()).await().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401) { refusedKey = k; log("PropLine stream: key refused (HTTP 401)"); return null }
            if (!r.isSuccessful) { log("PropLine stream: ${r.request.method} ${r.request.url.encodedPath} HTTP ${r.code} ${text.take(160)}"); return null }
            return runCatching { json.parseToJsonElement(text) }.getOrNull()
        }
    }

    companion object {
        const val WS_URL = "wss://ws.prop-line.com/v1/stream"
        const val TICK_MS = 1_000L
        /** Filters are PATCHed at most this often when the sports or books change. */
        const val PATCH_GAP_MS = 5 * 60_000L
        /** Steam this strong or more is pushed (0-100; a prop steam storm of ~170 events a game on a Sunday is cut to the real moves). */
        const val MIN_STEAM = 40
        /** PropLine's own advice: a +EV above about 10% is usually a stale or mismatched line. */
        const val MAX_EV = 15
        private val JSON_TYPE = "application/json".toMediaType()
    }
}
