package com.tjshea.vigilant.app

import android.app.Application
import android.content.SharedPreferences
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The kill switch's second copy (Tj, 2026-10-05: "a stop button kill switch … If I press this, everything remains off, even if I close the app and open it
 * again, until I press resume"). The switch is saved in the settings ([ScanSettings.killed]); this is a one-key preferences file beside it, written
 * synchronously the moment the button is pressed, so a settings file that is damaged, replaced by a restored backup or reset to its defaults cannot start
 * the app running again by accident. [AppContainer] reads it as the settings' default and puts it back into a settings file that lost it.
 */
class KillMarker(private val prefs: SharedPreferences) {
    /** The switch is on. */
    val on: Boolean get() = prefs.getBoolean(KEY_ON, false)

    /** When it was pressed (epoch ms); null when it is off or the time wasn't kept. */
    val atMs: Long? get() = prefs.getLong(KEY_AT, 0L).takeIf { on && it > 0L }

    /** Writes it to the disk before returning (not `apply`, which is queued): the app may be gone a moment later. */
    fun set(on: Boolean, atMs: Long? = null) {
        prefs.edit().putBoolean(KEY_ON, on).putLong(KEY_AT, if (on) atMs ?: 0L else 0L).commit()
    }

    companion object {
        const val PREFS = "vigilant_kill_switch"
        private const val KEY_ON = "on"
        private const val KEY_AT = "at"
    }
}

/**
 * The kill switch (Tj, 2026-10-05): [engage] stops everything that scans, bets or bids, and keeps it stopped (saved, twice) until [release]. Pressed from
 * the red bar on every tab, the floating widget or the notification, so it works with no screen: it needs only the [AppContainer].
 *
 * What it stops, in this order (the first is the one that matters, the rest make it immediate):
 *  1. **The switch is saved** ([KillMarker], then the settings): from here [ScanSettings.paused] is true everywhere, so no scan starts, CrazyNinjaOdds and
 *     the books aren't read, no background cycle runs, and the auto-bet, auto-lock and auto-make refuse to place anything, even a pass already under way
 *     (its next order asks the saved settings again).
 *  2. The scan under way stops, its notification goes, the background auto-scan's alarm and service stop, and Novig's live feed closes.
 *  3. Every bid resting on Novig comes down (one cancel-all, then each confirmed): a bid is a standing offer of real money, so "stopped" includes taking it back.
 * What it keeps: Tj's switches (auto-bet, auto-make, auto-scan, the interval, every limit), so Resume puts things back exactly as they were. What it does
 * not touch: bets already placed (they're his; the Tracker still grades them) and a bet placed by hand from the Bet sheet.
 */
object KillSwitch {

    /** What pressing it did, for the toast and the timeline. */
    data class Result(
        /** Saved in the settings (and so for good). False: the settings couldn't be written; the marker holds it and this session stopped anyway. */
        val saved: Boolean,
        /** Bids Novig took down; null when betting isn't set up or the cancel couldn't be sent. */
        val bidsCancelled: Int?,
        /** What went wrong, in words, if anything. */
        val problems: List<String> = emptyList(),
    ) {
        val toast: String
            get() = buildString {
                append("Everything is stopped")
                if (bidsCancelled != null && bidsCancelled > 0) append("; $bidsCancelled bid${if (bidsCancelled == 1) "" else "s"} taken down")
                append(". It stays off until you tap Resume.")
                if (problems.isNotEmpty()) append(" ").append(problems.joinToString(" "))
            }
    }

    /**
     * Presses it. Idempotent: pressed again, it stops whatever is still running and keeps the first time. Never throws; runs to its end even if the screen
     * that pressed it goes away.
     */
    suspend fun engage(app: Application, c: AppContainer, by: String, now: Long = System.currentTimeMillis()): Result = withContext(NonCancellable) {
        val problems = ArrayList<String>()
        // The bids up before the switch is saved: the app's own pause watcher may take them down the moment it is, so the count is what went, by either hand.
        val upBefore = runCatching { c.makerDesk()?.bids()?.count { it.active } }.getOrNull()
        // 1. Saved first, in two places.
        val at = c.killMarker.atMs ?: now
        runCatching { c.killMarker.set(true, at) }.onFailure { problems += "(The stop couldn't be saved on the phone's preferences: ${it.message ?: it.javaClass.simpleName}.)" }
        val saved = runCatching { c.settingsStore.update { if (it.killed) it else it.copy(killed = true, killedAtMs = at) } }.isSuccess
        if (!saved) problems += "(The settings file couldn't be written; the stop is held by a second copy and stays on.)"
        runCatching { c.eventLog.info("KILL", "kill switch ON ($by)") }
        // 2. What runs now.
        runCatching { c.runner.stop() }
        runCatching { ScanService.cancelDone(app) }
        runCatching { AutoScanService.stop(app) }
        runCatching { c.novig.stream?.close() }
        // 3. Every bid down.
        val cancelled = try {
            c.maker.cancelAll(CANCEL_WHY)?.let { n -> maxOf(n, (upBefore ?: 0) - (c.makerDesk()?.bids()?.count { it.active } ?: 0)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val text = "Bids couldn't be taken down just now (${e.message ?: e.javaClass.simpleName}); they come down when it works, and Novig ends each within its expiry."
            problems += text
            runCatching { c.problems.add("Kill switch", text) }
            null
        }
        Result(saved, cancelled, problems)
    }

    /** Tj's Resume: the switch is off in both copies and everything he had switched on runs again (the background scan restarts at once). */
    suspend fun release(app: Application, c: AppContainer, by: String): ScanSettings = withContext(NonCancellable) {
        runCatching { c.killMarker.set(false) }
        val next = c.settingsStore.update { it.copy(killed = false, killedAtMs = null) }
        runCatching { c.eventLog.info("KILL", "kill switch OFF ($by)") }
        if (next.activeAutoScan != com.tjshea.vigilant.data.scanner.AutoScanMode.OFF && !AutoScanService.running) runCatching { AutoScanService.start(app) }
        next
    }

    /** The reason on every bid the switch takes down. */
    const val CANCEL_WHY = "Stopped by the kill switch"

    /**
     * [s] with the kill switch put back from [marker] when the settings lost it (a damaged or replaced file reads as the defaults); [s] itself when they agree.
     * The marker never turns the switch off: only [release] does.
     */
    fun reconcile(s: ScanSettings, marker: KillMarker): ScanSettings =
        if (marker.on && !s.killed) s.copy(killed = true, killedAtMs = marker.atMs ?: s.killedAtMs) else s
}
