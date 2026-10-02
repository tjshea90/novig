package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking

/**
 * Auto-bet and background auto-scan start OFF every time Vigilant is closed and opened again (Tj, 2026-10-02: "anytime I close the app and
 * reopen it, auto bet and background scan is turned off by default. Nothing should auto bet or background scan unless I specifically set it in
 * the settings"). Whatever he switched on last time is not carried into this one: he switches it on in Settings when he wants it.
 *
 * "Opened again" is a fresh launch of the activity (no saved state: [MainActivity.onCreate]'s `savedInstanceState == null`): the app opened from
 * the launcher, from a notification or from Recents after it was closed. Not a rotation, not a return from Home or from another app (the activity
 * is still there), not Android restoring the app after it ended the process for memory (that comes with saved state), and not a reboot (the boot
 * receiver restarts what was on, until the app is opened).
 *
 * The reset is saved at once, before the screen reads the settings (the service stops on the saved setting, so a background cycle can't bet after it).
 */
object LaunchReset {

    /** [s] with the two background things off; everything else (limits, criteria, the interval, the halt) kept as it was. */
    fun apply(s: ScanSettings): ScanSettings = s.copy(autoBet = false, autoScan = AutoScanMode.OFF)

    /** What [apply] switches off in [s], in words for the note on screen; null when both were off already. */
    fun note(s: ScanSettings): String? {
        val bet = s.autoBet
        val scan = s.autoScan != AutoScanMode.OFF
        return when {
            bet && scan -> "Auto-bet and background auto-scan are off again after reopening Vigilant. Switch them on in Settings when you want them."
            bet -> "Auto-bet is off again after reopening Vigilant. Switch it on in Settings when you want it."
            scan -> "Background auto-scan is off again after reopening Vigilant. Switch it on in Settings when you want it."
            else -> null
        }
    }

    /**
     * The fresh launch: saves [apply] and returns the note (null when nothing was on). Blocks for the one small file: the settings are read and
     * written here before anything else in the app reads them.
     */
    fun onFreshLaunch(app: VigilantApp): String? = runBlocking {
        val before = runCatching { app.container.settingsStore.read() }.getOrNull() ?: return@runBlocking null
        val note = note(before) ?: return@runBlocking null
        runCatching { app.container.settingsStore.update { apply(it) } }.onFailure { return@runBlocking null }
        note
    }
}
