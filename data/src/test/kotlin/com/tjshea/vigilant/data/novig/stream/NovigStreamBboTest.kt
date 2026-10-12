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

/**
 * The best-bid channel (NOVIG_API.md §25) against a mock Novig that answers each market on the channel it was subscribed on, with the OpenAPI's `bbo` shapes: a snapshot
 * `{bbo: {seq, bbo: {outcome: {price, qty}}}}` and a delta `{bbo: {seq, deltas: [{outcomeId, bbo}]}}`.
 */
class NovigStreamBboTest {

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val received = CopyOnWriteArrayList<JsonObject>()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private val subscribes get() = received.filter { it["subscribe"] != null }
    private val snapshots get() = received.filter { it["snapshot"] != null }
    private fun channelsOf(frame: JsonObject, verb: String = "subscribe") = frame[verb]!!.jsonObject["markets"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }

    private fun bookOf(m: String) = """"book":{"seq":10,"orders":{"A-$m":[{"order":"o1-$m","price":"0.600","qty":100},{"order":"o9-$m","price":"0.590","qty":300}],"B-$m":[{"order":"o2-$m","price":"0.380","qty":50}]}}"""
    private fun bboOf(m: String, bestA: String = "0.600", qtyA: Int = 100) = """"bbo":{"seq":5,"bbo":{"A-$m":{"price":"$bestA","qty":$qtyA},"B-$m":{"price":"0.380","qty":50}}}"""

