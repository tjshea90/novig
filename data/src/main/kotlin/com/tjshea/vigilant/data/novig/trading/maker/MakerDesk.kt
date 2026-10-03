package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.EvMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Where one of Vigilant's bids is. */
@Serializable
enum class MakerStatus(val label: String, val ended: Boolean) {
    /** Sent; Novig's answer (the order id) not back yet, or lost. */
    SENT("Sent", false),
    RESTING("Resting", false),
    /**
     * Cancel sent; Novig hasn't confirmed it left the book. A fill can still land in that gap (NOVIG_API.md §17: "A fill can land between the cancel and
     * the place"), so it's watched until Novig says it ended: no new bid on its side until then.
     */
    CANCELING("Cancelling", false),
    FILLED("Filled", true),
    CANCELED("Cancelled", true),
    EXPIRED("Expired", true),
    /** Novig voided it at the start (`GOLIVE`) or closed the market. */
    VOIDED("Voided at the start", true),
    /** Novig refused it (post-only and it would have taken, or the order itself). */
    REFUSED("Refused", true),
    /** Novig has no trace of it after its answer was lost. */
    LOST("Not found on Novig", true),
}

/** One bid Vigilant posted ([MakerDesk]), kept for the Make tab and its numbers after it ends. */
@Serializable
data class MakerBid(
    val clientId: String,
    val orderId: String? = null,
    val marketId: String,
    val eventId: String,
    val outcomeId: String,
    val league: String,
    val eventName: String,
    val startsTs: Long,
    val marketLabel: String,
    val selection: String,
    val kind: BetKind = BetKind.OTHER,
    val price: Double,
    val contracts: Long,
    /** Vigilant's fair and the EV at it when posted ([MakerDecision.Post.evAtFair]). */
    val fair: Double,
    val evAtFair: Double,
    val margin: Double,
    val books: Int = 0,
    val source: String = BetTracker.SOURCE_VIGILANT,
    val gameUrl: String? = null,
    val fairBasis: FairBasis? = null,
    val postedAtMs: Long,
    val expiresAtMs: Long? = null,
    val status: MakerStatus = MakerStatus.SENT,
    /** Contracts filled so far, and what they cost. */
    val filled: Long = 0,
    val paid: Double = 0.0,
    val endedAtMs: Long? = null,
    /** Why it was cancelled or ended, in words. */
    val why: String? = null,
    /** Posted by the automatic cycle (false: Tj's Post button). */
    val auto: Boolean = true,
    /** The Tracker bet its fills became. */
    val betId: String? = null,
) {
    val active: Boolean get() = !status.ended

    /** Up on Novig as far as Vigilant knows (sent or resting), not on its way down. */
    val resting: Boolean get() = status == MakerStatus.SENT || status == MakerStatus.RESTING
    val cost: Double get() = contracts * price * EvMath.CONTRACT_PAYOUT_DOLLARS
    val restingDollars: Double get() = (contracts - filled).coerceAtLeast(0) * price * EvMath.CONTRACT_PAYOUT_DOLLARS

    fun target(): BetTarget = BetTarget(
        // Only the ids matter to the Tracker's record of the fills; Novig's catalog has the rest.
        market = NovigMarket(marketId, eventId, "", "OPEN", marketLabel, startsTs, null, emptyList()),
        outcomeId = outcomeId, league = league, eventName = eventName, startsTs = startsTs, marketLabel = marketLabel, selection = selection,
        fair = fair, fairAsOfMs = postedAtMs, source = source, gameUrl = gameUrl, basis = fairBasis, auto = auto,
    )
}

/** Every bid posted, newest last; ended ones are kept [KEEP_MS] for the Make tab's numbers. */
class MakerStore(file: File) {
    private val store = JsonFileStore(file, ListSerializer(MakerBid.serializer()), { emptyList() })
    val flow get() = store.flow
    suspend fun all(): List<MakerBid> = store.read()
    suspend fun update(transform: (List<MakerBid>) -> List<MakerBid>) = store.update(transform)

