package com.tjshea.vigilant.data.tracker

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes follow PropLine's docs (`/scores`, `/events/{id}/stats`; research/propline/llms-full_2026-10-16.txt) until the probe saves real answers. */
class PropLineScoresTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun scoresBecomeGames() {
        val raw = """[{"id":"12649","home_team":"Baltimore Orioles","home_score":5,"away_team":"Houston Astros","away_score":3,"completed":true,"commence_time":"2026-04-30T20:05:00Z"},
            {"id":"1","home_team":"A","home_score":null,"away_team":"B","away_score":null,"completed":false,"status":"postponed","commence_time":"2026-04-30T23:05:00Z"}]"""
        val g = PropLineScores.parseGames(raw, json, "MLB")
        assertEquals("pl:12649", g[0].id); assertTrue(g[0].final); assertEquals(5, g[0].homeScore); assertEquals(3, g[0].awayScore)
        assertTrue(g[1].called); assertFalse(g[1].final); assertEquals("Postponed", g[1].calledReason)
    }

    @Test
    fun aFinalBoxScoreIsMappedToNovigStatsAndALiveOneIsNotUsed() {
        val final = """{"status":"final","stats":[{"player_name":"Yordan Alvarez","team_abbr":"HOU","stat_type":"hits","stat_value":2},{"player_name":"Yordan Alvarez","stat_type":"total_bases","stat_value":5},
            {"player_name":"Yordan Alvarez","stat_type":"at_bats","stat_value":4},{"player_name":"Framber Valdez","stat_type":"outs","stat_value":19}]}"""
        val box = PropLineScores.parseBox(final, json, "baseball_mlb")!!
        assertEquals(mapOf("HITS" to 2.0, "TOTAL_BASES" to 5.0), box.first { it.name == "Yordan Alvarez" }.stats)
        assertEquals(19.0, box.first { it.name == "Framber Valdez" }.stats["PITCHER_OUTS"]!!, 0.0)
        assertNull(PropLineScores.parseBox("""{"status":"in_progress","stats":[{"player_name":"A","stat_type":"hits","stat_value":1}]}""", json, "baseball_mlb"))
    }

    @Test
    fun statNamesFollowTheSport() {
        assertEquals("PLAYER_GOALS", PropLineStatMap.novig("icehockey_nhl", "goals"))
        assertEquals("POINTS", PropLineStatMap.novig("basketball_nba", "points"))
        assertEquals("RECEIVING_YARDS", PropLineStatMap.novig("americanfootball_nfl", "receiving_yards"))
        assertNull(PropLineStatMap.novig("tennis", "aces"))
    }
}
