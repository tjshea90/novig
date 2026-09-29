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
    fun `ESPN's NBA box score also reads steals, blocks, turnovers and every sum Novig lists`() = runTest {
        routes["/espn/basketball/nba/summary"] = res("espn-nba-summary.json")
        val game = GameScore("x", "NBA", "Cleveland Cavaliers", "Toronto Raptors", 0, true, false, 1, 0)
        val harden = feeds().players(game)!!.first { it.name == "James Harden" }.stats
        assertEquals(28.0, harden.getValue("POINTS"), 0.0)
        assertEquals(5.0, harden.getValue("STEALS"), 0.0)
        assertEquals(1.0, harden.getValue("BLOCKS"), 0.0)
        assertEquals(5.0, harden.getValue("TURNOVERS"), 0.0)
        assertEquals(33.0, harden.getValue("POINTS_REBOUNDS_ASSISTS") - harden.getValue("ASSISTS"), 0.0)
        assertEquals(33.0, harden.getValue("POINTS_REBOUNDS"), 0.0)
        assertEquals(32.0, harden.getValue("POINTS_ASSISTS"), 0.0)
        assertEquals(9.0, harden.getValue("REBOUNDS_ASSISTS"), 0.0)
        assertEquals(6.0, harden.getValue("STEALS_BLOCKS"), 0.0)
        // 28 points is one category of ten or more: no double-double.
        assertEquals(0.0, harden.getValue("DOUBLE_DOUBLE"), 0.0)
        assertEquals(0.0, harden.getValue("TRIPLE_DOUBLE"), 0.0)
    }

    @Test
    fun `double and triple doubles count the categories of ten or more`() {
        fun box(pts: Int, reb: Int, ast: Int, stl: Int = 0, blk: Int = 0) = Json.parseToJsonElement(
            """{"boxscore":{"players":[{"statistics":[{"keys":["points","rebounds","assists","steals","blocks","threePointFieldGoalsMade-threePointFieldGoalsAttempted"],"athletes":[{"athlete":{"displayName":"P"},"stats":["$pts","$reb","$ast","$stl","$blk","1-2"]}]}]}]}}""",
        ).let(FreeScores::parseEspnBox).single().stats
        assertEquals(1.0, box(22, 11, 4).getValue("DOUBLE_DOUBLE"), 0.0)
        assertEquals(0.0, box(22, 11, 4).getValue("TRIPLE_DOUBLE"), 0.0)
        assertEquals(1.0, box(12, 10, 10).getValue("TRIPLE_DOUBLE"), 0.0)
        assertEquals(0.0, box(9, 9, 9, 9, 9).getValue("DOUBLE_DOUBLE"), 0.0)
        // Ten steals + ten blocks count too.
        assertEquals(1.0, box(2, 2, 2, 10, 10).getValue("DOUBLE_DOUBLE"), 0.0)
    }

    @Test
    fun `ESPN's hockey box score reads goals, assists, points, shots on goal, blocks and a goalie's saves`() = runTest {
        routes["/espn/hockey/nhl/summary"] = res("espn-nhl-summary.json")
        val game = GameScore("401803573", "NHL", "Detroit Red Wings", "Minnesota Wild", 0, true, false, 4, 5)
        val lines = feeds().players(game)!!.associateBy { it.name }
        val boldy = lines.getValue("Matt Boldy").stats
        assertEquals(1.0, boldy.getValue("PLAYER_GOALS"), 0.0)
        assertEquals(1.0, boldy.getValue("ASSISTS"), 0.0)
        assertEquals(2.0, boldy.getValue("POINTS"), 0.0) // hockey points: goals + assists
        assertEquals(2.0, boldy.getValue("GOALS_ASSISTS"), 0.0)
        assertEquals(3.0, boldy.getValue("SHOTS_ON_GOAL"), 0.0)
        assertEquals(2.0, boldy.getValue("BLOCKED_SHOTS"), 0.0)
        assertNull(boldy["REBOUNDS"]) // no basketball sums on a hockey player
        assertNull(boldy["POINTS_REBOUNDS_ASSISTS"])
        // A defenseman is in his own group.
        assertEquals(4.0, lines.getValue("Brock Faber").stats.getValue("BLOCKED_SHOTS"), 0.0)
        assertEquals(1.0, lines.getValue("Brock Faber").stats.getValue("POINTS"), 0.0)
        // The goalie's line is saves, not skater stats.
        val goalie = lines.getValue("Filip Gustavsson").stats
        assertEquals(20.0, goalie.getValue("SAVES"), 0.0)
        assertEquals(4.0, goalie.getValue("GOALS_AGAINST"), 0.0)
    }

    @Test
    fun `a player listed under skaters and forwards has his hockey line set once, not added twice`() {
        val json = Json.parseToJsonElement(
            """{"boxscore":{"players":[{"statistics":[
                {"name":"forwards","keys":["goals","assists","shotsTotal","blockedShots","hits","takeaways"],"athletes":[{"athlete":{"displayName":"F"},"stats":["1","0","4","0","1","0"]}]},
                {"name":"skaters","keys":["goals","assists","shotsTotal","blockedShots","hits","takeaways"],"athletes":[{"athlete":{"displayName":"F"},"stats":["1","0","4","0","1","0"]}]}
            ]}]}}""",
        )
        assertEquals(4.0, FreeScores.parseEspnBox(json).single().stats.getValue("SHOTS_ON_GOAL"), 0.0)
    }

    @Test
    fun `ESPN's football box score reads a defender's tackles`() {
        val json = Json.parseToJsonElement(
            """{"boxscore":{"players":[{"statistics":[
                {"name":"defensive","keys":["totalTackles","soloTackles","sacks","tacklesForLoss","passesDefended","QBHits","defensiveTouchdowns"],"athletes":[{"athlete":{"displayName":"Roquan Smith"},"stats":["11","7","1.5","2","0","1","0"]}]}
            ]}]}}""",
        )
        val smith = FreeScores.parseEspnBox(json).single().stats
        assertEquals(11.0, smith.getValue("TACKLES_ASSISTS"), 0.0)
        assertEquals(1.5, smith.getValue("SACKS"), 0.0)
    }

    @Test
    fun `ESPN's tennis scoreboard gives sets won and each set's games, by tour, singles only`() = runTest {
        routes["/espn/tennis/atp/scoreboard"] = res("espn-tennis-scoreboard.json")
        routes["/espn/tennis/wta/scoreboard"] = res("espn-tennis-scoreboard.json")
        val f = feeds()
        val men = f.games("ATP", LocalDate.of(2026, 9, 22))!!
        assertEquals(3, men.size) // the doubles and the women's matches aren't the ATP's singles
        val muller = men.first { it.id == "186127" }
        assertEquals("Alexandre Muller", muller.home)
        assertEquals("Luka Pavlovic", muller.away)
        assertTrue(muller.final)
        assertEquals(2, muller.homeScore) // sets
        assertEquals(1, muller.awayScore)
        assertEquals(listOf(6, 6, 6), muller.homePeriods) // games per set (the 7-6 set's tiebreak isn't a game)
        assertEquals(listOf(4, 7, 3), muller.awayPeriods)
        assertTrue(muller.tennis)
        assertFalse(men.first { it.id == "183422" }.final) // scheduled
        val women = f.games("WTA", LocalDate.of(2026, 9, 22))!!
        assertEquals(setOf("186226", "184060", "184093"), women.map { it.id }.toSet())
        val retired = women.first { it.id == "184060" }
        assertTrue(retired.called)
        assertFalse(retired.final)
        assertEquals("Retired", retired.calledReason)
        assertEquals("Walkover", women.first { it.id == "184093" }.calledReason)
        // A tennis match has no box score: an empty one (never "couldn't be read").
        assertEquals(emptyList<PlayerLine>(), f.players(muller))
        assertTrue(f.covers("ATP") && f.covers("wta"))
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
