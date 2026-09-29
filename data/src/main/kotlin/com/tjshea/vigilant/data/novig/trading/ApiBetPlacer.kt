package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
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

    /** A check said no before anything was sent. */
    data class Refused(val reason: String) : PlaceResult

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
) {
    private val lock = Mutex()

    /** What [stake] would do now: the confirm sheet's numbers. Reads the book; sends nothing. */
    suspend fun plan(target: BetTarget, stake: Double, allowRepeat: Boolean = false): PlanResult {
        if (paused()) return PlanResult.Refused("Scanning is paused (Settings): resume it before betting.")
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
        return ApiBetPlanner.plan(target, book, stake, clock(), limits(), spentToday(all))
    }

    /**
     * Places the bet the confirm sheet showed ([confirmedLimit]: its limit price). Everything is checked again on a new book read: a price that
     * has moved above [confirmedLimit] is refused ("the price moved: look again").
     */
    suspend fun place(target: BetTarget, stake: Double, confirmedLimit: Double, allowRepeat: Boolean = false): PlaceResult = lock.withLock {
        val plan = when (val p = plan(target, stake, allowRepeat)) {
            is PlanResult.Refused -> return PlaceResult.Refused(p.reason)
            is PlanResult.Ready -> p.plan
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
            return PlaceResult.Failed(e.advice)
        } catch (e: Exception) {
            // No usable answer (a timeout, a dropped connection, a reply that couldn't be read): the order may have gone through, so look
            // for it by its clientId before saying anything.
            withContext(NonCancellable) { findByClientId(clientId) } ?: return PlaceResult.Unconfirmed(
                "Novig didn't answer, and its lists don't show the order (${e.message ?: "no connection"}). Nothing is assumed: open the Tracker and tap " +
                    "Sync with Novig in a minute, and check Novig before betting this again.",
            )
        }
        // Once the order is in, what came of it is always read and recorded, even if the screen that asked has gone: money spent never goes untracked.
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

    /** The order carrying [clientId], looked for in every status twice (a lost answer's order can appear late), or null. */
    private suspend fun findByClientId(clientId: String): String? {
        repeat(2) { round ->
            pause(if (round == 0) 1_500 else 3_000)
            for (status in listOf("OPEN", "FILLED", "CANCELED", "REJECTED")) {
                val hit = try {
                    trading.orders(status, 100).firstOrNull { it.clientId == clientId }
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
        return all.filter { it.orderId != null && it.createdAtMs >= from }.sumOf { it.stake }
    }

    private fun percentText(p: Double) = String.format(Locale.US, "%.1f%%", p * 100)

    companion object {
        const val ORDER_WAIT_MS = 12_000L
        const val POLL_MS = 400L

        fun localMidnight(now: Long): Long {
            val zone = java.time.ZoneId.systemDefault()
            return java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}
