package com.tjshea.vigilant.data.cno

import java.util.Locale

/**
 * The sports and leagues CrazyNinjaOdds' Positive EV page offers (read off the live page, 2026-10-07: its two single-choice dropdowns; RESEARCH.md §85). A row's League cell prints
 * the option's label exactly ("MLS (USA)", "Premier League (England)"), so a pick is kept as the LABEL and matched on it; the numeric ids are only what the form is told when one
 * league or one sport covers the pick (and the page's own option list is preferred to this table: [CnoScope.plan]). No tennis, UFC or boxing exists on CNO.
 */
object CnoLeagues {

    /** CNO's sports, with the id its Sport dropdown takes (0 = All). */
    enum class Sport(val label: String, val id: Int) {
        FOOTBALL("Football", 2), BASKETBALL("Basketball", 4), BASEBALL("Baseball", 1), HOCKEY("Hockey", 3), SOCCER("Soccer", 5),
    }

    /** One league: its label (what a row prints and the dropdown shows), its League dropdown id and its sport. */
    data class League(val label: String, val id: Int, val sport: Sport)

    /** In the order the chips go: the US leagues first, soccer last. */
    val ALL: List<League> = listOf(
        League("NFL", 2, Sport.FOOTBALL), League("NCAAF", 3, Sport.FOOTBALL),
        League("MLB", 1, Sport.BASEBALL),
        League("WNBA", 7, Sport.BASKETBALL), League("NBA", 5, Sport.BASKETBALL), League("NCAAB", 6, Sport.BASKETBALL), League("NCAAW", 8, Sport.BASKETBALL),
        League("NHL", 4, Sport.HOCKEY),
        League("MLS (USA)", 10, Sport.SOCCER), League("Premier League (England)", 16, Sport.SOCCER), League("LaLiga (Spain)", 13, Sport.SOCCER),
        League("Bundesliga (Germany)", 14, Sport.SOCCER), League("Ligue 1 (France)", 15, Sport.SOCCER), League("Serie A (Italy)", 17, Sport.SOCCER),
        League("Serie A (Brazil)", 11, Sport.SOCCER), League("Liga MX (Mexico)", 12, Sport.SOCCER), League("FIFA World Cup", 9, Sport.SOCCER),
    )

    /** The leagues of [sport], in chip order. */
    fun of(sport: Sport): List<League> = ALL.filter { it.sport == sport }

    /** The sports that have a league here, in the order the settings list them. */
    val SPORTS: List<Sport> = listOf(Sport.FOOTBALL, Sport.BASKETBALL, Sport.BASEBALL, Sport.HOCKEY, Sport.SOCCER)

    /** A label as it is compared: trimmed, spaces collapsed, case ignored (and nothing else: "Serie A (Italy)" is never "Serie A (Brazil)"). */
    fun norm(label: String): String = label.trim().replace(Regex("\\s+"), " ").lowercase(Locale.US)

    /** Whether two labels name the same league. */
    fun same(a: String, b: String): Boolean = norm(a) == norm(b)

    /** The league [label] names, or null when it is not one CNO lists (a league CNO added, or a renamed one). */
    fun byLabel(label: String): League? = ALL.firstOrNull { same(it.label, label) }

    /** The sport of a row's Sport cell ("Football"), or null. */
    fun sportByLabel(label: String): Sport? = Sport.entries.firstOrNull { same(it.label, label) }
}
