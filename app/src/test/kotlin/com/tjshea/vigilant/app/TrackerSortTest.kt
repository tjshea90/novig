package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.BetSort
import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.ScannerFilter
import com.tjshea.vigilant.app.ui.TrackerSort
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * The Tracker's sorts and scanner filter (Tj, 2026-09-29: "date placed (orders bets placed by date and time), current EV (… the best current EV at
 * the top of the list compared to the odds I placed the bet), amount of bet, scanner used to place bet").
 */
class TrackerSortTest {

    private val hour = 3_600_000L
    private val t0 = 1_790_400_000_000L

    private fun bet(id: String, placed: Long, stake: Double, ev: Double?, starts: Long = t0 + 5 * hour, source: String = BetTracker.SOURCE_VIGILANT) = TrackedBet(
        id = id, createdAtMs = placed, league = "NFL", eventName = "A @ B", startsTs = starts, marketLabel = "Moneyline", selection = id,
        marketId = "m$id", outcomeId = "o$id", price = 0.4, cost = 0.4, fairAtBet = 0.42, evPercentAtBet = 0.05, stake = stake,
        source = source, nowEv = ev, nowFair = ev?.let { 0.4 * (1 + it) }, nowAtMs = ev?.let { t0 },
    )

    private val bets = listOf(
        bet("a", placed = t0 - 5 * hour, stake = 5.0, ev = 0.02, starts = t0 + 9 * hour),
        bet("b", placed = t0 - 1 * hour, stake = 25.0, ev = -0.03, starts = t0 + 1 * hour, source = BetTracker.SOURCE_CNO),
        bet("c", placed = t0 - 3 * hour, stake = 1.0, ev = 0.08, starts = t0 + 3 * hour),
        bet("d", placed = t0 - 2 * hour, stake = 10.0, ev = null, starts = t0 + 2 * hour, source = BetTracker.SOURCE_CNO),
    )

    private fun order(sort: BetSort, reversed: Boolean = false, list: List<TrackedBet> = bets) =
        TrackerSort.sorted(list, sort, reversed) { it.reversed() }.map { it.id }

    @Test
    fun `date placed puts the newest first, and turned round the oldest`() {
        assertEquals(listOf("b", "d", "c", "a"), order(BetSort.PLACED))
        assertEquals(listOf("a", "c", "d", "b"), order(BetSort.PLACED, reversed = true))
    }

    @Test
    fun `current EV puts the best first against the price each was placed at, and unpriced bets last either way`() {
        assertEquals(listOf("c", "a", "b", "d"), order(BetSort.EV))
        assertEquals(listOf("b", "a", "c", "d"), order(BetSort.EV, reversed = true))
    }

    @Test
    fun `amount puts the largest stake first`() {
        assertEquals(listOf("b", "d", "a", "c"), order(BetSort.AMOUNT))
        assertEquals(listOf("c", "a", "d", "b"), order(BetSort.AMOUNT, reversed = true))
    }

    @Test
    fun `game start puts the soonest first`() {
        assertEquals(listOf("b", "d", "c", "a"), order(BetSort.STARTS))
        assertEquals(listOf("a", "c", "d", "b"), order(BetSort.STARTS, reversed = true))
    }

    @Test
    fun `the default is the list's own order, and ties never reorder between runs`() {
        assertEquals(listOf("d", "c", "b", "a"), order(BetSort.DEFAULT))
        val same = listOf(bet("x", t0, 5.0, 0.01), bet("y", t0, 5.0, 0.01), bet("z", t0, 5.0, 0.01))
        assertEquals(order(BetSort.EV, list = same), order(BetSort.EV, list = same.reversed()))
        assertEquals(order(BetSort.AMOUNT, list = same), order(BetSort.AMOUNT, list = same.reversed()))
    }

    @Test
    fun `the scanner filter lists the bets its scanner found, and All lists everything`() {
        assertEquals(listOf("a", "c"), TrackerSort.inScanner(bets, ScannerFilter.VIGILANT).map { it.id })
        assertEquals(listOf("b", "d"), TrackerSort.inScanner(bets, ScannerFilter.CNO).map { it.id })
        assertEquals(4, TrackerSort.inScanner(bets, ScannerFilter.ALL).size)
    }

    @Test
    fun `a chip says which end of the list is at the top only once it is the chosen one`() {
        assertEquals("Date placed", TrackerSort.chipLabel(BetSort.PLACED, BetSort.EV, false, "Needs a look"))
        assertEquals("Current EV: best first", TrackerSort.chipLabel(BetSort.EV, BetSort.EV, false, "Needs a look"))
        assertEquals("Current EV: worst first", TrackerSort.chipLabel(BetSort.EV, BetSort.EV, true, "Needs a look"))
        assertEquals("Needs a look", TrackerSort.chipLabel(BetSort.DEFAULT, BetSort.EV, false, "Needs a look"))
    }

    @Test
    fun `the placed time is a date and a time of day`() {
        assertEquals("Sep 29, 4:12 PM", Format.placedAt(1_790_698_320_000L, ZoneId.of("UTC")))
    }
}
