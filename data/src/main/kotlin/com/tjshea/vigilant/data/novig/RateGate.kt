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
 *
 * With a [maxRate] above [ratePerSecond], steady success raises the pace by [rampStep] every
 * [rampEvery] clean requests, never past [maxRate]. Any refusal drops it back to the starting pace
 * (then halves it for [slowForMs]), and so does a quiet spell of [idleResetMs]: a scan an hour
 * later starts at the proven pace again, not at whatever the last one reached.
 */
class RateGate(
    private val ratePerSecond: Double,
    private val burst: Int,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val slowForMs: Long = 60_000,
    private val minRate: Double = 1.0,
    private val maxRate: Double = ratePerSecond,
    private val rampEvery: Int = 40,
    private val rampStep: Double = 0.5,
    private val idleResetMs: Long = 5 * 60_000L,
) {
    private val mutex = Mutex()
    private var tokens = burst.toDouble()
    private var lastRefill = clock()
    private var pausedUntil = 0L
    private var slowUntil = 0L
    private var slowRate = ratePerSecond

    /** The pace reached by steady success, between [ratePerSecond] and [maxRate]. */
    private var rampedRate = ratePerSecond
    private var cleanStreak = 0
    private var lastAcquire = Long.MIN_VALUE

    /** The rate in force right now, for display. */
    val currentRate: Double get() = if (clock() < slowUntil) slowRate else rampedRate

    /** Waits until one request may go out. */
    suspend fun acquire() {
        while (true) {
            val wait = mutex.withLock {
                val now = clock()
                if (lastAcquire != Long.MIN_VALUE && now - lastAcquire > idleResetMs) {
                    rampedRate = ratePerSecond
                    cleanStreak = 0
                }
                if (now < pausedUntil) return@withLock pausedUntil - now
                val rate = if (now < slowUntil) slowRate else rampedRate
                tokens = min(burst.toDouble(), tokens + (now - lastRefill) / 1000.0 * rate)
                lastRefill = now
                if (now < slowUntil) tokens = min(tokens, 1.0) // no bursts while slowed down
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    lastAcquire = now
                    return
                }
                ((1.0 - tokens) / rate * 1000.0).toLong().coerceAtLeast(1)
            }
            sleep(wait)
        }
    }

    /**
     * A request went through without a refusal. Every [rampEvery] of these in a row, outside a
     * slow-down, raise the pace one [rampStep] (up to [maxRate]).
     */
    suspend fun success() = mutex.withLock {
        if (maxRate <= ratePerSecond || clock() < slowUntil) return@withLock
        if (++cleanStreak >= rampEvery) {
            cleanStreak = 0
            rampedRate = min(maxRate, rampedRate + rampStep)
        }
    }

    /** Hold every request until [untilMs] (a server's Retry-After). */
    suspend fun pause(untilMs: Long) = mutex.withLock {
        pausedUntil = max(pausedUntil, untilMs)
        tokens = 0.0
    }

    /**
     * Halve the rate for a while after a refusal. Repeated refusals keep halving, down to
     * [minRate]. Any ramp-up is forgotten: afterwards the pace starts over at [ratePerSecond].
     */
    suspend fun slowDown() = mutex.withLock {
        val now = clock()
        slowRate = max(minRate, (if (now < slowUntil) slowRate else rampedRate) / 2)
        slowUntil = now + slowForMs
        rampedRate = ratePerSecond
        cleanStreak = 0
    }
}
