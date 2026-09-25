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
        "KXNFLPASSCOMP" to "PASSING_COMPLETIONS",
        // MLB
        "KXMLBKS" to "PITCHER_STRIKEOUTS",
        "KXMLBTB" to "TOTAL_BASES",
        "KXMLBHIT" to "HITS",
        "KXMLBHR" to "HOME_RUNS",
        "KXMLBHRR" to "HITS_RUNS_RBIS",
        "KXMLBRBI" to "RBIS",
        "KXMLBSB" to "STOLEN_BASES",
        "KXMLBHA" to "HITS_ALLOWED",
        // Pitcher lines, added 2026-09-25 ~18:30Z (open and liquid live; RESEARCH.md §15).
        "KXMLBOUTS" to "PITCHER_OUTS",
        "KXMLBERA" to "EARNED_RUNS",
        "KXMLBWA" to "WALKS",
        // WNBA
        "KXWNBAPTS" to "POINTS",
        "KXWNBAREB" to "REBOUNDS",
        "KXWNBAAST" to "ASSISTS",
        "KXWNBA3PT" to "THREE_POINTERS_MADE",
    )

    /**
     * The Odds API's player-prop market keys (their "betting markets" page, re-checked 2026-09-25)
     * for each Novig stat, per sport, in order of how widely the books post them. Over/Under
     * markets, except the [YES_NO] ones, which map Yes to Over 0.5 (Novig lists "Over 0.5
     * TOUCHDOWNS", "Over 0.5 DOUBLE_DOUBLE"). `*_alternate` ladders and "Over only" markets
     * have no second side to devig, so they're never requested.
     */
    private val SPORT_MARKETS: Map<String, List<Pair<String, String>>> = mapOf(
        "americanfootball_nfl" to listOf(
            "player_pass_yds" to "PASSING_YARDS",
            "player_rush_yds" to "RUSHING_YARDS",
            "player_reception_yds" to "RECEIVING_YARDS",
            "player_receptions" to "RECEPTIONS",
            "player_pass_tds" to "PASSING_TOUCHDOWNS",
            "player_tds" to "TOUCHDOWNS",
            "player_anytime_td" to "TOUCHDOWNS",
            "player_pass_attempts" to "PASSING_ATTEMPTS",
            "player_pass_completions" to "PASSING_COMPLETIONS",
            "player_rush_attempts" to "RUSHING_ATTEMPTS",
            "player_pass_interceptions" to "INTERCEPTIONS_THROWN",
            "player_rush_reception_yds" to "RUSHING_AND_RECEIVING_YARDS",
            "player_pass_rush_yds" to "PASSING_AND_RUSHING_YARDS",
            "player_reception_longest" to "LONGEST_RECEPTION",
            "player_rush_longest" to "LONGEST_RUSH",
            "player_pass_longest_completion" to "LONGEST_COMPLETION",
            "player_kicking_points" to "KICKING_POINTS",
            "player_field_goals" to "FIELD_GOALS_MADE",
        ),
        "baseball_mlb" to listOf(
            "batter_hits" to "HITS",
            "batter_total_bases" to "TOTAL_BASES",
            "pitcher_strikeouts" to "PITCHER_STRIKEOUTS",
            "batter_hits_runs_rbis" to "HITS_RUNS_RBIS",
            "batter_home_runs" to "HOME_RUNS",
            "batter_rbis" to "RBIS",
            "batter_runs_scored" to "RUNS",
            "batter_stolen_bases" to "STOLEN_BASES",
            "batter_strikeouts" to "BATTING_STRIKEOUTS",
            "batter_walks" to "BATTING_WALKS",
            "pitcher_hits_allowed" to "HITS_ALLOWED",
            "pitcher_earned_runs" to "EARNED_RUNS",
            "pitcher_outs" to "PITCHER_OUTS",
            "pitcher_walks" to "WALKS",
        ),
        "basketball_wnba" to listOf(
            "player_points" to "POINTS",
            "player_rebounds" to "REBOUNDS",
            "player_assists" to "ASSISTS",
            "player_threes" to "THREE_POINTERS_MADE",
            "player_points_rebounds_assists" to "POINTS_REBOUNDS_ASSISTS",
            "player_double_double" to "DOUBLE_DOUBLE",
        ),
    )

    /** Every Odds API prop key Vigilant uses, to its Novig stat. */
    val ODDS_API_MARKETS: Map<String, String> = SPORT_MARKETS.values.flatten().toMap()

    /** Yes/No markets: Yes is Over 0.5, No is Under 0.5. */
    val YES_NO: Set<String> = setOf("player_anytime_td", "player_double_double")

    /** How many of a sport's props [BookPropSet.CORE] buys: the four every major book posts. */
    private const val CORE_COUNT = 4

    /**
     * The Odds API prop market keys to request for a sport, limited to the stats Novig actually
     * lists for the game ([novigTypes], null = don't filter). Empty: no book props for it.
     */
    fun oddsApiMarkets(sportKey: String, set: BookPropSet, novigTypes: Set<String>? = null): List<String> {
        val all = SPORT_MARKETS[sportKey].orEmpty()
        val picked = if (set == BookPropSet.CORE) all.take(CORE_COUNT) else all
        return picked.filter { novigTypes == null || it.second in novigTypes }.map { it.first }
    }

    /** Novig market types fetched for the "Player props" family: every stat some source prices. */
    val NOVIG_TYPES: List<String> = (KALSHI_SERIES.values + ODDS_API_MARKETS.values).distinct()

    /** Stats only the sportsbooks price: not worth fetching from Novig unless book props are on. */
    val BOOK_ONLY_TYPES: Set<String> = ODDS_API_MARKETS.values.toSet() - KALSHI_SERIES.values.toSet()

    fun displayName(novigType: String): String =
        novigType.lowercase().split('_').joinToString(" ") { w ->
            when (w) {
                "rbis" -> "RBIs"
                "and" -> "+"
                else -> w.replaceFirstChar { it.uppercase() }
            }
        }
}
