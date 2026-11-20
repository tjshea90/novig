package com.tjshea.vigilant.data.rfq

/**
 * Corrects a leg's fair probability for the bias of the proportionally devigged price (MK5/MK33, Tj 2026-11-20: "make the parlay engine work and be profitable"). Measured on 49,000 resolved outcomes of Novig's
 * own closing lines (PropLine's resolved archive, window 2026-11-16 to 2026-11-19; game lines h2h/spreads/totals and player props, NBA/NHL/NFL/NCAAB, both sides of each line so the table is symmetric): sides the
 * devig puts at 5-35% win 10% LESS often than priced (band 0.1: 0.13 against 0.146, band 0.2: 0.222 against 0.249, band 0.3: 0.329 against 0.354), sides at 60-75% win 2-3% MORE often (0.667 against 0.646,
 * 0.769 against 0.752), and the middle and the heavy favourites are fair. A parlay multiplies these errors: a three-leg parlay of 60% legs hits about 6-9% more often than the product of its fairs (the maker pays out
 * more than it priced: the first live parlays lost 5 of 6, mostly favourite legs), and a parlay of 20% legs hits about 25% less often (the maker's best business). The pricer applies the ratio to each leg before it
 * multiplies. Anchors are rounded and smoothed (the middle bands' +1-2% are inside the sample's noise); the lowest band is capped at 0.90 (a 0.71 in props on 3,300 samples is not trusted for money yet).
 */
object RfqLegCalibration {
    /** (devigged probability, ratio realized / priced), probability ascending. */
    private val ANCHORS = listOf(0.05 to 0.90, 0.20 to 0.90, 0.35 to 0.94, 0.45 to 1.00, 0.60 to 1.02, 0.75 to 1.02, 0.85 to 1.00, 0.95 to 1.00)

    fun ratio(p: Double): Double {
        if (p <= ANCHORS.first().first) return ANCHORS.first().second
        if (p >= ANCHORS.last().first) return ANCHORS.last().second
        val i = ANCHORS.indexOfLast { it.first <= p }
        val (x0, y0) = ANCHORS[i]; val (x1, y1) = ANCHORS[i + 1]
        return y0 + (y1 - y0) * (p - x0) / (x1 - x0)
    }

    fun adjust(p: Double): Double = (p * ratio(p)).coerceIn(0.001, 0.999)
}
