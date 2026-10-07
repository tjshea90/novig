package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LowUsageSource
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.ScanContext
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Low-API-usage prop bids on the wire (Tj, 2026-10-05: "it should not waste api usage on scanning too frequently or scanning books other than the sharp prop books";
 * RESEARCH.md §92): what a scan in the mode asks of each feed, and what it reads of Novig.
 */
class LowUsageScanTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun ev(id: String, league: String, startsIn: Long) = NovigEvent(id, "X", league, "OPEN_PREGAME", "Away @ Home", now + startsIn)

    private fun prop(id: String, event: String, startsIn: Long, type: String = "PASSING_YARDS", status: String = "OPEN") = NovigMarket(
        id, event, type, status, "Player 224.5 $type", now + startsIn, MarketFee.GAME,
        listOf(NovigOutcome("$id-o", "Over 224.5", "TBD"), NovigOutcome("$id-u", "Under 224.5", "TBD")),
    )

    private fun main(id: String, event: String, startsIn: Long) = NovigMarket(
        id, event, "MONEY", "OPEN", "ML", now + startsIn, MarketFee.GAME, listOf(NovigOutcome("$id-a", "A", "TBD"), NovigOutcome("$id-b", "B", "TBD")),
    )

    /** A board with an NFL game in 3 hours, an NFL game and an MLB game in 2 days, and an NHL game in 4 hours that has no prop market yet. */
    private inner class Board : NovigSource {
        val events = listOf(ev("nfl-near", "NFL", 3 * hour), ev("nfl-far", "NFL", 48 * hour), ev("mlb-far", "MLB", 48 * hour), ev("nhl-near", "NHL", 4 * hour))
        val markets = listOf(
            prop("p-nfl-near", "nfl-near", 3 * hour), main("ml-nfl-near", "nfl-near", 3 * hour),
            prop("p-nfl-far", "nfl-far", 48 * hour), prop("p-mlb-far", "mlb-far", 48 * hour, "HITS"),
            main("ml-nhl-near", "nhl-near", 4 * hour),
        )
        var startsBefore: Long? = null
        var types: Collection<String> = emptyList()
        var read: Collection<String> = emptyList()
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events.filter { it.league in leagues }
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?): List<NovigMarket> {
            this.startsBefore = startsBefore
            types = marketTypes
            val ids = events.filter { it.league in leagues }.map { it.eventId }.toSet()
            return markets.filter { it.eventId in ids && it.marketType in marketTypes && (startsBefore == null || it.startsTs <= startsBefore) }
        }
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            read = marketIds
            return BookBatch(marketIds.associateWith { id -> NovigBook(id, 1, mapOf("$id-o" to listOf(BidLevel(470, 1000)), "$id-u" to listOf(BidLevel(500, 1000))), now) }, 0, marketIds.size, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    /** A feed that counts its asks and answers a prop both ways for the NFL game and nothing else. */
    private class Feed(override val id: String, override val propsOnly: Boolean = true) : ReferenceSource {
        val asked = mutableListOf<String>()
        override val displayName = id
        override val needsCatalog = false
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            asked += league.novigName
            return RefSnapshot(league.oddsApiSportKey, emptyList(), 0)
        }
    }

    private val on = ScanSettings(leagues = setOf("NFL", "MLB", "NHL"), makerFocus = BidFocus.LOW_USAGE, maker = true, lowUsageBooks = setOf("kalshi", "prophetx"))

    @Test
    fun `a league with no game in the window and a prop market on Novig is not asked - and one with a game is`() = runTest {
        val kalshi = Feed("kalshi")
        val parlay = Feed("parlay_props")
        val report = Scanner(Board(), clock = { now }).scan(on, listOf(LowUsageSource(kalshi), LowUsageSource(parlay)))
        // NFL has a game in 3 h with a prop market; MLB's only game is in 2 days; NHL's game in 4 h has no prop market.
        assertEquals(listOf("NFL"), kalshi.asked)
        assertEquals(listOf("NFL"), parlay.asked)
        // The report tells a league that was not asked from one that was (Tj's 8:41 PM file said "8 fetched" for reads that never happened).
        val k = report.sources.single { it.id == "kalshi" }
        assertEquals("asked", 1, k.fetched)
        assertEquals("not asked", 2, k.skipped)
    }

    @Test
    fun `a scan with nothing in the window reports every league as skipped, none fetched, and never re-uses a skipped league`() = runTest {
        val kalshi = Feed("kalshi")
        // No game inside the window with a prop market: only the far-off MLB game and the prop-less NHL one are listed.
        val onlyFar = on.copy(leagues = setOf("MLB", "NHL"))
        val scanner = Scanner(Board(), clock = { now })
        val first = scanner.scan(onlyFar, listOf(LowUsageSource(kalshi))).sources.single()
        assertEquals(0, first.fetched)
        assertEquals(2, first.skipped)
        assertTrue(kalshi.asked.isEmpty())
        // A second scan a moment later: still skipped, not "re-used" (a game may have come inside the window).
        val second = scanner.scan(onlyFar, listOf(LowUsageSource(kalshi))).sources.single()
        assertEquals(0, second.reused)
        assertEquals(2, second.skipped)
    }

    @Test
    fun `the same feeds unwrapped are asked for every league, as a scan always did`() = runTest {
        val kalshi = Feed("kalshi")
        Scanner(Board(), clock = { now }).scan(on, listOf(kalshi))
        assertEquals(setOf("NFL", "MLB", "NHL"), kalshi.asked.toSet())
    }

    @Test
    fun `a wrapped feed outside the mode is asked for every league too`() = runTest {
        val kalshi = Feed("kalshi")
        Scanner(Board(), clock = { now }).scan(on.copy(makerFocus = BidFocus.ALL), listOf(LowUsageSource(kalshi)))
        assertEquals(setOf("NFL", "MLB", "NHL"), kalshi.asked.toSet())
    }

    /**
     * Tj's v0.70.1 diagnostics: 2 of the 4 ParlayAPI props calls (6 of 40 credits) bought leagues with no game in the window, because the guard above ran in the low-usage mode alone.
     * [LowUsageSource.windowGuard] is the game-in-window half of it for every scan (the props feed also reads book-only lines Novig has no market for, so a league with a game and no
     * prop market on Novig is still asked).
     */
    @Test
    fun `a metered feed is not asked for a league with no game in the window in any scan, and one with a game is`() = runTest {
        val parlay = Feed("parlay_props")
        val normal = on.copy(makerFocus = BidFocus.ALL, startsWithinHours = 24)
        assertFalse(normal.effective().lowUsageScan)
        assertEquals(24, normal.scanWindowHours)
        val report = Scanner(Board(), clock = { now }).scan(normal, listOf(LowUsageSource(parlay, windowGuard = true)))
        // A 24 h window: NFL's game starts in 3 h and NHL's in 4 h (no prop market on Novig, still a game in the window); MLB's only game is in 2 days.
        assertEquals(setOf("NFL", "NHL"), parlay.asked.toSet())
        val r = report.sources.single { it.id == "parlay_props" }
        assertEquals("asked", 2, r.fetched)
        assertEquals("not asked, and costing nothing", 1, r.skipped)
    }

    @Test
    fun `the window guard is off unless asked for - a plain wrapped feed outside the mode reads every league`() = runTest {
        val parlay = Feed("parlay_props")
        Scanner(Board(), clock = { now }).scan(on.copy(makerFocus = BidFocus.ALL), listOf(LowUsageSource(parlay)))
        assertEquals(setOf("NFL", "MLB", "NHL"), parlay.asked.toSet())
    }

    @Test
    fun `only the feeds the mode enables are read, whoever offered them`() = runTest {
        val kalshi = Feed("kalshi")
        val parlay = Feed("parlay_props")
        val oddsApi = Feed("oddsapi", propsOnly = false)
        val polymarket = Feed("polymarket", propsOnly = false)
        val pinnacle = Feed("pinnacle", propsOnly = false)
        Scanner(Board(), clock = { now }).scan(on, listOf(kalshi, parlay, oddsApi, polymarket, pinnacle))
        assertTrue(kalshi.asked.isNotEmpty() && parlay.asked.isNotEmpty())
        assertTrue("The Odds API", oddsApi.asked.isEmpty())
        assertTrue("Polymarket", polymarket.asked.isEmpty())
        assertTrue("Pinnacle isn't a pick", pinnacle.asked.isEmpty())
    }

    @Test
    fun `Novig's markets are read for the window and a day of slack, props only, not for a week`() = runTest {
        val novig = Board()
        Scanner(novig, clock = { now }).scan(on, emptyList())
        assertEquals(now + (6 + 24) * hour, novig.startsBefore)
        assertTrue("PASSING_YARDS" in novig.types)
        // The main lines' market list always comes along (one cheap request, so the switch re-prices from cache); the plan prices families it is set to, props only.
        assertTrue(on.effective().families == setOf(MarketFamily.PLAYER_PROPS))
        // The usual scan reads days ahead.
        val usual = Board()
        Scanner(usual, clock = { now }).scan(on.copy(makerFocus = BidFocus.ALL), emptyList())
        assertEquals(now + (7 + 1) * 24 * hour, usual.startsBefore)
        assertTrue("SPREAD" in usual.types)
    }

    @Test
    fun `a bets-only pass is never narrowed - it prices Tj's open bets of every kind`() = runTest {
        val novig = Board()
        Scanner(novig, clock = { now }, betsOnly = true).scan(on, emptyList(), pinned = setOf("ml-nfl-near"))
        assertTrue("MONEY" in novig.types)
        assertTrue("SPREAD" in novig.types)
        assertEquals(now + (7 + 1) * 24 * hour, novig.startsBefore)
    }

    // ---- the wrapper itself ---------------------------------------------------------------------------------------------

    private fun context(vararg events: NovigEvent, markets: List<NovigMarket> = emptyList()) = ScanContext(events.toList(), markets, now)

    @Test
    fun `the window is the scan's own - pregame, started games out, a delayed game in`() {
        val nfl = Leagues.byNovigName("NFL")!!
        val p = prop("p", "g", 2 * hour)
        fun has(e: NovigEvent, s: ScanSettings = on.effective()) = LowUsageSource.hasPropsToBidOn(nfl, s, context(e, markets = listOf(p.copy(eventId = e.eventId))))
        assertTrue(has(ev("g", "NFL", 2 * hour)))
        assertTrue("right at the edge of 6 h", has(ev("g", "NFL", 6 * hour)))
        assertFalse("past 6 h", has(ev("g", "NFL", 6 * hour + 60_000L)))
        assertFalse("already started", has(ev("g", "NFL", -30 * 60_000L)))
        assertTrue("held before its start", has(ev("g", "NFL", 2 * hour).copy(status = NovigEvent.STATUS_DELAYED)))
        assertFalse("live", has(ev("g", "NFL", 2 * hour).copy(status = NovigEvent.STATUS_LIVE)))
        // A shorter "starts within" is the window.
        assertFalse(has(ev("g", "NFL", 4 * hour), on.copy(startsWithinHours = 3).effective()))
    }

    @Test
    fun `a closed prop market or a main line alone is nothing to bid on`() {
        val nfl = Leagues.byNovigName("NFL")!!
        val e = ev("g", "NFL", 2 * hour)
        assertFalse(LowUsageSource.hasPropsToBidOn(nfl, on.effective(), context(e, markets = listOf(prop("p", "g", 2 * hour, status = "CLOSED")))))
        assertFalse(LowUsageSource.hasPropsToBidOn(nfl, on.effective(), context(e, markets = listOf(main("m", "g", 2 * hour)))))
        assertFalse(LowUsageSource.hasPropsToBidOn(nfl, on.effective(), context(e)))
        assertTrue(LowUsageSource.hasPropsToBidOn(nfl, on.effective(), context(e, markets = listOf(prop("p", "g", 2 * hour)))))
    }

    @Test
    fun `a skipped league answers with an empty board that counts as answered - the feed behind it is not touched`() = runTest {
        val inner = Feed("kalshi")
        val wrapped = LowUsageSource(inner)
        val mlb = Leagues.byNovigName("MLB")!!
        val snap = wrapped.odds(mlb, on.effective(), context(ev("x", "MLB", 48 * hour)))
        assertTrue(snap.events.isEmpty())
        assertEquals("kalshi", snap.provider)
        assertTrue(inner.asked.isEmpty())
        // It still reports what the feed it wraps reports.
        assertEquals("kalshi", wrapped.id)
        assertTrue(wrapped.propsOnly)
        assertTrue(wrapped.needsCatalog)
        assertNotNull(wrapped.odds(Leagues.byNovigName("NFL")!!, on.effective(), context(ev("g", "NFL", 2 * hour), markets = listOf(prop("p", "g", 2 * hour)))))
        assertEquals(listOf("NFL"), inner.asked)
    }
}
