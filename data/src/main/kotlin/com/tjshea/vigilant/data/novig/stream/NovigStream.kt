package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

sealed interface StreamState {
    data object Off : StreamState
    data object Connecting : StreamState
    data class Live(val sinceMs: Long, val markets: Int) : StreamState
    data class Failed(val message: String, val atMs: Long) : StreamState
}

/**
 * Order books a source keeps current by push, so a scan reads them without a request each: Novig's
 * websocket with the connected key ([NovigStream]); tests fake it.
 */
interface PushedBooks {
    /**
     * The markets a scan wants, most important first. New ones are subscribed (in bulk, as the
     * throttle allows), ones no longer wanted are dropped. Connects on first use.
     */
    fun watch(marketIds: Collection<String>)

    /** The books held current right now among [marketIds]: only those already pushed. */
    fun live(marketIds: Collection<String>): Map<String, NovigBook>

    /** Why pushing stopped, if it did at or after [sinceMs]. */
    fun problemSince(sinceMs: Long): String?

    fun close()
}

/**
 * Novig's signed websocket (`GET /v3/ws`, NOVIG_API.md §6) carrying the `book` channel for exactly
 * the markets a scan prices (Tj, 2026-09-28: "Make sure the app is taking full advantage of the novig
 * API key").
 *
 * Why it's fast: a request is charged `weight × subjects` but **never more than the 512-token `stream`
 * bucket**, and one over that passes whenever the bucket is full (docs.novig.com/api/streaming/connection).
 * So once the bucket is full again after the upgrade (32 tokens, ~8 s at 4/s), ONE `subscribe` covers
 * every planned market, up to the 2,048 a connection may watch, and their snapshots arrive together;
 * after that each change is pushed. A 1,200-price scan takes that instead of ~90 s of REST reads.
 * Additions that fit the tokens left go at once; bigger ones wait for a full bucket. Events aren't
 * subscribed: an event counts as all its markets (500+ for an NFL game), which would crowd out the
 * 2,048 cap with lines no fair source quotes.
 *
 * It lives only while scans use it: closed [idleCloseMs] after the last [watch] or [live], and on any
 * failure (then not retried for [retryAfterFailureMs]; the key's REST route reads meanwhile). OkHttp
 * answers Novig's 15 s pings and pings back, so a dead connection fails instead of serving old books.
 */
