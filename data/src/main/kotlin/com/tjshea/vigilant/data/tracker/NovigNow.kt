package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket

/**
 * The Tracker's "Novig only" filter (Tj, 2026-10-02 ~18:50Z: "find the current novig odds for each of my open bets and show the percent EV compared only
 * from novig odds, filtering out other sports books … do the already in place stats and ev calculations … but only for novig. Make sure it is smart and
 * doesn't waste any api usage on other sports books … if I already just scanned without using this filter and there is still fresh novig odds for all my
 * bets, it doesn't need to rescan"). Pure.
 *
 * Novig's price for a side is its odds on Novig now: the offer, what Novig shows for the bet and what buying it costs (Tj, 2026-10-02 ~21:35Z: "I want
 * to compare only the novig current odds to the novig odds I placed the bets at … The current odds at novig only should be considered the 'fair odds'
 * … no data from any other sports book should be used"; the bid/offer middle it replaced read a thin prop's wide spread as a big move). It's kept on
 * the bet ([TrackedBet.novigFair], [TrackedBet.novigAtMs]) by any read of Novig's book for the bet (a pricing pass, or the filter's own read of just
 * the stale ones), and the last one before the start is Novig's closing odds ([TrackedBet.novigClose]).
 */
object NovigNow {

    /** How old Novig's price for a bet may be and still be shown without reading again. */
    const val FRESH_MS = 2 * 60_000L

    /** Novig's odds for [outcomeId] now: the price to buy it (its best offer), as Novig shows it. Null when Novig isn't offering it. */
    fun odds(book: NovigBook, market: NovigMarket, outcomeId: String): Double? =
        book.takeLadder(market, outcomeId).minOfOrNull { it.price }?.coerceIn(0.001, 0.999)

    /**
     * Novig's price [p] as the filter compares it with [b]'s: the odds bet at exactly when Novig shows the same American odds (a bet logged from
     * American odds, +122 = 0.4505, and Novig's grid price for +122, 0.450, are the same odds: no move, 0% EV), else [p].
     */
    fun asBet(b: TrackedBet, p: Double): Double =
        if (b.american != null && com.tjshea.vigilant.engine.Odds.probabilityToAmerican(p) == b.american) b.price else p

    /** [b] with Novig's price [fair] read at [at]: the latest, and the closing line when it's the latest read before the start. */
    fun apply(b: TrackedBet, fair: Double, at: Long): TrackedBet {
        val beforeStart = at < b.startsTs
        val newerClose = beforeStart && (b.novigCloseAtMs == null || at >= b.novigCloseAtMs)
        return b.copy(
            novigFair = fair, novigAtMs = at,
            novigClose = if (newerClose) fair else b.novigClose,
            novigCloseAtMs = if (newerClose) at else b.novigCloseAtMs,
        )
    }

    /** The open bets the filter prices: those whose odds can still be read, with their Novig market and side ([BetsScope.priceable]). */
    fun priceable(bets: List<TrackedBet>, now: Long): List<TrackedBet> = BetsScope.priceable(bets, now)

    /** The ones whose Novig price is missing or older than [FRESH_MS] (a look that found none counts as a read): all a read needs to ask for. */
    fun stale(bets: List<TrackedBet>, now: Long): List<TrackedBet> =
        priceable(bets, now).filter { b ->
            val last = maxOf(b.novigAtMs ?: Long.MIN_VALUE, b.novigWhyAtMs ?: Long.MIN_VALUE)
            last == Long.MIN_VALUE || now - last > FRESH_MS
        }

    /** Every open bet at Novig whose odds can still be read, with Novig's ids on record or not: what the filter owes a price or a reason. */
    fun open(bets: List<TrackedBet>, now: Long): List<TrackedBet> =
        bets.filter { it.status == BetStatus.PENDING && BetsScope.readable(it, now) && NovigIds.atNovig(it) }

