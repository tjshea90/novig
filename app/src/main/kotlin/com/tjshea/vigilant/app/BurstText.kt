package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.novig.burst.BurstLine
import com.tjshea.vigilant.data.novig.burst.BurstStatus
import com.tjshea.vigilant.data.novig.burst.BurstStudy
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.util.Locale

/** The live burst recorder's words for Settings and Diagnostics, free of Compose (RESEARCH.md §95). */
object BurstText {
    const val SWITCH_TITLE = "Live burst recorder (no orders)"
    const val SWITCH_SUB = "Records the cross-line mispricings after plays in live games. Never places or cancels anything."
    const val BUTTON = "Share live burst study with Claude"
    const val LEAGUES_TITLE = "Leagues to watch"

    const val HINT = "After a play, a game's moneyline, spreads and totals are re-quoted one after another, and for a moment a pair can cost under $1 to cover (a profit whatever happens). " +
        "This watches Novig's live books with your READ key (it has no way to place an order), logs each such window and what a taker with YOUR measured delays (your signed round trip to Novig, how late " +
        "pushes arrive) would have found when his order arrived, on paper, at your per-bet limit. Leave it on through live games (it uses one extra websocket and a little battery) and tap Share: " +
        "after 3 games and 10 windows it says whether a real $1 test is worth running. It cannot prove a profit by itself: no order is sent, so a faster rival or a refused order isn't seen."

    /** One line for the page: what it is doing now. */
    fun note(status: BurstStatus, records: List<BurstLine>): String {
        val totals = BurstStudy.summarize(records).lastOrNull()
        val recorded = if (totals == null || (totals.games == 0 && totals.windows == 0)) "nothing recorded yet" else
            "${totals.windows} window${if (totals.windows == 1) "" else "s"} in ${totals.games} game${if (totals.games == 1) "" else "s"} recorded: ${BurstStudy.verdict(totals).label}"
        val now = when {
            status.problem != null && !status.running -> "Not recording: ${status.problem}"
            !status.running -> "Off"
            status.games == 0 -> "Waiting for a live game of ${status.leagues.sorted().joinToString(", ")}"
            else -> "Recording ${status.games} live game${if (status.games == 1) "" else "s"} (${status.lines} lines, ${"%,d".format(Locale.US, status.updates)} pushes, ${status.open} window${if (status.open == 1) "" else "s"} open now)"
        }
        return now + (status.problem?.takeIf { status.running }?.let { " · $it" } ?: "") + " · $recorded"
    }

    /** The Diagnostics block: null when the recorder was never switched on and nothing was recorded. */
    fun diagnostics(status: BurstStatus, records: List<BurstLine>, latencyNote: String, settings: ScanSettings, running: Boolean): String? {
        if (!settings.burstRecorder && records.isEmpty()) return null
        val o = StringBuilder()
        o.appendLine("Live burst recorder: ${if (settings.burstRecorder) "ON" else "off"} · ${if (running) "running" else "not running"} · leagues ${settings.burstLeagues.sorted().joinToString(", ")}")
        o.appendLine("  now: ${note(status, emptyList()).substringBefore(" · nothing recorded yet").substringBefore(" · ")}${status.problem?.let { " · PROBLEM: $it" } ?: ""}")
        o.append(BurstStudy.report(records, latencyNote, settings.apiMaxStake))
        return o.toString()
    }
}
