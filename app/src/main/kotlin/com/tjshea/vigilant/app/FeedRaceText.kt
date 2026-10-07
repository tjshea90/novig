package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.live.FeedRaceStatus
import java.util.Locale

/** The words of the live feed test (Settings › Diagnostics & about; Tj, 2026-10-07; RESEARCH.md §106). */
object FeedRaceText {
    const val SWITCH_TITLE = "Test live score and odds feeds"
    const val SWITCH_SUB = "While a game is live on Novig, reads the free feeds of it (Sofascore, Polymarket's score and odds sockets, ESPN, NHL, MLB) and Novig's own trades, and says which shows a score before Novig's price moves. Reads only: no order, ever."
    const val HINT = "A test for live betting: it needs live games, a day or two of them, to say anything. Settings › Diagnostics has its verdict; the button shares the whole tape with Claude."
    const val BUTTON = "Share live feed test with Claude"

    /** The one line under the switch: what it is doing, and its verdict once it has one. */
    fun note(status: FeedRaceStatus, now: Long): String {
        if (!status.running && status.sinceMs == null && status.readings == 0L) return "Not running."
        val head = if (status.running) {
            "Running" + (status.sinceMs?.let { " for ${duration(now - it)}" } ?: "") + ": ${status.liveGames} live game${if (status.liveGames == 1) "" else "s"} on Novig" +
                (if (status.sports.isEmpty()) "" else " (${status.sports.joinToString(", ")})") + ", ${status.readings} score readings, ${status.novigTrades} Novig trades, ${status.oddsTicks} odds ticks, ${status.requests} requests."
        } else {
            "Stopped: ${status.readings} score readings, ${status.novigTrades} Novig trades this run."
        }
        return head + (status.problem?.let { " Problem: $it." } ?: "") + (status.verdict?.let { " $it" } ?: "")
    }

    private fun duration(ms: Long): String {
        val m = (ms / 60_000L).coerceAtLeast(0)
        return if (m < 60) "$m min" else String.format(Locale.US, "%d h %02d min", m / 60, m % 60)
    }
}
