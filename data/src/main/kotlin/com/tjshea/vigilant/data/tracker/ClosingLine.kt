package com.tjshea.vigilant.data.tracker

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * True closing line value (Tj, 2026-09-29: "make a system that finds the true closing odds for each of my bets"). A bet's closing line is the
 * devigged fair line read just before its game starts ([TrackedBet.closingFair], written by every pregame read: Check odds now, a scan, and
 * the capture below). It's only the TRUE close when that read was made in the last [TRUE_CLOSE_MS] before the start, and it's final once the
 * game has started; a line read hours earlier is not a close, so such a bet has no CLV.
 *
 * The capture: an exact alarm wakes Vigilant [LEAD_MS] before each open bet's start ([nextAt]) and re-reads every bet starting within [DUE_MS]
 * ([due]): CNO's game page for a CNO bet, Vigilant's own fair odds for the rest. A read that fails is tried again [RETRY_MS] later, until
 * [LAST_TRY_MS] before the start ([TrackedBet.closeTriedAtMs]). A bet read that early gets one more look [FINAL_LEAD_MS] before the start
 * ([needsFinalRead], Tj 2026-10-01: "real closing lines"), so the close is the line about two minutes from the start, not six; if that look
 * fails, the earlier one stands.
 *
 * A bet with no read near its start has NO close here: never "closed at the line it was bet at" (that would be its own EV at bet, so every +EV bet
 * would beat the close by construction). The back-fill ([CloseBackfill]) finds the real one afterwards.
 */
object ClosingLine {
    /** A read this close to the start is the closing line. */
    const val TRUE_CLOSE_MS = 15 * 60_000L

    /** The capture wakes this long before a start… */
    const val LEAD_MS = 6 * 60_000L

    /** …and reads every bet starting within this (inside [TRUE_CLOSE_MS], so what it reads counts). */
    const val DUE_MS = 12 * 60_000L

    /** A bet whose read failed is tried again this much later… */
    const val RETRY_MS = 2 * 60_000L

    /** …but not in the last minute: the answer would come after the start. */
    const val LAST_TRY_MS = 60_000L

    /** A bet whose read is older than this before the start gets a final read ([needsFinalRead])… */
    const val FINAL_FRESH_MS = 3 * 60_000L

    /** …when the start is this near (the alarm wakes [FINAL_LEAD_MS] before it, allowing for the worker's start-up)… */
    const val FINAL_DUE_MS = 150_000L

    /** …roughly two minutes before the start, still [LAST_TRY_MS] clear of it. */
    const val FINAL_LEAD_MS = 110_000L

    /** Tj's outliers: a bet more than 5% from its close either way. */
    const val OUTLIER_CLV = 0.05

    private const val EPS = 1e-9

    /**
     * [b]'s fair probability at the close, once its game has started ([now]) and only when it's a true close; else null. In order: a read
     * made in the last minutes before the start (the bet's own fair odds, [captured]); a close found afterwards in a source that keeps
     * history ([TrackedBet.closeFair]: ParlayAPI's Pinnacle closes, ESPN, Novig's trades; [CloseBackfill]). Nothing else: a bet's own line at
     * the time it was bet is not a closing line, however close to the start it was placed.
     */
    fun closeFair(b: TrackedBet, now: Long): Double? = closeOf(b, now)?.first

    /** [closeFair] and where it came from: [SOURCE_CAPTURED] or the history source's [TrackedBet.closeVia]. */
    fun closeOf(b: TrackedBet, now: Long): Pair<Double, String>? {
        // A lock isn't a pick: its close says nothing about the edges, so it has none (as BetTracker.stats leaves it out of CLV).
        if (now < b.startsTs || b.createdAtMs >= b.startsTs || b.isLock) return null
        captured(b)?.let { return it to SOURCE_CAPTURED }
        if (b.closeFair != null) return b.closeFair to (b.closeVia ?: "history")
        return null
    }

    /** The fair line read in the last [TRUE_CLOSE_MS] before the start, if one was. */
    fun captured(b: TrackedBet): Double? {
        val seen = b.closingSeenAtMs ?: return null
        return b.closingFair?.takeIf { seen < b.startsTs && b.startsTs - seen <= TRUE_CLOSE_MS }
    }

