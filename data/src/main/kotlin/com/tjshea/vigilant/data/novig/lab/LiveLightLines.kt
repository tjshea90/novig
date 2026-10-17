package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.data.novig.burst.LadderLine
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Which of a live game's lines Live light watches on Novig's feed (a bbo market costs 8 of the key's 512 stream tokens, the scan and the bids share them). A game has the moneyline and a ladder of alternate
 * spreads and totals, each strike its own market; the ones that can be wrong now are those near where the score says the game is going (they price the curve) and the far ones it has all but decided (the tail).
 * Pure: the lines within [REACH_SD] sigmas of the expected final margin or total, nearest first, at most [MAX_PER_GAME]; the moneyline always. Without a readable score: the moneyline and the few lines a
 * ladder's middle is likely to be, until the score arrives.
 */
object LiveLightLines {
    const val REACH_SD = 3.5
    const val MAX_PER_GAME = 14
    const val STICKY_SD = 4.5

    fun window(lines: List<LadderLine>, league: String, state: GameState?, e: EspnGame?, keep: Set<String> = emptySet(), maxPerGame: Int = MAX_PER_GAME): List<LadderLine> {
        val prior = LiveLightScan.priorTotal(league)
        if (state == null || e == null || prior == null) return lines.filter { it.kind == LadderKind.MARGIN && it.threshold == 0.0 }
        val left = state.fractionLeft
        val totalSd = if (state.sport.normal) state.sport.fullTotalSd * sqrt(left) else sqrt((prior * left * 1.2).coerceAtLeast(0.25))
        val marginSd = if (state.sport.normal) state.sport.fullMarginSd * sqrt(left) else sqrt((prior * left * 1.2).coerceAtLeast(0.25))
        val totalCentre = state.points + prior * left
        // (line, rank: nearest first, moneyline?)
        val scored = lines.mapNotNull { l ->
            val (centre, sd) = if (l.kind == LadderKind.TOTAL) totalCentre to totalSd else (e.marginOf(l.ref)?.toDouble() ?: return@mapNotNull null) to marginSd
            val z = abs(l.threshold - centre) / sd.coerceAtLeast(0.5)
            val ml = l.kind == LadderKind.MARGIN && l.threshold == 0.0
            // A line already watched stays a little longer than a new one is taken on (every change of the watched set costs the feed's subscription tokens).
            val held = l.marketId in keep
            if (ml) Triple(l, z, true) else if (z <= REACH_SD || (held && z <= STICKY_SD)) Triple(l, if (held) z - 0.5 else z, false) else null
        }
        return scored.filter { it.third }.map { it.first } + scored.filter { !it.third }.sortedBy { it.second }.take(maxPerGame).map { it.first }
    }
}
