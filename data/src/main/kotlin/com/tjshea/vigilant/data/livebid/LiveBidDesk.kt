package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.stream.BookChange
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.pinnodds.LiveTradeLimits
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** What the desk needs from Novig. The app's is the trading client; tests fake it. */
interface LiveBidOrders {
    /** One post-only order that Novig removes by itself after [ttlMs]; the order id. A [NovigApiException] = refused, nothing placed; any other failure = a lost answer (never re-sent). */
    suspend fun place(outcomeId: String, price: Double, qty: Long, ttlMs: Long, clientId: String): String

    /** Cancels [orderIds]; each id's status when its cancel arrived (`FILLED` = too late), null when Novig has no such order. */
    suspend fun cancel(orderIds: List<String>): Map<String, String?>

    /** Every order resting on the account. */
    suspend fun open(): List<NovigOrder>

    /** The order with this client id on this outcome in any status, or null. */
    suspend fun find(clientId: String, outcomeId: String): NovigOrder?

    suspend fun order(orderId: String): NovigOrder?
    suspend fun fills(orderId: String): List<NovigFill>

    /**
     * The fills of [orderIds], by order id, in as few reads as the client can (Novig's `history` bucket costs 8 tokens a read, 4 a second: one read an ended bid would run it dry). [startsAfterMs] is the
     * earliest start of their games, less a day.
     */
    suspend fun fillsOf(orderIds: Collection<String>, startsAfterMs: Long): Map<String, List<NovigFill>> = orderIds.associateWith { fills(it) }
}

/** What the desk is told each time it looks: Tj's switches and limits, read fresh so a change takes effect at once. [blockedWhy] is why REAL bids must not go up now (STOP ALL, a pause, no key). */
data class LiveBidConfig(
    val on: Boolean,
    val real: Boolean,
    val quality: LiveBidQuality,
    val limits: LiveBidLimits,
    val bankroll: Double,
    val apiMaxStake: Double,
    val preset: String? = null,
    val halted: String? = null,
    val blockedWhy: String? = null,
) {
    companion object {
        val OFF = LiveBidConfig(on = false, real = false, quality = LiveBidQuality(), limits = LiveBidLimits(), bankroll = 0.0, apiMaxStake = 0.0)
    }
}

/** A bid the runner would put up now, with what the record of it needs. */
data class LiveBidWant(
    val atMs: Long,
    val outcomeId: String,
    val marketId: String,
    val eventId: String,
    val pinnEventId: Long,
    val league: String,
    val eventName: String,
    val startsTs: Long,
    val marketLabel: String,
    val selection: String,
    val verdict: LiveBidVerdict.Post,
    val fee: MarketFee?,
    val score: String?,
    val clock: String?,
)

data class LiveBidDeskStatus(
    /** "off", "paper" or "real". */
    val mode: String = "off",
    val active: Int = 0,
    val restingDollars: Double = 0.0,
    val posted: Int = 0,
    val fills: Int = 0,
    val paid: Double = 0.0,
    val pulls: Map<String, Int> = emptyMap(),
    val skips: Map<String, Int> = emptyMap(),
    val refused: Int = 0,
    val halted: String? = null,
    val standDown: String? = null,
    val timing: String = "",
    val last: String? = null,
    val problem: String? = null,
)

/**
 * The live bid desk (Tj, 2026-10-10: "Build a live bid feature … strong safeguards in place to make sure the live bids don't get stale and that the live bids are truly EV"; RESEARCH.md §123-§124).
 * It owns the orders: what the runner says is justified ([want], [keepAlive], [pull]) becomes post-only orders that rest seconds at a time, and what stops being justified comes down.
 *
 * Why it is built the way it is:
 *  - **Every resting bid needs a fresh justification.** The runner re-judges each bid every half second and on every Pinnacle frame; a bid nobody has vouched for in [CLAIM_TTL_MS] is pulled, so a
 *    stalled runner, a dead feed or a lost callback takes the bids down by itself. Behind that, each order carries a `ttl` Novig enforces if the phone is gone altogether.
 *  - **A pull never waits.** Cancels do not take the shared order lock, run on their own coroutines and are retried; only placing takes the lock. A live order takes seconds to land (RESEARCH.md §120.1), so
 *    every network call here runs OUTSIDE the state lock and the state is touched only in short blocks.
 *  - **Money is counted at its worst.** Every bid not yet over (one being sent, one being pulled) counts as filled against the wallet, the game, the day and the number of bids; the replacement for a bid
 *    that is about to expire is the only overlap, and it is counted.
 *  - **A lost answer is never re-sent**; the order is looked for by its client id and taken down.
 *  - **It stops itself:** a halt on a bad day, on a run of picked-off fills, on a pull or a placement that measures too slow; a stand-down on Novig's network and account refusals; a market Novig refuses
 *    is left alone.
 *  - **Paper does everything but send:** the same decisions, the same pulls, fills inferred from the trades that print on Novig's book, the same follow-ups; the delay a real order takes is added.
 * Suspending calls are made from the desk's own coroutines; the runner's calls are plain functions that only record intent.
 */
