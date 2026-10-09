package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.CoverMath
import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.FairValue
import com.tjshea.vigilant.engine.Fees

/**
 * One outside book's two-sided price at one alternate line, in Novig's own convention so it can be laid against a Novig line: a TOTAL's [threshold] is the strike and YES is the Over; a MARGIN line's
 * [refSide] is the side whose margin is measured, [threshold] is Novig's (a spread of X -k is k, X +k is -k) and YES is "that team covers". [seenAtMs] is when the feed last saw the price (null: unknown).
 */
data class AltQuote(
    val bookKey: String,
    val bookTitle: String,
    val kind: LadderKind,
    /** A MARGIN line: "HOME" or "AWAY", the side whose margin is measured (Novig's name for it is resolved by the caller: Novig says "TB", a book says "Tampa Bay"); "" for a total. */
    val refSide: String,
    val threshold: Double,
    val yesDecimal: Double,
    val noDecimal: Double,
    val seenAtMs: Long?,
)

/** The alternate-line scan's rules. [minBooks] 2 is the default because one book's alternates did NOT hold up against Novig in the first tape (RESEARCH.md §120.6); 1 is for the study of exactly that. */
data class AltRules(
    val minEdge: Double = 0.03,
    val minBooks: Int = 2,
    /** The most the books' own fair lines may disagree about the strike, in probability points: wider and the strike is not really priced. */
    val maxDisagreement: Double = 0.05,
    val minContracts: Long = 100L,
    val maxAsk: Double = 0.97,
)

data class AltCandidate(
    val marketId: String,
    val eventId: String,
    val outcomeId: String,
    val label: String,
    val side: String,
    val strike: Double,
    val ask: Double,
    val fair: Double,
    val edge: Double,
    val contracts: Long,
    val books: Int,
    val oldestAgeSec: Int,
)

/**
 * The alternate-line scan (Tj, 2026-10-09: "the app should analyze real time alternate odds at other books vs alternate odds live on novig such as alternate spreads and totals"; RESEARCH.md §120.6).
 * For each Novig alternate strike, the fresh outside quotes AT THE SAME strike are each de-vigged on their own ([FairValue]: both sides required, the worst case of the devig methods, the lower of
 * the mean and the median across three or more books), the fair must come from at least [AltRules.minBooks] books that agree within [AltRules.maxDisagreement], and the Novig ask (plus the in-play fee)
 * must be under it by [AltRules.minEdge]. Pure; the quotes come from whichever feeds the caller has.
 */
/** One Novig strike with the outside books' fair for it: [yes]/[no] are the two sides' fair probabilities, [books] how many fresh two-sided books made them. */
data class StrikeFair(val point: LadderPoint, val yes: Double, val no: Double, val books: Int, val oldestAgeSec: Int)

object AltLineScan {
    /** The fair of every Novig strike that has at least [AltRules.minBooks] fresh two-sided outside books at the SAME strike, agreeing within [AltRules.maxDisagreement]. */
    fun strikeFairs(points: List<LadderPoint>, quotes: List<AltQuote>, now: Long, startsAtMs: Long?, rules: AltRules = AltRules(), sideOf: (String) -> String? = { null }): List<StrikeFair> {
        val out = ArrayList<StrikeFair>()
        for (p in points) {
            val line = p.line
            val matching = quotes.filter { q ->
                q.kind == line.kind && Math.abs(q.threshold - line.threshold) < 1e-9 &&
                    (line.kind == LadderKind.TOTAL || (q.refSide.isNotEmpty() && q.refSide == sideOf(line.ref))) &&
                    q.yesDecimal > 1.0 && q.noDecimal > 1.0 && Freshness.fresh(q.seenAtMs, now, startsAtMs)
            }.distinctBy { it.bookKey }
            if (matching.size < rules.minBooks) continue
            val fair = FairValue.compute(
                matching.map { BookPrices(it.bookKey, it.bookTitle, listOf(it.yesDecimal, it.noDecimal), it.seenAtMs) },
                FairSettings(source = FairSource.MARKET_AVERAGE, minBooks = rules.minBooks, outlierGuard = true),
            ) ?: continue
            val perBook = fair.perBook.map { it.fairProbabilities[0] }
            if (perBook.isEmpty() || perBook.max() - perBook.min() > rules.maxDisagreement) continue
            val oldest = matching.mapNotNull { it.seenAtMs }.minOrNull()?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0
            out += StrikeFair(p, fair.probabilities[0], fair.probabilities[1], matching.size, oldest)
        }
        return out
    }

    /** [sideOf] says whether a margin ladder's reference team (Novig's name for it) is the HOME or the AWAY side; null skips that ladder. */
    fun scan(points: List<LadderPoint>, quotes: List<AltQuote>, now: Long, startsAtMs: Long?, live: Boolean, rules: AltRules = AltRules(), sideOf: (String) -> String? = { null }): List<AltCandidate> {
        val out = ArrayList<AltCandidate>()
        for (sf in strikeFairs(points, quotes, now, startsAtMs, rules, sideOf)) {
            val p = sf.point
            val line = p.line
            for (yes in booleanArrayOf(true, false)) {
                val leg = CoverMath.leg(p.book, line, yes) ?: continue
                if (leg.contracts < rules.minContracts || leg.price > rules.maxAsk) continue
                val pWin = if (yes) sf.yes else sf.no
                val fee = Fees.takerFee(leg.price, line.fee, eventLive = live)
                val edge = pWin / (leg.price + fee) - 1.0
                if (edge < rules.minEdge) continue
                val total = line.kind == LadderKind.TOTAL
                out += AltCandidate(
                    line.marketId, line.eventId, if (yes) line.yesOutcomeId else line.noOutcomeId, line.label,
                    if (total) (if (yes) "OVER" else "UNDER") else (if (yes) "YES" else "NO"),
                    line.threshold, leg.price, pWin, edge, leg.contracts, sf.books, sf.oldestAgeSec,
                )
            }
        }
        return out.sortedByDescending { it.edge }
    }
}
