package com.tjshea.vigilant.data.rfq

/**
 * Two player props of ONE game priced together (MK20, Tj 2026-11-19: "the engine prices almost nothing. It doesn't have enough fair odds": of 64,873 RFQs in 16 minutes 21% were two legs of one game that
 * the maker declined as "correlation unmodelled", most of them two player props).
 *
 * The joint probability is the product of the legs' fair probabilities times a ratio, and the ratio is MEASURED: from the resolved player props of 2026-11-15..18 in the PropLine archive (Novig's closing
 * price as the fair, the result as the outcome; every pair of props of a game with different player or different stat; `tools/prop_pairs.py`, `research/prop_pairs_2026-11-19.md`): the ratio of how often
 * the pair hit together to what the closing probabilities multiply to. Same player, both Over: 1.41 (NBA, 220 games), 1.47 (NHL, 30), 1.34 (NFL, 13); same player Over + Under: 0.77 / 0.72 / 0.71; same player
 * both Under 1.11 / 1.16 / 1.26; different players about 1.0 (NBA Over-Over 1.12, Under-Under 0.90). The maker SELLS the parlay, so it must not underestimate the chance that it hits: each ratio used is the
 * UPPER 95% bound of the measured interval, never below 1 (the ratios under 1, an Over with an Under of one player, are not credited), taken as the largest over the three sports where the games are few.
 * Not modelled (the pair declines as before): a prop with a game line, three or more legs of one game, a leg whose player cannot be read from its label, two lines of one market.
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

    /** The upper-bound ratios (see above): same player, then different players; keyed by the two sides. */
    private fun ratio(samePlayer: Boolean, aOver: Boolean, bOver: Boolean): Double = when {
        samePlayer && aOver && bOver -> 1.70
        samePlayer && !aOver && !bOver -> 1.50
        samePlayer -> 1.00
        aOver && bOver -> 1.33
        !aOver && !bOver -> 1.22
        else -> 1.04
    }

    /** The joint probability of a pair of player props of one game, or null when this model does not cover the group. */
    fun joint(group: List<LegFair>): Double? {
        if (group.size != 2) return null
        val a = parse(group[0]) ?: return null
        val b = parse(group[1]) ?: return null
        if (group[0].marketType == group[1].marketType && a.player == b.player) return null          // two lines of one market: not a pair of different props
        val r = ratio(a.player == b.player, a.over, b.over)
        return minOf(0.999, group[0].probability * group[1].probability * r)
    }
}
