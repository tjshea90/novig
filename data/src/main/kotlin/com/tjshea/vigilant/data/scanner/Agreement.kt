package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.engine.Devig

/**
 * Whether the other books back a Vigilant bet (Tj, 2026-09-28: alert only when "multiple books agree
 * on the price"), in the terms of CNO's green check ([CnoBooks.check]): of the books quoting both
 * sides of its line, how many make Novig's price +EV on their own, each devigged worst case (the
 * lowest fair probability of multiplicative, additive, power and Shin). Agreed = [CnoBooks.MIN_TWO_SIDED]+
 * books price both sides and [CnoBooks.MIN_AGREEING]+ of them alone say it's +EV, so no single book
 * (or a single sharp one on a SHARP-only setting) makes the alert.
 */
object Agreement {

    /**
     * [twoSided] books (one a company) price both sides, [agreeing] of them make the price +EV alone. [sharpAgrees]: a sharp book for the market is among them (Tj, 2026-10-11: a ★ on
     * "any bet which a sharp book for that market also agrees the bet is EV"); [sharpAgainst]: one prices both sides and says it is not +EV.
     */
    data class Count(val twoSided: Int, val agreeing: Int, val sharpAgrees: Boolean = false, val sharpAgainst: Boolean = false, val spread: Double? = null) {
        /** "4 of 5 books agree", for a row. */
        val text: String get() = "$agreeing of $twoSided books agree"

        val agrees: Boolean get() = twoSided >= CnoBooks.MIN_TWO_SIDED && agreeing >= CnoBooks.MIN_AGREEING
    }

    fun of(o: Opportunity): Count {
        val fair = o.fair ?: return Count(0, 0)
        val side = o.referenceIndex ?: return Count(0, 0)
        val cost = o.quote?.cost?.takeIf { it > 0.0 } ?: return Count(0, 0)
        // One quote per company (two feeds can carry the same book, and a company's sites are one opinion: Tj, 2026-10-11), each with every side priced.
        val books = fair.perBook.filter { b -> b.book.decimalOdds.size >= 2 && b.book.decimalOdds.all { it > 1.0 && it.isFinite() } }
            .let { all -> com.tjshea.vigilant.engine.BookCompany.onePerCompany(all) }
        fun says(b: com.tjshea.vigilant.engine.BookFair): Boolean? = worstCase(b.book.decimalOdds)?.getOrNull(side)?.let { p -> p / cost - 1.0 > 0.0 }
        val verdicts = books.map { it to says(it) }
        val sharp = verdicts.filter { it.first.isSharp && it.second != null }
        return Count(
            twoSided = books.size, agreeing = verdicts.count { it.second == true },
            sharpAgrees = sharp.any { it.second == true }, sharpAgainst = sharp.any { it.second == false },
            spread = BetRating.spreadOf(books.mapNotNull { b -> worstCase(b.book.decimalOdds)?.getOrNull(side) }),
        )
    }

    /** A book's own fair probabilities, worst case; plain normalizing when it has no margin to remove. */
    private fun worstCase(decimalOdds: List<Double>): List<Double>? {
        val raw = decimalOdds.map { 1.0 / it }
        val sum = raw.sum()
        return runCatching { if (sum <= 1.0) raw.map { it / sum } else Devig.worstCase(raw) }.getOrNull()
    }
}
