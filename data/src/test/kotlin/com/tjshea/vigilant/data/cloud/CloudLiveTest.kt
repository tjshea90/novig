package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLiveTest {
    @Test
    fun `the quick tunnel's address is found in cloudflared's log`() {
        val log = "2026-10-17T07:00:00Z INF Requesting new quick Tunnel on trycloudflare.com...\n2026-10-17T07:00:02Z INF |  https://random-words-here-1234.trycloudflare.com  |\n"
        assertEquals("https://random-words-here-1234.trycloudflare.com", CloudLive.tunnelUrl(log))
        assertNull(CloudLive.tunnelUrl("INF Requesting new quick Tunnel"))
        assertNull("an api host is not the tunnel", CloudLive.tunnelUrl("https://api.trycloudflare.com/tunnel"))
    }

    @Test
    fun `the address carries the run's token and only a long hex token is valid`() {
        assertEquals("https://a-b.trycloudflare.com/s/0123456789abcdef01234567", CloudLive.url("https://a-b.trycloudflare.com/", "0123456789abcdef01234567"))
        assertTrue(CloudLive.validToken("0123456789abcdef01234567"))
        assertFalse(CloudLive.validToken("0123456789abcdef0123456"))
        assertFalse(CloudLive.validToken("0123456789ABCDEF01234567"))
        assertFalse(CloudLive.validToken("../../etc/passwd/xxxxxxxxxxxx"))
    }

    @Test
    fun `the event reader joins data lines into a message at the blank line and ignores pings`() {
        val r = CloudLive.SseReader()
        assertNull(r.feed(": ping")); assertNull(r.feed("data: {\"a\":1}"))
        assertEquals("{\"a\":1}", r.feed(""))
        assertNull(r.feed("")); assertNull(r.feed("event: x")); assertNull(r.feed("data: one")); assertNull(r.feed("data: two"))
        assertEquals("one\ntwo", r.feed(""))
    }

    @Test
    fun `the chip says live only while a direct reading is fresh`() {
        assertEquals("LIVE (direct)", CloudLive.chip(10_000L, 12_000L))
        assertTrue(CloudLive.chip(10_000L, 16_000L).startsWith("via GitHub"))
        assertTrue(CloudLive.chip(0L, 1_000L).startsWith("via GitHub"))
    }
}
