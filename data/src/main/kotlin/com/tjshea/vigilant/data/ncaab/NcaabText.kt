package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.scanner.Opportunity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Words and small numbers for the college basketball tab, kept out of the Compose code so they can be tested. */
object NcaabText {
    /** A line number as Novig prints it: "154.5", "-4.5", "+6", "PK". */
    fun number(x: Double): String = if (x == x.toLong().toDouble()) x.toLong().toString() else String.format(Locale.US, "%.1f", x)

    fun signed(x: Double): String = when {
        x == 0.0 -> "PK"
        x > 0 -> "+" + number(x)
        else -> "−" + number(abs(x))
    }

    /** "Davidson (74 ++)": the team and its points per game and tier average. Without a rating: "Davidson (—)". */
    fun team(name: String, t: NcaabTeam?): String = if (t == null) "$name (—)" else "$name (${t.ppg.roundToInt()} ${NcaabSymbols.pretty(t.sym)})"

    fun pct(x: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f%%", x * 100)

    fun cents(price: Double): String = String.format(Locale.US, "%.0f¢", price * 100)

    /** The share of the pooled bucket games where a signal's NAMED side won (a faded signal names the other side), and the games behind it. */
    fun pooled(t: NcaabSignalTable): Pair<Double, Int> {
        val n = t.buckets.sumOf { it.n }
        if (n == 0) return 0.5 to 0
        val followed = t.buckets.sumOf { it.r * it.n } / n
        return (if (t.dir >= 0) followed else 1.0 - followed) to n
    }

    /** One line per signal for the "how has this done" card, strongest first. */
    fun evidence(model: NcaabModel): List<String> = NcaabSignals.ALL.mapNotNull { id ->
        val t = model.signals[id] ?: return@mapNotNull null
        val (rate, n) = pooled(t)
        id to "${t.label}${if (t.dir < 0) " (fading it)" else ""}: ${pct(rate)} of ${"%,d".format(Locale.US, n)} games"
    }.sortedByDescending { (id, _) -> model.signals[id]?.let { pooled(it).first } ?: 0.0 }.map { it.second }

    /** The card's headline: how big the test was. */
    fun evidenceHeadline(model: NcaabModel): String {
        val games = "%,d".format(Locale.US, model.games)
        val seasons = model.seasons.size
        return "Tested on $games college games ($seasons season${if (seasons == 1) "" else "s"}) against the closing spread and total, with each team rated only from games before tip-off."
    }

    /** The verdict in a sentence, from the numbers: which signals cleared what a side costs at Novig. */
    fun verdict(model: NcaabModel): String {
        val clear = NcaabSignals.ALL.filter { id -> model.signals[id]?.let { pooled(it).let { (r, n) -> n >= NcaabSignals.MIN_BUCKET && NcaabSignals.lower95(r, n) >= NcaabSignals.COST_TO_BEAT } } == true }
        val needs = pct(NcaabSignals.COST_TO_BEAT)
        return when {
            clear.isEmpty() -> "A side at Novig costs about 50.5¢, so it must win more than $needs. None of these did, so they are opinions, not edges."
            else -> "A side at Novig costs about 50.5¢, so it must win more than $needs. ${clear.joinToString(" and ") { model.signals.getValue(it).label }} cleared it, thinly: that is the only edge here."
        }
    }

    // ---- a number to bet when the books don't price it ----------------------------------------------------------------------------------------

    /**
     * The chance the Bet sheet is given for a side no book prices: the middle of Novig's own bid and ask for it (so the sheet shows Novig's spread as the cost and claims no edge), or null when
     * Novig shows nothing to take. The sheet re-reads the real book at the tap either way.
     */
    fun midFair(o: Opportunity): Double? {
        val take = o.ladder.firstOrNull()?.price ?: return null
        val bid = o.bestBid ?: (take - 0.01)
        return ((take + bid) / 2).coerceIn(0.01, 0.99)
    }

    /**
     * A side's chance at another number than the one a reading was measured on: the reading's own chance moved along the normal curve by the points the number is easier (or harder). Spread numbers are
     * Novig's HOME handicaps, totals' lines. Null when the reading does not cover that side.
     */
    fun estimateAt(r: NcaabSignals.Reading, side: NcaabSignals.Pick, mainNumber: Double, number: Double, sigma: Double): Double? {
        if (side != r.pick) return null
        val easier = when (side) {
            NcaabSignals.Pick.HOME -> number - mainNumber
            NcaabSignals.Pick.AWAY -> mainNumber - number
            NcaabSignals.Pick.OVER -> mainNumber - number
            NcaabSignals.Pick.UNDER -> number - mainNumber
        }
        return NcaabSignals.shifted(r.rate, easier, sigma)
    }
}
