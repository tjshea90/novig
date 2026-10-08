package com.tjshea.vigilant.data.pinnodds

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The socket the way the docs say to use it, against a local server: the key in a header, no compression, subscribe at once, pong for ping, frames handed on. */
@OptIn(ExperimentalCoroutinesApi::class)
class PinnSocketTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun stop() {
        scope.cancel()
        server.shutdown()
    }

    private class Server : WebSocketListener() {
        val received = CopyOnWriteArrayList<String>()
        val subscribed = CountDownLatch(1)
        val ponged = CountDownLatch(1)
        @Volatile var socket: WebSocket? = null
        override fun onOpen(webSocket: WebSocket, response: Response) { socket = webSocket }
        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
            if (text.contains("\"subscribe\"")) subscribed.countDown()
            if (text.contains("\"pong\"")) ponged.countDown()
        }
    }

    @Test
    fun `it connects with the key in a header, offers no compression, subscribes, answers a ping and hands frames on`() {
        val srv = Server()
        server.enqueue(MockResponse().withWebSocketUpgrade(srv))
        server.start()
        val frames = CopyOnWriteArrayList<String>()
        val got = CountDownLatch(1)
        val sock = PinnSocket(OkHttpClient(), { "my-pinnodds-key" }, scope, { text, _ -> frames += text; got.countDown() }, sports = listOf(1, 3), url = server.url("/ws/feed").toString().replace("http", "ws"))
        sock.start()
        assertTrue("subscribed within 5 s", srv.subscribed.await(5, TimeUnit.SECONDS))
        val req = server.takeRequest()
        assertEquals("/ws/feed", req.path)
        assertEquals("the key travels in the header", "my-pinnodds-key", req.getHeader("x-api-key"))
        assertFalse("the key is not in the URL", req.path!!.contains("my-pinnodds-key"))
        assertNull("no permessage-deflate offer: compression is off", req.getHeader("Sec-WebSocket-Extensions"))
        assertEquals("""{"type":"subscribe","streams":["live"],"sport_ids":[1,3]}""", srv.received.first())
        assertTrue(sock.state.value is PinnSocketState.Live)
        srv.socket!!.send("""{"type":"ping","ts":1,"buffered_max_bytes":0}""")
        assertTrue("a ping is answered with a pong", srv.ponged.await(5, TimeUnit.SECONDS))
        assertTrue("and is not handed on as a frame", frames.isEmpty())
        srv.socket!!.send("""{"type":"live","sport_id":3,"topic":"matchups/reg/sp/4/live/ld","op":"upd","rec":{"id":1},"ts":1}""")
        assertTrue(got.await(5, TimeUnit.SECONDS))
        assertTrue(frames.single().contains("\"live\""))
        assertTrue(sock.frames >= 2)
        sock.stop()
        assertEquals(PinnSocketState.Off, sock.state.value)
    }

    @Test
    fun `without a key it does not connect and says why`() {
        server.start()
        val sock = PinnSocket(OkHttpClient(), { "" }, scope, { _, _ -> }, url = server.url("/ws/feed").toString().replace("http", "ws"))
        sock.start()
        val deadline = System.currentTimeMillis() + 3_000
        while (sock.state.value !is PinnSocketState.Down && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val st = sock.state.value as PinnSocketState.Down
        assertTrue(st.message.contains("No Pinnodds key"))
        assertNull(st.retryAtMs)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a plan without the websocket is told, not retried, and an eviction is told apart`() {
        fun explain(e: PinnSocket.Ended) = PinnSocket(OkHttpClient(), { "k" }, scope, { _, _ -> }).explain(e)
        val no = explain(PinnSocket.Ended(0, "", 403, "x"))
        assertTrue(no.first.contains("plan_lacks_ws")); assertNull(no.second)
        val bad = explain(PinnSocket.Ended(0, "", 401, "x"))
        assertTrue(bad.first.contains("refused the key")); assertNull(bad.second)
        val evicted = explain(PinnSocket.Ended(1001, "evicted by newer connection", null, null))
        assertTrue(evicted.first.contains("Another connection")); assertEquals(60_000L, evicted.second)
        val dereg = explain(PinnSocket.Ended(1011, "deregistered: slow_consumer", null, null))
        assertEquals(0L, dereg.second)
        assertNotNull(explain(PinnSocket.Ended(0, "", null, "timeout")).second)
    }
}
