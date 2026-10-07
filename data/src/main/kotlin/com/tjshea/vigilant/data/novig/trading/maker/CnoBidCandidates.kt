package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.TrapGuard

/**
 * Which CNO rows are worth a game-page read for a bid (RESEARCH.md §113-§114), pure. CNO's list holds only sides that are +EV at Novig's ask; a bid sits under the fair, so the rows
 * that matter are the ones whose game page can give a side a bid: pregame, outside the stop window and the trap guard's early window, a kind of bet the Bids rules allow, inside
 * the scope Tj gave the CNO scanner (leagues, kinds, words), with a fair that puts a bid inside the price window on one of its two sides. Ordered by start (the nearest games
 * fill most: 71% of prop takers' dollars trade in the last 6 hours, §71), then by the books behind CNO's fair. A side's complement is on its page, so only one of two listed
 * sides is read ([CnoBooks.complement]).
 */
object CnoBidCandidates {

    /** The reasons [CnoChecks.rejection] gives that decide scope; its price and EV rules are the taker list's, not a bid's. */
    private val SCOPE = setOf(CnoChecks.Reason.NOT_A_GAME, CnoChecks.Reason.LEAGUE, CnoChecks.Reason.KIND, CnoChecks.Reason.LIVE, CnoChecks.Reason.TEXT)

    /** Why [row] gets no page read for a bid, or null when it does. */
    fun skipReason(row: CnoRow, filters: CnoFilters, rules: MakerRules, now: Long): String? {
        if (!Picks.isGame(row.event)) return "not a game"
        CnoChecks.rejection(row, filters, now)?.takeIf { it in SCOPE }?.let { return it.text }
        if (row.oneWay) return "devigged from one side only"
        val starts = row.startsAtMs ?: return "no start time"
        if (starts <= now) return "already under way"
        if (starts - now < rules.stopMs) return "starts within the stop window"
        if (TrapGuard.isEarly(starts, now, rules.earlyHours)) return "starts too far off (trap guard)"
        if (BetKind.of(row.market, row.bet) !in rules.kinds) return "a kind of bet bids are off for"
        if (row.books != null && row.books < rules.minBooks) return "too few books behind CNO's fair"
        val fair = CnoChecks.fairProbability(row) ?: return "no fair price"
        if (fair <= 0.0 || fair >= 1.0) return "no fair price"
        // A bid on either side must be able to sit inside the price window: the listed side at its fair, the other at the rest.
        val inside = listOf(fair, 1.0 - fair).any { f ->
            val price = PriceGrid.floor(f / (1.0 + rules.margin)) ?: return@any false
            MakerQuote.outsideWindow(price, rules) == null
        }
        if (!inside) return "no bid on either side would sit inside the price window"
        return null
    }

    /**
     * [rows] (the list's and the wide read's, newest first) that get a page read, soonest start first, at most [limit]; a row whose complement is already picked, or that is
     * the same side as a picked one, is left out.
     */
    fun pick(rows: List<CnoRow>, filters: CnoFilters, rules: MakerRules, now: Long, limit: Int = DEFAULT_LIMIT): List<CnoRow> {
        val picked = ArrayList<CnoRow>()
        val seen = HashSet<String>()
        val ordered = rows.asSequence()
            .filter { skipReason(it, filters, rules, now) == null }
            .sortedWith(compareBy<CnoRow> { it.startsAtMs ?: Long.MAX_VALUE }.thenByDescending { it.books ?: 0 })
        for (r in ordered) {
            if (picked.size >= limit) break
            if (!seen.add("${r.event}|${r.market}|${r.bet.lowercase()}")) continue
            if (picked.any { it.event == r.event && it.market == r.market && CnoBooks.complement(it.bet, r.bet) }) continue
            picked += r
        }
        return picked
    }

    /** The most rows kept as candidates at once: a page is read for a few of them a cycle, so more would only go old. */
    const val DEFAULT_LIMIT = 60
}
