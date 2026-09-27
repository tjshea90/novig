package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PropLine (RESEARCH.md §22): thirty books' game lines per league and props per game, in The Odds
 * API's format with American prices. Fixtures follow PropLine's published schema (openapi.json,
 * 2026-09-27); no live board could be read (the shared demo key was at its daily cap).
 */
class PropLineClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = HashMap<String, () -> MockResponse>()
    private val hour = 3_600_000L
    private val now = 1_790_500_000_000L
    private val nfl = Leagues.byNovigName("NFL")!!

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.requestUrl!!.encodedPath]?.invoke() ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private var clockMs = now
    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })

    private fun client(keys: List<String> = listOf("pl-key")) = PropLineClient(
        OkHttpClient(), KeyPool(QuotaPolicy.PROPLINE, { keys }, meter), json,
        server.url("/v1").toString().trimEnd('/'), clock = { clockMs }, minIntervalMs = 0,
    )

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()
    private val start = now + 5 * hour
    private val upd = iso(now - 20_000)

    private fun ok(body: String) = MockResponse().setBody(body)
        .setHeader("X-Daily-Limit", "1000").setHeader("X-Daily-Used", "42").setHeader("X-Daily-Remaining", "958")

    /** One NFL game from four books, with everything the parser must throw away. */
    private val board = """
    [{"id":"555","sport_key":"football_nfl","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens",
      "commence_time":"${iso(start)}","live":false,"is_outright":false,"last_update":"$upd",
      "bookmakers":[
        {"key":"pinnacle","title":"Pinnacle","last_update":"$upd","markets":[
          {"key":"h2h","last_update":"$upd","outcomes":[
            {"name":"Dallas Cowboys","description":"","price":-150,"point":null,"side":"home","last_seen_at":"$upd"},
            {"name":"Baltimore Ravens","description":"","price":130,"point":null,"side":"away","last_seen_at":"$upd"}]},
          {"key":"spreads","last_update":"$upd","outcomes":[
            {"name":"Dallas Cowboys","description":"","price":-110,"point":-3.5,"side":"home"},
            {"name":"Baltimore Ravens","description":"","price":-110,"point":3.5,"side":"away"}]},
          {"key":"totals","last_update":"$upd","team":null,"outcomes":[
            {"name":"Over","description":"","price":-105,"point":47.5},
            {"name":"Under","description":"","price":-115,"point":47.5}]},
          {"key":"totals","description":"Team Total - Dallas Cowboys","last_update":"$upd","team":"Dallas Cowboys","outcomes":[
            {"name":"Over","description":"Dallas Cowboys","price":-110,"point":24.5},
            {"name":"Under","description":"Dallas Cowboys","price":-110,"point":24.5}]}]},
        {"key":"hardrock","title":"Hard Rock Bet","last_update":"$upd","markets":[
          {"key":"h2h","last_update":"$upd","outcomes":[
            {"name":"Dallas Cowboys","description":"","price":-160,"point":null,"side":"home"},
            {"name":"Baltimore Ravens","description":"","price":135,"point":null,"side":"away"}]},
          {"key":"spreads","last_update":"$upd","suspended_at":"$upd","outcomes":[
            {"name":"Dallas Cowboys","description":"","price":-110,"point":-3.5,"side":"home"},
            {"name":"Baltimore Ravens","description":"","price":-110,"point":3.5,"side":"away"}]}]},
        {"key":"draftkings","title":"DraftKings","last_update":"$upd","markets":[
          {"key":"totals","last_update":"$upd","team":null,"outcomes":[
            {"name":"Over","description":"","price":-110,"point":47.5,"last_seen_at":"$upd"},
            {"name":"Under","description":"","price":-110,"point":47.5,"last_seen_at":"${iso(now - 300_000)}"}]}]},
        {"key":"novig","title":"Novig","last_update":"$upd","markets":[
          {"key":"h2h","last_update":"$upd","outcomes":[
            {"name":"Dallas Cowboys","description":"","price":-140,"point":null,"side":"home"},
            {"name":"Baltimore Ravens","description":"","price":140,"point":null,"side":"away"}]}]}
      ]},
     {"id":"777","sport_key":"football_nfl","home_team":"NFC North Winner","away_team":"","commence_time":"${iso(start)}","is_outright":true,"bookmakers":[]}]
    """.trimIndent()

    @Test
    fun `a league's board parses every book's lines and drops pulled, withdrawn and non-book prices`() = runTest {
        routes["/v1/sports/americanfootball_nfl/odds"] = { ok(board) }
        val settings = ScanSettings(referenceBooks = listOf("pinnacle", "hardrockbet", "draftkings", "williamhill_us"))
        val snap = client().odds(nfl, settings)

        val r = requests.single()
        assertEquals("pl-key", r.getHeader("X-API-Key"))
        assertEquals("h2h,spreads,totals", r.requestUrl!!.queryParameter("markets"))
        // Caesars has no PropLine feed; Hard Rock goes by PropLine's own name. Novig rides along
        // (RESEARCH.md §23.6), never as a book.
        assertEquals("pinnacle,hardrock,draftkings,novig", r.requestUrl!!.queryParameter("bookmakers"))

        val e = snap.events.single()
        assertEquals("pl:555", e.id)
        assertEquals(start, e.commenceMs)
        val pin = e.markets.filter { it.bookKey == "pinnacle" }
        val ml = pin.single { it.kind == LineKind.MONEYLINE }
        assertEquals(1.0 + 100.0 / 150.0, ml.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
        assertEquals(2.3, ml.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-9)
        assertEquals(-3.5, pin.single { it.kind == LineKind.SPREAD }.line!!, 0.0)
        assertEquals(47.5, pin.single { it.kind == LineKind.TOTAL }.line!!, 0.0)
        val tt = pin.single { it.kind == LineKind.TEAM_TOTAL }
        assertEquals(RefBookMarket.HOME, tt.subject)
        assertEquals(24.5, tt.line!!, 0.0)
        // Hard Rock's moneyline under The Odds API's name; its pulled spread is gone.
        val hr = e.markets.filter { it.bookKey == "hardrockbet" }
        assertEquals(listOf(LineKind.MONEYLINE), hr.map { it.kind })
        assertEquals("Hard Rock Bet", hr.single().bookTitle)
        // DraftKings stopped sending the Under: the total can't be devigged, so it's dropped.
        assertTrue(e.markets.none { it.bookKey == "draftkings" })
        // Novig is what's being priced, never a reference: its prices come apart, to order Novig reads.
        assertTrue(e.markets.none { it.bookKey == "novig" })
        val novig = snap.novig.single()
        assertEquals("pl:555", novig.id)
        val nml = novig.markets.single()
        assertEquals("novig", nml.bookKey)
        assertEquals(1.0 + 100.0 / 140.0, nml.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
    }

    /**
     * RESEARCH.md §24: a price's time is when PropLine last saw it at the book (`last_seen_at`), so a
     * price the book stopped sending minutes ago can't pass for current behind a fresh market time.
     */
    @Test
    fun `each price carries when PropLine last saw it at the book`() {
        val longAgo = iso(now - 8 * 60_000)
        val raw = board.replace(""""side":"home","last_seen_at":"$upd"""", """"side":"home","last_seen_at":"$longAgo"""")
        val e = PropLineClient.parseEvents(raw, json, "americanfootball_nfl").single()
        assertEquals(now - 8 * 60_000, e.markets.single { it.bookKey == "pinnacle" && it.kind == LineKind.MONEYLINE }.lastUpdateMs)
        // Sent without last_seen_at: the market's own time.
        assertEquals(now - 20_000, e.markets.single { it.bookKey == "pinnacle" && it.kind == LineKind.SPREAD }.lastUpdateMs)
    }

    @Test
    fun `every reply's daily figures feed the usage meter`() = runTest {
        routes["/v1/sports/americanfootball_nfl/odds"] = { ok(board) }
        client().odds(nfl, ScanSettings())
        val u = meter.flow.value.providers.getValue(QuotaPolicy.PROPLINE.id).keys.getValue("pl-key")
        assertEquals(42, u.used)
        assertEquals(958, u.remaining)
        assertEquals(1000, u.limit)
    }

    @Test
    fun `a key at its daily cap rests until PropLine's reset and the next key answers`() = runTest {
        var calls = 0
        routes["/v1/sports/americanfootball_nfl/odds"] = {
            if (calls++ == 0) {
                MockResponse().setResponseCode(429).setHeader("Retry-After", "3600")
                    .setBody("""{"detail":{"error":"daily_limit_exceeded","message":"Daily limit of 1,000 requests exceeded."}}""")
            } else {
                ok(board)
            }
        }
        val snap = client(listOf("k1", "k2")).odds(nfl, ScanSettings())
        assertEquals(listOf("k1", "k2"), requests.map { it.getHeader("X-API-Key") })
        assertEquals(1, snap.events.size)
        val k1 = meter.flow.value.providers.getValue(QuotaPolicy.PROPLINE.id).keys.getValue("k1")
        assertEquals(now + 3_600_000L, k1.depletedUntil)
    }

    @Test
    fun `nothing is asked when no picked book is on PropLine`() = runTest {
        val snap = client().odds(nfl, ScanSettings(referenceBooks = listOf("williamhill_us", "espnbet")))
        assertTrue(snap.events.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `American prices convert to decimal`() {
        assertEquals(2.5, PropLineClient.decimal(150.0)!!, 1e-9)
        assertEquals(1.5, PropLineClient.decimal(-200.0)!!, 1e-9)
        assertEquals(2.0, PropLineClient.decimal(100.0)!!, 1e-9)
        assertNull(PropLineClient.decimal(0.0))
        assertNull(PropLineClient.decimal(null))
    }

    // ---- props ----------------------------------------------------------------------------------

    private val game = NovigEvent("nA", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", start)

    private fun prop(id: String, type: String, player: String, line: Double) =
        NovigMarket(id, "nA", type, "OPEN", "$player $line $type", start, MarketFee.GAME,
            listOf(NovigOutcome("$id-o", "Over $line", "TBD"), NovigOutcome("$id-u", "Under $line", "TBD")))

    private val context = ScanContext(
        novigEvents = listOf(game),
        novigMarkets = listOf(prop("m1", "PASSING_YARDS", "Lamar Jackson", 225.5), prop("m2", "FIELD_GOALS_MADE", "Justin Tucker", 1.5)),
        now = now,
    )

    private val props = """
    {"id":"555","sport_key":"football_nfl","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens","commence_time":"${iso(start)}",
     "bookmakers":[{"key":"fanduel","title":"FanDuel","last_update":"$upd","markets":[
       {"key":"player_pass_yds","last_update":"$upd","outcomes":[
         {"name":"Over","description":"Lamar Jackson","price":-115,"point":225.5},
         {"name":"Under","description":"Lamar Jackson","price":-105,"point":225.5},
         {"name":"250+ Passing Yards","description":"Lamar Jackson","price":180,"point":null}]},
       {"key":"player_field_goals_made","last_update":"$upd","outcomes":[
         {"name":"Over","description":"Justin Tucker","price":120,"point":1.5}]}]}]}
    """.trimIndent()

    /**
     * PropLine's game list as it really answers (Tj's phone, 2026-09-27 ~14:45Z, v0.16.0 showed
     * "Expected start of the array '[' but had 'n' … at path: $[0].bookmakers"): `/events` sends
     * `"bookmakers": null` and other nulls, which its schema allows.
     */
    private val realEvents = """
    [{"id":"555","sport_key":"football_nfl","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens",
      "home_team_key":"cowboys","away_team_key":"ravens","home_team_id":"espn.nfl:6","away_team_id":null,
      "home_team_logo_url":null,"away_team_logo_url":null,"commence_time":"${iso(start)}","live":false,"completed":false,
      "is_outright":false,"tournament":null,"tour":null,"espn_event_id":null,"mlb_game_pk":null,
      "merged_from_event_ids":null,"bookmakers":null},
     {"home_team_key":"colts","away_team_key":"texans","id":"556","sport_key":"football_nfl","home_team":"Indianapolis Colts",
      "away_team":"Houston Texans","commence_time":"${iso(start)}","merged_from_event_ids":null,"bookmakers":null}]
    """.trimIndent()

    @Test
    fun `PropLine's real game list, with null bookmakers, still names every game`() = runTest {
        routes["/v1/sports/americanfootball_nfl/events"] = { ok(realEvents) }
        val games = client().events("americanfootball_nfl")
        assertEquals(listOf("pl:555", "pl:556"), games.map { it.id })
        assertEquals("Houston Texans", games[1].away)
        assertTrue(games.all { it.markets.isEmpty() })
    }

    @Test
    fun `props are bought from the real game list's ids`() = runTest {
        routes["/v1/sports/americanfootball_nfl/events"] = { ok(realEvents) }
        routes["/v1/sports/americanfootball_nfl/events/555/odds"] = { ok(props) }
        val snap = PropLinePropsSource(client()).odds(nfl, ScanSettings(referenceBooks = listOf("fanduel")), context)
        assertEquals("PASSING_YARDS", snap.events.single().markets.single().stat)
    }

    @Test
    fun `nulls anywhere in a board are read as nothing, and one bad game never sinks the rest`() {
        val raw = """
        [{"id":"1","home_team":"Dallas Cowboys","away_team":"Baltimore Ravens","commence_time":"${iso(start)}",
          "bookmakers":[{"key":"pinnacle","title":null,"last_update":null,"markets":null},
                        {"key":"fanduel","title":"FanDuel","markets":[{"key":"h2h","last_update":null,"outcomes":null},
                          {"key":"h2h","description":null,"last_update":"$upd","outcomes":[
                            {"name":"Dallas Cowboys","description":null,"price":-150,"point":null,"side":"home"},
                            {"name":"Baltimore Ravens","description":null,"price":130,"point":null,"side":"away"}]}]}]},
         {"id":"2","home_team":null,"away_team":"X","commence_time":"${iso(start)}"},
         {"id":3,"home_team":"A","away_team":"B","commence_time":"not a time"},
         "junk"]
        """.trimIndent()
        val events = PropLineClient.parseEvents(raw, json, "americanfootball_nfl")
        val e = events.single()
        assertEquals("FanDuel", e.markets.single().bookTitle)
        assertEquals(LineKind.MONEYLINE, e.markets.single().kind)
    }

    @Test
    fun `props are bought per game for the stats Novig lists, and re-used for two minutes`() = runTest {
        routes["/v1/sports/americanfootball_nfl/events"] = { ok(board) }
        routes["/v1/sports/americanfootball_nfl/events/555/odds"] = { ok(props) }
        val source = PropLinePropsSource(client())
        val settings = ScanSettings(referenceBooks = listOf("fanduel", "draftkings"))
        val snap = source.odds(nfl, settings, context)

        val ask = requests.last()
        assertEquals("/v1/sports/americanfootball_nfl/events/555/odds", ask.requestUrl!!.encodedPath)
        assertEquals(setOf("player_pass_yds", "player_field_goals_made"), ask.requestUrl!!.queryParameter("markets")!!.split(",").toSet())
        assertEquals("fanduel,draftkings,novig", ask.requestUrl!!.queryParameter("bookmakers"))

        val e = snap.events.single()
        val m = e.markets.single()
        assertEquals(LineKind.PLAYER_PROP, m.kind)
        assertEquals("PASSING_YARDS", m.stat)
        assertEquals("Lamar Jackson", m.subject)
        assertEquals(225.5, m.line!!, 0.0)
        // The one-sided field-goal line and the milestone rung can't be devigged.
        assertEquals(1, e.markets.size)

        val before = requests.size
        clockMs = now + 60_000L
        source.odds(nfl, settings, context.copy(now = clockMs))
        assertEquals(before, requests.size)
        // Never older than a couple of minutes (RESEARCH.md §24): bought again three minutes on.
        clockMs = now + 3 * 60_000L
        source.odds(nfl, settings, context.copy(now = clockMs))
        assertTrue(requests.size > before)
    }

    @Test
    fun `no props are bought when player props aren't priced`() = runTest {
        val source = PropLinePropsSource(client())
        val snap = source.odds(nfl, ScanSettings(families = setOf(MarketFamily.MONEYLINE)), context)
        assertTrue(snap.events.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `the game list comes from the last board when it's recent`() = runTest {
        routes["/v1/sports/americanfootball_nfl/odds"] = { ok(board) }
        routes["/v1/sports/americanfootball_nfl/events/555/odds"] = { ok(props) }
        val c = client()
        c.odds(nfl, ScanSettings())
        PropLinePropsSource(c).odds(nfl, ScanSettings(), context)
        assertTrue(requests.none { it.requestUrl!!.encodedPath.endsWith("/events") })
    }

    @Test
    fun `an unreadable reply shows a short plain message, not the JSON`() {
        val e = runCatching { Json.decodeFromString(PlEvent.serializer(), "[1,2]") }.exceptionOrNull()!!
        assertEquals("sent a reply Vigilant couldn't read", readableError(e))
        val long = IllegalStateException("x".repeat(500))
        assertEquals(160, readableError(long).length)
    }
}
