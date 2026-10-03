package com.tjshea.vigilant.data.scanner

import java.util.Locale

/**
 * The trap guard (Tj, 2026-10-03: "some of my 'gift' positive EV bets moved against me dramatically, and I think they were made by sharp bettors with
 * information not yet reflected by other sports books. See if there is a way to find these trap bets and avoid them"; RESEARCH.md §71), pure. Two
 * rules, each measured before it was built:
 *
 *  - **Too early** ([early]): the game starts more than [ScanSettings.trapEarlyHours] from now. Tj's own 176 bets with a true close (v0.54.0 file):
 *    placed under 6 h before the start they beat the close by +2.2% (78% of them) and returned +8.3%; placed 6 h or more out they lost −0.6% to it
 *    (46%) and returned −9.8%, on the same shown EV (+3.1% vs +2.7%). Far from the start the books' lines Vigilant and CNO devig aren't settled, and a
 *    Novig price that disagrees with them is as often the better-informed one: the "gift" is the books lagging Novig, not Novig lagging the books.
 *    It is also where Novig's takers trade: 71% of prop dollars change hands in the last 6 h.
 *  - **Novig just moved** ([move], game lines only): Novig's own trades show the price just moved to make this bet look cheap: at least [MOVE] under
 *    where this side traded over the last hour, with [MOVE_DOLLARS] or more bought on the OTHER side in the last 15 minutes. 61 days of Novig's
 *    trades (`tools/research/novig_trap_study.py`): such takers on moneylines, spreads and totals lost −2.3¢ to the close (n = 2,551 in 469
 *    markets), when every taker loses about −0.2¢. Someone bought the other side hard and the price stayed there. The same move on a player prop
 *    (+0.2¢) or a 1st-half line (+1.4¢) is not a trap, so it isn't applied to them.
 */
object TrapGuard {

    /** The default: bet only within 6 h of the start (where Tj's own bets beat the close). 0 = off. */
    const val DEFAULT_EARLY_HOURS = 6

    /** The choices Settings offers (0 = off). */
    val EARLY_CHOICES = listOf(0, 3, 6, 12, 24)

    /** How far under its own last-hour level a game line's price must be, in probability (2¢), for the move rule. */
    const val MOVE = 0.02

    /** Dollars bought on the other side in the last [FLOW_MS] that make the move a trap. */
    const val MOVE_DOLLARS = 100.0

    /** The window of Novig's trades that sets a side's own level. */
    const val LEVEL_MS = 60 * 60_000L

    /** The window the other side's buying is counted over. */
    const val FLOW_MS = 15 * 60_000L

    /** Fewer trades than this in [LEVEL_MS] is no level to compare with (the rule says nothing). */
    const val MIN_LEVEL_TRADES = 3

    /** One of Novig's trades in a market as its public `/trades` route gives it (NOVIG_API.md §5): the RESTING order's outcome and price. */
    data class Trade(val outcomeId: String, val price: Double, val contracts: Long, val atMs: Long)

    /** Why [startsAtMs] is too far off at [now] for [hours] (0 = off), or null. */
    fun early(startsAtMs: Long?, now: Long, hours: Int): String? {
        if (hours <= 0 || startsAtMs == null) return null
        val left = startsAtMs - now
        if (left <= hours * 3_600_000L) return null
        return earlyReason(hours)
    }

    /** [early]'s words for [hours] (one wording per setting, so the auto-bet's report counts them together). */
    fun earlyReason(hours: Int): String = "it starts in more than $hours h (trap guard: bets this early lost to the close)"

    /** Whether [startsAtMs] is past the guard's window at [now] (the cards' and Bids tab's note). */
    fun isEarly(startsAtMs: Long?, now: Long, hours: Int): Boolean = early(startsAtMs, now, hours) != null

    /** The kinds of bet the move rule is for: full-game moneylines, spreads and totals. */
    val MOVE_KINDS = setOf(BetKind.MONEYLINE, BetKind.SPREAD, BetKind.TOTAL)

    /** What Novig's recent trades say about buying [outcomeId] at [price] (what a contract costs now) at [now]. */
    data class Move(
        /** The median of every trade in the last [LEVEL_MS] as a price for this side; null with fewer than [MIN_LEVEL_TRADES]. */
        val level: Double?,
        val trades: Int,
        /** Dollars takers paid for the other side in the last [FLOW_MS]. */
        val otherSideDollars: Double,
        /** How far [price] is under [level] (positive = cheaper than it has been trading). */
        val under: Double?,
    ) {
        /** The rule fires: [MOVE] or more under its level with [MOVE_DOLLARS] or more bought on the other side just before. */
        val trap: Boolean get() = (under ?: 0.0) >= MOVE - 1e-9 && otherSideDollars >= MOVE_DOLLARS - 1e-9
    }

    /**
     * Reads [trades] (one market's, any order, both outcomes) for buying [outcomeId] at [price]. A trade on our outcome is a resting bid on it that a
     * taker sold into: that taker BOUGHT the other side, at 1 − its price, for `contracts × (1 − price)` cents. A trade on the other outcome is a
     * taker who bought ours at 1 − its price. Either way it is a price for our side, which is what the level is the median of.
     */
    fun move(trades: List<Trade>, outcomeId: String, price: Double, now: Long): Move {
        val hour = trades.filter { it.atMs in (now - LEVEL_MS)..now }
        val ours = hour.map { if (it.outcomeId == outcomeId) it.price else 1.0 - it.price }.sorted()
        val level = if (ours.size >= MIN_LEVEL_TRADES) median(ours) else null
        val other = hour.filter { it.atMs >= now - FLOW_MS && it.outcomeId == outcomeId }
            .sumOf { it.contracts * (1.0 - it.price) * com.tjshea.vigilant.engine.EvMath.CONTRACT_PAYOUT_DOLLARS }
        return Move(level, hour.size, other, level?.let { it - price })
    }

    /** Why the move rule stops a [kind] bet, or null (not a game line, no level, or no trap). */
    fun moveReason(kind: BetKind?, m: Move): String? {
        if (kind !in MOVE_KINDS || !m.trap) return null
        return String.format(
            Locale.US, "Novig just moved: %.1f¢ under where it traded this hour, with \$%.0f bought on the other side in 15 min (trap guard)",
            (m.under ?: 0.0) * 100, m.otherSideDollars,
        )
    }

    /** One line for the bet's record and Diagnostics: the level, the move and the money behind it. */
    fun describe(m: Move): String = when (val l = m.level) {
        null -> "Novig: ${m.trades} trade${if (m.trades == 1) "" else "s"} this hour (no level)"
        else -> String.format(Locale.US, "Novig level %.3f, %+.1f¢ under, \$%.0f on the other side in 15 min", l, (m.under ?: 0.0) * 100, m.otherSideDollars)
    }

    private fun median(sorted: List<Double>): Double {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
