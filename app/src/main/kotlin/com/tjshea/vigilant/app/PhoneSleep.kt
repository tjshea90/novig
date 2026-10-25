package com.tjshea.vigilant.app

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * The phone as a monitor that sleeps (PB9, Tj 2026-10-25): with the GitHub engine running the system, nothing on the phone needs to run while Tj is not looking. [AppContainer.asleep] turns true
 * [AFTER_MS] after the app leaves the screen; every loop calls [awake] before it reads anything, so a sleeping phone holds no connection, makes no request and wakes the CPU for nothing.
 */
object PhoneSleep {
    /** How long the app stays fully awake after it leaves the screen (a quick switch to another app and back loses nothing). */
    const val AFTER_MS = 2 * 60_000L

    /** Returns at once while awake; while asleep, suspends (no timer, no wake-up) until the app is opened again. */
    suspend fun awake(asleep: StateFlow<Boolean>) {
        if (asleep.value) asleep.first { !it }
    }
}
