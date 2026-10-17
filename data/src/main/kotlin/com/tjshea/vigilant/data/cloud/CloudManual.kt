package com.tjshea.vigilant.data.cloud

import java.time.Instant

/**
 * Starting and stopping the engine by hand without touching the schedule (Tj, 2026-10-17). The schedule is [EngineHours] (08:00 to 01:00 Eastern, the 15-minute gate, the 6-hour hand-over). Two timestamps in
 * [CloudConfig] bend it for a while and then lapse by themselves, so the schedule is always what comes back:
 *  - RUN ([CloudConfig.manualRunUntilMs]): the engine counts as inside its hours until that time (at most [RUN_MAX_MS]), so a start outside 08:00-01:00 works and the gate lets it through.
 *  - STOP ([CloudConfig.manualStopUntilMs]): the engine counts as switched off until that time, which is the next scheduled opening ([stopUntil]); the gate starts nothing and a running job ends, taking its own orders down.
 * A Run clears a Stop and a Stop clears a Run. Pure.
 */
object CloudManual {
    const val RUN_MAX_MS = 12 * 3_600_000L

    fun runActive(c: CloudConfig, now: Long) = now < c.manualRunUntilMs
    fun stopActive(c: CloudConfig, now: Long) = now < c.manualStopUntilMs

    /** Whether the engine may work at [now]: its hours (when they are on) or a manual run. A manual stop is separate ([stopActive]). */
    fun open(hoursOn: Boolean, c: CloudConfig?, now: Long): Boolean =
        !hoursOn || EngineHours.isOpen(now) || (c != null && runActive(c, now))

    /**
     * The next scheduled opening after the window that contains [now] (or after now, when it is closed): tomorrow 08:00 Eastern from 08:00 until midnight, today's 08:00 from midnight on. A Stop, and an engine
     * switched off by hand, last until then and no longer.
     */
    fun stopUntil(now: Long): Long {
        if (!EngineHours.isOpen(now)) return EngineHours.nextOpenAtMs(now)
        val close = EngineHours.closesAtMs(now) ?: return EngineHours.nextOpenAtMs(now)
        return EngineHours.nextOpenAtMs(close + 1L)
    }

    /**
     * When an engine that Tj switched off resumes by itself (Tj, 2026-10-17, "I'm turning the GitHub engine off. make sure it resumes as scheduled without me"): the next scheduled opening after the moment he wrote
     * the off. Never after the kill switch ("STOP ALL", [com.tjshea.vigilant.data.scanner.ScanSettings.killed]) and never after the token was removed; null = it stays off until he switches it on.
     */
    fun resumeAtMs(c: CloudConfig): Long? =
        if (c.on || c.rev < MIN_REAL_REV || c.device == "token removed" || c.settings.killed) null else stopUntil(c.rev)

    /** A config's [CloudConfig.rev] is the phone's clock in epoch milliseconds; anything below 2017 (a test's counter, a file that never said) is not a time, so nothing resumes from it. */
    const val MIN_REAL_REV = 1_500_000_000_000L

    /** [c] as the engine and the gate read it: an off that has reached its scheduled resume counts as on (with the cloud scan switched on in its copy of the settings). */
    fun effective(c: CloudConfig, now: Long): CloudConfig {
        val at = resumeAtMs(c) ?: return c
        return if (now >= at) c.copy(on = true, settings = c.settings.copy(gates = c.settings.gates.copy(cloudScan = true), pausedByHand = false)) else c
    }

    /** The config's manual fields for a Run at [now]: a Stop is cleared; the run lasts until the window opens by itself, or [RUN_MAX_MS] at most. */
    fun runFields(now: Long): Pair<Long, Long> = (if (EngineHours.isOpen(now)) 0L else minOf(now + RUN_MAX_MS, EngineHours.nextOpenAtMs(now) + 60_000L)) to 0L

    /** The config's manual fields for a Stop at [now]: a Run is cleared; inside the hours the engine stays off until the next day's opening. */
    fun stopFields(now: Long): Pair<Long, Long> = 0L to stopUntil(now)

    /** One line for the phone: what the manual state is, in words. */
    fun words(c: CloudConfig?, now: Long): String? = when {
        c == null -> null
        stopActive(c, now) -> "Stopped by you until " + at(c.manualStopUntilMs) + " (the schedule resumes then)."
        runActive(c, now) -> "Running by your request outside its hours until " + at(c.manualRunUntilMs) + " (the schedule resumes after)."
        else -> null
    }

    private fun at(ms: Long): String = Instant.ofEpochMilli(ms).atZone(EngineHours.ZONE).let { "%d:%02d %s ET".format((it.hour % 12).let { h -> if (h == 0) 12 else h }, it.minute, if (it.hour < 12) "am" else "pm") }
}
