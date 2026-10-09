package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OddsPapi v5 (ODDSPAPI_API.md): parser, market table, books, converter, client, feed and sources against answers built by hand in the shapes the OpenAPI document and its examples publish. No live key has
 * been used: when Tj's Test-key sample arrives, a test is added from its real answers (the way SgoRealDataTest was).
 */
class OddsPapiTest {
    // ---- fixtures in the documented shapes ---------------------------------------------------------------------------------------------------

    private val now = 1_790_500_000_000L
    private val startSec = (now + 2 * 3_600_000L) / 1000L
    private val nfl = Leagues.byNovigName("NFL")!!
    private val fx = "id1400003160574217"

    /** One price row, as `/fixtures/odds` sends it (OpenAPI example: bookmaker, outcomeId, playerId, active, price, marketActive, mainLine, marketId, limit, priceAmerican, changedAt…). */
    private fun price(book: String, outcome: Long, dec: Double, market: Long, main: Boolean = true, changed: Long = now - 600_000L, player: Long = 0, active: Boolean = true): String =
        """"$fx:$book:$outcome:$player":{"bookmaker":"$book","outcomeId":$outcome,"playerId":$player,"active":$active,"price":$dec,"marketActive":true,"mainLine":$main,"marketId":$market,"limit":2000,"changedAt":$changed,"bookmakerChangedAt":$changed}"""

    private fun meta(vararg slugs: Pair<String, String>) =
        slugs.joinToString(",") { (slug, extra) -> """"$slug":{"bookmaker":"$slug","hasOdds":true,"staleOdds":false,"suspended":false,"participantsRotated":false$extra}""" }

    private fun fixture(rows: Map<String, List<String>>, meta: String = meta("pinnacle" to "", "draftkings" to ""), p1: String = "Minnesota Vikings", p2: String = "Detroit Lions", id: String = fx, status: Int = 0): String =
        """{"fixtureId":"$id","reissuedFixtureId":null,"status":{"live":${status == 1},"statusId":$status},"sport":{"sportId":14,"sportName":"American Football"},
        "tournament":{"tournamentId":31,"tournamentName":"NFL","categoryName":"USA"},"startTime":$startSec,
        "participants":{"participant1Id":4423,"participant1Name":"$p1","participant2Id":4419,"participant2Name":"$p2"},"scores":{},
        "externalProviders":{"pinnacleId":"1628488896"},"bookmakers":{$meta},
        "odds":{${rows.entries.joinToString(",") { (book, ps) -> "\"$book\":{${ps.joinToString(",")}}" }}}}"""

    private val markets = """[
      {"marketId":141,"marketLength":2,"sportId":14,"playerProp":false,"handicap":0.0,"period":"result","marketType":"1x2","marketName":"Winner","outcomes":[{"outcomeId":141,"outcomeName":"1"},{"outcomeId":142,"outcomeName":"2"}]},
      {"marketId":201,"marketLength":2,"sportId":14,"playerProp":false,"handicap":-3.5,"period":"result","marketType":"spreads","marketName":"Spread","outcomes":[{"outcomeId":201,"outcomeName":"1"},{"outcomeId":202,"outcomeName":"2"}]},
      {"marketId":203,"marketLength":2,"sportId":14,"playerProp":false,"handicap":-7.0,"period":"result","marketType":"spreads","marketName":"Spread","outcomes":[{"outcomeId":203,"outcomeName":"1"},{"outcomeId":204,"outcomeName":"2"}]},
      {"marketId":301,"marketLength":2,"sportId":14,"playerProp":false,"handicap":47.5,"period":"result","marketType":"totals","marketName":"Total","outcomes":[{"outcomeId":301,"outcomeName":"Over"},{"outcomeId":302,"outcomeName":"Under"}]},
      {"marketId":401,"marketLength":2,"sportId":14,"playerProp":false,"handicap":24.5,"period":"result","marketType":"teamtotals-team1","marketName":"Team 1 total","outcomes":[{"outcomeId":401,"outcomeName":"Over"},{"outcomeId":402,"outcomeName":"Under"}]},
      {"marketId":403,"marketLength":2,"sportId":14,"playerProp":false,"handicap":21.5,"period":"result","marketType":"teamtotals-team2","marketName":"Team 2 total","outcomes":[{"outcomeId":403,"outcomeName":"Over"},{"outcomeId":404,"outcomeName":"Under"}]},
      {"marketId":501,"marketLength":2,"sportId":14,"playerProp":false,"handicap":-1.5,"period":"p1+p2","marketType":"spreads","marketName":"1H Spread","outcomes":[{"outcomeId":501,"outcomeName":"1"},{"outcomeId":502,"outcomeName":"2"}]},
      {"marketId":601,"marketLength":2,"sportId":14,"playerProp":true,"handicap":250.5,"period":"result","marketType":"players-passingyards","marketName":"Passing yards","outcomes":[{"outcomeId":601,"outcomeName":"Over"},{"outcomeId":602,"outcomeName":"Under"}]},
      {"marketId":701,"marketLength":3,"sportId":14,"playerProp":false,"handicap":0.0,"period":"fulltime","marketType":"1x2","marketName":"3-way","outcomes":[{"outcomeId":701,"outcomeName":"1"},{"outcomeId":702,"outcomeName":"X"},{"outcomeId":703,"outcomeName":"2"}]},
      {"marketId":801,"marketLength":2,"sportId":14,"playerProp":true,"handicap":3.5,"period":"result","marketType":"players-mysterystat","marketName":"Mystery","outcomes":[{"outcomeId":801,"outcomeName":"Over"},{"outcomeId":802,"outcomeName":"Under"}]},
      {"marketId":901,"marketLength":2,"sportId":14,"playerProp":false,"handicap":0.0,"period":"fulltime","marketType":"1x2","marketName":"Winner (reg)","outcomes":[{"outcomeId":901,"outcomeName":"1"},{"outcomeId":902,"outcomeName":"2"}]}
    ]"""

