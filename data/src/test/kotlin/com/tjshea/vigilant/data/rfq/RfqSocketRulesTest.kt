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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * MK13: the maker's socket against a mock of Novig's, for the rules of docs.novig.com/api/rfq/socket and api/errors: a refused upgrade (429 TOO_MANY_SOCKETS, 451) or a close for a revoked key is NOT
 * retried at once (the old loop retried every second, every upgrade costing 32 stream tokens), a second socket on the same key subscribes to `quotes` only and only in LIVE, and a socket open in SHADOW
 * is one socket.
 */
class RfqSocketRulesTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"
    private val subscribes = CopyOnWriteArrayList<List<String>>()
    private val upgrades = AtomicInteger(); private val registers = AtomicInteger()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun client() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid-1", pem), server.url("").toString().trimEnd('/')), json)

    private fun listener(closeWith: Pair<Int, String>? = null) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {}
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = json.parseToJsonElement(text).jsonObject
            msg["subscribe"]?.let { sub ->
                subscribes += sub.jsonArray.map { it.jsonPrimitive.content }
                webSocket.send("""{"nonce":${msg["nonce"]!!.jsonPrimitive.content},"subscribed":[]}""")
                if (closeWith != null) webSocket.close(closeWith.first, closeWith.second)
            }
        }
    }

    private fun serve(upgrade: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/rfq/v3/maker" -> { registers.incrementAndGet(); MockResponse().setBody("""{"makerId":"mk-1"}""") }
                "/rfq/v3/ws" -> { upgrades.incrementAndGet(); upgrade(request) }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private class Built(val pricing: RfqSocket, val lastLook: RfqSocket)

    private fun build(mode: RfqConfig.Mode): Built {
        val cfg = RfqConfig(mode = mode)
        val maker = RfqMaker({ cfg }, { null }, { 0.0 }, { true }, { })
        lateinit var pricing: RfqSocket
        pricing = RfqSocket(OkHttpClient(), { client() }, maker, { cfg }, scope)
        val last = RfqSocket(OkHttpClient(), { client() }, maker, { cfg }, scope, channels = RfqSocket.LAST_LOOK, name = "RFQ last-look socket", ready = { pricing.makerId != null })
        return Built(pricing, last)
    }

    @Test fun tooManySocketsIsNotRetriedAtOnce() = runBlocking {
        serve { MockResponse().setResponseCode(429).setHeader("Content-Type", "application/json").setBody("""{"code":"TOO_MANY_SOCKETS","message":"The key already has 10 sockets open."}""") }
        val s = build(RfqConfig.Mode.SHADOW)
        s.pricing.start()
        withTimeout(15_000) { while (!s.pricing.lastNote.contains("TOO_MANY_SOCKETS")) delay(25) }
        val first = upgrades.get()
        delay(2_500)
        assertEquals("no second upgrade within 2.5 s: the old loop retried every second", first, upgrades.get())
        assertTrue(s.pricing.nextAttemptMs - System.currentTimeMillis() > 25_000L)
        s.pricing.stop()
    }

    @Test fun aRefusedLocationIsLeftAloneForMinutes() = runBlocking {
        serve { MockResponse().setResponseCode(451).setHeader("Content-Type", "application/json").setBody("""{"code":"ANONYMIZED_NETWORK","message":"anonymized network"}""") }
        val s = build(RfqConfig.Mode.SHADOW)
        s.pricing.start()
        withTimeout(15_000) { while (!s.pricing.lastNote.contains("ANONYMIZED_NETWORK")) delay(25) }
        delay(2_500)
        assertEquals(1, upgrades.get())
        assertTrue("10 minutes, not seconds", s.pricing.nextAttemptMs - System.currentTimeMillis() > 9 * 60_000L)
        s.pricing.stop()
    }

    @Test fun aSocketClosedForARevokedKeyIsNotReopened() = runBlocking {
        serve { MockResponse().withWebSocketUpgrade(listener(closeWith = 1008 to "KEY_REVOKED")) }
        val s = build(RfqConfig.Mode.SHADOW)
        s.pricing.start()
        withTimeout(15_000) { while (!s.pricing.lastNote.contains("revoked")) delay(25) }
        delay(2_500)
        assertEquals(1, upgrades.get()); assertFalse(s.pricing.connected)
        assertTrue(s.pricing.nextAttemptMs - System.currentTimeMillis() > 25 * 60_000L)
        s.pricing.stop()
    }

    @Test fun shadowIsOneSocketAndLiveAddsAQuietLastLookSocketThatSubscribesToQuotesOnly() = runBlocking {
        serve { MockResponse().withWebSocketUpgrade(listener()) }
        val shadow = build(RfqConfig.Mode.SHADOW)
        shadow.pricing.start(); shadow.lastLook.start()
        withTimeout(15_000) { while (!shadow.pricing.connected) delay(25) }
        delay(1_500)
        assertEquals("SHADOW: the pricing socket only", 1, upgrades.get()); assertEquals(listOf(listOf("rfq", "executions")), subscribes.toList())
        shadow.pricing.stop(); shadow.lastLook.stop()

        subscribes.clear(); upgrades.set(0)
        val live = build(RfqConfig.Mode.LIVE)
        live.pricing.start(); live.lastLook.start()
        withTimeout(20_000) { while (subscribes.size < 2) delay(25) }
        assertEquals(2, upgrades.get())
        assertTrue("the pricing socket: the firehose, the quotes and the market's fills", subscribes.any { it.toSet() == setOf("rfq", "quotes", "executions") })
        assertTrue("the last-look socket: quotes ONLY, a line with no firehose to fill its 256-frame queue", subscribes.any { it == listOf("quotes") })
        assertTrue(live.pricing.connected && live.lastLook.connected)
        live.pricing.stop(); live.lastLook.stop()
    }
}
