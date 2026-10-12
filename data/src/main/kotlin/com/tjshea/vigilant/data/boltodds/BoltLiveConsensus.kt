package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.engine.BookCompany
import com.tjshea.vigilant.engine.Devig
import com.tjshea.vigilant.engine.DevigMethod
import kotlin.math.abs

/**
 * The live fair from the soft books BoltOdds pushes (Tj, 2026-10-12: "a live betting engine based on an average of the soft sports books offered by the boltodds socket"). Pure.
 *
 * BoltOdds carries no sharp book (v0.94.1's file: none of its 34 books is Pinnacle, Circa or an exchange), so a live fair can only be a CONSENSUS, and a consensus of soft books trails the truth by seconds. The rules are
 * what keeps that honest, all of them applied to every line every time:
 *  - only books that pushed about THIS game within [Rules.maxAgeMs] vote (a book that went quiet, or suspended, is out, not carried);
 *  - one vote per COMPANY ([BookCompany]: bwin, Party Sports and BetMGM are one desk), each book devigged on its own (the method the engine uses);
 *  - at least [Rules.minCompanies] companies must vote, and they must agree: the highest and lowest fair for a side within [Rules.maxSpread], else there is no line (a game in the middle of repricing disagrees);
 *  - the fair is the TRIMMED mean (the highest and lowest dropped once five or more vote), so one stale or suspended book cannot drag it;
 *  - a spread or total is only the line the most companies quote (the main line); a line that loses its quorum is CLOSED, never left standing.
 */
object BoltLiveConsensus {

    data class Rules(
        val minCompanies: Int = 4,
        val maxAgeMs: Long = 10_000L,
        val maxSpread: Double = 0.05,
        val method: DevigMethod = DevigMethod.WORST_CASE,
    )

    /** One consensus line: [fair] is each side's probability (sums to 1), [points] the HOME handicap of a spread or the line of a total. */
    data class Line(
        val type: PinnLineType,
        val points: Double?,
        val fair: Map<PinnSide, Double>,
        val voters: Int,
        /** The widest disagreement between the companies on the first side. */
        val spread: Double,
        val books: List<String>,
    ) {
        /** Stable id of the line inside its game (a spread or total moves with its number). */
        val key: String get() = when (type) {
            PinnLineType.MONEYLINE -> "s;0;m"
            PinnLineType.SPREAD -> "s;0;s;${points}"
            PinnLineType.TOTAL -> "s;0;ou;${points}"
        }
    }

    /** One live game: its teams, start, the league's Pinnodds sport id and its consensus lines. */
    data class Game(val id: String, val league: String, val sportId: Int, val home: String, val away: String, val startMs: Long, val lines: List<Line>)

    /**
     * Every live game on [games] (BoltOdds' board entries, one per book and game) as consensus lines. [games] are filtered to those flagged live and pushed within the age window before they are read.
     */
    fun build(games: List<BoltBookGame>, nowMs: Long, rules: Rules = Rules()): List<Game> {
        val fresh = games.filter { it.live && nowMs - it.updatedAtMs <= rules.maxAgeMs }
        val out = ArrayList<Game>()
        for (league in fresh.mapNotNull { BoltBooks.leagueOfSport(it.sport) }.distinct()) {
            val lg = Leagues.byNovigName(league) ?: continue
            val ctx = BoltContext(
                league = lg, wanted = null, families = com.tjshea.vigilant.data.scanner.MarketFamily.entries.toSet(), includeLive = true, nowMs = nowMs,
                aliveAt = { _, _ -> null }, liveOnly = true,
            )
            for (e in BoltConvert.events(fresh, ctx)) {
                val lines = linesOf(e, rules)
                if (lines.isNotEmpty()) out += Game(e.id, lg.novigName, lg.pinnacleSportId ?: 0, e.home, e.away, e.commenceMs, lines)
            }
        }
        return out
    }

