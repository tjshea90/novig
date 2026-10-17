package com.tjshea.vigilant.data.novig.lab

import org.junit.Assert.assertEquals
import org.junit.Test

/** TCL94 (Tj, 2026-10-17): ESPN's plain college-football scoreboard lists ~16 featured games, so most live games read "no score"; FBS and FCS are asked by group. */
class ScoreboardUrlsTest {
    private val base = "https://site.api.espn.com/apis/site/v2/sports"

    @Test
    fun `college football asks FBS and FCS, college basketball Division I, the pro leagues the plain board`() {
        assertEquals(
            listOf("$base/football/college-football/scoreboard?groups=80&limit=400", "$base/football/college-football/scoreboard?groups=81&limit=400"),
            LabClock.scoreboardUrls("NCAAF", "football/college-football"),
        )
        assertEquals(listOf("$base/basketball/mens-college-basketball/scoreboard?groups=50&limit=400"), LabClock.scoreboardUrls("NCAAB", "basketball/mens-college-basketball"))
        assertEquals(listOf("$base/football/nfl/scoreboard"), LabClock.scoreboardUrls("NFL", "football/nfl"))
        assertEquals(listOf("$base/football/nfl/scoreboard?_cb=7"), LabClock.scoreboardUrls("NFL", "football/nfl", "_cb=7"))
        assertEquals(listOf("$base/football/college-football/scoreboard?groups=80&limit=400&_cb=7", "$base/football/college-football/scoreboard?groups=81&limit=400&_cb=7"), LabClock.scoreboardUrls("NCAAF", "football/college-football", "_cb=7"))
    }
}
