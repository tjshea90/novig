package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket

/**
 * The Tracker's "Novig only" filter (Tj, 2026-10-02 ~18:50Z: "find the current novig odds for each of my open bets and show the percent EV compared only
 * from novig odds, filtering out other sports books … do the already in place stats and ev calculations … but only for novig. Make sure it is smart and
 * doesn't waste any api usage on other sports books … if I already just scanned without using this filter and there is still fresh novig odds for all my
 * bets, it doesn't need to rescan"). Pure.
 *
 * Novig's own fair price for a side is the middle of its best bid and its offer (an exchange's bid and offer bracket the price; no vig to take out
 * beyond that spread). It's kept on the bet ([TrackedBet.novigFair], [TrackedBet.novigAtMs]) by any read of Novig's book for the bet (a pricing pass,
 * or the filter's own read of just the stale ones), and the last one before the start is Novig's closing line ([TrackedBet.novigClose]).
 */
object NovigNow {

    /** How old Novig's price for a bet may be and still be shown without reading again. */
    const val FRESH_MS = 2 * 60_000L

    /**
     * Novig's middle price for [outcomeId]: its best bid and the price to buy it now; the one side there is when the book has only one (a thin prop often
     * has bids on one side only: the offer alone when nobody bids, the bid alone when nothing is offered). Null when the book is empty.
     */
    fun mid(book: NovigBook, market: NovigMarket, outcomeId: String): Double? {
        val ask = book.takeLadder(market, outcomeId).minOfOrNull { it.price }
        val bid = book.bestBid(outcomeId)?.price
        val p = when {
            ask != null && bid != null -> (bid + ask) / 2.0
            else -> ask ?: bid ?: return null
        }
        return p.coerceIn(0.001, 0.999)
    }

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
     * [bets] as the "Novig only" filter shows and counts them: an open bet's fair now and EV now from Novig's price alone (none when Novig's hasn't been
     * read), its book list cut to Novig's line, and every bet's closing line Novig's own (none without one), so the Tracker's stats, EV and CLV come
     * from Novig and nothing else.
     */
    fun view(bets: List<TrackedBet>): List<TrackedBet> = bets.map { b ->
        val open = b.status == BetStatus.PENDING
        val fair = b.novigFair
        b.copy(
            nowFair = if (open) fair else b.nowFair,
            nowEv = if (open) fair?.let { it / b.cost - 1.0 } else b.nowEv,
            nowAtMs = if (open) b.novigAtMs else b.nowAtMs,
            nowBooks = if (open && fair != null) 1 else if (open) null else b.nowBooks,
            nowVia = if (open && fair != null) VIA else b.nowVia,
            nowNote = if (open) note(b) else b.nowNote,
            nowNoteAtMs = if (open) note(b)?.let { b.novigWhyAtMs } else b.nowNoteAtMs,
            cnoFair = null, vigFair = if (open) fair else b.vigFair,
            books = b.books.filter { it.name.equals(b.book.ifBlank { "Novig" }, ignoreCase = true) },
            // Novig's own close, judged as every close is: read in the last minutes before the start.
            closingFair = b.novigClose, closingSeenAtMs = b.novigCloseAtMs, closeFair = null, closeVia = null,
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

    /** [Read.why] when Novig's order book for the market couldn't be read. */
    const val BOOK_UNREAD = "Novig's order book for this market couldn't be read just now"

    /** [Read.why] when the market has left Novig's catalog. */
    const val NOT_LISTED = "Novig no longer lists this market (closed, or taken down)"

    /** [Read.why] when the book is empty. */
    const val NOTHING_OFFERED = "nobody is bidding on or offering this bet on Novig right now (not offered at the moment)"

    /** [Read.why] when the side on record isn't one of the market's. */
    const val NOT_A_SIDE = "the side on record isn't one of this Novig market's sides"

    /**
     * What a read found: bet id → Novig's middle price, how many bets were due, the markets asked for, and bet id → why a due bet got no price
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
            val p = mid(book, m, b.outcomeId)
            if (p != null) prices[b.id] = p else why[b.id] = NOTHING_OFFERED
        }
        return Read(prices, due.size, all.size, ids, why)
    }
}
