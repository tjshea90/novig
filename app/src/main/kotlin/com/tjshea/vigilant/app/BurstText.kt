package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.novig.burst.BurstLine
import com.tjshea.vigilant.data.novig.burst.BurstStatus
import com.tjshea.vigilant.data.novig.burst.BurstStudy
import com.tjshea.vigilant.data.novig.trading.burst.BurstTradeReport
import com.tjshea.vigilant.data.novig.trading.burst.BurstTradeStatus
import com.tjshea.vigilant.data.novig.trading.burst.TradeRecord
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.util.Locale

/** The live burst recorder's words for Settings and Diagnostics, free of Compose (RESEARCH.md §95). */
object BurstText {
    const val SWITCH_TITLE = "Live burst recorder (no orders)"
    const val SWITCH_SUB = "Records the cross-line mispricings after plays in live games. Never places or cancels anything."
    const val BUTTON = "Share live burst study with Claude"
    const val LEAGUES_TITLE = "Leagues to watch"

    const val HINT = "After a play, a game's moneyline, spreads and totals are re-quoted one after another, and for a moment a pair can cost under $1 to cover (a profit whatever happens). " +
        "This watches Novig's live books with your READ key (it has no way to place an order), logs each such window and what a taker with YOUR measured delays (your signed round trip to Novig, how late " +
        "pushes arrive) would have found when his order arrived, on paper, at your per-bet limit. Leave it on through live games (it uses one extra websocket and a little battery, and runs while the app is open or the background scan is on: Android may end the app otherwise) and tap Share: " +
        "after 3 games and 10 windows it says whether a real $1 test is worth running. It cannot prove a profit by itself: no order is sent, so a faster rival or a refused order isn't seen."

    const val TRADE_TITLE = "Real-money trader (needs the recorder's proof)"
    const val TRADE_SUB = "Buys both legs of a cover at once, with real money, within the limits below."
    const val TRADE_LOCKED = "Locked"
    const val TRADE_CONFIRM_TITLE = "Trade with real money?"
    const val TRADE_RESUME = "Resume trading"

    const val TRADE_HINT = "OFF, and it cannot be turned on until the recorder above has proved itself on THIS phone with THIS key: your delays measured (not assumed), at least 3 games and 10 windows that paid 1 cent " +
        "or more, and a paper result that is positive at your slow delay. Then it is still your switch. Each time a window opens it re-reads the books, and if the pair still pays it sends BOTH legs as one request of two " +
        "immediate-or-cancel orders at the prices it saw (they never rest), sized to your stake, your game and day limits and what is on offer. A leg that fills alone is bought out at break-even once; failing that it " +
        "is a bet you hold, and two such covers in a row, or the loss limit, halt it until you tap Resume. It keeps clear of your own resting bids, leaves a market Novig refuses alone, and stops at STOP ALL. Its legs are " +
        "not Tracker bets (one leg of a cover always loses): its record is the share file. Paper cannot prove a profit; the first real orders are the real test, so start at $1."

    /** The confirmation before the switch goes on: what it will do with money, in the user's numbers. */
    fun tradeConfirm(s: ScanSettings): String = "This places REAL orders on Novig from your Vigilant wallet, on its own, in live games: up to ${money(s.burstTradeStake)} a leg, ${money(s.burstTradeMaxGame)} a game and " +
        "${money(s.burstTradeMaxDay)} a day. It halts after legs held alone cost ${money(s.burstTradeHaltLoss)} or two covers in a row end that way. STOP ALL stops it at once. Turn it on?"

    /** What the page says about the trader: why it is locked (the proof's own reason), or that it is unlocked, and what it has done. [proofReason] null = proved. */
    fun tradeLine(proofReason: String?, status: BurstTradeStatus, settings: ScanSettings): String {
        val gate = when {
            !settings.burstRecorder -> "Locked: switch the recorder on first (it is the recorder's windows that are traded)"
            proofReason != null -> "Locked: $proofReason"
            settings.burstTrade && settings.burstTradeHalted == null -> "ON: trading with real money"
            else -> "Unlocked: the recorder has proved it on this phone; the switch is yours"
        }
        val did = if (status.attempts + status.refused == 0) "no order sent yet" else
            "${status.attempts} sent: ${status.locked} locked (${money(status.lockedProfit)}), ${status.partial + status.naked} with a leg held alone (${money(status.nakedCost)}), ${status.none} not filled, ${status.refused} refused"
        return "$gate · $did" + (status.last?.let { " · last: $it" } ?: "")
    }

    private fun money(d: Double) = "$" + (if (d == Math.floor(d)) d.toInt().toString() else "%.2f".format(Locale.US, d))

    /** One line for the page: what it is doing now. */
    fun note(status: BurstStatus, records: List<BurstLine>): String {
        val totals = BurstStudy.summarize(records).lastOrNull()
        val recorded = if (totals == null || (totals.games == 0 && totals.windows == 0)) "nothing recorded yet" else
            "${totals.windows} window${if (totals.windows == 1) "" else "s"} in ${totals.games} game${if (totals.games == 1) "" else "s"} recorded: ${BurstStudy.verdict(totals).label}"
        val now = when {
            status.problem != null && !status.running -> "Not recording: ${status.problem}"
            !status.running -> "Off"
            status.games == 0 -> "Waiting for a live game of ${status.leagues.sorted().joinToString(", ")}"
            else -> "Recording ${status.games} live game${if (status.games == 1) "" else "s"} (${status.lines} lines, ${"%,d".format(Locale.US, status.updates)} pushes, ${status.open} window${if (status.open == 1) "" else "s"} open now)"
        }
        return now + (status.problem?.takeIf { status.running }?.let { " · $it" } ?: "") + " · $recorded"
    }

    /** The Diagnostics block: null when the recorder was never switched on and nothing was recorded. */
    fun diagnostics(status: BurstStatus, records: List<BurstLine>, latencyNote: String, settings: ScanSettings, running: Boolean, trades: List<TradeRecord> = emptyList(), trader: BurstTradeStatus = BurstTradeStatus(), proofReason: String? = null): String? {
        if (!settings.burstRecorder && records.isEmpty() && trades.isEmpty()) return null
        val o = StringBuilder()
        o.appendLine("Live burst recorder: ${if (settings.burstRecorder) "ON" else "off"} · ${if (running) "running" else "not running"} · leagues ${settings.burstLeagues.sorted().joinToString(", ")}")
        o.appendLine("  now: ${note(status, emptyList()).substringBefore(" · nothing recorded yet").substringBefore(" · ")}${status.problem?.let { " · PROBLEM: $it" } ?: ""}")
        o.append(BurstStudy.report(records, latencyNote, settings.apiMaxStake))
        o.appendLine("Burst trader (real money): ${if (settings.burstTrade) "ON" else "off"} · stake ${money(settings.burstTradeStake)} a leg, ${money(settings.burstTradeMaxGame)} a game, ${money(settings.burstTradeMaxDay)} a day, halts at ${money(settings.burstTradeHaltLoss)} held alone" +
            (settings.burstTradeHalted?.let { " · HALTED: $it" } ?: ""))
        o.appendLine("  ${tradeLine(proofReason, trader, settings)}")
        if (trader.skipped.isNotEmpty()) o.appendLine("  held back this run: ${trader.skipped.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }}")
        if (trades.isNotEmpty()) o.appendLine("  ${BurstTradeReport.summary(trades)}")
        return o.toString()
    }
}
