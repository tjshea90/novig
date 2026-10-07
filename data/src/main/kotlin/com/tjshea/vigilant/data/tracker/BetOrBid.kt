package com.tjshea.vigilant.data.tracker

import java.time.ZoneId
import java.util.Locale

/**
 * Whether a logged record is a BET or a BID (Tj, 2026-10-07: "make the app bet logging differentiate from bets and bids … so I can see stats and ev filtered my bids as
 * well as bets, and also for the diagnostics and studies sections"). A bid is a make order Vigilant posted under its fair price that a taker filled; a bet is every taker
 * order. [TrackedBet.isBid] is the one rule; this is its name, for the filters, the splits and the files. RESEARCH.md §111.
 */
enum class BetOrBid(
    /** How a group of them is named in the splits. */
    val group: String,
    /** The word for one, as the files write it (`made` on every line). */
    val word: String,
) {
    BET("Bets (taker orders)", "bet"),
    BID("Bids (make orders that filled)", "bid");

    companion object {
        fun of(b: TrackedBet): BetOrBid = if (b.isBid) BID else BET

        /** The bets of kind [which], or all of them when it is null. */
        fun only(bets: List<TrackedBet>, which: BetOrBid?): List<TrackedBet> = if (which == null) bets else bets.filter { of(it) == which }
    }
}

/**
 * Bets and bids side by side, as lines of text for the Diagnostics file and the scan study (Tj, 2026-10-07: "so I can see stats and ev filtered my bids as well as bets,
 * and also for the diagnostics and studies sections"): for each kind the record, ROI, EV when bet and CLV, the closing-line numbers with the outliers in, the open ones
 * and their current EV, the time to the start, the trap guard's read of Novig's own trades, and how they were graded. Pure; every number is the Tracker's own
 * ([BetTracker.stats], [ClvStats], [BetLedger]). A bid's EV is the edge at its fair when it was POSTED; its fill is judged afterwards in the BIDS section.
 */
object BetsAndBids {

    private fun pct(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)
    private fun money(v: Double) = String.format(Locale.US, "%+.2f", v)

    /** Bets with a true close (the n behind a CLV average). */
    fun closedCount(bets: List<TrackedBet>, now: Long): Int = bets.count { it.status != BetStatus.VOID && ClosingLine.clv(it, now) != null }

    /**
     * [all] split into bets and bids, one block each. [currentEv]: whether an open bet's current EV is young enough to count (the app's freshness rule; by default any read).
     * Empty when [all] has no records.
     */
    fun lines(
        all: List<TrackedBet>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        currentEv: (TrackedBet) -> Boolean = { it.nowEv != null },
    ): List<String> {
        if (all.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (kind in BetOrBid.entries) {
            val group = BetOrBid.only(all, kind)
            val noun = kind.word
            if (group.isEmpty()) {
                out += "${kind.group}: none" + if (kind == BetOrBid.BID) " (no bid has been filled yet; the BIDS section counts every bid posted)" else ""
                continue
            }
            val kept = group.filterNot { it.isOutlier }
            val st = BetTracker.stats(group, now)
            out += "${kind.group}: " + TrackerBreakdown.describe(st, closedCount(kept, now), noun)
            if (st.outliers > 0) {
                out += "  every settled $noun, outliers too (the Tracker's Profit): profit ${money(st.profitAll)} on ${String.format(Locale.US, "%.2f", st.stakedAll)} staked" +
                    (st.roiAll?.let { " (${pct(it)})" } ?: "") + " · ${st.outliers} outlier${if (st.outliers == 1) "" else "s"} (over ±${(BetTracker.OUTLIER_EV * 100).toInt()}% EV when bet)"
            }
            val clv = ClvStats.of(group, now, zone = zone)
            out += "  closing line (all time, outliers in): beat the close " + (clv.beatShare?.let { "${Math.round(it * 100)}% (${clv.beat} of ${clv.closed})" } ?: "–") +
                " · avg vs close " + (clv.averageClv?.let(::pct) ?: "–") + " · avg EV at ${if (kind == BetOrBid.BID) "post" else "bet"} " + (clv.averageEvAtBet?.let(::pct) ?: "–") +
                (if (clv.waiting > 0) " · ${clv.waiting} waiting for their close" else "") + (if (clv.missed > 0) " · ${clv.missed} started with no close found yet" else "")
            val open = group.filter { it.status == BetStatus.PENDING }
            val upcoming = open.filter { now < it.startsTs }
            val priced = upcoming.filter { currentEv(it) && it.nowEv != null }
            out += "  open: ${open.size} (${upcoming.size} upcoming, ${open.size - upcoming.size} started) · current EV on the upcoming: ${priced.size} of ${upcoming.size} priced" +
                (priced.map { it.nowEv!! }.takeIf { it.isNotEmpty() }?.average()?.let { ", average ${pct(it)}" } ?: "")
            val lead = BetLedger.of(kept, BetLedger.Split.LEAD, now).sortedBy { BetLedger.LEAD_ORDER.indexOf(it.label).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            if (lead.isNotEmpty()) {
                out += "  by time to the start when ${if (kind == BetOrBid.BID) "posted" else "placed"}:"
                lead.forEach { row -> out += "    ${row.label}: " + TrackerBreakdown.describe(row.stats, closedCount(kept.filter { BetLedger.keyOf(it, BetLedger.Split.LEAD) == row.label }, now), noun) }
            }
            val trap = BetLedger.of(kept, BetLedger.Split.NOVIG_MOVE, now)
            if (trap.all { it.label == BetLedger.NOT_RECORDED }) {
                out += "  Novig's own trades just before (trap guard): not recorded for ${kind.group.substringBefore(" (").lowercase()}"
            } else {
                out += "  Novig's own trades just before (trap guard):"
                trap.forEach { row -> out += "    ${row.label}: " + TrackerBreakdown.describe(row.stats, closedCount(kept.filter { BetLedger.keyOf(it, BetLedger.Split.NOVIG_MOVE) == row.label }, now), noun) }
            }
            val started = open.filter { now >= it.startsTs }
            out += "  graded by: score feeds ${group.count { it.settledBy == BetSettler.BY_SCORES }}, Novig's ledger ${group.count { it.settledBy == BetSettler.BY_NOVIG }}, you ${group.count { it.settledBy == BetSettler.BY_YOU }}" +
                " · started and still open: ${started.size} (${started.count { now - it.startsTs > 6 * 3_600_000L }} for over 6 hours, ${started.count { it.gradeManual || it.autoGradeOff }} need a tap)"
        }
        return out
    }
}
