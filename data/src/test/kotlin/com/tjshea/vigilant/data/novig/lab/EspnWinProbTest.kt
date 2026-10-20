package com.tjshea.vigilant.data.novig.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CH46 (Tj, 2026-10-20): ESPN's win probability (game summary) cross-checks the live model; the id rides on the scoreboard. */
class EspnWinProbTest {
    @Test
    fun `the newest home win probability is read, a tie counting half, and a summary without one gives null`() {
        assertEquals(0.62, LabClock.parseWinProb("""{"winprobability":[{"homeWinPercentage":0.5},{"homeWinPercentage":0.62,"tiePercentage":0.0}]}""")!!, 1e-9)
        assertEquals(0.55, LabClock.parseWinProb("""{"winprobability":[{"homeWinPercentage":0.5,"tiePercentage":0.1}]}""")!!, 1e-9)
        assertNull(LabClock.parseWinProb("""{"winprobability":[]}"""))
        assertNull(LabClock.parseWinProb("{}"))
        assertNull(LabClock.parseWinProb("not json"))
    }

    @Test
    fun `the scoreboard carries ESPN's event id and the summary url uses it`() {
        val g = LabClock.parseEspn("""{"events":[{"id":"401","status":{"type":{"state":"in"}},"competitions":[{"competitors":[{"homeAway":"home","score":"1","team":{"displayName":"A","abbreviation":"A"}},{"homeAway":"away","score":"0","team":{"displayName":"B","abbreviation":"B"}}]}]}]}""").single()
        assertEquals("401", g.id)
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/football/nfl/summary?event=401", LabClock.winProbUrl("football/nfl", g.id))
    }
}
