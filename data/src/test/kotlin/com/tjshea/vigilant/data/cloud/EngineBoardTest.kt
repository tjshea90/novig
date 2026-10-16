package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the phone's dashboard shows of the engine's bets and bids (Tj, 2026-10-16): one list, both kinds, from the two files the engine publishes. */
class EngineBoardTest {
    private val now = 1_800_000_000_000L

    private fun entry(id: String, note: String, at: Long, status: String = "OPEN", pnl: Double? = null) = TradeEntry(
        id = id, atMs = at, mode = "LIVE", marketId = "m-$id", outcomeId = "o-$id", eventId = "e-$id", event = "A @ B", league = "NBA", kind = "Spreads", marketLabel = "Spreads", selection = "Team $id",
        startsTs = now + 3_600_000L, contracts = 100, avgPrice = 0.50, cost = 0.5, fee = 0.01, fair = 0.52, evPercent = 0.036, stars = null, fairAgeS = 30, books = 5, note = note, status = status, pnl = pnl,
    )

    private fun bid(id: String, status: MakerStatus = MakerStatus.RESTING) = MakerBid(
        clientId = id, orderId = "oid-$id", marketId = "m-$id", eventId = "e", outcomeId = "x-$id", league = "NFL", eventName = "C @ D", startsTs = now + 6 * 3_600_000L, marketLabel = "Passing Yards",
        selection = "Player Over 224.5", price = 0.48, contracts = 50, fair = 0.52, evAtFair = 0.083, margin = 0.04, postedAtMs = now - 60_000L, status = status,
    )

    @Test
    fun `bets, bid fills and resting bids appear together, resting first, and the totals add both`() {
        val ledger = TradeLedger(listOf(entry("1", "kelly", now - 5_000), entry("2", "bid (deeper) · posted at 48c, 50 wanted", now - 2_000), entry("3", "kelly", now - 9_000, "WON", 0.5)))
        val desk = DeskFile(now, "n", "", listOf(bid("b1"), bid("b2", MakerStatus.CANCELED)))
        val st = CloudStatus(heartbeatMs = now, deskActive = true, deskNote = "1 bids resting")
        val b = EngineBoard.of(ledger, desk, st, now)
        assertEquals(1, b.resting.size)
        assertEquals("BID", b.rows[0].kind)
        assertEquals(listOf("BID", "FILL", "BET", "BET"), b.rows.map { it.kind })
        assertEquals(1, b.bets!!.open)
        assertEquals(1, b.fills!!.open)
        assertTrue(b.headline, b.headline.contains("2 open") && b.headline.contains("1 bids resting"))
        assertTrue(b.deskLive)
        assertEquals("edge shows as a percent, not a fraction", true, b.rows[1].sub.contains("edge 3.6%"))
    }

    @Test
    fun `no files yet is an empty board that says so, and a silent engine's desk is not live`() {
        val b = EngineBoard.of(null, null, null, now)
        assertTrue(b.rows.isEmpty())
        assertFalse(b.deskLive)
        assertFalse(EngineBoard.of(null, null, CloudStatus(heartbeatMs = now - 10 * 60_000L, deskActive = true), now).deskLive)
    }

    @Test
    fun `a settled loss is bad and a win is good in the list`() {
        val ledger = TradeLedger(listOf(entry("1", "kelly", now - 5_000, "LOST", -0.5), entry("2", "kelly", now - 6_000, "WON", 0.5)))
        val rows = EngineBoard.of(ledger, null, null, now).rows
        assertEquals(DashboardModel.Tone.BAD, rows[0].tone)
        assertEquals(DashboardModel.Tone.GOOD, rows[1].tone)
        assertTrue(rows[0].sub, rows[0].sub.contains("lost -$0.50"))
    }

    @Test
    fun `the trader counts bid fills as open positions of the same book but never as markets it already bought`() {
        val t = CloudTrader(clock = { now })
        t.load(TradeLedger(listOf(entry("1", "kelly", now - 5_000))))
        t.record(entry("2", "bid · posted", now - 1_000))
        t.record(entry("2", "bid · posted", now - 500).copy(contracts = 120))
        assertEquals(2, t.ledger.entries.size)
        assertEquals("a grown fill replaces the entry", 120L, t.ledger.entries.single { it.id == "2" }.contracts)
        assertEquals(setOf("m-1"), t.openMarkets(now))
    }
}
