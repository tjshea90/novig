package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanProgress

/** The words on the scan notifications, kept apart from Android so they can be unit tested. */
object ScanText {

    /** "Novig prices 45/300 · 3 +EV so far". */
    fun progress(p: ScanProgress?, found: Int): String = buildString {
        when {
            p == null -> append("Starting…")
            p.total > 0 -> append("${p.step} ${p.done}/${p.total}")
            else -> append(p.step)
        }
        if (found > 0) append(" · $found +EV so far")
    }

    /** Title and text for the "scan done" notification, from the finished feed. */
    fun done(feed: List<Opportunity>, minEv: Double, errors: List<String>): Pair<String, String> {
        val title = when (feed.size) {
            0 -> "Scan done: no +EV right now"
            1 -> "Scan done: 1 +EV bet"
            else -> "Scan done: ${feed.size} +EV bets"
        }
        val best = feed.maxByOrNull { it.evPercent ?: Double.NEGATIVE_INFINITY }
        val text = buildString {
            if (best?.evPercent != null) {
                append("Best: ${best.selection} · ${best.marketLabel} · +${Format.percent(best.evPercent!!)} EV")
            } else {
                append("Nothing at or above +${Format.percent(minEv)} EV.")
            }
            errors.firstOrNull()?.let { append("\n").append(it) }
        }
        return title to text
    }
}
