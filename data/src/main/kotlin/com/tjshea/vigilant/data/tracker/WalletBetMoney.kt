package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigFill
import java.util.Locale

/**
 * The Tracker's money, from Novig's own wallet records (DG5, Tj 2026-11-16: "I want all of the tracker math to be done from novig data").
 *
 * For each market the wallet traded: what it PAID is the market's FILL rows (negative) plus FEE rows less MAKER_CREDIT rows (a ledger FILL, FEE or MAKER_CREDIT row names a fill id, and the fill says which
 * market), what it was PAID BACK is the SETTLEMENT rows (their ref is the market or one of its orders). The market's net is the difference, to the cent, fees included. The Tracker's bets on the market get
 * that net (shared by stake when more than one bet is in it) as [TrackedBet.netPnl], and a settlement that arrived settles a bet the grader had not (status from the money: a net gain = won, ~0 = push).
 * A market holding both outcomes keeps its grader's statuses (only its money is exact); nothing is invented for a bet with no ledger rows.
 */
object WalletBetMoney {
    data class Market(val paid: Double, val payout: Double, val settledAtMs: Long?) {
        val net: Double get() = payout - paid
    }

    /** Per market id. */
    fun markets(ledger: List<LedgerRow>, fills: List<NovigFill>): Map<String, Market> {
        val marketOfFill = fills.associate { it.fillId to it.marketId }
        val orderMarket = fills.associate { it.orderId to it.marketId }
        val paid = HashMap<String, Double>()
        val payout = HashMap<String, Double>()
        val at = HashMap<String, Long>()
        for (r in ledger.distinctBy { it.transactionId }) {
            val ref = r.ref ?: continue
            when (r.kind) {
                "FILL", "FEE", "MAKER_CREDIT" -> {
                    val m = marketOfFill[ref] ?: continue
                    paid[m] = (paid[m] ?: 0.0) - r.amount
                }
                "SETTLEMENT" -> {
                    val m = marketOfFill[ref] ?: orderMarket[ref] ?: ref
                    payout[m] = (payout[m] ?: 0.0) + r.amount
                    at[m] = maxOf(at[m] ?: 0L, r.ts)
                }
            }
        }
        return (paid.keys + payout.keys).associateWith { Market(paid[it] ?: 0.0, payout[it] ?: 0.0, at[it]) }
    }

    /** [bets] with Novig's money laid over them; unchanged where the ledger has nothing for the bet's market. */
    fun apply(bets: List<TrackedBet>, ledger: List<LedgerRow>, fills: List<NovigFill>): List<TrackedBet> =
        if (ledger.isEmpty() || fills.isEmpty()) bets else apply(bets, markets(ledger, fills))

    /** The same with the per-market money already worked out ([markets] reads every ledger row: do it once per new file, not once per saved bet). */
    fun apply(bets: List<TrackedBet>, byMarket: Map<String, Market>): List<TrackedBet> {
        if (byMarket.isEmpty()) return bets
        val perMarket = bets.filter { it.marketId.isNotBlank() && it.status != BetStatus.VOID && byMarket[it.marketId]?.paid?.let { p -> p > 0.0 } == true }.groupBy { it.marketId }
        val out = HashMap<String, TrackedBet>()
        for ((market, group) in perMarket) {
            val m = byMarket.getValue(market)
            val sides = group.map { it.outcomeId }.distinct().size
            val totalStake = group.sumOf { it.stake }.takeIf { it > 0.0 } ?: continue
            val settled = m.payout > 0.0 || group.any { it.status != BetStatus.PENDING && it.status != BetStatus.VOID }
            if (!settled) continue
            for (b in group) {
                // The grader says won (or push) but Novig has not paid the settlement yet: the market's net is only the cost so far, so it would show a WIN as a full loss
                // (Tj 2026-11-20: "2 won, 0 lost" and -$2.00). Keep the computed profit until the payout row arrives.
                if (m.payout <= 0.0 && (b.status == BetStatus.WON || b.status == BetStatus.PUSH || b.status == BetStatus.FMV)) continue
                val share = b.stake / totalStake
                val pnl = Math.round(m.net * share * 100.0) / 100.0
                var status = b.status
                if (sides == 1 && m.payout > 0.0 && (b.status == BetStatus.PENDING || b.status == BetStatus.LOST)) {
                    status = if (m.net > 0.005) BetStatus.WON else if (m.net > -0.005) BetStatus.PUSH else BetStatus.FMV
                }
                out[b.id] = b.copy(status = status, netPnl = pnl, settledAtMs = b.settledAtMs ?: m.settledAtMs, settleValue = if (status == BetStatus.FMV) b.settleValue else null)
            }
        }
        if (out.isEmpty()) return bets
        return bets.map { out[it.id] ?: it }
    }

    fun describe(m: Map<String, Market>): String = String.format(Locale.US, "%d markets, net %+.2f", m.size, m.values.filter { it.payout > 0.0 }.sumOf { it.net })
}
