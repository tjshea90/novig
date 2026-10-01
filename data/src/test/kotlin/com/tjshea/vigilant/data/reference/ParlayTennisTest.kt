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
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Tennis through ParlayAPI (Tj, 2026-10-01: "Build tennis through parlayapi"). The answers are ParlayAPI's own for `tennis_atp` and
 * `tennis_wta`, as Tj's key got them that day (PARLAY_API.md §6.11), trimmed to a few matches: Pinnacle's match event holds SET lines and
 * its "(Games)" twin the games lines, every other book's match event holds games lines (BetMGM at ±1.5 games), FanDuel and ProphetX list
 * some matches at start times of their own, and doubles ride along.
 */
class ParlayTennisTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val atp = Leagues.byNovigName("ATP")!!
    private val wta = Leagues.byNovigName("WTA")!!
    private val now = Instant.parse("2026-10-01T01:40:00Z").toEpochMilli()
    private val day = 24 * 3_600_000L
    private val sets = RefBookMarket.PERIOD_SETS

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    /** The fixture as the client makes it, every quote stamped a minute old (the real stamps are the day's). */
    private fun normalized(name: String) = ParlayTennis.normalize(TheOddsApiClient.parseEvents(res(name), json), day)
        .map { e -> e.copy(markets = e.markets.map { it.copy(lastUpdateMs = now - 60_000) }) }

    private fun RefEvent.lines(book: String, kind: LineKind, period: Int) = markets.filter { it.bookKey == book && it.kind == kind && it.period == period }

    private fun event(events: List<RefEvent>, player: String) = events.single { it.home == player || it.away == player }

    @Test
    fun `one event per singles match, doubles dropped, the Games twin and other books' listings merged in`() {
        val events = normalized("parlay-tennis-atp.json")
        assertTrue(events.none { '/' in it.home || '/' in it.away })
        assertTrue(events.none { it.home.contains("(Games)") || it.away.contains("(Games)") })
        // 13 listings: De Minaur (Games only), Molcan (+Games), Nakashima (+Games), Alcaraz, a doubles pair naming Nakashima,
        // Shimizu (ProphetX 04:10, Pinnacle 05:00, Games), Walton (Pinnacle 03:05, Games, FanDuel 02:00).
        assertEquals(6, events.size)

        val walton = event(events, "Adam Walton")
        // Pinnacle's match event leads: its start, not FanDuel's hour-earlier one.
        assertEquals(Instant.parse("2026-10-01T03:05:00Z").toEpochMilli(), walton.commenceMs)
        assertEquals(setOf("pinnacle", "fanduel"), walton.markets.map { it.bookKey }.toSet())
        assertEquals(2, walton.lines("pinnacle", LineKind.MONEYLINE, 0).size + walton.lines("fanduel", LineKind.MONEYLINE, 0).size)
        // Walton +1.5 sets at 1.413 (match event), +0.5 games at 1.901 (the Games twin).
        assertEquals(1.5, walton.lines("pinnacle", LineKind.SPREAD, sets).single().line!!, 0.0)
        assertEquals(1.413, walton.lines("pinnacle", LineKind.SPREAD, sets).single().quotes.single { it.side == Side.HOME }.decimalOdds, 0.0)
        assertEquals(0.5, walton.lines("pinnacle", LineKind.SPREAD, 0).single().line!!, 0.0)
        assertEquals(2.5, walton.lines("pinnacle", LineKind.TOTAL, sets).single().line!!, 0.0)
        assertEquals(22.5, walton.lines("pinnacle", LineKind.TOTAL, 0).single().line!!, 0.0)

        val shimizu = event(events, "Yuta Shimizu")
        assertEquals(setOf("pinnacle", "prophetx"), shimizu.markets.map { it.bookKey }.toSet())
        // ProphetX's own listing is games: +2.5 and 23.5.
        assertEquals(2.5, shimizu.lines("prophetx", LineKind.SPREAD, 0).single().line!!, 0.0)
        assertTrue(shimizu.lines("prophetx", LineKind.SPREAD, sets).isEmpty())

        // A Games event with no match event beside it stands alone: games lines only, its winner nowhere.
        val deMinaur = event(events, "Alex De Minaur")
        assertEquals("Mariano Navone", deMinaur.away)
        assertEquals(setOf(LineKind.SPREAD, LineKind.TOTAL), deMinaur.markets.map { it.kind }.toSet())
        assertTrue(deMinaur.markets.all { it.period == 0 })

        // Nakashima's singles match keeps its own books; the doubles pair he's in isn't merged into it.
        val nakashima = event(events, "Brandon Nakashima")
        assertEquals("Ugo Humbert", nakashima.away)
        assertEquals(1, nakashima.lines("pinnacle", LineKind.MONEYLINE, 0).size)
    }

    @Test
    fun `pinnacle's set lines are sets and every other book's are games, though both say plus 1 and a half`() {
        val molcan = event(normalized("parlay-tennis-atp.json"), "Alex Molcan")
        // The trap: Pinnacle's Molcan +1.5 is SETS (1.559), BetMGM's Molcan +1.5 is GAMES (1.98).
        val pinSets = molcan.lines("pinnacle", LineKind.SPREAD, sets).single()
        assertEquals(1.5, pinSets.line!!, 0.0)
        assertEquals(1.559, pinSets.quotes.single { it.side == Side.HOME }.decimalOdds, 0.0)
        val mgm = molcan.lines("betmgm", LineKind.SPREAD, 0).single()
        assertEquals(1.5, mgm.line!!, 0.0)
        assertEquals(1.98, mgm.quotes.single { it.side == Side.HOME }.decimalOdds, 0.0)
        // Pinnacle's only games spread is the Games twin's +2.5; no other book has set lines.
        assertEquals(listOf(2.5), molcan.lines("pinnacle", LineKind.SPREAD, 0).map { it.line })
        assertEquals(listOf(23.0), molcan.lines("pinnacle", LineKind.TOTAL, 0).map { it.line })
        assertEquals(setOf("pinnacle"), molcan.markets.filter { it.period == sets }.map { it.bookKey }.toSet())
        // Every games total is a games number; every sets total is 2.5.
        assertTrue(molcan.markets.filter { it.kind == LineKind.TOTAL && it.period == 0 }.all { it.line!! > 20 })
        assertEquals(listOf(2.5), molcan.lines("pinnacle", LineKind.TOTAL, sets).map { it.line })

        // Pinnacle's alternates (Alcaraz) are set lines too, and one line is listed once per book.
        val alcaraz = event(normalized("parlay-tennis-atp.json"), "Carlos Alcaraz")
        val pinSpreads = alcaraz.lines("pinnacle", LineKind.SPREAD, sets)
        assertEquals(pinSpreads.map { it.line }.distinct().size, pinSpreads.size)
        assertTrue(alcaraz.lines("pinnacle", LineKind.SPREAD, 0).isEmpty())
    }

    @Test
    fun `a book whose total is a sets number has its spread read as sets, and a line that fits neither unit is dropped`() {
        fun mk(book: String, kind: LineKind, home: Double, away: Double, point: Double?) = RefBookMarket(
            book, book, kind,
            if (kind == LineKind.SPREAD) listOf(RefQuote(Side.HOME, home, point), RefQuote(Side.AWAY, away, point?.let { -it }))
            else listOf(RefQuote(Side.OVER, home, point), RefQuote(Side.UNDER, away, point)),
            now,
        )
        val e = RefEvent(
            "x", "tennis_atp", now + 3_600_000L, "A Player", "B Player",
            listOf(
                // A book that one day sends sets lines: its 2.5 total says so.
                mk("draftkings", LineKind.TOTAL, 2.4, 1.6, 2.5), mk("draftkings", LineKind.SPREAD, 1.6, 2.4, 1.5),
                // Pinnacle with a games total in the match event: then its spread is games too.
                mk("pinnacle", LineKind.TOTAL, 1.9, 1.9, 22.5), mk("pinnacle", LineKind.SPREAD, 1.9, 1.9, -3.5),
                // A "sets" spread of 4.5 can't be sets.
                mk("bet365", LineKind.TOTAL, 2.4, 1.6, 3.5), mk("bet365", LineKind.SPREAD, 1.9, 1.9, 4.5),
            ),
        )
        val out = ParlayTennis.unitsOf(e)
        assertEquals(setOf(sets), out.markets.filter { it.bookKey == "draftkings" }.map { it.period }.toSet())
        assertEquals(setOf(0), out.markets.filter { it.bookKey == "pinnacle" }.map { it.period }.toSet())
        assertEquals(listOf(LineKind.TOTAL), out.markets.filter { it.bookKey == "bet365" }.map { it.kind })
    }

    @Test
    fun `WTA listings hours apart merge, the doubles pair goes`() {
        val events = normalized("parlay-tennis-wta.json")
        assertEquals(2, events.size)
        val ruzic = event(events, "Antonia Ruzic")
        // FanDuel lists it at 12:00, Pinnacle at 09:00: one match, Pinnacle's start.
        assertEquals(Instant.parse("2026-10-02T09:00:00Z").toEpochMilli(), ruzic.commenceMs)
        assertEquals(setOf("pinnacle", "fanduel"), ruzic.markets.map { it.bookKey }.toSet())
        assertEquals(listOf(-0.5), ruzic.lines("pinnacle", LineKind.SPREAD, 0).map { it.line })
        val linette = event(events, "Magda Linette")
        assertEquals(listOf(3.5), linette.lines("pinnacle", LineKind.SPREAD, 0).map { it.line })
        assertEquals(listOf(1.5), linette.lines("pinnacle", LineKind.SPREAD, sets).map { it.line })
    }

    // ---- priced end to end -------------------------------------------------------------------------------

    private val molcanStart = Instant.parse("2026-10-01T03:05:00Z").toEpochMilli()
    private val match = NovigEvent("n1", "TENNIS", "ATP", NovigEvent.STATUS_PREGAME, "Arthur Rinderknech @ Alex Molcan", molcanStart)

    private fun market(id: String, type: String, description: String, vararg outcomes: Pair<String, String>) =
        NovigMarket(id, "n1", type, "OPEN", description, molcanStart, MarketFee.GAME, outcomes.map { NovigOutcome(it.first, it.second, "TBD") })

    // As Novig lists a tennis match (its public catalog, 2026-10-01).
    private val novig = listOf(
        market("ml", "MONEY", "A. Molcan", "ml-m" to "A. Molcan", "ml-r" to "A. Rinderknech"),
        market("sp", "SPREAD", "A. Molcan +1.5", "sp-m" to "A. Molcan +1.5", "sp-r" to "A. Rinderknech -1.5"),
        market("ss", "SET_SPREAD", "A. Molcan +1.5", "ss-m" to "A. Molcan +1.5", "ss-r" to "A. Rinderknech -1.5"),
        market("to", "TOTAL", "A. Rinderknech @ A. Molcan t22.5", "to-o" to "Over 22.5", "to-u" to "Under 22.5"),
        market("ts", "TOTAL_SETS", "A. Rinderknech @ A. Molcan t2.5", "ts-o" to "Over 2.5", "ts-u" to "Under 2.5"),
    )

    private val settings = ScanSettings(
        leagues = setOf("ATP"), fairSource = FairSource.BLEND, sharpBooks = setOf("pinnacle"), minBooks = 1,
        devigMethod = DevigMethod.MULTIPLICATIVE, minEvPercent = -1.0, maxEvPercent = 1.0,
    )

    private fun book(id: String, vararg bids: Pair<String, Int>) =
        id to NovigBook(id, 1, bids.associate { (o, p) -> o to listOf(BidLevel(p, 10_000)) }, now)

    private fun mult(a: Double, b: Double) = (1 / a) / (1 / a + 1 / b)

    @Test
    fun `Novig's set spread and total sets price from Pinnacle's set lines, its games lines from games lines only`() {
        val snap = RefSnapshot(atp.oddsApiSportKey, normalized("parlay-tennis-atp.json"), now, provider = "parlay")
        val plan = Planner.plan(listOf(match), novig, listOf(snap), settings, now)
        assertEquals(1, plan.matchedEvents)
        assertEquals(setOf("ml", "sp", "ss", "to", "ts"), plan.marketIds.toSet())
        val r = Pricing.price(
            plan,
            mapOf(
                book("ml", "ml-m" to 380, "ml-r" to 590), book("sp", "sp-m" to 480, "sp-r" to 480), book("ss", "ss-m" to 600, "ss-r" to 360),
                book("to", "to-o" to 480, "to-u" to 480), book("ts", "ts-o" to 380, "ts-u" to 580),
            ),
            settings, now,
        )
        fun opp(id: String) = r.opportunities.single { it.outcome.outcomeId == id }

        // Sets: Pinnacle alone (1.559 / 2.55 for Molcan +1.5 sets; 2.37 / 1.633 for over 2.5 sets).
        val setSpread = opp("ss-m")
        assertEquals("Set Spread", setSpread.marketLabel)
        assertEquals("Alex Molcan +1.5", setSpread.selection)
        assertEquals(mult(1.559, 2.55), setSpread.fairProbability!!, 1e-9)
        assertEquals(listOf("Pinnacle"), setSpread.fair!!.booksUsed)
        val totalSets = opp("ts-o")
        assertEquals("Total Sets", totalSets.marketLabel)
        assertEquals(mult(2.37, 1.633), totalSets.fairProbability!!, 1e-9)

        // Games +1.5: BetMGM's games line alone. Before this split, Pinnacle's +1.5 SETS (64% to cover) would have priced it: a fake edge.
        val games = opp("sp-m")
        assertEquals("Games Spread", games.marketLabel)
        assertEquals(mult(1.98, 1.714), games.fairProbability!!, 1e-9)
        assertFalse("Pinnacle" in games.fair!!.booksUsed)
        // Total games 22.5: the four books at 22.5 (Pinnacle's games total is 23, DraftKings' 23.5).
        assertEquals(setOf("bet365", "Caesars", "ProphetX", "BetMGM"), opp("to-o").fair!!.booksUsed.toSet())
        assertEquals("Total Games", opp("to-o").marketLabel)
        // The winner: Pinnacle (sharp) blended with every book's moneyline, FanDuel's included.
        assertTrue("FanDuel" in opp("ml-m").fair!!.booksUsed && "Pinnacle" in opp("ml-m").fair!!.sharpBooksUsed)
    }

    @Test
    fun `set lines come with the Spread and Total families, never The Odds API's`() {
        assertTrue("SET_SPREAD" in MarketFamily.SPREAD.novigTypes)
        assertTrue("TOTAL_SETS" in MarketFamily.TOTAL.novigTypes)
        val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))
        val oddsApi = TheOddsApiClient(OkHttpClient(), KeyPool(QuotaPolicy.ODDS_API, { listOf("test-key") }, meter), json)
        val parlay = TheOddsApiClient(OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter), json, feed = OddsFeed.PARLAY)
        for (l in listOf(atp, wta)) {
            assertFalse(oddsApi.supports(l))
            assertTrue(parlay.supports(l))
        }
    }

    @Test
    fun `a tennis scan asks for the tour's three game markets, no alternates, and gets the matches back merged`() = runBlocking<Unit> {
        val asked = ArrayList<okhttp3.HttpUrl>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    asked += request.requestUrl!!
                    return when (request.requestUrl!!.encodedPath) {
                        "/v1/sports/tennis_atp/odds" -> MockResponse().setBody(res("parlay-tennis-atp.json")).setHeader("x-requests-last", "3")
                        "/v1/sports/americanfootball_nfl/odds" -> MockResponse().setBody("[]").setHeader("x-requests-last", "0")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        try {
            val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })
            val client = TheOddsApiClient(
                OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter), json,
                baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
            )
            val s = ScanSettings(leagues = setOf("ATP", "NFL"))
            val snap = client.odds(atp, s)
            assertEquals("h2h,spreads,totals", asked.single().queryParameter("markets"))
            assertEquals(6, snap.events.size)
            assertNotNull(snap.events.singleOrNull { it.home == "Alex Molcan" })
            assertTrue(snap.events.none { it.home.contains("(Games)") })
            // Football still asks for the alternates.
            client.odds(Leagues.byNovigName("NFL")!!, s)
            assertTrue(asked.last().queryParameter("markets")!!.contains("alternate_spreads"))
        } finally {
            server.shutdown()
        }
    }
}
