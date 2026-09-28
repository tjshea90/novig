package com.tjshea.vigilant.data

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tj, 2026-09-28: "now it is reading the API very slow". OkHttp's own default runs 5 requests at once per host, and
 * an open websocket holds one of them the whole time it's open: a connected key's scan read Novig 4 at a time.
 */
class HttpClientTest {

    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    @Test
    fun `with Novig's websocket open, the key's 10 book reads still all go at once`() {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/v3/ws") return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {})
                val n = inFlight.incrementAndGet()
                maxInFlight.accumulateAndGet(n) { a, b -> maxOf(a, b) }
                release.await(2, TimeUnit.SECONDS)
                inFlight.decrementAndGet()
                return MockResponse().setBody("{}")
            }
        }
        server.start()
        val http = vigilantHttpClient()
        val opened = CountDownLatch(1)
        val ws = http.newWebSocket(Request.Builder().url(server.url("/v3/ws")).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = opened.countDown()
        })
        opened.await(5, TimeUnit.SECONDS)
        val done = CountDownLatch(NOVIG_KEYED_IN_FLIGHT)
        repeat(NOVIG_KEYED_IN_FLIGHT) { i ->
            http.newCall(Request.Builder().url(server.url("/v3/catalog/markets/m$i/book")).build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = done.countDown()
                override fun onResponse(call: okhttp3.Call, response: Response) = response.use { done.countDown() }
            })
        }
        // Give every read the chance to start, then let them all finish.
        val t0 = System.currentTimeMillis()
        while (maxInFlight.get() < NOVIG_KEYED_IN_FLIGHT && System.currentTimeMillis() - t0 < 1_500) Thread.sleep(10)
        release.countDown()
        done.await(5, TimeUnit.SECONDS)
        ws.cancel()
        assertEquals(NOVIG_KEYED_IN_FLIGHT, maxInFlight.get())
    }

    @Test
    fun `the app's client allows the key's reads, its websocket and room to spare on one host`() {
        val http = vigilantHttpClient()
        assert(http.dispatcher.maxRequestsPerHost >= NOVIG_KEYED_IN_FLIGHT + 1 + 3)
        assertEquals(10_000, http.connectTimeoutMillis)
    }
}
