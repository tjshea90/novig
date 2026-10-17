package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovigErrorWatchTest {
    private val bad = listOf("Novig HTTP 502 (bad_gateway)")

    @Test
    fun `a server error starts a short retry, the message waits until the outage is ten minutes old, then says so once`() {
        val w = NovigErrorWatch()
        assertNull(w.observe(bad, 0L))
        assertTrue(w.active)
        assertEquals(10_000L, w.backoffMs())
        assertNull(w.observe(bad, 5 * 60_000L))
        assertEquals(NovigErrorWatch.Event.ALERT, w.observe(bad, 10 * 60_000L))
        assertNull("only once", w.observe(bad, 11 * 60_000L))
        assertEquals("doubles to the cap", NovigErrorWatch.MAX_MS, w.backoffMs())
    }

    @Test
    fun `recovery is said only after an alert, and clears the streak`() {
        val w = NovigErrorWatch()
        w.observe(bad, 0L); w.observe(bad, 11 * 60_000L)
        assertEquals(NovigErrorWatch.Event.RECOVERED, w.observe(emptyList(), 12 * 60_000L))
        assertFalse(w.active)
        w.observe(bad, 13 * 60_000L)
        assertNull("a short blip that never alerted is not announced", w.observe(emptyList(), 13 * 60_000L + 5_000L))
    }

    @Test
    fun `only server errors count, an address or key refusal and a rate limit do not`() {
        val w = NovigErrorWatch()
        assertNull(w.observe(listOf("Novig HTTP 451 (ANONYMIZED_NETWORK)", "Novig is rate-limiting this device (HTTP 429)", "Novig's edge blocked the request (HTTP 403)"), 0L))
        assertFalse(w.active)
        w.observe(listOf("Novig HTTP 504"), 1L)
        assertTrue(w.active)
    }
}
