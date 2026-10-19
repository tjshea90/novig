package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigPosition
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import java.util.Locale

/**
 * The Tracker's all-time money figure (Tj, 2026-10-19, PL: "I'd like the total all time profit loss to show the difference between my cash balance now from the initial slate of bets logged on day 1 (assume
 * the total stake of all bets on day 1 was my total deposit into the account)" ... "so I know if I'm making or losing money on my bets"; and then: "Make sure the wallet figure also takes out withdrawals
 * because sometimes I moved money back and forth").
 *
 * What the Vigilant wallet is worth now (its cash plus what its open bets cost: money at stake is neither won nor lost until the game is graded) minus the money put into it, net of every dollar taken back
 * out, from Novig's own ledger: every row of a money-moving kind ([NovigTradingClient.MONEY_MOVES]: transfers to and from the cash wallet, deposits, withdrawals, credits), each counted by the sign Novig
 * gives it ("Signed. A negative amount debits the wallet."), so money moved out and back in again nets to what stayed. The deposit is read, not assumed: day 1's stakes were $5.55 of a wallet that held over
 * $200, so taking them as the deposit would have shown a $235 profit that was never made. Fees and maker credits are in the cash, so they are in the figure too.
 */
object WalletProfit {
    data class Money(
        /** The wallet's balance now (resting bids are not held from it: NOVIG_API.md §17). */
        val cash: Double,
        /** What the open positions cost: the money on bets not settled yet. */
        val openCost: Double,
        val openBets: Int,
        /** Every dollar moved into the wallet, and every one moved out of it, all time. */
        val putIn: Double,
        val takenOut: Double,
        /** How many ledger rows moved money in or out. */
        val transfers: Int,
        val atMs: Long,
        /** Each kind's net and count, for Diagnostics ("TRANSFER_IN +250.00 (2), TRANSFER_OUT -20.00 (1)"). */
        val detail: String = "",
    ) {
        val value: Double get() = cash + openCost
        val netIn: Double get() = putIn - takenOut
        val profit: Double get() = value - netIn
        /** Profit over the money put in, net; null when nothing (net) was put in. */
        val pct: Double? get() = if (netIn > 0.005) profit / netIn else null
    }

    /** The money on the open positions (contracts still held, at what they cost). */
    fun openCost(positions: List<NovigPosition>): Double = positions.filter { it.qty > 0 && it.cost > 0.0 }.sumOf { it.cost }

    /**
     * The figure from one reading of the wallet: [cash], [positions], and the ledger's money-moving rows. Null when the ledger holds none: then what was put in is not known, and a figure without it would
     * call the whole wallet profit.
     */
    fun of(cash: Double, positions: List<NovigPosition>, moves: List<LedgerRow>, atMs: Long): Money? {
        if (!cash.isFinite()) return null
        val rows = moves.filter { it.kind in NovigTradingClient.MONEY_MOVES && it.amount.isFinite() }.distinctBy { it.transactionId }
        if (rows.isEmpty()) return null
        val held = positions.filter { it.qty > 0 && it.cost > 0.0 }
        val detail = rows.groupBy { it.kind }.entries.sortedBy { NovigTradingClient.MONEY_MOVES.indexOf(it.key) }
            .joinToString(", ") { (kind, list) -> String.format(Locale.US, "%s %+.2f (%d)", kind, list.sumOf { it.amount }, list.size) }
        return Money(
            cash, held.sumOf { it.cost }, held.map { it.marketId }.distinct().size,
            rows.filter { it.amount > 0.0 }.sumOf { it.amount }, rows.filter { it.amount < 0.0 }.sumOf { -it.amount }, rows.size, atMs, detail,
        )
    }
}
