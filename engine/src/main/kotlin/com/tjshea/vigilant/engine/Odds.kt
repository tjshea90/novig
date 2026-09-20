package com.tjshea.vigilant.engine

/** Conversions between American odds, decimal odds, and raw implied probability. */
object Odds {

    fun americanToDecimal(american: Int): Double {
        require(american != 0) { "American odds cannot be 0" }
        return if (american > 0) {
            1.0 + american / 100.0
        } else {
            1.0 + 100.0 / -american
        }
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

    /**
     * Novig quotes prices directly as decimal probabilities in (0, 1) — the stake per $1.00
     * payout (RESEARCH.md §2) — not American/decimal odds. This is that same value expressed
     * as decimal odds, for reuse with the devig math below, which all operates on decimal odds.
     */
    fun novigPriceToDecimalOdds(novigPrice: Double): Double {
        require(novigPrice > 0.0 && novigPrice < 1.0) { "Novig price must be in (0,1), got $novigPrice" }
        return 1.0 / novigPrice
    }
}