    private val catalog = """[{"slug":"pinnacle","bookmakerName":"Pinnacle","active":true,"maxDelayPregameInSec":2.0},{"slug":"draftkings","bookmakerName":"DraftKings"},{"slug":"caesars","bookmakerName":"Caesars"},
      {"slug":"circa","bookmakerName":"Circa Sports"},{"slug":"novig","bookmakerName":"Novig"},{"slug":"obscurebook","bookmakerName":"Obscure"}]"""

    private val tournaments = """[{"tournamentId":900,"sportId":14,"tournamentSlug":"nfl-preseason","categorySlug":"usa","tournamentName":"NFL Preseason","categoryName":"USA"},
      {"tournamentId":31,"sportId":14,"tournamentSlug":"nfl","categorySlug":"usa","tournamentName":"NFL","categoryName":"USA"},
      {"tournamentId":32,"sportId":14,"tournamentSlug":"cfl","categorySlug":"canada","tournamentName":"CFL","categoryName":"Canada"}]"""

    private val mlRows = listOf(price("pinnacle", 141, 1.9, 141, changed = now - 3_000_000L), price("pinnacle", 142, 1.95, 141, changed = now - 3_000_000L))

    private fun settings(books: List<String> = listOf("pinnacle", "draftkings", "williamhill_us"), extra: Boolean = true, alt: Boolean = true, families: Set<MarketFamily> = MarketFamily.entries.toSet()) =
        ScanSettings(referenceBooks = books, families = families, oddsPapi = true, opExtraBooks = extra, opAltLines = alt)

    private fun omarkets(league: String = "NFL") = OpMarkets(OpParser.markets(markets), league)
    private val wanted = setOf("pinnacle", "draftkings", "williamhill_us", "circa")
    private fun convert(f: OpFixture, games: Boolean = true, props: Boolean = true, names: Map<Long, String> = mapOf(777L to "Mahomes, Patrick")) =
        OpConvert.toRef(f, omarkets(), "americanfootball_nfl", wanted, now, names, games, props)

    // ---- parser ---------------------------------------------------------------------------------------------------------------------------

    @Test fun parserReadsAFixtureWithItsPricesAndBookStates() {
        val raw = fixture(mapOf("pinnacle" to mlRows), meta("pinnacle" to "", "draftkings" to ""","staleOdds":true"""))
        val f = OpParser.fixtures("[$raw]").single()
        assertEquals(fx, f.id)
        assertEquals(startSec * 1000L, f.startMs)               // schedule times are seconds, odds times milliseconds
        assertEquals("Minnesota Vikings", f.p1); assertEquals("Detroit Lions", f.p2)
        assertEquals(14, f.sportId); assertEquals(31L, f.tournamentId); assertEquals("NFL", f.tournament)
        assertEquals("1628488896", f.pinnacleId)
        assertEquals(2, f.prices.size)
        assertEquals(141L, f.prices[0].outcomeId); assertEquals(1.9, f.prices[0].decimal!!, 1e-9); assertTrue(f.prices[0].mainLine)
        assertFalse(f.started)
    }

