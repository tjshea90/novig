package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.pinnodds.LiveEdge
import com.tjshea.vigilant.data.pinnodds.LiveOrders
import com.tjshea.vigilant.data.pinnodds.LiveOwnBid
import com.tjshea.vigilant.data.pinnodds.LiveRecord
import com.tjshea.vigilant.data.pinnodds.LiveTradeLimits
import com.tjshea.vigilant.data.pinnodds.LiveTradeRules
import com.tjshea.vigilant.data.pinnodds.LiveTradeStatus
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Fees
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Locale

/** A tail bet the lab found, with what the trader needs to place it and the Tracker to record it. [state] is the game as the model saw it ("42-38, 12% left, centre 53.1"). */
data class TailOffer(
    val candidate: TailCandidate,
    val target: BetTarget,
    val fee: MarketFee,
    val state: String,
    val decidedAtMs: Long,
)

/** The fixed guards of the tail trader (Tj's own caps are the [LiveTradeRules] it is given): these protect him from the model. */
object TailTradeLimits {
    /** The model's fair for the bet must be at least this: a decided game, not a lean. */
    const val MIN_FAIR = 0.92

    /** An edge this large is the model or the game state being wrong, not a gift: refused. */
    const val MAX_EDGE = 0.30

    /** The same outcome is not tried again within this long. */
    const val COOLDOWN_MS = 60_000L

    /** The fewest contracts worth an order. */
    const val MIN_CONTRACTS = 20L

    /** The default smallest edge, after Novig's fee and the model's own safety margins (3% is the lab's floor; real money asks for more). */
    const val DEFAULT_MIN_EDGE = 0.05
}

/**
 * The real-money tail taker (Tj, 2026-10-10: "I want to live bet today"; RESEARCH.md §125, TG): buys, immediate-or-cancel, a far strike the late-game model ([TailScan] with the CONSERVATIVE rules:
 * spread widened 25%, centre moved 1.5 points against the bet, fair at least 90%) says the game has all but decided, when Novig still offers it under that fair by the minimum edge after its fee.
 * It needs NO outside price: the game state is ESPN's scoreboard and the centre is read off Novig's own liquid lines, so it keeps working when the Pinnodds trial ends.
 *
 * The same shape and the same stops as [com.tjshea.vigilant.data.pinnodds.PinnLiveTrader]: ONE order at a time under the app's order lock; the limit climbs a grid step at a time only while the
 * edge stays at the minimum ([LiveEdge.reachPrice]); fills are the truth and go to the Tracker; an answer that never came halts it; a day's loss past Tj's limit halts it; a 451/423 stands it
 * down; a market Novig refuses is left alone; its own resting bids are kept clear of. PAPER (bet off) decides and journals but sends nothing. Its own guards on top: the model's fair at least
 * [TailTradeLimits.MIN_FAIR], the edge no more than [TailTradeLimits.MAX_EDGE], the CONSERVATIVE rules only (the EXPLORE rules are for the paper record), a minute between tries on one outcome.
 */
