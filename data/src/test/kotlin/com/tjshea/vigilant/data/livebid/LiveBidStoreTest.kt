package com.tjshea.vigilant.data.livebid

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real file store (the desk's own tests use an in-memory one): a bid written to disk reads back the same, and a missing file is an empty list. */
class LiveBidStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun bid() = LiveBid(
        clientId = "c1", mode = LiveBid.MODE_PAPER, marketId = "m", eventId = "e", outcomeId = "o", pinnEventId = 1L, league = "NBA", eventName = "A @ B", startsTs = 1L, marketLabel = "Moneyline",
        selection = "B", price = 0.475, contracts = 10L, fair = 0.5, ev = 0.05, mid = 0.5, bestBid = 0.45, offer = 0.55, leads = true, overround = 0.04, pinnLimit = 1000.0, feeCoefficient = 0.03, makerCredit = 0.5,
        score = "0-0", clock = null, rules = "r", preset = "Balanced", postedAtMs = 1L, activeFromMs = 1L, expiresAtMs = 2L, status = LiveBidStatus.RESTING, openAtMs = 1L,
    )

    @Test
    fun `a missing file reads as nothing, and what is written reads back`() = runTest {
        val store = LiveBidStore(java.io.File(tmp.root, "live-bids.json"))
        assertEquals(emptyList<LiveBid>(), store.all())
        store.replace(listOf(bid()))
        assertEquals(listOf(bid()), LiveBidStore(java.io.File(tmp.root, "live-bids.json")).all())
    }
}
