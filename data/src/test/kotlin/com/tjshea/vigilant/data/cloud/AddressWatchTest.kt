package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-15: "If novig refuses a runner's address, it should find a way to reconnect or try a different runner automatically." Run 38673681637 (RESTRICTED_NETWORK_REGION) read 1,442 books a pass instead of 10,060. */
class AddressWatchTest {
    private val t0 = 1_000_000L
    private fun min(m: Int) = t0 + m * 60_000L

    @Test
    fun `a runner whose address was refused is replaced at once`() {
        var refused = false
        val w = AddressWatch(t0) { refused }
        refused = true
        assertFalse("not before the first request was made", w.shouldHandOver(t0 + 2_000L))
        assertTrue("refused and past the first seconds", w.shouldHandOver(t0 + 11_000L))
        assertEquals(1, w.count)
    }

    @Test
    fun `a runner Novig accepted is never replaced`() {
        val w = AddressWatch(t0, previousCount = 3, previousAtMs = t0 - 600_000L) { false }
        assertFalse(w.shouldHandOver(min(5)))
        assertFalse(w.shouldHandOver(7 * 60 * 60_000L + t0))
        assertEquals("a job that got through its window ends the streak", 0, w.count)
    }

    @Test
    fun `every refusal is replaced, with no cap and no cooldown, until a runner is accepted`() {
        val refused = true
        for (streak in listOf(5, 6, 20, 100)) {
            val w = AddressWatch(t0, previousCount = streak, previousAtMs = t0 - 5_000L) { refused }
            assertTrue("streak $streak: the next refused runner is replaced too", w.shouldHandOver(t0 + 11_000L))
            assertEquals(streak + 1, w.count)
        }
    }

    @Test
    fun `only the address verdicts count, never the device check or a bad key`() {
        assertTrue(AddressWatch.isAddressRefusal("Novig places this network's internet address outside the states it serves (RESTRICTED_NETWORK_REGION). Settings"))
        assertTrue(AddressWatch.isAddressRefusal("Novig lists the internet address ... as a VPN or proxy (ANONYMIZED_NETWORK), so the key can't read here."))
        assertFalse(AddressWatch.isAddressRefusal("Novig HTTP 451 GEOLOCATION_EXPIRED: open the Novig app"))
        assertFalse(AddressWatch.isAddressRefusal("Novig HTTP 401 UNAUTHORIZED"))
        assertFalse(AddressWatch.isAddressRefusal(null))
    }
}
