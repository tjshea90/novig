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
import com.tjshea.vigilant.data.tracker.GameExposure
import com.tjshea.vigilant.data.tracker.GameRef
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
    /**
     * Novig's book on this side when it was posted (Diagnostics: whether bids lead their side, why they fill or don't): the best bid already
     * resting, the price to take it, and when that book was read.
     */
    val bestBidAtPost: Double? = null,
    val offerAtPost: Double? = null,
    val bookAtMs: Long? = null,
    /**
     * Vigilant's blended fair when the bid was posted ([fair] is the fair its margin is under: the lower of this and the sharpest book's, [MakerRules.anchorOf]),
     * and the sharpest book's own fair then (the lowest of the sharp books' worst-case devigs; null = no sharp book in the fair). RESEARCH.md §88.3.
     */
    val blendFair: Double? = null,
    val sharpFairAtPost: Double? = null,
    /**
     * The first fill (Novig's own clock), and what the fair was on the first scan after it: Vigilant's blend, the sharpest book's, and when the oldest book price
     * behind it was seen. A fill whose fair had already moved under its price was picked off (RESEARCH.md §88.3, [fillEv]); null before a fill or when no
     * scan priced the side since. The fill's delay, [fillDelayMs], is how fast it was taken.
     */
    val firstFillAtMs: Long? = null,
    val fairAtFill: Double? = null,
    val sharpFairAtFill: Double? = null,
    val fairAtFillAsOfMs: Long? = null,
    /**
     * Seen in Novig's open orders at least once. Once it has been, an order missing from that list is off the book and its record isn't read (Novig
     * answers 404 by then); one never seen there may have been refused (post-only, `REJECTED`), which only its record says.
     */
    val seenOpen: Boolean = false,
) {
    val active: Boolean get() = !status.ended

    /** How long after posting the first fill came (Novig's clock against Vigilant's), null before a fill. */
    val fillDelayMs: Long? get() = firstFillAtMs?.let { (it - postedAtMs).coerceAtLeast(0L) }

    /**
     * The EV this bid's fill had on the first scan after it: the fair then (the lower of the blend and the sharpest book's, as the bid was priced) against the
     * price it was filled at. Negative = the fair had moved under the price: the market walked away from the bid (picked off). Null when no scan priced it after.
     */
    fun fillEv(anchorSharp: Boolean = true): Double? {
        val f = fairAtFill ?: return null
        return MakerRules.anchorOf(f, listOfNotNull(sharpFairAtFill), anchorSharp) / price - 1.0
    }

    /** Up on Novig as far as Vigilant knows (sent or resting), not on its way down. */
    val resting: Boolean get() = status == MakerStatus.SENT || status == MakerStatus.RESTING
    val cost: Double get() = contracts * price * EvMath.CONTRACT_PAYOUT_DOLLARS
    val restingDollars: Double get() = (contracts - filled).coerceAtLeast(0) * price * EvMath.CONTRACT_PAYOUT_DOLLARS

    fun target(): BetTarget = BetTarget(
        // Only the ids matter to the Tracker's record of the fills; Novig's catalog has the rest.
        market = NovigMarket(marketId, eventId, "", "OPEN", marketLabel, startsTs, null, emptyList()),
        outcomeId = outcomeId, league = league, eventName = eventName, startsTs = startsTs, marketLabel = marketLabel, selection = selection,
        fair = fair, fairAsOfMs = postedAtMs, source = source, gameUrl = gameUrl, basis = fairBasis, auto = auto,
        atBet = com.tjshea.vigilant.data.tracker.AtBets.bid(this, firstFillAtMs ?: postedAtMs),
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
        /** Of [cancelled], the bids the wallet (or the day's limit) no longer covered ([MakerPlan.TRIMMED]). */
        val trimmed: Int = 0,
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
        /**
         * Asked before each new bid is sent: false = no more go up this pass (Pause, or auto-make switched off, while it ran: Tj's Diagnostics
         * 2026-10-03, "I pressed pause and even that took a while to register": the pass held the lock the Pause's cancel-all waits for until its last
         * bid was posted, and then that bid was cancelled).
         */
        keepPosting: () -> Boolean = { true },
    ): Report = lock.withLock {
        val problems = ArrayList<String>()
        val fills = ArrayList(settle(problems))
        lineRefs = lines.associateBy { it.outcomeId }
        judgeFills()
        val now = clock()
        val bids = store.all()
        val active = bids.filter { it.resting && it.orderId != null }
        val bets = tracker.all()
        val activeOutcomes = active.mapTo(HashSet()) { it.outcomeId }
        // Busy: a bid on its way down (a fill can still land) or one whose answer was lost. Never a second bid on that side meanwhile.
        val busy = bids.filter { it.status == MakerStatus.CANCELING || (it.status == MakerStatus.SENT && it.orderId == null) }.mapTo(HashSet()) { it.outcomeId }
        val held = (bets.filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId } - activeOutcomes) + busy
        val refused = bids.filter { it.status == MakerStatus.REFUSED && now - (it.endedAtMs ?: it.postedAtMs) < REFUSED_COOLOFF_MS }.mapTo(HashSet()) { it.outcomeId }
        val decisions = MakerQuote.decideAll(MakerLines.withoutOwn(lines, bids), rules, now, held).map { d ->
            if (d is MakerDecision.Post && d.line.outcomeId in denied) return@map MakerDecision.Skip(d.line, DENIED)
            // Novig refused a bid here a moment ago (post-only: its price had moved to the bid): not asked again until the cool-off passes.
            if (d is MakerDecision.Post && d.line.outcomeId in refused && d.line.outcomeId !in activeOutcomes) {
                MakerDecision.Skip(d.line, "Novig refused a bid here in the last ${REFUSED_COOLOFF_MS / 60_000} min (its price had moved to the bid)")
            } else d
        }
        val spent = spentToday(bets, now)
        val stopAll = stop ?: if (spent >= maxPerDay - 1e-9) "Today's limit for API bets (${money(maxPerDay)}) is reached" else null
        val resting = restingOf(active)
        // Every bid not yet ended can still fill (one on its way down too), and Novig doesn't hold a resting bid's cost from the balance (Tj's v0.53.0
        // file: "wallet $8.32 → $8.32 with $12.54 resting", NOVIG_API.md §17): what's up counts against the wallet as well as the day's limit. Under zero
        // when the money behind the bids fell short after they went up (a bet by hand, an auto-bet, a fill: Tj, 2026-10-04): the plan takes those bids down.
        val budget = spare(bids, spent, wallet, maxPerDay)
        val actions = MakerPlan.plan(
            wanted = decisions.filterIsInstance<MakerDecision.Post>(), resting = resting, rules = rules, now = now,
            skips = decisions.filterIsInstance<MakerDecision.Skip>().associate { it.line.outcomeId to it.why }, stopAll = stopAll, budget = budget,
            repost = autoPost, partial = partial, heldItems = GameExposure.items(bets),
        )
        val noReplace = HashSet<String>()
        val cancelled = withContext(NonCancellable) { runCancels(actions.cancels, problems, fills, noReplace) }
        var placed = 0
        var queue = actions.places.filter { it.line.outcomeId !in noReplace }
        // Many new bids go up in a request each 256, not one by one (each takes ~0.2 s with this lock held, and fair prices move while they do).
        if (batching && queue.size >= 2) {
            val rest = ArrayList<MakerDecision.Post>()
            var lost = false
            for (chunk in queue.chunked(NovigTradingClient.MAX_BATCH)) {
                if (!keepPosting()) { lost = true; break }
                when (val r = withContext(NonCancellable) { placeBatch(chunk, rules, auto = true, problems) }) {
                    is Batch.Placed -> placed += r.n
                    is Batch.Singly -> rest += r.posts
                    is Batch.Lost -> { problems += r.why; lost = true; break }
                }
            }
            queue = if (lost) emptyList() else rest
        }
        for (post in queue) {
            if (!keepPosting()) break
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
        Report(placed, cancelled, fills, problems, after, stopAll, decisions, actions.waiting, partial, actions.trimmed)
    }

    /**
     * Between passes: what Novig says happened to every bid (fills to the Tracker), then the bids the [wallet] (and the day's limit) can't cover beside each
     * other come down, the least valuable first ([MakerPlan.plan]'s trim), nothing posted, moved or judged by a line. Called when a balance reading shows
     * more bids up than money (a bet by hand or an auto-bet took it, Tj, 2026-10-04), by every pass of the app's, and while no scan has run to judge lines.
     * The balance is read BEFORE this is called, so a fill that lands between that read and the settle only makes the count lower than the wallet
     * (a later look catches the rest), never higher. [wallet] null: only the day's limit is checked.
     */
    suspend fun fit(rules: MakerRules, maxPerDay: Double, wallet: Double?): Report = lock.withLock {
        val problems = ArrayList<String>()
        val fills = ArrayList(settle(problems))
        val now = clock()
        val bids = store.all()
        val actions = MakerPlan.plan(
            wanted = emptyList(), resting = restingOf(bids.filter { it.resting && it.orderId != null }), rules = rules, now = now,
            budget = spare(bids, spentToday(tracker.all(), now), wallet, maxPerDay), repost = false, partial = true,
        )
        val noReplace = HashSet<String>()
        val cancelled = withContext(NonCancellable) { runCancels(actions.cancels, problems, fills, noReplace) }
        Report(0, cancelled, fills, problems, store.all().count { it.active }, null, emptyList(), emptyMap(), true, actions.trimmed)
    }

    /** The latest pass's lines by side: what a fill is judged against ([judgeFills]). Only read and written inside [lock]. */
    private var lineRefs: Map<String, MakerLine> = emptyMap()

    /**
     * Gives every filled bid not yet judged the fair the first scan after its fill has for that side (RESEARCH.md §88.3): the blend and the sharpest book's own,
     * when some book price behind it was seen after the fill (a fair that all predates the fill says nothing about it). A fill with no such scan within
     * [JUDGE_WITHIN_MS] stays unjudged for good. Run at the start of every pass, so a fill found one pass is judged on the scan that follows it.
     */
    private suspend fun judgeFills() {
        val now = clock()
        val pending = store.all().filter { it.filled > 0 && it.firstFillAtMs != null && it.fairAtFill == null && now - it.firstFillAtMs < JUDGE_WITHIN_MS }
        if (pending.isEmpty() || lineRefs.isEmpty()) return
        val judged = HashMap<String, MakerLine>()
        for (b in pending) {
            val line = lineRefs[b.outcomeId] ?: continue
            val newest = line.fairNewestMs ?: continue
            if (line.fair == null || newest < b.firstFillAtMs!!) continue
            judged[b.clientId] = line
        }
        if (judged.isEmpty()) return
        store.update { list ->
            list.map { b ->
                val line = judged[b.clientId] ?: return@map b
                b.copy(fairAtFill = line.fair, sharpFairAtFill = line.sharpFairs.minOrNull(), fairAtFillAsOfMs = line.fairAsOfMs)
            }
        }
    }

    /** The bids resting on Novig as the plan sees them (a bid with no order id yet isn't one: it's in the budget as up, but nothing can cancel it). */
    private fun restingOf(bids: List<MakerBid>): List<RestingBid> = bids.filter { it.resting && it.orderId != null }.map {
        RestingBid(
            it.orderId!!, it.marketId, it.outcomeId, it.price, (it.contracts - it.filled).coerceAtLeast(0), it.filled, it.expiresAtMs,
            auto = it.auto, evAtFair = it.evAtFair, leads = it.bestBidAtPost.let { b -> b == null || b < it.price - 1e-9 },
            game = GameRef(it.eventId, it.eventName, it.startsTs, it.league),
        )
    }

    /**
     * What's left of the wallet and the day's limit beside every bid not yet ended (any can still fill, one on its way down too): the least of the two,
     * less what's up. Under zero when the bids up are worth more than either. [wallet] null = no reading.
     */
    private fun spare(bids: List<MakerBid>, spent: Double, wallet: Double?, maxPerDay: Double): Double =
        minOf(wallet ?: Double.MAX_VALUE, maxPerDay - spent) - bids.filter { it.active }.sumOf { it.restingDollars }

    /** Cancels [cancels] (bid, why); how many Novig took. Sides whose cancel isn't confirmed gone go into [noReplace]: nothing new there this pass. */
    private suspend fun runCancels(cancels: List<Pair<RestingBid, String>>, problems: MutableList<String>, fills: MutableList<TrackedBet>, noReplace: MutableSet<String>): Int {
        var cancelled = 0
        val results = cancelBids(cancels.map { (r, why) -> r.orderId to why }, problems, fills)
        for ((r, _) in cancels) {
            when (results[r.orderId]) {
                // Gone, confirmed: its side can be bid again now.
                Cancel.GONE -> cancelled++
                // On its way down but not confirmed, or it filled first, or Novig refused: nothing new on that side this pass.
                Cancel.PENDING -> { cancelled++; noReplace += r.outcomeId }
                else -> noReplace += r.outcomeId
            }
        }
        return cancelled
    }

    /**
     * Only what Novig says happened to the bids (fills to the Tracker, expiries, voids), nothing posted or moved: for a pass with no fair prices to
     * judge by (no scan yet in this process). [problems] collects what went wrong.
     */
    suspend fun settleOnly(problems: MutableList<String> = ArrayList()): List<TrackedBet> = lock.withLock { settle(problems) }

    /** Every bid on record (active and ended, newest last), and the same as it changes. */
    suspend fun bids(): List<MakerBid> = store.all()
    val flow get() = store.flow

    /**
     * Tj's Post button: one bid now, outside the cycle (the same checks: [decision] was worked out just now). With a [wallet] reading and/or a [maxPerDay]
     * limit, the bid must fit them beside the bids already up (Tj, 2026-10-04: each hand-approved bid was held only to the whole wallet, so two $5.00 bids
     * went up on $9.00); null leaves a check out.
     */
    suspend fun post(decision: MakerDecision.Post, rules: MakerRules, wallet: Double? = null, maxPerDay: Double = Double.MAX_VALUE): String? = lock.withLock {
        val bids = store.all()
        if (bids.any { it.active && it.outcomeId == decision.line.outcomeId }) return@withLock "There's already a bid on this side (or one on its way down)"
        if (wallet != null || maxPerDay < Double.MAX_VALUE) {
            val up = bids.filter { it.active }.sumOf { it.restingDollars }
            val byWallet = (wallet ?: Double.MAX_VALUE) - up
            val byDay = maxPerDay - spentToday(tracker.all(), clock()) - up
            if (decision.cost > byWallet + 1e-9) {
                return@withLock "The wallet's ${money(wallet ?: 0.0)} can't cover this bid's ${money(decision.cost)} beside the ${money(up)} already up"
            }
            if (decision.cost > byDay + 1e-9) {
                return@withLock "Today's limit for API bets (${money(maxPerDay)}) can't cover this bid's ${money(decision.cost)} beside what's been bet and is up"
            }
        }
        when (val r = withContext(NonCancellable) { placeOne(decision, rules, auto = false) }) {
            is Placed.Ok -> null
            is Placed.Refused -> r.why
        }
    }

    /** Tj's Cancel on one bid. Null when Novig took the cancel; else why not. */
    suspend fun cancel(orderId: String): String? = lock.withLock {
        val problems = ArrayList<String>()
        withContext(NonCancellable) {
            when (cancelBids(listOf(orderId to "Cancelled by you"), problems, ArrayList())[orderId]) {
                Cancel.GONE, Cancel.PENDING -> null
                Cancel.FILLED -> "It filled before the cancel reached Novig (see the Tracker)"
                else -> problems.firstOrNull() ?: "Novig didn't take the cancel"
            }
        }
    }

    /** The bids auto-make posted down (auto-make switched off; the ones Tj approved by hand stay). How many cancels Novig took. */
    suspend fun cancelAuto(why: String): Int = lock.withLock {
        withContext(NonCancellable) {
            val ids = store.all().filter { it.resting && it.auto && it.orderId != null }.map { it.orderId!! to why }
            cancelBids(ids, ArrayList(), ArrayList()).values.count { it != Cancel.FAILED }
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

    /** The bid [post] becomes at [now] and how long it may rest in ms, or null when the fair it was priced from is about to go old. */
    private fun bidFor(post: MakerDecision.Post, rules: MakerRules, auto: Boolean, now: Long): Pair<MakerBid, Long>? {
        val line = post.line
        val bid = MakerBid(
            clientId = NovigTradingClient.newClientId(), marketId = line.marketId, eventId = line.market.eventId, outcomeId = line.outcomeId,
            league = line.league, eventName = line.eventName, startsTs = line.startsTs, marketLabel = line.marketLabel, selection = line.selection,
            kind = line.kind, price = post.price, contracts = post.contracts, fair = post.anchorFair ?: line.fair ?: post.price, evAtFair = post.evAtFair,
            blendFair = line.fair, sharpFairAtPost = line.sharpFairs.minOrNull(),
            margin = rules.margin, books = line.books, source = line.source, gameUrl = line.gameUrl, fairBasis = line.basis, postedAtMs = now,
            // Never past what it was priced from ([MakerDecision.Post.restUntilMs]: the expiry, the fair's freshness, the stop window before the start).
            expiresAtMs = minOf(now + rules.ttlMs, line.startsTs - rules.stopMs, post.restUntilMs), auto = auto,
            bestBidAtPost = line.bestBid, offerAtPost = line.offer, bookAtMs = line.bookAtMs,
        )
        val ttl = bid.expiresAtMs!! - now
        // Worked out a moment ago: if that window has closed since, nothing is sent.
        return if (ttl < MIN_TTL_MS) null else bid to ttl
    }

    private val tooOld = { post: MakerDecision.Post -> "${post.line.selection}: its fair price is about to go old; re-priced at the next scan" }

    private suspend fun placeOne(post: MakerDecision.Post, rules: MakerRules, auto: Boolean): Placed {
        val line = post.line
        val (bid, ttl) = bidFor(post, rules, auto, clock()) ?: return Placed.Refused(tooOld(post), stopsCycle = false)
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

    /** The batch routes are used: the client has them and no answer from them has been unreadable (that turns them off for the life of this desk, one by one as ever). */
    private val batching: Boolean get() = trading.batchOrders && !batchUnreadable

    @Volatile private var batchUnreadable = false

    /** How a batch of new bids ended. */
    private sealed interface Batch {
        /** [n] bids are resting. */
        data class Placed(val n: Int) : Batch

        /** Novig placed none of them (all or nothing): each goes one by one, so a single refusal costs only itself. */
        data class Singly(val posts: List<MakerDecision.Post>) : Batch

        /** No usable answer: they may be resting, found by their client ids at the next look; nothing more is sent this pass. */
        data class Lost(val why: String) : Batch
    }

    /**
     * Up to [NovigTradingClient.MAX_BATCH] new bids in ONE request (Novig's batch route: all or nothing, one `place` token a bid). Every bid is recorded before the request
     * is sent, as [placeOne] does, so an answer that never comes back is still a bid Vigilant looks for by its client id. A refusal places none, so the records come out
     * and the bids go [Batch.Singly].
     */
    private suspend fun placeBatch(posts: List<MakerDecision.Post>, rules: MakerRules, auto: Boolean, problems: MutableList<String>): Batch {
        val now = clock()
        val built = ArrayList<Triple<MakerDecision.Post, MakerBid, Long>>()
        for (p in posts) {
            val b = bidFor(p, rules, auto, now)
            if (b == null) problems += tooOld(p) else built += Triple(p, b.first, b.second)
        }
        if (built.isEmpty()) return Batch.Placed(0)
        val bids = built.map { it.second }
        store.update { it + bids }
        val specs = built.map { (p, b, ttl) -> NovigTradingClient.NewOrder(p.line.outcomeId, p.price, p.contracts, "PO", b.clientId, ttl) }
        return try {
            val ids = trading.placeOrders(specs)
            store.update { list -> list.map { b -> ids[b.clientId]?.let { id -> b.copy(orderId = id, status = MakerStatus.RESTING) } ?: b } }
            val missing = bids.count { it.clientId !in ids }
            if (missing > 0) Batch.Lost("Novig's answer named ${ids.size} of ${bids.size} bids; the rest are looked for by their client ids next time") else Batch.Placed(bids.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            val gone = bids.mapTo(HashSet()) { it.clientId }
            store.update { list -> list.filterNot { it.clientId in gone } }
            problems += "A batch of ${bids.size} bids was refused (${e.advice}); they go one at a time"
            Batch.Singly(built.map { it.first })
        } catch (e: kotlinx.serialization.SerializationException) {
            // Placed (the reply was a success) but not in a shape this app reads: found by client id next look, and no batch is sent again.
            batchUnreadable = true
            Batch.Lost("Novig's answer to a batch of ${bids.size} bids couldn't be read; they are looked for by their client ids, and bids go one at a time from now on")
        } catch (e: Exception) {
            Batch.Lost("Novig didn't answer a batch of ${bids.size} bids (${e.message ?: e.javaClass.simpleName}); looked for again next time")
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
     * Cancels each order ([orderId] to why) and asks Novig once which are gone: a `200` only queues a cancel, and a fill can land before it's applied.
     * After the cancels, one read of the open orders (those no longer in it are off the book) and one read of their fills (any that filled first is
     * a bet, recorded into [grown]); only a confirmed cancel with nothing new filled frees its side for a new bid. Novig's record of one order isn't
     * read: it answers 404 once the order is off the book (Tj's v0.53.0 file: ~200 such 404s, each followed by a fills read of its own).
     */
    private suspend fun cancelBids(orders: List<Pair<String, String>>, problems: MutableList<String>, grown: MutableList<TrackedBet>): Map<String, Cancel> {
        val out = HashMap<String, Cancel>()
        val sent = ArrayList<String>()
        // Several cancels go in a request each 256 (partial: one unknown id does not stop the rest); an id the batch could not answer for, or a batch that failed, is
        // cancelled one by one below, which reports its own trouble.
        var singles = orders
        if (batching && orders.size >= 2) {
            val left = ArrayList<Pair<String, String>>()
            for (chunk in orders.chunked(NovigTradingClient.MAX_BATCH)) {
                val res = try {
                    trading.cancelOrdersBatch(chunk.map { it.first })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is kotlinx.serialization.SerializationException) batchUnreadable = true
                    left += chunk
                    continue
                }
                for ((orderId, why) in chunk) {
                    // FILLED = nothing left to cancel, it's a bet (its fills are read with the others'); CANCELED / NOT_FOUND = already off the book, confirmed below.
                    if (res.notCanceled[orderId] == "FILLED") out[orderId] = Cancel.FILLED
                    store.update { list -> list.map { if (it.orderId == orderId && it.resting) it.copy(status = MakerStatus.CANCELING, why = why) else it } }
                    sent += orderId
                }
            }
            singles = left
        }
        for ((orderId, why) in singles) {
            val status = try {
                trading.cancelOrder(orderId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problems += "Cancel: ${(e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName}"
                out[orderId] = Cancel.FAILED
                continue
            }
            store.update { list -> list.map { if (it.orderId == orderId && it.resting) it.copy(status = MakerStatus.CANCELING, why = why) else it } }
            // Its status when the cancel arrived: FILLED = nothing left to cancel, it's a bet (its fills are read with the others').
            if (status == "FILLED") out[orderId] = Cancel.FILLED
            sent += orderId
        }
        if (sent.isEmpty()) return out
        pause(CANCEL_LOOK_MS)
        val open = try {
            trading.orders("OPEN").mapTo(HashSet()) { it.orderId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not confirmed: each stays CANCELLING (no new bid on its side) and the next pass's look finishes it.
            sent.forEach { out.putIfAbsent(it, Cancel.PENDING) }
            return out
        }
        val gone = store.all().filter { it.orderId in sent && it.orderId !in open && it.active }
        val fills = if (gone.isEmpty()) emptyMap() else readFills(gone, problems)
        val now = clock()
        for (id in sent) {
            val bid = gone.firstOrNull { it.orderId == id }
            if (bid == null || fills == null) {
                out.putIfAbsent(id, Cancel.PENDING)
                continue
            }
            val bet = finish(bid, null, now, fills[id].orEmpty())
            bet?.let { grown += it }
            val after = store.all().firstOrNull { it.orderId == id }
            out[id] = if (out[id] == Cancel.FILLED || bet != null || (after != null && after.filled > bid.filled) || after?.status == MakerStatus.FILLED) Cancel.FILLED else Cancel.GONE
        }
        return out
    }

    /**
     * What Novig says happened to every active bid since the last look; fills go to the Tracker. Returns the bets new or grown. One read of the open
     * orders, then one read of the fills of every bid that left it or filled since (never one per bid: the `history` bucket a fills read costs 8 of
     * ran dry in Tj's v0.53.0 file). A bid whose fills can't be read stays as it was, to be finished next time: a fill must never go unrecorded.
     */
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
        // Still on the book with new fills, and off it (with Novig's record when one was read): both have their fills read, once, below.
        val grownOpen = ArrayList<Pair<MakerBid, NovigOrder>>()
        val ended = ArrayList<Pair<MakerBid, NovigOrder?>>()
        val seen = HashMap<String, String>()
        for (bid in active) {
            val order = bid.orderId?.let { openById[it] } ?: openByClient[bid.clientId]
            if (order != null) {
                // Still on the book (a lost answer's order found by its clientId gets its id; a cancel not applied yet stays CANCELING).
                if (bid.orderId == null || !bid.seenOpen) seen[bid.clientId] = order.orderId
                if (order.qty - order.remaining > bid.filled) grownOpen += bid.copy(orderId = order.orderId, seenOpen = true) to order
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
                store.update { l -> l.map { if (it.clientId == bid.clientId) it.copy(orderId = found.orderId, status = if (found.terminal) it.status else MakerStatus.RESTING) else it } }
                if (found.terminal) ended += bid.copy(orderId = found.orderId) to found
                continue
            }
            // Not in the open list. Seen there before, or a cancel was sent for it: off the book (its record answers 404 by then), its fills say whether
            // it filled. Never seen there: queued (Novig's reads can lag a 201) or refused (post-only), which its own record says.
            if (!bid.seenOpen && bid.status != MakerStatus.CANCELING) {
                val record = readOrder(bid.orderId)
                if (record == null && now - bid.postedAtMs < LOST_AFTER_MS) continue
                if (record != null && !record.terminal) {
                    if (record.qty - record.remaining > bid.filled) grownOpen += bid to record
                    continue
                }
                ended += bid to record
                continue
            }
            ended += bid to null
        }
        if (seen.isNotEmpty()) {
            store.update { l -> l.map { b -> seen[b.clientId]?.let { id -> b.copy(orderId = id, seenOpen = true, status = if (b.status == MakerStatus.SENT) MakerStatus.RESTING else b.status) } ?: b } }
        }
        val grown = ArrayList<TrackedBet>()
        if (grownOpen.isNotEmpty() || ended.isNotEmpty()) {
            val fills = readFills(grownOpen.map { it.first } + ended.map { it.first }, problems)
            if (fills != null) {
                for ((bid, order) in grownOpen) recordFills(bid, order, fills[bid.orderId].orEmpty())?.let { grown += it }
                for ((bid, order) in ended) finish(bid, order, now, fills[bid.orderId].orEmpty())?.let { grown += it }
            }
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

    /**
     * The fills of [bids]' orders, by order id, in one read: every fill on events starting after the earliest of their starts, less a day (the event's
     * start Novig filters on can sit before the market's). Null when the read failed ([problems] says why): nothing is finished without its fills.
     */
    private suspend fun readFills(bids: List<MakerBid>, problems: MutableList<String>): Map<String, List<NovigFill>>? {
        val ids = bids.mapNotNullTo(HashSet()) { it.orderId }
        if (ids.isEmpty()) return emptyMap()
        return try {
            trading.fillsStartingAfter(bids.minOf { it.startsTs } - FILLS_LOOKBACK_MS).filter { it.orderId in ids }.groupBy { it.orderId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problems += "Novig's fills: ${(e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName} (read again next time)"
            null
        }
    }

    /** [bid]'s order has ended ([order]: Novig's record, null when it wasn't read): its [fills] recorded, then why it ended. */
    private suspend fun finish(bid: MakerBid, order: NovigOrder?, now: Long, fills: List<NovigFill>): TrackedBet? {
        val bet = recordFills(bid, order, fills)
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

    /** Logs [bid]'s [fills] (read with [readFills]) to the Tracker; keeps the bid's filled count. */
    private suspend fun recordFills(bid: MakerBid, order: NovigOrder?, fills: List<NovigFill>): TrackedBet? {
        val orderId = bid.orderId ?: return null
        if (fills.isEmpty()) return null
        val filled = fills.distinctBy { it.fillId }.sumOf { it.qty }
        val known = store.all().firstOrNull { it.orderId == orderId }?.filled ?: bid.filled
        val bet = tracker.logMakerFills(bid.target(), orderId, fills) ?: return null
        val paid = fills.distinctBy { it.fillId }.sumOf { it.cost }
        val firstFill = fills.map { it.ts }.filter { it > 0 }.minOrNull()
        store.update { l ->
            l.map {
                if (it.orderId != orderId) it
                else it.copy(filled = filled, paid = paid, betId = bet.id, firstFillAtMs = it.firstFillAtMs ?: firstFill ?: clock(), status = if (order?.status == "FILLED" || filled >= it.contracts) MakerStatus.FILLED else it.status, endedAtMs = if (filled >= it.contracts) clock() else it.endedAtMs)
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

        /** After cancels, Novig's open orders are read this long later for their confirmation (Cancel all: up to [CANCEL_LOOKS] times). */
        const val CANCEL_LOOKS = 4
        const val CANCEL_LOOK_MS = 400L

        /** A fill is judged against the fair of a scan that priced the side within this long after it ([judgeFills]). */
        const val JUDGE_WITHIN_MS = 45 * 60_000L

        /** A fills read reaches back this far before the earliest start of the bids it's for ([readFills]). */
        const val FILLS_LOOKBACK_MS = 24 * 3_600_000L
    }
}
