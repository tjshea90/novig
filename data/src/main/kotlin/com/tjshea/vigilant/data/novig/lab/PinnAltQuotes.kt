package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.data.pinnodds.PinnEvent
import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide

/**
 * Pinnacle's live lines (the Pinnodds socket's [PinnEvent]: main and alternate spreads and totals of the full game) as [AltQuote]s in Novig's convention. [swapped] is true when Novig's home team is
 * Pinnacle's away team. A spread's [PinnLine.points] is the HOME handicap h: home covers when its margin is over -h, so both teams' views are given (a Novig ladder's reference team can be either).
 * A line's [AltQuote.seenAtMs] is the matchup's last frame: Pinnodds only sends CHANGES, so a quiet line is as fresh as the feed is alive.
 */
object PinnAltQuotes {
    fun decimal(american: Double): Double? = when {
        american >= 100.0 -> 1.0 + american / 100.0
        american <= -100.0 -> 1.0 + 100.0 / -american
        else -> null
    }

    fun quotes(e: PinnEvent, swapped: Boolean): List<AltQuote> {
        val out = ArrayList<AltQuote>()
        val seen = e.lastFrameAtMs.takeIf { it > 0L }
        for (l in e.lines.values) {
            if (!l.open || l.period != 0) continue
            val pts = l.points ?: continue
            when (l.type) {
                PinnLineType.TOTAL -> {
                    val over = l.american[PinnSide.OVER]?.let(::decimal) ?: continue
                    val under = l.american[PinnSide.UNDER]?.let(::decimal) ?: continue
                    out += AltQuote("pinnacle", "Pinnacle", LadderKind.TOTAL, "", pts, over, under, seen)
                }
                PinnLineType.SPREAD -> {
                    val home = l.american[PinnSide.HOME]?.let(::decimal) ?: continue
                    val away = l.american[PinnSide.AWAY]?.let(::decimal) ?: continue
                    val homeSide = if (swapped) "AWAY" else "HOME"
                    val awaySide = if (swapped) "HOME" else "AWAY"
                    out += AltQuote("pinnacle", "Pinnacle", LadderKind.MARGIN, homeSide, -pts, home, away, seen)
                    out += AltQuote("pinnacle", "Pinnacle", LadderKind.MARGIN, awaySide, pts, away, home, seen)
                }
                PinnLineType.MONEYLINE -> {
                    if (PinnSide.DRAW in l.american) continue
                    val home = l.american[PinnSide.HOME]?.let(::decimal) ?: continue
                    val away = l.american[PinnSide.AWAY]?.let(::decimal) ?: continue
                    out += AltQuote("pinnacle", "Pinnacle", LadderKind.MARGIN, if (swapped) "AWAY" else "HOME", 0.0, home, away, seen)
                    out += AltQuote("pinnacle", "Pinnacle", LadderKind.MARGIN, if (swapped) "HOME" else "AWAY", 0.0, away, home, seen)
                }
            }
        }
        return out
    }
}
