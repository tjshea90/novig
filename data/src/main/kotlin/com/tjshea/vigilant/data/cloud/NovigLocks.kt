package com.tjshea.vigilant.data.cloud

/**
 * The events and markets Novig has locked against new orders (`GET /v3/exchange/locks`, changelog 2026-11-10), read by the engine every 30 s with the trading key. A new order in one answers
 * `423 EVENT_LOCKED` / `MARKET_LOCKED` (cancels still work), so the engine's bets and bids skip it BEFORE spending a `place` token on a refusal ([CloudSources.unavailable]).
 *
 * Fail-open twice: a reading older than [STALE_MS] says nothing; and a list that would remove more than half of what the engine asks about (over [MIN_CALLS] questions in the last [WINDOW_MS]) is treated
 * as a fault on one side or the other and ignored, so a wrong list can never stop most of the trading (the first real answer, 2026-11-18, held thousands of entries: a size cap guessed beforehand was
 * wrong, the share of the engine's own candidates is the honest test).
 */
class NovigLocks(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var events: Set<String> = emptySet()
    @Volatile private var markets: Set<String> = emptySet()
    @Volatile var atMs = 0L
        private set

    // The window's questions and how many a lock answered.
    private var windowStart = 0L
    private var calls = 0
    private var hits = 0
    @Volatile var suspicious = false
        private set

    fun update(eventIds: Set<String>, marketIds: Set<String>, now: Long = clock()) {
        events = eventIds
        markets = marketIds
        atMs = now
    }

    /** Why a bet or bid on [marketId] of [eventId] must not be tried, or null. */
    @Synchronized
    fun reason(eventId: String, marketId: String, now: Long = clock()): String? {
        if (atMs == 0L || now - atMs > STALE_MS) return null
        if (now - windowStart > WINDOW_MS) { windowStart = now; calls = 0; hits = 0; suspicious = false }
        val why = when {
            eventId in events -> "Novig has locked this game: it takes no new orders"
            marketId in markets -> "Novig has locked this market: it takes no new orders"
            else -> null
        }
        calls++
        if (why != null) hits++
        if (calls >= MIN_CALLS && hits * 2 > calls) suspicious = true
        return if (suspicious) null else why
    }

    @Synchronized
    fun describe(now: Long = clock()): String =
        if (atMs == 0L) "Novig locks: not read yet"
        else "Novig locks: ${events.size} games, ${markets.size} markets (read ${((now - atMs) / 1000).coerceAtLeast(0)} s ago; " +
            (if (suspicious) "ignored for now: it would remove $hits of the last $calls candidates)" else "$hits of the last $calls candidates skipped)")

    companion object {
        const val STALE_MS = 3 * 60_000L
        const val WINDOW_MS = 5 * 60_000L
        const val MIN_CALLS = 40
    }
}
