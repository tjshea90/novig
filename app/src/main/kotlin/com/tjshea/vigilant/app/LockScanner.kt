package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.LockIn
import com.tjshea.vigilant.data.novig.trading.LockPositions
import com.tjshea.vigilant.data.novig.trading.LockResult
import com.tjshea.vigilant.data.novig.trading.MarketHolding
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * One Novig market Tj holds through the API, as a lock sees it (Tj, 2026-10-02 ~18:50Z: "it finds proper arbitrage opportunities based on the bets I already
 * placed"): the lock on offer now ([result]), what each side is called, what holding is worth at Novig's own middle price ([holdValue]), and the profit
 * already locked when both sides are held equally ([locked]). RESEARCH.md §67.
 */
data class LockView(
    val holding: MarketHolding,
    val market: NovigMarket?,
    val result: LockResult,
    val heldName: String,
    val otherName: String,
    val pushable: Boolean,
    val live: Boolean,
    val holdValue: Double?,
    val locked: Double?,
    val atMs: Long,
) {
    val marketId: String get() = holding.marketId
}

/**
 * Looks for locks on Tj's open API bets, cheaply: only markets the Vigilant subaccount holds ([LockPositions.of]), one Novig order book each (the
 * websocket's when it's on, else the public or keyed read with its "not modified" cache: no other book's API), and each market's details kept
 * [MARKET_TTL_MS] (its two outcomes, fee and line don't change). Nothing is placed here: [AutoLocker] and the Tracker's Lock button do that.
 */
class LockScanner(private val c: AppContainer, private val clock: () -> Long = System::currentTimeMillis) {

    private val markets = ConcurrentHashMap<String, Pair<NovigMarket, Long>>()

    /** The market's details, read at most every [MARKET_TTL_MS]; null when Novig doesn't list it. */
    suspend fun market(marketId: String): NovigMarket? {
        val now = clock()
        markets[marketId]?.takeIf { now - it.second < MARKET_TTL_MS }?.let { return it.first }
        val m = try {
            c.novig.market(marketId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return markets[marketId]?.first
        markets[marketId] = m to now
        return m
    }

    /** Every market [bets] hold through the API, with its lock now at [minProfit] (dollars, per market). Empty when there's none. */
    suspend fun scan(bets: List<TrackedBet>, minProfit: (MarketHolding) -> Double = { LockIn.MIN_PROFIT }): Map<String, LockView> {
        val now = clock()
        val holdings = LockPositions.of(bets, now)
        if (holdings.isEmpty()) return emptyMap()
        val books = try {
            c.novig.books(holdings.map { it.marketId }).books
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        val out = LinkedHashMap<String, LockView>()
        for (h in holdings) {
            val market = market(h.marketId)
            val first = h.first
            val pair = market?.let { h.pair(it.outcomes.map { o -> o.outcomeId }) }
            val pushable = market?.let { LockIn.pushable(it.marketType, it.strike, first.league) } ?: true
            val live = now >= first.startsTs - ApiBetPlacer.LIVE_MARGIN_MS
            val book = books[h.marketId]
            fun nameOf(outcomeId: String) = when {
                outcomeId == first.outcomeId -> first.selection
                else -> first.otherSide ?: market?.outcomes?.firstOrNull { it.outcomeId == outcomeId }?.name ?: "the other side"
            }
            val result = when {
                market == null -> LockResult.None("Novig no longer lists this market, so it can't be locked.")
                pair == null -> LockResult.None("This market doesn't have exactly two sides on Novig, so a lock can't be exact.")
                book == null -> LockResult.None("Novig's order book couldn't be read just now.")
                else -> LockIn.plan(pair.first, pair.second, book.takeLadder(market, pair.second.outcomeId), market.fee, live, pushable, minProfit(h))
            }
            val heldId = pair?.first?.outcomeId ?: first.outcomeId
            // What holding is worth at Novig's own middle price for the side held more of (its best bid and its offer).
            val holdValue = if (market != null && pair != null && book != null) {
                val ask = book.takeLadder(market, heldId).minOfOrNull { it.price }
                val bid = book.bestBid(heldId)?.price
                val mid = if (ask != null && bid != null) (ask + bid) / 2.0 else null
                mid?.let { LockIn.holdValue(pair.first, pair.second, heldId, it) }
            } else null
            val locked = pair?.takeIf { it.first.contracts == it.second.contracts && it.first.contracts > 0L }?.let {
                it.first.contracts * com.tjshea.vigilant.engine.EvMath.CONTRACT_PAYOUT_DOLLARS - h.spent
            }
            out[h.marketId] = LockView(
                holding = h, market = market, result = result, heldName = nameOf(heldId),
                otherName = nameOf(pair?.second?.outcomeId ?: ""), pushable = pushable, live = live, holdValue = holdValue, locked = locked, atMs = now,
            )
        }
        return out
    }

    companion object {
        const val MARKET_TTL_MS = 2 * 60_000L
    }
}

/**
 * Auto-lock (Tj, 2026-10-02 ~18:50Z: "include an option to auto bet these bets in addition to whatever the auto bet system already does"): run in each
 * background cycle while [ScanSettings.autoLocksNow]. Every market whose lock pays at least [ScanSettings.autoLockMinPercent] of what's staked in it
 * (and at least [LockIn.MIN_PROFIT]) whichever side wins is locked through [ApiBetPlacer.placeLock] (fresh book, Novig's positions checked, one
 * fill-or-kill order); in-game only with [ScanSettings.autoLockLive]. A market that was refused or failed waits [RETRY_MS] before it's tried again.
 */
class AutoLocker(
    private val app: Application,
    private val c: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val placer: () -> ApiBetPlacer? = { c.autoBetPlacer() },
) {
    private val waitUntil = ConcurrentHashMap<String, Long>()

    /** The least a lock in [h] must pay, in dollars, at [s]. */
    fun minProfit(s: ScanSettings, h: MarketHolding): Double = maxOf(LockIn.MIN_PROFIT, s.autoLockMinPercent * h.spent)

    /** Locks what [s] allows now; the locks placed. */
    suspend fun run(s: ScanSettings): List<TrackedBet> {
        if (!s.autoLocksNow || !AppBook.isNovig) return emptyList()
        val p = placer() ?: return emptyList()
        val now = clock()
        val views = c.locks.scan(c.tracker.all()) { minProfit(s, it) }
        val placed = ArrayList<TrackedBet>()
        for (v in views.values) {
            val plan = (v.result as? LockResult.Ready)?.plan ?: continue
            if (v.live && !s.autoLockLive) continue
            if ((waitUntil[v.marketId] ?: 0L) > now) continue
            val market = v.market ?: continue
            when (val r = p.placeLock(v.holding, market, minProfit(s, v.holding), v.pushable, auto = true)) {
                is PlaceResult.Placed -> {
                    placed += r.bet
                    c.eventLog.info("LOCK", "auto-lock placed: ${r.bet.contracts} contracts of ${v.otherName} at ${r.bet.american}, about ${money(plan.guaranteed)} either way")
                    c.eventLog.count("lock.auto.placed")
                    AutoBetNotes.locked(app, r.bet, v, plan.guaranteed)
                }
                is PlaceResult.Unconfirmed -> {
                    // An answer lost mid-order: nothing more on this market until a Sync shows what happened.
                    waitUntil[v.marketId] = Long.MAX_VALUE
                    c.eventLog.warn("LOCK", "auto-lock order unconfirmed: ${r.message}")
                    c.eventLog.count("lock.auto.unconfirmed")
                }
                else -> {
                    waitUntil[v.marketId] = now + RETRY_MS
                    c.eventLog.count("lock.auto.notPlaced")
                }
            }
        }
        return placed
    }

    private fun money(v: Double) = String.format(java.util.Locale.US, "$%.2f", v)

    companion object {
        const val RETRY_MS = 60_000L
    }
}
