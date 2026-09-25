package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicLong

sealed interface StreamState {
    data object Off : StreamState
    data object Connecting : StreamState
    data class Live(val sinceMs: Long, val events: Int) : StreamState
    data class Failed(val message: String, val atMs: Long) : StreamState
}

/**
 * Novig's signed websocket (`GET /v3/ws`, NOVIG_API.md §6) carrying the `book` channel for every
 * event the scanner prices. One socket replaces every REST book poll: each order-book change is
 * pushed the moment it happens.
 *
 * Throttle: the `stream` bucket holds 512 tokens and refills 4/s; the upgrade costs 32 and `book`
 * on one event costs 16. [setEvents] therefore subscribes in chunks of at most [CHUNK] events,
 * spaced so the local token model never overdraws. OkHttp answers Novig's 15s pings itself.
 */
class NovigStream(
    private val http: OkHttpClient,
    private val signer: NovigSignedClient,
    private val scope: CoroutineScope,
    private val wsUrl: String = "wss://api.novig.com/v3/ws",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    val books = StreamBooks(clock)

    private val _state = MutableStateFlow<StreamState>(StreamState.Off)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var socket: WebSocket? = null
    private val nonce = AtomicLong(0)
    private var wanted: Set<String> = emptySet()
    private var subscribed: Set<String> = emptySet()
    private var syncJob: Job? = null
    private var tokens = 0.0
    private var tokensAt = 0L

    @Synchronized
    fun connect() {
        if (socket != null) return
        _state.value = StreamState.Connecting
        val signed = signer.signedRequest("GET", "/v3/ws")
        val request = signed.newBuilder().url(wsUrl).build()
        tokens = STREAM_CAPACITY - UPGRADE_COST
        tokensAt = clock()
        socket = http.newWebSocket(request, Listener())
    }

    @Synchronized
    fun close() {
        syncJob?.cancel()
        socket?.close(1000, "app in background")
        socket = null
        subscribed = emptySet()
        books.clear()
        _state.value = StreamState.Off
    }

    /** The events whose books the scanner wants live. Subscribes new ones, drops old ones. */
    @Synchronized
    fun setEvents(eventIds: Set<String>) {
        wanted = eventIds
        if (_state.value is StreamState.Live) scheduleSync()
    }

    fun book(marketId: String): NovigBook? =
        if (_state.value is StreamState.Live) books.book(marketId)?.copy(fetchedAtMs = clock()) else null

    private fun scheduleSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch { sync() }
    }

    private suspend fun sync() {
        while (true) {
            val (add, drop) = synchronized(this) { (wanted - subscribed) to (subscribed - wanted) }
            if (add.isEmpty() && drop.isEmpty()) break
            if (drop.isNotEmpty()) {
                send(buildJsonObject {
                    put("nonce", nonce.incrementAndGet())
                    putJsonArray("unsubscribe") { drop.forEach { add(JsonPrimitive("event:$it")) } }
                })
                synchronized(this) { subscribed = subscribed - drop }
                spend(drop.size.toDouble())
            }
            val chunk = add.take(CHUNK)
            if (chunk.isNotEmpty()) {
                val cost = chunk.size * BOOK_WEIGHT.toDouble()
                waitForTokens(cost)
                send(buildJsonObject {
                    put("nonce", nonce.incrementAndGet())
                    putJsonObject("subscribe") { putJsonObject("events") { chunk.forEach { put(it, "book") } } }
                })
                synchronized(this) { subscribed = subscribed + chunk }
                spend(cost)
            }
            _state.value = (state.value as? StreamState.Live)?.copy(events = subscribed.size) ?: state.value
        }
        // Resync any book whose seq had a gap.
        val gaps = books.needsSnapshot
        if (gaps.isNotEmpty()) {
            val cost = gaps.size * BOOK_WEIGHT.toDouble()
            waitForTokens(minOf(cost, STREAM_CAPACITY.toDouble()))
            send(buildJsonObject {
                put("nonce", nonce.incrementAndGet())
                putJsonObject("snapshot") { putJsonObject("markets") { gaps.forEach { put(it, "book") } } }
            })
            spend(cost)
        }
    }

    private fun refill() {
        val now = clock()
        tokens = minOf(STREAM_CAPACITY.toDouble(), tokens + (now - tokensAt) / 1000.0 * REFILL_PER_SEC)
        tokensAt = now
    }

    private suspend fun waitForTokens(cost: Double) {
        refill()
        if (tokens < cost) delay(((cost - tokens) / REFILL_PER_SEC * 1000).toLong() + 250)
    }

    private fun spend(cost: Double) {
        refill()
        tokens -= cost
    }

    private fun send(message: JsonObject) {
        socket?.send(message.toString())
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _state.value = StreamState.Live(clock(), 0)
            scheduleSync()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            msg["snapshot"]?.jsonObject?.forEach { (marketId, body) ->
                body.jsonObject["book"]?.jsonObject?.let { books.applySnapshot(marketId, it) }
            }
            var gap = false
            msg["delta"]?.jsonObject?.forEach { (marketId, body) ->
                body.jsonObject["book"]?.jsonObject?.let { if (!books.applyDelta(marketId, it)) gap = true }
            }
            if (gap) scheduleSync()
            msg["code"]?.jsonPrimitive?.content?.let { code ->
                if (code == "RATE_LIMIT_EXCEEDED") scope.launch { delay(2_000); scheduleSync() }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(
            when (reason) {
                "GEOLOCATION_EXPIRED" -> "Open the Novig app so it can confirm your location."
                "SLOW_CONSUMER" -> "Connection was too slow; reconnecting."
                else -> "Stream closed ($code${if (reason.isNotBlank()) " $reason" else ""})."
            },
        )

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(
            when (response?.code) {
                401 -> "Novig rejected the key for streaming (401). Check the key in Settings."
                403 -> "This key can't stream (403). It needs trading or trading::read scope."
                451 -> "Novig's location check failed (451): turn off any VPN and open the Novig app."
                423 -> "Novig says the account is locked (423)."
                else -> t.message ?: t.javaClass.simpleName
            },
        )
    }

    private fun dropped(message: String) {
        synchronized(this) {
            socket = null
            subscribed = emptySet()
        }
        books.clear()
        syncJob?.cancel()
        _state.value = StreamState.Failed(message, clock())
    }

    companion object {
        const val STREAM_CAPACITY = 512
        const val REFILL_PER_SEC = 4.0
        const val UPGRADE_COST = 32
        const val BOOK_WEIGHT = 16
        /** 28 events x 16 = 448 tokens, under the 512 per-request cap. */
        const val CHUNK = 28
    }
}
