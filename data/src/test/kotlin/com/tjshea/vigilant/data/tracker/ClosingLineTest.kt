package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * True closing line value (Tj, 2026-09-29): the close is the fair line read in the last 15 minutes before the start, final once the game has
 * started; the capture wakes before each start and retries a failed read; the CLV stats run over every bet ever, with Tj's periods and
 * outlier switch.
 */
class ClosingLineTest {

    private val min = 60_000L
    private val start = 1_800_000_000_000L

    /** A bet at cost 0.50 placed a day before [starts], its last pregame read [closingFair] at [seenBefore] before the start. */
    private fun bet(
        id: String = "b",
        closingFair: Double? = 0.52,
        seenBefore: Long? = 5 * min,
        starts: Long = start,
        placed: Long = starts - 24 * 60 * min,
        status: BetStatus = BetStatus.PENDING,
        tried: Long? = null,
        evAtBet: Double? = 0.03,
    ) = TrackedBet(
        id, placed, "NBA", "A @ B", starts, "Moneyline", "A", "m-$id", "o-$id", 0.5, 0.5, 0.515, evAtBet, 10.0,
        status = status, closingFair = closingFair, closingSeenAtMs = seenBefore?.let { starts - it }, closeTriedAtMs = tried,
    )

    // ---- what a true close is --------------------------------------------------------------------------------------

    @Test
    fun `a read in the last 15 minutes is the close, final once the game starts`() {
        val b = bet(seenBefore = 5 * min)
        assertNull("not final before the start", ClosingLine.clv(b, start - 1))
        assertEquals(0.52 / 0.5 - 1, ClosingLine.clv(b, start)!!, 1e-12)
        assertEquals(0.52 / 0.5 - 1, ClosingLine.clv(b, start + 3 * 3_600_000L)!!, 1e-12)
        assertEquals(0.52 / 0.5 - 1, ClosingLine.clv(bet(seenBefore = 15 * min), start)!!, 1e-12)
    }

    @Test
    fun `a read hours before the start is not a close`() {
        assertNull(ClosingLine.clv(bet(seenBefore = 16 * min), start + min))
        assertNull(ClosingLine.clv(bet(seenBefore = 6 * 60 * min), start + min))
        assertNull(ClosingLine.clv(bet(closingFair = null, seenBefore = null), start + min))
    }

    /** Tj, 2026-10-01: "properly tracking clv based on real closing lines". The line a bet was placed at is not a closing line. */
    @Test
    fun `a bet placed in the last minutes with nothing read since has no close, never its own line, and a live bet has none either`() {
        val late = bet(closingFair = null, seenBefore = null, placed = start - 4 * min)
        assertNull("its fair at bet (0.515) would make CLV equal its own EV at bet", ClosingLine.closeOf(late, start))
        assertNull(ClosingLine.clv(late, start))
        assertNull(ClosingLine.clv(late, start + 3 * 60 * min))
        assertNull(ClosingLine.clv(bet(placed = start + min), start + 2 * min))
        // It counts as started without a close (the back-fill looks for the real one), not as one that beat it.
        val s = ClvStats.of(listOf(late), now = start + min)
        assertEquals(0, s.closed)
        assertEquals(1, s.missed)
        assertTrue("so the back-fill is asked for the real close", CloseBackfill.due(late, start + 11 * min))
        // A history close (ParlayAPI's Pinnacle, ESPN, Novig's trades) is the close when one is found.
        val found = late.copy(closeFair = 0.53, closeVia = "ESPN")
        assertEquals(0.53 / 0.5 - 1, ClosingLine.clv(found, start + 11 * min)!!, 1e-12)
        assertFalse(CloseBackfill.due(found, start + 11 * min))
    }

    @Test
    fun `beating the close is a price better than the closing fair odds`() {
        // Bet at 0.50 (+100); the close moved to 0.55 fair (−122): the price was 10% better than the close.
        assertEquals(0.10, ClosingLine.clv(bet(closingFair = 0.55), start)!!, 1e-12)
        // The close drifted to 0.45 fair: 10% worse.
        assertEquals(-0.10, ClosingLine.clv(bet(closingFair = 0.45), start)!!, 1e-12)
    }

    // ---- the capture's schedule ------------------------------------------------------------------------------------

    @Test
    fun `the capture wakes 6 minutes before the next start and reads what starts within 12`() {
        val far = bet("far", closingFair = null, seenBefore = null, starts = start + 60 * min)
        val soon = bet("soon", closingFair = null, seenBefore = null)
        val bets = listOf(far, soon)
        assertEquals(start - 6 * min, ClosingLine.nextAt(bets, start - 3 * 60 * min))
        assertEquals(listOf("soon"), ClosingLine.due(bets, start - 6 * min).map { it.id })
        assertEquals(listOf("soon"), ClosingLine.due(bets, start - 12 * min).map { it.id })
        assertTrue(ClosingLine.due(bets, start - 13 * min).isEmpty())
    }

