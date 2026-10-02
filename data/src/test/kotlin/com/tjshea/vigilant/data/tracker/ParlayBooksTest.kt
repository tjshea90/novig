package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.OddsFeed
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import java.io.File
import java.time.Instant

/**
 * Check odds now's backup for CrazyNinjaOdds (Tj, 2026-09-30: "if there is a good backup that does the same exact odds check, I think
 * parlayapi can do this same odds check"): an open bet's every-book prices from ParlayAPI, shaped like CNO's game page so CNO's own check
 * judges them. The answers are ParlayAPI's own, as Tj's key got them on 2026-09-30, trimmed to a few books.
 */
class ParlayBooksTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Instant.parse("2026-09-30T05:00:00Z").toEpochMilli()
    private lateinit var server: MockWebServer
    private val asked = ArrayList<String>()

    private val props = """[
        {"event_id":"a42df93f323ba531dc8e545efdb2dd94","sport_key":"americanfootball_nfl","game_date":"2026-10-04","home_team":"Buffalo Bills","away_team":"New England Patriots","commence_time":"2026-10-04T17:00:00.000Z","bookmaker":"caesars","bookmaker_title":"Caesars","player":"Josh Allen","market_key":"player_passing_attempts","period":"FULL","market":"Passing Attempts","line":29.5,"over_price":-113,"under_price":-117,"last_update":"2026-09-30T04:06:27Z","age_seconds":129.6},
        {"event_id":"a42df93f323ba531dc8e545efdb2dd94","sport_key":"americanfootball_nfl","game_date":"2026-10-04","home_team":"Buffalo Bills","away_team":"New England Patriots","commence_time":"2026-10-04T17:00:00.000Z","bookmaker":"draftkings","bookmaker_title":"DraftKings","player":"Josh Allen","market_key":"player_passing_attempts","period":"FULL","market":"Passing Attempts","line":29.5,"over_price":-109,"under_price":-117,"last_update":"2026-09-30T04:00:35Z","age_seconds":481.6},
        {"event_id":"a42df93f323ba531dc8e545efdb2dd94","sport_key":"americanfootball_nfl","game_date":"2026-10-04","home_team":"Buffalo Bills","away_team":"New England Patriots","commence_time":"2026-10-04T17:00:00.000Z","bookmaker":"fanduel","bookmaker_title":"FanDuel","player":"Josh Allen","market_key":"player_passing_attempts","period":"FULL","market":"Passing Attempts","line":29.5,"over_price":-122,"under_price":-108,"last_update":"2026-09-30T04:00:35Z","age_seconds":481.6},
        {"event_id":"a42df93f323ba531dc8e545efdb2dd94","sport_key":"americanfootball_nfl","game_date":"2026-10-04","home_team":"Buffalo Bills","away_team":"New England Patriots","commence_time":"2026-10-04T17:00:00.000Z","bookmaker":"caesars","bookmaker_title":"Caesars","player":"Josh Allen","market_key":"player_anytime_td","period":"FULL","market":"Anytime Touchdown Scorer","line":0.5,"over_price":-120,"under_price":null,"last_update":"2026-09-30T04:06:27Z","age_seconds":129.6}
    ]"""

    private val odds = """[{"id":"02466c20070007c5fc3867453fa291ce","sport_key":"americanfootball_nfl","sport_title":"NFL","commence_time":"2026-10-02T00:15:00Z","home_team":"Cleveland Browns","away_team":"Pittsburgh Steelers","bookmakers":[
        {"key":"fanduel","title":"FanDuel","last_update":"2026-09-30T04:07:43Z","markets":[{"key":"h2h","outcomes":[{"name":"Cleveland Browns","price":2.2},{"name":"Pittsburgh Steelers","price":1.704}]},{"key":"totals","outcomes":[{"name":"Over","price":1.98,"point":38.5},{"name":"Under","price":1.833,"point":38.5}]},{"key":"spreads","outcomes":[{"name":"Cleveland Browns","price":1.926,"point":2.5},{"name":"Pittsburgh Steelers","price":1.893,"point":-2.5}]}]},
        {"key":"pinnacle","title":"Pinnacle","last_update":"2026-09-30T04:07:34Z","markets":[{"key":"h2h","outcomes":[{"name":"Cleveland Browns","price":2.31},{"name":"Pittsburgh Steelers","price":1.676}]},{"key":"totals","outcomes":[{"name":"Over","price":1.909,"point":38.5},{"name":"Under","price":1.943,"point":38.5}]}]},
        {"key":"draftkings","title":"DraftKings","last_update":"2026-09-30T04:07:42Z","markets":[{"key":"h2h","outcomes":[{"name":"Cleveland Browns","price":2.24},{"name":"Pittsburgh Steelers","price":1.676}]},{"key":"totals","outcomes":[{"name":"Over","price":1.952,"point":38.5},{"name":"Under","price":1.87,"point":38.5}]},{"key":"spreads","outcomes":[{"name":"Cleveland Browns","price":2.0,"point":2.5},{"name":"Pittsburgh Steelers","price":1.833,"point":-2.5}]}]},
        {"key":"caesars","title":"Caesars","last_update":"2026-09-30T04:07:42Z","markets":[{"key":"h2h","outcomes":[{"name":"Cleveland Browns","price":2.23},{"name":"Pittsburgh Steelers","price":1.69}]},{"key":"totals","outcomes":[{"name":"Over","price":1.909,"point":38.5},{"name":"Under","price":1.917,"point":38.5}]},{"key":"spreads","outcomes":[{"name":"Cleveland Browns","price":1.943,"point":2.5},{"name":"Pittsburgh Steelers","price":1.877,"point":-2.5}]}]}]}]"""

    @Before fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    asked += request.requestUrl!!.encodedPath
                    val path = request.requestUrl!!.encodedPath
                    return when {
                        path.endsWith("/props") -> MockResponse().setBody(props).setHeader("x-requests-remaining", "19900").setHeader("x-requests-used", "100")
                        path.endsWith("/odds") -> MockResponse().setBody(odds).setHeader("x-requests-remaining", "19895").setHeader("x-requests-used", "105")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun books(active: Boolean = true) = ParlayBooks(
        TheOddsApiClient(
            OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter), json,
            baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
        ),
        active = { active }, clock = { now },
    )

    private fun bet(id: String, event: String, starts: String, market: String, selection: String) = TrackedBet(
        id, now - 3_600_000L, "NFL", event, Instant.parse(starts).toEpochMilli(), market, selection, "m", "", 0.5, 0.5, 0.52, 0.04, 10.0,
    )

    private fun us(decimal: Double) = Odds.decimalToAmerican(decimal)

    @Test
    fun `a prop's every-book prices at its line, the way CNO's game page lists them`() = runBlocking<Unit> {
        val b = books()
        val allen = bet("p", "New England Patriots @ Buffalo Bills", "2026-10-04T17:00:00Z", "Passing Attempts", "Josh Allen Under 29.5")
        val view = b.view(allen)!!
        val byCode = view.prices.associateBy { it.code }
        assertEquals(setOf("CZR", "DK", "FD"), byCode.keys)
        // The under first (the bet's side), the over as the other side.
        assertEquals(-117 to -113, byCode.getValue("CZR").odds to byCode.getValue("CZR").otherOdds)
        assertEquals(-108 to -122, byCode.getValue("FD").odds to byCode.getValue("FD").otherOdds)
        // CNO's check reads it like a game page: three two-sided books.
        val check = CnoBooks.check(view, BetRecheck.rowOf(allen), preferListOdds = true)
        assertEquals(3, check.twoSided)
        assertTrue(check.fairProbability!! in 0.45..0.55)
        // Another line, or a one-sided market, isn't this bet.
        assertNull(b.view(allen.copy(id = "q", selection = "Josh Allen Under 30.5")))
    }

    @Test
    fun `game lines from each book at the bet's own number, the league asked once for a whole pass`() = runBlocking<Unit> {
        val b = books()
        val game = "Pittsburgh Steelers @ Cleveland Browns"
        val start = "2026-10-02T00:15:00Z"
        val ml = b.view(bet("ml", game, start, "Moneyline", "Cleveland Browns"))!!.prices.associateBy { it.code }
        assertEquals(setOf("FD", "PN", "DK", "CZR"), ml.keys)
        assertEquals(us(2.31) to us(1.676), ml.getValue("PN").odds to ml.getValue("PN").otherOdds)
        val spread = b.view(bet("s", game, start, "Spread", "Pittsburgh Steelers -2.5"))!!.prices.associateBy { it.code }
        assertEquals(setOf("FD", "DK", "CZR"), spread.keys) // Pinnacle's answer had no spread
        assertEquals(us(1.893) to us(1.926), spread.getValue("FD").odds to spread.getValue("FD").otherOdds)
        val under = b.view(bet("t", game, start, "Total", "Under 38.5"))!!.prices.associateBy { it.code }
        assertEquals(us(1.943) to us(1.909), under.getValue("PN").odds to under.getValue("PN").otherOdds)
        assertNull(b.view(bet("s2", game, start, "Spread", "Pittsburgh Steelers -3.5")))
        // Four bets, one game-lines call.
        assertEquals(listOf("/v1/sports/americanfootball_nfl/odds"), asked)
        assertEquals(1, b.requests)
    }

    @Test
    fun `off, or a league ParlayAPI doesn't carry, asks nothing`() = runBlocking<Unit> {
        assertNull(books(active = false).view(bet("p", "New England Patriots @ Buffalo Bills", "2026-10-04T17:00:00Z", "Passing Attempts", "Josh Allen Under 29.5")))
        assertNull(books().view(bet("x", "A @ B", "2026-10-04T17:00:00Z", "Moneyline", "A").copy(league = "Nowhere League")))
        assertTrue(asked.isEmpty())
    }

    /** Tj, 2026-10-01: "Build tennis through parlayapi": a tennis bet's books from the tour's answer, in the bet's own unit (PARLAY_API.md §6.11). */
    @Test
    fun `a tennis bet's books come in its own unit, games or sets, from the tour's answer`() = runBlocking<Unit> {
        val tennisNow = Instant.parse("2026-10-01T01:40:00Z").toEpochMilli()
        val paths = ArrayList<okhttp3.HttpUrl>()
        val tennis = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.requestUrl!!
                    return if (request.requestUrl!!.encodedPath == "/v1/sports/tennis_atp/odds") {
                        MockResponse().setBody(javaClass.classLoader!!.getResource("parlay-tennis-atp.json")!!.readText()).setHeader("x-requests-last", "3")
                    } else MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        try {
            val b = ParlayBooks(
                TheOddsApiClient(
                    OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter), json,
                    baseUrl = tennis.url("/v1").toString().trimEnd('/'), clock = { tennisNow }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
                ),
                active = { true }, clock = { tennisNow },
            )
            fun bet(id: String, market: String, selection: String) = TrackedBet(
                id, tennisNow - 3_600_000L, "ATP", "Arthur Rinderknech @ Alex Molcan Round of 32", Instant.parse("2026-10-01T03:05:00Z").toEpochMilli(),
                market, selection, "m", "", 0.5, 0.5, 0.52, 0.04, 10.0,
            )
            // Molcan +1.5 GAMES: BetMGM's +1.5 games line, never Pinnacle's +1.5 sets.
            val games = b.view(bet("g", "Games Spread", "Alex Molcan +1.5"))!!
            assertEquals(listOf("MGM"), games.prices.map { it.code })
            assertEquals(us(1.98) to us(1.714), games.prices.single().odds to games.prices.single().otherOdds)
            // Molcan +1.5 SETS: Pinnacle's set line.
            val sets = b.view(bet("s", "Set Spread", "Alex Molcan +1.5"))!!
            assertEquals(listOf("PN"), sets.prices.map { it.code })
            assertEquals(us(1.559) to us(2.55), sets.prices.single().odds to sets.prices.single().otherOdds)
            val totalSets = b.view(bet("ts", "Total Sets", "Under 2.5"))!!
            assertEquals(us(1.633) to us(2.37), totalSets.prices.single().odds to totalSets.prices.single().otherOdds)
            // The winner: every book's, FanDuel's own listing included.
            assertEquals(setOf("PN", "B365", "CZR", "DK", "FD", "PX"), b.view(bet("w", "Moneyline", "Arthur Rinderknech"))!!.prices.map { it.code }.toSet())
            // One call for the tour (3 credits: no alternates), shared by every bet.
            assertEquals(1, paths.size)
            assertEquals("h2h,spreads,totals", paths.single().queryParameter("markets"))
        } finally {
            tennis.shutdown()
        }
    }

    /** Tj, 2026-10-02: a sharp-book confirmation judges a quote by its own time, so each book's price carries the feed's `last_update`. */
    @Test
    fun `each book's price carries its own last_update, the time a sharp-book check judges freshness by`() = runBlocking<Unit> {
        val b = books()
        val game = "Pittsburgh Steelers @ Cleveland Browns"
        val ml = b.view(bet("ml", game, "2026-10-02T00:15:00Z", "Moneyline", "Cleveland Browns"))!!.prices.associateBy { it.code }
        assertEquals(Instant.parse("2026-09-30T04:07:34Z").toEpochMilli(), ml.getValue("PN").atMs)
        assertEquals(Instant.parse("2026-09-30T04:07:43Z").toEpochMilli(), ml.getValue("FD").atMs)
        val allen = b.view(bet("p", "New England Patriots @ Buffalo Bills", "2026-10-04T17:00:00Z", "Passing Attempts", "Josh Allen Under 29.5"))!!.prices.associateBy { it.code }
        assertEquals(Instant.parse("2026-09-30T04:06:27Z").toEpochMilli(), allen.getValue("CZR").atMs)
        assertEquals(Instant.parse("2026-09-30T04:00:35Z").toEpochMilli(), allen.getValue("DK").atMs)
    }
}
