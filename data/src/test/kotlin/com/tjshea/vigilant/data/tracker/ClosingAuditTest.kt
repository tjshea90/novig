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

/** Tj, 2026-10-11: "Fix whatever should be fixed to make it most logical": the real start, the independent close, honest CLV. */
class IndependentCloseTest {
    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()
    private val start = 1_790_000_000_000L
    private val min = 60_000L

    private fun bet(
        id: String = "ind00001", closeFair: Double? = 0.52, seenAt: Long? = start - 2 * min, nowVia: String? = BetTracker.VIA_CNO, source: String = BetTracker.SOURCE_CNO,
        hist: Double? = null, histVia: String? = null, status: BetStatus = BetStatus.WON, createdAt: Long = start - 60 * min, final: Boolean = false,
    ) = TrackedBet(
        id = "$id-0000-0000", createdAtMs = createdAt, league = "NFL", eventName = "A @ B", startsTs = start, marketLabel = "Moneyline", selection = "A", marketId = "m$id", outcomeId = "o$id",
        price = 0.5, cost = 0.5, fairAtBet = 0.51, evPercentAtBet = 0.02, stake = 1.0, status = status, closingFair = closeFair, closingSeenAtMs = seenAt, source = source, nowVia = nowVia,
        closeFair = hist, closeVia = histVia, closeFinal = final,
    )

    private val now = start + 3 * 3_600_000L

    // ---- which close counts ----------------------------------------------------------------------------------

    @Test fun `a sharp book's price at the start is the close when it exists, a read before the start otherwise, ESPN's only when there is no read`() {
        val read = bet()
        assertEquals(ClosingLine.SOURCE_CAPTURED, ClosingLine.closeOf(read, now)!!.second)
        val both = bet(hist = 0.55, histVia = "ParlayAPI · Pinnacle close")
        assertEquals(0.55, ClosingLine.closeOf(both, now)!!.first, 0.0)
        assertEquals("ParlayAPI · Pinnacle close", ClosingLine.closeOf(both, now)!!.second)
        assertEquals("a read stays ahead of ESPN's single soft book", ClosingLine.SOURCE_CAPTURED, ClosingLine.closeOf(bet(hist = 0.55, histVia = "ESPN · DraftKings close"), now)!!.second)
        assertEquals("ESPN's close when there is no read", "ESPN · DraftKings close", ClosingLine.closeOf(bet(closeFair = null, seenAt = null, hist = 0.55, histVia = "ESPN · DraftKings close"), now)!!.second)
        assertTrue(ClosingLine.independent("ESPN · DraftKings close") && ClosingLine.independent("OddsPapi · Pinnacle close"))
        assertFalse(ClosingLine.independent("Novig's last trades (5)") || ClosingLine.independent(ClosingLine.SOURCE_CAPTURED))
    }

    @Test fun `a CNO read, or any read taken early, is a weak close worth upgrading, a fresh Vigilant read is not`() {
        assertTrue(ClosingLine.weakRead(bet()))                                                              // CNO's consensus, 2 min before
        assertTrue(ClosingLine.weakRead(bet(nowVia = BetTracker.VIA_BOTH)))
        assertTrue(ClosingLine.weakRead(bet(nowVia = BetTracker.VIA_VIGILANT, source = BetTracker.SOURCE_VIGILANT, seenAt = start - 9 * min)))   // Vigilant's, but 9 minutes early
        assertFalse(ClosingLine.weakRead(bet(nowVia = BetTracker.VIA_VIGILANT, source = BetTracker.SOURCE_VIGILANT, seenAt = start - 2 * min)))
        assertFalse("no read at all is a missing close, not a weak one", ClosingLine.weakRead(bet(closeFair = null, seenAt = null)))
        assertTrue(ClosingLine.upgradeable(bet(), now))
        assertFalse(ClosingLine.upgradeable(bet(hist = 0.55, histVia = "ParlayAPI · Pinnacle close"), now))
        assertFalse("not before the game", ClosingLine.upgradeable(bet(), start - min))
    }

