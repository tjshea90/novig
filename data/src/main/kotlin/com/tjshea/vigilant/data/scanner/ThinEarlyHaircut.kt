package com.tjshea.vigilant.data.scanner

/**
 * The thin-books-early haircut (Tj, 2026-10-13: "Do option 1"; research/nhl_sog_matching_2026-10-13.md). Vigilant's fair for NHL shots on goal, when it rests on fewer than [FEW_BOOKS] books and
 * the game is more than [EARLY_HOURS] away, ran +3.0 to +3.7 points above Pinnacle's independent close (3-4 books, 12 h+: +3.1 points over 14 bets; under 2 h: +0.1; 8+ books: about 0). Matching was checked and is
 * exact; the early soft-book numbers are simply generous. So each side's fair chance is cut [POINTS] under those conditions, before the edge, the stake, the star rating, the auto-bet and the bids see it.
 * Scoped to the stat that was measured (other player props ran +0.8 over the same hours); a stat is added to [STATS] only with the same evidence. Pure.
 */
object ThinEarlyHaircut {
    /** Probability points taken off each side's fair (0.025 = 2.5 points: the measured gap, +3.0 to +3.7 at 3-4 books, less a margin for noise). */
    const val POINTS = 0.025
    const val FEW_BOOKS = 5
    const val EARLY_HOURS = 12.0
    private val STATS = setOf("NHL|SHOTS_ON_GOAL")

    /** The points to take off a [league] prop on [stat] priced by [books] books, [hoursToStart] hours before the start; 0 when none apply. */
    fun points(league: String, stat: String?, books: Int, hoursToStart: Double): Double =
        if (stat != null && "$league|$stat" in STATS && books < FEW_BOOKS && hoursToStart > EARLY_HOURS) POINTS else 0.0
}
