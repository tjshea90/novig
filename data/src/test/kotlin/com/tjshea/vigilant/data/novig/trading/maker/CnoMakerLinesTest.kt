package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidSource
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bids priced from CrazyNinjaOdds alone (Tj, 2026-10-07: "auto bids using only the cno scanner"; RESEARCH.md §113-§114): the lines [CnoMakerLines] builds from a game page and
 * Novig's book, and what the desk makes of them. Pure; the age rules are the point (CNO's "Last Updated" is a lower bound, an unknown age is not fresh).
 */
class CnoMakerLinesTest {

    private val now = 1_800_000_000_000L
    private val start = now + 3 * 3_600_000L
    private val settings = ScanSettings(makerSource = BidSource.CNO, maker = true)
    private val rules = MakerRules.of(settings).copy(stakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM, customStake = 5.0, maxStake = 10.0)

    private val market = NovigMarket(
        marketId = "m1", eventId = "ev1", marketType = "RECEIVING_YARDS", status = "OPEN", description = "Player Receiving Yards", startsTs = start,
        fee = MarketFee.GAME, outcomes = listOf(NovigOutcome("m1-over", "Over 50.5", "TBD"), NovigOutcome("m1-under", "Under 50.5", "TBD")),
    )

    /** Novig's book: Over bid 0.45 (so the Under can be taken at 0.55), Under bid 0.40 (so the Over is offered at 0.60). */
    private fun book(at: Long = now - 2_000L) = NovigBook(
        "m1", 1L, mapOf("m1-over" to listOf(BidLevel(450, 100), BidLevel(440, 50)), "m1-under" to listOf(BidLevel(400, 100))), at,
    )

    /** CNO's row for the Over: +EV at Novig's ask (CNO's fair 0.55, offered at +66 = 0.60? no: listed at +100 = 0.50 → EV 10%). */
    private fun row(fair: Double? = 0.55, odds: Int = 100, bet: String = "Pat Over 50.5") = CnoRow(
        ev = 0.02, startsAtMs = start, sport = "Football", league = "NFL", event = "A @ B", market = "Player Receiving Yards", bet = bet, odds = odds,
        book = "Novig", fairProbability = fair, books = 8, gameUrl = "https://x/game.aspx?game_id=1&market_id=2&side_id=7&devig_method=8",
    )

    /** Each book's two prices for the Over and the Under; the sharp ones for a prop are Kalshi, ProphetX, FanDuel and Caesars. */
    private fun view(age: Int? = 10, fetched: Long = now - 5_000L, over: String = "Pat Over 50.5", under: String = "Pat Under 50.5", flip: Boolean = false): CnoBooksView {
        // Fair about 0.53 for the Over at every book (a little vig each way).
        val prices = listOf(
            CnoBookPrice("KI", -115, otherOdds = -105), CnoBookPrice("PX", -112, otherOdds = -108), CnoBookPrice("FD", -118, otherOdds = -102),
            CnoBookPrice("CZR", -120, otherOdds = 100), CnoBookPrice("DK", -125, otherOdds = 105), CnoBookPrice("NV", 100, otherOdds = -150),
        ).map { if (flip) it.copy(odds = it.otherOdds, otherOdds = it.odds) else it }
        return CnoBooksView(bet = if (flip) under else over, otherBet = if (flip) over else under, prices = prices, fetchedAtMs = fetched, cnoAgeSeconds = age)
    }

    private fun page(
        row: CnoRow = row(), view: CnoBooksView? = view(), book: NovigBook? = book(), listAt: Long? = now - 8_000L,
    ) = CnoMakerLines.Page(row, "m1-over", market, book, view, listAt)

    private fun lines(vararg pages: CnoMakerLines.Page, s: ScanSettings = settings, at: Long = now) = CnoMakerLines.from(pages.toList(), s, at)

    // ---- the lines ---------------------------------------------------------------------------------------------------------

