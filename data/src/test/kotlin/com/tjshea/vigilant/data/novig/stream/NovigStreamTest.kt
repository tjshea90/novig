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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A mock Novig websocket speaking the documented protocol (docs.novig.com/api/streaming): `book` on
 * markets, a snapshot per subscribed market, then deltas (Tj, 2026-09-28: "Make sure the app is taking
 * full advantage of the novig API key").
 */
class NovigStreamTest {

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Every frame Vigilant sent, parsed. */
    private val received = CopyOnWriteArrayList<JsonObject>()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private val subscribes get() = received.filter { it["subscribe"] != null }
    private fun marketsOf(frame: JsonObject) = frame["subscribe"]!!.jsonObject["markets"]!!.jsonObject

    private fun snapshotOf(m: String) =
        """"$m":{"eventId":"e1","book":{"seq":10,"orders":{"A-$m":[{"order":"o1-$m","price":"0.600","qty":100}],"B-$m":[{"order":"o2-$m","price":"0.380","qty":50}]}},"lifecycle":{"seq":1,"status":"OPEN"}}"""

    /**
     * Novig's side. [reply] decides a subscribe's answer from (its nonce, its markets, how many came
     * before): null = the normal snapshot of every market, then one delta on the first.
     */
    private fun novigSide(reply: (Long, List<String>, Int) -> String? = { _, _, _ -> null }) = object : WebSocketListener() {
        var count = 0
        override fun onOpen(webSocket: WebSocket, response: Response) {}
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            received += msg
            val sub = msg["subscribe"]?.jsonObject ?: return
            val nonce = msg["nonce"]!!.jsonPrimitive.content.toLong()
            val markets = sub["markets"]!!.jsonObject.keys.toList()
            val special = reply(nonce, markets, count++)
            if (special != null) {
                webSocket.send(special)
                return
            }
            webSocket.send("""{"ts":1,"nonce":$nonce,"subscribed":{},"snapshot":{${markets.joinToString(",") { snapshotOf(it) }}}}""")
            val first = markets.first()
            webSocket.send("""{"ts":2,"delta":{"$first":{"eventId":"e1","book":{"seq":11,"deltas":[{"kind":"add","order":"o3","outcome":"B-$first","price":"0.390","qty":20}]}}}}""")
        }
    }

    private suspend fun until(check: () -> Boolean) = withTimeout(10_000) { while (!check()) delay(20) }

    private fun signer(keyId: String = "read-key-1"): NovigSignedClient {
        val vault = MemoryVault().apply { generate("read") }
        return NovigSignedClient(OkHttpClient(), Json, vault.signer("read", keyId), server.url("").toString().trimEnd('/'))
    }

    private fun stream(refillPerSec: Double = 4_000.0, maxMarkets: Int = 2_000, idleCloseMs: Long = 60_000, clock: () -> Long = System::currentTimeMillis) =
        NovigStream(
            OkHttpClient(), signer(), scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"),
            refillPerSec = refillPerSec, maxMarkets = maxMarkets, idleCloseMs = idleCloseMs, clock = clock,
        )

    private val plan = (1..300).map { "m$it" }

    @Test
    fun `a scan's whole plan is subscribed in one request and every book stays live`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val stream = stream()
        stream.watch(plan)
        until { stream.live(plan).size == plan.size }

        // One subscribe for all 300 markets (charged at most the 512-token bucket), each on `book`.
        assertEquals(1, subscribes.size)
        assertEquals(plan.toSet(), marketsOf(subscribes.single()).keys)
        assertTrue(marketsOf(subscribes.single()).values.all { it.jsonPrimitive.content == "book" })
        // The delta after the snapshot is applied: B-m1 has 0.390 × 20 on top.
        until { stream.book("m1")?.seq == 11L }
        assertEquals(listOf(BidLevel(390, 20), BidLevel(380, 50)), stream.book("m1")!!.bidsByOutcome["B-m1"])

        val upgrade = server.takeRequest()
        assertEquals("/v3/ws", upgrade.path)
        assertEquals("read-key-1", upgrade.getHeader("Novig-Key-Id"))
        assertNotNull(upgrade.getHeader("Novig-Signature"))

        stream.close()
        assertTrue(stream.state.value is StreamState.Off)
        assertNull(stream.book("m1"))
        assertTrue(stream.live(plan).isEmpty())
    }

    @Test
    fun `the first subscribe waits for a full bucket so the plan's first lines don't spend it`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        // 100 tokens a second: the 32 the upgrade spent come back in ~0.3 s, the whole bucket in ~5 s.
        val stream = stream(refillPerSec = 100.0)
        stream.watch(plan.take(5))
        delay(50)
        stream.watch(plan.take(200)) // the plan grew before the bucket refilled
        until { stream.live(plan).size == 200 }
        assertEquals("the early five went with the rest", 1, subscribes.size)
        assertEquals(200, marketsOf(subscribes.single()).size)
        // Later, a few more lines: small enough to go as soon as their tokens are back.
        stream.watch(plan.take(203))
        until { stream.live(plan).size == 203 }
        assertEquals(2, subscribes.size)
        assertEquals(setOf("m201", "m202", "m203"), marketsOf(subscribes[1]).keys)
        stream.close()
    }

    @Test
    fun `markets a scan no longer wants are dropped`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val stream = stream()
        stream.watch(listOf("m1", "m2", "m3"))
        until { stream.live(listOf("m1", "m2", "m3")).size == 3 }
        stream.watch(listOf("m2"))
        until { received.any { it["unsubscribe"] != null } }
        val unsub = received.single { it["unsubscribe"] != null }["unsubscribe"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("market:m1", "market:m3"), unsub)
        assertEquals(setOf("m2"), stream.live(listOf("m1", "m2", "m3")).keys)
        stream.close()
    }

    @Test
    fun `dropping more markets than the bucket holds costs one bucket, not minutes of debt`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        // A 100-token bucket refilling 50 a second. Novig charges any request at most the bucket (docs.novig.com/api/streaming), so swapping
        // 300 markets for 300 others is one full bucket to drop them and one to add the rest: ~4 s here. Charged 1 a subject uncapped, the
        // drop waited for 300 tokens a 100-token bucket can never hold and left it 200 in debt: ~12 s here, minutes on the phone.
        val stream = NovigStream(
            OkHttpClient(), signer(), scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"),
            capacity = 100.0, refillPerSec = 50.0, maxMarkets = 300,
        )
        val first = (1..300).map { "m$it" }
        val second = (301..600).map { "m$it" }
        stream.watch(first)
        until { stream.live(first).size == 300 }
        val t0 = System.currentTimeMillis()
        stream.watch(second)
        until { stream.live(second).size == 300 }
        val took = System.currentTimeMillis() - t0
        assertTrue("took $took ms", took < 8_000)
        assertEquals(300, received.single { it["unsubscribe"] != null }["unsubscribe"]!!.jsonArray.size)
        assertEquals(2, subscribes.size)
        stream.close()
    }

    @Test
    fun `opening the feed connects without changing what it watches`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val stream = stream()
        stream.watch(listOf("m1", "m2"))
        until { stream.live(listOf("m1", "m2")).size == 2 }
        // A scan opens the feed at its first plan: what it holds stays held, and nothing is dropped or added.
        stream.open()
        delay(200)
        assertEquals(setOf("m1", "m2"), stream.live(listOf("m1", "m2")).keys)
        assertTrue(received.none { it["unsubscribe"] != null })
        assertEquals(1, subscribes.size)
        stream.close()
        // Closed, open() connects again (one more upgrade) and subscribes nothing until it's handed markets.
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        stream.open()
        until { stream.state.value is StreamState.Live }
        delay(200)
        assertEquals(1, subscribes.size)
        stream.close()
    }

    @Test
    fun `a throttled subscribe is sent again, and a limit refusal asks for fewer`() = runBlocking {
        // First: Novig's throttle (a reply with no nonce). Then: over the watch limit above 50 markets.
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                novigSide { nonce, markets, count ->
                    when {
                        count == 0 -> """{"code":"RATE_LIMIT_EXCEEDED","message":"Rate limit exceeded. Please wait before retrying."}"""
                        markets.size > 50 -> """{"nonce":$nonce,"code":"SUBSCRIPTION_LIMIT_EXCEEDED","message":"too many"}"""
                        else -> null
                    }
                },
            ),
        )
        val stream = stream()
        stream.watch(plan.take(80))
        until { stream.live(plan).size >= 40 }
        assertTrue("retried after the throttle", subscribes.size >= 3)
        // What's held is what Novig accepted: never more than 50 in one go.
        assertTrue(stream.live(plan).size <= 50)
        stream.close()
    }

    @Test
    fun `a refused upgrade says why, and scans read by REST until the retry time`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK"}"""))
        val stream = stream()
        stream.watch(plan)
        until { stream.state.value is StreamState.Failed }
        // Novig's own code, read as what it is: a verdict on the network's address (Tj, 2026-09-28: "I don't" have a VPN).
        assertTrue((stream.state.value as StreamState.Failed).message.contains("ANONYMIZED_NETWORK"))
        assertTrue(stream.problemSince(0)!!.contains("Test key"))
        assertNull(stream.problemSince(Long.MAX_VALUE)) // a scan that started after it isn't told
        // The next scan doesn't knock again straight away.
        stream.watch(plan)
        delay(300)
        assertEquals(1, server.requestCount)
        assertTrue(stream.live(plan).isEmpty())
    }

    /**
     * The v0.70.1 diagnostics: after Novig's 451 ANONYMIZED_NETWORK the live feed waited 5 minutes while the key's REST route came back in 2, so two scans ran 16-29 s long. A refusal about
     * the network's address is tried again as soon as the REST route is (2 minutes); a feed that broke some other way keeps its 5.
     */
    @Test
    fun `a network refusal is tried again in 2 minutes like the key route, any other failure in 5`() = runBlocking {
        var now = 1_000_000L
        server.enqueue(MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK"}"""))
        val stream = stream(clock = { now })
        stream.watch(plan)
        until { stream.state.value is StreamState.Failed }
        assertEquals(1, server.requestCount)

        // 119 s later it still isn't knocked; 121 s later it is.
        now += 119_000
        stream.watch(plan)
        delay(300)
        assertEquals("not before the 2 minutes", 1, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(451).setBody("""{"code":"RESTRICTED_NETWORK_REGION"}"""))
        now += 2_000
        stream.watch(plan)
        until { server.requestCount == 2 }

        // A different failure (the upgrade refused 403) waits the full 5 minutes.
        until { (stream.state.value as? StreamState.Failed)?.message?.contains("Novig") == true }
        now += 10_000
        server.enqueue(MockResponse().setResponseCode(403))
        now += 111_000
        stream.watch(plan) // 2 min after the second 451: through
        until { server.requestCount == 3 }
        until { (stream.state.value as? StreamState.Failed)?.message?.contains("403") == true }
        now += 121_000
        stream.watch(plan)
        delay(300)
        assertEquals("a 403 is not retried at 2 minutes", 3, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(403))
        now += 180_000
        stream.watch(plan)
        until { server.requestCount == 4 }
    }

    @Test
    fun `a key the phone can no longer sign with fails cleanly instead of crashing`() {
        val broken = object : com.tjshea.vigilant.data.novig.signing.NovigSigningKey {
            override val keyId = "k"
            override val algorithm = com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray): ByteArray = error("Novig key alias is missing from the Keystore")
        }
        val stream = NovigStream(OkHttpClient(), NovigSignedClient(OkHttpClient(), Json, broken), scope)
        stream.watch(plan)
        assertTrue((stream.state.value as StreamState.Failed).message.contains("Reconnect"))
    }

    @Test
    fun `the feed closes itself once scans stop using it`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val stream = stream(idleCloseMs = 300)
        stream.watch(plan.take(3))
        until { stream.live(plan.take(3)).size == 3 }
        until { stream.state.value is StreamState.Off }
        assertNull(stream.problemSince(0)) // closing when idle isn't a problem
    }

    /**
     * Found by the full test (2026-09-27): a late callback from a socket Vigilant already closed
     * flipped the stream to "Failed", and after a quick reconnect wiped the new connection too.
     */
    @Test
    fun `a closed socket's late goodbye neither fails the stream nor touches a newer connection`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val stream = stream()
        stream.watch(listOf("m1"))
        until { stream.book("m1")?.seq == 11L }
        stream.close()
        delay(1_000) // the server's close frame comes back
        assertTrue("closed on purpose stays Off, was ${stream.state.value}", stream.state.value is StreamState.Off)
        // Close and reconnect at once: the first socket's goodbye must not reach the second.
        stream.watch(listOf("m1"))
        until { stream.book("m1")?.seq == 11L }
        delay(1_000)
        assertTrue("the new connection is still live, was ${stream.state.value}", stream.state.value is StreamState.Live)
        assertEquals(11L, stream.book("m1")?.seq)
        stream.close()
    }
}
