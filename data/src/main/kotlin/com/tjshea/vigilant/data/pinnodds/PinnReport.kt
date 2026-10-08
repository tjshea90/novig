package com.tjshea.vigilant.data.pinnodds

import java.util.Locale

/**
 * What the live feed has done and whether its edge held up (Tj, 2026-10-08: "ensure that it only bets truly positive EV"). A decision is judged on three prices: the EV it was taken at (Pinnacle's
 * fair against Novig's ask and fee, at the moment), and the EV the SAME ask would have had against Pinnacle's fair 30 and 120 seconds later ([LiveFollow]). If Pinnacle's later fair stays up
 * there, the edge was real; if it falls back, Pinnacle's move was noise (a spike) and the bet was not worth it. Pure.
 */
object PinnReport {
    data class Stats(
        val n: Int,
        val avgEvAtBet: Double,
        val n30: Int,
        val avgEv30: Double?,
        val n120: Int,
        val avgEv120: Double?,
        /** Of the decisions with a 120 s reading, the share whose later fair was at least as high as at the decision less one point: Pinnacle kept the move. */
        val held120: Double?,
        /** Of the decisions with a 120 s reading, the share where Novig's ask had moved up toward the fair (the lag closed). */
        val novigFollowed120: Double?,
    )

    private fun evLater(r: LiveRecord, fairLater: Double): Double = fairLater / (r.ask + r.fee) - 1.0

    fun stats(records: List<LiveRecord>, follows: List<LiveFollow>): Stats {
        val byId = follows.groupBy { it.id }
        fun at(r: LiveRecord, sec: Int) = byId[r.id]?.firstOrNull { it.offsetSec == sec && !it.closed && it.fair != null }
        val f30 = records.mapNotNull { r -> at(r, 30)?.let { evLater(r, it.fair!!) } }
        val both = records.mapNotNull { r -> at(r, 120)?.let { r to it } }
        val f120 = both.map { (r, f) -> evLater(r, f.fair!!) }
        return Stats(
            n = records.size,
            avgEvAtBet = records.map { it.ev }.average().takeIf { !it.isNaN() } ?: 0.0,
            n30 = f30.size, avgEv30 = f30.average().takeIf { !it.isNaN() },
            n120 = f120.size, avgEv120 = f120.average().takeIf { !it.isNaN() },
            held120 = both.takeIf { it.isNotEmpty() }?.let { l -> l.count { (r, f) -> f.fair!! >= r.fair - 0.01 }.toDouble() / l.size },
            novigFollowed120 = both.mapNotNull { (r, f) -> f.ask?.let { a -> a > r.ask + 0.004 } }.takeIf { it.isNotEmpty() }?.let { l -> l.count { it }.toDouble() / l.size },
        )
    }

    private fun pct(x: Double?) = if (x == null) "n/a" else String.format(Locale.US, "%+.1f%%", x * 100)
    private fun share(x: Double?) = if (x == null) "n/a" else String.format(Locale.US, "%.0f%%", x * 100)

    /** The lines of the Diagnostics block for [records] and [follows] (all kept days). */
    fun lines(records: List<LiveRecord>, follows: List<LiveFollow>): List<String> {
        if (records.isEmpty()) return listOf("No decisions yet.")
        val out = ArrayList<String>()
        val bets = records.filter { it.mode == "BET" }
        val paper = records.filter { it.mode == "PAPER" }
        fun block(title: String, l: List<LiveRecord>) {
            if (l.isEmpty()) return
            val s = stats(l, follows)
            out += "$title: ${s.n} decisions · EV at decision ${pct(s.avgEvAtBet)} · the same ask against Pinnacle's fair 30 s later ${pct(s.avgEv30)} (n=${s.n30}), 120 s later ${pct(s.avgEv120)} (n=${s.n120}) · " +
                "Pinnacle kept the move ${share(s.held120)} · Novig followed ${share(s.novigFollowed120)}"
        }
        block("Real bets", bets)
        block("Paper decisions", paper)
        if (bets.isNotEmpty()) {
            val filled = bets.filter { it.outcome == "FILLED" || it.outcome == "PARTIAL" }
            val missed = bets.count { it.outcome == "MISSED" }
            val ms = filled.map { it.sendToEndMs }.sorted()
            out += "Orders: ${bets.size} sent · ${filled.size} filled · $missed missed (the offer was gone) · ${bets.count { it.outcome == "REFUSED" }} refused · ${bets.count { it.outcome == "UNCONFIRMED" }} unconfirmed" +
                (if (ms.isNotEmpty()) " · send-to-fill median ${ms[ms.size / 2]} ms" else "") +
                " · Pinnacle move to decision median ${bets.map { it.decisionMs }.sorted()[bets.size / 2]} ms"
            // Why orders miss: how long they took to end (the in-play delay shows here) and whether the missed ones were lags at all (a move of 0 is a standing disagreement).
            val missedRecs = bets.filter { it.outcome == "MISSED" }
            val endAll = bets.filter { it.sendToEndMs > 0 }.map { it.sendToEndMs }.sorted()
            if (endAll.isNotEmpty()) {
                out += "Order time, all orders: median ${endAll[endAll.size / 2]} ms · slowest ${endAll.last()} ms" +
                    (if (missedRecs.isNotEmpty()) " · missed with no Pinnacle move behind them ${missedRecs.count { (it.move ?: 0.0) < 0.005 }} of ${missedRecs.size}" else "")
            }
            out += String.format(Locale.US, "Spent: $%.2f on %d contracts (fees $%.2f)", filled.sumOf { it.paid }, filled.sumOf { it.filled }, filled.sumOf { it.feePaid })
        }
        out += "By league: " + records.groupBy { it.league }.entries.sortedByDescending { it.value.size }.take(6).joinToString(" · ") { "${it.key} ${it.value.size}" }
        out += "Last decisions:"
        records.takeLast(12).reversed().forEach { r ->
            out += String.format(
                Locale.US, "  %s %s · %s · %s · ask %.3f fair %.3f EV %+.1f%% move %s · %s", java.time.Instant.ofEpochMilli(r.atMs).toString().substring(11, 19), r.mode, r.league, r.selection, r.ask, r.fair, r.ev * 100,
                r.move?.let { String.format(Locale.US, "%+.1f pts", it * 100) } ?: "n/a", r.outcome,
            )
        }
        return out
    }
}
