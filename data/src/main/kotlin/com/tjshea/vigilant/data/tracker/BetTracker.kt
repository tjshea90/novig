package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

@Serializable
enum class BetStatus {
    PENDING, WON, LOST, PUSH, VOID,

    /** Novig settled the market at a fair-market value: [TrackedBet.settleValue] per $1 contract. */
    FMV,
}

/**
 * One book's American price for a bet and its other side, as last read ("Check odds now" reads CNO's
 * game page, a Vigilant scan reads the books behind its fair line). Kept on the open bet so its sheet
 * shows every book at once, offline; dropped when the bet settles (it would only grow the file).
 */
@Serializable
data class BookLine(val name: String, val odds: Int? = null, val other: Int? = null) {
    /** Only a book pricing both sides can be devigged honestly. */
    val twoSided: Boolean get() = odds != null && other != null
}

/**
 * One bet Tj logged from a card. Prices are Novig prices (cost per $1 payout, fee included in
 * [cost]). [closingFair] is the last fair probability the scanner saw before the game started:
 * the closing-line value (CLV) this bet beat or didn't, which is the real test of an EV finder.
 */
@Serializable
data class TrackedBet(
    val id: String,
    val createdAtMs: Long,
    val league: String,
    val eventName: String,
    val startsTs: Long,
    val marketLabel: String,
    val selection: String,
    val marketId: String,
    val outcomeId: String,
    val price: Double,
    val cost: Double,
    /** Null for a bet imported from an old ✓ mark (its fair odds weren't kept). */
    val fairAtBet: Double?,
    val evPercentAtBet: Double?,
    val stake: Double,
    val status: BetStatus = BetStatus.PENDING,
    val settledAtMs: Long? = null,
    val closingFair: Double? = null,
    val closingSeenAtMs: Long? = null,
    /** "vigilant" (a +EV card or a Vigilant bet's ✓) or "cno" (a CNO bet's ✓). */
    val source: String = BetTracker.SOURCE_VIGILANT,
    /** The widget/CNO-tab key of the ✓ that logged it ("cno:<row key>"): Undo removes the bet. */
    val placedKey: String? = null,
    /** The price as Tj saw it (American). */
    val american: Int? = null,
    val book: String = "Novig",
    /** CNO's game page and deeplink, for rechecking the books and finding the Novig outcome. */
    val gameUrl: String? = null,
    val betUrl: String? = null,
    /** "Check odds now": the fair probability from every book now, the EV at the price bet, when, from how many books. */
    val nowFair: Double? = null,
    val nowEv: Double? = null,
    val nowAtMs: Long? = null,
    val nowBooks: Int? = null,
    /** A fair-market-value settlement's payout per $1 contract ([BetStatus.FMV]). */
    val settleValue: Double? = null,
    /** "novig" (settled from Novig's catalog) or "you" (tapped). */
    val settledBy: String? = null,
    /** Logged from a ✓ mark made before the Tracker kept them (its EV and fair odds weren't kept). */
    val imported: Boolean = false,
    /** Every book's price for this bet at the last read ([BookLine]), when it was read, and the other side's name ("Under 4.5"). */
    val books: List<BookLine> = emptyList(),
    val booksAtMs: Long? = null,
    val otherSide: String? = null,
    /** The bet's own book's price at the last read (American): Novig's price now, to set beside [american]. */
    val nowAmerican: Int? = null,
    /**
     * How the last grading attempt went, and when: why an open bet isn't graded yet ("Game isn't over yet",
     * "Can't grade this market automatically"), or, once settled, what settled it ("Final: Mets 7, Nationals 1").
     */
    val gradeNote: String? = null,
    val gradeAtMs: Long? = null,
    /** [gradeNote] is a reason only Tj can fix with a tap (a market or player the feeds can't grade), not a game still being played. */
    val gradeManual: Boolean = false,
    /**
     * Placed through Novig's API from Vigilant (Tj, 2026-09-29): the order, the contracts that filled (1¢ each when they win), the dollars paid
     * for them and the taker fee. Everything else about the bet is exact from the fills, and Novig's own ledger grades it.
     */
    val orderId: String? = null,
    val contracts: Long? = null,
    val paid: Double? = null,
    val fee: Double? = null,
) {
    /** Placed through the API: a real order on Novig, never removed by an Undo of a ✓ mark. */
    val viaApi: Boolean get() = orderId != null

    /** What a win pays back in profit: every $1 of cost returns $1 / cost. */
    val profitIfWon: Double get() = stake * (1.0 / cost - 1.0)

    val profit: Double?
        get() = when (status) {
            BetStatus.WON -> profitIfWon
            BetStatus.LOST -> -stake
            BetStatus.PUSH, BetStatus.VOID -> 0.0
            BetStatus.FMV -> settleValue?.let { stake * (it / cost - 1.0) } ?: 0.0
            BetStatus.PENDING -> null
        }

    val expectedProfit: Double get() = stake * (evPercentAtBet ?: 0.0)

    /** Open and the game has started: only its result is left. */
    fun awaitingResult(now: Long): Boolean = status == BetStatus.PENDING && now >= startsTs

    /**
     * Auto-grading is off for this bet: Tj tapped a result and then undid it ([BetTracker.settle]), so
     * [BetSettler] leaves it alone until "Grade automatically" turns it back on ([BetTracker.regrade]).
     */
    val autoGradeOff: Boolean get() = status == BetStatus.PENDING && settledBy == BetSettler.BY_YOU

    /** Closing-line value: the EV this price had against the closing fair line. */
    val clvPercent: Double? get() = closingFair?.let { it / cost - 1.0 }

    /**
     * An EV so far from the rest (over [BetTracker.OUTLIER_EV] either way when bet) that it's left out
     * of every stat (Tj, 2026-09-27: "I don't want the average skewed by a single bet that is an
     * outlier"). A bet with no EV on record isn't one.
     */
    val isOutlier: Boolean get() = evPercentAtBet?.let { kotlin.math.abs(it) > BetTracker.OUTLIER_EV + 1e-9 } ?: false
}

