package com.tjshea.vigilant.data.teams

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections

/** Player teams from ESPN for the widget's "D. Schultz (HOU)" (Tj, 2026-09-26). Made-up rosters. */
class PlayerTeamsTest {

    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val agents: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var fail = false

    private val teams = """{"sports":[{"leagues":[{"teams":[
        {"team":{"id":"34","abbreviation":"HOU","displayName":"Houston Texans","shortDisplayName":"Texans","location":"Houston","name":"Texans"}},
        {"team":{"id":"11","abbreviation":"IND","displayName":"Indianapolis Colts","shortDisplayName":"Colts","location":"Indianapolis","name":"Colts"}},
        {"team":{"id":"24","abbreviation":"LAC","displayName":"Los Angeles Chargers","shortDisplayName":"Chargers","location":"Los Angeles","name":"Chargers"}},
        {"team":{"id":"14","abbreviation":"LAR","displayName":"Los Angeles Rams","shortDisplayName":"Rams","location":"Los Angeles","name":"Rams"}}
    ]}]}]}"""

    // Football groups players by position; other sports list them flat.
    private val houston = """{"athletes":[{"position":"offense","items":[{"displayName":"Dalton Schultz","shortName":"D. Schultz"},{"displayName":"Nico Collins"}]},
        {"position":"defense","items":[{"displayName":"Will Anderson Jr."}]}]}"""
    private val indy = """{"athletes":[{"displayName":"Jonathan Taylor"},{"displayName":"Michael Pittman Jr."}]}"""

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                paths += path
                agents += request.getHeader("User-Agent").orEmpty()
                if (fail) return MockResponse().setResponseCode(500)
                // ESPN's CDN refuses a User-Agent naming the app (or a browser's, from an app).
                if (request.getHeader("User-Agent").orEmpty().contains("Vigilant")) return MockResponse().setResponseCode(403).setBody("<HTML>Access Denied</HTML>")
                // A 24-team league for the full-slate test: teams 1..24, one player each.
                Regex("""^/hockey/nhl/teams/(\d+)/roster$""").find(path)?.let { m ->
                    return MockResponse().setBody("""{"athletes":[{"displayName":"Skater ${m.groupValues[1]}"}]}""")
                }
                if (path.startsWith("/hockey/nhl/teams?")) {
                    val list = (1..24).joinToString(",") { """{"team":{"id":"$it","abbreviation":"T$it","displayName":"Team $it"}}""" }
                    return MockResponse().setBody("""{"sports":[{"leagues":[{"teams":[$list]}]}]}""")
                }
                return when {
                    path.startsWith("/football/nfl/teams?") -> MockResponse().setBody(teams)
                    path == "/football/nfl/teams/34/roster" -> MockResponse().setBody(houston)
                    path == "/football/nfl/teams/11/roster" -> MockResponse().setBody(indy)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun teamsClient(file: File? = null, clock: () -> Long = { 1_000L }) = PlayerTeams(
        OkHttpClient(),
        store = file?.let { JsonFileStore(it, TeamsCache.serializer(), { TeamsCache() }) },
        clock = clock,
        baseUrl = server.url("/").toString(),
    )

    private fun row(bet: String, market: String = "Player Receptions", event: String = "Houston Texans @ Indianapolis Colts", league: String = "NFL") =
        CnoRow(0.02, league = league, event = event, market = market, bet = bet, odds = 141, book = "Novig")

    @Test
    fun `a player's team comes from the two teams' rosters, with suffixes and short first names matched`() = runBlocking {
        val t = teamsClient()
        val rows = listOf(row("Dalton Schultz Over 5.5"), row("Michael Pittman Over 50.5", market = "Player Receiving Yards"), row("Mike Pittman Jr. Under 4.5"))
        assertEquals(3, t.fill(PlayerTeams.gamesOf(rows)))
        val cache = t.state.value
        assertEquals("HOU", cache.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Dalton Schultz"))
        assertEquals("IND", cache.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Michael Pittman"))
        assertEquals("IND", cache.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Mike Pittman Jr."))
        assertEquals("HOU", cache.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Will Anderson"))
        // Not on either roster, or a league without rosters: no tag rather than a guess.
        assertNull(cache.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Someone Else"))
        assertNull(cache.teamOf("KBO", "A @ B", "Dalton Schultz"))
    }

    @Test
    fun `game bets need no rosters, a game already read costs nothing, and what's read survives a restart`() = runBlocking {
        val file = File(tmp.root, "teams.json")
        val t = teamsClient(file)
        assertEquals(0, t.fill(PlayerTeams.gamesOf(listOf(row("Houston Texans -3.5", market = "Point Spread"), row("Over 47.5", market = "Total Points")))))
        assertEquals(3, t.fill(PlayerTeams.gamesOf(listOf(row("Dalton Schultz Over 5.5")))))
        assertEquals(0, t.fill(PlayerTeams.gamesOf(listOf(row("Nico Collins Over 60.5")))))
        val reopened = teamsClient(file)
        reopened.load()
        assertEquals("HOU", reopened.state.value.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Nico Collins"))
        assertEquals(0, reopened.fill(PlayerTeams.gamesOf(listOf(row("Nico Collins Over 60.5")))))
    }

    @Test
    fun `a failed read is left alone for half an hour, then tried again`() = runBlocking {
        var now = 1_000L
        val t = teamsClient(clock = { now })
        fail = true
        val games = PlayerTeams.gamesOf(listOf(row("Dalton Schultz Over 5.5")))
        assertEquals(1, t.fill(games)) // the teams list fails; no roster can be looked for yet
        assertEquals(0, t.fill(games))
        fail = false
        now += PlayerTeams.RETRY_MS
        assertEquals(3, t.fill(games))
        assertEquals("HOU", t.state.value.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Dalton Schultz"))
    }

    @Test
    fun `rosters are asked for with OkHttp's own User-Agent, which ESPN's CDN lets through`() = runBlocking {
        val t = teamsClient()
        assertEquals(3, t.fill(PlayerTeams.gamesOf(listOf(row("Dalton Schultz Over 5.5")))))
        assertEquals("HOU", t.state.value.teamOf("NFL", "Houston Texans @ Indianapolis Colts", "Dalton Schultz"))
        assertTrue(agents.isNotEmpty() && agents.all { it.startsWith("okhttp/") })
    }

    @Test
    fun `a full slate (more rosters than one pass reads) gets every team at once, not half now and half in 30 minutes`() = runBlocking {
        val t = teamsClient()
        // 12 games, 24 rosters + the team list: more than one pass's 20 reads.
        val rows = MutableStateFlow((1..12).map { g -> row("Skater ${2 * g} Over 0.5", event = "Team ${2 * g - 1} @ Team ${2 * g}", league = "NHL") })
        val job = launch { t.keepFresh(rows) }
        withTimeout(15_000) { while (t.state.value.rosters.size < 24) delay(50) }
        job.cancel()
        assertEquals("T24", t.state.value.teamOf("NHL", "Team 23 @ Team 24", "Skater 24"))
    }

    @Test
    fun `team names - full names first, a city or school alone only when it's one team`() {
        val list = PlayerTeams.parseTeams(kotlinx.serialization.json.Json.parseToJsonElement(teams))
        assertEquals("HOU", PlayerTeams.findTeam("Houston Texans", list)?.abbreviation)
        assertEquals("HOU", PlayerTeams.findTeam("Houston", list)?.abbreviation)
        assertEquals("LAR", PlayerTeams.findTeam("Los Angeles Rams", list)?.abbreviation)
        assertNull(PlayerTeams.findTeam("Los Angeles", list)) // two teams: no guess
        assertEquals(listOf("Houston Texans", "Indianapolis Colts"), PlayerTeams.sides("Houston Texans @ Indianapolis Colts"))
        assertEquals(listOf("Team A", "Team B"), PlayerTeams.sides("Team A vs. Team B"))
        assertEquals("football/nfl", PlayerTeams.espnPath("NFL"))
        assertEquals("soccer/usa.1", PlayerTeams.espnPath("MLS (USA)"))
    }

    @Test
    fun `keepFresh reads for new games only, and nothing once cancelled`() = runBlocking {
        val t = teamsClient()
        val rows = MutableStateFlow(listOf(row("Dalton Schultz Over 5.5")))
        val job = launch { t.keepFresh(rows) }
        withTimeout(10_000) { while (t.state.value.rosters.size < 2) delay(50) }
        val count = paths.size
        // The list re-read with new prices, same game: no reads.
        rows.value = listOf(row("Dalton Schultz Over 5.5").copy(odds = 150), row("Nico Collins Over 60.5"))
        delay(1_000)
        assertEquals(count, paths.size)
        job.cancel()
        // A new game after the scanner closed: nothing.
        rows.value = listOf(row("Somebody Over 1.5", event = "Los Angeles Rams @ Los Angeles Chargers"))
        delay(500)
        assertEquals(count, paths.size)
        assertTrue(paths.all { it.startsWith("/football/nfl/") })
    }
}
