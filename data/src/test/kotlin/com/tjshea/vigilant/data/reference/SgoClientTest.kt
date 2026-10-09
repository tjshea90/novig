package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.ChainedScores
import com.tjshea.vigilant.data.tracker.CloseLookup
import com.tjshea.vigilant.data.tracker.GameScore
import com.tjshea.vigilant.data.tracker.PlayerLine
import com.tjshea.vigilant.data.tracker.ScoreSource
import com.tjshea.vigilant.data.tracker.SgoCloses
import com.tjshea.vigilant.data.tracker.SgoScores
import com.tjshea.vigilant.data.tracker.TrackedBet
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** The SportsGameOdds client, sources, closes and scores against a mock server speaking the schema in SPORTSGAMEODDS_API.md (no live key yet). */
class SgoClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }
    private var clockMs = 1_790_500_000_000L
    private val nfl = Leagues.byNovigName("NFL")!!
    private val sample = javaClass.getResource("/sgo-events-sample.json")!!.readText()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })
    private fun client(keys: List<String> = listOf("k1")) = SportsGameOddsClient(
        OkHttpClient(), KeyPool(QuotaPolicy.SGO, { keys }, meter), json, server.url("/v2").toString().trimEnd('/'), clock = { clockMs }, minIntervalMs = 0, retryDelayMs = { 0L },
    )
    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private val settings = ScanSettings(referenceBooks = listOf("pinnacle", "draftkings"), families = MarketFamily.entries.toSet())

    @Test fun gamesSourceAsksOnePageWithTheKeyInTheHeaderAndConvertsIt() = runTest {
        handler = { ok(sample.replace("\"nextCursor\": \"n.123.abc\"", "\"nextCursor\": null")) }
        val snap = SgoGamesSource(client(), { clockMs }).odds(nfl, settings)
        val r = requests.single()
        assertEquals("k1", r.getHeader("x-api-key"))
        assertEquals("NFL", r.requestUrl!!.queryParameter("leagueID"))
        assertEquals("true", r.requestUrl!!.queryParameter("oddsAvailable"))
        assertEquals("true", r.requestUrl!!.queryParameter("includeAltLines"))
        assertTrue(r.requestUrl!!.queryParameter("oddID")!!.contains("points-home-game-sp-home"))
        assertFalse("the key never travels in the URL", r.requestUrl.toString().contains("k1"))
        assertEquals("sgo", snap.provider)
        assertEquals(1, snap.events.size)
        assertTrue(snap.events.single().markets.any { it.kind == LineKind.SPREAD && it.line == -6.5 })   // the alternate line
        assertTrue("props are the props source's, not the games call's", snap.events.single().markets.none { it.kind == LineKind.PLAYER_PROP })
    }

    @Test fun morePagesAreFollowedByCursorUntilTheLast() = runTest {
        var n = 0
        handler = { r -> if (r.requestUrl!!.queryParameter("cursor") == null) ok(sample) else { n++; ok("""{"success":true,"data":[],"nextCursor":null}""") } }
        val pages = client().eventsAll(listOf("leagueID" to "NFL"))
        assertEquals(2, pages.pages); assertEquals(1, n); assertEquals(1, pages.events.size)
        assertEquals("Response is missing 1 events", pages.notice)
    }

    @Test fun aQueryTooHeavyForAlternatesIsAskedAgainWithoutThem() = runTest {
        handler = { r -> if (r.requestUrl!!.queryParameter("includeAltLines") != null) MockResponse().setResponseCode(504).setBody("""{"success":false,"error":"timeout"}""") else ok(sample.replace("\"n.123.abc\"", "null")) }
        val snap = SgoGamesSource(client(), { clockMs }).odds(nfl, settings)
        assertEquals(2, requests.size)
        assertNotNull(requests[0].requestUrl!!.queryParameter("includeAltLines"))
        assertNull(requests[1].requestUrl!!.queryParameter("includeAltLines"))
        assertEquals(1, snap.events.size)
    }

    @Test fun aRefusedKeyIsSkippedForTheNextOne() = runTest {
        handler = { r -> if (r.getHeader("x-api-key") == "bad") MockResponse().setResponseCode(401).setBody("""{"success":false,"error":"bad key"}""") else ok(sample.replace("\"n.123.abc\"", "null")) }
        val snap = SgoGamesSource(client(listOf("bad", "good")), { clockMs }).odds(nfl, settings)
        assertEquals(listOf("bad", "good"), requests.map { it.getHeader("x-api-key") })
        assertEquals(1, snap.events.size)
    }

    @Test fun aServerErrorIsRetriedOnceAndTwoFailuresMarkItDown() = runTest {
        handler = { MockResponse().setResponseCode(500) }
        val c = client()
        assertFalse(c.down(clockMs))
        runCatching { c.events(listOf("leagueID" to "NFL")) }
        assertEquals("one retry, no loop", 2, requests.size)
        runCatching { c.events(listOf("leagueID" to "NFL")) }
        clockMs += SportsGameOddsClient.DOWN_AFTER_MS + 1
        assertTrue(c.down(clockMs))
        handler = { ok(sample) }
        c.events(listOf("leagueID" to "NFL"))
        assertFalse("one good answer ends it", c.down(clockMs))
    }

    @Test fun propsSourceAsksThePlayerWildcardForTheLeaguesStats() = runTest {
        handler = { ok(sample.replace("\"n.123.abc\"", "null")) }
        val snap = SgoPropsSource(client(), { clockMs }).odds(nfl, settings)
        val ids = requests.single().requestUrl!!.queryParameter("oddID")!!
        assertTrue("passing_yards-PLAYER_ID-game-ou-over" in ids)
        assertTrue(snap.events.single().markets.any { it.kind == LineKind.PLAYER_PROP && it.stat == "PASSING_YARDS" })
        assertTrue(SgoPropsSource(client(), { clockMs }).supports(nfl))
    }

    @Test fun outsideSgoLeavesTennisToTheOtherFeedsAndSgoLeaguesToSgo() {
        val inner = SgoGamesSource(client(), { clockMs })
        val wrapped = OutsideSgo(inner)
        assertFalse(wrapped.supports(nfl))
        val atp = Leagues.byNovigName("ATP")!!
        assertFalse(SgoBooks.supports(atp))
    }

    @Test fun usageSaysTheLimitsInWords() = runTest {
        handler = { ok("""{"success":true,"data":{"keyID":"x","isActive":true,"rateLimits":{"per-minute":{"maxRequestsPerInterval":300,"maxEntitiesPerInterval":"unlimited","currentIntervalRequests":4,"currentIntervalEntities":"n/a","currentIntervalEndTime":"2026-10-09T18:01:00.000Z"},"per-month":{"maxRequestsPerInterval":"unlimited","maxEntitiesPerInterval":"unlimited","currentIntervalRequests":"n/a","currentIntervalEntities":120,"currentIntervalEndTime":"2026-10-31T00:00:00.000Z"}}}}""") }
        val u = client().usage()!!
        assertEquals("this minute 4/300 requests · this month 120/unlimited objects", u.summary())
    }

    // ---- closes -----------------------------------------------------------------------------------------------------------------

    private fun closeEvent(started: Boolean = true) = """
    {"success":true,"data":[{"eventID":"E9","sportID":"FOOTBALL","leagueID":"NFL","teams":{"home":{"names":{"long":"Kansas City Chiefs"},"score":27},"away":{"names":{"long":"Las Vegas Raiders"},"score":20}},
     "status":{"startsAt":"2026-10-11T17:00:00.000Z","started":$started,"ended":$started,"finalized":$started,"live":false},
     "odds":{
      "points-home-game-ml-home":{"oddID":"points-home-game-ml-home","statID":"points","statEntityID":"home","periodID":"game","betTypeID":"ml","sideID":"home","byBookmaker":{"pinnacle":{"odds":"-150","available":false,"lastUpdatedAt":"2026-10-11T16:59:00.000Z","openOdds":"-140","closeOdds":"-160"}}},
      "points-away-game-ml-away":{"oddID":"points-away-game-ml-away","statID":"points","statEntityID":"away","periodID":"game","betTypeID":"ml","sideID":"away","byBookmaker":{"pinnacle":{"odds":"+130","available":false,"lastUpdatedAt":"2026-10-11T16:59:00.000Z","openOdds":"+120","closeOdds":"+140"}}},
      "points-home-game-sp-home":{"oddID":"points-home-game-sp-home","statID":"points","statEntityID":"home","periodID":"game","betTypeID":"sp","sideID":"home","byBookmaker":{"pinnacle":{"odds":"-110","spread":"-3.5","available":false,"lastUpdatedAt":"2026-10-11T16:59:00.000Z","openSpread":"-3","closeSpread":"-3.5","openOdds":"-110","closeOdds":"-108"}}},
      "points-away-game-sp-away":{"oddID":"points-away-game-sp-away","statID":"points","statEntityID":"away","periodID":"game","betTypeID":"sp","sideID":"away","byBookmaker":{"pinnacle":{"odds":"-110","spread":"+3.5","available":false,"lastUpdatedAt":"2026-10-11T16:59:00.000Z","openSpread":"+3","closeSpread":"+3.5","openOdds":"-110","closeOdds":"-112"}}}
     }}]}"""

    private fun bet(selection: String, market: String, id: String = "b1") = TrackedBet(
        id = id, createdAtMs = 1L, league = "NFL", eventName = "Las Vegas Raiders @ Kansas City Chiefs", startsTs = java.time.Instant.parse("2026-10-11T17:00:00Z").toEpochMilli(),
        marketLabel = market, selection = selection, marketId = "m", outcomeId = "o", price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 4.0, stake = 10.0,
    )

    @Test fun closeIsPinnaclesPriceAtTheStartDevigged() = runTest {
        handler = { ok(closeEvent()) }
        val c = SgoCloses(client(), { true }, { clockMs }).also { it.enabled = true }
        val r = c.closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found)
        // -160 / +140 -> 0.6154 and 0.4167 -> fair 0.5961
        assertEquals(0.5961, (r as CloseLookup.Found).fair, 0.001)
        assertTrue(r.via.contains("Pinnacle"))
        val url = requests.single().requestUrl!!
        assertEquals("true", url.queryParameter("includeOpenCloseOdds"))
        assertNotNull(url.queryParameter("startsAfter"))
    }

    @Test fun aSpreadThatClosedElsewhereIsNoCloseForTheBetNumber() = runTest {
        handler = { ok(closeEvent()) }
        val c = SgoCloses(client(), { true }, { clockMs }).also { it.enabled = true }
        val ok = c.closes(listOf(bet("Kansas City Chiefs -3.5", "Spread")))["b1"]
        assertTrue(ok.toString(), ok is CloseLookup.Found)
        val other = c.closes(listOf(bet("Kansas City Chiefs -3", "Spread", "b2")))["b2"]
        assertTrue(other.toString(), other is CloseLookup.None && other.reason.contains("closed at -3.5"))
    }

    @Test fun aGameNotStartedIsLaterAndOffMeansNothingIsAsked() = runTest {
        handler = { ok(closeEvent(started = false)) }
        val c = SgoCloses(client(), { true }, { clockMs })
        assertTrue(c.closes(listOf(bet("Kansas City Chiefs", "Money"))).isEmpty())
        assertTrue(requests.isEmpty())
        c.enabled = true
        assertTrue(c.closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"] is CloseLookup.Later)
    }

    // ---- scores -----------------------------------------------------------------------------------------------------------------

    @Test fun scoresReadFinalAndOvertimeInclusiveAndChainFallsBackToTheFreeFeed() = runTest {
        handler = { ok(closeEvent()) }
        val sgo = SgoScores(client(), { true }, { clockMs }).also { it.enabled = true }
        val games = sgo.games("NFL", LocalDate.of(2026, 10, 11))!!
        assertEquals(1, games.size)
        val g = games.single()
        assertTrue(g.final); assertEquals(27, g.homeScore); assertEquals(20, g.awayScore)
        val free = object : ScoreSource {
            override suspend fun games(league: String, date: LocalDate) = listOf(GameScore("espn:1", league, "Kansas City Chiefs", "Las Vegas Raiders", g.startMs, true, false, 27, 20))
            override suspend fun players(game: GameScore) = listOf(PlayerLine("Patrick Mahomes", mapOf("PASSING_YARDS" to 300.0)))
            override fun covers(league: String) = true
        }
        val chain = ChainedScores(sgo, free)
        assertEquals("sgo:E9", chain.games("NFL", LocalDate.of(2026, 10, 11))!!.single().id)
        assertEquals("a prop's box score comes from the free feed that has the game", 300.0, chain.players(g)!!.single().stats["PASSING_YARDS"]!!, 0.0)
        sgo.enabled = false
        assertEquals("espn:1", chain.games("NFL", LocalDate.of(2026, 10, 11))!!.single().id)
    }
}
