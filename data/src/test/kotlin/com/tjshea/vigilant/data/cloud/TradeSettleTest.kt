package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.tracker.BetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `entries without a result and a pending bet are left out, an unconfirmed order is settled like an open one, and a settled one gives the same answer again`() {
        val rows = listOf(entry("a", "m1"), entry("b", "m2", status = "WON"), entry("c", "m3"), entry("u", "m4", status = "UNCONFIRMED"))
        val s = TradeSettle.settle(rows) { m, _ -> when (m) { "m2" -> TradeSettle.Outcome(BetStatus.WON); "m3" -> TradeSettle.Outcome(BetStatus.PENDING); "m4" -> TradeSettle.Outcome(BetStatus.LOST); else -> null } }
        assertEquals(listOf("b", "u"), s.map { it.id })
        assertNull(s.firstOrNull { it.id == "a" })
    }

    @Test
    fun `the results file survives JSON`() {
        val f = TradeSettle.File(5L, listOf(TradeSettle.Settled("x", "WON", 1.5)))
        assertEquals(f, TradeSettle.File.parse(TradeSettle.File.text(f)))
        assertNull(TradeSettle.File.parse("nonsense"))
    }

    /** PX14: orders the engine settled from Novig's ledger (the study never logged them) are carried by the grader's file, never lost. */
    @Test
    fun `an order the engine settled from the ledger is carried, never lost`() {
        val now = 10 * TradeSettle.ASK_AFTER_START_MS
        val started = { e: TradeEntry -> e.copy(startsTs = now - TradeSettle.ASK_AFTER_START_MS) }
        val rows = listOf(started(entry("studied", "m1")), started(entry("missed", "m2")), entry("early", "m3").copy(startsTs = now - 60_000L), started(entry("done", "m4", status = "WON")).copy(pnl = 1.0))
        val studied = { m: String, _: String -> m == "m1" }
        assertEquals(listOf(TradeSettle.Settled("done", "WON", 1.0)), TradeSettle.carried(rows, studied))
        // a carried order that the study grades later is settled from the study, never twice
        assertTrue(TradeSettle.carried(rows) { _, _ -> true }.isEmpty())
    }

    @Test
    fun `the subaccount's ledger settles a live order the study missed (PX14)`() {
        val h = 3_600_000L
        val now = 100 * h
        fun live(id: String, market: String, outcome: String = "o-$market", startsTs: Long = now - 8 * h, order: String? = "ord-$id") =
            entry(id, market).copy(mode = "LIVE", outcomeId = outcome, startsTs = startsTs, orderId = order)
        fun pay(ref: String, amount: Double) = com.tjshea.vigilant.data.novig.trading.LedgerRow("t-$ref-$amount", "SETTLEMENT", amount, ref, now)
        val none = emptyList<com.tjshea.vigilant.data.novig.trading.NovigPosition>()
        // 200 contracts win $2.00 on a $1.00 cost
        assertEquals(listOf(TradeSettle.Settled("w", "WON", 1.0)), TradeSettle.fromLedger(listOf(live("w", "m1")), listOf(pay("m1", 2.0)), none, now))
        assertEquals(listOf(TradeSettle.Settled("p", "PUSH", 0.0)), TradeSettle.fromLedger(listOf(live("p", "m1")), listOf(pay("m1", 1.0)), none, now))
        // an odd payout is a fair value only when the row names this order; one naming the market may be the phone's order on it
        assertEquals(listOf(TradeSettle.Settled("f", "FMV", -0.2)), TradeSettle.fromLedger(listOf(live("f", "m1")), listOf(pay("ord-f", 0.8)), none, now))
        assertTrue(TradeSettle.fromLedger(listOf(live("f", "m1")), listOf(pay("m1", 0.8)), none, now).isEmpty())
        // no payout and nothing held 6 h+ after the start: a loss; still held, too soon, or positions unread: nothing
        assertEquals(listOf(TradeSettle.Settled("l", "LOST", -1.0)), TradeSettle.fromLedger(listOf(live("l", "m1")), emptyList(), none, now))
        assertTrue(TradeSettle.fromLedger(listOf(live("l", "m1")), emptyList(), listOf(com.tjshea.vigilant.data.novig.trading.NovigPosition("m1", "o-m1", 200, 1.0)), now).isEmpty())
        assertTrue(TradeSettle.fromLedger(listOf(live("l", "m1", startsTs = now - 4 * h)), emptyList(), none, now).isEmpty())
        assertTrue(TradeSettle.fromLedger(listOf(live("l", "m1")), emptyList(), null, now).isEmpty())
        // both sides held: told apart only by a payout that is exactly one side's win; silence is never a loss there
        val a = live("a", "m2", "oa"); val b = live("b", "m2", "ob").copy(contracts = 300)
        assertEquals(setOf(TradeSettle.Settled("a", "WON", 1.0), TradeSettle.Settled("b", "LOST", -1.0)), TradeSettle.fromLedger(listOf(a, b), listOf(pay("m2", 2.0)), none, now).toSet())
        assertTrue(TradeSettle.fromLedger(listOf(a, b), emptyList(), none, now).isEmpty())
        // paper orders and games under 3 h old are never asked
        assertTrue(TradeSettle.fromLedger(listOf(entry("x", "m3").copy(startsTs = now - 8 * h)), listOf(pay("m3", 2.0)), none, now).isEmpty())
        assertTrue(TradeSettle.fromLedger(listOf(live("y", "m3", startsTs = now - h)), listOf(pay("m3", 2.0)), none, now).isEmpty())
    }
}
