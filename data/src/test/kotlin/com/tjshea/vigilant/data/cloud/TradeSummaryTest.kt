package com.tjshea.vigilant.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TradeSummaryTest {
    private val et = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 10, 15, 15, 0, 0, 0, et).toInstant().toEpochMilli()
    private val yesterday = ZonedDateTime.of(2026, 10, 14, 20, 0, 0, 0, et).toInstant().toEpochMilli()

    private fun entry(id: String, at: Long, cost: Double, status: String, pnl: Double? = null) = TradeEntry(
        id = id, atMs = at, mode = "paper", marketId = "m", outcomeId = "o", eventId = "e", event = "A @ B", league = "NBA", kind = "SPREAD", marketLabel = "Spread", selection = "A -3.5",
        startsTs = at + 3_600_000L, contracts = 10, avgPrice = 0.5, cost = cost, fee = 0.0, fair = 0.55, evPercent = 4.0, stars = 3.0, fairAgeS = 20, books = 4, note = "", status = status, pnl = pnl,
    )

    @Test
    fun `totals count open, won, lost and pushed, profit overall and today in Eastern time, and ROI over settled stakes`() {
        val l = TradeLedger(entries = listOf(
            entry("1", now - 3_600_000L, 5.0, "WON", 4.5), entry("2", now - 7_200_000L, 5.0, "LOST", -5.0), entry("3", yesterday, 10.0, "WON", 9.0),
            entry("4", now - 600_000L, 8.0, "OPEN"), entry("5", yesterday, 2.0, "PUSH", 0.0),
        ))
        val s = TradeSummary.of(l, now)
        assertEquals(1, s.open); assertEquals(8.0, s.atRisk, 1e-9)
        assertEquals(2, s.won); assertEquals(1, s.lost); assertEquals(1, s.pushed)
        assertEquals(8.5, s.pnl, 1e-9); assertEquals(-0.5, s.pnlToday, 1e-9)
        assertEquals(8.5 / 20.0, s.roi!!, 1e-9)   // the push's stake is not counted
        assertEquals(3, s.ordersToday)
        assertEquals("4", s.newest.first().id)
        assertTrue(s.line(), s.line().startsWith("1 open (\$8.00 at risk) · 2 won · 1 lost · 1 push · +\$8.50 settled, -\$0.50 today"))
    }

    @Test
    fun `an empty ledger is all zeros and has no ROI`() {
        val s = TradeSummary.of(TradeLedger(), now)
        assertEquals(0, s.open); assertNull(s.roi); assertTrue(s.newest.isEmpty())
    }

    @Test
    fun `the status carries the wallet and its sleep note through the file`() {
        val st = CloudStatus(walletSleep = "the trading wallet has \$0.12", walletCash = 0.12, walletEquity = 3.5, walletReadAtMs = 5L)
        val back = CloudStatus.parse(CloudStatus.text(st))
        assertNotNull(back); assertEquals(0.12, back!!.walletCash, 1e-9); assertEquals(3.5, back.walletEquity, 1e-9); assertEquals(st.walletSleep, back.walletSleep)
        assertEquals("an older engine's status has no wallet", -1.0, CloudStatus.parse("{\"version\":1}")!!.walletCash, 0.0)
    }
}
