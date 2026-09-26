package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

@Serializable
enum class BetStatus { PENDING, WON, LOST, PUSH, VOID }

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
    val fairAtBet: Double,
    val evPercentAtBet: Double,
    val stake: Double,
    val status: BetStatus = BetStatus.PENDING,
    val settledAtMs: Long? = null,
    val closingFair: Double? = null,
    val closingSeenAtMs: Long? = null,
) {
    /** What a win pays back in profit: every $1 of cost returns $1 / cost. */
    val profitIfWon: Double get() = stake * (1.0 / cost - 1.0)

    val profit: Double?
        get() = when (status) {
            BetStatus.WON -> profitIfWon
            BetStatus.LOST -> -stake
            BetStatus.PUSH, BetStatus.VOID -> 0.0
            BetStatus.PENDING -> null
        }

    val expectedProfit: Double get() = stake * evPercentAtBet

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

    suspend fun track(o: Opportunity, stake: Double): TrackedBet? {
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
        )
        store.update { it + bet }
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
                averageEv = live.takeIf { it.isNotEmpty() }?.map { it.evPercentAtBet }?.average(),
                averageClv = withClv.takeIf { it.isNotEmpty() }?.average(),
                beatClosePercent = withClv.takeIf { it.isNotEmpty() }?.let { l -> l.count { it > 0 }.toDouble() / l.size },
            )
        }
    }
}
