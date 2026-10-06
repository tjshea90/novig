package com.tjshea.vigilant.data.novig.trading.burst

import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * What the real-money burst trader did, in words (Diagnostics and the share file; RESEARCH.md §95): the one number that settles Tj's question is the REAL result of covers that were sent,
 * against what the recorder's paper trade said they would make. Dollars are the order's own money (cost and fee as Novig reported them), not the Tracker's.
 */
object BurstTradeReport {
    private val json = Json { encodeDefaults = true }

    private fun money(d: Double) = (if (d < 0) "-$" else "$") + "%.2f".format(Locale.US, Math.abs(d))

    /** Totals over [trades]: how each attempt ended, the money, and how fast the trader moved. */
    fun summary(trades: List<TradeRecord>): String {
        if (trades.isEmpty()) return "no real-money attempt yet"
        val by = trades.groupingBy { it.outcome }.eachCount()
        val locked = trades.sumOf { it.lockedProfit }
        val naked = trades.sumOf { it.nakedCost }
        val spent = trades.sumOf { it.paid + it.fees }
        val decisions = trades.map { it.decisionMs }.sorted()
        val sent = trades.count { it.outcome != "REFUSED" }
        return "${trades.size} attempt${if (trades.size == 1) "" else "s"}: " +
            listOf("LOCKED", "PARTIAL", "NAKED", "NONE", "REFUSED", "UNCONFIRMED").filter { (by[it] ?: 0) > 0 }.joinToString(", ") { "${by[it]} ${it.lowercase()}" } +
            " · profit locked ${money(locked)} · legs held alone cost ${money(naked)} (not counted as lost: they are open bets) · spent ${money(spent)} on $sent sent" +
            " · the trader took ${decisions[decisions.size / 2]} ms at the median from the window opening to its order"
    }

    /** The section of the share file: the summary, then each attempt as one JSON line. */
    fun section(trades: List<TradeRecord>): String {
        val o = StringBuilder()
        o.appendLine("  ${summary(trades)}")
        for (t in trades.sortedBy { it.atMs }) o.appendLine(json.encodeToString(TradeRecord.serializer(), t))
        return o.toString()
    }
}