    @Test fun parserTakesABareObjectAnArrayOrAWrapperAndSkipsWhatItCannotRead() {
        val one = fixture(mapOf("pinnacle" to mlRows))
        assertEquals(1, OpParser.fixtures(one).size)
        assertEquals(1, OpParser.fixtures("[$one]").size)
        assertEquals(1, OpParser.fixtures("""{"data":[$one,{"nofixtureid":1}]}""").size)
        assertTrue(OpParser.fixtures("not json").isEmpty())
        assertTrue(OpParser.fixtures("""{"error":401,"code":"invalid_api_key"}""").isEmpty())
    }

    @Test fun parserReadsAmericanWhenThereIsNoDecimalAndIdsFromTheOddsIdWhenTheRowOmitsThem() {
        val raw = """{"fixtureId":"$fx","startTime":$startSec,"participants":{"participant1Name":"A","participant2Name":"B"},"odds":{"pinnacle":{"$fx:pinnacle:141:0":{"priceAmerican":-110,"active":true,"changedAt":$now}}}}"""
        val p = OpParser.fixtures(raw).single().prices.single()
        assertEquals(141L, p.outcomeId); assertEquals(0L, p.playerId); assertEquals(1.909, p.decimal!!, 0.001)
    }

    @Test fun parserReadsMarketsBooksTournamentsClvAndHistory() {
        val m = OpParser.markets(markets)
        assertEquals(141L, m[0].marketId); assertEquals("1x2", m[0].type); assertEquals("result", m[0].period); assertEquals(listOf(141L to "1", 142L to "2"), m[0].outcomes)
        assertTrue(m.first { it.marketId == 601L }.playerProp)
        assertEquals(listOf("pinnacle", "draftkings", "caesars", "circa", "novig", "obscurebook"), OpParser.bookmakers(catalog).map { it.slug })
        assertEquals(2.0, OpParser.bookmakers(catalog).first().maxDelayPregameSec!!, 1e-9)
        assertEquals(listOf(900L, 31L, 32L), OpParser.tournaments(tournaments).map { it.id })
        val clv = OpParser.clv("""{"fixtureId":"$fx","odds":{"pinnacle":{"$fx:pinnacle:141:0":{"clv":{"price":1.8,"changedAt":${now - 1000}},"olv":{"price":2.0,"changedAt":${now - 900000}}}}}}""").single()
        assertEquals(1.8, clv.closeDecimal!!, 1e-9); assertEquals(2.0, clv.openDecimal!!, 1e-9); assertEquals(141L, clv.outcomeId)
        val h = OpParser.historical("""{"fixtureId":"$fx","odds":{"pinnacle":{"$fx:pinnacle:141:0":{"${now - 5000}":{"price":1.7,"active":true,"changedAt":${now - 5000}},"${now - 1000}":{"price":1.6,"active":true,"changedAt":${now - 1000}}}}}}""")
        assertEquals(listOf(1.7, 1.6), h.sortedBy { it.changedMs }.map { it.decimal })
    }

    // ---- the market table -------------------------------------------------------------------------------------------------------------------

    @Test fun marketsAreClassifiedByTypePeriodAndOutcomeNamesAndNothingElseIsGuessed() {
        val m = omarkets()
        assertEquals(LineKind.MONEYLINE, m.classify(141)!!.kind)
        val sp = m.classify(201)!!
        assertEquals(LineKind.SPREAD, sp.kind); assertEquals(-3.5, sp.point!!, 1e-9); assertEquals(0, sp.period)
        assertEquals(LineKind.TOTAL, m.classify(301)!!.kind)
        assertEquals(1, m.classify(401)!!.team); assertEquals(2, m.classify(403)!!.team)
        assertEquals(1, m.classify(501)!!.period)                       // p1+p2 = the first half of a quarter sport
        val prop = m.classify(601)!!
        assertEquals("PASSING_YARDS", prop.stat); assertEquals(250.5, prop.point!!, 1e-9)
        assertNull("three outcomes: not a 2-way market", m.classify(701))
        assertNull("a prop type with no Vigilant stat is not guessed at", m.classify(801))
        assertTrue("players-mysterystat" in m.unmappedProps)
        assertTrue("players-passingyards" !in m.unmappedProps)
        assertEquals(141L, m.marketOf(142))
    }

