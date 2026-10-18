package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.reference.SxBetConvert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** [SxBetStream] against a mock server that speaks Centrifugo's JSON protocol as SXBET_API.md (docs developers/realtime-*) describes it. No live key has been used. */
class SxBetStreamTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val received = CopyOnWriteArrayList<String>()
    private val tokenRequests = CopyOnWriteArrayList<RecordedRequest>()
    private val seeded = AtomicInteger()
    private lateinit var serverSocket: WebSocket

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { serverSocket = webSocket }
        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
            val o = json.parseToJsonElement(text).jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: return
            if (o.containsKey("connect")) webSocket.send("""{"id":$id,"connect":{"client":"c1","ttl":3600,"expires":true}}""")
            if (o.containsKey("subscribe")) webSocket.send("""{"id":$id,"subscribe":{}}""")
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.headers["Upgrade"].equals("websocket", true)) return MockResponse().withWebSocketUpgrade(serverListener)
                tokenRequests += request
                return if (request.headers["x-sx-api-key"] == "good") MockResponse().setBody("""{"token":"tok123"}""") else MockResponse().setResponseCode(401)
            }
        }
        server.start()
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun stream(key: String = "good") = SxBetStream(
        OkHttpClient(), json, { key }, scope, restUrl = server.url("/").toString().trimEnd('/'),
        wsUrl = server.url("/connection/websocket").toString().replace("http", "ws"), backoffMinMs = 50, backoffMaxMs = 100, onSubscribed = { seeded.incrementAndGet() },
    )

    private suspend fun until(ms: Long = 5_000, cond: () -> Boolean) { withTimeout(ms) { while (!cond()) delay(20) } }

    @Test fun connectsWithTheTokenSubscribesAndReseedsOnceTheOddsChannelIsAcknowledged() = runBlocking {
        val s = stream()
        s.watch(listOf("0xA"))
        s.open()
        until { s.live }
        assertEquals("good", tokenRequests.first().headers["x-sx-api-key"])
        assertTrue(received.first().contains("\"tok123\""))
        for (ch in SxBetStream.CHANNELS) assertTrue(ch, received.any { it.contains(ch) })
        until { seeded.get() == 1 }
        s.close()
    }

    @Test fun aBestOddsPushIsConvertedToTheTakersAsksAndOnlyWatchedMarketsAreKept() = runBlocking {
        val s = stream()
        s.watch(listOf("0xA"))
        s.open()
        until { s.live }
        // maker frame: makers back outcome one at 51% and outcome two at 48% -> a taker pays 52% for one and 49% for two
        fun lvl(p: Int) = """{"percentageOdds":"${p}000000000000000000","size":"${500L * 1_000_000}"}"""
        serverSocket.send("""{"push":{"channel":"best_odds_v3:global","pub":{"data":{"marketHash":"0xA","outcomeOne":${lvl(51)},"outcomeTwo":${lvl(48)}}}}}""")
        serverSocket.send("""{"push":{"channel":"best_odds_v3:global","pub":{"data":{"marketHash":"0xZ","outcomeOne":${lvl(51)},"outcomeTwo":${lvl(48)}}}}}""")
        until { s.books.containsKey("0xA") }
        val b = s.books.getValue("0xA")
        assertEquals(0.52, b.one!!.prob, 1e-9)
        assertEquals(0.49, b.two!!.prob, 1e-9)
        assertNotNull(SxBetConvert.pair(b))
        assertNull(s.books["0xZ"])
        s.close()
    }

    @Test fun scoresAndMainLinesAreKept() = runBlocking {
        val s = stream()
        s.open()
        until { s.live }
        serverSocket.send("""{"push":{"channel":"fixtures:live_scores","pub":{"data":{"sportXeventId":"L1","sportId":3,"teamOneScore":4,"teamTwoScore":1,"currentPeriod":"5th Inning","periodTime":"-1"}}}}""")
        serverSocket.send("""{"push":{"channel":"main_line:global","pub":{"data":{"marketHash":"0xM","marketType":28,"sportXeventId":"L1","updateTime":"1"}}}}""")
        until { s.scores.containsKey("L1") && s.mainLines.containsKey("L1|28") }
        assertEquals(4, s.scores.getValue("L1").teamOne)
        assertEquals("0xM", s.mainLines["L1|28"])
        s.close()
    }

    @Test fun aServerPingIsAnsweredAndAnUnacceptedKeyNeverConnects() = runBlocking {
        val s = stream()
        s.open()
        until { s.live }
        serverSocket.send("{}")
        until { received.any { it == "{}" } }
        s.close()
        val bad = stream("bad")
        bad.open()
        until { bad.lastError != null }
        assertFalse(bad.live)
        assertTrue(bad.lastError!!.contains("401"))
        bad.close()
    }
}
