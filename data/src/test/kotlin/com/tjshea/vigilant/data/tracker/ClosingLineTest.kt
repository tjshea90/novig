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

    @Test
    fun `a bet placed in the last minutes with nothing read since closes at its own line, and a live bet has no close`() {
        val late = bet(closingFair = null, seenBefore = null, placed = start - 4 * min)
        assertEquals(0.515 / 0.5 - 1, ClosingLine.clv(late, start)!!, 1e-12)
        assertNull(ClosingLine.clv(bet(placed = start + min), start + 2 * min))
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
        assertNull(ClosingLine.nextAt(listOf(bet(seenBefore = 8 * min)), now))
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
