package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.pinnodds.LiveFollow
import com.tjshea.vigilant.data.pinnodds.LiveRecord
import com.tjshea.vigilant.data.pinnodds.LiveRunnerStatus
import com.tjshea.vigilant.data.pinnodds.LiveTradeStatus
import com.tjshea.vigilant.data.pinnodds.PinnReport
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.util.Locale

/** The words of Settings › Pinnodds live and its Diagnostics block (Tj, 2026-10-08; RESEARCH.md §116). */
object PinnText {
    const val HINT = "Pinnacle is the sharpest sportsbook, and Pinnodds streams its live prices over one WebSocket in about a tenth of a second. Novig's market makers re-quote a median " +
        "16 seconds after a play, so for a few seconds after Pinnacle moves, Novig's quote can be stale. This devigs Pinnacle's price into a fair chance and buys on Novig only when the fair beats " +
        "Novig's ask by the minimum edge AFTER Novig's in-play fee, AND (by default) Pinnacle repriced because the SCORE changed (a measured test: Pinnacle moves with no score behind them often " +
        "reverted within two minutes), AND Pinnacle's price has sat still for half a second. One immediate-or-cancel order per Pinnacle move, never resting. Real bets start OFF: watch the paper numbers first. Only one Pinnodds connection is allowed per account: no other app or " +
        "script may use the key while this is on. While it is on a quiet notification keeps it running with the screen off (Stop and STOP ALL are on it)."
    const val KEY_LABEL = "Add the Pinnodds key"
    const val TEST_BUTTON = "Test key"
    const val FEED_TITLE = "Pinnodds live feed"
    const val FEED_SUB = "Watches Pinnacle's live prices and Novig's live books. With real bets off it only decides and writes down what it would have bet (paper): nothing is sent."
    const val BET_TITLE = "Place real bets"
    const val BET_SUB = "Sends the immediate-or-cancel order when every test passes, at most the stake below, within the caps below."
    const val RESUME = "Resume"
    const val CONFIRM_TITLE = "Place real bets on live games?"

    fun confirm(s: ScanSettings): String = "With this on, the app buys on Novig by itself during live games: up to ${money(s.pinnLiveStake)} a bet, ${money(s.pinnLiveMaxGame)} a game and ${money(s.pinnLiveMaxDay)} a day " +
        "(fees in), and stops for the day if its settled bets lose ${money(s.pinnLiveHaltLoss)}. Each bet needs at least ${pct(s.pinnLiveMinEv)} EV after Novig's fee and a Pinnacle move of at least " +
        "${pts(s.pinnLiveMinMove)} toward that side. Pinnacle's price is an estimate of the true chance, so a bet that passes can still lose: it is positive expected value, not a sure thing. " +
        "Novig's in-play delay for an order is not documented and has not been measured on a real order yet: start small."

    fun money(d: Double): String = if (d == Math.floor(d)) "$" + d.toInt() else String.format(Locale.US, "$%.2f", d)
    fun pct(p: Double): String = String.format(Locale.US, "%.1f%%", p * 100).replace(".0%", "%")
    fun pts(p: Double): String = String.format(Locale.US, "%.1f points", p * 100).replace(".0 points", " points")

    /** The line under the switches: where the engine is and what it has done. */
    fun statusLine(r: LiveRunnerStatus, t: LiveTradeStatus, s: ScanSettings, keyCount: Int): String {
        if (!s.pinnLive) return "Off."
        if (keyCount == 0 && !s.pinnWebsite.website) return "On, but no Pinnodds key is saved: add it above, or switch to the free Pinnacle website feed."
        if (!r.running) return r.problem?.let { "Not running: $it" } ?: "Starting…"
        val age = r.frameAgeMs?.let { if (it < 2_000) "just now" else "${it / 1000} s ago" } ?: "no frame yet"
        val mode = if (s.pinnLiveBet && s.pinnLiveHalted == null) "REAL BETS" else if (s.pinnLiveHalted != null) "HALTED" else "paper"
        return "${if (s.pinnWebsite.website) "Pinnacle website feed" else "Pinnodds feed"} ${r.socket}, last frame $age · ${r.pinnLive} live matchups, ${r.matched} matched to Novig, ${r.watched} Novig markets held · ${r.candidates} candidates · " +
            "$mode: ${t.bets} bets, ${t.paper} paper, ${t.missed} missed" + (t.last?.let { " · last: $it" } ?: "") + (r.problem?.let { " · $it" } ?: "")
    }

    fun diagnostics(s: ScanSettings, r: LiveRunnerStatus, t: LiveTradeStatus, records: List<LiveRecord>, follows: List<LiveFollow>, running: Boolean, reopen: List<com.tjshea.vigilant.data.pinnodds.ReopenProbe> = emptyList()): String {
        val o = StringBuilder()
        o.appendLine("PINNODDS LIVE (RESEARCH.md §116)")
        o.appendLine(
            "Feed: ${if (s.pinnLive) "ON" else "off"} · ${running.let { if (it) "running" else "not running" }} · real bets ${if (s.pinnLiveBet) "ON" else "off (paper)"}" +
                (s.pinnLiveHalted?.let { " · HALTED: $it" } ?: "") + " · stake ${money(s.pinnLiveStake)}, game ${money(s.pinnLiveMaxGame)}, day ${money(s.pinnLiveMaxDay)}, halt at ${money(s.pinnLiveHaltLoss)} lost · " +
                "min EV ${pct(s.pinnLiveMinEv)}, min move ${pts(s.pinnLiveMinMove)}, trigger ${s.pinnLiveTrigger.label}, devig ${s.pinnLiveDevig.displayName}, pregame ${if (s.pinnLivePregame) "on" else "off"}, hold-off after a score ${if (s.pinnLiveHoldoffSeconds == 0) "off" else "${s.pinnLiveHoldoffSeconds} s"}",
        )
        o.appendLine(
            "Engine: socket ${r.socket} · Pinnacle matchups ${r.pinnEvents} (${r.pinnLive} live) · Novig live games ${r.novigGames} · matched ${r.matched} · markets held ${r.watched} · frames ${r.frames} · " +
                "last frame ${r.frameAgeMs?.let { "${it} ms ago" } ?: "none"} · evaluations ${r.evaluations} · candidates ${r.candidates}" + (r.problem?.let { " · problem: $it" } ?: ""),
        )
        if (r.skips.isNotEmpty()) o.appendLine("Why not (counts of looks): " + r.skips.entries.sortedByDescending { it.value }.take(8).joinToString(" · ") { "${it.key} ${it.value}" })
        if (t.skipped.isNotEmpty()) o.appendLine("Trader held back: " + t.skipped.entries.sortedByDescending { it.value }.take(6).joinToString(" · ") { "${it.key} ${it.value}" })
        o.appendLine("Trader this run: ${t.bets} bets · ${t.paper} paper · ${t.missed} missed · ${t.refused} refused · spent ${money(t.spent)}" + (t.last?.let { " · last: $it" } ?: ""))
        PinnReport.lines(records, follows).forEach { o.appendLine(it) }
        o.appendLine("Post-score study (the pause after a score; reads only, sends nothing):")
        com.tjshea.vigilant.data.pinnodds.ReopenStudy.lines(reopen).forEach { o.appendLine("  $it") }
        return o.toString()
    }
}
