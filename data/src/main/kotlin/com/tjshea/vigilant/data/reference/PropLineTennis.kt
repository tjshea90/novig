package com.tjshea.vigilant.data.reference

import kotlin.math.abs

/**
 * PropLine's tennis (PX11e, 2026-10-26: Pinnacle's tennis lines must not depend on SportsGameOdds alone, Tj: "I will soon not have the sgo api ... the other apis need to fill in"). Read from a real answer
 * (propline.yml task `shape`, 2026-10-26 02:09 UTC: 377 matches, Pinnacle on 114):
 *
 * - **One `tennis` key** spans every tour; each match carries `tour` (ATP, Challenger, WTA, ITF, UTR) and `tournament`. ATP and Challenger are the app's ATP, WTA is WTA, ITF goes by its tournament's men's or
 *   women's name; UTR and an unnamed tour are left out. Doubles ("A / B") ride in the same answer: left out.
 * - **Only full-match `h2h`, `spreads` and `totals`** (main and alternate lines; no player game totals, no set-by-set lines).
 * - **Sets and games share one event and one market key.** A total under 6 can only be sets (a best-of-3 match has at least 12 games): [RefBookMarket.PERIOD_SETS]; 6 and over is games. A spread is not
 *   told apart by its key: Pinnacle's main spread is ±1.5 SETS beside a games total on one match and beside a 2.5 sets total on another, so a Pinnacle spread within ±2.5 is left out (a sets spread
 *   pricing Novig's games spread is a fake edge, [ParlayTennis]); wider ones can only be games. Every other book's spreads are games ([ParlayTennis]'s verified rule).
 */
object PropLineTennis {
    /** PropLine's one sport key for every tour. */
    const val SPORT = "tennis"

    private const val SETS_TOTAL_BELOW = 6.0
    private const val MAX_SETS_SPREAD = 2.5

    /** The app's league key for a PropLine match's [tour] and [tournament], or null when the app has no such league. */
    fun leagueOf(tour: String?, tournament: String?): String? = when (tour?.trim()?.lowercase()) {
        "atp", "challenger" -> ATP
        "wta" -> WTA
        "itf" -> tournament.orEmpty().lowercase().let { t ->
            when {
                "women" in t || Regex("\\bw\\d{2,3}\\b").containsMatchIn(t) -> WTA
                " men" in " $t" || Regex("\\bm\\d{2,3}\\b").containsMatchIn(t) -> ATP
                else -> null
            }
        }
        else -> null
    }

    /** [e] with doubles left out (null) and each line in its unit: moneyline as is, a total under 6 as sets, games spreads and totals as the full match, the lines that cannot be told dropped. */
    fun normalize(e: RefEvent): RefEvent? {
        if ('/' in e.home || '/' in e.away) return null
        val markets = e.markets.mapNotNull { m ->
            if (m.period != 0) return@mapNotNull null
            when (m.kind) {
                LineKind.MONEYLINE -> m
                LineKind.TOTAL -> m.line?.let { if (it < SETS_TOTAL_BELOW) m.copy(period = RefBookMarket.PERIOD_SETS) else m }
                LineKind.SPREAD -> m.line?.let { if (m.bookKey == PINNACLE && abs(it) <= MAX_SETS_SPREAD) null else m }
                else -> null
            }
        }
        return e.copy(markets = markets)
    }

    private const val ATP = "tennis_atp"
    private const val WTA = "tennis_wta"
    private const val PINNACLE = "pinnacle"
}
