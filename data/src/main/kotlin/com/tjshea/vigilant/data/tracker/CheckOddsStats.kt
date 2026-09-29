package com.tjshea.vigilant.data.tracker

import kotlin.math.abs

/**
 * The Tracker's "Check odds now" counter (Tj, 2026-09-29: "as the refreshed odds come in, there is a counter at the top of the section that
 * shows how many of my open bets are currently positive EV and how many are currently negative EV plus a percentage of bets that are positive
 * EV. This counter should refresh back to zero every time I do a new check for odds ... Then next to that, make an average EV stat that shows
 * the average EV percentage of all of my current open bets but not counting any outliers such as any bets showing a current EV of more than 5%
 * positive or a current EV of more than 5% negative.").
 *
 * Counts only open bets whose EV was re-read since the check began ([of]'s `sinceMs`): a new check starts at 0 and counts up as each batch
 * of refreshed odds is saved; a bet settled meanwhile drops out. The EV is [TrackedBet.nowEv]: the devigged fair odds now against the price the
 * bet was placed at. [averageEv] leaves out every EV beyond [OUTLIER_EV] either way ([outliers] says how many).
 */
data class CheckOddsStats(
    val positive: Int,
    val negative: Int,
    /** Exactly at fair value: neither +EV nor −EV. */
    val even: Int,
    /** The plain average of the re-read EVs within ±[OUTLIER_EV]; null when none are. */
    val averageEv: Double?,
    /** How many EVs [averageEv] is the average of. */
    val averaged: Int,
    /** Re-read EVs over +[OUTLIER_EV] or under −[OUTLIER_EV]: counted as + or −, left out of [averageEv]. */
    val outliers: Int,
) {
    /** Open bets re-read in this check. */
    val priced: Int get() = positive + negative + even

    /** The share of [priced] that is +EV; null before any is. */
    val positiveShare: Double? get() = if (priced > 0) positive.toDouble() / priced else null

    companion object {
        /** Tj's line for "outliers" in the average: over 5% either way (a bet at exactly 5% still counts). */
        const val OUTLIER_EV = 0.05

        private const val EPS = 1e-9

        val EMPTY = CheckOddsStats(0, 0, 0, null, 0, 0)

        /** [bets]' open ones whose current EV was read at or after [sinceMs] (when the check began). */
        fun of(bets: List<TrackedBet>, sinceMs: Long): CheckOddsStats {
            val evs = bets.mapNotNull { b -> b.nowEv?.takeIf { b.status == BetStatus.PENDING && (b.nowAtMs ?: Long.MIN_VALUE) >= sinceMs && !it.isNaN() } }
            if (evs.isEmpty()) return EMPTY
            val positive = evs.count { it > EPS }
            val negative = evs.count { it < -EPS }
            val kept = evs.filter { abs(it) <= OUTLIER_EV + EPS }
            return CheckOddsStats(
                positive = positive,
                negative = negative,
                even = evs.size - positive - negative,
                averageEv = if (kept.isEmpty()) null else kept.average(),
                averaged = kept.size,
                outliers = evs.size - kept.size,
            )
        }
    }
}
