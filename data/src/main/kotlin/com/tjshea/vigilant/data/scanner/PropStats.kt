package com.tjshea.vigilant.data.scanner

/**
 * Player props Vigilant can price: Novig's stat names, their display names, and the Kalshi series
 * and sportsbook (The Odds API) markets that quote the same stat (RESEARCH.md §13–14). Only props
 * with a fair-odds source are listed; a prop nothing else quotes can't be +EV-checked.
 */
object PropStats {
    val KALSHI_SERIES: Map<String, String> = mapOf(
        // NFL
        "KXNFLPASSYDS" to "PASSING_YARDS",
        "KXNFLRSHYDS" to "RUSHING_YARDS",
        "KXNFLREC" to "RECEPTIONS",
        "KXNFLRECYDS" to "RECEIVING_YARDS",
        "KXNFLRRYDS" to "RUSHING_AND_RECEIVING_YARDS",
        "KXNFLTD" to "TOUCHDOWNS",
        "KXNFLPASSTDS" to "PASSING_TOUCHDOWNS",
        "KXNFLPASSATT" to "PASSING_ATTEMPTS",
        "KXNFLRSHATT" to "RUSHING_ATTEMPTS",
        "KXNFLPASSINT" to "INTERCEPTIONS_THROWN",
        "KXNFLLONGREC" to "LONGEST_RECEPTION",
        "KXNFLLONGRSH" to "LONGEST_RUSH",
        // MLB
        "KXMLBKS" to "PITCHER_STRIKEOUTS",
        "KXMLBTB" to "TOTAL_BASES",
        "KXMLBHIT" to "HITS",
        "KXMLBHR" to "HOME_RUNS",
        "KXMLBHRR" to "HITS_RUNS_RBIS",
        "KXMLBRBI" to "RBIS",
        "KXMLBSB" to "STOLEN_BASES",
        "KXMLBHA" to "HITS_ALLOWED",
        // WNBA
        "KXWNBAPTS" to "POINTS",
        "KXWNBAREB" to "REBOUNDS",
        "KXWNBAAST" to "ASSISTS",
        "KXWNBA3PT" to "THREE_POINTERS_MADE",
    )

    /**
     * The Odds API's player-prop market keys (their "betting markets" page, 2026-09-25) for each
     * Novig stat. Over/Under markets only, except the two Yes/No ones noted, which map Yes to
     * Over 0.5. `*_alternate` ladders (Over only) can't be devigged and aren't requested.
     */
    val ODDS_API_MARKETS: Map<String, String> = mapOf(
        // NFL
        "player_pass_yds" to "PASSING_YARDS",
        "player_rush_yds" to "RUSHING_YARDS",
        "player_reception_yds" to "RECEIVING_YARDS",
        "player_receptions" to "RECEPTIONS",
        "player_pass_tds" to "PASSING_TOUCHDOWNS",
        "player_pass_attempts" to "PASSING_ATTEMPTS",
        "player_pass_completions" to "PASSING_COMPLETIONS",
        "player_rush_attempts" to "RUSHING_ATTEMPTS",
        "player_pass_interceptions" to "INTERCEPTIONS_THROWN",
        "player_rush_reception_yds" to "RUSHING_AND_RECEIVING_YARDS",
        "player_pass_rush_yds" to "PASSING_AND_RUSHING_YARDS",
        "player_reception_longest" to "LONGEST_RECEPTION",
        "player_rush_longest" to "LONGEST_RUSH",
        "player_pass_longest_completion" to "LONGEST_COMPLETION",
        "player_anytime_td" to "TOUCHDOWNS", // Yes/No
        "player_kicking_points" to "KICKING_POINTS",
        "player_field_goals" to "FIELD_GOALS_MADE",
        // MLB
        "batter_hits" to "HITS",
        "batter_total_bases" to "TOTAL_BASES",
        "batter_home_runs" to "HOME_RUNS",
        "batter_rbis" to "RBIS",
        "batter_runs_scored" to "RUNS",
        "batter_hits_runs_rbis" to "HITS_RUNS_RBIS",
        "batter_stolen_bases" to "STOLEN_BASES",
        "batter_strikeouts" to "BATTING_STRIKEOUTS",
        "batter_walks" to "BATTING_WALKS",
        "pitcher_strikeouts" to "PITCHER_STRIKEOUTS",
        "pitcher_hits_allowed" to "HITS_ALLOWED",
        "pitcher_earned_runs" to "EARNED_RUNS",
        "pitcher_outs" to "PITCHER_OUTS",
        "pitcher_walks" to "WALKS",
        // WNBA
        "player_points" to "POINTS",
        "player_rebounds" to "REBOUNDS",
        "player_assists" to "ASSISTS",
        "player_threes" to "THREE_POINTERS_MADE",
        "player_points_rebounds_assists" to "POINTS_REBOUNDS_ASSISTS",
        "player_double_double" to "DOUBLE_DOUBLE", // Yes/No
    )

    /** The four props per sport that every major book posts: the cheap default. */
    private val CORE: Map<String, List<String>> = mapOf(
        "americanfootball_nfl" to listOf("player_pass_yds", "player_rush_yds", "player_reception_yds", "player_receptions"),
        "baseball_mlb" to listOf("batter_hits", "batter_total_bases", "pitcher_strikeouts", "batter_hits_runs_rbis"),
        "basketball_wnba" to listOf("player_points", "player_rebounds", "player_assists", "player_threes"),
    )

    private val SPORT_PREFIX = mapOf(
        "americanfootball_nfl" to listOf("player_"),
        "baseball_mlb" to listOf("batter_", "pitcher_"),
        "basketball_wnba" to listOf("player_"),
    )
    private val FOOTBALL_ONLY = setOf("player_pass_", "player_rush", "player_reception", "player_anytime_td", "player_kicking", "player_field_goals")

    /** The Odds API prop market keys to request for a sport. Empty: no book props for it. */
    fun oddsApiMarkets(sportKey: String, set: BookPropSet): List<String> {
        if (set == BookPropSet.CORE) return CORE[sportKey].orEmpty()
        val prefixes = SPORT_PREFIX[sportKey] ?: return emptyList()
        val football = sportKey == "americanfootball_nfl"
        return ODDS_API_MARKETS.keys.filter { k ->
            prefixes.any { k.startsWith(it) } && (football == FOOTBALL_ONLY.any { k.startsWith(it) })
        }
    }

    /** Novig market types fetched for the "Player props" family: every stat some source prices. */
    val NOVIG_TYPES: List<String> = (KALSHI_SERIES.values + ODDS_API_MARKETS.values).distinct()

    fun displayName(novigType: String): String =
        novigType.lowercase().split('_').joinToString(" ") { w ->
            when (w) {
                "rbis" -> "RBIs"
                "and" -> "+"
                else -> w.replaceFirstChar { it.uppercase() }
            }
        }
}
