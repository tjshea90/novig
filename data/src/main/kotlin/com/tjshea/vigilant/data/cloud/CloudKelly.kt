package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.trading.AutoBet

/**
 * The engine's stake for one bet (Tj, 2026-10-15: "It should use a smart kelly system for bet and bid sizes"; TCL20d). Pure.
 *
 * Full Kelly for a binary contract that costs `c` (fee included) and pays 1 with fair chance `p` is `(p - c) / (1 - c)` of the bankroll. Three things make it "smart" rather than plain:
 *  1. **The edge is shrunk to what is likely to hold.** With no sharp book agreeing, only [AutoBet.NO_SHARP_KEEPS] (70%) of the edge is used (what Tj's own bets kept at the close, RESEARCH.md §71-72).
 *  2. **The fraction is shrunk by confidence** ([confidence]): old fair odds, few books agreeing and a low star rating each cut it, and they multiply.
 *  3. **Drawdown and caps.** Half stakes once the day is down half of the daily stop, no stake at all at the stop; and a bet never exceeds its per-bet cap, what is left of its game's cap, what is left of the
 *     wallet-use cap, or the cash there is.
 * The bankroll is the wallet plus what is at risk (the equity), so a stake does not shrink just because other bets are open.
 */
object CloudKelly {
    data class Inputs(
        /** Fair chance the outcome wins. */
        val fair: Double,
        /** What one $1-payout contract costs, fee included ([com.tjshea.vigilant.engine.EvQuote.cost]). */
        val cost: Double,
        /** Cash available to bet. */
        val cash: Double,
        /** Dollars already at risk (open bets) across everything. */
        val atRisk: Double,
        /** Dollars already at risk on this bet's game. */
        val gameAtRisk: Double,
        /** The bankroll at the start of today (equity), for the drawdown rule; 0 = unknown, no rule. */
        val dayStartEquity: Double,
        val fairAgeMs: Long?,
        /** Books pricing both sides that say +EV on their own. */
        val agreeing: Int,
        val sharpAgrees: Boolean,
        val stars: Double?,
        val kelly: Double,
        val betCap: Double,
        val gameCap: Double,
        val walletUse: Double,
        val dayStop: Double,
    )

    sealed interface Stake {
        data class Amount(val dollars: Double, val note: String) : Stake
        data class Skip(val reason: String) : Stake
    }

    /** How much of the Kelly fraction the evidence behind the bet earns, 0.3 to 1.0. */
    fun confidence(fairAgeMs: Long?, agreeing: Int, stars: Double?): Double {
        val age = when {
            fairAgeMs == null -> 0.6
            fairAgeMs <= 2 * 60_000L -> 1.0
            fairAgeMs <= 5 * 60_000L -> 0.9
            fairAgeMs <= 15 * 60_000L -> 0.75
            else -> 0.6
        }
        val books = when {
            agreeing >= 4 -> 1.0
            agreeing >= 2 -> 0.8
            else -> 0.6
        }
        val rating = when {
            stars == null -> 0.7
            stars >= 4.0 -> 1.0
            stars >= 3.0 -> 0.85
            else -> 0.7
        }
        return (age * books * rating).coerceIn(0.3, 1.0)
    }

    fun stake(i: Inputs): Stake {
        if (!(i.cost > 0.0) || i.cost >= 1.0) return Stake.Skip("no price to size on")
        val keep = if (i.sharpAgrees) 1.0 else AutoBet.NO_SHARP_KEEPS
        val edge = (i.fair - i.cost) * keep
        if (edge <= 0.0) return Stake.Skip("no edge left to stake after the price")
        val equity = i.cash + i.atRisk
        if (!(equity > 0.0)) return Stake.Skip("the bankroll is empty")
        var fraction = i.kelly.coerceIn(0.0, 1.0)
        var drawdown = ""
        if (i.dayStartEquity > 0.0 && i.dayStop > 0.0) {
            val down = (i.dayStartEquity - equity) / i.dayStartEquity
            when {
                down >= i.dayStop - 1e-9 -> return Stake.Skip("today's loss stop is reached (down ${pct(down)} of the day's start)")
                down >= i.dayStop / 2 -> { fraction *= 0.5; drawdown = ", half stakes after a ${pct(down)} drawdown" }
            }
        }
        val conf = confidence(i.fairAgeMs, i.agreeing, i.stars)
        val wanted = equity * fraction * conf * (edge / (1.0 - i.cost))
        val perBet = equity * i.betCap
        val gameLeft = equity * i.gameCap - i.gameAtRisk
        val walletLeft = equity * i.walletUse - i.atRisk
        val capped = minOf(wanted, perBet, gameLeft, walletLeft, i.cash)
        if (gameLeft <= 1e-9) return Stake.Skip("its game already has the most at risk")
        if (walletLeft <= 1e-9) return Stake.Skip("the wallet-use cap is reached")
        val dollars = AutoBet.floorCents(capped)
        if (dollars < AutoBet.MIN_STAKE - 1e-9) return Stake.Skip("its Kelly stake is under a cent")
        val why = when (capped) {
            perBet -> "per-bet cap"
            gameLeft -> "game cap"
            walletLeft -> "wallet-use cap"
            i.cash -> "cash"
            else -> "Kelly"
        }
        return Stake.Amount(dollars, "${pct(fraction)} Kelly x confidence ${"%.2f".format(conf)}, edge ${pct(edge)}, held by $why$drawdown")
    }

    private fun pct(v: Double) = "%.1f%%".format(v * 100)
}
