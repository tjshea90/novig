package com.tjshea.vigilant.engine

/**
 * Turns however many book quotes we managed to fetch for one market into a single fair
 * probability per outcome. Directly encodes Tj's own instruction (2026-09-20): prefer a sharp
 * book (Pinnacle or Circa) as the sole "source of truth" when one is available, otherwise devig
 * and average whatever major sportsbooks were fetched. See RESEARCH.md §4.3, §5.
 */
object Consensus {

    val DEFAULT_PREFERRED_BOOKS = listOf("Pinnacle", "Circa", "Circa Sports")

    fun computeReferenceProbabilities(
        quotes: List<BookQuote>,
        method: DevigMethod,
        preferredBooks: List<String> = DEFAULT_PREFERRED_BOOKS,
    ): ReferenceProbabilities {
        require(quotes.isNotEmpty()) { "Need at least one book quote to build a reference line" }
        val outcomeCount = quotes.first().decimalOddsByOutcome.size
        require(quotes.all { it.decimalOddsByOutcome.size == outcomeCount }) {
            "All book quotes for one market must have the same number of outcomes"
        }

        val preferred = quotes.firstOrNull { it.bookName in preferredBooks }
        val booksToUse = if (preferred != null) listOf(preferred) else quotes

        val deviggedPerBook = booksToUse.map { quote ->
            val rawProbs = quote.decimalOddsByOutcome.map(Odds::impliedProbability)
            Devig.devig(rawProbs, method)
        }

        val averaged = (0 until outcomeCount).map { outcomeIndex ->
            deviggedPerBook.sumOf { it[outcomeIndex] } / deviggedPerBook.size
        }

        return ReferenceProbabilities(
            fairProbabilityByOutcome = averaged,
            method = method,
            booksUsed = booksToUse.map { it.bookName },
        )
    }
}
