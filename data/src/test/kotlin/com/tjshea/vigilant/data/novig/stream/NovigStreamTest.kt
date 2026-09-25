package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.BidLevel
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
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** A mock Novig websocket speaking the documented protocol (docs.novig.com/api/streaming). */
class NovigStreamTest {

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val received = CopyOnWriteArrayList<String>()
    private var upgradeHeaders: okhttp3.Headers? = null

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun novigSide() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {}
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
            val msg = Json.parseToJsonElement(text).jsonObject
            if (msg["subscribe"] != null) {
                webSocket.send(
                    """{"ts":1,"nonce":${msg["nonce"]},"subscribed":{},"snapshot":{"m1":{"eventId":"e1",
                       "book":{"seq":10,"orders":{"A":[{"order":"o1","price":"0.600","qty":100}],"B":[{"order":"o2","price":"0.380","qty":50}]}},
                       "lifecycle":{"seq":1,"status":"OPEN"}}}}""",
                )
                webSocket.send("""{"ts":2,"delta":{"m1":{"eventId":"e1","book":{"seq":11,"deltas":[{"kind":"add","order":"o3","outcome":"B","price":"0.390","qty":20}]}}}}""")
            }
        }
    }

    private suspend fun until(check: () -> Boolean) = withTimeout(5_000) { while (!check()) delay(20) }

    @Test
    fun `connects signed, subscribes the scanner's events, and keeps books live`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val vault = MemoryVault().apply { generate("read") }
        val signer = NovigSignedClient(OkHttpClient(), Json, vault.signer("read", "read-key-1"), server.url("").toString().trimEnd('/'))
        val stream = NovigStream(OkHttpClient(), signer, scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"))

        stream.setEvents(setOf("e1"))
        stream.connect()
        until { stream.book("m1")?.seq == 11L }

        val book = stream.book("m1")!!
        assertEquals(listOf(BidLevel(390, 20), BidLevel(380, 50)), book.bidsByOutcome["B"])
        assertTrue(stream.state.value is StreamState.Live)

        val upgrade = server.takeRequest()
        assertEquals("/v3/ws", upgrade.path)
        assertEquals("read-key-1", upgrade.getHeader("Novig-Key-Id"))
        assertNotNull(upgrade.getHeader("Novig-Signature"))
        val sub = Json.parseToJsonElement(received.first()).jsonObject
        assertEquals("""{"e1":"book"}""", sub["subscribe"]!!.jsonObject["events"].toString())

        stream.close()
        assertTrue(stream.state.value is StreamState.Off)
        assertEquals(null, stream.book("m1"))
    }

    @Test
    fun `a refused upgrade reports why in plain English`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK"}"""))
        val vault = MemoryVault().apply { generate("read") }
        val signer = NovigSignedClient(OkHttpClient(), Json, vault.signer("read", "k"), server.url("").toString().trimEnd('/'))
        val stream = NovigStream(OkHttpClient(), signer, scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"))
        stream.connect()
        until { stream.state.value is StreamState.Failed }
        assertTrue((stream.state.value as StreamState.Failed).message.contains("VPN"))
    }
}