    @Test fun periodsMapPerLeagueAndNcaabPlaysHalves() {
        assertEquals(1, OpMarkets.periodOf("p1", "NCAAB")); assertNull(OpMarkets.periodOf("p1", "NBA"))
        assertEquals(1, OpMarkets.periodOf("p1+p2", "NBA"))
        assertEquals(1, OpMarkets.periodOf("p1+p2+p3+p4+p5", "MLB")); assertEquals(RefBookMarket.PERIOD_FIRST_INNING, OpMarkets.periodOf("p1", "MLB"))
        assertNull("regulation is not Novig's game", OpMarkets.periodOf("fulltime", "NHL")); assertEquals(0, OpMarkets.periodOf("result", "NHL"))
    }

    @Test fun anAmericanFootballTwoWayOnFulltimeIsUsedOnlyWhenTheBookHasNoResultOne() {
        val fallbackOnly = OpParser.fixtures(fixture(mapOf("pinnacle" to listOf(price("pinnacle", 901, 1.9, 901), price("pinnacle", 902, 1.95, 901))))).single()
        assertEquals(1, convert(fallbackOnly)!!.markets.count { it.kind == LineKind.MONEYLINE })
        val both = OpParser.fixtures(fixture(mapOf("pinnacle" to listOf(price("pinnacle", 901, 1.5, 901), price("pinnacle", 902, 2.6, 901)) + mlRows))).single()
        val ml = convert(both)!!.markets.filter { it.kind == LineKind.MONEYLINE }
        assertEquals(1, ml.size)
        assertEquals(1.9, ml.single().quotes.first { it.side == Side.HOME }.decimalOdds, 1e-9)
    }

    // ---- books ----------------------------------------------------------------------------------------------------------------------------

    @Test fun booksAreMatchedByLettersAndDigitsAndExchangesAreNeverTheFairLine() {
        assertEquals("hardrockbet", OpBooks.appKey("hard-rock-bet")); assertEquals("hardrockbet", OpBooks.appKey("hardrock"))
        assertEquals("williamhill_us", OpBooks.appKey("caesars")); assertEquals("betonlineag", OpBooks.appKey("betonline.ag"))
        assertEquals("circa", OpBooks.appKey("x", "Circa Sports"))
        assertNull(OpBooks.appKey("novig")); assertNull(OpBooks.appKey("kalshi")); assertNull(OpBooks.appKey("polymarket")); assertNull(OpBooks.appKey("prophetx")); assertNull(OpBooks.appKey("prizepicks"))
        val cat = OpParser.bookmakers(catalog)
        assertEquals(listOf("caesars", "circa", "draftkings", "pinnacle"), OpBooks.slugsFor(wanted, cat))
        assertEquals(setOf("pinnacle", "circa", "superbook", "bet365"), OpBooks.wanted(listOf("pinnacle", "novig"), true))
        assertEquals(setOf("pinnacle"), OpBooks.wanted(listOf("pinnacle"), false))
    }

    @Test fun theTournamentIsTheLeagueNotAPreseasonOrAnotherCountry() {
        val t = OpParser.tournaments(tournaments)
        assertEquals(31L, OpBooks.tournamentFor(nfl, t)!!.id)
        assertNull("no WNBA in this catalogue", OpBooks.tournamentFor(Leagues.byNovigName("WNBA")!!, t))
        assertEquals(11, OpBooks.sportId(Leagues.byNovigName("NBA")!!)); assertEquals(15, OpBooks.sportId(Leagues.byNovigName("NHL")!!)); assertNull(OpBooks.sportId(Leagues.byNovigName("ATP")!!))
    }

    @Test fun outsideOpRestsAFeedForEveryLeagueOpCarriesAndNoOther() {
        val inner = object : ReferenceSource {
            override val id = "x"; override val displayName = "x"
            override suspend fun odds(league: com.tjshea.vigilant.data.scanner.League, settings: ScanSettings) = RefSnapshot("k", emptyList(), 0L)
        }
        val o = OutsideOp(inner)
        assertFalse(o.supports(nfl)); assertTrue(o.supports(Leagues.byNovigName("ATP")!!)); assertEquals("x", o.id)
    }

    // ---- conversion -------------------------------------------------------------------------------------------------------------------------

