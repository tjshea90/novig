package com.tjshea.vigilant.data.kalshi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One market's top of book as Kalshi's `ticker` channel last said it (prices in dollars, sizes in contracts). [atMs] is when THIS phone received it (the clock a REST read is ordered against); [sentMs] is
 * Kalshi's own `sending_ts_ms`, for the push-lag line in Diagnostics.
 */
data class KalshiTop(val yesBid: Double?, val yesAsk: Double?, val bidSize: Double?, val askSize: Double?, val atMs: Long, val sentMs: Long? = null)

/**
 * Kalshi's WebSocket (KALSHI_API.md): one signed connection to `wss://external-api-ws.kalshi.com/trade-api/ws/v2` carrying the `ticker` channel for the markets a scan reads (a push whenever a market's best
 * bid or ask or its size changes) and `market_lifecycle_v2` (a market paused, determined or settled). Nothing here prices or orders anything: [KalshiClient] reads this board over the REST catalog.
 *
 * How a price is trusted. `ticker` sends a message "whenever any ticker field changes", so a market that has not spoken since it was subscribed has not changed: its REST price is still its price. But a REST
 * read made BEFORE the subscription was acknowledged could have missed a change in between, so a read counts as current ([trusted]) only when it was made after its market's subscription was acknowledged,
 * on this connection, and after the last gap in a sequence ([trustFloorMs]). A dropped connection, a gap in `seq`, or a market that was never subscribed (Kalshi refused it) leaves the REST read to stand
 * as old as it is, and the next read after the connection is back makes it current again.
 *
 * What the docs do not say (the per-subscription market limit, the command rate, the connection count) is handled by asking in small steps: each chunk of [chunk] markets is a subscription of its own, spaced
 * [COMMAND_GAP_MS] apart, and error 26 (market limit) halves the chunk for the ones after.
 */
