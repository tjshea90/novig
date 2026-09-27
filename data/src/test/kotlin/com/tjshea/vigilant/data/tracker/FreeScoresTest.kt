package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The score feeds behind the Tracker's auto-settle, read from REAL replies saved 2026-09-27 (ESPN's
 * NFL scoreboard and box score for Falcons 35 @ Packers 14, WNBA box score; MLB's Stats API schedule
 * and box score for Mets 7 @ Nationals 1), trimmed to what's parsed.
 */
class FreeScoresTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = HashMap<String, String>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.requestUrl!!.encodedPath]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun feeds() = FreeScores(
        OkHttpClient(), json,
        espnBase = server.url("/espn").toString().trimEnd('/'),
        mlbBase = server.url("/mlb").toString().trimEnd('/'),
        gapMs = 0,
    )

    @Test
    fun `ESPN's scoreboard gives the final score by quarter, read once per league and day`() = runTest {
        routes["/espn/football/nfl/scoreboard"] = res("espn-nfl-scoreboard.json")
        val f = feeds()
        val g = f.games("NFL", LocalDate.of(2026, 9, 24))!!.single()
        assertEquals("20260924", requests.single().requestUrl!!.queryParameter("dates"))
        assertEquals("Green Bay Packers", g.home)
        assertEquals("Atlanta Falcons", g.away)
        assertTrue(g.final)
        assertFalse(g.called)
        assertEquals(14, g.homeScore)
        assertEquals(35, g.awayScore)
        assertEquals(listOf(7, 10, 7, 11), g.awayPeriods)
        f.games("NFL", LocalDate.of(2026, 9, 24))
        assertEquals(1, requests.size)
    }

    @Test
    fun `ESPN's football box score reads as Novig's stats`() = runTest {
        routes["/espn/football/nfl/summary"] = res("espn-nfl-summary.json")
        val game = GameScore("401872948", "NFL", "Green Bay Packers", "Atlanta Falcons", 0, true, false, 14, 35)
        val lines = feeds().players(game)!!.associateBy { it.name }
        val penix = lines.getValue("Michael Penix Jr.").stats
        assertEquals(256.0, penix.getValue("PASSING_YARDS"), 0.0)
        assertEquals(18.0, penix.getValue("PASSING_COMPLETIONS"), 0.0)
        assertEquals(25.0, penix.getValue("PASSING_ATTEMPTS"), 0.0)
        assertEquals(1.0, penix.getValue("INTERCEPTIONS_THROWN"), 0.0)
        val bijan = lines.getValue("Bijan Robinson").stats
        assertEquals(194.0, bijan.getValue("RUSHING_YARDS"), 0.0)
        assertEquals(2.0, bijan.getValue("RECEPTIONS"), 0.0)
        // Two rushing touchdowns, none receiving.
        assertEquals(2.0, bijan.getValue("TOUCHDOWNS"), 0.0)
        assertEquals(213.0, bijan.getValue("RUSHING_AND_RECEIVING_YARDS"), 0.0)
        val folk = lines.getValue("Nick Folk").stats
        assertEquals(2.0, folk.getValue("FIELD_GOALS_MADE"), 0.0)
        assertEquals(9.0, folk.getValue("KICKING_POINTS"), 0.0)
        assertEquals("401872948", requests.single().requestUrl!!.queryParameter("event"))
    }

    @Test
    fun `ESPN's basketball box score reads points, rebounds, assists and threes`() = runTest {
        routes["/espn/basketball/wnba/summary"] = res("espn-wnba-summary.json")
        val game = GameScore("x", "WNBA", "H", "A", 0, true, false, 1, 0)
        val harrison = feeds().players(game)!!.first { it.name == "Isabelle Harrison" }.stats
        assertEquals(19.0, harrison.getValue("POINTS"), 0.0)
        assertEquals(10.0, harrison.getValue("REBOUNDS"), 0.0)
        assertEquals(0.0, harrison.getValue("ASSISTS"), 0.0)
        assertEquals(0.0, harrison.getValue("THREE_POINTERS_MADE"), 0.0)
        assertEquals(29.0, harrison.getValue("POINTS_REBOUNDS_ASSISTS"), 0.0)
    }

    @Test
    fun `MLB's own schedule gives the final score by inning, and its box score every prop stat`() = runTest {
        routes["/mlb/schedule"] = res("mlb-schedule.json")
        routes["/mlb/game/822678/boxscore"] = res("mlb-boxscore.json")
        val f = feeds()
        val games = f.games("MLB", LocalDate.of(2026, 9, 26))!!
        assertEquals("linescore", requests.single().requestUrl!!.queryParameter("hydrate"))
        val mets = games.first { it.id == "822678" }
        assertEquals("New York Mets", mets.away)
        assertEquals(7, mets.awayScore)
        assertEquals(1, mets.homeScore)
        assertTrue(mets.final)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 4, 1, 2), mets.awayPeriods)
        val lines = f.players(mets)!!.associateBy { it.name }
        assertEquals(4.0, lines.getValue("Carson Benge").stats.getValue("TOTAL_BASES"), 0.0)
        assertEquals(2.0, lines.getValue("Tobias Myers").stats.getValue("PITCHER_STRIKEOUTS"), 0.0)
        // A pitcher's line has no batting stats, and the other way around.
        assertNull(lines.getValue("Tobias Myers").stats["HITS"])
        assertNull(lines.getValue("Carson Benge").stats["PITCHER_STRIKEOUTS"])
    }

    @Test
    fun `leagues without a feed aren't asked`() = runTest {
        val f = feeds()
        assertFalse(f.covers("UFC"))
        assertTrue(f.covers("MLB") && f.covers("NCAAF") && f.covers("wnba"))
        assertNull(f.games("UFC", LocalDate.of(2026, 9, 26)))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a game's Eastern date is the one its scoreboard lists it under`() {
        // Falcons @ Packers kicked off 2026-09-25 00:15 UTC: Thursday night in the East.
        assertEquals(LocalDate.of(2026, 9, 24), FreeScores.etDate(java.time.Instant.parse("2026-09-25T00:15:00Z").toEpochMilli()))
    }
}
