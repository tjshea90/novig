package com.tjshea.vigilant.engine

import kotlin.math.roundToInt

/** Conversions between American odds, decimal odds, and probabilities. */
object Odds {

    fun americanToDecimal(american: Int): Double {
        require(american != 0) { "American odds cannot be 0" }
        return if (american > 0) 1.0 + american / 100.0 else 1.0 + 100.0 / -american
    }

    fun decimalToAmerican(decimal: Double): Int {
        require(decimal > 1.0) { "Decimal odds must be > 1.0, got $decimal" }
        return if (decimal >= 2.0) {
            Math.round((decimal - 1.0) * 100.0).toInt()
        } else {
            Math.round(-100.0 / (decimal - 1.0)).toInt()
        }
    }

    /** Raw (vig-inflated) implied probability from decimal odds. Not a fair probability on its own. */
    fun impliedProbability(decimal: Double): Double {
        require(decimal > 1.0) { "Decimal odds must be > 1.0, got $decimal" }
        return 1.0 / decimal
    }

    /** A probability (or a Novig price, which is the same thing: cost per $1 payout) as decimal odds. */
    fun probabilityToDecimal(probability: Double): Double {
        require(probability > 0.0 && probability < 1.0) { "Probability must be in (0,1), got $probability" }
        return 1.0 / probability
    }

    /** A probability (or a Novig price) as American odds, e.g. 0.385 -> +160, 0.62 -> -163. */
    fun probabilityToAmerican(probability: Double): Int = decimalToAmerican(probabilityToDecimal(probability))

    /** American odds as the text a bettor expects: a plus sign on underdogs, "EVEN" for +100. */
    fun formatAmerican(american: Int): String = when {
        american == 100 || american == -100 -> "+100"
        american > 0 -> "+$american"
        else -> american.toString()
    }

    fun formatProbability(probability: Double): String = "${(probability * 1000).roundToInt() / 10.0}%"
}
