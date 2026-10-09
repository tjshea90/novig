package com.tjshea.vigilant.data.novig.lab

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** How a sport scores, for the remaining-points model: [NORMAL] sports score in many small steps, the others in few big ones. */
enum class TailSport(val normal: Boolean, val fullTotalSd: Double, val fullMarginSd: Double, val dispersion: Double) {
    /** NFL / NCAAF: a full game's total has a standard deviation of about 13.5-15 points; a margin about 13.5. */
    FOOTBALL(true, 14.5, 13.5, 0.0),

    /** NBA / WNBA / college: a total's sd is about 18-20 points, a margin's about 12-13. */
    BASKETBALL(true, 19.0, 12.5, 0.0),

    /** NHL: about 6 goals a game, scored one at a time: negative binomial. */
    HOCKEY(false, 0.0, 0.0, 12.0),

    /** MLB: runs, a little more spread than Poisson. */
    BASEBALL(false, 0.0, 0.0, 9.0),

    /** Soccer: goals, Poisson-like. */
    SOCCER(false, 0.0, 0.0, 20.0),
}

/**
 * The late-game tail model (Tj, 2026-10-09; RESEARCH.md §120.6): how likely the final total (or margin) ends above a far-off strike, from the score NOW, the share of the game left and the
 * centre of the distribution. The centre is NOT a guess: it is read off the game's own liquid lines ([centre]), so the model only has to say how much the game can still move, which is a
 * standard deviation that shrinks with the square root of the time left (and, for sports that score in a few big steps, a negative binomial on the points to come). Pure; a rough model, so
 * [TailScan] only ever uses it with a safety margin on both the spread and the centre.
 */
object TailModel {
    /**
     * P(the final total ends ABOVE [strike]) given [points] scored so far, [fractionLeft] of regulation to play and the expected final total [centre]. [sdMult] widens the spread and
     * [centreShift] moves the centre (points), both for a bound rather than a best guess.
     */
    fun pTotalOver(sport: TailSport, strike: Double, points: Int, fractionLeft: Double, centre: Double, sdMult: Double = 1.0, centreShift: Double = 0.0): Double {
        val need = strike - points   // the points still to come must be MORE than this
        if (need < 0.0) return 1.0
        val mean = (centre + centreShift - points).coerceAtLeast(0.0)
        val left = fractionLeft.coerceIn(0.0, 1.0)
        val r = Math.floor(need).toInt() + 1   // the smallest whole number of points that makes the total go over
        return if (sport.normal) {
            val sd = (sport.fullTotalSd * sqrt(left) * sdMult).coerceAtLeast(0.5)
            1.0 - phi((r - 0.5 - mean) / sd)
        } else {
            tailNegBin(r, mean, sport.dispersion / sdMult.coerceAtLeast(0.2))
        }.coerceIn(0.0, 1.0)
    }

    /**
     * P(the final margin of the reference team ends ABOVE [threshold]) given its margin [margin] now and the expected final margin [centreMargin].
     */
    fun pMarginAbove(sport: TailSport, threshold: Double, margin: Int, fractionLeft: Double, centreMargin: Double, sdMult: Double = 1.0, centreShift: Double = 0.0, remainingTotal: Double = 0.0): Double {
        val left = fractionLeft.coerceIn(0.0, 1.0)
        val sd = (if (sport.normal) sport.fullMarginSd * sqrt(left) else sqrt(remainingTotal.coerceAtLeast(0.25))) * sdMult
        val mean = centreMargin + centreShift
        return (1.0 - phi((threshold - mean) / sd.coerceAtLeast(0.5))).coerceIn(0.0, 1.0)
    }

    /**
     * The threshold where the ladder's own prices cross 50%: [mids] are (threshold, P(YES) at the middle of the book), YES getting less likely as the threshold rises. Interpolates between the
     * two lines either side of 50%; null with no crossing (the ladder says nothing about where the game is centred).
     */
    fun centre(mids: List<Pair<Double, Double>>): Double? {
        val s = mids.sortedBy { it.first }
        for (i in 0 until s.size - 1) {
            val (t0, p0) = s[i]
            val (t1, p1) = s[i + 1]
            if (p0 >= 0.5 && p1 <= 0.5 && p0 > p1) return t0 + (t1 - t0) * (p0 - 0.5) / (p0 - p1)
            if (p0 == 0.5) return t0
        }
        return null
    }

    /** The standard normal CDF (Abramowitz-Stegun 26.2.17: error under 1e-7). */
    fun phi(x: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val d = 0.3989423 * exp(-x * x / 2.0)
        val p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return if (x >= 0) 1.0 - p else p
    }

    /** P(R >= [r]) for a negative binomial with mean [mean] and shape [k] (variance mean + mean^2/k). */
    private fun tailNegBin(r: Int, mean: Double, k: Double): Double {
        if (r <= 0) return 1.0
        if (mean <= 0.0) return 0.0
        var p = exp(k * (ln(k) - ln(k + mean)))   // P(R = 0)
        var cdf = p
        for (n in 0 until r - 1) {
            p *= (n + k) / (n + 1.0) * (mean / (k + mean))
            cdf += p
        }
        return (1.0 - cdf).coerceIn(0.0, 1.0)
    }
}
