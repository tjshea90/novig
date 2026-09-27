package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.runBlocking
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

/**
 * A CNO bet found in Novig's own catalog when CNO can't hand over its link (Tj, 2026-09-27). The
 * markets are real ones from Novig's public catalog (Seahawks @ Commanders, 2026-09-26).
 */
class NovigBetFinderTest {

    private val start = 1_790_528_400_000L
    private val event = NovigBetFinder.Event("E1", "Seattle Seahawks @ Washington Commanders", start)

    private val marketsJson = """{"items":[
      {"marketId":"m1","marketType":"RECEIVING_YARDS","strike":"3.5","description":"Elijah Arroyo 3.5 RECEIVING_YARDS","outcomes":[{"outcomeId":"arroyo-o35","name":"Over 3.5"},{"outcomeId":"arroyo-u35","name":"Under 3.5"}]},
      {"marketId":"m2","marketType":"RECEIVING_YARDS","strike":"4.5","description":"Elijah Arroyo 4.5 RECEIVING_YARDS","outcomes":[{"outcomeId":"arroyo-o45","name":"Over 4.5"},{"outcomeId":"arroyo-u45","name":"Under 4.5"}]},
      {"marketId":"m3","marketType":"RECEPTIONS","strike":"0.5","description":"Elijah Arroyo 0.5 RECEPTIONS","outcomes":[{"outcomeId":"arroyo-rec-o","name":"Over 0.5"},{"outcomeId":"arroyo-rec-u","name":"Under 0.5"}]},
      {"marketId":"m4","marketType":"RUSHING_AND_RECEIVING_YARDS","strike":"39.5","description":"Rachaad White 39.5 RUSHING_AND_RECEIVING_YARDS","outcomes":[{"outcomeId":"white-rr-o","name":"Over 39.5"},{"outcomeId":"white-rr-u","name":"Under 39.5"}]},
      {"marketId":"m5","marketType":"RECEIVING_YARDS","strike":"16.5","description":"Rachaad White 16.5 RECEIVING_YARDS","outcomes":[{"outcomeId":"white-rec-o","name":"Over 16.5"},{"outcomeId":"white-rec-u","name":"Under 16.5"}]},
      {"marketId":"m6","marketType":"MONEY","strike":"0","description":"WSH","outcomes":[{"outcomeId":"ml-wsh","name":"WSH"},{"outcomeId":"ml-sea","name":"SEA"}]},
      {"marketId":"m7","marketType":"SPREAD","strike":"27.5","description":"WSH +27.5","outcomes":[{"outcomeId":"sp-wsh","name":"WSH +27.5"},{"outcomeId":"sp-sea","name":"SEA -27.5"}]},
      {"marketId":"m8","marketType":"SPREAD_1H","strike":"4.5","description":"WSH +4.5 1H","outcomes":[{"outcomeId":"sp1h-wsh","name":"WSH +4.5"},{"outcomeId":"sp1h-sea","name":"SEA -4.5"}]},
      {"marketId":"m9","marketType":"TOTAL","strike":"32.5","description":"SEA @ WSH t32.5","outcomes":[{"outcomeId":"tot-o","name":"Over 32.5"},{"outcomeId":"tot-u","name":"Under 32.5"}]},
      {"marketId":"m10","marketType":"TEAM_TOTAL","strike":"15.5","description":"Washington Commanders 15.5 TEAM_TOTAL","outcomes":[{"outcomeId":"tt-o","name":"Over 15.5"},{"outcomeId":"tt-u","name":"Under 15.5"}]}
    ]}"""
    private val markets = NovigBetFinder.parseMarkets(Json.parseToJsonElement(marketsJson))

    private fun row(bet: String, market: String) =
        CnoRow(0.03, start, "Football", "NFL", "Seattle Seahawks @ Washington Commanders", market, bet, 110, book = "Novig")

    private fun find(bet: String, market: String) = NovigBetFinder.matchOutcome(row(bet, market), event, markets)?.id

