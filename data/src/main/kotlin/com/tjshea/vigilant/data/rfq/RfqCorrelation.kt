package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The normal distribution, bivariate and trivariate (MK12, Tj 2026-11-19: "figure out 2 leg correlated parlays and what a fair price should be"). Pure, no dependencies: the same-game parlay pricer
 * ([SameGame]) turns each leg's fair probability into a normal threshold and joins them with a Gaussian copula, so the legs keep the probabilities the books gave and only their dependence is modelled.
 */
object Gauss {
    /** The standard normal CDF (Hart 1968, the double-precision algorithm: 1e-15 relative error). */
    fun cdf(x: Double): Double {
        val a = abs(x)
        if (a > 37.0) return if (x > 0) 1.0 else 0.0
        val e = exp(-a * a / 2.0)
        val c: Double = if (a < 7.07106781186547) {
            var b = 3.52624965998911E-02 * a + 0.700383064443688
            b = b * a + 6.37396220353165
            b = b * a + 33.912866078383
            b = b * a + 112.079291497871
            b = b * a + 221.213596169931
            b = b * a + 220.206867912376
            var d = 8.83883476483184E-02 * a + 1.75566716318264
            d = d * a + 16.064177579207
            d = d * a + 86.7807322029461
            d = d * a + 296.564248779674
            d = d * a + 637.333633378831
            d = d * a + 793.826512519948
            d = d * a + 440.413735824752
            e * b / d
        } else {
            var b = a + 0.65
            b = a + 4.0 / b
            b = a + 3.0 / b
            b = a + 2.0 / b
            b = a + 1.0 / b
            e / b / 2.506628274631
        }
        return if (x > 0) 1.0 - c else c
    }

    fun pdf(x: Double): Double = exp(-x * x / 2.0) / 2.5066282746310002

    /** The inverse of [cdf] (Acklam's rational approximation, then two Halley steps: full double precision). p is clamped to [1e-12, 1 - 1e-12]. */
    fun inv(pIn: Double): Double {
        val p = pIn.coerceIn(1e-12, 1.0 - 1e-12)
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val lo = 0.02425
        var x = when {
            p < lo -> { val q = sqrt(-2.0 * ln(p)); (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0) }
            p > 1.0 - lo -> { val q = sqrt(-2.0 * ln(1.0 - p)); -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0) }
            else -> { val q = p - 0.5; val r = q * q; (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0) }
        }
        repeat(2) {
            val e = cdf(x) - p
            val u = e * 2.5066282746310002 * exp(x * x / 2.0)
            x -= u / (1.0 + x * u / 2.0)
        }
        return x
    }

    // 48-point Gauss-Legendre rule on [-1, 1], computed once by Newton iteration on the Legendre polynomial.
    private const val N = 48
    private val nodes = DoubleArray(N)
    private val weights = DoubleArray(N)
    init {
        for (i in 0 until (N + 1) / 2) {
            var z = Math.cos(PI * (i + 0.75) / (N + 0.5))
            var pp = 0.0
            repeat(100) {
                var p1 = 1.0; var p2 = 0.0
                for (j in 1..N) { val p3 = p2; p2 = p1; p1 = ((2.0 * j - 1.0) * z * p2 - (j - 1.0) * p3) / j }
                pp = N * (z * p1 - p2) / (z * z - 1.0)
                val z1 = z
                z = z1 - p1 / pp
                if (abs(z - z1) < 1e-15) return@repeat
            }
            nodes[i] = -z; nodes[N - 1 - i] = z
            weights[i] = 2.0 / ((1.0 - z * z) * pp * pp); weights[N - 1 - i] = weights[i]
        }
    }

    private inline fun integrate(a: Double, b: Double, f: (Double) -> Double): Double {
        if (b == a) return 0.0
        val mid = (a + b) / 2.0; val half = (b - a) / 2.0
        var s = 0.0
        for (i in 0 until N) s += weights[i] * f(mid + half * nodes[i])
        return s * half
    }

    /**
     * P(X <= a, Y <= b) for standard normals with correlation [rho]: P = Phi(a) Phi(b) + integral from 0 to rho of the bivariate density, with t = sin(theta) so the 1/sqrt(1 - t^2) factor cancels (the
     * integrand is smooth, 48 nodes are exact to about 1e-12 for |rho| <= 0.99).
     */
    fun bivariate(a: Double, b: Double, rho: Double): Double {
        val r = rho.coerceIn(-0.999, 0.999)
        if (r == 0.0) return cdf(a) * cdf(b)
        val theta = asin(r)
        val extra = integrate(0.0, theta) { th ->
            val s = sin(th); val c2 = 1.0 - s * s
            exp(-(a * a - 2.0 * s * a * b + b * b) / (2.0 * c2)) / (2.0 * PI)
        }
        return (cdf(a) * cdf(b) + extra).coerceIn(0.0, min(cdf(a), cdf(b)))
    }

    /** P(X1 <= a1, X2 <= a2, X3 <= a3) for standard normals with the given pairwise correlations (conditioning on X1, integrated numerically). */
    fun trivariate(a1: Double, a2: Double, a3: Double, r12: Double, r13: Double, r23: Double): Double {
        val c12 = r12.coerceIn(-0.995, 0.995); val c13 = r13.coerceIn(-0.995, 0.995)
        val s2 = sqrt(1.0 - c12 * c12); val s3 = sqrt(1.0 - c13 * c13)
        val r23c = ((r23 - c12 * c13) / (s2 * s3)).coerceIn(-0.995, 0.995)
        val lo = -9.0
        if (a1 <= lo) return 0.0
        val v = integrate(lo, a1) { z -> pdf(z) * bivariate((a2 - c12 * z) / s2, (a3 - c13 * z) / s3, r23c) }
        return v.coerceIn(0.0, min(cdf(a1), min(cdf(a2), cdf(a3))))
    }
}

