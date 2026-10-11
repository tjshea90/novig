package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.pinnodds.LiveScan
import com.tjshea.vigilant.data.pinnodds.LiveScanStatus

/** The words of the Feed's real-time live lane (Settings › Include live games; Tj, 2026-10-11). */
object LiveScanText {
    const val SWITCH_SUB = "Live games are matched the moment they start and kept on Novig's and Pinnacle's live sockets (Pinnodds key, or the Pinnacle website feed, and a Novig key). " +
        "Their EV is worked out in real time from Pinnacle's live fair price against Novig's live price, and a bet stays on the list only while both feeds spoke about its game within the last 5 seconds. " +
        "Off: live games stay off the Feed."

    /** One line for the Feed and the notification. */
    fun line(s: LiveScanStatus): String = when {
        !s.on && s.reason != null -> "Live: ${s.reason}"
        !s.on -> "Live: starting"
        s.reason != null -> "Live: nothing shown, ${s.reason}"
        else -> "Live: ${s.games} game${if (s.games == 1) "" else "s"} linked · ${s.watched} markets watched · ${s.books} Novig books held · ${s.shown} +EV bet${if (s.shown == 1) "" else "s"} now" +
            (s.oldestMs?.let { " (feeds ${"%.0f".format(java.util.Locale.US, it / 1000.0)} s since last word)" } ?: "") +
            (if (s.dropped > 0) " · ${s.dropped} dropped as stale" else "")
    }
}
