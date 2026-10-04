package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.GameExposure
import com.tjshea.vigilant.data.tracker.GameRef
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

/** How placing one bet came out. Exactly one of these; only [Placed] and [Unconfirmed] can have moved money. */
sealed interface PlaceResult {
    /** Filled (all or part): the bet is in the Tracker with the real price, contracts and fee. */
    data class Placed(val bet: TrackedBet, val unfilledContracts: Long) : PlaceResult

    /** Novig took the order and nothing filled at that price: no money moved. */
    data class NotFilled(val reason: String) : PlaceResult

    /**
     * A check said no before anything was sent, or Novig refused the order as too small ([tooSmall]: `ORDER_TOO_SMALL`, no money moved; the
     * auto-bet learns the stake it refused and skips the same or smaller ones instead of asking again).
     */
    /** [gameLimit]: refused for the per-game limit ([BetLimits.maxPerGame]), which the auto-bet counts under one reason. */
    data class Refused(val reason: String, val tooSmall: Boolean = false, val gameLimit: Boolean = false) : PlaceResult

    /** Novig refused the order or the call failed with an answer (a wallet that's too small, a location check): no money moved. */
    data class Failed(val message: String) : PlaceResult

    /**
     * The order was sent and the answer never came back, and Novig's lists can't say what became of it. Nothing is assumed: the Tracker's
     * "Sync with Novig" adds it if it filled.
     */
    data class Unconfirmed(val message: String) : PlaceResult
}

/**
 * Places one bet at a time through Novig's API from the Vigilant subaccount (Tj, 2026-09-29: "Build the betting through the API function").
 * Money safety, in the order things happen: a confirm tap belongs to the UI; here every [ApiBetPlanner] check runs on a book read just now;
 * the order is an `IOC` at the deepest price the stake reaches, so it fills what's there at that price or better and never rests; the price
 * the confirm showed is a ceiling (a price that moved against Tj is refused, one that moved for him is taken); the same outcome is never bet
 * twice unless asked; one order at a time; an answer that gets lost is looked up by its `clientId` before anything is called failed or placed.
 * The Tracker gets the real fills, never the plan.
 */
