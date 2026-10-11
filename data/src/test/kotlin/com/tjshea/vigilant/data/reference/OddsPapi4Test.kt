package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.CloseLookup
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.Op4Closes
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.test.runTest
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
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** OddsPapi FREE (v4) against hand-built answers in the shapes of its docs pages (ODDSPAPI_API.md addendum 2026-10-11): account, quota rules, budget, and the Pinnacle close from free historical odds. */
class OddsPapi4Test {
    private val startMs = Instant.parse("2026-10-11T17:00:00Z").toEpochMilli()
    private var clockMs = startMs + 4 * 3_600_000L
    private val fx = "id1400003160574299"

    private fun account(count: Int, limit: Int = 250, books: String = """"pinnacle":{"has_live_odds":false,"has_player_props":false}""", sports: String = "[11,13,14,15]") =
        """{"api_key":"SECRET-KEY-XYZ","current_subscription_id":"s1","subscriptions":[{"subscription_id":"s1","is_active":true,"valid_until":"2026-11-01T00:00:00.000Z","bookmakers":{$books},"sport_ids":$sports,"websocket_access":0,"request_limit":$limit,"request_count":$count}]}"""

    private val tournaments = """[{"tournamentId":31,"tournamentSlug":"nfl","categorySlug":"usa","tournamentName":"NFL","categoryName":"USA"},{"tournamentId":99,"tournamentSlug":"ncaaf","categorySlug":"usa","tournamentName":"NCAAF","categoryName":"USA"}]"""
    private val markets = """[
      {"marketId":101,"marketLength":3,"marketName":"Full Time Result","playerProp":false,"sportId":10,"handicap":0,"period":"fulltime","marketType":"1x2","outcomes":[{"outcomeId":101,"outcomeName":"1"},{"outcomeId":102,"outcomeName":"X"},{"outcomeId":103,"outcomeName":"2"}]},
      {"marketId":141,"marketLength":2,"marketName":"Winner","playerProp":false,"sportId":14,"handicap":0,"period":"result","marketType":"1x2","outcomes":[{"outcomeId":141,"outcomeName":"1"},{"outcomeId":142,"outcomeName":"2"}]},
      {"marketId":311,"marketLength":2,"marketName":"Total","playerProp":false,"sportId":14,"handicap":47.5,"period":"result","marketType":"totals","outcomes":[{"outcomeId":311,"outcomeName":"Over"},{"outcomeId":312,"outcomeName":"Under"}]}
    ]"""

    /** `/v4/fixtures`: participant 1 is the AWAY team here. */
    private fun fixtures(status: Int = 2) = """[{"fixtureId":"$fx","participant1Id":1,"participant2Id":2,"sportId":14,"tournamentId":31,"statusId":$status,"hasOdds":true,"startTime":"2026-10-11T17:00:00.000Z","trueStartTime":null,
        "participant1Name":"Las Vegas Raiders","participant2Name":"Kansas City Chiefs"}]"""

    private fun tick(at: String, price: Double, active: Boolean = true) = """{"createdAt":"$at","price":$price,"limit":5000,"active":$active,"exchangeMeta":null}"""

