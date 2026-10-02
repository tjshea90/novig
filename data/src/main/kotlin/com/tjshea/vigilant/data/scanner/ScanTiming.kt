package com.tjshea.vigilant.data.scanner

import java.util.Locale

/**
 * Where a scan's time went, in milliseconds from its start (Tj, 2026-09-28: "now it is reading the API very slow"):
 * shown under Settings › Novig API, so the next "slow" comes with numbers from the phone itself.
 */
data class ScanTiming(
    /** Novig's board in (read, or re-used from the last few minutes). */
    val boardAtMs: Long = 0,
    /** Every fair-odds source answered. */
    val fairAtMs: Long = 0,
    /** Novig's prices: the first asked for, and the last in (the end-of-scan re-read too). Null = none read. */
    val novigFromMs: Long? = null,
    val novigToMs: Long? = null,
    /** The feed first showed a bet, mid-scan or at the end. Null = no bet. */
    val firstBetAtMs: Long? = null,
    val totalMs: Long = 0,
    /** Times Novig refused a price (a 429, or its edge's 403): each one pauses the scan and slows it for a minute. */
    val refused: Int = 0,
    /** Lines left for the next scan: read any later, their other books' odds would have been too old to show. */
    val leftTooLate: Int = 0,
    /** When each fair-odds source finished, from the scan's start (a source that answered nothing is left out). */
    val sourceMs: List<Pair<String, Long>> = emptyList(),
    /** When the key's live feed was handed this scan's markets, and how many (it holds up to 2,000): null = it wasn't (no key, or nothing to read). */
    val liveFeedAtMs: Long? = null,
    val liveFeedAsked: Int = 0,
) {
    val novigMs: Long get() = if (novigFromMs != null && novigToMs != null) (novigToMs - novigFromMs).coerceAtLeast(0) else 0

    companion object {
        /**
         * One line for Settings, e.g. "Last scan took 41 s: board 0.9 s · fair odds 27 s · 1,200 Novig prices in 38 s
         * (31.6 a second: 700 by live feed, 500 through the key) · first bet at 12 s · Novig refused none · the key's
         * limit is 16 a second".
         */
        fun text(t: ScanTiming, prices: Int, viaKey: Int, viaPush: Int, keyPerSec: Double? = null): String = buildString {
            append("Last scan took ").append(seconds(t.totalMs)).append(": board ").append(seconds(t.boardAtMs))
            append(" · fair odds ").append(seconds(t.fairAtMs))
            // Which source it waited for (a league's bets wait for all its sources): "Kalshi 27 s, Polymarket 13 s".
            t.sourceMs.sortedByDescending { it.second }.take(3).takeIf { it.size > 1 }?.let { slow ->
                append(" (").append(slow.joinToString(", ") { "${it.first} ${seconds(it.second)}" }).append(")")
            }
            if (prices > 0) {
                append(" · ").append(String.format(Locale.US, "%,d", prices)).append(" Novig price").append(if (prices == 1) "" else "s")
                append(" in ").append(seconds(t.novigMs))
                val pace = if (t.novigMs > 0) String.format(Locale.US, "%.1f a second", prices * 1000.0 / t.novigMs) else null
                val public = (prices - viaKey - viaPush).coerceAtLeast(0)
                val ways = listOfNotNull(
                    "$viaPush by live feed".takeIf { viaPush > 0 },
                    "$viaKey through the key".takeIf { viaKey > 0 },
                    "$public public".takeIf { public > 0 },
                ).joinToString(", ").takeIf { it.isNotEmpty() }
                listOfNotNull(pace, ways).takeIf { it.isNotEmpty() }?.let { append(" (").append(it.joinToString(": ")).append(")") }
            }
            // Whether the one bulk subscribe went out, and when: the next report says whether it carried the scan (RESEARCH.md §63).
            t.liveFeedAtMs?.takeIf { t.liveFeedAsked > 0 && viaKey + viaPush > 0 }?.let {
                append(" · live feed asked for ").append(String.format(Locale.US, "%,d", t.liveFeedAsked)).append(" at ").append(seconds(it))
            }
            append(" · ").append(t.firstBetAtMs?.let { "first bet at ${seconds(it)}" } ?: "no bet")
            append(" · Novig refused ").append(if (t.refused == 0) "none" else "${t.refused}")
            if (t.leftTooLate > 0) append(" · ").append(String.format(Locale.US, "%,d", t.leftTooLate)).append(" left for the next scan (their odds would have been too old)")
            keyPerSec?.let { append(" · the key's limit is ").append(rate(it)).append(" a second") }
        }

        /** "0.9 s" under ten seconds, "27 s" above. */
        fun seconds(ms: Long): String =
            if (ms < 10_000) String.format(Locale.US, "%.1f s", ms / 1000.0) else "${(ms + 500) / 1000} s"

        private fun rate(perSec: Double): String =
            if (perSec == Math.floor(perSec)) perSec.toLong().toString() else String.format(Locale.US, "%.1f", perSec)
    }
}