    private fun full(): OpFixture {
        val pin = listOf(
            price("pinnacle", 141, 1.9, 141), price("pinnacle", 142, 1.95, 141),
            price("pinnacle", 201, 1.91, 201), price("pinnacle", 202, 1.95, 201),                                 // main spread -3.5
            price("pinnacle", 203, 2.6, 203, main = false, changed = now - 4_000_000L), price("pinnacle", 204, 1.5, 203, main = false, changed = now - 4_000_000L),
            price("pinnacle", 301, 1.9, 301), price("pinnacle", 302, 1.9, 301),
            price("pinnacle", 401, 1.85, 401), price("pinnacle", 402, 1.97, 401),
            price("pinnacle", 501, 1.9, 501, changed = now - 20_000L), price("pinnacle", 502, 1.9, 501, changed = now - 20_000L),
            price("pinnacle", 601, 1.87, 601, player = 777), price("pinnacle", 602, 1.95, 601, player = 777),
            price("pinnacle", 801, 1.9, 801, player = 777), price("pinnacle", 802, 1.9, 801, player = 777),
        )
        val dk = listOf(price("draftkings", 301, 1.91, 301), price("draftkings", 302, 1.91, 301, active = false))   // one side off: no price at all
        val nov = listOf(price("novig", 141, 1.9, 141), price("novig", 142, 1.9, 141), price("obscurebook", 141, 1.9, 141), price("obscurebook", 142, 1.9, 141))
        return OpParser.fixtures(fixture(mapOf("pinnacle" to pin, "draftkings" to dk, "novig" to nov, "obscurebook" to nov.drop(2)))).single()
    }

    @Test fun everyKindBecomesALineWithBothSidesAndNeverThePricedBookOrAHalfPrice() {
        val ref = convert(full())!!
        assertEquals("op:$fx", ref.id); assertEquals("Minnesota Vikings", ref.home); assertEquals("Detroit Lions", ref.away); assertEquals(startSec * 1000L, ref.commenceMs)
        val ms = ref.markets
        assertTrue("only wanted books", ms.all { it.bookKey == "pinnacle" })
        val spread = ms.filter { it.kind == LineKind.SPREAD && it.period == 0 }.associateBy { it.line }
        assertEquals(setOf(-3.5, -7.0), spread.keys)                                             // the main line and the alternate
        val s = spread.getValue(-3.5)
        assertEquals(-3.5, s.quotes.first { it.side == Side.HOME }.point!!, 1e-9); assertEquals(3.5, s.quotes.first { it.side == Side.AWAY }.point!!, 1e-9)
        assertEquals(1.91, s.quotes.first { it.side == Side.HOME }.decimalOdds, 1e-9)
        assertEquals(47.5, ms.single { it.kind == LineKind.TOTAL }.line!!, 1e-9)
        assertEquals(RefBookMarket.HOME, ms.single { it.kind == LineKind.TEAM_TOTAL && it.line == 24.5 }.subject)
        assertEquals(1, ms.single { it.kind == LineKind.SPREAD && it.period == 1 }.period)
        val prop = ms.single { it.kind == LineKind.PLAYER_PROP }
        assertEquals("Patrick Mahomes", prop.subject); assertEquals("PASSING_YARDS", prop.stat); assertEquals(250.5, prop.line!!, 1e-9)
        assertTrue("draftkings had one side off", ms.none { it.bookKey == "draftkings" })
        assertTrue("an unmapped prop type is left out", ms.count { it.kind == LineKind.PLAYER_PROP } == 1)
    }

    @Test fun theGamesSourceAndThePropsSourceTakeTheirOwnShare() {
        assertTrue(convert(full(), games = true, props = false)!!.markets.none { it.kind == LineKind.PLAYER_PROP })
        assertTrue(convert(full(), games = false, props = true)!!.markets.all { it.kind == LineKind.PLAYER_PROP })
        assertTrue("a prop without a player name is left out", convert(full(), props = true, names = emptyMap())!!.markets.none { it.kind == LineKind.PLAYER_PROP })
    }

    @Test fun aMainLineOfAConnectedBookIsAsFreshAsTheReadButOtherLinesKeepTheirOwnChangeTime() {
        val ms = convert(full())!!.markets
        assertEquals("a steady main line is current", now, ms.single { it.kind == LineKind.MONEYLINE }.lastUpdateMs)   // its price last MOVED 50 minutes ago
        assertEquals("an alternate: its own age", now - 4_000_000L, ms.single { it.kind == LineKind.SPREAD && it.line == -7.0 }.lastUpdateMs)
    }

