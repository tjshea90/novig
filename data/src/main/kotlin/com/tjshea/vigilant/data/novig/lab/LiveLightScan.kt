package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.CoverMath
import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.engine.Fees
import kotlin.math.sqrt

/**
 * What "Live light" will call obviously wrong (Tj, 2026-10-14). Every number errs against a bet: the spread is widened, the expected final score is moved against the bet, and the bet must still be a
 * decided game ([minFair]) at a price that beats it by [minEdge] after Novig's live fee. Unlike [TailRules] it needs nothing from Novig's own prices to find where the game is centred, so it sees a
 * stale price even when the whole ladder is stale (a blowout offered at 50/50).
 */
data class LiveLightRules(
    val sdMult: Double = 1.3,
    /** Points (goals, runs) the expected final margin or total is moved against the bet. */
    val shiftPoints: Double = 1.5,
    val minFair: Double = 0.90,
    val minEdge: Double = 0.025,
    /** Too little time left to bet is a game that is over (Novig is about to settle it); too much is a game that has barely started. */
    val minFractionLeft: Double = 0.02,
    val maxFractionLeft: Double = 0.75,
    val maxAsk: Double = 0.985,
    val minContracts: Long = 20L,
    /** A total's remaining points are bounded from below and above by these shares of the league's usual scoring for the time left. */
    val lowShare: Double = 0.7,
    val highShare: Double = 1.3,
    val name: String = LiveLightScan.RULE,
)

/**
 * The score-and-clock model behind Live light (pure). A team's lead is a martingale to a good approximation (the expected final margin is the margin now), so the chance a side covers a number is a normal
 * (or, for the few-big-steps sports, a score-count) around the score NOW, with a standard deviation that shrinks with the square root of the time left. The expected remaining points of a total are bounded
 * by the league's usual scoring ([priorTotal]) rather than guessed from the pace. Pushes are never traded (a whole-number line is skipped, except a moneyline, whose tie credit is left out of both sides).
 */
object LiveLightScan {
    const val RULE = "light"

    /** The middle tier (Tj, 2026-10-19: "tweak the minimum percentages for more fills ... real money just at 10 cent stakes"): sides the model calls [TailTradeLimits.MIN_FAIR_MID] to 90% sure. */
    const val MID = "light-mid"

    /** A league's usual full-game total (points, goals, runs): only ever used as a wide bound on what is still to come. */
    fun priorTotal(league: String): Double? = when (league) {
        "NFL" -> 45.0
        "NCAAF" -> 55.0
        "NBA" -> 226.0
        "WNBA" -> 165.0
        "NCAAB" -> 140.0
        "NHL" -> 6.0
        "MLB" -> 8.8
        else -> null
    }

    /** P(the reference team's final margin ends above [threshold]) bounded by moving the expected margin [shift] points against the bet it backs. */
    private fun pMargin(sport: TailSport, league: String, threshold: Double, margin: Int, left: Double, rules: LiveLightRules, yes: Boolean): Double? {
        val prior = priorTotal(league) ?: return null
        val sd = (if (sport.normal) sport.fullMarginSd * sqrt(left) else sqrt((prior * left * rules.highShare).coerceAtLeast(0.25))) * rules.sdMult
        val shift = rules.shiftPoints * (if (sport.normal) 1.0 else 0.5)   // a goal or a run is worth far more than a point
        val mean = margin + if (yes) -shift else shift
        // A moneyline's YES wins with a lead of 1 or more, its NO with a deficit of 1 or more: half a point either side, the tie's credit left out.
        val t = if (threshold == 0.0) (if (yes) 0.5 else -0.5) else threshold
        val above = 1.0 - TailModel.phi((t - mean) / sd.coerceAtLeast(0.5))
        return if (yes) above else 1.0 - above
    }

