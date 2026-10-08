package com.tjshea.vigilant.data.pinnodds

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.max

/**
 * What Novig's moneyline looks like in the seconds after a score (RESEARCH.md §118; Tj, 2026-10-08: "There is a live bet pause after a score on novig"). No order is sent: at the score and
 * at fixed offsets after it, the runner writes one [ReopenProbe] for each matched moneyline, holding Pinnacle's fair for each side and Novig's cheapest ask for it. The report answers
 * what the tapes could not: how long Novig's ask stays where it was, and how big the gap to Pinnacle's fair is while it does (the room a first quote after the pause would have).
 */
@Serializable
data class ReopenProbe(
    val atMs: Long,
    val scoreAtMs: Long,
    val offsetSec: Int,
    val eventId: String,
    val league: String,
    val marketId: String,
    val fairHome: Double? = null,
    val fairAway: Double? = null,
    val askHome: Double? = null,
    val askAway: Double? = null,
    val lineOpen: Boolean = false,
) {
    /** The biggest gap (probability) between Pinnacle's fair for a side and Novig's ask for it; null with no side priced on both. */
    val gap: Double? get() = listOfNotNull(
        if (fairHome != null && askHome != null) fairHome - askHome else null,
        if (fairAway != null && askAway != null) fairAway - askAway else null,
    ).maxOrNull()
}

object ReopenStudy {
    /** Seconds after a score at which the books are read. */
    val OFFSETS = listOf(0, 1, 2, 3, 5, 8, 12, 20, 30)

    private fun median(xs: List<Double>): Double? = xs.sorted().let { if (it.isEmpty()) null else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    /** The Diagnostics lines for [rows]: by offset, how many markets were read, how big the best gap was, and how many asks had moved off their value at the score. */
    fun lines(rows: List<ReopenProbe>): List<String> {
        val scored = rows.groupBy { it.marketId to it.scoreAtMs }
        if (scored.isEmpty()) return listOf("No score has been probed yet: after each score on a matched game the moneyline is read at ${OFFSETS.joinToString(", ") { "+${it}s" }}.")
        val out = ArrayList<String>()
        out += "After a score (${scored.size} moneyline readings of ${scored.values.map { it.first().eventId }.toSet().size} games; Pinnacle's fair against Novig's cheapest ask, no order sent):"
        for (off in OFFSETS) {
            val at = rows.filter { it.offsetSec == off }
            if (at.isEmpty()) continue
            val gaps = at.mapNotNull { it.gap }
            // An ask counts as moved when it differs from the same market's ask at the score (or at the first probe we have for it).
            var comparable = 0
            var moved = 0
            for ((_, g) in scored) {
                val base = g.minByOrNull { it.offsetSec } ?: continue
                val now = g.firstOrNull { it.offsetSec == off } ?: continue
                if (now === base) continue
                val a0 = listOfNotNull(base.askHome, base.askAway)
                val a1 = listOfNotNull(now.askHome, now.askAway)
                if (a0.isEmpty() || a1.isEmpty()) continue
                comparable++
                if (max(kotlin.math.abs((base.askHome ?: 0.0) - (now.askHome ?: 0.0)), kotlin.math.abs((base.askAway ?: 0.0) - (now.askAway ?: 0.0))) >= 0.004) moved++
            }
            out += String.format(
                Locale.US, "  +%2d s: %d read · best gap median %s, ≥3 points in %s%s", off, at.size,
                median(gaps)?.let { String.format(Locale.US, "%+.1f pts", it * 100) } ?: "n/a",
                if (gaps.isEmpty()) "n/a" else String.format(Locale.US, "%.0f%%", gaps.count { it >= 0.03 } * 100.0 / gaps.size),
                if (comparable > 0 && off > 0) String.format(Locale.US, " · Novig's ask had moved in %.0f%% (n=%d)", moved * 100.0 / comparable, comparable) else "",
            )
        }
        return out
    }
}
