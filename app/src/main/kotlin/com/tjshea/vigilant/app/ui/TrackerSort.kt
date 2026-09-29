package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet

/**
 * How the Tracker's bets are ordered (Tj, 2026-09-29: "add filter options on the top of this bet tracker section, including date placed (orders
 * bets placed by date and time), current EV (orders bets by current EV with the best current EV at the top of the list compared to the odds I
 * placed the bet), amount of bet, scanner used to place bet"). Tapping the sort that is already chosen turns it around.
 */
enum class BetSort(val label: String, val natural: String, val reversed: String) {
    /** Open: the bets that need a look first, then the next games; settled: latest result; all: latest placed. */
    DEFAULT("Default", "", ""),
    PLACED("Date placed", "newest first", "oldest first"),
    EV("Current EV", "best first", "worst first"),
    AMOUNT("Amount", "largest first", "smallest first"),
    STARTS("Game start", "soonest first", "latest first"),
}

/** Which scanner's bets are listed: the one that found the bet when it was placed ([TrackedBet.source]). */
enum class ScannerFilter(val label: String) { ALL("All scanners"), VIGILANT("Vigilant"), CNO("CNO") }

object TrackerSort {

    fun scannerOf(b: TrackedBet): ScannerFilter = if (b.source == BetTracker.SOURCE_CNO) ScannerFilter.CNO else ScannerFilter.VIGILANT

    fun inScanner(bets: List<TrackedBet>, f: ScannerFilter): List<TrackedBet> = if (f == ScannerFilter.ALL) bets else bets.filter { scannerOf(it) == f }

    /** The chip's text: just the name, until it's the chosen one, when it also says which end is at the top. */
    fun chipLabel(sort: BetSort, chosen: BetSort, reversed: Boolean, defaultLabel: String): String = when {
        sort == BetSort.DEFAULT -> defaultLabel
        sort != chosen -> sort.label
        else -> "${sort.label}: ${if (reversed) sort.reversed else sort.natural}"
    }

    /**
     * [bets] in [sort]'s order, [reversed] the other way round. A bet with no current EV goes last either way, so the list of what's worth a look
     * never opens on bets nothing has priced. [default] is the order for [BetSort.DEFAULT] (it depends on which list is shown).
     */
    fun sorted(bets: List<TrackedBet>, sort: BetSort, reversed: Boolean, default: (List<TrackedBet>) -> List<TrackedBet>): List<TrackedBet> = when (sort) {
        BetSort.DEFAULT -> default(bets)
        BetSort.PLACED -> bets.sortedWith(direction(compareBy<TrackedBet> { it.createdAtMs }.thenBy { it.id }, natural = true, reversed))
        BetSort.AMOUNT -> bets.sortedWith(direction(compareBy<TrackedBet> { it.stake }.thenBy { it.createdAtMs }.thenBy { it.id }, natural = true, reversed))
        BetSort.STARTS -> bets.sortedWith(direction(compareBy<TrackedBet> { it.startsTs }.thenBy { it.createdAtMs }.thenBy { it.id }, natural = false, reversed))
        BetSort.EV -> {
            val (priced, unpriced) = bets.partition { it.nowEv != null }
            priced.sortedWith(direction(compareBy<TrackedBet> { it.nowEv!! }.thenByDescending { it.stake }.thenBy { it.id }, natural = true, reversed)) +
                unpriced.sortedWith(compareBy<TrackedBet> { it.startsTs }.thenBy { it.id })
        }
    }

    /** [cmp] is ascending; [naturalDescending]: the sort's own order puts the biggest at the top ("newest first"), [reversed] turns that round. */
    private fun direction(cmp: Comparator<TrackedBet>, naturalDescending: Boolean, reversed: Boolean): Comparator<TrackedBet> {
        val descending = naturalDescending != reversed
        return if (descending) cmp.reversed() else cmp
    }
}
