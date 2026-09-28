package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerPricingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Fixtures.START_MS - 2 * 86_400_000L

    private val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)

    private fun market(id: String, type: String, vararg outcomes: Pair<String, String>) =
        NovigMarket(id, Fixtures.EVENT_ID, type, "OPEN", "", Fixtures.START_MS, MarketFee.GAME, outcomes.map { NovigOutcome(it.first, it.second, "TBD") })

    private val markets = listOf(
        market(Fixtures.ML_MARKET, "MONEY", Fixtures.ML_DAL to "DAL", Fixtures.ML_BAL to "BAL"),
        market(Fixtures.SPREAD_MARKET, "SPREAD", Fixtures.SPREAD_DAL to "DAL +3.5", Fixtures.SPREAD_BAL to "BAL -3.5"),
        market(Fixtures.ALT_SPREAD_MARKET, "SPREAD", "alt-dal" to "DAL +20.5", "alt-bal" to "BAL -20.5"),
        market(Fixtures.TOTAL_MARKET, "TOTAL", Fixtures.TOTAL_OVER to "Over 47.5", Fixtures.TOTAL_UNDER to "Under 47.5"),
    )

    private val refs = mapOf(
        "americanfootball_nfl" to RefSnapshot("americanfootball_nfl", TheOddsApiClient.parseEvents(Fixtures.oddsApi, json), now),
    )

    private fun book(id: String, vararg bids: Pair<String, Pair<Int, Long>>) =
        NovigBook(id, 1, bids.groupBy({ it.first }, { BidLevel(it.second.first, it.second.second) }), now)

    private val books = mapOf(
        Fixtures.ML_MARKET to book(Fixtures.ML_MARKET, Fixtures.ML_DAL to (380 to 516_409L), Fixtures.ML_BAL to (615 to 248_541L), Fixtures.ML_BAL to (610 to 4_098L)),
        Fixtures.SPREAD_MARKET to book(Fixtures.SPREAD_MARKET, Fixtures.SPREAD_DAL to (470 to 10_000L), Fixtures.SPREAD_BAL to (500 to 20_000L)),
        Fixtures.TOTAL_MARKET to book(Fixtures.TOTAL_MARKET, Fixtures.TOTAL_OVER to (490 to 5_000L), Fixtures.TOTAL_UNDER to (490 to 5_000L)),
    )

    private val sharpOnly = ScanSettings(fairSource = FairSource.SHARP, devigMethod = DevigMethod.MULTIPLICATIVE)

    @Test
    fun `only lines a reference book quotes get planned - the alternate spread costs no book request`() {
        val plan = Planner.plan(listOf(event), markets, refs, sharpOnly, now)
        assertEquals(1, plan.matchedEvents)
        assertEquals(setOf(Fixtures.ML_MARKET, Fixtures.SPREAD_MARKET, Fixtures.TOTAL_MARKET), plan.marketIds.toSet())
    }

    @Test
    fun `moneyline sides and fair price come out right against Pinnacle`() {
        val plan = Planner.plan(listOf(event), markets, refs, sharpOnly, now)
        val r = Pricing.price(plan, books, sharpOnly, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertEquals("Dallas Cowboys", dal.selection)

        // Pinnacle: BAL 1.62 / DAL 2.45, multiplicative devig.
        val raw = listOf(1 / 2.45, 1 / 1.62) // [HOME=DAL, AWAY=BAL]
        val fairDal = raw[0] / raw.sum()
        assertEquals(fairDal, dal.fairProbability!!, 1e-12)
        assertEquals(0.385, dal.quote!!.price, 1e-12)
        assertEquals(fairDal / 0.385 - 1, dal.evPercent!!, 1e-12)
        assertEquals(listOf("Pinnacle"), dal.fair!!.sharpBooksUsed)
        // Novig's own width on this market: 0.385 + 0.62 - 1.
        assertEquals(0.005, dal.novigWidth!!, 1e-12)
    }

    @Test
    fun `spread fair price uses only books on the exact same point`() {
        val avg = ScanSettings(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, devigMethod = DevigMethod.MULTIPLICATIVE)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, avg, now), books, avg, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.SPREAD_DAL }
        assertEquals("Dallas Cowboys +3.5", dal.selection)
        assertEquals(listOf("Pinnacle"), dal.fair!!.averageBooksUsed) // DraftKings is on 3.0, not 3.5
        val raw = listOf(1 / 1.93, 1 / 1.95)
        assertEquals(raw[0] / raw.sum(), dal.fairProbability!!, 1e-12)
        // Take DAL +3.5 against the BAL bid at 0.50.
        assertEquals(0.50, dal.quote!!.price, 1e-12)
    }

    @Test
    fun `totals map over and under`() {
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, sharpOnly, now), books, sharpOnly, now)
        val over = r.opportunities.first { it.outcome.outcomeId == Fixtures.TOTAL_OVER }
        assertEquals("Over 47.5", over.selection)
        val raw = listOf(1 / 1.91, 1 / 1.97)
        assertEquals(raw[0] / raw.sum(), over.fairProbability!!, 1e-12)
        assertEquals(0.51, over.quote!!.price, 1e-12)
    }

    @Test
    fun `the feed keeps positive EV above the threshold, best first`() {
        val s = sharpOnly.copy(minEvPercent = 0.0)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, s, now), books, s, now)
        val feed = r.feed(s)
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.evPercent!! >= 0.0 })
        assertEquals(feed.sortedByDescending { it.evPercent }, feed)
        // DAL at +160 vs a fair ~+151 on Pinnacle is a real edge (~3.4%).
        assertTrue(feed.any { it.outcome.outcomeId == Fixtures.ML_DAL })
    }

    @Test
    fun `a home and away swap between providers still maps the right team`() {
        // Same game, but the reference feed lists Baltimore as home (e.g. a neutral site).
        val swapped = RefEvent("ref-x", "americanfootball_nfl", Fixtures.START_MS, home = "Baltimore Ravens", away = "Dallas Cowboys",
            markets = listOf(RefBookMarket("pinnacle", "Pinnacle", LineKind.MONEYLINE,
                listOf(RefQuote(Side.HOME, 1.62, null), RefQuote(Side.AWAY, 2.45, null)), null)))
        val r = mapOf("americanfootball_nfl" to RefSnapshot("americanfootball_nfl", listOf(swapped), now))
        val plan = Planner.plan(listOf(event), markets, r, sharpOnly, now)
        val res = Pricing.price(plan, books, sharpOnly, now)
        val dal = res.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        val raw = listOf(1 / 1.62, 1 / 2.45) // HOME=BAL, AWAY=DAL in the reference feed
        assertEquals(raw[1] / raw.sum(), dal.fairProbability!!, 1e-12)
    }

    @Test
    fun `an event with no reference match still shows Novig's moneyline, without a fair price`() {
        val plan = Planner.plan(listOf(event), markets, emptyMap(), sharpOnly, now)
        assertEquals(listOf(Fixtures.ML_MARKET), plan.marketIds)
        val r = Pricing.price(plan, books, sharpOnly, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertNull(dal.fairProbability)
        assertNull(dal.quote)
        assertTrue(r.feed(sharpOnly).isEmpty())
    }

    @Test
    fun `games beyond the day window and live games are skipped unless asked for`() {
        val far = event.copy(eventId = "far", startsTs = now + 10 * 86_400_000L)
        val live = event.copy(eventId = "live", status = "OPEN_INGAME")
        assertEquals(listOf(Fixtures.EVENT_ID), Planner.eligibleEvents(listOf(event, far, live), sharpOnly, now).map { it.eventId })
        assertEquals(2, Planner.eligibleEvents(listOf(event, far, live), sharpOnly.copy(includeLive = true), now).size)
    }

    @Test
    fun `stake is fractional kelly capped by what the book can fill at a positive edge`() {
        val s = sharpOnly.copy(bankroll = 1_000_000.0, kellyMultiplier = 1.0, minEvPercent = 0.0)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, s, now), books, s, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertNotNull(dal.depth)
        // A million-dollar Kelly stake can't exceed the +EV liquidity on the book.
        assertEquals(dal.depth!!.dollarCost, dal.suggestedStake!!, 1e-9)
        assertTrue(Odds.probabilityToAmerican(dal.quote!!.price) == 160)
    }

    private fun ref(id: String, sport: String, away: String, home: String, start: Long) =
        RefEvent(id, sport, start, home = home, away = away, markets = emptyList())

    @Test
    fun `same-slot college games never cross-match on shared school words`() {
        val t = Fixtures.START_MS
        val novig = listOf(
            NovigEvent("n1", "FOOTBALL", "NCAAF", "OPEN_PREGAME", "Texas @ Oklahoma", t),
            NovigEvent("n2", "FOOTBALL", "NCAAF", "OPEN_PREGAME", "Texas Tech @ Oklahoma State", t),
        )
        val refs = mapOf("americanfootball_ncaaf" to RefSnapshot("americanfootball_ncaaf", listOf(
            // Listed "wrong way round" first, so a first-come match would pick the wrong one.
            ref("r2", "americanfootball_ncaaf", "Texas Tech Red Raiders", "Oklahoma State Cowboys", t),
            ref("r1", "americanfootball_ncaaf", "Texas Longhorns", "Oklahoma Sooners", t),
        ), now))
        val m = Planner.matchEvents(novig, refs).associate { it.event.eventId to it.refEvent?.id }
        assertEquals("r1", m["n1"])
        assertEquals("r2", m["n2"])
    }

    @Test
    fun `a missing game is never matched to a different game that shares only city names`() {
        val t = Fixtures.START_MS
        val novig = listOf(NovigEvent("n", "BASEBALL", "MLB", "OPEN_PREGAME", "New York Yankees @ Chicago Cubs", t))
        val refs = mapOf("baseball_mlb" to RefSnapshot("baseball_mlb", listOf(ref("r", "baseball_mlb", "New York Mets", "Chicago White Sox", t)), now))
        assertNull(Planner.matchEvents(novig, refs).single().refEvent)
    }

    @Test
    fun `in a baseball series Friday's game is never priced with Saturday's line`() {
        val fri = Fixtures.START_MS
        val novig = listOf(NovigEvent("fri", "BASEBALL", "MLB", "OPEN_PREGAME", "Chicago Cubs @ Boston Red Sox", fri))
        // The book feed no longer lists Friday's game; only Saturday's (24h later) remains.
        val refs = mapOf("baseball_mlb" to RefSnapshot("baseball_mlb", listOf(ref("sat", "baseball_mlb", "Chicago Cubs", "Boston Red Sox", fri + 24 * 3_600_000L)), now))
        assertNull(Planner.matchEvents(novig, refs).single().refEvent)
    }

    @Test
    fun `a game past its start time is dropped even if the catalog still says pregame`() {
        val started = event.copy(eventId = "started", startsTs = now - 10 * 60_000L)
        assertTrue(Planner.eligibleEvents(listOf(started), sharpOnly, now).isEmpty())
        assertEquals(1, Planner.eligibleEvents(listOf(started), sharpOnly.copy(includeLive = true), now).size)
    }

    @Test
    fun `the feed drops a league the moment it's deselected, before any refetch`() {
        val s = sharpOnly.copy(minEvPercent = 0.0)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, s, now), books, s, now)
        assertTrue(r.feed(s).isNotEmpty())
        assertTrue(r.feed(s.copy(leagues = setOf("MLB"))).isEmpty())
    }

    // ---- several fair-odds feeds at once (v0.6.0) ------------------------------------------------

    private fun exchangeMl(key: String, away: Double, home: Double) =
        RefBookMarket(key, key, LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, away, null), RefQuote(Side.HOME, home, null)), now)

    @Test
    fun `quotes from every feed that matched a game are pooled, each flipped to one orientation`() {
        // Polymarket lists the game straight; a Kalshi-like feed lists it the other way round.
        val poly = RefEvent("pm:1", "americanfootball_nfl", Fixtures.START_MS, home = "Cowboys", away = "Ravens",
            markets = listOf(exchangeMl("polymarket", away = 1 / 0.63, home = 1 / 0.38)))
        val flipped = RefEvent("k:1", "americanfootball_nfl", Fixtures.START_MS, home = "Baltimore", away = "Dallas",
            markets = listOf(exchangeMl("kalshi", away = 1 / 0.40, home = 1 / 0.61)))
        val snaps = listOf(
            RefSnapshot("americanfootball_nfl", listOf(poly), now, provider = "polymarket"),
            RefSnapshot("americanfootball_nfl", listOf(flipped), now, provider = "kalshi"),
        )
        val avg = sharpOnly.copy(fairSource = FairSource.MARKET_AVERAGE, minBooks = 2)
        val plan = Planner.plan(listOf(event), markets, snaps, avg, now)
        assertEquals(listOf("polymarket", "kalshi"), plan.events.single().providers)
        val dal = Pricing.price(plan, books, avg, now).opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        val p1 = (1 / (1 / 0.38)) / (0.63 + 0.38)
        val p2 = (1 / (1 / 0.40)) / (0.40 + 0.61)
        assertEquals((p1 + p2) / 2, dal.fairProbability!!, 1e-12)
        assertEquals(setOf("polymarket", "kalshi"), dal.fair!!.averageBooksUsed.toSet())
    }

    @Test
    fun `a book two feeds both carry is priced once, from the first feed`() {
        val direct = RefEvent("pin:1", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens",
            markets = listOf(RefBookMarket("pinnacle", "Pinnacle", LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 2.40, null), RefQuote(Side.AWAY, 1.64, null)), now)))
        val snaps = listOf(
            RefSnapshot("americanfootball_nfl", listOf(direct), now, provider = "pinnacle"),
            RefSnapshot("americanfootball_nfl", TheOddsApiClient.parseEvents(Fixtures.oddsApi, json), now, provider = "oddsapi"),
        )
        val r = Pricing.price(Planner.plan(listOf(event), markets, snaps, sharpOnly, now), books, sharpOnly, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        val raw = listOf(1 / 2.40, 1 / 1.64)
        assertEquals(raw[0] / raw.sum(), dal.fairProbability!!, 1e-12)
    }

    @Test
    fun `a date-only feed matches on the Eastern date, never a different day`() {
        val sun = RefEvent("k:sun", "americanfootball_nfl", 0L, home = "Dallas", away = "Baltimore", markets = emptyList(), etDate = "2026-09-27")
        val m = Planner.matchEvents(listOf(event), listOf(RefSnapshot("americanfootball_nfl", listOf(sun), now, provider = "kalshi")))
        assertEquals("k:sun", m.single().refEvent?.id)
        val nba = NovigEvent("b", "BASKETBALL", "NBA", "OPEN_PREGAME", "Boston Celtics @ New York Knicks", Fixtures.START_MS)
        val wrongDay = RefEvent("k:mon", "basketball_nba", 0L, home = "New York", away = "Boston", markets = emptyList(), etDate = "2026-09-28")
        assertNull(Planner.matchEvents(listOf(nba), listOf(RefSnapshot("basketball_nba", listOf(wrongDay), now))).single().refEvent)
    }

    @Test
    fun `spreads and totals are capped per game at the best-covered lines`() {
        val lines = listOf(1.5, 2.5, 3.5, 4.5, 20.5)
        val novigSpreads = lines.map { l ->
            market("sp$l", "SPREAD", "d$l" to "DAL +$l", "b$l" to "BAL -$l")
        }
        // Every line quoted by the exchange; 3.5 also by Pinnacle; 2.5 is closest to even.
        val ex = lines.map { l ->
            val p = 0.5 + (l - 2.5) * 0.04
            RefBookMarket("polymarket", "Polymarket", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 1 / p, l), RefQuote(Side.AWAY, 1 / (1.02 - p), -l)), now)
        }
        val pin = RefBookMarket("pinnacle", "Pinnacle", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 1.93, 3.5), RefQuote(Side.AWAY, 1.95, -3.5)), now)
        val ref = RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens", markets = ex + pin)
        val snaps = listOf(RefSnapshot("americanfootball_nfl", listOf(ref), now))
        val two = Planner.plan(listOf(event), novigSpreads, snaps, sharpOnly.copy(linesPerGame = 2, fillBudget = false), now)
        assertEquals(listOf("sp3.5", "sp2.5"), two.marketIds)
        assertEquals(5, Planner.plan(listOf(event), novigSpreads, snaps, sharpOnly.copy(linesPerGame = 5, fillBudget = false), now).marketIds.size)
        // A line Tj has a bet on is always priced, cap or not, so its closing value keeps updating.
        val pinned = Planner.plan(listOf(event), novigSpreads, snaps, sharpOnly.copy(linesPerGame = 2, fillBudget = false), now, pinned = setOf("sp20.5"))
        assertEquals(setOf("sp3.5", "sp2.5", "sp20.5"), pinned.marketIds.toSet())
    }

    // ---- the per-scan budget's leftovers (Tj, 2026-09-28: "find as many positive EV bets … as possible") ----

    /** Five quoted spreads (3.5 by two books, 2.5 nearest even) and one prop quoted by one book. */
    private fun thinSlate(): Triple<List<NovigMarket>, List<RefSnapshot>, List<Double>> {
        val lines = listOf(1.5, 2.5, 3.5, 4.5, 20.5)
        val novig = lines.map { l -> market("sp$l", "SPREAD", "d$l" to "DAL +$l", "b$l" to "BAL -$l") }
        val ex = lines.map { l ->
            val p = 0.5 + (l - 2.5) * 0.04
            RefBookMarket("polymarket", "Polymarket", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 1 / p, l), RefQuote(Side.AWAY, 1 / (1.02 - p), -l)), now)
        }
        val pin = listOf(3.5, 20.5).map { l -> RefBookMarket("pinnacle", "Pinnacle", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 1.93, l), RefQuote(Side.AWAY, 1.95, -l)), now) }
        val ref = RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens", markets = ex + pin)
        return Triple(novig, listOf(RefSnapshot("americanfootball_nfl", listOf(ref), now)), lines)
    }

    @Test
    fun `budget left after the per-game picks goes to every other quoted line, best-covered first`() {
        val (novig, snaps, _) = thinSlate()
        val plan = Planner.plan(listOf(event), novig, snaps, sharpOnly.copy(linesPerGame = 2), now)
        // The per-game picks as before, then the rest: 20.5 (two books), then nearest even (1.5 before 4.5).
        assertEquals(listOf("sp3.5", "sp2.5", "sp20.5", "sp1.5", "sp4.5"), plan.marketIds)
        assertEquals(listOf(false, false, true, true, true), plan.markets.map { it.spare })
        // Filling never passes the per-scan budget: 3 prices = the 2 picks and the best-covered filler.
        val three = Planner.plan(listOf(event), novig, snaps, sharpOnly.copy(linesPerGame = 2, maxBooksPerScan = 3), now)
        assertEquals(listOf("sp3.5", "sp2.5", "sp20.5"), three.marketIds)
        // A budget the picks already fill adds nothing.
        assertEquals(listOf("sp3.5", "sp2.5"), Planner.plan(listOf(event), novig, snaps, sharpOnly.copy(linesPerGame = 2, maxBooksPerScan = 2), now).marketIds)
    }

    @Test
    fun `filling is on by default and a saved file without it gets it`() {
        assertTrue(ScanSettings().fillBudget)
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(ScanSettings.serializer(), """{"leagues":["NFL"],"linesPerGame":2,"schema":6}""")
        assertTrue(old.fillBudget)
    }

    @Test
    fun `a scan reads the per-game picks before the filler lines`() = kotlinx.coroutines.test.runTest {
        val (novig, snaps, _) = thinSlate()
        val reads = ArrayList<String>()
        val source = object : com.tjshea.vigilant.data.novig.NovigSource {
            override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(event)
            override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = novig
            override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): com.tjshea.vigilant.data.novig.BookBatch {
                reads += marketIds
                return com.tjshea.vigilant.data.novig.BookBatch(emptyMap(), 0, 0, 0)
            }
            override suspend fun market(marketId: String): NovigMarket? = null
        }
        val fair = object : com.tjshea.vigilant.data.reference.ReferenceSource {
            override val id = "polymarket"
            override val displayName = id
            override suspend fun odds(league: League, settings: ScanSettings) = snaps.single()
        }
        Scanner(source, clock = { now }).scan(sharpOnly.copy(linesPerGame = 2), listOf(fair), emptySet(), {}, {})
        assertEquals(listOf("sp3.5", "sp2.5"), reads.take(2).sorted().reversed())
        assertEquals(setOf("sp20.5", "sp1.5", "sp4.5"), reads.drop(2).toSet())
    }

    @Test
    fun `the feed can be ordered by start time instead of EV`() {
        val s = sharpOnly.copy(minEvPercent = -1.0, maxEvPercent = 1.0)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, s, now), books, s, now)
        val byEv = r.feed(s)
        assertEquals(byEv.sortedByDescending { it.evPercent }, byEv)
        val later = r.feed(s.copy(feedSort = FeedSort.START))
        assertEquals(later.sortedWith(compareBy<Opportunity> { it.event.startsTs }.thenByDescending { it.evPercent }), later)
    }
}
