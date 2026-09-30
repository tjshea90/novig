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

    /**
     * CNO's list and a scan's priced outcomes, each read once ([PlacedIndex.identity] parses a bet's wording), so a screen of picks looks each
     * one up without re-reading hundreds of rows per card.
     */
    class Index(cnoRows: List<CnoRow>, opportunities: List<Opportunity>) {
        private val cno: Map<String, List<CnoRow>> = cnoRows.groupBy { PlacedIndex.identity(it.event, it.market, it.bet) ?: "" } - ""
        private val priced = opportunities.filter { it.fairProbability != null }
        private val byOutcome: Map<String, Opportunity> = priced.associateBy { it.outcome.outcomeId }
        private val byIdentity: Map<String, List<Opportunity>> by lazy {
            priced.groupBy { PlacedIndex.identity(it.event.description, it.marketLabel, it.selection) ?: "" } - ""
        }

        /**
         * CNO's list row for the same bet as [pick]: the same game, market, side and line read with BetGrader's rules, starting close enough to
         * be the same game. A Novig row first (it is the same Novig outcome, and its game page lists every book).
         */
        fun cnoRowFor(pick: ParlayPick): CnoRow? {
            val same = identity(pick)?.let { cno[it] }.orEmpty().filter { sameStart(it.startsAtMs, pick.row.startsAtMs, pick.row.league) }
            return same.firstOrNull { CnoBooks.codeFor(it.book) == CnoBooks.NOVIG } ?: same.firstOrNull()
        }

        /** Vigilant's own priced outcome for [pick]: Novig's outcome id, else the same bet read as [cnoRowFor] does. */
        fun opportunityFor(pick: ParlayPick): Opportunity? {
            pick.outcomeId?.let { id -> byOutcome[id]?.let { return it } }
            return identity(pick)?.let { byIdentity[it] }?.firstOrNull { sameStart(it.event.startsTs, pick.row.startsAtMs, pick.row.league) }
        }

        private fun identity(pick: ParlayPick) = PlacedIndex.identity(pick.row.event, pick.row.market, pick.row.bet)
    }

    /** EV of [pick] at Novig's price now against [fair]. */
    fun evAt(fair: Double, pick: ParlayPick, now: Long): Double =
        CnoBooks.evAt(fair, pick.row.odds, live = pick.row.startsAtMs?.let { it <= now } == true)

    /** Two listings start close enough to be one game (PlacedIndex's rule: half a day's slack, two hours in baseball); unknown = yes. */
    private fun sameStart(a: Long?, b: Long?, league: String): Boolean {
        if (a == null || b == null) return true
        val gap = if (league.equals("MLB", ignoreCase = true)) PlacedIndex.SAME_BASEBALL_GAME_MS else PlacedIndex.SAME_GAME_MS
        return abs(a - b) <= gap
    }

    /**
     * CNO's EV for [pick]: CNO's fair line from its list row for the same bet ([Index.cnoRowFor]) at Novig's price now. [listAtMs]: when CNO's
     * list was current (null: not read yet); [cnoOn] false: Tj has CNO asleep (Vigilant only).
     */
    fun cno(pick: ParlayPick, index: Index, listAtMs: Long?, now: Long, cnoOn: Boolean): Read {
        if (!cnoOn) return Read.none("CNO is asleep (Vigilant only)")
        if (listAtMs == null) return Read.none("CNO's list hasn't been read yet: open the CNO tab")
        // An old list's fair line isn't compared (the feed's rule for any book's price): the CNO tab reads it again.
        if (!Freshness.fresh(listAtMs, now, pick.row.startsAtMs)) return Read.none("CNO's list is ${(now - listAtMs) / 60_000L} min old: open the CNO tab")
        val row = index.cnoRowFor(pick) ?: return Read.none("not on CNO's +EV list")
        val fair = CnoChecks.fairProbability(row) ?: return Read.none("CNO lists it with no fair odds")
        return Read(evAt(fair, pick, now), fair, listAtMs, null)
    }

    /**
     * Vigilant's EV for [pick]: the newer of the last scan's fair line for the same Novig outcome ([Index.opportunityFor], while it is still
     * fresh enough to price: [Freshness]) and the bets-only read made for the picks ([reads], by pick key), at Novig's price now.
     * [reading]: that read is under way; [vigilantOn] false: Tj has Vigilant asleep (CNO only).
     */
    fun vigilant(
        pick: ParlayPick,
        index: Index,
        reads: Map<String, OpenBetPricer.FairRead>,
        now: Long,
        vigilantOn: Boolean,
        reading: Boolean = false,
    ): Read {
        if (!vigilantOn) return Read.none("Vigilant's scanner is asleep (CNO only)")
        val scanned = index.opportunityFor(pick)?.takeIf { Freshness.fresh(it.fairAsOfMs, now, pick.row.startsAtMs) }
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
