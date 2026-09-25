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
)

object Leagues {
    val ALL: List<League> = listOf(
        League("NFL", "NFL", "americanfootball_nfl", "🏈"),
        League("NCAAF", "NCAAF", "americanfootball_ncaaf", "🏈"),
        League("NBA", "NBA", "basketball_nba", "🏀"),
        League("NCAAB", "NCAAB", "basketball_ncaab", "🏀"),
        League("WNBA", "WNBA", "basketball_wnba", "🏀"),
        League("MLB", "MLB", "baseball_mlb", "⚾"),
        League("NHL", "NHL", "icehockey_nhl", "🏒"),
        League("UFC", "UFC", "mma_mixed_martial_arts", "🥊"),
        League("Boxing", "Boxing", "boxing_boxing", "🥊"),
        League("EPL", "Premier League", "soccer_epl", "⚽"),
        League("MLS", "MLS", "soccer_usa_mls", "⚽"),
        League("La Liga", "La Liga", "soccer_spain_la_liga", "⚽"),
        League("Bundesliga", "Bundesliga", "soccer_germany_bundesliga", "⚽"),
        League("Serie A", "Serie A", "soccer_italy_serie_a", "⚽"),
        League("Ligue 1", "Ligue 1", "soccer_france_ligue_one", "⚽"),
        League("Champions League", "Champions League", "soccer_uefa_champs_league", "⚽"),
        League("Europa League", "Europa League", "soccer_uefa_europa_league", "⚽"),
        League("CFL", "CFL", "americanfootball_cfl", "🏈"),
        League("KBO", "KBO", "baseball_kbo", "⚾"),
        League("NPB", "NPB", "baseball_npb", "⚾"),
    )

    private val byNovig = ALL.associateBy { it.novigName }

    fun byNovigName(name: String): League? = byNovig[name]
}
