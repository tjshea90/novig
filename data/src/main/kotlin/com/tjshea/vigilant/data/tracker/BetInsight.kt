package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.engine.Odds

/**
 * Everything an open bet's sheet says (Tj, 2026-09-29: "click on any of my open bets and it shows the
 * current odds for that same bet across other sports books, and other relevant information such as the
 * odds I bet it at, the calculated difference in the odds I bet from the current fair, devigged odds
 * based on current odds"): the price bet at against the fair price now, what each book would make of
 * that price, and how the fair line has moved. Pure: [of] reads only the bet.
 */
data class BetInsight(
    /** The odds bet at (American), what they imply, and the cost of $1 of payout (the implied chance plus any fee). */
    val betOdds: Int,
    val betImplied: Double,
    val cost: Double,
    /** Fair probability when bet (null for an imported ✓) and the EV it gave. */
    val fairAtBet: Double?,
    val evAtBet: Double?,
    /** Fair probability now (books devigged worst case, the lower of mean and median), and the EV at the price bet. */
    val fairNow: Double?,
    val evNow: Double?,
    /** How many books price both sides behind [fairNow]. */
    val booksBehind: Int?,
    /** [fairNow] − [fairAtBet], in probability points (positive: the market moved toward this bet). */
    val fairMove: Double?,
    /** Fair probability now minus the cost: the edge in probability points at the price bet. */
    val edgePoints: Double?,
    /** The longest odds that would still break even against [fairNow]: what the bet clears (or misses). */
    val breakEvenOdds: Int?,
    /** The bet's own book's price now (Novig's), American. */
    val priceNow: Int?,
    /** Closing-line value so far (fair at the close, or last read, against the cost). */
    val clv: Double?,
    val books: List<BookRow>,
    /** When the books were last read. */
    val booksAtMs: Long?,
    /** The "Novig only" filter's view ([NovigNow.view]): every fair price here is Novig's own odds, no other book's. */
    val novig: Boolean = false,
) {
    /**
     * One book's price for the bet and its other side. [fair] is that book's own devigged probability for
     * the bet; [ev] the EV of the price bet at against it (how that book alone would judge the bet); [counted]
     * when it's part of the fair line now (two-sided, not the bet's own book, not a pick'em app).
     */
    data class BookRow(val name: String, val odds: Int?, val other: Int?, val fair: Double?, val ev: Double?, val counted: Boolean, val isOwn: Boolean)

    companion object {
        fun of(b: TrackedBet): BetInsight {
            val betOdds = b.american ?: Odds.probabilityToAmerican(b.price.coerceIn(0.001, 0.999))
            val own = b.book.ifBlank { "Novig" }
            fun isOwn(name: String) = name.equals(own, ignoreCase = true)
            fun counts(l: BookLine) = l.twoSided && !isOwn(l.name) && !l.name.startsWith("PrizePicks", ignoreCase = true)
            val rows = b.books.map { l ->
                val fair = if (l.twoSided) CnoBooks.fairFor(l.odds!!, l.other!!) else null
                BookRow(l.name, l.odds, l.other, fair, fair?.let { it / b.cost - 1.0 }, counts(l), isOwn(l.name))
            }
            val counted = rows.filter { it.counted }
            // The fair line now: the last read's (recheck or scan), else what the books on hand make of it, one vote a company ([CnoBooks.company]).
            val fairNow = b.nowFair ?: CnoBooks.consensus(CnoBooks.perCompany(counted.mapNotNull { r -> r.fair?.let { r.name to it } }, CnoBooks::companyOfName))
            return BetInsight(
                betOdds = betOdds,
                betImplied = b.price,
                cost = b.cost,
                fairAtBet = b.fairAtBet,
                evAtBet = b.evPercentAtBet,
                fairNow = fairNow,
                evNow = fairNow?.let { it / b.cost - 1.0 },
                booksBehind = (b.nowBooks ?: counted.mapTo(HashSet()) { CnoBooks.companyOfName(it.name) }.size).takeIf { fairNow != null && it > 0 },
                fairMove = if (fairNow != null && b.fairAtBet != null) fairNow - b.fairAtBet else null,
                edgePoints = fairNow?.let { it - b.cost },
                breakEvenOdds = fairNow?.takeIf { it in 0.01..0.99 }?.let { Odds.probabilityToAmerican(it) },
                priceNow = b.nowAmerican,
                clv = b.clvPercent,
                books = rows.sortedWith(compareByDescending<BookRow> { it.isOwn }.thenByDescending { it.counted }.thenBy { it.name }),
                booksAtMs = b.booksAtMs,
                novig = b.nowVia == NovigNow.VIA,
            )
        }
    }
}
