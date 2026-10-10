package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Where a live bid is in its life. The first four are on Novig (or on their way); the rest are over. */
@Serializable
enum class LiveBidStatus(val label: String, val ended: Boolean) {
    /** The order is being sent: Novig has not answered yet (a live order can take seconds). */
    SENDING("Sending", false),

    /** Novig accepted it (`201`); not yet seen on the book. */
    SENT("Sent", false),
    RESTING("Resting", false),

    /** A cancel is on its way; a fill can still land first. */
    CANCELING("Being pulled", false),
    FILLED("Filled", true),
    CANCELED("Pulled", true),
    EXPIRED("Expired", true),
    REFUSED("Refused", true),

    /** Novig's answer never came and no list shows the order. */
    LOST("Not found on Novig", true),
}

/**
 * One live bid (a post-only order on a side of a live Novig line), real or paper, kept for the Tracker's numbers, Diagnostics and the next session. [fair] is Pinnacle's devigged price for the
 * side when it was decided on, [ev] the edge at it; [mid] Novig's own middle then ([LiveBidQuality.novigMovePull] pulls on it falling). The times are the phone's clock: [postedAtMs] the decision,
 * [ackedAtMs] Novig's `201`, [openAtMs] first seen resting, [cancelSentAtMs] the pull, [endedAtMs] the end; the gaps between them are what Diagnostics reports (how long a live order and a pull really
 * take). A fill is followed up at 30 s and 120 s: Pinnacle's fair then ([fairAt30], [fairAt120]); [pickedOff] says the fair 30 s later was under the price paid.
 */
@Serializable
data class LiveBid(
    val clientId: String,
    val orderId: String? = null,
    /** REAL or PAPER. */
    val mode: String,
    val marketId: String,
    val eventId: String,
    val outcomeId: String,
    val pinnEventId: Long = 0L,
    val league: String,
    val eventName: String,
    val startsTs: Long,
    val marketLabel: String,
    val selection: String,
    val price: Double,
    val contracts: Long,
    val fair: Double,
    val ev: Double,
    val mid: Double? = null,
    val bestBid: Double? = null,
    val offer: Double? = null,
    val leads: Boolean = false,
    val overround: Double = 0.0,
    val pinnLimit: Double? = null,
    val feeCoefficient: Double = 0.0,
    val makerCredit: Double = 0.0,
    val score: String? = null,
    val clock: String? = null,
    val rules: String = "",
    val preset: String? = null,
    val postedAtMs: Long,
    val ackedAtMs: Long? = null,
    val openAtMs: Long? = null,
    /** The moment the bid is expected to be on Novig's book and fillable (paper: the posting plus the delay a real order takes). */
    val activeFromMs: Long = postedAtMs,
    val expiresAtMs: Long,
    val status: LiveBidStatus = LiveBidStatus.SENDING,
    val filled: Long = 0L,
    val paid: Double = 0.0,
    val firstFillAtMs: Long? = null,
    /** At least one fill was strictly through the price (certain), not just at it. */
    val strict: Boolean = false,
    val cancelSentAtMs: Long? = null,
    val endedAtMs: Long? = null,
    /** Why it was pulled or ended, in words. */
    val why: String? = null,
    val betId: String? = null,
    val fairAt30: Double? = null,
    val fairAt120: Double? = null,
    val pickedOff: Boolean? = null,
    /** A pull arrived while the order was still being sent: it is cancelled the moment Novig answers. */
    val cancelWanted: Boolean = false,
    /** Nothing more is expected but the order may still be on Novig's book (a lost answer): looked for by its client id. */
    val lostAtMs: Long? = null,
) {
    val active: Boolean get() = !status.ended
    val real: Boolean get() = mode == MODE_REAL

    /** Contracts still resting. */
    val remaining: Long get() = (contracts - filled).coerceAtLeast(0L)

    /** Dollars the unfilled part would cost if it filled. */
    val restingDollars: Double get() = remaining * price * EvMath.CONTRACT_PAYOUT_DOLLARS
    val cost: Double get() = contracts * price * EvMath.CONTRACT_PAYOUT_DOLLARS

    /** How long the order took to be accepted, and to appear on the book. */
    val ackMs: Long? get() = ackedAtMs?.let { it - postedAtMs }
    val openMs: Long? get() = openAtMs?.let { it - postedAtMs }

    /** How long the pull took, from the cancel sent to the order gone. */
    val pullMs: Long? get() = if (cancelSentAtMs != null && endedAtMs != null && status == LiveBidStatus.CANCELED) endedAtMs - cancelSentAtMs else null

    /** The EV the bid kept at +30 s / +120 s (the fair then against the price, no credit); null before the follow-up. */
    fun evAt(fairLater: Double?): Double? = fairLater?.let { it / price - 1.0 }

    /** What the Tracker needs to record this bid's fills as a bet. */
    fun target(version: String? = null): BetTarget = BetTarget(
        market = NovigMarket(marketId, eventId, "", "OPEN", marketLabel, startsTs, null, emptyList()),
        outcomeId = outcomeId, league = league, eventName = eventName, startsTs = startsTs, marketLabel = marketLabel, selection = selection,
        fair = fair, fairAsOfMs = postedAtMs, source = BetTracker.SOURCE_LIVEBID,
        basis = FairBasis(FairBasis.SOURCE_PINNODDS, listOf("Pinnacle"), 1), auto = true,
        atBet = AtBet(
            atMs = firstFillAtMs ?: postedAtMs, version = version, how = AtBet.HOW_BID, scanner = "Live bids", preset = preset, rules = rules, league = league,
            sport = SharpVeto.sportOf(league).name, kind = when (marketLabel) { "Moneyline" -> BetKind.MONEYLINE; "Spread" -> BetKind.SPREAD; "Total" -> BetKind.TOTAL; else -> BetKind.OTHER }.name, live = true, american = Odds.probabilityToAmerican(price.coerceIn(0.001, 0.999)), ev = ev, fair = fair,
            fairAmerican = Odds.probabilityToAmerican(fair.coerceIn(0.001, 0.999)), fairMethod = "Pinnacle live, devigged", fairBooks = listOf("Pinnacle"), fairSharp = listOf("Pinnacle"),
            stake = paid.takeIf { it > 0.0 } ?: cost, novigAgeSec = null,
        ),
    )

    companion object {
        const val MODE_REAL = "REAL"
        const val MODE_PAPER = "PAPER"
    }
}

/** Every live bid, newest last; ended ones are kept [KEEP_MS] for the numbers. */
class LiveBidStore(file: File) {
    private val store = JsonFileStore(file, ListSerializer(LiveBid.serializer()), { emptyList() })
    suspend fun all(): List<LiveBid> = store.read()
    suspend fun replace(list: List<LiveBid>) = store.update { list }

    companion object {
        const val KEEP_MS = 14 * 24 * 3_600_000L
    }
}

/** One thing the live bid engine did, for the day journal (research: how often bids go up, what pulls them, what fills): not the record of a bid, which is [LiveBid]. */
@Serializable
data class LiveBidEvent(
    val atMs: Long,
    /** POST, PULL, FILL, END, FOLLOW, HALT, STAND_DOWN, TIMING, SKIP. */
    val type: String,
    val bidId: String? = null,
    val mode: String? = null,
    val league: String? = null,
    val event: String? = null,
    val selection: String? = null,
    val price: Double? = null,
    val fair: Double? = null,
    val value: Double? = null,
    val text: String? = null,
)
