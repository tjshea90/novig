package com.tjshea.vigilant.data.scanner

/**
 * How old the odds behind a bet are, said only as far as the app actually knows it (Tj, 2026-10-11: "Add a function that says how old the odds are for any scanning in the app if it can be figured out
 * accurately"). Pure.
 *  - A real-time live row knows the four ages exactly: Pinnacle's line last changed / its feed last spoke, Novig's market last changed / its feed last spoke ([LiveAges]).
 *  - A scanned row knows when Novig's book was read ([Opportunity.bookFetchedAtMs]) and how old the books behind the fair odds are: the newest and the oldest `last_update` ([Opportunity.fairUpdatedMs],
 *    [Opportunity.fairAsOfMs]). A book with no stamp is not counted: an unknown age is said as unknown, never guessed.
 */
object OddsAge {
    fun span(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000L
        return when {
            s < 1 -> "<1 s"
            s < 120 -> "$s s"
            s < 7200 -> "${s / 60} min"
            else -> "${s / 3600} h"
        }
    }

    /** The age line for [o] at [now]; null when nothing about its age is known. */
    fun text(o: Opportunity, now: Long): String? {
        o.liveAges?.let { a ->
            return "Live odds · Pinnacle line changed ${span(now - a.pinnLineChangedMs)} ago (its feed ${span(now - a.pinnFeedMs)} ago) · " +
                "Novig " + (a.novigMarketMs?.let { "market changed ${span(now - it)} ago" } ?: "market unchanged since it was first read") + " (its feed ${span(now - a.novigFeedMs)} ago)"
        }
        val novig = o.bookFetchedAtMs?.let { "Novig read ${span(now - it)} ago" }
        val newest = o.fairUpdatedMs
        val oldest = o.fairAsOfMs
        val fair = when {
            oldest == null -> "other books' ages unknown"
            newest == null || span(now - newest) == span(now - oldest) -> "other books' prices ${span(now - oldest)} old"
            else -> "other books' prices ${span(now - newest)} to ${span(now - oldest)} old"
        }
        return listOfNotNull(novig, fair).joinToString(" · ")
    }
}