    @Test
    fun `player props land on the exact player, line and side`() {
        assertEquals("arroyo-u45", find("Elijah Arroyo Under 4.5", "Player Receiving Yards"))
        assertEquals("arroyo-o35", find("Elijah Arroyo Over 3.5", "Player Receiving Yards"))
        assertEquals("arroyo-rec-o", find("Elijah Arroyo Over 0.5", "Player Receptions"))
        assertEquals("white-rr-u", find("Rachaad White Under 39.5", "Player Rushing + Receiving Yards"))
        assertEquals("white-rec-o", find("Rachaad White Over 16.5", "Player Receiving Yards"))
    }

    @Test
    fun `game lines - total, team total, spread (full game and half) and moneyline`() {
        assertEquals("tot-u", find("Under 32.5", "Total Points"))
        assertEquals("tt-o", find("Washington Commanders Over 15.5", "Team Total Points"))
        assertEquals("sp-wsh", find("Washington Commanders +27.5", "Point Spread"))
        assertEquals("sp-sea", find("Seattle Seahawks -27.5", "Point Spread"))
        assertEquals("sp1h-sea", find("Seattle Seahawks -4.5", "1st Half Point Spread"))
        assertEquals("ml-wsh", find("Washington Commanders", "Moneyline"))
        assertEquals("ml-sea", find("Seattle Seahawks", "Moneyline"))
    }

    @Test
    fun `nothing short of one exact match - a missing line, another market, the wrong team`() {
        assertNull(find("Elijah Arroyo Over 5.5", "Player Receiving Yards")) // no such line
        assertNull(find("Rachaad White Over 39.5", "Player Receiving Yards")) // that line is rush+rec
        assertNull(find("Rachaad White Over 16.5", "Player Rushing + Receiving Yards")) // and that one receiving only
        assertNull(find("Someone Else Over 3.5", "Player Receiving Yards"))
        assertNull(find("Washington Commanders +26.5", "Point Spread"))
        assertNull(find("Seattle Seahawks +27.5", "Point Spread")) // Seattle is -27.5
        assertNull(find("Elijah Arroyo Over 3.5", "Player Longest Reception"))
    }

    @Test
    fun `market names match Novig's types word for word, halves only halves`() {
        val w = NovigBetFinder::marketWords
        assertTrue(NovigBetFinder.typeFits("RECEIVING_YARDS", w("Player Receiving Yards")))
        assertTrue(NovigBetFinder.typeFits("PITCHER_STRIKEOUTS", w("Player Pitching Strikeouts")))
        assertTrue(NovigBetFinder.typeFits("TACKLES_ASSISTS", w("Player Tackles + Assists")))
        assertTrue(NovigBetFinder.typeFits("SPREAD", w("Point Spread")))
        assertTrue(NovigBetFinder.typeFits("SPREAD_1H", w("1st Half Point Spread")))
        assertFalse(NovigBetFinder.typeFits("SPREAD", w("1st Half Point Spread")))
        assertFalse(NovigBetFinder.typeFits("SPREAD_1H", w("Point Spread")))
        assertFalse(NovigBetFinder.typeFits("RUSHING_YARDS", w("Player Rushing + Receiving Yards")))
        assertFalse(NovigBetFinder.typeFits("PASSING_YARDS", w("Player Passing + Rushing Yards")))
        assertTrue(NovigBetFinder.typeFits("PASSING_AND_RUSHING_YARDS", w("Player Passing + Rushing Yards")))
    }

    @Test
    fun `the game is found by its teams near CNO's start time`() {
        val events = listOf(
            NovigBetFinder.Event("E0", "Seattle Seahawks @ Washington Commanders", start - 7 * 86_400_000L), // last week's
            event,
            NovigBetFinder.Event("E2", "Kansas City Chiefs @ Miami Dolphins", start),
        )
        assertEquals("E1", NovigBetFinder.matchEvent(row("x", "y"), events)?.id)
        assertNull(NovigBetFinder.matchEvent(row("x", "y").copy(event = "Denver Broncos @ Las Vegas Raiders"), events))
        assertEquals("NFL", NovigBetFinder.novigLeague("NFL"))
        assertEquals("MLS", NovigBetFinder.novigLeague("MLS (USA)"))
    }

