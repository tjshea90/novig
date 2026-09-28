package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.PropLineProps
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tennis (Tj, 2026-09-28: "It may be missing many games and bets"): the day it was added Novig listed
 * 50 ATP/WTA matches (763 markets) in the next four days against 9 games in the leagues Vigilant scanned.
 * Novig's and Kalshi's shapes below are as read live that day; Pinnacle's follow PinnWire's documented
 * `/kit/v1/markets` shape (sport_id 2 = tennis, period 1 = 1st set).
 */
class TennisTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val atp = Leagues.byNovigName("ATP")!!
    private val wta = Leagues.byNovigName("WTA")!!
    private val start = Instant.parse("2026-09-28T04:00:00Z").toEpochMilli()
    private val now = start - 6 * 3_600_000L

    // ---- Novig, as listed 2026-09-28 ---------------------------------------------------------------

    private val rublev = NovigEvent("t1", "TENNIS", "ATP", NovigEvent.STATUS_PREGAME, "Andrey Rublev @ Kyrian Jacquet Semifinals", start)

    private fun market(id: String, type: String, description: String, vararg outcomes: Pair<String, String>, event: String = "t1") =
        NovigMarket(id, event, type, "OPEN", description, start, MarketFee.GAME, outcomes.map { NovigOutcome(it.first, it.second, "TBD") })

    private val rublevMarkets = listOf(
        market("ml", "MONEY", "A. Rublev", "ml-r" to "A. Rublev", "ml-j" to "K. Jacquet"),
        market("sp", "SPREAD", "K. Jacquet +3.5", "sp-j" to "K. Jacquet +3.5", "sp-r" to "A. Rublev -3.5"),
        market("to", "TOTAL", "A. Rublev @ K. Jacquet t21.5", "to-o" to "Over 21.5", "to-u" to "Under 21.5"),
        market("gw", "PLAYER_GAMES_WON", "Andrey Rublev 12.5 PLAYER_GAMES_WON", "gw-o" to "Over 12.5", "gw-u" to "Under 12.5"),
        market("s1", "FIRST_SET_MONEYLINE", "K. Jacquet Set 1", "s1-j" to "K. Jacquet", "s1-r" to "A. Rublev"),
        // Sets markets: no fair source here prices them, so they're never read.
        market("ss", "SET_SPREAD", "A. Rublev -1.5", "ss-r" to "A. Rublev -1.5", "ss-j" to "K. Jacquet +1.5"),
        market("ts", "TOTAL_SETS", "A. Rublev @ K. Jacquet t2.5", "ts-o" to "Over 2.5", "ts-u" to "Under 2.5"),
    )

    // ---- Kalshi, as read 2026-09-28 (titles list the players alphabetically, not away/home) ----------

    private val kalshi = """
        {"events":[
         {"event_ticker":"KXATPMATCH-26SEP27JACRUB","series_ticker":"KXATPMATCH","title":"Jacquet vs Rublev","sub_title":"Jacquet vs Rublev (Sep 27)",
          "markets":[
           {"ticker":"KXATPMATCH-26SEP27JACRUB-JAC","yes_sub_title":"Kyrian Jacquet","yes_bid_dollars":"0.2500","yes_ask_dollars":"0.2600","status":"active","yes_bid_size_fp":"20371.04","yes_ask_size_fp":"16311.75"},
           {"ticker":"KXATPMATCH-26SEP27JACRUB-RUB","yes_sub_title":"Andrey Rublev","yes_bid_dollars":"0.7400","yes_ask_dollars":"0.7500","status":"active","yes_bid_size_fp":"15002.75","yes_ask_size_fp":"94014.35"}]},
         {"event_ticker":"KXWTAMATCH-26SEP27BASNIC","series_ticker":"KXWTAMATCH","title":"Bassols Ribera vs Nicholls","sub_title":"Bassols Ribera vs Nicholls (Sep 27)",
          "markets":[
           {"ticker":"KXWTAMATCH-26SEP27BASNIC-BAS","yes_sub_title":"Marina Bassols Ribera","yes_bid_dollars":"0.9600","yes_ask_dollars":"0.9700","status":"active","yes_bid_size_fp":"4757.00","yes_ask_size_fp":"13144.00"},
           {"ticker":"KXWTAMATCH-26SEP27BASNIC-NIC","yes_sub_title":"Olivia Nicholls","yes_bid_dollars":"0.0400","yes_ask_dollars":"0.0500","status":"active","yes_bid_size_fp":"17.63","yes_ask_size_fp":"33144.57"}]}
        ],"cursor":""}
    """.trimIndent()

    private fun kalshiEvents() = json.decodeFromString(KalshiClient.PageDto.serializer(), kalshi).events

    // ---- Pinnacle (PinnWire's shape): one match, and the rows that must not price it ----------------

    private val pinnacle = """
        {"events":[
         {"event_id":1,"sport_id":2,"league_name":"ATP Tokyo","home":"Kyrian Jacquet","away":"Andrey Rublev","starts":"2026-09-28T05:00:00Z",
          "periods":{
           "num_0":{"money_line":{"home":3.70,"away":1.30},
                    "spreads":{"3.5":{"hdp":3.5,"home":1.92,"away":1.96}},
                    "totals":{"21.5":{"points":21.5,"over":1.90,"under":1.96}},
                    "team_total":{"home":{"points":9.5,"over":1.95,"under":1.87},"away":{"points":12.5,"over":1.85,"under":1.97}}},
           "num_1":{"money_line":{"home":3.10,"away":1.38}}}},
         {"event_id":2,"sport_id":2,"league_name":"ATP Tokyo","home":"Kyrian Jacquet (Sets)","away":"Andrey Rublev (Sets)","starts":"2026-09-28T05:00:00Z",
          "periods":{"num_0":{"money_line":{"home":3.70,"away":1.30},"spreads":{"1.5":{"hdp":1.5,"home":1.60,"away":2.35}}}}},
         {"event_id":3,"sport_id":2,"league_name":"ATP Tokyo Doubles","home":"Jacquet K / Mannarino A","away":"Rublev A / Khachanov K","starts":"2026-09-28T05:00:00Z",
          "periods":{"num_0":{"money_line":{"home":2.10,"away":1.75}}}},
         {"event_id":4,"sport_id":2,"league_name":"ITF Men Monastir","home":"Kyrian Jacquet","away":"Andrey Rublev","starts":"2026-09-28T05:00:00Z",
          "periods":{"num_0":{"money_line":{"home":1.50,"away":2.60}}}},
         {"event_id":5,"sport_id":2,"league_name":"ATP Beijing","home":"Tomas Machac","away":"Yannick Hanfmann","starts":"2026-09-28T05:00:00Z",
          "periods":{"num_0":{"money_line":{"home":1.40,"away":3.00},"spreads":{"-1.5":{"hdp":-1.5,"home":2.10,"away":1.75}},"totals":{"2.5":{"points":2.5,"over":2.60,"under":1.50}}}}}
        ]}
    """.trimIndent()

    private fun pinnacleEvents(league: League = atp) =
        PinnapiClient.parse((json.parseToJsonElement(pinnacle) as JsonObject)["events"].let { it as JsonArray }.map { it as JsonObject }, league, now)

    private val settings = ScanSettings(
        leagues = setOf("ATP", "WTA"), fairSource = FairSource.SHARP, sharpBooks = setOf("pinnacle", "kalshi"),
        devigMethod = DevigMethod.MULTIPLICATIVE, minEvPercent = -1.0, maxEvPercent = 1.0,
    )

    private fun book(id: String, vararg bids: Pair<String, Int>) =
        id to NovigBook(id, 1, bids.associate { (o, p) -> o to listOf(BidLevel(p, 10_000)) }, now)

    private fun mult(a: Double, b: Double) = (1 / a) / (1 / a + 1 / b)

    // ---- tests -----------------------------------------------------------------------------------------

    @Test
    fun `tennis leagues exist, read by Kalshi and Pinnacle, never asked of The Odds API or PropLine`() {
        for (l in listOf(atp, wta)) {
            assertTrue(l.tennis)
            assertFalse(l.oddsApiListed)
            assertEquals(2, l.pinnacleSportId) // PinnWire/pinnapi: 1 soccer, 2 tennis, 3 basketball …
            assertTrue(l.kalshiSeries.all { KalshiClient.familyOf(it) == MarketFamily.MONEYLINE })
            assertFalse(l.oddsApiSportKey in PropLineClient.SPORTS)
            assertTrue(PropLineProps.marketsFor(l.oddsApiSportKey).isEmpty())
        }
        assertEquals(listOf("KXATPMATCH", "KXATPCHALLENGERMATCH"), atp.kalshiSeries)
        assertEquals(listOf("KXWTAMATCH", "KXWTACHALLENGERMATCH"), wta.kalshiSeries)
        val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))
        val oddsApi = TheOddsApiClient(OkHttpClient(), KeyPool(QuotaPolicy.ODDS_API, { listOf("test-key") }, meter), json)
        assertFalse(oddsApi.supports(atp))
        assertTrue(oddsApi.supports(Leagues.byNovigName("NFL")!!))
        // The new market types come with the families that already hold their kind.
        assertTrue("FIRST_SET_MONEYLINE" in MarketFamily.FIRST_HALF.novigTypes)
        assertTrue("PLAYER_GAMES_WON" in MarketFamily.TEAM_TOTAL.novigTypes)
    }

    @Test
    fun `a tennis round after the home player's name is not part of the name`() {
        assertEquals("Kyrian Jacquet", NovigText.parseMatchup("Andrey Rublev @ Kyrian Jacquet Semifinals")!!.home)
        assertEquals("Marina Bassols Ribera", NovigText.parseMatchup("Olivia Nicholls @ Marina Bassols Ribera 1st Qualifying Round")!!.home)
        assertEquals("Kristiana Sidorova", NovigText.parseMatchup("Daria Egorova @ Kristiana Sidorova Round of 32")!!.home)
        assertEquals("Daniil Medvedev", NovigText.parseMatchup("Roman Safiullin @ Daniil Medvedev Final")!!.home)
        assertEquals("Roman Safiullin", NovigText.parseMatchup("Roman Safiullin @ Daniil Medvedev Quarterfinals")!!.away)
        // Team sports are untouched.
        assertEquals("Dallas Cowboys", NovigText.parseMatchup("Baltimore Ravens @ Dallas Cowboys")!!.home)
        assertEquals("Mississippi State", NovigText.parseMatchup("Alabama @ Mississippi State")!!.home)
    }

    @Test
    fun `kalshi's tennis matches parse into a winner line per match`() {
        val games = KalshiClient.parse(kalshiEvents(), atp, 0.03, now)
        val r = games.single { it.id.endsWith("JACRUB") }
        // Away/home follow the code's order ("JACRUB"); the planner checks both ways anyway.
        assertEquals("Kyrian Jacquet", r.away)
        assertEquals("Andrey Rublev", r.home)
        assertEquals("2026-09-27", r.etDate)
        val ml = r.markets.single()
        assertEquals(LineKind.MONEYLINE, ml.kind)
        assertEquals(1 / 0.26, ml.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-12)
        assertEquals(1 / 0.75, ml.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-12)
        // Nicholls' side is too thin (17 contracts bid): Bassols Ribera's market alone gives both sides.
        val b = games.single { it.id.endsWith("BASNIC") }.markets.single()
        assertEquals(1 / 0.97, b.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-12)
        assertEquals(1 / 0.04, b.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-12)
    }

    @Test
    fun `pinnacle's tennis board gives the match's own lines and skips sets, doubles and other tours`() {
        val events = pinnacleEvents()
        assertEquals(setOf("pin:1", "pin:5"), events.map { it.id }.toSet())
        val m = events.single { it.id == "pin:1" }.markets
        assertEquals(1.30, m.single { it.kind == LineKind.MONEYLINE && it.period == 0 }.quotes.single { it.side == Side.AWAY }.decimalOdds, 0.0)
        assertEquals(1.38, m.single { it.kind == LineKind.MONEYLINE && it.period == 1 }.quotes.single { it.side == Side.AWAY }.decimalOdds, 0.0)
        assertEquals(3.5, m.single { it.kind == LineKind.SPREAD }.line!!, 0.0)
        assertEquals(21.5, m.single { it.kind == LineKind.TOTAL }.line!!, 0.0)
        assertEquals(12.5, m.single { it.kind == LineKind.TEAM_TOTAL && it.subject == RefBookMarket.AWAY }.line!!, 0.0)
        // A row whose totals are sets (2.5) keeps its winner only: its spread and total aren't games.
        val sets = events.single { it.id == "pin:5" }.markets
        assertEquals(listOf(LineKind.MONEYLINE), sets.map { it.kind })
        // Other sports never take a period-1 winner from Pinnacle.
        val nfl = PinnapiClient.parse(
            listOf(json.parseToJsonElement("""{"event_id":9,"league_name":"NFL","home":"Dallas Cowboys","away":"Baltimore Ravens","starts":"2026-09-28T05:00:00Z","periods":{"num_0":{"money_line":{"home":1.8,"away":2.1}},"num_1":{"money_line":{"home":1.7,"away":2.2}}}}""") as JsonObject),
            Leagues.byNovigName("NFL")!!, now,
        ).single()
        assertEquals(listOf(0), nfl.markets.filter { it.kind == LineKind.MONEYLINE }.map { it.period })
    }

    @Test
    fun `a Novig tennis match prices its winner, games spread and total, games won and 1st set`() {
        val refs = listOf(
            RefSnapshot(atp.oddsApiSportKey, pinnacleEvents(), now, provider = "pinnacle"),
            RefSnapshot(atp.oddsApiSportKey, KalshiClient.parse(kalshiEvents(), atp, 0.03, now), now, provider = "kalshi"),
        )
        val plan = Planner.plan(listOf(rublev), rublevMarkets, refs, settings, now)
        assertEquals(1, plan.matchedEvents)
        assertEquals(setOf("ml", "sp", "to", "gw", "s1"), plan.marketIds.toSet())

        val books = mapOf(
            // Rublev costs 1 − 0.280 = 0.72 to take.
            book("ml", "ml-j" to 280, "ml-r" to 700),
            book("sp", "sp-j" to 500, "sp-r" to 480),
            book("to", "to-o" to 480, "to-u" to 490),
            book("gw", "gw-o" to 450, "gw-u" to 500),
            book("s1", "s1-j" to 260, "s1-r" to 700),
        )
        val r = Pricing.price(plan, books, settings, now)
        fun opp(outcome: String) = r.opportunities.single { it.outcome.outcomeId == outcome }

        val win = opp("ml-r")
        assertEquals("Andrey Rublev", win.selection)
        // Both books, each devigged, averaged: Pinnacle 1.30/3.70 and Kalshi (oriented the other way) 0.75/0.26.
        val fair = (mult(1.30, 3.70) + 0.75 / 1.01) / 2
        assertEquals(fair, win.fairProbability!!, 1e-9)
        assertEquals(0.72, win.quote!!.price, 1e-12)
        assertEquals(fair / 0.72 - 1, win.evPercent!!, 1e-9)
        assertEquals(setOf("Pinnacle", "Kalshi"), win.fair!!.sharpBooksUsed.toSet())

        assertEquals("Kyrian Jacquet +3.5", opp("sp-j").selection)
        assertEquals(mult(1.92, 1.96), opp("sp-j").fairProbability!!, 1e-9)
        assertEquals(mult(1.90, 1.96), opp("to-o").fairProbability!!, 1e-9)

        val games = opp("gw-o")
        assertEquals("Andrey Rublev Over 12.5", games.selection)
        assertEquals("Games Won", games.marketLabel)
        assertEquals(mult(1.85, 1.97), games.fairProbability!!, 1e-9)

        val set1 = opp("s1-j")
        assertEquals("Kyrian Jacquet", set1.selection)
        assertEquals("1st Set Winner", set1.marketLabel)
        assertEquals(mult(3.10, 1.38), set1.fairProbability!!, 1e-9)
    }

    @Test
    fun `a WTA qualifier matches Kalshi's listing a day either side, players named the other way round`() {
        // 22:00 Eastern on Sep 27, Kalshi's date; Novig lists Nicholls away, Kalshi lists her second.
        val match = NovigEvent("t2", "TENNIS", "WTA", NovigEvent.STATUS_PREGAME, "Olivia Nicholls @ Marina Bassols Ribera 1st Qualifying Round", Instant.parse("2026-09-28T02:00:00Z").toEpochMilli())
        val ml = market("wml", "MONEY", "M. Bassols Ribera", "w-b" to "M. Bassols Ribera", "w-n" to "O. Nicholls", event = "t2")
        val refs = listOf(RefSnapshot(wta.oddsApiSportKey, KalshiClient.parse(kalshiEvents(), wta, 0.03, now), now, provider = "kalshi"))
        val plan = Planner.plan(listOf(match), listOf(ml), refs, settings, now)
        assertEquals(listOf("wml"), plan.marketIds)
        val r = Pricing.price(plan, mapOf(book("wml", "w-b" to 950, "w-n" to 30)), settings, now)
        val bassols = r.opportunities.single { it.outcome.outcomeId == "w-b" }
        assertEquals("Marina Bassols Ribera", bassols.selection)
        assertEquals(0.97 / 1.01, bassols.fairProbability!!, 1e-9)
        assertEquals(0.97, bassols.quote!!.price, 1e-12)
        // Two days on is another match: no fair price (shown on the Games tab only).
        val later = match.copy(startsTs = match.startsTs + 2 * 86_400_000L)
        val unmatched = Planner.plan(listOf(later), listOf(ml), refs, settings, now)
        assertEquals(0, unmatched.matchedEvents)
        assertTrue(unmatched.markets.none { it.lineKey != null })
    }

    @Test
    fun `tennis is turned on once for a saved file, and stays off once turned off`() {
        assertEquals(setOf("NFL", "MLB", "ATP", "WTA"), ScanSettings(leagues = setOf("NFL", "MLB"), schema = 6).migrate().leagues)
        assertEquals(setOf("NFL"), ScanSettings(leagues = setOf("NFL"), schema = 7).migrate().leagues)
        // Chips: after Tj's first five.
        assertEquals(listOf("ATP", "WTA"), Leagues.ALL.drop(5).take(2).map { it.novigName })
    }
}