    @Test fun aBookThatDroppedSuspendedOrRotatedGivesNothing() {
        fun with(extra: String) = OpParser.fixtures(fixture(mapOf("pinnacle" to mlRows), meta("pinnacle" to extra))).single()
        assertEquals(1, convert(with(""))!!.markets.size)
        assertTrue(convert(with(""","staleOdds":true"""))!!.markets.isEmpty())
        assertTrue(convert(with(""","suspended":true"""))!!.markets.isEmpty())
        assertTrue(convert(with(""","participantsRotated":true"""))!!.markets.isEmpty())
        assertNull("cancelled", OpConvert.toRef(OpParser.fixtures(fixture(mapOf("pinnacle" to mlRows), status = 3)).single(), omarkets(), "k", wanted, now, emptyMap()))
    }

    @Test fun aBooksLinesAreCappedNearTheMainLine() {
        val many = (0 until 30).flatMap { i ->
            val a = 1000L + i * 2; val b = a + 1
            listOf(price("pinnacle", a, 1.9, a, main = i == 0, player = 0), price("pinnacle", b, 1.9, a, main = i == 0))
        }
        val bigMarkets = OpMarkets((0 until 30).map { i ->
            val a = 1000L + i * 2
            OpMarket(a, "totals", "result", 40.5 + i, false, "t", listOf(a to "Over", a + 1 to "Under"))
        }, "NFL")
        val f = OpParser.fixtures(fixture(mapOf("pinnacle" to many))).single()
        val ref = OpConvert.toRef(f, bigMarkets, "k", wanted, now, emptyMap())!!
        assertEquals(OpConvert.MAX_LINES, ref.markets.size)
        assertTrue("the main line is kept", ref.markets.any { it.line == 40.5 })
        assertTrue("the far rungs are not", ref.markets.none { it.line == 69.5 })
    }

