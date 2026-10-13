package com.tjshea.vigilant.data.boltodds

import org.junit.Assert.assertEquals
import org.junit.Test

/** The remembered answers of [BoltMarkets] are the definition's, for every shape of outcome (Tj's 2026-10-13 file: classifying 215,000 outcomes again on every pass made the BoltOdds refresh take 25-45 s). */
class BoltMarketsMemoTest {
    private val names = listOf(
        "Moneyline", "Spread", "Total", "Total Points", "Team Total", "Alternate Spread", "Alt Total", "1st Half Moneyline", "1st Half Total", "First 5 Innings Total", "1st Inning Total Runs", "Q1 Spread",
        "2nd Half Spread", "Player Points", "Rebounds", "Home Runs", "Passing Yards", "Receiving Yards", "Shots on Goal", "Goals", "Hits Runs RBIs", "Totals", "Team Total Points", "Fight Winner", "Set 1 Winner",
        "Anytime Touchdown", "Strikeouts", "Pass Completions", "Points Assists Rebounds", "Run Line", "Puck Line", "Mystery Market", "",
    )
    private val leagues = listOf("NFL", "NBA", "MLB", "NHL", "NCAAF", "WNBA", "ATP")

    private fun outcome(name: String, line: Double?, ou: String?) = BoltOutcome("l", -110, name, line, ou, "T", 0L)

    @Test fun `every name in every shape classifies the same remembered or not, and a second call agrees with the first`() {
        for (league in leagues) for (name in names) for (line in listOf(null, 1.5)) for (ou in listOf(null, "Over", "Under")) {
            val o = outcome(name, line, ou)
            val expected = BoltMarkets.classifyUncached(league, o)
            assertEquals("$league | $name | $line | $ou", expected, BoltMarkets.classify(league, o))
            assertEquals("$league | $name | $line | $ou (remembered)", expected, BoltMarkets.classify(league, o))
        }
    }

    @Test fun `usable names agree with the definition for lines and for props`() {
        for (league in leagues) for (name in names) for (props in listOf(false, true)) {
            val expected = BoltMarkets.usableNameUncached(league, name, props)
            assertEquals("$league | $name | $props", expected, BoltMarkets.usableName(league, name, props))
            assertEquals("$league | $name | $props (remembered)", expected, BoltMarkets.usableName(league, name, props))
        }
    }
}
