package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** The fixed limits of the live trader: Tj's own caps are [LiveTradeRules]; these protect him from the code. */
object LiveTradeLimits {
    /** No stake is ever set under or over these, whatever the saved settings say. */
    const val MIN_STAKE = 1.0
    const val MAX_STAKE = 25.0

    /** The same outcome is not tried again within this long (the first try's fill or miss has not shown in the book yet). */
    const val OUTCOME_COOLDOWN_MS = 15_000L

    /** How long a placed order may take to end (an `IOC` ends at once); past it nothing is assumed and the trader halts. */
    const val ORDER_WAIT_MS = 2_500L
    const val POLL_MS = 80L

    /** A market Novig refused to trade in play (`NOT_LIVE_TRADABLE`, a price band, closed ...) is left alone this long. */
    const val BLACKLIST_MS = 10 * 60_000L

    /** A 451 / 423 (Novig judges the network or the account) stops all trading this long. */
    const val STAND_DOWN_MS = 10 * 60_000L

    /** A halt is honoured this long even before the settings say so (the setting is written a moment later). */
    const val HALT_GRACE_MS = 5_000L

    /** Novig's refusals that are about ONE market (or game), not the account. */
    val MARKET_CODES = setOf("NOT_LIVE_TRADABLE", "EVENT_NOT_TRADABLE", "MARKET_CLOSED", "MARKET_INACTIVE", "PRICE_BAND_VIOLATION", "ORDER_TOO_SMALL", "ORDER_TOO_LARGE", "MARKET_LOCKED", "EVENT_LOCKED")

    /** After a decision, the follow-up readings of the same line (seconds): what Pinnacle and Novig said later is how the bet is judged against the close. */
    val FOLLOW_UP_SECONDS = listOf(30, 120)
}

/**
 * Tj's own limits for the live trader (Settings › Pinnodds live), read each time: [enabled] (the feed is on), [bet] (real orders; off = PAPER: it decides, journals and follows up but
 * sends nothing), [stake] dollars a bet, [maxPerGame] and [maxPerDay] dollars spent (fees in), [haltLoss] dollars lost on settled live bets in a day that halt it, [halted] (why it stopped).
 */
data class LiveTradeRules(
    val enabled: Boolean,
    val bet: Boolean,
    val stake: Double,
    val maxPerGame: Double,
    val maxPerDay: Double,
    val haltLoss: Double,
    val halted: String? = null,
)

/** A resting bid of Vigilant's own: an order that buys the other side could trade against it (a wash), so the trader keeps clear. */
data class LiveOwnBid(val marketId: String, val outcomeId: String, val price: Double)

/** What the trader needs from Novig. The app's is [NovigTradingClient]; tests fake it. */
interface LiveOrders {
    /** One `IOC` order; the order id. A [NovigApiException] = refused, nothing placed; any other failure = a lost answer. */
    suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String): String
    suspend fun order(orderId: String): NovigOrder?
    suspend fun fills(orderId: String): List<NovigFill>
}

/** A bet worth making, as the runner hands it over: the judgment plus what the Tracker and the journal need to say about it. */
data class LiveCandidate(
    val target: LiveTarget,
    val side: PinnSide,
    val outcomeId: String,
    val verdict: LiveVerdict.Bet,
    val lineKey: String,
    val points: Double?,
    val betTarget: BetTarget,
    val fee: MarketFee,
    val score: String?,
    val clock: String?,
    val decidedAtMs: Long,
    /** Pinnacle's last change to the line, the phone's clock: how long it took from that change to this decision. */
    val pinnChangedAtMs: Long,
)

/** One decision, as journaled. [mode] BET or PAPER; [outcome] FILLED, PARTIAL, MISSED (nothing filled), REFUSED, UNCONFIRMED or PAPER. */
@Serializable
data class LiveRecord(
    val id: String,
    val atMs: Long,
    val mode: String,
    val outcome: String,
    val league: String,
    val event: String,
    val eventId: String,
    val marketId: String,
    val outcomeId: String,
    val market: String,
    val selection: String,
    val side: String,
    val lineKey: String,
    val fair: Double,
    val ask: Double,
    val limitPrice: Double,
    val fee: Double,
    val ev: Double,
    val move: Double? = null,
    val stableMs: Long = 0,
    val overround: Double = 0.0,
    val contracts: Long = 0,
    val filled: Long = 0,
    val paid: Double = 0.0,
    val feePaid: Double = 0.0,
    val decisionMs: Long = 0,
    val sendToEndMs: Long = 0,
    val score: String? = null,
    val clock: String? = null,
    val message: String = "",
)