class KalshiStream(
    private val http: OkHttpClient,
    private val signer: () -> KalshiSigner?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wsUrl: String = DEFAULT_WS_URL,
    private val idleCloseMs: Long = IDLE_CLOSE_MS,
    private val backoffMinMs: Long = 2_000L,
    private val backoffMaxMs: Long = 60_000L,
) {
    enum class State { OFF, CONNECTING, LIVE, DOWN }

    @Volatile var state: State = State.OFF
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var lastClose: String? = null
        private set

    private val tops = ConcurrentHashMap<String, KalshiTop>()
    private val closedMarkets = ConcurrentHashMap.newKeySet<String>()
    private val subscribedAt = ConcurrentHashMap<String, Long>()
    private val wanted = ConcurrentHashMap<String, Set<String>>()

    /** Reads made before this instant do not count as current (a gap in a sequence, a new connection). */
    @Volatile var trustFloorMs: Long = 0L
        private set

    @Volatile private var socket: WebSocket? = null
    @Volatile private var loop: Job? = null
    @Volatile private var lastUsedMs = 0L
    @Volatile private var chunk = CHUNK
    @Volatile private var readyAtMs = 0L
    @Volatile private var framesThisConn = 0L
    private val lastSeq = ConcurrentHashMap<Long, Long>()
    private val pending = ConcurrentHashMap<Long, List<String>>()
    private val sidOfChannel = ConcurrentHashMap<String, Long>()
    @Volatile private var nextId = 1L
    private val syncLock = kotlinx.coroutines.sync.Mutex()

    @Volatile var connects = 0
        private set
    @Volatile var drops = 0
        private set
    @Volatile var frames = 0L
        private set
    @Volatile var tickerMessages = 0L
        private set
    @Volatile var lifecycleMessages = 0L
        private set
    @Volatile var gaps = 0
        private set
    @Volatile var subscribedAcks = 0
        private set
    @Volatile var oks = 0
        private set
    @Volatile var lastFrameMs = 0L
        private set
    @Volatile var openedAtMs = 0L
        private set
    @Volatile var errors = 0
        private set

    /** How long the last push took to reach this phone (receipt minus Kalshi's own send time; includes any clock difference). */
    @Volatile var lagMs: Long? = null
        private set
    private val errorCodes = ConcurrentHashMap<Int, Int>()

    val live: Boolean get() = state == State.LIVE

    /** Markets whose subscription Kalshi has acknowledged on this connection. */
    val subscribedCount: Int get() = subscribedAt.size
    val wantedCount: Int get() = wanted.values.sumOf { it.size }

    /** [tickers] are the markets [group] (a series) reads now: what is subscribed is the union over every group. Wakes the connection. */
    fun want(group: String, tickers: Collection<String>) {
        wanted[group] = tickers.toHashSet()
        lastUsedMs = clock()
        ensureRunning()
        if (state == State.LIVE) scope.launch { syncSubscriptions() }
    }

    /** A scan used the socket: keeps it open (it closes [idleCloseMs] after the last use). */
    fun use() { lastUsedMs = clock() }

    /** Waits up to [timeoutMs] for the connection to be live with its lifecycle channel subscribed. */
    suspend fun awaitReady(timeoutMs: Long): Boolean {
        val until = clock() + timeoutMs
        while (true) {
            if (state == State.LIVE && readyAtMs > 0L) return true
            if (clock() >= until || !currentCoroutineContext().isActive) return false
            delay(100)
        }
    }

    /** Whether a REST read of [ticker] made at [readAtMs] is current right now: see the class comment. */
    fun trusted(ticker: String, readAtMs: Long): Boolean {
        if (state != State.LIVE || framesThisConn == 0L) return false
        val at = subscribedAt[ticker] ?: return false
        return readAtMs > at && readAtMs > trustFloorMs
    }

    /** The latest push for [ticker], or null if it has not spoken on this connection. */
    fun top(ticker: String): KalshiTop? = tops[ticker]

    /** A market Kalshi said is paused, determined or settled. */
    fun isClosed(ticker: String): Boolean = ticker in closedMarkets

    fun close() {
        loop?.cancel()
        socket?.close(1000, "closed")
        socket = null
        resetConnection()
        state = State.OFF
    }

    private fun resetConnection() {
        subscribedAt.clear()
        pending.clear()
        sidOfChannel.clear()
        lastSeq.clear()
        tops.clear()
        readyAtMs = 0L
        framesThisConn = 0L
    }

    private fun ensureRunning() {
        synchronized(this) { if (loop?.isActive != true) loop = scope.launch { run() } }
    }

    private suspend fun run() {
        var backoff = backoffMinMs
        while (currentCoroutineContext().isActive) {
            if (clock() - lastUsedMs > idleCloseMs) break
            val key = signer()
            if (key == null) {
                lastError = "no Kalshi key saved (Settings › Kalshi)"
                state = State.DOWN
                delay(30_000)
                continue
            }
            val closed = CompletableDeferred<Closed>()
            val startedAt = clock()
            state = State.CONNECTING
            connects++
            openedAtMs = 0L
            resetConnection()
            // A new connection is trusted only for reads made after it is subscribed.
            trustFloorMs = startedAt
            val request = Request.Builder().url(wsUrl).apply { key.headers("GET", WS_SIGNED_PATH, startedAt).forEach { (k, v) -> header(k, v) } }.build()
            val ws = http.newWebSocket(request, Listener(closed))
            socket = ws
            var idle = false
            while (!closed.isCompleted) {
                withTimeoutOrNull(CHECK_EVERY_MS) { closed.await() }
                if (closed.isCompleted) break
                if (clock() - lastUsedMs > idleCloseMs) { idle = true; ws.close(1000, "idle"); withTimeoutOrNull(3_000) { closed.await() }; break }
                if (state == State.CONNECTING && openedAtMs == 0L && clock() - startedAt > CONNECT_TIMEOUT_MS) {
                    ws.cancel(); closed.complete(Closed(-1, "the upgrade did not finish within ${CONNECT_TIMEOUT_MS / 1000}s")); break
                }
            }
            val c = if (closed.isCompleted) closed.await() else Closed(1000, "idle")
            socket = null
            resetConnection()
            val lived = clock() - startedAt
            if (idle) { state = State.OFF; break }
            drops++
            lastClose = "code ${c.code}${if (c.reason.isNotBlank()) " ${c.reason.take(120)}" else ""} after ${lived / 1000}s"
            state = State.DOWN
            lastError = lastClose
            // A refused handshake (HTTP 401/403): the key is wrong or revoked. Tried again, slowly, so a fixed key (or a clock that was off) heals by itself.
            delay(if (c.refused) maxOf(backoff, AUTH_RETRY_MS) else backoff)
            if (lived >= 60_000L) backoff = backoffMinMs else backoff = minOf(backoff * 2, backoffMaxMs)
        }
        if (clock() - lastUsedMs > idleCloseMs) state = State.OFF
    }

    private fun send(frame: String): Boolean = socket?.send(frame) == true

    private fun command(cmd: String, params: JsonObject?): Pair<Long, String> {
        val id = nextId++
        val text = buildJsonObject {
            put("id", id)
            put("cmd", cmd)
            if (params != null) put("params", params)
        }.toString()
        return id to text
    }

    /** Subscribes the lifecycle channel once, then every wanted market not yet subscribed, a chunk at a time. */
    private suspend fun syncSubscriptions() {
        syncLock.lock()
        try {
            if (state != State.LIVE) return
            if (sidOfChannel["market_lifecycle_v2"] == null && pending.values.none { it.firstOrNull() == LIFECYCLE }) {
                val (id, text) = command("subscribe", buildJsonObject { put("channels", buildJsonArray { add(JsonPrimitive("market_lifecycle_v2")) }) })
                pending[id] = listOf(LIFECYCLE)
                if (!send(text)) return
                delay(COMMAND_GAP_MS)
            }
            val inFlight = pending.values.flatten().toHashSet()
            val todo = wanted.values.flatten().filter { !subscribedAt.containsKey(it) && it !in inFlight }.distinct()
            for (part in todo.chunked(chunk)) {
                if (state != State.LIVE) return
                val (id, text) = command(
                    "subscribe",
                    buildJsonObject {
                        put("channels", buildJsonArray { add(JsonPrimitive("ticker")) })
                        put("market_tickers", buildJsonArray { part.forEach { add(JsonPrimitive(it)) } })
                        put("skip_ticker_ack", true)
                    },
                )
                pending[id] = part
                if (!send(text)) return
                delay(COMMAND_GAP_MS)
            }
        } finally {
            syncLock.unlock()
        }
    }

    private fun onFrame(text: String) {
        val now = clock()
        lastFrameMs = now
        frames++
        framesThisConn++
        val obj = try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: return
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return
        val msg = obj["msg"] as? JsonObject
        val seq = (obj["seq"] as? JsonPrimitive)?.longOrNull
        val sid = (obj["sid"] as? JsonPrimitive)?.longOrNull ?: (msg?.get("sid") as? JsonPrimitive)?.longOrNull
        if (seq != null && sid != null) {
            val last = lastSeq.put(sid, seq)
            if (last != null && seq != last + 1 && type != "ok" && type != "unsubscribed") {
                // A message was lost: the board may be stale. Reads made before now no longer count as current.
                gaps++
                trustFloorMs = now
            }
        }
        when (type) {
            "subscribed" -> {
                val id = (obj["id"] as? JsonPrimitive)?.longOrNull
                val channel = (msg?.get("channel") as? JsonPrimitive)?.contentOrNull
                if (channel != null && sid != null) sidOfChannel.putIfAbsent(channel, sid)
                val part = id?.let { pending.remove(it) }
                subscribedAcks++
                if (part != null && part.firstOrNull() == LIFECYCLE) {
                    readyAtMs = now
                } else if (part != null) {
                    part.forEach { subscribedAt[it] = now }
                    if (readyAtMs == 0L) readyAtMs = now
                }
            }
            // A subscribe to a channel already subscribed on this connection is acknowledged as `ok` (Tj's first report, 2026-10-13: 10,049 markets wanted, 200 acknowledged as `subscribed`, 3,184 of them
            // pushing): the markets were added to the existing subscription, and count from this acknowledgement all the same.
            "ok" -> {
                oks++
                val id = (obj["id"] as? JsonPrimitive)?.longOrNull
                val part = id?.let { pending.remove(it) }
                if (part != null && part.firstOrNull() != LIFECYCLE) {
                    part.forEach { subscribedAt[it] = now }
                    if (readyAtMs == 0L) readyAtMs = now
                }
            }
            "ticker" -> {
                tickerMessages++
                if (msg != null) applyTicker(msg, (obj["sending_ts_ms"] as? JsonPrimitive)?.longOrNull ?: now, now)
            }
            "market_lifecycle_v2" -> {
                lifecycleMessages++
                if (msg != null) applyLifecycle(msg)
            }
            "error" -> {
                errors++
                val code = (msg?.get("code") as? JsonPrimitive)?.intOrNull ?: (obj["code"] as? JsonPrimitive)?.intOrNull
                val why = (msg?.get("msg") as? JsonPrimitive)?.contentOrNull ?: (obj["msg"] as? JsonPrimitive)?.contentOrNull
                if (code != null) errorCodes.merge(code, 1, Int::plus)
                lastError = "error ${code ?: "?"}${why?.let { ": ${it.take(120)}" } ?: ""}"
                val id = (obj["id"] as? JsonPrimitive)?.longOrNull
                val part = id?.let { pending.remove(it) }
                // Error 26: the subscription took too many markets. The ones after go in smaller chunks, and these are tried again in them.
                if (code == 26 && chunk > MIN_CHUNK) {
                    chunk = maxOf(MIN_CHUNK, chunk / 2)
                    scope.launch { delay(COMMAND_GAP_MS); syncSubscriptions() }
                } else if ((code == 27 || code == 18) && part != null) {
                    // Too fast or timed out: the same chunk again, a little later.
                    scope.launch { delay(2_000); syncSubscriptions() }
                }
            }
        }
    }

    private fun applyTicker(msg: JsonObject, sentMs: Long, now: Long) {
        val ticker = (msg["market_ticker"] as? JsonPrimitive)?.contentOrNull ?: return
        fun d(k: String) = (msg[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: (msg[k] as? JsonPrimitive)?.doubleOrNull
        val old = tops[ticker]
        // A field a message leaves out keeps what the last one said.
        tops[ticker] = KalshiTop(
            yesBid = d("yes_bid_dollars") ?: old?.yesBid, yesAsk = d("yes_ask_dollars") ?: old?.yesAsk,
            bidSize = d("yes_bid_size_fp") ?: old?.bidSize, askSize = d("yes_ask_size_fp") ?: old?.askSize,
            atMs = now, sentMs = sentMs.takeIf { it > 0 },
        )
        (sentMs.takeIf { it > 0 })?.let { lagMs = (now - it).coerceAtLeast(0) }
    }

    private fun applyLifecycle(msg: JsonObject) {
        val ticker = (msg["market_ticker"] as? JsonPrimitive)?.contentOrNull ?: return
        when ((msg["event_type"] as? JsonPrimitive)?.contentOrNull) {
            "determined", "settled" -> closedMarkets.add(ticker)
            "deactivated" -> closedMarkets.add(ticker)
            "activated" -> closedMarkets.remove(ticker)
        }
        (msg["is_deactivated"] as? JsonPrimitive)?.contentOrNull?.let { if (it == "true") closedMarkets.add(ticker) else closedMarkets.remove(ticker) }
    }

    private class Closed(val code: Int, val reason: String, val refused: Boolean = false)

    private inner class Listener(private val closed: CompletableDeferred<Closed>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== socket) return
            openedAtMs = clock()
            state = State.LIVE
            lastError = null
            scope.launch { syncSubscriptions() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === socket) onFrame(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            closed.complete(Closed(code, reason))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(Closed(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val refused = response?.code == 401 || response?.code == 403
            val why = listOfNotNull(response?.let { "HTTP ${it.code}" }, t.message?.take(120)).joinToString(": ")
            closed.complete(Closed(-1, why, refused))
        }
    }

    /** Diagnostics (Kalshi's block): the socket's state and counters, what it holds, the error codes it has seen. */
    fun report(now: Long = clock()): List<String> = buildList {
        add(
            "socket: ${state.name.lowercase()} · connects $connects, drops $drops" + (lastClose?.let { " (last: $it)" } ?: "") +
                " · frames $frames (ticker $tickerMessages, lifecycle $lifecycleMessages)" + (readyAtMs.takeIf { it > 0 }?.let { " · ready ${(now - it) / 1000}s ago" } ?: " · not ready"),
        )
        add("  markets: wanted $wantedCount, subscribed $subscribedCount, with a push ${tops.size}, closed/paused ${closedMarkets.size} · chunk $chunk · sequence gaps $gaps · acks: subscribed $subscribedAcks, ok $oks · still waiting for ${pending.values.sumOf { it.size }}")
        lastError?.let { add("  last error: $it") }
        if (errorCodes.isNotEmpty()) add("  error codes seen: " + errorCodes.entries.sortedBy { it.key }.joinToString { "${it.key}×${it.value}" })
        if (lastFrameMs > 0) add("  last frame ${(now - lastFrameMs) / 1000}s ago" + (lagMs?.let { " · last push arrived $it ms after Kalshi sent it" } ?: ""))
    }

    companion object {
        const val DEFAULT_WS_URL = "wss://external-api-ws.kalshi.com/trade-api/ws/v2"
        const val WS_SIGNED_PATH = KalshiSigner.WS_PATH

        /** Markets in one subscription to start with (unpublished limit: error 26 halves it down to [MIN_CHUNK]). */
        const val CHUNK = 200
        const val MIN_CHUNK = 20

        /** Space between subscribe commands (error 27 is a per-subscription command-rate limit that the docs give no number for). */
        const val COMMAND_GAP_MS = 250L
        const val CHECK_EVERY_MS = 1_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val AUTH_RETRY_MS = 5 * 60_000L

        /** Closes after this long without a scan using it. */
        const val IDLE_CLOSE_MS = 10 * 60_000L
        private const val LIFECYCLE = "\u0000lifecycle"

        /** The OkHttp client for the socket: Kalshi pings every 10 s and OkHttp answers; its own ping every 20 s notices a dead connection. */
        fun socketClient(base: OkHttpClient): OkHttpClient = base.newBuilder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    }
}
