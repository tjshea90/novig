package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.signing.MemoryVault
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** The private `orders` stream on the app's one connection (docs.novig.com/api/streaming/private): subscribed once a connection, an order's end pushed, a gap or a refusal falling back to REST. */
class NovigStreamOrdersTest {

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val received = CopyOnWriteArrayList<JsonObject>()
    @Volatile private var socket: WebSocket? = null

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun privateSubscribes() = received.filter { it["subscribe"]?.jsonObject?.get("private") != null }

    private fun novigSide(refusePrivate: Boolean = false, snapshotSeq: Int = 10) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { socket = webSocket }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            received += msg
            val nonce = msg["nonce"]!!.jsonPrimitive.content.toLong()
            val sub = msg["subscribe"]?.jsonObject ?: return
            if (sub["private"] != null) {
                if (refusePrivate) webSocket.send("""{"nonce":$nonce,"code":"SCOPE_INSUFFICIENT","message":"orders need a trading key"}""")
                else webSocket.send("""{"ts":1,"nonce":$nonce,"subscribed":{"markets":{},"events":{},"private":["orders"]},"snapshot":{},"orders":{"seq":$snapshotSeq,"open":[]}}""")
                return
            }
            val markets = sub["markets"]!!.jsonObject.keys
            webSocket.send("""{"ts":1,"nonce":$nonce,"subscribed":{},"snapshot":{${markets.joinToString(",") { """"$it":{"eventId":"e","book":{"seq":1,"orders":{}}}""" }}}}""")
        }
    }

    private suspend fun until(check: () -> Boolean) = withTimeout(10_000) { while (!check()) delay(20) }

    private fun stream(): NovigStream {
        val vault = MemoryVault().apply { generate("read") }
        val signer = NovigSignedClient(OkHttpClient(), Json, vault.signer("read", "k"), server.url("").toString().trimEnd('/'))
        return NovigStream(OkHttpClient(), signer, scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"), refillPerSec = 4_000.0)
    }

    @Test
    fun `with order results on the private orders channel is subscribed once a connection and an order's end is pushed`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.configureFeed(bbo = false, keepOpen = false, orders = true)
        s.watch(listOf("m1"))
        until { s.orderEvents.live }
        assertEquals(1, privateSubscribes().size)
        assertEquals(listOf("orders"), privateSubscribes().single()["subscribe"]!!.jsonObject["private"]!!.jsonArray.map { it.jsonPrimitive.content })
        // a fill is pushed while the placer waits
        val end = kotlinx.coroutines.async { s.orderEvents.awaitEnd("o1", 5_000) }
        delay(50)
        socket!!.send("""{"ts":2,"delta":{},"orders":{"seq":11,"deltas":[{"kind":"fill","orderId":"o1","outcomeId":"A","price":"0.46","qty":5,"remaining":0}]}}""")
        assertEquals(OrderEvents.End.FILLED, end.await()!!.end)
        assertTrue(s.feedReport().any { it.startsWith("orders: pushed (1 events heard") })
        s.close()
    }

    @Test
    fun `with order results off nothing private is asked for`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.watch(listOf("m1"))
        until { s.live(listOf("m1")).size == 1 }
        delay(150)
        assertTrue(privateSubscribes().isEmpty())
        assertFalse(s.orderEvents.live)
        assertTrue(s.feedReport().any { it.startsWith("orders: pushed order results are switched off") })
        s.close()
    }

    @Test
    fun `a heartbeat ahead of the last seq asks for the orders again`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(snapshotSeq = 10)))
        val s = stream()
        s.configureFeed(bbo = false, keepOpen = false, orders = true)
        s.watch(listOf("m1"))
        until { s.orderEvents.live }
        socket!!.send("""{"heartbeat":{"orders":14,"ts":3}}""")
        until { privateSubscribes().size == 2 }
        until { s.orderEvents.live }
        assertEquals(1, s.orderEvents.gaps)
        s.close()
    }

    @Test
    fun `a refusal of the private stream is said, and orders are read by request`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(refusePrivate = true)))
        val s = stream()
        s.configureFeed(bbo = false, keepOpen = false, orders = true)
        s.watch(listOf("m1"))
        until { s.feedReport().any { it.startsWith("orders: refused by Novig") } }
        assertFalse(s.orderEvents.live)
        assertNull(s.orderEvents.awaitEnd("o1", 100))
        // the markets are unaffected
        until { s.live(listOf("m1")).size == 1 }
        s.close()
    }

    @Test
    fun `switching order results off drops the private channel`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.configureFeed(bbo = false, keepOpen = false, orders = true)
        s.watch(listOf("m1"))
        until { s.orderEvents.live }
        s.configureFeed(bbo = false, keepOpen = false, orders = false)
        until { received.any { it["unsubscribe"]?.jsonArray?.any { u -> u.jsonPrimitive.content == "PRIVATE" } == true } }
        assertFalse(s.orderEvents.live)
        assertNotNull(s)
        s.close()
    }
}