    /**
     * Novig's side. [bboQty] is the size the `bbo` snapshots claim at A's best bid (100 is the book's own); [silentBbo]: never answers a `bbo` market; [refuseBbo]: answers a subscribe
     * naming `bbo` with this error; [lateBboDelta]: sends a `bbo` delta for the first market after the snapshots.
     */
    private fun novigSide(
        bboQty: Int = 100,
        silentBbo: Boolean = false,
        refuseBbo: String? = null,
        lateBboDelta: Boolean = false,
        lifecycle: Boolean = true,
    ) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {}
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            received += msg
            val nonce = msg["nonce"]!!.jsonPrimitive.content.toLong()
            val verb = if (msg["subscribe"] != null) "subscribe" else if (msg["snapshot"] != null) "snapshot" else return
            val channels = channelsOf(msg, verb)
            if (refuseBbo != null && channels.values.any { it == "bbo" }) {
                webSocket.send("""{"nonce":$nonce,"code":"INVALID_CHANNEL","message":"$refuseBbo"}""")
                return
            }
            val life = if (lifecycle) ""","lifecycle":{"seq":3,"status":"OPEN"}""" else ""
            val body = channels.filter { (_, ch) -> !(silentBbo && ch == "bbo") }.map { (m, ch) ->
                """"$m":{"eventId":"e1",${if (ch == "bbo") bboOf(m, qtyA = bboQty) else bookOf(m)}$life}"""
            }
            webSocket.send("""{"ts":1,"nonce":$nonce,"subscribed":{},"snapshot":{${body.joinToString(",")}}}""")
            if (lateBboDelta && verb == "subscribe") {
                val first = channels.entries.first { it.value == "bbo" }.key
                webSocket.send("""{"ts":2,"delta":{"$first":{"eventId":"e1","bbo":{"seq":6,"deltas":[{"outcomeId":"A-$first","bbo":{"price":"0.610","qty":7}}]}}}}""")
            }
        }
    }

    private suspend fun until(timeoutMs: Long = 10_000, check: () -> Boolean) = withTimeout(timeoutMs) { while (!check()) delay(20) }

    private fun signer(): NovigSignedClient {
        val vault = MemoryVault().apply { generate("read") }
        return NovigSignedClient(OkHttpClient(), Json, vault.signer("read", "read-key-1"), server.url("").toString().trimEnd('/'))
    }

    private fun stream(
        bbo: Boolean = true,
        refillPerSec: Double = 4_000.0,
        bboDataWaitMs: Long = 400,
        canaryMarkets: Int = 4,
        deepHoldMs: Long = 60_000,
        keepOpen: Boolean = false,
        liveRetryMs: Long = 100,
        clock: () -> Long = System::currentTimeMillis,
    ) = NovigStream(
        OkHttpClient(), signer(), scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"),
        refillPerSec = refillPerSec, idleCloseMs = 60_000, bbo = bbo, bboDataWaitMs = bboDataWaitMs, canaryMarkets = canaryMarkets,
        deepHoldMs = deepHoldMs, liveRetryMs = liveRetryMs, clock = clock,
    ).also { if (keepOpen) it.configureFeed(bbo, true) }

    private val plan = (1..12).map { "m$it" }

    @Test
    fun `with the best-bid feed on the plan is subscribed on bbo and read as partial books`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.watch(plan)
        until { s.liveTop(plan).size == plan.size }
        assertEquals(1, subscribes.size)
        assertTrue(channelsOf(subscribes.single()).values.all { it == "bbo" })
        val b = s.liveTop(listOf("m1")).getValue("m1")
        assertTrue(b.partial)
        assertEquals(listOf(BidLevel(600, 100)), b.bidsByOutcome["A-m1"])
        assertEquals(listOf(BidLevel(380, 50)), b.bidsByOutcome["B-m1"])
        assertEquals(BookChannel.BBO, s.books.channelOf("m1"))
        assertTrue("the lifecycle came with it", s.books.lifecycle("m1")!!.open)
        s.close()
    }

    @Test
    fun `a delta moves the best bid`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(lateBboDelta = true)))
        val s = stream()
        s.watch(plan)
        until { s.liveTop(plan).size == plan.size }
        until { s.liveTop(listOf(plan.first())).getValue(plan.first()).bidsByOutcome["A-${plan.first()}"]?.firstOrNull()?.priceMilli == 610 }
        s.close()
    }

    @Test
    fun `a complete-book read of a bbo market returns nothing, moves it to book, and the ladder arrives`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.watch(plan)
        until { s.liveTop(plan).size == plan.size }
        assertTrue("a complete book is not on offer for a bbo market", s.live(listOf("m3")).isEmpty())
        until { subscribes.size == 2 }
        assertEquals(mapOf("m3" to "book"), channelsOf(subscribes[1]))
        until { s.live(listOf("m3")).size == 1 }
        val full = s.live(listOf("m3")).getValue("m3")
        assertFalse(full.partial)
        assertEquals(listOf(BidLevel(600, 100), BidLevel(590, 300)), full.bidsByOutcome["A-m3"])
        // the others stay on bbo
        assertEquals(BookChannel.BBO, s.books.channelOf("m4"))
        s.close()
    }

    @Test
    fun `deepen moves markets to book in one request, and a market not wanted any more is dropped with its depth`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.watch(plan)
        until { s.liveTop(plan).size == plan.size }
        s.deepen(listOf("m1", "m2"))
        until { subscribes.size == 2 }
        assertEquals(mapOf("m1" to "book", "m2" to "book"), channelsOf(subscribes[1]))
        until { s.live(listOf("m1", "m2")).size == 2 }
        // deepening again changes nothing
        s.deepen(listOf("m1"))
        delay(150)
        assertEquals(2, subscribes.size)
        s.watch(plan.drop(2))
        until { received.any { it["unsubscribe"] != null } }
        s.close()
    }

    @Test
    fun `the live lane's pinned markets are always on book`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.watchLive(listOf("live1", "live2"))
        s.watch(plan)
        until { s.liveTop(plan + listOf("live1", "live2")).size == plan.size + 2 }
        val all = subscribes.flatMap { channelsOf(it).entries }.associate { it.key to it.value }
        assertEquals("book", all["live1"])
        assertEquals("book", all["live2"])
        assertEquals("bbo", all["m1"])
        assertFalse(s.live(listOf("live1")).isEmpty())
        s.close()
    }

    @Test
    fun `with the switch off every market is on book as it always was`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream(bbo = false)
        s.watch(plan)
        until { s.live(plan).size == plan.size }
        assertTrue(channelsOf(subscribes.single()).values.all { it == "book" })
        assertFalse(s.live(plan).values.any { it.partial })
        assertTrue(s.feedReport().first().contains("switched off"))
        s.close()
    }

    @Test
    fun `turning the switch off puts the held markets back on book, and on again puts the screening ones back on bbo`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.watch(plan)
        until { s.liveTop(plan).size == plan.size }
        s.configureFeed(bbo = false, keepOpen = false)
        until { s.live(plan).size == plan.size }
        assertTrue(subscribes.drop(1).flatMap { channelsOf(it).values }.all { it == "book" })
        s.configureFeed(bbo = true, keepOpen = false)
        until { s.books.channelOf("m5") == BookChannel.BBO }
        s.close()
    }

    @Test
    fun `Novig refusing the bbo channel by name puts everything back on book at once`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(refuseBbo = "unknown channel bbo")))
        val s = stream()
        s.watch(plan)
        until { s.live(plan).size == plan.size }
        assertTrue(s.feedReport().first().contains("given up"))
        assertTrue(s.feedReport().first().contains("refused the best-bid channel"))
        assertFalse(s.liveTop(plan).values.any { it.partial })
        // the fallback is sticky: a later watch does not try bbo again
        s.watch(plan + "m99")
        until { s.live(listOf("m99")).size == 1 }
        assertEquals("book", subscribes.last().let { channelsOf(it)["m99"] })
        s.close()
    }

    @Test
    fun `bbo markets that never get a snapshot put everything back on book`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(silentBbo = true)))
        val s = stream(bboDataWaitMs = 300)
        s.watch(plan)
        // the idle watchdog looks every 15 s at most; with the connection this short a direct tick is not available, so wait on the stream's own loop
        until(timeoutMs = 30_000) { s.feedReport().first().contains("given up") }
        assertTrue(s.feedReport().first().contains("no best-bid snapshot"))
        until { s.live(plan).size == plan.size }
        s.close()
    }

    @Test
    fun `the canary compares a few bbo markets with their book and a match keeps bbo`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        s.watch(plan)
        until { s.canary != null }
        val c = s.canary!!
        assertEquals(4, c.checked)
        assertEquals(c.detail, 4, c.matched)
        // it asked the `book` of 4 markets with the snapshot verb and left the subscription alone
        assertEquals(4, channelsOf(snapshots.single(), "snapshot").size)
        assertTrue(channelsOf(snapshots.single(), "snapshot").values.all { it == "book" })
        assertEquals(1, subscribes.size)
        // the markets are still held as bbo: the book reply was a comparison, not state
        assertEquals(BookChannel.BBO, s.books.channelOf("m1"))
        assertTrue(s.feedReport().first().startsWith("feed: best-bid feed"))
        assertTrue(s.feedReport().first { it.startsWith("canary:") }.contains("4 of 4"))
        s.close()
    }

    @Test
    fun `a canary that finds the best bids different puts everything back on book`() = runBlocking {
        // bbo says 77 contracts at the best bid where the book has 100
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(bboQty = 77)))
        val s = stream()
        s.watch(plan)
        until(timeoutMs = 30_000) { s.feedReport().first().contains("given up") }
        assertTrue(s.feedReport().first().contains("disagreed with the book"))
        assertEquals(0, s.canary!!.matched)
        // asked twice, the second time of other markets
        assertEquals(2, snapshots.size)
        assertTrue(channelsOf(snapshots[0], "snapshot").keys.intersect(channelsOf(snapshots[1], "snapshot").keys).isEmpty())
        until { s.live(plan).size == plan.size }
        assertFalse(s.live(plan).values.any { it.partial })
        s.close()
    }

    @Test
    fun `a 429 on the upgrade waits the Retry-After it names, not five minutes, and the stream connects again by itself`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("""{"code":"RATE_LIMIT_EXCEEDED"}"""))
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream(keepOpen = true)
        val t0 = System.currentTimeMillis()
        s.watch(plan)
        until(timeoutMs = 15_000) { s.liveTop(plan).size == plan.size }
        val took = System.currentTimeMillis() - t0
        assertTrue("took $took ms", took in 900..8_000)
        assertEquals(2, server.requestCount)
        assertTrue(s.feedReport().any { it.contains("upgrade 429s 1") })
        s.close()
    }

    @Test
    fun `without keep-open a 429 is still waited out by the next scan, not by a timer`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("""{"code":"RATE_LIMIT_EXCEEDED"}"""))
        val s = stream()
        s.watch(plan)
        until { s.state.value is StreamState.Failed }
        assertTrue((s.state.value as StreamState.Failed).message.contains("429"))
        delay(1_500)
        assertEquals("nobody asked again", 1, server.requestCount)
        s.close()
    }

    @Test
    fun `with the connection kept open a plain drop is retried by the stream itself and the plan comes back`() = runBlocking {
        val dropper = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.close(1011, "gone") }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(dropper))
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream(keepOpen = true)
        s.watch(plan)
        until(timeoutMs = 15_000) { s.liveTop(plan).size == plan.size }
        assertEquals(2, server.requestCount)
        s.close()
    }

    @Test
    fun `a bbo market held in the stream does not count as complete for a ladder-needing reader even after a fallback`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(refuseBbo = "channel bbo is not enabled")))
        val s = stream()
        s.watch(plan)
        until { s.feedReport().first().contains("given up") }
        until { s.live(plan).size == plan.size }
        assertNotNull(s.live(plan)["m1"])
        assertNull(s.live(listOf("not-wanted"))["not-wanted"])
        s.close()
    }

    @Test
    fun `two lanes' live markets are held together on the one connection, each lane replacing only its own`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide()))
        val s = stream()
        val a = SharedStreamView({ s }, BookListener { _, _, _ -> })
        val b = SharedStreamView({ s }, BookListener { _, _, _ -> })
        a.watchLive(listOf("la1", "la2"))
        b.watchLive(listOf("lb1"))
        until { s.live(listOf("la1", "la2", "lb1")).size == 3 }
        // lane b changes its list: lane a's markets stay pinned (and on book), not dropped by a list that replaced them
        b.watchLive(listOf("lb1", "lb2"))
        s.watch(plan)
        until { s.live(listOf("lb2")).size == 1 }
        assertEquals(setOf("la1", "la2"), s.live(listOf("la1", "la2")).keys)
        assertEquals("one connection for both lanes", 1, server.requestCount)
        val all = subscribes.flatMap { channelsOf(it).entries }.associate { it.key to it.value }
        assertTrue(listOf("la1", "la2", "lb1", "lb2").all { all[it] == "book" })
        s.close()
    }

    @Test
    fun `a key that reports a bigger watch cap is given the room, a smaller one is held to it`() = runBlocking {
        val s = stream()
        assertEquals(NovigStream.MAX_MARKETS, s.room())
        s.tune(capacity = 512, refillPerSec = 32.0, maxWatchedMarkets = 8_192)
        assertEquals(8_192 - (NovigStream.MAX_WATCHED - NovigStream.MAX_MARKETS), s.room())
        s.tune(capacity = 512, refillPerSec = 32.0, maxWatchedMarkets = 1_000)
        assertEquals(1_000 - (NovigStream.MAX_WATCHED - NovigStream.MAX_MARKETS), s.room().coerceAtLeast(1))
        // without the best-bid channel a full book on 8,000 markets is more than a phone's heap should hold: the room stays at the old 2,000
        val book = stream(bbo = false)
        book.tune(capacity = 512, refillPerSec = 32.0, maxWatchedMarkets = 8_192)
        assertEquals(NovigStream.MAX_MARKETS, book.room())
    }
}
