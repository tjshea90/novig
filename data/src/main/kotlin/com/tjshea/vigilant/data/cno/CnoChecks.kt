package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.engine.Odds
import kotlin.math.abs

/** A CNO row that passed [CnoChecks], with the EV Tj would actually get. */
data class CnoPick(
    val row: CnoRow,
    /** CNO's EV, less Novig's taker fee when the game has started (pregame fills are free). */
    val ev: Double,
    val live: Boolean,
)

/** What the checks kept, and how many rows each check left out. */
data class CnoScreened(val picks: List<CnoPick>, val hidden: Map<CnoChecks.Reason, Int>) {
    val hiddenCount: Int get() = hidden.values.sum()
}

/**
 * The app's own screen over CNO's list (Tj, 2026-09-26: "accurate, true positive EV bets";
 * RESEARCH.md §19). CNO already applies the same filters server-side; these catch anything its
 * page ignored or that the link overrode, plus what CNO doesn't check:
 *
 *  - EV that doesn't follow from the row's own fair odds and price (a misread row);
 *  - one-way devigs (CNO's ⚠️: fair value from a single side plus a guessed juice);
 *  - EV so high it's almost always a stale or mismatched line ([MAX_EV]);
 *  - live games, where Novig charges the taker a fee CNO's EV doesn't subtract.
 */
object CnoChecks {

    /** Above this EV a row is far more likely a stale or wrong line than a real edge. */
    const val MAX_EV = 0.20

    /** CNO's own EV and one recomputed from its fair odds may differ by rounding, not more. */
    const val EV_TOLERANCE = 0.005

    /** CNO's FAQ: a "Last Updated" over 10 minutes means its updater has stopped. */
    const val STUCK_MS = 10 * 60_000L

    enum class Reason(val text: String) {
        MISMATCH("EV doesn't follow from its fair odds"),
        ONE_WAY("devigged from one side only"),
        BOOKS("too few books"),
        ODDS("longer odds than your cap"),
        TOO_GOOD("EV over 20% (likely a stale line)"),
        EV("below your minimum EV"),
    }

    fun screen(snapshot: CnoSnapshot, filters: CnoFilters, now: Long): CnoScreened {
        val hidden = LinkedHashMap<Reason, Int>()
        val picks = snapshot.rows.mapNotNull { row ->
            val reason = reject(row, filters, now)
            if (reason != null) {
                hidden[reason] = (hidden[reason] ?: 0) + 1
                null
            } else {
                val live = row.startsAtMs != null && row.startsAtMs <= now
                CnoPick(row, netEv(row, live), live)
            }
        }
        // Net EV can drop a live row under the floor.
        val kept = picks.filter { it.ev >= filters.minEv - 1e-9 }
        if (kept.size < picks.size) hidden[Reason.EV] = (hidden[Reason.EV] ?: 0) + picks.size - kept.size
        return CnoScreened(kept.sortedByDescending { it.ev }, hidden)
    }

    private fun reject(row: CnoRow, f: CnoFilters, now: Long): Reason? {
        val fair = fairProbability(row)
        val started = row.startsAtMs != null && row.startsAtMs <= now
        // Live rows may carry a fee in CNO's EV; pregame Novig fills are free, so there it must add up.
        if (!started && fair != null && abs(fair * Odds.americanToDecimal(row.odds) - 1.0 - row.ev) > EV_TOLERANCE) return Reason.MISMATCH
        if (row.oneWay) return Reason.ONE_WAY
        if (row.books != null && row.books < f.minBooks) return Reason.BOOKS
        if (f.maxOdds > 0 && row.odds > f.maxOdds) return Reason.ODDS
        if (row.ev > MAX_EV) return Reason.TOO_GOOD
        if (row.ev < f.minEv - 1e-9) return Reason.EV
        return null
    }

    /** The row's fair probability: CNO's exact one, else from its fair odds. */
    fun fairProbability(row: CnoRow): Double? =
        row.fairProbability ?: row.fairOdds?.takeIf { it != 0 }?.let { 1.0 / Odds.americanToDecimal(it) }

    private fun netEv(row: CnoRow, live: Boolean): Double {
        if (!live) return row.ev
        val fair = fairProbability(row) ?: return row.ev
        return CnoBooks.evAt(fair, row.odds, live = true)
    }

    /** Whether CNO has stopped updating (its data is older than [STUCK_MS]). */
    fun stuck(snapshot: CnoSnapshot, now: Long): Boolean = now - snapshot.dataAtMs > STUCK_MS
}
