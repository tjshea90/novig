package com.tjshea.vigilant.data.scanner

/**
 * The best-to-worst order of the aggregator APIs, from what each was measured to deliver (Tj, 2026-10-13: "always use the apis from Best to worst order, favoring apis that provide the most accurate,
 * freshest odds and scanning speed"). Pure.
 *
 * Measured per feed from the boards a scan just read ([Measure]): the 90th-percentile age of its quotes by the books' OWN stamps, and how far its DraftKings/FanDuel main lines sit from Pinnacle's
 * ([SourceAgreement]). The order is: feeds measured accurate before feeds measured inaccurate; then by age in 1-minute steps (fresher first); a feed with no measurement yet counts as 4 minutes old,
 * so a proven-fresh feed outranks it and it outranks a proven-stale one; then the fixed order the app shipped with ([prior]). A feed is auditioned (asked once even though better feeds answered) when
 * it has no measurement newer than [AUDITION_MS], so a feed that stood by can still earn the first place.
 */
object SourceScore {
    data class Measure(
        val id: String,
        val quotes: Int,
        /** 90th-percentile age of the feed's quotes by the books' own stamps, null when the feed stamps nothing (it is read at request time). */
        val p90AgeMs: Long?,
        /** Mean distance from Pinnacle in percentage points on DK/FD main lines, and how many lines that rests on. */
        val agreementPts: Double?,
        val agreementLines: Int,
        val atMs: Long,
    )

    const val MIN_QUOTES = 200
    const val BUCKET_MS = 60_000L
    const val UNMEASURED_AGE_MS = 4 * 60_000L
    const val AUDITION_MS = 6 * 3_600_000L

    /** A feed whose prices sit more than this far from Pinnacle's on the same books, over enough lines, is ranked after every accurate one. */
    const val INACCURATE_PTS = 3.0
    const val MIN_AGREEMENT_LINES = 20

    fun inaccurate(m: Measure?): Boolean = m != null && m.agreementPts != null && m.agreementLines >= MIN_AGREEMENT_LINES && m.agreementPts > INACCURATE_PTS

    /** [prior] re-ordered by [measures]; ids not in [measures] keep their place by the 4-minute rule. */
    fun rank(prior: List<String>, measures: Map<String, Measure>): List<String> =
        prior.withIndex().sortedWith(
            compareBy<IndexedValue<String>>(
                { (_, id) -> if (inaccurate(measures[id])) 1 else 0 },
                // A feed with a small monthly allowance never leads, however fresh its last answers were (Tj, 2026-10-16: "make sure the GitHub cloud scanning does not waste any api usage that is
                // unnecessary ... redundant data offered by a better api ... not used unless needed for extra data or a fallback"): The Odds API led both chains on its 46-second p90 and spent 2,507 of
                // its 3,500 monthly credits in 16 days while flat-rate SGO and PropLine slept. It is asked as the fallback or for a gap, never first.
                { (_, id) -> if (id in SCARCE) 1 else 0 },
                { (_, id) -> bucket(measures[id]) },
                { it.index },
            ),
        ).map { it.value }

    private fun bucket(m: Measure?): Long {
        if (m == null || m.quotes < MIN_QUOTES) return UNMEASURED_AGE_MS / BUCKET_MS
        // A feed that stamps nothing is read at request time: as fresh as the read.
        return (m.p90AgeMs ?: 0L) / BUCKET_MS
    }

    /** Whether [id] has no measurement newer than [AUDITION_MS] (and is not one of the [scarce] feeds, whose few credits an audition would waste). */
    fun auditionDue(id: String, now: Long, measures: Map<String, Measure>, scarce: Set<String> = SCARCE): Boolean =
        id !in scarce && (measures[id]?.let { now - it.atMs > AUDITION_MS } ?: true)

    /** Feeds with a small monthly allowance: never auditioned. */
    val SCARCE = setOf("oddsapi", "oddsapi_props", "oddspapi_free", "oddspapi_free-props")

    /** One line for Diagnostics: the order and what it rests on. */
    fun describe(order: List<String>, measures: Map<String, Measure>): String = order.joinToString(" > ") { id ->
        val m = measures[id]
        FairProvenance.apiName(id) + when {
            m == null || m.quotes < MIN_QUOTES -> " (not measured)"
            else -> " (" + (m.p90AgeMs?.let { "p90 ${OddsAge.span(it)}" } ?: "read live") + (m.agreementPts?.takeIf { m.agreementLines >= MIN_AGREEMENT_LINES }?.let { ", %.1f pts off Pinnacle".format(it) } ?: "") + (if (inaccurate(m)) ", INACCURATE" else "") + ")"
        }
    }
}
