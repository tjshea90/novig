package com.tjshea.vigilant.data.keys

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How a single attempt with one key went — what [KeyRotator.execute] needs to decide what's next. */
sealed interface KeyAttemptResult<out T> {
    data class Success<T>(val value: T) : KeyAttemptResult<T>

    /**
     * Rate-limited (e.g. HTTP 429) — this key should recover, so it goes back in rotation after
     * [retryAfterMs]. [reason] is a short, human-readable detail (e.g. "HTTP 429") a client can
     * attach — surfaced in [AllKeysExhaustedException]'s message so a failure is diagnosable from
     * the error text alone, not just by re-deriving it from server-side logs or screenshots (a
     * real gap found 2026-09-20: a generic "rate-limited or invalid" message left it unclear which
     * of the two actually happened, or why).
     */
    data class RateLimited(val retryAfterMs: Long, val reason: String? = null) : KeyAttemptResult<Nothing>

    /** Quota exhausted, revoked, or otherwise rejected (e.g. HTTP 401/403) — not retried automatically. */
    data class Invalid(val reason: String? = null) : KeyAttemptResult<Nothing>
}

sealed interface KeyStatus {
    data object Available : KeyStatus
    data class CoolingDown(val untilEpochMs: Long) : KeyStatus
    data object Exhausted : KeyStatus
}

data class ApiKeyState(val key: String, val status: KeyStatus)

/** Every key for a provider is unusable right now — [KeyRotator.execute] couldn't find one that worked. */
class AllKeysExhaustedException(message: String) : Exception(message)

/**
 * Tries each configured API key in turn, automatically rotating past ones that are rate-limited
 * or invalid, per Tj's own request (2026-09-20): "make a system for the app to switch keys
 * automatically when my usage runs out on any key." Provider-agnostic — SharpAPI and The Odds
 * API both use this, each reporting rate-limit/invalid in whatever shape their own HTTP responses
 * actually use (see [com.tjshea.vigilant.data.novig.SharpApiClient] and
 * [com.tjshea.vigilant.data.reference.TheOddsApiClient]).
 *
 * A rate-limited key (429-style) is assumed to recover on its own and returns to rotation once
 * its cooldown passes. An invalid key (401/403-style — bad key, or quota exhausted with no
 * automatic reset) is marked exhausted and never retried automatically; that needs a human to
 * fix (a new key, or waiting for a monthly reset neither side can predict).
 */
class KeyRotator(
    keys: List<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(keys.isNotEmpty()) { "KeyRotator needs at least one key" }
    }

    private val mutex = Mutex()
    private val states = keys.map { ApiKeyState(it, KeyStatus.Available) }.toMutableList()

    suspend fun <T> execute(providerName: String, action: suspend (key: String) -> KeyAttemptResult<T>): T {
        val triedKeys = mutableSetOf<String>()
        var lastFailureDetail: String? = null
        while (true) {
            val key = mutex.withLock {
                refreshCooldownsLocked()
                states.firstOrNull { it.status is KeyStatus.Available && it.key !in triedKeys }?.key
            } ?: throw AllKeysExhaustedException(
                "All ${states.size} $providerName key(s) are rate-limited or invalid — add a new key or wait for a reset." +
                    (lastFailureDetail?.let { " Last failure: $it." } ?: "")
            )

            triedKeys += key
            when (val result = action(key)) {
                is KeyAttemptResult.Success -> {
                    markAvailable(key)
                    return result.value
                }
                is KeyAttemptResult.RateLimited -> {
                    lastFailureDetail = "rate-limited" + (result.reason?.let { " ($it)" } ?: "")
                    markCoolingDown(key, result.retryAfterMs)
                }
                is KeyAttemptResult.Invalid -> {
                    lastFailureDetail = "invalid" + (result.reason?.let { " ($it)" } ?: "")
                    markExhausted(key)
                }
            }
        }
    }

    suspend fun snapshot(): List<ApiKeyState> = mutex.withLock {
        refreshCooldownsLocked()
        states.toList()
    }

    private suspend fun markAvailable(key: String) = mutex.withLock {
        setStatusLocked(key, KeyStatus.Available)
    }

    private suspend fun markCoolingDown(key: String, retryAfterMs: Long) = mutex.withLock {
        setStatusLocked(key, KeyStatus.CoolingDown(clock() + retryAfterMs))
    }

    private suspend fun markExhausted(key: String) = mutex.withLock {
        setStatusLocked(key, KeyStatus.Exhausted)
    }

    private fun setStatusLocked(key: String, status: KeyStatus) {
        val index = states.indexOfFirst { it.key == key }
        if (index >= 0) states[index] = states[index].copy(status = status)
    }

    private fun refreshCooldownsLocked() {
        val now = clock()
        for (i in states.indices) {
            val status = states[i].status
            if (status is KeyStatus.CoolingDown && now >= status.untilEpochMs) {
                states[i] = states[i].copy(status = KeyStatus.Available)
            }
        }
    }
}