    /** Real shapes from Novig's catalog (2026-09-27), for markets v0.15.2 only found the game of. */
    @Test
    fun `passing interceptions and soccer's 3-way moneyline land on the exact outcome`() {
        val nfl = NovigBetFinder.Event("E2", "New England Patriots @ Jacksonville Jaguars", start)
        val nflMarkets = NovigBetFinder.parseMarkets(Json.parseToJsonElement("""{"items":[
          {"marketId":"i1","marketType":"INTERCEPTIONS_THROWN","strike":"0.5","description":"Drake Maye 0.5 INTERCEPTIONS_THROWN","outcomes":[{"outcomeId":"maye-int-o","name":"Over 0.5"},{"outcomeId":"maye-int-u","name":"Under 0.5"}]},
          {"marketId":"i2","marketType":"INTERCEPTIONS_THROWN","strike":"0.5","description":"Trevor Lawrence 0.5 INTERCEPTIONS_THROWN","outcomes":[{"outcomeId":"tl-int-o","name":"Over 0.5"},{"outcomeId":"tl-int-u","name":"Under 0.5"}]},
          {"marketId":"i3","marketType":"PASSING_TOUCHDOWNS","strike":"0.5","description":"Drake Maye 0.5 PASSING_TOUCHDOWNS","outcomes":[{"outcomeId":"maye-td-o","name":"Over 0.5"},{"outcomeId":"maye-td-u","name":"Under 0.5"}]}
        ]}"""))
        fun nflRow(bet: String, market: String) = CnoRow(0.03, start, "Football", "NFL", nfl.description, market, bet, 110, book = "Novig")
        assertEquals("maye-int-o", NovigBetFinder.matchOutcome(nflRow("Drake Maye Over 0.5", "Player Passing Interceptions"), nfl, nflMarkets)?.id)
        assertEquals("tl-int-u", NovigBetFinder.matchOutcome(nflRow("Trevor Lawrence Under 0.5", "Player Passing Interceptions"), nfl, nflMarkets)?.id)

        val mls = NovigBetFinder.Event("E3", "New England Revolution @ Real Salt Lake", start)
        val mlsMarkets = NovigBetFinder.parseMarkets(Json.parseToJsonElement("""{"items":[
          {"marketId":"w1","marketType":"MONEYLINE_3_WAY_WIN","strike":"0","description":"New England MONEYLINE_3_WAY_WIN","outcomes":[{"outcomeId":"ne-yes","name":"Yes"},{"outcomeId":"ne-no","name":"No"}]},
          {"marketId":"d1","marketType":"MONEYLINE_3_WAY_DRAW","strike":"0","description":"New England @ Real Salt Lake MONEYLINE_3_WAY_DRAW","outcomes":[{"outcomeId":"draw-yes","name":"Yes"},{"outcomeId":"draw-no","name":"No"}]},
          {"marketId":"w2","marketType":"MONEYLINE_3_WAY_WIN","strike":"0","description":"Real Salt Lake MONEYLINE_3_WAY_WIN","outcomes":[{"outcomeId":"rsl-yes","name":"Yes"},{"outcomeId":"rsl-no","name":"No"}]},
          {"marketId":"m1","marketType":"MONEY","strike":"0","description":"RSL","outcomes":[{"outcomeId":"ml-rsl","name":"RSL"},{"outcomeId":"ml-ne","name":"NE"}]}
        ]}"""))
        fun mlsRow(bet: String) = CnoRow(0.03, start, "Soccer", "MLS (USA)", mls.description, "Moneyline 3-way", bet, 110, book = "Novig")
        assertEquals("rsl-no", NovigBetFinder.matchOutcome(mlsRow("Real Salt Lake No"), mls, mlsMarkets)?.id)
        assertEquals("rsl-yes", NovigBetFinder.matchOutcome(mlsRow("Real Salt Lake Yes"), mls, mlsMarkets)?.id)
        assertEquals("ne-no", NovigBetFinder.matchOutcome(mlsRow("New England Revolution No"), mls, mlsMarkets)?.id)
        assertEquals("draw-yes", NovigBetFinder.matchOutcome(mlsRow("Draw Yes"), mls, mlsMarkets)?.id)
        // A 3-way bet never lands on the 2-way moneyline, and a team that isn't playing finds nothing.
        assertNull(NovigBetFinder.matchOutcome(mlsRow("Real Salt Lake"), mls, mlsMarkets))
        assertNull(NovigBetFinder.matchOutcome(mlsRow("Seattle Sounders No"), mls, mlsMarkets))
    }

