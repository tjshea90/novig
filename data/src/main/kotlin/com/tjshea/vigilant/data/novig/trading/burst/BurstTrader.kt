package com.tjshea.vigilant.data.novig.trading.burst

import com.tjshea.vigilant.data.novig.burst.Cover
import com.tjshea.vigilant.data.novig.burst.WindowOpening
import com.tjshea.vigilant.data.novig.burst.WindowSink
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Fees
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** The fixed limits of the burst trader (RESEARCH.md §95): Tj's own caps are [BurstTradeRules]; these protect him from the code. */
object BurstTradeLimits {
    /** The least a cover must pay, per $1 of payout, after both fees, to be worth an order (the recorder's window floor is 0.3 cent). */
    const val MIN_NET = 0.01

    /** A window older than this when the trader gets to it is not acted on: the books have moved on. */
    const val MAX_AGE_MS = 250L

    /** The fewest contracts a cover is worth ordering (20 = 20 cents of payout). */
    const val MIN_CONTRACTS = 20L

    /** How long a placed order may take to end (an `IOC` ends at once); past it nothing is assumed and the trader halts. */
    const val ORDER_WAIT_MS = 2_500L
    const val POLL_MS = 120L

    /** Two tries on one game's ladder at least this far apart (a window flickers; one attempt per moment). */
    const val COOLDOWN_MS = 2_000L

    /** A market Novig refused to trade in play (`NOT_LIVE_TRADABLE`, price band, closed ...) is left alone this long. */
    const val BLACKLIST_MS = 10 * 60_000L

    /** A 451 / 423 (Novig judges the network or the account) stops all trading this long. */
    const val STAND_DOWN_MS = 10 * 60_000L

    /** Two covers in a row that end with a leg held alone halt the trader until Tj resumes it. */
    const val NAKED_IN_A_ROW = 2

    /** Novig's refusals that are about ONE market (or game), not the account. */
    val MARKET_CODES = setOf("NOT_LIVE_TRADABLE", "EVENT_NOT_TRADABLE", "MARKET_CLOSED", "MARKET_INACTIVE", "PRICE_BAND_VIOLATION", "ORDER_TOO_SMALL", "ORDER_TOO_LARGE", "MARKET_LOCKED", "EVENT_LOCKED")
}

/**
 * Tj's own limits for the real-money burst trader (Settings), as the trader reads them each time: [stakePerLeg] dollars one leg may cost, [maxPerGame] and [maxPerDay] dollars spent (both legs
 * of every attempt, fees in), [haltLoss] dollars of legs held alone (naked) that halt it, and [halted] (why it stopped; null = running; Tj's Resume clears it).
 */
data class BurstTradeRules(
    val enabled: Boolean,
    val stakePerLeg: Double,
    val maxPerGame: Double,
    val maxPerDay: Double,
    val haltLoss: Double,
    val halted: String? = null,
    val minNet: Double = BurstTradeLimits.MIN_NET,
)

/** A resting bid of Vigilant's own (the bid desk's): an order that buys the other side could trade against it (a wash), so the trader keeps clear of it. */
data class OwnBid(val marketId: String, val outcomeId: String, val price: Double)

/** What the trader needs from Novig: the batch of orders, an order's state, its fills. The app's is [NovigTradingClient]'s; tests fake it. */
interface BurstOrders {
    /** One request, all or nothing: the order id by client id. A [NovigApiException] = none placed; any other failure = a lost answer. */
    suspend fun placeBatch(orders: List<NovigTradingClient.NewOrder>): Map<String, String>
    suspend fun order(orderId: String): NovigOrder?
    suspend fun fills(orderId: String): List<NovigFill>
}

/** One attempt, as journaled. [outcome]: LOCKED (both legs equal), PARTIAL (some locked, some held alone), NAKED (one leg only), NONE (nothing filled), REFUSED, UNCONFIRMED. */
@Serializable
data class TradeRecord(
    val atMs: Long,
    val league: String,
    val eventId: String,
    val event: String,
    val pair: String,
    val contracts: Long,
    val yesLimit: Double,
    val noLimit: Double,
    val netAtSend: Double,
    val decisionMs: Long,
    val outcome: String,
    val yesFilled: Long,
    val noFilled: Long,
    val paid: Double,
    val fees: Double,
    val hedgeContracts: Long,
    val lockedContracts: Long,
    val lockedProfit: Double,
    val nakedContracts: Long,
    val nakedCost: Double,
    val message: String,
)

