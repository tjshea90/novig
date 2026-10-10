package com.tjshea.vigilant.data.livebid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Why so few fills" reads only what the bids recorded: where each sat, how long it lived, how it ended. */
class LiveBidReportTest {
    private fun bid(i: Int, price: Double, best: Double?, filled: Long = 0L, status: LiveBidStatus = LiveBidStatus.EXPIRED, lifeMs: Long = 20_000L, why: String? = null) = LiveBid(
        clientId = "c$i", mode = LiveBid.MODE_REAL, marketId = "m$i", eventId = "e$i", outcomeId = "o$i", league = "NBA", eventName = "A @ B", startsTs = 0L, marketLabel = "Moneyline",
        selection = "A", price = price, contracts = 10, fair = price * 1.05, ev = 0.05, bestBid = best, postedAtMs = 1_000L, openAtMs = 1_000L, expiresAtMs = 1_000L + lifeMs,
        status = if (filled > 0) LiveBidStatus.FILLED else status, filled = filled, endedAtMs = 1_000L + lifeMs, why = why,
    )

    @Test
    fun `a bid's place is read from its price against the best bid when it went up`() {
        assertEquals(LiveBidReport.Place.LED, LiveBidReport.place(bid(1, 0.50, 0.48)))
        assertEquals(LiveBidReport.Place.LED, LiveBidReport.place(bid(2, 0.50, null)))
        assertEquals(LiveBidReport.Place.JOINED, LiveBidReport.place(bid(3, 0.50, 0.50)))
        assertEquals(LiveBidReport.Place.BEHIND, LiveBidReport.place(bid(4, 0.50, 0.53)))
    }

    @Test
    fun `no bids yet says what the looks ended in`() {
        val out = LiveBidReport.whyFew(emptyList(), mapOf("Pinnacle margin too wide" to 12, "tennis is off" to 40))
        assertTrue(out.single().contains("tennis is off ×40"))
    }

    @Test
    fun `too few bids is said plainly and not guessed at`() {
        val out = LiveBidReport.whyFew((1..4).map { bid(it, 0.50, 0.48) }, emptyMap())
        assertTrue(out.last().contains("too few"))
    }

    @Test
    fun `bids that sit behind the book and rarely fill are named as the cause when the leading ones fill more`() {
        val behind = (1..12).map { bid(it, 0.50, 0.55) }
        val led = (13..16).map { bid(it, 0.50, 0.48, filled = if (it <= 14) 10 else 0) }
        val out = LiveBidReport.whyFew(behind + led, emptyMap())
        assertTrue(out.first { it.startsWith("Where bids sat") }.contains("behind the best bid 12 (0 filled"))
        assertTrue(out.any { it.contains("median 5¢ under the best bid") })
        assertTrue(out.last(), out.last().startsWith("Reading: most bids sit behind"))
    }

    @Test
    fun `bids pulled within seconds are blamed on the pulls`() {
        val pulled = (1..12).map { bid(it, 0.50, 0.48, status = LiveBidStatus.CANCELED, lifeMs = 6_000L, why = "the score changed") }
        val out = LiveBidReport.whyFew(pulled, emptyMap())
        assertTrue(out.any { it.contains("Pulled for: the score changed ×12") })
        assertTrue(out.last(), out.last().contains("coming down after about 6 s"))
    }

    @Test
    fun `a healthy fill rate is called healthy`() {
        val bids = (1..10).map { bid(it, 0.50, 0.48, filled = if (it <= 3) 10 else 0) }
        assertTrue(LiveBidReport.whyFew(bids, emptyMap()).last().contains("healthy"))
    }

    @Test
    fun `the Diagnostics lines carry the reading`() {
        val lines = LiveBidReport.lines((1..12).map { bid(it, 0.50, 0.48) }, LiveBidDeskStatus())
        assertTrue(lines.any { it == "Why so few fills:" })
        assertTrue(lines.any { it.trim().startsWith("Reading:") })
    }
}