/** What the same line said [offsetSec] after a decision: Pinnacle's devigged fair for the side then, and Novig's ask. */
@Serializable
data class LiveFollow(val id: String, val atMs: Long, val offsetSec: Int, val fair: Double? = null, val ask: Double? = null, val closed: Boolean = false)

/** An append-only journal, one JSON line a record, one file a day (Eastern, like the other journals), never rewritten. */
class DayJournal<T>(private val dir: File, private val prefix: String, private val serializer: KSerializer<T>, private val timeOf: (T) -> Long) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun append(r: T) {
        dir.mkdirs()
        val f = File(dir, "$prefix-${java.time.Instant.ofEpochMilli(timeOf(r)).atZone(java.time.ZoneId.of("America/New_York")).toLocalDate()}.jsonl")
        val line = (if (f.exists() && f.length() > 0 && !endsWithNewline(f)) "\n" else "") + json.encodeToString(serializer, r) + "\n"
        FileOutputStream(f, true).use { out -> out.write(line.toByteArray()); out.fd.sync() }
    }

    fun readAll(): List<T> = (dir.listFiles { f -> f.isFile && f.name.startsWith("$prefix-") }?.sortedBy { it.name }.orEmpty()).flatMap { f ->
        f.useLines { ls -> ls.filter { it.isNotBlank() }.mapNotNull { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.toList() }
    }

    private fun endsWithNewline(f: File): Boolean = java.io.RandomAccessFile(f, "r").use { r -> r.seek(r.length() - 1); r.read() == '\n'.code }
}

data class LiveTradeStatus(
    val bets: Int = 0,
    val paper: Int = 0,
    val missed: Int = 0,
    val refused: Int = 0,
    val spent: Double = 0.0,
    val skipped: Map<String, Int> = emptyMap(),
    val standDownUntilMs: Long? = null,
    val last: String? = null,
)

/**
 * The real-money half of the Pinnodds live feature (Tj, 2026-10-08): for each [LiveCandidate] the runner finds (a Novig price that lags Pinnacle's devigged fair by more than the fee and the
 * safety margin, right after Pinnacle moved), it checks the rules and the gate; keeps clear of Vigilant's own resting bids (a wash); sizes the bet to the stake, to what is on offer at a
 * positive edge and to the per-game and per-day caps; sends ONE `IOC` order (it fills what is there at that price or better and never rests); reads the order and its fills back; logs the
 * real fills to the Tracker; and journals the decision with its timings. PAPER mode (bet off) does everything except send. An answer that never came halts it ("nothing is assumed"), as does a
 * day's loss past Tj's limit; a 451 or 423 stops it for a while; a market Novig refuses is left alone. One attempt at a time, sharing the app's one-order-at-a-time [lock].
 */
