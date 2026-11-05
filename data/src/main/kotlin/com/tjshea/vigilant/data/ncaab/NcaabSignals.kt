package com.tjshea.vigilant.data.ncaab

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The four signals of Tj's method as `tools/ncaab_ats.py` defines and tests them (NC1), for one game: the same numbers from the same ratings ([NcaabSignalGoldenTest] pins the two languages together).
 * A signal is a value signed toward the home side (spreads) or the Over (totals); [read] finds its size bucket in the backtest's table and says what that bucket did against closing lines. Nothing is
 * fitted and nothing is promised: a reading is "backed by history" only when its bucket has [MIN_BUCKET] games, its win rate is 95% sure to beat [COST_TO_BEAT], and it held in both halves of the sample.
 */
object NcaabSignals {
    /** Points of home-court edge a spread already holds; none at a neutral site. */
    const val HCA = 3.0
    /**
     * What a side near even money costs on Novig to take (no pregame fee): a spread or total whose two sides cost 0.50 and 0.51 pays about 0.505 on average. A bucket is evidence only when the data are 95% sure
     * its true win rate is above this ([lower95]), so a hot streak in a small bucket never counts.
     */
    const val COST_TO_BEAT = 0.505
    const val MIN_BUCKET = 100
    /** A half of the sample must have at least this many games before it can veto a bucket. */
    private const val MIN_HALF = 30

    enum class Pick { HOME, AWAY, OVER, UNDER }

    const val AVGS_LINE = "avgs_line"
    const val ATS_GAP = "ats_gap"
    const val YOUR_RULE = "your_rule"
    const val TOTAL_GAP = "total_gap"
    val ALL = listOf(AVGS_LINE, ATS_GAP, YOUR_RULE, TOTAL_GAP)

    /** The signal's value for a game, home first, or null when it does not apply. [homeSpread] is the HOME team's handicap (negative = home favoured). */
    fun value(id: String, h: NcaabTeam, a: NcaabTeam, homeSpread: Double?, total: Double?, neutral: Boolean): Double? = when (id) {
        AVGS_LINE -> homeSpread?.let { (((h.ppg - h.papg) - (a.ppg - a.papg)) / 2) + (if (neutral) 0.0 else HCA) + it }
        ATS_GAP -> h.ats - a.ats
        YOUR_RULE -> {
            val dO = h.ppg - a.ppg
            val dR = h.ats - a.ats
            if (dO * dR > 0 && abs(dR) >= 0.5) dO else null
        }
        TOTAL_GAP -> total?.let { (h.ppg + a.papg) / 2 + (a.ppg + h.papg) / 2 - it }
        else -> null
    }

    data class Reading(
        val id: String,
        val label: String,
        val kind: String,
        val about: String,
        val value: Double,
        val unit: String,
        /** The side to take if this signal were followed as the history reads it. */
        val pick: Pick,
        /** The history says this side LOSES, so the pick is the other one. */
        val fade: Boolean,
        val bucket: NcaabBucket,
        /** How often the named [pick] won against the closing line in this bucket, all logged games; this season alone when it has any. */
        val rate: Double,
        val rateCurrent: Double?,
        val nCurrent: Int,
        /** The bucket's win rate is 95% sure to beat what a side costs at Novig ([COST_TO_BEAT]), over enough games, and held in both halves. */
        val backed: Boolean,
    ) {
        val n: Int get() = bucket.n
    }

    /** What the history says of backing the named side: [NcaabBucket.r] is the share for the side the value points to; a faded signal names the other. */
    private fun named(r: Double, dir: Int) = if (dir >= 0) r else 1.0 - r

    fun read(model: NcaabModel, h: NcaabTeam, a: NcaabTeam, homeSpread: Double?, total: Double?, neutral: Boolean): List<Reading> {
        if (!model.measured) return emptyList()
        val out = ArrayList<Reading>()
        for (id in ALL) {
            val t = model.signals[id] ?: continue
            val v = value(id, h, a, homeSpread, total, neutral)?.takeIf { it != 0.0 } ?: continue
            val b = t.buckets.firstOrNull { it.holds(abs(v)) } ?: t.buckets.lastOrNull() ?: continue
            val points = if (t.kind == "total") (if (v > 0) Pick.OVER else Pick.UNDER) else (if (v > 0) Pick.HOME else Pick.AWAY)
            val pick = if (t.dir >= 0) points else when (points) { Pick.HOME -> Pick.AWAY; Pick.AWAY -> Pick.HOME; Pick.OVER -> Pick.UNDER; Pick.UNDER -> Pick.OVER }
            val rate = named(b.r, t.dir)
            val h1 = b.r1?.takeIf { b.n1 >= MIN_HALF }?.let { named(it, t.dir) }
            val h2 = b.r2?.takeIf { b.n2 >= MIN_HALF }?.let { named(it, t.dir) }
            val backed = b.n >= MIN_BUCKET && lower95(rate, b.n) >= COST_TO_BEAT && (h1 == null || h1 >= 0.5) && (h2 == null || h2 >= 0.5)
            out += Reading(id, t.label, t.kind, t.about, v, t.unit, pick, t.dir < 0, b, rate, b.rc?.let { named(it, t.dir) }, b.nc, backed)
        }
        return out
    }

    /** The win rate the data are 95% sure the true one is above (one-sided normal bound). */
    fun lower95(rate: Double, n: Int): Double = if (n <= 0) 0.0 else rate - 1.645 * sqrt(rate * (1 - rate) / n)

    /** A shrunk win rate for ranking: a bucket with few games is pulled toward a coin flip. */
    fun score(r: Reading): Double = (r.rate * r.n + 0.5 * 50) / (r.n + 50) - 0.5

    /** The profit per $1 staked if [rate] holds and the side costs [cost] (Novig's take price, no pregame fee): rate / cost - 1. */
    fun evAt(rate: Double, cost: Double): Double? = if (cost in 0.01..0.99) rate / cost - 1.0 else null

    // ---- a different number than the line: the same belief moved along the normal curve ----------------------------------------------------------

    /**
     * The chance of the same side at a line [shift] points easier (positive) or harder (negative) than the one the history was measured on, with results spread [sigma] points around the line.
     * An estimate: it assumes the normal curve, which is why the app labels an alternate's figure as one.
     */
    fun shifted(p: Double, shift: Double, sigma: Double): Double {
        if (p <= 0.0 || p >= 1.0 || sigma <= 0.0) return p
        return cdf(inverseCdf(p) + shift / sigma)
    }

    fun cdf(x: Double): Double {
        // Abramowitz & Stegun 7.1.26 on erf: good to about 1e-7, plenty here.
        val t = 1.0 / (1.0 + 0.3275911 * abs(x) / sqrt(2.0))
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x / 2.0)
        return 0.5 * (1.0 + if (x >= 0) y else -y)
    }

    fun inverseCdf(p: Double): Double {
        // Acklam's rational approximation, relative error about 1e-9.
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val low = 0.02425
        return when {
            p < low -> { val q = sqrt(-2 * ln(p)); (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
            p <= 1 - low -> { val q = p - 0.5; val r = q * q; (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1) }
            else -> { val q = sqrt(-2 * ln(1 - p)); -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
        }
    }
}
