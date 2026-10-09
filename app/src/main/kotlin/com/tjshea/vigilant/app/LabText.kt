package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.novig.lab.LabGrade
import com.tjshea.vigilant.data.novig.lab.LabPaper
import com.tjshea.vigilant.data.novig.lab.LabRecord
import com.tjshea.vigilant.data.novig.lab.LabStatus

/** Words for the lab recorder (Tj, 2026-10-09; RESEARCH.md §120.6): the Settings switch, the note under it and the Diagnostics block. It places nothing: every line here is about paper bets. */
object LabText {
    const val SWITCH_TITLE = "Paper lab: ladders, late-game tail, alternate lines"
    const val SWITCH_SUB = "While a game is live on Novig, reads its totals, spread and moneyline books and writes down (no order, ever) would-be bets: a cover across lines, a far strike the game has all but decided, and an alternate line priced under Pinnacle's. Graded from Novig's own settled markets."
    const val HINT = "Research only. It needs live games (NFL, NCAAF, NBA, NHL, MLB). Settings › Diagnostics & about has what it found."

    const val RESEARCH_TITLE = "Research mode (turn everything on)"
    const val RESEARCH_SUB = "One switch for every recorder that places nothing: the live feed test, the burst recorder, the paper lab and the paper bids. Never turns on real-money betting."
    const val RESEARCH_HINT = "Leave the app open (plugged in) through games, evenings and a few days of pregame, then tap the button and send the file."
    const val RESEARCH_BUTTON = "Share research file with Claude"

    private fun duration(ms: Long): String {
        val m = ms / 60_000L
        return if (m < 60) "${m} min" else "${m / 60} h ${m % 60} min"
    }

    /** The line under the switch. */
    fun note(s: LabStatus, now: Long): String {
        if (!s.running && s.sinceMs == null) return "Off."
        val head = if (s.running) "Running" + (s.sinceMs?.let { " for ${duration(now - it)}" } ?: "") else "Stopped"
        val games = if (s.games == 0) "waiting for a live game" else "${s.games} live game${if (s.games == 1) "" else "s"} (${s.withState} with a clock)"
        return "$head: $games · ${s.cycles} reads · would-be bets so far: ${s.tail} tail, ${s.alt} alternate-line, ${s.covers} cover" + (s.problem?.let { " · PROBLEM: $it" } ?: "")
    }

    /** The Diagnostics block's lines. */
    fun diagnostics(s: LabStatus, records: List<LabRecord>, grades: List<LabGrade>, now: Long): List<String> =
        listOf(note(s, now)) + listOfNotNull(s.ladders?.let { "last ladder read: $it" }) + LabPaper.report(records, grades).map { "  $it" } +
            records.takeLast(5).map { r -> "  ${r.kind} ${r.event} · ${r.label} ${r.side} @ ${"%.3f".format(java.util.Locale.US, r.ask)} fair ${"%.3f".format(java.util.Locale.US, r.fair)} edge ${"%+.1f".format(java.util.Locale.US, 100 * r.edge)}% · ${r.note}" }
}
