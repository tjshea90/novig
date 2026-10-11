package com.tjshea.vigilant.engine

import kotlin.math.abs

/**
 * Books whose odds look wrong (Tj, 2026-10-11: "exclude from any fair odds evaluation any book that shows odds that look incorrect, such as extreme outliers or inverted odds (favorite on the
 * opposite side of the rest of the market)"). A stale quote, a mismatched line or a flipped side poisons a fair price far more than an honest book's small disagreement does, so
 * such a book is left out of the fair line, and the sheet shows it greyed with the reason.
 *
 * Judged on each book's own devigged chances against the market's MEDIAN (a median cannot be dragged by the bad book itself), among the books left after one a company:
 *  - **Inverted:** the market's favourite is the book's underdog, each by a clear margin ([INVERTED_MARGIN]).
 *  - **Extreme outlier:** a side's chance is further from the median than [tolerance].
 * With three or more books both rules apply. With two books nobody can say which is right, so only a flat contradiction (inverted) counts: the sharp book is kept, and with no
 * sharp book (or two) neither is, so the line is not priced at all. One book is never judged.
 */
object BadOdds {

    /** How far a favourite and underdog must be apart (in chance) on both sides for the flip to be a flip and not a pick'em. */
    const val INVERTED_MARGIN = 0.05

    data class Screened(val kept: List<BookFair>, val dropped: List<BookFair>)

    /** How far from the median a side's chance may be before the book is an outlier: 30% of the smaller of the side's chance and its complement, never under 6 points. */
    fun tolerance(median: Double): Double = maxOf(0.06, 0.3 * minOf(median, 1.0 - median))

    fun screen(books: List<BookFair>): Screened {
        if (books.size < 2) return Screened(books, emptyList())
        val outcomes = books.first().fairProbabilities.size
        if (outcomes < 2 || books.any { it.fairProbabilities.size != outcomes }) return Screened(books, emptyList())
        val median = (0 until outcomes).map { i -> FairValue.median(books.map { it.fairProbabilities[i] }) }
        val bad = if (books.size >= 3) books.filter { isInverted(it, median) || isOutlier(it, median) } else twoBooks(books)
        if (bad.isEmpty()) return Screened(books, emptyList())
        return Screened(books.filter { b -> bad.none { it === b } }, bad)
    }

    private fun twoBooks(books: List<BookFair>): List<BookFair> {
        val (a, b) = books
        if (!inverted(a.fairProbabilities, b.fairProbabilities)) return emptyList()
        return when {
            a.isSharp && !b.isSharp -> listOf(b)
            b.isSharp && !a.isSharp -> listOf(a)
            else -> listOf(a, b)
        }
    }

    private fun isOutlier(b: BookFair, median: List<Double>): Boolean =
        median.indices.any { abs(b.fairProbabilities[it] - median[it]) > tolerance(median[it]) + 1e-12 }

    private fun isInverted(b: BookFair, median: List<Double>): Boolean = inverted(b.fairProbabilities, median)

    /** [a]'s favourite is [b]'s underdog (and the other way), each by [INVERTED_MARGIN] or more. */
    private fun inverted(a: List<Double>, b: List<Double>): Boolean {
        val fa = a.indices.maxByOrNull { a[it] } ?: return false
        val fb = b.indices.maxByOrNull { b[it] } ?: return false
        if (fa == fb) return false
        return a[fa] - a[fb] >= INVERTED_MARGIN && b[fb] - b[fa] >= INVERTED_MARGIN
    }
}
