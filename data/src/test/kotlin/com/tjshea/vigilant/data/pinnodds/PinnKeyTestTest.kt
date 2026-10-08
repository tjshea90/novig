package com.tjshea.vigilant.data.pinnodds

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnKeyTestTest {
    private val server = MockWebServer()

    @After
    fun stop() = server.shutdown()

    /** The shape of the real answer (2026-10-08), with the account's email left out. */
    private fun me(wsActive: Boolean = true, wsUntil: Long = 1_791_675_281_105L, label: String = "Trial · 3-day full demo") =
        """{"id":1,"api_key_prefix":"uq8xqStT","plan":{"id":"trial_demo","label":"$label","limits":{"perSec":10},"days":3,"price":0},"plan_expires_at":1791675281105,"status":"active",
        "usage":{"sec":1,"min":1,"hour":1,"day":3},"ws_addon":{"eligible":false,"active":$wsActive,"until":$wsUntil,"monthly_usd":99}}"""

    @Test
    fun `a good key says the plan, when it ends and that the websocket is on`() {
        val r = PinnKeyTest.parse(200, me()) as PinnKeyTest.Result.Ok
        assertEquals("Trial · 3-day full demo", r.planLabel)
        assertEquals(1_791_675_281_105L, r.expiresAtMs)
        assertEquals(10, r.perSecond)
        assertEquals(3, r.usedToday)
        assertTrue(r.canStream(1_791_420_000_000L))
        assertFalse("after the add-on's end it cannot stream", r.canStream(1_791_675_281_106L))
        val text = r.summary(1_791_420_000_000L)
        assertTrue(text, text.startsWith("Pinnodds accepted the key: Trial · 3-day full demo"))
        assertTrue(text.contains("WebSocket add-on is ON"))
        assertFalse("never the account's email", text.contains("@"))
    }

    @Test
    fun `a key without the websocket add-on says the live feed cannot connect`() {
        val r = PinnKeyTest.parse(200, me(wsActive = false)) as PinnKeyTest.Result.Ok
        assertFalse(r.canStream(1_791_420_000_000L))
        assertTrue(r.summary(1_791_420_000_000L).contains("WebSocket add-on is OFF"))
    }

    @Test
    fun `a wrong key, a rate limit and an odd answer are told apart`() {
        assertTrue((PinnKeyTest.parse(401, """{"error":"invalid_key"}""") as PinnKeyTest.Result.Bad).message.contains("does not know this key"))
        assertTrue((PinnKeyTest.parse(429, """{"error":"rate_limited"}""") as PinnKeyTest.Result.Bad).message.contains("rate limiting"))
        assertTrue((PinnKeyTest.parse(500, "oops") as PinnKeyTest.Result.Bad).message.contains("500"))
        assertTrue((PinnKeyTest.parse(200, "<html>") as PinnKeyTest.Result.Bad).message.contains("200"))
    }

    @Test
    fun `the test is one GET to the panel with the key in a header, never in the URL`() = runTest {
        server.enqueue(MockResponse().setBody(me()))
        server.start()
        val r = PinnKeyTest.run(OkHttpClient(), "  secretkey123  ", baseUrl = server.url("/").toString().trimEnd('/'))
        assertTrue(r is PinnKeyTest.Result.Ok)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/panel/api/me", req.path)
        assertEquals("secretkey123", req.getHeader("x-api-key"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an empty key is refused before any request, and an unreachable server is said so`() = runTest {
        assertTrue((PinnKeyTest.run(OkHttpClient(), "   ") as PinnKeyTest.Result.Bad).message.contains("Paste"))
        server.start()
        val url = server.url("/").toString().trimEnd('/')
        server.shutdown()
        assertTrue((PinnKeyTest.run(OkHttpClient(), "abc12345", baseUrl = url) as PinnKeyTest.Result.Bad).message.contains("could not be reached"))
    }
}
