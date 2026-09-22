package com.tjshea.vigilant.data.novig

/**
 * Maps a [com.tjshea.vigilant.data.scanner.Sport.key] (The Odds API's naming convention) to the
 * league string Novig's own GraphQL API expects for its `$league: String!` query variable
 * (RESEARCH.md §4.4). Best-effort: the `novig-liquidity` package's own README only demonstrates
 * `"NFL"` and `"WNBA"` literally; the rest are inferred from the same all-caps-abbreviation
 * convention and not yet confirmed against a real response. A sport with no entry here — soccer
 * and MMA, currently — is simply skipped for the Novig leg (RESEARCH.md §4.4), the same way
 * [com.tjshea.vigilant.data.scanner.EvScanner] skips an unmapped Novig market type, rather than
 * guessing a league string that might silently return zero events every time.
 */
object NovigLeagues {
    private val bySportKey: Map<String, String> = mapOf(
        "americanfootball_nfl" to "NFL",
        "americanfootball_ncaaf" to "NCAAF",
        "basketball_nba" to "NBA",
        "basketball_ncaab" to "NCAAB",
        "basketball_wnba" to "WNBA",
        "baseball_mlb" to "MLB",
        "icehockey_nhl" to "NHL",
    )

    fun forSportKey(sportKey: String): String? = bySportKey[sportKey]
}
