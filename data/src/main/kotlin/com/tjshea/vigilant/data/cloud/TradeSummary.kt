package com.tjshea.vigilant.data.cloud

import java.time.Instant
import java.time.ZoneId

/**
 * What the phone shows of the engine's own trading (Tj, 2026-10-15: "if I open the vigilant app it automatically syncs ... what the automated system did ... wins/losses ... wallet"): the totals and the newest
 * orders of `trades.json`, worked out from the ledger alone so the phone needs nothing but the file. Pure.
 */
data class TradeSummary(
    val open: Int,
    val atRisk: Double,
    val won: Int,
    val lost: Int,
    val pushed: Int,
    /** Settled profit and loss in dollars, all entries kept (14 days) and just today's (Eastern day). */
    val pnl: Double,
    val pnlToday: Double,
    /** Profit over the money staked on settled entries; null before anything settled. */
    val roi: Double?,
    val ordersToday: Int,
    val newest: List<TradeEntry>,
) {
    /** One line: "3 open ($41.20 at risk) · 12 won · 9 lost · 1 push · +$18.40 settled, +$3.10 today". */
    fun line(): String = buildString {
        append("$open open (${money(atRisk)} at risk) · $won won · $lost lost")
        if (pushed > 0) append(" · $pushed push")
        append(" · ${signed(pnl)} settled, ${signed(pnlToday)} today")
        roi?.let { append(" · ROI ${"%+.1f".format(java.util.Locale.US, it * 100)}%") }
    }

    companion object {
        private val ET = ZoneId.of("America/New_York")
        private fun money(x: Double) = String.format(java.util.Locale.US, "$%.2f", x)
        private fun signed(x: Double) = (if (x < 0) "-" else "+") + money(kotlin.math.abs(x))

        fun of(l: TradeLedger, nowMs: Long, newest: Int = 12): TradeSummary {
            val today = Instant.ofEpochMilli(nowMs).atZone(ET).toLocalDate()
            fun day(e: TradeEntry) = Instant.ofEpochMilli(e.atMs).atZone(ET).toLocalDate()
            val settled = l.entries.filter { it.status == "WON" || it.status == "LOST" || it.status == "PUSH" }
            val open = l.entries.filter { it.status == "OPEN" || it.status == "UNCONFIRMED" }
            val staked = settled.filter { it.status != "PUSH" }.sumOf { it.cost }
            val pnl = settled.sumOf { it.pnl ?: 0.0 }
            return TradeSummary(
                open = open.size, atRisk = open.sumOf { it.cost },
                won = settled.count { it.status == "WON" }, lost = settled.count { it.status == "LOST" }, pushed = settled.count { it.status == "PUSH" },
                pnl = pnl, pnlToday = settled.filter { day(it) == today }.sumOf { it.pnl ?: 0.0 },
                roi = if (staked > 0.0) pnl / staked else null,
                ordersToday = l.entries.count { day(it) == today },
                newest = l.entries.sortedByDescending { it.atMs }.take(newest),
            )
        }
    }
}
