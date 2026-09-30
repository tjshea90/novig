package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.tracker.OpenBetPricer
import com.tjshea.vigilant.data.tracker.PlacedIndex
import kotlin.math.abs

/**
 * TASKS.md P2 (Tj, 2026-09-30: "next to parlayapi's percent positive EV number in that section, put cno/vigilant's percentage so I can compare
 * and see if it is truly positive EV on each bet"): a ParlayAPI pick's EV at Novig's price now by CNO's fair line and by Vigilant's own, each at
 * the same price as ParlayAPI's (Novig's taker fee taken out on a live game), or why one has none. Pure.
 */
object ParlayCompare {

    /** One scanner's read of a pick: EV at Novig's price now, the fair probability behind it and when it was seen, or [why] there's none. */
    data class Read(val ev: Double?, val fair: Double?, val atMs: Long?, val why: String?) {
        companion object {
            fun none(why: String) = Read(null, null, null, why)
        }
    }

    /** EV of [pick] at Novig's price now against [fair]. */
    fun evAt(fair: Double, pick: ParlayPick, now: Long): Double =
        CnoBooks.evAt(fair, pick.row.odds, live = pick.row.startsAtMs?.let { it <= now } == true)

    /** Two listings start close enough to be one game (PlacedIndex's rule: a day's slack, two hours in baseball); unknown = yes. */
    private fun sameStart(a: Long?, b: Long?, league: String): Boolean {
        if (a == null || b == null) return true
        val gap = if (league.equals("MLB", ignoreCase = true)) PlacedIndex.SAME_BASEBALL_GAME_MS else PlacedIndex.SAME_GAME_MS
        return abs(a - b) <= gap
    }

    /**
     * CNO's list row for the same bet as [pick]: the same game, market, side and line read with BetGrader's rules ([PlacedIndex.identity]),
     * starting close enough to be the same game. A Novig row first (it is the same Novig outcome, and its game page lists every book).
     */
    fun cnoRowFor(pick: ParlayPick, rows: List<CnoRow>): CnoRow? {
        val id = PlacedIndex.identity(pick.row.event, pick.row.market, pick.row.bet) ?: return null
        val same = rows.filter { r ->
            PlacedIndex.identity(r.event, r.market, r.bet) == id && sameStart(r.startsAtMs, pick.row.startsAtMs, pick.row.league)
        }
        return same.firstOrNull { CnoBooks.codeFor(it.book) == CnoBooks.NOVIG } ?: same.firstOrNull()
    }

    /** Vigilant's own priced outcome for [pick] in a scan's [opportunities]: Novig's outcome id, else the same bet read as [cnoRowFor] does. */
    fun opportunityFor(pick: ParlayPick, opportunities: List<Opportunity>): Opportunity? {
        val priced = opportunities.filter { it.fairProbability != null }
        pick.outcomeId?.let { id -> priced.firstOrNull { it.outcome.outcomeId == id }?.let { return it } }
        val id = PlacedIndex.identity(pick.row.event, pick.row.market, pick.row.bet) ?: return null
        return priced.firstOrNull { o ->
            PlacedIndex.identity(o.event.description, o.marketLabel, o.selection) == id && sameStart(o.event.startsTs, pick.row.startsAtMs, pick.row.league)
        }
    }

    /**
     * CNO's EV for [pick]: CNO's fair line from its list row for the same bet ([cnoRowFor]) at Novig's price now. [listAtMs]: when CNO's list
     * was current (null: not read yet); [cnoOn] false: Tj has CNO asleep (Vigilant only).
     */
    fun cno(pick: ParlayPick, rows: List<CnoRow>, listAtMs: Long?, now: Long, cnoOn: Boolean): Read {
        if (!cnoOn) return Read.none("CNO is asleep (Vigilant only)")
        if (listAtMs == null) return Read.none("CNO's list hasn't been read yet: open the CNO tab")
        val row = cnoRowFor(pick, rows) ?: return Read.none("not on CNO's +EV list")
        val fair = CnoChecks.fairProbability(row) ?: return Read.none("CNO lists it with no fair odds")
        return Read(evAt(fair, pick, now), fair, listAtMs, null)
    }

    /**
     * Vigilant's EV for [pick]: the newer of the last scan's fair line for the same Novig outcome ([opportunityFor], while it is still fresh
     * enough to price: [Freshness]) and the bets-only read made for the picks ([reads], by pick key), at Novig's price now.
     * [reading]: that read is under way; [vigilantOn] false: Tj has Vigilant asleep (CNO only).
     */
    fun vigilant(
        pick: ParlayPick,
        opportunities: List<Opportunity>,
        reads: Map<String, OpenBetPricer.FairRead>,
        now: Long,
        vigilantOn: Boolean,
        reading: Boolean = false,
    ): Read {
        if (!vigilantOn) return Read.none("Vigilant's scanner is asleep (CNO only)")
        val scanned = opportunityFor(pick, opportunities)?.takeIf { Freshness.fresh(it.fairAsOfMs, now, pick.row.startsAtMs) }
            ?.let { o -> Read(evAt(o.fairProbability!!, pick, now), o.fairProbability, o.fairAsOfMs, null) }
        val read = reads[pick.key]
        val asked = read?.fair?.let { Read(evAt(it, pick, now), it, read.atMs, null) }
        val best = listOfNotNull(scanned, asked).maxByOrNull { it.atMs ?: 0L }
        return best ?: when {
            reading -> Read.none("reading Vigilant's fair odds…")
            read?.why != null -> Read.none(read.why)
            else -> Read.none("not read yet: tap Recheck")
        }
    }
}
