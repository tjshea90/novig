package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

/** The make-order routes on the wire (NOVIG_API.md §17): a post-only bid with its `ttl`, one cancel, a cancel of a market's orders, an order's expiry. */
class MakerOrdersClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid-1", pem), server.url("").toString().trimEnd('/')), json)

    @Test
    fun `a post-only bid carries its ttl in milliseconds, and an order without one sends none`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"orderId":"o-1"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"orderId":"o-2"}"""))
        val id = NovigTradingClient.newClientId()
        assertEquals("o-1", client().placeOrder("out-1", 0.485, 1_030, "PO", id, ttlMs = 1_800_000))
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("PO", body["tif"]!!.jsonPrimitive.content)
        assertEquals("0.485", body["price"]!!.jsonPrimitive.content)
        assertEquals(1_800_000L, body["ttl"]!!.jsonPrimitive.content.toLong())
        assertEquals(id, body["clientId"]!!.jsonPrimitive.content)
        client().placeOrder("out-1", 0.485, 1_030, "IOC", NovigTradingClient.newClientId())
        assertFalse("ttl" in json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject)
    }

    @Test
    fun `a ttl is refused before sending on anything but PO and GTT, and GTT needs one`() = runBlocking {
        val c = client()
        assertTrue(runCatching { c.placeOrder("o", 0.5, 10, "IOC", NovigTradingClient.newClientId(), ttlMs = 60_000) }.isFailure)
        assertTrue(runCatching { c.placeOrder("o", 0.5, 10, "GTT", NovigTradingClient.newClientId()) }.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancels - one order, a market's orders, and an order Novig no longer has`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"orderId":"o-1","status":"OPEN"}"""))
        server.enqueue(MockResponse().setBody("""{"canceled":3}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"ORDER_NOT_FOUND","message":"order not found"}"""))
        server.enqueue(MockResponse().setBody("""{"canceled":0}"""))
        val c = client()
        assertEquals("OPEN", c.cancelOrder("o-1"))
        server.takeRequest().let { assertEquals("DELETE", it.method); assertEquals("/v3/orders/o-1", it.path) }
        assertEquals(3, c.cancelOrders(marketId = "m-1"))
        server.takeRequest().let { assertEquals("DELETE", it.method); assertEquals("/v3/orders?market=m-1", it.path) }
        assertNull(c.cancelOrder("gone"))
        server.takeRequest()
        assertEquals(0, c.cancelOrders())
        assertEquals("/v3/orders", server.takeRequest().path)
    }

    @Test
    fun `a batch of bids is one signed POST, answered by client id, and a batch Novig refuses places none`() = runBlocking {
        val a = NovigTradingClient.newClientId()
        val b = NovigTradingClient.newClientId()
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"accepted":[{"orderId":"o-1","clientId":"$b"},{"orderId":"o-2","clientId":"$a"}]}"""))
        val placed = client().placeOrders(
            listOf(
                NovigTradingClient.NewOrder("out-1", 0.485, 1_030, "PO", a, ttlMs = 1_800_000),
                NovigTradingClient.NewOrder("out-2", 0.45, 200, "PO", b, ttlMs = 600_000),
            ),
        )
        // The answer is read by client id, not by position.
        assertEquals(mapOf(a to "o-2", b to "o-1"), placed)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v3/orders/batch", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
        val sent = json.parseToJsonElement(req.body.readUtf8()).jsonObject["orders"]!!.let { it as kotlinx.serialization.json.JsonArray }
        assertEquals(2, sent.size)
        assertEquals("0.485", sent[0].jsonObject["price"]!!.jsonPrimitive.content)
        assertEquals(1_800_000L, sent[0].jsonObject["ttl"]!!.jsonPrimitive.content.toLong())
        assertEquals(b, sent[1].jsonObject["clientId"]!!.jsonPrimitive.content)
        // A reply that echoes no client ids is read in the order sent.
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"accepted":[{"orderId":"p-1"},{"orderId":"p-2"}]}"""))
        assertEquals(
            mapOf(a to "p-1", b to "p-2"),
            client().placeOrders(listOf(NovigTradingClient.NewOrder("out-1", 0.5, 10, "PO", a, 60_000), NovigTradingClient.NewOrder("out-2", 0.5, 10, "PO", b, 60_000))),
        )
        server.takeRequest()
        // All or nothing: a refusal is Novig's own code, and the caller knows none of them is resting.
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"code":"INSUFFICIENT_FUNDS","message":"not enough balance","rejected":[{"index":1}]}"""))
        val refused = runCatching { client().placeOrders(listOf(NovigTradingClient.NewOrder("out-1", 0.5, 10, "PO", a, 60_000))) }.exceptionOrNull()
        assertTrue(refused.toString(), refused is com.tjshea.vigilant.data.novig.signing.NovigApiException && (refused as com.tjshea.vigilant.data.novig.signing.NovigApiException).status == 422)
    }

    /** v0.70.4: Novig's batch reply was unreadable 3 of 3 times in the v0.70.1 file and nothing recorded its shape. */
    @Test
    fun `a batch reply the app cannot read is reported with its shape - keys and kinds of value, never a value`() = runBlocking {
        val a = NovigTradingClient.newClientId()
        val order = NovigTradingClient.NewOrder("out-1", 0.5, 10, "PO", a, 60_000)
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"accepted":"SECRET-ONE","note":"x"}"""))
        val e1 = runCatching { client().placeOrders(listOf(order)) }.exceptionOrNull()
        assertTrue(e1.toString(), e1 is NovigTradingClient.BatchReplyUnreadable)
        assertEquals("{accepted:string,note:string}", (e1 as NovigTradingClient.BatchReplyUnreadable).shape)
        assertFalse("no value leaves the reply", e1.message!!.contains("SECRET-ONE"))
        assertTrue("still a SerializationException, so every caller that handles one handles this", e1 is kotlinx.serialization.SerializationException)
        server.takeRequest()
        // A list of the right kind whose items are not: the first item's shape and the count.
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"accepted":[{"orderId":{"id":"abc"},"clientId":"$a"},{"orderId":{"id":"def"}}]}"""))
        val e2 = runCatching { client().placeOrders(listOf(order)) }.exceptionOrNull() as NovigTradingClient.BatchReplyUnreadable
        assertEquals("{accepted:[{clientId:string,orderId:{id:string}}x2]}", e2.shape)
        server.takeRequest()
        // An empty body, and a page that is not JSON, say so without quoting it.
        server.enqueue(MockResponse().setResponseCode(201).setBody(""))
        assertEquals("an empty body (0 characters)", (runCatching { client().placeOrders(listOf(order)) }.exceptionOrNull() as NovigTradingClient.BatchReplyUnreadable).shape)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(201).setBody("<html>Forbidden</html>"))
        val e4 = runCatching { client().placeOrders(listOf(order)) }.exceptionOrNull() as NovigTradingClient.BatchReplyUnreadable
        assertEquals("not JSON (22 characters, starting with '<' (HTML))", e4.shape)
        assertFalse(e4.message!!.contains("Forbidden"))
    }

    @Test
    fun `a batch is refused before sending when empty, over 256, or built wrongly`() = runBlocking {
        val c = client()
        val ok = NovigTradingClient.NewOrder("o", 0.5, 10, "PO", NovigTradingClient.newClientId(), 60_000)
        assertTrue(runCatching { c.placeOrders(emptyList()) }.isFailure)
        assertTrue(runCatching { c.placeOrders(List(257) { ok.copy(clientId = NovigTradingClient.newClientId()) }) }.isFailure)
        assertTrue(runCatching { c.placeOrders(listOf(ok.copy(clientId = "not-a-uuid"))) }.isFailure)
        assertTrue(runCatching { c.placeOrders(listOf(ok.copy(tif = "IOC"))) }.isFailure)
        assertTrue(runCatching { c.cancelOrdersBatch(emptyList()) }.isFailure)
        assertTrue(runCatching { c.cancelOrdersBatch(List(257) { "o$it" }) }.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a batch cancel sends the ids in a DELETE body and reads both the 200 and the 207`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"canceled":["o-1","o-2"],"notCanceled":[]}"""))
        server.enqueue(MockResponse().setResponseCode(207).setBody("""{"canceled":["o-1"],"notCanceled":[{"orderId":"o-2","reason":"FILLED"},{"orderId":"o-3","reason":"NOT_FOUND"}]}"""))
        val c = client()
        assertEquals(NovigTradingClient.BatchCancel(setOf("o-1", "o-2"), emptyMap()), c.cancelOrdersBatch(listOf("o-1", "o-2")))
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/v3/orders/batch", req.path)
        assertEquals(listOf("o-1", "o-2"), json.parseToJsonElement(req.body.readUtf8()).jsonObject["orderIds"]!!.let { it as kotlinx.serialization.json.JsonArray }.map { it.jsonPrimitive.content })
        assertEquals(
            NovigTradingClient.BatchCancel(setOf("o-1"), mapOf("o-2" to "FILLED", "o-3" to "NOT_FOUND")),
            c.cancelOrdersBatch(listOf("o-1", "o-2", "o-3")),
        )
    }

    @Test
    fun `a listed order's expiry is read`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"orderId":"o-1","marketId":"m","outcomeId":"a","price":"0.485","qty":100,"remaining":100,"tif":"PO","status":"OPEN","createdTs":1,"expiresTs":1800001}""",
            ),
        )
        assertEquals(1_800_001L, client().order("o-1")!!.expiresTs)
    }
}
