package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyState
import com.tjshea.vigilant.data.keys.ProviderView

/**
 * The light on an API card (Tj, 2026-10-20, CH48): "green (up and active), yellow (slow or problematic or refused connections recently), red (no connection or no credits left)", grey when the API is off.
 * Decided from what is fresh: the engine's own live check of the API ([ApiCheckLine], every few minutes), the usage ledger (credits, refused and spent keys), the scans (did they read data from it lately) and the
 * phone's own test (Test now, or the pull to refresh) and, last, how the API did on the last scan. A green light needs a check that passed, recently, with no key refused and credits to spend; the last scan's
 * counts alone never make it green (a Pinnacle board answered by PinnWire lit pinnapi's card).
 *
 * XO1 (Tj, 2026-10-21: "some of the apis claim to be failing, but when I test the keys they are green and accepted"): a check that FAILS is no longer the last word. The engine's check of ParlayAPI's ten keys timed
 * out (it made a hundred reads) while the scans read the API without a hitch; the light said "no connection". Now a failing check is yellow, with both facts in words, when the scans read the API lately or the phone's
 * own fresh test is accepted; it is red only when nothing else works either. The API manager's own word ([ApiCheckLine.fix]: restarted, a fresh runner asked for, switched off) is part of the line.
 */
object ApiState {
    enum class Light { OFF, GREEN, YELLOW, RED, UNKNOWN }

    data class Verdict(val light: Light, /** One short line: why, in words. */ val words: String)

    /** The phone's own test of the API's keys (the same free account read as the engine's, from this phone's address and with this phone's keys), and when. */
    data class PhoneCheck(val atMs: Long, val ok: Boolean, val keysOk: Int, val keys: Int, val message: String)

    /** A check older than this says nothing now (the engine checks every 5 minutes; a few misses are tolerated). */
    const val STALE_MS = 15 * 60_000L

    /** A phone test older than this is not fresh evidence. */
    const val PHONE_FRESH_MS = 10 * 60_000L

    /** A check that took longer than this is slow. */
    const val SLOW_MS = 5_000L

    /** A scan that read data from the API this recently says the API itself works. */
    const val SCAN_FINE_MS = ApiManager.SCAN_FINE_MS

    /** Credits under this share of the allowance are low. */
    const val LOW_FRACTION = 0.10

    private fun since(ms: Long) = if (ms < 90_000L) "${ms / 1000} s" else if (ms < 7_200_000L) "${ms / 60_000L} min" else "${ms / 3_600_000L} h"

    fun of(check: ApiCheckLine?, view: ProviderView?, lastScan: CloudDashboard.Health?, now: Long, phone: PhoneCheck? = null, engineRunning: Boolean = true): Verdict {
        if (check != null && !check.on) return Verdict(Light.OFF, check.fix.ifBlank { if (!check.hasKey) "no key saved: never called" else "switched off: never called" })
        val keys = view?.keys.orEmpty()
        val refused = keys.count { it.state == KeyState.REFUSED }
        val allRefused = keys.isNotEmpty() && refused >= keys.size
        // Red: nothing can be asked of it. Credits used up, or every key refused by the provider (the ledger knows from real calls and checks).
        if (view != null && view.spent) return Verdict(Light.RED, "no credits left" + (keys.mapNotNull { it.until }.minOrNull()?.let { " · back ${com.tjshea.vigilant.data.keys.UsageMeter.whenText(it, now)}" } ?: ""))
        if (allRefused) return Verdict(Light.RED, "every key refused by the provider")
        val engineFresh = check != null && check.atMs > 0L && engineRunning && now - check.atMs <= STALE_MS
        val phoneFresh = phone != null && now - phone.atMs <= PHONE_FRESH_MS
        val fix = check?.fix?.takeIf { it.isNotBlank() }?.let { " · engine: $it" }.orEmpty()
        if (!engineFresh) {
            // The engine is not checking (outside its hours, or a stale check): this phone's own fresh test is the evidence there is.
            if (phoneFresh && phone != null) {
                val age = since(now - phone.atMs)
                return if (phone.ok) {
                    if (phone.keys > 1 && phone.keysOk < phone.keys) Verdict(Light.YELLOW, "${phone.keys - phone.keysOk} of ${phone.keys} keys failed this phone's test $age ago")
                    else Verdict(Light.GREEN, "up · tested from this phone $age ago" + if (engineRunning) " (the engine's check is old)" else " (the engine is not running)")
                } else Verdict(Light.RED, "this phone's test $age ago failed: ${phone.message.take(80)}")
            }
            if (check == null || check.atMs <= 0L) {
                // Not checked yet by the engine (an older engine build, or just started): say so rather than guess green from the last scan.
                return when (lastScan) {
                    CloudDashboard.Health.FAILING -> Verdict(Light.RED, "failed on the last scan")
                    null -> Verdict(Light.UNKNOWN, "not checked yet")
                    else -> Verdict(Light.UNKNOWN, "not checked yet (last scan: ${lastScan.label.substringAfter(' ')})")
                }
            }
            val age = since(now - check.atMs)
            return if (!engineRunning) Verdict(Light.UNKNOWN, "engine not running · last check $age ago was ${if (check.ok) "fine" else "failing"}")
            else Verdict(Light.YELLOW, "last check $age ago (the engine is not checking)")
        }
        val c = check!!
        if (!c.ok) {
            val msg = c.message.ifBlank { "no answer" }
            val scansFine = c.scanOkAtMs > 0L && now - c.scanOkAtMs <= SCAN_FINE_MS
            return when {
                scansFine -> Verdict(Light.YELLOW, "its health check fails ($msg) but the scans are reading it fine$fix")
                phoneFresh && phone != null && phone.ok -> Verdict(Light.YELLOW, "the engine cannot use it ($msg) but this phone's test is accepted: the engine's key or runner address differs$fix")
                c.failStreak >= 2 -> Verdict(Light.RED, "no connection: $msg (retrying)$fix")
                else -> Verdict(Light.YELLOW, "last check failed: $msg (retrying)$fix")
            }
        }
        // Yellow: up, but something is wrong or unproven.
        val age = now - c.atMs
        if (refused > 0) return Verdict(Light.YELLOW, "$refused of ${keys.size} key${if (keys.size > 1) "s" else ""} refused; the others work")
        if (c.keys > 1 && c.keysOk < c.keys) return Verdict(Light.YELLOW, "${c.keys - c.keysOk} of ${c.keys} keys failed their check")
        if (c.ms > SLOW_MS) return Verdict(Light.YELLOW, "slow: answered in ${c.ms / 1000} s")
        val allowance = view?.totalAllowance
        val left = view?.totalLeft
        if (allowance != null && left != null && allowance > 0 && left.toDouble() / allowance < LOW_FRACTION) return Verdict(Light.YELLOW, "credits low: $left of $allowance left")
        if (lastScan == CloudDashboard.Health.FAILING) return Verdict(Light.YELLOW, "answers the check but failed on the last scan")
        return Verdict(Light.GREEN, "up · checked ${since(age)} ago · ${c.ms} ms")
    }
}
