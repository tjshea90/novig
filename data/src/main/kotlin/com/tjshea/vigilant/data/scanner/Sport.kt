package com.tjshea.vigilant.data.scanner

/**
 * A sport The Odds API can be queried for, keyed the way its API expects (RESEARCH.md §4.3). This
 * is a curated subset of the sports The Odds API documents (it lists ~70, including many
 * niche/regional soccer leagues), chosen to cover the major US leagues plus a couple of the
 * biggest non-US markets — not exhaustive. Sport keys follow The Odds API's own documented
 * `sport_league` naming convention, but the exact set of currently *in-season* keys should be
 * confirmed against a live `GET /v4/sports` call before assuming every entry here is active
 * year-round — a reasonable follow-up, not solved here.
 */
data class Sport(val key: String, val displayName: String)

object SportsCatalog {
    val ALL: List<Sport> = listOf(
        Sport("americanfootball_nfl", "NFL"),
        Sport("americanfootball_ncaaf", "NCAAF"),
        Sport("basketball_nba", "NBA"),
        Sport("basketball_ncaab", "NCAAB"),
        Sport("basketball_wnba", "WNBA"),
        Sport("baseball_mlb", "MLB"),
        Sport("icehockey_nhl", "NHL"),
        Sport("soccer_epl", "Soccer — EPL"),
        Sport("soccer_usa_mls", "Soccer — MLS"),
        Sport("mma_mixed_martial_arts", "MMA"),
    )
}
