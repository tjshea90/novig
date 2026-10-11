package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.engine.Devig

/**
 * How a close found AFTER the game (SGO, OddsPapi, ParlayAPI's Pinnacle, ESPN) is turned into a fair chance (closing audit 2026-10-11): by the POWER method, the app's own default for the fair at bet
 * ([com.tjshea.vigilant.data.scanner.ScanSettings.devigMethod]), not the multiplicative one it used before. Multiplicative puts too much of the margin on the favourite and too little on the
 * longshot: on a +260 / -320 pair it gives the dog 0.267 against power's 0.253, so a bet whose market never moved read as +5.6% CLV (+12.8% at +450). The fair at bet and the close must be made the same way.
 * A pair with no margin (a stale or crossed quote) is just normalised.
 */
object CloseDevig {
    /** The fair chance of the first side of a two-way market whose vigged chances are [mine] and [other]. */
    fun fair(mine: Double, other: Double): Double {
        val raw = listOf(mine, other)
        if (raw.sum() < 1.0 - 1e-9) return mine / (mine + other)
        return runCatching { Devig.power(raw)[0] }.getOrElse { Devig.multiplicative(raw)[0] }
    }
}
