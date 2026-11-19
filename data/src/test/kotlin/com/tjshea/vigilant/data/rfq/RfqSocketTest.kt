package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** The maker's socket against a mock of Novig's (docs `api/rfq/socket`): registers, connects signed, subscribes, hands frames to the maker, and in SHADOW sends no quote. */
class RfqSocketTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"
    private val received = CopyOnWriteArrayList<String>()
    private val now get() = System.currentTimeMillis()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun client() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid-1", pem), server.url("").toString().trimEnd('/')), json)

    private fun legs() = listOf(
        LegFair("a", "e1", "A", 0.5, 5, now - 60_000L, now + 3 * 3_600_000L, false, "MONEY"),
        LegFair("b", "e2", "B", 0.4, 5, now - 60_000L, now + 3 * 3_600_000L, false, "SPREAD"),
    )

    private fun novigSide(rfq: Boolean) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {}
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
            val msg = json.parseToJsonElement(text).jsonObject
            if (msg["subscribe"] != null && rfq) {
                webSocket.send("""{"nonce":${msg["nonce"]!!.jsonPrimitive.content},"subscribed":["rfq"]}""")
                webSocket.send("""{"ts":${now},"type":"rfq_created","rfqId":"r-1","outcomeIds":["a","b"],"expiresTs":${now + 3_000L},"minWager":"10"}""")
                webSocket.send("""{"ts":${now},"type":"executed","offset":1,"rfqId":"r-1","wager":"20","price":"0.26"}""")
            }
        }
    }

    private fun run(mode: RfqConfig.Mode, body: suspend (rows: List<RfqRow>, socket: RfqSocket) -> Unit) = runBlocking {
        val rows = CopyOnWriteArrayList<RfqRow>()
        val cfg = RfqConfig(mode = mode, maxLegCollateral = 0.0)
        lateinit var socket: RfqSocket
        val maker = RfqMaker({ cfg }, { id -> legs().firstOrNull { it.outcomeId == id } }, { 0.0 }, { socket.send(it) }, { rows += it }, cash = { 1_000.0 })
        socket = RfqSocket(OkHttpClient(), { client() }, maker, { cfg }, scope)
        server.enqueue(MockResponse().setBody("""{"makerId":"mk-1"}"""))                     // POST /rfq/v3/maker
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(true)))                  // GET /rfq/v3/ws
        socket.start()
        withTimeout(15_000) { while (rows.none { it.kind == "EXECUTED" }) delay(25) }
        body(rows, socket)
    }

    @Test fun shadowRegistersConnectsSubscribesWithoutQuotesAndSendsNothingBack() = run(RfqConfig.Mode.SHADOW) { rows, socket ->
        val register = server.takeRequest()
        assertEquals("POST", register.method); assertEquals("/rfq/v3/maker", register.requestUrl!!.encodedPath)
        assertNotNull(register.getHeader("Novig-Key-Id"))
        val upgrade = server.takeRequest()
        assertEquals("/rfq/v3/ws", upgrade.requestUrl!!.encodedPath); assertNotNull(upgrade.getHeader("Novig-Signature"))
        assertEquals("mk-1", socket.makerId)
        val sub = json.parseToJsonElement(received.first()).jsonObject["subscribe"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("rfq", "executions"), sub)
        assertTrue("the RFQ was priced and journaled", rows.any { it.kind == "CREATED" && it.data.contains("\"decision\":\"quote\"") })
        assertTrue("and what the market paid was compared", rows.any { it.kind == "EXECUTED" && it.data.contains("\"wouldWin\":true") })
        delay(200)
        assertEquals("only the subscribe was ever sent: no quote in SHADOW", 1, received.size)
        socket.stop()
    }

    @Test fun liveSubscribesToQuotesAndSendsAQuoteForWhatItCanPrice() = run(RfqConfig.Mode.LIVE) { rows, socket ->
        val sub = json.parseToJsonElement(received.first()).jsonObject["subscribe"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("without the quotes channel a won quote is never seen", "quotes" in sub)
        withTimeout(5_000) { while (received.none { it.contains("\"quote\"") }) delay(25) }
        val q = json.parseToJsonElement(received.first { it.contains("\"quote\"") }).jsonObject["quote"]!!.jsonObject
        assertEquals("r-1", q["rfqId"]!!.jsonPrimitive.content); assertEquals("fair 0.20: (0.20 + 0.06)/1.06 = 0.24528 rounded up to the thousandth", "0.246", q["price"]!!.jsonPrimitive.content)
        assertTrue(rows.any { it.kind == "QUOTE_SENT" })
        socket.stop()
    }
}