    /** P(the final total ends above [strike]) bounded against the bet: the points still to come at the low end of what the league scores for an Over, the high end for an Under. */
    private fun pOver(sport: TailSport, league: String, strike: Double, points: Int, left: Double, rules: LiveLightRules, over: Boolean): Double? {
        val prior = priorTotal(league) ?: return null
        val remaining = prior * left
        val elapsed = 1.0 - left
        val pace = if (elapsed >= 0.2 && points > 0) points / elapsed * left else null
        val lowRem = remaining * rules.lowShare
        val highRem = maxOf(remaining * rules.highShare, (pace ?: 0.0) * 1.2)
        val centre = points + if (over) lowRem else highRem
        val moved = rules.shiftPoints * (if (sport.normal) 1.0 else 0.5)
        val shift = if (over) -moved else moved
        val pOver = TailModel.pTotalOver(sport, strike, points, left, centre, rules.sdMult, shift)
        return if (over) pOver else 1.0 - pOver
    }

    /** One side of one line with the model's conservative chance it wins and what Novig asks for it ([leg]); null when nothing can be bought there. */
    data class SideFair(val line: com.tjshea.vigilant.data.novig.burst.LadderLine, val yes: Boolean, val fair: Double, val ask: Double, val contracts: Long, val book: com.tjshea.vigilant.data.novig.NovigBook?) {
        val outcomeId: String get() = if (yes) line.yesOutcomeId else line.noOutcomeId
        val side: String get() = if (line.kind == LadderKind.TOTAL) (if (yes) "OVER" else "UNDER") else (if (yes) "YES" else "NO")
    }

    /**
     * The model's fair for every side of every line in [points] that it can judge (a whole-number line, a team that can't be matched, a game with no usable clock are left out). Bets and bids are both read off this:
     * a bet needs the side to be decided and cheaper than its fair by the edge; a bid needs it decided and sits well under it.
     */
    fun sides(points: List<LadderPoint>, league: String, state: GameState, marginOf: (String) -> Int?, rules: LiveLightRules = LiveLightRules()): List<SideFair> {
        if (state.fractionLeft < rules.minFractionLeft || state.fractionLeft > rules.maxFractionLeft) return emptyList()
        val out = ArrayList<SideFair>()
        for (p in points) {
            val line = p.line
            val margin = if (line.kind == LadderKind.MARGIN) (marginOf(line.ref) ?: continue) else 0
            // Pushes: a whole-number line (other than a moneyline) is left alone.
            if (!(line.kind == LadderKind.MARGIN && line.threshold == 0.0) && line.threshold % 1.0 == 0.0) continue
            for (yes in booleanArrayOf(true, false)) {
                val pWin = (if (line.kind == LadderKind.TOTAL) pOver(state.sport, league, line.threshold, state.points, state.fractionLeft, rules, over = yes)
                else pMargin(state.sport, league, line.threshold, margin, state.fractionLeft, rules, yes)) ?: continue
                val leg = CoverMath.leg(p.book, line, yes)
                out += SideFair(line, yes, pWin, leg?.price ?: Double.NaN, leg?.contracts ?: 0L, p.book)
            }
        }
        return out
    }

    /**
     * The bets in [points] the score and clock call decided while Novig still offers them cheaper than that. [marginOf] gives a margin ladder's reference team's lead now (null = the team can't be matched to
     * the score: that ladder is skipped, never guessed).
     */
    fun scan(points: List<LadderPoint>, league: String, state: GameState, marginOf: (String) -> Int?, rules: LiveLightRules = LiveLightRules()): List<TailCandidate> {
        val out = ArrayList<TailCandidate>()
        for (sf in sides(points, league, state, marginOf, rules)) {
            if (sf.ask.isNaN() || sf.contracts < rules.minContracts || sf.ask > rules.maxAsk || sf.fair < rules.minFair) continue
            val fee = Fees.takerFee(sf.ask, sf.line.fee, eventLive = true)
            val edge = sf.fair / (sf.ask + fee) - 1.0
            if (edge < rules.minEdge) continue
            out += TailCandidate(rules.name, sf.line.marketId, sf.line.eventId, sf.outcomeId, sf.line.label, sf.side, sf.line.threshold, sf.ask, sf.fair, edge, sf.contracts, 0.0, state.fractionLeft)
        }
        return out.sortedByDescending { it.edge }
    }
}
