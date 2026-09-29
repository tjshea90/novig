package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tj, 2026-09-29: "update the EV for every single open bet, including bets added from vigilant scanner": Vigilant's own bets are priced from
 * Vigilant's own fair odds (the feed's), in a pass that reads only those bets' games and Novig books, and every bet ends with a current EV
 * (against the price it was bet at) or the reason it has none.
 */
class OpenBetPricerTest {

    @get:Rule val tmp = TemporaryFolder()
    private var now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0, sharpBooks = setOf("pinnacle"))

    private val otherEvent = "event-other"
    private val otherMarket = "market-other"

    /** Novig with two games listed: the one the fair odds cover, and another nobody covers. Records what's read and watched. */
    private inner class FakeNovig(val league: String = "NFL", val startsMs: Long = Fixtures.START_MS) : NovigSource {
        val bookIds = ArrayList<String>()
        var watched = 0
        var catalogTypes: Collection<String> = emptyList()
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(
            NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", league, "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", startsMs),
            NovigEvent(otherEvent, "FOOTBALL", league, "OPEN_PREGAME", "Denver Broncos @ Miami Dolphins", startsMs),
        )
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?): List<NovigMarket> {
            catalogTypes = marketTypes
            return listOf(
                NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", startsMs, MarketFee.GAME,
                    listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD"))),
                NovigMarket(otherMarket, otherEvent, "MONEY", "OPEN", "MIA", startsMs, MarketFee.GAME,
                    listOf(NovigOutcome("o-mia", "MIA", "TBD"), NovigOutcome("o-den", "DEN", "TBD"))),
            )
        }
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            bookIds += marketIds
            onProgress?.invoke(marketIds.size, marketIds.size)
            val out = HashMap<String, NovigBook>()
            for (id in marketIds) {
                when (id) {
                    Fixtures.ML_MARKET -> out[id] = NovigBook(id, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(615, 1000))), now)
                    otherMarket -> out[id] = NovigBook(id, 1, mapOf("o-mia" to listOf(BidLevel(500, 1000)), "o-den" to listOf(BidLevel(500, 1000))), now)
                }
            }
            return BookBatch(out, 0, out.size, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
        override fun watch(marketIds: Collection<String>) { watched++ }
    }

    /** The Odds API, faked: quotes the Ravens @ Cowboys game only, and counts its calls. */
    private class FakeOddsApi : ReferenceSource {
        var calls = 0
        override val id = "oddsapi"
        override val displayName = "The Odds API"
        override val metered = true
        override fun reuseMs(settings: ScanSettings) = settings.oddsApiReuseMinutes * 60_000L
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            calls++
            return RefSnapshot(league.oddsApiSportKey, Fixtures.oddsApiSeenNow(), 0, 488, 12)
        }
    }

    private fun bet(
        id: String, marketId: String = Fixtures.ML_MARKET, outcomeId: String = Fixtures.ML_DAL, league: String = "NFL", cost: Double = 0.40,
        startsTs: Long = Fixtures.START_MS, status: BetStatus = BetStatus.PENDING,
    ) = TrackedBet(
        id = id, createdAtMs = now - 3_600_000L, league = league, eventName = "Baltimore Ravens @ Dallas Cowboys", startsTs = startsTs,
        marketLabel = "Moneyline", selection = "DAL", marketId = marketId, outcomeId = outcomeId, price = cost, cost = cost,
        fairAtBet = 0.45, evPercentAtBet = 0.125, stake = 10.0, status = status,
    )

    private fun tracker(vararg bets: TrackedBet): BetTracker {
        File(tmp.root, "bets.json").writeText(Json.encodeToString(ListSerializer(TrackedBet.serializer()), bets.toList()))
        return BetTracker(File(tmp.root, "bets.json"), clock = { now })
    }

    private fun pricer(t: BetTracker, novig: FakeNovig, fair: FakeOddsApi = FakeOddsApi()) =
        OpenBetPricer(t, Scanner(novig, clock = { now }, betsOnly = true), { listOf(fair) }, clock = { now })

    @Test
    fun `a Vigilant bet gets Vigilant's fair odds now, as EV at the price it was bet at, and reads only its own book`() = runTest {
        val novig = FakeNovig()
        val t = tracker(bet("a"))
        val report = pricer(t, novig).run(settings, listOf("a"))
        assertEquals(OpenBetPricer.Report(1, 1, 0), report)
        val b = t.all().single()
        // The feed's own scan of the same market gives the same fair probability: the pass is the feed's pricing, not a second opinion.
        val feed = Scanner(FakeNovig(), clock = { now }).scan(settings, listOf(FakeOddsApi())).result!!.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertEquals(feed.fairProbability!!, b.nowFair!!, 1e-12)
        assertEquals(b.nowFair!! / 0.40 - 1.0, b.nowEv!!, 1e-12)
        assertEquals(BetTracker.VIA_VIGILANT, b.nowVia)
        assertEquals(now, b.nowAtMs)
        assertNull(b.nowNote)
        assertTrue(b.books.isNotEmpty())
        // Only this bet's market was read; the other game on Novig's board never was, and the live feed's watch list was left alone.
        assertEquals(listOf(Fixtures.ML_MARKET), novig.bookIds)
        assertEquals(0, novig.watched)
    }

    @Test
    fun `leagues Tj switched off, games past Days ahead and market families he hid don't hide an open bet`() = runTest {
        // Feed filters: only MLB, one day ahead, moneylines off. The bet is an NFL moneyline nine days out.
        now = Fixtures.START_MS - 9 * 86_400_000L
        val narrow = settings.copy(leagues = setOf("MLB"), daysAhead = 1, families = setOf(com.tjshea.vigilant.data.scanner.MarketFamily.PLAYER_PROPS))
        val t = tracker(bet("a"))
        val report = pricer(t, FakeNovig()).run(narrow, listOf("a"))
        assertEquals(1, report.priced)
        assertNotNull(t.all().single().nowEv)
    }

    @Test
    fun `every pass asks for fresh fair odds, and never re-uses the games an earlier pass asked for`() = runTest {
        val fair = FakeOddsApi()
        val p = pricer(tracker(bet("a")), FakeNovig(), fair)
        p.run(settings.copy(oddsApiReuseMinutes = 2), listOf("a"))
        now += 30_000
        p.run(settings.copy(oddsApiReuseMinutes = 2), listOf("a"))
        assertEquals(2, fair.calls)
    }

    @Test
    fun `a bet nothing prices keeps its last number and says why, in the reason for its case`() = runTest {
        val t = tracker(
            bet("gone", marketId = "closed-market"),
            bet("nofair", marketId = otherMarket, outcomeId = "o-mia"),
            bet("league", league = "MLS (USA)"),
            bet("old", marketId = "", outcomeId = ""),
            bet("won", status = BetStatus.WON),
            bet("started", startsTs = now - 60_000L),
        )
        val first = pricer(t, FakeNovig()).run(settings, listOf("gone", "nofair", "league", "old", "won", "started"))
        // The settled bet and the started game aren't asked about at all; the other four each get their own reason.
        assertEquals(OpenBetPricer.Report(4, 0, 4), first)
        val by = t.all().associateBy { it.id }
        assertEquals("Novig no longer lists this market (closed, or the game moved)", by.getValue("gone").nowNote)
        assertTrue(by.getValue("nofair").nowNote!!.startsWith("No fair-odds source has current prices for this game"))
        assertEquals("Vigilant doesn't price MLS (USA)", by.getValue("league").nowNote)
        assertTrue(by.getValue("old").nowNote!!.contains("no Novig market on record"))
        assertNull(by.getValue("won").nowNote)
        assertNull(by.getValue("started").nowNote)
        by.values.forEach { assertNull(it.id, it.nowEv) }
        assertEquals(now, by.getValue("gone").nowNoteAtMs)
    }

    @Test
    fun `a bet that was priced before keeps that EV when a later pass can't price it, with the reason beside it`() = runTest {
        val t = tracker(bet("a"))
        val ok = pricer(t, FakeNovig())
        ok.run(settings, listOf("a"))
        val before = t.all().single()
        now += 10 * 60_000L
        // The fair-odds source answers nothing this time.
        val silent = object : ReferenceSource {
            override val id = "oddsapi"
            override val displayName = "The Odds API"
            override suspend fun odds(league: League, settings: ScanSettings) = RefSnapshot(league.oddsApiSportKey, emptyList(), 0)
        }
        val report = OpenBetPricer(t, Scanner(FakeNovig(), clock = { now }, betsOnly = true), { listOf(silent) }, clock = { now }).run(settings, listOf("a"))
        assertEquals(0, report.priced)
        val after = t.all().single()
        assertEquals(before.nowEv, after.nowEv)
        assertEquals(before.nowAtMs, after.nowAtMs) // the age is the old read's: the card shows it as old, not as "now"
        assertNotNull(after.nowNote)
        // Priced again, the reason is gone.
        now += 60_000L
        ok.run(settings, listOf("a"))
        assertNull(t.all().single().nowNote)
        assertEquals(now, t.all().single().nowAtMs)
    }

    @Test
    fun `nothing to ask about is no pass at all`() = runTest {
        val fair = FakeOddsApi()
        val novig = FakeNovig()
        val report = pricer(tracker(bet("won", status = BetStatus.WON)), novig, fair).run(settings, listOf("won", "missing"))
        assertEquals(OpenBetPricer.Report(0, 0, 0), report)
        assertEquals(0, fair.calls)
        assertTrue(novig.bookIds.isEmpty())
    }

    @Test
    fun `the pass's settings reach the bets and nothing of the feed's filters`() {
        val base = settings.copy(leagues = setOf("MLB"), daysAhead = 1, startsWithinHours = 12, includeLive = true, minBooks = 3)
        val bets = listOf(bet("a"), bet("b", league = "NHL", startsTs = now + 4 * 86_400_000L))
        val s = BetsScope.settingsFor(base, bets, now)
        assertEquals(setOf("NFL", "NHL"), s.leagues)
        assertFalse(s.includeLive)
        assertEquals(0, s.startsWithinHours)
        assertTrue(s.scanWindowHours >= 24 * 4)
        assertEquals(com.tjshea.vigilant.data.scanner.MarketFamily.entries.toSet(), s.families)
        assertEquals(2, s.maxBooksPerScan)
        // How fair odds are worked out is Tj's: the same as the feed's.
        assertEquals(3, s.minBooks)
        assertEquals(base.fairSource, s.fairSource)
    }
}
