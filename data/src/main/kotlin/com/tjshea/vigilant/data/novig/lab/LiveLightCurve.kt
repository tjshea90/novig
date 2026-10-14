package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.CoverMath
import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.engine.Fees
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A ladder's liquid prices fitted to a normal curve (Tj, 2026-10-14: "a smart math system ... to calculate bad odds in real time"). A game's final margin or total is close to normal, so the chance a strike
 * ends YES is 1 - Φ((strike - μ) / σ) and Φ⁻¹ of the ladder's own middle prices is a straight line in the strike: probit regression. [mu] is where the ladder says the game is centred, [sigma] how wide, [rms] the
 * fit's error in probability (a ladder whose prices do not lie on a curve is not fitted at all).
 */
data class LadderFit(val mu: Double, val sigma: Double, val rms: Double, val n: Int, val minT: Double, val maxT: Double) {
    /** P(YES) at [t] from the curve, the centre moved [shiftSd] sigmas against the bet and the width multiplied by [sdMult]. */
    fun pYes(t: Double, yes: Boolean, shiftSd: Double, sdMult: Double): Double {
        val m = mu + if (yes) -shiftSd * sigma else shiftSd * sigma
        val above = 1.0 - TailModel.phi((t - m) / (sigma * sdMult).coerceAtLeast(1e-6))
        return if (yes) above else 1.0 - above
    }

    companion object {
        /** The fit of (strike, P(YES)) middles: only prices between 3% and 97% count (the tails are where a probit is noise); null with fewer than [MIN_POINTS] such, a curve that rises, or a bad fit. */
        fun of(mids: List<Pair<Double, Double>>, maxRms: Double = MAX_RMS): LadderFit? {
            val pts = mids.filter { it.second in 0.03..0.97 }.sortedBy { it.first }
            if (pts.size < MIN_POINTS || pts.map { it.first }.distinct().size < MIN_POINTS) return null
            val xs = pts.map { it.first }
            val zs = pts.map { probit(it.second) }
            val n = pts.size
            val mx = xs.average()
            val mz = zs.average()
            var sxx = 0.0
            var sxz = 0.0
            for (i in 0 until n) { sxx += (xs[i] - mx) * (xs[i] - mx); sxz += (xs[i] - mx) * (zs[i] - mz) }
            if (sxx <= 1e-12) return null
            val b = sxz / sxx
            if (b >= -1e-9) return null   // YES must get less likely as the strike rises
            val a = mz - b * mx
            val sigma = -1.0 / b
            val mu = a * sigma
            var se = 0.0
            for (i in 0 until n) { val p = 1.0 - TailModel.phi((xs[i] - mu) / sigma); se += (p - pts[i].second) * (p - pts[i].second) }
            val rms = sqrt(se / n)
            if (rms > maxRms || !(sigma > 0.0) || mu.isNaN()) return null
            return LadderFit(mu, sigma, rms, n, xs.first(), xs.last())
        }

        const val MIN_POINTS = 4
        const val MAX_RMS = 0.04

        /** Φ⁻¹ (Acklam's rational approximation, error under 1.2e-9). */
        fun probit(p: Double): Double {
            val q = p.coerceIn(1e-9, 1 - 1e-9)
            val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
            val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
            val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
            val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
            return when {
                q < 0.02425 -> { val r = sqrt(-2 * ln(q)); (((((c[0] * r + c[1]) * r + c[2]) * r + c[3]) * r + c[4]) * r + c[5]) / ((((d[0] * r + d[1]) * r + d[2]) * r + d[3]) * r + 1) }
                q > 1 - 0.02425 -> { val r = sqrt(-2 * ln(1 - q)); -(((((c[0] * r + c[1]) * r + c[2]) * r + c[3]) * r + c[4]) * r + c[5]) / ((((d[0] * r + d[1]) * r + d[2]) * r + d[3]) * r + 1) }
                else -> { val r = (q - 0.5) * (q - 0.5); val s = q - 0.5; (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * s / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1) }
            }
        }
    }
}

