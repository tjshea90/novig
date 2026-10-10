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
        out += "Why so few fills:"
        whyFew(bids, status.skips).forEach { out += "  $it" }
        out += "Last bids:"
        bids.takeLast(10).reversed().forEach { b ->
            out += String.format(
                Locale.US, "  %s %s · %s · %s · bid %.3f fair %.3f EV %+.1f%% · %s%s", java.time.Instant.ofEpochMilli(b.postedAtMs).toString().substring(11, 19), b.mode, b.league, b.selection, b.price, b.fair, b.ev * 100,
                b.status.label, (b.why?.let { " ($it)" } ?: "") + (if (b.filled > 0) ", filled ${b.filled}" else ""),
            )
        }
        return out
    }

    /** Where a bid sat in Novig's book when it went up: at the front of its side, level with the best bid, or behind it. */
    enum class Place(val label: String) { LED("led the book"), JOINED("level with the best bid"), BEHIND("behind the best bid") }

    fun place(b: LiveBid): Place {
        val best = b.bestBid ?: return Place.LED
        return when {
            b.price > best + 0.0005 -> Place.LED
            b.price >= best - 0.0005 -> Place.JOINED
            else -> Place.BEHIND
        }
    }

    /** Seconds a bid was actually on the book (from first seen resting, else from posting) to its end; null while it is still up. */
    private fun lifeSec(b: LiveBid): Double? = b.endedAtMs?.let { (it - (b.openAtMs ?: b.postedAtMs)) / 1000.0 }

    /**
     * Why the bids get so few fills (Tj, 2026-10-10: "figure out why I don't get a lot of action on live bets and bids"), from what the bids themselves recorded: where each sat in the book when
     * it went up, how long it lived, how it ended, and what the desk said no to. Each line is a fact about the data; the last line is the reading, or says there is too little to read yet. [skips]
     * are the desk's counts of looks that ended in no bid. Pure.
     */
    fun whyFew(bids: List<LiveBid>, skips: Map<String, Int>): List<String> {
        val out = ArrayList<String>()
        val done = bids.filter { it.status.ended }
        if (bids.isEmpty()) {
            out += "No bid has gone up yet" + (skips.entries.sortedByDescending { it.value }.take(4).takeIf { it.isNotEmpty() }?.let { ": the looks ended in " + it.joinToString(" · ") { e -> "${e.key} ×${e.value}" } } ?: " (no line has been judged).")
            return out
        }
        val byPlace = bids.groupBy { place(it) }
        out += "Where bids sat when posted: " + Place.entries.filter { it in byPlace }.joinToString(" · ") { p ->
            val l = byPlace.getValue(p)
            "${p.label} ${l.size} (${l.count { it.filled > 0 }} filled, ${share(l.count { it.filled > 0 }.toDouble() / l.size)})"
        }
        val behind = byPlace[Place.BEHIND].orEmpty()
        if (behind.isNotEmpty()) {
            val gaps = behind.mapNotNull { b -> b.bestBid?.let { ((it - b.price) * 100).toLong() } }.sorted()
            if (gaps.isNotEmpty()) out += "Behind bids sat a median ${gaps[gaps.size / 2]}¢ under the best bid (the edge rule keeps the price down; a smaller margin lets it climb)"
        }
        val unfilled = done.filter { it.filled == 0L }
        val lives = unfilled.mapNotNull { lifeSec(it) }.sorted()
        if (lives.isNotEmpty()) out += "Unfilled bids lived a median ${"%.0f".format(Locale.US, lives[lives.size / 2])} s on the book (n=${lives.size}); ${unfilled.count { it.status == LiveBidStatus.CANCELED }} were pulled, ${unfilled.count { it.status == LiveBidStatus.EXPIRED }} ran out their time, ${unfilled.count { it.status == LiveBidStatus.REFUSED }} were refused"
        val pulled = unfilled.filter { it.status == LiveBidStatus.CANCELED }.groupBy { it.why ?: "no reason recorded" }.entries.sortedByDescending { it.value.size }.take(4)
        if (pulled.isNotEmpty()) out += "Pulled for: " + pulled.joinToString(" · ") { "${it.key} ×${it.value.size}" }
        val fills = bids.count { it.filled > 0 }
        val rate = fills.toDouble() / bids.size
        val led = byPlace[Place.LED].orEmpty()
        val ledRate = if (led.isEmpty()) null else led.count { it.filled > 0 }.toDouble() / led.size
        val behindShare = behind.size.toDouble() / bids.size
        val medLife = lives.getOrNull(lives.size / 2)
        out += when {
            bids.size < 10 -> "Reading: only ${bids.size} bids so far, too few to say why. Leave it running through a full slate."
            rate >= 0.15 -> "Reading: ${share(rate)} of bids fill, which is healthy; more fills come from more bids up (Fill the wallet) rather than from changing the rules."
            behindShare >= 0.5 && (ledRate ?: 0.0) > rate -> "Reading: most bids sit behind the best bid (${share(behindShare)}), and the ones that lead fill more (${share(ledRate)}): a bid priced for the margin can't get ahead of the book. A smaller margin or 'More fills' moves them up."
            medLife != null && medLife < 12.0 -> "Reading: bids are coming down after about ${"%.0f".format(Locale.US, medLife)} s, before a trade can find them (a live order takes ~5 s just to land). The pulls above are the cause."
            else -> "Reading: bids rest their full time and just are not traded against: the market has nobody selling into them at that price. More games and more markets up at once is the lever."
        }
        return out
    }

    private fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)
}
