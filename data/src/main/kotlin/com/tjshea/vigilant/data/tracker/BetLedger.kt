package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.SharpVeto
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Every bet for the Diagnostics file (Tj, 2026-10-02 17:01Z: "include this information for all bets in the diagnosis feature. The diagnosis file can be as large
 * and comprehensive as needed for Claude to properly diagnose and fine tune the app … the goal is profit and positive EV and clv"): one JSON line per bet with
 * its record as placed ([AtBet]), its close and its result, and the close and results split by what the record says. Pure. RESEARCH.md §66.4.
 */
object BetLedger {

    /** One bet: its own fields, its record as placed, its close and its result. */
    @Serializable
    data class Row(
        val id: String,
        val placedAtMs: Long,
        val scanner: String,
        val auto: Boolean,
        val api: Boolean,
        val league: String,
        val event: String,
        val market: String,
        val selection: String,
        val startsAtMs: Long,
        val american: Int?,
        val cost: Double,
        val stake: Double,
        val evAtBet: Double?,
        val fairAtBet: Double?,
        val status: String,
        val settledAtMs: Long?,
        val profit: Double?,
        val clv: Double?,
        val closeFair: Double?,
        val closeVia: String?,
        val closeFinal: Boolean,
        val nowEv: Double?,
        val nowAtMs: Long?,
        val outlier: Boolean,
        val atBet: AtBet?,
        /** A lock's: the bet it locks in (RESEARCH.md §67). */
        val lockFor: String? = null,
        /** Novig's own price now and its close ([NovigNow]). */
        val novigFair: Double? = null,
        val novigClose: Double? = null,
        /**
         * Novig's public catalog ids for the bet (not account ids: anyone can read them), so research can find the bet in Novig's published trades
         * by id (RESEARCH.md §71 had to match Tj's bets to his own trades by time and price).
         */
        val marketId: String? = null,
        val outcomeId: String? = null,
        /**
         * An API bet's grading note when it is worth a look: a loss taken from Novig's silence alone ([ApiSettler.SILENT_LOSS], no payout row, no score
         * feed), or a note that needs a tap. Tj's Ollie Gordon and Bhayshul Tuten overs won and were graded lost this way (RESEARCH.md §87).
         */
        val gradeNote: String? = null,
    )

    private val json = Json { encodeDefaults = false; explicitNulls = false }

    fun row(b: TrackedBet, now: Long): Row = Row(
        id = b.id.take(8), placedAtMs = b.createdAtMs, scanner = b.source, auto = b.auto, api = b.viaApi, league = b.league, event = b.eventName,
        market = b.marketLabel, selection = b.selection, startsAtMs = b.startsTs, american = b.american, cost = b.cost, stake = b.stake,
        evAtBet = b.evPercentAtBet, fairAtBet = b.fairAtBet, status = b.status.name, settledAtMs = b.settledAtMs, profit = b.profit,
        clv = ClosingLine.clv(b, now), closeFair = ClosingLine.closeFair(b, now), closeVia = b.closeVia, closeFinal = b.closeFinal,
        nowEv = b.nowEv, nowAtMs = b.nowAtMs, outlier = b.isOutlier, atBet = b.atBet,
        lockFor = b.lockFor?.take(8), novigFair = b.novigFair, novigClose = b.novigClose,
        marketId = b.marketId.ifBlank { null }, outcomeId = b.outcomeId.ifBlank { null },
        gradeNote = b.gradeNote?.takeIf { b.viaApi && (it == ApiSettler.SILENT_LOSS || b.gradeManual) }?.take(240),
    )

    /** [b] as one JSON line. */
    fun line(b: TrackedBet, now: Long): String = json.encodeToString(row(b, now))

    /** The ways the close and the results are split by the record as placed. */
    enum class Split(val label: String) {
        AGREEMENT("Books agreeing"),
        DISSENT("Books saying not +EV"),
        SHARP("Sharp veto"),
        SHARP_BOOK("Sharpest book on the page"),
        LEAD("Time to the start"),
        CHECK_EV("Book check's EV"),
        TWO_SIDED("Companies pricing both sides"),
        KIND("Kind of bet"),
        SPORT("Sport"),
        PRESET("Preset"),
        HOW("How placed"),
        LIQUIDITY("Novig dollars at the price"),
        PAGE_AGE("Book page's age"),
        NOVIG_MOVE("Novig's own trades just before (trap guard)"),
    }

    /** [b]'s group for [split]; [NOT_RECORDED] for a bet placed before v0.45.0 (or one that didn't have that fact). */
    fun keyOf(b: TrackedBet, split: Split): String {
        // Every bet knows when it was placed and when its game starts: the time to the start needs no record as placed (RESEARCH.md §71: the
        // strongest split of Tj's closes, and the trap guard's first rule).
        if (split == Split.LEAD) return (b.atBet?.minutesToStart ?: b.startsTs.takeIf { it > 0 }?.let { (it - b.createdAtMs) / 60_000L })?.let(::leadBand) ?: NOT_RECORDED
        val a = b.atBet ?: return NOT_RECORDED
        return when (split) {
            Split.AGREEMENT -> a.agreeing?.let { n -> a.twoSided?.let { t -> if (t > 0 && n == t) "every one ($n of $t)" else if (t > 0) "${t - n} of $t not agreeing" else null } } ?: NOT_RECORDED
            // A record with no book page (a ✓ on a notification) has the counts but not the names: the count says how many disagreed.
            Split.DISSENT -> when (val d = if (a.books.isEmpty() && a.twoSided != null && a.agreeing != null) (a.twoSided - a.agreeing).coerceAtLeast(0) else a.dissent.size) {
                0 -> if (a.twoSided == null) NOT_RECORDED else "none"
                1 -> "1"
                else -> if (d >= 3) "3 or more" else "2"
            }
            Split.SHARP -> a.sharpVerdict ?: NOT_RECORDED
            Split.SHARP_BOOK -> a.sharpBook?.let { "$it ${if (a.sharpVerdict == SharpVeto.Verdict.VETOED.name) "said no" else "agreed"}" } ?: if (a.sharpVerdict != null) "none on the page" else NOT_RECORDED
            Split.LEAD -> NOT_RECORDED // answered above
            Split.CHECK_EV -> a.checkEv?.let(TrackerBreakdown::evBand) ?: NOT_RECORDED
            Split.TWO_SIDED -> a.twoSided?.let { if (it >= 10) "10 or more" else if (it >= 6) "6-9" else if (it >= 4) "4-5" else "$it" } ?: NOT_RECORDED
            Split.KIND -> runCatching { BetKind.valueOf(a.kind).label }.getOrDefault(a.kind.ifEmpty { NOT_RECORDED })
            Split.SPORT -> a.sport.ifEmpty { NOT_RECORDED }.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
            Split.PRESET -> a.preset ?: "none"
            Split.HOW -> a.how
            Split.LIQUIDITY -> a.available?.let { if (it < 25) "under $25" else if (it < 100) "$25-100" else if (it < 500) "$100-500" else "$500 or more" } ?: NOT_RECORDED
            Split.PAGE_AGE -> a.pageAgeSec?.let { if (it < 30) "under 30 s" else if (it < 120) "30 s-2 min" else if (it < 600) "2-10 min" else "10 min or more" } ?: NOT_RECORDED
            Split.NOVIG_MOVE -> a.novigMove?.substringBefore(" ·") ?: NOT_RECORDED
        }
    }

    /** [leadBand]'s bands, nearest to the start first (the Tracker's "Time to start" rows read in this order). */
    val LEAD_ORDER = listOf("under 30 min", "30 min-2 h", "2-6 h", "6-24 h", "24 h or more", "after the start", "not recorded")

    /** Minutes before the start, banded. */
    fun leadBand(minutes: Long): String = when {
        minutes < 0 -> "after the start"
        minutes < 30 -> "under 30 min"
        minutes < 120 -> "30 min-2 h"
        minutes < 360 -> "2-6 h"
        minutes < 1440 -> "6-24 h"
        else -> "24 h or more"
    }

    /** [bets] grouped by [split], the most-settled groups first ([TrackerBreakdown.of]'s order); closes as of [now]. */
    fun of(bets: List<TrackedBet>, split: Split, now: Long = System.currentTimeMillis()): List<TrackerBreakdown.Row> = bets
        .groupBy { keyOf(it, split) }
        .map { (label, group) -> TrackerBreakdown.Row(label, BetTracker.stats(group, now)) }
        .filter { it.stats.bets > 0 }
        .sortedWith(compareByDescending<TrackerBreakdown.Row> { it.stats.settled }.thenByDescending { it.stats.bets }.thenBy { it.label })

    const val NOT_RECORDED = "not recorded"
}