    /** A close's source in a word or two, for the card and Diagnostics. */
    fun sourceLabel(source: String): String = when {
        source == SOURCE_CAPTURED -> "read before the start"
        source.startsWith("ESPN") -> "ESPN"
        source.startsWith("ParlayAPI") -> "Pinnacle via ParlayAPI"
        source.startsWith("Novig") -> "Novig's trades"
        else -> source
    }

    /** [closeOf]'s source for a close read before the start by Vigilant itself. */
    const val SOURCE_CAPTURED = "captured"

    /**
     * [b]'s closing line value: how much better its price was than the closing line, as the EV that price had at the closing fair odds
     * (your decimal odds over the close's fair decimal odds, minus 1). Positive = beat the close. Null until there's a true close.
     */
    fun clv(b: TrackedBet, now: Long): Double? = closeFair(b, now)?.let { it / b.cost - 1.0 }

    /** Open, bet before its game, still to start, and without a close read in the window yet: the capture still has work for it. */
    fun needsClose(b: TrackedBet, now: Long): Boolean {
        if (b.status != BetStatus.PENDING || b.createdAtMs >= b.startsTs || now >= b.startsTs || b.isLock) return false
        val seen = b.closingSeenAtMs
        val inWindow = b.closingFair != null && seen != null && seen < b.startsTs && b.startsTs - seen <= TRUE_CLOSE_MS && seen >= b.startsTs - DUE_MS
        return !inWindow
    }

    /**
     * Open, bet before its game, still to start, and its close read is more than [FINAL_FRESH_MS] from the start: one more read nearer the start
     * replaces it ([ClosingLine]). A bet with no read yet isn't this one (it [needsClose]).
     */
    fun needsFinalRead(b: TrackedBet, now: Long): Boolean {
        if (b.status != BetStatus.PENDING || b.createdAtMs >= b.startsTs || now >= b.startsTs || b.isLock) return false
        val seen = b.closingSeenAtMs ?: return false
        return b.closingFair != null && seen < b.startsTs && b.startsTs - seen <= TRUE_CLOSE_MS && b.startsTs - seen > FINAL_FRESH_MS
    }

    /** The bets a capture at [now] reads: starting within [DUE_MS] (or [FINAL_DUE_MS] for a final read), not tried in the last [RETRY_MS], not in their last minute. */
    fun due(bets: List<TrackedBet>, now: Long): List<TrackedBet> = bets.filter { b ->
        val left = b.startsTs - now
        left > LAST_TRY_MS && (b.closeTriedAtMs == null || now - b.closeTriedAtMs >= RETRY_MS - 5_000L) &&
            ((needsClose(b, now) && left <= DUE_MS) || (needsFinalRead(b, now) && left <= FINAL_DUE_MS))
    }

    /** When the next capture should run for [bets] (never before [now]); null when no open bet needs one. */
    fun nextAt(bets: List<TrackedBet>, now: Long): Long? = bets.mapNotNull { b ->
        val lead = when {
            needsClose(b, now) -> LEAD_MS
            needsFinalRead(b, now) -> FINAL_LEAD_MS
            else -> return@mapNotNull null
        }
        val at = maxOf(b.startsTs - lead, (b.closeTriedAtMs ?: Long.MIN_VALUE / 2) + RETRY_MS, now)
        at.takeIf { b.startsTs - it > LAST_TRY_MS }
    }.minOrNull()

    /**
     * Whether a read made before the start, whose oldest price is from [seenAtMs], may replace [b]'s close: only when it's no older than the one
     * held. The close is the freshest line, never an earlier one that arrived later (a cached page read after a fresher one).
     */
    fun supersedes(b: TrackedBet, seenAtMs: Long): Boolean = b.closingFair == null || b.closingSeenAtMs == null || seenAtMs >= b.closingSeenAtMs
}

/** The CLV section's time periods (Tj, 2026-09-29: "all time, today, yesterday, last 3 days, last week"), by when each bet was placed. */
enum class ClvPeriod(val label: String) {
    ALL("All time"), TODAY("Today"), YESTERDAY("Yesterday"), DAYS_3("Last 3 days"), WEEK("Last week");