    companion object {
        const val KEEP_MS = 14 * 24 * 3_600_000L
    }
}

/** A bid Tj denied (or cancelled by hand): no bid on that side again before its game starts, unless he undoes it. */
@Serializable
data class DeniedBid(val outcomeId: String, val startsTs: Long, val selection: String, val deniedAtMs: Long)

/**
 * Tj's "no" to bids (Tj, 2026-10-03: "recommend bets to make and I manually approve or deny them"): a denied side gets no bid, by hand or by
 * auto-make, until its game starts or he undoes it (files/maker_denied.json).
 */
class MakerDenials(file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val store = JsonFileStore(file, ListSerializer(DeniedBid.serializer()), { emptyList() })
    val flow get() = store.flow

    /** The sides denied at [now] (games not started). */
    suspend fun all(now: Long = clock()): List<DeniedBid> = store.read().filter { it.startsTs > now }

    suspend fun outcomes(now: Long = clock()): Set<String> = all(now).mapTo(HashSet()) { it.outcomeId }

    suspend fun deny(outcomeId: String, startsTs: Long, selection: String) {
        val now = clock()
        store.update { list -> list.filter { it.startsTs > now && it.outcomeId != outcomeId } + DeniedBid(outcomeId, startsTs, selection, now) }
    }

    suspend fun undo(outcomeId: String) {
        store.update { list -> list.filterNot { it.outcomeId == outcomeId } }
    }
}

/**
 * Make orders (Tj, 2026-10-03: "build the system in the app"; RESEARCH.md §70, NOVIG_API.md §17): Vigilant's bids under its fair price, posted
 * post-only with an expiry from the Vigilant wallet, re-priced as the fair moves, and every fill recorded in the Tracker as a bet
 * ([BetTracker.logMakerFills], [TrackedBet.maker]). Suspending throughout: the caller (the app's background cycle, a scan's end, or a tap) owns
 * each call. Every order goes through [lock], the one the Bet sheet, the auto-bet and the locks share, so nothing here interleaves with a bet.
 *
 * Money safety: a bid is `PO` (it can never take) with a `ttl` (it can't outlive Vigilant watching it); a lost answer is never re-sent (it's looked
 * for by its `clientId`); an open post-only order Vigilant has no record of is cancelled; [cycle] with a stop reason (paused, the wallet empty,
 * the day's limit, bids off) cancels every bid; the same side is never bought twice; new bids never ask for more than the wallet holds.
 */