data class TrackerStats(
    val bets: Int,
    val pending: Int,
    val settled: Int,
    val staked: Double,
    val profit: Double,
    val roi: Double?,
    /**
     * Expected profit on the settled bets that have an EV on record: the number [profitWithEv] is to be
     * compared with. (Open bets' expected profit is [openExpected]; adding it in here made "Expected" a
     * different set of bets from "Profit".)
     */
    val expectedProfit: Double,
    val averageEv: Double?,
    val averageClv: Double?,
    val beatClosePercent: Double?,
    val won: Int = 0,
    val lost: Int = 0,
    /** Pushes and fair-value settlements: neither a win nor a loss. */
    val pushed: Int = 0,
    /** Bets left out of every number above ([TrackedBet.isOutlier]). */
    val outliers: Int = 0,
    /** Bets voided: they never happened, so they count toward nothing else. */
    val voided: Int = 0,
    /** Money on open bets, what they pay if they all win, and their expected profit. */
    val openStaked: Double = 0.0,
    val openToWin: Double = 0.0,
    val openExpected: Double = 0.0,
    /** Actual profit over the settled bets that have an EV on record, and how many those are. */
    val profitWithEv: Double = 0.0,
    val settledWithEv: Int = 0,
    /** Standard deviation of [profitWithEv] if every bet's fair probability were exactly right. */
    val expectedSd: Double = 0.0,
    /** Settled profit with the outliers counted too: what the bankroll really did. */
    val profitAll: Double = 0.0,
) {
    /** Wins out of decided bets (Tj: "percentage of actual bet wins and losses"); null before any. */
    val winRate: Double? get() = (won + lost).takeIf { it > 0 }?.let { won.toDouble() / it }

    /** Profit above (below) what the edges promised, on the same bets. */
    val vsExpected: Double get() = profitWithEv - expectedProfit

    /**
     * How many standard deviations [profitWithEv] sits from [expectedProfit]: near 0 is what a real edge
     * looks like, ±2 is unusual luck (or wrong fair prices). Null with too few bets to say anything.
     */
    val luck: Double? get() = if (settledWithEv >= MIN_BETS_FOR_LUCK && expectedSd > 1e-9) vsExpected / expectedSd else null

    companion object {
        const val MIN_BETS_FOR_LUCK = 10
    }
}

