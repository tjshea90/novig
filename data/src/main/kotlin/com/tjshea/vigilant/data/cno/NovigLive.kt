package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Novig's own price right now for a CNO bet, read from Novig's order book. */
data class LivePrice(
    /** What Novig's bet slip shows now (American, before any live-game fee). */
    val american: Int,
    /** Dollars at that price (the top of the book), as CNO's "available" counts them. */
    val available: Double?,
    /** EV at that price against CNO's fair probability (Novig's taker fee taken out on a live game). */
    val ev: Double?,
    /** When the book was read. */
    val atMs: Long,
    /** The Novig market and outcome it is (ParlayAPI's picks are priced by Vigilant's own fair odds from them, TASKS.md P2). */
    val marketId: String? = null,
    val outcomeId: String? = null,
)

/**
 * Keeps Novig's current price for the listed CNO bets (Tj, 2026-09-27: "consider ways to make cno
 * respond even with high traffic and rapid refreshing"). CNO wasn't limiting reads (60 a minute,
 * no refusals; RESEARCH.md §20.3), but its list is only as fresh as its last update (13–33 s
 * apart, longer when it's slow or out of reach). Novig's public order book is Novig's price now,
 * whatever CNO is doing: the top [top] Novig bets' books are read every [everyMs] (Novig answers
 * "not modified" when nothing changed), and each CNO read is re-priced against the books already
 * read, at once and with no request. The EV stays CNO's fair probability against Novig's price.
 *
 * Which Novig market and outcome a CNO bet is comes from Novig's catalog ([find], the same exact
 * match that opens the bet slip); a bet it can't pin down is left as CNO priced it.
 */
