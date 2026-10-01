package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A background cycle that started well after the schedule said ([lateMs]), and what the phone was doing then. */
@Serializable
data class LateCycle(val atMs: Long, val lateMs: Long, val screenOff: Boolean = false, val dozing: Boolean = false)

/** What [CycleLog] keeps (files/cycles.json): counters since [sinceMs], and the late cycles. */
@Serializable
data class CycleBook(
    val sinceMs: Long? = null,
    val cycles: Int = 0,
    /** Cycles that started while the screen was off, and of those the ones while Android's Doze was on. */
    val screenOffCycles: Int = 0,
    val dozeCycles: Int = 0,
    val lastStartMs: Long? = null,
    val lastEndMs: Long? = null,
    /** The interval the last cycle ran at: a switch to another one is not lateness ([CycleLog.apply]). */
    val lastSeconds: Int = 0,
    /** True while a schedule is running; false after a deliberate stop (Stop, auto-scan off), so the hours between aren't "late". */
    val open: Boolean = false,
    val lateCount: Int = 0,
    val worst: LateCycle? = null,
    /** The newest [CycleLog.KEEP_LATE], oldest first. */
    val late: List<LateCycle> = emptyList(),
)

/**
 * Whether background auto-scan really keeps its schedule while the phone sits idle with the screen off (Tj, 2026-10-02): every cycle records when
 * it started against when the schedule said, and whether the screen was off and Android's Doze on. After a night, "N cycles with the screen off, M in
 * Doze, none late" is the answer; a process killed in the night shows as a late cycle at the next start. Kept across restarts (files/cycles.json,
 * written at most every [SAVE_EVERY_MS] unless a cycle was late), so it is the only thing that survives what it measures.
 */
class CycleLog(private val store: JsonFileStore<CycleBook>, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private var book: CycleBook? = null
    private var savedAtMs = 0L

    /** [afterPause]: this cycle is the first since Check odds now held the focus: the wait was on purpose. */
    suspend fun record(startMs: Long, endMs: Long, seconds: Int, screenOff: Boolean, dozing: Boolean, afterPause: Boolean = false) = mutex.withLock {
        val before = book ?: store.read().also { book = it }
        val next = apply(before, startMs, endMs, seconds, screenOff, dozing, afterPause)
        book = next
        if (next.lateCount != before.lateCount || endMs - savedAtMs >= SAVE_EVERY_MS) save(next, endMs)
    }

    /** The schedule was stopped on purpose (Stop, auto-scan off): the next start isn't late for the time between. */
    suspend fun stopped() = mutex.withLock {
        val before = book ?: store.read().also { book = it }
        if (before.open) {
            val next = before.copy(open = false)
            book = next
            save(next, clock())
        }
    }

    suspend fun summary(): CycleBook = mutex.withLock { book ?: store.read().also { book = it } }

    private suspend fun save(next: CycleBook, now: Long) {
        savedAtMs = now
        store.update { next }
    }

    companion object {
        const val KEEP_LATE = 12
        const val SAVE_EVERY_MS = 30_000L

        /** A record this old starts again, so the numbers stay about recent behaviour. */
        const val WINDOW_MS = 7 * 24 * 60 * 60_000L

        /** A cycle that starts this much after its time (or one interval, if more) is late: alarms and Android's own timing wander by seconds. */
        const val LATE_MIN_MS = 30_000L

        fun lateAfterMs(seconds: Int): Long = maxOf(LATE_MIN_MS, seconds.coerceAtLeast(1) * 1_000L)

        /** [book] with this cycle counted. Pure. */
        fun apply(book: CycleBook, startMs: Long, endMs: Long, seconds: Int, screenOff: Boolean, dozing: Boolean, afterPause: Boolean = false): CycleBook {
            val fresh = book.sinceMs == null || startMs - book.sinceMs > WINDOW_MS
            val base = if (fresh) CycleBook(sinceMs = startMs, lastStartMs = book.lastStartMs, lastEndMs = book.lastEndMs, lastSeconds = book.lastSeconds, open = book.open) else book
            // The time the schedule gave it: an interval after the last start, or the last end if that cycle ran longer. The longer of the two
            // intervals when Tj changed it between them, so that switching from 10 minutes to 5 seconds isn't lateness.
            val scheduled = if (base.open && base.lastStartMs != null && !afterPause) {
                maxOf(base.lastStartMs + maxOf(base.lastSeconds, seconds).coerceAtLeast(1) * 1_000L, base.lastEndMs ?: 0L)
            } else null
            val lateMs = scheduled?.let { startMs - it } ?: 0L
            val isLate = lateMs > lateAfterMs(seconds)
            val event = LateCycle(startMs, lateMs, screenOff, dozing)
            return base.copy(
                cycles = base.cycles + 1,
                screenOffCycles = base.screenOffCycles + if (screenOff) 1 else 0,
                dozeCycles = base.dozeCycles + if (dozing) 1 else 0,
                lastStartMs = startMs,
                lastEndMs = endMs,
                lastSeconds = seconds,
                open = true,
                lateCount = base.lateCount + if (isLate) 1 else 0,
                worst = if (isLate && lateMs > (base.worst?.lateMs ?: 0L)) event else base.worst,
                late = if (isLate) (base.late + event).takeLast(KEEP_LATE) else base.late,
            )
        }

        /** Late cycles in the last [withinMs] before [now], from the kept list. */
        fun lateWithin(book: CycleBook, now: Long, withinMs: Long): List<LateCycle> = book.late.filter { now - it.atMs <= withinMs }

        /** "4 min", "45 sec", "2 h 10 min". */
        fun span(ms: Long): String {
            val s = (ms / 1_000).coerceAtLeast(0)
            return when {
                s < 90 -> "$s sec"
                s < 90 * 60 -> "${Math.round(s / 60.0)} min"
                else -> "${s / 3_600} h ${(s % 3_600) / 60} min"
            }
        }

        /** The Diagnostics line. */
        fun line(book: CycleBook, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
            val since = book.sinceMs ?: return "Cycle record: none yet (it starts with the next background cycle)"
            val clock = SimpleDateFormat("MMM d, h:mm a", Locale.US).apply { timeZone = zone }
            val late = if (book.lateCount == 0) "none late" else {
                val w = book.worst
                "${book.lateCount} late (more than ${span(lateAfterMs(book.lastSeconds))} after schedule)" +
                    (w?.let { ", worst ${span(it.lateMs)} at ${clock.format(Date(it.atMs))}" + (if (it.dozing) " in Doze" else if (it.screenOff) " with the screen off" else "") } ?: "")
            }
            return "Cycle record since ${clock.format(Date(since))}: ${"%,d".format(Locale.US, book.cycles)} cycles, ${"%,d".format(Locale.US, book.screenOffCycles)} started with the screen off " +
                "(${"%,d".format(Locale.US, book.dozeCycles)} of those in Doze) · $late"
        }
    }
}
