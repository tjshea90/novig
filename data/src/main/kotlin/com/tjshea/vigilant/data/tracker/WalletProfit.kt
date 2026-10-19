package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigPosition
import kotlin.math.abs

/**
 * The Tracker's all-time money figure (Tj, 2026-10-19, PL: "I'd like the total all time profit loss to show the difference between my cash balance now from the initial slate of bets logged on day 1 (assume
 * the total stake of all bets on day 1 was my total deposit into the account)" ... "so I know if I'm making or losing money on my bets").
 *
 * What the Vigilant wallet is worth now (its cash plus what its open bets cost: money at stake is neither won nor lost until the game is graded) minus the money put into it, net of money taken out, from
 * Novig's own ledger (`TRANSFER_IN` / `TRANSFER_OUT` rows, written when a transfer is applied). The deposit is read, not assumed: day 1's stakes were $5.55 of a wallet that held over $200, so taking them
 * as the deposit would have shown a $235 profit that was never made. Fees and maker credits are in the cash, so they are in the figure too.
 */
object WalletProfit {
    data class Money(
        /** The wallet's balance now (resting bids are not held from it: NOVIG_API.md §17). */
        val cash: Double,
        /** What the open positions cost: the money on bets not settled yet. */
        val openCost: Double,
        val openBets: Int,
        /** Every transfer into the wallet, and every one out of it, all time. */
        val putIn: Double,
        val takenOut: Double,
        val transfers: Int,
        val atMs: Long,
    ) {
        val value: Double get() = cash + openCost
        val netIn: Double get() = putIn - takenOut
        val profit: Double get() = value - netIn
        /** Profit over the money put in, net; null when nothing (net) was put in. */
        val pct: Double? get() = if (netIn > 0.005) profit / netIn else null
    }

    const val IN = "TRANSFER_IN"
    const val OUT = "TRANSFER_OUT"

    /** The money on the open positions (contracts still held, at what they cost). */
    fun openCost(positions: List<NovigPosition>): Double = positions.filter { it.qty > 0 && it.cost > 0.0 }.sumOf { it.cost }

    /**
     * The figure from one reading of the wallet: [cash], [positions], and the ledger's transfer rows. Null when the ledger holds no transfer: then what was put in is not known, and a figure without it
     * would call the whole wallet profit.
     */
    fun of(cash: Double, positions: List<NovigPosition>, transfers: List<LedgerRow>, atMs: Long): Money? {
        if (!cash.isFinite()) return null
        val rows = transfers.filter { it.kind == IN || it.kind == OUT }.distinctBy { it.transactionId }
        if (rows.isEmpty()) return null
        val held = positions.filter { it.qty > 0 && it.cost > 0.0 }
        return Money(
            cash, held.sumOf { it.cost }, held.map { it.marketId }.distinct().size,
            rows.filter { it.kind == IN }.sumOf { abs(it.amount) }, rows.filter { it.kind == OUT }.sumOf { abs(it.amount) }, rows.size, atMs,
        )
    }
}