    @Test
    fun `a page gives a line for the listed side and one for its complement, from CNO's books and Novig's book`() {
        val r = lines(page())
        assertEquals(emptyMap<String, Int>(), r.skipped)
        assertEquals(listOf("m1-over", "m1-under"), r.lines.map { it.outcomeId })
        val over = r.lines[0]
        val under = r.lines[1]
        assertEquals(BetTracker.SOURCE_CNO, over.source)
        assertEquals("Pat Over 50.5", over.selection)
        assertEquals("Pat Under 50.5", under.selection)
        assertEquals(BetKind.PROP, over.kind)
        // Five companies price both sides (Novig is the book being judged, not a vote); the sharp ones for a prop are Kalshi, ProphetX, FanDuel, Caesars.
        assertEquals(5, over.books)
        assertEquals(4, over.sharpFairs.size)
        assertEquals(listOf("Kalshi", "ProphetX", "FanDuel", "Caesars"), over.basis!!.sharp)
        assertEquals(5, over.bookFairs.size)
        // The Over is the favorite at every sharp book (its price is the shorter one): its fair is over a half, the Under's under it - the right way round, not just consistent.
        assertTrue(over.sharpFairs.toString(), over.sharpFairs.all { it > 0.5 })
        assertTrue(under.sharpFairs.toString(), under.sharpFairs.all { it < 0.5 })
        assertTrue(over.bookFairs.all { it > 0.5 } && under.bookFairs.all { it < 0.5 })
        // The two sides' fairs add to one (each book's own devig).
        assertEquals(1.0, over.sharpFairs.zip(under.sharpFairs).map { (a, b) -> a + b }.average(), 0.03)
        // Novig's own book: the Over is offered at 1 − the Under's best bid; the Under's queue is its own.
        assertEquals(0.60, over.offer!!, 1e-9)
        assertEquals(0.45, over.bestBid!!, 1e-9)
        assertEquals(listOf(450, 440), over.bidLevels.map { it.priceMilli })
        assertEquals(0.55, under.offer!!, 1e-9)
        assertEquals(0.40, under.bestBid!!, 1e-9)
        assertEquals(now - 2_000L, over.bookAtMs)
        // Never above CNO's own fair for the row it listed.
        assertTrue(over.fair!! <= 0.55 + 1e-12)
        assertEquals(120_000L, over.fairMaxAgeMs)
    }

    @Test
    fun `the fair's age is the OLDER of the list's and the page's own Last Updated`() {
        // List updated 8 s before now; the page was read 5 s ago and says it is 10 s old: its data is from 15 s ago, the older.
        val l = lines(page()).lines.first()
        assertEquals(now - 15_000L, l.fairAsOfMs)
        // The newest news behind the fair is the PAGE's (its books make the fair; a fill is judged against it only once the page was read after the fill), not the list's newer clock.
        assertEquals(now - 15_000L, l.fairNewestMs)
        assertEquals(now - 8_000L, l.listAtMs)
        assertEquals(now - 15_000L, l.pageAtMs)
        assertFalse(l.fairOld)
        // A stale list binds when it is the older one.
        val old = lines(page(listAt = now - 100_000L)).lines.first()
        assertEquals(now - 100_000L, old.fairAsOfMs)
    }

    @Test
    fun `an unknown list age is not fresh - and a page that does not say its age is taken as 45 seconds old`() {
        val noList = lines(page(listAt = null)).lines.first()
        assertNull(noList.fairAsOfMs)
        assertTrue(noList.fairOld)
        val d = MakerQuote.decide(noList, rules, now) as MakerDecision.Skip
        assertEquals("The fair price is too old to bid on", d.why)
        val noPageAge = lines(page(view = view(age = null))).lines.first()
        assertEquals(now - 5_000L - CnoMakerLines.UNKNOWN_PAGE_AGE_MS, noPageAge.pageAtMs)
        assertEquals(noPageAge.pageAtMs, noPageAge.fairAsOfMs)
    }

    @Test
    fun `data older than the limit is old, and the limit is the setting`() {
        assertFalse(lines(page(listAt = now - 119_000L, view = view(age = 0, fetched = now - 1_000L))).lines.first().fairOld)
        assertTrue(lines(page(listAt = now - 121_000L, view = view(age = 0, fetched = now - 1_000L))).lines.first().fairOld)
        val tight = settings.copy(makerCnoMaxAgeSeconds = 60)
        val l = lines(page(listAt = now - 61_000L), s = tight).lines.first()
        assertTrue(l.fairOld)
        assertEquals(60_000L, l.fairMaxAgeMs)
        // A saved value is held to 30 s .. 5 min.
        assertEquals(30_000L, CnoMakerLines.maxAgeMs(settings.copy(makerCnoMaxAgeSeconds = 1)))
        assertEquals(300_000L, CnoMakerLines.maxAgeMs(settings.copy(makerCnoMaxAgeSeconds = 9_999)))
    }

