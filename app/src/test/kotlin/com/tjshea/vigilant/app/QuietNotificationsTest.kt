package com.tjshea.vigilant.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-09: only money notifications on request, and the open bids on every notification. */
class QuietNotificationsTest {
    @After fun reset() { NotifyGate.quiet = false }

    @Test fun off_everything_posts() {
        NotifyGate.quiet = false
        assertTrue(NotifyGate.allow()); assertTrue(NotifyGate.allow(money = true))
    }

    @Test fun on_only_money_posts() {
        NotifyGate.quiet = true
        assertFalse(NotifyGate.allow()); assertTrue(NotifyGate.allow(money = true))
    }

    @Test fun the_header_says_how_many_bids_are_up() {
        assertEquals("Wallet \$123.08 · 8 bids up (\$9.86)", WalletNote.withBids("Wallet \$123.08", 8, 9.86))
        assertEquals("Wallet \$5.00 · 1 bid up (\$2.00)", WalletNote.withBids("Wallet \$5.00", 1, 2.0))
        assertEquals("Wallet \$5.00 · no bids up", WalletNote.withBids("Wallet \$5.00", 0, 0.0))
    }
}
