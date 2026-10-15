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
    fun `a runner whose address was refused is replaced after a few minutes, not at once`() {
        var refused = false
        val w = AddressWatch(t0) { refused }
        refused = true
        assertFalse("too early: a transient refusal passes", w.shouldHandOver(min(1)))
        assertTrue("refused and past the minimum age", w.shouldHandOver(min(5)))
        assertEquals(1, w.count)
    }

    @Test
    fun `a runner Novig accepted is never replaced, and a late refusal is not the runner's address`() {
        val w = AddressWatch(t0, previousCount = 3, previousAtMs = t0 - 600_000L) { false }
        assertFalse(w.shouldHandOver(min(5)))
        assertFalse(w.shouldHandOver(min(20)))
        assertEquals("a job that got through its first minutes ends the streak", 0, w.count)
        var late = false
        val w2 = AddressWatch(t0) { late }
        late = true
        assertFalse("a refusal at minute 30 is not the runner's address", w2.shouldHandOver(min(30)))
    }

    @Test
    fun `five replacements in a row, then one an hour`() {
        var refused = true
        val w = AddressWatch(t0, previousCount = 5, previousAtMs = t0 - 10 * 60_000L) { refused }
        assertFalse("five in a row and the last was 10 minutes ago: public routes carry on", w.shouldHandOver(min(5)))
        val later = AddressWatch(t0, previousCount = 5, previousAtMs = t0 - 61 * 60_000L) { refused }
        assertTrue("an hour after the last one, one more try", later.shouldHandOver(min(5)))
        assertEquals(6, later.count)
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