/** Every attempt, appended one JSON line a day, never rewritten (like the recorder's journal). */
class BurstTradeJournal(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun append(r: TradeRecord) {
        dir.mkdirs()
        val f = File(dir, "burst-trades-${java.time.Instant.ofEpochMilli(r.atMs).atZone(java.time.ZoneId.of("America/New_York")).toLocalDate()}.jsonl")
        val line = (if (f.exists() && f.length() > 0 && !endsWithNewline(f)) "\n" else "") + json.encodeToString(TradeRecord.serializer(), r) + "\n"
        FileOutputStream(f, true).use { out -> out.write(line.toByteArray()); out.fd.sync() }
    }

    fun readAll(): List<TradeRecord> = (dir.listFiles { f -> f.isFile && f.name.startsWith("burst-trades-") }?.sortedBy { it.name }.orEmpty()).flatMap { f ->
        f.useLines { ls -> ls.filter { it.isNotBlank() }.mapNotNull { runCatching { json.decodeFromString(TradeRecord.serializer(), it) }.getOrNull() }.toList() }
    }

    private fun endsWithNewline(f: File): Boolean = java.io.RandomAccessFile(f, "r").use { r -> r.seek(r.length() - 1); r.read() == '\n'.code }
}

data class BurstTradeStatus(
    val attempts: Int = 0,
    val locked: Int = 0,
    val partial: Int = 0,
    val naked: Int = 0,
    val none: Int = 0,
    val refused: Int = 0,
    val lockedProfit: Double = 0.0,
    val nakedCost: Double = 0.0,
    val skipped: Map<String, Int> = emptyMap(),
    val standDownUntilMs: Long? = null,
    val last: String? = null,
)

/**
 * The real-money half of the score-burst idea (Tj, 2026-10-06: "make it good enough so that if it is proven I can just turn it on for actual money betting"; RESEARCH.md §95). OFF unless
 * [BurstTradeRules.enabled]; the app turns that on only while the recorder's verdict says it has proved itself on this phone ([gate]).
 *
 * For each window the recorder reports ([WindowSink]) it: checks the rules and the gate; refuses a window older than [BurstTradeLimits.MAX_AGE_MS], a ladder tried in the last
 * [BurstTradeLimits.COOLDOWN_MS], a market Novig refused lately, and a pair that no longer pays [BurstTradeRules.minNet] on the live books; keeps clear of Vigilant's own resting
 * bids (a wash); sizes the cover to the stake per leg, to what is on offer and to the per-game and per-day caps; sends BOTH legs as ONE batch of two `IOC` orders at the seen asks
 * (they never rest; the fills are independent); reads the fills back; buys a leg that filled alone at the break-even price (one try); and records everything. A leg still held alone is a naked
 * bet: it is counted, and [BurstTradeLimits.NAKED_IN_A_ROW] in a row, or [BurstTradeRules.haltLoss] dollars of them, halt the trader until Tj resumes it. So does an answer that never came
 * ([onHalt]: "nothing is assumed", as for every other order of the app). A 451 or 423 stops it for a while; a market Novig refuses is left alone.
 *
 * One attempt at a time. It places no resting orders and cancels nothing: an `IOC` order ends by itself.
 */
