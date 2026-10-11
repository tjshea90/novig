package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * Unabated Free (UNABATED_API.md): parser, converter, books, client, feed and sources against answers built by hand in the shapes the docs and the OpenAPI examples publish (the NBA
 * `Thunder @ Pacers` board of the odds operation's own example). No live key has been used: when Tj's Test-key sample arrives, a test is added from its real answers.
 */
class UnabatedTest {
    private var clockMs = Instant.parse("2026-10-11T18:00:00Z").toEpochMilli()
    private val start = "2026-10-11T23:30:00Z"
    private val nba = Leagues.byNovigName("NBA")!!

    private fun line(source: Int, points: String?, price: Int, modified: String = "2026-10-11T12:00:00Z", expires: String? = null, extra: String = "") =
        """"$source":{"marketId":1,"marketLineId":${source * 10},"marketSourceId":$source,${points?.let { "\"points\":$it," } ?: ""}"price":$price,"sourcePrice":$price,"sourceFormat":1,"statusId":1,"disabled":false,
        "modifiedOn":"$modified"${expires?.let { ",\"freshnessExpiresAt\":\"$it\"" } ?: ""}$extra}"""

    private fun side(idx: Int, team: Int?, lines: String, person: Int? = null, name: String = "") =
        """"si$idx:t$team":{"sideIndex":$idx,"sideName":"$name",${team?.let { "\"teamId\":$it," } ?: ""}${person?.let { "\"personId\":$it," } ?: ""}"marketSourceLines":{$lines}}"""

    private fun group(betType: Int, sides: String, eventId: Int = 940356, status: Int = 1) =
        """"eid$eventId:bt$betType":{"eventId":$eventId,"isLive":false,"eventStart":"$start","statusId":$status,"betTypeId":$betType,"eventName":"Thunder Oklahoma City OKC @ Pacers Indiana IND",
        "eventTeams":{"0":{"teamId":75,"sideIndex":0},"1":{"teamId":84,"sideIndex":1}},"sides":{$sides}}"""

    private val future = "2026-10-11T18:05:00Z"

    private val altOver = ""","alternateLines":[{"marketSourceId":91,"alternateNumber":1,"points":231.5,"price":-120,"statusId":1,"modifiedOn":"2026-10-11T12:00:00Z"}]"""
    private val altUnder = ""","alternateLines":[{"marketSourceId":91,"alternateNumber":1,"points":231.5,"price":100,"statusId":1,"modifiedOn":"2026-10-11T12:00:00Z"}]"""

    private val boardJson = """{"data":{"odds":{"nba":{"leagueId":3,"periodTypes":{"game":{"periodTypeId":1,"pregame":{
        ${group(1, side(0, 75, line(91, null, -150, expires = future) + "," + line(92, null, -149) + "," + line(93, null, -140)) + "," + side(1, 84, line(91, null, 130, expires = future) + "," + line(92, null, 129) + "," + line(93, null, 120)))},
        ${group(72, side(0, null, line(91, "230.5", -110, extra = altOver), name = "Over") + "," + side(1, null, line(91, "230.5", -110, extra = altUnder), name = "Under"))},
        ${group(73, side(0, 75, line(91, "3.5", -105)) + "," + side(1, 84, line(91, "-3.5", -115)))},
        ${group(80, side(0, 75, line(91, null, -105).replace("\"disabled\":false", "\"disabled\":true")) + "," + side(1, 84, line(91, null, -115)), eventId = 940357)}
      },"live":{}}}}},"lastUpdated":1791741900000},"success":true,"messages":[]}"""

    private val settings = ScanSettings(referenceBooks = listOf("draftkings", "fanduel", "betmgm", "williamhill_us"))
    private val sources = listOf(UnSource(91, "DraftKings", true, 1, 1), UnSource(92, "Novig", true, 1, 1), UnSource(93, "PrizePicks", true, 1, 1), UnSource(94, "Pinnacle-less MGM", true, 3, 3))
    private val teams = mapOf(75L to "Oklahoma City Thunder", 84L to "Indiana Pacers")

    private fun ctx(readAt: Long = clockMs, families: Set<MarketFamily> = MarketFamily.entries.toSet(), updated: Long? = null) = UnabatedContext(
        nba, UnabatedBooks.sourceMap(sources, settings.referenceBooks.toSet()), setOf(91L), emptyMap(), emptyMap(), teams, emptyMap(), families, false, readAt, updated,
    )

    // ---- parser ------------------------------------------------------------------------------------------------------------------------------

    @Test fun theBoardIsWalkedGroupByGroupAndALineThatIsDisabledOrUnavailableIsLeftOut() {
        val b = UnabatedParser.board(boardJson, includeLive = false)
        assertEquals(4, b.groups.size)
        assertEquals(1791741900000L, b.lastUpdatedMs)
        val total = b.groups.single { it.betTypeId == 72 }
        assertEquals("the main line and its alternate", 2, total.sides.first { it.sideIndex == 0 }.lines.size)
        assertTrue(total.sides.first { it.sideIndex == 0 }.lines.any { it.alternate && it.points == 231.5 && it.price == -120 })
        assertEquals(75L, total.awayTeamId); assertEquals(84L, total.homeTeamId)
        val disabled = b.groups.single { it.betTypeId == 80 }
        assertTrue("a disabled line has no price to read", disabled.sides.first { it.sideIndex == 0 }.lines.isEmpty())
    }

    @Test fun aLastUpdatedInSecondsIsReadAsSecondsAndASuccessFalseBoardThrowsItsMessages() {
        assertEquals(1791741900000L, UnabatedParser.board(boardJson.replace("1791741900000", "1791741900"), false).lastUpdatedMs)
        val e = runCatching { UnabatedParser.board("""{"data":null,"success":false,"messages":["No access to nfl"]}""", false) }.exceptionOrNull()
        assertTrue(e.toString(), e is UnabatedFailure && e.message!!.contains("No access to nfl"))
        assertTrue(runCatching { UnabatedParser.envelope("""{"message":"This endpoint is not included in Free API access."}""") }.exceptionOrNull() is UnabatedFailure)
    }

    @Test fun discoveryAnswersAreRead() {
        assertEquals("nba", UnabatedParser.leagues("""{"data":[{"id":3,"name":"nba","fullName":"NBA"}],"success":true,"messages":[]}""").single().name)
        assertEquals(72, UnabatedParser.betTypes("""{"data":[{"id":72,"name":"Total Points","hasPoints":true}],"success":true}""").single().id)
        assertEquals(mapOf(75L to "Oklahoma City Thunder"), UnabatedParser.teams("""{"data":{"75":{"id":75,"name":"Oklahoma City Thunder"}},"success":true}"""))
        assertEquals(mapOf(5L to "Shai Gilgeous-Alexander"), UnabatedParser.people("""{"data":{"5":{"id":5,"firstName":"Shai","lastName":"Gilgeous-Alexander"}},"success":true}"""))
        assertEquals(91L, UnabatedParser.sources("""{"data":[{"id":91,"name":"DraftKings","isActive":true,"statusId":1,"propsStatusId":2}],"success":true}""").single().id)
    }

    // ---- books ---------------------------------------------------------------------------------------------------------------------------

    @Test fun booksAreMatchedByNameAndNoveigKalshiPolymarketAndTheDfsAppsAreNeverTheFairLine() {
        assertEquals("draftkings", UnabatedBooks.appKey("DraftKings"))
        assertEquals("fanduel", UnabatedBooks.appKey("FanDuel"))
        assertEquals("betmgm", UnabatedBooks.appKey("MGM"))
        assertEquals("williamhill_us", UnabatedBooks.appKey("Caesars"))
        assertEquals("espnbet", UnabatedBooks.appKey("theScoreBet"))
        assertEquals("fanatics", UnabatedBooks.appKey("Fanatics"))
        for (n in listOf("Novig", "Kalshi", "Polymarket", "PrizePicks", "DraftKings Pick6", "Underdog", "Underdog Prediction Markets", "ProphetX")) assertNull(n, UnabatedBooks.appKey(n))
        val map = UnabatedBooks.sourceMap(sources, setOf("draftkings", "betmgm"))
        assertEquals("an offline source (status 3) and the excluded books give nothing", mapOf(91L to "draftkings"), map)
        assertEquals("nba", UnabatedBooks.routeFor(nba, listOf(UnLeague(3, "nba", "NBA"))))
        assertNull(UnabatedBooks.routeFor(Leagues.byNovigName("NHL")!!, listOf(UnLeague(3, "nba", "NBA"))))
    }

    // ---- converter -------------------------------------------------------------------------------------------------------------------------

    @Test fun everyKindBecomesALineWithBothSidesForTheWantedBookOnly() {
        val events = UnabatedConvert.events(UnabatedParser.board(boardJson, false), ctx())
        val e = events.single { it.id == "un-940356" }
        assertEquals("Indiana Pacers", e.home); assertEquals("Oklahoma City Thunder", e.away)
        assertTrue("only DraftKings: Novig and PrizePicks are never the fair line", e.markets.all { it.bookKey == "draftkings" })
        val ml = e.markets.single { it.kind == LineKind.MONEYLINE }
        assertEquals(1.0 + 100.0 / 150.0, ml.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-9)
        assertEquals(2.30, ml.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
        val totals = e.markets.filter { it.kind == LineKind.TOTAL }.sortedBy { it.line }
        assertEquals("the main total and its alternate", listOf(230.5, 231.5), totals.map { it.line })
        assertEquals(1.0 + 100.0 / 120.0, totals[1].quotes.single { it.side == Side.OVER }.decimalOdds, 1e-9)
        val spread = e.markets.single { it.kind == LineKind.SPREAD }
        assertEquals("the HOME handicap is the line", -3.5, spread.line!!, 1e-9)
        assertEquals(3.5, spread.quotes.single { it.side == Side.AWAY }.point!!, 1e-9)
        assertTrue("a game whose only line is disabled prices nothing", events.none { it.id == "un-940357" })
    }

    @Test fun aLineThatHasNotExpiredIsAsFreshAsTheReadMinusTheDelayAndAnOldUnexpiredAlternateKeepsItsOwnTime() {
        val e = UnabatedConvert.events(UnabatedParser.board(boardJson, false), ctx(updated = clockMs - 30_000L)).single()
        val ml = e.markets.single { it.kind == LineKind.MONEYLINE }
        assertEquals("freshnessExpiresAt in the future: current as of the read, 15 s behind", clockMs - UnabatedConvert.DELAY_MS, ml.lastUpdateMs)
        val alt = e.markets.single { it.kind == LineKind.TOTAL && it.line == 231.5 }
        assertEquals("an alternate with no expiry keeps its own modification time", Instant.parse("2026-10-11T12:00:00Z").toEpochMilli(), alt.lastUpdateMs)
        val main = e.markets.single { it.kind == LineKind.TOTAL && it.line == 230.5 }
        assertEquals("an unchanged main line of a high-frequency source is current when the board is recent", clockMs - UnabatedConvert.DELAY_MS, main.lastUpdateMs)
        val stale = UnabatedConvert.events(UnabatedParser.board(boardJson, false), ctx(updated = clockMs - 3_600_000L)).single().markets.single { it.kind == LineKind.TOTAL && it.line == 230.5 }
        assertEquals("a board an hour old vouches for nothing", Instant.parse("2026-10-11T12:00:00Z").toEpochMilli(), stale.lastUpdateMs)
    }

    @Test fun familiesTheScanDidNotAskForAreNotBuilt() {
        val e = UnabatedConvert.events(UnabatedParser.board(boardJson, false), ctx(families = setOf(MarketFamily.MONEYLINE))).single()
        assertEquals(listOf(LineKind.MONEYLINE), e.markets.map { it.kind }.distinct())
    }

    @Test fun aPlayerPropIsOverUnderWithTheNameFromThePeopleListAndItsStatFromTheBetTypeName() {
        val propJson = """{"data":{"odds":{"nba":{"leagueId":3,"periodTypes":{"game":{"periodTypeId":1,"pregame":{
          ${group(200, side(0, null, line(91, "28.5", -115), person = 5, name = "Over") + "," + side(1, null, line(91, "28.5", -105), person = 5, name = "Under"))}
        }}}}},"lastUpdated":1791741900000},"success":true,"messages":[]}"""
        val c = UnabatedContext(nba, mapOf(91L to "draftkings"), setOf(91L), mapOf(200 to UnBetType(200, "Player Points", "", true)), emptyMap(), teams, mapOf(5L to "Shai Gilgeous-Alexander"), MarketFamily.entries.toSet(), false, clockMs, clockMs)
        val m = UnabatedConvert.events(UnabatedParser.board(propJson, false), c).single().markets.single()
        assertEquals(LineKind.PLAYER_PROP, m.kind); assertEquals("Shai Gilgeous-Alexander", m.subject); assertEquals(28.5, m.line!!, 1e-9)
        assertNotNull(m.stat)
        val unmapped = UnabatedContext(nba, mapOf(91L to "draftkings"), setOf(91L), mapOf(200 to UnBetType(200, "Player Mascot Dances", "", true)), emptyMap(), teams, mapOf(5L to "X Y"), MarketFamily.entries.toSet(), false, clockMs, clockMs)
        assertTrue("a prop type with no stat is left alone, never guessed", UnabatedConvert.events(UnabatedParser.board(propJson, false), unmapped).isEmpty())
    }

    @Test fun thePeriodComesFromTheDiscoveredNameAndOnlyNovigsPeriodsAreKept() {
        fun p(name: String) = UnabatedConvert.periodOf(
            UnabatedParser.board(boardJson, false).groups.first().copy(periodTypeId = 9, periodKey = "x"),
            UnabatedContext(nba, emptyMap(), emptySet(), emptyMap(), mapOf(9 to UnPeriod(9, name, "")), emptyMap(), emptyMap(), emptySet(), false, 0L, null),
        )
        assertEquals(0, p("Game")); assertEquals(1, p("1st Half")); assertEquals(1, p("First 5 Innings")); assertEquals(RefBookMarket.PERIOD_FIRST_INNING, p("1st Inning")); assertNull(p("3rd Quarter"))
    }

    @Test fun unabatedIsTheLastSourceSoTheBetterFeedsWinAnyBookBothSend() {
        val order = Scanner.SOURCE_ORDER
        assertEquals(listOf("unabated", "unabated-props"), order.takeLast(2))
        assertTrue(order.indexOf("sgo") < order.indexOf("unabated") && order.indexOf("oddspapi") < order.indexOf("unabated") && order.indexOf("parlay") < order.indexOf("unabated"))
        assertEquals(setOf("unabated", "unabated-props"), ScanSettings(unabated = true).enabledSources.intersect(setOf("unabated", "unabated-props")))
        assertTrue(ScanSettings().enabledSources.none { it.startsWith("unabated") })
    }

    // ---- client and feed ---------------------------------------------------------------------------------------------------------------

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }
    private val sleeps = CopyOnWriteArrayList<Long>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) } }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })
    private fun client(keys: List<String> = listOf("k1")) = UnabatedClient(OkHttpClient(), { keys }, meter, server.url("/").toString().trimEnd('/'), { clockMs }, sleep = { sleeps += it })

    private fun route(r: RecordedRequest): MockResponse = when (r.requestUrl!!.encodedPath) {
        "/league" -> ok("""{"data":[{"id":3,"name":"nba","fullName":"NBA"}],"success":true,"messages":[]}""")
        "/bettype" -> ok("""{"data":[{"id":1,"name":"Moneyline"},{"id":72,"name":"Total"},{"id":73,"name":"Spread"}],"success":true}""")
        "/period-type/nba" -> ok("""{"data":[{"id":1,"name":"Game","abbreviation":"G"}],"success":true}""")
        "/market/nba/straight/sources" -> ok("""{"data":[{"id":91,"name":"DraftKings","isActive":true,"statusId":1},{"id":92,"name":"Novig","isActive":true,"statusId":1}],"success":true}""")
        "/market/nba/straight/teams" -> ok("""{"data":{"75":{"id":75,"name":"Oklahoma City Thunder"},"84":{"id":84,"name":"Indiana Pacers"}},"success":true}""")
        "/market/nba/straight/odds" -> ok(boardJson)
        else -> MockResponse().setResponseCode(404)
    }

    @Test fun theKeyTravelsInTheHeaderAndEveryOddsCallIsSpacedBySeconds() = runTest {
        handler = { route(it) }
        val c = client()
        c.oddsRaw("nba", "straight"); c.oddsRaw("nba", "straight")
        assertTrue(requests.all { it.getHeader("X-Api-Key") == "k1" && !it.requestUrl.toString().contains("k1") })
        assertEquals("the second odds call waits out the 5.2 s gate", listOf(5_200L), sleeps.toList())
        c.leagues(); c.betTypes()
        assertEquals("discovery calls do not use the odds interval", 1, sleeps.count { it == 5_200L })
    }

    @Test fun aRateLimitIsWaitedOutByItsRetryAfterAndTheSameKeyIsUsed() = runTest {
        var n = 0
        handler = { if (n++ == 0) MockResponse().setResponseCode(429).setHeader("Retry-After", "7") else ok(boardJson) }
        val body = client().oddsRaw("nba", "straight")
        assertTrue(body.contains("lastUpdated"))
        assertEquals(2, requests.size)
        assertTrue("it waited the server's 7 s", sleeps.any { it in 6_500L..7_500L })
        assertEquals(listOf("k1", "k1"), requests.map { it.getHeader("X-Api-Key") })
    }

    @Test fun aSnapshotUnavailable503IsRetriedBoundedThenFailsWithoutSwitchingKey() = runTest {
        handler = { MockResponse().setResponseCode(503).setHeader("Retry-After", "5") }
        val c = client()
        val e = runCatching { c.oddsRaw("nba", "straight") }.exceptionOrNull()
        assertTrue(e.toString(), e is ReferenceException && e.message!!.contains("503"))
        assertEquals("three attempts, then the failure is shown", UnabatedClient.MAX_ATTEMPTS, requests.size)
    }

    @Test fun aFreeKeyOnAnEndpointItLacksIsAFinal403AndAnInvalidKeyIsRefused() = runTest {
        handler = { MockResponse().setResponseCode(403).setBody("""{"message":"This endpoint is not included in Free API access."}""") }
        val e = runCatching { client().get("/subscriptions") }.exceptionOrNull()
        assertTrue(e.toString(), e is ReferenceException && e.message!!.contains("not included in Free API access"))
        assertEquals("a 403 is asked once", 1, requests.size)
        requests.clear()
        handler = { MockResponse().setResponseCode(401) }
        assertNotNull(runCatching { client().leagues() }.exceptionOrNull())
    }

    @Test fun httpSuccessWithSuccessFalseIsAFailureWhoseMessagesAreShownButItNeverMarksTheWholeApiDownWhileServerErrorsDo() = runTest {
        val c = client()
        handler = { route(it) }; c.leagues()   // one good answer first, so "down" is measured from it
        handler = { ok("""{"data":null,"success":false,"messages":["Delayed snapshot not ready"]}""") }
        val e = runCatching { c.oddsRaw("nba", "straight") }.exceptionOrNull()
        assertTrue(e.toString(), e is UnabatedFailure && e.message!!.contains("Delayed snapshot not ready"))
        runCatching { c.oddsRaw("nba", "straight") }; runCatching { c.oddsRaw("nba", "straight") }
        assertFalse("Unabated answered: not an outage (Tj's diagnostics: a props board it could not build took the whole API down)", c.down(clockMs + UnabatedClient.DOWN_AFTER_MS + 1))
        handler = { MockResponse().setResponseCode(500) }
        runCatching { c.get("/league") }; runCatching { c.get("/league") }
        assertTrue(c.down(clockMs + UnabatedClient.DOWN_AFTER_MS + 1)); assertFalse(c.down(clockMs))
    }

    @Test fun withTwoKeysARateLimitedKeyRestsAndTheNextKeyAnswersAtOnceAndEachKeyKeepsItsOwnInterval() = runTest {
        handler = { r -> if (r.getHeader("X-Api-Key") == "k1") MockResponse().setResponseCode(429).setHeader("Retry-After", "6") else ok(boardJson) }
        val c = client(listOf("k1", "k2"))
        assertTrue(c.oddsRaw("nba", "straight").contains("lastUpdated"))
        assertEquals(listOf("k1", "k2"), requests.map { it.getHeader("X-Api-Key") })
        assertTrue("no waiting for k2: it had its own interval", sleeps.none { it >= 1_000L })
        requests.clear()
        handler = { ok(boardJson) }
        c.oddsRaw("nba", "straight"); c.oddsRaw("nba", "straight")
        assertEquals("k1 is resting, so both go to k2, 5.2 s apart", listOf("k2", "k2"), requests.map { it.getHeader("X-Api-Key") })
        assertTrue(sleeps.any { it in 5_000L..5_300L })
    }

    @Test fun withTwoKeysTheCallsAlternateSoTwoRequestsGoOutWithoutWaiting() = runTest {
        handler = { ok(boardJson) }
        val c = client(listOf("k1", "k2"))
        c.oddsRaw("nba", "straight"); c.oddsRaw("nba", "straight")
        assertEquals(listOf("k1", "k2"), requests.map { it.getHeader("X-Api-Key") })
        assertTrue("two keys: no 5.2 s wait", sleeps.none { it >= 1_000L })
        c.oddsRaw("nba", "straight")
        assertEquals("the third waits for the first key's interval", "k1", requests.last().getHeader("X-Api-Key"))
        assertTrue(sleeps.any { it in 4_000L..5_300L })
    }

    @Test fun aRefusedKeyIsSkippedForTheNextOne() = runTest {
        handler = { r -> if (r.getHeader("X-Api-Key") == "bad") MockResponse().setResponseCode(401) else ok(boardJson) }
        assertTrue(client(listOf("bad", "good")).oddsRaw("nba", "straight").contains("lastUpdated"))
        assertEquals(listOf("bad", "good"), requests.map { it.getHeader("X-Api-Key") })
    }

    @Test fun aRealAlternateWithThe0001PlaceholderIsDatedByItsSequenceNumberAndAJunkAlternateIsDropped() {
        val j = """{"data":{"odds":{"nfl":{"leagueId":1,"periodTypes":{"game":{"periodTypeId":1,"pregame":{
          ${group(72, side(0, null, line(91, "230.5", -110, extra = ""","alternateLines":[{"marketSourceId":91,"alternateNumber":0,"points":10.0,"price":-10000,"statusId":1,"sequenceNumber":1791574208710,"modifiedOn":"0001-01-01T00:00:00Z"},{"marketSourceId":91,"alternateNumber":0,"points":231.5,"price":-120,"statusId":1,"sequenceNumber":1791574208710,"modifiedOn":"0001-01-01T00:00:00Z"}]"""), name = "Over") + "," + side(1, null, line(91, "230.5", -110), name = "Under"))}
        }}}}},"lastUpdated":1791741900000},"success":true,"messages":[]}"""
        val lines = UnabatedParser.board(j, false).groups.single().sides.first { it.sideIndex == 0 }.lines
        assertEquals("the junk -10000 alternate is dropped", 2, lines.size)
        assertEquals("1791574208710 is epoch ms: the real time of the change", 1791574208710L, lines.single { it.alternate }.modifiedMs)
    }

    @Test fun vigilantsCollegeLeaguesAreUnabatedsCfbAndCbbAndOnlyTheGameAndPropTypesWeCanPriceAreAsked() {
        val leagues = listOf(UnLeague(1, "NFL", ""), UnLeague(2, "CFB", ""), UnLeague(4, "CBB", ""))
        assertEquals("CFB", UnabatedBooks.routeFor(Leagues.byNovigName("NCAAF")!!, leagues))
        assertEquals("CBB", UnabatedBooks.routeFor(Leagues.byNovigName("NCAAB")!!, leagues))
        val types = listOf(UnBetType(1, "Moneyline", "", false), UnBetType(2, "Spread", "", true), UnBetType(3, "Total", "", true), UnBetType(845, "Team Total Odd/Even", "", false),
            UnBetType(14, "Passing Yards", "", true), UnBetType(37, "Most Rushing Yards", "", true), UnBetType(10, "Make Playoffs", "", false), UnBetType(59, "Player Field Goals Made", "", true))
        assertEquals(listOf(1, 2, 3), UnabatedConvert.gameBetTypeIds(types))
        assertEquals(listOf(14, 59), UnabatedConvert.propBetTypeIds(types, Leagues.byNovigName("NFL")!!))
    }

    @Test fun aBoardUnabatedCannotBuildIsAnEmptyBoardForAWhileNotAFailedScan() = runTest {
        handler = { r -> if (r.requestUrl!!.encodedPath.endsWith("/sources")) ok("""{"data":null,"success":false,"messages":["Unexpected error while retrieving market sources for the provided filter."]}""") else route(r) }
        val feed = UnabatedFeed(client(), CoroutineScope(Dispatchers.IO)) { clockMs }
        val r = feed.read(nba, "straight", settings)!!
        assertTrue(r.events.isEmpty())
        val n = requests.size
        feed.read(nba, "straight", settings)
        assertEquals("and it is not asked again for ten minutes", n, requests.size)
    }

    @Test fun theBoardIsAskedWithTheBetTypeFilter() = runTest {
        handler = { route(it) }
        val feed = UnabatedFeed(client(), CoroutineScope(Dispatchers.IO)) { clockMs }
        feed.read(nba, "straight", settings)
        val odds = requests.single { it.requestUrl!!.encodedPath.endsWith("/odds") }.requestUrl!!
        assertEquals(listOf("1", "72", "73"), odds.queryParameterValues("betTypeId").sortedBy { it!!.toInt() })
    }

    @Test fun theFeedReadsABoardOnceServesItFromCacheAndRefreshesInTheBackgroundOnceStale() = runTest {
        handler = { route(it) }
        val feed = UnabatedFeed(client(), CoroutineScope(Dispatchers.IO)) { clockMs }
        val first = feed.read(nba, "straight", settings)!!
        assertEquals(1, first.events.size)
        assertTrue(first.events.single().markets.all { it.bookKey == "draftkings" })
        feed.read(nba, "straight", settings)
        assertEquals("within the TTL no second board is read", 1, requests.count { it.requestUrl!!.encodedPath.endsWith("/odds") })
        clockMs += UnabatedFeed.GAMES_TTL_MS + 1
        val stale = feed.read(nba, "straight", settings)!!
        assertEquals("the stale board is returned at once", first.readAtMs, stale.readAtMs)
        val deadline = System.currentTimeMillis() + 5_000
        while (requests.count { it.requestUrl!!.encodedPath.endsWith("/odds") } < 2 && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertEquals("and refreshed behind it", 2, requests.count { it.requestUrl!!.encodedPath.endsWith("/odds") })
        assertEquals("the league list, bet types, periods, sources and teams were each asked once", 1, requests.count { it.requestUrl!!.encodedPath == "/league" })
    }

    @Test fun aLeagueTheKeyDoesNotCarryGivesAnEmptySnapshotAndTheSourcesAreFallbacksOnly() = runTest {
        handler = { route(it) }
        val feed = UnabatedFeed(client(), CoroutineScope(Dispatchers.IO)) { clockMs }
        val games = UnabatedGamesSource(feed) { clockMs }
        assertTrue(games.odds(Leagues.byNovigName("NHL")!!, settings).events.isEmpty())
        assertEquals("unabated", games.odds(nba, settings).provider)
        assertTrue(games.supports(nba) && !games.supports(Leagues.byNovigName("ATP") ?: nba.copy(novigName = "ATP")))
        assertFalse(games.metered); assertNull(games.fallbackFor)
        val props = UnabatedPropsSource(feed) { clockMs }
        assertTrue(props.propsOnly)
        assertTrue("props are not read when the scan did not ask for them", props.odds(nba, settings.copy(families = setOf(MarketFamily.MONEYLINE))).events.isEmpty())
    }
}
