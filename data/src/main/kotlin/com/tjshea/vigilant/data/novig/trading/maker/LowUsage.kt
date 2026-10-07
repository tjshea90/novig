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
 *  - **Price**: the margin Tj set ([ScanSettings.lowUsageMargin]: 2.5% by default, 1.5% or a typed amount from [LowUsageBids.MIN_MARGIN]) or more under that fair, and under the lowest picked book's own fair ([MakerRules.anchorSharp]), so each picked book gives the bid the edge.
 *  - **Odds**: no longer than +[LowUsageBids.MAX_ODDS] (a tighter setting wins), no bid priced over 0.60 (it almost never fills).
 *  - **Likely to fill**: [QuickLikely]'s rules (props, leads their side, hottest market first, likeliest fill first) and not the kinds of prop takers were measured to trade
 *    rarely ([MarketPopularity.measuredObscure]), unless the small-market fill is on ([QuickLikely.withObscure]); only games inside the trap guard's hours ([LowUsageBids.windowHours], 6 by default).
 */
object LowUsage {

    /** The fewest picked books a fair is built from ([LowUsageBids.MIN_BOOKS]). */
    const val MIN_BOOKS = LowUsageBids.MIN_BOOKS

    /** Why a line has no bid when its fair isn't the low-usage scan's. */
    const val NOT_PRICED = "Not priced by the low-usage scan from two or more of the picked sharp books yet: waiting for the next scan"

    /** Whether [s] bids this way. */
    fun on(s: ScanSettings): Boolean = s.makerFocus == BidFocus.LOW_USAGE

    /**
     * [rules] as this focus makes them: [QuickLikely]'s narrowing, props alone, the margin Tj set (2.5% by default, never under [LowUsageBids.MIN_MARGIN]), nothing longer than +130
     * unless he picked a longer limit, the sharp rules all on, two books or more, games within 6 h. Nothing is ever loosened.
     */
    fun narrow(rules: MakerRules, s: ScanSettings): MakerRules = QuickLikely.withObscure(QuickLikely.narrow(rules), s).let { q ->
        val picked = LowUsageBids.books(s).size
        q.copy(
            // Its fair is two or three books by design, so a small-market line can hold at most the books picked: a setting above that would never be met (a 3 with two books picked).
            obscureMinBooks = q.obscureMinBooks.coerceIn(MIN_BOOKS, maxOf(MIN_BOOKS, picked)),
            margin = s.lowUsageMargin.coerceIn(LowUsageBids.MIN_MARGIN, LowUsageBids.MAX_MARGIN),
            kinds = q.kinds.intersect(setOf(BetKind.PROP)),
            // +130 is the mode's default when Tj has set no longest odds (No limit); any longest odds he picks, tighter or looser, is the one that runs.
            maxOdds = maxOdds(rules.maxOdds),
            minBooks = maxOf(rules.minBooks, MIN_BOOKS),
            sharpVeto = true, anchorSharp = true, requireSharp = true,
            // The trap guard is Tj's: 6 h by default, any hours he picks, or Off ([LowUsageBids.windowHours] reads exactly as far as it lets a bid go).
            skipObscure = true,
            // Its fair is two or three books by design: "how many books price the line" would call every line small.
            popularOnly = false, minLineBooks = 0,
            lowUsageBooks = LowUsageBids.books(s).mapNotNullTo(LinkedHashSet()) { key -> LowUsageBids.BOOKS.firstOrNull { it.key == key }?.title },
        )
    }

    /** The longest odds a bid may be posted at in this mode: Tj's own limit when he set one ([chosen] > 0), else the mode's default [LowUsageBids.MAX_ODDS]. */
    fun maxOdds(chosen: Int): Int = if (chosen > 0) chosen else LowUsageBids.MAX_ODDS

    /**
     * The Novig markets Vigilant has a bid resting on: the low-usage scan reads them first ([ScanSettings.lowUsageNow]; the scan's read order puts a line by its EV at Novig's TAKE
     * price, and a market with a bid of ours is usually under water there, so it came last: a bid is re-posted from a fresh scan only when its market is read, and one read last
     * ended before it was reached; RESEARCH.md §93).
     */
    fun restingMarkets(bids: Collection<MakerBid>): Set<String> = bids.filter { it.resting }.mapTo(LinkedHashSet()) { it.marketId }

    /** What this focus does, in a few sentences for the Bids tab (the design is RESEARCH.md §92, §93). */
    const val EXPLAINER =
        "Reads as little as it can: only player props, only games starting in the next 6 hours, only the 2-3 sharp prop books picked below, only when there is a game to bid on, " +
            "and Vigilant's scan runs at the pace chosen (Auto: just before the bids' prices go old, so they stay up). The fair is those books' own two-sided prices devigged, at least two of them, each fresh " +
            "(5 minutes, 10 for a game over 3 hours away) and quoting BOTH sides of the exact line; fewer than two and there is no bid. Each bid is posted at least the margin " +
            "under that fair (2.5% unless you pick 1.5% or type your own, never under 0.5%), and under the lowest picked book's own fair, at no longer than +130, priced 30-60% (over that a bid almost never fills), on the " +
            "kinds of prop takers trade most, the likeliest to fill first. A bid never outlives the fair behind it, so a fixed pace slower than Auto means bids are up part of the time."
}
