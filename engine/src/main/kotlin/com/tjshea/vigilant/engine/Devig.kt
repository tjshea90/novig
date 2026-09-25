package com.tjshea.vigilant.engine

import kotlin.math.sqrt

/**
 * Margin-removal ("devigging"): turn a set of raw implied probabilities (which sum to more than
 * 1 because of the book's overround/vig) into fair probabilities that sum to exactly 1.
 *
 * All functions take `rawProbs`, the per-outcome raw implied probability (1/decimal_odds — see
 * [Odds.impliedProbability]), for every outcome in one market, and return fair probabilities in
 * the same order. Formulas and reasoning: RESEARCH.md §5.
 */
object Devig {

    fun devig(rawProbs: List<Double>, method: DevigMethod): List<Double> {
        require(rawProbs.isNotEmpty()) { "Need at least one outcome" }
        require(rawProbs.all { it > 0.0 && it < 1.0 }) { "Raw implied probabilities must be in (0,1): $rawProbs" }
        return when (method) {
            DevigMethod.MULTIPLICATIVE -> multiplicative(rawProbs)
            DevigMethod.ADDITIVE -> additive(rawProbs)
            DevigMethod.POWER -> power(rawProbs)
            DevigMethod.SHIN -> shin(rawProbs)
            DevigMethod.WORST_CASE -> worstCase(rawProbs)
        }
    }

    /**
     * OddsJam's "worst case": for each outcome, the LOWEST fair probability any of the four real
     * methods gives it. Deliberately does not sum to 1. It is a conservative per-side estimate,
     * so an edge that survives it survives every method.
     */
    fun worstCase(rawProbs: List<Double>): List<Double> {
        val all = listOf(multiplicative(rawProbs), additive(rawProbs), power(rawProbs), shin(rawProbs))
        return rawProbs.indices.map { i -> all.minOf { it[i] } }
    }

    /** P_fair,i = (1/O_i) / sum_k(1/O_k) — spreads the vig proportionally to each outcome's own weight. */
    fun multiplicative(rawProbs: List<Double>): List<Double> {
        val sum = rawProbs.sum()
        return rawProbs.map { it / sum }
    }

    /** P_fair,i = (1/O_i) - (sum_k(1/O_k) - 1) / n — spreads the vig evenly across outcomes. */
    fun additive(rawProbs: List<Double>): List<Double> {
        val n = rawProbs.size
        val overround = rawProbs.sum() - 1.0
        return rawProbs.map { it - overround / n }
    }

    /**
     * Solves for exponent k such that sum_i (raw_i)^k = 1, then returns (raw_i)^k per outcome.
     * f(k) = sum(raw_i^k) - 1 is strictly decreasing in k for k > 0 (each raw_i in (0,1)), so a
     * plain bisection converges reliably; no closed form.
     */
    fun power(rawProbs: List<Double>): List<Double> {
        require(rawProbs.sum() >= 1.0 - 1e-9) {
            "Power method assumes real (non-negative) margin — raw probabilities summing to " +
                "${rawProbs.sum()} imply arbitrage, which isn't a real book's line: $rawProbs"
        }
        fun sumAtK(k: Double): Double = rawProbs.sumOf { Math.pow(it, k) }

        if (rawProbs.size == 1) return rawProbs // no vig to solve for on a single outcome

        var lo = 1.0
        var hi = 2.0
        var hiIterations = 0
        while (sumAtK(hi) > 1.0 && hiIterations < 64) {
            hi *= 2.0
            hiIterations++
        }

        repeat(100) {
            val mid = (lo + hi) / 2.0
            if (sumAtK(mid) > 1.0) lo = mid else hi = mid
        }
        val k = (lo + hi) / 2.0
        return rawProbs.map { Math.pow(it, k) }
    }

    /**
     * Shin's method: models the overround as arising from a fraction z of informed ("insider")
     * money. Solves for z in [0, 1) such that the implied fair probabilities sum to 1, using the
     * closed-form-per-z expression from RESEARCH.md §5:
     *
     *   p_i(z) = [ sqrt(z^2 + 4(1-z) * raw_i^2 / sumRaw) - z ] / [2(1-z)]
     *
     * g(z) = sum_i p_i(z) - 1 runs from positive at z=0 (equals sqrt(sumRaw) - 1 for margin > 0)
     * down to negative as z -> 1, so bisection on [0, 1) converges to a unique root.
     */
    fun shin(rawProbs: List<Double>): List<Double> {
        require(rawProbs.sum() >= 1.0 - 1e-9) {
            "Shin's method assumes real (non-negative) margin — raw probabilities summing to " +
                "${rawProbs.sum()} imply arbitrage, which isn't a real book's line: $rawProbs"
        }
        if (rawProbs.size == 1) return rawProbs

        val sumRaw = rawProbs.sum()

        fun probsAtZ(z: Double): List<Double> {
            return rawProbs.map { raw ->
                val underSqrt = z * z + 4.0 * (1.0 - z) * (raw * raw / sumRaw)
                (sqrt(underSqrt) - z) / (2.0 * (1.0 - z))
            }
        }

        var lo = 0.0
        var hi = 1.0 - 1e-9
        repeat(100) {
            val mid = (lo + hi) / 2.0
            val sumAtMid = probsAtZ(mid).sum()
            if (sumAtMid > 1.0) lo = mid else hi = mid
        }
        val z = (lo + hi) / 2.0
        return probsAtZ(z)
    }
}
