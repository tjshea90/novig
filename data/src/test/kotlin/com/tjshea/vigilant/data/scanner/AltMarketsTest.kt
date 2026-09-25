package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.ExchangeFixtures
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Player props, team totals and 1st-half lines (Tj's request, 2026-09-25 ~15:20Z). */
class AltMarketsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Fixtures.START_MS - 2 * 86_400_000L
    private val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
    private val s = ScanSettings(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, devigMethod = DevigMethod.MULTIPLICATIVE, minEvPercent = -1.0, maxEvPercent = 1.0)

    private fun market(id: String, type: String, description: String, vararg outcomes: Pair<String, String>) =
        NovigMarket(id, Fixtures.EVENT_ID, type, "OPEN", description, Fixtures.START_MS, MarketFee.GAME, outcomes.map { NovigOutcome(it.first, it.second, "TBD") })

    private fun ou(key: String, kind: LineKind, over: Double, under: Double, line: Double, period: Int = 0, subject: String? = null, stat: String? = null) =
        RefBookMarket(key, key, kind, listOf(RefQuote(Side.OVER, over, line), RefQuote(Side.UNDER, under, line)), now, period, subject, stat)

    private fun ref(vararg markets: RefBookMarket) =
        listOf(RefSnapshot("americanfootball_nfl", listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens", markets = markets.toList())), now))

    private fun book(id: String, a: String, aBid: Int, b: String, bBid: Int) =
        id to NovigBook(id, 1, mapOf(a to listOf(BidLevel(aBid, 10_000)), b to listOf(BidLevel(bBid, 10_000))), now)

    // ---- names ----------------------------------------------------------------------------------

    @Test
    fun `player names match across providers only when it's the same player`() {
        assertTrue(PlayerNames.same("CJ Donaldson Jr.", "C.J. Donaldson"))
        assertTrue(PlayerNames.same("A'ja Wilson", "Aja Wilson"))
        assertTrue(PlayerNames.same("Cam Ward", "Cameron Ward"))
        assertFalse(PlayerNames.same("Marquise Brown", "Hollywood Brown"))
        assertFalse(PlayerNames.same("Josh Allen", "Kyle Allen"))
        assertFalse(PlayerNames.same("Bryce Young", "Bryce Young Jr. Smith"))
    }

    // ---- props ----------------------------------------------------------------------------------

    @Test
    fun `a player prop prices against the same player, stat and line only`() {
        val props = listOf(
            market("p1", "PASSING_YARDS", "Lamar Jackson 224.5 PASSING_YARDS", "o1" to "Over 224.5", "u1" to "Under 224.5"),
            market("p2", "PASSING_YARDS", "Dak Prescott 224.5 PASSING_YARDS", "o2" to "Over 224.5", "u2" to "Under 224.5"),
            market("p3", "RECEPTIONS", "CeeDee Lamb 6.5 RECEPTIONS", "o3" to "Over 6.5", "u3" to "Under 6.5"),
        )
        val refs = ref(
            ou("kalshi", LineKind.PLAYER_PROP, 1 / 0.50, 1 / 0.52, 224.5, subject = "Lamar Jackson", stat = "PASSING_YARDS"),
            // Same player, other stat; other player, same stat but another line: neither applies.
            ou("kalshi", LineKind.PLAYER_PROP, 1 / 0.40, 1 / 0.62, 224.5, subject = "Lamar Jackson", stat = "RUSHING_YARDS"),
            ou("kalshi", LineKind.PLAYER_PROP, 1 / 0.45, 1 / 0.57, 249.5, subject = "Dak Prescott", stat = "PASSING_YARDS"),
        )
        val plan = Planner.plan(listOf(event), props, refs, s, now)
        assertEquals(listOf("p1"), plan.marketIds)
        val r = Pricing.price(plan, mapOf(book("p1", "o1", 470, "u1", 500)), s, now)
        val over = r.opportunities.first { it.outcome.outcomeId == "o1" }
        assertEquals("Lamar Jackson Over 224.5", over.selection)
        assertEquals("Passing Yards", over.marketLabel)
        assertEquals(0.50 / (0.50 + 0.52), over.fairProbability!!, 1e-12)
        assertEquals(0.50, over.quote!!.price, 1e-12) // take Over against the Under bid at 0.50
    }

    @Test
    fun `props are capped per game, best-covered first`() {
        val props = (1..6).map { i -> market("p$i", "RECEPTIONS", "Player $i 3.5 RECEPTIONS", "o$i" to "Over 3.5", "u$i" to "Under 3.5") }
        val quotes = (1..6).map { i -> ou("kalshi", LineKind.PLAYER_PROP, 2.0, 1.9, 3.5, subject = "Player $i", stat = "RECEPTIONS") } +
            ou("pinnacle", LineKind.PLAYER_PROP, 2.0, 1.9, 3.5, subject = "Player 5", stat = "RECEPTIONS")
        val plan = Planner.plan(listOf(event), props, ref(*quotes.toTypedArray()), s.copy(propsPerGame = 2), now)
        assertEquals(2, plan.marketIds.size)
        assertTrue("p5" in plan.marketIds) // quoted by two books
        assertTrue(Planner.plan(listOf(event), props, ref(*quotes.toTypedArray()), s.copy(propsPerGame = 0), now).marketIds.isEmpty())
    }

    // ---- team totals and halves -----------------------------------------------------------------

    @Test
    fun `a team total prices against that team's line, even when the feed lists home and away the other way`() {
        val tt = listOf(market("t1", "TEAM_TOTAL", "Dallas Cowboys 20.5 TEAM_TOTAL", "o" to "Over 20.5", "u" to "Under 20.5"))
        // A feed that calls Baltimore home: Dallas is its AWAY team.
        val swapped = listOf(
            RefSnapshot(
                "americanfootball_nfl",
                listOf(
                    RefEvent(
                        "x", "americanfootball_nfl", Fixtures.START_MS, home = "Baltimore Ravens", away = "Dallas Cowboys",
                        markets = listOf(
                            ou("pinnacle", LineKind.TEAM_TOTAL, 1.95, 1.87, 20.5, subject = RefBookMarket.AWAY),
                            ou("pinnacle", LineKind.TEAM_TOTAL, 1.70, 2.15, 20.5, subject = RefBookMarket.HOME),
                            RefBookMarket("pinnacle", "pinnacle", LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 1.62, null), RefQuote(Side.AWAY, 2.45, null)), now),
                        ),
                    ),
                ),
                now,
            ),
        )
        val plan = Planner.plan(listOf(event), tt, swapped, s, now)
        val r = Pricing.price(plan, mapOf(book("t1", "o", 480, "u", 500)), s, now)
        val over = r.opportunities.first { it.outcome.outcomeId == "o" }
        assertEquals("Dallas Cowboys Over 20.5", over.selection)
        val raw = listOf(1 / 1.95, 1 / 1.87)
        assertEquals(raw[0] / raw.sum(), over.fairProbability!!, 1e-12)
    }

    @Test
    fun `a 1st-half line never prices against the full-game line on the same number`() {
        val half = listOf(market("h1", "TOTAL_1H", "BAL @ DAL t23.5 1H", "o" to "Over 23.5", "u" to "Under 23.5"))
        val onlyFullGame = ref(ou("pinnacle", LineKind.TOTAL, 1.9, 1.9, 23.5, period = 0))
        assertTrue(Planner.plan(listOf(event), half, onlyFullGame, s, now).marketIds.isEmpty())
        val withHalf = ref(ou("pinnacle", LineKind.TOTAL, 1.94, 1.90, 23.5, period = 1))
        val plan = Planner.plan(listOf(event), half, withHalf, s, now)
        assertEquals(listOf("h1"), plan.marketIds)
        assertEquals("1H Total", plan.markets.single().label)
    }

    @Test
    fun `baseball's 1st half is labeled as the first 5 innings`() {
        val mlb = NovigEvent("m", "BASEBALL", "MLB", "OPEN_PREGAME", "Chicago Cubs @ Boston Red Sox", Fixtures.START_MS)
        val f5 = NovigMarket("f5", "m", "SPREAD_1H", "OPEN", "BOS -0.5 1H", Fixtures.START_MS, MarketFee.GAME, listOf(NovigOutcome("b", "BOS -0.5", "TBD"), NovigOutcome("c", "CHC +0.5", "TBD")))
        val refs = listOf(
            RefSnapshot(
                "baseball_mlb",
                listOf(
                    RefEvent(
                        "k", "baseball_mlb", Fixtures.START_MS, home = "Boston", away = "Chicago C",
                        markets = listOf(RefBookMarket("kalshi", "Kalshi", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 2.0, -0.5), RefQuote(Side.AWAY, 1.9, 0.5)), now, period = 1)),
                    ),
                ),
                now,
            ),
        )
        val plan = Planner.plan(listOf(mlb), listOf(f5), refs, s.copy(leagues = setOf("MLB")), now)
        assertEquals("F5 Spread", plan.markets.single().label)
    }

    // ---- budget, leagues, migration --------------------------------------------------------------

    @Test
    fun `the per-scan budget keeps main lines and open bets ahead of props`() {
        val ml = market("ml", "MONEY", "", Fixtures.ML_DAL to "DAL", Fixtures.ML_BAL to "BAL")
        val props = (1..5).map { i -> market("p$i", "RECEPTIONS", "Player $i 3.5 RECEPTIONS", "o$i" to "Over 3.5", "u$i" to "Under 3.5") }
        val quotes = listOf(RefBookMarket("pinnacle", "Pinnacle", LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 2.45, null), RefQuote(Side.AWAY, 1.62, null)), now)) +
            (1..5).map { i -> ou("kalshi", LineKind.PLAYER_PROP, 2.0, 1.9, 3.5, subject = "Player $i", stat = "RECEPTIONS") }
        val plan = Planner.plan(listOf(event), listOf(ml) + props, ref(*quotes.toTypedArray()), s.copy(propsPerGame = 5, maxBooksPerScan = 3), now, pinned = setOf("p4"))
        assertEquals(3, plan.marketIds.size)
        assertTrue("ml" in plan.marketIds)
        assertTrue("p4" in plan.marketIds)
    }

    @Test
    fun `leagues run in Tj's order and the removed ones are gone`() {
        assertEquals(listOf("NFL", "NCAAF", "MLB", "WNBA", "NHL"), Leagues.ALL.take(5).map { it.novigName })
        listOf("EPL", "MLS", "La Liga", "Bundesliga", "Serie A", "Ligue 1", "Champions League", "Europa League", "CFL", "KBO", "NPB")
            .forEach { assertNull(it, Leagues.byNovigName(it)) }
    }

    @Test
    fun `saved settings drop removed leagues and pick up the new market families`() {
        val old = ScanSettings(leagues = setOf("EPL", "NFL", "KBO"), families = setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD, MarketFamily.TOTAL), schema = 2)
        val m = old.migrate()
        assertEquals(setOf("NFL"), m.leagues)
        assertEquals(MarketFamily.entries.toSet(), m.families)
        assertEquals(setOf("NFL"), ScanSettings(leagues = setOf("EPL"), schema = 2).migrate().leagues)
    }

    // ---- sources --------------------------------------------------------------------------------

    @Test
    fun `kalshi props, team totals and 1st-half lines parse into the right lines`() {
        val events = json.decodeFromString(KalshiClient.PageDto.serializer(), ExchangeFixtures.kalshiNflAlt).events
        val g = KalshiClient.parse(events, Leagues.byNovigName("NFL")!!, 0.05, 0L).single()
        val young = g.markets.filter { it.kind == LineKind.PLAYER_PROP && it.subject == "Bryce Young" }
        assertEquals(setOf(199.5, 224.5), young.map { it.line }.toSet())
        assertTrue(young.all { it.stat == "PASSING_YARDS" })
        assertEquals("RECEPTIONS", g.markets.single { it.subject == "Quinshon Judkins" }.stat)
        val tt = g.markets.single { it.kind == LineKind.TEAM_TOTAL }
        assertEquals(RefBookMarket.HOME, tt.subject) // Cleveland is home in "CAR vs CLE"
        assertEquals(20.5, tt.line!!, 0.0)
        val halfSpread = g.markets.single { it.kind == LineKind.SPREAD }
        assertEquals(1, halfSpread.period)
        assertEquals(1.5, halfSpread.line!!, 0.0) // CAR (away) -1.5 means home +1.5
        assertEquals(1, g.markets.single { it.kind == LineKind.TOTAL }.period)
        assertEquals(MarketFamily.PLAYER_PROPS, KalshiClient.familyOf("KXMLBKS"))
        assertEquals(MarketFamily.FIRST_HALF, KalshiClient.familyOf("KXMLBF5TOTAL"))
        assertEquals(MarketFamily.TEAM_TOTAL, KalshiClient.familyOf("KXNCAAFTEAMTOTAL"))
        assertEquals(MarketFamily.SPREAD, KalshiClient.familyOf("KXNFLSPREAD"))
    }

    @Test
    fun `pinnacle's 1st half and team totals come from the same board`() {
        val events = (json.parseToJsonElement(ExchangeFixtures.pinnapiFootballPeriods).jsonObject["events"] as JsonArray).map { it as JsonObject }
        val e = PinnapiClient.parse(events, Leagues.byNovigName("NFL")!!, 1L).single()
        assertEquals(1, e.markets.single { it.kind == LineKind.SPREAD }.period)
        assertEquals(23.5, e.markets.single { it.kind == LineKind.TOTAL && it.period == 1 }.line!!, 0.0)
        val home = e.markets.filter { it.kind == LineKind.TEAM_TOTAL && it.subject == RefBookMarket.HOME }
        assertEquals(setOf(20.5, 21.5), home.map { it.line }.toSet())
        assertEquals(24.5, e.markets.single { it.kind == LineKind.TEAM_TOTAL && it.subject == RefBookMarket.AWAY }.line!!, 0.0)
    }
}
