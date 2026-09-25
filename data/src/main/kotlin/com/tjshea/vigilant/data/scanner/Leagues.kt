package com.tjshea.vigilant.data.scanner

/**
 * A league the scanner can price: Novig's exact league name (NOVIG_API.md §5, from
 * `/v3/public/types/leagues`) paired with The Odds API's sport key for the same competition.
 * Leagues The Odds API has no stable key for (tennis tournaments, esports) are left out.
 */
data class League(
    val novigName: String,
    val displayName: String,
    val oddsApiSportKey: String,
    val emoji: String,
    /**
     * How far apart Novig's and the sportsbooks' start times may be and still be the same game.
     * Daily sports need it tight, or Friday's game gets priced against Saturday's line when the
     * two teams play a series. Football needs it loose: Novig lists some NCAAF games with
     * placeholder times (seen live 2026-09-25: many at 04:00 UTC).
     */
    val maxStartGapHours: Int = 6,
    /** Polymarket Gamma `tag_id` for this league's game markets (from `/sports`), if it has any. */
    val polymarketTag: Int? = null,
    /** Kalshi series tickers: game winner, spread, total. */
    val kalshiSeries: List<String> = emptyList(),
    /** pinnapi `sport_id` (one request covers every league of that sport). */
    val pinnacleSportId: Int? = null,
)

object Leagues {
    /**
     * In chip order. Tj's order (2026-09-25): NFL, NCAAF, MLB, WNBA, NHL first. Soccer, CFL, KBO and
     * NPB were removed from the app at his request the same day.
     */
    val ALL: List<League> = listOf(
        League(
            "NFL", "NFL", "americanfootball_nfl", "🏈", maxStartGapHours = 36, polymarketTag = 450, pinnacleSportId = 5,
            kalshiSeries = listOf(
                "KXNFLGAME", "KXNFLSPREAD", "KXNFLTOTAL", "KXNFL1HSPREAD", "KXNFL1HTOTAL", "KXNFLTEAMTOTAL",
                "KXNFLPASSYDS", "KXNFLRSHYDS", "KXNFLREC", "KXNFLRECYDS", "KXNFLRRYDS", "KXNFLTD", "KXNFLPASSTDS",
                "KXNFLPASSATT", "KXNFLRSHATT", "KXNFLPASSINT", "KXNFLLONGREC", "KXNFLLONGRSH",
            ),
        ),
        League(
            "NCAAF", "NCAAF", "americanfootball_ncaaf", "🏈", maxStartGapHours = 36, polymarketTag = 100351, pinnacleSportId = 5,
            kalshiSeries = listOf("KXNCAAFGAME", "KXNCAAFSPREAD", "KXNCAAFTOTAL", "KXNCAAF1HSPREAD", "KXNCAAF1HTOTAL", "KXNCAAFTEAMTOTAL"),
        ),
        League(
            "MLB", "MLB", "baseball_mlb", "⚾", polymarketTag = 100381, pinnacleSportId = 6,
            kalshiSeries = listOf(
                "KXMLBGAME", "KXMLBSPREAD", "KXMLBTOTAL", "KXMLBF5SPREAD", "KXMLBF5TOTAL", "KXMLBTEAMTOTAL",
                "KXMLBKS", "KXMLBTB", "KXMLBHIT", "KXMLBHR", "KXMLBHRR", "KXMLBRBI", "KXMLBSB", "KXMLBHA",
            ),
        ),
        League(
            "WNBA", "WNBA", "basketball_wnba", "🏀", polymarketTag = 100254, pinnacleSportId = 3,
            kalshiSeries = listOf(
                "KXWNBAGAME", "KXWNBASPREAD", "KXWNBATOTAL", "KXWNBA1HSPREAD", "KXWNBA1HTOTAL", "KXWNBATEAMTOTAL",
                "KXWNBAPTS", "KXWNBAREB", "KXWNBAAST", "KXWNBA3PT",
            ),
        ),
        League("NHL", "NHL", "icehockey_nhl", "🏒", polymarketTag = 899, kalshiSeries = listOf("KXNHLGAME", "KXNHLSPREAD", "KXNHLTOTAL"), pinnacleSportId = 4),
        League("NBA", "NBA", "basketball_nba", "🏀", polymarketTag = 745, kalshiSeries = listOf("KXNBAGAME", "KXNBASPREAD", "KXNBATOTAL"), pinnacleSportId = 3),
        League("NCAAB", "NCAAB", "basketball_ncaab", "🏀", kalshiSeries = listOf("KXNCAABGAME"), pinnacleSportId = 3),
        League("UFC", "UFC", "mma_mixed_martial_arts", "🥊", maxStartGapHours = 12, polymarketTag = 279, kalshiSeries = listOf("KXUFCFIGHT"), pinnacleSportId = 8),
        League("Boxing", "Boxing", "boxing_boxing", "🥊", maxStartGapHours = 12, pinnacleSportId = 9),
    )

    private val byNovig = ALL.associateBy { it.novigName }

    fun byNovigName(name: String): League? = byNovig[name]
}
