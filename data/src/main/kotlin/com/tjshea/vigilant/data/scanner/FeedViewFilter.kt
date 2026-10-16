package com.tjshea.vigilant.data.scanner

/**
 * Display-only filters for the +EV screen (Tj, 2026-10-16: "filters I can toggle from the +ev scanner screen ... amount of books
 * agreeing with the positive EV, and an option that all books must agree positive ev ... should not affect the GitHub engine, only
 * what bets show up in the screen on the Android app"). It is NOT part of [ScanSettings], so it is never mirrored to cloud-config
 * and the scan, the auto-bet and the bid desk never see it; the phone keeps it in its own preferences.
 *
 * [minAgreeing]: a row needs at least this many books (one per company) that make it +EV alone; 0 = no filter.
 * [allMustAgree]: every book that prices both sides must say +EV (and at least one must price it).
 */
data class FeedViewFilter(val minAgreeing: Int = 0, val allMustAgree: Boolean = false) {
    val active: Boolean get() = minAgreeing > 0 || allMustAgree

    fun passes(a: Agreement.Count): Boolean =
        a.agreeing >= minAgreeing && (!allMustAgree || (a.twoSided > 0 && a.agreeing == a.twoSided))

    fun apply(rows: List<Opportunity>): List<Opportunity> = if (!active) rows else rows.filter { passes(it.agreement) }

    companion object {
        const val MAX_MIN = 6
    }
}