class PinnLiveTrader(
    private val orders: LiveOrders,
    private val scope: CoroutineScope,
    private val rules: () -> LiveTradeRules,
    /** Why trading must not happen now (STOP ALL, paused, no betting key, no wallet ...), or null. */
    private val gate: suspend () -> String?,
    private val ownBids: () -> List<LiveOwnBid>,
    private val journal: DayJournal<LiveRecord>,
    private val onHalt: (String) -> Unit,
    /** The Tracker: records the real fills of an order. False = the fills could not be recorded (Sync with Novig finds them). */
    private val logFills: suspend (BetTarget, String, List<NovigFill>) -> Boolean,
    /** Dollars lost today on settled live bets (positive = a loss), or 0. */
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

    /** Hands the candidate over; returns at once. The attempt runs on its own coroutine and counts why it did not trade. */
    fun offer(c: LiveCandidate, onRecord: (LiveRecord) -> Unit = {}) {
        if (!rules().enabled) return
        scope.launch { attempt(c)?.let(onRecord) }
    }

    private fun skip(why: String): LiveRecord? {
        _status.value = _status.value.let { it.copy(skipped = it.skipped + (why to (it.skipped[why] ?: 0) + 1)) }
        return null
    }

    internal suspend fun attempt(c: LiveCandidate): LiveRecord? {
        val r = rules()
        if (!r.enabled) return null
        val start = clock()
        if (r.halted != null || start - haltedAtMs < LiveTradeLimits.HALT_GRACE_MS) return skip("halted")
        if (r.bet && start < standDownUntil) return skip("stood down")
        if (r.bet) gate()?.let { return skip(it) }
        if (start - (lastTry[c.outcomeId] ?: 0L) < LiveTradeLimits.OUTCOME_COOLDOWN_MS) return skip("cooldown")
        if ((blacklist[c.target.market.marketId] ?: 0L) > start) return skip("market refused lately")
        if (clashesWithOwnBid(c)) return skip("own bid in the way")
        if (!mutex.tryLock()) return skip("busy")
        if (lock != null && !lock.tryLock()) { mutex.unlock(); return skip("busy") }
        try {
            lastTry[c.outcomeId] = start
            val limit = lossLimitHit(r)
            if (limit != null) {
                haltNow(limit)
                return skip("halted")
            }
            val qty = size(c, r, start) ?: return skip("limit reached")
            return withContext(NonCancellable) { if (r.bet) trade(c, qty, start) else paper(c, qty) }
        } finally {
            lock?.unlock()
            mutex.unlock()
        }
    }

    private suspend fun lossLimitHit(r: LiveTradeRules): String? {
        if (!r.bet || r.haltLoss <= 0.0) return null
        val loss = runCatching { lossToday() }.getOrDefault(0.0)
        return if (loss >= r.haltLoss) "live bets lost ${"$"}${"%.2f".format(Locale.US, loss)} today, over your ${"$"}${"%.2f".format(Locale.US, r.haltLoss)} limit" else null
    }

    private fun clashesWithOwnBid(c: LiveCandidate): Boolean =
        ownBids().any { it.marketId == c.target.market.marketId && it.outcomeId != c.outcomeId && it.price >= 1.0 - c.verdict.limitPrice - 1e-9 }

    /** Contracts: the stake at the WORST price the order may fill at (fee in), what is on offer at a positive edge, and what the game's and the day's caps leave. */
    private fun size(c: LiveCandidate, r: LiveTradeRules, now: Long): Long? {
        val payout = EvMath.CONTRACT_PAYOUT_DOLLARS
        val worst = c.verdict.limitPrice
        val perContract = (worst + Fees.takerFee(worst.coerceIn(0.001, 0.999), c.fee, true)) * payout
        val stake = r.stake.coerceIn(LiveTradeLimits.MIN_STAKE, LiveTradeLimits.MAX_STAKE)
        val today = history.filter { it.atMs >= dayStart(now) && it.mode == "BET" }
        val dayLeft = if (r.maxPerDay > 0) r.maxPerDay - today.sumOf { it.paid + it.feePaid } else Double.MAX_VALUE
        val gameLeft = if (r.maxPerGame > 0) r.maxPerGame - today.filter { it.eventId == c.target.event.eventId }.sumOf { it.paid + it.feePaid } else Double.MAX_VALUE
        val q = minOf(c.verdict.contracts, Math.floor(stake / perContract).toLong(), Math.floor(minOf(dayLeft, gameLeft) / perContract).toLong())
        return q.takeIf { it >= MIN_CONTRACTS }
    }

    private fun base(c: LiveCandidate, mode: String, outcome: String, qty: Long, id: String, message: String, filled: Long = 0, paid: Double = 0.0, feePaid: Double = 0.0, endMs: Long = 0) = LiveRecord(
        id = id, atMs = c.decidedAtMs, mode = mode, outcome = outcome, league = c.betTarget.league, event = c.betTarget.eventName, eventId = c.target.event.eventId,
        marketId = c.target.market.marketId, outcomeId = c.outcomeId, market = c.betTarget.marketLabel, selection = c.betTarget.selection, side = c.side.name, lineKey = c.lineKey,
        fair = c.verdict.fair, ask = c.verdict.ask, limitPrice = c.verdict.limitPrice, fee = c.verdict.fee, ev = c.verdict.ev, move = c.verdict.move, stableMs = c.verdict.stableMs,
        overround = c.verdict.overround, contracts = qty, filled = filled, paid = paid, feePaid = feePaid, decisionMs = c.decidedAtMs - c.pinnChangedAtMs, sendToEndMs = endMs,
        score = c.score, clock = c.clock, message = message,
    )

    private fun paper(c: LiveCandidate, qty: Long): LiveRecord {
        val r = base(c, "PAPER", "PAPER", qty, newClientId(), "paper: would buy $qty at up to ${"%.3f".format(Locale.US, c.verdict.limitPrice)} (nothing sent)")
        record(r)
        _status.value = _status.value.copy(paper = _status.value.paper + 1, last = "PAPER · ${r.selection} · ${"%.1f".format(Locale.US, r.ev * 100)}% EV")
        return r
    }

    private suspend fun trade(c: LiveCandidate, qty: Long, decidedAtMs: Long): LiveRecord {
        val clientId = newClientId()
        val sentAt = clock()
        val orderId: String = try {
            orders.place(c.outcomeId, c.verdict.limitPrice, qty, clientId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            return refused(base(c, "BET", "REFUSED", qty, clientId, ""), e, c)
        } catch (e: Exception) {
            val r = base(c, "BET", "UNCONFIRMED", qty, clientId, "Novig's answer to the order never came (${e.message ?: e.javaClass.simpleName}): nothing is assumed.")
            record(r)
            haltNow("an order's answer was lost: check Novig and the Tracker (Sync with Novig), then Resume")
            return r
        }
        val settled = settle(orderId)
        val endMs = clock() - sentAt
        if (settled == null) {
            val r = base(c, "BET", "UNCONFIRMED", qty, clientId, "The order had not ended after ${LiveTradeLimits.ORDER_WAIT_MS} ms.", endMs = endMs)
            record(r)
            haltNow("an order had not ended: check Novig and the Tracker, then Resume")
            return r
        }
        val (order, fills) = settled
        // The fills are the truth. An order that says FILLED while its fills have not shown is a fill we cannot price exactly: counted at its limit price (a ceiling), and not logged.
        val filled = fills.sumOf { it.qty }.takeIf { it > 0 } ?: (if (order.status == "FILLED") order.qty else 0L)
        if (filled <= 0L) {
            val r = base(c, "BET", "MISSED", qty, clientId, "Nobody was selling at that price any more: no bet, no money moved.", endMs = endMs)
            record(r)
            _status.value = _status.value.copy(missed = _status.value.missed + 1, last = "MISSED · ${r.selection} (${endMs} ms)")
            return r
        }
        val paid = if (fills.isNotEmpty()) fills.sumOf { it.cost } else filled * c.verdict.limitPrice * EvMath.CONTRACT_PAYOUT_DOLLARS
        val fee = if (fills.isNotEmpty()) fills.sumOf { it.fee } else filled * Fees.takerFee(c.verdict.limitPrice.coerceIn(0.001, 0.999), c.fee, true) * EvMath.CONTRACT_PAYOUT_DOLLARS
        val logged = fills.isNotEmpty() && runCatching { logFills(c.betTarget, orderId, fills) }.getOrDefault(false)
        val outcome = if (filled >= qty) "FILLED" else "PARTIAL"
        val r = base(
            c, "BET", outcome, qty, clientId,
            "bought $filled of $qty for ${"$"}${"%.2f".format(Locale.US, paid)} + ${"$"}${"%.2f".format(Locale.US, fee)} fee" + if (logged) "" else " (not in the Tracker yet: Sync with Novig)",
            filled = filled, paid = paid, feePaid = fee, endMs = endMs,
        )
        record(r)
        _status.value = _status.value.let { it.copy(bets = it.bets + 1, spent = it.spent + paid + fee, last = "$outcome · ${r.selection} · ${"%.1f".format(Locale.US, r.ev * 100)}% EV · ${endMs} ms") }
        return r
    }

    private suspend fun settle(orderId: String): Pair<NovigOrder, List<NovigFill>>? {
        val started = clock()
        var o: NovigOrder? = null
        while (clock() - started < LiveTradeLimits.ORDER_WAIT_MS) {
            o = try { orders.order(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (o?.terminal == true) break
            pause(LiveTradeLimits.POLL_MS)
        }
        if (o?.terminal != true) return null
        var fills = try { orders.fills(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        if (fills.isEmpty() && o.status == "FILLED") {
            // The order says it filled and its fills have not shown yet.
            pause(600)
            fills = try { orders.fills(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        }
        return o to fills
    }

    private fun refused(r: LiveRecord, e: NovigApiException, c: LiveCandidate): LiveRecord {
        val now = clock()
        when {
            e.status == 451 || e.status == 423 && e.code !in LiveTradeLimits.MARKET_CODES -> standDownUntil = now + LiveTradeLimits.STAND_DOWN_MS
            e.code in LiveTradeLimits.MARKET_CODES -> blacklist[c.target.market.marketId] = now + LiveTradeLimits.BLACKLIST_MS
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
        /** The fewest contracts worth an order (a contract pays 1 cent). */
        const val MIN_CONTRACTS = 20L
    }
}