class LiveBidDesk(
    private val scope: CoroutineScope,
    private val orders: LiveBidOrders?,
    private val store: LiveBidPersistence,
    private val journal: DayJournal<LiveBidEvent>?,
    private val config: () -> LiveBidConfig,
    /** The wallet in dollars, or null while unread. */
    private val wallet: () -> Double?,
    /** Dollars of other resting bids on the account (the pregame desk's), counted against the wallet. */
    private val otherRestingDollars: () -> Double = { 0.0 },
    /** Dollars of API bets filled today across every desk, and the day's limit across every desk. */
    private val spentToday: suspend () -> Double = { 0.0 },
    private val dayLimit: () -> Double = { Double.MAX_VALUE },
    /** Dollars lost today on settled live bids (positive = a loss). */
    private val lossToday: suspend () -> Double = { 0.0 },
    /** Records fills in the Tracker; the bet's id. */
    private val logFills: suspend (BetTarget, String, List<NovigFill>) -> String? = { _, _, _ -> null },
    private val onHalt: (String) -> Unit = {},
    /** The lock every order-placing desk shares, taken only around a placement. */
    private val lock: Mutex = Mutex(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val version: String? = null,
    private val tickMs: Long = TICK_MS,
) {
    private val mu = Any()
    private val bids = LinkedHashMap<String, LiveBid>()
    private val wants = HashMap<String, LiveBidWant>()
    private val claimedAt = HashMap<String, Long>()
    private val coolUntil = HashMap<String, Long>()
    private val marketBlockedUntil = HashMap<String, Long>()
    private val replaced = HashSet<String>()
    private val cancelTriedAt = HashMap<String, Long>()
    private val audits = HashMap<String, Int>()
    private val persistLock = Mutex()
    private val latestFair = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val skipCounts = HashMap<String, Int>()
    private val pullCounts = HashMap<String, Int>()
    private val placeAckMs = ArrayDeque<Long>()
    private val placeOpenMs = ArrayDeque<Long>()
    private val pullMs = ArrayDeque<Long>()
    private val judged = ArrayDeque<Boolean>()
    private var halted: String? = null
    private var standDownUntil = 0L
    private var standDownWhy: String? = null
    private var backoffUntil = 0L
    private var refused = 0
    private var lostAnswers = 0
    private var problem: String? = null
    private var last: String? = null
    private var dirty = false
    private var lastPersistMs = 0L
    private var lastPollMs = 0L
    private var lastLossCheckMs = 0L
    private var started = false

    @Volatile private var settling: Job? = null
    @Volatile private var worker: Job? = null
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val _status = MutableStateFlow(LiveBidDeskStatus())
    val status: StateFlow<LiveBidDeskStatus> = _status.asStateFlow()
    private val _bids = MutableStateFlow<List<LiveBid>>(emptyList())

    /** Every bid on record, newest last (the Tracker's Live bids list and Diagnostics read it). */
    val bidsFlow: StateFlow<List<LiveBid>> = _bids.asStateFlow()

    // ---- what the runner says -------------------------------------------------------------------------------------------------------------------------------------------------

    /** A bid is justified now: a new one goes up if none is, and a bid already up on the outcome is vouched for. Plain and quick; callable from any thread. */
    fun want(w: LiveBidWant) {
        synchronized(mu) {
            wants[w.outcomeId] = w
            claimedAt[w.outcomeId] = w.atMs
        }
        signals.trySend(Unit)
    }

    /** The bid up on [outcomeId] is still justified as of [nowMs] (the judge's `keep` said so). */
    fun keepAlive(outcomeId: String, nowMs: Long) {
        synchronized(mu) { claimedAt[outcomeId] = nowMs }
    }

    /** No new bid on [outcomeId] (counted by [reason]); a bid already up is not touched by this. */
    fun noWant(outcomeId: String, reason: String) {
        synchronized(mu) {
            wants.remove(outcomeId)
            skipCounts[reason] = (skipCounts[reason] ?: 0) + 1
        }
    }

    /** The bid up on [outcomeId] must come down now. Acted on at once, from this call. */
    fun pull(outcomeId: String, reason: String) {
        val victims = synchronized(mu) {
            wants.remove(outcomeId)
            bids.values.filter { it.active && it.outcomeId == outcomeId }.map { it.clientId }
        }
        for (id in victims) cancelOne(id, reason)
    }

    /** Every bid on game [eventId] comes down now. */
    fun pullEvent(eventId: String, reason: String) {
        val victims = synchronized(mu) {
            wants.values.removeAll { it.eventId == eventId }
            bids.values.filter { it.active && it.eventId == eventId }.map { it.clientId }
        }
        for (id in victims) cancelOne(id, reason)
    }

    /** The latest Pinnacle fair for an outcome, noted by the runner at every judgment (fills are followed up against it). */
    fun noteFair(outcomeId: String, fair: Double, atMs: Long) {
        latestFair[outcomeId] = fair to atMs
    }

    /** Every bid on record right now, newest last. */
    fun bidsNow(): List<LiveBid> = synchronized(mu) { bids.values.toList() }

    /** The bid up on [outcomeId], as the judge needs it, or null. */
    fun held(outcomeId: String): LiveBidHeld? = synchronized(mu) {
        bids.values.lastOrNull { it.active && it.outcomeId == outcomeId && it.status != LiveBidStatus.CANCELING }?.let { LiveBidHeld(it.price, it.postedAtMs, it.mid) }
    }

    /** Our resting contracts by price in thousandths, per outcome: taken out of Novig's book so a bid is not judged against itself. */
    fun ownLevels(): Map<String, Map<Int, Long>> = synchronized(mu) {
        val out = HashMap<String, HashMap<Int, Long>>()
        for (b in bids.values) if (b.active && b.remaining > 0) {
            val m = out.getOrPut(b.outcomeId) { HashMap() }
            val key = Math.round(b.price * 1000).toInt()
            m[key] = (m[key] ?: 0L) + b.remaining
        }
        out
    }

    /** True when a bid is up (or being sent or pulled) on any outcome of [marketId]. */
    fun busy(marketId: String): Boolean = synchronized(mu) { bids.values.any { it.active && it.marketId == marketId } }

    /** The changes to one market's book. A fill on a resting order is a trade: a paper bid at or over its price is filled by it, and a real bid's fill is looked for at once. */
    fun onBook(marketId: String, atMs: Long, changes: List<BookChange>) {
        var poke = false
        val filled = ArrayList<Pair<LiveBid, BookChange>>()
        synchronized(mu) {
            for (c in changes) {
                if (c.kind != BookChange.Kind.REMOVE || c.reason != "fill") continue
                for (b in bids.values) {
                    if (!b.active || b.marketId != marketId || b.outcomeId != c.outcome) continue
                    val limit = Math.round(b.price * 1000).toInt()
                    if (c.priceMilli > limit) continue   // the trade was at a better price for the seller than this bid: it did not reach it
                    if (b.real) { poke = true; continue }
                    if (atMs < b.activeFromMs) continue
                    if (b.status == LiveBidStatus.CANCELING && atMs >= (b.cancelSentAtMs ?: Long.MAX_VALUE) + PAPER_LATENCY_MS) continue
                    filled += b to c
                }
            }
        }
        for ((b, c) in filled) paperFill(b.clientId, c, atMs)
        if (poke) { pokeSettle = true; signals.trySend(Unit) }
    }

    @Volatile private var pokeSettle = false

    // ---- life cycle -----------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Starts the desk's own loop (once). Bids left from a run that ended mid-way are taken down first. */
    fun start() {
        synchronized(mu) {
            if (started) return
            started = true
        }
        worker = scope.launch {
            recover()
            run()
        }
    }

    fun stop() {
        worker?.cancel()
        worker = null
        synchronized(mu) { started = false }
    }

    /** Every bid down, now (STOP ALL, the runner stopping, a halt): the count that were up. Waits for the cancels to be sent, not to be confirmed. */
    suspend fun stopAll(why: String): Int {
        val ids = synchronized(mu) {
            wants.clear()
            bids.values.filter { it.active }.map { it.clientId }
        }
        withContext(NonCancellable) { cancelMany(ids, why) }
        return ids.size
    }

    /** Tj tapped Resume: the halt is lifted and the self-checks start over. */
    fun resumed() {
        synchronized(mu) {
            halted = null
            judged.clear()
            placeAckMs.clear(); placeOpenMs.clear(); pullMs.clear()
            lostAnswers = 0
            standDownUntil = 0L
        }
    }

    private fun halt(why: String) {
        val first = synchronized(mu) {
            if (halted != null) false else { halted = why; true }
        }
        if (!first) return
        event("HALT", text = why)
        scope.launch { stopAll("halted: $why") }
        onHalt(why)
    }

    private suspend fun recover() {
        val left = store.all()
        val now = clock()
        val ids = ArrayList<String>()
        synchronized(mu) {
            for (b in left) {
                var nb = b
                if (b.active) {
                    if (b.real) {
                        // A real bid left over from a run that ended: it comes down. Its order id may be unknown (an answer that never came): looked for by client id.
                        if (b.orderId != null) {
                            ids += b.clientId
                        } else {
                            nb = b.copy(status = LiveBidStatus.SENT, why = "the app restarted", lostAtMs = b.lostAtMs ?: now)
                        }
                    } else {
                        nb = b.copy(status = LiveBidStatus.CANCELED, endedAtMs = now, why = "the app restarted")
                    }
                }
                bids[b.clientId] = nb
            }
            dirty = true
        }
        if (ids.isNotEmpty()) cancelMany(ids, "the app restarted")
    }

    private suspend fun run() {
        while (currentCoroutineContext().isActive) {
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(mu) { problem = "live bid desk: ${e.message ?: e.javaClass.simpleName}" }
            }
            withTimeoutOrNull(tickMs) { signals.receive() }
        }
    }

    // ---- one step ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    private suspend fun step() {
        val now = clock()
        val cfg = config()
        val up = cfg.on && cfg.halted == null && synchronized(mu) { halted == null }
        val modeReal = cfg.real
        // 1. What must come down, whatever else is true.
        val down = ArrayList<Pair<String, String>>()
        synchronized(mu) {
            for (b in bids.values) {
                if (!b.active || b.status == LiveBidStatus.CANCELING) continue
                val why = when {
                    !cfg.on -> "live bids switched off"
                    cfg.halted != null -> "halted: ${cfg.halted}"
                    halted != null -> "halted: $halted"
                    b.real != modeReal -> "the mode changed"
                    b.real && cfg.blockedWhy != null -> cfg.blockedWhy
                    now < standDownUntil && b.real -> "stood down: $standDownWhy"
                    now - (claimedAt[b.outcomeId] ?: b.postedAtMs) > CLAIM_TTL_MS -> LiveBidSkip.CLAIM_GONE
                    else -> null
                }
                if (why != null) down += b.clientId to why
            }
        }
        for ((id, why) in down) cancelOne(id, why)
        // 2. What Novig says happened (a poll of the open orders, a read of fills); its own coroutine so a slow answer never holds a pull.
        val real = synchronized(mu) { bids.values.any { it.real && it.active } }
        if (real && orders != null && settling?.isActive != true && (pokeSettle || now - lastPollMs >= POLL_MS)) {
            lastPollMs = now
            pokeSettle = false
            settling = scope.launch { settleOnce() }
        }
        retryCancels(now)
        paperTick(now)
        followUps(now)
        checkLoss(now)
        // 3. New bids.
        if (up && (!modeReal || cfg.blockedWhy == null)) postWanted(now, cfg)
        persist(now)
        publish(now, cfg)
    }

    // ---- posting --------------------------------------------------------------------------------------------------------------------------------------------------------------

    private suspend fun postWanted(now: Long, cfg: LiveBidConfig) {
        // A real bid needs a way to be sent (a key); without one nothing goes up (the gate says why).
        if (cfg.real && orders == null) return
        val fresh = synchronized(mu) { wants.values.filter { now - it.atMs <= WANT_TTL_MS }.sortedByDescending { it.verdict.ev } }
        if (fresh.isEmpty()) return
        // Read once per pass, outside the lock: dollars of API bets already filled today by every desk.
        val spent = if (cfg.real) runCatching { spentToday() }.getOrDefault(Double.MAX_VALUE / 4) else 0.0
        var posts = 0
        for (w in fresh) {
            if (posts >= MAX_POSTS_PER_STEP) break
            val decision = synchronized(mu) { decide(w, cfg, now) }
            when (decision) {
                is Decision.Skip -> if (decision.count) count(decision.why)
                is Decision.Go -> {
                    val dollars = LiveBidStake.dollars(cfg.limits, w.verdict.fair, w.verdict.price, cfg.bankroll, cfg.apiMaxStake)
                    if (dollars == null) { count("no stake: ${cfg.limits.stakeMode.label} has nothing to bid here (is the bankroll set?)"); continue }
                    val contracts = LiveBidStake.contracts(dollars, w.verdict.price)
                    if (contracts < MIN_CONTRACTS) { count("stake too small for a bid"); continue }
                    val cost = contracts * w.verdict.price * EvMath.CONTRACT_PAYOUT_DOLLARS
                    val blocked = synchronized(mu) { budget(w, cost, cfg, now, decision.replacing, spent) }
                    if (blocked != null) { count(blocked); continue }
                    if (post(w, contracts, cfg, now, decision.replacing)) posts++
                }
            }
        }
    }

    private sealed interface Decision {
        data class Skip(val why: String, val count: Boolean = true) : Decision
        data class Go(val replacing: LiveBid?) : Decision
    }

    /** Whether a bid for [w] may go up now. Inside [mu]. */
    private fun decide(w: LiveBidWant, cfg: LiveBidConfig, now: Long): Decision {
        if (now < backoffUntil) return Decision.Skip("slowing down: Novig said too many requests", count = false)
        if (cfg.real && now < standDownUntil) return Decision.Skip("stood down: $standDownWhy", count = false)
        if ((marketBlockedUntil[w.marketId] ?: 0L) > now) return Decision.Skip("Novig refused this market in play", count = false)
        if ((coolUntil[w.outcomeId] ?: 0L) > now) return Decision.Skip("cooling off after a fill or a refusal", count = false)
        val onOutcome = bids.values.filter { it.active && it.outcomeId == w.outcomeId }
        var replacing: LiveBid? = null
        if (onOutcome.isNotEmpty()) {
            // A bid is up. Its replacement goes up before it ends, when asked for, once.
            val old = onOutcome.singleOrNull() ?: return Decision.Skip("a bid is already up here", count = false)
            val q = cfg.quality
            val dueMs = old.expiresAtMs - q.refreshBeforeSec * 1000L
            if (old.status == LiveBidStatus.CANCELING || old.status == LiveBidStatus.SENDING || now < dueMs || old.clientId in replaced) return Decision.Skip("a bid is already up here", count = false)
            if (!q.overlapRepost && now < old.expiresAtMs) return Decision.Skip("waiting for the bid to end", count = false)
            replacing = old
        }
        if (!cfg.quality.bothSides && bids.values.any { it.active && it.marketId == w.marketId && it.outcomeId != w.outcomeId }) return Decision.Skip("the other side of this market has a bid up", count = false)
        return Decision.Go(replacing)
    }

    /** Why a bid costing [cost] must not go up, or null. Inside [mu]. Every bid not yet over counts as if it fills. */
    private fun budget(w: LiveBidWant, cost: Double, cfg: LiveBidConfig, now: Long, replacing: LiveBid?, spent: Double): String? {
        val lim = cfg.limits
        // Counts of bids leave out a bid that is being replaced (it is the same bid, renewed); the MONEY counts every bid not yet over, that one too: both can fill for a moment.
        val live = bids.values.filter { it.active }
        val others = live.filter { it.clientId != replacing?.clientId && it.clientId !in replaced }
        if (others.size >= lim.maxBids) return "at the most bids up (${lim.maxBids})"
        if (others.count { it.eventId == w.eventId } >= lim.maxBidsPerGame) return "at the most bids up in one game (${lim.maxBidsPerGame})"
        val day = ApiBetPlacer.localMidnight(now)
        val gameDollars = live.filter { it.eventId == w.eventId }.sumOf { it.restingDollars } + bids.values.filter { it.eventId == w.eventId && it.postedAtMs >= day }.sumOf { it.paid }
        if (gameDollars + cost > lim.maxPerGame + 1e-9) return "at the most for one game (${money(lim.maxPerGame)})"
        val dayDollars = live.sumOf { it.restingDollars } + bids.values.filter { it.postedAtMs >= day }.sumOf { it.paid }
        if (dayDollars + cost > lim.maxPerDay + 1e-9) return "at the most for a day (${money(lim.maxPerDay)})"
        if (cfg.real) {
            if (spent + live.sumOf { it.restingDollars } + cost > dayLimit() + 1e-9) return "at the day's limit for API bets"
            val w0 = wallet() ?: return "wallet not read yet"
            val up = live.filter { it.real }.sumOf { it.restingDollars } + otherRestingDollars()
            if (w0 - lim.walletReserve - up < cost) return "the wallet cannot cover it beside the bids up"
        }
        return null
    }

    private fun post(w: LiveBidWant, contracts: Long, cfg: LiveBidConfig, now: Long, replacing: LiveBid?): Boolean {
        val q = cfg.quality
        val v = w.verdict
        val ttlMs = maxOf(q.ttlSec, MIN_TTL_SEC) * 1000L
        val real = cfg.real
        val bid = LiveBid(
            clientId = NovigTradingClient.newClientId(), mode = if (real) LiveBid.MODE_REAL else LiveBid.MODE_PAPER, marketId = w.marketId, eventId = w.eventId, outcomeId = w.outcomeId,
            pinnEventId = w.pinnEventId, league = w.league, eventName = w.eventName, startsTs = w.startsTs, marketLabel = w.marketLabel, selection = w.selection, price = v.price,
            contracts = contracts, fair = v.fair, ev = v.ev, mid = v.mid, bestBid = v.bestBid, offer = v.offer, leads = v.leads, overround = v.overround, pinnLimit = v.limit,
            feeCoefficient = w.fee?.coefficient ?: 0.0, makerCredit = w.fee?.makerCredit ?: 0.0, score = w.score, clock = w.clock, rules = q.summary(), preset = cfg.preset,
            postedAtMs = now, activeFromMs = if (real) now else now + PAPER_LATENCY_MS, expiresAtMs = now + ttlMs, status = if (real) LiveBidStatus.SENDING else LiveBidStatus.RESTING,
            openAtMs = if (real) null else now + PAPER_LATENCY_MS,
        )
        synchronized(mu) {
            bids[bid.clientId] = bid
            if (replacing != null) replaced += replacing.clientId
            claimedAt[w.outcomeId] = now
            dirty = true
            last = "${if (real) "BID" else "PAPER"} · ${w.selection} @ ${"%.3f".format(Locale.US, v.price)} · fair ${"%.3f".format(Locale.US, v.fair)} · ${"%.1f".format(Locale.US, v.ev * 100)}% EV"
        }
        event("POST", bid)
        if (real && orders != null) scope.launch { place(bid, ttlMs) }
        return true
    }

    /** Sends the order. Takes the shared order lock for the call only; a live order may take seconds to be accepted, so this is its own coroutine. */
    private suspend fun place(bid: LiveBid, ttlMs: Long) {
        val o = orders ?: return
        // On disk before it is sent: an answer that never comes back is still a bid this desk knows to look for, and a restart takes it down.
        persist(clock(), force = true)
        val t0 = clock()
        val id: String = try {
            withContext(NonCancellable) {
                lock.withLock {
                    // The price was worked out when the bid was decided on. Waiting for another desk's order this long (the lock is held while it is answered) makes it old: not sent.
                    if (clock() - bid.postedAtMs > LOCK_WAIT_MAX_MS) throw WaitedTooLong()
                    o.place(bid.outcomeId, bid.price, bid.contracts, ttlMs, bid.clientId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WaitedTooLong) {
            val now = clock()
            synchronized(mu) { bids[bid.clientId]?.let { bids[bid.clientId] = it.copy(status = LiveBidStatus.CANCELED, endedAtMs = now, why = "not sent: another order held the lock too long") }; dirty = true }
            event("END", bid, text = "not sent: the order lock was busy")
            return
        } catch (e: NovigApiException) {
            refused(bid, e)
            return
        } catch (e: Exception) {
            lostAnswer(bid, e)
            return
        }
        val now = clock()
        var cancelNow = false
        synchronized(mu) {
            val b = bids[bid.clientId] ?: return
            val wanted = b.cancelWanted
            bids[bid.clientId] = b.copy(orderId = id, status = if (b.status == LiveBidStatus.SENDING) LiveBidStatus.SENT else b.status, ackedAtMs = now)
            sample(placeAckMs, now - t0)
            dirty = true
            cancelNow = wanted
        }
        if (cancelNow) cancelOne(bid.clientId, bids[bid.clientId]?.why ?: "pulled while being sent")
        checkTiming()
        signals.trySend(Unit)
    }

    private class WaitedTooLong : RuntimeException()

    private fun refused(bid: LiveBid, e: NovigApiException) {
        val now = clock()
        synchronized(mu) {
            bids[bid.clientId]?.let { bids[bid.clientId] = it.copy(status = LiveBidStatus.REFUSED, endedAtMs = now, why = e.advice) }
            refused++
            coolUntil[bid.outcomeId] = now + REFUSED_COOLOFF_MS
            when {
                e.status == 429 -> backoffUntil = now + BACKOFF_MS
                e.status == 451 || e.status == 423 && e.code !in LiveTradeLimits.MARKET_CODES || e.status == 401 || e.status == 403 -> { standDownUntil = now + LiveTradeLimits.STAND_DOWN_MS; standDownWhy = e.brief }
                e.status == 422 -> { standDownUntil = now + 60_000L; standDownWhy = e.advice }
                e.code in LiveTradeLimits.MARKET_CODES -> marketBlockedUntil[bid.marketId] = now + LiveTradeLimits.BLACKLIST_MS
            }
            problem = "${bid.selection}: ${e.brief}"
            dirty = true
        }
        event("END", bid, text = "refused: ${e.advice}")
    }

    private fun lostAnswer(bid: LiveBid, e: Exception) {
        val now = clock()
        var bad = false
        synchronized(mu) {
            bids[bid.clientId]?.let { bids[bid.clientId] = it.copy(status = LiveBidStatus.SENT, lostAtMs = now, ackedAtMs = null) }
            lostAnswers++
            problem = "${bid.selection}: Novig's answer to the order never came (${e.message ?: e.javaClass.simpleName}); looked for by its client id"
            coolUntil[bid.outcomeId] = now + REFUSED_COOLOFF_MS
            dirty = true
            bad = lostAnswers >= MAX_LOST
        }
        event("END", bid, text = "answer lost: ${e.message ?: e.javaClass.simpleName}")
        if (bad) halt("Novig's answers to orders keep getting lost ($MAX_LOST today): check Novig's orders and the Tracker")
    }

    // ---- pulling -------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** One bid down. Plain function: it only records the intent and starts the cancel on its own coroutine. */
    private fun cancelOne(clientId: String, why: String) {
        var send: LiveBid? = null
        val now = clock()
        synchronized(mu) {
            val b = bids[clientId] ?: return
            if (!b.active) return
            when (b.status) {
                LiveBidStatus.SENDING -> bids[clientId] = b.copy(cancelWanted = true, why = b.why ?: why)
                LiveBidStatus.CANCELING -> return
                else -> {
                    val nb = b.copy(status = LiveBidStatus.CANCELING, cancelSentAtMs = now, why = why)
                    bids[clientId] = nb
                    cancelTriedAt[clientId] = now
                    send = nb
                }
            }
            pullCounts[why] = (pullCounts[why] ?: 0) + 1
            // A bid just pulled is not put straight back (the reason that pulled it is still in the air).
            coolUntil[b.outcomeId] = maxOf(coolUntil[b.outcomeId] ?: 0L, now + PULL_COOLOFF_MS)
            dirty = true
        }
        val nb = send ?: return
        event("PULL", nb, text = why)
        if (nb.real) sendCancel(listOf(nb)) else signals.trySend(Unit)
    }

    private suspend fun cancelMany(ids: List<String>, why: String) {
        val sends = ArrayList<LiveBid>()
        val now = clock()
        synchronized(mu) {
            for (id in ids) {
                val b = bids[id] ?: continue
                if (!b.active) continue
                when (b.status) {
                    LiveBidStatus.SENDING -> bids[id] = b.copy(cancelWanted = true, why = b.why ?: why)
                    LiveBidStatus.CANCELING -> {}
                    else -> {
                        val nb = b.copy(status = LiveBidStatus.CANCELING, cancelSentAtMs = now, why = why)
                        bids[id] = nb
                        cancelTriedAt[id] = now
                        sends += nb
                        pullCounts[why] = (pullCounts[why] ?: 0) + 1
                    }
                }
            }
            dirty = true
        }
        sends.forEach { event("PULL", it, text = why) }
        val real = sends.filter { it.real && it.orderId != null }
        if (real.isNotEmpty()) sendCancelNow(real)
        signals.trySend(Unit)
    }

    private fun sendCancel(bids: List<LiveBid>) {
        val real = bids.filter { it.orderId != null }
        if (real.isEmpty()) return
        scope.launch { withContext(NonCancellable) { sendCancelNow(real) } }
    }

    private suspend fun sendCancelNow(bids: List<LiveBid>) {
        val o = orders ?: return
        val now = clock()
        synchronized(mu) { bids.forEach { cancelTriedAt[it.clientId] = now } }
        try {
            val res = if (bids.size == 1) mapOf(bids[0].orderId!! to o.cancel(listOf(bids[0].orderId!!)).values.firstOrNull()) else o.cancel(bids.map { it.orderId!! })
            if (res.values.any { it == "FILLED" }) { pokeSettle = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            synchronized(mu) { problem = "pull: ${e.brief}" }
        } catch (e: Exception) {
            synchronized(mu) { problem = "pull: ${e.message ?: e.javaClass.simpleName} (tried again in a moment)" }
        }
        pokeSettle = true
        signals.trySend(Unit)
    }

    /** A cancel that has not been confirmed gone is sent again every few seconds. */
    private fun retryCancels(now: Long) {
        val again = synchronized(mu) {
            bids.values.filter { it.real && it.status == LiveBidStatus.CANCELING && it.orderId != null && now - (cancelTriedAt[it.clientId] ?: 0L) >= CANCEL_RETRY_MS }
        }
        if (again.isNotEmpty()) sendCancel(again)
    }

    // ---- settling (real) ------------------------------------------------------------------------------------------------------------------------------------------------------

    private suspend fun settleOnce() {
        val o = orders ?: return
        val live = synchronized(mu) { bids.values.filter { it.real && it.active } }
        if (live.isEmpty()) return
        val open = try {
            o.open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(mu) { problem = "Novig's open orders: ${(e as? NovigApiException)?.brief ?: e.message ?: e.javaClass.simpleName}" }
            return
        }
        val byId = open.associateBy { it.orderId }
        val byClient = open.mapNotNull { x -> x.clientId?.let { it to x } }.toMap()
        val now = clock()
        val reads = ArrayList<Pair<LiveBid, NovigOrder?>>()
        val lookups = ArrayList<LiveBid>()
        val unseen = ArrayList<LiveBid>()
        synchronized(mu) {
            for (b0 in live) {
                val b = bids[b0.clientId] ?: continue
                if (!b.active) continue
                val ord = b.orderId?.let { byId[it] } ?: byClient[b.clientId]
                if (ord != null) {
                    var nb = b
                    if (nb.orderId == null) nb = nb.copy(orderId = ord.orderId, lostAtMs = null)
                    if (nb.openAtMs == null) {
                        nb = nb.copy(openAtMs = now, status = if (nb.status == LiveBidStatus.SENT || nb.status == LiveBidStatus.SENDING) LiveBidStatus.RESTING else nb.status)
                        sample(placeOpenMs, now - nb.postedAtMs)
                    }
                    bids[nb.clientId] = nb
                    dirty = true
                    if (ord.qty - ord.remaining > nb.filled) reads += nb to ord
                    continue
                }
                if (b.orderId == null) {
                    if (b.lostAtMs != null && now - b.lostAtMs >= LOST_LOOKUP_MS) lookups += b
                    continue
                }
                // Not on the book. One never seen there may be queued (a live order waits out Novig's in-play delay as PENDING, and the list can lag a 201): its own record says.
                if (b.openAtMs == null) { unseen += b; continue }
                reads += b to null
            }
        }
        for (b in lookups) lookup(b, o)
        for (b in unseen) {
            val id = b.orderId ?: continue
            val rec = try {
                o.order(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val age = now - (b.ackedAtMs ?: b.postedAtMs)
            when {
                // Queued (PENDING) or not yet listed (404 just after the 201): waited for, up to a long delay.
                rec == null && age < QUEUE_WAIT_MS -> {}
                rec != null && !rec.terminal && rec.status != "OPEN" -> {}
                rec != null && !rec.terminal -> {
                    // OPEN but the list did not have it yet: seen now.
                    synchronized(mu) {
                        bids[b.clientId]?.let {
                            if (it.openAtMs == null) { bids[b.clientId] = it.copy(openAtMs = now, status = if (it.status == LiveBidStatus.SENT) LiveBidStatus.RESTING else it.status); sample(placeOpenMs, now - it.postedAtMs); dirty = true }
                        }
                    }
                }
                else -> reads += b to rec
            }
        }
        // Bids that ended with no fill are looked at again for a late one (the fills list can lag the order's end).
        val audit = synchronized(mu) {
            bids.values.filter { b ->
                val ended = b.endedAtMs ?: return@filter false
                val age = now - ended
                b.real && b.status.ended && b.filled == 0L && b.orderId != null && (b.status == LiveBidStatus.CANCELED || b.status == LiveBidStatus.EXPIRED) && age in AUDIT_FIRST_MS..AUDIT_END_MS &&
                    (audits[b.clientId] ?: 0) < (if (age >= AUDIT_SECOND_MS) 2 else 1)
            }
        }
        val ids = (reads.mapNotNull { it.first.orderId } + audit.mapNotNull { it.orderId }).distinct()
        if (ids.isEmpty()) { checkTiming(); return }
        val startsAfter = (reads.map { it.first.startsTs } + audit.map { it.startsTs }).minOrNull()?.minus(FILLS_LOOKBACK_MS) ?: 0L
        val fillsBy = try {
            o.fillsOf(ids, startsAfter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(mu) { problem = "Novig's fills: ${(e as? NovigApiException)?.brief ?: e.message ?: e.javaClass.simpleName} (read again in a moment)" }
            return
        }
        for ((b, ord) in reads) {
            val id = b.orderId ?: continue
            finish(b.clientId, ord, ended = ord == null || ord.terminal, fills = fillsBy[id].orEmpty())
        }
        for (b in audit) {
            val id = b.orderId ?: continue
            val age = now - (b.endedAtMs ?: now)
            synchronized(mu) { audits[b.clientId] = if (age >= AUDIT_SECOND_MS) 2 else 1 }
            val late = fillsBy[id].orEmpty()
            if (late.isNotEmpty()) lateFill(b.clientId, late)
        }
        checkTiming()
    }

    /** A fill that showed up after its bid was written down as over with none: recorded, the bid corrected. */
    private suspend fun lateFill(clientId: String, fills: List<NovigFill>) {
        val all = fills.distinctBy { it.fillId }
        val qty = all.sumOf { it.qty }
        if (qty <= 0L) return
        val now = clock()
        var snapshot: LiveBid? = null
        synchronized(mu) {
            val b = bids[clientId] ?: return
            if (b.filled >= qty) return
            val nb = b.copy(
                filled = qty, paid = all.sumOf { it.cost }, firstFillAtMs = b.firstFillAtMs ?: all.map { it.ts }.filter { it > 0 }.minOrNull() ?: now,
                status = if (qty >= b.contracts) LiveBidStatus.FILLED else b.status, why = (b.why ?: b.status.label) + " (a fill showed up late)",
            )
            bids[clientId] = nb
            coolUntil[nb.outcomeId] = now + config().quality.coolOffSec * 1000L
            dirty = true
            snapshot = nb
        }
        val nb = snapshot ?: return
        event("FILL", nb, value = nb.filled.toDouble(), text = "late: ${nb.filled} of ${nb.contracts} for ${money(nb.paid)}")
        val orderId = nb.orderId ?: return
        val betId = runCatching { logFills(nb.target(version), orderId, all) }.getOrNull()
        if (betId != null) synchronized(mu) { bids[clientId]?.let { bids[clientId] = it.copy(betId = betId) }; dirty = true }
    }

    /** A bid whose answer was lost: found by its client id (in any status), else called gone after a while. */
    private suspend fun lookup(b: LiveBid, o: LiveBidOrders) {
        val found = try {
            o.find(b.clientId, b.outcomeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val now = clock()
        if (found != null) {
            synchronized(mu) { bids[b.clientId]?.let { bids[b.clientId] = it.copy(orderId = found.orderId, lostAtMs = null) } }
            if (b.status != LiveBidStatus.CANCELING && found.status == "OPEN") cancelOne(b.clientId, "its answer had been lost")
            pokeSettle = true
            return
        }
        if (now - (b.lostAtMs ?: now) >= LOST_GIVE_UP_MS) {
            synchronized(mu) { bids[b.clientId]?.let { bids[b.clientId] = it.copy(status = LiveBidStatus.LOST, endedAtMs = now, why = "Novig's lists do not show it") }; dirty = true }
            event("END", b, text = "not found on Novig")
        }
    }

    /** A bid's fills are in ([ended]: it left the book): recorded in the Tracker, its end named, a partly filled one pulled. */
    private suspend fun finish(clientId: String, ord: NovigOrder?, ended: Boolean, fills: List<NovigFill>) {
        val now = clock()
        val all = fills.distinctBy { it.fillId }
        val qty = all.sumOf { it.qty }
        val paid = all.sumOf { it.cost }
        var snapshot: LiveBid? = null
        var pullRest = false
        var newFill = false
        synchronized(mu) {
            val b = bids[clientId] ?: return
            if (!b.active) return
            val filled = maxOf(b.filled, qty)
            newFill = filled > b.filled
            val avg = if (qty > 0) paid / (qty * EvMath.CONTRACT_PAYOUT_DOLLARS) else b.price
            var nb = b.copy(
                filled = filled, paid = maxOf(b.paid, paid), firstFillAtMs = b.firstFillAtMs ?: all.map { it.ts }.filter { it > 0 }.minOrNull() ?: if (filled > 0) now else null,
                strict = b.strict || (qty > 0 && avg < b.price - 1e-6),
            )
            if (ended) {
                val status = when {
                    ord?.status == "REJECTED" -> LiveBidStatus.REFUSED
                    filled >= nb.contracts -> LiveBidStatus.FILLED
                    nb.status == LiveBidStatus.CANCELING -> LiveBidStatus.CANCELED
                    now >= nb.expiresAtMs - EXPIRY_SLACK_MS -> LiveBidStatus.EXPIRED
                    else -> LiveBidStatus.CANCELED
                }
                val why = when (status) {
                    LiveBidStatus.REFUSED -> "Novig refused it (a post-only bid that would have taken, or the order itself)"
                    LiveBidStatus.CANCELED -> nb.why ?: "Novig ended it"
                    else -> nb.why
                }
                nb = nb.copy(status = status, endedAtMs = now, why = why)
                if (status == LiveBidStatus.CANCELED && nb.cancelSentAtMs != null) sample(pullMs, now - nb.cancelSentAtMs!!)
                if (status == LiveBidStatus.REFUSED) { refused++; coolUntil[nb.outcomeId] = now + REFUSED_COOLOFF_MS }
            } else if (filled >= nb.contracts) {
                nb = nb.copy(status = LiveBidStatus.FILLED, endedAtMs = now)
            } else if (newFill && nb.status != LiveBidStatus.CANCELING) {
                pullRest = true
            }
            if (newFill) coolUntil[nb.outcomeId] = now + (config().quality.coolOffSec * 1000L)
            bids[clientId] = nb
            snapshot = nb
            dirty = true
        }
        val nb = snapshot ?: return
        if (nb.status.ended) event("END", nb, text = nb.why ?: nb.status.label)
        if (newFill) {
            event("FILL", nb, value = nb.filled.toDouble(), text = "${nb.filled} of ${nb.contracts} for ${money(nb.paid)}${if (nb.strict) " (through the price)" else ""}")
            val orderId = nb.orderId ?: return
            val betId = runCatching { logFills(nb.target(version), orderId, all) }.getOrNull()
            if (betId != null) synchronized(mu) { bids[clientId]?.let { bids[clientId] = it.copy(betId = betId) }; dirty = true }
        }
        if (pullRest) cancelOne(clientId, "filled in part: the rest is pulled")
    }

    // ---- paper ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    private fun paperFill(clientId: String, c: BookChange, atMs: Long) {
        var snap: LiveBid? = null
        var pullRest = false
        synchronized(mu) {
            val b = bids[clientId] ?: return
            if (!b.active || b.real) return
            val take = minOf(b.remaining, c.qty)
            if (take <= 0L) return
            val strict = c.priceMilli < Math.round(b.price * 1000).toInt()
            val filled = b.filled + take
            var nb = b.copy(filled = filled, paid = b.paid + take * b.price * EvMath.CONTRACT_PAYOUT_DOLLARS, firstFillAtMs = b.firstFillAtMs ?: atMs, strict = b.strict || strict)
            if (filled >= nb.contracts) nb = nb.copy(status = LiveBidStatus.FILLED, endedAtMs = atMs) else if (nb.status != LiveBidStatus.CANCELING) pullRest = true
            coolUntil[nb.outcomeId] = atMs + config().quality.coolOffSec * 1000L
            bids[clientId] = nb
            snap = nb
            dirty = true
        }
        val nb = snap ?: return
        event("FILL", nb, value = nb.filled.toDouble(), text = "paper: ${nb.filled} of ${nb.contracts}${if (nb.strict) " (through the price)" else " (at the price)"}")
        if (nb.status.ended) event("END", nb, text = "filled")
        if (pullRest) cancelOne(clientId, "filled in part: the rest is pulled")
    }

    /** Paper bids end when their pull has had the delay a real one takes, or their time is up. */
    private fun paperTick(now: Long) {
        val ended = ArrayList<LiveBid>()
        synchronized(mu) {
            for (b in bids.values.toList()) {
                if (!b.active || b.real) continue
                val over = when {
                    b.status == LiveBidStatus.CANCELING && b.cancelSentAtMs != null && now >= b.cancelSentAtMs + PAPER_LATENCY_MS -> LiveBidStatus.CANCELED
                    now >= b.expiresAtMs -> LiveBidStatus.EXPIRED
                    else -> null
                }
                if (over != null) {
                    val nb = b.copy(status = over, endedAtMs = now)
                    bids[b.clientId] = nb
                    ended += nb
                    dirty = true
                }
            }
        }
        ended.forEach { event("END", it, text = it.why ?: it.status.label) }
    }

    // ---- self-checks -----------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Fills are judged against the fair 30 s and 120 s after (what the bid really kept); a run of picked-off fills halts the feature. */
    private fun followUps(now: Long) {
        var haltWhy: String? = null
        val logged = ArrayList<LiveBid>()
        synchronized(mu) {
            for (b in bids.values.toList()) {
                val t = b.firstFillAtMs ?: continue
                if (b.fairAt30 == null && now - t >= 30_000L) {
                    val f = latestFair[b.outcomeId]?.takeIf { now - it.second <= FAIR_FRESH_MS }?.first
                    if (f != null) {
                        val picked = f < b.price
                        bids[b.clientId] = b.copy(fairAt30 = f, pickedOff = picked)
                        logged += bids[b.clientId]!!
                        // Only real money stops the feature; a paper fill is judged and counted the same but never halts it.
                        if (b.real) {
                            judged.addLast(picked)
                            val q = config().quality
                            while (judged.size > maxOf(q.pickOffWindow, 1)) judged.removeFirst()
                            if (q.pickOffLimit > 0 && judged.size >= q.pickOffWindow.coerceAtLeast(1) && judged.count { it } >= q.pickOffLimit && halted == null) {
                                haltWhy = "${judged.count { it }} of the last ${judged.size} live bid fills were picked off (Pinnacle's fair 30 s later was under the price paid)"
                            }
                        }
                        dirty = true
                    }
                }
                val cur = bids[b.clientId] ?: continue
                if (cur.fairAt120 == null && now - t >= 120_000L) {
                    val f = latestFair[cur.outcomeId]?.takeIf { now - it.second <= FAIR_FRESH_MS }?.first
                    if (f != null) { bids[cur.clientId] = cur.copy(fairAt120 = f); logged += bids[cur.clientId]!!; dirty = true }
                }
            }
        }
        logged.forEach { event("FOLLOW", it, value = it.fairAt120 ?: it.fairAt30, text = "fair ${"%.3f".format(Locale.US, it.fairAt120 ?: it.fairAt30 ?: 0.0)} against ${"%.3f".format(Locale.US, it.price)}") }
        haltWhy?.let { halt(it) }
    }

    private fun sample(q: ArrayDeque<Long>, ms: Long) {
        q.addLast(ms)
        while (q.size > TIMING_KEEP) q.removeFirst()
    }

    /** A pull or a placement that measures too slow stops the feature: a bid that cannot be pulled fast, or that lands late, is not safe to rest. */
    private fun checkTiming() {
        val q = config().quality
        var why: String? = null
        synchronized(mu) {
            if (halted != null) return
            val real = bids.values.any { it.real }
            if (!real) return
            if (q.maxCancelSec > 0 && pullMs.size >= TIMING_MIN && p90(pullMs) > q.maxCancelSec * 1000L) why = "pulling a bid takes ${p90(pullMs) / 1000} s on this connection (limit ${q.maxCancelSec} s)"
            if (why == null && q.maxPlaceSec > 0 && placeOpenMs.size >= TIMING_MIN && p90(placeOpenMs) > q.maxPlaceSec * 1000L) why = "a bid takes ${p90(placeOpenMs) / 1000} s to reach Novig's book (limit ${q.maxPlaceSec} s)"
        }
        why?.let { halt(it) }
    }

    private fun p90(q: ArrayDeque<Long>): Long = q.sorted().let { it[(0.9 * (it.size - 1)).toInt()] }

    private suspend fun checkLoss(now: Long) {
        if (now - lastLossCheckMs < LOSS_EVERY_MS) return
        lastLossCheckMs = now
        val cfg = config()
        if (!cfg.on || cfg.limits.haltLoss <= 0.0 || synchronized(mu) { halted != null }) return
        val loss = runCatching { lossToday() }.getOrDefault(0.0)
        if (loss >= cfg.limits.haltLoss) halt("live bids lost ${money(loss)} today (the limit is ${money(cfg.limits.haltLoss)})")
    }

    // ---- bookkeeping ---------------------------------------------------------------------------------------------------------------------------------------------------------

    private fun count(reason: String) {
        synchronized(mu) { skipCounts[reason] = (skipCounts[reason] ?: 0) + 1 }
    }

    private fun event(type: String, b: LiveBid? = null, value: Double? = null, text: String? = null) {
        val j = journal ?: return
        runCatching {
            j.append(
                LiveBidEvent(
                    atMs = clock(), type = type, bidId = b?.clientId, mode = b?.mode, league = b?.league, event = b?.eventName, selection = b?.selection, price = b?.price, fair = b?.fair,
                    value = value, text = text,
                ),
            )
        }
    }

    private suspend fun persist(now: Long, force: Boolean = false) = persistLock.withLock {
        val list = synchronized(mu) {
            if (!force && (!dirty || now - lastPersistMs < PERSIST_EVERY_MS)) return@withLock
            dirty = false
            lastPersistMs = now
            val keep = bids.values.filter { it.active || (it.endedAtMs ?: it.postedAtMs) >= now - LiveBidStore.KEEP_MS }
            if (keep.size != bids.size) { bids.clear(); keep.forEach { bids[it.clientId] = it } }
            replaced.retainAll(bids.keys)
            cancelTriedAt.keys.retainAll(bids.keys)
            audits.keys.retainAll(bids.keys)
            keep.toList()
        }
        _bids.value = list
        runCatching { store.replace(list) }
        Unit
    }

    private fun publish(now: Long, cfg: LiveBidConfig) {
        val s = synchronized(mu) {
            val day = ApiBetPlacer.localMidnight(now)
            val active = bids.values.filter { it.active }
            val today = bids.values.filter { it.postedAtMs >= day }
            LiveBidDeskStatus(
                mode = if (!cfg.on) "off" else if (cfg.real) "real" else "paper", active = active.size, restingDollars = active.sumOf { it.restingDollars }, posted = today.size,
                fills = today.count { it.filled > 0 }, paid = today.sumOf { it.paid }, pulls = pullCounts.toMap(), skips = skipCounts.toMap(), refused = refused, halted = halted ?: cfg.halted,
                standDown = if (now < standDownUntil) standDownWhy else null, timing = timingLine(), last = last, problem = problem,
            )
        }
        _status.value = s
    }

    private fun timingLine(): String = buildList {
        if (placeAckMs.isNotEmpty()) add("order accepted in ${median(placeAckMs)} ms")
        if (placeOpenMs.isNotEmpty()) add("on the book in ${median(placeOpenMs)} ms")
        if (pullMs.isNotEmpty()) add("pulled in ${median(pullMs)} ms")
    }.joinToString(" · ")

    private fun median(q: ArrayDeque<Long>): Long = q.sorted().let { it[it.size / 2] }

    private fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)

    companion object {
        const val TICK_MS = 250L
        const val POLL_MS = 1_000L

        /** A resting bid nobody vouched for this long comes down; the runner judges every half second and on every Pinnacle frame. */
        const val CLAIM_TTL_MS = 3_500L

        /** A wanted bid older than this is no longer wanted. */
        const val WANT_TTL_MS = 2_000L

        /** New orders per 250 ms step: Novig's `place` bucket refills 8 a second. */
        const val MAX_POSTS_PER_STEP = 2
        const val MIN_CONTRACTS = 5L
        const val MIN_TTL_SEC = 10

        /** What a real order takes to reach the book, and a cancel to land, in play (RESEARCH.md §120.1: 5.3 s): the delay paper bids are given. */
        const val PAPER_LATENCY_MS = 5_300L
        const val REFUSED_COOLOFF_MS = 5 * 60_000L
        const val PULL_COOLOFF_MS = 10_000L
        const val BACKOFF_MS = 10_000L
        const val CANCEL_RETRY_MS = 2_500L
        const val LOST_LOOKUP_MS = 2_000L
        const val LOST_GIVE_UP_MS = 90_000L
        const val EXPIRY_SLACK_MS = 2_000L
        const val FAIR_FRESH_MS = 10_000L
        const val MAX_LOST = 3
        const val PERSIST_EVERY_MS = 1_000L
        const val LOCK_WAIT_MAX_MS = 2_000L

        /** A queued order (PENDING, or not yet listed) is waited for this long before it is called lost. */
        const val QUEUE_WAIT_MS = 30_000L
        const val LOSS_EVERY_MS = 60_000L
        /** A bid that ended with no fill is looked at again for a late one at these ages (the fills list can lag the order's end). */
        const val AUDIT_FIRST_MS = 3_000L
        const val AUDIT_SECOND_MS = 15_000L
        const val AUDIT_END_MS = 60_000L
        const val FILLS_LOOKBACK_MS = 24 * 3_600_000L
        const val TIMING_KEEP = 10
        const val TIMING_MIN = 5
    }
}
