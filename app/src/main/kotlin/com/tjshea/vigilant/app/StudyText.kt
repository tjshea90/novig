package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.data.study.ScanStudy
import java.util.Locale

/** The scan study's words in Settings › Diagnostics & about (Tj, 2026-10-03), free of Compose so they're tested. */
object StudyText {

    const val BUTTON = "Share scan study with Claude"

    const val HINT =
        "Scan study: the app logs every bet a CNO or Vigilant scan lists as +EV (odds, type of bet, EV, how many books agree and what share, minutes to the start, and more), " +
            "watches each one while it stays listed, and after its game grades it (won, lost, push) and records its closing line. Share scan study with Claude sends the whole log " +
            "with a note telling Claude to analyze every bet for the patterns that beat the close and profit. It makes no request of its own."

    const val SWITCH_TITLE = "Log every scan for the study"

    const val SWITCH_SUB = "Off: nothing new is logged (what is logged stays on this phone)."

    /** "1,234 bets logged over 6 days · 812 graded · 640 with a closing line · last logged 3m ago · 4.1 MB". */
    fun note(o: ScanStudy.Overview, now: Long): String {
        if (o.bets == 0) return "Nothing logged yet: from now on every bet a scan lists is logged here."
        val last = o.lastLoggedAtMs?.let { " · last logged ${Format.age(it, now)}" }.orEmpty()
        val size = if (o.bytes >= 1_048_576) String.format(Locale.US, "%.1f MB", o.bytes / 1_048_576.0) else "${o.bytes / 1024} KB"
        return "${String.format(Locale.US, "%,d", o.bets)} bets logged over ${o.days} day${if (o.days == 1) "" else "s"} · ${String.format(Locale.US, "%,d", o.graded)} graded · " +
            "${String.format(Locale.US, "%,d", o.closed)} with a closing line$last · $size"
    }
}
