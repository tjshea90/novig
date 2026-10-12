package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.stream.OrderEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Where a taker order can be sent from besides a REST request: [NovigTradingClient.placeOrder] asks [ready] and, when it is, hands the order to [place]. */
interface OrderSocket {
    /** An order can go out over the socket right now (connected, switched on, and not put on REST after a failure). */
    val ready: Boolean

    /** Places one order and returns its id. A [NovigApiException] is Novig's refusal; a [SocketFallback] means NOTHING was placed (send it by REST); any other [IOException] is a lost answer. */
    suspend fun place(order: NovigTradingClient.NewOrder): String
}

/** The socket could not take this order and nothing left the phone, or Novig could not read the frame (so nothing was placed): the caller sends it by REST. */
class SocketFallback(why: String) : Exception(why)

/** The order went out over the socket and no usable answer came back: it may be placed. The caller looks it up by its clientId, as for a lost REST answer. */
class SocketNoAnswer(why: String) : IOException(why)

/**
 * Orders over Novig's websocket (NOVIG_API.md §27; docs.novig.com/api/streaming/connection "Trade"): `{"nonce": n, "place": {"orders": [...]}}` answered by `{"nonce": n, "placed": [{"orderId", "clientId"}]}`
 * or by an error `{"nonce": n, "code", "message", "rejected"?}`. It needs a `trading` key (a `trading::read` key's connection gets SCOPE_INSUFFICIENT), so it is a connection of its own, signed by the
 * subaccount's TRADING key, with its own `stream` bucket: the market-data socket (the read key's, loaded with snapshots) is never in the way of an order. It also subscribes the private `orders` channel, whose
 * events say how the order ended ([events]); an order's end then reaches [com.tjshea.vigilant.data.novig.trading.ApiBetPlacer] by push.
 *
 * What it saves over `POST /v3/orders`: no request to sign (the connection is signed once; a P-256 signature on the phone's Keystore is the slowest step of a REST order), no new HTTP exchange, an open TLS
 * session. What it does not change: an ack is "accepted and queued", the matching engine's answer is the `fill` / `reject` event, and the fills are still read from REST for the record (they carry the fee).
 *
 * Never a replay: Novig does not check a `clientId` for uniqueness ("a replay places a second order"), so an order that was sent and not answered is found by its clientId, never sent again. Only when
 * nothing can have been placed does it hand the order back for REST: not ready, a frame that could not be sent, or a reply that says Novig could not read the frame ([SHAPE_CODES]), which also puts the
 * socket on REST for [UNTRUSTED_MS]. A missing or unreadable answer does the same, and says so in [report].
 */
