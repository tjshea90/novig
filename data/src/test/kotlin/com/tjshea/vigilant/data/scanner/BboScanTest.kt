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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scan on the live feed's best-bid channel (NOVIG_API.md §26): every market is screened on its best bid alone, the markets whose best price is +EV are moved to the full book and priced again
 * on the complete ladder, so the result is the one an all-`book` feed gives (the stake, the depth, the ladder), and the markets that are not worth a bet are never asked for a ladder.
 */
class BboScanTest {

    private var now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(leagues = setOf("MLB"), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01, daysAhead = 60)

    private val events = (0 until 6).map { i -> NovigEvent("e$i", "BASEBALL", "MLB", "OPEN_PREGAME", "Away $i @ Home $i", Fixtures.START_MS + i * 3_600_000L) }
    private val markets = (0 until 6).map { i ->
        NovigMarket("m$i", "e$i", "MONEY", "OPEN", "ML", Fixtures.START_MS + i * 3_600_000L, MarketFee.GAME, listOf(NovigOutcome("a$i", "Away $i", "TBD"), NovigOutcome("h$i", "Home $i", "TBD")))
    }

    /** Markets m0 and m1 bid 0.55 on both sides (a taker pays 0.45 for a 50% side: +EV) with a second level behind; the rest bid 0.48 (a taker pays 0.52). */
    private fun full(id: String): NovigBook {
        val i = id.drop(1).toInt()
        val levels = if (i < 2) listOf(BidLevel(550, 40), BidLevel(540, 1000)) else listOf(BidLevel(480, 1000), BidLevel(470, 1000))
        return NovigBook(id, 1, mapOf("a$i" to levels, "h$i" to levels), now)
    }

    private fun top(id: String): NovigBook = full(id).let { b -> b.copy(bidsByOutcome = b.bidsByOutcome.mapValues { (_, l) -> l.take(1) }, partial = true) }

    /**
     * A key with the best-bid feed: it holds every watched market as its best bid; [deepen] makes a market's complete book available after [deliverAfterPolls] asks (never, with a null).
     * REST reads give complete books.
     */
    private inner class Bbo(val deliverAfterPolls: Int? = 1) : NovigSource {
        val restCalls = ArrayList<List<String>>()
        val deepened = ArrayList<String>()
        private var held: Set<String> = emptySet()
        private val polls = HashMap<String, Int>()

        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            restCalls += marketIds.toList()
            val b = marketIds.associateWith { full(it) }
            return BookBatch(b, 0, b.size, 0, viaKey = b.size)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
        override fun watch(marketIds: Collection<String>) { held = marketIds.toSet() }
        override fun pushedTop(marketIds: Collection<String>): Map<String, NovigBook> = marketIds.filter { it in held }.associateWith { if (it in deepened && ready(it)) full(it) else top(it) }
        override fun pushed(marketIds: Collection<String>): Map<String, NovigBook> =
            marketIds.filter { it in held && it in deepened && ready(it) }.associateWith { full(it) }
        override fun deepen(marketIds: Collection<String>) { marketIds.forEach { if (it !in deepened) deepened += it } }
        private fun ready(id: String): Boolean {
            val n = deliverAfterPolls ?: return false
            return polls.merge(id, 1, Int::plus)!! > n
        }
    }

    private inner class Fair : ReferenceSource {
        override val id = "polymarket"
        override val displayName = "polymarket"
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot =
            RefSnapshot(
                league.oddsApiSportKey,
                (0 until 6).map { i ->
                    RefEvent(
                        "polymarket$i", league.oddsApiSportKey, events[i].startsTs, home = "Home $i", away = "Away $i",
                        markets = listOf(RefBookMarket("polymarket", "polymarket", LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, 2.0, null), RefQuote(Side.HOME, 2.0, null)), now)),
                    )
                },
                now,
            )
    }

    private fun scanner(n: NovigSource) = Scanner(n, clock = { now }, elapsed = { 0L })

    @Test
    fun `only the markets whose best price is +EV are moved to the full book, and their result has the complete ladder`() = runTest {
        val novig = Bbo()
        val r = scanner(novig).scan(settings, listOf(Fair()), onProgress = {}, onPartial = {})
        val result = r.result!!
        assertEquals("the +EV markets and no others", setOf("m0", "m1"), novig.deepened.toSet())
        for (id in listOf("m0", "m1")) {
            val os = result.opportunities.filter { it.market.marketId == id }
            assertTrue(id, os.isNotEmpty())
            assertTrue("a complete ladder for $id: ${os.map { it.ladder.size }}", os.all { it.ladder.size == 2 })
            assertTrue(os.all { it.bidLevels.size == 2 })
        }
        // the markets not worth a bet are priced on their best bid alone
        assertTrue(result.opportunities.filter { it.market.marketId == "m4" }.all { it.ladder.size == 1 })
        assertEquals("nothing was read one by one", 0, novig.restCalls.size)
    }

    @Test
    fun `the stake and depth of a +EV bet are those of the complete book, not of its top level`() = runTest {
        val novig = Bbo()
        val deep = scanner(novig).scan(settings, listOf(Fair()), onProgress = {}, onPartial = {}).result!!
        // The same scan on a feed with no best-bid channel: complete books for everything.
        val classic = object : NovigSource by Bbo() {
            private var held: Set<String> = emptySet()
            override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(marketIds.associateWith { full(it) }, 0, marketIds.size, 0)
            override fun watch(marketIds: Collection<String>) { held = marketIds.toSet() }
            override fun pushed(marketIds: Collection<String>) = marketIds.filter { it in held }.associateWith { full(it) }
        }
        val want = scanner(classic).scan(settings, listOf(Fair()), onProgress = {}, onPartial = {}).result!!
        fun key(o: Opportunity) = o.market.marketId to o.outcome.outcomeId
        val a = deep.opportunities.filter { it.market.marketId in setOf("m0", "m1") }.associateBy(::key)
        val b = want.opportunities.filter { it.market.marketId in setOf("m0", "m1") }.associateBy(::key)
        assertEquals(b.keys, a.keys)
        for (k in a.keys) {
            assertEquals("depth $k", b.getValue(k).depth, a.getValue(k).depth)
            assertEquals("stake $k", b.getValue(k).suggestedStake, a.getValue(k).suggestedStake)
            assertEquals("ladder $k", b.getValue(k).ladder, a.getValue(k).ladder)
        }
        assertTrue("the depth reaches past the top level (40 contracts)", a.values.any { (it.depth?.contracts ?: 0L) > 40L })
    }

    @Test
    fun `a feed that does not deliver the ladders in time leaves them to a read by request`() = runTest {
        val novig = Bbo(deliverAfterPolls = null)
        val r = scanner(novig).scan(settings, listOf(Fair()), onProgress = {}, onPartial = {})
        assertEquals(setOf("m0", "m1"), novig.restCalls.flatten().toSet())
        assertTrue(r.result!!.opportunities.filter { it.market.marketId in setOf("m0", "m1") }.all { it.ladder.size == 2 })
        assertFalse(novig.restCalls.flatten().any { it in setOf("m2", "m3", "m4", "m5") })
    }

    @Test
    fun `a market the bid desk has a bid on is moved to the full book whatever its price`() = runTest {
        val novig = Bbo()
        Scanner(novig, clock = { now }, elapsed = { 0L }, deepExtra = { listOf("m4") }).scan(settings, listOf(Fair()), onProgress = {}, onPartial = {})
        assertEquals(setOf("m0", "m1", "m4"), novig.deepened.toSet())
    }
}
