package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.ChainedScores
import com.tjshea.vigilant.data.tracker.CloseLookup
import com.tjshea.vigilant.data.tracker.GameScore
import com.tjshea.vigilant.data.tracker.OpCloses
import com.tjshea.vigilant.data.tracker.OpScores
import com.tjshea.vigilant.data.tracker.PlayerLine
import com.tjshea.vigilant.data.tracker.ScoreSource
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.test.runTest
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
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** OddsPapi's closing lines (CLV for any bet), grading scores and the tapped bet's other books, against hand-built answers in the documented shapes (ODDSPAPI_API.md §4-§5). */
class OddsPapiClosesTest {
    private val startMs = Instant.parse("2026-10-11T17:00:00Z").toEpochMilli()
    private val clockStart = startMs + 4 * 3_600_000L
    private var clockMs = clockStart
    private val fx = "id1400003160574299"

    private val tournaments = """[{"tournamentId":31,"sportId":14,"tournamentSlug":"nfl","categorySlug":"usa","tournamentName":"NFL","categoryName":"USA"}]"""
    private val markets = """[
      {"marketId":141,"marketLength":2,"sportId":14,"playerProp":false,"handicap":0.0,"period":"result","marketType":"1x2","marketName":"Winner","outcomes":[{"outcomeId":141,"outcomeName":"1"},{"outcomeId":142,"outcomeName":"2"}]},
      {"marketId":211,"marketLength":2,"sportId":14,"playerProp":false,"handicap":3.5,"period":"result","marketType":"spreads","marketName":"Spread","outcomes":[{"outcomeId":211,"outcomeName":"1"},{"outcomeId":212,"outcomeName":"2"}]},
      {"marketId":311,"marketLength":2,"sportId":14,"playerProp":false,"handicap":47.5,"period":"result","marketType":"totals","marketName":"Total","outcomes":[{"outcomeId":311,"outcomeName":"Over"},{"outcomeId":312,"outcomeName":"Under"}]},
      {"marketId":611,"marketLength":2,"sportId":14,"playerProp":true,"handicap":275.5,"period":"result","marketType":"players-passingyards","marketName":"Passing yards","outcomes":[{"outcomeId":611,"outcomeName":"Over"},{"outcomeId":612,"outcomeName":"Under"}]}
    ]"""
    private val catalog = """[{"slug":"pinnacle","bookmakerName":"Pinnacle"},{"slug":"circa","bookmakerName":"Circa Sports"},{"slug":"draftkings","bookmakerName":"DraftKings"}]"""

    /** The game as `/fixtures` lists it: participant 1 is the AWAY team here (the NFL example's orientation). */
    private fun game(status: Int, scores: String = "{}") = """{"fixtureId":"$fx","status":{"live":${status == 1},"statusId":$status},"sport":{"sportId":14},"tournament":{"tournamentId":31,"tournamentName":"NFL","categoryName":"USA"},
        "startTime":${startMs / 1000L},"participants":{"participant1Id":1,"participant1Name":"Las Vegas Raiders","participant2Id":2,"participant2Name":"Kansas City Chiefs"},"scores":$scores}"""

    private fun clvRow(book: String, outcome: Long, close: Double, closeAt: Long, open: Double = 2.0, player: Long = 0) =
        """"$fx:$book:$outcome:$player":{"clv":{"bookmaker":"$book","outcomeId":$outcome,"playerId":$player,"price":$close,"changedAt":$closeAt},"olv":{"bookmaker":"$book","outcomeId":$outcome,"playerId":$player,"price":$open,"changedAt":${closeAt - 3_600_000L}}}"""