    // ---- the back-fill asks the sharp sources for a bet that already has a weak read ------------------------------

    private class Fake(override val id: String, override val sharp: Boolean, val answer: (TrackedBet) -> CloseLookup) : CloseSource {
        val asked = ArrayList<String>()
        override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> { asked += bets.map { it.id.take(8) }; return bets.associate { it.id to answer(it) } }
    }

    @Test fun `a bet with a weak read is asked the sharp sources only, keeps both closes, and the sharp one wins`() = kotlinx.coroutines.test.runTest {
        val t = BetTracker(java.io.File(tmp.root, "b.json"), clock = { now })
        t.addAll(listOf(bet("weak0001"), bet("fresh001", nowVia = BetTracker.VIA_VIGILANT, source = BetTracker.SOURCE_VIGILANT), bet("none0001", closeFair = null, seenAt = null)))
        val sharp = Fake("sharp", true) { CloseLookup.Found(0.56, "ParlayAPI · Pinnacle close") }
        val soft = Fake("espn", false) { CloseLookup.Found(0.57, "ESPN · DraftKings close") }
        val r = CloseBackfill(t, listOf(sharp, soft), clock = { now }).run()
        // The weak read and the missing close are asked the sharp source; ESPN is asked only for the bet with no read and finds nothing left to answer.
        assertEquals(setOf("weak0001", "none0001"), sharp.asked.toSet())
        assertTrue("ESPN's was not asked for the bet that has a read", "weak0001" !in soft.asked)
        assertEquals(2, r.found)
        val weak = t.all().first { it.id.startsWith("weak0001") }
        assertEquals(0.52, weak.closingFair!!, 0.0)          // the read is kept…
        assertEquals(0.56, weak.closeFair!!, 0.0)            // …beside the sharp close
        assertEquals(0.56, ClosingLine.closeOf(weak, now)!!.first, 0.0)
        assertEquals(0.52, ClosingLine.closeOf(t.all().first { it.id.startsWith("fresh001") }, now)!!.first, 0.0)
        // Asked once: the next look has nothing to do.
        assertEquals(0, CloseBackfill(t, listOf(sharp, soft), clock = { now + 4 * 3_600_000L }).run().looked)
    }

    @Test fun `a sharp source that never has it ends the look for a weak read without ESPN being asked`() = kotlinx.coroutines.test.runTest {
        val t = BetTracker(java.io.File(tmp.root, "c.json"), clock = { now })
        t.addAll(listOf(bet("weak0002")))
        val sharp = Fake("sharp", true) { CloseLookup.None("Pinnacle didn't post it") }
        val soft = Fake("espn", false) { CloseLookup.Found(0.57, "ESPN · DraftKings close") }
        CloseBackfill(t, listOf(sharp, soft), clock = { now }).run()
        val b = t.all().single()
        assertTrue(soft.asked.isEmpty())
        assertTrue("every source that could be asked said never", b.closeFinal)
        assertNull(b.closeFair)
        assertEquals(0.52, ClosingLine.closeOf(b, now)!!.first, 0.0)
    }

    // ---- CLV stats show the independent figure beside the other ---------------------------------------------

    @Test fun `the closing-line stats count the independent closes apart`() {
        val bets = listOf(bet("a0000001"), bet("a0000002", hist = 0.55, histVia = "ParlayAPI · Pinnacle close"), bet("a0000003", closeFair = null, seenAt = null, hist = 0.51, histVia = "ESPN · DraftKings close"),
            bet("a0000004", closeFair = null, seenAt = null, hist = 0.47, histVia = "Novig's last trades (4)"))
        val s = ClvStats.of(bets, now)
        assertEquals(4, s.closed)
        assertEquals(2, s.independentClosed)
        assertEquals(2, s.independentBeat)                                   // 0.55/0.5-1 and 0.51/0.5-1
        assertEquals(((0.55 / 0.5 - 1) + (0.51 / 0.5 - 1)) / 2, s.independentAverageClv!!, 1e-12)
    }

