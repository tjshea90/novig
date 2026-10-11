package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.engine.Devig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fixes from the closing-odds audit (research/closing_audit_2026-10-11.md), so the closing odds and CLV in the bet files can be trusted. */
class ClosingAuditTest {
    private val start = 1_790_000_000_000L
    private val min = 60_000L

    private fun bet(
        createdAt: Long = start - 60 * min, closeFair: Double? = 0.52, seenAt: Long? = start - 2 * min, status: BetStatus = BetStatus.WON, source: String = BetTracker.SOURCE_CNO,
        nowVia: String? = BetTracker.VIA_CNO, closeHist: Double? = null, closeVia: String? = null, novigClose: Double? = null, novigCloseAt: Long? = null,
    ) = TrackedBet(
        id = "audit001-0000-0000", createdAtMs = createdAt, league = "NFL", eventName = "A @ B", startsTs = start, marketLabel = "Moneyline", selection = "A", marketId = "m", outcomeId = "o",
        price = 0.5, cost = 0.5, fairAtBet = 0.51, evPercentAtBet = 0.02, stake = 1.0, status = status, settledAtMs = start + 3_600_000L, closingFair = closeFair, closingSeenAtMs = seenAt,
        source = source, american = 100, nowVia = nowVia, closeFair = closeHist, closeVia = closeVia, novigClose = novigClose, novigCloseAtMs = novigCloseAt,
    )

    private val now = start + 3 * 3_600_000L

    // ---- the devig method of a close found afterwards ---------------------------------------------------------

    @Test fun `a close found afterwards is devigged by the conservative worst case like the fair at bet, not multiplicatively`() {
        val mine = 1.0 / 3.6      // +260
        val other = 3.2 / 4.2     // -320
        val mult = Devig.multiplicative(listOf(mine, other))[0]
        val worst = CloseDevig.fair(mine, other)
        assertEquals(Devig.worstCase(listOf(mine, other))[0], worst, 1e-12)
        assertTrue("longshot: multiplicative $mult vs worst case $worst", mult - worst > 0.01)
        assertTrue(worst <= Devig.power(listOf(mine, other))[0] + 1e-12)
        // A pair with no margin is normalised; a symmetric pair is 50/50.
        assertEquals(0.5, CloseDevig.fair(0.5, 0.5), 1e-12)
        assertEquals(0.4, CloseDevig.fair(0.36, 0.54), 1e-12)
    }

    // ---- a close is not the bet's own line ---------------------------------------------------------------------

    @Test fun `a read seconds after a late bet is its own line, not the close`() {
        val late = bet(createdAt = start - 100_000L, seenAt = start - 90_000L)
        assertNull(ClosingLine.captured(late))
        assertNull(ClosingLine.clv(late, now))
        assertTrue("the capture keeps looking", ClosingLine.needsClose(late.copy(status = BetStatus.PENDING), start - 95_000L))
        // 60 s later it counts.
        assertNotNull(ClosingLine.captured(bet(createdAt = start - 10 * min, seenAt = start - 8 * min)))
        assertNull(ClosingLine.captured(bet(createdAt = start - 10 * min, seenAt = start - 10 * min + 30_000L)))
    }

    // ---- what the file says about each close -------------------------------------------------------------------

    @Test fun `a read close says what it is made of, when it was read and how long after the bet`() {
        val r = BetHistory.row(bet(), null, now)
        assertEquals("read:cno", r.closeBasis)
        assertEquals(false, r.closeIndependent)
        assertEquals(120L, r.closeLeadSec)
        assertEquals(58 * 60L, r.closeReadAfterBetSec)
        assertEquals("read:vigilant", BetHistory.row(bet(source = BetTracker.SOURCE_VIGILANT, nowVia = BetTracker.VIA_VIGILANT), null, now).closeBasis)
        assertEquals("read:both", BetHistory.row(bet(nowVia = BetTracker.VIA_BOTH), null, now).closeBasis)
    }

    @Test fun `a close found afterwards has no read time and is independent when it is Pinnacle's`() {
        val h = bet(closeFair = null, seenAt = null, closeHist = 0.55, closeVia = "ParlayAPI · Pinnacle close")
        val r = BetHistory.row(h, null, now)
        assertEquals("history:pinnacle", r.closeBasis)
        assertEquals(true, r.closeIndependent)
        assertNull(r.closeReadMs)
        assertNull(r.closeLeadSec)
        assertNull(r.closeReadAfterBetSec)
        assertEquals(0.55, r.closeFair!!, 1e-12)
        val trades = BetHistory.row(bet(closeFair = null, seenAt = null, closeHist = 0.55, closeVia = "Novig's last trades (5)"), null, now)
        assertEquals("history:novig-trades", trades.closeBasis)
        assertEquals(false, trades.closeIndependent)
        assertEquals("history:espn", BetHistory.row(bet(closeFair = null, seenAt = null, closeHist = 0.55, closeVia = "ESPN · DraftKings close"), null, now).closeBasis)
    }

    @Test fun `a voided bet has no close and no CLV in the file, as in every in-app stat`() {
        val r = BetHistory.row(bet(status = BetStatus.VOID), null, now)
        assertNull(r.clv)
        assertNull(r.closeFair)
        assertNull(r.closeOdds)
        assertNull(r.beatClose)
        assertEquals("No true close yet", BetDims.valueOf(bet(status = BetStatus.VOID), BetDim.CLOSE_SOURCE, now))
    }

    @Test fun `Novig's own last price counts only when it was read in the last 15 minutes before the start`() {
        val fresh = BetHistory.row(bet(novigClose = 0.53, novigCloseAt = start - 4 * min), null, now)
        assertNotNull(fresh.novigCloseOdds)
        assertEquals(240L, fresh.novigCloseAgeSec)
        val old = BetHistory.row(bet(novigClose = 0.53, novigCloseAt = start - 9 * 3_600_000L), null, now)
        assertNull(old.novigCloseOdds)
        assertNull(old.novigCloseAgeSec)
        assertFalse(BetHistory.line(old).contains("novigClose"))
    }

    @Test fun `an unproven group is not called best or worst`() {
        // 25 closed bets are under the 30 needed.
        val few = (1..25).map { bet().copy(id = "f%07d-0000-0000".format(it), createdAtMs = start - (30 + it) * min) }
        assertTrue(BetSmartFilters.findings(few, now).first.isEmpty())
    }
}