    private fun clv(vararg byBook: Pair<String, List<String>>) = """{"fixtureId":"$fx","odds":{${byBook.joinToString(",") { (b, rows) -> "\"$b\":{${rows.joinToString(",")}}" }}}}"""

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) } }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })
    private fun client() = OddsPapiClient(OkHttpClient(), KeyPool(QuotaPolicy.ODDSPAPI, { listOf("k1") }, meter), server.url("/en").toString().trimEnd('/'), clock = { clockMs }, oddsGapMs = 0, otherGapMs = 0, retryDelayMs = { 0L })
    private fun base(r: RecordedRequest, then: (RecordedRequest) -> MockResponse): MockResponse = when (r.requestUrl!!.encodedPath) {
        "/en/bookmakers" -> ok(catalog); "/en/tournaments" -> ok(tournaments); "/en/markets" -> ok(markets)
        "/en/players" -> ok("""[{"playerId":777,"playerName":"Mahomes, Patrick"}]""")
        else -> then(r)
    }

    private fun closes(c: OddsPapiClient = client()) = OpCloses(c, OddsPapiFeed(c) { clockMs }, { true }, { clockMs }).also { it.enabled = true }

    private fun bet(selection: String, market: String, id: String = "b1", league: String = "NFL") = TrackedBet(
        id = id, createdAtMs = 1L, league = league, eventName = "Las Vegas Raiders @ Kansas City Chiefs", startsTs = startMs,
        marketLabel = market, selection = selection, marketId = "m", outcomeId = "o", price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 4.0, stake = 10.0,
    )

    // ---- closes -----------------------------------------------------------------------------------------------------------------------------

    @Test fun theCloseIsPinnaclesLastPriceDeviggedForTheBetsSideWhoeverIsParticipantOne() = runTest {
        handler = { r -> base(r) { q -> if (q.requestUrl!!.encodedPath == "/en/fixtures") ok("[${game(2)}]") else ok(clv("pinnacle" to listOf(clvRow("pinnacle", 141, 2.0, startMs - 60_000L), clvRow("pinnacle", 142, 1.8, startMs - 60_000L)))) } }
        val r = closes().closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found)
        // Chiefs are participant 2 (outcome 142): 1/1.8 = .5556 against 1/2.0 = .5 -> fair .5263
        assertEquals(0.5263, (r as CloseLookup.Found).fair, 0.001)
        assertTrue(r.via, r.via.contains("OddsPapi") && r.via.contains("Pinnacle"))
        val list = requests.single { it.requestUrl!!.encodedPath == "/en/fixtures" }.requestUrl!!
        assertEquals("31", list.queryParameter("tournamentId"))
        assertTrue("schedule times are seconds", list.queryParameter("startTimeFrom")!!.toLong() < startMs / 1000L && list.queryParameter("startTimeTo")!!.toLong() > startMs / 1000L)
        val c = requests.single { it.requestUrl!!.encodedPath == "/en/fixtures/odds/clv" }.requestUrl!!
        assertEquals(fx, c.queryParameter("fixtureId")); assertEquals("pinnacle", c.queryParameter("bookmakers"))
        assertEquals("$fx:pinnacle:141:0,$fx:pinnacle:142:0", c.queryParameter("oddsIds"))
    }

    @Test fun aSpreadIsClosedOnlyAtTheExactNumberAndInParticipantOnesTerms() = runTest {
        handler = { r -> base(r) { q -> if (q.requestUrl!!.encodedPath == "/en/fixtures") ok("[${game(2)}]") else ok(clv("pinnacle" to listOf(clvRow("pinnacle", 211, 1.95, startMs - 1000L), clvRow("pinnacle", 212, 1.87, startMs - 1000L)))) } }
        val c = closes()
        // Chiefs -3.5 = Raiders +3.5 = market 211/212; the bet is the second outcome
        val ok = c.closes(listOf(bet("Kansas City Chiefs -3.5", "Spread")))["b1"]
        assertTrue(ok.toString(), ok is CloseLookup.Found)
        assertEquals((1 / 1.87) / (1 / 1.87 + 1 / 1.95), (ok as CloseLookup.Found).fair, 1e-6)
        val other = c.closes(listOf(bet("Kansas City Chiefs -3", "Spread", "b2")))["b2"]
        assertTrue(other.toString(), other is CloseLookup.None && other.reason.contains("no market"))
    }

    @Test fun aTotalAndAnUnderUseTheRightOutcome() = runTest {
        handler = { r -> base(r) { q -> if (q.requestUrl!!.encodedPath == "/en/fixtures") ok("[${game(2)}]") else ok(clv("pinnacle" to listOf(clvRow("pinnacle", 311, 1.8, startMs - 1000L), clvRow("pinnacle", 312, 2.05, startMs - 1000L)))) } }
        val r = closes().closes(listOf(bet("Under 47.5", "Total")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found)
        assertEquals((1 / 2.05) / (1 / 2.05 + 1 / 1.8), (r as CloseLookup.Found).fair, 1e-6)
    }

    @Test fun aCloseStampedAfterKickoffIsALivePriceSoTheTimelineGivesTheLastPriceBeforeIt() = runTest {
        handler = { r -> base(r) { q ->
            when (q.requestUrl!!.encodedPath) {
                "/en/fixtures" -> ok("[${game(2)}]")
                "/en/fixtures/odds/clv" -> ok(clv("pinnacle" to listOf(clvRow("pinnacle", 141, 1.01, startMs + 3_600_000L), clvRow("pinnacle", 142, 30.0, startMs + 3_600_000L))))
                else -> ok("""{"fixtureId":"$fx","odds":{"pinnacle":{
                    "$fx:pinnacle:141:0":{"${startMs - 9000}":{"price":2.0,"active":true,"changedAt":${startMs - 9000}},"${startMs + 5000}":{"price":1.2,"active":true,"changedAt":${startMs + 5000}}},
                    "$fx:pinnacle:142:0":{"${startMs - 8000}":{"price":1.8,"active":true,"changedAt":${startMs - 8000}},"${startMs + 6000}":{"price":4.0,"active":true,"changedAt":${startMs + 6000}}}}}}""")
            }
        } }
        val r = closes().closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found)
        assertEquals(0.5263, (r as CloseLookup.Found).fair, 0.001)       // 1.8 / 2.0, not the live 4.0 / 1.2
        val h = requests.single { it.requestUrl!!.encodedPath == "/en/fixtures/odds/historical" }.requestUrl!!
        assertEquals("pinnacle", h.queryParameter("bookmaker")); assertEquals(fx, h.queryParameter("fixtureId"))
    }

    @Test fun circaIsUsedWhenPinnacleHasNoClose() = runTest {
        handler = { r -> base(r) { q ->
            if (q.requestUrl!!.encodedPath == "/en/fixtures") ok("[${game(2)}]")
            else if (q.requestUrl!!.queryParameter("bookmakers") == "pinnacle") ok(clv())
            else ok(clv("circa" to listOf(clvRow("circa", 141, 2.0, startMs - 1000L), clvRow("circa", 142, 1.8, startMs - 1000L))))
        } }
        val r = closes().closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found && r.via.contains("Circa"))
    }

    @Test fun aPlayerPropIsFoundByNameFromTheGamesPlayerIds() = runTest {
        handler = { r -> base(r) { q ->
            if (q.requestUrl!!.encodedPath == "/en/fixtures") ok("[${game(2)}]")
            else ok(clv("pinnacle" to listOf(clvRow("pinnacle", 611, 1.9, startMs - 1000L, player = 777), clvRow("pinnacle", 612, 1.9, startMs - 1000L, player = 777), clvRow("pinnacle", 611, 1.5, startMs - 1000L, player = 888), clvRow("pinnacle", 612, 2.6, startMs - 1000L, player = 888))))
        } }
        val r = closes().closes(listOf(bet("Patrick Mahomes Over 275.5", "Player Passing Yards")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found)
        assertEquals(0.5, (r as CloseLookup.Found).fair, 1e-6)
        assertTrue(requests.any { it.requestUrl!!.encodedPath == "/en/players" })
    }

    @Test fun aGameNotStartedIsLaterAnUnknownGameOrLeagueIsNoneAndOffMeansNothingIsAsked() = runTest {
        handler = { r -> base(r) { q -> if (q.requestUrl!!.encodedPath == "/en/fixtures") ok("[${game(0)}]") else ok(clv()) } }
        val c = closes()
        assertTrue(c.closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"] is CloseLookup.Later)
        handler = { r -> base(r) { ok("[]") } }
        assertTrue(closes().closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"].let { it is CloseLookup.None && it.reason.contains("Not in OddsPapi") })
        assertTrue(c.closes(listOf(bet("Novak Djokovic", "Money", league = "ATP")))["b1"].let { it is CloseLookup.None && it.reason.contains("no feed") })
        requests.clear()
        val off = OpCloses(client(), OddsPapiFeed(client()), { true }, { clockMs })
        assertTrue(off.closes(listOf(bet("Kansas City Chiefs", "Money"))).isEmpty()); assertTrue(requests.isEmpty())
        assertTrue(!off.active)
    }

    @Test fun anOutageIsLaterNotNone() = runTest {
        handler = { MockResponse().setResponseCode(500) }
        assertTrue(closes().closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"] is CloseLookup.Later)
    }

    // ---- scores -----------------------------------------------------------------------------------------------------------------------------

    private val finalScores = """{"p1":{"period":"p1","participant1Score":7,"participant2Score":3},"p2":{"period":"p2","participant1Score":10,"participant2Score":7},"p3":{"period":"p3","participant1Score":0,"participant2Score":7},
        "p4":{"period":"p4","participant1Score":6,"participant2Score":3},"overtime":{"period":"overtime","participant1Score":0,"participant2Score":3},"result":{"period":"result","participant1Score":23,"participant2Score":23}}"""

    @Test fun scoresAreOvertimeInclusiveWithPeriodsAndParticipantOneIsTheFirstTeam() = runTest {
        handler = { r -> base(r) { ok("[${game(2, finalScores)}]") } }
        val c = client()
        val games = OpScores(c, OddsPapiFeed(c) { clockMs }, { true }, { clockMs }).also { it.enabled = true }.games("NFL", LocalDate.of(2026, 10, 11))!!
        val g = games.single()
        assertEquals("op:$fx", g.id); assertEquals("Las Vegas Raiders", g.home); assertEquals("Kansas City Chiefs", g.away)
        assertTrue(g.final); assertEquals(23, g.homeScore); assertEquals(23, g.awayScore)
        assertEquals(listOf(7, 10, 0, 6, 0), g.homePeriods); assertEquals(listOf(3, 7, 7, 3, 3), g.awayPeriods)
    }

    @Test fun aFinishedGameWithoutAResultScoreIsNotFinalAndACancelledOneIsCalled() = runTest {
        handler = { r -> base(r) { ok("[${game(2, "{}")},${game(3).replace(fx, "id2")}]") } }
        val c = client()
        val games = OpScores(c, OddsPapiFeed(c) { clockMs }, { true }, { clockMs }).also { it.enabled = true }.games("NFL", LocalDate.of(2026, 10, 11))!!
        assertTrue(!games[0].final && !games[0].called)
        assertTrue(games[1].called && games[1].calledReason == "Cancelled")
    }

    @Test fun offOrNoLeagueMeansNothingIsAsked() = runTest {
        val c = client()
        val s = OpScores(c, OddsPapiFeed(c) { clockMs }, { true }, { clockMs })
        assertTrue(!s.covers("NFL")); assertNull(s.games("NFL", LocalDate.of(2026, 10, 11)))
        s.enabled = true
        assertTrue(s.covers("NFL")); assertTrue(!s.covers("ATP")); assertNull(s.games("ATP", LocalDate.of(2026, 10, 11)))
        assertNull(s.players(GameScore("op:x", "NFL", "A", "B", 0L, true, false, 1, 0)))
        assertTrue(requests.isEmpty())
    }

    @Test fun theChainReadsOddsPapiFirstThenTheFreeFeedAndAPropBoxScoreComesFromTheFreeFeedEvenAcrossOrientations() = runTest {
        handler = { r -> base(r) { ok("[${game(2, finalScores)}]") } }
        val c = client()
        val op = OpScores(c, OddsPapiFeed(c) { clockMs }, { true }, { clockMs }).also { it.enabled = true }
        // the free feed lists the Chiefs as home: the opposite orientation to OddsPapi's participant 1
        val free = object : ScoreSource {
            val asked = ArrayList<String>()
            override fun covers(league: String) = true
            override suspend fun games(league: String, date: LocalDate) = listOf(GameScore("espn:1", league, "Kansas City Chiefs", "Las Vegas Raiders", startMs, true, false, 23, 23))
            override suspend fun players(game: GameScore): List<PlayerLine>? { asked += game.id; return listOf(PlayerLine("Patrick Mahomes", mapOf("PASSING_YARDS" to 301.0))) }
        }
        val chain = ChainedScores(op, free)
        val g = chain.games("NFL", LocalDate.of(2026, 10, 11))!!.single()
        assertEquals("op:$fx", g.id)
        val lines = chain.players(g)!!
        assertEquals(listOf("espn:1"), free.asked)
        assertEquals(301.0, lines.single().stats["PASSING_YARDS"]!!, 0.0)
    }

    // ---- the tapped bet's other books --------------------------------------------------------------------------------------------------------

    @Test fun theTappedBetsOtherBooksComeFromOddsPapiAloneWhileItIsOn() = runTest {
        val ev = RefEvent("op:$fx", "americanfootball_nfl", startMs, "Las Vegas Raiders", "Kansas City Chiefs", listOf(
            RefBookMarket("pinnacle", "Pinnacle", LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, 1.9, 275.5), RefQuote(Side.UNDER, 1.9, 275.5)), startMs - 60_000L, subject = "Patrick Mahomes", stat = "PASSING_YARDS"),
            RefBookMarket("draftkings", "DraftKings", LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, 2.0, 275.5), RefQuote(Side.UNDER, 1.8, 275.5)), startMs - 60_000L, subject = "Patrick Mahomes", stat = "PASSING_YARDS"),
            RefBookMarket("draftkings", "DraftKings", LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, 1.5, 250.5), RefQuote(Side.UNDER, 2.6, 250.5)), startMs - 60_000L, subject = "Patrick Mahomes", stat = "PASSING_YARDS"),
        ))
        val others = OtherBooks(null, null, null, kotlinx.serialization.json.Json, on = { OtherBooks.Sources(parlay = false, propLine = false, oddsApi = false, op = true) }, clock = { startMs - 30_000L }, op = { listOf(ev) })
        val r = others.view("NFL", "Las Vegas Raiders @ Kansas City Chiefs", startMs, "Player Passing Yards", "Patrick Mahomes Over 275.5")
        assertEquals(listOf("OddsPapi"), r.sources)
        assertEquals(setOf("PIN", "DK").size, r.view!!.prices.size)
        assertTrue("only the bet's own number", r.view!!.prices.all { it.otherOdds != null })
    }
}
