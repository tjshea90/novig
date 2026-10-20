package com.tjshea.vigilant.app.kalshi

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalshiMoneyTest {
    private val now = 1_800_000_000_000L
    private fun bet(id: String, status: BetStatus, stake: Double = 10.0, cost: Double = 0.5, createdAt: Long = now - 60_000L, settledAt: Long? = null) =
        TrackedBet(id, createdAt, "NFL", "A @ B", now + 3_600_000L, "Moneyline", "A", "m", "o", cost, cost, 0.55, 0.05, stake, status, settledAtMs = settledAt)

    @Test
    fun `counts open, settled and today's Kalshi bets only from the Kalshi record`() {
        val ui = KalshiUi(bets = listOf(bet("1", BetStatus.PENDING, 10.0), bet("2", BetStatus.WON, 10.0, 0.5, settledAt = now), bet("3", BetStatus.LOST, 4.0, 0.5, settledAt = now - 5 * 86_400_000L, createdAt = now - 6 * 86_400_000L)))
        val m = KalshiMoney.of(ui, now)
        assertEquals(1, m.open)
        assertEquals(10.0, m.atRisk, 1e-9)
        assertEquals(1, m.won)
        assertEquals(1, m.lost)
        assertEquals(10.0 - 4.0, m.settledProfit, 1e-9)
        assertEquals(10.0, m.profitToday, 1e-9)
    }

    @Test
    fun `bids say why they are not resting when the switches are off`() {
        val m = KalshiMoney.of(KalshiUi(), now)
        assertEquals(0, m.bidsResting)
        assertTrue(m.bidLine.contains("off"))
    }
}
