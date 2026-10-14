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
        "The app will buy live tail strikes by itself: at most ${money(s.tailLive.stake)} a bet, ${money(s.tailLive.maxGame)} a game and ${money(s.tailLive.maxDay)} a day, only when the model's fair is 92% or more and the edge after Novig's fee is ${pct(s.tailLive.minEdge)} or more. " +
            "It stops for the day if tail bets lose ${money(s.tailLive.haltLoss)}. The model is a rough one (a score or clock from ESPN can be a little behind the game), so a decided-looking bet can still lose, and the paper record behind it covers only a handful of games, so there is no real track record yet. Start small." + (if (s.tailLive.light) " Live light's push lane judges each live game the moment Novig's feed or the score changes" + (if (s.tailLive.bids) ", and its bids rest as post-only orders under the same limits" else "") + ". An edge over 30% is only taken when BoltOdds' soft books agree." else "")

    const val LIGHT_HINT = "Live light judges each live game the moment Novig's live feed or the score changes, instead of every 20 seconds, and it works from the score and clock alone, so a stale price is found even when the whole ladder is stale (a team up 25 offered near 50/50). A bet this big needs a second live source to agree (BoltOdds' soft-book consensus, when it is on); without one it only takes edges up to 30%. Needs the Novig key's live feed. Pinnodds is not used."

    const val LIGHT_BIDS_HINT = "Also puts up post-only bids on the sides the score and clock call decided, 10% under the model's fair and under what Novig offers, each re-vouched for every second and pulled the moment the model no longer clears it or the game stops being judged (a bid nobody vouches for comes down in 3.5 seconds). Real money only while \"Buy tail bets with real money\" is on; the stake, game, day and loss limits below apply to bids and bets together. Pinnodds is not used."

    /** The Diagnostics lines of Live light's push lane and its bid desk. */
    fun lightDiagnostics(s: ScanSettings, l: com.tjshea.vigilant.data.novig.lab.LiveLightStatus, running: Boolean, feedConnected: Boolean, desk: com.tjshea.vigilant.data.livebid.LiveBidDeskStatus? = null): String {
        val o = StringBuilder()
        o.appendLine("LIVE LIGHT (push lane for obviously wrong live prices; Tj 2026-10-14)")
        o.appendLine("Switch: ${if (s.tailLive.on && s.tailLive.light) "ON" else "off"} · running $running · Novig live feed ${if (feedConnected) "connected" else "NOT connected (the 20-second pass is used)"} · bids ${if (s.tailLive.bids) "on" else "off"}")
        if (!running) return o.toString().trimEnd()
        val age = { at: Long -> if (at == 0L) "never" else "${(System.currentTimeMillis() - at) / 1000} s ago" }
        o.appendLine("Games ${l.games} (${l.withScore} with a score) · markets watched ${l.markets} · pushes ${l.pushes} (last ${age(l.lastPushMs)}) · judgments ${l.scans} (last ${age(l.lastScanMs)}, ${l.lastScanTookMs} ms from the push) · offers to the taker ${l.offers}")
        if (l.skipped.isNotEmpty()) o.appendLine("Not judged: " + l.skipped.entries.sortedByDescending { it.value }.take(8).joinToString(" · ") { "${it.key} ${it.value}" })
        l.last?.let { o.appendLine("Last offer: $it") }
        if (s.tailLive.bids) {
            o.appendLine("Bids: wanted ${l.bidWants} · taken down by the model ${l.bidPulls}" + (desk?.let { d -> " · desk ${d.mode}: ${d.active} up (${money(d.restingDollars)}), ${d.posted} posted, ${d.fills} filled, paid ${money(d.paid)}, refused ${d.refused}" + (d.halted?.let { h -> " · HALTED: $h" } ?: "") + (d.last?.let { x -> " · last: $x" } ?: "") } ?: ""))
            if (desk != null && desk.skips.isNotEmpty()) o.appendLine("Bids not put up: " + desk.skips.entries.sortedByDescending { it.value }.take(6).joinToString(" · ") { "${it.key} ${it.value}" })
            if (desk != null && desk.pulls.isNotEmpty()) o.appendLine("Bids pulled: " + desk.pulls.entries.sortedByDescending { it.value }.take(6).joinToString(" · ") { "${it.key} ${it.value}" })
        }
        l.problem?.let { o.appendLine("Problem: $it") }
        return o.toString().trimEnd()
    }

    fun statusLine(s: ScanSettings, t: LiveTradeStatus, lab: LabStatus, light: com.tjshea.vigilant.data.novig.lab.LiveLightStatus = com.tjshea.vigilant.data.novig.lab.LiveLightStatus()): String {
        if (!s.tailLive.on) return "Off."
        if (s.tailLive.halted != null) return "Stopped: ${s.tailLive.halted}"
        val mode = if (s.tailLive.bet) "REAL" else "paper"
        val feed = if (light.running) "push lane: ${light.games} live games, ${light.withScore} with a score, ${light.pushes} pushes, ${light.offers} offers" else if (!lab.running) "game reader starting" else "${lab.games} live games read, ${lab.withState} with a score and clock"
        return "$mode: ${t.bets} bought, ${t.missed} missed, ${t.paper} paper, spent ${money(t.spent)} · $feed" + (t.last?.let { " · last: $it" } ?: "") +
            (lab.problem?.let { " · problem: $it" } ?: "")
    }

    fun diagnostics(s: ScanSettings, t: LiveTradeStatus, lab: LabStatus, records: List<LiveRecord>): String {
        val o = StringBuilder()
        o.appendLine("LIVE TAIL BETS (RESEARCH.md §125)")
        o.appendLine(
            "Switch: ${if (s.tailLive.on) "ON" else "off"} · ${if (s.tailLive.bet) "REAL money" else "paper"}" + (s.tailLive.halted?.let { " · HALTED: $it" } ?: "") +
                " · stake ${money(s.tailLive.stake)}, game ${money(s.tailLive.maxGame)}, day ${money(s.tailLive.maxDay)}, halt at ${money(s.tailLive.haltLoss)} lost · min edge ${pct(s.tailLive.minEdge)}",
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
