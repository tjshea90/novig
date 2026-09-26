package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.engine.Odds
import java.net.URLEncoder

/** A second opinion on one line from CrazyNinjaOdds' free devigger, prefilled. */
data class DeviggerLink(val url: String, val bookTitle: String)

/**
 * Builds a link to CrazyNinjaOdds' devigger with one book's two-sided odds and Novig's price already
 * filled in, so a bet can be checked against an independent calculator in one tap. The page reads
 * `autofill=1&LegOdds=<this side>/<other side>&FinalOdds=<price>` and computes on load (checked
 * 2026-09-26, RESEARCH.md §16). Its default method is worst case.
 */
object CrossCheck {

    private const val DEVIGGER = "https://crazyninjaodds.com/Public/sportsbooks/sportsbook_devigger.aspx"

    /**
     * The book used is the sharpest one behind the fair price: Pinnacle if it fed it, else the
     * lowest-hold sharp book, else the lowest-hold book. (An exchange's tight bid/ask reads as a
     * low hold, but Pinnacle's line is the reference bettors check against.) Null when there's no
     * two-sided reference line or no price.
     */
    fun devigger(o: Opportunity): DeviggerLink? {
        val fair = o.fair ?: return null
        val cost = o.quote?.cost ?: return null
        val idx = o.referenceIndex ?: return null
        val used = fair.perBook.filter { it.book.bookTitle in fair.booksUsed && it.book.decimalOdds.size == 2 }
        val book = used.firstOrNull { it.book.bookKey == "pinnacle" }
            ?: used.filter { it.isSharp }.minByOrNull { it.hold }
            ?: used.minByOrNull { it.hold }
            ?: return null
        val odds = book.book.decimalOdds
        val legs = listOf(odds[idx], odds[1 - idx]).joinToString("/") { Odds.formatAmerican(Odds.decimalToAmerican(it)) }
        val final = Odds.formatAmerican(Odds.probabilityToAmerican(cost.coerceIn(0.001, 0.999)))
        val url = "$DEVIGGER?autofill=1&LegOdds=${enc(legs)}&FinalOdds=${enc(final)}"
        return DeviggerLink(url, book.book.bookTitle)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