/** One leg of a same-game parlay, described just enough to say how it moves with the game's margin and total. */
data class GameLeg(
    val kind: LineKind,
    /** HOME / AWAY for a moneyline, spread or team total's team side (team totals: which team's total); OVER / UNDER for totals and team totals. */
    val side: Side,
    /** Team total only: whether the total is the AWAY team's. */
    val awayTeam: Boolean = false,
    /** 0 = full game, 1 = first half. */
    val period: Int = 0,
    /** The leg's fair probability. */
    val p: Double,
)

/**
 * The fair price of several legs of ONE game together (MK12). A game is two latent variables, the margin M (home minus away points) and the total T, with correlation [Sport.rhoMT] and spread ratio
 * [Sport.sdRatio] (sd of the total over sd of the margin). Every leg is "a linear mix of M and T is high" (a moneyline or spread: M; a total: T; a team's total: half of each), so the legs' pairwise
 * correlations follow from those two numbers, and a first-half line is the same variable observed over half the time (a Brownian increment: its covariance with the full game is the shorter time).
 * The joint probability is the Gaussian copula's: each leg keeps the probability the books gave it, only the dependence is modelled.
 *
 * Calibration (research/novig_parlay_maker_2026-11-19.md): 17,323 college games' closing lines: the correlation of the margin residual and the total residual is +0.023, the favourite covering and the Over
 * together happen 0.973 of the independent rate; Novig's market prices a side and the total about 3% over independent (404 trades): the legs are nearly independent, so the copula's job is the team-total
 * and first-half pairs (correlation 0.6-0.7: the market charges +47% on them) and the small tilts.
 */
object SameGame {
    data class Sport(val sdRatio: Double, val rhoMT: Double)

