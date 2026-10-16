package com.tjshea.vigilant.data.cloud

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The hours the GitHub engine works (Tj, 2026-10-15: "The engine should only run from 8am to 10pm Eastern Time daily. Make sure there is a method to turn it off and back on automatically"):
 * then (Tj, 2026-10-16: "run until 1am eastern every day instead of stopping at 10pm") 08:00 inclusive to 01:00 exclusive the NEXT morning, America/New_York, daylight saving included: the window
 * crosses midnight and is closed only from 01:00 to 08:00. Pure; the workflow's gate step applies the same window in python (`zoneinfo`) and the engine
 * ends itself at [closesAtMs] ([CloudEngine.End.WINDOW]). The phone's switch is `gates.cloudHours` (default on); off = the engine runs round the clock as before.
 */
object EngineHours {
    val ZONE: ZoneId = ZoneId.of("America/New_York")
    val OPEN: LocalTime = LocalTime.of(8, 0)
    val CLOSE: LocalTime = LocalTime.of(1, 0)

    fun isOpen(nowMs: Long): Boolean {
        val t = Instant.ofEpochMilli(nowMs).atZone(ZONE).toLocalTime()
        // open from OPEN through midnight, and again from midnight until CLOSE
        return !t.isBefore(OPEN) || t.isBefore(CLOSE)
    }

    private fun at(day: LocalDate, t: LocalTime): Long = day.atTime(t).atZone(ZONE).toInstant().toEpochMilli()

    /** When the window that contains [nowMs] closes; null when it is closed now. */
    fun closesAtMs(nowMs: Long): Long? =
        if (!isOpen(nowMs)) null else {
            val z = Instant.ofEpochMilli(nowMs).atZone(ZONE)
            // after OPEN the window closes at CLOSE tomorrow morning; after midnight, this morning
            at(if (z.toLocalTime().isBefore(CLOSE)) z.toLocalDate() else z.toLocalDate().plusDays(1), CLOSE)
        }

    /** The next time the window opens (now, when it is open). */
    fun nextOpenAtMs(nowMs: Long): Long {
        if (isOpen(nowMs)) return nowMs
        val z = Instant.ofEpochMilli(nowMs).atZone(ZONE)
        // closed only between CLOSE and OPEN, so the next opening is this morning's
        return at(z.toLocalDate(), OPEN)
    }
}