class NovigLive(
    private val novig: NovigSource,
    private val find: suspend (CnoRow) -> NovigBetFinder.Found?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _prices = MutableStateFlow<Map<String, LivePrice>>(emptyMap())

    /** Live prices by CNO row key. */
    val prices: StateFlow<Map<String, LivePrice>> = _prices.asStateFlow()

    private class Target(val marketId: String, val outcomeId: String)

    /** Each row's market and outcome, by row key. */
    private val targets = LinkedHashMap<String, Target>()

    /** Rows the catalog couldn't pin down (or couldn't be read for), and when: asked again after [RETRY_MS]. */
    private val missedAt = HashMap<String, Long>()

    /** Markets (their fee and outcomes), which don't change. */
    private val markets = HashMap<String, NovigMarket>()

    /** The last book read per market. */
    private val books = HashMap<String, NovigBook>()

    /** Book requests made (tests count them). */
    var reads = 0
        private set

    @Volatile
    private var lastReadMs = Long.MIN_VALUE / 2

    /** [keepFresh] (the screen's loop) and [readNow] (a background check) share the maps above. */
    private val mutex = Mutex()

    /**
     * Novig's price now for [rows] (the Novig ones), read once: for the background check behind
     * alerts (Tj, 2026-09-28), which runs with no screen and so no [keepFresh]. Merged into [prices]
     * too, so the lists show them when opened. Rows the catalog can't pin down are left out.
     */
    suspend fun readNow(rows: List<CnoRow>): Map<String, LivePrice> = mutex.withLock {
        val list = rows.filter { it.book.equals("Novig", ignoreCase = true) }.distinctBy { it.key }
        if (list.isEmpty()) return@withLock emptyMap()
        for (row in list) {
            if (row.key in targets || missedAt[row.key]?.let { clock() - it < RETRY_MS } == true) continue
            val t = resolve(row)
            if (t != null) targets[row.key] = t.also { missedAt.remove(row.key) } else missedAt[row.key] = clock()
        }
        read(list.mapNotNull { targets[it.key]?.marketId }.distinct())
        val now = price(list)
        _prices.value = _prices.value + now
        now
    }

    /** Runs until cancelled; the caller runs it only while CNO's list is on screen. */
    suspend fun keepFresh(rows: Flow<List<CnoRow>>, top: Int = LIVE_TOP, everyMs: Long = LIVE_EVERY_MS) {
        rows.map { list -> list.filter { it.book.equals("Novig", ignoreCase = true) }.take(top) }
            .distinctUntilChanged()
            .collectLatest { list ->
                if (list.isEmpty()) {
                    _prices.value = emptyMap()
                    return@collectLatest
                }
                while (true) {
                    val wait = mutex.withLock {
                        for (row in list) {
                            if (row.key in targets || missedAt[row.key]?.let { clock() - it < RETRY_MS } == true) continue
                            val t = resolve(row)
                            if (t != null) targets[row.key] = t.also { missedAt.remove(row.key) } else missedAt[row.key] = clock()
                        }
                        prune(list)
                        val wait = lastReadMs + everyMs - clock()
                        if (wait <= 0) {
                            lastReadMs = clock()
                            read(list.mapNotNull { targets[it.key]?.marketId }.distinct())
                        }
                        // A new CNO read re-prices against the books already read: no request.
                        _prices.value = price(list)
                        wait
                    }
                    delay(if (wait <= 0) everyMs else wait)
                }
            }
    }

    private suspend fun resolve(row: CnoRow): Target? {
        val found = runCatching { find(row) }.getOrNull() as? NovigBetFinder.Found.Bet ?: return null
        val marketId = found.marketId ?: return null
        // The catalog read that found it already has its market (fee, outcomes): no extra request.
        if (marketId !in markets) markets[marketId] = found.market ?: runCatching { novig.market(marketId) }.getOrNull() ?: return null
        return Target(marketId, found.outcomeId)
    }

    private suspend fun read(marketIds: List<String>) {
        if (marketIds.isEmpty()) return
        reads += marketIds.size
        val batch = runCatching { novig.books(marketIds) }.getOrNull() ?: return
        books.putAll(batch.books)
    }

    private fun price(list: List<CnoRow>): Map<String, LivePrice> = list.mapNotNull { row ->
        val t = targets[row.key] ?: return@mapNotNull null
        val market = markets[t.marketId] ?: return@mapNotNull null
        val book = books[t.marketId] ?: return@mapNotNull null
        priceOf(row, market, book, t.outcomeId, clock())?.let { row.key to it }
    }.toMap()

    private fun prune(list: List<CnoRow>) {
        if (targets.size <= KEEP_TARGETS) return
        val listed = list.mapTo(HashSet()) { it.key }
        val drop = targets.keys.filter { it !in listed }.take(targets.size - KEEP_TARGETS)
        drop.forEach { targets.remove(it) }
        missedAt.keys.retainAll(listed)
        val usedMarkets = targets.values.mapTo(HashSet()) { it.marketId }
        markets.keys.retainAll(usedMarkets)
        books.keys.retainAll(usedMarkets)
    }

    companion object {
        /** The listed Novig bets whose books are read. */
        const val LIVE_TOP = 10

        /** How often they're read (most answers are "not modified"). */
        const val LIVE_EVERY_MS = 15_000L

        /** A live price older than this isn't shown (the widget went quiet, or Novig didn't answer). */
        const val FRESH_MS = 60_000L

        private const val KEEP_TARGETS = 300

        /** A bet the catalog couldn't pin down is looked up again after this long. */
        const val RETRY_MS = 60_000L

        /**
         * [row]'s price on [book] right now: the best price to take [outcomeId] (every order is a
         * bid; the other side's best bid, flipped), its dollars, and the EV at it against CNO's fair
         * probability, with Novig's taker fee on a game that has started. Null with nothing to take.
         */
        fun priceOf(row: CnoRow, market: NovigMarket, book: NovigBook, outcomeId: String, now: Long): LivePrice? {
            val fee = market.fee ?: return null
            val top = book.takeLadder(market, outcomeId).minByOrNull { it.price } ?: return null
            val live = row.startsAtMs != null && row.startsAtMs <= now
            val fair = CnoChecks.fairProbability(row)
            val quote = fair?.let { EvMath.quote(it, top.price, fee, live) }
            val cost = quote?.cost ?: top.price
            return LivePrice(
                american = Odds.probabilityToAmerican(top.price.coerceIn(0.001, 0.999)),
                available = top.contracts * cost * EvMath.CONTRACT_PAYOUT_DOLLARS,
                ev = quote?.evPercent,
                atMs = book.fetchedAtMs,
                marketId = market.marketId,
                outcomeId = outcomeId,
            )
        }
    }
}
