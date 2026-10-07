package com.tjshea.vigilant.data.tracker

/**
 * Whether a logged record is a BET or a BID (Tj, 2026-10-07: "make the app bet logging differentiate from bets and bids … so I can see stats and ev filtered my bids as
 * well as bets, and also for the diagnostics and studies sections"). A bid is a make order Vigilant posted under its fair price that a taker filled; a bet is every taker
 * order. [TrackedBet.isBid] is the one rule; this is its name, for the filters, the splits and the files. RESEARCH.md §111.
 */
enum class BetOrBid(
    /** How a group of them is named in the splits. */
    val group: String,
    /** The word for one, as the files write it (`made` on every line). */
    val word: String,
) {
    BET("Bets (taker orders)", "bet"),
    BID("Bids (make orders that filled)", "bid");

    companion object {
        fun of(b: TrackedBet): BetOrBid = if (b.isBid) BID else BET

        /** The bets of kind [which], or all of them when it is null. */
        fun only(bets: List<TrackedBet>, which: BetOrBid?): List<TrackedBet> = if (which == null) bets else bets.filter { of(it) == which }
    }
}
