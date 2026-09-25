package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.BookPropSet
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sportsbook player props through The Odds API (Tj's request, 2026-09-25 ~16:15Z): parsed across
 * the books' listing styles, bought only for games Novig lists props for within a strict credit
 * budget, re-used per game, and priced as a devigged market average.
 */
class OddsApiPropsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = HashMap<String, MockResponse>()
    private val hour = 3_600_000L
    private val now = 1_790_500_000_000L

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.requestUrl!!.encodedPath] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))

    private fun client() = TheOddsApiClient(
        OkHttpClient(), KeyPool(QuotaPolicy.ODDS_API, { listOf("test-key") }, meter), json,
        server.url("/v4").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0,
    )

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    // ---- the Novig board ------------------------------------------------------------------------

    private val ravensCowboys = NovigEvent("nA", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", now + 3 * hour)
    private val billsJets = NovigEvent("nB", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Buffalo Bills @ New York Jets", now + 30 * hour)
    private val chiefsBroncos = NovigEvent("nD", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Kansas City Chiefs @ Denver Broncos", now + 5 * hour)
    private val yankeesRedSox = NovigEvent("nC", "BASEBALL", "MLB", NovigEvent.STATUS_PREGAME, "New York Yankees @ Boston Red Sox", now + 1 * hour)

    private fun prop(id: String, eventId: String, type: String, player: String, line: Double, startsTs: Long) =
        NovigMarket(id, eventId, type, "OPEN", "$player $line $type", startsTs, MarketFee.GAME,
            listOf(NovigOutcome("$id-o", "Over $line", "TBD"), NovigOutcome("$id-u", "Under $line", "TBD")))

    private val board = ScanContext(
        novigEvents = listOf(ravensCowboys, billsJets, chiefsBroncos, yankeesRedSox),
        novigMarkets = listOf(
            prop("pA1", "nA", "PASSING_YARDS", "Lamar Jackson", 224.5, ravensCowboys.startsTs),
            prop("pA2", "nA", "RECEPTIONS", "CeeDee Lamb", 6.5, ravensCowboys.startsTs),
            prop("pB1", "nB", "PASSING_YARDS", "Josh Allen", 244.5, billsJets.startsTs),
            prop("pC1", "nC", "HITS", "Aaron Judge", 0.5, yankeesRedSox.startsTs),
            prop("pC2", "nC", "PITCHER_STRIKEOUTS", "Garrett Crochet", 7.5, yankeesRedSox.startsTs),
            // The Broncos game has only a moneyline on Novig: no props worth buying.
            NovigMarket("mD", "nD", "MONEY", "OPEN", "KC", chiefsBroncos.startsTs, MarketFee.GAME,
                listOf(NovigOutcome("d1", "KC", "TBD"), NovigOutcome("d2", "DEN", "TBD"))),
        ),
        now = now,
    )

    private val settings = ScanSettings(
        leagues = setOf("NFL", "MLB"),
        referenceBooks = listOf("draftkings", "fanduel"),
        bookPropSet = BookPropSet.CORE,
        bookPropCreditsPerScan = 24,
        bookPropHours = 24,
        bookPropReuseMinutes = 60,
    )

    private val nfl = Leagues.byNovigName("NFL")!!
    private val mlb = Leagues.byNovigName("MLB")!!

    // ---- The Odds API responses ---------------------------------------------------------------

    private val nflEvents = """[
        {"id":"oA","sport_key":"americanfootball_nfl","commence_time":"${iso(now + 3 * hour)}","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens"},
        {"id":"oB","sport_key":"americanfootball_nfl","commence_time":"${iso(now + 30 * hour)}","home_team":"New York Jets","away_team":"Buffalo Bills"},
        {"id":"oD","sport_key":"americanfootball_nfl","commence_time":"${iso(now + 5 * hour)}","home_team":"Denver Broncos","away_team":"Kansas City Chiefs"}
    ]"""

    private val mlbEvents = """[
        {"id":"oC","sport_key":"baseball_mlb","commence_time":"${iso(now + hour)}","home_team":"Boston Red Sox","away_team":"New York Yankees"}
    ]"""

    /**
     * DraftKings writes "Lamar Jackson"; FanDuel writes "Jackson, Lamar" and "Ceedee Lamb". Each
     * posts a line nobody else does, and one side-only line that can't be devigged.
     */
    private val ravensCowboysProps = """{
        "id":"oA","sport_key":"americanfootball_nfl","commence_time":"${iso(now + 3 * hour)}",
        "home_team":"Dallas Cowboys","away_team":"Baltimore Ravens",
        "bookmakers":[
          {"key":"draftkings","title":"DraftKings","last_update":"${iso(now - 60_000)}","markets":[
            {"key":"player_pass_yds","last_update":"${iso(now - 60_000)}","outcomes":[
              {"name":"Over","description":"Lamar Jackson","price":1.87,"point":224.5},
              {"name":"Under","description":"Lamar Jackson","price":1.95,"point":224.5},
              {"name":"Over","description":"Dak Prescott","price":1.90,"point":249.5}
            ]},
            {"key":"player_receptions","outcomes":[
              {"name":"Over","description":"CeeDee Lamb","price":1.80,"point":6.5},
              {"name":"Under","description":"CeeDee Lamb","price":2.02,"point":6.5},
              {"name":"Over","description":"CeeDee Lamb","price":1.45,"point":5.5},
              {"name":"Under","description":"CeeDee Lamb","price":2.75,"point":5.5}
            ]}
          ]},
          {"key":"fanduel","title":"FanDuel","last_update":"${iso(now - 30_000)}","markets":[
            {"key":"player_pass_yds","outcomes":[
              {"name":"Over","description":"Jackson, Lamar","price":1.91,"point":224.5},
              {"name":"Under","description":"Jackson, Lamar","price":1.91,"point":224.5},
              {"name":"Over","description":"Lamar Jackson","price":1.83,"point":225.5},
              {"name":"Under","description":"Lamar Jackson","price":1.99,"point":225.5}
            ]},
            {"key":"player_receptions","outcomes":[
              {"name":"Over","description":"Ceedee Lamb","price":1.83,"point":6.5},
              {"name":"Under","description":"Ceedee Lamb","price":1.98,"point":6.5}
            ]}
          ]}
        ]}"""

    private val yankeesRedSoxProps = """{
        "id":"oC","sport_key":"baseball_mlb","commence_time":"${iso(now + hour)}",
        "home_team":"Boston Red Sox","away_team":"New York Yankees",
        "bookmakers":[
          {"key":"draftkings","title":"DraftKings","markets":[
            {"key":"batter_hits","outcomes":[
              {"name":"Over","description":"Aaron Judge","price":1.60,"point":0.5},
              {"name":"Under","description":"Aaron Judge","price":2.35,"point":0.5}
            ]}
          ]}
        ]}"""

    private fun routeAll() {
        routes["/v4/sports/americanfootball_nfl/events"] = MockResponse().setBody(nflEvents).setHeader("x-requests-remaining", "480")
        routes["/v4/sports/baseball_mlb/events"] = MockResponse().setBody(mlbEvents).setHeader("x-requests-remaining", "480")
        routes["/v4/sports/americanfootball_nfl/events/oA/odds"] = MockResponse().setBody(ravensCowboysProps)
            .setHeader("x-requests-remaining", "476").setHeader("x-requests-used", "24").setHeader("x-requests-last", "2")
        routes["/v4/sports/baseball_mlb/events/oC/odds"] = MockResponse().setBody(yankeesRedSoxProps)
            .setHeader("x-requests-remaining", "474").setHeader("x-requests-used", "26").setHeader("x-requests-last", "1")
    }

    private fun paths() = requests.map { it.requestUrl!!.encodedPath }

    // ---- parsing ------------------------------------------------------------------------------

    @Test
    fun `a book's flat prop list becomes one over-under line per player and number`() {
        val e = TheOddsApiClient.parseEvent(ravensCowboysProps, json)
        val dk = e.markets.filter { it.bookKey == "draftkings" }
        // Lamar 224.5, Lamb 6.5, Lamb 5.5. Dak's Over-only line has nothing to devig against.
        assertEquals(3, dk.size)
        assertTrue(dk.none { it.subject == "Dak Prescott" })
        val lamar = dk.single { it.stat == "PASSING_YARDS" }
        assertEquals(LineKind.PLAYER_PROP, lamar.kind)
        assertEquals("Lamar Jackson", lamar.subject)
        assertEquals(224.5, lamar.line!!, 0.0)
        assertEquals(1.87, lamar.quotes.single { it.side == Side.OVER }.decimalOdds, 0.0)
        assertEquals(1.95, lamar.quotes.single { it.side == Side.UNDER }.decimalOdds, 0.0)
        assertEquals(listOf(5.5, 6.5), dk.filter { it.stat == "RECEPTIONS" }.map { it.line!! }.sorted())
        // The Odds API's market key is translated to Novig's stat name.
        assertEquals(setOf("PASSING_YARDS", "RECEPTIONS"), e.markets.mapNotNull { it.stat }.toSet())
    }

    @Test
    fun `Yes-No props are over-under 0_5, and a Yes-only book is dropped`() {
        val body = """{"id":"x","sport_key":"americanfootball_nfl","commence_time":"${iso(now)}","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens",
          "bookmakers":[
            {"key":"draftkings","title":"DraftKings","markets":[{"key":"player_anytime_td","outcomes":[
              {"name":"Yes","description":"Derrick Henry","price":1.72},
              {"name":"No","description":"Derrick Henry","price":2.10}
            ]}]},
            {"key":"fanduel","title":"FanDuel","markets":[{"key":"player_anytime_td","outcomes":[
              {"name":"Yes","description":"Derrick Henry","price":1.70}
            ]}]}
          ]}"""
        val m = TheOddsApiClient.parseEvent(body, json).markets.single()
        assertEquals("draftkings", m.bookKey)
        assertEquals("TOUCHDOWNS", m.stat)
        assertEquals(0.5, m.line!!, 0.0)
        assertEquals(1.72, m.quotes.single { it.side == Side.OVER }.decimalOdds, 0.0)
        assertEquals(2.10, m.quotes.single { it.side == Side.UNDER }.decimalOdds, 0.0)
    }

    @Test
    fun `alternate ladders and Over-only markets are never requested`() {
        for (sport in listOf("americanfootball_nfl", "baseball_mlb", "basketball_wnba")) {
            val all = PropStats.oddsApiMarkets(sport, BookPropSet.ALL)
            assertTrue(sport, all.isNotEmpty())
            assertTrue(all.none { it.endsWith("_alternate") || it == "player_tds_over" })
            assertEquals(4, PropStats.oddsApiMarkets(sport, BookPropSet.CORE).size)
        }
        assertTrue(PropStats.oddsApiMarkets("americanfootball_ncaaf", BookPropSet.ALL).isEmpty())
        // Only the stats Novig lists for the game are bought.
        assertEquals(listOf("player_receptions"), PropStats.oddsApiMarkets("americanfootball_nfl", BookPropSet.CORE, setOf("RECEPTIONS", "TOUCHDOWNS")))
        assertEquals(listOf("player_receptions", "player_tds", "player_anytime_td"), PropStats.oddsApiMarkets("americanfootball_nfl", BookPropSet.ALL, setOf("RECEPTIONS", "TOUCHDOWNS")))
    }

    @Test
    fun `the game list is free and the props call names books, never regions`() = runTest {
        routeAll()
        val c = client()
        val listed = c.events("americanfootball_nfl", now + 48 * hour)
        assertEquals(3, listed.value.size)
        val odds = c.eventOdds("americanfootball_nfl", "oA", listOf("draftkings", "novig", "fanduel"), listOf("player_pass_yds", "player_receptions"))
        assertEquals(476, odds.remaining)
        val eventsCall = requests[0].requestUrl!!
        assertEquals(Instant.ofEpochMilli(now + 48 * hour).toString().replace(".000", ""), eventsCall.queryParameter("commenceTimeTo"))
        val propsCall = requests[1].requestUrl!!
        assertEquals("draftkings,fanduel", propsCall.queryParameter("bookmakers"))
        assertEquals("player_pass_yds,player_receptions", propsCall.queryParameter("markets"))
        assertNull(propsCall.queryParameter("regions"))
        assertEquals("decimal", propsCall.queryParameter("oddsFormat"))
        // The meter charged what the header said: 0 for the list, 2 for the props.
        assertEquals(2, meter.book.value.providers[QuotaPolicy.ODDS_API.provider]!!.keys.values.single().used)
    }

    // ---- choosing games -----------------------------------------------------------------------

    @Test
    fun `only games Novig lists props for, inside the window, soonest first`() = runTest {
        routeAll()
        val source = OddsApiPropsSource(client())
        val nflSnap = source.odds(nfl, settings, board)
        val mlbSnap = source.odds(mlb, settings, board)
        // Ravens-Cowboys (3h) and Yankees-Red Sox (1h) are bought. Bills-Jets (30h) is outside
        // the 24h window and Chiefs-Broncos has no props on Novig: neither costs a credit.
        assertEquals(listOf("oA"), nflSnap.events.map { it.id })
        assertEquals(listOf("oC"), mlbSnap.events.map { it.id })
        assertFalse(paths().any { it.endsWith("/oB/odds") || it.endsWith("/oD/odds") })
        // Only the stats Novig lists for each game are asked for.
        val nflAsk = requests.single { it.requestUrl!!.encodedPath.endsWith("/oA/odds") }.requestUrl!!
        assertEquals("player_pass_yds,player_receptions", nflAsk.queryParameter("markets"))
        val mlbAsk = requests.single { it.requestUrl!!.encodedPath.endsWith("/oC/odds") }.requestUrl!!
        assertEquals("batter_hits,pitcher_strikeouts", mlbAsk.queryParameter("markets"))
        assertEquals(474, mlbSnap.creditsRemaining)
    }

    @Test
    fun `the credit budget covers the soonest games across every league first`() = runTest {
        routeAll()
        val source = OddsApiPropsSource(client())
        // Two credits: exactly the Yankees game (1h, 2 prop types), even though NFL is scanned first.
        val tight = settings.copy(bookPropCreditsPerScan = 2)
        val nflSnap = source.odds(nfl, tight, board)
        val mlbSnap = source.odds(mlb, tight, board)
        assertTrue(nflSnap.events.isEmpty())
        assertEquals(listOf("oC"), mlbSnap.events.map { it.id })
        assertFalse(paths().any { it.contains("americanfootball_nfl") })

        requests.clear()
        val none = settings.copy(bookPropCreditsPerScan = 0)
        source.odds(nfl, none, board.copy(now = now + 1))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `nothing is bought when player props are off`() = runTest {
        routeAll()
        val source = OddsApiPropsSource(client())
        val snap = source.odds(nfl, settings.copy(families = setOf(MarketFamily.MONEYLINE)), board)
        assertTrue(snap.events.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a game's props are re-used inside the window, then bought again`() = runTest {
        routeAll()
        val source = OddsApiPropsSource(client())
        source.odds(nfl, settings, board)
        val bought = requests.size

        // Ten minutes later: nothing new to buy, and the same props come back.
        val again = source.odds(nfl, settings, board.copy(now = now + 10 * 60_000))
        assertEquals(bought, requests.size)
        assertEquals(listOf("oA"), again.events.map { it.id })

        // Past the hour: the soonest game is bought again.
        source.odds(nfl, settings, board.copy(now = now + 61 * 60_000))
        assertEquals(2, requests.count { it.requestUrl!!.encodedPath.endsWith("/oA/odds") })

        // A different set of books is a different ask: bought fresh, not re-used.
        source.odds(nfl, settings.copy(referenceBooks = listOf("betmgm")), board.copy(now = now + 62 * 60_000))
        assertEquals(3, requests.count { it.requestUrl!!.encodedPath.endsWith("/oA/odds") })
    }

    @Test
    fun `a failure halfway keeps the props already bought and says what failed`() = runTest {
        routeAll()
        // A second NFL game with props, whose props call fails.
        val second = NovigEvent("nD", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Kansas City Chiefs @ Denver Broncos", now + 5 * hour)
        val ctx = board.copy(
            novigEvents = board.novigEvents.filter { it.eventId != "nD" } + second,
            novigMarkets = board.novigMarkets + prop("pD1", "nD", "PASSING_YARDS", "Patrick Mahomes", 249.5, second.startsTs),
        )
        routes["/v4/sports/americanfootball_nfl/events/oD/odds"] = MockResponse().setResponseCode(500).setBody("oops")
        val source = OddsApiPropsSource(client())
        try {
            source.odds(nfl, settings, ctx)
            fail("expected a partial result")
        } catch (e: PartialReferenceException) {
            assertEquals(listOf("oA"), e.partial.events.map { it.id })
            assertTrue(e.message!!, e.message!!.startsWith("Sportsbook props NFL:"))
        }
    }

    // ---- pricing ------------------------------------------------------------------------------

    @Test
    fun `a Novig prop prices at the books' devigged average, matched across name styles`() = runTest {
        routeAll()
        val snap = OddsApiPropsSource(client()).odds(nfl, settings, board)
        val s = settings.copy(fairSource = FairSource.MARKET_AVERAGE, devigMethod = DevigMethod.MULTIPLICATIVE, minBooks = 2, minEvPercent = -1.0, maxEvPercent = 1.0)
        val plan = Planner.plan(board.novigEvents, board.novigMarkets, listOf(snap), s, now)
        assertTrue(plan.marketIds.containsAll(listOf("pA1", "pA2")))

        val books = mapOf(
            "pA1" to NovigBook("pA1", 1, mapOf("pA1-o" to listOf(BidLevel(470, 1000)), "pA1-u" to listOf(BidLevel(490, 1000))), now),
            "pA2" to NovigBook("pA2", 1, mapOf("pA2-o" to listOf(BidLevel(450, 1000)), "pA2-u" to listOf(BidLevel(500, 1000))), now),
        )
        val r = Pricing.price(plan, books, s, now)

        // Lamar 224.5: DraftKings ("Lamar Jackson") and FanDuel ("Jackson, Lamar"), each devigged,
        // then averaged. FanDuel's 225.5 is another line and stays out.
        fun fair(over: Double, under: Double) = (1 / over) / (1 / over + 1 / under)
        val lamar = r.opportunities.single { it.outcome.outcomeId == "pA1-o" }
        assertEquals((fair(1.87, 1.95) + fair(1.91, 1.91)) / 2, lamar.fairProbability!!, 1e-12)
        assertEquals("Lamar Jackson Over 224.5", lamar.selection)

        // Lamb 6.5: "CeeDee Lamb" and "Ceedee Lamb" are one player; the 5.5 line is not his 6.5.
        val lamb = r.opportunities.single { it.outcome.outcomeId == "pA2-u" }
        assertEquals((fair(2.02, 1.80) + fair(1.98, 1.83)) / 2, lamb.fairProbability!!, 1e-12)
    }
}
