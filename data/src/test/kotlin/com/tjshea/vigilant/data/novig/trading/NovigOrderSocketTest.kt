package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.signing.MemoryVault
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.stream.OrderEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orders over the websocket (NOVIG_API.md §27) against a mock speaking the OpenAPI's shapes: `place` answered by `placed: [{orderId, clientId}]` or by an error with the nonce; the private `orders` channel;
 * and the rules that keep money safe: an order that was sent and not answered is never sent again, a refusal is Novig's own, a frame Novig could not read goes by REST once.
 */
class NovigOrderSocketTest {

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val received = CopyOnWriteArrayList<JsonObject>()
    private val rest = CopyOnWriteArrayList<RecordedRequest>()
    private val upgrades = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile private var socket: WebSocket? = null

    /** What the mock does with a `place`: null = the normal ack. */
    @Volatile private var onPlace: ((WebSocket, Long, JsonObject) -> Unit)? = null
    @Volatile private var upgradeStatus: MockResponse? = null

    private val serverSide = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { socket = webSocket }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            received += msg
            val nonce = msg["nonce"]!!.jsonPrimitive.content.toLong()
            if (msg["subscribe"]?.jsonObject?.get("private") != null) {
                webSocket.send("""{"ts":1,"nonce":$nonce,"subscribed":{"markets":{},"events":{},"private":["orders"]},"snapshot":{},"orders":{"seq":10,"open":[]}}""")
                return
            }
            val place = msg["place"]?.jsonObject ?: return
            val custom = onPlace
            if (custom != null) { custom(webSocket, nonce, place); return }
            val order = place["orders"]!!.jsonArray.first().jsonObject
            webSocket.send("""{"nonce":$nonce,"placed":[{"orderId":"ord-socket","clientId":"${order["clientId"]!!.jsonPrimitive.content}"}]}""")
        }
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Upgrade")?.equals("websocket", ignoreCase = true) == true) {
                    upgrades += request
                    return upgradeStatus ?: MockResponse().withWebSocketUpgrade(serverSide)
                }
                rest += request
                return when {
                    request.method == "POST" && request.path == "/v3/orders" -> MockResponse().setResponseCode(201).setBody("""{"orderId":"ord-rest","clientId":"c"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun signer(): NovigSignedClient {
        val vault = MemoryVault().apply { generate("trading") }
        return NovigSignedClient(OkHttpClient(), json, vault.signer("trading", "trading-key-1"), server.url("").toString().trimEnd('/'))
    }

    private fun orderSocket(ackTimeoutMs: Long = 2_000, reconnectMs: Long = 100): NovigOrderSocket =
        NovigOrderSocket(OkHttpClient(), signer(), scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"), ackTimeoutMs = ackTimeoutMs, reconnectMs = reconnectMs)

    private fun trading(s: OrderSocket?) = NovigTradingClient(signer(), json, orderSocket = { s })

    private suspend fun until(timeoutMs: Long = 10_000, check: () -> Boolean) = withTimeout(timeoutMs) { while (!check()) delay(20) }

    private val outcome = "11111111-1111-1111-1111-111111111111"
    private val client = "22222222-2222-2222-2222-222222222222"
    private fun order(tif: String = "IOC", ttl: Long? = null) = NovigTradingClient.NewOrder(outcome, 0.465, 400, tif, client, ttl)

    private fun placeFrames() = received.filter { it["place"] != null }

    @Test
    fun `an IOC order goes over the socket as a place frame, is answered with its id, and no REST request is made`() = runBlocking {
        val s = orderSocket()
        s.warm()
        until { s.ready }
        val id = trading(s).placeOrder(outcome, 0.465, 400, "IOC", client)
        assertEquals("ord-socket", id)
        assertTrue("no REST order: ${rest.map { it.path }}", rest.none { it.path == "/v3/orders" })
        val frame = placeFrames().single()
        val o = frame["place"]!!.jsonObject["orders"]!!.jsonArray.single().jsonObject
        assertEquals(outcome, o["outcomeId"]!!.jsonPrimitive.content)
        assertEquals("0.465", o["price"]!!.jsonPrimitive.content)
        assertEquals("400", o["qty"]!!.jsonPrimitive.content)
        assertEquals("IOC", o["tif"]!!.jsonPrimitive.content)
        assertEquals(client, o["clientId"]!!.jsonPrimitive.content)
        assertEquals("the trading key signed the upgrade", "trading-key-1", upgrades.single().getHeader("Novig-Key-Id"))
        assertEquals(1, s.placed)
        assertNotNull(s.lastAckMs)
        s.close()
    }

    @Test
    fun `an order that finds the socket not ready goes by REST at once and wakes it for the next`() = runBlocking {
        val s = orderSocket()
        assertEquals("ord-rest", trading(s).placeOrder(outcome, 0.465, 400, "IOC", client))
        assertEquals(1, rest.count { it.path == "/v3/orders" })
        until { s.ready }
        assertEquals("ord-socket", trading(s).placeOrder(outcome, 0.465, 400, "IOC", client))
        s.close()
    }

    @Test
    fun `a maker order and a switched-off socket always go by REST`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.ready }
        assertEquals("ord-rest", trading(s).placeOrder(outcome, 0.45, 100, "PO", client, ttlMs = 60_000))
        s.enabled = false
        assertFalse(s.ready)
        assertEquals("ord-rest", trading(s).placeOrder(outcome, 0.465, 400, "IOC", client))
        assertEquals(2, rest.count { it.path == "/v3/orders" })
        assertTrue(placeFrames().isEmpty())
    }

    @Test
    fun `Novig's refusals come back as its own errors with the status REST would have carried`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.ready }
        for ((code, status) in listOf("ORDER_TOO_SMALL" to 400, "MARKET_LOCKED" to 423, "NOT_LIVE_TRADABLE" to 400, "MARKET_CLOSED" to 409, "INSUFFICIENT_BALANCE" to 422, "GEOLOCATION_EXPIRED" to 451, "SCOPE_INSUFFICIENT" to 403)) {
            onPlace = { ws, nonce, _ -> ws.send("""{"nonce":$nonce,"code":"$code","message":"m $code"}""") }
            try {
                trading(s).placeOrder(outcome, 0.465, 400, "IOC", client)
                fail("$code should be refused")
            } catch (e: NovigApiException) {
                assertEquals(code, e.code)
                assertEquals(code, status, e.status)
            }
        }
        assertTrue("a refusal does not put the socket on REST", s.ready)
        assertEquals(7, s.refused)
        assertTrue("a lock on one market is about that bet", NovigApiException(NovigOrderSocket.statusOf("MARKET_LOCKED"), "MARKET_LOCKED", null).betLocked)
        assertTrue(rest.none { it.path == "/v3/orders" })
        s.close()
    }

    @Test
    fun `a frame Novig could not read is placed by REST once and the socket is put on REST`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.ready }
        onPlace = { ws, nonce, _ -> ws.send("""{"nonce":$nonce,"code":"INVALID_BODY","message":"place: unknown field"}""") }
        assertEquals("ord-rest", trading(s).placeOrder(outcome, 0.465, 400, "IOC", client))
        assertEquals(1, rest.count { it.path == "/v3/orders" })
        assertFalse("on REST for a while", s.ready)
        assertTrue(s.report().first().contains("ON HOLD"))
        assertTrue(s.report().first().contains("INVALID_BODY"))
        assertEquals("ord-rest", trading(s).placeOrder(outcome, 0.465, 400, "IOC", client))
        assertEquals(1, placeFrames().size)
        s.close()
    }

    @Test
    fun `an order that gets no answer is never sent again, comes back as a lost answer, and the socket goes on hold`() = runBlocking {
        val s = orderSocket(ackTimeoutMs = 300)
        s.warm(); until { s.ready }
        onPlace = { _, _, _ -> /* the mock hears it and says nothing */ }
        try {
            trading(s).placeOrder(outcome, 0.465, 400, "IOC", client)
            fail("no answer")
        } catch (e: SocketNoAnswer) {
            assertTrue(e is IOException)
        }
        assertEquals("never replayed by REST", 0, rest.count { it.path == "/v3/orders" })
        assertEquals(1, placeFrames().size)
        assertFalse(s.ready)
        assertEquals(1, s.lost)
        s.close()
    }

    @Test
    fun `an answer that does not name the order is a lost answer, not a placed one`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.ready }
        onPlace = { ws, nonce, _ -> ws.send("""{"nonce":$nonce,"placed":[{}]}""") }
        try {
            trading(s).placeOrder(outcome, 0.465, 400, "IOC", client)
            fail("unreadable")
        } catch (e: SocketNoAnswer) {
            // looked up by clientId by the placer
        }
        assertEquals(0, rest.count { it.path == "/v3/orders" })
        s.close()
    }

    @Test
    fun `a connection that drops while an order waits is a lost answer`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.ready }
        onPlace = { ws, _, _ -> ws.close(1011, "gone") }
        try {
            trading(s).placeOrder(outcome, 0.465, 400, "IOC", client)
            fail("dropped")
        } catch (e: IOException) {
            assertTrue(e.message!!, e is SocketNoAnswer || e.message!!.contains("dropped") || e.message!!.contains("closed"))
        }
        assertEquals(0, rest.count { it.path == "/v3/orders" })
        s.close()
    }

    @Test
    fun `a throttled frame answered with no nonce is the order's refusal`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.ready }
        onPlace = { ws, _, _ -> ws.send("""{"code":"RATE_LIMIT_EXCEEDED","message":"slow down"}""") }
        try {
            trading(s).placeOrder(outcome, 0.465, 400, "IOC", client)
            fail("throttled")
        } catch (e: NovigApiException) {
            assertEquals(429, e.status)
        }
        s.close()
    }

    @Test
    fun `the private orders channel is subscribed on the connection and an order's end is pushed over it`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.events.live }
        assertEquals(1, received.count { it["subscribe"]?.jsonObject?.get("private") != null })
        val end = async { s.events.awaitEnd("ord-socket", 5_000) }
        delay(50)
        socket!!.send("""{"ts":2,"delta":{},"orders":{"seq":11,"deltas":[{"kind":"fill","orderId":"ord-socket","outcomeId":"$outcome","price":"0.4625","qty":400,"remaining":0}]}}""")
        assertEquals(OrderEvents.End.FILLED, end.await()!!.end)
        s.close()
    }

    @Test
    fun `an upgrade the key is refused on leaves orders on REST and says why`() = runBlocking {
        upgradeStatus = MockResponse().setResponseCode(403).setBody("""{"code":"SCOPE_INSUFFICIENT","message":"api key scope is insufficient for this route"}""")
        val s = orderSocket()
        s.warm()
        until { s.state == NovigOrderSocket.State.FAILED }
        assertFalse(s.ready)
        assertEquals("ord-rest", trading(s).placeOrder(outcome, 0.465, 400, "IOC", client))
        assertTrue(s.report().joinToString("\n"), s.report().first().contains("ON HOLD") && s.report().joinToString().contains("403"))
    }

    @Test
    fun `a dropped connection is reconnected by itself while orders are likely`() = runBlocking {
        val s = orderSocket()
        s.warm(); until { s.ready }
        socket!!.close(1011, "restart")
        until { upgrades.size >= 2 && s.ready }
        assertEquals("ord-socket", trading(s).placeOrder(outcome, 0.465, 400, "IOC", client))
        s.close()
    }

    @Test
    fun `the status of every error code is the one REST carries`() {
        val table = mapOf(
            "ORDER_TOO_LARGE" to 400, "INVALID_PRICE" to 400, "PRICE_BAND_VIOLATION" to 400, "EVENT_NOT_TRADABLE" to 400, "KYC_REQUIRED" to 403, "OUTCOME_NOT_FOUND" to 404,
            "MARKET_INACTIVE" to 409, "POSITION_LIMIT_EXCEEDED" to 422, "WAGER_LIMIT_EXCEEDED" to 422, "SELF_EXCLUDED" to 423, "EVENT_LOCKED" to 423, "ANONYMIZED_NETWORK" to 451,
            "RESTRICTED_NETWORK_REGION" to 451, "GEOLOCATION_OUT_OF_BOUNDARY" to 451, "RATE_LIMIT_EXCEEDED" to 429, "GEOLOCATION_SCREENING_UNAVAILABLE" to 503, "INTERNAL_ERROR" to 500,
        )
        for ((code, status) in table) assertEquals(code, status, NovigOrderSocket.statusOf(code))
        assertTrue(NovigApiException(451, "ANONYMIZED_NETWORK", null).networkRefusal)
        assertTrue(NovigApiException(NovigOrderSocket.statusOf("NOT_LIVE_TRADABLE"), "NOT_LIVE_TRADABLE", null).betRefused)
    }
}
