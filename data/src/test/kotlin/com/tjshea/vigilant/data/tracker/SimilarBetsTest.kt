package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The similar-bets key on the label shapes Tj's own bets carry (phone-data bets-2026-10-24.jsonl): Novig's names for imported fills ("SYR @ UNC t52.5", "Miami Heat 115.5 TEAM_TOTAL", "CIT +17.5"),
 * the Bet sheet's ("Total", "Team Total", "Points" + "Trae Young Under 24.5") and the engine's ("1H Total", "ML BOS yes", "Total 5.5 under").
 */
class SimilarBetsTest {
    private val start = 1_792_884_600_000L
    private fun fam(event: String, market: String, selection: String) = RepeatGuard.family(event, start, market, selection)

    @Test fun `a game's overs at every line and from every source are one bet (Tj's SYR at UNC video)`() {
        val g = "Syracuse @ North Carolina"
        val novig = listOf(fam(g, "SYR @ UNC t52.5", "Over 52.5"), fam(g, "SYR @ UNC t50.5", "Over 50.5"), fam(g, "SYR @ UNC t49.5", "Over 49.5"), fam(g, "SYR @ UNC t51.5", "Over 51.5"))
        val sheet = listOf(fam(g, "Total", "Over 50.5"), fam(g, "Total Points", "Over 51.5"), fam(g, "1H Total", "Over 24.5"), fam(g, "Total", "Total 49.5 over"))
        assertEquals(1, (novig + sheet).toSet().size)
        assertNotEquals(novig[0], fam(g, "SYR @ UNC t52.5", "Under 52.5"))
        assertNotEquals(novig[0], fam("Georgia State @ Arkansas State", "GAST @ ARST t60.5", "Over 60.5"))
        assertEquals(fam(g, "HOU @ ATL t110.5 1H", "Over 110.5"), fam(g, "Total", "Over 49.5"))
    }

    @Test fun `a team's total is its own bet, named by Novig or by the sheet`() {
        val g = "Miami Heat @ Charlotte Hornets"
        assertEquals(fam(g, "Miami Heat 115.5 TEAM_TOTAL", "Under 115.5"), fam(g, "Team Total", "Miami Heat Under 113.5"))
        assertEquals(fam(g, "Miami Heat 115.5 TEAM_TOTAL", "Under 115.5"), fam(g, "Team Total Points", "Miami Heat Under 112.5"))
        assertNotEquals(fam(g, "Miami Heat 115.5 TEAM_TOTAL", "Under 115.5"), fam(g, "MIA @ CHA t229.5", "Under 229.5"))
        assertNotEquals(fam(g, "Miami Heat 115.5 TEAM_TOTAL", "Under 115.5"), fam(g, "Team Total", "Charlotte Hornets Under 115.5"))
        assertEquals(fam("Carolina Hurricanes @ St. Louis Blues", "St. Louis Blues 3.5 TEAM_TOTAL", "Under 3.5"), fam("Carolina Hurricanes @ St. Louis Blues", "Team Total", "St. Louis Blues Under 2.5"))
        assertEquals(fam("Memphis Grizzlies @ Utah Jazz", "Team Total", "Memphis Under 110.5"), fam("Memphis Grizzlies @ Utah Jazz", "Memphis Grizzlies 109.5 TEAM_TOTAL", "Under 109.5"))
    }

