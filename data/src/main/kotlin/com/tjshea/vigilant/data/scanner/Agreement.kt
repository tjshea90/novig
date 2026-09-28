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

    data class Count(val twoSided: Int, val agreeing: Int) {
        val agrees: Boolean get() = twoSided >= CnoBooks.MIN_TWO_SIDED && agreeing >= CnoBooks.MIN_AGREEING
    }

    fun of(o: Opportunity): Count {
        val fair = o.fair ?: return Count(0, 0)
        val side = o.referenceIndex ?: return Count(0, 0)
        val cost = o.quote?.cost?.takeIf { it > 0.0 } ?: return Count(0, 0)
        // One quote per book (two feeds can carry the same book), each with every side priced.
        val books = fair.perBook.map { it.book }.distinctBy { it.bookKey }.filter { b -> b.decimalOdds.size >= 2 && b.decimalOdds.all { it > 1.0 && it.isFinite() } }
        val agreeing = books.count { b -> worstCase(b.decimalOdds)?.getOrNull(side)?.let { p -> p / cost - 1.0 > 0.0 } == true }
        return Count(books.size, agreeing)
    }

    /** A book's own fair probabilities, worst case; plain normalizing when it has no margin to remove. */
    private fun worstCase(decimalOdds: List<Double>): List<Double>? {
        val raw = decimalOdds.map { 1.0 / it }
        val sum = raw.sum()
        return runCatching { if (sum <= 1.0) raw.map { it / sum } else Devig.worstCase(raw) }.getOrNull()
    }
}
