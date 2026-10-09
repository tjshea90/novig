package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.CoverMath
import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.engine.Fees

/** A live game as the tail scanner needs it: the sport, the score now and the share of regulation still to play (0-1). */
data class GameState(val sport: TailSport, val homeScore: Int, val awayScore: Int, val fractionLeft: Double) {
    val points: Int get() = homeScore + awayScore
}

/**
 * The tail scanner's rules (paper only; Tj, 2026-10-09). It looks for far-off strikes that the game has all but decided and that someone is still offering under their fair price (the
 * favourite-longshot bias on alternate lines: a retail bid on a 10-cent Over is the Under at 90). Every number is meant to err against a bet: the spread is widened ([sdMult]), the centre
 * moved against the bet ([centreShift], points) and the fair price must still be [minFair] and beat the ask plus fee by [minEdge] after both.
 */
data class TailRules(
    val minEdge: Double = 0.03,
    val minFair: Double = 0.90,
    val minFractionLeft: Double = 0.03,
    val maxFractionLeft: Double = 0.65,
    val minContracts: Long = 100L,
    val sdMult: Double = 1.25,
    val centreShift: Double = 1.5,
    /** A strike is "liquid" (it says where the game is centred) when its two sides are within this of each other. */
    val maxSpread: Double = 0.06,
    val maxAsk: Double = 0.985,
)

/** One would-be bet: [side] is "UNDER"/"OVER" for a total, "YES"/"NO" for a margin line; [fair] is the conservative probability the bet wins. */
data class TailCandidate(
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
    val centre: Double,
    val fractionLeft: Double,
)

object TailScan {
    /**
     * Candidates on the totals and margin ladders in [points] for a game in [state]. [marginOf] gives a margin ladder's reference team's lead now (positive = ahead) or null when the team can't
     * be matched to the score (that ladder is skipped). Totals need a centre from the ladder's own liquid lines and an expected final total no lower than the points already scored.
     */
    fun scan(points: List<LadderPoint>, state: GameState, marginOf: (String) -> Int?, rules: TailRules = TailRules()): List<TailCandidate> {
        if (state.fractionLeft < rules.minFractionLeft || state.fractionLeft > rules.maxFractionLeft) return emptyList()
        val out = ArrayList<TailCandidate>()
        val byLadder = points.groupBy { it.line.ladderKey }
        var totalCentre: Double? = null
        for ((_, g) in byLadder) {
            val first = g.first().line
            val centre = TailModel.centre(mids(g, rules)) ?: continue
            if (first.kind == LadderKind.TOTAL) {
                if (centre < state.points - 3.0) continue   // a centre under what is already on the board is a clock or a score that is wrong: never trade it
                totalCentre = centre
                for (p in g) out += candidates(p, rules, state, centre) { yes, shift ->
                    TailModel.pTotalOver(state.sport, p.line.threshold, state.points, state.fractionLeft, centre, rules.sdMult, if (yes) -shift else shift)
                }
            }
        }
        val remaining = ((totalCentre ?: 0.0) - state.points).coerceAtLeast(0.0)
        for ((_, g) in byLadder) {
            val first = g.first().line
            if (first.kind != LadderKind.MARGIN) continue
            val margin = marginOf(first.ref) ?: continue
            val centre = TailModel.centre(mids(g, rules)) ?: continue
            if (!state.sport.normal && totalCentre == null) continue   // a few-big-steps sport needs the points still to come for its spread
            for (p in g) out += candidates(p, rules, state, centre) { yes, shift ->
                TailModel.pMarginAbove(state.sport, p.line.threshold, margin, state.fractionLeft, centre, rules.sdMult, if (yes) -shift else shift, remaining)
            }
        }
        return out.sortedByDescending { it.edge }
    }

    /** (threshold, mid P(YES)) of the lines whose two sides are close together: the ones that say where the game is centred. */
    private fun mids(g: List<LadderPoint>, rules: TailRules): List<Pair<Double, Double>> = g.mapNotNull { p ->
        val askYes = CoverMath.leg(p.book, p.line, yes = true) ?: return@mapNotNull null
        val askNo = CoverMath.leg(p.book, p.line, yes = false) ?: return@mapNotNull null
        val bidYes = 1.0 - askNo.price
        if (askYes.price - bidYes > rules.maxSpread) return@mapNotNull null
        p.line.threshold to (askYes.price + bidYes) / 2.0
    }

    /** Both sides of one line, each judged on [pYes] (the probability YES wins, given a direction to err in: true = the bound for a YES bet, false = for a NO bet). */
    private fun candidates(p: LadderPoint, rules: TailRules, state: GameState, centre: Double, pYes: (Boolean, Double) -> Double): List<TailCandidate> {
        val out = ArrayList<TailCandidate>()
        for (yes in booleanArrayOf(true, false)) {
            val leg = CoverMath.leg(p.book, p.line, yes) ?: continue
            if (leg.contracts < rules.minContracts || leg.price > rules.maxAsk) continue
            val pWin = if (yes) pYes(true, rules.centreShift) else 1.0 - pYes(false, rules.centreShift)
            if (pWin < rules.minFair) continue
            val fee = Fees.takerFee(leg.price, p.line.fee, eventLive = true)
            val edge = pWin / (leg.price + fee) - 1.0
            if (edge < rules.minEdge) continue
            val total = p.line.kind == LadderKind.TOTAL
            out += TailCandidate(
                p.line.marketId, p.line.eventId, if (yes) p.line.yesOutcomeId else p.line.noOutcomeId, p.line.label,
                if (total) (if (yes) "OVER" else "UNDER") else (if (yes) "YES" else "NO"),
                p.line.threshold, leg.price, pWin, edge, leg.contracts, centre, state.fractionLeft,
            )
        }
        return out
    }
}
