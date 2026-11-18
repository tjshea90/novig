package com.tjshea.vigilant.data.cloud

/**
 * The events and markets Novig has locked against new orders (`GET /v3/exchange/locks`, changelog 2026-11-10), read by the engine every 30 s with the trading key. A new order in one answers
 * `423 EVENT_LOCKED` / `MARKET_LOCKED` (cancels still work), so the engine's bets and bids skip it BEFORE spending a `place` token on a refusal ([CloudSources.unavailable]). Fail-open: a reading older than
 * [STALE_MS] says nothing, and an implausibly long list (a fault on Novig's side, not a night of locks) is ignored rather than allowed to stop all trading.
 */
class NovigLocks(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var events: Set<String> = emptySet()
    @Volatile private var markets: Set<String> = emptySet()
    @Volatile var atMs = 0L
        private set
    @Volatile var ignored = false
        private set

    fun update(eventIds: Set<String>, marketIds: Set<String>, now: Long = clock()) {
        ignored = eventIds.size + marketIds.size > MAX_PLAUSIBLE
        events = if (ignored) emptySet() else eventIds
        markets = if (ignored) emptySet() else marketIds
        atMs = now
    }

    /** Why a bet or bid on [marketId] of [eventId] must not be tried, or null. */
    fun reason(eventId: String, marketId: String, now: Long = clock()): String? {
        if (atMs == 0L || now - atMs > STALE_MS) return null
        return when {
            eventId in events -> "Novig has locked this game: it takes no new orders"
            marketId in markets -> "Novig has locked this market: it takes no new orders"
            else -> null
        }
    }

    fun describe(now: Long = clock()): String =
        if (atMs == 0L) "Novig locks: not read yet"
        else "Novig locks: ${events.size} games, ${markets.size} markets (read ${((now - atMs) / 1000).coerceAtLeast(0)} s ago${if (ignored) ", list ignored as implausibly long" else ""})"

    companion object {
        const val STALE_MS = 3 * 60_000L
        const val MAX_PLAUSIBLE = 2_000
    }
}
