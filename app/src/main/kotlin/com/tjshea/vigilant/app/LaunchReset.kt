package com.tjshea.vigilant.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
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

/** How the app's previous process ended, as Android recorded it ([ApplicationExitInfo]). */
data class LastExit(val reason: Int, val atMs: Long, val description: String? = null) {
    companion object {
        /** The newest record, or null when there is none. */
        fun read(context: Context): LastExit? = runCatching {
            context.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(null, 0, 1)?.firstOrNull()
                ?.let { LastExit(it.reason, it.timestamp, it.description) }
        }.getOrNull()
    }
}

/**
 * Whether a screen being made is a fresh launch, the only time [LaunchReset] switches auto-bet off (Tj, 2026-10-02: "If I switch from vigilant to
 * another app then back to vigilant, do not turn off auto bet. Auto bet should only be off by default on a fresh app launch or restart, not just
 * switching apps"). One per process ([AppContainer.launches]).
 *
 * A screen Android restored (saved state) is never one. Another screen in a process that already had one (the mini window closed, then Vigilant
 * opened again) is one only when Tj swiped Vigilant out of the recent apps while a service kept the process ([taskRemoved]). The first screen of a
 * new process is one when the last process ended by Tj's hand (swiped away, force-stopped), an update, a crash or a phone restart, and is not when
 * Android ended it to free memory while he was in another app.
 */
class LaunchGate {
    private var screenSeen = false
    private var swipedAway = false

    /** The screen is the mini window now ([IN_IT]), was closed from it at this time, or null: it's full screen. */
    private var miniWindowAtMs: Long? = null

    /** The picture-in-picture window opened ([inIt]), went back to full screen ([expanded]), or was closed (neither). */
    @Synchronized
    fun miniWindow(inIt: Boolean, expanded: Boolean, now: Long) {
        miniWindowAtMs = when {
            inIt -> IN_IT
            expanded -> null
            else -> now
        }
    }

    /** Vigilant's task left the recent apps (a running service's onTaskRemoved): Tj closing it, unless it was the mini window being closed. */
    @Synchronized
    fun taskRemoved(now: Long) {
        val m = miniWindowAtMs
        if (m == null || (m != IN_IT && now - m > MINI_WINDOW_GRACE_MS)) swipedAway = true
    }

    /** A screen is being made: whether it's a fresh launch. [lastExit]: the previous process's end; [bootAtMs]: when the phone started. */
    @Synchronized
    fun opening(savedState: Boolean, lastExit: LastExit?, bootAtMs: Long): Boolean {
        val fresh = when {
            savedState -> false
            screenSeen -> swipedAway
            else -> lastExit == null || lastExit.atMs < bootAtMs || !androidsOwn(lastExit)
        }
        screenSeen = true
        swipedAway = false
        miniWindowAtMs = null
        return fresh
    }

    companion object {
        private const val IN_IT = Long.MAX_VALUE

        /** Closing the mini window can tell a running service its task went: that close is within this of the window's. */
        const val MINI_WINDOW_GRACE_MS = 10_000L

        /** Android freeing memory or resources while Vigilant was in the background (an update's "installPackage" stop aside). */
        fun androidsOwn(e: LastExit): Boolean = when (e.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_SIGNALED, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, REASON_FREEZER -> true
            ApplicationExitInfo.REASON_OTHER -> e.description?.contains("install", ignoreCase = true) != true
            else -> false
        }

        /** [ApplicationExitInfo.REASON_FREEZER] (Android 14). */
        private const val REASON_FREEZER = 14
    }
}
