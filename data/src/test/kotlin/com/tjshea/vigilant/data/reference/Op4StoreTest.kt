package com.tjshea.vigilant.data.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Tj's 2026-10-13 diagnostics: oddspapi_free_cache.json was 17 MB, rewritten whole on every change and held in memory whole. Entries nobody will read again are dropped. */
class Op4StoreTest {
    @Test fun `whole-league and window entries expire on their own, the month-long ones stay`() {
        var now = 1_000_000_000_000L
        val file = java.io.File.createTempFile("op4", ".json")
        val store = Op4Store(file) { now }
        store.put("markets", "[1]")
        store.put("tournaments|1", "[2]")
        store.put("fxall|7", "x".repeat(1000))
        store.put("fx|7|3", "y".repeat(1000))
        store.put("fair|5", "3")
        assertEquals(5, store.size)
        now += 7 * 3_600_000L
        store.put("fxall|8", "z")
        assertNull("a whole-league answer older than 6 h is gone", store.get("fxall|7", Long.MAX_VALUE))
        assertNotNull(store.get("fx|7|3", Long.MAX_VALUE))
        now += 25 * 3_600_000L
        store.put("fxall|9", "w")
        assertNull("a window older than a day is gone", store.get("fx|7|3", Long.MAX_VALUE))
        assertNotNull(store.get("markets", Long.MAX_VALUE))
        // A file saved with old entries is cleaned when it is opened.
        now += 3 * OddsPapi4Client.DAY_MS
        val reopened = Op4Store(file) { now }
        assertNull(reopened.get("fxall|9", Long.MAX_VALUE))
        assertNull(reopened.get("fair|5", Long.MAX_VALUE))
        assertNotNull(reopened.get("tournaments|1", Long.MAX_VALUE))
        assertEquals(2, reopened.size)
        file.delete()
    }
}
