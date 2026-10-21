package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyState
import com.tjshea.vigilant.data.keys.ProviderView

/**
 * The light on an API card (Tj, 2026-10-20, CH48): "green (up and active), yellow (slow or problematic or refused connections recently), red (no connection or no credits left)", grey when the API is off.
 * Decided from what is fresh: the engine's own live check of the API ([ApiCheckLine], every few minutes), the usage ledger (credits, refused and spent keys) and, last, how the API did on the last scan. A green
 * light needs a check that passed, recently, with no key refused and credits to spend; the last scan's counts alone never make it green (a Pinnacle board answered by PinnWire lit pinnapi's card).
 */
object ApiState {
    enum class Light { OFF, GREEN, YELLOW, RED, UNKNOWN }

    data class Verdict(val light: Light, /** One short line: why, in words. */ val words: String)

    /** A check older than this says nothing now (the engine checks every 5 minutes; a few misses are tolerated). */
    const val STALE_MS = 15 * 60_000L

    /** A check that took longer than this is slow. */
    const val SLOW_MS = 5_000L

    /** Credits under this share of the allowance are low. */
    const val LOW_FRACTION = 0.10

    fun of(check: ApiCheckLine?, view: ProviderView?, lastScan: CloudDashboard.Health?, now: Long): Verdict {
        if (check != null && !check.on) return Verdict(Light.OFF, if (!check.hasKey) "no key saved: never called" else "switched off: never called")
        val keys = view?.keys.orEmpty()
        val refused = keys.count { it.state == KeyState.REFUSED }
        val allRefused = keys.isNotEmpty() && refused >= keys.size
        // Red: nothing can be asked of it. Credits used up, every key refused, or the last checks could not connect.
        if (view != null && view.spent) return Verdict(Light.RED, "no credits left" + (keys.mapNotNull { it.until }.minOrNull()?.let { " · back ${com.tjshea.vigilant.data.keys.UsageMeter.whenText(it, now)}" } ?: ""))
        if (allRefused) return Verdict(Light.RED, "every key refused by the provider")
        if (check != null && check.atMs > 0 && !check.ok && check.failStreak >= 2) return Verdict(Light.RED, "no connection: ${check.message.ifBlank { "the last ${check.failStreak} checks failed" }} (retrying)")
        if (check == null || check.atMs <= 0L) {
            // Not checked yet by the engine (an older engine build, or just started): say so rather than guess green from the last scan.
            return when (lastScan) {
                CloudDashboard.Health.FAILING -> Verdict(Light.RED, "failed on the last scan")
                null -> Verdict(Light.UNKNOWN, "not checked yet")
                else -> Verdict(Light.UNKNOWN, "not checked yet (last scan: ${lastScan.label.substringAfter(' ')})")
            }
        }
        // Yellow: up, but something is wrong or unproven.
        val age = now - check.atMs
        if (!check.ok) return Verdict(Light.YELLOW, "last check failed: ${check.message.ifBlank { "no answer" }} (retrying)")
        if (age > STALE_MS) return Verdict(Light.YELLOW, "last check ${age / 60_000} min ago (the engine is not checking)")
        if (refused > 0) return Verdict(Light.YELLOW, "$refused of ${keys.size} key${if (keys.size > 1) "s" else ""} refused; the others work")
        if (check.keys > 1 && check.keysOk < check.keys) return Verdict(Light.YELLOW, "${check.keys - check.keysOk} of ${check.keys} keys failed their check")
        if (check.ms > SLOW_MS) return Verdict(Light.YELLOW, "slow: answered in ${check.ms / 1000} s")
        val allowance = view?.totalAllowance
        val left = view?.totalLeft
        if (allowance != null && left != null && allowance > 0 && left.toDouble() / allowance < LOW_FRACTION) return Verdict(Light.YELLOW, "credits low: $left of $allowance left")
        if (lastScan == CloudDashboard.Health.FAILING) return Verdict(Light.YELLOW, "answers the check but failed on the last scan")
        return Verdict(Light.GREEN, "up · checked ${if (age < 90_000) "${age / 1000} s" else "${age / 60_000} min"} ago · ${check.ms} ms")
    }
}
