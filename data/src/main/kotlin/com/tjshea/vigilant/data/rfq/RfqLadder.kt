package com.tjshea.vigilant.data.rfq

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Prices a player-prop rung the books do not post from the rungs of the SAME player and stat they do post (MK11, Tj 2026-11-19: "paying for apis and the system can't even price legs"). Novig lists a player's yards at
 * 10.5, 17.5, 24.5, 29.5 ...; the books post the main number and a few rungs, so most rungs had no fair line (journal 2026-11-19: 40% of parlay requests). A rung BETWEEN two posted ones is interpolated linearly in
 * probit space (the normal quantile of the over probability against the line), which keeps it monotone and smooth. The pricer then treats the leg as any prop leg (premium per prop leg, at most two).
 *
 * A rung OUTSIDE the posted range (the books post a player's points at 25.5 and 26.5, Novig lists 14.5 to 34.5: 77% of NBA prop legs in a 25-minute journal of 2026-11-19 had no fair line for that reason alone) is
 * EXTRAPOLATED from the posted rung nearest even ([extrapolate], MK21, research/ladder_extrapolation_2026-11-20.md): over probability at K = cdf(probit(p at L) + beta x (L - K) / sqrt(L)), the slope beta fitted per stat
 * on Novig's own closing ladders of 2026-11-15..18 (NBA 8 stats, NHL shots, NFL yards and receptions) and checked on held-out games: a median error of 2.4 to 3.7 probability points, the relative error of a side
 * between 25% and 90% 5-12% (standard deviation), 29-69% under 25%: so only a side the model puts at 12% to 92% is priced, and the leg carries the prop premium like every prop leg (20%, above the 14% the model
 * under-states a 10-25% side by at the 10th percentile).
 */
object RfqLadder {
    /** One Novig rung we may be asked to price: which player-and-stat [group] it belongs to, its line and which side the outcome is. */
    data class Target(val group: String, val strike: Double, val over: Boolean, val eventId: String, val marketType: String, val startsMs: Long)

    /** One posted rung: the books' fair probability of OVER [strike], the companies behind it and its age. */
    data class Rung(val strike: Double, val overProb: Double, val books: Int, val asOfMs: Long)

    /** The player-and-stat a rung belongs to: event, market type and the description with the line taken out ("George Kittle 53.5 RECEIVING_YARDS" -> "George Kittle  RECEIVING_YARDS"). */
    fun group(eventId: String, marketType: String, description: String, strike: Double): String =
        "$eventId|$marketType|" + description.replaceFirst(strikeText(strike), " ").replace(Regex("\\s+"), " ").trim()

    fun strikeText(x: Double): String = if (x == Math.floor(x) && abs(x) < 1e9) x.toLong().toString() else x.toString()

    /** The rung at [strike] between the nearest posted rungs below and above it; null with no neighbour on a side, or when the posted rungs are not falling with the line. */
    fun interpolate(rungs: List<Rung>, strike: Double): Rung? {
        val sorted = rungs.filter { it.overProb in 0.02..0.98 }.sortedBy { it.strike }.distinctBy { it.strike }
        val lo = sorted.lastOrNull { it.strike < strike } ?: return null
        val hi = sorted.firstOrNull { it.strike > strike } ?: return null
        if (hi.overProb > lo.overProb + 1e-9) return null
        val t = (strike - lo.strike) / (hi.strike - lo.strike)
        val z = probit(lo.overProb) + t * (probit(hi.overProb) - probit(lo.overProb))
        return Rung(strike, cdf(z), minOf(lo.books, hi.books), minOf(lo.asOfMs, hi.asOfMs))
    }

    /**
     * The slope of probit(over probability) per unit of (L - K) / sqrt(L), by Novig market type, fitted on the closing ladders of 2026-11-15..18 (`tools/ladder_extrap.py`; held-out error in the research note).
     * A stat not in the table is never extrapolated.
     */
    val SLOPES: Map<String, Double> = mapOf(
        "POINTS" to 0.515, "REBOUNDS" to 0.735, "ASSISTS" to 0.775, "THREE_POINTERS_MADE" to 0.730,
        "POINTS_REBOUNDS_ASSISTS" to 0.540, "POINTS_REBOUNDS" to 0.490, "POINTS_ASSISTS" to 0.510, "REBOUNDS_ASSISTS" to 0.655,
        "SHOTS_ON_GOAL" to 0.805,
        "RECEIVING_YARDS" to 0.180, "RUSHING_YARDS" to 0.200, "PASSING_YARDS" to 0.200, "RECEPTIONS" to 0.795, "RUSHING_AND_RECEIVING_YARDS" to 0.080,
    )
    /** A rung is extrapolated only when the model puts the side we price between these (the held-out error grows fast in the tails). */
    const val EXTRAP_MIN_SIDE = 0.12
    const val EXTRAP_MAX_SIDE = 0.92
    /** The farthest an extrapolated rung may sit from the base, in the model's unit (L - K) / sqrt(L): the checked range. */
    const val EXTRAP_MAX_UNITS = 3.0

    /** The over probability at [strike] from the posted rung nearest even, or null: no slope for [marketType], no posted rung, or the strike is not outside the posted range (a rung inside it is [interpolate]'s). */
    fun extrapolate(rungs: List<Rung>, strike: Double, marketType: String): Rung? {
        val beta = SLOPES[marketType] ?: return null
        val posted = rungs.filter { it.overProb in 0.02..0.98 }.distinctBy { it.strike }
        if (posted.isEmpty()) return null
        if (posted.any { it.strike < strike } && posted.any { it.strike > strike }) return null
        val base = posted.minByOrNull { abs(it.overProb - 0.5) } ?: return null
        if (base.strike <= 0.0) return null
        val units = (base.strike - strike) / sqrt(maxOf(base.strike, 0.5))
        if (abs(units) > EXTRAP_MAX_UNITS) return null
        val over = cdf(probit(base.overProb) + beta * units)
        // the over probability must fall with the line: a rung under the base is at least the base's, one above is at most
        if ((strike < base.strike && over < base.overProb - 1e-9) || (strike > base.strike && over > base.overProb + 1e-9)) return null
        return Rung(strike, over, posted.minOf { it.books }.coerceAtMost(base.books), posted.minOf { it.asOfMs })
    }

    /**
     * [direct] first; a leg it does not know is interpolated from [rungs] (group -> posted rungs) when [targets] knows what it is, and with [extrapolate] on priced from the nearest posted rung when it lies outside
     * the posted range ([EXTRAP_MIN_SIDE]..[EXTRAP_MAX_SIDE] only; such a leg's label starts "extrapolated" and its [LegFair.extrapolated] is set).
     */
    class Fairs(private val direct: LegFairs, private val targets: () -> Map<String, Target>, private val rungs: () -> Map<String, List<Rung>>, private val extrapolate: () -> Boolean = { false }) : LegFairs {
        override fun leg(outcomeId: String): LegFair? {
            direct.leg(outcomeId)?.let { return it }
            val t = targets()[outcomeId] ?: return null
            val posted = rungs()[t.group] ?: return null
            val side = if (t.over) "Over" else "Under"
            interpolate(posted, t.strike)?.let { r ->
                val p = if (t.over) r.overProb else 1.0 - r.overProb
                return LegFair(outcomeId, t.eventId, "ladder ${t.group} $side ${strikeText(t.strike)}", p, r.books, r.asOfMs, t.startsMs, false, t.marketType)
            }
            if (!extrapolate()) return null
            val r = extrapolate(posted, t.strike, t.marketType) ?: return null
            val p = if (t.over) r.overProb else 1.0 - r.overProb
            if (p < EXTRAP_MIN_SIDE || p > EXTRAP_MAX_SIDE) return null
            return LegFair(outcomeId, t.eventId, "extrapolated ${t.group} $side ${strikeText(t.strike)}", p, r.books, r.asOfMs, t.startsMs, false, t.marketType, extrapolated = true)
        }
    }

    /** Standard normal CDF (Abramowitz and Stegun 7.1.26, error under 1.5e-7). */
    fun cdf(z: Double): Double {
        val x = abs(z) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * x)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
        return if (z >= 0) 0.5 * (1.0 + y) else 0.5 * (1.0 - y)
    }

    /** Standard normal quantile (Acklam's rational approximation, relative error 1.15e-9). */
    fun probit(p: Double): Double {
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val lo = 0.02425
        return when {
            p < lo -> { val q = sqrt(-2 * ln(p)); (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
            p <= 1 - lo -> { val q = p - 0.5; val r = q * q; (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1) }
            else -> { val q = sqrt(-2 * ln(1 - p)); -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
        }
    }
}
