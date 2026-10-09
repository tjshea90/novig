package com.tjshea.vigilant.data.scanner

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SGO Pro mode (Tj, 2026-10-09): SGO's ~5-minute pregame refresh is allowed up to a 10-minute guard; off, the rule is untouched. */
class SgoFreshnessTest {
    private val now = 1_000_000_000L
    private val soon = now + 60 * 60_000L

    @After fun reset() { Freshness.sgoMode = false }

    @Test fun off_a_near_game_keeps_the_five_minute_limit() {
        Freshness.sgoMode = false
        assertEquals(5 * 60_000L, Freshness.maxAgeMs(soon, now))
        assertFalse(Freshness.fresh(now - 6 * 60_000L, now, soon))
        assertEquals("over 5 minutes, or 10 for games more than 3 hours away", Freshness.LIMIT_TEXT)
    }

    @Test fun on_a_near_game_takes_sgo_six_minute_prices_but_drops_ten_minute_ones() {
        Freshness.sgoMode = true
        assertTrue(Freshness.fresh(now - 6 * 60_000L, now, soon))
        assertTrue(Freshness.fresh(now - 9 * 60_000L, now, soon))
        assertFalse(Freshness.fresh(now - 10 * 60_000L - 1, now, soon))
        assertEquals(10 * 60_000L, Freshness.maxAgeMs(null, now))
    }
}
