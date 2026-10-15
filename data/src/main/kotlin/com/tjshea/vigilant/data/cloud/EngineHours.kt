package com.tjshea.vigilant.data.cloud

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The hours the GitHub engine works (Tj, 2026-10-15: "The engine should only run from 8am to 10pm Eastern Time daily. Make sure there is a method to turn it off and back on automatically"):
 * 08:00 inclusive to 22:00 exclusive, America/New_York, daylight saving included. Pure; the workflow's gate step applies the same window in python (`zoneinfo`) and the engine
 * ends itself at [closesAtMs] ([CloudEngine.End.WINDOW]). The phone's switch is `gates.cloudHours` (default on); off = the engine runs round the clock as before.
 */
object EngineHours {
    val ZONE: ZoneId = ZoneId.of("America/New_York")
    val OPEN: LocalTime = LocalTime.of(8, 0)
    val CLOSE: LocalTime = LocalTime.of(22, 0)

    fun isOpen(nowMs: Long): Boolean {
        val t = Instant.ofEpochMilli(nowMs).atZone(ZONE).toLocalTime()
        return !t.isBefore(OPEN) && t.isBefore(CLOSE)
    }

    private fun at(day: LocalDate, t: LocalTime): Long = day.atTime(t).atZone(ZONE).toInstant().toEpochMilli()

    /** When the window that contains [nowMs] closes; null when it is closed now. */
    fun closesAtMs(nowMs: Long): Long? =
        if (!isOpen(nowMs)) null else at(Instant.ofEpochMilli(nowMs).atZone(ZONE).toLocalDate(), CLOSE)

    /** The next time the window opens (now, when it is open). */
    fun nextOpenAtMs(nowMs: Long): Long {
        if (isOpen(nowMs)) return nowMs
        val z = Instant.ofEpochMilli(nowMs).atZone(ZONE)
        val day = if (z.toLocalTime().isBefore(OPEN)) z.toLocalDate() else z.toLocalDate().plusDays(1)
        return at(day, OPEN)
    }
}
