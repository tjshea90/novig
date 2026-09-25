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
    val ALL: List<League> = listOf(
        League("NFL", "NFL", "americanfootball_nfl", "🏈", maxStartGapHours = 36, polymarketTag = 450, kalshiSeries = listOf("KXNFLGAME", "KXNFLSPREAD", "KXNFLTOTAL"), pinnacleSportId = 5),
        League("NCAAF", "NCAAF", "americanfootball_ncaaf", "🏈", maxStartGapHours = 36, polymarketTag = 100351, kalshiSeries = listOf("KXNCAAFGAME", "KXNCAAFSPREAD", "KXNCAAFTOTAL"), pinnacleSportId = 5),
        League("NBA", "NBA", "basketball_nba", "🏀", polymarketTag = 745, kalshiSeries = listOf("KXNBAGAME", "KXNBASPREAD", "KXNBATOTAL"), pinnacleSportId = 3),
        League("NCAAB", "NCAAB", "basketball_ncaab", "🏀", kalshiSeries = listOf("KXNCAABGAME"), pinnacleSportId = 3),
        League("WNBA", "WNBA", "basketball_wnba", "🏀", polymarketTag = 100254, kalshiSeries = listOf("KXWNBAGAME"), pinnacleSportId = 3),
        League("MLB", "MLB", "baseball_mlb", "⚾", polymarketTag = 100381, kalshiSeries = listOf("KXMLBGAME", "KXMLBSPREAD", "KXMLBTOTAL"), pinnacleSportId = 6),
        League("NHL", "NHL", "icehockey_nhl", "🏒", polymarketTag = 899, kalshiSeries = listOf("KXNHLGAME"), pinnacleSportId = 4),
        League("UFC", "UFC", "mma_mixed_martial_arts", "🥊", maxStartGapHours = 12, polymarketTag = 279, kalshiSeries = listOf("KXUFCFIGHT"), pinnacleSportId = 8),
        League("Boxing", "Boxing", "boxing_boxing", "🥊", maxStartGapHours = 12, pinnacleSportId = 9),
        League("EPL", "Premier League", "soccer_epl", "⚽", pinnacleSportId = 1),
        League("MLS", "MLS", "soccer_usa_mls", "⚽", pinnacleSportId = 1),
        League("La Liga", "La Liga", "soccer_spain_la_liga", "⚽", pinnacleSportId = 1),
        League("Bundesliga", "Bundesliga", "soccer_germany_bundesliga", "⚽", pinnacleSportId = 1),
        League("Serie A", "Serie A", "soccer_italy_serie_a", "⚽", pinnacleSportId = 1),
        League("Ligue 1", "Ligue 1", "soccer_france_ligue_one", "⚽", pinnacleSportId = 1),
        League("Champions League", "Champions League", "soccer_uefa_champs_league", "⚽", pinnacleSportId = 1),
        League("Europa League", "Europa League", "soccer_uefa_europa_league", "⚽", pinnacleSportId = 1),
        League("CFL", "CFL", "americanfootball_cfl", "🏈", maxStartGapHours = 36, pinnacleSportId = 5),
        League("KBO", "KBO", "baseball_kbo", "⚾", pinnacleSportId = 6),
        League("NPB", "NPB", "baseball_npb", "⚾", pinnacleSportId = 6),
    )

    private val byNovig = ALL.associateBy { it.novigName }

    fun byNovigName(name: String): League? = byNovig[name]
}
