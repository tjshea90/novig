package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.CloseBackfill
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The focus of "Check odds now" (Tj, 2026-10-01: "pause other parts of the app such as the cno scanner so that it focuses on refreshing the current odds and
 * EV and stats"): while a check runs, the loops that read CrazyNinjaOdds, Novig and the fair-odds feeds on their own (the background auto-scan cycle, and
 * with it auto-bet, the CNO list's refresh, Vigilant's scans, the widget's rescans, ParlayAPI's movers) wait, so the check has CNO's pace, the APIs and the
 * phone to itself. Held in memory only: a check can't outlive its process, so nothing is ever left paused; and for at most [CEILING_MS], so a check that
 * hangs can't hold the app. Nothing here is the Pause switch ([com.tjshea.vigilant.data.scanner.ScanSettings.paused]): that stays as Tj set it.
 *
 * Not held: the closing capture's alarm (the last read of a bet before its start is the close, and that moment doesn't come back), and the Bet sheet
 * (Tj's own tap).
 */
class FocusGate(private val clock: () -> Long = System::currentTimeMillis) {
    private val _since = MutableStateFlow<Long?>(null)

    /** When the focus began, null when none is held ([active] also checks the ceiling). */
    val since: StateFlow<Long?> = _since.asStateFlow()

    fun begin() { _since.value = clock() }

    fun end() { _since.value = null }

    /** Whether a check holds the focus at [now]. */
    fun active(now: Long = clock()): Boolean = _since.value?.let { now - it in 0 until CEILING_MS } == true

    companion object {
        /** The longest a check holds the focus. */
        const val CEILING_MS = 15 * 60_000L
    }
}

/** The sentence about closing lines in a Check odds now's report (Tj, 2026-10-01: "make sure it gets all available closing line data"). */
object CloseText {
    /** Started bets that still have no closing line at [now], and could still get one (the bet was placed before the start). */
    fun missing(bets: List<TrackedBet>, now: Long): Int =
        bets.count { it.status != BetStatus.VOID && it.createdAtMs < it.startsTs && now >= it.startsTs && ClosingLine.closeOf(it, now) == null }

    /** "Closing lines: found 14 of 20 (ESPN 3, Novig 5, Pinnacle 6) · 6 started bets still have none: Novig publishes this day's trades the next morning (4)". */
    fun summary(r: CloseBackfill.Report?, missing: Int): String {
        val asked = r?.looked ?: 0
        if (r == null || asked == 0) {
            return if (missing == 0) "Closing lines: every started bet has one"
            else "Closing lines: $missing started bet${if (missing == 1) " has" else "s have"} none yet (looked for in the last ${CloseBackfill.FORCE_GAP_MS / 60_000L} minutes: each bet says why)"
        }
        val by = r.bySource.entries.sortedBy { it.key }.joinToString(", ", " (", ")") { "${it.key} ${it.value}" }.takeIf { r.bySource.isNotEmpty() }.orEmpty()
        val found = "Closing lines: found ${r.found} of $asked$by"
        if (missing == 0) return "$found · every started bet has one now"
        val why = r.missing.entries.sortedByDescending { it.value }.take(2).joinToString("; ") { "${it.key.take(70)} (${it.value})" }
        return "$found · $missing started bet${if (missing == 1) " still has" else "s still have"} none" + if (why.isNotEmpty()) ": $why" else ""
    }
}