class TailTaker(
    private val orders: LiveOrders,
    private val scope: CoroutineScope,
    private val rules: () -> LiveTradeRules,
    private val minEdge: () -> Double,
    private val gate: suspend () -> String?,
    private val ownBids: () -> List<LiveOwnBid>,
    private val journal: DayJournal<LiveRecord>,
    private val onHalt: (String) -> Unit,
    private val logFills: suspend (BetTarget, String, List<NovigFill>) -> Boolean,
    private val lossToday: suspend () -> Double = { 0.0 },
    private val clock: () -> Long = System::currentTimeMillis,
    private val dayStart: (Long) -> Long = { ApiBetPlacer.localMidnight(it) },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val newClientId: () -> String = { NovigTradingClient.newClientId() },
    private val lock: Mutex? = null,
) {
    private val _status = MutableStateFlow(LiveTradeStatus())
    val status: StateFlow<LiveTradeStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private val lastTry = HashMap<String, Long>()
    private val blacklist = HashMap<String, Long>()
    private val history = ArrayList<LiveRecord>().also { it.addAll(runCatching { journal.readAll() }.getOrDefault(emptyList())) }
    private var standDownUntil = 0L

    @Volatile
    private var haltedAtMs = 0L

    /** Hands the offer over; returns at once. The attempt runs on its own coroutine and counts why it did not trade. */
    fun offer(o: TailOffer, onRecord: (LiveRecord) -> Unit = {}) {
        if (!rules().enabled) return
        scope.launch { attempt(o)?.let(onRecord) }
    }

    private fun skip(why: String): LiveRecord? {
        _status.value = _status.value.let { it.copy(skipped = it.skipped + (why to (it.skipped[why] ?: 0) + 1)) }
        return null
    }

    internal suspend fun attempt(o: TailOffer): LiveRecord? {
        val r = rules()
        if (!r.enabled) return null
        val c = o.candidate
        val start = clock()
        if (r.halted != null || start - haltedAtMs < LiveTradeLimits.HALT_GRACE_MS) return skip("halted")
        if (r.bet && start < standDownUntil) return skip("stood down")
        if (c.rule != CONSERVATIVE) return skip("not the conservative rules")
        if (c.fair < TailTradeLimits.MIN_FAIR) return skip("fair under ${(TailTradeLimits.MIN_FAIR * 100).toInt()}%")
        val floor = maxOf(minEdge(), 0.0)
        if (c.edge < floor) return skip("edge under your minimum")
        if (c.edge > TailTradeLimits.MAX_EDGE) return skip("edge too large to be real")
        if (r.bet) gate()?.let { return skip(it) }
        if (start - (lastTry[c.outcomeId] ?: 0L) < TailTradeLimits.COOLDOWN_MS) return skip("cooldown")
        if ((blacklist[c.marketId] ?: 0L) > start) return skip("market refused lately")
        val limit = LiveEdge.reachPrice(c.fair, c.ask, o.fee, true, floor)
        if (ownBids().any { it.marketId == c.marketId && it.outcomeId != c.outcomeId && it.price >= 1.0 - limit - 1e-9 }) return skip("own bid in the way")
        if (!mutex.tryLock()) return skip("busy")
        if (lock != null && !lock.tryLock()) { mutex.unlock(); return skip("busy") }
        try {
            lastTry[c.outcomeId] = start
            val loss = lossLimitHit(r)
            if (loss != null) {
                haltNow(loss)
                return skip("halted")
            }
            val qty = size(o, limit, r, start) ?: return skip("limit reached")
            return withContext(NonCancellable) { if (r.bet) trade(o, limit, qty) else paper(o, limit, qty) }
        } finally {
            lock?.unlock()
            mutex.unlock()
        }
    }

    private suspend fun lossLimitHit(r: LiveTradeRules): String? {
        if (!r.bet || r.haltLoss <= 0.0) return null
        val loss = runCatching { lossToday() }.getOrDefault(0.0)
        return if (loss >= r.haltLoss) "live tail bets lost ${"$"}${"%.2f".format(Locale.US, loss)} today, over your ${"$"}${"%.2f".format(Locale.US, r.haltLoss)} limit" else null
    }

    /** Contracts: the stake at the WORST price the order may fill at (fee in), what is on offer, and what the game's and the day's caps leave. */
    private fun size(o: TailOffer, limit: Double, r: LiveTradeRules, now: Long): Long? {
        val payout = EvMath.CONTRACT_PAYOUT_DOLLARS
        val perContract = (limit + Fees.takerFee(limit.coerceIn(0.001, 0.999), o.fee, true)) * payout
        val stake = r.stake.coerceIn(LiveTradeLimits.MIN_STAKE, LiveTradeLimits.MAX_STAKE)
        val today = history.filter { it.atMs >= dayStart(now) && it.mode == "BET" }
        val dayLeft = if (r.maxPerDay > 0) r.maxPerDay - today.sumOf { it.paid + it.feePaid } else Double.MAX_VALUE
        val gameLeft = if (r.maxPerGame > 0) r.maxPerGame - today.filter { it.eventId == o.candidate.eventId }.sumOf { it.paid + it.feePaid } else Double.MAX_VALUE
        val q = minOf(o.candidate.contracts, Math.floor(stake / perContract).toLong(), Math.floor(minOf(dayLeft, gameLeft) / perContract).toLong())
        return q.takeIf { it >= TailTradeLimits.MIN_CONTRACTS }
    }

    private fun base(o: TailOffer, mode: String, outcome: String, limit: Double, qty: Long, id: String, message: String, filled: Long = 0, paid: Double = 0.0, feePaid: Double = 0.0, endMs: Long = 0): LiveRecord {
        val c = o.candidate
        return LiveRecord(
            id = id, atMs = o.decidedAtMs, mode = mode, outcome = outcome, league = o.target.league, event = o.target.eventName, eventId = c.eventId, marketId = c.marketId, outcomeId = c.outcomeId,
            market = o.target.marketLabel, selection = o.target.selection, side = c.side, lineKey = "tail ${c.strike}", fair = c.fair, ask = c.ask, limitPrice = limit,
            fee = Fees.takerFee(c.ask.coerceIn(0.001, 0.999), o.fee, true), ev = c.edge, contracts = qty, filled = filled, paid = paid, feePaid = feePaid, sendToEndMs = endMs,
            score = o.state, message = message,
        )
    }

    private fun paper(o: TailOffer, limit: Double, qty: Long): LiveRecord {
        val r = base(o, "PAPER", "PAPER", limit, qty, newClientId(), "paper: would buy $qty at up to ${"%.3f".format(Locale.US, limit)} (nothing sent)")
        record(r)
        _status.value = _status.value.copy(paper = _status.value.paper + 1, last = "PAPER · ${r.selection} · ${"%.1f".format(Locale.US, r.ev * 100)}% edge")
        return r
    }

    private suspend fun trade(o: TailOffer, limit: Double, qty: Long): LiveRecord {
        val c = o.candidate
        val clientId = newClientId()
        val sentAt = clock()
        val orderId: String = try {
            orders.place(c.outcomeId, limit, qty, clientId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            return refused(base(o, "BET", "REFUSED", limit, qty, clientId, ""), e, o)
        } catch (e: Exception) {
            val r = base(o, "BET", "UNCONFIRMED", limit, qty, clientId, "Novig's answer to the order never came (${e.message ?: e.javaClass.simpleName}): nothing is assumed.")
            record(r)
            haltNow("a tail order's answer was lost: check Novig and the Tracker (Sync with Novig), then Resume")
            return r
        }
        val settled = settleOrder(orders, clock, pause, orderId)
        val endMs = clock() - sentAt
        if (settled == null) {
            val r = base(o, "BET", "UNCONFIRMED", limit, qty, clientId, "The order had not ended after ${LiveTradeLimits.ORDER_WAIT_MS} ms.", endMs = endMs)
            record(r)
            haltNow("a tail order was still pending after ${LiveTradeLimits.ORDER_WAIT_MS / 1000} s, so its result is unknown: check Novig's orders and the Tracker (Sync with Novig), then Resume")
            return r
        }
        val (order, fills) = settled
        val filled = fills.sumOf { it.qty }.takeIf { it > 0 } ?: (if (order.status == "FILLED") order.qty else 0L)
        if (filled <= 0L) {
            val r = base(o, "BET", "MISSED", limit, qty, clientId, "Nobody was selling at that price any more: no bet, no money moved.", endMs = endMs)
            record(r)
            _status.value = _status.value.copy(missed = _status.value.missed + 1, last = "MISSED · ${r.selection} ($endMs ms)")
            return r
        }
        val paid = if (fills.isNotEmpty()) fills.sumOf { it.cost } else filled * limit * EvMath.CONTRACT_PAYOUT_DOLLARS
        val fee = if (fills.isNotEmpty()) fills.sumOf { it.fee } else filled * Fees.takerFee(limit.coerceIn(0.001, 0.999), o.fee, true) * EvMath.CONTRACT_PAYOUT_DOLLARS
        val logged = fills.isNotEmpty() && runCatching { logFills(o.target, orderId, fills) }.getOrDefault(false)
        val outcome = if (filled >= qty) "FILLED" else "PARTIAL"
        val r = base(
            o, "BET", outcome, limit, qty, clientId,
            "bought $filled of $qty for ${"$"}${"%.2f".format(Locale.US, paid)} + ${"$"}${"%.2f".format(Locale.US, fee)} fee" + if (logged) "" else " (not in the Tracker yet: Sync with Novig)",
            filled = filled, paid = paid, feePaid = fee, endMs = endMs,
        )
        record(r)
        _status.value = _status.value.let { it.copy(bets = it.bets + 1, spent = it.spent + paid + fee, last = "$outcome · ${r.selection} · ${"%.1f".format(Locale.US, r.ev * 100)}% edge · $endMs ms") }
        return r
    }

    private fun refused(r: LiveRecord, e: NovigApiException, o: TailOffer): LiveRecord {
        val now = clock()
        when {
            e.status == 451 || e.status == 423 && e.code !in LiveTradeLimits.MARKET_CODES -> standDownUntil = now + LiveTradeLimits.STAND_DOWN_MS
            e.code in LiveTradeLimits.MARKET_CODES -> blacklist[o.candidate.marketId] = now + LiveTradeLimits.BLACKLIST_MS
        }
        val out = r.copy(message = "Novig refused the order (${e.code ?: e.status}): ${e.advice}")
        record(out)
        _status.value = _status.value.copy(refused = _status.value.refused + 1, standDownUntilMs = standDownUntil.takeIf { it > now }, last = "REFUSED · ${e.code ?: e.status}")
        return out
    }

    private fun haltNow(why: String) {
        haltedAtMs = clock()
        onHalt(why)
        _status.value = _status.value.copy(last = "HALTED: $why")
    }

    private fun record(r: LiveRecord) {
        history += r
        try { journal.append(r) } catch (e: Exception) { _status.value = _status.value.copy(last = "the journal could not be written: ${e.message}") }
    }

    /** Resume after a halt (the settings' halted line is cleared by the caller). */
    fun resumed() {
        haltedAtMs = 0L
    }

    /** Every record so far (the journal's, plus this run's), oldest first. */
    fun records(): List<LiveRecord> = history.toList()

    companion object {
        /** The rule set the model must have been judged by ([TailRules.name] of the conservative rules). */
        const val CONSERVATIVE = "cons"
    }
}

/** Waits for an order to end (an in-play order waits out Novig's delay as PENDING) and reads its fills; null when it has not ended in [LiveTradeLimits.ORDER_WAIT_MS]. The same loop as the Pinnodds trader's. */
internal suspend fun settleOrder(orders: LiveOrders, clock: () -> Long, pause: suspend (Long) -> Unit, orderId: String): Pair<NovigOrder, List<NovigFill>>? {
    val started = clock()
    var o: NovigOrder? = null
    while (clock() - started < LiveTradeLimits.ORDER_WAIT_MS) {
        o = try { orders.order(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        if (o?.terminal == true) break
        pause(if (clock() - started < LiveTradeLimits.FAST_POLL_WINDOW_MS) LiveTradeLimits.POLL_MS else LiveTradeLimits.SLOW_POLL_MS)
    }
    if (o?.terminal != true) return null
    var fills = try { orders.fills(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    if (fills.isEmpty() && o.status == "FILLED") {
        pause(600)
        fills = try { orders.fills(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }
    return o to fills
}