    // ---- the game's real start ------------------------------------------------------------------------------

    private fun opp(status: String, startsTs: Long, fetchedAt: Long = start - 20 * min) = com.tjshea.vigilant.data.scanner.Opportunity(
        league = com.tjshea.vigilant.data.scanner.Leagues.byNovigName("NFL")!!,
        event = com.tjshea.vigilant.data.novig.NovigEvent("e", "FOOTBALL", "NFL", status, "B @ A", startsTs),
        market = com.tjshea.vigilant.data.novig.NovigMarket("mx", "e", "MONEY", "OPEN", "A", startsTs, com.tjshea.vigilant.engine.MarketFee.GAME, emptyList()),
        outcome = com.tjshea.vigilant.data.novig.NovigOutcome("ox", "A", "TBD"), marketLabel = "Moneyline", kind = com.tjshea.vigilant.data.reference.LineKind.MONEYLINE, selection = "A",
        fair = null, fairProbability = null, quote = null, ladder = emptyList(), depth = null, suggestedStake = null, novigWidth = null,
        bookFetchedAtMs = fetchedAt, fairUpdatedMs = null, refEvent = null, lineKey = null, target = null,
    )

    @Test fun `a game Novig has in play before its listed start takes the moment it was seen, and a delayed game takes Novig's new start`() {
        val b = bet().copy(status = BetStatus.PENDING)
        val early = BetTracker.startChange(b, opp("OPEN_INGAME", start), start - 10 * min)!!
        assertEquals(start - 20 * min, early.startsTs)
        assertEquals(start, early.startsLoggedTs)
        assertTrue(early.startNoteHas("started early"))
        // The logged start is kept once, whatever happens next.
        assertEquals(start, BetTracker.startChange(early.copy(startsTs = start - 5 * min), opp("OPEN_PREGAME", start + 40 * min), start - 30 * min)!!.startsLoggedTs)
        val delayed = BetTracker.startChange(b, opp("OPEN_PREGAME", start + 35 * min), start - 5 * min)!!
        assertEquals(start + 35 * min, delayed.startsTs)
        assertTrue(delayed.startNoteHas("moved +35 min"))
        // Noise and nonsense change nothing.
        assertNull(BetTracker.startChange(b, opp("OPEN_PREGAME", start + 2 * min), start - 5 * min))
        assertNull(BetTracker.startChange(b, opp("OPEN_PREGAME", start + 3 * 24 * 3_600_000L), start - 5 * min))
        assertNull(BetTracker.startChange(b, null, start - 5 * min))
        assertNull("a game already past its listed start", BetTracker.startChange(b, opp("OPEN_INGAME", start), start + 5 * min))
        assertNull("a settled bet", BetTracker.startChange(b.copy(status = BetStatus.WON), opp("OPEN_INGAME", start), start - 10 * min))
        assertNull("a lock", BetTracker.startChange(b.copy(lockFor = "x"), opp("OPEN_INGAME", start), start - 10 * min))
    }

    @Test fun `a delayed game keeps its close open: the read taken for the old start is no longer a close`() {
        val b = bet().copy(status = BetStatus.PENDING)                      // read 2 minutes before the listed start
        val delayed = BetTracker.startChange(b, opp("OPEN_PREGAME", start + 35 * min), start - min)!!
        assertNull("33 minutes before the new start is outside the 15-minute window", ClosingLine.captured(delayed))
        assertTrue(ClosingLine.needsClose(delayed, start - min))
        assertEquals(delayed.startsTs - ClosingLine.LEAD_MS, ClosingLine.nextAt(listOf(delayed), start - min))
    }

    private fun TrackedBet.startNoteHas(s: String) = startsNote!!.contains(s)
}