    /**
     * [bets] as the "Novig only" filter shows and counts them (Tj, 2026-10-02 ~21:35Z: "ONLY compare novig odds currently scanned to the odds I placed
     * each bet at … no data from any other sports book should be used"): Novig's odds are the fair price throughout. When bet: the price paid (so a bet
     * at Novig's odds had no edge over them: EV at bet 0, fee aside). Now: Novig's odds now ([novigFair]; none until read), the same odds as bet = 0% EV
     * ([asBet]). The close: Novig's odds read in the last minutes before the start, else what Novig's own trades closed at ([NovigTradeCloses]).
     * Every other book's line, fair and close is left out, so the Tracker's stats, EV and CLV come from Novig and nothing else.
     */
    fun view(bets: List<TrackedBet>): List<TrackedBet> = bets.map { b ->
        val open = b.status == BetStatus.PENDING
        val fair = b.novigFair?.let { asBet(b, it) }
        b.copy(
            fairAtBet = b.price, evPercentAtBet = b.price / b.cost - 1.0,
            nowFair = if (open) fair else b.nowFair,
            nowEv = if (open) fair?.let { it / b.cost - 1.0 } else b.nowEv,
            nowAtMs = if (open) b.novigAtMs else b.nowAtMs,
            nowBooks = if (open) null else b.nowBooks,
            nowVia = if (open) VIA else b.nowVia,
            nowNote = if (open) note(b) else b.nowNote,
            nowNoteAtMs = if (open) note(b)?.let { b.novigWhyAtMs } else b.nowNoteAtMs,
            nowAmerican = if (open) b.novigFair?.let { com.tjshea.vigilant.engine.Odds.probabilityToAmerican(it) } else b.nowAmerican,
            cnoFair = null, vigFair = null, books = emptyList(),
            closingFair = b.novigClose?.let { asBet(b, it) }, closingSeenAtMs = b.novigCloseAtMs,
            closeFair = b.closeFair?.takeIf { novigTrades(b) }, closeVia = b.closeVia?.takeIf { novigTrades(b) },
        )
    }

    /**
     * Why an open bet shows no Novig price now: the last look's reason while it's newer than the last price ([TrackedBet.novigWhy]), else that it hasn't
     * been read (or its exact Novig bet looked up) yet. Null when its price is the latest word.
     */
    fun note(b: TrackedBet): String? {
        val why = b.novigWhy
        if (why != null && (b.novigWhyAtMs ?: 0L) >= (b.novigAtMs ?: Long.MIN_VALUE)) return why
        if (b.novigFair != null) return null
        return if (b.marketId.isBlank() || b.outcomeId.isBlank()) "Novig's exact bet hasn't been looked up yet (tap Check Novig now)"
        else "Novig's price for this bet hasn't been read yet (tap Check Novig now)"
    }

    const val VIA = "novig"

    /** [b]'s close found afterwards came from Novig's own trade history ([NovigTradeCloses]). */
    private fun novigTrades(b: TrackedBet): Boolean = b.closeVia?.startsWith(CloseBackfill.VIA_NOVIG) == true

    /** [Read.why] when Novig's order book for the market couldn't be read. */
    const val BOOK_UNREAD = "Novig's order book for this market couldn't be read just now"

    /** [Read.why] when the market has left Novig's catalog. */
    const val NOT_LISTED = "Novig no longer lists this market (closed, or taken down)"

    /** [Read.why] when the book is empty. */
    const val NOTHING_OFFERED = "Novig isn't offering this bet right now (no odds to buy it at)"

    /** [Read.why] when the side on record isn't one of the market's. */
    const val NOT_A_SIDE = "the side on record isn't one of this Novig market's sides"

    /**
     * What a read found: bet id → Novig's odds now, how many bets were due, the markets asked for, and bet id → why a due bet got no price
     * ([BOOK_UNREAD], [NOT_LISTED], [NOT_A_SIDE], [NOTHING_OFFERED]).
     */
    data class Read(val prices: Map<String, Double>, val due: Int, val all: Int, val marketsAsked: List<String>, val why: Map<String, String> = emptyMap())

    /**
     * Novig's prices for the open [bets] that need them: the stale ones ([stale]), or all of them when [force]. Only [books] (Novig's order books,
     * one per market) and [market] (a market's two sides) are asked: no other book. Nothing is asked when no bet is due.
     */
    suspend fun read(
        bets: List<TrackedBet>,
        now: Long,
        force: Boolean,
        books: suspend (List<String>) -> Map<String, NovigBook>,
        market: suspend (String) -> NovigMarket?,
    ): Read {
        val all = priceable(bets, now)
        val due = if (force) all else stale(bets, now)
        if (due.isEmpty()) return Read(emptyMap(), 0, all.size, emptyList())
        val ids = due.map { it.marketId }.distinct()
        val read = books(ids)
        val prices = HashMap<String, Double>()
        val why = HashMap<String, String>()
        for (b in due) {
            val book = read[b.marketId] ?: run { why[b.id] = BOOK_UNREAD; null } ?: continue
            val m = market(b.marketId) ?: run { why[b.id] = NOT_LISTED; null } ?: continue
            if (m.outcomes.isNotEmpty() && m.outcomes.none { it.outcomeId == b.outcomeId }) {
                why[b.id] = NOT_A_SIDE
                continue
            }
            val p = odds(book, m, b.outcomeId)
            if (p != null) prices[b.id] = p else why[b.id] = NOTHING_OFFERED
        }
        return Read(prices, due.size, all.size, ids, why)
    }
}
