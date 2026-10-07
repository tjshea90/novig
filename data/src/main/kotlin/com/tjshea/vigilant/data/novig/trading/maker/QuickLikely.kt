package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * "Quick & likely to win" bids (Tj, 2026-10-05: "only the bets which have the maximum chance of being filled quickly and also are decent chance for me to win the
 * bet (remove longshots and keep favorites and small underdogs for my side of the bet to win) but remain positive EV and the best chance at beating clv").
 * The numbers are RESEARCH.md §88.4 (`tools/research/novig_bid_focus_study.py`: 5,782 markets from Novig's published trades, 2026-09-06..10-04, 155,000 simulated
 * bids a margin under the fair, a fill = a taker trading through the bid):
 *
 *  - **Kind: player props and team totals.** A prop bid at 0.30-0.60 fills 8-11% of the time within an hour; a game-line bid 1-2% (and at a fair that knows nothing
 *    Novig doesn't it loses to the close, -1.5%). Period lines fill 5% an hour. So quick means props.
 *  - **Price 0.30 to 0.60.** A bid's price is about the chance its side wins (it's the fair less the margin): under 0.30 is a longshot (a bid at 0.10-0.20 fills 22% an
 *    hour but wins 17-23% of the time); over 0.60 almost never fills (4-5% an hour at 0.60-0.70, 0-1% above): takers buy the favorite, so the underdog's bid fills.
 *    Between them the fill rate eases from 10-11% to 8-9% an hour while the chance to win goes from a third to well over a half: that's the band that is both.
 *  - **A sharp book behind the price.** Tj's bets with a sharp book on the page kept +3.5% at the close, those without -1.9% (62 and 12 closes, §81.3), and a bid is a
 *    standing offer to whoever knows more: the price is taken under the sharp book's fair ([ScanSettings.makerAnchorSharp]) and one must price the line both ways.
 *  - **Order: the bids that lead their side first** (takers reach them first), then the kinds of market takers trade most, then the most edge against the sharp book.
 */
object QuickLikely {

    /** Bid prices: this window (the chance to win is about the price: 30% is +233 at the fair, 60% is -150). */
    const val MIN_PRICE = 0.30
    const val MAX_PRICE = 0.60

    /** The kinds of market it bids on. */
    val KINDS: Set<BetKind> = setOf(BetKind.PROP, BetKind.TEAM_TOTAL)

    /**
     * The share of prop bids a margin under the fair that fill within an hour, by the bid's price (§88.4, posted 3 h before the close, a fair a quarter of the way
     * to the close; w=0 is lower by a third): straight between the band centers. It orders bids, and the Bids tab says what to expect; it is not a promise.
     */
    fun fillChancePerHour(price: Double): Double {
        val rows = listOf(0.10 to 0.22, 0.25 to 0.17, 0.35 to 0.105, 0.45 to 0.10, 0.55 to 0.085, 0.65 to 0.045, 0.80 to 0.005)
        if (price <= rows.first().first) return rows.first().second
        if (price >= rows.last().first) return rows.last().second
        val i = rows.indexOfFirst { it.first >= price }
        val (x0, y0) = rows[i - 1]
        val (x1, y1) = rows[i]
        return y0 + (y1 - y0) * (price - x0) / (x1 - x0)
    }

    /** [rules] as this focus makes them: the narrower of Tj's window and ours, only our kinds, and a sharp book required. Nothing is ever loosened. */
    fun narrow(rules: MakerRules, minLineBooks: Int = MIN_LINE_BOOKS): MakerRules = rules.copy(
        kinds = rules.kinds.intersect(KINDS),
        minPrice = maxOf(rules.minPrice, MIN_PRICE),
        maxPrice = minOf(rules.maxPrice, MAX_PRICE),
        requireSharp = true,
        popularFirst = true,
        quick = true,
        // "No strange props or small markets" (Tj, 2026-10-07): a filter, not only an order. Obscure kinds and thinly priced lines get no bid at all.
        skipObscure = true,
        popularOnly = true,
        minLineBooks = maxOf(rules.minLineBooks, minLineBooks.coerceAtLeast(0)),
    )

    /** Fewest books that price a line for it to be a market takers want (the study's median prop was priced by 7; 5 keeps three quarters of them). */
    const val MIN_LINE_BOOKS = 5

    /** Whether [s] bids this way. */
    fun on(s: ScanSettings): Boolean = s.makerFocus == BidFocus.QUICK_LIKELY

    /** What this focus does, in a few sentences for the Bids tab (the numbers above). */
    const val EXPLAINER =
        "Only player props and team totals (they fill: 8-11% of bids within an hour at these prices; game lines 1-2%), only the kinds of market takers actually trade (never a longest-rush or " +
            "kicking-points prop, never a market only a few books price), only bids priced 30-60% (your side wins about that " +
            "often: no longshots, and past 60% a bid almost never fills), only where a sharp book (Pinnacle, Circa, an exchange) prices the line both ways, with the " +
            "price taken under that book's fair. Bids that lead their side go up first. Fewer bids than All bids; the ones that go up fill sooner and win more often."
}
