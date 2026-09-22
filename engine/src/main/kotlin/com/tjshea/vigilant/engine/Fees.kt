package com.tjshea.vigilant.engine

/**
 * Novig's own trading fees (RESEARCH.md §3, confirmed 2026-09-22 against the novig_ev_scanner
 * briefing — a source that read Novig's current fee page directly, higher confidence than the
 * earlier search-summary citation this superseded). Netting this out is what keeps the EV
 * calculator from flagging bets that look profitable but aren't once the fee is paid.
 */
object Fees {

    /** Live-straight taker fee, per $1 of contract, at price [p] (a decimal probability in (0,1)). */
    fun liveStraightTakerFee(p: Double): Double = 0.03 * p * (1.0 - p)

    /**
     * Parlay ("RFQ combination contract") taker fee — same shape as [liveStraightTakerFee] but a
     * 0.10 multiplier instead of 0.03 (meaningfully more expensive). Confirmed 2026-09-22,
     * resolving RESEARCH.md §10 item 3's old "TBD" — previously this context returned
     * [FeeResult.Unknown] rather than risk silently understating the fee.
     */
    fun parlayTakerFee(p: Double): Double = 0.10 * p * (1.0 - p)

    fun estimate(novig: NovigQuote): FeeResult {
        if (novig.isMaker) {
            // Maker fee is $0 across every trade type Novig documents (RESEARCH.md §3).
            return FeeResult.Known(0.0)
        }
        return when (novig.context) {
            TradeContext.PREGAME_STRAIGHT -> FeeResult.Known(0.0)
            TradeContext.LIVE_STRAIGHT -> FeeResult.Known(liveStraightTakerFee(novig.price))
            TradeContext.PARLAY -> FeeResult.Known(parlayTakerFee(novig.price))
        }
    }
}
