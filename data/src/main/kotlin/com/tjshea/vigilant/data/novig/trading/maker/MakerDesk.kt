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
import kotlinx.serialization.json.Json
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
    /**
     * Which "Which bids go up" choice posted it ([com.tjshea.vigilant.data.scanner.BidFocus] name; null = posted before v0.68.0), the books its fair was made from (names
     * as the fair line gives them) and how old the oldest and newest of their prices were when it was posted, in seconds: what Diagnostics and the scan study split
     * low-usage bids by (RESEARCH.md §92).
     */
    val focus: String? = null,
    val fairBooks: List<String> = emptyList(),
    val fairAgeSec: Int? = null,
    val fairNewestAgeSec: Int? = null,
    /** A small-market bid ([MakerRules.obscureFill]; Tj, 2026-10-07): Diagnostics and the Bids tab split bids and fills by it. */
    val obscure: Boolean = false,
    /** A riskier bid posted only to keep the wallet working (Tj, 2026-10-14; [MakerDesk.deployFor]): Diagnostics counts these apart. */
    val overflow: Boolean = false,
    /** 0 = the usual bid on its side; 1 = a second, DEEPER bid on the same side (Tj, 2026-10-14: fill the wallet at a higher edge; [MakerDesk.DEEP_MARGIN]). Each rung is planned, moved and cancelled on its own. */
    val rung: Int = 0,
    /**
     * A CNO-priced bid ([source] "cno", [CnoMakerLines]): how old CNO's own data was when it was posted, in seconds, for the list that found the side and for the game page whose
     * books made the fair (CNO's "Last Updated" at the read plus the time since the read). What the BIDS section splits picked-off rates by (RESEARCH.md §113). Null for every other bid.
     */
    val listAgeSec: Int? = null,
    val pageAgeSec: Int? = null,
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
    // Fields still at their default are not written (v0.89.35): a bid is a log record whose defaults never change, and the file is rewritten whole and synced to the disk on every post and cancel (Tj's
    // v0.89.34 file: 23 MB, 19,497 bids). Old files, written with every field, read the same.
    private val store = JsonFileStore(file, ListSerializer(MakerBid.serializer()), { emptyList() }, Json { ignoreUnknownKeys = true; encodeDefaults = false })
    val flow get() = store.flow
    suspend fun all(): List<MakerBid> = store.read()
    suspend fun update(transform: (List<MakerBid>) -> List<MakerBid>) = store.update(transform)

    companion object {
        /**
         * Ended bids that never filled leave after 6 hours; ones that filled after a week (Tj, 2026-10-10: the file was 15 MB, rewritten whole on every change, and
         * made the whole app lag; the fills themselves live on in the Tracker for good). It was 2 days until v0.97.2: "More, smaller bids" (v0.96.0) posts hundreds a day and
         * Tj's 2026-10-13 file had maker.json at 22 MB with the heap at 92% twice. Nothing reads an ended unfilled bid later (the repeat guard counts only bids not yet ended).
         */
        const val KEEP_MS = 6 * 3_600_000L
        const val KEEP_FILLED_MS = 7 * 24 * 3_600_000L

        /** An ended bid that is past its keep window. */
        fun expired(b: MakerBid, now: Long): Boolean =
            b.status.ended && (endedOrPosted(b) < now - (if (b.filled > 0) KEEP_FILLED_MS else KEEP_MS))

        private fun endedOrPosted(b: MakerBid) = b.endedAtMs ?: b.postedAtMs
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
    /** Post-only orders that belong to another desk (the live bids): not this desk's to cancel as strays. */
    private val otherDesks: () -> OtherOrders = { OtherOrders.NONE },
    /**
     * The GitHub engine's desk (TCL31): its bids carry [ENGINE_PREFIX] client ids and no other desk may call them strays; it never cancels orders it has no record of either (the phone's and the live desks' bids
     * rest under the same subaccount), and a bid it loses track of ends with its own expiry.
     */
    private val cancelStrays: Boolean = true,
    private val newClientId: () -> String = { NovigTradingClient.newClientId() },
) {
    /** The ids of the orders another desk placed: [orderIds] by Novig's id, [clientIds] by the id they were sent with (an order whose answer never came has only that one). */
    data class OtherOrders(val orderIds: Set<String>, val clientIds: Set<String>) {
        companion object {
            val NONE = OtherOrders(emptySet(), emptySet())
        }
    }

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
        /** What keeping the wallet working did this pass ([Deploy]); null = off or no wallet reading. */
        val deploy: Deploy? = null,
    )

    /** The wallet-deployment state of one pass, for Diagnostics: the target, what was up, the stake multiple used, and the riskier bids wanted. */
    data class Deploy(val walletDollars: Double, val targetDollars: Double, val restingDollars: Double, val scale: Double, val kellyCeiling: Double, val overflowWanted: Int, val overflowDollars: Double, val wantedDollars: Double,
        /** The most common reasons lines got no bid this pass (numbers folded together), with their counts, and how many lines were judged: what limits the deployment. */
        val skips: List<Pair<String, Int>> = emptyList(), val judged: Int = 0,
        /** The same for the wallet-filling pass that looks a week ahead (what stopped THOSE lines), and how many it judged: Diagnostics' answer to "why did the week tier post nothing". */
        val weekSkips: List<Pair<String, Int>> = emptyList(), val weekJudged: Int = 0,
        /** The last wallet-filling pass (every start time, every kind, no sharp book required, a higher edge asked of riskier kinds): lines judged, what stopped the rest. */
        val anySkips: List<Pair<String, Int>> = emptyList(), val anyJudged: Int = 0,
        /** How the bids this pass wants are spread: games with a bid, and the games carrying the most (name, bids, dollars): Diagnostics' check that no few games hold the wallet. */
        val spread: List<String> = emptyList())

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
        /** The rung this pass works on ([MakerBid.rung]); 1 only runs while the bids up are under the wallet target. */
        rung: Int = 0,
        /** The last start the scan behind [lines] reached ([MakerPlan.plan]'s reachUntilMs); null = all. */
        reachUntilMs: Long? = null,
    ): Report = lock.withLock {
        cycleRung = rung
        val problems = ArrayList<String>()
        val fills = ArrayList(settle(problems))
        if (rung == 0) lineRefs = lines.associateBy { it.outcomeId } else lineRefs = lineRefs + lines.associateBy { it.outcomeId }
        judgeFills()
        val now = clock()
        // Every bid on record counts against the wallet, the game and the day; a pass plans, moves and cancels only the bids of its own rung.
        val allBids = store.all()
        val bids = allBids.filter { it.rung == rung }
        val others = allBids.filter { it.rung != rung }
        if (rung > 0) {
            val pct = rules.deployPct
            val target = if (wallet != null) pct * wallet else 0.0
            val inUse = allBids.filter { it.active }.sumOf { it.restingDollars }
            if (pct <= 0.0 || wallet == null || inUse >= target) return@withLock Report(0, 0, fills, problems, allBids.count { it.active }, null, emptyList(), emptyMap(), partial, 0)
        }
        val active = bids.filter { it.resting && it.orderId != null }
        val bets = tracker.all()
        val activeOutcomes = active.mapTo(HashSet()) { it.outcomeId }
        // Busy: a bid on its way down (a fill can still land) or one whose answer was lost. Never a second bid on that side meanwhile.
        val busy = bids.filter { it.status == MakerStatus.CANCELING || (it.status == MakerStatus.SENT && it.orderId == null) }.mapTo(HashSet()) { it.outcomeId }
        val held = held(bets, activeOutcomes) + busy
        val refused = bids.filter { it.status == MakerStatus.REFUSED && now - (it.endedAtMs ?: it.postedAtMs) < REFUSED_COOLOFF_MS }.mapTo(HashSet()) { it.outcomeId }
        val judge = { d: MakerDecision ->
            if (d is MakerDecision.Post && d.line.outcomeId in denied) MakerDecision.Skip(d.line, DENIED)
            else if (d is MakerDecision.Post && (lockedEvents[d.line.market.eventId] ?: 0L) > now) MakerDecision.Skip(d.line, "Novig has locked this game (started or frozen): it is tried again in ${LOCKED_COOLOFF_MS / 60_000} min")
            // Novig refused a bid here a moment ago (post-only: its price had moved to the bid): not asked again until the cool-off passes.
            else if (d is MakerDecision.Post && d.line.outcomeId in refused && d.line.outcomeId !in activeOutcomes) {
                MakerDecision.Skip(d.line, "Novig refused a bid here in the last ${REFUSED_COOLOFF_MS / 60_000} min (its price had moved to the bid)")
            } else d
        }
        // Keep the wallet working: stakes scale up while the bids up are worth less than the target, and riskier bids fill what the usual ones cannot.
        val usual = allBids.filter { it.active }.sumOf { it.restingDollars }
        // Volume mode scales the stakes of NEW bids up while the bids up are worth less than the wallet target (Tj, 2026-10-14: "still only around half the wallet balance"): the count stays, each bid is bigger, never past the Kelly ceiling or the most per bid.
        val scale = if (rung > 0) 1.0 else deployScale(rules, usual, wallet)
        val rulesNow = if (scale > 1.0) rules.copy(stakeScale = scale, kellyCeiling = rules.deployMaxKelly) else rules
        val ownless = MakerLines.withoutOwn(lines, allBids)
        val base = MakerQuote.decideAll(ownless, rulesNow, now, held).map(judge)
        val extra = if (rung > 0 || rules.volume) (emptyList<MakerDecision>() to null) else deployFor(rulesNow, ownless, base, bids, activeOutcomes, held, now, wallet, usual, judge)
        val decisions = base + extra.first
        // Volume mode runs no wallet-filling pass, so its Diagnostics line is made here: how many bids are wanted, how much money, how they are spread over games.
        val deployInfo = extra.second ?: if (rules.volume && wallet != null && wallet > 0.0) {
            val posts = base.filterIsInstance<MakerDecision.Post>()
            val fresh = posts.filter { it.line.outcomeId !in activeOutcomes }
            val gb = HashMap<String, Int>()
            val gd = HashMap<String, Double>()
            for (b in allBids) if (b.active) { gb.merge(b.eventId, 1, Int::plus); gd.merge(b.eventId, b.restingDollars, Double::plus) }
            for (c in fresh) { gb.merge(c.line.market.eventId, 1, Int::plus); gd.merge(c.line.market.eventId, c.cost, Double::plus) }
            Deploy(wallet, (if (rules.deployPct > 0.0) rules.deployPct else 0.9) * wallet, usual, scale, rules.deployMaxKelly, fresh.size, fresh.sumOf { it.cost }, usual + fresh.sumOf { it.cost }, spread = spreadLines(gb, gd, allBids, lines, rules))
        } else null
        if (rung == 0) lastDeploy = deployInfo?.copy(
            scale = scale, judged = decisions.size,
            skips = decisions.filterIsInstance<MakerDecision.Skip>().groupingBy { it.why.replace(Regex("[0-9]+(\\.[0-9]+)?"), "#") }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
        )
        val spent = spentToday(bets, now)
        val stopAll = stop ?: if (spent >= maxPerDay - 1e-9) "Today's limit for API bets (${money(maxPerDay)}) is reached" else null
        val resting = restingOf(active)
        // Every bid not yet ended can still fill (one on its way down too), and Novig doesn't hold a resting bid's cost from the balance (Tj's v0.53.0
        // file: "wallet $8.32 → $8.32 with $12.54 resting", NOVIG_API.md §17): what's up counts against the wallet as well as the day's limit. Under zero
        // when the money behind the bids fell short after they went up (a bet by hand, an auto-bet, a fill: Tj, 2026-10-04): the plan takes those bids down.
        val budget = spare(allBids, spent, wallet, maxPerDay)
        val actions = MakerPlan.plan(
            wanted = decisions.filterIsInstance<MakerDecision.Post>(), resting = resting, rules = rules, now = now,
            skips = decisions.filterIsInstance<MakerDecision.Skip>().associate { it.line.outcomeId to it.why }, stopAll = stopAll, budget = budget,
            repost = autoPost, partial = partial, reachUntilMs = reachUntilMs, heldItems = GameExposure.items(bets) + GameExposure.bidItems(others), onTheWay = comingDown(allBids),
            held = if (rules.repeat != null) com.tjshea.vigilant.data.tracker.RepeatGuard.fromBets(bets) + com.tjshea.vigilant.data.tracker.RepeatGuard.fromBids(others) else emptyList(),
        )
        val noReplace = HashSet<String>()
        val cancelled = withContext(NonCancellable) { runCancels(actions.cancels, problems, fills, noReplace) }
        var placed = 0
        var queue = actions.places.filter { it.line.outcomeId !in noReplace }
        // Many new bids go up in a request each 256, not one by one (each takes ~0.2 s with this lock held, and fair prices move while they do).
        if (batching && queue.size >= 2) {
            val rest = ArrayList<MakerDecision.Post>()
            var lost = false
            var first = true
            for (chunk in queue.chunked(PLACE_CHUNK)) {
                if (!keepPosting()) { lost = true; break }
                if (!first) pause(PLACE_PAUSE_MS)
                first = false
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
            if ((lockedEvents[post.line.market.eventId] ?: 0L) > clock()) continue
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
        if (rung > 0) deepPlaced += placed
        Report(placed, cancelled, fills, problems, after, stopAll, decisions, actions.waiting, partial, actions.trimmed, lastDeploy)
    }

    /** Games Novig answered EVENT_LOCKED/LEAGUE_LOCKED for, to the time (ms) we try them again. */
    private val lockedEvents = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** The rung the pass in progress works on (read by [bidFor], which runs inside [cycle]'s lock). */
    @Volatile private var cycleRung = 0

    /** Deeper second bids posted since the app started, for Diagnostics. */
    @Volatile var deepPlaced = 0L
        private set

    /** What the last pass's wallet deployment worked out, for Diagnostics ([Report.deploy]). */
    @Volatile var lastDeploy: Deploy? = null
        private set

    /**
     * How many times its usual stake a new bid asks for this pass (1 = usual): the wallet's target ([MakerRules.deployPct] of the balance) over what the bids up are worth, at most [DEPLOY_MAX_SCALE],
     * so a wallet far from the target is filled in a few passes and one near it is left alone. Each stake is still held to the per-bid maximum and to [MakerRules.deployMaxKelly] of full Kelly for its own
     * edge ([MakerQuote.stake]): a bigger stake is never a bigger bet than the edge supports.
     */
    private fun deployScale(rules: MakerRules, resting: Double, wallet: Double?): Double {
        if (rules.deployPct <= 0.0 || wallet == null || wallet <= 0.0) return 1.0
        val target = rules.deployPct * wallet
        if (resting >= target) return 1.0
        return (target / maxOf(resting, DEPLOY_FLOOR_DOLLARS)).coerceIn(1.0, DEPLOY_MAX_SCALE)
    }

    /**
     * The riskier bids that fill what the usual ones leave of the target (Tj, 2026-10-14: "if there are not great bids to put up, make it put up riskier bids but with safeguards such as higher EV"): the same
     * lines judged again with a margin [DEPLOY_EXTRA_MARGIN] wider (every one of them is +EV by at least that much more), a sharp book required, games up to [DEPLOY_HOURS] h out, and half the stake. They
     * are the last in and the first out ([MakerDecision.Post.overflow], also `obscure` for the plan). A riskier bid already resting stays wanted while it is still valid, so the set does not flap as the
     * deficit closes; a new one is added only while there is room under the target. Returns the decisions and the note for Diagnostics.
     */
    private fun deployFor(
        rules: MakerRules, lines: List<MakerLine>, base: List<MakerDecision>, bids: List<MakerBid>, activeOutcomes: Set<String>, held: Set<String>, now: Long, wallet: Double?, resting: Double,
        judge: (MakerDecision) -> MakerDecision,
    ): Pair<List<MakerDecision>, Deploy?> {
        if (rules.deployPct <= 0.0 || wallet == null || wallet <= 0.0) return emptyList<MakerDecision>() to null
        val target = rules.deployPct * wallet
        val posted = base.filterIsInstance<MakerDecision.Post>()
        val postedOutcomes = posted.mapTo(HashSet()) { it.line.outcomeId }
        val wantedNew = posted.filter { it.line.outcomeId !in activeOutcomes }.sumOf { it.cost }
        val relaxed = rules.copy(
            margin = rules.margin + DEPLOY_EXTRA_MARGIN, requireSharp = true, stakeScale = DEPLOY_OVERFLOW_STAKE, kellyCeiling = 0.0,
            earlyHours = if (rules.earlyHours > 0) maxOf(rules.earlyHours, DEPLOY_HOURS) else 0,
            // A riskier bid is posted to "see if anyone takes it" (Tj, 2026-10-14): resting well behind the best bid is the point, so the "too far behind to fill" rule does not apply to it.
            maxBehind = 0.0,
            // ... and it rests (Tj: "They don't rest anywhere near long enough"): on a game 12 h or more out its fair may be [DEPLOY_FAR_FAIR_MS] old before it must end.
            farFairMaxAgeMs = DEPLOY_FAR_FAIR_MS,
        )
        val candidates = MakerQuote.decideAll(lines, relaxed, now, held).map(judge).filterIsInstance<MakerDecision.Post>().filter { it.line.outcomeId !in postedOutcomes }
        val overflowResting = bids.filter { it.active && it.overflow }.mapTo(HashSet()) { it.outcomeId }
        var room = target - resting - wantedNew
        val out = ArrayList<MakerDecision>()
        var dollars = 0.0
        var weekJudged = 0
        var weekSkips: List<Pair<String, Int>> = emptyList()
        var anyJudged = 0
        var anySkips: List<Pair<String, Int>> = emptyList()
        // What each game already has up (resting bids and the usual bids this pass posts): the wallet-filling bids are spread over many games, never piled on a few (Tj, 2026-10-14: "I only want so many
        // bids/money on each game. Make it diversify bids among many games and bid types").
        val gameBids = HashMap<String, Int>()
        val gameDollars = HashMap<String, Double>()
        for (b in bids) if (b.active) { gameBids.merge(b.eventId, 1, Int::plus); gameDollars.merge(b.eventId, b.restingDollars, Double::plus) }
        for (c in posted) if (c.line.outcomeId !in activeOutcomes) { gameBids.merge(c.line.market.eventId, 1, Int::plus); gameDollars.merge(c.line.market.eventId, c.cost, Double::plus) }
        /** Takes [cands]: the ones already up first (they stay while valid), then every game's best in turn, within each game's cap; stops at the room the target has left. */
        fun pick(cands: List<MakerDecision.Post>, order: Comparator<MakerDecision.Post>) {
            val sorted = cands.sortedWith(compareByDescending<MakerDecision.Post> { it.line.outcomeId in overflowResting }.then(order))
            val (up, fresh) = sorted.partition { it.line.outcomeId in overflowResting }
            for (c in up) {
                out += c.copy(obscure = true, overflow = true)
                dollars += c.cost
            }
            for (c in BidSpread.interleave(fresh)) {
                if (room <= 0.0) break
                val game = c.line.market.eventId
                if (rules.perGameBids > 0 && (gameBids[game] ?: 0) >= rules.perGameBids) continue
                if (rules.perGameBidDollars > 0.0 && (gameDollars[game] ?: 0.0) + c.cost > rules.perGameBidDollars + 1e-9) continue
                out += c.copy(obscure = true, overflow = true)
                dollars += c.cost
                room -= c.cost
                gameBids.merge(game, 1, Int::plus)
                gameDollars.merge(game, c.cost, Double::plus)
            }
        }
        // The riskier bids already up first (they stay while valid), then the best new ones by their edge, while the target has room.
        pick(candidates, compareByDescending { it.evAtFair })
        // Still short, with nothing left in the usual window (Tj, 2026-10-14: "If it can't find good bids, increase the time period of game start date to bid on games later in the week ... but all at a safe EV"):
        // the same lines judged for games up to a week out, with a margin [DEPLOY_WEEK_MARGIN] wider again (a bid that rests for days is priced from older books), a sharp book required and half the stake.
        // Every rule that makes a bid safe (fresh fair, books agreeing, the sharp veto, the repeat and game guards, Profit-proven) is unchanged; only the clock and the margin move.
        if (room > 0.0 && rules.earlyHours > 0 && rules.earlyHours < DEPLOY_WEEK_HOURS) {
            val taken = HashSet<String>(postedOutcomes).apply { out.forEach { add(it.line.outcomeId) } }
            val week = relaxed.copy(margin = relaxed.margin + DEPLOY_WEEK_MARGIN, earlyHours = DEPLOY_WEEK_HOURS)
            val farAll = MakerQuote.decideAll(lines, week, now, held).map(judge)
            weekJudged = farAll.size
            weekSkips = farAll.filterIsInstance<MakerDecision.Skip>().groupingBy { it.why.replace(Regex("[0-9]+(\\.[0-9]+)?"), "#") }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
            pick(farAll.filterIsInstance<MakerDecision.Post>().filter { it.line.outcomeId !in taken }, compareByDescending { it.evAtFair })
        }
        // Last, still short (Tj, 2026-10-14: "Post as many bids as possible. Fill up the wallet. Hundreds of bids. All sports ... all times ... all props are allowed. Fill it with popular sports and market types first.
        // As long as risky bids have a higher ev ask, it is fine"): every start time, every kind, no sharp book required and no tier rules; the price sits [DEPLOY_ANY_MARGIN] further under the fair, and a
        // risky kind (prop overs, the NFL, game lines: [BidTier]) a further [DEPLOY_ANY_RISKY_MARGIN]. At least [DEPLOY_ANY_MIN_BOOKS] books must price it. The fair's freshness, the books agreeing, a sharp book's
        // veto where one prices the line, the repeat and game guards, Profit-proven and the half stake are unchanged. Popular kinds of market go first, the riskier after, each over many games.
        if (room > 0.0) {
            val taken = HashSet<String>(postedOutcomes).apply { out.forEach { add(it.line.outcomeId) } }
            val anyRules = relaxed.copy(
                margin = relaxed.margin + DEPLOY_ANY_MARGIN, earlyHours = 0, requireSharp = false, tiered = false, minBooks = maxOf(rules.minBooks, DEPLOY_ANY_MIN_BOOKS),
            )
            val riskyRules = anyRules.copy(margin = anyRules.margin + DEPLOY_ANY_RISKY_MARGIN)
            val (riskyLines, otherLines) = lines.partition { BidTier.of(it) == BidTier.RISKY }
            val anyAll = MakerQuote.decideAll(otherLines, anyRules, now, held).map(judge) + MakerQuote.decideAll(riskyLines, riskyRules, now, held).map(judge)
            anyJudged = anyAll.size
            anySkips = anyAll.filterIsInstance<MakerDecision.Skip>().groupingBy { it.why.replace(Regex("[0-9]+(\\.[0-9]+)?"), "#") }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
            val anyPosts = anyAll.filterIsInstance<MakerDecision.Post>().filter { it.line.outcomeId !in taken }
            // The hottest kinds of market first (over every game), then the popular, then the rest; the safer kind before the riskier within each.
            val byTier = anyPosts.groupBy { MarketPopularity.tier(it.line.league, it.line.market.marketType, it.line.books, rules.popularBooks) }
            for (tier in byTier.keys.sorted()) pick(byTier.getValue(tier), compareBy<MakerDecision.Post> { BidTier.of(it.line) }.thenByDescending { it.evAtFair })
        }
        val spread = spreadLines(gameBids, gameDollars, bids, lines, rules)
        return out to Deploy(wallet, target, resting, 1.0, rules.deployMaxKelly, out.size, dollars, resting + wantedNew, weekSkips = weekSkips, weekJudged = weekJudged, anySkips = anySkips, anyJudged = anyJudged, spread = spread)
    }


    /** How the bids are spread over games (Diagnostics): games with a bid, the most on one, and the five games carrying the most. */
    private fun spreadLines(gameBids: Map<String, Int>, gameDollars: Map<String, Double>, bids: List<MakerBid>, lines: List<MakerLine>, rules: MakerRules): List<String> {
        val names = HashMap<String, String>().apply {
            for (b in bids) put(b.eventId, b.eventName)
            for (l in lines) put(l.market.eventId, l.eventName)
        }
        return buildList {
            add("${gameBids.values.sum()} bids (up now and wanted this pass) across ${gameBids.size} games · most bids on one game ${gameBids.values.maxOrNull() ?: 0}, most dollars ${String.format(java.util.Locale.US, "$%.2f", gameDollars.values.maxOrNull() ?: 0.0)} (limits: ${if (rules.perGameBids > 0) "${rules.perGameBids} bids" else "no bid count"}, ${if (rules.perGameBidDollars > 0.0) String.format(java.util.Locale.US, "$%.2f", rules.perGameBidDollars) else "no dollars"} a game)")
            gameDollars.entries.sortedByDescending { it.value }.take(5).forEach { (g, d) -> add("  ${names[g] ?: g}: ${gameBids[g] ?: 0} bids, ${String.format(java.util.Locale.US, "$%.2f", d)}") }
        }
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
    private fun restingOf(bids: List<MakerBid>): List<RestingBid> = bids.filter { it.resting && it.orderId != null }.map(::restingBid)

    /** Bids whose cancel is in flight: still on Novig's book until it confirms ([MakerPlan.plan]'s onTheWay: no new bid goes up that would trade with one). */
    private fun comingDown(bids: List<MakerBid>): List<RestingBid> = bids.filter { it.status == MakerStatus.CANCELING && it.orderId != null }.map(::restingBid)

    private fun restingBid(it: MakerBid) = RestingBid(
        it.orderId!!, it.marketId, it.outcomeId, it.price, (it.contracts - it.filled).coerceAtLeast(0), it.filled, it.expiresAtMs,
        auto = it.auto, evAtFair = it.evAtFair, leads = it.bestBidAtPost.let { b -> b == null || b < it.price - 1e-9 }, obscure = it.obscure,
        game = GameRef(it.eventId, it.eventName, it.startsTs, it.league),
    )

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
        // The cycle's wash guard ([MakerPlan.wouldTrade]) for a bid by hand: one that would meet a bid of ours on the other side of its market isn't sent.
        if (MakerPlan.wouldTrade(decision, bids.filter { it.active }.groupBy({ it.marketId }, { it.outcomeId to it.price }))) {
            return@withLock "It would trade with your own bid on the other side of this market (a wash: the two prices add up to \$1 or more): cancel that bid first"
        }
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
            clientId = newClientId(), marketId = line.marketId, eventId = line.market.eventId, outcomeId = line.outcomeId,
            league = line.league, eventName = line.eventName, startsTs = line.startsTs, marketLabel = line.marketLabel, selection = line.selection,
            kind = line.kind, price = post.price, contracts = post.contracts, fair = post.anchorFair ?: line.fair ?: post.price, evAtFair = post.evAtFair,
            blendFair = line.fair, sharpFairAtPost = line.sharpFairs.minOrNull(),
            margin = MakerQuote.marginOf(line, rules, post.obscure), books = line.books, source = line.source, gameUrl = line.gameUrl, fairBasis = line.basis, postedAtMs = now,
            // Never past what it was priced from ([MakerDecision.Post.restUntilMs]: the expiry, the fair's freshness, the stop window before the start).
            expiresAtMs = minOf(now + rules.ttlMs, line.startsTs - rules.stopMs, post.restUntilMs), auto = auto,
            bestBidAtPost = line.bestBid, offerAtPost = line.offer, bookAtMs = line.bookAtMs,
            focus = rules.focus, fairBooks = line.fairBooks, obscure = post.obscure, overflow = post.overflow || cycleRung > 0, rung = cycleRung,
            fairAgeSec = line.fairAsOfMs?.let { ((now - it) / 1000L).coerceAtLeast(0L).toInt() }, fairNewestAgeSec = line.fairNewestMs?.let { ((now - it) / 1000L).coerceAtLeast(0L).toInt() },
            listAgeSec = line.listAtMs?.let { ((now - it) / 1000L).coerceAtLeast(0L).toInt() }, pageAgeSec = line.pageAtMs?.let { ((now - it) / 1000L).coerceAtLeast(0L).toInt() },
        )
        val ttl = bid.expiresAtMs!! - now
        // Worked out a moment ago: if that window has closed since, nothing is sent.
        return if (ttl < MIN_TTL_MS) null else bid to ttl
    }

    private val tooOld = { post: MakerDecision.Post -> "${post.line.selection}: its fair price is about to go old; re-priced at the next scan" }

    private suspend fun placeOne(post: MakerDecision.Post, rules: MakerRules, auto: Boolean): Placed {
        val line = post.line
        // No money ever goes on a negative-EV bid (Tj, 2026-10-11: "whether auto bet or bid or manual bet or bid"): a bid above the fair it is anchored to, or whose EV there is negative, is not sent.
        val anchor = post.anchorFair ?: line.fair
        if (post.evAtFair < 0.0 || (anchor != null && post.price > anchor + 1e-9)) {
            return Placed.Refused("${line.selection}: not posted, its bid is above the fair price (negative EV)", stopsCycle = false)
        }
        // A sharp book that prices the line below this bid disagrees that it is +EV: vetoed (Tj, 2026-10-11).
        line.sharpFairs.minOrNull()?.let { sharp ->
            if (post.price > sharp + 1e-9) return Placed.Refused("${line.selection}: not posted, a sharp book's fair is below this bid", stopsCycle = false)
        }
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
            // A locked game, market, league or player is that one's own trouble (Novig locks a game when it starts or its odds are frozen): every other bid still goes up, and the rest of that game waits. The old
            // rule stopped the whole pass at the first locked game, so a pass posted 3 bids of 250 while the wallet sat idle (Tj, 2026-10-17).
            if (e.betLocked) { if (e.code == "EVENT_LOCKED" || e.code == "LEAGUE_LOCKED") lockedEvents[line.market.eventId] = clock() + LOCKED_COOLOFF_MS }
            // The wallet, the account, the location or the throttle: the same for every bid after this one.
            Placed.Refused(why, stopsCycle = !e.betLocked && (e.status == 422 || e.status == 423 || e.status == 451 || e.status == 429 || e.status == 401 || e.status == 403))
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
            val looked = (e as? NovigTradingClient.BatchReplyUnreadable)?.let { " (it looked like ${it.shape})" } ?: ""
            Batch.Lost("Novig's answer to a batch of ${bids.size} bids couldn't be read$looked; they are looked for by their client ids, and bids go one at a time from now on")
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
        if (bids.any { MakerStore.expired(it, now) }) {
            store.update { list -> list.filterNot { MakerStore.expired(it, now) } }
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
        val other = otherDesks()
        for (o in open) {
            if (cancelStrays && o.tif == "PO" && o.clientId?.startsWith(ENGINE_PREFIX) != true && o.orderId !in knownIds && o.clientId !in knownClients && o.orderId !in other.orderIds && o.clientId !in other.clientIds) {
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
        /**
         * The first group of every client id the GitHub engine's desk sends (TCL31): a plain UUID still, so Novig takes it, and recognisable, so the phone's desk and the live desks never take a resting engine bid for a stray
         * (every one of them cancels post-only orders it has no record of).
         */
        const val ENGINE_PREFIX = "e91ce0de-"

        /** A client id for an engine bid: [ENGINE_PREFIX] and the rest of a random UUID. */
        fun engineClientId(): String = ENGINE_PREFIX + java.util.UUID.randomUUID().toString().substring(ENGINE_PREFIX.length)

        /** Wallet deployment ([deployScale], [deployFor]): the most a stake is scaled up, the resting dollars below which the scale stops growing, the riskier bids' extra margin, their stake share and their furthest start. */
        const val DEPLOY_MAX_SCALE = 4.0
        const val DEPLOY_FLOOR_DOLLARS = 1.0
        const val DEPLOY_EXTRA_MARGIN = 0.04
        const val DEPLOY_OVERFLOW_STAKE = 0.5
        const val DEPLOY_HOURS = 36

        /** The furthest start the wallet-filling bids reach when the usual window has nothing left, and the margin they add for it. */
        const val DEPLOY_WEEK_HOURS = 168
        const val DEPLOY_WEEK_MARGIN = 0.02
        /** The last wallet-filling pass: points further under the fair than the riskier pass's (any start time, any kind), then more again for a risky kind, and the fewest books pricing it. */
        const val DEPLOY_ANY_MARGIN = 0.04
        const val DEPLOY_ANY_RISKY_MARGIN = 0.04
        const val DEPLOY_ANY_MIN_BOOKS = 3

        /** How old the fair behind a wallet-filling bid on a game 12 h or more out may get before the bid ends: 30 minutes (the quotes in it were a scan's fresh ones when it was priced). */
        const val DEPLOY_FAR_FAIR_MS = 30 * 60_000L

        /** A second, deeper bid on a side that already has one: this much more margin under the fair (about 6 points more edge), so its Kelly stake is bigger and it rests where it fills rarely but well. */
        const val DEEP_MARGIN = 0.06
        /** New bids go up this many to a request (Novig's batch is all or nothing: one refused bid costs only its own ten, not a whole pass of them; Tj, 2026-10-09: "smaller batches because they are getting rejected sometimes"). */
        const val PLACE_CHUNK = 10

        /** Between two requests of new bids (Novig answered 'slow down' 7,138 times in Tj's file). */
        const val PLACE_PAUSE_MS = 300L

        /**
         * Sides held by an open bet, for [MakerQuote.decide]'s "already bet or bid" (RESEARCH.md §114): every side a bet that is NOT a bid's fill has open (by hand, the auto-bet, a
         * lock, an import) holds it, a bid resting on it or not; a bid's own fill holds its side only once that bid is over ([activeOutcomes]: the sides bids are up on).
         * Before, any open bet on a side with a bid resting there was taken for the bid's partial fill, so a taker bet on a side with a bid up left both standing.
         */
        fun held(bets: List<TrackedBet>, activeOutcomes: Set<String>): Set<String> {
            val open = bets.filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }
            return open.filter { !it.isBid }.mapTo(HashSet()) { it.outcomeId } + open.filter { it.isBid && it.outcomeId !in activeOutcomes }.map { it.outcomeId }
        }

        /** Why a denied side gets no bid. */
        const val DENIED = "You denied this bid (Bids tab › Denied to undo)"

        /** A bid whose answer was lost and that no list shows after this long is looked for in Novig's other lists, then called gone. */
        const val LOST_AFTER_MS = 90_000L

        /** No bid is sent to rest for less than this (its window closed while the pass worked). */
        const val MIN_TTL_MS = 60_000L

        /** A side Novig refused a post-only bid on isn't bid on again for this long (the scan's price there was out of date). */
        const val REFUSED_COOLOFF_MS = 5 * 60_000L
        const val LOCKED_COOLOFF_MS = 10 * 60_000L

        /** After cancels, Novig's open orders are read this long later for their confirmation (Cancel all: up to [CANCEL_LOOKS] times). */
        const val CANCEL_LOOKS = 4
        const val CANCEL_LOOK_MS = 400L

        /** A fill is judged against the fair of a scan that priced the side within this long after it ([judgeFills]). */
        const val JUDGE_WITHIN_MS = 45 * 60_000L

        /** A fills read reaches back this far before the earliest start of the bids it's for ([readFills]). */
        const val FILLS_LOOKBACK_MS = 24 * 3_600_000L
    }
}
