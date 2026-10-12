package com.tjshea.vigilant.data.novig.stream

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
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.zip.Deflater

/** Socket compression (NOVIG_API.md §27): the header is sent only when asked for, binary raw-DEFLATE frames are read like text ones, and frames that cannot be read give the feature up and the feed comes back plain. */
class NovigStreamCompressTest {

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private fun signer(): NovigSignedClient {
        val vault = MemoryVault().apply { generate("read") }
        return NovigSignedClient(OkHttpClient(), Json, vault.signer("read", "read-key-1"), server.url("").toString().trimEnd('/'))
    }

    private fun stream() = NovigStream(OkHttpClient(), signer(), scope, wsUrl = server.url("/v3/ws").toString().replace("http", "ws"), refillPerSec = 4_000.0, idleCloseMs = 60_000)

    private fun deflate(text: String): ByteArray {
        val d = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        d.setInput(text.toByteArray())
        d.finish()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun snapshot(nonce: Long, ids: Collection<String>): String {
        val body = ids.joinToString(",") { m ->
            """"$m":{"eventId":"e1","book":{"seq":10,"orders":{"A-$m":[{"order":"o1-$m","price":"0.600","qty":100}],"B-$m":[{"order":"o2-$m","price":"0.380","qty":50}]}}}"""
        }
        return """{"ts":1,"nonce":$nonce,"subscribed":{},"snapshot":{$body}}"""
    }

    private enum class Mode { DEFLATED, PLAIN, GARBAGE_WHEN_ASKED }

    private fun novigSide(mode: Mode, asked: () -> Boolean) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {}
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            val sub = msg["subscribe"]?.jsonObject?.get("markets")?.jsonObject ?: return
            val nonce = msg["nonce"]!!.jsonPrimitive.content.toLong()
            val reply = snapshot(nonce, sub.keys)
            when {
                mode == Mode.DEFLATED -> webSocket.send(deflate(reply).toByteString())
                mode == Mode.GARBAGE_WHEN_ASKED && asked() -> repeat(4) { webSocket.send(ByteArray(40) { (it * 7 + 3).toByte() }.toByteString()) }
                else -> webSocket.send(reply)
            }
        }
    }

    private suspend fun until(timeoutMs: Long = 10_000, check: () -> Boolean) = withTimeout(timeoutMs) { while (!check()) delay(20) }
    private val plan = (1..20).map { "m$it" }

    @Test
    fun `asked for, the header goes out and binary deflate frames are read like text ones`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(Mode.DEFLATED) { false }))
        val s = stream()
        s.configureCompression(true)
        s.watch(plan)
        until { s.live(plan).size == plan.size }
        val upgrade = server.takeRequest()
        assertEquals("deflate", upgrade.getHeader(NovigStream.COMPRESS_HEADER))
        val line = s.feedReport().first { it.startsWith("compression:") }
        assertTrue(line, line.startsWith("compression: on: ") && line.contains("smaller"))
        s.close()
    }

    @Test
    fun `not asked for, no header goes out and the report says it is off`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(Mode.PLAIN) { false }))
        val s = stream()
        s.watch(plan)
        until { s.live(plan).size == plan.size }
        assertNull(server.takeRequest().getHeader(NovigStream.COMPRESS_HEADER))
        assertEquals("compression: switched off", s.feedReport().first { it.startsWith("compression:") })
        s.close()
    }

    @Test
    fun `a server that ignores the header and sends text is read all the same and the report says nothing was saved`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(Mode.PLAIN) { false }))
        val s = stream()
        s.configureCompression(true)
        s.watch(plan)
        until { s.live(plan).size == plan.size }
        val line = s.feedReport().first { it.startsWith("compression:") }
        assertTrue(line, line.contains("answered in plain text"))
        s.close()
    }

    @Test
    fun `frames that will not inflate give compression up and the feed comes back plain`() = runBlocking {
        // the first connection answers garbage to a request that carried the header; the second (asked without it) answers plainly
        val garbage = java.util.concurrent.atomic.AtomicBoolean(true)
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(Mode.GARBAGE_WHEN_ASKED) { garbage.getAndSet(true) }))
        garbage.set(true)
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(Mode.PLAIN) { false }))
        val s = stream()
        s.configureCompression(true)
        s.watch(plan)
        until { s.live(plan).size == plan.size }
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("deflate", first.getHeader(NovigStream.COMPRESS_HEADER))
        assertNull("the reconnect asks for plain frames", second.getHeader(NovigStream.COMPRESS_HEADER))
        val line = s.feedReport().first { it.startsWith("compression:") }
        assertTrue(line, line.startsWith("compression: given up"))
        s.close()
    }

    @Test
    fun `switching it off and on after a give up tries it again`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(Mode.GARBAGE_WHEN_ASKED) { true }))
        server.enqueue(MockResponse().withWebSocketUpgrade(novigSide(Mode.PLAIN) { false }))
        val s = stream()
        s.configureCompression(true)
        s.watch(plan)
        until { s.feedReport().first { it.startsWith("compression:") }.startsWith("compression: given up") && s.live(plan).size == plan.size }
        s.configureCompression(false)
        s.configureCompression(true)
        assertFalse(s.feedReport().first { it.startsWith("compression:") }.startsWith("compression: given up"))
        s.close()
    }
}