class BurstTrader(
    private val orders: BurstOrders,
    private val scope: CoroutineScope,
    private val rules: () -> BurstTradeRules,
    /** Why trading must not happen now (STOP ALL, paused, no betting key, no wallet, the proof lapsed, no measured delays ...), or null. */
    private val gate: suspend () -> String?,
    private val ownBids: () -> List<OwnBid>,
    private val journal: BurstTradeJournal,
    private val onHalt: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dayStart: (Long) -> Long = { ApiBetPlacer.localMidnight(it) },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val ioContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
    private val newClientId: () -> String = { NovigTradingClient.newClientId() },
) : WindowSink {
    private val _status = MutableStateFlow(BurstTradeStatus())
    val status: StateFlow<BurstTradeStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private val lastTry = HashMap<String, Long>()
    private val blacklist = HashMap<String, Long>()
    private val history = ArrayList<TradeRecord>().also { it.addAll(runCatching { journal.readAll() }.getOrDefault(emptyList())) }
    private var nakedStreak = 0
    private var standDownUntil = 0L

    @Volatile
    private var haltedAtMs = 0L

    override fun onOpen(w: WindowOpening) {
        if (!rules().enabled) return
        scope.launch { attempt(w) }
    }

    private fun skip(why: String) {
        _status.value = _status.value.let { it.copy(skipped = it.skipped + (why to (it.skipped[why] ?: 0) + 1)) }
    }

    private suspend fun attempt(w: WindowOpening) {
        val r = rules()
        if (!r.enabled) return
        if (r.halted != null || clock() - haltedAtMs < HALT_GRACE_MS) return skip("halted")
        val start = clock()
        if (start < standDownUntil) return skip("stood down")
        if (start - w.openedMs > BurstTradeLimits.MAX_AGE_MS) return skip("window too old")
        gate()?.let { return skip(it) }
        val ladder = w.first.lo.ladderKey
        if (start - (lastTry[ladder] ?: 0L) < BurstTradeLimits.COOLDOWN_MS) return skip("cooldown")
        val barred = listOf(w.first.lo.marketId, w.first.hi.marketId).any { (blacklist[it] ?: 0L) > start }
        if (barred) return skip("market refused lately")
        if (!mutex.tryLock()) return skip("busy")
        try {
            lastTry[ladder] = start
            val c = w.current() ?: return skip("no longer pays")
            if (c.net < r.minNet) return skip("net under the minimum")
            if (clashesWithOwnBid(c)) return skip("own bid in the way")
            val q = size(c, r, w.eventId, start) ?: return skip("limit reached")
            withContext(NonCancellable) { trade(w, c, q, start) }
        } finally {
            mutex.unlock()
        }
    }

    /** Contracts per leg: the stake per leg, what is on offer on both legs, and what the game's and the day's caps leave (both legs, fees not yet in: a margin of the fee is kept). */
    private fun size(c: Cover, r: BurstTradeRules, eventId: String, now: Long): Long? {
        val payout = EvMath.CONTRACT_PAYOUT_DOLLARS
        val perContract = (c.yes.price + c.no.price + FEE_MARGIN) * payout
        val today = history.filter { it.atMs >= dayStart(now) }
        val dayLeft = r.maxPerDay - today.sumOf { it.paid + it.fees }
        val gameLeft = r.maxPerGame - today.filter { it.eventId == eventId }.sumOf { it.paid + it.fees }
        val q = minOf(
            c.contracts,
            Math.floor(r.stakePerLeg / (c.yes.price * payout)).toLong(),
            Math.floor(r.stakePerLeg / (c.no.price * payout)).toLong(),
            Math.floor(dayLeft / perContract).toLong(),
            Math.floor(gameLeft / perContract).toLong(),
        )
        return q.takeIf { it >= BurstTradeLimits.MIN_CONTRACTS }
    }

    private fun clashesWithOwnBid(c: Cover): Boolean {
        val bids = ownBids()
        fun clash(marketId: String, buys: String, limit: Double) = bids.any { it.marketId == marketId && it.outcomeId != buys && it.price >= 1.0 - limit - 1e-9 }
        return clash(c.lo.marketId, c.lo.yesOutcomeId, c.yes.price) || clash(c.hi.marketId, c.hi.noOutcomeId, c.no.price)
    }

    private suspend fun trade(w: WindowOpening, c: Cover, q: Long, decidedAtMs: Long) {
        val first = NovigTradingClient.NewOrder(c.lo.yesOutcomeId, c.yes.price, q, "IOC", newClientId())
        val second = NovigTradingClient.NewOrder(c.hi.noOutcomeId, c.no.price, q, "IOC", newClientId())
        val pair = "${c.lo.label} YES / ${c.hi.label} NOT"
        fun base(outcome: String, msg: String, yesF: Long = 0, noF: Long = 0, paid: Double = 0.0, fees: Double = 0.0, hedge: Long = 0, lockedC: Long = 0, lockedP: Double = 0.0, nakedC: Long = 0, nakedCost: Double = 0.0) =
            TradeRecord(decidedAtMs, w.league, w.eventId, w.event, pair, q, c.yes.price, c.no.price, c.net, decidedAtMs - w.openedMs, outcome, yesF, noF, paid, fees, hedge, lockedC, lockedP, nakedC, nakedCost, msg)

        val ids: Map<String, String> = try {
            orders.placeBatch(listOf(first, second))
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            return refused(base("REFUSED", e.advice), e, c)
        } catch (e: Exception) {
            return halt(base("UNCONFIRMED", "Novig's answer to the order never came (${e.message ?: e.javaClass.simpleName}): nothing is assumed."), "an order's answer was lost: check Novig and the Tracker (Sync with Novig), then Resume")
        }
        val yesId = ids[first.clientId]
        val noId = ids[second.clientId]
        if (yesId == null || noId == null) return halt(base("UNCONFIRMED", "Novig took the batch but named only part of it."), "an order's answer was incomplete: check Novig and the Tracker, then Resume")
        val yes = settle(yesId) ?: return halt(base("UNCONFIRMED", "A leg's order had not ended after ${BurstTradeLimits.ORDER_WAIT_MS} ms."), "an order had not ended: check Novig and the Tracker, then Resume")
        val no = settle(noId) ?: return halt(base("UNCONFIRMED", "A leg's order had not ended after ${BurstTradeLimits.ORDER_WAIT_MS} ms."), "an order had not ended: check Novig and the Tracker, then Resume")
        var yesFilled = yes.qty
        var noFilled = no.qty
        var paidYes = yes.cost
        var paidNo = no.cost
        var feeYes = yes.fee
        var feeNo = no.fee
        var hedged = 0L
        var message = "both legs sent at once"
        // A leg that filled alone: buy the other at the break-even price, once.
        if (yesFilled != noFilled && (yesFilled > 0 || noFilled > 0)) {
            val surplus = Math.abs(yesFilled - noFilled)
            val heldYes = yesFilled > noFilled
            val heldPrice = if (heldYes) paidYes / (yesFilled * EvMath.CONTRACT_PAYOUT_DOLLARS) else paidNo / (noFilled * EvMath.CONTRACT_PAYOUT_DOLLARS)
            val limit = breakEven(heldPrice, c, heldYes)
            if (limit != null) {
                val outcome = if (heldYes) c.hi.noOutcomeId else c.lo.yesOutcomeId
                val order = NovigTradingClient.NewOrder(outcome, limit, surplus, "IOC", newClientId())
                try {
                    val id = orders.placeBatch(listOf(order))[order.clientId]
                    val h = id?.let { settle(it) }
                    if (h == null) {
                        return halt(base("UNCONFIRMED", "The hedge order had not ended."), "a hedge order had not ended: check Novig and the Tracker, then Resume")
                    }
                    hedged = h.qty
                    if (heldYes) { noFilled += h.qty; paidNo += h.cost; feeNo += h.fee } else { yesFilled += h.qty; paidYes += h.cost; feeYes += h.fee }
                    message = "a leg filled alone: bought the other at up to ${"%.3f".format(Locale.US, limit)} (${h.qty} of $surplus)"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NovigApiException) {
                    message = "a leg filled alone and Novig refused the hedge (${e.code ?: e.status})"
                } catch (e: Exception) {
                    return halt(base("UNCONFIRMED", "The hedge order's answer never came (${e.message ?: e.javaClass.simpleName})."), "a hedge order's answer was lost: check Novig and the Tracker, then Resume")
                }
            } else {
                message = "a leg filled alone and no price could still make the cover pay"
            }
        }
        val locked = minOf(yesFilled, noFilled)
        val payout = EvMath.CONTRACT_PAYOUT_DOLLARS
        val lockedProfit = if (locked > 0) {
            val yShare = locked.toDouble() / yesFilled
            val nShare = locked.toDouble() / noFilled
            locked * payout - paidYes * yShare - paidNo * nShare - feeYes * yShare - feeNo * nShare
        } else 0.0
        val naked = Math.abs(yesFilled - noFilled)
        val nakedCost = if (naked > 0) (if (yesFilled > noFilled) (paidYes + feeYes) * naked / yesFilled else (paidNo + feeNo) * naked / noFilled) else 0.0
        val outcome = when {
            yesFilled == 0L && noFilled == 0L -> "NONE"
            naked == 0L -> "LOCKED"
            locked > 0 -> "PARTIAL"
            else -> "NAKED"
        }
        finish(base(outcome, message, yesFilled, noFilled, paidYes + paidNo, feeYes + feeNo, hedged, locked, lockedProfit, naked, nakedCost))
    }

    private class Settled(val qty: Long, val cost: Double, val fee: Double)

    /** Waits for [orderId] to end, then reads what filled; null when it has not ended in time. */
    private suspend fun settle(orderId: String): Settled? {
        val started = clock()
        var o: NovigOrder? = null
        while (clock() - started < BurstTradeLimits.ORDER_WAIT_MS) {
            o = try { orders.order(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (o?.terminal == true) break
            pause(BurstTradeLimits.POLL_MS)
        }
        if (o?.terminal != true) return null
        val fills = try { orders.fills(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        if (fills.isNotEmpty()) return Settled(fills.sumOf { it.qty }, fills.sumOf { it.cost }, fills.sumOf { it.fee })
        // The order says what filled; its fills have not shown: its own numbers, at its limit price (a ceiling), fee from the model.
        val filled = (o.qty - o.remaining).coerceAtLeast(0L)
        return Settled(filled, filled * o.price * EvMath.CONTRACT_PAYOUT_DOLLARS, filled * Fees.takerFee(o.price.coerceIn(0.001, 0.999), com.tjshea.vigilant.engine.MarketFee.GAME, true) * EvMath.CONTRACT_PAYOUT_DOLLARS)
    }

    /** The dearest price (thousandths) at which buying the other leg still leaves the cover paying at least nothing after both fees, given the leg held at [heldPrice]; null when none. */
    internal fun breakEven(heldPrice: Double, c: Cover, heldYes: Boolean): Double? {
        val heldFee = Fees.takerFee(heldPrice.coerceIn(0.001, 0.999), if (heldYes) c.lo.fee else c.hi.fee, true)
        val otherFee = if (heldYes) c.hi.fee else c.lo.fee
        var milli = 990
        while (milli >= 10) {
            val p = milli / 1000.0
            if (1.0 - heldPrice - p - heldFee - Fees.takerFee(p, otherFee, true) >= 0.0) return p
            milli--
        }
        return null
    }

    private fun refused(r: TradeRecord, e: NovigApiException, c: Cover) {
        val now = clock()
        when {
            e.status == 451 || e.status == 423 && e.code !in BurstTradeLimits.MARKET_CODES -> standDownUntil = now + BurstTradeLimits.STAND_DOWN_MS
            e.code in BurstTradeLimits.MARKET_CODES -> {
                blacklist[c.lo.marketId] = now + BurstTradeLimits.BLACKLIST_MS
                blacklist[c.hi.marketId] = now + BurstTradeLimits.BLACKLIST_MS
            }
        }
        record(r.copy(message = "Novig refused the orders (${e.code ?: e.status}): ${e.advice}"))
        _status.value = _status.value.copy(refused = _status.value.refused + 1, standDownUntilMs = standDownUntil.takeIf { it > now })
    }

    private fun halt(r: TradeRecord, why: String) {
        haltedAtMs = clock()
        record(r)
        onHalt(why)
        _status.value = _status.value.copy(last = "HALTED: $why")
    }

    private fun finish(r: TradeRecord) {
        record(r)
        var s = _status.value
        s = s.copy(attempts = s.attempts + 1, lockedProfit = s.lockedProfit + r.lockedProfit, nakedCost = s.nakedCost + r.nakedCost, last = "${r.outcome} · ${r.pair} · ${r.lockedContracts} locked, ${r.nakedContracts} held alone")
        s = when (r.outcome) {
            "LOCKED" -> s.copy(locked = s.locked + 1)
            "PARTIAL" -> s.copy(partial = s.partial + 1)
            "NAKED" -> s.copy(naked = s.naked + 1)
            else -> s.copy(none = s.none + 1)
        }
        _status.value = s
        if (r.nakedContracts > 0) nakedStreak++ else if (r.outcome == "LOCKED") nakedStreak = 0
        val nakedToday = history.filter { it.atMs >= dayStart(r.atMs) }.sumOf { it.nakedCost }
        val limit = rules().haltLoss
        when {
            nakedStreak >= BurstTradeLimits.NAKED_IN_A_ROW -> halt0("$nakedStreak covers in a row ended with a leg held alone")
            limit > 0.0 && nakedToday >= limit -> halt0("legs held alone today cost ${"$"}${"%.2f".format(Locale.US, nakedToday)}, over your ${"$"}${"%.2f".format(Locale.US, limit)} limit")
        }
    }

    private fun halt0(why: String) {
        haltedAtMs = clock()
        onHalt("$why: Resume it in Settings once you have looked at the Tracker")
        _status.value = _status.value.copy(last = "HALTED: $why")
    }

    private fun record(r: TradeRecord) {
        history += r
        try { journal.append(r) } catch (e: Exception) { _status.value = _status.value.copy(last = "the trade journal could not be written: ${e.message}") }
    }

    /** Resume after a halt: the streak starts over (the settings' halted line is cleared by the caller). */
    fun resumed() {
        nakedStreak = 0
        haltedAtMs = 0L
    }

    companion object {
        /** A halt is honoured this long even before the settings say so (the setting is written a moment later). */
        const val HALT_GRACE_MS = 5_000L

        /** Dollars per contract-set the caps keep back for the fees. */
        const val FEE_MARGIN = 0.02
    }
}
