package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.novig.lab.LabStatus
import com.tjshea.vigilant.data.pinnodds.LiveRecord
import com.tjshea.vigilant.data.pinnodds.LiveTradeStatus
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.util.Locale

/** The words for the live tail bets (Settings › Live bids › Live tail bets, and the Diagnostics block). */
object TailText {
    const val TITLE = "Live tail bets"
    const val HINT = "Buys a far strike the game has all but decided (the late-game model: ESPN's score and clock, the centre read off Novig's own liquid lines, the spread widened 25% and the centre moved 1.5 points against the bet, a fair of 92% or more), when Novig still offers it under that fair by your minimum edge after its fee. One immediate-or-cancel order, a tiny stake. It needs no outside price, so it keeps running after the Pinnodds trial ends."
    const val CONFIRM_TITLE = "Place live tail bets with real money?"

    fun money(d: Double): String = if (d == Math.floor(d)) "$" + d.toInt() else String.format(Locale.US, "$%.2f", d)
    private fun pct(p: Double) = com.tjshea.vigilant.data.livebid.LiveBidQuality.pct(p)

    fun confirm(s: ScanSettings): String =
        "The app will buy live tail strikes by itself: at most ${money(s.tailLiveStake)} a bet, ${money(s.tailLiveMaxGame)} a game and ${money(s.tailLiveMaxDay)} a day, only when the model's fair is 92% or more and the edge after Novig's fee is ${pct(s.tailLiveMinEdge)} or more. " +
            "It stops for the day if tail bets lose ${money(s.tailLiveHaltLoss)}. The model is a rough one (a score or clock from ESPN can be a little behind the game), so a decided-looking bet can still lose, and the paper record behind it covers only a handful of games, so there is no real track record yet. Start small."

    fun statusLine(s: ScanSettings, t: LiveTradeStatus, lab: LabStatus): String {
        if (!s.tailLive) return "Off."
        if (s.tailLiveHalted != null) return "Stopped: ${s.tailLiveHalted}"
        val mode = if (s.tailLiveBet) "REAL" else "paper"
        val feed = if (!lab.running) "game reader starting" else "${lab.games} live games read, ${lab.withState} with a score and clock"
        return "$mode: ${t.bets} bought, ${t.missed} missed, ${t.paper} paper, spent ${money(t.spent)} · $feed" + (t.last?.let { " · last: $it" } ?: "") +
            (lab.problem?.let { " · problem: $it" } ?: "")
    }

    fun diagnostics(s: ScanSettings, t: LiveTradeStatus, lab: LabStatus, records: List<LiveRecord>): String {
        val o = StringBuilder()
        o.appendLine("LIVE TAIL BETS (RESEARCH.md §125)")
        o.appendLine(
            "Switch: ${if (s.tailLive) "ON" else "off"} · ${if (s.tailLiveBet) "REAL money" else "paper"}" + (s.tailLiveHalted?.let { " · HALTED: $it" } ?: "") +
                " · stake ${money(s.tailLiveStake)}, game ${money(s.tailLiveMaxGame)}, day ${money(s.tailLiveMaxDay)}, halt at ${money(s.tailLiveHaltLoss)} lost · min edge ${pct(s.tailLiveMinEdge)}",
        )
        o.appendLine("Lab: running ${lab.running} · games ${lab.games} · with state ${lab.withState} · cycles ${lab.cycles} · tail strikes seen ${lab.tail}" + (lab.problem?.let { " · problem: $it" } ?: ""))
        if (t.skipped.isNotEmpty()) o.appendLine("Held back: " + t.skipped.entries.sortedByDescending { it.value }.take(8).joinToString(" · ") { "${it.key} ${it.value}" })
        o.appendLine("Taker this run: ${t.bets} bought · ${t.paper} paper · ${t.missed} missed · ${t.refused} refused · spent ${money(t.spent)}" + (t.last?.let { " · last: $it" } ?: ""))
        val real = records.filter { it.mode == "BET" }
        if (real.isNotEmpty()) o.appendLine("Real orders: " + real.groupBy { it.outcome }.entries.joinToString(" · ") { "${it.key} ${it.value.size}" })
        records.takeLast(8).reversed().forEach { r ->
            o.appendLine(
                String.format(
                    Locale.US, "  %s %s %s · %s · %s · ask %.3f fair %.3f edge %+.1f%% · %s", java.time.Instant.ofEpochMilli(r.atMs).toString().substring(11, 19), r.mode, r.outcome, r.league, r.selection, r.ask, r.fair, r.ev * 100,
                    r.message.take(80),
                ),
            )
        }
        return o.toString()
    }
}
