package com.tjshea.vigilant.engine

/**
 * Novig's own trading fees (RESEARCH.md §3 — summarized via search, not yet confirmed against
 * Novig's fee page directly; see RESEARCH.md §10). Netting this out is what keeps the EV
 * calculator from flagging bets that look profitable but aren't once the fee is paid.
 */
object Fees {

    /** Live-straight taker fee, per $1 of contract, at price [p] (a decimal probability in (0,1)). */
    fun liveStraightTakerFee(p: Double): Double = 0.03 * p * (1.0 - p)

    fun estimate(novig: NovigQuote): FeeResult {
        if (novig.isMaker) {
            // Maker fee is $0 across every trade type Novig documents (RESEARCH.md §3).
            return FeeResult.Known(0.0)
        }
        return when (novig.context) {
            TradeContext.PREGAME_STRAIGHT -> FeeResult.Known(0.0)
            TradeContext.LIVE_STRAIGHT -> FeeResult.Known(liveStraightTakerFee(novig.price))
            TradeContext.PARLAY -> FeeResult.Unknown(
                "Novig's parlay fee structure isn't confirmed yet (RESEARCH.md §3/§10 item 3) " +
                    "— never assume $0 here, it would silently overstate parlay EV."
            )
        }
    }
}
