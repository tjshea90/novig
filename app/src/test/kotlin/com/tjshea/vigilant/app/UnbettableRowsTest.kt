package com.tjshea.vigilant.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-23: a bet the slip finds unbettable leaves the list at once and returns only when a newer price read lists it again. */
class UnbettableRowsTest {
    private val now = SampleScan.NOW

    @Test
    fun `a row the slip found unbettable stays off the list however long, until a newer price read lists it`() {
        val s = SampleScan.state()
        val row = s.feedAt(now).first { it.bookFetchedAtMs != null }
        val found = (row.bookFetchedAtMs ?: now) + 1_000L
        val flagged = s.copy(negativeSeen = mapOf(row.key to found))
        assertFalse(flagged.feedAt(now).any { it.key == row.key })
        assertFalse("ten minutes later it is still off: nothing newer was read", flagged.feedAt(now + 600_000L).any { it.key == row.key })
        val reread = flagged.copy(feed = flagged.feed.map { if (it.key == row.key) it.copy(bookFetchedAtMs = found + 5_000L) else it })
        assertTrue("a newer read lists it again", reread.feedAt(now).any { it.key == row.key })
    }
}
