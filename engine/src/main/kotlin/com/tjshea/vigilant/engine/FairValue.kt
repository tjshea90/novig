package com.tjshea.vigilant.engine

/**
 * Where the "fair" price comes from. Tj's request (2026-09-25): "a market fair devigged price
 * using either sharp sports books or average odds across books or a blend", modeled on
 * OddsJam's fair-odds source picker.
 */
enum class FairSource(val displayName: String, val shortName: String) {
    /** Only the books marked sharp (Pinnacle by default). */
    SHARP("Sharp books", "Sharp"),

    /** Every book quoting the line, each devigged, then averaged. */
    MARKET_AVERAGE("Market average", "Average"),

    /** `sharpWeight x sharp + (1 - sharpWeight) x market average`. */
    BLEND("Blend", "Blend"),
}

data class FairSettings(
    val source: FairSource = FairSource.BLEND,
    val method: DevigMethod = DevigMethod.POWER,
    /** Odds API bookmaker keys treated as sharp. */
    val sharpBooks: Set<String> = DEFAULT_SHARP_BOOKS,
    /** BLEND only: weight on the sharp component, in [0, 1]. */
    val sharpWeight: Double = 0.7,
    /**
     * SHARP only: with no sharp book on a line, fall back to the market average instead of
     * skipping the line. This keeps Tj's original 2026-09-20 rule available: prefer a sharp book
     * when fetched, else average every book (BRIEF.md, locked decision).
     */
    val fallbackToAverage: Boolean = true,
    /** Fewest books a market-average component may be built from. */
    val minBooks: Int = 2,
) {
    init {
        require(sharpWeight in 0.0..1.0) { "sharpWeight must be in [0,1], got $sharpWeight" }
        require(minBooks >= 1) { "minBooks must be >= 1" }
    }

    companion object {
        val DEFAULT_SHARP_BOOKS: Set<String> = setOf("pinnacle")

        /** Books commonly treated as sharp that The Odds API carries. */
        val KNOWN_SHARP_CANDIDATES: List<String> =
            listOf("pinnacle", "betonlineag", "lowvig", "betfair_ex_eu", "matchbook")
    }
}

/** One book's decimal odds for every outcome of one line, in the caller's outcome order. */
data class BookPrices(
    val bookKey: String,
    val bookTitle: String,
    val decimalOdds: List<Double>,
    val lastUpdateMs: Long? = null,
)

/** One book, devigged on its own. [hold] is its margin (sum of implied probabilities − 1). */
data class BookFair(
    val book: BookPrices,
    val fairProbabilities: List<Double>,
    val hold: Double,
    val isSharp: Boolean,
)

/**
 * The fair line for one market. [sourceUsed] may differ from the requested source when a
 * fallback happened (e.g. BLEND with no sharp book on this line is plain MARKET_AVERAGE).
 */
data class FairLine(
    val probabilities: List<Double>,
    val requestedSource: FairSource,
    val sourceUsed: FairSource,
    val method: DevigMethod,
    val perBook: List<BookFair>,
    val sharpBooksUsed: List<String>,
    val averageBooksUsed: List<String>,
) {
    val booksUsed: List<String> get() = (sharpBooksUsed + averageBooksUsed).distinct()

    /** Mean hold of the books that fed this line, a proxy for how confident the line is. */
    val hold: Double
        get() {
            val used = perBook.filter { it.book.bookTitle in booksUsed }
            return if (used.isEmpty()) 0.0 else used.sumOf { it.hold } / used.size
        }
}

/** Builds a [FairLine] from whatever books quoted one market. */
object FairValue {

    fun compute(books: List<BookPrices>, settings: FairSettings): FairLine? {
        if (books.isEmpty()) return null
        val outcomeCount = books.first().decimalOdds.size
        if (outcomeCount < 2) return null

        val perBook = books
            .filter { it.decimalOdds.size == outcomeCount && it.decimalOdds.all { o -> o > 1.0 && o.isFinite() } }
            .mapNotNull { devigBook(it, settings) }
        if (perBook.isEmpty()) return null

        val sharp = perBook.filter { it.isSharp }
        val average = perBook

        fun avg(list: List<BookFair>): List<Double> =
            (0 until outcomeCount).map { i -> list.sumOf { it.fairProbabilities[i] } / list.size }

        val averageOk = average.size >= settings.minBooks

        return when (settings.source) {
            FairSource.SHARP -> when {
                sharp.isNotEmpty() -> line(avg(sharp), settings, FairSource.SHARP, perBook, sharp, emptyList())
                settings.fallbackToAverage && averageOk ->
                    line(avg(average), settings, FairSource.MARKET_AVERAGE, perBook, emptyList(), average)
                else -> null
            }

            FairSource.MARKET_AVERAGE ->
                if (averageOk) line(avg(average), settings, FairSource.MARKET_AVERAGE, perBook, emptyList(), average) else null

            FairSource.BLEND -> when {
                sharp.isNotEmpty() && averageOk -> {
                    val s = avg(sharp)
                    val a = avg(average)
                    val w = settings.sharpWeight
                    line(s.indices.map { w * s[it] + (1 - w) * a[it] }, settings, FairSource.BLEND, perBook, sharp, average)
                }
                sharp.isNotEmpty() -> line(avg(sharp), settings, FairSource.SHARP, perBook, sharp, emptyList())
                averageOk -> line(avg(average), settings, FairSource.MARKET_AVERAGE, perBook, emptyList(), average)
                else -> null
            }
        }
    }

    private fun devigBook(book: BookPrices, settings: FairSettings): BookFair? {
        val raw = book.decimalOdds.map(Odds::impliedProbability)
        val hold = raw.sum() - 1.0
        // A book whose own sides imply arbitrage (hold < 0) is stale or mis-quoted. Power and
        // Shin can't solve it, and averaging it in would drag the fair line toward garbage.
        if (hold < 0.0) return null
        val fair = runCatching { Devig.devig(raw, settings.method) }.getOrNull() ?: return null
        if (fair.any { it <= 0.0 || it >= 1.0 || !it.isFinite() }) return null
        return BookFair(book, fair, hold, isSharp = book.bookKey in settings.sharpBooks)
    }

    private fun line(
        probs: List<Double>,
        settings: FairSettings,
        used: FairSource,
        perBook: List<BookFair>,
        sharp: List<BookFair>,
        average: List<BookFair>,
    ) = FairLine(
        probabilities = probs,
        requestedSource = settings.source,
        sourceUsed = used,
        method = settings.method,
        perBook = perBook,
        sharpBooksUsed = sharp.map { it.book.bookTitle },
        averageBooksUsed = average.map { it.book.bookTitle },
    )
}
