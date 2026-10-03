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
    fun `a listed order's expiry is read`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"orderId":"o-1","marketId":"m","outcomeId":"a","price":"0.485","qty":100,"remaining":100,"tif":"PO","status":"OPEN","createdTs":1,"expiresTs":1800001}""",
            ),
        )
        assertEquals(1_800_001L, client().order("o-1")!!.expiresTs)
    }
}
