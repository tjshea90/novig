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

    @After fun reset() { Freshness.sgoMode = false; Freshness.plainFastFeeds = false; Freshness.sgoMaxAgeMs = 10 * 60_000L }

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

    /** PX9 (Tj 2026-10-25, "Why are the odds this stale if they are streamed?"): the relaxed limit was SportsGameOdds'; PropLine's and ParlayAPI's quotes had it too, 15 minutes with Tj's setting. */
    @Test fun on_only_sgo_quotes_get_the_long_limit_and_a_fast_feed_keeps_the_plain_one() {
        Freshness.sgoMode = true
        Freshness.plainFastFeeds = true
        Freshness.sgoMaxAgeMs = 15 * 60_000L
        val far = now + 5 * 3_600_000L
        // SGO's own quotes: up to the guard, near or far.
        assertTrue(Freshness.fresh(now - 12 * 60_000L, now, soon, "sgo"))
        assertTrue(Freshness.fresh(now - 12 * 60_000L, now, soon, "sgo-props"))
        assertFalse(Freshness.fresh(now - 16 * 60_000L, now, soon, "sgo"))
        // A fast feed: 5 minutes near the start, 10 for a game over 3 hours away, whatever the switch says.
        for (feed in listOf("propline", "propline_props", "propline_1h", "parlay", "parlay_props", "kalshi", "oddsapi")) {
            assertTrue(feed, Freshness.fresh(now - 4 * 60_000L, now, soon, feed))
            assertFalse(feed, Freshness.fresh(now - 6 * 60_000L, now, soon, feed))
            assertTrue(feed, Freshness.fresh(now - 9 * 60_000L, now, far, feed))
            assertFalse(feed, Freshness.fresh(now - 11 * 60_000L, now, far, feed))
        }
        // No source named (the whole fair, a bid's life: it may hold SGO's quotes): the switch's limit, as before.
        assertTrue(Freshness.fresh(now - 12 * 60_000L, now, soon))
        assertEquals(15 * 60_000L, Freshness.maxAgeMs(soon, now))
        assertEquals(5 * 60_000L, Freshness.maxAgeMs(soon, now, "propline"))
    }

    /** Until the census has said what the plain limit costs, the switch is off and a named fast feed keeps the long limit exactly as before. */
    @Test fun the_plain_limit_for_fast_feeds_is_off_by_default() {
        Freshness.sgoMode = true
        Freshness.sgoMaxAgeMs = 15 * 60_000L
        assertEquals(15 * 60_000L, Freshness.maxAgeMs(soon, now, "propline"))
        assertTrue(Freshness.fresh(now - 12 * 60_000L, now, soon, "propline"))
    }

    @Test fun off_a_named_feed_is_untouched() {
        Freshness.sgoMode = false
        assertEquals(5 * 60_000L, Freshness.maxAgeMs(soon, now, "sgo"))
        assertEquals(5 * 60_000L, Freshness.maxAgeMs(soon, now, "propline"))
    }
}
