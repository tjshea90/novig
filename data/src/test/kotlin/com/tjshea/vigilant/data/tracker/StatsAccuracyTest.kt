package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.engine.Fees
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tj, 2026-10-02 ~21:15Z: "Run full tests on this app, make sure all the math is right and that the stats and closing lines are gathered correctly and
 * reflect accurate data." Each test here is a fix the full test found (TASKS.md BA), checked to fail before it.
 */
class StatsAccuracyTest {

    private val start = 1_800_000_000_000L
    private var now = start - 3_600_000L

    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })

    private fun bet(id: String, cost: Double = 0.40, status: BetStatus = BetStatus.PENDING, lockFor: String? = null, starts: Long = start) = TrackedBet(
        id = id, createdAtMs = start - 86_400_000L, league = "NFL", eventName = "A @ B", startsTs = starts, marketLabel = "Moneyline", selection = "A",
        marketId = "m1", outcomeId = "m1-A", price = cost, cost = cost, fairAtBet = 0.42, evPercentAtBet = 0.05, stake = 4.0, status = status,
        orderId = "o-$id", contracts = 1000, paid = 4.0, fee = 0.0, lockFor = lockFor,
    )

    @Test
    fun `F1 a live CNO bet with no fair on record backs its fair out of the EV at its cost, fee included, not its price`() = runBlocking {
        // +110 live: price 0.476…, Novig's live fee on top. CNO's EV (net of the fee) 3%: fair = 1.03 × cost.
        val row = CnoRow(0.03, startsAtMs = start - 600_000L, league = "NFL", event = "A @ B", market = "Moneyline", bet = "A", odds = 110, book = "Novig")
        val b = tracker().logCno(row, ev = 0.03, live = true, placedKey = "k")
        val price = 1.0 / 2.1
        val cost = price + Fees.takerFee(price, MarketFee.GAME, eventLive = true)
        assertEquals(cost, b.cost, 1e-12)
        assertEquals(1.03 * cost, b.fairAtBet!!, 1e-12)
        // So the EV it was logged with is the EV its own fair gives at its cost.
        assertEquals(b.evPercentAtBet!!, b.fairAtBet!! / b.cost - 1.0, 1e-12)
    }

    @Test
    fun `F2 a lock has no closing line value, counts in no CLV stat, and no capture or look-up is spent on it`() {
        val after = start + 600_000L
        val closed = { b: TrackedBet -> b.copy(closingFair = 0.50, closingSeenAtMs = start - 120_000L) }
        val pick = closed(bet("p"))
        val lock = closed(bet("l", cost = 0.55, lockFor = "p").copy(outcomeId = "m1-B"))
        assertEquals(0.50 / 0.40 - 1.0, ClosingLine.clv(pick, after)!!, 1e-12)
        assertNull(ClosingLine.clv(lock, after))
        val s = ClvStats.of(listOf(pick, lock), after)
        assertEquals(1, s.closed)
        assertEquals(0, s.missed)
        // Before the start: no capture work and no alarm for the lock.
        val early = start - 5 * 60_000L
        assertTrue(ClosingLine.needsClose(bet("p"), early))
        assertFalse(ClosingLine.needsClose(bet("l", lockFor = "p"), early))
        assertEquals(listOf("p"), ClosingLine.due(listOf(bet("p"), bet("l", lockFor = "p")), early).map { it.id })
        // After it: no back-fill look (ParlayAPI's credits) for the lock.
        val later = start + 3_600_000L
        assertTrue(CloseBackfill.due(bet("p"), later))
        assertFalse(CloseBackfill.due(bet("l", lockFor = "p"), later))
    }

    @Test
    fun `F3 a close merged from Vigilant's read is dated by its oldest book price, as a scan's is, not by when it was saved`() = runBlocking {
        // Read 12 minutes before the start, from book prices 5 minutes older: 17 minutes before the start, not a true close.
        now = start - 12 * 60_000L
        val asOf = now - 5 * 60_000L
        val saved = File.createTempFile("bets", ".json").also { it.delete() }
        val t2 = BetTracker(saved, clock = { now })
        t2.logCno(CnoRow(0.03, startsAtMs = start, league = "NFL", event = "A @ B", market = "Moneyline", bet = "A", odds = 150, book = "Novig", fairProbability = 0.42), 0.03, false, "k")
        val id = t2.all().single().id
        t2.edit(id) { it.copy(vigFair = 0.45, vigAtMs = now, vigAsOfMs = asOf) }
        t2.mergeReads(listOf(id), since = now - 1_000L)
        val b = t2.all().single()
        assertEquals(asOf, b.closingSeenAtMs)
        assertNull(ClosingLine.captured(b))
    }

    @Test
    fun `F4 the Check odds counter leaves lock bets out`() {
        val since = now - 60_000L
        val read = { b: TrackedBet, ev: Double -> b.copy(nowEv = ev, nowAtMs = now) }
        val s = CheckOddsStats.of(listOf(read(bet("p"), 0.03), read(bet("l", lockFor = "p"), -0.04)), since, now)
        assertEquals(1, s.positive)
        assertEquals(0, s.negative)
        assertEquals(0.03, s.averageEv!!, 1e-12)
    }

    @Test
    fun `F7 the Novig-only view keeps a close found in Novig's own trade history, and drops other books' closes`() {
        val after = start + 600_000L
        val novigClose = bet("n", status = BetStatus.WON).copy(closeFair = 0.44, closeVia = "Novig's last trades (12)")
        val pinnacle = bet("p", status = BetStatus.WON).copy(closeFair = 0.44, closeVia = "ParlayAPI · Pinnacle close")
        val v = NovigNow.view(listOf(novigClose, pinnacle)).associateBy { it.id }
        assertEquals(0.44 / 0.40 - 1.0, ClosingLine.clv(v.getValue("n"), after)!!, 1e-12)
        assertNull(ClosingLine.clv(v.getValue("p"), after))
    }

    @Test
    fun `F9 a pushed API bet gives back what it paid, not the fee, so its profit is minus the fee`() {
        val live = bet("x").copy(stake = 4.10, paid = 4.0, fee = 0.10, cost = 0.41, status = BetStatus.PUSH)
        assertEquals(-0.10, live.profit!!, 1e-12)
        // A pregame (fee-free) push and a ✓ bet (nothing paid on record) are $0.
        assertEquals(0.0, bet("y", status = BetStatus.PUSH).profit!!, 1e-12)
        assertEquals(0.0, bet("z", status = BetStatus.PUSH).copy(paid = null, orderId = null).profit!!, 1e-12)
    }
}
