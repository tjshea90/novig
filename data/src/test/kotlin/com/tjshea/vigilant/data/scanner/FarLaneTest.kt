package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quick scan between full-reach ones (Tj, 2026-10-14: "The scan takes way too long": ten days was ~7,700 Novig prices and 8 minutes). */
class FarLaneTest {
    private val now = 1_800_000_000_000L
    private val bidding = ScanSettings(maker = true, daysAhead = 10, vigGuards = ScanGuards(bidDeploy = true, bidFarLane = true))

    @Test
    fun `the first background scan is full, the ones after it quick until a full one is due`() {
        assertFalse("none yet in this process: full", FarLane.nearOnly(bidding, background = true, now = now, lastFullAtMs = 0L))
        assertTrue(FarLane.nearOnly(bidding, background = true, now = now + 60_000, lastFullAtMs = now))
        assertTrue(FarLane.nearOnly(bidding, background = true, now = now + FarLane.FAR_EVERY_MS - 1, lastFullAtMs = now))
        assertFalse("a full one is due", FarLane.nearOnly(bidding, background = true, now = now + FarLane.FAR_EVERY_MS, lastFullAtMs = now))
    }

    @Test
    fun `Tj's own scan, bids off, the switch off or a short reach are always full`() {
        val later = now + 60_000
        assertFalse(FarLane.nearOnly(bidding, background = false, now = later, lastFullAtMs = now))
        assertFalse(FarLane.nearOnly(bidding.copy(maker = false), background = true, now = later, lastFullAtMs = now))
        assertFalse(FarLane.nearOnly(bidding.copy(vigGuards = bidding.vigGuards.copy(bidFarLane = false)), background = true, now = later, lastFullAtMs = now))
        assertFalse(FarLane.nearOnly(bidding.copy(vigGuards = bidding.vigGuards.copy(bidDeploy = false)), background = true, now = later, lastFullAtMs = now))
        assertFalse("two days is already the quick reach", FarLane.nearOnly(bidding.copy(daysAhead = 2), background = true, now = later, lastFullAtMs = now))
    }

    @Test
    fun `a quick scan reads 48 hours, a full one the whole reach`() {
        assertEquals(240, bidding.scanWindowHours)
        assertEquals(FarLane.NEAR_HOURS, bidding.copy(nearScan = true).scanWindowHours)
        assertEquals("never past the reach itself", 24, bidding.copy(nearScan = true, daysAhead = 1).scanWindowHours)
        assertFalse("never saved", kotlinx.serialization.json.Json.encodeToString(ScanSettings.serializer(), bidding.copy(nearScan = true)).contains("nearScan"))
    }
}
