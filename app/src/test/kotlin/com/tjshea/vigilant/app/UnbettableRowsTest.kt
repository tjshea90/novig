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

    @Test
    fun `an order that found nobody selling, or a refusal that says the bet is gone, takes the row off the list`() {
        assertTrue(UNBETTABLE_WORDS.containsMatchIn("Novig has closed this market."))
        assertTrue(UNBETTABLE_WORDS.containsMatchIn("The edge is gone: Novig's best price is now +120."))
        assertTrue(UNBETTABLE_WORDS.containsMatchIn("A sharp book prices both sides of this bet and says it is not +EV at Novig's price, so nothing is bet on it."))
        assertTrue(UNBETTABLE_WORDS.containsMatchIn("Nobody is offering this side on Kalshi right now."))
        assertFalse("a limit the user set is not the bet's fault", UNBETTABLE_WORDS.containsMatchIn("That's over your \$10 limit per bet (Settings)."))
        assertFalse(UNBETTABLE_WORDS.containsMatchIn("Pick an amount to bet."))
    }
}
