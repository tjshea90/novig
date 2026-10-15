package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.trading.maker.BidCandidates
import com.tjshea.vigilant.data.novig.trading.maker.MakerDecision
import com.tjshea.vigilant.data.novig.trading.maker.MakerLine
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerQuote
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bid desk's data on GitHub (Tj, 2026-10-15): the fair side of every bid decision is made by the engine ([BidCandidates]), the Novig side by the phone ([CloudBidLane]), and the desk itself is unchanged.
 * The proof is one equivalence: for one priced board and the same Novig books, what the desk decides from the candidates equals what it decides from the whole local scan.
 */
class CloudBidsTest {
    private val now = Fixtures.START_MS - 2 * 3_600_000L
    private val settings = ScanSettings(leagues = setOf("NFL"), makerFocus = BidFocus.LOW_USAGE, maker = true, makerStakeMode = AutoBetStake.CUSTOM, makerStake = 5.0)
    private val rules = MakerRules.of(settings)
    private val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)

    private val players = listOf("Lamar Jackson", "Dak Prescott", "Zay Flowers", "CeeDee Lamb")
    private fun market(i: Int) = NovigMarket(
        "p$i", Fixtures.EVENT_ID, "PASSING_YARDS", "OPEN", "${players[i]} 224.5 PASSING_YARDS", Fixtures.START_MS, MarketFee.GAME,
        listOf(NovigOutcome("o$i", "Over 224.5", "TBD"), NovigOutcome("u$i", "Under 224.5", "TBD")),
    )

    private fun quote(i: Int, key: String, title: String, over: Double, under: Double, ageMs: Long = 60_000) = RefBookMarket(
        key, title, LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, over, 224.5), RefQuote(Side.UNDER, under, 224.5)), now - ageMs, 0, players[i], "PASSING_YARDS",
    )

    private fun books(at: Long) = players.indices.associate { i ->
        "p$i" to NovigBook("p$i", 1, mapOf("o$i" to listOf(BidLevel(470, 10_000), BidLevel(460, 5_000)), "u$i" to listOf(BidLevel(500, 10_000))), at)
    }

    /** Four props through the real planner and pricer: an ordinary one, a second, a long shot nobody would bid on, and a player who is out. */
    private fun board(bookAt: Long = now): ScanResult {
        val e = settings.effective()
        val quotes = listOf(
            quote(0, "kalshi", "Kalshi", 1.95, 1.95) to quote(0, "prophetx", "ProphetX", 1.90, 2.00),
            quote(1, "kalshi", "Kalshi", 1.85, 2.05) to quote(1, "prophetx", "ProphetX", 1.88, 2.02),
            quote(2, "kalshi", "Kalshi", 1.25, 4.20) to quote(2, "prophetx", "ProphetX", 1.26, 4.10),
            quote(3, "kalshi", "Kalshi", 1.95, 1.95) to quote(3, "prophetx", "ProphetX", 1.90, 2.00),
        ).flatMap { it.toList() }
        val refs = listOf(RefSnapshot("americanfootball_nfl", listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, "Dallas Cowboys", "Baltimore Ravens", quotes)), now))
        val plan = Planner.plan(listOf(event), players.indices.map(::market), refs, e, now)
        return Pricing.price(plan, books(bookAt), e, now)
    }

    private val outPlayer = { o: Opportunity -> if (o.market.marketId == "p3") "The player is out (Out): no bet or bid on a player who isn't playing" else null }

    /** A pass of the desk's decisions, reduced to what a bid is: which side, at what price, for how many contracts. */
    private fun posts(d: List<MakerDecision>) = d.filterIsInstance<MakerDecision.Post>().map { Triple(it.line.outcomeId, it.price, it.contracts) }.sortedBy { it.first }

    private class FakeNovig(private val books: Map<String, NovigBook>) : NovigSource {
        val asked = ArrayList<String>()
        val watched = ArrayList<String>()
        var fail: String? = null
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = emptyList<NovigEvent>()
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = emptyList<NovigMarket>()
        override suspend fun market(marketId: String): NovigMarket? = null
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            asked += marketIds
            val err = fail
            if (err != null) throw java.io.IOException(err)
            val got = marketIds.mapNotNull { id -> books[id]?.let { id to it } }.toMap()
            return BookBatch(got, notModified = 0, fetched = got.size, failed = marketIds.size - got.size)
        }
        override fun watch(marketIds: Collection<String>) { watched += marketIds }
    }

    private fun viaGithub(result: ScanResult, novigBooks: Map<String, NovigBook>, at: Long, lane: CloudBidLane = CloudBidLane(FakeNovig(novigBooks), clock = { at }), keep: Set<String> = emptySet()): List<MakerLine> = runBlockingTest {
        val sent = BidCandidates.from(result, settings, at, outPlayer)
        // across the wire: encoded, compressed, read again
        val got = CloudBids.decode(CloudBids.encode(CloudBids(at, false, 7L, sent)))!!
        lane.put(got)
        lane.refresh(at, 1_000)
        lane.lines(at, rules, keep)
    }

    private fun <T> runBlockingTest(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

    // ---- the equivalence ---------------------------------------------------------------------------------------------------

    @Test
    fun `the desk decides the same bids from GitHub's candidates and the phone's Novig books as from the whole local scan`() {
        val result = board()
        val local = MakerQuote.decideAll(MakerLines.from(result, settings, now, unavailable = outPlayer), rules, now, emptySet())
        // the phone's own books are newer than the engine's: the engine's side is replaced by them, as it is on the phone
        val fresh = books(now + 5_000)
        val lines = viaGithub(result, fresh, now + 5_000)
        val cloud = MakerQuote.decideAll(lines, rules, now + 5_000, emptySet())
        val localNew = MakerQuote.decideAll(MakerLines.from(Pricing.price(Planner.plan(listOf(event), players.indices.map(::market), board().let { emptyList<RefSnapshot>() }, settings.effective(), now), fresh, settings.effective(), now + 5_000), settings, now + 5_000, outPlayer), rules, now + 5_000, emptySet())
        assertTrue("some bids go up from the ordinary props: ${posts(local)}", posts(local).isNotEmpty())
        assertEquals("same bids, same prices, same size", posts(local), posts(cloud))
        assertEquals("(no refs, no lines: the local scan with new books and no fair has nothing to bid on)", emptyList<Any>(), posts(localNew))
        // every side the candidates leave out is a skip on the local scan
        val candidateIds = BidCandidates.from(result, settings, now, outPlayer).map { it.line.outcomeId }.toSet()
        for (d in local) if (d.line.outcomeId !in candidateIds) assertTrue("${d.line.outcomeId} was left out but the desk would bid on it", d !is MakerDecision.Post)
        assertTrue("the candidates are fewer than the sides priced", candidateIds.size < result.opportunities.size)
        assertFalse("a player who is out is no candidate", "o3" in candidateIds)
    }

    @Test
    fun `Novig's side of a candidate is replaced by a newer book, equal to the pricer's own and the same four fields`() {
        val result = board()
        val newer = books(now + 30_000)
        val line = BidCandidates.from(result, settings, now, outPlayer).first { it.line.outcomeId == "o0" }.line
        val repriced = MakerLines.withBook(line, newer.getValue("p0"))
        // the pricer's own arithmetic for the same book
        val e = settings.effective()
        val plan = Planner.plan(listOf(event), players.indices.map(::market), listOf(RefSnapshot("americanfootball_nfl", listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, "Dallas Cowboys", "Baltimore Ravens", listOf(quote(0, "kalshi", "Kalshi", 1.95, 1.95), quote(0, "prophetx", "ProphetX", 1.90, 2.00)))), now)), e, now)
        val direct = Pricing.price(plan, newer, e, now + 30_000).opportunities.first { it.outcome.outcomeId == "o0" }
        assertEquals(direct.quote?.price, repriced.offer)
        assertEquals(direct.bestBid, repriced.bestBid)
        assertEquals(direct.bidLevels, repriced.bidLevels)
        assertEquals(direct.bookFetchedAtMs, repriced.bookAtMs)
    }

    @Test
    fun `a market Novig has closed or put in play has nothing to take`() {
        val line = BidCandidates.from(board(), settings, now, outPlayer).first().line
        val book = books(now + 1_000).getValue(line.marketId)
        val closed = book.copy(lifecycle = com.tjshea.vigilant.data.novig.stream.MarketLifecycle(closed = true, live = false, atMs = now))
        assertNull(MakerLines.withBook(line, closed).offer)
        val inPlay = book.copy(lifecycle = com.tjshea.vigilant.data.novig.stream.MarketLifecycle(closed = false, live = true, atMs = now))
        assertNull(MakerLines.withBook(line, inPlay).offer)
        assertNotNull(MakerLines.withBook(line, book).offer)
    }

    // ---- what is a candidate ------------------------------------------------------------------------------------------------

    @Test
    fun `a candidate is a side that passes every check that does not need Novig's book, and carries what the desk needs`() {
        val result = board()
        val c = BidCandidates.from(result, settings, now, outPlayer)
        val ids = c.map { it.line.outcomeId }
        assertTrue(ids.toString(), "o0" in ids && "u0" in ids)
        assertFalse("a long shot is outside every bid's price window", "o2" in ids)
        // soonest game first, the order the phone reads their books in
        assertEquals(c.map { it.line.startsTs }, c.map { it.line.startsTs }.sorted())
        val l = c.first { it.line.outcomeId == "o0" }.line
        assertTrue("the books' own fairs travel with the line", l.bookFairs.isNotEmpty() && l.sharpFairs.isNotEmpty())
        assertEquals(setOf("Kalshi", "ProphetX"), l.fairBooks.toSet())
        assertEquals(Fixtures.START_MS, c.first().eventStartsTs)
    }

    @Test
    fun `a bids scan with live games, other leagues or no fair gives no candidates`() {
        val none = BidCandidates.from(null, settings, now)
        assertTrue(none.isEmpty())
        assertTrue(BidCandidates.from(board(), settings.copy(leagues = setOf("NBA")), now).isEmpty())
    }

    // ---- the wire ---------------------------------------------------------------------------------------------------------

    @Test
    fun `a candidate list survives the wire whole, and a file this version cannot read is no list`() {
        val sent = BidCandidates.from(board(), settings, now, outPlayer)
        val got = CloudBids.decode(CloudBids.encode(CloudBids(now, true, 9L, sent)))!!
        assertEquals(now, got.atMs)
        assertTrue(got.partial)
        assertEquals(9L, got.configRev)
        assertEquals(0, got.unreadable)
        assertEquals(sent.map { it.line.copy(fairOld = false) }, got.lines.map { it.line })
        assertEquals(sent.map { it.eventStartsTs }, got.lines.map { it.eventStartsTs })
        assertNull(CloudBids.decode("not gzip".toByteArray()))
        // one unreadable line is skipped and counted, not the whole list
        val text = java.util.zip.GZIPInputStream(CloudBids.encode(CloudBids(now, false, 1L, sent)).inputStream()).readBytes().toString(Charsets.UTF_8)
        val broken = text.replaceFirst("\"kd\":\"PROP\"", "\"kd\":\"NO_SUCH_KIND\"")
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(broken.toByteArray(Charsets.UTF_8)) }
        val partial = CloudBids.decode(out.toByteArray())!!
        assertEquals(1, partial.unreadable)
        assertEquals(sent.size - 1, partial.lines.size)
    }

    // ---- the phone's lane ---------------------------------------------------------------------------------------------------

    @Test
    fun `the lane reads books only for candidates whose Novig side is old, soonest first, and says how many are ready`() = runTest {
        val result = board()
        val novig = FakeNovig(books(now + 1_000))
        val lane = CloudBidLane(novig, clock = { now })
        val sent = BidCandidates.from(result, settings, now, outPlayer)
        lane.put(CloudBids(now, false, 1L, sent))
        assertTrue("the stream is told which markets to watch", novig.watched.isNotEmpty())
        // the engine's books are as old as the list: nothing to read yet
        assertEquals(0, lane.refresh(now, 100))
        assertTrue(novig.asked.isEmpty())
        assertEquals(sent.size to sent.size, lane.readiness(now))
        // two minutes on they are older than REFRESH_AFTER_MS: each market once, at most maxReads
        val later = now + 120_000
        assertEquals(1, lane.refresh(later, 1))
        assertEquals(1, novig.asked.size)
        assertEquals(sent.map { it.line.marketId }.distinct().first(), novig.asked.single())
        assertTrue(lane.refresh(later, 100) >= 1)
        assertEquals("every market asked for once, no more", novig.asked.toSet().size, novig.asked.size)
        // a failed read leaves the engine's book in place, says why, and the lines are still judged by their age
        novig.fail = "HTTP 429"
        assertEquals(0, lane.refresh(later + 200_000, 100))
        assertEquals("HTTP 429", lane.problem)
    }

    @Test
    fun `a line is judged at the phone's own clock - an old book or an old fair leaves it out, and a side with a bid is kept from the last list`() {
        val result = board()
        val sent = BidCandidates.from(result, settings, now, outPlayer)
        val lane = CloudBidLane(FakeNovig(emptyMap()), clock = { now })
        lane.put(CloudBids(now, false, 1L, sent))
        assertEquals(sent.size, lane.lines(now, rules).size)
        // the book is 20 minutes old and no newer one came: no line (the same rule as MakerLines.from)
        assertTrue(lane.lines(now + MakerLines.MAX_BOOK_AGE_MS + 1_000, rules).isEmpty())
        // the fair gets old with the game near: the line is still there, marked old, and the desk skips it
        val l = lane.lines(now + 10 * 60_000, rules).first()
        assertEquals(sent.first { it.line.outcomeId == l.outcomeId }.line.fairAsOfMs, l.fairAsOfMs)
        // a newer list without a side the phone has a bid on: its last line is kept so the bid's fill can still be judged
        val mine = setOf(sent.first().line.outcomeId)
        lane.lines(now, rules, keep = mine)
        lane.put(CloudBids(now + 1_000, false, 1L, sent.drop(1)))
        val kept = lane.lines(now + 1_000, rules, keep = mine)
        assertTrue(kept.any { it.outcomeId in mine })
        // and let go of it the moment no bid asks for it
        assertFalse(lane.lines(now + 1_000, rules, keep = emptySet()).any { it.outcomeId in mine })
        // an older list never replaces a newer one
        lane.put(CloudBids(now - 5_000, false, 1L, sent))
        assertEquals(now + 1_000, lane.current?.atMs)
    }

    @Test
    fun `with no candidate list yet there are no lines`() {
        val lane = CloudBidLane(FakeNovig(emptyMap()), clock = { now })
        assertTrue(lane.lines(now, rules).isEmpty())
        assertEquals(0 to 0, lane.readiness(now))
    }
}
