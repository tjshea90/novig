package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.engine.BookQuote

/** One event's reference-book quotes for a single market key (e.g. "h2h" = moneyline). */
data class ReferenceEvent(
    val homeTeam: String,
    val awayTeam: String,
    val commenceTimeIso: String,
    val quotesByMarket: Map<String, List<BookQuote>>,
)
