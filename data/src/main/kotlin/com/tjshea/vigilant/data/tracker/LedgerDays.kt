package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigFill
import java.time.LocalDate

/**
 * Daily profit and loss from NOVIG'S OWN WALLET (LD2/LD3, Tj 2026-11-16: "for the daily charts and daily profit/loss, do not use the numbers from before. use novig's numbers from the wallet for each day's
 * profit and loss").
 *
 * Per market the wallet traded ([WalletBetMoney.markets]): paid = fills + fees - maker credits, payout = settlements. A market is FINAL when it paid out, or when a Tracker bet on it is graded (a lost
 * bet has no settlement row), or (no Tracker bet at all) its last fill is more than [STALE_MS] old. A market's net (payout - paid) is the day's profit, on the game day of its Tracker bets (else the day
 * of its settlement less [SlateProfit.TYPICAL_GAME_MS], else of its last fill). Markets whose fills are older than the phone's window (paid = 0) are left out, never counted as pure profit.
 * Pure, for tests: the same wallet gives the same days.
 */
object LedgerDays {
    const val STALE_MS = 3L * 86_400_000L
    /** A fill this young may not show in Novig's positions yet. */
    const val RESTING_MS = 10L * 60_000L

    @Volatile var days: List<SlateProfit.Day> = emptyList()
    /** Bumps on every [set], so a screen can tell the wallet's numbers changed. */
    @Volatile var version: Int = 0
    fun set(d: List<SlateProfit.Day>) { days = d; version++ }

    /**
     * Market ids Novig still holds a position on (the last read of its positions), or null when not read yet. A market the wallet paid for that is NOT here is over: a lost bet leaves no settlement
     * row, so without this its loss was only counted after [STALE_MS] or a Tracker grade, and the last days showed the winners alone (Tj 2026-11-16: "made \$148 in 3 days" while the wallet was flat).
     */
    @Volatile var openMarkets: Set<String>? = null

    fun build(bets: List<TrackedBet>, ledger: List<LedgerRow>, fills: List<NovigFill>, now: Long, open: Set<String>? = openMarkets): List<SlateProfit.Day> {
        if (ledger.isEmpty() || fills.isEmpty()) return emptyList()
        val markets = WalletBetMoney.markets(ledger, fills)
        val lastFill = HashMap<String, Long>()
        for (f in fills) lastFill[f.marketId] = maxOf(lastFill[f.marketId] ?: 0L, f.ts)
        val betsOf = bets.filter { it.marketId.isNotBlank() && it.status != BetStatus.VOID }.groupBy { it.marketId }
        class Acc(var profit: Double = 0.0, var paid: Double = 0.0, var settled: Int = 0, var won: Int = 0, var lost: Int = 0, var first: Long = Long.MAX_VALUE, var end: Long = 0L)
        val byDay = java.util.TreeMap<LocalDate, Acc>()
        for ((id, m) in markets) {
            if (m.paid <= 0.005) continue
            val tracked = betsOf[id].orEmpty()
            val final = m.payout > 0.0 || tracked.any { it.status != BetStatus.PENDING } || (open != null && id !in open && now - (lastFill[id] ?: now) > RESTING_MS) || (tracked.isEmpty() && now - (lastFill[id] ?: now) > STALE_MS)
            if (!final) continue
            val settledAt = m.settledAtMs ?: lastFill[id] ?: now
            val start = if (tracked.isNotEmpty()) tracked.minOf(SlateProfit::gameMs) else settledAt - SlateProfit.TYPICAL_GAME_MS
            val a = byDay.getOrPut(SlateProfit.dayOf(start)) { Acc() }
            val net = Math.round(m.net * 100.0) / 100.0
            a.profit += net; a.paid += m.paid; a.settled++
            if (net > 0.005) a.won++ else if (net < -0.005) a.lost++
            a.first = minOf(a.first, start); a.end = maxOf(a.end, settledAt)
        }
        return byDay.map { (d, a) -> SlateProfit.Day(d, a.first, a.end, a.profit, a.paid, a.settled, 0, a.won, a.lost, a.paid) }
    }
}
