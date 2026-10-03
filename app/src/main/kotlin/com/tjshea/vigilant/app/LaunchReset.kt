package com.tjshea.vigilant.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

/**
 * Auto-bet and background auto-scan are switched off by Vigilant itself only once, after the phone restarted (Tj, 2026-10-02 16:05Z: "I want the
 * app never to turn off auto bet unless I turn it off. The default is auto bet off but only when opening the app after a restart or after I already
 * turned off auto bet manually"). Background auto-scan goes with it: auto-bet bets from its cycles ([ScanSettings.autoBetsNow]).
 *
 * Nothing else switches them off: not closing Vigilant (a swipe out of the recent apps, a force stop), an update, a crash, Android ending the process
 * for memory, the mini window closing, or a return from another app. Whatever Tj switched on stays on until he switches it off (it was turned off
 * on every reopening before 2026-10-02 16:05Z, his earlier rule).
 *
 * The restart is handled at boot by the boot receiver (nothing can bet before Tj opens Vigilant) and, for a boot it never heard (Android doesn't
 * tell an app that was force-stopped), by the first screen after it ([LaunchGate.restarted]). The reset is saved before the screen reads the
 * settings, and before the restart is marked handled: a process that dies in between does it again.
 */
object LaunchReset {

    /** [s] with the two background things off; everything else (limits, criteria, the interval, the halt) kept as it was. */
    fun apply(s: ScanSettings): ScanSettings = s.copy(autoBet = false, autoScan = AutoScanMode.OFF, maker = false)

    /** What [apply] switches off in [s], in words for the note on screen; null when both were off already. */
    fun note(s: ScanSettings): String? {
        val bet = s.autoBet
        val scan = s.autoScan != AutoScanMode.OFF
        return when {
            bet && scan -> "Auto-bet and background auto-scan are off after the phone restarted. Switch them on in Settings when you want them."
            bet -> "Auto-bet is off after the phone restarted. Switch it on in Settings when you want it."
            scan -> "Background auto-scan is off after the phone restarted. Switch it on in Settings when you want it."
            else -> null
        }
    }

    /**
     * A phone restart Vigilant hasn't handled yet: saves [apply], keeps the note for the next screen, then marks [boot] handled. True when it
     * handled one. The boot receiver's, and each screen's as it opens ([onOpen]).
     */
    suspend fun afterRestart(app: VigilantApp, boot: Boot): Boolean {
        val gate = app.container.launches
        if (!gate.restarted(boot)) return false
        // Unreadable or unwritten: the restart stays unhandled, so the next look tries again.
        val before = runCatching { app.container.settingsStore.read() }.getOrNull() ?: return false
        note(before)?.let { note ->
            runCatching { app.container.settingsStore.update { apply(it) } }.onFailure { return false }
            gate.keepNote(note)
        }
        gate.handled(boot)
        return true
    }

    /**
     * A screen opening: [afterRestart], then the note it left (once, whether this screen or the boot receiver made it), or null: whatever was on
     * stays on. Blocks for the small files: the settings are read and written here before anything else in the app reads them.
     */
    fun onOpen(app: VigilantApp, boot: Boot): String? = runBlocking {
        runCatching { afterRestart(app, boot) }
        app.container.launches.takeNote()
    }
}

/** Which run of the phone this is: Android's boot count, and when it started by the wall clock (for a phone that doesn't give the count). */
data class Boot(val count: Int?, val atMs: Long) {
    companion object {
        fun now(context: Context): Boot = Boot(
            runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1).takeIf { it >= 0 },
            System.currentTimeMillis() - SystemClock.elapsedRealtime(),
        )
    }
}

/**
 * Whether the phone restarted since Vigilant last looked, the only time [LaunchReset] switches auto-bet off (Tj, 2026-10-02 16:05Z). The boot it
 * handled last is saved in [prefs], so a process ending for any other reason changes nothing.
 */
class LaunchGate(private val prefs: SharedPreferences) {

    /**
     * Whether [boot] is a restart not handled yet. The very first look (a new install, or the update that brought this rule) is not one: it is
     * marked handled, and whatever Tj had on stays on.
     */
    @Synchronized
    fun restarted(boot: Boot): Boolean {
        if (!prefs.contains(AT)) {
            handled(boot)
            return false
        }
        val count = prefs.getInt(COUNT, -1)
        return if (boot.count != null && count >= 0) boot.count != count else abs(boot.atMs - prefs.getLong(AT, 0L)) > BOOT_SLACK_MS
    }

    /** [boot]'s restart is handled: nothing is switched off again until the next one. */
    @Synchronized
    fun handled(boot: Boot) {
        prefs.edit().putInt(COUNT, boot.count ?: -1).putLong(AT, boot.atMs).commit()
    }

    /** The reset's note, for the next screen (the boot receiver has none to show it on). */
    @Synchronized
    fun keepNote(note: String) {
        prefs.edit().putString(NOTE, note).commit()
    }

    /** The kept note, once. */
    @Synchronized
    fun takeNote(): String? = prefs.getString(NOTE, null)?.also { prefs.edit().remove(NOTE).commit() }

    companion object {
        const val PREFS = "launch"
        private const val COUNT = "bootCount"
        private const val AT = "bootAtMs"
        private const val NOTE = "note"

        /** Without a boot count, two looks are the same run of the phone when its start differs by less than this (the wall clock being set). */
        const val BOOT_SLACK_MS = 10 * 60_000L
    }
}
