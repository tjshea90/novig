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
 * Steady success raises the pace by [rampStep] every [rampEvery] clean requests, never past [maxRate]. A
 * refusal halves it for [slowForMs], then the pace restarts a step under the one refused (at most the
 * starting pace) and climbs no higher than a step under it for [ceilingForMs]; a quiet spell of
 * [idleResetMs] restarts at the starting pace (or under the remembered ceiling while it lasts).
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
    /** How long the pace that drew a refusal is remembered as the ceiling ([slowDown]). */
    private val ceilingForMs: Long = 10 * 60_000L,
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
    private var lastSlowDown = Long.MIN_VALUE

    /**
     * The pace a refusal came at, and until when it's remembered: after the slow-down the pace climbs back toward it but stops a step under it,
     * instead of going straight back to the pace that was just refused (Tj's v0.52.0 file: Novig's public edge answered 429 about once a minute,
     * every time the minute's slow-down ended and the full pace came back: 1,163 refusals).
     */
    private var ceiling = Double.MAX_VALUE
    private var ceilingUntil = Long.MIN_VALUE

    /** The rate in force right now, for display. */
    val currentRate: Double get() = if (clock() < slowUntil) slowRate else rampedRate

    /** The lowest pace a refusal took the gate to since [takeLowRate] last asked; [Double.MAX_VALUE] = none did. */
    private var lowRate = Double.MAX_VALUE

    /**
     * The lowest pace a refusal took this gate to since the last call (null when none did), then forgotten: a scan's record of what slowed it
     * (Tj, 2026-10-04: "the vigilant scanner slows down significantly when it is scanning novig prices, maybe down to 2 per second").
     */
    suspend fun takeLowRate(): Double? = mutex.withLock { lowRate.takeIf { it != Double.MAX_VALUE }.also { lowRate = Double.MAX_VALUE } }

    /** Waits until one request may go out. */
    suspend fun acquire() {
        while (true) {
            val wait = mutex.withLock {
                val now = clock()
                if (lastAcquire != Long.MIN_VALUE && now - lastAcquire > idleResetMs) {
                    rampedRate = if (now < ceilingUntil) min(ratePerSecond, slowRate) else ratePerSecond
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
        val now = clock()
        if (now < slowUntil) return@withLock
        val top = if (now < ceilingUntil) min(maxRate, ceiling - rampStep).coerceAtLeast(minRate) else maxRate
        if (rampedRate >= top) return@withLock
        if (++cleanStreak >= rampEvery) {
            cleanStreak = 0
            rampedRate = min(top, rampedRate + rampStep)
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
     * Refusals within [SAME_BURST_MS] of the last slow-down are the same one: every request in
     * flight when Novig said no comes back refused at once, and that's one refusal, not six
     * halvings (Tj, 2026-09-28: "now it is reading the API very slow").
     */
    suspend fun slowDown() = mutex.withLock {
        val now = clock()
        if (lastSlowDown != Long.MIN_VALUE && now - lastSlowDown < SAME_BURST_MS) return@withLock
        lastSlowDown = now
        val refusedAt = if (now < slowUntil) slowRate else rampedRate
        slowRate = max(minRate, refusedAt / 2)
        lowRate = min(lowRate, slowRate)
        slowUntil = now + slowForMs
        ceiling = refusedAt
        ceilingUntil = now + ceilingForMs
        // After the slow-down, back to the starting pace only if that's under the pace just refused; else a step under it, climbing from there.
        rampedRate = max(slowRate, min(ratePerSecond, refusedAt - rampStep))
        cleanStreak = 0
    }

    companion object {
        /** Refusals this close together came from one burst of requests in flight: they slow the pace once. */
        const val SAME_BURST_MS = 1_000L
    }
}
