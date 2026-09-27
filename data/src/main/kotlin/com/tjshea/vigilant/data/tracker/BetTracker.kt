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
) {
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

    /** Closing-line value: the EV this price had against the closing fair line. */
    val clvPercent: Double? get() = closingFair?.let { it / cost - 1.0 }
}

data class TrackerStats(
    val bets: Int,
    val pending: Int,
    val settled: Int,
    val staked: Double,
    val profit: Double,
    val roi: Double?,
    val expectedProfit: Double,
    val averageEv: Double?,
    val averageClv: Double?,
    val beatClosePercent: Double?,
)

/** Tracked bets, persisted in one JSON file (see [JsonFileStore] for the crash-safety rules). */
class BetTracker(file: File, private val clock: () -> Long = System::currentTimeMillis) {

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
        store.update { list -> list.filterNot { it.placedKey == placedKey && it.status == BetStatus.PENDING } + bet }
        return bet
    }

    /** Undo, or "not placed after all": the open bet that ✓ logged goes. Settled ones stay. */
    suspend fun untrack(placedKey: String) {
        store.update { list -> if (list.none { it.placedKey == placedKey }) list else list.filterNot { it.placedKey == placedKey && it.status == BetStatus.PENDING } }
    }

    /** Changes one bet (stake, a recheck, a settlement): [transform] gets the stored bet. */
    suspend fun edit(id: String, transform: (TrackedBet) -> TrackedBet) {
        store.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    suspend fun setStake(id: String, stake: Double) = edit(id) { it.copy(stake = stake) }

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
                TrackedBet(
                    id = UUID.randomUUID().toString(),
                    createdAtMs = m.placedAtMs,
                    league = "",
                    eventName = parts.getOrNull(1).orEmpty(),
                    startsTs = m.startsAtMs ?: m.placedAtMs,
                    marketLabel = parts.getOrNull(0).orEmpty(),
                    selection = m.title,
                    marketId = "",
                    outcomeId = "",
                    price = price,
                    cost = price,
                    fairAtBet = null,
                    evPercentAtBet = null,
                    stake = DEFAULT_STAKE,
                    source = if (m.key.startsWith("cno:")) SOURCE_CNO else SOURCE_VIGILANT,
                    placedKey = m.key,
                    american = american,
                    book = parts.getOrNull(2) ?: "Novig",
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
        )
        store.update { list -> (if (placedKey == null) list else list.filterNot { it.placedKey == placedKey && it.status == BetStatus.PENDING }) + bet }
        return bet
    }

    suspend fun settle(id: String, status: BetStatus) {
        store.update { list ->
            list.map { if (it.id == id) it.copy(status = status, settledAtMs = if (status == BetStatus.PENDING) null else clock()) else it }
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
        store.update { list -> list.map { b -> fresh(b)?.let { b.copy(closingFair = it, closingSeenAtMs = now) } ?: b } }
        return true
    }

    companion object {
        const val SOURCE_VIGILANT = "vigilant"
        const val SOURCE_CNO = "cno"

        /** A ✓ logs a $1 bet: Novig's API can't read the app's own bets (NOVIG_API.md: subaccounts only). */
        const val DEFAULT_STAKE = 1.0

        fun stats(bets: List<TrackedBet>): TrackerStats {
            val settled = bets.filter { it.status != BetStatus.PENDING && it.status != BetStatus.VOID }
            val staked = settled.sumOf { it.stake }
            val profit = settled.sumOf { it.profit ?: 0.0 }
            // A voided bet never happened: it counts toward nothing but the bet count.
            val live = bets.filter { it.status != BetStatus.VOID }
            val withClv = live.mapNotNull { it.clvPercent }
            return TrackerStats(
                bets = bets.size,
                pending = bets.count { it.status == BetStatus.PENDING },
                settled = settled.size,
                staked = staked,
                profit = profit,
                roi = if (staked > 0) profit / staked else null,
                expectedProfit = live.sumOf { it.expectedProfit },
                averageEv = live.mapNotNull { it.evPercentAtBet }.takeIf { it.isNotEmpty() }?.average(),
                averageClv = withClv.takeIf { it.isNotEmpty() }?.average(),
                beatClosePercent = withClv.takeIf { it.isNotEmpty() }?.let { l -> l.count { it > 0 }.toDouble() / l.size },
            )
        }
    }
}
