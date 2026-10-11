package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.engine.Devig

/**
 * How a close found AFTER the game (SGO, OddsPapi, ParlayAPI's Pinnacle, ESPN) is turned into a fair chance: by the conservative WORST CASE (the lowest fair chance of the multiplicative, additive, power and Shin
 * methods), the same as the app's own fair at bet ([com.tjshea.vigilant.data.scanner.ScanSettings.devigMethod], Tj 2026-10-11: "use conservative devig methods to be safest"). The fair at bet and the close must be made
 * the same way (closing audit 2026-10-11: closes devigged multiplicatively against a power fair flattered longshots by 5-13%), and the lowest close is the cautious one for CLV. A pair with no margin (a stale or crossed
 * quote) is just normalised.
 */
object CloseDevig {
    /** The fair chance of the first side of a two-way market whose vigged chances are [mine] and [other]. */
    fun fair(mine: Double, other: Double): Double {
        val raw = listOf(mine, other)
        if (raw.sum() < 1.0 - 1e-9) return mine / (mine + other)
        return runCatching { Devig.worstCase(raw)[0] }.getOrElse { Devig.multiplicative(raw)[0] }
    }
}
