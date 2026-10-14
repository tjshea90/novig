package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.scanner.BetKind
import java.util.Locale
import kotlin.math.sqrt

/**
 * Is the fair line honest? (Tj, 2026-10-14: "Investigate how I'm consistently beating clv yet losing so much money. Either the clv values are wrong, or the EV analysis is wrong"; research/bet_history_2026-10-14.md.)
 * Both the EV and the CLV are measured against a no-vig price built from books. If that price were a true probability, the share of bets won would equal it. This lines each group's win rate up against the fair
 * chance the bet was placed on ([TrackedBet.fairAtBet]) and the chance at the close ([TrackedBet.closeFair]), with the z of the gap, so a fair line that runs high for a kind and side (the history's prop Overs:
 * 41% won against a 49% fair) shows up in every Diagnostics file instead of only in a bet-history study. Pure.
 */
object FairCalibration {
    private val SIDE = Regex("\\b(over|under)\\b", RegexOption.IGNORE_CASE)

    data class Row(val label: String, val n: Int, val fair: Double, val close: Double?, val won: Double, val roi: Double, val profit: Double) {
        val gap: Double get() = won - fair
        val z: Double get() = if (n > 0 && fair in 0.01..0.99) gap / sqrt(fair * (1.0 - fair) / n) else 0.0
    }

    private fun side(b: TrackedBet): String = SIDE.findAll(b.selection).lastOrNull()?.value?.lowercase() ?: "side"

    fun rows(bets: List<TrackedBet>, minN: Int = 20): List<Row> {
        val settled = bets.filter { (it.status == BetStatus.WON || it.status == BetStatus.LOST) && it.fairAtBet != null && !it.isLock && it.stake > 0.0 }
        fun row(label: String, v: List<TrackedBet>): Row? {
            if (v.size < minN) return null
            val closes = v.mapNotNull { it.closeFair }
            val stake = v.sumOf { it.stake }
            val profit = v.sumOf { it.profit ?: 0.0 }
            return Row(label, v.size, v.sumOf { it.fairAtBet!! } / v.size, closes.takeIf { it.size >= v.size / 2 }?.average(), v.count { it.status == BetStatus.WON }.toDouble() / v.size, if (stake > 0) profit / stake else 0.0, profit)
        }
        val out = ArrayList<Row>()
        row("all", settled)?.let { out += it }
        for (bid in listOf(false, true)) {
            val m = settled.filter { it.isBid == bid }
            row(if (bid) "bids" else "bets", m)?.let { out += it }
            for ((k, g) in m.groupBy { BetKind.of(it.marketLabel, it.selection) }.entries.sortedByDescending { it.value.size }) {
                if (k == BetKind.PROP) {
                    for ((s, h) in g.groupBy(::side)) row("${if (bid) "bids" else "bets"} · ${k.label} · $s", h)?.let { out += it }
                } else row("${if (bid) "bids" else "bets"} · ${k.label}", g)?.let { out += it }
            }
        }
        for ((lo, hi) in listOf(0.0 to 0.02, 0.02 to 0.035, 0.035 to 0.05, 0.05 to 1.0)) {
            row("bets · stated EV ${"%.1f".format(Locale.US, lo * 100)}-${"%.1f".format(Locale.US, hi * 100)}%", settled.filter { !it.isBid && (it.evPercentAtBet ?: 0.0) >= lo && (it.evPercentAtBet ?: 0.0) < hi })?.let { out += it }
        }
        return out
    }

    fun lines(bets: List<TrackedBet>): List<String> {
        val r = rows(bets)
        if (r.isEmpty()) return listOf("not enough settled bets yet")
        fun p(d: Double) = "%.1f".format(Locale.US, d * 100)
        return buildList {
            add("win rate against the fair chance the bet was placed on (and the chance at the close): a gap under zero with |z| over 2 means the fair line runs high for that group; the edge and the CLV are then overstated by about twice the gap")
            r.forEach { x ->
                add("  ${x.label}: n ${x.n} · fair ${p(x.fair)}%${x.close?.let { " · close ${p(it)}%" } ?: ""} · won ${p(x.won)}% · gap ${if (x.gap >= 0) "+" else ""}${p(x.gap)} pts (z ${"%.1f".format(Locale.US, x.z)}) · ROI ${if (x.roi >= 0) "+" else ""}${p(x.roi)}% · ${"%+.2f".format(Locale.US, x.profit)}")
            }
        }
    }
}
