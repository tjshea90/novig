package com.tjshea.vigilant.data.novig.trading.maker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj's 2026-10-13 diagnostics: maker.json was 22 MB with hundreds of small bids a day. An ended bid that never filled leaves after 6 hours; a filled one stays a week; one still resting never leaves. */
class MakerKeepTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun bid(status: MakerStatus, endedAgoH: Long?, filled: Long = 0, postedAgoH: Long = 50) = MakerBid(
        clientId = "c", marketId = "m", eventId = "e", outcomeId = "o", league = "NFL", eventName = "A @ B", startsTs = now, marketLabel = "Total", selection = "Over 40.5",
        price = 0.45, contracts = 10, fair = 0.48, evAtFair = 0.03, margin = 0.03, postedAtMs = now - postedAgoH * hour, status = status, filled = filled,
        endedAtMs = endedAgoH?.let { now - it * hour },
    )

    @Test fun `ended unfilled bids go after six hours, filled ones after a week, open ones never`() {
        val ended = MakerStatus.entries.first { it.ended }
        val open = MakerStatus.entries.first { !it.ended }
        assertFalse(MakerStore.expired(bid(ended, endedAgoH = 5), now))
        assertTrue(MakerStore.expired(bid(ended, endedAgoH = 7), now))
        assertFalse("a filled bid is kept a week", MakerStore.expired(bid(ended, endedAgoH = 48, filled = 3), now))
        assertTrue(MakerStore.expired(bid(ended, endedAgoH = 8 * 24, filled = 3), now))
        assertFalse("a bid still resting is never dropped", MakerStore.expired(bid(open, endedAgoH = null, postedAgoH = 100), now))
    }
}