class ApiBetPlacer(
    private val trading: NovigTradingClient,
    private val tracker: BetTracker,
    /** The market's book right now (a fresh read, not a cache), or null when it couldn't be read. */
    private val books: suspend (marketId: String) -> NovigBook?,
    private val limits: () -> BetLimits,
    private val paused: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
    /** Midnight (the device's) starting the day [now] is in: the daily limit's day. */
    private val dayStart: (Long) -> Long = { now -> localMidnight(now) },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    /** One order at a time across every placer of the app: the Bet sheet's and the auto-bet's share it, so the two can never bet the same outcome at once. */
    private val lock: Mutex = Mutex(),
    /** What Vigilant's resting bids would cost if they filled, by game ([GameExposure.bidItems]): the per-game limit counts them beside the open bets. */
    private val restingBids: suspend () -> List<GameExposure.Item> = { emptyList() },
) {

    /** What [stake] would do now: the confirm sheet's numbers. Reads the book; sends nothing. [limitsOverride]: the auto-bet's own limits. */
    suspend fun plan(target: BetTarget, stake: Double, allowRepeat: Boolean = false, limitsOverride: BetLimits? = null): PlanResult {
        val limits = limitsOverride ?: limits()
        // Pause holds what runs by itself (auto-bet); a bet Tj places by hand is his call (Tj, 2026-10-02: "I should be able to bet on whatever I want manually").
        if (!limits.manual && paused()) return PlanResult.Refused("Scanning is paused (Settings): resume it before betting.")
        val all = tracker.all()
        if (!allowRepeat && all.any { it.status == BetStatus.PENDING && it.orderId != null && it.outcomeId == target.outcomeId }) {
            return PlanResult.Refused("You've already bet this through the API and it's still open (Tracker). Bet it again from the Tracker's bet if you mean to.")
        }
        val book = try {
            books(target.market.marketId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // One game is one event (Tj, 2026-10-04): what is at risk on it across every market, open bets and resting bids, before this one.
        val game = limits.maxPerGame.takeIf { it > 0.0 }?.let { cap ->
            val ref = GameRef(target.market.eventId, target.eventName, target.startsTs, target.league)
            GameExposure.check(ref, GameExposure.items(all) + restingBids(), target.market.marketId, target.outcomeId, stake, cap)
        }
        return ApiBetPlanner.plan(target, book, stake, clock(), limits, spentToday(all), game)
    }

    /**
     * Places the bet the confirm sheet showed ([confirmedLimit]: its limit price). Everything is checked again on a new book read: a price that
     * has moved above [confirmedLimit] is refused ("the price moved: look again").
     */
    suspend fun place(target: BetTarget, stake: Double, confirmedLimit: Double, allowRepeat: Boolean = false): PlaceResult =
        placeLocked(target, stake, confirmedLimit, allowRepeat, limitsOverride = null, expectedPrice = null)

    /**
     * An auto-bet (Tj, 2026-10-01): nobody confirms, so the ceiling is the plan's own (the deepest level still at [limits]' minimum edge, on a
     * book read just now) and every refusal of [plan] applies under [limits] (Tj's per-bet maximum and minimum edge for it). [expectedPrice]: the
     * price the bet was judged at; the order book's best price must be within [AutoBet.PRICE_TOLERANCE] of it, or nothing is sent (the Novig
     * outcome found may not be the bet that was shown, or the market just moved). Never a repeat of an open bet.
     */
    suspend fun placeAuto(target: BetTarget, stake: Double, limits: BetLimits, expectedPrice: Double): PlaceResult =
        placeLocked(target, stake, confirmedLimit = 1.0, allowRepeat = false, limitsOverride = limits, expectedPrice = expectedPrice)

    private suspend fun placeLocked(
        target: BetTarget,
        stake: Double,
        confirmedLimit: Double,
        allowRepeat: Boolean,
        limitsOverride: BetLimits?,
        expectedPrice: Double?,
    ): PlaceResult = lock.withLock {
        val plan = when (val p = plan(target, stake, allowRepeat, limitsOverride)) {
            is PlanResult.Refused -> return PlaceResult.Refused(p.reason, gameLimit = p.gameLimit)
            is PlanResult.Ready -> p.plan
        }
        if (expectedPrice != null && !AutoBet.priceMatches(expectedPrice, plan.bestPrice)) {
            return PlaceResult.Refused(
                "Novig's price for the bet found (${percentText(plan.bestPrice)}) isn't the price it was judged at (${percentText(expectedPrice)}): it may not be the same bet, or the market just moved. Not bet.",
            )
        }
        if (plan.limitPrice > confirmedLimit + 1e-9) {
            return PlaceResult.Refused("The price moved while you were confirming (${percentText(confirmedLimit)} → ${percentText(plan.limitPrice)}): look at the new price and confirm again.")
        }
        // Novig parses the clientId as a UUID and refuses anything else (a "vigilant-" prefix did, on the first real order).
        val clientId = NovigTradingClient.newClientId()
        val orderId: String = try {
            trading.placeOrder(target.outcomeId, minOf(plan.limitPrice, confirmedLimit), plan.contracts, "IOC", clientId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            // Too small is about this order, not about the account: say so (an auto-bet carries on with its other bets).
            if (e.status == 400 && e.code == TOO_SMALL_CODE) {
                return PlaceResult.Refused("Novig refused the order as too small ($TOO_SMALL_CODE). Try a bigger amount.", tooSmall = true)
            }
            // A lock on this one market, game, league or player (423 MARKET_LOCKED …) is about this bet too: the account can still bet the rest.
            if (e.betLocked) return PlaceResult.Refused(e.advice)
            return PlaceResult.Failed(e.advice)
        } catch (e: Exception) {
            // No usable answer (a timeout, a dropped connection, a reply that couldn't be read): the order may have gone through, so look
            // for it by its clientId before saying anything.
            withContext(NonCancellable) { findByClientId(clientId, target.outcomeId) } ?: return PlaceResult.Unconfirmed(
                "Novig didn't answer, and its lists don't show the order (${e.message ?: "no connection"}). Nothing is assumed: open the Tracker and tap " +
                    "Sync with Novig in a minute, and check Novig before betting this again.",
            )
        }
        // Once the order is in, what came of it is always read and recorded, even if the screen that asked has gone: money spent never goes untracked.
        withContext(NonCancellable) { finish(target, orderId) }
    }

    /**
     * Locks in [holding]'s profit (Tj, 2026-10-02 ~18:50Z: "take the other side of the bet later on and guarantee a profit no matter which side of the bet
     * wins … It must guarantee profit because I will put real money on it"; RESEARCH.md §67). Under the same one-order lock as every bet: [market] must
     * be the holding's and have exactly two outcomes; the book is read again (no older than [ApiBetPlanner.MAX_BOOK_AGE_MS]) and [LockIn.plan] worked
     * out on it with [minProfit]; Novig's own positions must hold exactly what the Tracker says ([LockPositions.mismatch]), else nothing is sent; then
     * one fill-or-kill order buys the side held less of at the plan's limit: it fills whole at that price or better, or nothing moves. The fills are
     * logged as a lock ([TrackedBet.lockFor]). Not held to the per-bet or daily limits (it can only lower the risk), nor to pregame (an in-game fee is
     * in the plan's worst case); [auto] locks wait while scanning is paused.
     */
    suspend fun placeLock(holding: MarketHolding, market: NovigMarket, minProfit: Double, pushable: Boolean, auto: Boolean): PlaceResult = lock.withLock {
        if (auto && paused()) return PlaceResult.Refused("Scanning is paused: auto-lock waits for it.")
        if (market.marketId != holding.marketId) return PlaceResult.Refused("That isn't this bet's market.")
        if (market.status != "OPEN") return PlaceResult.Refused("Novig has closed this market, so it can't be locked now.")
        val pair = holding.pair(market.outcomes.map { it.outcomeId })
            ?: return PlaceResult.Refused("This market doesn't have exactly two sides on Novig, so a lock can't be exact.")
        val book = try {
            books(market.marketId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return PlaceResult.Refused("Novig's order book couldn't be read just now: try again in a moment.")
        val now = clock()
        if (now - book.fetchedAtMs > ApiBetPlanner.MAX_BOOK_AGE_MS) return PlaceResult.Refused("Novig's price is ${(now - book.fetchedAtMs) / 1000} seconds old: try again in a moment.")
        val (held, short) = pair
        // In-game (the fee charged) from the earlier of Novig's own start and the Tracker's: a start time that moved never makes a live fill look fee-free.
        val live = now >= minOf(market.startsTs, holding.first.startsTs) - LIVE_MARGIN_MS
        val plan = when (val r = LockIn.plan(held, short, book.takeLadder(market, short.outcomeId), market.fee, live, pushable, minProfit)) {
            is LockResult.None -> return PlaceResult.Refused(r.reason)
            is LockResult.Ready -> r.plan
        }
        // Only from holdings Novig confirms: a lock worked out from a wrong count could lose on one side.
        val positions = try {
            trading.positions(market.marketId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PlaceResult.Refused("Novig's positions couldn't be read (${e.message ?: e.javaClass.simpleName}): no lock without them.")
        }
        LockPositions.mismatch(holding, positions)?.let { return PlaceResult.Refused(it) }
        val first = holding.first
        val buy = market.outcomes.first { it.outcomeId == plan.buyOutcomeId }
        val ask = book.takeLadder(market, buy.outcomeId).minOfOrNull { it.price } ?: plan.limitPrice
        val target = BetTarget(
            market = market, outcomeId = buy.outcomeId, league = first.league, eventName = first.eventName, startsTs = first.startsTs,
            marketLabel = first.marketLabel,
            selection = if (buy.outcomeId == first.outcomeId) first.selection else first.otherSide ?: buy.name,
            // What Novig's own book says that side is worth (the middle of its bid and offer): a lock isn't a +EV pick.
            fair = book.bestBid(buy.outcomeId)?.price?.let { (it + ask) / 2.0 } ?: ask,
            fairAsOfMs = now, source = first.source, gameUrl = first.gameUrl, betUrl = null, auto = auto, lockFor = first.id,
        )
        val clientId = NovigTradingClient.newClientId()
        val orderId: String = try {
            trading.placeOrder(plan.buyOutcomeId, plan.limitPrice, plan.contracts, "FOK", clientId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            if (e.status == 400 && e.code == TOO_SMALL_CODE) return PlaceResult.Refused("Novig refused the lock as too small ($TOO_SMALL_CODE).", tooSmall = true)
            if (e.betLocked) return PlaceResult.Refused(e.advice)
            return PlaceResult.Failed(e.advice)
        } catch (e: Exception) {
            withContext(NonCancellable) { findByClientId(clientId, plan.buyOutcomeId) } ?: return PlaceResult.Unconfirmed(
                "Novig didn't answer, and its lists don't show the lock's order (${e.message ?: "no connection"}). Nothing is assumed: tap Sync with Novig " +
                    "in the Tracker in a minute before locking again.",
            )
        }
        withContext(NonCancellable) { finish(target, orderId) }
    }

    /** Waits for the order to end, reads its fills, and logs the bet. */
    private suspend fun finish(target: BetTarget, orderId: String): PlaceResult {
        var order: NovigOrder? = null
        val started = clock()
        while (clock() - started < ORDER_WAIT_MS) {
            order = try {
                trading.order(orderId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (order?.terminal == true) break
            pause(POLL_MS)
        }
        var fills = readFills(orderId)
        if (fills.isEmpty() && order?.status == "FILLED") {
            // The order says filled and its fills haven't shown yet.
            pause(1_000)
            fills = readFills(orderId)
        }
        if (fills.isEmpty()) {
            return when {
                order?.terminal == true -> PlaceResult.NotFilled("Nobody was selling at that price any more (Novig ended the order with nothing filled): no bet was placed and no money moved.")
                else -> PlaceResult.Unconfirmed(
                    "Novig took the order but hadn't finished it after ${ORDER_WAIT_MS / 1000} seconds. Nothing is assumed: Sync with Novig in the Tracker in a minute.",
                )
            }
        }
        val bet = tracker.logApi(target, orderId, fills) ?: return PlaceResult.Unconfirmed("Novig filled the order but the fills couldn't be read: Sync with Novig in the Tracker.")
        val unfilled = ((order?.qty ?: bet.contracts ?: 0L) - (bet.contracts ?: 0L)).coerceAtLeast(0L)
        return PlaceResult.Placed(bet, unfilled)
    }

    private suspend fun readFills(orderId: String): List<NovigFill> = try {
        trading.fills(orderId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * The order carrying [clientId] on [outcomeId], looked for in every status Novig has (a queued order is `PENDING`) twice (a lost answer's
     * order can appear late), or null.
     */
    private suspend fun findByClientId(clientId: String, outcomeId: String): String? {
        repeat(2) { round ->
            pause(if (round == 0) 1_500 else 3_000)
            for (status in listOf("PENDING", "OPEN", "FILLED", "CANCELED", "REJECTED")) {
                val hit = try {
                    trading.orders(status, outcomeId = outcomeId).firstOrNull { it.clientId == clientId }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (hit != null) return hit.orderId
            }
        }
        return null
    }

    /** Dollars staked on API bets since local midnight, orders that filled only. */
    private fun spentToday(all: List<TrackedBet>): Double {
        val from = dayStart(clock())
        // A lock only lowers the risk: it doesn't use up the day's limit.
        return all.filter { it.orderId != null && !it.isLock && it.createdAtMs >= from }.sumOf { it.stake }
    }

    private fun percentText(p: Double) = String.format(Locale.US, "%.1f%%", p * 100)

    companion object {
        /** Novig's 400 for an order under its minimum size (docs.novig.com/api/errors; the threshold isn't published). */
        const val TOO_SMALL_CODE = "ORDER_TOO_SMALL"

        const val ORDER_WAIT_MS = 12_000L

        /** A lock this close to the start is worked out as if in-game (the fee is charged by the time it fills: NOVIG_API.md §8). */
        const val LIVE_MARGIN_MS = 2 * 60_000L
        const val POLL_MS = 400L

        fun localMidnight(now: Long): Long {
            val zone = java.time.ZoneId.systemDefault()
            return java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}
