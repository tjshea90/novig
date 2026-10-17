package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.cloud.CloudLive
import com.tjshea.vigilant.data.cloud.CloudStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LiveStatusServerTest {
    private val token = "0123456789abcdef01234567"

    private fun get(url: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 3_000; c.readTimeout = 3_000
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to body
    }

    @Test
    fun `serves the current status at its token path and nothing else`() {
        var beat = 1L
        val s = LiveStatusServer(token, { CloudStatus(runId = "r1", heartbeatMs = beat++) })
        val port = s.start()
        try {
            val (code, body) = get("http://127.0.0.1:$port/s/$token")
            assertEquals(200, code)
            assertEquals("r1", CloudStatus.parse(body)!!.runId)
            val second = CloudStatus.parse(get("http://127.0.0.1:$port/s/$token").second)!!
            assertTrue("each reading is rebuilt, not cached", second.heartbeatMs > CloudStatus.parse(body)!!.heartbeatMs)
            assertEquals(404, get("http://127.0.0.1:$port/s/wrong").first)
            assertEquals(404, get("http://127.0.0.1:$port/").first)
            assertEquals(404, get("http://127.0.0.1:$port/s/${token}x").first)
        } finally { s.stop() }
    }

    @Test
    fun `the stream sends a reading a second as server-sent events the phone's reader can parse`() {
        val s = LiveStatusServer(token, { CloudStatus(runId = "r2") }, everyMs = 50L)
        val port = s.start()
        try {
            val c = URL("http://127.0.0.1:$port/s/$token/stream").openConnection() as HttpURLConnection
            c.readTimeout = 3_000
            assertEquals("text/event-stream", c.contentType)
            val r = CloudLive.SseReader()
            val messages = ArrayList<String>()
            c.inputStream.bufferedReader().useLines { lines -> for (l in lines) { r.feed(l)?.let { messages += it }; if (messages.size >= 3) break } }
            assertEquals(3, messages.size)
            assertEquals("r2", CloudStatus.parse(messages[0])!!.runId)
        } finally { s.stop() }
    }

    @Test
    fun `a bad token is refused before anything starts`() {
        val bad = runCatching { LiveStatusServer("short", { null }).start() }
        assertTrue(bad.isFailure)
        assertNull(null)
    }
}
