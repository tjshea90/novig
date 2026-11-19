package com.tjshea.vigilant.data.rfq

/**
 * Two player props of ONE game priced together (MK20, Tj 2026-11-19: "the engine prices almost nothing. It doesn't have enough fair odds": of 59,028 correlation-unmodelled RFQs in the journals, 30,221 were
 * two player props of different players, 3,939 two props of one player, 14,541 a game line with a prop).
 *
 * The joint probability is the product of the legs' fair probabilities times a ratio, capped at the smaller leg's probability (the largest a joint can be). The ratio is MEASURED: from the resolved player
 * props of 2026-11-15..18 in the PropLine archive (Novig's closing price as the fair, the result as the outcome; one line per player and stat, the one nearest even; `research/prop_pairs_2026-11-19.md`,
 * scripts `tools/prop_pairs.py`): the ratio of how often the pair hit together to what the closing probabilities multiply to, by pair of stats and by sides.
 *  - DIFFERENT players, NBA (217 games): Over-Over 1.12 (95% up to 1.25), Over-Under 1.01, Under-Under 0.90; NHL (30 games) and NFL (13) about 1.0. The archive does not say which team a player is on, so the
 *    ratio is a blend of teammates (higher) and opponents (about 1.0): the table below uses roughly 1.5 times the blend's upper bound for Over-Over, so a teammate pair is covered.
 *  - The SAME player, different stats: it depends hard on how the stats overlap (NBA points with points+rebounds+assists Over-Over 1.75 up to 2.0, NHL assists with points 2.13 up to 2.5, NFL receptions with
 *    receiving yards 1.56 up to 1.84, longest reception with receiving yards 1.88 up to 2.10; Over-Under below 1 nearly everywhere, 1.24 at the top). A pooled ratio hid this (1.41); the table uses the highest
 *    95% upper bound of any measured pair of stats for each side combination.
 * The maker SELLS the parlay, so it must not underestimate the chance that it hits: every ratio is an UPPER bound and never below 1.0 (an Over with an Under of one player is not credited). A pair not covered
 * here (a prop with a game line, three or more legs of one game, a leg whose player cannot be read, two lines of one market) declines as before.
 *
 * SHADOW first: [RfqConfig.propPairsLive] is off, so the pricer only records this model's fair as the decline's fair (the journals then say what the market charges for such pairs); nothing is quoted until
 * paper evidence says the price beats the market's trades.
 */
object PropPairs {
    private val SIDE = Regex("""^(.*?)\s+(Over|Under)\s+[0-9.]+$""")

    data class Parsed(val player: String, val over: Boolean)

    /** The player and side of a prop leg from its label ("Receiving Yards Nico Collins Over 71.5" with type RECEIVING_YARDS), or null when it cannot be read. */
    fun parse(l: LegFair): Parsed? {
        if (l.marketType.isBlank() || l.marketType in RfqPricer.GAME_LINES) return null
        val m = SIDE.matchEntire(l.label.trim()) ?: return null
        val head = m.groupValues[1]
        val stat = l.marketType.split('_').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
        val player = (if (head.startsWith(stat, ignoreCase = true)) head.substring(stat.length) else return null).trim()
        if (player.isEmpty()) return null
        return Parsed(player.lowercase(), m.groupValues[2] == "Over")
    }

    /** The upper-bound ratios (see above): same player, then different players; by the two sides. */
    private fun ratio(samePlayer: Boolean, aOver: Boolean, bOver: Boolean): Double = when {
        samePlayer && aOver && bOver -> 2.50
        samePlayer && !aOver && !bOver -> 2.00
        samePlayer -> 1.60
        aOver && bOver -> 1.55
        !aOver && !bOver -> 1.15
        else -> 1.05
    }

    /** The joint probability of a pair of player props of one game, or null when this model does not cover the group. */
    fun joint(group: List<LegFair>): Double? {
        if (group.size != 2) return null
        val a = parse(group[0]) ?: return null
        val b = parse(group[1]) ?: return null
        if (group[0].marketType == group[1].marketType && a.player == b.player) return null          // two lines of one market: not a pair of different props
        val pa = group[0].probability
        val pb = group[1].probability
        val r = ratio(a.player == b.player, a.over, b.over)
        return minOf(0.999, pa * pb * r, minOf(pa, pb))
    }
}