    @Test
    fun `a bet already read in the window, settled, or placed live needs no capture`() {
        val now = start - 10 * min
        assertFalse(ClosingLine.needsClose(bet(seenBefore = 11 * min), now))
        assertTrue("a read 14 minutes out is a close, but a closer one is still taken", ClosingLine.needsClose(bet(seenBefore = 14 * min), now))
        assertFalse(ClosingLine.needsClose(bet(closingFair = null, seenBefore = null, status = BetStatus.VOID), now))
        assertFalse(ClosingLine.needsClose(bet(closingFair = null, seenBefore = null, placed = start + 1), start + 2))
        // A read inside the last 3 minutes needs nothing more; one 8 minutes out is read once more near the start (below).
        assertNull(ClosingLine.nextAt(listOf(bet(seenBefore = 2 * min)), now))
        assertEquals(start - ClosingLine.FINAL_LEAD_MS, ClosingLine.nextAt(listOf(bet(seenBefore = 8 * min)), now))
    }

    /** A read 6 minutes out is a close, but not the real one: a second read about 2 minutes before the start replaces it. */
    @Test
    fun `a bet read 6 minutes before its start is read once more about 2 minutes before, and that read is the close`() {
        val first = bet(seenBefore = 6 * min, closingFair = 0.52, tried = start - 6 * min)
        assertTrue(ClosingLine.needsFinalRead(first, start - 5 * min))
        assertFalse("not a first read", ClosingLine.needsClose(first, start - 5 * min))
        assertEquals(start - ClosingLine.FINAL_LEAD_MS, ClosingLine.nextAt(listOf(first), start - 5 * min))
        assertTrue("too early for the final read", ClosingLine.due(listOf(first), start - 5 * min).isEmpty())
        assertTrue(ClosingLine.due(listOf(first), start - 4 * min).isEmpty())
        assertEquals(listOf("b"), ClosingLine.due(listOf(first), start - ClosingLine.FINAL_LEAD_MS).map { it.id })
        // The worker may start a little late: still due until the last minute, never in it.
        assertEquals(listOf("b"), ClosingLine.due(listOf(first), start - 70_000L).map { it.id })
        assertTrue(ClosingLine.due(listOf(first), start - 50_000L).isEmpty())
        // Until then the 6 minute read is the close (a final read that fails changes nothing).
        assertEquals(0.52 / 0.5 - 1, ClosingLine.clv(first, start)!!, 1e-12)
        // Read at 100 s: the close is that one, and nothing more is read.
        val final = first.copy(closingFair = 0.54, closingSeenAtMs = start - 100_000L, closeTriedAtMs = start - 105_000L)
        assertFalse(ClosingLine.needsFinalRead(final, start - 90_000L))
        assertNull(ClosingLine.nextAt(listOf(final), start - 90_000L))
        assertEquals(0.54 / 0.5 - 1, ClosingLine.clv(final, start)!!, 1e-12)
    }

    @Test
    fun `no final read for a bet read in the last 3 minutes, a settled or live one, or one with no read yet`() {
        val now = start - 100_000L
        assertFalse(ClosingLine.needsFinalRead(bet(seenBefore = 2 * min), now))
        assertTrue(ClosingLine.needsFinalRead(bet(seenBefore = 3 * min + 1), now))
        assertFalse(ClosingLine.needsFinalRead(bet(seenBefore = 8 * min, status = BetStatus.WON), now))
        assertFalse(ClosingLine.needsFinalRead(bet(seenBefore = 8 * min, placed = start + 1), now))
        assertFalse("a bet with no read at all is the first read's, retried until the last minute", ClosingLine.needsFinalRead(bet(closingFair = null, seenBefore = null), now))
        assertFalse(ClosingLine.needsFinalRead(bet(seenBefore = 8 * min), start + 1))
        // A read 14 minutes out (inside the 15) is read again near the start too.
        assertTrue(ClosingLine.needsFinalRead(bet(seenBefore = 14 * min), now))
        // A final read that was just tried (a failure) isn't tried again at once, and a retry would land in the last minute.
        val tried = bet(seenBefore = 8 * min, tried = start - 100_000L)
        assertTrue(ClosingLine.due(listOf(tried), start - 90_000L).isEmpty())
        assertNull(ClosingLine.nextAt(listOf(tried), start - 90_000L))
    }

    @Test
    fun `a read before the start replaces the close only when it is no older than the one held`() {
        val held = bet(seenBefore = 4 * min, closingFair = 0.52)
        assertTrue(ClosingLine.supersedes(held, start - 2 * min))
        assertTrue(ClosingLine.supersedes(held, start - 4 * min))
        assertFalse("a cached page from 9 minutes out arriving after a fresh read", ClosingLine.supersedes(held, start - 9 * min))
        assertTrue(ClosingLine.supersedes(bet(closingFair = null, seenBefore = null), start - 9 * min))
    }

