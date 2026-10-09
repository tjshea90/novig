package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.burst.CoverMath
import com.tjshea.vigilant.data.novig.burst.Leg
import com.tjshea.vigilant.data.novig.burst.LadderLine
import com.tjshea.vigilant.engine.Fees

/** One market of a game's ladder and its book as read (null: no book read for it). */
data class LadderPoint(val line: LadderLine, val book: NovigBook?)

/** A cover that costs less than it pays: [lo] YES + [hi] NOT, [net] per $1 of payout after the fees that apply (none before the game starts). */
data class LadderCover(val lo: LadderLine, val hi: LadderLine, val yes: Leg, val no: Leg, val net: Double) {
    val cost: Double get() = yes.price + no.price
    val contracts: Long get() = minOf(yes.contracts, no.contracts)
}

/** What one ladder (one game's totals, or one team's margin lines) looked like in one snapshot. */
data class LadderReport(
    val ladderKey: String,
    val strikes: Int,
    /** Sides (a strike has two) that had something to buy / nothing to buy (the app shows 99.9% for those). */
    val pricedSides: Int,
    val unpricedSides: Int,
    /** Exactly one strike has any price at all: the lone offer is the only thing on the ladder (a stale or a longshot bid, never a cover). */
    val isolated: Boolean,
    val covers: List<LadderCover>,
    /** Pairs that cost within a cent of paying (net between -[LadderScan.NEAR] and the minimum): noise or a price about to cross. */
    val nearMisses: Int,
)

/**
 * Ladder-consistency detection (Tj, 2026-10-09, after his live-NFL screenshots: "under 52.5 is 99.9% and under 54.5 is 99.9%, yet under 53.5 is 90%"; RESEARCH.md §120.6).
 *
 * The lines of one game are separate order books for ONE number, so they must agree: a higher threshold can never be worth more than a lower one. Buying YES at the lower line and NOT at the higher one
 * pays at least $1 whatever happens, so a pair whose two asks sum to under $1 (after the in-play fee, none before the game) is a locked profit. [com.tjshea.vigilant.data.novig.burst.CoverWindows] already
 * finds those as books CHANGE on the live websocket; this reads any snapshot of books (a REST read, pregame or live) and also says how thin a ladder is. Nothing here places an order.
 */
object LadderScan {
    /** A cover must pay at least this per $1 of payout to be reported (the burst recorder's floor: under it is a rounding of a price). */
    const val MIN_NET = 0.003

    /** A pair this close below paying is a near miss. */
    const val NEAR = 0.01

    fun scan(points: List<LadderPoint>, live: Boolean): List<LadderReport> =
        points.groupBy { it.line.ladderKey }.map { (key, group) -> one(key, group.sortedBy { it.line.threshold }, live) }.sortedBy { it.ladderKey }

    private fun one(key: String, g: List<LadderPoint>, live: Boolean): LadderReport {
        var priced = 0
        var unpriced = 0
        val pricedStrikes = HashSet<String>()
        for (p in g) {
            for (yes in booleanArrayOf(true, false)) {
                if (CoverMath.leg(p.book, p.line, yes) != null) { priced++; pricedStrikes += p.line.marketId } else unpriced++
            }
        }
        val covers = ArrayList<LadderCover>()
        var near = 0
        for (i in g.indices) for (j in i + 1 until g.size) {
            val lo = g[i]
            val hi = g[j]
            if (lo.line.threshold >= hi.line.threshold) continue
            val yes = CoverMath.leg(lo.book, lo.line, yes = true) ?: continue
            val no = CoverMath.leg(hi.book, hi.line, yes = false) ?: continue
            val net = 1.0 - yes.price - no.price - fee(yes.price, lo.line, live) - fee(no.price, hi.line, live)
            when {
                net >= MIN_NET -> covers += LadderCover(lo.line, hi.line, yes, no, net)
                net > -NEAR -> near++
            }
        }
        return LadderReport(key, g.size, priced, unpriced, isolated = pricedStrikes.size == 1 && g.size >= 3, covers = covers.sortedByDescending { it.net }, nearMisses = near)
    }

    private fun fee(price: Double, line: LadderLine, live: Boolean): Double = if (price > 0.0 && price < 1.0) Fees.takerFee(price, line.fee, eventLive = live) else 0.0

    /** One line for Diagnostics: how many ladders were read, how thin they were, and the best cover if there was one. */
    fun summary(reports: List<LadderReport>): String {
        if (reports.isEmpty()) return "no ladder read"
        val sides = reports.sumOf { it.pricedSides + it.unpricedSides }
        val unpriced = reports.sumOf { it.unpricedSides }
        val covers = reports.flatMap { it.covers }
        val best = covers.maxByOrNull { it.net }
        return "${reports.size} ladder${if (reports.size == 1) "" else "s"}, ${reports.sumOf { it.strikes }} lines, $unpriced of $sides sides with nothing to buy" +
            (if (reports.any { it.isolated }) ", ${reports.count { it.isolated }} with one lone priced line" else "") +
            ", ${reports.sumOf { it.nearMisses }} near misses, " +
            if (best == null) "no cover" else "${covers.size} cover${if (covers.size == 1) "" else "s"} (best ${"%.1f".format(java.util.Locale.US, best.net * 100)}% on ${best.contracts} contracts: ${best.lo.label} YES + ${best.hi.label} NOT)"
    }
}