    private fun historical(outcome: Long, vararg ticks: String) =
        """{"fixtureId":"$fx","bookmakers":{"pinnacle":{"markets":{"${if (outcome < 200) 141 else 311}":{"outcomes":{"$outcome":{"players":{"0":[${ticks.joinToString(",")}]}}}}}}}}"""

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }
    private var accountCount = 100

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) } }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })
    private fun client(keys: List<String> = listOf("k1")) = OddsPapi4Client(OkHttpClient(), KeyPool(QuotaPolicy.ODDSPAPI_FREE, { keys }, meter), { keys }, server.url("/").toString().trimEnd('/'), { clockMs }, sleep = {})

    private fun route(r: RecordedRequest): MockResponse = when (r.requestUrl!!.encodedPath) {
        "/v4/account" -> ok(account(accountCount))
        "/v4/tournaments" -> { accountCount++; ok(tournaments) }
        "/v4/markets" -> { accountCount++; ok(markets) }
        "/v4/fixtures" -> { accountCount++; ok(fixtures()) }
        "/v4/historical-odds" -> when (r.requestUrl!!.queryParameter("outcomeId")) {
            // newest first, as the service sends it; the last price before the 17:00 start is the close, a later live one is not
            "141" -> ok(historical(141, tick("2026-10-11T17:20:00.000000+00:00", 1.2), tick("2026-10-11T16:58:00.000000+00:00", 2.0), tick("2026-10-11T14:00:00.000000+00:00", 2.2), tick("2026-10-11T12:00:00.000000+00:00", 2.5, active = false)))
            "142" -> ok(historical(142, tick("2026-10-11T16:58:00.000000+00:00", 1.8)))
            "311" -> ok(historical(311, tick("2026-10-11T16:59:00.000000+00:00", 1.95)))
            "312" -> ok(historical(312, tick("2026-10-11T16:59:00.000000+00:00", 1.95)))
            else -> MockResponse().setResponseCode(404)
        }
        else -> MockResponse().setResponseCode(404)
    }

    private fun closes(c: OddsPapi4Client = client(), store: Op4Store = Op4Store(null) { clockMs }) = Op4Closes(c, Op4Budget(c) { clockMs }, store) { clockMs }.also { it.enabled = true }

    private fun bet(selection: String, market: String, id: String = "b1", league: String = "NFL", start: Long = startMs) = TrackedBet(
        id = id, createdAtMs = 1L, league = league, eventName = "Las Vegas Raiders @ Kansas City Chiefs", startsTs = start,
        marketLabel = market, selection = selection, marketId = "m", outcomeId = "o", price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 4.0, stake = 10.0,
    )

    // ---- account, quota, budget ------------------------------------------------------------------------------------------------------------

    @Test fun theAccountGivesThePlansOwnFiguresAndTheKeyTravelsAsTheApiKeyParameter() = runTest {
        handler = { ok(account(120)) }
        val a = client().account()
        assertEquals(250, a.limit); assertEquals(120, a.count); assertEquals(130, a.left); assertTrue(a.active)
        assertEquals(setOf("pinnacle"), a.books.keys); assertEquals(listOf(11, 13, 14, 15), a.sportIds)
        assertEquals("k1", requests.single().requestUrl!!.queryParameter("apiKey"))
        assertFalse("the summary never carries the key", a.summary().contains("SECRET"))
    }

    @Test fun aBillableCallReadsTheUnmeteredAccountAfterwardsSoTheMeterUsesTheServersCount() = runTest {
        accountCount = 100
        handler = { route(it) }
        val c = client()
        c.billable("/v4/tournaments", listOf("sportId" to "14"))
        assertEquals(listOf("/v4/tournaments", "/v4/account"), requests.map { it.requestUrl!!.encodedPath })
        assertEquals(101, c.lastAccount!!.count)
        assertEquals(1, c.spentToday())
    }

    @Test fun theMonthlyLimitMarksTheKeySpentAndTheFreeCallsStillWork() = runTest {
        handler = { r -> if (r.requestUrl!!.encodedPath == "/v4/historical-odds") ok(historical(141, tick("2026-10-11T16:58:00.000000+00:00", 2.0))) else MockResponse().setResponseCode(429).setBody("""{"message":"Request limit exceeded","details":"You have exceeded your request limit of 250 requests.","code":"REQUEST_LIMIT_EXCEEDED"}""") }
        val c = client()
        assertTrue(runCatching { c.billable("/v4/fixtures", emptyList()) }.exceptionOrNull() is AllKeysExhaustedException)
        // historical odds are free: asked without the quota gate
        assertTrue(c.free("/v4/historical-odds", listOf("fixtureId" to fx)).contains("pinnacle"))
    }

    @Test fun aSpentKeyMovesTheCallToTheNextKeyAndTheSpentOneStaysResting() = runTest {
        handler = { r ->
            if (r.requestUrl!!.encodedPath == "/v4/account") ok(account(10))
            else if (r.requestUrl!!.queryParameter("apiKey") == "k1") MockResponse().setResponseCode(429).setBody("""{"message":"Request limit exceeded","code":"REQUEST_LIMIT_EXCEEDED"}""")
            else ok(tournaments)
        }
        val c = client(listOf("k1", "k2"))
        assertTrue(c.billable("/v4/tournaments", listOf("sportId" to "14")).contains("NFL"))
        assertEquals(listOf("k1", "k2"), requests.filter { it.requestUrl!!.encodedPath == "/v4/tournaments" }.map { it.requestUrl!!.queryParameter("apiKey") })
        requests.clear()
        c.billable("/v4/tournaments", listOf("sportId" to "14"))
        assertEquals("the spent key is not asked again", listOf("k2"), requests.filter { it.requestUrl!!.encodedPath == "/v4/tournaments" }.map { it.requestUrl!!.queryParameter("apiKey") })
    }

    @Test fun theBudgetStopsAtTheReserveAndSpendsAnEvenShareOfWhatIsLeft() = runTest {
        val c = client()
        val b = Op4Budget(c) { clockMs }
        assertTrue("unknown figures allow the first call, whose answer fills them in", b.mayBuy())
        handler = { ok(account(245)) }; c.account()
        assertFalse("5 left is under the reserve", b.mayBuy())
        handler = { ok(account(100)) }; c.account()
        assertTrue(b.mayBuy())
        handler = { ok(account(100).replace("\"is_active\":true", "\"is_active\":false")) }; c.account()
        assertFalse("an inactive plan buys nothing", b.mayBuy())
    }

    // ---- the Pinnacle close ----------------------------------------------------------------------------------------------------------------

    @Test fun theCloseIsPinnaclesLastActivePriceAtOrBeforeTheStartDeviggedForTheBetsSide() = runTest {
        handler = { route(it) }
        val r = closes().closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found)
        // Chiefs are participant 2 (outcome 142 = 1.8); the Raiders' last price before the start is 2.0, not the 1.2 after it: 1/1.8 against 1/2.0 -> .5263
        assertEquals(0.5263, (r as CloseLookup.Found).fair, 0.001)
        assertTrue(r.via, r.via.contains("OddsPapi Free") && r.via.contains("Pinnacle"))
        val hist = requests.filter { it.requestUrl!!.encodedPath == "/v4/historical-odds" }
        assertEquals(2, hist.size)
        assertTrue(hist.all { it.requestUrl!!.queryParameter("bookmakers") == "pinnacle" })
    }

    @Test fun aTotalIsClosedAtItsExactNumberAndOverIsOutcomeOne() = runTest {
        handler = { route(it) }
        val r = closes().closes(listOf(bet("Over 47.5", "Total")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Found)
        assertEquals(0.5, (r as CloseLookup.Found).fair, 0.001)
    }

    @Test fun theCostIsAFewBillableCallsOnceAndNothingBillableForTheNextBetsOfThatGame() = runTest {
        handler = { route(it) }
        val store = Op4Store(null) { clockMs }
        val cl = closes(store = store)
        cl.closes(listOf(bet("Kansas City Chiefs", "Money", id = "a"), bet("Over 47.5", "Total", id = "b")))
        val billable = requests.map { it.requestUrl!!.encodedPath }.filter { it in setOf("/v4/tournaments", "/v4/markets", "/v4/fixtures") }
        assertEquals("tournaments, one game list and the market catalogue: each once", listOf("/v4/tournaments", "/v4/fixtures", "/v4/markets"), billable)
        requests.clear()
        closes(client(), store).closes(listOf(bet("Kansas City Chiefs", "Money", id = "c")))
        assertTrue("a second run (a restart) reuses the saved lookups", requests.none { it.requestUrl!!.encodedPath in setOf("/v4/tournaments", "/v4/markets", "/v4/fixtures") })
    }

    @Test fun whenTheDailyShareOrTheReserveSaysNoTheBetWaitsAndNothingIsBought() = runTest {
        accountCount = 248
        handler = { route(it) }
        val c = client(); c.account()
        val r = closes(c).closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"]
        assertTrue(r.toString(), r is CloseLookup.Later)
        assertTrue(requests.none { it.requestUrl!!.encodedPath in setOf("/v4/tournaments", "/v4/markets", "/v4/fixtures") })
    }

    @Test fun propsAndSportsThePlanLacksAreNeverAskedAndAnOldGameIsNone() = runTest {
        handler = { route(it) }
        val cl = closes()
        val prop = cl.closes(listOf(bet("Patrick Mahomes Over 275.5", "Player Passing Yards")))["b1"]
        assertTrue(prop.toString(), prop is CloseLookup.None)
        assertTrue(cl.closes(listOf(bet("Chiefs", "Money", league = "NHL")))["b1"].let { it is CloseLookup.None || it is CloseLookup.Later })
        assertTrue(cl.closes(listOf(bet("Kansas City Chiefs", "Money", start = Instant.parse("2025-10-11T17:00:00Z").toEpochMilli())))["b1"] is CloseLookup.None)
        accountCount = 100
        val c = client(); handler = { ok(account(100, sports = "[11]")) }; c.account()
        handler = { route(it) }
        assertTrue("a plan without football gets no football lookups", closes(c).closes(listOf(bet("Kansas City Chiefs", "Money")))["b1"] is CloseLookup.None)
    }

    @Test fun theSwitchOffAsksNothing() = runTest {
        handler = { route(it) }
        val cl = closes().also { it.enabled = false }
        assertTrue(cl.closes(listOf(bet("Kansas City Chiefs", "Money"))).isEmpty())
        assertEquals(0, requests.size)
        assertNull(null)
        assertNotNull(Op4Closes.ID)
    }

    // ---- the fair-odds feed (whole games, every book, one request each) ---------------------------------------------------------------------

    private fun oddsGame(vararg books: Pair<String, Double>, changed: String = "2026-10-11T14:00:00.000Z") =
        """{"fixtureId":"$fx","participant1Id":1,"participant2Id":2,"sportId":14,"tournamentId":31,"statusId":0,"startTime":"2026-10-11T17:00:00.000Z","participant1Name":"Las Vegas Raiders","participant2Name":"Kansas City Chiefs",
        "bookmakerOdds":{${books.joinToString(",") { (b, p1) -> """"$b":{"bookmakerIsActive":true,"suspended":false,"markets":{"141":{"marketActive":true,"outcomes":{
          "141":{"players":{"0":{"active":true,"changedAt":"$changed","price":$p1,"mainLine":true}}},"142":{"players":{"0":{"active":true,"changedAt":"$changed","price":${2.0 + 1.8 - p1},"mainLine":true}}}}}}}""" }}}}"""

    private fun feedSetup(perDay: Int, every: Boolean, books: String = """"pinnacle":{"has_live_odds":false,"has_player_props":true},"obscurebook":{"has_live_odds":false,"has_player_props":true}"""): Pair<Op4Feed, ScanSettings> {
        clockMs = startMs - 2 * 3_600_000L
        accountCount = 100
        handler = { r ->
            when (r.requestUrl!!.encodedPath) {
                "/v4/account" -> ok(account(accountCount, books = books))
                "/v4/odds" -> { accountCount++; ok(oddsGame("pinnacle" to 2.0, "obscurebook" to 1.9)) }
                "/v4/fixtures" -> { accountCount++; ok(fixtures(0)) }
                else -> route(r)
            }
        }
        val c = client()
        val store = Op4Store(null) { clockMs }
        val budget = Op4Budget(c) { clockMs }
        val feed = Op4Feed(c, Op4Catalog(c, budget, store, { clockMs }, lenient = true), budget, store) { clockMs }
        return feed to ScanSettings(referenceBooks = listOf("pinnacle"), oddsPapiFree = true, families = setOf(MarketFamily.MONEYLINE), gates = ApiGates(op4Fair = perDay, opAllBooks = every), leagues = setOf("NFL"))
    }

    @Test fun theFairFeedReadsAWholeGameInOneRequestAndKeepsEveryBookWhenAskedTo() = runTest {
        val (feed, s) = feedSetup(perDay = 5, every = true)
        val nfl = Leagues.byNovigName("NFL")!!
        val r = Op4GamesSource(feed) { clockMs }.odds(nfl, s)
        val ev = r.events.single()
        assertEquals("participant 1 is the home side the planner will orient", "Las Vegas Raiders", ev.home)
        assertEquals(setOf("pinnacle", "obscurebook"), ev.markets.map { it.bookKey }.toSet())
        val odds = requests.single { it.requestUrl!!.encodedPath == "/v4/odds" }.requestUrl!!
        assertEquals(fx, odds.queryParameter("fixtureId")); assertEquals("3", odds.queryParameter("verbosity"))
        assertEquals("every book: no bookmakers filter", null, odds.queryParameter("bookmakers"))
        assertTrue("a main line of a connected book is as fresh as the read", ev.markets.all { it.lastUpdateMs == clockMs })
    }

    @Test fun withoutEveryBookOnlyThePickedBooksAreAskedForAndKept() = runTest {
        val (feed, s) = feedSetup(perDay = 5, every = false)
        val r = Op4GamesSource(feed) { clockMs }.odds(Leagues.byNovigName("NFL")!!, s)
        assertEquals(setOf("pinnacle"), r.events.single().markets.map { it.bookKey }.toSet())
        assertEquals("pinnacle", requests.single { it.requestUrl!!.encodedPath == "/v4/odds" }.requestUrl!!.queryParameter("bookmakers"))
    }

    @Test fun theDailyFigureIsTheCapAndZeroNeverBuysAGameRead() = runTest {
        val (off, s0) = feedSetup(perDay = 0, every = true)
        assertTrue(Op4GamesSource(off) { clockMs }.odds(Leagues.byNovigName("NFL")!!, s0).events.isEmpty())
        assertTrue("0 a day: no /v4/odds request", requests.none { it.requestUrl!!.encodedPath == "/v4/odds" })
        val (feed, s) = feedSetup(perDay = 1, every = true)
        val src = Op4GamesSource(feed) { clockMs }
        src.odds(Leagues.byNovigName("NFL")!!, s)
        clockMs += Op4Feed.READ_MS + 1
        src.odds(Leagues.byNovigName("NFL")!!, s)
        assertEquals("one a day: the same game is not bought twice", 1, requests.count { it.requestUrl!!.encodedPath == "/v4/odds" })
    }

    @Test fun theFeedNeverSpendsTheLastRequestsOfThePeriod() = runTest {
        val (feed, s) = feedSetup(perDay = 50, every = true)
        accountCount = 235   // 15 left: under the reserve 10 + the 25 kept for closing lines
        client().account()
        val r = Op4GamesSource(feed) { clockMs }.odds(Leagues.byNovigName("NFL")!!, s)
        assertTrue(r.events.isEmpty())
        assertTrue(requests.none { it.requestUrl!!.encodedPath == "/v4/odds" })
    }

    @Test fun aTournamentThatTheGameListHasButNoPricesYetGivesNoQuotesNotAnError() = runTest {
        val (feed, s) = feedSetup(perDay = 5, every = true)
        handler = { r -> if (r.requestUrl!!.encodedPath == "/v4/fixtures") { accountCount++; ok(fixtures(0)) } else if (r.requestUrl!!.encodedPath == "/v4/odds") { accountCount++; ok(oddsGame()) } else if (r.requestUrl!!.encodedPath == "/v4/account") ok(account(accountCount)) else route(r) }
        val r = Op4GamesSource(feed) { clockMs }.odds(Leagues.byNovigName("NFL")!!, s)
        assertTrue(r.events.isEmpty())
    }
}
