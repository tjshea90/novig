package com.tjshea.vigilant.data.novig.trading.maker

/**
 * How much takers trade each kind of Novig market, so a bid goes where fills are (Tj, 2026-10-04: "bids … with more attractive bets that involve bets that are
 * more popular than obscure players props"; RESEARCH.md §81.4). A bid fills only when a taker crosses it, and takers spread over thousands of listed markets very unevenly:
 * Novig's published `markets.csv` (NOVIG_API.md §10), 2026-09-27..10-03, gives the dollars traded per LISTED market in a day by kind ([DAILY_DOLLARS]; re-run
 * `tools/research/novig_popularity_study.py`): an NFL anytime-touchdown market meets about $5,900 a day, a rushing-attempts one $2,000, a receptions one $980, a receiving-yards
 * one $490, a longest-reception one $51; an MLB pitcher-outs market $7,400 against a hits one $190 and a runs one $21; NHL shots on goal $780 against assists $75.
 *
 * Pure. Used only to order the bids that go up when not every one fits ([MakerPlan.priority]); never to decide which qualify.
 */
object MarketPopularity {

    /** Mean dollars traded per listed market per day, by "LEAGUE|MARKET_TYPE" (kinds with 60+ listed markets in the seven days). */
    private val DAILY_DOLLARS: Map<String, Double> = mapOf(
        // NFL
        "NFL|SPREAD" to 17001.0,
        "NFL|TOTAL" to 11582.0,
        "NFL|TOUCHDOWNS" to 5914.0,
        "NFL|FIRST_TOUCHDOWN_SCORER" to 3906.0,
        "NFL|INTERCEPTIONS_THROWN" to 2895.0,
        "NFL|RUSHING_ATTEMPTS" to 1999.0,
        "NFL|SPREAD_1H" to 1919.0,
        "NFL|PASSING_COMPLETIONS" to 1462.0,
        "NFL|TOTAL_1H" to 1359.0,
        "NFL|PASSING_ATTEMPTS" to 1211.0,
        "NFL|RECEPTIONS" to 978.0,
        "NFL|PASSING_TOUCHDOWNS" to 675.0,
        "NFL|RUSHING_YARDS" to 663.0,
        "NFL|TEAM_TOTAL" to 621.0,
        "NFL|TACKLES_ASSISTS" to 555.0,
        "NFL|RECEIVING_YARDS" to 488.0,
        "NFL|PASSING_YARDS" to 487.0,
        "NFL|LONGEST_COMPLETION" to 193.0,
        "NFL|RUSHING_AND_RECEIVING_YARDS" to 143.0,
        "NFL|KICKING_POINTS" to 86.0,
        "NFL|LONGEST_RUSH" to 57.0,
        "NFL|LONGEST_RECEPTION" to 51.0,
        "NFL|PASSING_AND_RUSHING_YARDS" to 3.0,
        // NCAAF
        "NCAAF|SPREAD" to 4592.0,
        "NCAAF|TOTAL" to 2910.0,
        "NCAAF|MONEY_1H" to 1260.0,
        "NCAAF|SPREAD_1H" to 739.0,
        "NCAAF|TOTAL_1H" to 440.0,
        "NCAAF|TEAM_TOTAL" to 141.0,
        // MLB
        "MLB|TOTAL" to 17883.0,
        "MLB|SPREAD" to 11895.0,
        "MLB|PITCHER_STRIKEOUTS" to 7549.0,
        "MLB|PITCHER_OUTS" to 7447.0,
        "MLB|HOME_RUNS" to 3873.0,
        "MLB|TOTAL_1H" to 1869.0,
        "MLB|SPREAD_1H" to 1608.0,
        "MLB|HITS_ALLOWED" to 1266.0,
        "MLB|TEAM_TOTAL" to 610.0,
        "MLB|EARNED_RUNS" to 506.0,
        "MLB|WALKS" to 331.0,
        "MLB|HITS" to 194.0,
        "MLB|HITS_RUNS_RBIS" to 182.0,
        "MLB|TOTAL_BASES" to 155.0,
        "MLB|RBIS" to 50.0,
        "MLB|BATTING_WALKS" to 32.0,
        "MLB|BATTING_STRIKEOUTS" to 31.0,
        "MLB|STOLEN_BASES" to 28.0,
        "MLB|RUNS" to 21.0,
        // NHL
        "NHL|TOTAL" to 4381.0,
        "NHL|SPREAD" to 3164.0,
        "NHL|SHOTS_ON_GOAL" to 780.0,
        "NHL|SAVES" to 695.0,
        "NHL|PLAYER_GOALS" to 345.0,
        "NHL|TEAM_TOTAL" to 297.0,
        "NHL|POINTS" to 173.0,
        "NHL|ASSISTS" to 75.0,
        "NHL|FIRST_GOAL_SCORER" to 19.0,
        "NHL|POWER_PLAY_POINTS" to 9.0,
        // WNBA
        "WNBA|SPREAD" to 3949.0,
        "WNBA|TOTAL" to 3807.0,
        "WNBA|TOTAL_1H" to 762.0,
        "WNBA|POINTS" to 697.0,
        "WNBA|REBOUNDS" to 554.0,
        "WNBA|ASSISTS" to 414.0,
        "WNBA|FIRST_BASKET" to 406.0,
        "WNBA|POINTS_REBOUNDS_ASSISTS" to 387.0,
        "WNBA|DOUBLE_DOUBLE" to 233.0,
        "WNBA|THREE_POINTERS_MADE" to 226.0,
        "WNBA|SPREAD_1H" to 194.0,
        "WNBA|TEAM_TOTAL" to 32.0,
    )

    /** A kind that trades this much or more per listed market a day is hot (anytime TD, rushing attempts, pitcher outs, a game's spread). */
    const val HOT = 1_000.0

    /** ... popular from here (receptions, the yardage props, shots on goal, points); under it obscure (longest reception, hits, assists, runs). */
    const val POPULAR = 250.0

    /** The dollars a day takers trade in a market of [marketType] in [league] on average; null for a kind the study didn't measure. */
    fun dollars(league: String, marketType: String): Double? = DAILY_DOLLARS["${league.uppercase()}|$marketType"]

    /**
     * 0 = hot, 1 = popular, 2 = obscure. A kind the study didn't measure is judged by how many books price it: [popularBooks] or more is popular, fewer obscure
     * (a line many books quote is one many bettors want).
     */
    fun tier(league: String, marketType: String, books: Int, popularBooks: Int): Int {
        val d = dollars(league, marketType)
        return when {
            d != null -> if (d >= HOT) 0 else if (d >= POPULAR) 1 else 2
            books >= popularBooks -> 1
            else -> 2
        }
    }
}
