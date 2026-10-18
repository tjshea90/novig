package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.reference.SxBetConvert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** One game's score as `fixtures:live_scores` last said it. */
data class SxScore(
    val eventId: String, val sportId: Int, val teamOne: Int?, val teamTwo: Int?, val period: String?, val periodTime: String?, val atMs: Long,
    /** When the SCORE (not the clock) last changed: a live price older than this was set before the score moved, so it is not trusted ([com.tjshea.vigilant.data.sxbet.SxLiveBuild]). */
    val changedAtMs: Long = atMs,
    /** True once every period is finished (the game is over). */
    val finished: Boolean = false,
)

/** One raw push the recorder tap saw (`sxlive-<day>.jsonl`): the channel, when it arrived, and the data exactly as sent, so the live frame shapes are read from real answers. */
@kotlinx.serialization.Serializable
data class SxLiveRow(val atMs: Long, val channel: String, val data: String)

/**
 * SX Bet's real-time WebSocket (SXBET_API.md; docs developers/realtime-*). It is Centrifugo v6 speaking its JSON protocol (text frames, one JSON command per line):
 * `connect` with a short-lived token from `GET /user/realtime-token-v3/api-key` (any valid key, 30 a minute), then one `subscribe` per channel. Channels used: `best_odds_v3:global`
 * (a market's top of book changed; no history, so the books are seeded from REST on every connect: [onSubscribed]), `fixtures:live_scores`, `main_line:global`. A server ping is an
 * empty `{}` frame answered with the same. A token expires (the connect reply says `ttl`): a `refresh` command with a new one is sent a little before.
 *
 * What a book is worth: a market in [books] is current as of its push ([SxBetConvert.Book.atMs]); the channel carries no history, so after any reconnect [live] is false until the new
 * connection's subscription was acknowledged AND the caller re-seeded ([seed]). Only markets in [watch] are kept (the channel carries every market on the exchange). Read-only: nothing
 * here can post or cancel an order.
 */