class MakerDesk(
    private val trading: NovigTradingClient,
    private val tracker: BetTracker,
    private val store: MakerStore,
    private val lock: Mutex = Mutex(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val dayStart: (Long) -> Long = { ApiBetPlacer.localMidnight(it) },
    private val pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    /** What a cycle did. [fills]: Tracker bets that are new or grew this cycle. */
    data class Report(
        val placed: Int,
        val cancelled: Int,
        val fills: List<TrackedBet>,
        val problems: List<String>,
        val resting: Int,
        val stopped: String?,
        val decisions: List<MakerDecision>,
        /** Bids wanted but not posted this pass, by why ([MakerActions.waiting]); empty with auto-make off (nothing is posted by a pass then). */
        val waiting: Map<String, Int> = emptyMap(),
        /** The pass judged a scan still running (only its finished leagues' lines). */
        val partial: Boolean = false,
    )

    /**
     * One pass: what Novig says happened to every bid (fills to the Tracker), then the bids [lines] want under [rules] at [now], then the cancels and
     * the new bids. [stop]: why every bid comes down instead (null = post). [maxPerDay]: the most a day's API bets may add up to (fills count; resting
     * bids may not push it over).
     */
    suspend fun cycle(
        lines: List<MakerLine>,
        rules: MakerRules,
        stop: String?,
        maxPerDay: Double,
        wallet: Double?,
        /** Sides Tj denied ([MakerDenials]): no bid there, and a resting one comes down. */
        denied: Set<String> = emptySet(),
        /** Auto-make on: post, move and re-post. Off: only take down bids that stopped being worth it ([MakerPlan.plan]'s repost). */
        autoPost: Boolean = true,
        /** [lines] are a running scan's ([MakerPlan.plan]'s partial): a bid whose line isn't among them stays up for a later pass to judge. */
        partial: Boolean = false,
    ): Report = lock.withLock {
        val problems = ArrayList<String>()
        val fills = settle(problems)
        val now = clock()
        val bids = store.all()
        val active = bids.filter { it.resting && it.orderId != null }
        val bets = tracker.all()
        val activeOutcomes = active.mapTo(HashSet()) { it.outcomeId }
        // Busy: a bid on its way down (a fill can still land) or one whose answer was lost. Never a second bid on that side meanwhile.
        val busy = bids.filter { it.status == MakerStatus.CANCELING || (it.status == MakerStatus.SENT && it.orderId == null) }.mapTo(HashSet()) { it.outcomeId }
        val held = (bets.filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId } - activeOutcomes) + busy
        val refused = bids.filter { it.status == MakerStatus.REFUSED && now - (it.endedAtMs ?: it.postedAtMs) < REFUSED_COOLOFF_MS }.mapTo(HashSet()) { it.outcomeId }
        val decisions = MakerQuote.decideAll(lines, rules, now, held).map { d ->
            if (d is MakerDecision.Post && d.line.outcomeId in denied) return@map MakerDecision.Skip(d.line, DENIED)
            // Novig refused a bid here a moment ago (post-only: its price had moved to the bid): not asked again until the cool-off passes.
            if (d is MakerDecision.Post && d.line.outcomeId in refused && d.line.outcomeId !in activeOutcomes) {
                MakerDecision.Skip(d.line, "Novig refused a bid here in the last ${REFUSED_COOLOFF_MS / 60_000} min (its price had moved to the bid)")
            } else d
        }
        val spent = spentToday(bets, now)
        val stopAll = stop ?: if (spent >= maxPerDay - 1e-9) "Today's limit for API bets (${money(maxPerDay)}) is reached" else null
        val resting = active.map { RestingBid(it.orderId!!, it.marketId, it.outcomeId, it.price, (it.contracts - it.filled).coerceAtLeast(0), it.filled, it.expiresAtMs) }
        val budget = minOf(wallet ?: Double.MAX_VALUE, (maxPerDay - spent - resting.sumOf { it.restingDollars }).coerceAtLeast(0.0))
        val actions = MakerPlan.plan(
            wanted = decisions.filterIsInstance<MakerDecision.Post>(), resting = resting, rules = rules, now = now,
            skips = decisions.filterIsInstance<MakerDecision.Skip>().associate { it.line.outcomeId to it.why }, stopAll = stopAll, budget = budget,
            repost = autoPost, partial = partial,
        )
        var cancelled = 0
        val noReplace = HashSet<String>()
        withContext(NonCancellable) {
            for ((r, why) in actions.cancels) {
                when (cancelOne(r.orderId, why, problems)) {
                    // Gone, confirmed: its side can be bid again now.
                    Cancel.GONE -> cancelled++
                    // On its way down but not confirmed, or it filled first, or Novig refused: nothing new on that side this pass.
                    Cancel.PENDING -> { cancelled++; noReplace += r.outcomeId }
                    Cancel.FILLED, Cancel.FAILED -> noReplace += r.outcomeId
                }
            }
        }
        var placed = 0
        val wantedLines = actions.places.filter { it.line.outcomeId !in noReplace }
        for (post in wantedLines) {
            val result = withContext(NonCancellable) { placeOne(post, rules, auto = true) }
            when (result) {
                is Placed.Ok -> placed++
                is Placed.Refused -> {
                    problems += result.why
                    if (result.stopsCycle) break
                }
            }
        }
        val after = store.all().count { it.active }
        Report(placed, cancelled, fills, problems, after, stopAll, decisions, actions.waiting, partial)
    }

    /**
     * Only what Novig says happened to the bids (fills to the Tracker, expiries, voids), nothing posted or moved: for a pass with no fair prices to
     * judge by (no scan yet in this process). [problems] collects what went wrong.
     */
    suspend fun settleOnly(problems: MutableList<String> = ArrayList()): List<TrackedBet> = lock.withLock { settle(problems) }

    /** Every bid on record (active and ended, newest last), and the same as it changes. */
    suspend fun bids(): List<MakerBid> = store.all()
    val flow get() = store.flow

    /** Tj's Post button: one bid now, outside the cycle (the same checks: [decision] was worked out just now). */
    suspend fun post(decision: MakerDecision.Post, rules: MakerRules): String? = lock.withLock {
        if (store.all().any { it.active && it.outcomeId == decision.line.outcomeId }) return@withLock "There's already a bid on this side (or one on its way down)"
        when (val r = withContext(NonCancellable) { placeOne(decision, rules, auto = false) }) {
            is Placed.Ok -> null
            is Placed.Refused -> r.why
        }
    }

    /** Tj's Cancel on one bid. Null when Novig took the cancel; else why not. */
    suspend fun cancel(orderId: String): String? = lock.withLock {
        val problems = ArrayList<String>()
        withContext(NonCancellable) {
            when (cancelOne(orderId, "Cancelled by you", problems)) {
                Cancel.GONE, Cancel.PENDING -> null
                Cancel.FILLED -> "It filled before the cancel reached Novig (see the Tracker)"
                Cancel.FAILED -> problems.firstOrNull() ?: "Novig didn't take the cancel"
            }
        }
    }

    /** The bids auto-make posted down (auto-make switched off; the ones Tj approved by hand stay). How many cancels Novig took. */
    suspend fun cancelAuto(why: String): Int = lock.withLock {
        withContext(NonCancellable) {
            val problems = ArrayList<String>()
            store.all().filter { it.resting && it.auto && it.orderId != null }.count { cancelOne(it.orderId!!, why, problems) != Cancel.FAILED }
        }
    }

    /** Every bid down at once ([why]: paused, the wallet ran out, Tj's Cancel all): one `DELETE /v3/orders`, then each bid marked. */
    suspend fun cancelAll(why: String): Int = lock.withLock {
        withContext(NonCancellable) {
            val n = trading.cancelOrders()
            val now = clock()
            // On their way down: each is watched until Novig says it ended (a fill can still land first), then marked. Confirmed now, a few looks at
            // most (a pause or an empty wallet runs no further passes to finish the job).
            store.update { list -> list.map { if (it.active && it.orderId != null && it.status != MakerStatus.CANCELING) it.copy(status = MakerStatus.CANCELING, why = why) else it } }
            var looks = 0
            while (true) {
                settle(ArrayList())
                looks++
                if (looks >= CANCEL_LOOKS || store.all().none { it.status == MakerStatus.CANCELING }) break
                pause(CANCEL_LOOK_MS)
            }
            n
        }
    }

    // ---- inside the lock -----------------------------------------------------------------------------------------------

    private sealed interface Placed {
        data class Ok(val bid: MakerBid) : Placed
        data class Refused(val why: String, val stopsCycle: Boolean) : Placed
    }

    private suspend fun placeOne(post: MakerDecision.Post, rules: MakerRules, auto: Boolean): Placed {
        val now = clock()
        val line = post.line
        val bid = MakerBid(
            clientId = NovigTradingClient.newClientId(), marketId = line.marketId, eventId = line.market.eventId, outcomeId = line.outcomeId,
            league = line.league, eventName = line.eventName, startsTs = line.startsTs, marketLabel = line.marketLabel, selection = line.selection,
            kind = line.kind, price = post.price, contracts = post.contracts, fair = line.fair ?: post.price, evAtFair = post.evAtFair,
            margin = rules.margin, books = line.books, source = line.source, gameUrl = line.gameUrl, fairBasis = line.basis, postedAtMs = now,
            // Never past what it was priced from ([MakerDecision.Post.restUntilMs]: the expiry, the fair's freshness, the stop window before the start).
            expiresAtMs = minOf(now + rules.ttlMs, line.startsTs - rules.stopMs, post.restUntilMs), auto = auto,
        )
        val ttl = bid.expiresAtMs!! - now
        // Worked out a moment ago: if that window has closed since, nothing is sent.
        if (ttl < MIN_TTL_MS) return Placed.Refused("${line.selection}: its fair price is about to go old; re-priced at the next scan", stopsCycle = false)
        // Recorded before it's sent: an answer that never comes back is still a bid Vigilant knows to look for.
        store.update { it + bid }
        return try {
            val orderId = trading.placeOrder(line.outcomeId, post.price, post.contracts, "PO", bid.clientId, ttl)
            store.update { list -> list.map { if (it.clientId == bid.clientId) it.copy(orderId = orderId, status = MakerStatus.RESTING) else it } }
            Placed.Ok(bid.copy(orderId = orderId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            val why = "${line.selection}: ${e.advice}"
            store.update { list -> list.map { if (it.clientId == bid.clientId) it.copy(status = MakerStatus.REFUSED, endedAtMs = clock(), why = e.advice) else it } }
            // The wallet, the account, the location or the throttle: the same for every bid after this one.
            Placed.Refused(why, stopsCycle = e.status == 422 || e.status == 423 || e.status == 451 || e.status == 429 || e.status == 401 || e.status == 403)
        } catch (e: Exception) {
            // No answer: it may be resting. The next cycle finds it by its clientId; it's never sent again.
            Placed.Refused("${line.selection}: Novig didn't answer (${e.message ?: e.javaClass.simpleName}); looked for again next time", stopsCycle = true)
        }
    }

    /** What came of a cancel. */
    private enum class Cancel {
        /** Novig says it left the book with nothing new filled: its side can be bid again. */
        GONE,
        /** Cancel queued, not yet confirmed (watched as [MakerStatus.CANCELING] until it is). */
        PENDING,
        /** It filled (all or part) before the cancel: a bet now, recorded. */
        FILLED,
        /** Novig refused the cancel or didn't answer ([problems] says why); the bid stays as it was. */
        FAILED,
    }

    /**
     * Cancels [orderId] and waits a moment for Novig to confirm it left the book (a `200` only says the cancel was queued, and a fill can land before
     * it's applied): every fill is recorded, and only a confirmed cancel with nothing new filled frees the side for a new bid.
     */
    private suspend fun cancelOne(orderId: String, why: String, problems: MutableList<String>): Cancel {
        val bid = store.all().firstOrNull { it.orderId == orderId }
        val status = try {
            trading.cancelOrder(orderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problems += "Cancel: ${(e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName}"
            return Cancel.FAILED
        }
        store.update { list -> list.map { if (it.orderId == orderId && it.resting) it.copy(status = MakerStatus.CANCELING, why = why) else it } }
        // Novig's record of it, until it says the order ended (or a few looks, then the next pass finishes the job).
        var order = readOrder(orderId)
        var looks = 1
        while (order != null && !order.terminal && looks < CANCEL_LOOKS) {
            pause(CANCEL_LOOK_MS)
            order = readOrder(orderId)
            looks++
        }
        val filledNow = order?.let { it.qty - it.remaining } ?: bid?.filled ?: 0L
        val filled = bid != null && filledNow > bid.filled
        if (filled) recordFills(bid!!, order)
        val ended = order?.terminal == true || status == null || status == "FILLED" || status == "CANCELED" || status == "REJECTED"
        if (!ended) return if (filled) Cancel.FILLED else Cancel.PENDING
        val now = clock()
        val fullyFilled = order?.status == "FILLED" || status == "FILLED"
        store.update { list ->
            list.map {
                if (it.orderId != orderId || it.status.ended) it
                else if (fullyFilled) it.copy(status = MakerStatus.FILLED, endedAtMs = now)
                else it.copy(status = MakerStatus.CANCELED, endedAtMs = now, why = why)
            }
        }
        return if (filled || fullyFilled) Cancel.FILLED else Cancel.GONE
    }

    /** What Novig says happened to every active bid since the last look; fills go to the Tracker. Returns the bets new or grown. */
    private suspend fun settle(problems: MutableList<String>): List<TrackedBet> {
        val now = clock()
        val bids = store.all()
        // Ended bids past the keep window leave the list.
        if (bids.any { it.status.ended && (it.endedAtMs ?: it.postedAtMs) < now - MakerStore.KEEP_MS }) {
            store.update { list -> list.filterNot { it.status.ended && (it.endedAtMs ?: it.postedAtMs) < now - MakerStore.KEEP_MS } }
        }
        val active = store.all().filter { it.active }
        val open = try {
            trading.orders("OPEN")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problems += "Novig's open orders: ${(e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName}"
            return emptyList()
        }
        val openById = open.associateBy { it.orderId }
        val openByClient = open.mapNotNull { o -> o.clientId?.let { it to o } }.toMap()
        val grown = ArrayList<TrackedBet>()
        for (bid in active) {
            val order = bid.orderId?.let { openById[it] } ?: openByClient[bid.clientId]
            if (order != null) {
                // Still on the book (a lost answer's order found by its clientId gets its id; a cancel not applied yet stays CANCELING).
                if (bid.orderId == null) store.update { l -> l.map { if (it.clientId == bid.clientId) it.copy(orderId = order.orderId, status = MakerStatus.RESTING) else it } }
                val withId = bid.copy(orderId = order.orderId)
                if (order.qty - order.remaining > bid.filled) recordFills(withId, order)?.let { grown += it }
                continue
            }
            if (bid.orderId == null) {
                // A lost answer and no resting order with its clientId: it may have filled or ended already, so Novig's other lists are asked.
                if (now - bid.postedAtMs < LOST_AFTER_MS) continue
                val found = findByClientId(bid)
                if (found == null) {
                    store.update { l -> l.map { if (it.clientId == bid.clientId) it.copy(status = MakerStatus.LOST, endedAtMs = now, why = "Novig's lists don't show it") else it } }
                    continue
                }
                store.update { l -> l.map { if (it.clientId == bid.clientId) it.copy(orderId = found.orderId) else it } }
                finish(bid.copy(orderId = found.orderId), found, now)?.let { grown += it }
                continue
            }
            // Not in the open list: filled, expired, voided or cancelled, or so new it's still queued. Its own record says which.
            val ended = readOrder(bid.orderId)
            // Novig's reads can lag a just-placed order (404, or PENDING): asked again next time.
            if (ended == null && now - bid.postedAtMs < LOST_AFTER_MS) continue
            if (ended != null && !ended.terminal) {
                if (ended.qty - ended.remaining > bid.filled) recordFills(bid, ended)?.let { grown += it }
                continue
            }
            finish(bid, ended, now)?.let { grown += it }
        }
        // A post-only order resting on Novig that Vigilant has no record of (a lost list) is nobody's to watch: it comes down.
        val known = store.all()
        val knownIds = known.mapNotNullTo(HashSet()) { it.orderId }
        val knownClients = known.mapTo(HashSet()) { it.clientId }
        for (o in open) {
            if (o.tif == "PO" && o.orderId !in knownIds && o.clientId !in knownClients) {
                try {
                    trading.cancelOrder(o.orderId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    problems += "Cancel of an unknown bid: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
        return grown
    }

    /** [bid]'s order has ended ([order]: Novig's record, null when it can't be read): every fill recorded, then why it ended. */
    private suspend fun finish(bid: MakerBid, order: NovigOrder?, now: Long): TrackedBet? {
        // Fills are read whatever the record says (or when there's none): a fill must never go unrecorded.
        val bet = recordFills(bid, order)
        val stored = store.all().firstOrNull { it.clientId == bid.clientId }?.filled ?: bid.filled
        val filledNow = maxOf(order?.let { it.qty - it.remaining } ?: 0L, stored)
        val status = when {
            order?.status == "FILLED" || (order != null && order.remaining <= 0 && filledNow > 0) || filledNow >= bid.contracts -> MakerStatus.FILLED
            order?.status == "REJECTED" -> MakerStatus.REFUSED
            bid.status == MakerStatus.CANCELING -> MakerStatus.CANCELED
            now >= bid.startsTs -> MakerStatus.VOIDED
            bid.expiresAtMs != null && now >= bid.expiresAtMs - 1_000 -> MakerStatus.EXPIRED
            else -> MakerStatus.CANCELED
        }
        val why = when (status) {
            MakerStatus.REFUSED -> "Novig refused it (a post-only bid that would have taken)"
            MakerStatus.VOIDED -> "The game started: Novig cancels resting bids"
            MakerStatus.CANCELED -> if (bid.status == MakerStatus.CANCELING) null else "Novig cancelled it"
            else -> null
        }
        store.update { l -> l.map { if (it.clientId == bid.clientId && it.active) it.copy(status = status, endedAtMs = now, why = why ?: it.why) else it } }
        return bet
    }

    /** The order a lost answer placed, looked for by its clientId in every status Novig lists (on its outcome only); null when none has it. */
    private suspend fun findByClientId(bid: MakerBid): NovigOrder? {
        for (status in listOf("FILLED", "CANCELED", "REJECTED", "PENDING")) {
            val hit = try {
                trading.orders(status, outcomeId = bid.outcomeId).firstOrNull { it.clientId == bid.clientId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (hit != null) return hit
        }
        return null
    }

    /** Reads [bid]'s fills and logs them to the Tracker; keeps the bid's filled count. */
    private suspend fun recordFills(bid: MakerBid, order: NovigOrder?): TrackedBet? {
        val orderId = bid.orderId ?: return null
        val fills: List<NovigFill> = try {
            trading.fills(orderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        if (fills.isEmpty()) return null
        val filled = fills.distinctBy { it.fillId }.sumOf { it.qty }
        val known = store.all().firstOrNull { it.orderId == orderId }?.filled ?: bid.filled
        val bet = tracker.logMakerFills(bid.target(), orderId, fills) ?: return null
        val paid = fills.distinctBy { it.fillId }.sumOf { it.cost }
        store.update { l ->
            l.map {
                if (it.orderId != orderId) it
                else it.copy(filled = filled, paid = paid, betId = bet.id, status = if (order?.status == "FILLED" || filled >= it.contracts) MakerStatus.FILLED else it.status, endedAtMs = if (filled >= it.contracts) clock() else it.endedAtMs)
            }
        }
        // Only a bet that's new or grew is news (a notification each).
        return bet.takeIf { filled > known }
    }

    private suspend fun readOrder(orderId: String): NovigOrder? = try {
        trading.order(orderId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Dollars of API bets (fills) since local midnight, locks left out (as the Bet sheet's daily limit counts them). */
    private fun spentToday(bets: List<TrackedBet>, now: Long): Double {
        val from = dayStart(now)
        return bets.filter { it.orderId != null && !it.isLock && it.createdAtMs >= from }.sumOf { it.stake }
    }

    private fun money(v: Double) = String.format(java.util.Locale.US, "$%,.2f", v)

    companion object {
        /** Why a denied side gets no bid. */
        const val DENIED = "You denied this bid (Bids tab › Denied to undo)"

        /** A bid whose answer was lost and that no list shows after this long is looked for in Novig's other lists, then called gone. */
        const val LOST_AFTER_MS = 90_000L

        /** No bid is sent to rest for less than this (its window closed while the pass worked). */
        const val MIN_TTL_MS = 60_000L

        /** A side Novig refused a post-only bid on isn't bid on again for this long (the scan's price there was out of date). */
        const val REFUSED_COOLOFF_MS = 5 * 60_000L

        /** After a cancel, Novig's record is read up to this many times, this far apart, for its confirmation. */
        const val CANCEL_LOOKS = 4
        const val CANCEL_LOOK_MS = 400L
    }
}
