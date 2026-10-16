package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class EngineHoursTest {
    private fun et(y: Int, mo: Int, d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(y, mo, d, h, m, 0, 0, EngineHours.ZONE).toInstant().toEpochMilli()

    @Test
    fun `open from 08-00 inclusive through midnight to 01-00 exclusive Eastern`() {
        assertFalse(EngineHours.isOpen(et(2026, 10, 15, 7, 59)))
        assertTrue(EngineHours.isOpen(et(2026, 10, 15, 8, 0)))
        assertTrue(EngineHours.isOpen(et(2026, 10, 15, 22, 0)))
        assertTrue(EngineHours.isOpen(et(2026, 10, 15, 23, 59)))
        assertTrue(EngineHours.isOpen(et(2026, 10, 15, 0, 0)))
        assertTrue(EngineHours.isOpen(et(2026, 10, 15, 0, 59)))
        assertFalse(EngineHours.isOpen(et(2026, 10, 15, 1, 0)))
        assertFalse(EngineHours.isOpen(et(2026, 10, 15, 4, 0)))
    }

    @Test
    fun `the window follows daylight saving, at both changes`() {
        // 2026-11-01 clocks go back (EDT -> EST): 08:00 EST is 13:00 UTC, in summer 12:00 UTC
        assertEquals(java.time.Instant.parse("2026-10-31T12:00:00Z").toEpochMilli(), EngineHours.nextOpenAtMs(et(2026, 10, 31, 3)))
        assertEquals(java.time.Instant.parse("2026-11-01T13:00:00Z").toEpochMilli(), EngineHours.nextOpenAtMs(et(2026, 11, 1, 3)))
        // 2026-03-08 clocks go forward
        assertTrue(EngineHours.isOpen(java.time.Instant.parse("2026-03-08T12:00:00Z").toEpochMilli()))   // 08:00 EDT
        assertFalse(EngineHours.isOpen(java.time.Instant.parse("2026-03-08T11:59:00Z").toEpochMilli()))
        assertTrue(EngineHours.isOpen(java.time.Instant.parse("2026-03-09T04:59:00Z").toEpochMilli()))   // 00:59 EDT
        assertFalse(EngineHours.isOpen(java.time.Instant.parse("2026-03-09T05:00:00Z").toEpochMilli()))  // 01:00 EDT
    }

    @Test
    fun `closesAt and nextOpenAt`() {
        val noon = et(2026, 10, 15, 12)
        assertEquals(et(2026, 10, 16, 1), EngineHours.closesAtMs(noon))
        assertEquals(et(2026, 10, 16, 1), EngineHours.closesAtMs(et(2026, 10, 15, 23)))
        assertEquals("after midnight it closes this morning", et(2026, 10, 16, 1), EngineHours.closesAtMs(et(2026, 10, 16, 0, 30)))
        assertNull(EngineHours.closesAtMs(et(2026, 10, 15, 3)))
        assertEquals(noon, EngineHours.nextOpenAtMs(noon))
        assertEquals(et(2026, 10, 15, 8), EngineHours.nextOpenAtMs(et(2026, 10, 15, 2)))
        assertEquals(et(2026, 10, 15, 8), EngineHours.nextOpenAtMs(et(2026, 10, 15, 1)))
    }
}
