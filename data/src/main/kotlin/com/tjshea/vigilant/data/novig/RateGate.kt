package com.tjshea.vigilant.data.novig

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * A token bucket shared by every request to one host: on average at most [ratePerSecond], with
 * bursts of up to [burst]. Novig's public edge throttles per IP (NOVIG_API.md §5.1: ~40–100 fast
 * requests, then `429` with `Retry-After: 1`), so staying under it costs a scan a few seconds
 * and saves it from being refused.
 *
 * [pause] holds every caller until a server-given time (a `Retry-After`), and [slowDown] halves
 * the rate for [slowForMs] after a refusal, so one 429 can't turn into a burst of them.
 */
class RateGate(
    private val ratePerSecond: Double,
    private val burst: Int,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val slowForMs: Long = 60_000,
    private val minRate: Double = 1.0,
) {
    private val mutex = Mutex()
    private var tokens = burst.toDouble()
    private var lastRefill = clock()
    private var pausedUntil = 0L
    private var slowUntil = 0L
    private var slowRate = ratePerSecond

    /** The rate in force right now, for display. */
    val currentRate: Double get() = if (clock() < slowUntil) slowRate else ratePerSecond

    /** Waits until one request may go out. */
    suspend fun acquire() {
        while (true) {
            val wait = mutex.withLock {
                val now = clock()
                if (now < pausedUntil) return@withLock pausedUntil - now
                val rate = if (now < slowUntil) slowRate else ratePerSecond
                tokens = min(burst.toDouble(), tokens + (now - lastRefill) / 1000.0 * rate)
                lastRefill = now
                if (now < slowUntil) tokens = min(tokens, 1.0) // no bursts while slowed down
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    return
                }
                ((1.0 - tokens) / rate * 1000.0).toLong().coerceAtLeast(1)
            }
            sleep(wait)
        }
    }

    /** Hold every request until [untilMs] (a server's Retry-After). */
    suspend fun pause(untilMs: Long) = mutex.withLock {
        pausedUntil = max(pausedUntil, untilMs)
        tokens = 0.0
    }

    /** Halve the rate for a while after a refusal. Repeated refusals keep halving, down to [minRate]. */
    suspend fun slowDown() = mutex.withLock {
        val now = clock()
        slowRate = max(minRate, (if (now < slowUntil) slowRate else ratePerSecond) / 2)
        slowUntil = now + slowForMs
    }
}
