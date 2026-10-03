package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoWideState
import com.tjshea.vigilant.data.study.ScanStudy
import java.util.Locale

/** The scan study's words in Settings › Diagnostics & about (Tj, 2026-10-03), free of Compose so they're tested. */
object StudyText {

    const val BUTTON = "Share scan study with Claude"

    const val HINT =
        "Scan study: the app logs every bet a CNO or Vigilant scan lists as +EV (odds, type of bet, EV, how many books agree and what share, minutes to the start, and more), " +
            "watches each one while it stays listed, and after its game grades it (won, lost, push) and records its closing line. Share scan study with Claude sends the whole log " +
            "with a note telling Claude to analyze every bet for the patterns that beat the close and profit. The log also holds the bets your CNO filters hide from the app " +
            "(still hidden there): see the switch below."

    const val SWITCH_TITLE = "Log every scan for the study"

    const val SWITCH_SUB = "Off: nothing new is logged (what is logged stays on this phone)."

    const val HIDDEN_TITLE = "Also log what your CNO filters hide"

    const val HIDDEN_SUB =
        "Reads CNO's list a second time, at most every 30 s while the CNO list is being read, with its filters opened up (any EV, odds, number of books), " +
            "and logs every row for the study, marked hidden and why. The app's list, alerts, auto-bet and widget don't change. Off: only the rows the app lists are logged."

    /**
     * The wide read in a line, for Diagnostics and the study file's header: what it read last (and how many of those rows the app's own list [appList] had), what CNO was
     * asked, and how the reads are going. Words only: nothing here changes what the app shows.
     */
    fun wideNote(w: CnoWideState, appList: CnoSnapshot?, on: Boolean, now: Long): String {
        if (!on) return "off (Settings › Diagnostics & about › Also log what your CNO filters hide)"
        val problem = w.error?.let { " · last problem: $it (${w.errors} in a row; asking for up to ${w.rowsAsked} rows)" }.orEmpty()
        val snap = w.snapshot ?: return "no read yet since the app opened$problem"
        val keys = appList?.takeIf { it.url == snap.url }?.rows?.mapTo(HashSet()) { it.key }
        val shared = keys?.let { k -> snap.rows.count { it.key in k } }
        val versus = if (shared != null) " (${shared} also in the app's list of ${keys.size}; ${snap.rows.size - shared} only the wide read found)" else ""
        val full = if (snap.limit != null && snap.rows.size >= snap.limit) " — AS MANY AS ASKED FOR, so CNO may have more" else ""
        return "${snap.rows.size} rows read ${Format.age(snap.fetchedAtMs, now)}$versus, asked for up to ${snap.limit ?: "?"}$full · ${w.reads} good reads since the app opened · " +
            "form as posted: ${snap.asked ?: "?"}$problem"
    }

    /** "1,234 bets logged over 6 days · 812 graded · 640 with a closing line · last logged 3m ago · 4.1 MB". */
    fun note(o: ScanStudy.Overview, now: Long): String {
        if (o.bets == 0) return "Nothing logged yet: from now on every bet a scan lists is logged here."
        val last = o.lastLoggedAtMs?.let { " · last logged ${Format.age(it, now)}" }.orEmpty()
        val size = if (o.bytes >= 1_048_576) String.format(Locale.US, "%.1f MB", o.bytes / 1_048_576.0) else "${o.bytes / 1024} KB"
        return "${String.format(Locale.US, "%,d", o.bets)} bets logged over ${o.days} day${if (o.days == 1) "" else "s"} · ${String.format(Locale.US, "%,d", o.graded)} graded · " +
            "${String.format(Locale.US, "%,d", o.closed)} with a closing line$last · $size"
    }
}