class SxBetStream(
    private val http: OkHttpClient,
    private val json: Json,
    private val key: () -> String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val restUrl: String = "https://api.sx.bet",
    private val wsUrl: String = "wss://realtime.sx.bet/connection/websocket",
    private val backoffMinMs: Long = 2_000L,
    private val backoffMaxMs: Long = 60_000L,
    /** Called after every (re)subscription of the odds channel: the owner seeds [watch]ed markets from REST here. */
    private val onSubscribed: suspend () -> Unit = {},
) {
    val books = ConcurrentHashMap<String, SxBetConvert.Book>()
    val scores = ConcurrentHashMap<String, SxScore>()
    val mainLines = ConcurrentHashMap<String, String>()

    /** `fixtures:global` statuses by event id (1 not started, 2 in progress, 3 finished, 6 interrupted, 9 about to start: docs fixture-statuses), and when each was heard. */
    val fixtureStatus = ConcurrentHashMap<String, Int>()
    val fixtureStatusAt = ConcurrentHashMap<String, Long>()

    /** Told the market hash of every watched book push and the event id of every score or fixture push (called on the socket's thread: keep it quick). The live feed is push, not polled. */
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()
    fun addListener(l: (String) -> Unit) { listeners += l }
    fun removeListener(l: (String) -> Unit) { listeners -= l }
    private fun told(key: String) { for (l in listeners) runCatching { l(key) } }

    /** Research recorder (the engine sets it): sees every push before it is applied. Must not throw or block. */
    @Volatile var tap: ((String, JsonElement, Long) -> Unit)? = null

    private val watched = ConcurrentHashMap.newKeySet<String>()
    private val ids = AtomicInteger(1)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var job: Job? = null
    @Volatile var connected = false
        private set
    @Volatile private var oddsSubscribedAt = 0L
    @Volatile var lastMessageMs = 0L
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var messages = 0L
        private set
    @Volatile var reconnects = 0
        private set

    /** The books are current: connected, the odds channel acknowledged and heard from within [STALE_MS] (a quiet exchange still pings). */
    val live: Boolean get() = connected && oddsSubscribedAt > 0 && clock() - lastMessageMs < STALE_MS

    fun watch(hashes: Collection<String>) { watched.addAll(hashes) }

    /** Puts a REST read into the board as of its own time (only for watched markets, and never over a newer push). */
    fun seed(rows: Collection<SxBetConvert.Book>) {
        for (b in rows) {
            if (b.marketHash !in watched) continue
            books.merge(b.marketHash, b) { old, new -> if (new.atMs >= old.atMs) new else old }
        }
    }

    fun health(): String = when {
        job == null -> "off"
        live -> "live ($messages pushes, ${books.size} books, ${scores.size} scores, $reconnects reconnects)"
        connected -> "connected, waiting for the odds channel"
        else -> "connecting${lastError?.let { " (last error: $it)" } ?: ""}"
    }

    fun open() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = backoffMinMs
            while (isActive) {
                val ok = runCatching { session() }.onFailure { lastError = (it.message ?: it.javaClass.simpleName).take(160) }.isSuccess
                connected = false; oddsSubscribedAt = 0L
                if (!isActive) break
                reconnects++
                delay(backoff)
                backoff = if (ok) backoffMinMs else (backoff * 2).coerceAtMost(backoffMaxMs)
            }
        }
    }

    fun close() {
        job?.cancel(); job = null
        socket?.close(1000, "closing"); socket = null
        connected = false; oddsSubscribedAt = 0L
        books.clear()
    }

    private suspend fun token(): String {
        val req = Request.Builder().url("$restUrl/user/realtime-token-v3/api-key").header("x-sx-api-key", key().trim()).header("Accept", "application/json").get().build()
        return http.newCall(req).await().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("token HTTP ${r.code}")
            (json.parseToJsonElement(body).jsonObject["token"] as? JsonPrimitive)?.contentOrNull ?: error("no token in the reply")
        }
    }

    /** One connection: returns when it closes (the loop in [open] reconnects). */
    private suspend fun session() {
        if (key().isBlank()) error("no SX Bet key")
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        val tok = token()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                webSocket.send(buildJsonObject { put("id", ids.getAndIncrement()); put("connect", buildJsonObject { put("token", tok); put("name", "vigilant") }) }.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split('\n').filter { it.isNotBlank() }.forEach { line -> runCatching { handle(webSocket, json.parseToJsonElement(line).jsonObject) }.onFailure { lastError = "frame: ${it.message}".take(160) } }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { lastError = "closed $code $reason".trim(); done.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { lastError = (t.message ?: t.javaClass.simpleName).take(160); done.complete(Unit) }
        }
        http.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
        // A token lives for `ttl` seconds: refreshed by the connect handler's timer.
        try { done.await() } finally { socket?.close(1000, "bye"); socket = null }
    }

    private val subscribing = ConcurrentHashMap<Int, String>()

    private fun send(ws: WebSocket, cmd: String, body: JsonObject) {
        val id = ids.getAndIncrement()
        if (cmd == "subscribe") (body["channel"] as? JsonPrimitive)?.contentOrNull?.let { subscribing[id] = it }
        ws.send(buildJsonObject { put("id", id); put(cmd, body) }.toString())
    }

    /** The markets the owner asked to keep ([watch]). */
    fun watchedHashes(): List<String> = watched.toList()

    private fun handle(ws: WebSocket, o: JsonObject) {
        lastMessageMs = clock()
        if (o.isEmpty()) { ws.send("{}"); return }   // server ping -> pong
        o["error"]?.let { e -> lastError = "server: ${(e as? JsonObject)?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: e}".take(160) }
        o["connect"]?.let { c ->
            connected = true
            subscribing.clear()
            for (ch in CHANNELS) send(ws, "subscribe", buildJsonObject { put("channel", ch) })
            val ttl = ((c as? JsonObject)?.get("ttl") as? JsonPrimitive)?.intOrNull
            if ((c as? JsonObject)?.get("expires")?.let { (it as? JsonPrimitive)?.contentOrNull } == "true" && ttl != null) {
                scope.launch {
                    delay((ttl * 1000L * 8 / 10).coerceAtLeast(5_000L))
                    runCatching { send(ws, "refresh", buildJsonObject { put("token", token()) }) }.onFailure { lastError = "refresh: ${it.message}".take(160) }
                }
            }
        }
        o["subscribe"]?.let {
            // The reply carries the command's id, not the channel: [subscribing] remembers which channel each id asked for.
            val channel = (o["id"] as? JsonPrimitive)?.intOrNull?.let { subscribing.remove(it) }
            if (channel == "best_odds_v3:global" && oddsSubscribedAt == 0L) { oddsSubscribedAt = clock(); scope.launch { runCatching { onSubscribed() }.onFailure { lastError = "seed: ${it.message}".take(160) } } }
        }
        val push = o["push"] as? JsonObject ?: return
        val channel = (push["channel"] as? JsonPrimitive)?.contentOrNull ?: return
        val data = ((push["pub"] as? JsonObject)?.get("data")) ?: return
        messages++
        tap?.let { t -> runCatching { t(channel, data, lastMessageMs) } }
        apply(channel, data)
    }

    internal fun apply(channel: String, data: JsonElement) {
        val now = clock()
        when {
            channel == "best_odds_v3:global" -> SxBetConvert.bookOf(data, now)?.let { b -> if (b.marketHash in watched) { books[b.marketHash] = b; told(b.marketHash) } }
            channel == "fixtures:live_scores" -> (data as? JsonObject)?.let { d ->
                val id = (d["sportXeventId"] as? JsonPrimitive)?.contentOrNull ?: return
                val one = (d["teamOneScore"] as? JsonPrimitive)?.intOrNull
                val two = (d["teamTwoScore"] as? JsonPrimitive)?.intOrNull
                val old = scores[id]
                val periods = d["periods"] as? kotlinx.serialization.json.JsonArray
                val finished = periods != null && periods.isNotEmpty() && periods.all { ((it as? JsonObject)?.get("isFinished") as? JsonPrimitive)?.contentOrNull == "true" }
                scores[id] = SxScore(id, (d["sportId"] as? JsonPrimitive)?.intOrNull ?: 0, one, two,
                    (d["currentPeriod"] as? JsonPrimitive)?.contentOrNull, (d["periodTime"] as? JsonPrimitive)?.contentOrNull, now,
                    changedAtMs = if (old == null || old.teamOne != one || old.teamTwo != two) now else old.changedAtMs, finished = finished)
                told(id)
            }
            channel == "fixtures:global" -> (data as? JsonObject)?.let { d ->
                val id = (d["sportXeventId"] as? JsonPrimitive)?.contentOrNull ?: return
                val st = (d["status"] as? JsonPrimitive)?.intOrNull ?: return
                fixtureStatus[id] = st; fixtureStatusAt[id] = now
                told(id)
            }
            channel == "main_line:global" -> (data as? JsonObject)?.let { d ->
                val ev = (d["sportXeventId"] as? JsonPrimitive)?.contentOrNull ?: return
                val type = (d["marketType"] as? JsonPrimitive)?.contentOrNull ?: return
                (d["marketHash"] as? JsonPrimitive)?.contentOrNull?.let { mainLines["$ev|$type"] = it }
            }
        }
    }

    companion object {
        val CHANNELS = listOf("best_odds_v3:global", "fixtures:live_scores", "fixtures:global", "main_line:global")
        const val STALE_MS = 90_000L
    }
}
