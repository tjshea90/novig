package com.tjshea.vigilant.data.scanner

/**
 * When background auto-scan keeps the phone's CPU awake between scans, and the safety alarm behind it (Tj, 2026-10-02: "keep it alive
 * robustly to keep auto bet on and scanning even if the phone is idle and the screen is turned off and locked … I still want the screen
 * turned off if possible").
 *
 * Why (RESEARCH.md §59): with the screen off and the phone still, Android enters Doze, and a scan that waits for an alarm is woken only about
 * once every 9 minutes ([ALARM_ONLY_BELOW_SECONDS]) whatever the interval says. A foreground service that holds a partial wake lock keeps the CPU
 * running and, being a foreground service, keeps its network too; the screen stays off. Pure: the service and the tests call it.
 */
object KeepAwake {

    /**
     * Intervals this long or longer don't need the CPU held: Android's own allowance for an exact alarm in Doze is one about every 9 minutes,
     * so an alarm is on time for them and holding the CPU awake for 10 to 40 minutes would only cost battery.
     */
    const val ALARM_ONLY_BELOW_SECONDS = 540

    /** Whether the service holds the CPU awake and drives the cycles itself: the switch is on, background scanning runs, and cycles are closer than Doze lets an alarm be. */
    fun active(s: ScanSettings): Boolean =
        s.autoScanKeepAwake && s.activeAutoScan != AutoScanMode.OFF && s.autoScanSeconds < ALARM_ONLY_BELOW_SECONDS

    /** The safety alarm is never closer than this: a stalled loop is noticed after three intervals, but not under three minutes (a Vigilant scan runs ~2). */
    const val WATCHDOG_MIN_MS = 3 * 60_000L

    /** How long after the last sign of life the safety alarm goes off. */
    fun watchdogDelayMs(seconds: Int): Long = maxOf(3 * seconds.coerceAtLeast(1) * 1_000L, WATCHDOG_MIN_MS)

    /** When to arm it from [now]. */
    fun watchdogAtMs(now: Long, seconds: Int): Long = now + watchdogDelayMs(seconds)

    /**
     * Moving the armed alarm costs a call to Android, so a loop that cycles every 5 seconds doesn't make it every time: only when what is armed
     * has lost a third of its delay ([armedAtMs] = what was last armed for), or none is.
     */
    fun rearmDue(armedAtMs: Long?, now: Long, seconds: Int): Boolean =
        armedAtMs == null || armedAtMs - now < watchdogDelayMs(seconds) * 2 / 3

    /** The wake lock is held with a timeout, so a service that dies can't leave it held for ever; the loop renews it before it runs out. */
    const val LOCK_TIMEOUT_MS = 15 * 60_000L

    /** Renewed when this little of it is left. */
    const val LOCK_RENEW_BEFORE_MS = 5 * 60_000L

    fun lockRenewDue(heldUntilMs: Long?, now: Long): Boolean = heldUntilMs == null || heldUntilMs - now < LOCK_RENEW_BEFORE_MS
}
