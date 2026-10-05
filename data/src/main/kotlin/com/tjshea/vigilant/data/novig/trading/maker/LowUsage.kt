package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.LowUsageBids
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * "Low API usage" bids (Tj, 2026-10-05: "make an option for a low API usage auto bid feature … scan current odds from only the sharpest books for props … devig these
 * odds to find fair odds and place bids at least 2.5% below (positive EV) the fair odds … the longest odds it should place bids at is +130 (no long shots), and make it
 * place the types of bets most likely to be matched and filled … at least two sharp books … current and not stale … the sharp books must prove both sides"; RESEARCH.md §92).
 * The bid half; what the scan reads is [LowUsageBids] (the settings half).
 *
 *  - **Fair**: the picked sharp books' own two-sided prices, each devigged the worst way, quotes past the freshness limit dropped before the devig, at least two books
 *    ([LowUsageBids.profile]); [MakerRules.lowUsageBooks] refuses a line priced any other way (an older full scan's).
 *  - **Price**: [LowUsageBids.MIN_MARGIN] or more under that fair, and under the lowest picked book's own fair ([MakerRules.anchorSharp]), so each picked book gives the bid the edge.
 *  - **Odds**: no longer than +[LowUsageBids.MAX_ODDS] (a tighter setting wins), no bid priced over 0.60 (it almost never fills).
 *  - **Likely to fill**: [QuickLikely]'s rules (props, leads their side, hottest market first, likeliest fill first) and not the kinds of prop takers were measured to trade
 *    rarely ([MarketPopularity.measuredObscure]); only games inside [LowUsageBids.WINDOW_HOURS] h.
 */
object LowUsage {

    /** The fewest picked books a fair is built from ([LowUsageBids.MIN_BOOKS]). */
    const val MIN_BOOKS = LowUsageBids.MIN_BOOKS

    /** Why a line has no bid when its fair isn't the low-usage scan's. */
    const val NOT_PRICED = "Not priced by the low-usage scan from two or more of the picked sharp books yet: waiting for the next scan"

    /** Whether [s] bids this way. */
    fun on(s: ScanSettings): Boolean = s.makerFocus == BidFocus.LOW_USAGE

    /**
     * [rules] as this focus makes them: [QuickLikely]'s narrowing, props alone, the margin Tj set (never under [LowUsageBids.MIN_MARGIN]), nothing longer than +130
     * (a tighter limit of his stays), the sharp rules all on, two books or more, games within 6 h. Nothing is ever loosened.
     */
    fun narrow(rules: MakerRules, s: ScanSettings): MakerRules = QuickLikely.narrow(rules).let { q ->
        q.copy(
            margin = s.lowUsageMargin.coerceIn(LowUsageBids.MIN_MARGIN, 0.5),
            kinds = q.kinds.intersect(setOf(BetKind.PROP)),
            maxOdds = if (rules.maxOdds in MakerRules.MIN_MAX_ODDS until LowUsageBids.MAX_ODDS) rules.maxOdds else LowUsageBids.MAX_ODDS,
            minBooks = maxOf(rules.minBooks, MIN_BOOKS),
            sharpVeto = true, anchorSharp = true, requireSharp = true,
            earlyHours = if (rules.earlyHours in 1..LowUsageBids.WINDOW_HOURS) rules.earlyHours else LowUsageBids.WINDOW_HOURS,
            skipObscure = true,
            lowUsageBooks = LowUsageBids.books(s).mapNotNullTo(LinkedHashSet()) { key -> LowUsageBids.BOOKS.firstOrNull { it.key == key }?.title },
        )
    }

    /** What this focus does, in a few sentences for the Bids tab (the design is RESEARCH.md §92). */
    const val EXPLAINER =
        "Reads as little as it can: only player props, only games starting in the next 6 hours, only the 2-3 sharp prop books picked below, only when there is a game to bid on, " +
            "and Vigilant's scan runs at the pace chosen (not every few minutes). The fair is those books' own two-sided prices devigged, at least two of them, each fresh " +
            "(5 minutes, 10 for a game over 3 hours away) and quoting BOTH sides of the exact line; fewer than two and there is no bid. Each bid is posted the margin below " +
            "under that fair (never under 2.5%) and under the lowest picked book's own fair, at no longer than +130, priced 30-60% (over that a bid almost never fills), on the " +
            "kinds of prop takers trade most, the likeliest to fill first. A bid never outlives the fair behind it, so a slower pace means bids are up part of the time."
}