    /** Per league (Novig's league names). Values come from [calibration]-style fits on closing lines and results; leagues not listed use [DEFAULT]. */
    val SPORTS: Map<String, Sport> = mapOf(
        "NBA" to Sport(1.45, 0.0), "NCAAB" to Sport(1.46, 0.023), "WNBA" to Sport(1.45, 0.0),
        "NFL" to Sport(1.0, 0.0), "NCAAF" to Sport(1.0, 0.0), "NHL" to Sport(1.0, 0.0), "MLB" to Sport(1.0, 0.0),
    )
    val DEFAULT = Sport(1.2, 0.0)

    /** The most legs of one game this prices: three (a side, the total and a team total is the commonest triple). */
    const val MAX_LEGS = 3

    private fun cm(l: GameLeg): Double = when (l.kind) {
        LineKind.MONEYLINE, LineKind.SPREAD -> if (l.side == Side.HOME) 1.0 else -1.0
        LineKind.TOTAL -> 0.0
        LineKind.TEAM_TOTAL -> { val s = if (l.side == Side.OVER) 1.0 else -1.0; if (l.awayTeam) -0.5 * s else 0.5 * s }
        LineKind.PLAYER_PROP -> 0.0
    }

    private fun ct(l: GameLeg): Double = when (l.kind) {
        LineKind.MONEYLINE, LineKind.SPREAD -> 0.0
        LineKind.TOTAL -> if (l.side == Side.OVER) 1.0 else -1.0
        LineKind.TEAM_TOTAL -> if (l.side == Side.OVER) 0.5 else -0.5
        LineKind.PLAYER_PROP -> 0.0
    }

    /** Whether [SameGame] can price this leg: game lines and team totals, whole game or first half. */
    fun modelled(kind: LineKind, period: Int): Boolean = (kind != LineKind.PLAYER_PROP) && period in 0..1

    private fun cov(a: GameLeg, b: GameLeg, sport: Sport): Double {
        val t = min(if (a.period == 0) 1.0 else 0.5, if (b.period == 0) 1.0 else 0.5)
        val r = sport.sdRatio
        return t * (cm(a) * cm(b) + (cm(a) * ct(b) + ct(a) * cm(b)) * sport.rhoMT * r + ct(a) * ct(b) * r * r)
    }

    /** The correlation of two legs' latent variables, or null when one has no game-line structure. */
    fun correlation(a: GameLeg, b: GameLeg, sport: Sport = DEFAULT): Double? {
        if (!modelled(a.kind, a.period) || !modelled(b.kind, b.period)) return null
        val va = cov(a, a, sport); val vb = cov(b, b, sport)
        if (va <= 0.0 || vb <= 0.0) return null
        return (cov(a, b, sport) / sqrt(va * vb)).coerceIn(-0.99, 0.99)
    }

    /**
     * The probability that every leg wins, for 2 or [MAX_LEGS] legs of one game, or null when a leg is not a modelled game line (or the legs contradict: the same line twice, which Novig refuses anyway).
     * Probabilities are clamped inside (0.001, 0.999).
     */
    fun joint(legs: List<GameLeg>, sport: Sport = DEFAULT): Double? {
        if (legs.size !in 2..MAX_LEGS) return null
        val z = legs.map { Gauss.inv(it.p.coerceIn(0.001, 0.999)) }
        fun r(i: Int, j: Int) = correlation(legs[i], legs[j], sport)
        return when (legs.size) {
            2 -> { val r01 = r(0, 1) ?: return null; Gauss.bivariate(z[0], z[1], r01) }
            else -> { val r01 = r(0, 1) ?: return null; val r02 = r(0, 2) ?: return null; val r12 = r(1, 2) ?: return null; Gauss.trivariate(z[0], z[1], z[2], r01, r02, r12) }
        }
    }

    /** The joint probability divided by the independent product: what correlation does to the price (1.0 = independent). */
    fun lift(legs: List<GameLeg>, sport: Sport = DEFAULT): Double? {
        val j = joint(legs, sport) ?: return null
        val ind = legs.fold(1.0) { acc, l -> acc * l.p.coerceIn(0.001, 0.999) }
        return max(0.0, j) / ind
    }
}
