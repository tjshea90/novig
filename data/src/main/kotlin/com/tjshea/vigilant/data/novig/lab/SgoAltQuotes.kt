package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.burst.LadderKind
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.Side

/**
 * SportsGameOdds' main and alternate full-game spreads, totals and moneylines (every book, each with the time SGO last saw it) as [AltQuote]s in Novig's convention, for the paper lab
 * (Tj, 2026-10-09: "incorporate sportsgamesodds pro into the research labs"). It replaces the Pinnodds feed the lab used before (Pinnodds is dormant). [swapped] is true when Novig's home team is
 * SGO's away team. A spread's home handicap h: home covers when its margin is over -h, so both teams' views are given (a Novig ladder's reference team can be either), as [PinnAltQuotes] does.
 * [seenAtMs] is the market's own update time, so the lab's freshness rule judges each book's price by its age.
 */
object SgoAltQuotes {
    fun quotes(e: RefEvent, swapped: Boolean): List<AltQuote> {
        val out = ArrayList<AltQuote>()
        val homeSide = if (swapped) "AWAY" else "HOME"
        val awaySide = if (swapped) "HOME" else "AWAY"
        for (m in e.markets) {
            if (m.period != 0) continue
            val q = m.quotes.associateBy { it.side }
            when (m.kind) {
                LineKind.TOTAL -> {
                    val over = q[Side.OVER] ?: continue
                    val under = q[Side.UNDER] ?: continue
                    val pt = over.point ?: continue
                    out += AltQuote(m.bookKey, m.bookTitle, LadderKind.TOTAL, "", pt, over.decimalOdds, under.decimalOdds, m.lastUpdateMs)
                }
                LineKind.SPREAD -> {
                    val home = q[Side.HOME] ?: continue
                    val away = q[Side.AWAY] ?: continue
                    val h = home.point ?: continue
                    out += AltQuote(m.bookKey, m.bookTitle, LadderKind.MARGIN, homeSide, -h, home.decimalOdds, away.decimalOdds, m.lastUpdateMs)
                    out += AltQuote(m.bookKey, m.bookTitle, LadderKind.MARGIN, awaySide, h, away.decimalOdds, home.decimalOdds, m.lastUpdateMs)
                }
                LineKind.MONEYLINE -> {
                    val home = q[Side.HOME] ?: continue
                    val away = q[Side.AWAY] ?: continue
                    out += AltQuote(m.bookKey, m.bookTitle, LadderKind.MARGIN, homeSide, 0.0, home.decimalOdds, away.decimalOdds, m.lastUpdateMs)
                    out += AltQuote(m.bookKey, m.bookTitle, LadderKind.MARGIN, awaySide, 0.0, away.decimalOdds, home.decimalOdds, m.lastUpdateMs)
                }
                else -> {}
            }
        }
        return out
    }
}