/**
 * Tracked bets, persisted in one JSON file (see [JsonFileStore] for the crash-safety rules).
 * [ownBook] names the book the app's own scan prices ("Novig"; "BetMGM" in Vigilant MGM).
 */
class BetTracker(file: File, private val clock: () -> Long = System::currentTimeMillis, private val ownBook: String = "Novig") {

    private val store = JsonFileStore(file, ListSerializer(TrackedBet.serializer()), { emptyList() })

    val flow get() = store.flow

    suspend fun all(): List<TrackedBet> = store.read()

    /**
     * A CNO bet Tj marked placed (✓ in the widget or the CNO tab; Tj, 2026-09-27: "for every bet
     * that I check on the cno scanner, log it permanently"): kept for good, [stake] $1 unless he
     * changes it (Novig's API can't read the app's own bets, so the amount isn't known). [row] is
     * the bet at the price shown when it was marked; [live]: the game had started (Novig's taker
     * fee is part of the cost). Marking the same ✓ again replaces the open bet, never duplicates it.
     */
    suspend fun logCno(
        row: com.tjshea.vigilant.data.cno.CnoRow,
        ev: Double,
        live: Boolean,
        placedKey: String,
        stake: Double = DEFAULT_STAKE,
        marketId: String = "",
        outcomeId: String = "",
    ): TrackedBet {
        val decimal = com.tjshea.vigilant.engine.Odds.americanToDecimal(row.odds)
        val price = 1.0 / decimal
        val fee = if (live && price > 0.0 && price < 1.0) {
            com.tjshea.vigilant.engine.Fees.takerFee(price, com.tjshea.vigilant.engine.MarketFee.GAME, eventLive = true)
        } else {
            0.0
        }
        val bet = TrackedBet(
            id = UUID.randomUUID().toString(),
            createdAtMs = clock(),
            league = row.league,
            eventName = row.event,
            startsTs = row.startsAtMs ?: clock(),
            marketLabel = row.market,
            selection = row.bet,
            marketId = marketId,
            outcomeId = outcomeId,
            price = price,
            cost = price + fee,
            fairAtBet = com.tjshea.vigilant.data.cno.CnoChecks.fairProbability(row) ?: ((1 + ev) / decimal),
            evPercentAtBet = ev,
            stake = stake,
            source = SOURCE_CNO,
            placedKey = placedKey,
            american = row.odds,
            book = row.book,
            gameUrl = row.gameUrl,
            betUrl = row.betUrl,
        )
        store.update { list -> list.filterNot { it.placedKey == placedKey && it.status == BetStatus.PENDING && it.orderId == null } + bet }
        return bet
    }

    /**
     * A bet Tj marked placed from a +EV push notification's "✓ Placed" (Tj, 2026-09-29): logged at the alert's
     * price with [stake] ($1 unless the alert carried Settings' bet-slip amount; he can correct the stake and the
     * price he got in the Tracker). [placedKey] is the alert's own key, the one the lists hide it by, so Undo
     * ([untrack]) finds it. Marking the same alert again replaces the open bet, never duplicates it.
     */
    suspend fun logAlert(a: com.tjshea.vigilant.data.alerts.EvAlert, stake: Double = DEFAULT_STAKE): TrackedBet {
        val price = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(a.american)
        val fee = if (a.live && price > 0.0 && price < 1.0) {
            com.tjshea.vigilant.engine.Fees.takerFee(price, com.tjshea.vigilant.engine.MarketFee.GAME, eventLive = true)
        } else {
            0.0
        }
        val cost = price + fee
        val bet = TrackedBet(
            id = UUID.randomUUID().toString(),
            createdAtMs = clock(),
            league = a.league,
            eventName = a.event,
            startsTs = a.startsAtMs ?: clock(),
            marketLabel = a.market,
            selection = a.bet,
            marketId = a.marketId.orEmpty(),
            outcomeId = a.outcomeId.orEmpty(),
            price = price,
            cost = cost,
            // The alert's EV already has the live fee in it, so the fair probability is what that EV implies at the cost.
            fairAtBet = a.fair ?: ((1.0 + a.ev) * cost),
            evPercentAtBet = a.ev,
            stake = stake,
            source = if (a.isCno) SOURCE_CNO else SOURCE_VIGILANT,
            placedKey = a.key,
            american = a.american,
            book = a.book,
            gameUrl = a.gameUrl,
            betUrl = a.betUrl,
        )
        store.update { list -> list.filterNot { it.placedKey == a.key && it.status == BetStatus.PENDING && it.orderId == null } + bet }
        return bet
    }