class NovigStream(
    http: OkHttpClient,
    private val signer: NovigSignedClient,
    private val scope: CoroutineScope,
    private val wsUrl: String = "wss://api.novig.com/v3/ws",
    private val clock: () -> Long = System::currentTimeMillis,
    private val capacity: Double = STREAM_CAPACITY.toDouble(),
    private val refillPerSec: Double = REFILL_PER_SEC,
    maxMarkets: Int = MAX_MARKETS,
    private val idleCloseMs: Long = IDLE_CLOSE_MS,
    private val retryAfterFailureMs: Long = RETRY_AFTER_FAILURE_MS,
) : PushedBooks {
    private val http = http.newBuilder().pingInterval(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    val books = StreamBooks(clock)

    private val _state = MutableStateFlow<StreamState>(StreamState.Off)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var socket: WebSocket? = null
    private val nonce = AtomicLong(0)

    /** Wanted markets, most important first. */
    private var wanted: List<String> = emptyList()

    /** Asked for on this connection (acknowledged or not). */
    private val subscribed = HashSet<String>()

    /** Subscribes not yet answered, by nonce: a refusal takes them back out of [subscribed]. */
    private val unanswered = HashMap<Long, List<String>>()

    /** The most markets held at once: the documented 2,048 (less a margin), lowered if Novig says so. */
    private var limit = maxMarkets

    private var syncJob: Job? = null
    private var idleJob: Job? = null
    private var tokens = 0.0
    private var tokensAt = 0L
    private var lastUsedMs = 0L
    private var failedAtMs: Long? = null
    private var problem: String? = null

    /**
     * Whether this connection's first subscribe went out. It waits for a full bucket: a small early
     * one (the first few planned lines) would spend the tokens the whole plan's single request needs,
     * and the bucket takes two minutes to refill.
     */
    private var firstSent = false

    /** Subscribe requests sent on the current connection (for tests and the scan report). */
    @Volatile
    var subscribeRequests = 0
        private set

    override fun watch(marketIds: Collection<String>) {
        val start: Boolean
        synchronized(this) {
            lastUsedMs = clock()
            wanted = marketIds.distinct()
            start = socket == null && failedAtMs.let { it == null || clock() - it >= retryAfterFailureMs }
        }
        if (start) connect() else if (_state.value is StreamState.Live) scheduleSync()
    }

    override fun live(marketIds: Collection<String>): Map<String, NovigBook> {
        synchronized(this) { lastUsedMs = clock() }
        if (_state.value !is StreamState.Live) return emptyMap()
        val now = clock()
        val out = HashMap<String, NovigBook>()
        for (id in marketIds) {
            if (!synchronized(this) { id in subscribed }) continue
            // Current as of now: every change since its snapshot has been applied.
            books.book(id)?.let { out[id] = it.copy(fetchedAtMs = now) }
        }
        return out
    }

    override fun problemSince(sinceMs: Long): String? = synchronized(this) { problem?.takeIf { (failedAtMs ?: return null) >= sinceMs } }

    fun book(marketId: String): NovigBook? = live(listOf(marketId))[marketId]

    @Synchronized
    fun connect() {
        if (socket != null) return
        _state.value = StreamState.Connecting
        val request = try {
            signer.signedRequest("GET", "/v3/ws").newBuilder().url(wsUrl).build()
        } catch (e: Exception) {
            // The Keystore key is gone (e.g. settings restored onto a new phone) or unusable.
            fail("This phone can't sign with the saved Novig key. Reconnect it in Settings.")
            return
        }
        // The upgrade costs 32; the bucket is assumed full before it (a fresh or long-idle key).
        tokens = capacity - UPGRADE_COST
        tokensAt = clock()
        subscribeRequests = 0
        firstSent = false
        socket = http.newWebSocket(request, Listener())
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(minOf(idleCloseMs, IDLE_CHECK_MS))
                val idle = synchronized(this@NovigStream) { clock() - lastUsedMs >= idleCloseMs }
                if (idle) {
                    close()
                    break
                }
            }
        }
    }

    override fun close() {
        val (sync, idle, ws) = synchronized(this) {
            val t = Triple(syncJob, idleJob, socket)
            syncJob = null
            idleJob = null
            socket = null
            subscribed.clear()
            unanswered.clear()
            t
        }
        sync?.cancel()
        ws?.close(1000, "idle")
        books.clear()
        _state.value = StreamState.Off
        // Last: the idle watchdog may be the caller.
        idle?.cancel()
    }

    private fun scheduleSync() {
        synchronized(this) {
            if (syncJob?.isActive == true) return
            syncJob = scope.launch { sync() }
        }
    }

    /** Subscribes what's wanted and not held, drops what isn't wanted, and re-snapshots gaps. */
    private suspend fun sync() {
        while (scope.isActive) {
            val (drop, add) = synchronized(this) {
                if (socket == null) return
                val want = wanted.take(limit.coerceAtLeast(0))
                val wantSet = want.toHashSet()
                val drop = subscribed.filter { it !in wantSet }
                val room = limit - (subscribed.size - drop.size)
                drop to want.filter { it !in subscribed }.take(room.coerceAtLeast(0))
            }
            if (drop.isNotEmpty()) {
                // 1 token a subject: cheap, but a refused unsubscribe would leave them pushing.
                waitFor(drop.size.toDouble())
                send(buildJsonObject {
                    put("nonce", nonce.incrementAndGet())
                    putJsonArray("unsubscribe") { drop.forEach { add(JsonPrimitive("market:$it")) } }
                })
                synchronized(this) { subscribed.removeAll(drop.toSet()) }
                books.forget(drop)
                spend(drop.size.toDouble())
            }
            if (add.isEmpty()) break
            // Charged weight × subjects, never over the bucket; a request at the cap needs it full.
            val cost = minOf(add.size * BOOK_WEIGHT.toDouble(), capacity)
            if (!haveTokens(cost)) {
                // Wait for enough, then look again: more markets may be wanted by then, and go in the same request.
                waitFor(cost)
                continue
            }
            val n = nonce.incrementAndGet()
            synchronized(this) {
                if (socket == null) return
                subscribed.addAll(add)
                unanswered[n] = add
            }
            send(buildJsonObject {
                put("nonce", n)
                putJsonObject("subscribe") { putJsonObject("markets") { add.forEach { put(it, "book") } } }
            })
            subscribeRequests++
            spend(cost)
            _state.value = (state.value as? StreamState.Live)?.copy(markets = synchronized(this) { subscribed.size }) ?: state.value
        }
        // Books whose seq skipped are re-read with `snapshot` (it keeps the subscriptions).
        val gaps = books.needsSnapshot.filter { synchronized(this) { it in subscribed } }
        if (gaps.isNotEmpty()) {
            val cost = minOf(gaps.size * BOOK_WEIGHT.toDouble(), capacity)
            waitFor(cost)
            send(buildJsonObject {
                put("nonce", nonce.incrementAndGet())
                putJsonObject("snapshot") { putJsonObject("markets") { gaps.forEach { put(it, "book") } } }
            })
            spend(cost)
        }
    }

    private fun refill() = synchronized(this) {
        val now = clock()
        tokens = minOf(capacity, tokens + (now - tokensAt).coerceAtLeast(0) / 1000.0 * refillPerSec)
        tokensAt = now
    }

    private fun haveTokens(cost: Double): Boolean {
        refill()
        return synchronized(this) { tokens + 1e-9 >= cost }
    }

    private suspend fun waitFor(cost: Double) {
        refill()
        val short = synchronized(this) { cost - tokens }
        if (short > 0) delay((short / refillPerSec * 1000).toLong() + 50)
    }

    private fun spend(cost: Double) {
        refill()
        synchronized(this) { tokens -= cost }
    }

    private fun send(message: JsonObject) {
        synchronized(this) { socket }?.send(message.toString())
    }

    /**
     * Whether [webSocket] is the connection in use. A socket [close] let go of (or one replaced by
     * a reconnect) still gets its last callbacks on OkHttp's thread: they must change nothing,
     * or a deliberate close reads "Failed" and a quick reconnect is torn down by the old socket's
     * goodbye (full test, 2026-09-27).
     */
    private fun current(webSocket: WebSocket): Boolean = synchronized(this) { socket === webSocket }

    private fun held(marketId: String): Boolean = synchronized(this) { marketId in subscribed }

    /** A reply's error code, top level or under `error` (Novig's frames don't document one shape). */
    private fun errorCode(msg: JsonObject): String? =
        (msg["code"] as? JsonPrimitive)?.content ?: ((msg["error"] as? JsonObject)?.get("code") as? JsonPrimitive)?.content

    private fun refused(msg: JsonObject, code: String) {
        val n = (msg["nonce"] as? JsonPrimitive)?.longOrNull
        synchronized(this) {
            // A throttled frame is answered with no nonce: every unanswered subscribe may be the one.
            val lost = if (n != null) unanswered.remove(n).orEmpty() else unanswered.values.flatten().also { unanswered.clear() }
            subscribed.removeAll(lost.toSet())
            when (code) {
                // Our token model ran ahead of Novig's: start it from empty.
                "RATE_LIMIT_EXCEEDED" -> { tokens = 0.0; tokensAt = clock() }
                // Held fewer than we thought possible: ask for half of what was refused next time.
                "SUBSCRIPTION_LIMIT_EXCEEDED" -> limit = subscribed.size + lost.size / 2
            }
        }
        scheduleSync()
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current(webSocket)) return
            _state.value = StreamState.Live(clock(), 0)
            scheduleSync()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current(webSocket)) return
            val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            errorCode(msg)?.let { code ->
                refused(msg, code)
                return
            }
            (msg["nonce"] as? JsonPrimitive)?.longOrNull?.let { n -> synchronized(this@NovigStream) { unanswered.remove(n) } }
            (msg["snapshot"] as? JsonObject)?.forEach { (marketId, body) ->
                if (!held(marketId)) return@forEach
                (body as? JsonObject)?.get("book")?.let { it as? JsonObject }?.let { books.applySnapshot(marketId, it) }
            }
            var gap = false
            (msg["delta"] as? JsonObject)?.forEach { (marketId, body) ->
                // Pushes for a market just dropped are ignored (its unsubscribe may still be on the way).
                if (!held(marketId)) return@forEach
                (body as? JsonObject)?.get("book")?.let { it as? JsonObject }?.let { if (!books.applyDelta(marketId, it)) gap = true }
            }
            if (gap) scheduleSync()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(
            webSocket,
            when {
                reason.startsWith("GEOLOCATION") -> "Novig wants your location confirmed: open the Novig app."
                reason == "SLOW_CONSUMER" -> "Novig closed the live feed (the phone fell behind)."
                else -> "Novig closed the live feed ($code${if (reason.isNotBlank()) " $reason" else ""})."
            },
        )

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(
            webSocket,
            when (response?.code) {
                401 -> "Novig refused the key for its live feed (401)."
                403 -> "This key can't open Novig's live feed (403)."
                451 -> "Novig's location check failed (451): turn off any VPN and open the Novig app."
                423 -> "Novig says the account is locked (423)."
                else -> "Novig's live feed dropped (${t.message ?: t.javaClass.simpleName})."
            },
        )
    }

    private fun fail(message: String) {
        synchronized(this) {
            failedAtMs = clock()
            problem = message
        }
        _state.value = StreamState.Failed(message, clock())
    }

    private fun dropped(webSocket: WebSocket, message: String) {
        val jobs = synchronized(this) {
            // Not the connection in use (closed on purpose, or replaced): nothing was dropped.
            if (socket !== webSocket) return
            socket = null
            subscribed.clear()
            unanswered.clear()
            listOf(syncJob, idleJob).also { syncJob = null; idleJob = null }
        }
        books.clear()
        jobs.forEach { it?.cancel() }
        fail(message)
    }

    companion object {
        const val STREAM_CAPACITY = 512
        const val REFILL_PER_SEC = 4.0
        const val UPGRADE_COST = 32
        const val BOOK_WEIGHT = 16

        /** Documented: 2,048 markets watched per connection. A margin under it. */
        const val MAX_MARKETS = 2_000

        /** Closed this long after a scan last used it: rechecks right after a scan are instant. */
        const val IDLE_CLOSE_MS = 2 * 60_000L
        private const val IDLE_CHECK_MS = 15_000L

        /** After a failure, scans read by REST this long before the feed is tried again. */
        const val RETRY_AFTER_FAILURE_MS = 5 * 60_000L
    }
}
