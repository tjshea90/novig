package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.engine.Odds

/**
 * "How well do my +EV bets profit?" (Tj, 2026-09-29: "The goal is to see how well my positive EV bets profit
 * with vigilant"): the same stats as the Stats tab, split by what a bet was, to show where the edge is real
 * (league, kind of market, size of edge, price range, scanner) and where it isn't. Each row's [stats] leave
 * out outliers like every other number in the Tracker.
 */
object TrackerBreakdown {

    enum class By(val label: String) {
        SCANNER("Scanner"), LEAGUE("League"), MARKET("Market"), EV("Edge"), ODDS("Price"),

        /**
         * How long before the start the bet was placed (Tj, 2026-10-05, v0.60.0 file: placed within 6 h his bets kept their edge at the close, earlier none of it;
         * RESEARCH.md §82). The one split here whose rows read in time order, not by the number settled.
         */
        LEAD("Time to start"),
    }

    data class Row(val label: String, val stats: TrackerStats)

    /** [bets] grouped by [by], the most-settled groups first; a group with no bet counted (all outliers) is left out. */
    fun of(bets: List<TrackedBet>, by: By): List<Row> = bets
        .groupBy { keyOf(it, by) }
        .map { (label, group) -> Row(label, BetTracker.stats(group)) }
        .filter { it.stats.bets > 0 }
        .sortedWith(
            if (by == By.LEAD) compareBy<Row> { BetLedger.LEAD_ORDER.indexOf(it.label).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it.label }
            else compareByDescending<Row> { it.stats.settled }.thenByDescending { it.stats.bets }.thenBy { it.label },
        )

    fun keyOf(b: TrackedBet, by: By): String = when (by) {
        By.SCANNER -> when (b.source) {
            BetTracker.SOURCE_CNO -> "CNO"
            BetTracker.SOURCE_PARLAY -> "ParlayAPI"
            else -> "Vigilant"
        }
        By.LEAGUE -> b.league.trim().ifEmpty { "Unknown" }
        By.MARKET -> marketOf(b)
        By.EV -> evBand(b.evPercentAtBet)
        By.ODDS -> oddsBand(b.american ?: Odds.probabilityToAmerican(b.price.coerceIn(0.001, 0.999)))
        By.LEAD -> BetLedger.keyOf(b, BetLedger.Split.LEAD)
    }

    /** The kind of market: moneyline, spread, total, team total, player prop, or the period/set variants. */
    fun marketOf(b: TrackedBet): String = when (val pick = BetGrader.pickOf(b)) {
        is BetGrader.Pick.Moneyline -> "Moneyline"
        is BetGrader.Pick.Spread -> if (pick.period == BetGrader.Period.GAME) "Spread" else "1st half / set spread"
        is BetGrader.Pick.Total -> if (pick.period == BetGrader.Period.GAME) "Total" else "1st half / inning / set total"
        is BetGrader.Pick.TeamTotal -> "Team total"
        is BetGrader.Pick.FirstSet -> "1st set winner"
        is BetGrader.Pick.Prop -> "Player props"
        null -> "Other"
    }

    /** The edge bands: what the bet's EV was when placed. */
    fun evBand(ev: Double?): String = when {
        ev == null -> "No EV on record"
        ev < 0.01 -> "Under 1%"
        ev < 0.02 -> "1–2%"
        ev < 0.03 -> "2–3%"
        ev < 0.04 -> "3–4%"
        else -> "4% and up"
    }

    /** The price bands: a bet on a favorite and a long shot need very different numbers of bets to show an edge. */
    fun oddsBand(american: Int): String = when {
        american <= -200 -> "Heavy favorite (−200 or shorter)"
        american < -110 -> "Favorite (−200 to −110)"
        american <= 110 -> "Even money (−110 to +110)"
        american <= 200 -> "Underdog (+110 to +200)"
        else -> "Long shot (over +200)"
    }
}