    /** Undo, or "not placed after all": the open bet that ✓ logged goes. Settled ones stay. */
    suspend fun untrack(placedKey: String) {
        // A bet placed through the API is a real order on Novig: only deleting it in the Tracker removes it.
        store.update { list -> if (list.none { it.placedKey == placedKey }) list else list.filterNot { it.placedKey == placedKey && it.status == BetStatus.PENDING && it.orderId == null } }
    }

    /** Changes one bet (stake, a recheck, a settlement): [transform] gets the stored bet. */
    suspend fun edit(id: String, transform: (TrackedBet) -> TrackedBet) {
        store.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /**
     * Changes several bets in one save: every save rewrites and fsyncs the whole file, so a recheck or a
     * grading pass over a hundred bets writes it once per batch, not once per bet. Each transform gets the
     * stored bet (so it never overwrites a change made since the pass read it). Bets gone meanwhile are skipped.
     */
    suspend fun editMany(changes: Map<String, (TrackedBet) -> TrackedBet>) {
        if (changes.isEmpty()) return
        store.update { list -> list.map { b -> changes[b.id]?.invoke(b) ?: b } }
    }

    /** A bet placed through the API has its stake and price from the fills: they can't be corrected by hand. */
    suspend fun setStake(id: String, stake: Double) = edit(id) { if (it.orderId != null) it else it.copy(stake = stake) }

    /**
     * Corrects the price a bet was really filled at (an alert's price moved, or a $1 ✓ was placed at another
     * price): its cost, EV and profit follow. The taker fee is kept only if the bet had one (a live bet).
     * Ignored for odds inside ±100.
     */
    suspend fun setPrice(id: String, american: Int) {
        if (american > -100 && american < 100) return
        edit(id) { b ->
            if (b.orderId != null) return@edit b
            val price = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(american)
            val live = b.cost > b.price + 1e-9
            val fee = if (live && price < 1.0) com.tjshea.vigilant.engine.Fees.takerFee(price, com.tjshea.vigilant.engine.MarketFee.GAME, eventLive = true) else 0.0
            val cost = price + fee
            b.copy(
                price = price, cost = cost, american = american,
                evPercentAtBet = b.fairAtBet?.let { it / cost - 1.0 } ?: b.evPercentAtBet,
                nowEv = b.nowFair?.let { it / cost - 1.0 },
            )
        }
    }

    /** Turns auto-grading back on for an open bet whose result Tj tapped and undid: the score feeds grade it again. */
    suspend fun regrade(id: String) = edit(id) {
        if (it.status == BetStatus.PENDING && it.settledBy == BetSettler.BY_YOU) it.copy(settledBy = null, gradeNote = null, gradeAtMs = null, gradeManual = false) else it
    }

    /**
     * ✓ marks made before the Tracker kept them (placed.json, Tj 2026-09-27: "It should move all of
     * the bets I made into the tracker section automatically"): each one not already a bet becomes
     * one, $1, with what the mark kept (bet, market, game, price, start). Removed (✕) marks aren't
     * bets. Returns how many were added.
     */
    suspend fun importPlaced(marks: List<PlacedBet>): Int {
        var added = 0
        store.update { list ->
            val known = list.mapNotNullTo(HashSet()) { it.placedKey }
            val new = marks.filter { !it.hidden && it.key !in known && it.aliases.none { k -> k in known } }.mapNotNull { m ->
                val american = m.odds.replace("−", "-").trim().toIntOrNull()?.takeIf { it >= 100 || it <= -100 } ?: return@mapNotNull null
                val price = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(american)
                val parts = m.detail.split(" · ")
                val cno = m.key.startsWith("cno:")
                // A Vigilant mark's key is its Novig "<market>/<outcome>"; a CNO one's starts with its game page.
                val ids = if (cno) null else m.key.split('/').takeIf { it.size == 2 && it.all(String::isNotBlank) }
                val gameUrl = if (cno) m.key.removePrefix("cno:").substringBeforeLast('|').takeIf { it.startsWith("http") } else null
                TrackedBet(
                    id = UUID.randomUUID().toString(),
                    createdAtMs = m.placedAtMs,
                    league = "",
                    eventName = parts.getOrNull(1).orEmpty(),
                    startsTs = m.startsAtMs ?: m.placedAtMs,
                    marketLabel = parts.getOrNull(0).orEmpty(),
                    selection = m.title,
                    marketId = ids?.get(0).orEmpty(),
                    outcomeId = ids?.get(1).orEmpty(),
                    price = price,
                    cost = price,
                    fairAtBet = null,
                    evPercentAtBet = null,
                    stake = DEFAULT_STAKE,
                    source = if (cno) SOURCE_CNO else SOURCE_VIGILANT,
                    placedKey = m.key,
                    american = american,
                    book = parts.getOrNull(2) ?: "Novig",
                    gameUrl = gameUrl,
                    imported = true,
                )
            }
            added = new.size
            if (new.isEmpty()) list else list + new
        }
        return added
    }

    suspend fun track(o: Opportunity, stake: Double, placedKey: String? = null): TrackedBet? {
        val q = o.quote ?: return null
        val fair = o.fairProbability ?: return null
        val bet = TrackedBet(
            id = UUID.randomUUID().toString(),
            createdAtMs = clock(),
            league = o.league.displayName,
            eventName = o.event.description,
            startsTs = o.event.startsTs,
            marketLabel = o.marketLabel,
            selection = o.selection,
            marketId = o.market.marketId,
            outcomeId = o.outcome.outcomeId,
            price = q.price,
            cost = q.cost,
            fairAtBet = fair,
            evPercentAtBet = q.evPercent,
            stake = stake,
            placedKey = placedKey,
            american = com.tjshea.vigilant.engine.Odds.probabilityToAmerican(q.price.coerceIn(0.001, 0.999)),
            book = ownBook,
        )
        store.update { list -> (if (placedKey == null) list else list.filterNot { it.placedKey == placedKey && it.status == BetStatus.PENDING && it.orderId == null }) + bet }
        return bet
    }

    /**
     * A bet placed through Novig's API (Tj, 2026-09-29): logged from what the order really did, [fills] summed. The stake is the dollars
     * paid plus the fee, the price the average paid, the EV that at the fair probability the bet rested on. The same order is never logged
     * twice (a sync after a lost answer finds it already here). Null when nothing filled.
     */
    suspend fun logApi(
        target: com.tjshea.vigilant.data.novig.trading.BetTarget,
        orderId: String,
        fills: List<com.tjshea.vigilant.data.novig.trading.NovigFill>,
        /** Found in Novig's fills after the fact (no fair odds on record): no EV is claimed. */
        imported: Boolean = false,
    ): TrackedBet? {
        val contracts = fills.sumOf { it.qty }
        if (contracts <= 0L) return null
        val paid = fills.sumOf { it.cost }
        val fee = fills.sumOf { it.fee }
        val payout = contracts * com.tjshea.vigilant.engine.EvMath.CONTRACT_PAYOUT_DOLLARS
        val stake = paid + fee
        val price = paid / payout
        val cost = stake / payout
        val bet = TrackedBet(
            id = UUID.randomUUID().toString(),
            createdAtMs = fills.minOf { it.ts }.takeIf { it > 0 } ?: clock(),
            league = target.league,
            eventName = target.eventName,
            startsTs = target.startsTs,
            marketLabel = target.marketLabel,
            selection = target.selection,
            marketId = target.market.marketId,
            outcomeId = target.outcomeId,
            price = price,
            cost = cost,
            fairAtBet = target.fair.takeUnless { imported },
            evPercentAtBet = (target.fair / cost - 1.0).takeUnless { imported },
            stake = stake,
            source = target.source,
            placedKey = target.placedKey,
            imported = imported,
            american = com.tjshea.vigilant.engine.Odds.probabilityToAmerican(price.coerceIn(0.001, 0.999)),
            book = target.book,
            gameUrl = target.gameUrl,
            betUrl = target.betUrl,
            orderId = orderId,
            contracts = contracts,
            paid = paid,
            fee = fee,
            gradeNote = "Placed through Novig's API: ${contracts} contracts, ${"%.2f".format(java.util.Locale.US, paid)} paid",
        )
        var logged: TrackedBet = bet
        store.update { list ->
            val have = list.firstOrNull { it.orderId == orderId }
            if (have != null) {
                logged = have
                list
            } else {
                // The ✓ marks for the same row (a CNO bet or an alert) are replaced by the real bet.
                (if (target.placedKey == null) list else list.filterNot { it.placedKey == target.placedKey && it.status == BetStatus.PENDING && it.orderId == null }) + bet
            }
        }
        return logged
    }

    suspend fun settle(id: String, status: BetStatus) {
        store.update { list ->
            // A tap (or its undo) is Tj's call: the auto-settle leaves it alone from then on ([regrade] gives it back).
            list.map {
                if (it.id == id) {
                    it.copy(
                        status = status, settledAtMs = if (status == BetStatus.PENDING) null else clock(), settleValue = null, settledBy = BetSettler.BY_YOU,
                        // A result ends the book snapshot (only open bets show it); an undo starts a clean grading note.
                        books = if (status == BetStatus.PENDING) it.books else emptyList(),
                        gradeNote = if (status == BetStatus.PENDING) null else "Marked ${status.name.lowercase()} by you", gradeAtMs = if (status == BetStatus.PENDING) null else clock(), gradeManual = false,
                    )
                } else {
                    it
                }
            }
        }
    }

    suspend fun delete(id: String) {
        store.update { list -> list.filterNot { it.id == id } }
    }

    /**
     * Records the latest fair price for every pending bet the scan still sees, as long as the game
     * hasn't started. The last one written before the start is the closing line.
     */
    suspend fun observe(result: ScanResult): Boolean {
        val now = clock()
        val byKey = result.opportunities.associateBy { it.market.marketId to it.outcome.outcomeId }
        fun fresh(b: TrackedBet): Double? {
            if (b.status != BetStatus.PENDING || now >= b.startsTs) return null
            val fair = byKey[b.marketId to b.outcomeId]?.fairProbability ?: return null
            // Only a real change is worth a disk write.
            return fair.takeIf { b.closingFair == null || kotlin.math.abs(it - b.closingFair) > 1e-9 }
        }
        if (store.read().none { fresh(it) != null }) return false
        // The scan's fair line is also the bet's EV now (the Tracker's "now +3% EV"), and its books are the bet's books.
        store.update { list ->
            list.map { b ->
                val fair = fresh(b) ?: return@map b
                val o = byKey[b.marketId to b.outcomeId]
                val lines = o?.let(::booksOf).orEmpty()
                b.copy(
                    closingFair = fair, closingSeenAtMs = now, nowFair = fair, nowEv = fair / b.cost - 1.0, nowAtMs = now,
                    books = lines.ifEmpty { b.books }, booksAtMs = if (lines.isEmpty()) b.booksAtMs else now,
                    nowBooks = lines.count { it.twoSided }.takeIf { lines.isNotEmpty() } ?: b.nowBooks,
                    // Novig's price now without its fee (the fee stays in the bet's own cost), as CNO's page shows it.
                    nowAmerican = o?.quote?.price?.coerceIn(0.001, 0.999)?.let(com.tjshea.vigilant.engine.Odds::probabilityToAmerican) ?: b.nowAmerican,
                )
            }
        }
        return true
    }

    companion object {
        const val SOURCE_VIGILANT = "vigilant"
        const val SOURCE_CNO = "cno"

        /** A ✓ logs a $1 bet: Novig's API can't read the app's own bets (NOVIG_API.md: subaccounts only). */
        const val DEFAULT_STAKE = 1.0

        /**
         * A bet whose EV when placed was over this, either way, is an outlier: kept in the list of bets,
         * left out of every stat (Tj, 2026-09-27: "currently + or - over 6% ev").
         */
        const val OUTLIER_EV = 0.06

        /** Every book's price behind [o]'s fair line, for [TrackedBet.books]: this outcome's odds and, on a two-way line, the other side's. */
        fun booksOf(o: Opportunity): List<BookLine> {
            val idx = o.referenceIndex ?: return emptyList()
            val perBook = o.fair?.perBook ?: return emptyList()
            return perBook.mapNotNull { bf ->
                val odds = bf.book.decimalOdds
                val mine = odds.getOrNull(idx)?.takeIf { it > 1.0 }?.let(com.tjshea.vigilant.engine.Odds::decimalToAmerican) ?: return@mapNotNull null
                val other = if (odds.size == 2) odds[1 - idx].takeIf { it > 1.0 }?.let(com.tjshea.vigilant.engine.Odds::decimalToAmerican) else null
                BookLine(bf.book.bookTitle, mine, other)
            }
        }

        /**
         * The Tracker's numbers for [all]: outliers ([TrackedBet.isOutlier]) left out of everything but
         * [TrackerStats.profitAll]; voided bets counted only as voided. "Profit" and "Expected" are over the
         * same settled bets, so they can be compared (the edge is real when they run together).
         */
        fun stats(all: List<TrackedBet>): TrackerStats {
            val bets = all.filterNot { it.isOutlier }
            val decided = { b: TrackedBet -> b.status != BetStatus.PENDING && b.status != BetStatus.VOID }
            val settled = bets.filter(decided)
            val staked = settled.sumOf { it.stake }
            val profit = settled.sumOf { it.profit ?: 0.0 }
            // A voided bet never happened: it counts toward nothing but the bet count.
            val live = bets.filter { it.status != BetStatus.VOID }
            val open = bets.filter { it.status == BetStatus.PENDING }
            val withClv = live.mapNotNull { it.clvPercent }
            // "Expected vs actual" over the won and lost bets whose EV is on record (an imported ✓ has none; a push is refunded).
            val judged = settled.filter { (it.status == BetStatus.WON || it.status == BetStatus.LOST) && it.evPercentAtBet != null && it.fairAtBet != null }
            // A bet's profit is stake x (1/cost - 1) with probability p (its fair chance) and -stake otherwise:
            // variance stake^2 x p(1-p) / cost^2.
            val variance = judged.sumOf { b ->
                val p = (b.fairAtBet ?: 0.0).coerceIn(0.0, 1.0)
                b.stake * b.stake * p * (1.0 - p) / (b.cost * b.cost)
            }
            return TrackerStats(
                bets = bets.size,
                pending = open.size,
                settled = settled.size,
                staked = staked,
                profit = profit,
                roi = if (staked > 0) profit / staked else null,
                expectedProfit = judged.sumOf { it.expectedProfit },
                averageEv = live.mapNotNull { it.evPercentAtBet }.takeIf { it.isNotEmpty() }?.average(),
                averageClv = withClv.takeIf { it.isNotEmpty() }?.average(),
                beatClosePercent = withClv.takeIf { it.isNotEmpty() }?.let { l -> l.count { it > 0 }.toDouble() / l.size },
                won = bets.count { it.status == BetStatus.WON },
                lost = bets.count { it.status == BetStatus.LOST },
                pushed = bets.count { it.status == BetStatus.PUSH || it.status == BetStatus.FMV },
                outliers = all.size - bets.size,
                voided = bets.count { it.status == BetStatus.VOID },
                openStaked = open.sumOf { it.stake },
                openToWin = open.sumOf { it.profitIfWon },
                openExpected = open.sumOf { it.expectedProfit },
                profitWithEv = judged.sumOf { it.profit ?: 0.0 },
                settledWithEv = judged.size,
                expectedSd = kotlin.math.sqrt(variance),
                profitAll = all.filter(decided).sumOf { it.profit ?: 0.0 },
            )
        }
    }
}
