package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BetRating
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
 * Tj, 2026-10-11: every bet and bid with its odds, bet or bid, auto or manual, EV, time before the game, books behind the fair, sharp book, closing odds, CLV, result, liquidity, type, sport and star rating,
 * in files that can't grow too big, and a day-by-day archive.
 */
class BetHistoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val start = 1_790_000_000_000L
    private val now = start + 6 * 3_600_000L

    private fun at(
        how: String = AtBet.HOW_AUTO, kind: BetKind = BetKind.PROP, twoSided: Int? = 5, agreeing: Int? = 5, sharpVerdict: String? = "PASSED", stars: Double? = null, estimated: Boolean? = null,
        ev: Double = 0.04, minutes: Long = 90, available: Double? = 120.0,
    ) = AtBet(
        atMs = start - minutes * 60_000L, version = "0.89.17", how = how, scanner = "CNO", preset = "Volume + safe CLV", league = "NFL", sport = "FOOTBALL", kind = kind.name,
        minutesToStart = minutes, american = 105, otherAmerican = -125, available = available, ev = ev, twoSided = twoSided, agreeing = agreeing,
        sharpVerdict = sharpVerdict, sharpBook = "Pinnacle", sharpEv = 0.03, books = listOf(AtBetBook("Pinnacle", -112, -108, 0.49, 0.03)), stars = stars, starsEstimated = estimated,
    )

    private fun bet(
        id: String = "abcd1234", at: AtBet? = at(), status: BetStatus = BetStatus.WON, closeFair: Double? = 0.52, createdAt: Long = start - 90 * 60_000L, auto: Boolean = true, maker: Boolean = false,
        stake: Double = 2.0,
    ) = TrackedBet(
        id = "$id-0000-0000", createdAtMs = createdAt, league = "NFL", eventName = "Ravens @ Cowboys", startsTs = start, marketLabel = "Player Receptions", selection = "Joe Over 4.5",
        marketId = "m1", outcomeId = "o1", price = 0.488, cost = 0.488, fairAtBet = 0.508, evPercentAtBet = 0.04, stake = stake, status = status,
        settledAtMs = if (status == BetStatus.PENDING) null else now, closingFair = closeFair, closingSeenAtMs = closeFair?.let { start - 120_000L },
        source = BetTracker.SOURCE_CNO, american = 105, atBet = at, auto = auto, maker = maker,
    )

    private fun maker(betId: String? = null, status: MakerStatus = MakerStatus.FILLED, filled: Long = 200, id: String = "client-1") = MakerBid(
        clientId = id, marketId = "m1", eventId = "e1", outcomeId = "o1", league = "NFL", eventName = "Ravens @ Cowboys", startsTs = start, marketLabel = "Player Receptions",
        selection = "Joe Over 4.5", price = 0.48, contracts = 400, fair = 0.51, evAtFair = 0.06, margin = 0.03, books = 4, postedAtMs = start - 3 * 3_600_000L,
        status = status, filled = filled, paid = filled * 0.48 * 0.01, endedAtMs = start - 3_000_000L, betId = betId, firstFillAtMs = start - 3 * 3_600_000L + 40_000L, fairAtFill = 0.49,
    )

    // ---- the 14 facts ---------------------------------------------------------------------------------------

    @Test fun `a bet carries every fact Tj asked for`() {
        val r = BetHistory.row(bet(), null, now)
        assertEquals(105, r.odds)                       // 1 odds when placed
        assertEquals("bet", r.made)                     // 2 bet or bid
        assertEquals("auto", r.how)                     // 3 auto or manual
        assertEquals(0.04, r.ev!!, 1e-12)               // 4 EV when placed
        assertEquals(90L, r.leadMin)                    // 5 time before the game
        assertEquals(5, r.books)                        // 6 books behind the fair
        assertEquals(5, r.booksAgree)
        assertEquals("agree", r.sharp)                  // 7 sharp book
        assertEquals("Pinnacle", r.sharpBook)
        assertNotNull(r.closeOdds)                      // 8 closing odds (the fair price of the side at the start)
        assertEquals(0.52, r.closeFair!!, 1e-12)
        assertEquals("captured", r.closeSource!!.takeIf { it == ClosingLine.SOURCE_CAPTURED }?.let { "captured" })
        assertEquals(120L, r.closeLeadSec)
        assertEquals(0.52 / 0.488 - 1, r.clv!!, 1e-9)   // 9 CLV
        assertEquals(true, r.beatClose)
        assertEquals("won", r.result)                   // 10 won / lost / pushed
        assertEquals(120.0, r.liquidity!!, 1e-9)        // 11 liquidity
        assertEquals("Player props", r.kind)            // 12 type of bet
        assertEquals("football", r.sport)               // 13 sport
        assertNotNull(r.stars)                          // 14 rating
        assertEquals("estimated", r.starsBasis)         //   (this record predates stars: worked out from it)
    }

    @Test fun `a recorded rating is used as recorded and says so`() {
        val r = BetHistory.row(bet(at = at(stars = 4.5, estimated = false)), null, now)
        assertEquals(4.5, r.stars!!, 0.0)
        assertEquals("recorded", r.starsBasis)
    }

    @Test fun `lost, pushed and open bets say so and an open bet has no close or profit`() {
        assertEquals("lost", BetHistory.row(bet(status = BetStatus.LOST), null, now).result)
        assertEquals("push", BetHistory.row(bet(status = BetStatus.PUSH), null, now).result)
        val open = BetHistory.row(bet(status = BetStatus.PENDING, closeFair = null), null, start - 3_600_000L)
        assertEquals("open", open.result)
        assertNull(open.profit)
        assertNull(open.clv)
        assertNull(open.closeOdds)
    }

    @Test fun `a manual bet and a bid fill are told apart, and the bid carries its posting`() {
        assertEquals("manual", BetHistory.row(bet(auto = false, at = at(how = AtBet.HOW_SHEET)), null, now).how)
        val b = bet(id = "bid00001", auto = true, maker = true, at = at(how = AtBet.HOW_BID))
        val r = BetHistory.row(b, maker(betId = b.id), now)
        assertEquals("bid", r.made)
        assertEquals(40_000L, r.bid!!.fillDelayMs)
        assertEquals(0.06, r.bid!!.evAtFair!!, 1e-12)
        assertEquals(0.49 / 0.48 - 1, r.bid!!.fillEv!!, 1e-9)
    }

    @Test fun `an unknown fact is left out of the line, never written as zero`() {
        val r = BetHistory.row(bet(at = null, closeFair = null), null, now)
        val line = BetHistory.line(r)
        assertFalse(line, line.contains("\"books\""))
        assertFalse(line, line.contains("\"sharp\""))
        assertFalse(line, line.contains("\"stars\""))
        assertFalse(line, line.contains("\"liquidity\""))
        assertFalse(line, line.contains("\"clv\""))
        assertTrue(line.contains("\"made\":\"bet\""))
        assertFalse(line.contains("\n"))
    }

    @Test fun `a line is small and reads back`() {
        val line = BetHistory.line(BetHistory.row(bet(), null, now))
        assertTrue("${line.length} bytes", line.length < 1_200)
        val back = BetHistory.json.decodeFromString<BetHistory.Row>(line)
        assertEquals("abcd1234", back.id)
        assertEquals(105, back.odds)
    }

    @Test fun `the daily copy adds every book's price and the rules`() {
        val r = BetHistory.row(bet(), null, now, detail = true)
        assertTrue(r.detail!!.bookPrices.single().startsWith("Pinnacle: -112/-108"))
    }

    // ---- the rating estimate ------------------------------------------------------------------------------

    @Test fun `an older record's rating is worked out from what it holds`() {
        val strong = BetRating.estimate(at(twoSided = 6, agreeing = 6, sharpVerdict = "PASSED", ev = 0.05))!!
        val weak = BetRating.estimate(at(twoSided = 2, agreeing = 1, sharpVerdict = "NO_SHARP", ev = 0.01))!!
        val against = BetRating.estimate(at(twoSided = 6, agreeing = 3, sharpVerdict = "VETOED", ev = 0.04))!!
        assertTrue(strong.stars > weak.stars)
        assertTrue(against.stars <= 2.5)
        assertNull(BetRating.estimate(at().copy(ev = null)))
        assertEquals(4.5 to false, BetRating.of(at(stars = 4.5, estimated = false)))
    }

    // ---- dimensions and filters --------------------------------------------------------------------------

    @Test fun `each dimension names a bet's group`() {
        val b = bet()
        assertEquals("Auto", BetDims.valueOf(b, BetDim.HOW, now))
        assertEquals("Manual", BetDims.valueOf(bet(auto = false, at = at(how = AtBet.HOW_SHEET)), BetDim.HOW, now))
        assertEquals("Player props", BetDims.valueOf(b, BetDim.KIND, now))
        assertEquals("A sharp book agreed", BetDims.valueOf(b, BetDim.SHARP, now))
        assertEquals("4-5 books", BetDims.valueOf(b, BetDim.BOOKS, now))
        assertEquals("Every book agreed", BetDims.valueOf(b, BetDim.AGREE, now))
        assertEquals("$100-500", BetDims.valueOf(b, BetDim.LIQUIDITY, now))
        assertEquals("Beat the close", BetDims.valueOf(b, BetDim.CLOSE, now))
        assertEquals("Won", BetDims.valueOf(b, BetDim.RESULT, now))
        assertEquals("No true close yet", BetDims.valueOf(bet(closeFair = null), BetDim.CLOSE, now))
        assertEquals(BetDims.UNRATED, BetDims.valueOf(bet(at = null), BetDim.STARS, now))
    }

    @Test fun `a filter keeps the bets in a chosen group of every dimension it names`() {
        val bets = listOf(bet("a0000001"), bet("a0000002", at = at(sharpVerdict = "VETOED"), status = BetStatus.LOST), bet("a0000003", at = at(kind = BetKind.MONEYLINE)))
        val sharpAgreed = BetFilterSpec.of(BetDim.SHARP, "A sharp book agreed")
        assertEquals(2, sharpAgreed.apply(bets, now).size)
        val props = sharpAgreed.with(BetDim.KIND, setOf("Player props"))
        assertEquals(listOf("a0000001"), props.apply(bets, now).map { it.id.take(8) })
        assertEquals(3, BetFilterSpec.ALL.apply(bets, now).size)
        assertEquals(1, props.with(BetDim.KIND, emptySet()).keep.size)
        assertEquals("All bets", BetFilterSpec.ALL.label())
        assertTrue(props.label().contains("Player props"))
    }

    @Test fun `the smart choices list the fixed ones and the best and worst groups once there are bets enough`() {
        // 40 prop bets that beat the close and 40 moneylines that give it away.
        val good = (1..40).map { bet("g%07d".format(it), at = at(kind = BetKind.PROP), closeFair = 0.50 + (it % 5) * 0.004, createdAt = start - it * 60_000L) }
        val bad = (1..40).map { bet("b%07d".format(it), at = at(kind = BetKind.MONEYLINE), closeFair = 0.45 + (it % 5) * 0.004, createdAt = start - it * 61_000L) }
        val choices = BetSmartFilters.choices(good + bad, now)
        assertTrue(choices.any { it.title == "Everything" })
        assertTrue(choices.any { it.title == "Player props" && it.n == 40 })
        assertTrue(choices.any { it.title.startsWith("Best:") })
        assertTrue(choices.any { it.title.startsWith("Worst:") })
        val (best, worst) = BetSmartFilters.findings(good + bad, now)
        assertTrue(best.first().meanClv > 0 && worst.first().meanClv < 0)
        // Too few closed bets: no findings.
        assertTrue(BetSmartFilters.findings(good.take(5), now).first.isEmpty())
    }

    // ---- the export -------------------------------------------------------------------------------------

    private fun many(n: Int): List<TrackedBet> = (0 until n).map { i ->
        bet("e%07d".format(i), at = at(kind = if (i % 3 == 0) BetKind.PROP else BetKind.MONEYLINE, twoSided = 2 + i % 6, agreeing = 1 + i % 2, ev = 0.01 + (i % 7) * 0.006),
            status = if (i % 5 == 0) BetStatus.LOST else BetStatus.WON, closeFair = 0.47 + (i % 11) * 0.01, createdAt = start - (i + 30L) * 60_000L, auto = i % 4 != 0)
    }

    @Test fun `a big history is split into parts that stay under the limit, one line per bet, with a README`() {
        val bets = many(3_000)
        val dir = tmp.newFolder("x")
        val r = BetHistoryExport.write(dir, "vigilant-bets-test", bets, emptyList(), sequenceOf(BetHistory.line(BetHistory.bidRow(maker(), "football"))), now, partBytes = 150_000)
        assertTrue("parts: ${r.betParts.size}", r.betParts.size >= 5)
        r.betParts.forEach { assertTrue("${it.name} ${it.length()}", it.length() <= 150_000 + 1_200) }
        assertEquals(3_000, r.betParts.sumOf { it.readLines().size })
        assertEquals(3_000, r.bets)
        assertEquals(1, r.bids)
        // Oldest first across the parts.
        val firsts = r.betParts.flatMap { f -> f.readLines().map { BetHistory.json.decodeFromString<BetHistory.Row>(it).placedMs } }
        assertEquals(firsts.sorted(), firsts)
        val readme = r.readme.readText()
        listOf("# Vigilant bet history", "## Your task", "## Definitions", "## Headline", "## Splits", "### Star rating", "### Sharp book", "### Type of bet", "## Automatic findings", "## Bids posted", "## Data quality")
            .forEach { assertTrue("README lacks $it", readme.contains(it)) }
        assertTrue(readme.contains(r.betParts.first().name))
        assertTrue(r.readme.length() < 200_000)
    }

    @Test fun `the range limits the bets and a rerun replaces the old parts`() {
        val bets = many(400)
        val dir = tmp.newFolder("y")
        val all = BetHistoryExport.write(dir, "s", bets, emptyList(), emptySequence(), now, partBytes = 40_000)
        val recent = BetHistoryExport.write(dir, "s", bets, emptyList(), emptySequence(), now, fromMs = start - 100 * 60_000L, partBytes = 40_000)
        assertTrue(recent.bets < all.bets)
        assertEquals(recent.betParts.map { it.name }.toSet(), dir.listFiles { f -> f.name.contains("-bets-p") }!!.map { it.name }.toSet())
    }

    @Test fun `an empty history still writes a readable README`() {
        val r = BetHistoryExport.write(tmp.newFolder("z"), "e", emptyList(), emptyList(), emptySequence(), now)
        assertEquals(0, r.bets)
        assertTrue(r.readme.readText().contains("0 bets"))
    }

    @Test fun `the estimate of a share's size is in step with real lines`() {
        val bets = many(500)
        val r = BetHistoryExport.write(tmp.newFolder("w"), "q", bets, emptyList(), emptySequence(), now)
        val perBet = r.betParts.sumOf { it.length() } / 500
        assertTrue("real $perBet B a bet vs assumed ${BetHistoryExport.BYTES_PER_BET}", perBet <= BetHistoryExport.BYTES_PER_BET)
    }

    // ---- the day-by-day archive ----------------------------------------------------------------------------

    @Test fun `the archive writes a file a day, rewrites only what changed and tells what to copy out`() {
        val a = BetArchive(tmp.newFolder("arch"))
        val bets = many(60)
        val first = a.sync(bets, emptyList(), now, full = true)
        assertTrue(first.changed.isNotEmpty())
        val files = a.pendingMirror()
        assertEquals(first.changed.map { it.name }.sorted(), files.map { it.name }.sorted())
        files.forEach { a.markMirrored(it) }
        assertTrue(a.pendingMirror().isEmpty())
        // Nothing changed: nothing rewritten, nothing to copy.
        assertTrue(a.sync(bets, emptyList(), now, full = true).changed.isEmpty())
        // One bet settles: its day is rewritten and offered again, the others are not.
        val one = bets.first().copy(status = BetStatus.LOST)
        val second = a.sync(listOf(one) + bets.drop(1), emptyList(), now, full = true)
        assertEquals(1, second.changed.size)
        assertEquals(1, a.pendingMirror().size)
        // Every line in a day file is a full row with its detail.
        val line = a.betFile(BetHistory.day(bets.first().createdAtMs)).readLines().first()
        assertTrue(BetHistory.json.decodeFromString<BetHistory.Row>(line).detail != null)
    }

    @Test fun `bids are kept after the desk forgets them, and each is listed once`() {
        val a = BetArchive(tmp.newFolder("arch2"))
        val filled = maker(id = "client-A")
        val cancelled = maker(id = "client-B", status = MakerStatus.CANCELED, filled = 0)
        val resting = maker(id = "client-C", status = MakerStatus.RESTING, filled = 0).copy(endedAtMs = null)
        a.sync(emptyList(), listOf(filled, cancelled, resting), now)
        // The resting bid is not archived until it ends; the others are.
        val archived = a.bidLines(emptyList()).map { BetHistory.json.decodeFromString<BetHistory.BidRow>(it).clientId }.toSet()
        assertEquals(setOf("client-A", "client-B"), archived)
        // Days later the desk has forgotten them all; the live list now holds the ended resting bid.
        val ended = resting.copy(status = MakerStatus.EXPIRED, endedAtMs = start)
        a.sync(emptyList(), listOf(ended), now)
        val all = a.bidLines(listOf(ended)).map { BetHistory.json.decodeFromString<BetHistory.BidRow>(it).clientId }.toList()
        assertEquals(listOf("client-A", "client-B", "client-C").sorted(), all.sorted())
        assertEquals(all.size, all.toSet().size)
        // A still-active bid is listed from the live list.
        val live = maker(id = "client-D", status = MakerStatus.RESTING, filled = 0).copy(endedAtMs = null)
        assertTrue(a.bidLines(listOf(live)).any { it.contains("client-D") })
        // The range keeps only recent bids.
        assertTrue(a.bidLines(emptyList(), fromMs = start + 1).none())
    }

    @Test fun `a million-dollar-sized history is written without holding it`() {
        // 30,000 bets, written in parts of 1 MB: the writer's memory is a line, so this finishes quickly and every part is bounded.
        val bets = many(30_000)
        val dir = tmp.newFolder("big")
        val t0 = System.nanoTime()
        val r = BetHistoryExport.write(dir, "big", bets, emptyList(), emptySequence(), now, partBytes = 1_000_000)
        val secs = (System.nanoTime() - t0) / 1e9
        assertEquals(30_000, r.betParts.sumOf { f -> f.useLines { it.count() } })
        r.betParts.forEach { assertTrue(it.length() <= 1_000_000 + 1_200) }
        assertTrue("took $secs s", secs < 60)
    }

    @Suppress("unused")
    private fun nothing(f: File) = f
}