/** How the curve check is held to account. Every number errs against a bet. */
data class LiveLightCurveRules(
    /** An alternate line is judged only when this many OTHER liquid lines of its ladder lie on one curve. */
    val minOthers: Int = 4,
    val shiftSd: Double = 0.25,
    val sdMult: Double = 1.25,
    val minFair: Double = 0.55,
    /** The score model's own chance for the side under which a curve bet is refused (see [LiveLightCurve.scan]). */
    val vetoBelow: Double = 0.02,
    /** Higher than the score model's: a curve says a price is out of line with its neighbours, not that the game is decided. */
    val minEdge: Double = 0.06,
    val maxAsk: Double = 0.985,
    val minContracts: Long = 20L,
    /** A strike this many sigmas outside the liquid lines is an extrapolation, not an interpolation: left alone. */
    val reachSd: Double = 0.5,
    /** The fitted sigma must be within this factor of the one the game's sport and time left allow. */
    val sigmaBand: Double = 2.5,
    /** A line counts as liquid (it says where the game is centred) when its two sides are within this of each other. */
    val maxSpread: Double = 0.06,
    val name: String = LiveLightCurve.RULE,
)

/**
 * Alternate spreads and totals priced off their own ladder (Tj, 2026-10-14): each ladder's liquid middles are fitted ([LadderFit]) with the line under test LEFT OUT, and a side Novig sells under what the curve
 * (cautiously) says is a bet. The score model, when it can judge the same side, vetoes a bet it rates almost impossible (a ladder that disagrees that much with the score is the stale one). Pure.
 */
object LiveLightCurve {
    const val RULE = "curve"

    /** The sigma the game's sport and time left allow for a ladder of [kind] (margin or total): the model's own, used only to refuse a fit that is wildly off it. */
    fun expectedSigma(kind: LadderKind, league: String, state: GameState): Double? {
        val prior = LiveLightScan.priorTotal(league) ?: return null
        val left = state.fractionLeft
        return if (state.sport.normal) (if (kind == LadderKind.TOTAL) state.sport.fullTotalSd else state.sport.fullMarginSd) * sqrt(left)
        else sqrt((prior * left * 1.2).coerceAtLeast(0.25))
    }

    fun scan(points: List<LadderPoint>, league: String, state: GameState, scoreFair: Map<String, Double> = emptyMap(), rules: LiveLightCurveRules = LiveLightCurveRules()): List<TailCandidate> {
        val out = ArrayList<TailCandidate>()
        for ((_, g) in points.groupBy { it.line.ladderKey }) {
            val kind = g.first().line.kind
            val expected = expectedSigma(kind, league, state) ?: continue
            val liquid = g.mapNotNull { p -> mid(p, rules)?.let { p to it } }
            for (p in g) {
                val others = liquid.filter { it.first.line.marketId != p.line.marketId }.map { it.first.line.threshold to it.second }
                if (others.size < rules.minOthers) continue
                val fit = LadderFit.of(others) ?: continue
                if (fit.sigma < expected / rules.sigmaBand || fit.sigma > expected * rules.sigmaBand) continue
                val t = p.line.threshold
                if (t < fit.minT - rules.reachSd * fit.sigma || t > fit.maxT + rules.reachSd * fit.sigma) continue
                for (yes in booleanArrayOf(true, false)) {
                    val leg = CoverMath.leg(p.book, p.line, yes) ?: continue
                    if (leg.contracts < rules.minContracts || leg.price > rules.maxAsk) continue
                    val fair = fit.pYes(t, yes, rules.shiftSd, rules.sdMult)
                    val outcome = if (yes) p.line.yesOutcomeId else p.line.noOutcomeId
                    // The score model is a veto, not a cap: its bounds are deliberately pessimistic, so only a side it rates almost impossible while the ladder says it is likely means the ladder is stale or mismatched.
                    scoreFair[outcome]?.let { if (it < rules.vetoBelow) continue }
                    if (fair < rules.minFair) continue
                    val fee = Fees.takerFee(leg.price, p.line.fee, eventLive = true)
                    val edge = fair / (leg.price + fee) - 1.0
                    if (edge < rules.minEdge) continue
                    val total = p.line.kind == LadderKind.TOTAL
                    out += TailCandidate(
                        rules.name, p.line.marketId, p.line.eventId, outcome, p.line.label,
                        if (total) (if (yes) "OVER" else "UNDER") else (if (yes) "YES" else "NO"), t, leg.price, fair, edge, leg.contracts, fit.mu, state.fractionLeft,
                    )
                }
            }
        }
        return out.sortedByDescending { it.edge }
    }

    private fun mid(p: LadderPoint, rules: LiveLightCurveRules): Double? {
        val askYes = CoverMath.leg(p.book, p.line, yes = true) ?: return null
        val askNo = CoverMath.leg(p.book, p.line, yes = false) ?: return null
        val bidYes = 1.0 - askNo.price
        if (askYes.price - bidYes > rules.maxSpread || abs(askYes.price - bidYes) > 1.0) return null
        return (askYes.price + bidYes) / 2.0
    }
}
