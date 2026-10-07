package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.engine.Odds
import kotlin.math.abs

/** A CNO row that passed [CnoChecks], with the EV Tj would actually get. */
data class CnoPick(
    val row: CnoRow,
    /** CNO's EV, less Novig's taker fee when the game has started (pregame fills are free). */
    val ev: Double,
    val live: Boolean,
)

/** What the checks kept, and how many rows each check left out. */
data class CnoScreened(val picks: List<CnoPick>, val hidden: Map<CnoChecks.Reason, Int>) {
    val hiddenCount: Int get() = hidden.values.sum()
}

/**
 * The app's own screen over CNO's list (Tj, 2026-09-26: "accurate, true positive EV bets";
 * RESEARCH.md §19). CNO already applies the same filters server-side; these catch anything its
 * page ignored or that the link overrode, plus what CNO doesn't check:
 *
 *  - anything that isn't a game between two sides: futures and awards (Tj, 2026-09-29: "I'm not
 *    interested in futures bets. Leave those out of the app");
 *  - EV that doesn't follow from the row's own fair odds and price (a misread row);
 *  - one-way devigs (CNO's ⚠️: fair value from a single side plus a guessed juice);
 *  - EV so high it's almost always a stale or mismatched line ([MAX_EV]);
 *  - live games, where Novig charges the taker a fee CNO's EV doesn't subtract.
 */
object CnoChecks {

    /** Above this EV a row is far more likely a stale or wrong line than a real edge. */
    const val MAX_EV = 0.20

    /** CNO's own EV and one recomputed from its fair odds may differ by rounding, not more. */
    const val EV_TOLERANCE = 0.005

    /** CNO's FAQ: a "Last Updated" over 10 minutes means its updater has stopped. */
    const val STUCK_MS = 10 * 60_000L

    enum class Reason(val text: String) {
        NOT_A_GAME("futures (not a game)"),
        // The games Tj looks at ([CnoScope], 2026-10-07): checked right after "not a game", so a row outside his scope is counted under it, not under a price rule.
        LEAGUE("in a league you left out"),
        KIND("of a kind of bet you left out"),
        LIVE("in a game already under way"),
        LIQUIDITY("with fewer dollars available than your minimum"),
        TEXT("not matching your words"),
        PROPS("over your props-per-game cap"),
        MISMATCH("EV doesn't follow from its fair odds"),
        ONE_WAY("devigged from one side only"),
        BOOKS("too few books"),
        ODDS("longer odds than your cap"),
        SHORT_ODDS("shorter odds than your limit"),
        TOO_GOOD("EV over 20% (likely a stale line)"),
        EV("below your minimum EV"),
    }

    fun screen(snapshot: CnoSnapshot, filters: CnoFilters, now: Long): CnoScreened {
        val hidden = LinkedHashMap<Reason, Int>()
        val words = Words.of(filters.scope)
        val picks = snapshot.rows.mapNotNull { row ->
            val reason = reject(row, filters, now, words)
            if (reason != null) {
                hidden[reason] = (hidden[reason] ?: 0) + 1
                null
            } else {
                val live = row.startsAtMs != null && row.startsAtMs <= now
                CnoPick(row, netEv(row, live), live)
            }
        }
        // Net EV can drop a live row under the floor.
        val kept = picks.filter { it.ev >= filters.minEv - 1e-9 }
        if (kept.size < picks.size) hidden[Reason.EV] = (hidden[Reason.EV] ?: 0) + picks.size - kept.size
        val sorted = kept.sortedByDescending { it.ev }
        return CnoScreened(capProps(sorted, filters.scope.propsPerGame, hidden), hidden)
    }

    /** At most [cap] player props from one game, the best edge first ([sorted] is best first); the rest are counted under [Reason.PROPS]. 0 = no cap. */
    private fun capProps(sorted: List<CnoPick>, cap: Int, hidden: MutableMap<Reason, Int>): List<CnoPick> {
        if (cap <= 0) return sorted
        val seen = HashMap<String, Int>()
        val kept = ArrayList<CnoPick>(sorted.size)
        for (p in sorted) {
            if (com.tjshea.vigilant.data.scanner.BetKind.of(p.row.market, p.row.bet) != com.tjshea.vigilant.data.scanner.BetKind.PROP) { kept += p; continue }
            val game = "${p.row.event}|${p.row.startsAtMs}"
            val n = seen.merge(game, 1, Int::plus)!!
            if (n <= cap) kept += p else hidden[Reason.PROPS] = (hidden[Reason.PROPS] ?: 0) + 1
        }
        return kept
    }

