package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.book.BookBoard
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.engine.TakeLevel

/**
 * A Kalshi market of the scan (a [BookBoard] market) turned back into the thing an order needs: which Kalshi ticker and side each outcome is, and a fresh
 * book built from the tickers' tops, so the shared planner ([com.tjshea.vigilant.data.novig.trading.ApiBetPlanner]) judges the price AS IT IS NOW.
 */
object KalshiBook {
    /** What an outcome is on Kalshi: its market [ticker] and [side] ("yes" or "no"). */
    data class Ref(val ticker: String, val side: String)

    /** Each outcome's Kalshi ticker and side, from the ids the venue read put on them (`TICKER|yes`); an outcome without one is left out. */
    fun refs(market: NovigMarket): Map<String, Ref> = market.outcomes.mapNotNull { o ->
        val id = o.bookRef?.outcomeId ?: return@mapNotNull null
        val ticker = id.substringBeforeLast('|', "")
        val side = id.substringAfterLast('|', "")
        if (ticker.isEmpty() || (side != "yes" && side != "no")) null else o.outcomeId to Ref(ticker, side)
    }.toMap()

    /** The Kalshi tickers a market's outcomes sit on. */
    fun tickers(market: NovigMarket): List<String> = refs(market).values.map { it.ticker }.distinct()

    /**
     * The book now: each outcome's one level is Kalshi's price to buy it with the contracts offered there (counted the way the shared maths count: 100 per Kalshi contract).
     * A market any of whose tickers is not tradable has [closed] set; an outcome nobody sells has an empty ladder.
     */
    fun of(market: NovigMarket, tops: Map<String, KalshiTop>, now: Long): Pair<NovigBook, Boolean> {
        val refs = refs(market)
        var closed = refs.isEmpty()
        val posted = market.outcomes.associate { o ->
            val ref = refs[o.outcomeId]
            val top = ref?.let { tops[it.ticker] }
            if (top == null || !top.tradable) closed = true
            val price = top?.priceFor(ref.side)
            val size = top?.sizeFor(ref.side)
            o.outcomeId to if (price == null || size == null || size < 1.0) emptyList() else listOf(TakeLevel(price, (size * BookBoard.CONTRACTS_PER_DOLLAR_CONTRACT).toLong()))
        }
        return NovigBook(market.marketId, 0L, emptyMap(), now, posted = posted) to closed
    }
}
