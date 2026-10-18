package com.tjshea.vigilant.data.scanner

/**
 * Where a bet's fair odds came from and how old each price was (Tj, 2026-10-13: "I have no way to know which apis are accurate, which ones have the freshest odds ... I always wonder how old the odds
 * are"). For one [Opportunity]: the books that priced its fair line, each with the feed that sent it and the age of that book's price. Pure.
 */
object FairProvenance {
    data class Item(val bookTitle: String, val source: String?, val ageMs: Long?, val sharp: Boolean)

    /** Feed ids as people know them ("sgo-props" and "propline_props" are the same API as "sgo" and "propline"). */
    fun apiName(id: String?): String = when (id?.removeSuffix("-props")?.removeSuffix("_props")?.removeSuffix("_1h")) {
        null, "" -> "feed"
        "pinnacle" -> "Pinnacle feed"
        "polymarket" -> "Polymarket"
        "kalshi" -> "Kalshi"
        "sxbet" -> "SX Bet"
        "sgo" -> "SGO"
        "oddspapi" -> "OddsPapi"
        "oddspapi_free" -> "OddsPapi Free"
        "bolt" -> "BoltOdds"
        "parlay" -> "ParlayAPI"
        "propline" -> "PropLine"
        "oddsapi" -> "The Odds API"
        "unabated" -> "Unabated"
        "sportsbook" -> "Sportsbook props"
        else -> id!!
    }

    /** The books behind [o]'s fair line with their feed and age at [now], sharp books first, then freshest; empty when the bet has no fair line. */
    fun of(o: Opportunity, now: Long, freshest: Boolean = true): List<Item> {
        val fair = o.fair ?: return emptyList()
        val ref = o.refEvent ?: return emptyList()
        val key = o.lineKey ?: return emptyList()
        val sharp = fair.sharpBooksUsed.toHashSet()
        val used = fair.booksUsed.toHashSet()
        return Pricing.chosenMarkets(ref, key, freshest)
            .filter { (m, _) -> m.bookTitle in used }
            .map { (m, _) -> Item(m.bookTitle, m.source, m.lastUpdateMs?.let { (now - it).coerceAtLeast(0L) }, m.bookTitle in sharp) }
            .sortedWith(compareByDescending<Item> { it.sharp }.thenBy { it.ageMs ?: Long.MAX_VALUE })
    }

    /** "Fair odds from: Pinnacle via Pinnacle feed 2 s · DraftKings via PropLine 4 s · FanDuel via SGO 5 min · +2 more" (the sharp books first); null when unknown. */
    fun text(o: Opportunity, now: Long, freshest: Boolean = true, max: Int = 4): String? {
        val items = of(o, now, freshest).ifEmpty { return null }
        val shown = items.take(max).joinToString(" · ") { i -> "${i.bookTitle} via ${apiName(i.source)} " + (i.ageMs?.let { OddsAge.span(it) } ?: "age unknown") }
        return "Fair odds from: " + shown + if (items.size > max) " · +${items.size - max} more" else ""
    }
}