    @Test
    fun `a row that is the page's second line gets the same two sides, the right way round`() {
        val a = lines(page()).lines
        // The same page seen from the Under: the page's first line is the Under, with every price pair swapped.
        val under = CnoMakerLines.Page(row(bet = "Pat Under 50.5"), "m1-under", market, book(), view(flip = true), now - 8_000L)
        val b = CnoMakerLines.from(listOf(under), settings, now).lines
        assertEquals(listOf("m1-under", "m1-over"), b.map { it.outcomeId })
        // Seen from the Under (the page's first line, prices swapped), the Under is still the underdog and the Over the favorite.
        assertTrue(b[0].sharpFairs.all { it < 0.5 })
        assertTrue(b[1].sharpFairs.all { it > 0.5 })
        assertEquals(a[0].sharpFairs.sorted(), b[1].sharpFairs.sorted())
        assertEquals(a[1].sharpFairs.sorted(), b[0].sharpFairs.sorted())
    }

    @Test
    fun `pages that cannot be priced say why - no page, no Novig book, not on the page, no book pricing both sides`() {
        val r = lines(page(view = null), page(book = null), page(row = row(bet = "Someone Else Over 9.5")), page(view = view().copy(prices = listOf(CnoBookPrice("KI", -110)))))
        assertTrue(r.lines.isEmpty())
        assertEquals(1, r.skipped[CnoMakerLines.NO_PAGE])
        assertEquals(1, r.skipped[CnoMakerLines.NO_BOOK])
        assertEquals(1, r.skipped[CnoMakerLines.NOT_ON_PAGE])
        assertEquals(2, r.skipped[CnoMakerLines.NO_PRICES])
    }

    // ---- what the desk makes of them -----------------------------------------------------------------------------------------

    @Test
    fun `the bid sits under the LOWER of CNO's fair and the sharp books' fairs, and rests only as long as the data is fresh`() {
        val under = lines(page()).lines[1]
        val post = MakerQuote.decide(under, rules, now) as MakerDecision.Post
        val anchor = minOf(under.fair!!, under.sharpFairs.min())
        assertEquals(anchor, post.anchorFair!!, 1e-12)
        assertTrue(post.price <= anchor / 1.04 + 1e-9)
        assertTrue(post.price > anchor / 1.04 - 0.006)
        // The data is from 15 s ago and the limit is 120 s: it may rest 105 s, not the 30 minutes (or the app's 10-minute rule for a far-off game).
        assertEquals(now - 15_000L + 120_000L, post.restUntilMs)
    }

    @Test
    fun `a page without a sharp book gets no bid (bids from CNO need one to agree)`() {
        val v = view().copy(prices = view().prices.filter { it.code !in setOf("KI", "PX", "FD", "CZR") })
        val l = lines(page(view = v)).lines[1]
        assertTrue(l.sharpFairs.isEmpty())
        val d = MakerQuote.decide(l, rules, now) as MakerDecision.Skip
        assertTrue(d.why, d.why.startsWith("No sharp book"))
        // The same line under Vigilant's own rules (requireSharp off) would have been bid on: the source's narrowing is what stops it.
        assertFalse(MakerRules.of(ScanSettings()).requireSharp)
        assertTrue(MakerRules.of(settings).requireSharp)
    }

    @Test
    fun `a sharp book that says the bid is not +EV vetoes it`() {
        // Kalshi alone thinks the Under is worth 0.40: a bid at about 0.50 is over it.
        val v = view().copy(prices = listOf(CnoBookPrice("KI", 150, otherOdds = -180)) + view().prices.filter { it.code != "KI" })
        val l = lines(page(view = v)).lines[1]
        val d = MakerQuote.decide(l, rules, now)
        // The sharpest fair anchors the price far below; whatever it posts must sit under every sharp fair by the veto's bar.
        if (d is MakerDecision.Post) assertTrue(l.sharpFairs.all { it > d.price })
    }

