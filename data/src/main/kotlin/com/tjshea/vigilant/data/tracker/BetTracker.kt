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
    /** The last time the closing capture tried to read this bet just before its start ([ClosingLine]), so a failed read waits before the next. */
    val closeTriedAtMs: Long? = null,
    /**
     * The close found after the start from a source that keeps history ([CloseBackfill], Tj 2026-09-30: "My phone will not always be on"):
     * the bet's side's fair probability at the start, where it came from ([CloseBackfill.VIA_ESPN] / [CloseBackfill.VIA_NOVIG] plus
     * detail), why there's none yet ([closeNote]) and when it was last looked for.
     */
    val closeFair: Double? = null,
    val closeVia: String? = null,
    val closeNote: String? = null,
    val closeLookedAtMs: Long? = null,
    /** Found, or every source said it never will have it: [CloseBackfill] looks no more. */
    val closeFinal: Boolean = false,
    /**
     * The close sources ([CloseSource.id]) that all said "never" when [closeFinal] was set without a close: a source added later (ParlayAPI's
     * Pinnacle closes, 2026-09-30) is asked once more. Null on bets finalised before this was kept: ESPN's and Novig's.
     */
    val closeAskedOf: List<String>? = null,
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
    /**
     * Whose fair line [nowFair] is ([BetTracker.VIA_CNO]: CrazyNinjaOdds' books and its devig; [BetTracker.VIA_VIGILANT]: Vigilant's own
     * blend of the reference books), null for a read made before this was kept.
     */
    val nowVia: String? = null,
    /** Why the last try at pricing this open bet found no fair price (Tj, 2026-09-29: every open bet is priced or says why not), and when. */
    val nowNote: String? = null,
    val nowNoteAtMs: Long? = null,
    /**
     * The two reads "Check odds now" makes of every open bet (Tj, 2026-09-30: "always scan relevant vigilant odds in addition to the cno
     * scan … always get full updates on all of my bets and an accurate stats reading"): CNO's ([cnoFair]: its game page, or ParlayAPI's books
     * judged CNO's way) and Vigilant's own fair odds ([vigFair], every source it scans with, ParlayAPI included), each with when it was made
     * and, for Vigilant's, how many books it came from. A bet read both ways in one check has their average as its [nowFair]
     * ([BetTracker.VIA_BOTH]), and as its closing line so far.
     */
    val cnoFair: Double? = null,
    val cnoAtMs: Long? = null,
    val vigFair: Double? = null,
    val vigAtMs: Long? = null,
    val vigBooks: Int? = null,
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
    /** Novig's ids for the fills, one of which (or the market's) a ledger row may name as what it settles. */
    val fillIds: List<String> = emptyList(),
    /** How the fair odds behind [fairAtBet] were made ([FairBasis]); null for a bet logged before v0.36.0. */
    val fairBasis: FairBasis? = null,
    /** Placed by the auto-bet with nobody confirming it (Tj, 2026-10-01); otherwise exactly like a bet placed from the Bet sheet. */
    val auto: Boolean = false,
    /** Everything about the bet as it was placed ([AtBet], Tj 2026-10-02 17:01Z): never changed by a re-check. Null before v0.45.0. */
    val atBet: AtBet? = null,
    /**
     * A lock (Tj, 2026-10-02 ~18:50Z: "take the other side of the bet later on and guarantee a profit no matter which side of the bet wins"): the id of
     * the bet whose other side this bought ([com.tjshea.vigilant.data.novig.trading.LockIn]). Its profit counts in the Tracker's money; it isn't a +EV
     * pick, so the EV and closing-line stats leave it out ([isLock]).
     */
    val lockFor: String? = null,
    /**
     * Novig's own price for this bet's side (Tj, 2026-10-02 ~18:50Z: "find the current novig odds for each of my open bets and show the percent EV compared
     * only from novig odds"): the middle of Novig's best bid and offer, and when it was read ([NovigNow]); [novigClose] is the last one read before the
     * start (Novig's own closing line). The Tracker's "Novig only" filter prices the bet from these alone.
     */
    val novigFair: Double? = null,
    val novigAtMs: Long? = null,
    val novigClose: Double? = null,
    val novigCloseAtMs: Long? = null,
) {
    /** Bought to lock in another bet's profit ([lockFor]). */
    val isLock: Boolean get() = lockFor != null

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

    /**
     * The EV this price has against the last pregame fair line read ([closingFair]): CLV "so far". It's the true CLV only when that read was
     * made just before the start ([ClosingLine.clv], what every stat uses).
     */
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
    /** Locks ([TrackedBet.isLock]): in the stakes and profit above, not in the record, EV or closing line. */
    val locks: Int = 0,
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
        /** Whose list it came from: CNO's ([SOURCE_CNO]) or ParlayAPI's ([SOURCE_PARLAY]), both CNO-shaped rows. */
        source: String = SOURCE_CNO,
        atBet: AtBet? = null,
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
            source = source,
            placedKey = placedKey,
            american = row.odds,
            book = row.book,
            gameUrl = row.gameUrl,
            betUrl = row.betUrl,
            fairBasis = FairBasis(if (source == SOURCE_PARLAY) FairBasis.SOURCE_PARLAY else FairBasis.SOURCE_CNO, books = row.books ?: 0),
            atBet = atBet,
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
    suspend fun logAlert(a: com.tjshea.vigilant.data.alerts.EvAlert, stake: Double = DEFAULT_STAKE, atBet: AtBet? = AtBets.alert(a, clock())): TrackedBet {
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
            atBet = atBet?.copy(stake = stake),
        )
        store.update { list -> list.filterNot { it.placedKey == a.key && it.status == BetStatus.PENDING && it.orderId == null } + bet }
        return bet
    }

    /** Undo, or "not placed after all": the open bet that ✓ logged goes. Settled ones stay. */
    suspend fun untrack(placedKey: String) {
        // A bet placed through the API is a real order on Novig: only deleting it in the Tracker removes it.
        store.update { list -> if (list.none { it.placedKey == placedKey }) list else list.filterNot { it.placedKey == placedKey && it.status == BetStatus.PENDING && it.orderId == null } }
    }

    /** The closing capture is reading these bets now ([ClosingLine.due]): a failed read isn't tried again for [ClosingLine.RETRY_MS]. */
    suspend fun markCloseTried(ids: Collection<String>, at: Long = clock()) {
        if (ids.isEmpty()) return
        val set = ids.toHashSet()
        store.update { list -> list.map { if (it.id in set) it.copy(closeTriedAtMs = at) else it } }
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

    suspend fun track(o: Opportunity, stake: Double, placedKey: String? = null, atBet: AtBet? = null): TrackedBet? {
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
            fairBasis = FairBasis.of(o),
            atBet = atBet,
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
            fillIds = fills.map { it.fillId },
            fairBasis = target.basis.takeUnless { imported },
            auto = target.auto && !imported,
            // As decided, with what the order really cost (the record's price is the one judged; the bet's own fields hold the fill).
            atBet = target.atBet?.takeUnless { imported }?.copy(stake = stake),
            lockFor = target.lockFor,
            gradeNote = (if (target.lockFor != null) (if (target.auto) "Auto-lock through Novig's API: " else "Lock through Novig's API: ")
                else if (target.auto && !imported) "Auto-bet through Novig's API: " else "Placed through Novig's API: ") +
                "${contracts} contracts, ${"%.2f".format(java.util.Locale.US, paid)} paid",
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

    /** Novig's own price now for these bets ([NovigNow.apply]): bet id → the middle of Novig's bid and offer for its side, read at [at]. */
    suspend fun recordNovig(prices: Map<String, Double>, at: Long) {
        if (prices.isEmpty()) return
        store.update { list -> list.map { b -> prices[b.id]?.let { NovigNow.apply(b, it, at) } ?: b } }
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
        fun fresh(b: TrackedBet): Opportunity? {
            if (b.status != BetStatus.PENDING || now >= b.startsTs) return null
            val o = byKey[b.marketId to b.outcomeId] ?: return null
            val fair = o.fairProbability ?: return null
            // Only a real change is worth a disk write, and so is the age of a read: "3 min ago" must not stay 3 min ago.
            val changed = b.closingFair == null || kotlin.math.abs(fair - b.closingFair) > 1e-9 || b.nowVia != VIA_VIGILANT
            val aged = b.nowAtMs == null || now - b.nowAtMs >= OBSERVE_REFRESH_MS
            return o.takeIf { changed || aged }
        }
        if (store.read().none { fresh(it) != null }) return false
        // The scan's fair line is also the bet's EV now (the Tracker's "now +3% EV"), and its books are the bet's books.
        store.update { list ->
            list.map { b ->
                val o = fresh(b) ?: return@map b
                applyFair(b, o, now, VIA_VIGILANT)
            }
        }
        return true
    }

    /** What [applyPricing] did: [priced] open bets now have a current EV, [unpriced] got the reason they have none. */
    data class Applied(val priced: Int, val unpriced: Int)

    /**
     * The result of a bets-only pricing pass ([com.tjshea.vigilant.data.scanner.Scanner]'s `betsOnly`; Tj, 2026-09-29: "update the EV for every
     * single open bet, including bets added from vigilant scanner"): each of [betIds] still open and pregame that the [result] prices gets its
     * fair price, EV against the price it was bet at, age, books and Novig's price now; one it doesn't price keeps what it had and gets its
     * reason from [reasons] (the card says why, never a stale number that looks current).
     */
    suspend fun applyPricing(
        result: ScanResult?,
        betIds: Collection<String>,
        reasons: Map<String, String> = emptyMap(),
        /**
         * Vigilant's read beside CNO's, in a check that reads both ([mergeReads] decides what the bet shows): only [TrackedBet.vigFair] and
         * its time are written (and every book's price when the bet has none yet), never the current EV, the closing line or a reason.
         */
        alongside: Boolean = false,
    ): Applied {
        val now = clock()
        val ids = betIds.toHashSet()
        val byKey = result?.opportunities?.filter { it.fairProbability != null }?.associateBy { it.market.marketId to it.outcome.outcomeId }.orEmpty()
        var priced = 0
        var unpriced = 0
        store.update { list ->
            priced = 0
            unpriced = 0
            list.map { b ->
                if (b.id !in ids || b.status != BetStatus.PENDING || !BetsScope.readable(b, now)) return@map b
                val o = byKey[b.marketId to b.outcomeId]
                when {
                    o != null -> { priced++; applyFair(b, o, now, VIA_VIGILANT, alongside) }
                    else -> {
                        unpriced++
                        if (alongside) b else reasons[b.id]?.let { b.copy(nowNote = it, nowNoteAtMs = now) } ?: b
                    }
                }
            }
        }
        return Applied(priced, unpriced)
    }

    /**
     * [b] with [o]'s fair line as its fair now: EV at the price it was bet at, the books behind it, Novig's price now, and the closing line so far.
     * [alongside]: Vigilant's read only ([TrackedBet.vigFair]), the rest left to [mergeReads].
     */
    private fun applyFair(b0: TrackedBet, o: Opportunity, now: Long, via: String, alongside: Boolean = false): TrackedBet {
        // Novig's own price from the same book read (its bid and offer for this side): the "Novig only" filter reuses it instead of reading again.
        val novigMid = o.quote?.price?.let { ask -> o.bestBid?.let { bid -> ((bid + ask) / 2.0).coerceIn(0.001, 0.999) } ?: ask }
        val b = if (novigMid != null && o.bookFetchedAtMs != null) NovigNow.apply(b0, novigMid, o.bookFetchedAtMs) else b0
        val fair = o.fairProbability ?: return b
        val lines = booksOf(o)
        val twoSided = lines.count { it.twoSided }.takeIf { lines.isNotEmpty() }
        // Novig's price now without its fee (the fee stays in the bet's own cost), as CNO's page shows it.
        val novigNow = o.quote?.price?.coerceIn(0.001, 0.999)?.let(com.tjshea.vigilant.engine.Odds::probabilityToAmerican) ?: b.nowAmerican
        if (alongside) {
            // The book list CNO's page (or its backup) wrote in this same check stays; an older one gives way to this read's (Tj, 2026-09-30:
            // "all the vigilant results show stale odds").
            val keepBooks = lines.isEmpty() || b.booksAtMs?.let { now - it < BetRecheck.FRESH_MS } == true
            return b.copy(
                vigFair = fair, vigAtMs = now, vigBooks = twoSided ?: b.vigBooks, nowAmerican = novigNow,
                books = if (keepBooks) b.books else lines, booksAtMs = if (keepBooks) b.booksAtMs else now,
            )
        }
        // A read once the game is under way is its odds now, never its close. The close is dated by its oldest book price, not by when it
        // was saved, so only a line that was really current in the last minutes before the start counts as the true close.
        val closing = now < b.startsTs
        val asOf = minOf(now, o.fairAsOfMs ?: now)
        return b.copy(
            closingFair = if (closing && ClosingLine.supersedes(b, asOf)) fair else b.closingFair,
            closingSeenAtMs = if (closing && ClosingLine.supersedes(b, asOf)) asOf else b.closingSeenAtMs,
            nowFair = fair, nowEv = fair / b.cost - 1.0, nowAtMs = now, nowVia = via, nowNote = null, nowNoteAtMs = null,
            books = lines.ifEmpty { b.books }, booksAtMs = if (lines.isEmpty()) b.booksAtMs else now,
            nowBooks = twoSided ?: b.nowBooks,
            vigFair = fair, vigAtMs = now, vigBooks = twoSided ?: b.vigBooks,
            nowAmerican = novigNow,
        )
    }

    /** Which open bets a check read both ways, by CNO alone, by Vigilant alone, or neither ([mergeReads]). */
    data class Merged(val both: Set<String>, val cnoOnly: Set<String>, val vigOnly: Set<String>, val neither: Set<String>) {
        companion object {
            val EMPTY = Merged(emptySet(), emptySet(), emptySet(), emptySet())
        }
    }

    /**
     * After a check that read [ids] both ways (CNO's pages and Vigilant's own fair odds, [applyPricing] `alongside`): each bet's current EV
     * from what was read since [since] (CNO's since [cnoSince]: a page read in the minute before the tap is left as current, not read again).
     * Both: the average of the two fair lines ([VIA_BOTH]), a better estimate than either alone for the stats Tj reads; CNO's alone: as read;
     * Vigilant's alone: its line ([VIA_VIGILANT]). Before the start the same line is the closing line so far (CLV). Neither: [reasons]' why,
     * unless CNO's read already left one.
     */
    suspend fun mergeReads(ids: Collection<String>, since: Long, cnoSince: Long = since, reasons: Map<String, String> = emptyMap()): Merged {
        val now = clock()
        val wanted = ids.toHashSet()
        val both = HashSet<String>()
        val cnoOnly = HashSet<String>()
        val vigOnly = HashSet<String>()
        val neither = HashSet<String>()
        store.update { list ->
            both.clear(); cnoOnly.clear(); vigOnly.clear(); neither.clear()
            list.map { b ->
                if (b.id !in wanted || b.status != BetStatus.PENDING) return@map b
                val c = b.cnoFair?.takeIf { (b.cnoAtMs ?: Long.MIN_VALUE) >= cnoSince }
                val v = b.vigFair?.takeIf { (b.vigAtMs ?: Long.MIN_VALUE) >= since }
                // A close is as old as the older of the reads behind it ([seen]), and only a read made before the start is one.
                fun withFair(fair: Double, at: Long, via: String, books: Int?, seen: Long = at) = b.copy(
                    nowFair = fair, nowEv = fair / b.cost - 1.0, nowAtMs = at, nowVia = via, nowBooks = books ?: b.nowBooks,
                    nowNote = null, nowNoteAtMs = null,
                    closingFair = if (at < b.startsTs && ClosingLine.supersedes(b, seen)) fair else b.closingFair,
                    closingSeenAtMs = if (at < b.startsTs && ClosingLine.supersedes(b, seen)) seen else b.closingSeenAtMs,
                )
                when {
                    c != null && v != null -> {
                        both += b.id
                        withFair(
                            (c + v) / 2.0, maxOf(b.cnoAtMs!!, b.vigAtMs!!), VIA_BOTH, maxOf(b.nowBooks ?: 0, b.vigBooks ?: 0).takeIf { it > 0 },
                            seen = minOf(b.cnoAtMs, b.vigAtMs),
                        )
                    }
                    c != null -> { cnoOnly += b.id; b }
                    v != null -> { vigOnly += b.id; withFair(v, b.vigAtMs!!, VIA_VIGILANT, b.vigBooks) }
                    else -> {
                        neither += b.id
                        val cnoSaid = b.nowNote != null && (b.nowNoteAtMs ?: Long.MIN_VALUE) >= cnoSince
                        reasons[b.id]?.takeIf { !cnoSaid }?.let { b.copy(nowNote = it, nowNoteAtMs = now) } ?: b
                    }
                }
            }
        }
        return Merged(both.toSet(), cnoOnly.toSet(), vigOnly.toSet(), neither.toSet())
    }

    companion object {
        const val SOURCE_VIGILANT = "vigilant"
        const val SOURCE_CNO = "cno"

        /** ParlayAPI's own +EV list at Novig (its /best-bets, re-priced at Novig; PARLAY_API.md §6.5). */
        const val SOURCE_PARLAY = "parlay"

        /** [TrackedBet.nowVia]: whose fair line the current EV rests on. */
        const val VIA_CNO = "cno"
        const val VIA_VIGILANT = "vigilant"
        /** [TrackedBet.nowVia]: every book's price from ParlayAPI, judged with CNO's check ([ParlayBooks]), when CNO didn't have the bet. */
        const val VIA_PARLAY = "parlay"
        /** [TrackedBet.nowVia]: the average of CNO's read and Vigilant's own, both made in one check ([mergeReads]). */
        const val VIA_BOTH = "both"

        /** A scan re-writes an open bet's read only when its fair line moved or the last read is this old (the age on the card stays honest). */
        const val OBSERVE_REFRESH_MS = 60_000L

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
        fun stats(all: List<TrackedBet>, now: Long = System.currentTimeMillis()): TrackerStats {
            val money = all.filterNot { it.isOutlier }
            // A lock ([TrackedBet.isLock]) is real money, so it counts in the stakes and the profit; it isn't a pick, so the record, the EV and the closing
            // line leave it out (a locked pair would read as a win and a loss, and the lock's EV and CLV say nothing about the picks).
            val bets = money.filterNot { it.isLock }
            val decided = { b: TrackedBet -> b.status != BetStatus.PENDING && b.status != BetStatus.VOID }
            val settled = bets.filter(decided)
            val settledMoney = money.filter(decided)
            val staked = settledMoney.sumOf { it.stake }
            val profit = settledMoney.sumOf { it.profit ?: 0.0 }
            // A voided bet never happened: it counts toward nothing but the bet count.
            val live = bets.filter { it.status != BetStatus.VOID }
            val open = bets.filter { it.status == BetStatus.PENDING }
            // True closing lines only (Tj, 2026-09-29): read just before the start, final once it's started ([ClosingLine]).
            val withClv = live.mapNotNull { ClosingLine.clv(it, now) }
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
                outliers = all.size - money.size,
                voided = bets.count { it.status == BetStatus.VOID },
                openStaked = open.sumOf { it.stake },
                openToWin = open.sumOf { it.profitIfWon },
                openExpected = open.sumOf { it.expectedProfit },
                profitWithEv = judged.sumOf { it.profit ?: 0.0 },
                settledWithEv = judged.size,
                expectedSd = kotlin.math.sqrt(variance),
                profitAll = all.filter(decided).sumOf { it.profit ?: 0.0 },
                locks = money.count { it.isLock },
            )
        }
    }
}

