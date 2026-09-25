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
)

object Leagues {
    val ALL: List<League> = listOf(
        League("NFL", "NFL", "americanfootball_nfl", "🏈", maxStartGapHours = 36),
        League("NCAAF", "NCAAF", "americanfootball_ncaaf", "🏈", maxStartGapHours = 36),
        League("NBA", "NBA", "basketball_nba", "🏀"),
        League("NCAAB", "NCAAB", "basketball_ncaab", "🏀"),
        League("WNBA", "WNBA", "basketball_wnba", "🏀"),
        League("MLB", "MLB", "baseball_mlb", "⚾"),
        League("NHL", "NHL", "icehockey_nhl", "🏒"),
        League("UFC", "UFC", "mma_mixed_martial_arts", "🥊", maxStartGapHours = 12),
        League("Boxing", "Boxing", "boxing_boxing", "🥊", maxStartGapHours = 12),
        League("EPL", "Premier League", "soccer_epl", "⚽"),
        League("MLS", "MLS", "soccer_usa_mls", "⚽"),
        League("La Liga", "La Liga", "soccer_spain_la_liga", "⚽"),
        League("Bundesliga", "Bundesliga", "soccer_germany_bundesliga", "⚽"),
        League("Serie A", "Serie A", "soccer_italy_serie_a", "⚽"),
        League("Ligue 1", "Ligue 1", "soccer_france_ligue_one", "⚽"),
        League("Champions League", "Champions League", "soccer_uefa_champs_league", "⚽"),
        League("Europa League", "Europa League", "soccer_uefa_europa_league", "⚽"),
        League("CFL", "CFL", "americanfootball_cfl", "🏈", maxStartGapHours = 36),
        League("KBO", "KBO", "baseball_kbo", "⚾"),
        League("NPB", "NPB", "baseball_npb", "⚾"),
    )

    private val byNovig = ALL.associateBy { it.novigName }

    fun byNovigName(name: String): League? = byNovig[name]
}
