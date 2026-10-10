package com.tjshea.vigilant.data.livebid

import java.util.Locale

/**
 * What the live bids have done and whether their edge held (Tj, 2026-10-10: "make sure ... the live bids are truly EV"). A bid is judged on three prices: the EV it was posted at (Pinnacle's fair against
 * the bid's price), and the EV the SAME price had against Pinnacle's fair 30 and 120 seconds after it filled. A fill whose fair has fallen under the price paid was picked off: the market moved
 * through the bid. The means are what matter (a median hides the fills that lose), with the share picked off beside them. Pure.
 */
object LiveBidReport {
    data class Stats(
        val bids: Int,
        val filledBids: Int,
        val contracts: Long,
        val paid: Double,
        val avgEvAtPost: Double?,
        val n30: Int,
        val avgEv30: Double?,
        val n120: Int,
        val avgEv120: Double?,
        val pickedOff: Double?,
        val strict: Double?,
    )

    fun stats(bids: List<LiveBid>): Stats {
        val filled = bids.filter { it.filled > 0 }
        val e30 = filled.mapNotNull { it.evAt(it.fairAt30) }
        val e120 = filled.mapNotNull { it.evAt(it.fairAt120) }
        val judged = filled.mapNotNull { it.pickedOff }
        return Stats(
            bids = bids.size, filledBids = filled.size, contracts = filled.sumOf { it.filled }, paid = filled.sumOf { it.paid },
            avgEvAtPost = filled.map { it.ev }.average().takeIf { !it.isNaN() },
            n30 = e30.size, avgEv30 = e30.average().takeIf { !it.isNaN() }, n120 = e120.size, avgEv120 = e120.average().takeIf { !it.isNaN() },
            pickedOff = judged.takeIf { it.isNotEmpty() }?.let { l -> l.count { it }.toDouble() / l.size },
            strict = filled.takeIf { it.isNotEmpty() }?.let { l -> l.count { it.strict }.toDouble() / l.size },
        )
    }

    private fun pct(x: Double?) = if (x == null) "n/a" else String.format(Locale.US, "%+.1f%%", x * 100)
    private fun share(x: Double?) = if (x == null) "n/a" else String.format(Locale.US, "%.0f%%", x * 100)
    private fun median(xs: List<Long>): Long? = xs.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    private fun p90(xs: List<Long>): Long? = xs.sorted().let { if (it.isEmpty()) null else it[(0.9 * (it.size - 1)).toInt()] }

    /** The lines of the Diagnostics block for [bids] (all kept days) and the desk's [status]. */
    fun lines(bids: List<LiveBid>, status: LiveBidDeskStatus): List<String> {
        val out = ArrayList<String>()
        out += "Desk: ${status.mode} · ${status.active} up (${money(status.restingDollars)} if all filled) · today ${status.posted} posted, ${status.fills} filled for ${money(status.paid)}" +
            (status.halted?.let { " · HALTED: $it" } ?: "") + (status.standDown?.let { " · stood down: $it" } ?: "") + (if (status.refused > 0) " · ${status.refused} refused" else "")
        if (status.timing.isNotEmpty()) out += "Timing (this run): ${status.timing}"
        status.problem?.let { out += "Problem: $it" }
        if (status.pulls.isNotEmpty()) out += "Why bids came down: " + status.pulls.entries.sortedByDescending { it.value }.take(8).joinToString(" · ") { "${it.key} ${it.value}" }
        if (status.skips.isNotEmpty()) out += "Why no bid (counts of looks): " + status.skips.entries.sortedByDescending { it.value }.take(10).joinToString(" · ") { "${it.key} ${it.value}" }
        if (bids.isEmpty()) return out + "No live bids yet."
        for ((title, list) in listOf("Real bids" to bids.filter { it.real }, "Paper bids" to bids.filter { !it.real })) {
            if (list.isEmpty()) continue
            val s = stats(list)
            out += "$title: ${s.bids} posted · ${s.filledBids} filled (${s.contracts} contracts, ${money(s.paid)}) · EV at post ${pct(s.avgEvAtPost)} · the same price against Pinnacle's fair 30 s after the fill ${pct(s.avgEv30)} " +
                "(n=${s.n30}), 120 s ${pct(s.avgEv120)} (n=${s.n120}) · picked off ${share(s.pickedOff)} · through the price ${share(s.strict)}"
            val ack = list.mapNotNull { it.ackMs }
            val open = list.mapNotNull { it.openMs }.filter { list.any { b -> b.real } }
            val pull = list.mapNotNull { it.pullMs }
            if (list.any { it.real }) {
                out += "  order accepted: median ${median(ack) ?: "n/a"} ms, p90 ${p90(ack) ?: "n/a"} · on the book: median ${median(open) ?: "n/a"} ms, p90 ${p90(open) ?: "n/a"} · pulled: median ${median(pull) ?: "n/a"} ms, p90 ${p90(pull) ?: "n/a"} (n=${pull.size})"
            }
            val ended = list.filter { it.status.ended }
            out += "  ended: " + ended.groupBy { it.status.label }.entries.sortedByDescending { it.value.size }.joinToString(" · ") { "${it.key} ${it.value.size}" }
        }
        out += "By league: " + bids.groupBy { it.league }.entries.sortedByDescending { it.value.size }.take(6).joinToString(" · ") { "${it.key} ${it.value.size}" }
        out += "By line: " + bids.groupBy { it.marketLabel }.entries.sortedByDescending { it.value.size }.joinToString(" · ") { "${it.key} ${it.value.size} (${it.value.count { b -> b.filled > 0 }} filled)" }
        out += "Last bids:"
        bids.takeLast(10).reversed().forEach { b ->
            out += String.format(
                Locale.US, "  %s %s · %s · %s · bid %.3f fair %.3f EV %+.1f%% · %s%s", java.time.Instant.ofEpochMilli(b.postedAtMs).toString().substring(11, 19), b.mode, b.league, b.selection, b.price, b.fair, b.ev * 100,
                b.status.label, (b.why?.let { " ($it)" } ?: "") + (if (b.filled > 0) ", filled ${b.filled}" else ""),
            )
        }
        return out
    }

    private fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)
}