/**
 * What a bet's fair odds were made of when it was placed (Tj's diagnostics 2026-09-30: Vigilant's own bets lose to the close while CNO's beat
 * it, and nothing said which fair odds were to blame): the method ("SHARP", "BLEND", "MARKET_AVERAGE" for Vigilant's own; "CNO" or
 * "ParlayAPI" for a list's), the sharp books in it, and how many books in all. Diagnostics splits closing-line value by it.
 */
@kotlinx.serialization.Serializable
data class FairBasis(val source: String, val sharp: List<String> = emptyList(), val books: Int = 0) {
    /** A short group name: "Pinnacle-anchored", "exchange only (Kalshi)", "books' average", "CNO". */
    val group: String get() = when {
        source == SOURCE_CNO || source == SOURCE_PARLAY -> source
        "pinnacle" in sharp -> "Pinnacle in the fair"
        sharp.isNotEmpty() -> "exchange sharp only (${sharp.joinToString("+") { com.tjshea.vigilant.data.reference.TheOddsApiClient.bookTitle(it) }})"
        else -> "books' average only"
    } + (if (books == 1) ", one book" else "")

    companion object {
        const val SOURCE_CNO = "CNO"
        const val SOURCE_PARLAY = "ParlayAPI"

        /** [o]'s fair line as it was when bet; null when it has none. */
        fun of(o: com.tjshea.vigilant.data.scanner.Opportunity): FairBasis? = o.fair?.let { f ->
            FairBasis(f.sourceUsed.name, f.sharpBooksUsed.distinct(), f.booksUsed.size)
        }
    }
}