    // ---- client, feed, sources ---------------------------------------------------------------------------------------------------------------

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }
    private var clockMs = now

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) } }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })
    private fun client(keys: List<String> = listOf("k1")) = OddsPapiClient(OkHttpClient(), KeyPool(QuotaPolicy.ODDSPAPI, { keys }, meter), server.url("/en").toString().trimEnd('/'), clock = { clockMs }, oddsGapMs = 0, otherGapMs = 0, retryDelayMs = { 0L })
    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun route(r: RecordedRequest): MockResponse = when (r.requestUrl!!.encodedPath) {
        "/en/bookmakers" -> ok(catalog)
        "/en/tournaments" -> ok(tournaments)
        "/en/markets" -> ok(markets)
        "/en/players" -> ok("""[{"playerId":777,"playerName":"Mahomes, Patrick"}]""")
        "/en/fixtures/odds/main" -> ok("[" + fixture(mapOf("pinnacle" to (mlRows + listOf(price("pinnacle", 201, 1.91, 201), price("pinnacle", 202, 1.95, 201))))) + "]")
        "/en/fixtures/odds" -> ok(fixture(mapOf("pinnacle" to full().prices.filter { it.book == "pinnacle" }.map { p -> price(p.book, p.outcomeId, p.decimal!!, p.marketId!!, p.mainLine, p.changedMs!!, p.playerId) })))
        else -> MockResponse().setResponseCode(404)
    }

    @Test fun theKeyTravelsInTheHeaderNeverTheUrl() = runTest {
        handler = { ok(catalog) }
        client().bookmakers()
        val r = requests.single()
        assertEquals("k1", r.getHeader("X-API-Key")); assertFalse(r.requestUrl.toString().contains("k1")); assertEquals("/en/bookmakers", r.requestUrl!!.encodedPath)
    }

    @Test fun aRateLimitedKeyRestsAndTheNextKeyIsTried() = runTest {
        handler = { r -> if (r.getHeader("X-API-Key") == "k1") MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("""{"error":429,"code":"rate_limited","retryAfterSec":1}""") else ok(catalog) }
        val books = client(listOf("k1", "k2")).bookmakers()
        assertEquals(6, books.size)
        assertEquals(listOf("k1", "k2"), requests.map { it.getHeader("X-API-Key") })
    }

    @Test fun anInvalidKeyIsRefusedAndAForbiddenEndpointIsNotRetriedNorRefusesTheKey() = runTest {
        handler = { MockResponse().setResponseCode(401).setBody("""{"error":401,"code":"invalid_api_key"}""") }
        val e = runCatching { client().bookmakers() }.exceptionOrNull()
        assertNotNull(e)
        requests.clear()
        handler = { MockResponse().setResponseCode(403).setBody("""{"error":403,"code":"channel_not_allowed"}""") }
        val f = runCatching { client().get("/fixtures/odds/clv") }.exceptionOrNull()
        assertTrue(f.toString(), f is ReferenceException && f.message!!.contains("403") && f.message!!.contains("channel_not_allowed"))
        assertEquals("a 403 is final: asked once", 1, requests.size)
    }

    @Test fun aServerErrorIsRetriedOnceAndTwoFailuresInARowMarkItDown() = runTest {
        var n = 0
        handler = { if (n++ == 0) MockResponse().setResponseCode(503) else ok(catalog) }
        val c = client()
        assertEquals(6, c.bookmakers().size)
        assertEquals(2, requests.size)
        handler = { MockResponse().setResponseCode(500) }
        runCatching { c.get("/bookmakers") }; runCatching { c.get("/bookmakers") }
        assertTrue(c.down(clockMs + OddsPapiClient.DOWN_AFTER_MS + 1)); assertFalse(c.down(clockMs))
        handler = { ok(catalog) }
        c.get("/bookmakers")
        assertFalse(c.down(clockMs + 10 * OddsPapiClient.DOWN_AFTER_MS))
    }

    @Test fun theFeedReadsMainLinesInOneRequestThenEachGameInDepthAndSharesTheReadBetweenTheTwoSources() = runTest {
        handler = ::route
        val c = client()
        val feed = OddsPapiFeed(c) { clockMs }
        val games = OpGamesSource(feed, { clockMs }); val props = OpPropsSource(feed)
        val g = games.odds(nfl, settings())
        val p = props.odds(nfl, settings())
        assertEquals("oddspapi", g.provider); assertEquals("oddspapi-props", p.provider)
        val main = requests.single { it.requestUrl!!.encodedPath == "/en/fixtures/odds/main" }.requestUrl!!
        assertEquals("31", main.queryParameter("tournamentId"))
        assertEquals("caesars,circa,draftkings,pinnacle", main.queryParameter("bookmakers"))
        val deep = requests.single { it.requestUrl!!.encodedPath == "/en/fixtures/odds" }.requestUrl!!
        assertEquals(fx, deep.queryParameter("fixtureId"))
        assertEquals("one read serves both sources", 1, requests.count { it.requestUrl!!.encodedPath == "/en/fixtures/odds/main" })
        assertTrue(g.events.single().markets.any { it.kind == LineKind.SPREAD && it.line == -7.0 })               // an alternate came with the depth read
        assertTrue(g.events.single().markets.none { it.kind == LineKind.PLAYER_PROP })
        assertEquals("Patrick Mahomes", p.events.single().markets.single().subject)
        assertEquals(1, requests.count { it.requestUrl!!.encodedPath == "/en/players" })
        assertTrue(requests.all { it.getHeader("X-API-Key") == "k1" })
        // after the keep time the league is read again
        clockMs += OddsPapiFeed.TTL_MS + 1
        games.odds(nfl, settings())
        assertEquals(2, requests.count { it.requestUrl!!.encodedPath == "/en/fixtures/odds/main" })
        assertTrue(c.lastReads.getValue("NFL").contains("NFL (id 31)"))
    }

    @Test fun withoutAlternatesOrPropsOnlyTheOneMainLinesRequestIsMade() = runTest {
        handler = ::route
        val feed = OddsPapiFeed(client()) { clockMs }
        val snap = OpGamesSource(feed, { clockMs }).odds(nfl, settings(alt = false, families = setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD)))
        assertTrue(requests.none { it.requestUrl!!.encodedPath == "/en/fixtures/odds" })
        assertEquals(setOf(LineKind.MONEYLINE, LineKind.SPREAD), snap.events.single().markets.map { it.kind }.toSet())
    }

    @Test fun aFamilySwitchedOffIsNotKept() = runTest {
        handler = ::route
        val snap = OpGamesSource(OddsPapiFeed(client()) { clockMs }, { clockMs }).odds(nfl, settings(families = setOf(MarketFamily.TOTAL)))
        assertEquals(setOf(LineKind.TOTAL), snap.events.single().markets.map { it.kind }.toSet())
    }

    @Test fun aGameWhoseDepthReadFailsKeepsItsMainLines() = runTest {
        handler = { r -> if (r.requestUrl!!.encodedPath == "/en/fixtures/odds") MockResponse().setResponseCode(403).setBody("""{"error":403,"code":"channel_not_allowed"}""") else route(r) }
        val c = client()
        val snap = OpGamesSource(OddsPapiFeed(c) { clockMs }, { clockMs }).odds(nfl, settings(families = setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD)))
        assertEquals(setOf(LineKind.MONEYLINE, LineKind.SPREAD), snap.events.single().markets.map { it.kind }.toSet())
        assertTrue(c.lastReads.getValue("NFL").contains("failed"))
    }

    @Test fun aLeagueTheCatalogueLacksIsAnErrorNotAWrongCompetition() = runTest {
        handler = { r -> if (r.requestUrl!!.encodedPath == "/en/tournaments") ok("""[{"tournamentId":5,"sportId":14,"tournamentName":"Arena League","categoryName":"USA"}]""") else route(r) }
        val e = runCatching { OpGamesSource(OddsPapiFeed(client()) { clockMs }, { clockMs }).odds(nfl, settings()) }.exceptionOrNull()
        assertTrue(e.toString(), e is ReferenceException && e.message!!.contains("NFL"))
    }

    @Test fun gamesStartingBeyondTheWindowAndLiveGamesAreLeftOut() = runTest {
        handler = { r -> if (r.requestUrl!!.encodedPath == "/en/fixtures/odds/main") ok("[" + fixture(mapOf("pinnacle" to mlRows), id = "live1", status = 1) + "]") else route(r) }
        val feed = OddsPapiFeed(client()) { clockMs }
        val s = settings(alt = false, families = setOf(MarketFamily.MONEYLINE))
        assertTrue(OpGamesSource(feed, { clockMs }).odds(nfl, s).events.isEmpty())
        clockMs += OddsPapiFeed.TTL_MS + 1
        assertEquals(1, OpGamesSource(feed, { clockMs }).odds(nfl, s.copy(includeLive = true)).events.size)
    }

    @Test fun sourcesSupportOnlyTheLeaguesOddsPapiCarriesAndPropsOnlyWherePropsAreMapped() {
        val feed = OddsPapiFeed(client())
        val games = OpGamesSource(feed); val props = OpPropsSource(feed)
        for (n in listOf("NFL", "NCAAF", "NBA", "NCAAB", "WNBA", "MLB", "NHL")) { assertTrue(n, games.supports(Leagues.byNovigName(n)!!)); assertTrue(n, props.supports(Leagues.byNovigName(n)!!)) }
        assertFalse(games.supports(Leagues.byNovigName("ATP")!!)); assertFalse(props.supports(Leagues.byNovigName("UFC")!!))
        assertTrue(props.propsOnly); assertFalse(games.propsOnly); assertTrue("PASSING_YARDS" in props.extraPropTypes)
    }

    @Test fun theKeyTestSaysWhatTheKeyCarriesAndSavesTheRawAnswers() = runTest {
        handler = { r -> if (r.requestUrl!!.encodedPath == "/en/fixtures/odds/clv") ok("""{"fixtureId":"$fx","odds":{"pinnacle":{"$fx:pinnacle:141:0":{"clv":{"price":1.8,"changedAt":$now},"olv":{"price":2.0,"changedAt":$now}}}}}""") else route(r) }
        val r = OpKeyTest.run(client(), now)
        assertTrue(r.summary, r.ok)
        assertTrue(r.summary, r.summary.contains("fair-line books found") && r.summary.contains("NFL: tournament \"NFL\"") && r.summary.contains("players-mysterystat"))
        assertTrue(r.summary, r.summary.contains("closing-line endpoint: 1 prices, 1 with a close, 1 with an open"))
        assertTrue(r.sample.contains("=== GET /bookmakers ===") && r.sample.contains(fx))
    }

    @Test fun theKeyTestTellsAV4KeyApartFromTheV5Kind() = runTest {
        handler = { MockResponse().setResponseCode(401).setBody("""{"error":401,"code":"invalid_api_key"}""") }
        val r = OpKeyTest.run(client(), now)
        assertFalse(r.ok)
        assertTrue(r.summary, r.summary.contains("v5") && r.summary.contains("v4") && r.summary.contains("55-tech"))
    }
}
