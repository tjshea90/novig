package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.cloud.DashboardModel.Tone
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import java.util.Locale

/**
 * What the phone's dashboard shows of everything the GitHub engine is doing with money (Tj, 2026-10-16: "the point of the android app is to be a monitor and control center, so I need to be able to see everything
 * GitHub is doing whenever I open the android app"): the bets it placed, the bids it has up and the bids that filled, in one list, with the totals. Worked out from the two files the engine publishes (`trades.json`,
 * `desk.json`) and its heartbeat; pure, so every line is tested and the screen only draws.
 */
object EngineBoard {
    /** One line of the list: [kind] is BET, BID (resting) or FILL (a bid that filled); [title] the selection, [sub] the numbers. */
    data class Row(val atMs: Long, val kind: String, val title: String, val sub: String, val tone: Tone)

    data class Board(
        val headline: String,
        val bets: TradeSummary?,
        val fills: TradeSummary?,
        val resting: List<MakerBid>,
        val restingDollars: Double,
        val rows: List<Row>,
        /** What the desk says about itself (its last pass), blank when it never ran. */
        val deskNote: String,
        val deskStopped: String,
        /** True when the engine's desk is posting the bids now (the phone's own desk is then down). */
        val deskLive: Boolean,
    )

    fun isBidEntry(e: TradeEntry) = e.note.startsWith("bid")

    fun of(ledger: TradeLedger?, desk: DeskFile?, st: CloudStatus?, now: Long, maxRows: Int = 40): Board {
        val all = ledger?.entries.orEmpty()
        val betEntries = all.filterNot(::isBidEntry)
        val fillEntries = all.filter(::isBidEntry)
        val bets = ledger?.let { TradeSummary.of(it.copy(entries = betEntries), now, 0) }
        val fills = ledger?.let { TradeSummary.of(it.copy(entries = fillEntries), now, 0) }
        val resting = desk?.bids.orEmpty().filter { it.resting }.sortedBy { it.startsTs }
        val restingDollars = resting.sumOf { it.restingDollars }
        val rows = ArrayList<Row>()
        for (b in resting) {
            val left = (b.contracts - b.filled).coerceAtLeast(0)
            rows += Row(
                b.postedAtMs, "BID", "${b.selection} (${b.league})",
                "${b.eventName} · $left at ${cents(b.price)} · \$${money(b.restingDollars)} resting · edge ${pct(b.evAtFair)} at the fair" + (if (b.filled > 0) " · ${b.filled} filled" else "") + (if (b.overflow) " · riskier" else ""),
                Tone.NEUTRAL,
            )
        }
        for (e in all.sortedByDescending { it.atMs }) {
            val bid = isBidEntry(e)
            val result = when (e.status) {
                "OPEN", "UNCONFIRMED" -> "open"
                else -> e.status.lowercase() + (e.pnl?.let { " " + signed(it) } ?: "")
            }
            val tone = when (e.status) { "WON" -> Tone.GOOD; "LOST" -> Tone.BAD; "UNCONFIRMED" -> Tone.WARN; else -> Tone.NEUTRAL }
            rows += Row(
                e.atMs, if (bid) "FILL" else "BET", "${e.selection} (${e.league})",
                "${e.event} · \$${money(e.cost)} @ ${cents(e.avgPrice)} · edge ${pct(e.evPercent)}" + (if (e.mode.equals("paper", true)) " · PAPER" else "") + " · $result", tone,
            )
        }
        val headline = buildString {
            val open = (bets?.open ?: 0) + (fills?.open ?: 0)
            append("$open open (\$${money((bets?.atRisk ?: 0.0) + (fills?.atRisk ?: 0.0))}) · ${resting.size} bids resting (\$${money(restingDollars)})")
            val today = (bets?.ordersToday ?: 0) + (fills?.ordersToday ?: 0)
            append(" · $today placed or filled today")
            val pnl = (bets?.pnl ?: 0.0) + (fills?.pnl ?: 0.0)
            if ((bets?.won ?: 0) + (bets?.lost ?: 0) + (fills?.won ?: 0) + (fills?.lost ?: 0) > 0) append(" · ${signed(pnl)} settled")
        }
        return Board(headline, bets, fills, resting, restingDollars, rows.take(maxRows), st?.deskNote.orEmpty(), st?.deskStopped.orEmpty(), st?.deskLive(now) == true)
    }

    private fun money(x: Double) = String.format(Locale.US, "%.2f", x)
    private fun cents(p: Double) = String.format(Locale.US, "%.0fc", p * 100)
    private fun pct(x: Double) = String.format(Locale.US, "%.1f%%", x * (if (kotlin.math.abs(x) < 1.0) 100 else 1))
    private fun signed(x: Double) = (if (x < 0) "-$" else "+$") + String.format(Locale.US, "%.2f", kotlin.math.abs(x))
}
