package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.tracker.BetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TradeSettleTest {
    private fun entry(id: String, market: String, status: String = "OPEN", contracts: Long = 200, cost: Double = 1.0) = TradeEntry(
        id = id, atMs = 1, mode = "PAPER", marketId = market, outcomeId = "o-$market", eventId = "e", event = "A @ B", league = "NBA", kind = "Spread", marketLabel = "Spread", selection = "A -3.5", startsTs = 2,
        contracts = contracts, avgPrice = 0.5, cost = cost, fee = 0.0, fair = 0.55, evPercent = 0.1, stars = 4.0, fairAgeS = 1, books = 5, note = "", status = status,
    )

    @Test
    fun `a win pays a cent a contract less the cost, a loss loses the cost, a push or void gives it back`() {
        val rows = listOf(entry("w", "m1"), entry("l", "m2"), entry("p", "m3"), entry("v", "m4"))
        val results = mapOf("m1" to BetStatus.WON, "m2" to BetStatus.LOST, "m3" to BetStatus.PUSH, "m4" to BetStatus.VOID)
        val s = TradeSettle.settle(rows) { m, _ -> results[m]?.let { TradeSettle.Outcome(it) } }.associateBy { it.id }
        assertEquals(1.0, s.getValue("w").pnl, 1e-9)      // 200 contracts pay $2.00 on a $1.00 cost
        assertEquals("WON", s.getValue("w").status)
        assertEquals(-1.0, s.getValue("l").pnl, 1e-9)
        assertEquals(0.0, s.getValue("p").pnl, 1e-9)
        assertEquals("VOID", s.getValue("v").status)
    }

    @Test
    fun `a fair-market settlement pays its value per contract`() {
        val s = TradeSettle.settle(listOf(entry("f", "m1"))) { _, _ -> TradeSettle.Outcome(BetStatus.FMV, 0.75) }.single()
        assertEquals(0.5, s.pnl, 1e-9)      // 200 contracts x 1c x 0.75 = $1.50, cost $1.00
    }

    @Test
    fun `entries without a result, already settled ones and a pending bet are left alone, and an unconfirmed order is settled like an open one`() {
        val rows = listOf(entry("a", "m1"), entry("b", "m2", status = "WON"), entry("c", "m3"), entry("u", "m4", status = "UNCONFIRMED"))
        val s = TradeSettle.settle(rows) { m, _ -> when (m) { "m3" -> TradeSettle.Outcome(BetStatus.PENDING); "m4" -> TradeSettle.Outcome(BetStatus.LOST); else -> null } }
        assertEquals(listOf("u"), s.map { it.id })
        assertNull(s.firstOrNull { it.id == "a" })
    }

    @Test
    fun `the results file survives JSON`() {
        val f = TradeSettle.File(5L, listOf(TradeSettle.Settled("x", "WON", 1.5)))
        assertEquals(f, TradeSettle.File.parse(TradeSettle.File.text(f)))
        assertNull(TradeSettle.File.parse("nonsense"))
    }
}
