package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.ApiBetPlanner
import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetPlan
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.novig.trading.PlanResult
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.GameExposure
import com.tjshea.vigilant.data.tracker.GameRef
import com.tjshea.vigilant.data.tracker.RepeatGuard
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Places one bet at a time on Kalshi (KV3, Tj 2026-10-20: "find positive EV bets on kalshi just like what vigilant already does for novig ... use all the same
 * features"). It is the Novig placer's sequence with Kalshi's order API: every [ApiBetPlanner] check runs on the tickers' tops read just now (the SAME planner, so the pregame,
 * freshness, edge, odds, per-bet, per-day and per-game rules are the Novig app's, word for word with "Kalshi" for "Novig"); the order is an IOC at the ask it was judged at
 * (it fills what is there at that price or better, never rests); the fee is Kalshi's rounded-up cent on the whole order, and the edge is checked AFTER it; an unanswered
 * order is looked up by its client id, never sent again; one order at a time; the Tracker gets the real fills.
 *
 * Counting: the shared maths count contracts of 1c payout (Novig's). A Kalshi contract pays $1, so a plan of N such contracts is N/100 Kalshi contracts, and only whole
 * ones are ordered; the tracker is fed the same 100-per-contract units, so its payout and profit maths are right for both.
 */
class KalshiBetPlacer(
    private val trading: KalshiTradingClient,
    private val tracker: BetTracker,
    private val limits: () -> BetLimits,
    private val paused: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
    private val dayStart: (Long) -> Long = { now -> ApiBetPlacer.localMidnight(now) },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    /** One order at a time across every Kalshi placer of the app (the Bet sheet's and the auto-bet's). */
    private val lock: Mutex = Mutex(),
    private val restingBids: suspend () -> List<GameExposure.Item> = { emptyList() },
    private val repeatRules: () -> RepeatGuard.Rules? = { null },
    private val restingPositions: suspend () -> List<RepeatGuard.Pos> = { emptyList() },
) {
    /** What [plan] tells the sheet; the fresh book it was worked from is kept for the order. */
    private data class Planned(val result: PlanResult, val ref: KalshiBook.Ref?, val book: NovigBook?)

    suspend fun plan(target: BetTarget, stake: Double, allowRepeat: Boolean = false, limitsOverride: BetLimits? = null): PlanResult = planned(target, stake, allowRepeat, limitsOverride).result

    private suspend fun planned(target: BetTarget, stake: Double, allowRepeat: Boolean, limitsOverride: BetLimits?): Planned {
        val limits = limitsOverride ?: limits()
        if (!limits.manual && paused()) return Planned(PlanResult.Refused("Scanning is paused (Settings): resume it before betting."), null, null)
        val all = tracker.all()
        if (!allowRepeat && all.any { it.status == BetStatus.PENDING && it.orderId != null && it.outcomeId == target.outcomeId }) {
            return Planned(PlanResult.Refused("You've already bet this on Kalshi and it's still open (Bets). Bet it again from there if you mean to."), null, null)
        }
        val refs = KalshiBook.refs(target.market)
        val ref = refs[target.outcomeId] ?: return Planned(PlanResult.Refused("This isn't a Kalshi market the app can order on (its ticker is missing): scan again."), null, null)
        val now0 = clock()
        val tops = try {
            trading.tops(KalshiBook.tickers(target.market))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val (book, closed) = if (tops == null) null to false else KalshiBook.of(target.market, tops, clock())
        val market = if (closed) target.market.copy(status = "CLOSED") else target.market
        val game = limits.maxPerGame.takeIf { it > 0.0 }?.let { cap ->
            val gref = GameRef(target.market.eventId, target.eventName, target.startsTs, target.league)
            GameExposure.check(gref, GameExposure.items(all) + restingBids(), target.market.marketId, target.outcomeId, stake, cap)
        }
        val kt = target.copy(market = market, book = BOOK)
        val raw = ApiBetPlanner.plan(kt, book, stake, clock(), limits, spentToday(all), game, null)
        val words = words(raw)
        val whole = when (words) {
            is PlanResult.Refused -> words
            is PlanResult.Ready -> wholeContracts(kt, words.plan, limits, ref)
        }
        return Planned(guardRepeat(whole, target, limits, all), ref, book)
    }

    /**
     * Whole Kalshi contracts and the cent-rounded fee: [plan] is in 1c units; the order is [floor] of a hundredth of that, its fee Kalshi's, and the edge is judged
     * on THAT cost (a 1-contract order at 50c pays 2c of fee for a 1.75c model fee), under the same minimum the planner used.
     */
    private fun wholeContracts(target: BetTarget, plan: BetPlan, limits: BetLimits, ref: KalshiBook.Ref): PlanResult {
        val count = floor(plan.contracts / 100.0 + 1e-9).toLong()
        val price = plan.limitPrice
        if (count < 1L) {
            return PlanResult.Refused("That is less than one Kalshi contract (a contract pays $1; this side costs ${cents(price)} each): bet at least ${money(price + KalshiFees.takerPerContract(price))}.")
        }
        val fee = KalshiFees.takerFeeDollars(count.toDouble(), price)
        val cost = count * price + fee
        val perContract = cost / count
        val ev = target.fair / perContract - 1.0
        val floorEv = if (limits.manual) 0.0 else limits.minEv
        if (ev < floorEv - 1e-9) {
            return PlanResult.Refused(
                "After Kalshi's fee for ${count} contract${if (count == 1L) "" else "s"} (${money(fee)}, rounded up to the cent) the edge is ${percent(ev)}, " +
                    (if (limits.manual) "under zero" else "under your ${percent(limits.minEv)} minimum") + ": a bigger bet spreads the rounding.",
                negativeEv = ev <= 0.0,
            )
        }
        val rounded = if (count * 100 != plan.contracts) "Kalshi sells whole \$1 contracts: this bets $count of them (${money(cost)})." else null
        val note = listOfNotNull(plan.note, rounded).joinToString(" ").ifEmpty { null }
        return PlanResult.Ready(
            plan.copy(
                contracts = count * 100, expectedCost = cost, averagePrice = price, payout = count.toDouble(), evPercent = ev,
                bestPrice = price, note = note, feeDollars = fee,
            ),
        )
    }

    private suspend fun guardRepeat(result: PlanResult, target: BetTarget, limits: BetLimits, all: List<TrackedBet>): PlanResult {
        val rules = repeatRules() ?: return result
        val ready = result as? PlanResult.Ready ?: return result
        val p = ready.plan
        val held = RepeatGuard.fromBets(all) + restingPositions()
        val why = RepeatGuard.check(
            rules, held, target.market.marketId, target.outcomeId, p.expectedCost, p.payout, target.market.outcomes.map { it.outcomeId },
            RepeatGuard.family(target.eventName, target.startsTs, target.marketLabel, target.selection),
        ) ?: return result
        return if (limits.manual) PlanResult.Ready(p.copy(note = listOfNotNull(p.note, "Heads up: $why You can still place it.").joinToString(" ")))
        else PlanResult.Refused("Not bet: $why")
    }

    /** A bet by hand: [confirmedLimit] is the price the sheet showed; a price above it is refused. */
    suspend fun place(target: BetTarget, stake: Double, confirmedLimit: Double, allowRepeat: Boolean = false): PlaceResult =
        placeLocked(target, stake, confirmedLimit, allowRepeat, null, null)

    /**
     * Exactly [contracts] whole contracts at [maxPrice] or better (the second leg of an arbitrage, KV5b: the size is the first leg's fill, the price ceiling is the break-even that
     * keeps the pair profitable). The tops are read just now and the ask must be at or under [maxPrice]; the order is an IOC at the ask; the fills are logged like any bet. The fair on
     * [target] is the break-even probability the caller derived, so the bet's recorded edge is the arbitrage's own.
     */
    suspend fun placeExact(target: BetTarget, contracts: Long, maxPrice: Double): PlaceResult {
        lock.lock()
        try {
            if (contracts < 1L) return PlaceResult.Refused("Nothing to buy: under one contract.")
            val ref = KalshiBook.refs(target.market)[target.outcomeId] ?: return PlaceResult.Refused("This isn't a Kalshi market the app can order on (its ticker is missing).")
            val tops = try {
                trading.tops(KalshiBook.tickers(target.market))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return PlaceResult.Refused("Kalshi's price couldn't be read just now: ${e.message ?: e.javaClass.simpleName}.")
            }
            val top = tops[ref.ticker]
            if (top == null || !top.tradable) return PlaceResult.Refused("Kalshi has closed this market.")
            val ask = top.priceFor(ref.side) ?: return PlaceResult.Refused("Nobody is selling this side on Kalshi right now.")
            if (ask > maxPrice + 1e-9) return PlaceResult.Refused("Kalshi's price moved: ${percentText(ask)} now, more than the ${percentText(maxPrice)} that keeps the pair profitable.")
            val size = top.sizeFor(ref.side) ?: 0.0
            val take = minOf(contracts.toDouble(), size).toLong()
            if (take < 1L) return PlaceResult.Refused("Kalshi has under one contract at that price.")
            val clientId = KalshiTradingClient.newClientId()
            val placed = try {
                trading.buy(ref.ticker, ref.side, ask, take, clientId, KalshiTradingClient.IOC)
            } catch (e: CancellationException) {
                throw e
            } catch (e: KalshiApiException) {
                if (e.status == 0 || e.status >= 500) {
                    val found = withContext(NonCancellable) { findByClientId(clientId, ref.ticker) }
                        ?: return PlaceResult.Unconfirmed("Kalshi didn't answer, and its orders don't show this one (${e.message}). Nothing is assumed: check Kalshi's portfolio.")
                    return withContext(NonCancellable) { finish(target, found.orderId, take, ref, clientId) }
                }
                return if (e.status == 400 || e.status == 409 || e.status == 422) PlaceResult.Refused(advice(e)) else PlaceResult.Failed(advice(e))
            }
            return withContext(NonCancellable) { finish(target, placed.orderId, take, ref, clientId, placed) }
        } finally {
            lock.unlock()
        }
    }

    /** An auto-bet: nobody confirms, so [expectedPrice] (the price it was judged at) must match the ask now and [limits] are the auto-bet's. */
    suspend fun placeAuto(target: BetTarget, stake: Double, limits: BetLimits, expectedPrice: Double): PlaceResult =
        placeLocked(target, stake, 1.0, false, limits, expectedPrice)

    private suspend fun placeLocked(
        target: BetTarget,
        stake: Double,
        confirmedLimit: Double,
        allowRepeat: Boolean,
        limitsOverride: BetLimits?,
        expectedPrice: Double?,
    ): PlaceResult {
        lock.lock()
        try {
            val p = planned(target, stake, allowRepeat, limitsOverride)
            val plan = when (val r = p.result) {
                is PlanResult.Refused -> return PlaceResult.Refused(r.reason, gameLimit = r.gameLimit)
                is PlanResult.Ready -> r.plan
            }
            val ref = p.ref ?: return PlaceResult.Refused("This isn't a Kalshi market the app can order on.")
            if (expectedPrice != null && !AutoBet.priceMatches(expectedPrice, plan.bestPrice)) {
                return PlaceResult.Refused("Kalshi's price for the bet found (${percentText(plan.bestPrice)}) isn't the price it was judged at (${percentText(expectedPrice)}): it may not be the same bet, or the market just moved. Not bet.")
            }
            if (plan.limitPrice > confirmedLimit + 1e-9) {
                return PlaceResult.Refused("The price moved while you were confirming (${percentText(confirmedLimit)} → ${percentText(plan.limitPrice)}): look at the new price and confirm again.")
            }
            val count = plan.contracts / 100
            val clientId = KalshiTradingClient.newClientId()
            val placed = try {
                trading.buy(ref.ticker, ref.side, minOf(plan.limitPrice, confirmedLimit), count, clientId, KalshiTradingClient.IOC)
            } catch (e: CancellationException) {
                throw e
            } catch (e: KalshiApiException) {
                if (e.status == 0 || e.status >= 500) {
                    // No usable answer: the order may have gone through, so it is looked for by its client id before anything is said.
                    val found = withContext(NonCancellable) { findByClientId(clientId, ref.ticker) }
                        ?: return PlaceResult.Unconfirmed("Kalshi didn't answer, and its orders don't show this one (${e.message}). Nothing is assumed: check Kalshi's portfolio before betting this again.")
                    return withContext(NonCancellable) { finish(target, found.orderId, count, ref, clientId) }
                }
                return when {
                    e.status == 400 || e.status == 409 || e.status == 422 -> PlaceResult.Refused(advice(e))
                    else -> PlaceResult.Failed(advice(e))
                }
            } catch (e: Exception) {
                val found = withContext(NonCancellable) { findByClientId(clientId, ref.ticker) }
                    ?: return PlaceResult.Unconfirmed("Something went wrong while placing it (${e.message ?: e.javaClass.simpleName}) and Kalshi's orders don't show it. Nothing is assumed: check Kalshi's portfolio.")
                return withContext(NonCancellable) { finish(target, found.orderId, count, ref, clientId) }
            }
            return withContext(NonCancellable) { finish(target, placed.orderId, count, ref, clientId, placed) }
        } finally {
            lock.unlock()
        }
    }

    /** Reads what became of the order and logs the bet from the real fills. */
    private suspend fun finish(target: BetTarget, orderId: String, count: Long, ref: KalshiBook.Ref, clientId: String, placed: KalshiPlaced? = null): PlaceResult {
        var order: KalshiOrder? = null
        repeat(READS) { i ->
            order = try { trading.order(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { order }
            if (order?.terminal == true || (placed != null && placed.remaining <= 0.0)) return@repeat
            if (i < READS - 1) pause(POLL_MS)
        }
        var fills = try { trading.fills(orderId = orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        if (fills.isEmpty() && (order?.fillCount ?: placed?.filled ?: 0.0) > 0.0) {
            pause(1_000)
            fills = try { trading.fills(orderId = orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        }
        val novig = if (fills.isNotEmpty()) {
            fills.map { f ->
                NovigFill(f.fillId, orderId, clientId, target.market.marketId, target.outcomeId, (f.count * 100.0).roundToLong(), f.count * f.price, f.taker, f.fee, f.ts ?: clock())
            }
        } else {
            // The fills list lags the order: the order's own totals are the record.
            val o = order
            val n = o?.fillCount ?: placed?.filled ?: 0.0
            if (n <= 0.0) emptyList()
            else listOf(
                NovigFill(
                    "order-$orderId", orderId, clientId, target.market.marketId, target.outcomeId, (n * 100.0).roundToLong(),
                    o?.cost?.takeIf { it > 0.0 } ?: (n * (placed?.averagePrice ?: 0.0)), true,
                    o?.fees?.takeIf { it > 0.0 } ?: (n * (placed?.averageFee ?: 0.0)), clock(),
                ),
            )
        }
        if (novig.isEmpty() || novig.sumOf { it.qty } <= 0L) {
            // An IOC that matched nothing ends cancelled, with nothing to pay.
            if (order?.terminal == true || (placed != null && placed.filled <= 0.0 && placed.remaining <= 0.0)) {
                return PlaceResult.NotFilled("Nobody was selling at that price any more (Kalshi ended the order with nothing filled): no bet was placed and no money moved.")
            }
            // Resting or unknown: cancel this very order, never a second one.
            val cancelled = try { trading.cancel(orderId, ref.ticker) } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
            return PlaceResult.Unconfirmed(
                (if (cancelled) "Kalshi took the order but it hadn't filled and was cancelled" else "Kalshi took the order and its state is unknown") +
                    " (last seen: ${order?.status ?: "never read"}). Nothing is assumed: check Kalshi's portfolio before betting this again.",
            )
        }
        val kt = target.copy(book = BOOK, betUrl = target.betUrl ?: ref.ticker.let { "https://kalshi.com/markets/$it" })
        val bet = tracker.logApi(kt, orderId, novig) ?: return PlaceResult.Unconfirmed("Kalshi filled the order but the fills couldn't be recorded: check Kalshi's portfolio.")
        val unfilled = (count * 100 - (bet.contracts ?: 0L)).coerceAtLeast(0L)
        return PlaceResult.Placed(bet, unfilled)
    }

    /** The order carrying [clientId] on [ticker], looked for twice (a lost answer's order can appear late). */
    private suspend fun findByClientId(clientId: String, ticker: String): KalshiOrder? {
        repeat(2) { round ->
            pause(if (round == 0) 1_500 else 3_000)
            val hit = try { trading.findByClientId(clientId, ticker) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (hit != null) return hit
        }
        return null
    }

    private fun spentToday(all: List<TrackedBet>): Double {
        val from = dayStart(clock())
        return all.filter { it.orderId != null && !it.isLock && it.createdAtMs >= from }.sumOf { it.stake }
    }

    /** Kalshi's refusal in words for the card. */
    private suspend fun advice(e: KalshiApiException): String = when {
        e.isAuth -> "Kalshi refused the key (${e.code ?: e.status}): ${e.detail ?: "check that the key can trade"} (Settings › Kalshi)."
        // Kalshi's own words go on the card too (Tj, 2026-10-23: "all my kalshi bets say I don't have enough money" with money in the account): the reason must be readable, not only the guess.
        e.code?.contains("insufficient", true) == true -> "Kalshi says the account doesn't have enough money for this order (Kalshi: HTTP ${e.status} ${e.code}${e.detail?.let { " — $it" }.orEmpty()}; sent ${trading.lastOrder ?: "?"}; Kalshi cash now ${runCatching { trading.balance().let { b -> money(b.cash) + if (b.shards.isEmpty()) "" else " (by exchange shard: " + b.shards.joinToString(", ") { (i, v) -> "#$i ${money(v)}" } + ")" } }.getOrDefault("unreadable")})."
        e.code?.contains("market_closed", true) == true || e.detail?.contains("closed", true) == true -> "Kalshi says this market is closed."
        else -> "Kalshi refused the order: ${e.code ?: e.status}${e.detail?.let { " — $it" }.orEmpty()}"
    }

    /** [r]'s reasons said for Kalshi: the shared planner names its book in its words. */
    private fun words(r: PlanResult): PlanResult = when (r) {
        is PlanResult.Refused -> r.copy(reason = kalshiWords(r.reason))
        is PlanResult.Ready -> r.copy(plan = r.plan.copy(note = r.plan.note?.let(::kalshiWords)))
    }

    private fun percentText(p: Double) = String.format(Locale.US, "%.1f%%", p * 100)
    private fun percent(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)
    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)
    private fun cents(p: Double) = String.format(Locale.US, "%.0f¢", p * 100)

    companion object {
        const val BOOK = "Kalshi"
        const val READS = 4
        const val POLL_MS = 400L

        /** The shared planner's words (written for Novig) said for Kalshi. */
        fun kalshiWords(s: String): String = s
            .replace("Settings › Betting & Novig account", "Settings › Kalshi")
            .replace("Auto-bet tab › Smallest edge (EV) at Novig's price now", "Kalshi tab › Auto-bet › Smallest edge (EV) at Kalshi's price now")
            .replace("Novig takes about 5 seconds to answer an order in play: if the price moves against you first, nothing is bought.", "Kalshi answers an order in milliseconds, but the order only fills at the price shown or better: if the price moves against you first, nothing is bought.")
            .replace("Novig", "Kalshi")
    }
}
