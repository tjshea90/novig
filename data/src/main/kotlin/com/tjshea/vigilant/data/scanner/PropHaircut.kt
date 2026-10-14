package com.tjshea.vigilant.data.scanner

/**
 * The player-prop haircut (Tj, 2026-10-14: "Do what you think is most profitable because I'm losing money"; research/bet_history_2026-10-14.md). Over 1,228 settled taker bets, player-prop OVERS won
 * 39.7% against a stated fair chance of 49.4% (n 481, z = -4.2, ROI -15.7%), Unders 47.0% against 50.0% (n 317, z = -1.1); every other kind of bet, and every even-money bet, matched its stated edge. The
 * bigger the stated edge on a prop the worse it did (EV 3.5%+: 10 to 15 points under its fair), which is what a fair that is too generous looks like: books shade the public's side, so a devigged Over fair
 * runs high (RESEARCH.md §135). So each prop side's fair chance is cut [OVER_POINTS] / [UNDER_POINTS] BEFORE the edge, the stake, the star rating, the auto-bet, the bids and the Bet sheet read it. Pure.
 */
object PropHaircut {
    /** Probability points taken off a prop Over's fair (measured -9.7 in taker bets, -1.8 in bids: weighted -8.3, less a margin). */
    const val OVER_POINTS = 0.07

    /** Probability points taken off a prop Under's fair (measured -3.0 in bets, -3.7 in bids). */
    const val UNDER_POINTS = 0.03

    private val SIDE = Regex("\\b(over|under)\\b", RegexOption.IGNORE_CASE)

    /** The points to cut from a [kind] bet on [selection] ("Bo Nix Under 12.5", "Over 72.5"); 0 for anything but a player-prop Over or Under. */
    fun points(kind: BetKind?, selection: String): Double {
        if (kind != BetKind.PROP) return 0.0
        return when (SIDE.findAll(selection).lastOrNull()?.value?.lowercase()) {
            "over" -> OVER_POINTS
            "under" -> UNDER_POINTS
            else -> 0.0
        }
    }
}