    // ---- Over the network -----------------------------------------------------------------------

    private val server = MockWebServer()
    private var down = false
    private var busyOnce = false

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (down) return MockResponse().setResponseCode(503)
                val path = request.path.orEmpty()
                if (busyOnce && path.startsWith("/v3/public/catalog/markets")) {
                    busyOnce = false
                    return MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
                }
                return when {
                    path.startsWith("/v3/public/catalog/events?league=NFL") -> MockResponse().setBody(
                        """{"items":[{"eventId":"E1","description":"Seattle Seahawks @ Washington Commanders","sport":"FOOTBALL","league":"NFL","status":"OPEN_PREGAME","startsTs":$start}]}""",
                    )
                    path.startsWith("/v3/public/catalog/events?status=") && "SETTLED" in path && "FINAL" in path && "startsAfter=" in path -> MockResponse().setBody(
                        """{"items":[{"eventId":"E1","description":"Seattle Seahawks @ Washington Commanders","sport":"FOOTBALL","league":"NFL","status":"FINAL","startsTs":$start}]}""",
                    )
                    path.startsWith("/v3/public/catalog/markets?event=E1") -> MockResponse().setBody(marketsJson)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun finder() = NovigBetFinder(OkHttpClient(), baseUrl = server.url("/").toString().removeSuffix("/"))

    @Test
    fun `a tap finds the bet slip link in two reads, and the next bet of that game costs none`() = runBlocking {
        val f = finder()
        assertEquals("novigapp://events/arroyo-u45", f.find(row("Elijah Arroyo Under 4.5", "Player Receiving Yards"))?.link)
        assertEquals(2, f.requests)
        assertEquals("novigapp://events/tot-o", f.find(row("Over 32.5", "Total Points"))?.link)
        assertEquals(2, f.requests)
    }

    @Test
    fun `a bet Novig doesn't list opens its game instead, and Novig down means no link at all`() = runBlocking {
        val f = finder()
        assertEquals("novigapp://event-markets/E1", f.find(row("Elijah Arroyo Over 9.5", "Player Receiving Yards"))?.link)
        down = true
        assertNull(finder().find(row("Elijah Arroyo Under 4.5", "Player Receiving Yards")))
    }

    @Test
    fun `Novig busy - "couldn't look" (asked again later), not "only the game", and its pause is kept`() = runBlocking {
        val f = finder()
        busyOnce = true
        val first = f.find(row("Elijah Arroyo Under 4.5", "Player Receiving Yards"))
        assertEquals(NovigBetFinder.Found.Game("E1", searched = false), first)
        val t0 = System.nanoTime()
        assertEquals("novigapp://events/arroyo-u45", f.find(row("Elijah Arroyo Under 4.5", "Player Receiving Yards"))?.link)
        assertTrue("waited out Retry-After", (System.nanoTime() - t0) / 1_000_000 >= 900)
    }

    @Test
    fun `an exact find carries its market as read, fee included, for pricing it from Novig's book`() {
        val withFee = NovigBetFinder.parseMarkets(Json.parseToJsonElement("""{"items":[
          {"marketId":"m1","eventId":"E1","marketType":"RECEIVING_YARDS","status":"OPEN","strike":"4.5","description":"Elijah Arroyo 4.5 RECEIVING_YARDS",
           "fee":{"coefficient":"0.03","makerCredit":"0.5","charged":"WHEN_LIVE"},
           "outcomes":[{"outcomeId":"arroyo-o45","name":"Over 4.5","status":"TBD"},{"outcomeId":"arroyo-u45","name":"Under 4.5","status":"TBD"}]}
        ]}"""))
        val m = withFee.single().novig!!
        assertEquals(com.tjshea.vigilant.engine.MarketFee.GAME, m.fee)
        assertEquals("arroyo-o45", m.otherOutcome("arroyo-u45")?.outcomeId)
        // A market whose fee can't be read has none: it's never priced as if it were free.
        assertEquals(null, markets.first().novig?.fee)
    }
}