    @Test
    fun `a failed read is tried again 2 minutes later, never in the last minute`() {
        val tried = bet(closingFair = null, seenBefore = null, tried = start - 6 * min)
        assertTrue("just tried", ClosingLine.due(listOf(tried), start - 5 * min).isEmpty())
        assertEquals(start - 4 * min, ClosingLine.nextAt(listOf(tried), start - 5 * min))
        assertEquals(listOf("b"), ClosingLine.due(listOf(tried), start - 4 * min).map { it.id })
        val lastTry = bet(closingFair = null, seenBefore = null, tried = start - 2 * min - 30_000)
        assertNull("the retry would land in the last minute", ClosingLine.nextAt(listOf(lastTry), start - 2 * min))
        assertTrue(ClosingLine.due(listOf(bet(closingFair = null, seenBefore = null)), start - 30_000).isEmpty())
    }

    @Test
    fun `a bet placed a few minutes before its start is captured at once`() {
        val now = start - 3 * min
        val b = bet(closingFair = null, seenBefore = null, placed = now - 1)
        assertEquals(now, ClosingLine.nextAt(listOf(b), now))
        assertEquals(1, ClosingLine.due(listOf(b), now).size)
    }

    // ---- the stats -------------------------------------------------------------------------------------------------

    @Test
    fun `the share that beat the close and the average by how much, over every bet ever`() {
        val bets = listOf(
            bet("a", closingFair = 0.52),            // +4%
            bet("b", closingFair = 0.51),            // +2%
            bet("c", closingFair = 0.49),            // −2%
            bet("open", closingFair = null, seenBefore = null, starts = start + 3 * 60 * min),
            bet("missed", seenBefore = 3 * 60 * min),
            bet("void", status = BetStatus.VOID),
        )
        val s = ClvStats.of(bets, now = start + min)
        assertEquals(3, s.closed)
        assertEquals(2, s.beat)
        assertEquals(2.0 / 3, s.beatShare!!, 1e-12)
        assertEquals((0.04 + 0.02 - 0.02) / 3, s.averageClv!!, 1e-12)
        assertEquals(0.03, s.averageEvAtBet!!, 1e-12)
        assertEquals(1, s.waiting)
        assertEquals(1, s.missed)
        // Settled bets keep counting: the line runs forever.
        val settled = ClvStats.of(bets.map { if (it.id == "a") it.copy(status = BetStatus.WON) else it }, now = start + 5 * 24 * 60 * min)
        assertEquals(3, settled.closed)
    }

    @Test
    fun `outliers over 5 percent from the close can be taken out`() {
        val bets = listOf(bet("a", closingFair = 0.51), bet("big", closingFair = 0.56), bet("bad", closingFair = 0.47), bet("edge", closingFair = 0.525))
        val all = ClvStats.of(bets, now = start + min)
        assertEquals(4, all.closed)
        assertEquals(2, all.outliers) // +12% and −6%; exactly +5% stays
        assertFalse(all.outliersLeftOut)
        val kept = ClvStats.of(bets, now = start + min, dropOutliers = true)
        assertEquals(2, kept.closed)
        assertEquals(2, kept.beat)
        assertEquals((0.02 + 0.05) / 2, kept.averageClv!!, 1e-12)
        assertTrue(kept.outliersLeftOut)
    }

    @Test
    fun `the periods go by when each bet was placed, in local calendar days`() {
        val zone = ZoneId.of("America/New_York")
        val now = LocalDateTime.of(2026, 9, 30, 15, 0).atZone(zone).toInstant().toEpochMilli()
        fun placedAt(day: Int, hour: Int) = LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
        // Each bet's game started an hour after it was placed, with a close read 5 minutes before.
        fun b(id: String, placed: Long) = bet(id, starts = placed + 60 * min, placed = placed)
        val bets = listOf(
            b("today", placedAt(30, 9)), b("yesterday late", placedAt(29, 23)), b("yesterday early", placedAt(29, 0)),
            b("3 days", placedAt(28, 12)), b("6 days", placedAt(24, 12)), b("8 days", placedAt(22, 12)),
        )
        fun count(p: ClvPeriod) = ClvStats.of(bets, now, p, zone = zone).closed
        assertEquals(6, count(ClvPeriod.ALL))
        assertEquals(1, count(ClvPeriod.TODAY))
        assertEquals(2, count(ClvPeriod.YESTERDAY))
        assertEquals(4, count(ClvPeriod.DAYS_3))
        assertEquals(5, count(ClvPeriod.WEEK))
    }
}
