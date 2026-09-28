package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-28: "The app found several positive EV bets while scanning but they quickly disappeared. Is this
 * supposed to happen?" Measured live (`LiveFlickerTest`): a Mystics moneyline showed 19 s into a scan, priced
 * from Polymarket alone, and left the feed when Kalshi answered and the fair price moved. A bet shown mid-scan
 * must be the bet the scan ends with, and must not be one about to go stale.
 */
class SteadyFeedTest {

    private val now = Fixtures.START_MS - 86_400_000L
    private val event = NovigEvent("e1", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
    private val ml = NovigMarket("ml", "e1", "MONEY", "OPEN", "", Fixtures.START_MS, MarketFee.GAME, listOf(NovigOutcome("dal", "DAL", "TBD"), NovigOutcome("bal", "BAL", "TBD")))
    private val prop = NovigMarket("p1", "e1", "RECEPTIONS", "OPEN", "CeeDee Lamb 6.5 RECEPTIONS", Fixtures.START_MS, MarketFee.GAME, listOf(NovigOutcome("o", "Over 6.5", "TBD"), NovigOutcome("u", "Under 6.5", "TBD")))

    /** Dallas can be taken at 0.50 (the Baltimore bid is 0.50); the Over at 0.50. */
    private val novig = object : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(event)
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = listOf(ml, prop)
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            val books = marketIds.associateWith { id ->
                if (id == "ml") NovigBook(id, 1, mapOf("bal" to listOf(BidLevel(500, 1000)), "dal" to listOf(BidLevel(480, 1000))), now)
                else NovigBook(id, 1, mapOf("u" to listOf(BidLevel(500, 1000)), "o" to listOf(BidLevel(480, 1000))), now)
            }
            return BookBatch(books, 0, books.size, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private fun ref(vararg markets: RefBookMarket) =
        RefSnapshot("americanfootball_nfl", listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens", markets = markets.toList())), now)

    /** Dallas's win chance [p] (home), from [key]'s moneyline. */
    private fun moneyline(key: String, p: Double, seen: Long? = now) =
        RefBookMarket(key, key, LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 1 / p, null), RefQuote(Side.AWAY, 1 / (1 - p), null)), seen)

    private fun receptions(key: String, over: Double) =
        RefBookMarket(key, key, LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, 1 / over, 6.5), RefQuote(Side.UNDER, 1 / (1 - over), 6.5)), now, 0, "CeeDee Lamb", "RECEPTIONS")

    /** A source that answers when [gate] is completed (null = at once). */
    private class Source(override val id: String, private val snap: RefSnapshot, private val gate: CompletableDeferred<Unit>? = null, override val propsOnly: Boolean = false) : ReferenceSource {
        override val displayName = id
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            gate?.await()
            return snap
        }
    }

    private val settings = ScanSettings(
        leagues = setOf("NFL"), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01,
        sharpBooks = emptySet(), outlierGuard = false,
    )

    @Test
    fun `a league's bets wait mid-scan until every fair source for it has answered`() = runTest {
        // Polymarket alone: Dallas 53% fair against a 0.50 price = +6%. With Kalshi's 47%, the average is 50%: no edge.
        val fast = Source("polymarket", ref(moneyline("polymarket", 0.53)))
        val slowGate = CompletableDeferred<Unit>()
        val slow = Source("kalshi", ref(moneyline("kalshi", 0.47)), slowGate)
        val shownMidScan = ArrayList<List<String>>()
        val scan = async {
            Scanner(novig, clock = { now }).scan(settings.copy(families = setOf(MarketFamily.MONEYLINE)), listOf(fast, slow), emptySet(), {}, { r ->
                shownMidScan += r.feed(settings).map { it.outcome.outcomeId }
                // The first partial result lands while Kalshi is still out: then let it answer.
                if (!slowGate.isCompleted) slowGate.complete(Unit)
            })
        }
        repeat(50) { yield() }
        slowGate.complete(Unit)
        val report = scan.await()
        assertTrue("a partial result was published", shownMidScan.isNotEmpty())
        // Before this fix the first partial showed Dallas at +6% (Polymarket alone), then it vanished.
        assertTrue("Dallas never flashed: $shownMidScan", shownMidScan.none { "dal" in it })
        assertTrue(report.result!!.feed(settings).none { it.outcome.outcomeId == "dal" })
    }

    @Test
    fun `game lines show while a props-only source is still out, props once it answers`() = runTest {
        val lines = Source("pinnacle", ref(moneyline("pinnacle", 0.56), receptions("pinnacle", 0.56)))
        val propsGate = CompletableDeferred<Unit>()
        val books = Source("propline_props", ref(receptions("draftkings", 0.56)), propsGate, propsOnly = true)
        val partials = ArrayList<Set<String>>()
        val scan = async {
            Scanner(novig, clock = { now }).scan(settings, listOf(lines, books), emptySet(), {}, { r ->
                partials += r.feed(settings).map { it.outcome.outcomeId }.toSet()
            })
        }
        repeat(50) { yield() }
        val beforeProps = partials.toList()
        propsGate.complete(Unit)
        val report = scan.await()
        assertTrue("a partial result was published before the props source answered", beforeProps.isNotEmpty())
        assertTrue("the moneyline showed while props were loading: $beforeProps", beforeProps.any { "dal" in it })
        assertTrue("no prop before its source answered: $beforeProps", beforeProps.none { "o" in it })
        assertTrue(report.result!!.feed(settings).any { it.outcome.outcomeId == "o" })
    }

    @Test
    fun `a scan never prices with a book price that would go stale within 2 minutes`() = runTest {
        // A sportsbook price the feed last saw 3.5 minutes ago: under 5, but only 1.5 minutes of life left.
        val borderline = Source("kalshi", ref(moneyline("kalshi", 0.53, seen = now - 210_000L)))
        var report = Scanner(novig, clock = { now }).scan(settings.copy(families = setOf(MarketFamily.MONEYLINE)), listOf(borderline), emptySet(), {}, {})
        assertNull(report.result!!.opportunities.firstOrNull { it.outcome.outcomeId == "dal" }?.fairProbability)
        assertTrue(report.result!!.feed(settings).isEmpty())
        // 2.5 minutes old (2.5 left): priced, and shown for at least 2 minutes.
        val fresh = Source("kalshi", ref(moneyline("kalshi", 0.53, seen = now - 150_000L)))
        report = Scanner(novig, clock = { now }).scan(settings.copy(families = setOf(MarketFamily.MONEYLINE)), listOf(fresh), emptySet(), {}, {})
        val dal = report.result!!.opportunities.first { it.outcome.outcomeId == "dal" }
        assertNotNull(dal.fairProbability)
        assertEquals(false, dal.fairIsOld(now + Freshness.MIN_SHOWN_MS))
        assertEquals(3 * 60_000L, Freshness.MAX_QUOTE_AGE_MS - Freshness.MIN_SHOWN_MS)
    }
}