    /** The consensus lines of one converted game (its markets are only the fresh books'). */
    fun linesOf(e: RefEvent, rules: Rules): List<Line> {
        val main = e.markets.filter { it.period == 0 && it.kind in setOf(LineKind.MONEYLINE, LineKind.SPREAD, LineKind.TOTAL) }
        val out = ArrayList<Line>()
        for (kind in listOf(LineKind.MONEYLINE, LineKind.SPREAD, LineKind.TOTAL)) {
            // One vote per company: the first of its books.
            val votes = main.filter { it.kind == kind }.groupBy { BookCompany.of(it.bookKey) }.values.map { it.first() }
            if (kind == LineKind.MONEYLINE) consensus(votes, PinnLineType.MONEYLINE, null, kind, rules)?.let { out += it }
            else {
                // The number quoted by the most companies (a tie: the one whose prices are closest to even).
                val byNumber = votes.groupBy { v -> v.quotes.firstOrNull { it.side == (if (kind == LineKind.SPREAD) Side.HOME else Side.OVER) }?.point }.filterKeys { it != null }
                val best = byNumber.entries.maxWithOrNull(compareBy<Map.Entry<Double?, List<com.tjshea.vigilant.data.reference.RefBookMarket>>> { it.value.size }.thenBy { -abs(evenness(it.value, rules)) }) ?: continue
                consensus(best.value, if (kind == LineKind.SPREAD) PinnLineType.SPREAD else PinnLineType.TOTAL, best.key, kind, rules)?.let { out += it }
            }
        }
        return out
    }

    private fun evenness(votes: List<com.tjshea.vigilant.data.reference.RefBookMarket>, rules: Rules): Double =
        votes.mapNotNull { fairs(it, rules)?.first() }.average().let { if (it.isNaN()) 1.0 else it - 0.5 }

    /** A book's devigged fairs for its two sides in quote order, or null when its prices cannot be read. */
    private fun fairs(m: com.tjshea.vigilant.data.reference.RefBookMarket, rules: Rules): List<Double>? {
        if (m.quotes.size != 2) return null
        val raw = m.quotes.map { 1.0 / it.decimalOdds }
        if (raw.any { !(it > 0.0 && it < 1.0) }) return null
        return runCatching { Devig.devig(raw, rules.method) }.getOrNull()
    }

    private fun consensus(votes: List<com.tjshea.vigilant.data.reference.RefBookMarket>, type: PinnLineType, points: Double?, kind: LineKind, rules: Rules): Line? {
        if (votes.size < rules.minCompanies) return null
        val first = if (type == PinnLineType.TOTAL) Side.OVER else Side.HOME
        val firstFairs = votes.mapNotNull { m -> fairs(m, rules)?.let { f -> m.quotes.indexOfFirst { it.side == first }.takeIf { it >= 0 }?.let { f[it] } } }
        if (firstFairs.size < rules.minCompanies) return null
        val sorted = firstFairs.sorted()
        val spread = sorted.last() - sorted.first()
        if (spread > rules.maxSpread) return null
        val used = if (sorted.size >= 5) sorted.drop(1).dropLast(1) else sorted
        val p = used.average()
        if (!(p > 0.0 && p < 1.0)) return null
        val sides = if (type == PinnLineType.TOTAL) PinnSide.OVER to PinnSide.UNDER else PinnSide.HOME to PinnSide.AWAY
        return Line(type, points, mapOf(sides.first to p, sides.second to 1.0 - p), firstFairs.size, spread, votes.map { it.bookKey })
    }

    /** American odds (two decimals) for a zero-vig probability, the form a Pinnodds frame carries. */
    fun american(p: Double): Double {
        val q = p.coerceIn(0.001, 0.999)
        val a = if (q >= 0.5) -(q / (1.0 - q)) * 100.0 else ((1.0 - q) / q) * 100.0
        return Math.round(a * 100.0) / 100.0
    }
}