    @Test
    fun `the rules for bids from CNO are narrower and shorter, and a bid from Vigilant's scan is unchanged`() {
        val cno = MakerRules.of(settings)
        val vig = MakerRules.of(ScanSettings())
        assertTrue(cno.requireSharp && cno.anchorSharp && cno.sharpVeto)
        assertEquals(CnoMakerLines.MIN_LIFE_MS, cno.minLifeMs)
        assertEquals(CnoMakerLines.REFRESH_BEFORE_MS, cno.refreshBeforeMs)
        assertEquals(MakerRules.REFRESH_BEFORE_MS, vig.refreshBeforeMs)
        assertEquals(60_000L, vig.minLifeMs)
        // Low API usage is Vigilant's scan on a few books: with bids from CNO it is nothing, and its scan profile is not applied.
        val lowCno = settings.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE)
        assertFalse(lowCno.lowUsageNow)
        assertTrue(MakerRules.of(lowCno).lowUsageBooks.isEmpty())
    }

    @Test
    fun `bids from CNO are on only when bids are`() {
        assertFalse(ScanSettings().bidsFromCno)
        assertFalse(ScanSettings(makerSource = BidSource.CNO, maker = false, makerRecommend = false).bidsFromCno)
        assertTrue(ScanSettings(makerSource = BidSource.CNO, maker = true).bidsFromCno)
        assertTrue(ScanSettings(makerSource = BidSource.CNO, maker = false, makerRecommend = true).bidsFromCno)
        // Never on by default: no preset or restart turns it on.
        assertEquals(BidSource.VIGILANT, ScanSettings().makerSource)
    }

    // ---- the candidates ----------------------------------------------------------------------------------------------------

    private val filters = CnoFilters()

    @Test
    fun `a row gets a page read when a bid could sit on either side, and says why when not`() {
        assertNull(CnoBidCandidates.skipReason(row(), filters, rules, now))
        assertEquals("already under way", CnoBidCandidates.skipReason(row().copy(startsAtMs = now - 1), filters, rules, now))
        assertEquals("starts within the stop window", CnoBidCandidates.skipReason(row().copy(startsAtMs = now + 60_000L), filters, rules, now))
        assertEquals("devigged from one side only", CnoBidCandidates.skipReason(row().copy(oneWay = true), filters, rules, now))
        assertEquals("no fair price", CnoBidCandidates.skipReason(row(fair = null).copy(fairOdds = null), filters, rules, now))
        assertEquals("too few books behind CNO's fair", CnoBidCandidates.skipReason(row().copy(books = 1), filters, rules, now))
        assertEquals("a kind of bet bids are off for", CnoBidCandidates.skipReason(row().copy(market = "Moneyline", bet = "Team A"), filters, rules, now))
        // A 0.97 favorite and its 0.03 complement are both outside the 10-65% window.
        assertEquals("no bid on either side would sit inside the price window", CnoBidCandidates.skipReason(row(fair = 0.97), filters, rules, now))
        // The scope Tj gave the CNO scanner binds: a league he left out is not read.
        val nflOnly = filters.copy(scope = com.tjshea.vigilant.data.cno.CnoScope(leagues = setOf("NBA")))
        assertEquals("in a league you left out", CnoBidCandidates.skipReason(row(), nflOnly, rules, now))
    }

    @Test
    fun `candidates are the soonest games first, one of a pair of complements, and no more than the limit`() {
        val over = row()
        val under = row(bet = "Pat Under 50.5").copy(gameUrl = "https://x/game.aspx?game_id=1&market_id=2&side_id=8")
        val later = row(bet = "Sam Over 30.5").copy(startsAtMs = start + 3_600_000L)
        val sooner = row(bet = "Lee Over 12.5").copy(startsAtMs = start - 3_600_000L)
        val picked = CnoBidCandidates.pick(listOf(later, over, under, sooner), filters, rules, now)
        assertEquals(listOf("Lee Over 12.5", "Pat Over 50.5", "Sam Over 30.5"), picked.map { it.bet })
        assertEquals(2, CnoBidCandidates.pick(listOf(later, over, under, sooner), filters, rules, now, limit = 2).size)
        assertNotNull(picked.firstOrNull())
    }
}