class NovigOrderSocket(
    http: OkHttpClient,
    private val signer: NovigSignedClient,
    private val scope: CoroutineScope,
    private val wsUrl: String = "wss://api.novig.com/v3/ws",
    private val clock: () -> Long = System::currentTimeMillis,
    /** How long a placed order waits for its answer. REST waits for a response the same way; the median is under 0.2 s. */
    private val ackTimeoutMs: Long = ACK_TIMEOUT_MS,
    /** The connection closes this long after the last order or plan; a new order finds it closed and goes by REST while it reconnects. */
    private val idleCloseMs: Long = IDLE_CLOSE_MS,
    private val reconnectMs: Long = RECONNECT_MS,
) : OrderSocket {

    enum class State { OFF, CONNECTING, READY, FAILED }

    private val http = http.newBuilder().pingInterval(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** The subaccount's own orders over this connection (`private: [orders]`): the end of an order, pushed. */
    val events = OrderEvents(clock)

    @Volatile var state: State = State.OFF
        private set

    /** Settings: orders by socket on or off. Off = every order goes by REST and nothing is connected. */
    @Volatile var enabled = true
        set(v) {
            field = v
            if (!v) close()
        }

    @Volatile private var socket: WebSocket? = null
    @Volatile private var lastUsedMs = 0L
    @Volatile private var untrustedUntilMs = 0L
    @Volatile private var untrustedWhy: String? = null
    @Volatile private var lastError: String? = null
    @Volatile private var failedAtMs = 0L
    @Volatile private var retryAtMs = 0L
    private var failures = 0
    private val nonce = AtomicLong(0)
    private val lock = Any()
    private var inFlight: Pair<Long, CompletableDeferred<JsonObject>>? = null
    private var privateNonce = 0L
    private var privateOk = false
    private val placeMutex = Mutex()
    private var reconnectJob: Job? = null
    private var idleJob: Job? = null

    // Diagnostics
    @Volatile var connects = 0
        private set
    @Volatile var placed = 0
        private set
    @Volatile var refused = 0
        private set
    @Volatile var lost = 0
        private set
    @Volatile var fellBack = 0
        private set
    @Volatile var lastAckMs: Long? = null
        private set

    override val ready: Boolean get() = enabled && state == State.READY && clock() >= untrustedUntilMs

    /** Opens the connection if it is not open (an order is likely soon: the Bet sheet opened, the auto-bet is planning). Cheap to call often. */
    fun warm() {
        if (!enabled) return
        synchronized(lock) { lastUsedMs = clock() }
        val go = synchronized(lock) { socket == null && clock() >= retryAtMs && clock() >= untrustedUntilMs }
        if (go) connect()
    }

    private fun connect() {
        val request = try {
            signer.signedRequest("GET", "/v3/ws").newBuilder().url(wsUrl).build()
        } catch (e: Exception) {
            fail("This phone can't sign with the saved Novig trading key (${e.message ?: e.javaClass.simpleName}).", RETRY_AFTER_FAILURE_MS)
            return
        }
        synchronized(lock) {
            if (socket != null) return
            state = State.CONNECTING
            privateOk = false
            events.reset()
            connects++
            socket = http.newWebSocket(request, Listener())
        }
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(IDLE_CHECK_MS)
                if (clock() - lastUsedMs >= idleCloseMs) {
                    close()
                    break
                }
            }
        }
    }

    fun close() {
        val ws: WebSocket?
        synchronized(lock) {
            ws = socket
            socket = null
            state = State.OFF
            reconnectJob?.cancel()
            reconnectJob = null
        }
        idleJob?.cancel()
        idleJob = null
        ws?.close(1000, "idle")
        events.reset()
        failPending(IOException("the order socket was closed"))
    }

    // ---- placing ------------------------------------------------------------------------------------------------------------------------------------------------------------

    override suspend fun place(order: NovigTradingClient.NewOrder): String {
        if (!ready) throw SocketFallback("the order socket is not ready (${state.name.lowercase()})")
        return placeMutex.withLock {
            // Ready is asked again once this order's turn has come: the one before it may have lost the connection.
            if (!ready) throw SocketFallback("the order socket is not ready (${state.name.lowercase()})")
            lastUsedMs = clock()
            val n = nonce.incrementAndGet()
            val wait = CompletableDeferred<JsonObject>()
            synchronized(lock) { inFlight = n to wait }
            val ws = synchronized(lock) { socket }
            val frame = buildJsonObject {
                put("nonce", n)
                putJsonObject("place") { putJsonArray("orders") { add(orderJson(order)) } }
            }.toString()
            val sentAt = clock()
            if (ws == null || !ws.send(frame)) {
                synchronized(lock) { inFlight = null }
                fellBack++
                throw SocketFallback("the order could not be sent over the socket")
            }
            val reply = try {
                withTimeoutOrNull(ackTimeoutMs) { wait.await() }
            } catch (e: CancellationException) {
                synchronized(lock) { inFlight = null }
                throw e
            } catch (e: IOException) {
                synchronized(lock) { inFlight = null }
                lost++
                throw SocketNoAnswer("the order socket dropped before it answered (${e.message ?: "no connection"})")
            } finally {
                synchronized(lock) { if (inFlight?.first == n) inFlight = null }
            }
            if (reply == null) {
                lost++
                putOnRest("no answer to an order in ${ackTimeoutMs / 1000.0} s")
                throw SocketNoAnswer("the order socket did not answer in ${ackTimeoutMs / 1000.0} s")
            }
            lastAckMs = clock() - sentAt
            answer(order, reply)
        }
    }

    private fun answer(order: NovigTradingClient.NewOrder, reply: JsonObject): String {
        val code = (reply["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (code != null) {
            val message = (reply["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val why = (reply["rejected"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.get("reason") as? JsonPrimitive }?.content
            if (code in SHAPE_CODES) {
                // Novig could not read the frame: nothing was placed, and the next one would fail the same way. The order goes by REST.
                fellBack++
                putOnRest("Novig could not read an order frame ($code${message?.let { ": $it" } ?: ""})")
                throw SocketFallback("Novig could not read the order frame ($code)")
            }
            refused++
            throw NovigApiException(statusOf(code), code, listOfNotNull(message, why).joinToString(" · ").ifBlank { null })
        }
        val placedArray = reply["placed"] as? JsonArray
        val entry = placedArray?.mapNotNull { it as? JsonObject }?.let { list -> list.firstOrNull { (it["clientId"] as? JsonPrimitive)?.content == order.clientId } ?: list.firstOrNull() }
        val id = (entry?.get("orderId") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (id == null) {
            // An answer to our request that does not say which order: it is probably placed. Never sent again; looked up by its clientId.
            lost++
            putOnRest("a placed answer without an order id")
            throw SocketNoAnswer("the order socket answered, but not in a shape that names the order")
        }
        placed++
        return id
    }

    private fun orderJson(o: NovigTradingClient.NewOrder): JsonObject = JsonObject(
        buildMap {
            put("outcomeId", JsonPrimitive(o.outcomeId))
            put("price", JsonPrimitive(NovigTradingClient.priceText(o.price)))
            put("qty", JsonPrimitive(o.qty))
            put("tif", JsonPrimitive(o.tif))
            o.ttlMs?.let { put("ttl", JsonPrimitive(it)) }
            put("clientId", JsonPrimitive(o.clientId))
        },
    )

    private fun putOnRest(why: String) {
        synchronized(lock) {
            untrustedUntilMs = clock() + UNTRUSTED_MS
            untrustedWhy = why
        }
    }

    // ---- the connection -----------------------------------------------------------------------------------------------------------------------------------------------------

    private fun send(message: JsonObject) {
        synchronized(lock) { socket }?.send(message.toString())
    }

    private fun failPending(e: IOException) {
        val p = synchronized(lock) { inFlight.also { inFlight = null } }
        p?.second?.completeExceptionally(e)
    }

    private fun current(ws: WebSocket) = synchronized(lock) { socket === ws }

    private fun subscribePrivate() {
        val n = nonce.incrementAndGet()
        synchronized(lock) { privateNonce = n }
        send(
            buildJsonObject {
                put("nonce", n)
                putJsonObject("subscribe") { putJsonArray("private") { add(JsonPrimitive("orders")) } }
            },
        )
    }

    private fun onFrame(text: String) {
        val msg = try { json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: return
        val n = (msg["nonce"] as? JsonPrimitive)?.longOrNull
        // The subaccount's orders, in the snapshot answering the subscribe and in the deltas after it; a heartbeat ahead of the last seq is a message lost: subscribe again.
        var gap = false
        (msg["orders"] as? JsonObject)?.let { if (!events.apply(it)) gap = true }
        ((msg["heartbeat"] as? JsonObject)?.get("orders") as? JsonPrimitive)?.longOrNull?.let { if (!events.heartbeat(it)) gap = true }
        if (gap) subscribePrivate()
        val code = (msg["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val pending = synchronized(lock) { inFlight }
        when {
            n != null && n == synchronized(lock) { privateNonce } -> {
                if (code != null) lastError = "the orders channel was refused ($code); results are read by request"
                else privateOk = true
            }
            n != null && pending != null && n == pending.first -> pending.second.complete(msg)
            // A frame that failed to parse or hit the throttle is answered with no nonce: with exactly one order in flight it is the order's answer, and nothing was placed.
            n == null && code != null && pending != null && code in NO_NONCE_CODES -> pending.second.complete(msg)
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current(webSocket)) return
            synchronized(lock) {
                state = State.READY
                failures = 0
                lastError = null
            }
            subscribePrivate()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (current(webSocket)) onFrame(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (current(webSocket)) onFrame(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket, "Novig closed the order socket ($code${if (reason.isNotBlank()) " $reason" else ""})", quick = true)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val status = response?.code
            val retryAfter = if (status == 429) response.header("Retry-After")?.trim()?.toLongOrNull() else null
            dropped(
                webSocket,
                when {
                    status == 401 -> "Novig refused the trading key for the order socket (401)"
                    status == 403 -> "This key can't open the order socket (403)"
                    status == 429 -> "Novig's stream throttle was empty for the order socket (429)"
                    status == 451 -> "Novig's location check refused the order socket (451)"
                    else -> "The order socket dropped (${t.message ?: t.javaClass.simpleName})"
                },
                quick = status == null || status >= 500 || status == 429,
                waitMs = retryAfter?.let { it.coerceIn(1, 30) * 1000 + 500 },
                putOnRest = status == 401 || status == 403 || status == 451,
            )
        }
    }

    private fun dropped(ws: WebSocket, why: String, quick: Boolean, waitMs: Long? = null, putOnRest: Boolean = false) {
        val wasCurrent: Boolean
        synchronized(lock) {
            wasCurrent = socket === ws
            if (wasCurrent) socket = null
        }
        if (!wasCurrent) return
        events.reset()
        failPending(IOException(why))
        if (putOnRest) putOnRest(why)
        val keepTrying = quick && enabled && clock() - lastUsedMs < idleCloseMs
        val wait = waitMs ?: minOf(reconnectMs shl minOf(failures, 5), MAX_RECONNECT_MS)
        fail(why, if (keepTrying) wait else RETRY_AFTER_FAILURE_MS)
        if (keepTrying) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(wait)
                if (enabled && clock() - lastUsedMs < idleCloseMs) warm()
            }
        }
    }

    private fun fail(why: String, retryMs: Long) {
        synchronized(lock) {
            state = State.FAILED
            lastError = why
            failedAtMs = clock()
            failures++
            retryAtMs = clock() + retryMs
        }
    }

    // ---- diagnostics -----------------------------------------------------------------------------------------------------------------------------------------------------

    fun report(): List<String> {
        val now = clock()
        return buildList {
            add(
                "order socket: " + when {
                    !enabled -> "switched off: orders go by REST"
                    now < untrustedUntilMs -> "ON HOLD, orders go by REST for ${(untrustedUntilMs - now) / 1000} s more: $untrustedWhy"
                    else -> state.name.lowercase()
                } + " · connects $connects, placed $placed, refused by Novig $refused, no answer $lost, handed to REST $fellBack" +
                    (lastAckMs?.let { " · last order answered in $it ms" } ?: ""),
            )
            lastError?.let { add("  last problem: $it") }
            add("  orders channel: " + (if (events.live) "pushed (${events.events} events, ${events.gaps} gaps)" else "not live"))
        }
    }

    companion object {
        const val ACK_TIMEOUT_MS = 4_000L
        const val IDLE_CLOSE_MS = 20 * 60_000L
        const val RECONNECT_MS = 3_000L
        const val MAX_RECONNECT_MS = 60_000L
        const val RETRY_AFTER_FAILURE_MS = 5 * 60_000L
        const val UNTRUSTED_MS = 10 * 60_000L
        private const val IDLE_CHECK_MS = 15_000L

        /** Errors that say Novig could not read the request frame: nothing was placed, and REST can take the order. */
        val SHAPE_CODES = setOf("INVALID_BODY", "INVALID_REQUEST", "INVALID_QUERY", "EMPTY_SELECTION", "STALE_NONCE")

        /** Errors Novig sends with no nonce (docs: "A frame that fails to parse or hits the throttle gets a reply with no nonce"). */
        private val NO_NONCE_CODES = setOf("INVALID_BODY", "INVALID_REQUEST", "RATE_LIMIT_EXCEEDED")

        /** The HTTP status the same error carries on REST (docs.novig.com/api/errors), which [NovigApiException] and its callers branch on. */
        fun statusOf(code: String): Int = when (code) {
            "SCOPE_INSUFFICIENT", "KYC_REQUIRED", "SIGNATURE_REJECTED" -> 403
            "MARKET_NOT_FOUND", "EVENT_NOT_FOUND", "OUTCOME_NOT_FOUND", "ORDER_NOT_FOUND", "WALLET_NOT_FOUND", "SUBACCOUNT_NOT_FOUND" -> 404
            "MARKET_CLOSED", "MARKET_INACTIVE" -> 409
            "INSUFFICIENT_BALANCE", "POSITION_LIMIT_EXCEEDED", "OPEN_ORDER_LIMIT_EXCEEDED", "WAGER_LIMIT_EXCEEDED" -> 422
            "SYSTEM_LOCKED", "ACCOUNT_LOCKED", "LEAGUE_LOCKED", "COMPETITOR_LOCKED", "ACCOUNT_EXCLUDED", "SELF_EXCLUDED", "EMPLOYEE_ACCOUNT", "EVENT_LOCKED", "MARKET_LOCKED" -> 423
            "RATE_LIMIT_EXCEEDED" -> 429
            "ANONYMIZED_NETWORK", "RESTRICTED_NETWORK_REGION", "GEOLOCATION_NOT_FOUND", "GEOLOCATION_FAILED", "GEOLOCATION_OUT_OF_BOUNDARY", "GEOLOCATION_BLOCKED_NETWORK",
            "GEOLOCATION_BLOCKED_SOFTWARE", "INVALID_GEOLOCATION_REGION", "RESTRICTED_GEOLOCATION_REGION", "GEOLOCATION_EXPIRED",
            -> 451
            "GEOLOCATION_SCREENING_UNAVAILABLE" -> 503
            "INTERNAL_ERROR" -> 500
            else -> 400
        }
    }
}
