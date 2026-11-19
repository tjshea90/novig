package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MKV (Tj, 2026-11-18: live at small limits): the maker's own fills as Novig's executions answer them, the profit of each, and the loss stop built on them. */
class RfqLedgerTest {
    private val now = 1_790_500_000_000L
    private fun item(rfq: String, price: String, cost: String, user: String, status: String, result: String? = null, fmv: String? = null, settledAgoH: Double? = null) =
        """{"rfqId":"$rfq","instrumentId":"i","price":"$price","cost":"$cost","userCost":"$user","status":"$status","outcomeIds":["a","b"],"ts":${now - 5 * 3_600_000L},""" +
            (result?.let { """"result":"$it",""" } ?: "") + (fmv?.let { """"fmvValue":"$it",""" } ?: "") + (settledAgoH?.let { """"settledTs":${now - (it * 3_600_000L).toLong()},""" } ?: "") + """"x":1}"""
    private fun page(vararg items: String) = """{"items":[${items.joinToString(",")}],"next":"n"}"""

    @Test fun openParlaysCarryTheirCollateralOnEveryLegAndSettledOnesCarryNone() {
        val execs = RfqLedger.parse(page(item("r1", "0.4", "15", "10", "open"), item("r2", "0.5", "10", "10", "open"), item("r3", "0.4", "99", "10", "settled", "loss", settledAgoH = 1.0)))
        assertEquals(listOf("a", "b"), execs.first().outcomeIds)
        val m = RfqLedger.exposure(execs)
        assertEquals("both open parlays are on a and b: 15 + 10; the settled one is gone", mapOf("a" to 25.0, "b" to 25.0), m)
    }

    @Test fun theDocumentedItemsParseAndAnythingElseReadsAsNone() {
        val e = RfqLedger.parse(page(item("r1", "0.4", "15", "10", "settled", "loss", settledAgoH = 2.0), item("r2", "0.4", "15", "10", "open"))).let { it.first() to it.last() }
        assertEquals(15.0, e.first.cost, 0.0); assertEquals(10.0, e.first.userCost, 0.0); assertTrue(e.first.settled); assertEquals("loss", e.first.result); assertTrue(!e.second.settled)
        assertTrue(RfqLedger.parse("""{"items":"no"}""").isEmpty()); assertTrue(RfqLedger.parse("not json").isEmpty()); assertTrue(RfqLedger.parse("""{"items":[{"rfqId":"x"}]}""").isEmpty())
    }

    @Test fun theMakersProfitFollowsThePotAndTheScalar() {
        fun pnl(result: String, fmv: String? = null) = RfqLedger.pnl(RfqLedger.parse(page(item("r", "0.4", "15", "10", "settled", result, fmv, 1.0))).single())
        assertEquals("the user lost: the maker keeps the stake", 10.0, pnl("loss")!!, 1e-9)
        assertEquals("the user won: the maker pays its collateral", -15.0, pnl("win")!!, 1e-9)
        assertEquals("a void returns both sides' money", 0.0, pnl("push")!!, 1e-9)
        assertEquals("settled at 0.5: the user gets half the $25 pot, the maker $12.50 for its $15", -2.5, pnl("fmv", "0.5")!!, 1e-9)
        assertNull("an fmv result with no value is not guessed", pnl("fmv"))
        assertNull("open", RfqLedger.pnl(RfqLedger.parse(page(item("r", "0.4", "15", "10", "open"))).single()))
    }

    @Test fun theSummaryCountsWinsLossesOpenAndTheLastDay() {
        val s = RfqLedger.summarize(RfqLedger.parse(page(
            item("a", "0.4", "15", "10", "settled", "loss", settledAgoH = 2.0),
            item("b", "0.4", "15", "10", "settled", "win", settledAgoH = 3.0),
            item("c", "0.4", "15", "10", "settled", "win", settledAgoH = 30.0),
            item("d", "0.4", "15", "10", "open"),
        )), now)
        assertEquals(3, s.settled); assertEquals(1, s.makerWon); assertEquals(2, s.makerLost); assertEquals(1, s.open); assertEquals(15.0, s.openCollateral, 0.0)
        assertEquals(10.0 - 15.0 - 15.0, s.pnlTotal, 1e-9); assertEquals("only the settled in the last 24 hours", 10.0 - 15.0, s.pnl24h, 1e-9)
    }

    @Test fun theLossStopHaltsLiveQuotingAndTellsTjButNeverShadow() {
        val told = ArrayList<String>(); val rows = ArrayList<RfqRow>()
        val live = RfqConfig(mode = RfqConfig.Mode.LIVE, dayLossStop = 20.0)
        val maker = RfqMaker({ live }, { null }, { 0.0 }, { true }, { rows += it }, { t, _ -> told += t }, { now })
        maker.onLedger(RfqLedger.Summary(4, 2, 2, 1, 15.0, -5.0, -19.99), live)
        assertNull("19.99 lost is under the 20 stop", maker.halted)
        maker.onLedger(RfqLedger.Summary(5, 2, 3, 1, 15.0, -20.0, -20.0), live)
        assertNotNull(maker.halted); assertTrue(maker.halted!!, maker.halted!!.contains("lost 20.00")); assertEquals(1, told.size); assertTrue(told.single().contains("stopped"))
        val snap = maker.snapshot(true, live, "mk", 15.0, "", now)
        assertEquals(-20.0, snap.realizedPnl24h!!, 1e-9); assertEquals(3, snap.makerLost)
        val shadow = RfqMaker({ live.copy(mode = RfqConfig.Mode.SHADOW) }, { null }, { 0.0 }, { true }, { }, { _, _ -> }, { now })
        shadow.onLedger(RfqLedger.Summary(5, 0, 5, 0, 0.0, -50.0, -50.0), live.copy(mode = RfqConfig.Mode.SHADOW))
        assertNull("SHADOW quotes nothing, so there is nothing to stop", shadow.halted)
        val off = RfqMaker({ live.copy(dayLossStop = 0.0) }, { null }, { 0.0 }, { true }, { }, { _, _ -> }, { now })
        off.onLedger(RfqLedger.Summary(5, 0, 5, 0, 0.0, -50.0, -50.0), live.copy(dayLossStop = 0.0))
        assertNull("a stop of 0 is no stop", off.halted)
    }
}