    /** The placed-at range [from, until) in [zone]'s calendar days ("last 3 days" = today and the two before it); null = no limit. */
    fun range(now: Long, zone: ZoneId): Pair<Long, Long>? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun start(daysBack: Long) = today.minusDays(daysBack).atStartOfDay(zone).toInstant().toEpochMilli()
        return when (this) {
            ALL -> null
            TODAY -> start(0) to Long.MAX_VALUE
            YESTERDAY -> start(1) to start(0)
            DAYS_3 -> start(2) to Long.MAX_VALUE
            WEEK -> start(6) to Long.MAX_VALUE
        }
    }
}

/**
 * The CLV section's numbers (Tj, 2026-09-29: "the percentage of my bets that beat closing line value ... Also include the average percentage
 * that my bets beat the closing line ... Keep this stat line running forever, it does not reset. New bets will add to this statistic."). Over
 * every bet placed in the period (All time: all of them, ever) whose game has started with a true close; voided bets and bets placed after
 * their start don't count.
 */
data class ClvStats(
    /** Bets with a true close (after [outliersLeftOut] are taken out). */
    val closed: Int,
    /** Of [closed], placed at a better price than the close. */
    val beat: Int,
    /** The average CLV of [closed]; null when there are none. */
    val averageClv: Double?,
    /** Bets over ±[ClosingLine.OUTLIER_CLV] from the close. */
    val outliers: Int,
    /** Whether [outliers] were left out of [closed], [beat] and [averageClv]. */
    val outliersLeftOut: Boolean,
    /** Open bets whose game hasn't started: no close yet. */
    val waiting: Int,
    /** Games that started without a close read in the last [ClosingLine.TRUE_CLOSE_MS] (the phone asleep or offline, CNO only, paused). */
    val missed: Int,
    /** The average EV when bet of the same [closed] bets: the edge the price was thought to have, beside what the close says it had. */
    val averageEvAtBet: Double? = null,
    /** How many of [closed]'s closes came from where ([ClosingLine.sourceLabel]): read before the start, ESPN, Novig's trades. */
    val bySource: Map<String, Int> = emptyMap(),
) {
    val beatShare: Double? get() = if (closed > 0) beat.toDouble() / closed else null

    companion object {
        fun of(bets: List<TrackedBet>, now: Long, period: ClvPeriod = ClvPeriod.ALL, dropOutliers: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): ClvStats {
            val range = period.range(now, zone)
            val inPeriod = bets.filter { b ->
                b.status != BetStatus.VOID && !b.isLock && b.createdAtMs < b.startsTs && (range == null || (b.createdAtMs >= range.first && b.createdAtMs < range.second))
            }
            val closedBets = inPeriod.mapNotNull { b -> ClosingLine.clv(b, now)?.let { b to it } }
            val clvs = closedBets.map { it.second }
            val outliers = clvs.count { abs(it) > ClosingLine.OUTLIER_CLV + EPS }
            val keptBets = if (dropOutliers) closedBets.filter { abs(it.second) <= ClosingLine.OUTLIER_CLV + EPS } else closedBets
            val kept = keptBets.map { it.second }
            return ClvStats(
                closed = kept.size,
                beat = kept.count { it > EPS },
                averageClv = kept.takeIf { it.isNotEmpty() }?.average(),
                outliers = outliers,
                outliersLeftOut = dropOutliers,
                waiting = inPeriod.count { it.status == BetStatus.PENDING && now < it.startsTs },
                missed = inPeriod.count { now >= it.startsTs && ClosingLine.closeFair(it, now) == null },
                averageEvAtBet = keptBets.mapNotNull { it.first.evPercentAtBet }.takeIf { it.isNotEmpty() }?.average(),
                bySource = keptBets.mapNotNull { ClosingLine.closeOf(it.first, now)?.second?.let(ClosingLine::sourceLabel) }.groupingBy { it }.eachCount(),
            )
        }

        private const val EPS = 1e-9
    }
}
