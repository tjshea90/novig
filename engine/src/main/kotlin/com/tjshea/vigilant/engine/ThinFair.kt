package com.tjshea.vigilant.engine

import kotlin.math.abs

/**
 * A fair price that rests on only a few small books is not trusted for an order (Tj, 2026-10-19, chat t1792446562982-91ba): two or three small books with the same odds
 * often take one upstream feed, so they are one opinion counted twice. With a sharp book in the line, or with two or more large brands (and more than three companies
 * in all), the fair is as it was. Otherwise, when at most [SMALL_COMPANIES] companies stand behind it and they are all small, or their odds are identical, there is no order.
 * Large brands whose odds are close but not identical pass. Pure; the bet and bid deciders ask it, the feed's own list is unchanged.
 */
object ThinFair {
    /** Most companies a fair may rest on and still be called thin. */
    const val SMALL_COMPANIES = 3

    /** Two fair chances this close count as the same odds. */
    const val IDENTICAL = 0.003

    /** Companies of the large national brands (see [BookCompany]). */
    val LARGE: Set<String> = setOf("flutter", "draftkings", "caesars", "entain", "penn", "fanatics", "hardrock", "bet365", "kambi", "pinnacle", "circa")

    // Kambi's clients are one company that is not large for this rule: its desk is one opinion.
    private val BIG = LARGE - "kambi"

    fun reason(fair: FairLine?): String? {
        fair ?: return null
        if (fair.sharpBooksUsed.isNotEmpty()) return null
        val used = fair.perBook.filter { it.book.bookTitle in fair.booksUsed }
        val companies = used.groupBy { BookCompany.of(it.book.bookKey) }
        if (companies.size > SMALL_COMPANIES) return null
        val large = companies.keys.count { it in BIG }
        if (large >= 2 && !identical(used)) return null
        val n = companies.size
        val names = used.joinToString(", ") { it.book.bookTitle }
        return when {
            large == 0 -> "the fair rests on only $n small book${if (n == 1) "" else "s"} ($names), no large brand"
            identical(used) -> "the fair rests on $n books with identical odds ($names): likely one source counted twice"
            else -> "the fair rests on only $n books ($names) with one large brand"
        }
    }

    private fun identical(used: List<BookFair>): Boolean {
        if (used.size < 2) return false
        val first = used.first().fairProbabilities
        return used.all { b -> b.fairProbabilities.indices.all { abs(b.fairProbabilities[it] - first[it]) <= IDENTICAL } }
    }
}