    @Test fun `a team's spreads and moneyline are one side, short codes included`() {
        val g = "Tennessee Tech @ The Citadel"
        assertEquals(fam(g, "CIT +17.5", "TNTC -17.5"), fam(g, "Point Spread", "Tennessee Tech -14.5"))
        assertEquals(fam(g, "CIT +17.5", "CIT +17.5"), fam(g, "Spread", "The Citadel +20.5"))
        assertEquals(fam(g, "CIT +17.5", "CIT +17.5"), fam(g, "Moneyline", "The Citadel"))
        assertNotEquals(fam(g, "CIT +17.5", "CIT +17.5"), fam(g, "CIT +17.5", "TNTC -17.5"))
        assertEquals(fam("Colorado @ Oklahoma State", "OKST -3.5", "OKST -3.5"), fam("Colorado @ Oklahoma State", "Spread", "Oklahoma State -6.5"))
        assertEquals(fam("North Dakota State @ New Mexico", "UNM -2.5 1H", "NDSU +2.5"), fam("North Dakota State @ New Mexico", "Spread", "North Dakota State +4.5"))
        assertEquals(fam("North Dakota State @ New Mexico", "UNM -2.5 1H", "UNM -2.5"), fam("North Dakota State @ New Mexico", "Moneyline", "New Mexico"))
        assertEquals(fam("Campbell @ Maine", "ME +3.5", "ME +3.5"), fam("Campbell @ Maine", "Spread", "Maine +7.5"))
        assertEquals(fam("Utah Tech @ Cal Poly", "CP -6.5", "UTU +6.5"), fam("Utah Tech @ Cal Poly", "Spread", "Utah Tech +6.5"))
        // "ML BOS yes" is Boston's side; "no" is the other team's.
        assertEquals(fam("Boston Celtics @ New York Knicks", "Moneyline", "ML BOS yes"), fam("Boston Celtics @ New York Knicks", "Spread", "Boston Celtics -2.5"))
        assertEquals(fam("Boston Celtics @ New York Knicks", "Moneyline", "ML BOS no"), fam("Boston Celtics @ New York Knicks", "Spread", "New York Knicks +2.5"))
    }

    @Test fun `a player's unders are one bet whatever the stat and line, his overs another, another player another`() {
        val g = "Washington Wizards @ Chicago Bulls"
        val pra = fam(g, "Kyshawn George 21.5 POINTS_REBOUNDS_ASSISTS", "Under 21.5")
        assertEquals(pra, fam(g, "Points", "Kyshawn George Under 14.5"))
        assertEquals(pra, fam(g, "Kyshawn George 20.5 POINTS_REBOUNDS_ASSISTS", "Under 20.5"))
        assertNotEquals(pra, fam(g, "Points", "Kyshawn George Over 14.5"))
        assertNotEquals(pra, fam(g, "Anthony Davis 22.5 POINTS", "Under 22.5"))
        // a player is never read as the game's total or a team's
        assertNotEquals(pra, fam(g, "WAS @ CHI t229.5", "Under 229.5"))
        assertEquals(fam("Miami Dolphins @ New York Jets", "Ollie Gordon II 53.5 RUSHING_YARDS", "Under 53.5"), fam("Miami Dolphins @ New York Jets", "Rushing Yards", "Ollie Gordon II Under 49.5"))
    }

    @Test fun `short codes match the right team`() {
        val t = listOf("Syracuse", "North Carolina")
        assertEquals(0, SimilarBets.role("SYR", t))
        assertEquals(1, SimilarBets.role("UNC", t))
        assertEquals(1, SimilarBets.role("TNST", listOf("Charleston Southern", "Tennessee State")))
        assertEquals(1, SimilarBets.role("MORE", listOf("Stetson", "Morehead State")))
        assertEquals(1, SimilarBets.role("LINDEN", listOf("Chicago State", "Lindenwood")))
        assertEquals(0, SimilarBets.role("CHST", listOf("Chicago State", "Lindenwood")))
        assertEquals(null, SimilarBets.role("XYZ", t))
        assertEquals(null, SimilarBets.role("Joel Embiid", listOf("Milwaukee Bucks", "Philadelphia 76ers")))
    }

    @Test fun `another day's game and a bet nobody can read stay apart`() {
        val g = "Syracuse @ North Carolina"
        assertNotEquals(fam(g, "Total", "Over 50.5"), RepeatGuard.family(g, start + 24 * 3_600_000L, "Total", "Over 50.5"))
        assertEquals("", fam(g, "Total", ""))
        assertEquals("", fam("", "Total", "Over 50.5"))
    }
}
