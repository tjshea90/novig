package com.tjshea.vigilant.data.novig

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A bet being checked or placed comes first (Tj, 2026-10-12: "sometimes it lags when I place a bet ... it may be because it is scanning simultaneously ... briefly pause scanning as soon as I press bet, then resume where it left off").
 * Every request any scan makes goes through a [RateGate]; while a bet holds [hold], those requests wait at the gate ([yieldToBet], at most [MAX_WAIT_MS] so a stuck bet cannot freeze scanning), so the bet's own reads and order go out
 * at once on a free connection and key limit. When the bet ends the waiting requests go on exactly where they were: nothing is cancelled or restarted. The bet's own requests carry [Element] in their coroutine context and never wait.
 */
object BetPriority {
    private val active = AtomicInteger(0)

    /** Whether a bet is being checked or placed right now. */
    val isActive: Boolean get() = active.get() > 0

    object Key : CoroutineContext.Key<Element>
    object Element : AbstractCoroutineContextElement(Key)

    const val MAX_WAIT_MS = 20_000L
    private const val STEP_MS = 20L

    /** Runs [block] as the bet that scans give way to. Nesting is fine. */
    suspend fun <T> hold(block: suspend () -> T): T {
        active.incrementAndGet()
        try {
            return withContext(Element) { block() }
        } finally {
            active.decrementAndGet()
        }
    }

    /** A scan's request waits here while a bet is held; the bet's own requests pass. */
    suspend fun yieldToBet() {
        if (active.get() == 0 || currentCoroutineContext()[Key] != null) return
        var waited = 0L
        while (active.get() > 0 && waited < MAX_WAIT_MS) {
            delay(STEP_MS)
            waited += STEP_MS
        }
    }
}