    /** The words in the scope, parsed once for a whole screen. */
    private class Words(val include: List<String>, val exclude: List<String>) {
        val any: Boolean get() = include.isNotEmpty() || exclude.isNotEmpty()

        /** Whether [row]'s event, market and bet text keep it: some include term in it (when there are any) and no exclude term. */
        fun keeps(row: CnoRow): Boolean {
            val hay = CnoScope.fold("${row.event} ${row.market} ${row.bet}")
            return (include.isEmpty() || include.any { hay.contains(it) }) && exclude.none { hay.contains(it) }
        }

        companion object {
            fun of(scope: CnoScope) = Words(scope.terms(scope.include), scope.terms(scope.exclude))
        }
    }

    /** Why the app's own screen leaves [row] out of its list under [filters] at [now], or null when it shows it (the scan study records this beside every row). */
    fun rejection(row: CnoRow, filters: CnoFilters, now: Long): Reason? = reject(row, filters, now, Words.of(filters.scope))

    private fun reject(row: CnoRow, f: CnoFilters, now: Long, words: Words): Reason? {
        if (!Picks.isGame(row.event)) return Reason.NOT_A_GAME
        val sc = f.scope
        if (!sc.allows(row.league)) return Reason.LEAGUE
        if (sc.kinds.isNotEmpty() && !sc.allowsKind(com.tjshea.vigilant.data.scanner.BetKind.of(row.market, row.bet))) return Reason.KIND
        if (sc.hideLive && row.startsAtMs != null && row.startsAtMs <= now) return Reason.LIVE
        if (sc.minLiquidity > 0 && row.available != null && row.available < sc.minLiquidity) return Reason.LIQUIDITY
        if (words.any && !words.keeps(row)) return Reason.TEXT
        val fair = fairProbability(row)
        val started = row.startsAtMs != null && row.startsAtMs <= now
        // Live rows may carry a fee in CNO's EV; pregame Novig fills are free, so there it must add up.
        if (!started && fair != null && abs(fair * Odds.americanToDecimal(row.odds) - 1.0 - row.ev) > EV_TOLERANCE) return Reason.MISMATCH
        if (row.oneWay) return Reason.ONE_WAY
        if (row.books != null && row.books < f.minBooks) return Reason.BOOKS
        if (f.maxOdds > 0 && row.odds > f.maxOdds) return Reason.ODDS
        if (com.tjshea.vigilant.data.novig.trading.AutoBet.tooShort(f.minOdds, row.odds)) return Reason.SHORT_ODDS
        if (row.ev > MAX_EV) return Reason.TOO_GOOD
        if (row.ev < f.minEv - 1e-9) return Reason.EV
        return null
    }

    /** The row's fair probability: CNO's exact one, else from its fair odds. */
    fun fairProbability(row: CnoRow): Double? =
        row.fairProbability ?: row.fairOdds?.takeIf { it != 0 }?.let { 1.0 / Odds.americanToDecimal(it) }

    private fun netEv(row: CnoRow, live: Boolean): Double {
        // Only Novig charges the taker once a game is live; a sportsbook's price is all-in (Vigilant MGM's BetMGM rows).
        if (!live || !chargesNovigFee(row)) return row.ev
        val fair = fairProbability(row) ?: return row.ev
        return CnoBooks.evAt(fair, row.odds, live = true)
    }

    /** A row at Novig (or of unknown book, as before): its live fills pay Novig's taker fee. */
    private fun chargesNovigFee(row: CnoRow): Boolean = row.book.isBlank() || CnoBooks.codeFor(row.book) == CnoBooks.NOVIG

    /** Whether CNO has stopped updating (its data is older than [STUCK_MS]). */
    fun stuck(snapshot: CnoSnapshot, now: Long): Boolean = now - snapshot.dataAtMs > STUCK_MS
}
