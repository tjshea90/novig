package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.engine.Odds

/** One book's odds for the bet and for its other side(s), as American odds text: the sheet's book table shows both (Tj, 2026-10-11). */
object BookSides {
    private fun american(decimal: Double): String = Odds.formatAmerican(Odds.decimalToAmerican(decimal))

    /** The odds of side [index] (the bet itself); null when the line has no such side. */
    fun thisSide(decimalOdds: List<Double>, index: Int?): String? = index?.let { decimalOdds.getOrNull(it) }?.let(::american)

    /** The odds of every other side ("-110", or "+150 / +210" on a three-way line), in the line's order; null when there are none. */
    fun otherSides(decimalOdds: List<Double>, index: Int?): String? {
        if (index == null) return null
        val others = decimalOdds.filterIndexed { i, _ -> i != index }
        return others.takeIf { it.isNotEmpty() }?.joinToString(" / ", transform = ::american)
    }
}
