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
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Low-API-usage prop bids' settings half (Tj, 2026-10-05; RESEARCH.md §92): the picked books, the feeds that carry them, the scan as the mode narrows it, and what that scan
 * prices a line from (two fresh two-sided picked books, nothing else).
 */
class LowUsageBidsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val on = ScanSettings(leagues = setOf("NFL"), makerFocus = BidFocus.LOW_USAGE, maker = true)

    // ---- the margin Tj sets (2026-10-06: "add options for minimum 1.5% positive EV or an amount I type in") ------------------------

    @Test
    fun `the margin has a 1_5 percent chip beside 2_5 percent, which stays the default`() {
        assertEquals(listOf(0.015, 0.025, 0.03, 0.035, 0.04), LowUsageBids.MARGIN_CHOICES)
        assertEquals(0.025, ScanSettings().lowUsageMargin, 0.0)
        assertEquals(0.025, LowUsageBids.DEFAULT_MARGIN, 0.0)
        assertTrue("every chip is allowed", LowUsageBids.MARGIN_CHOICES.all { it >= LowUsageBids.MIN_MARGIN })
    }

    @Test
    fun `a typed margin is a percent from half a percent to fifty, in tenths or hundredths, and anything else is refused`() {
        assertEquals(0.015, LowUsageBids.parseMargin("1.5")!!, 0.0)
        assertEquals(0.015, LowUsageBids.parseMargin(" 1,5 % ")!!, 0.0)
        assertEquals(0.0325, LowUsageBids.parseMargin("3.25")!!, 0.0)
        assertEquals("rounded to a hundredth of a percent", 0.0155, LowUsageBids.parseMargin("1.549")!!, 0.0)
        assertEquals(0.02, LowUsageBids.parseMargin("2.")!!, 0.0)
        assertEquals(0.005, LowUsageBids.parseMargin("0.5")!!, 0.0)
        assertEquals(0.5, LowUsageBids.parseMargin("50")!!, 0.0)
        assertNull("under the floor", LowUsageBids.parseMargin("0.4"))
        assertNull(LowUsageBids.parseMargin("0"))
        assertNull("over half the fair", LowUsageBids.parseMargin("51"))
        assertNull(LowUsageBids.parseMargin(""))
        assertNull(LowUsageBids.parseMargin("."))
        assertNull(LowUsageBids.parseMargin("1.2.3"))
        assertNull(LowUsageBids.parseMargin("-1"))
        assertNull(LowUsageBids.parseMargin("abc"))
    }

    @Test
    fun `the field shows the saved margin as a percent with no trailing zeros, and what it shows parses back to it`() {
        assertEquals("2.5", LowUsageBids.marginText(0.025))
        assertEquals("3", LowUsageBids.marginText(0.03))
        assertEquals("1.5", LowUsageBids.marginText(0.015))
        assertEquals("3.25", LowUsageBids.marginText(0.0325))
        assertEquals("0.5", LowUsageBids.marginText(0.005))
        (LowUsageBids.MARGIN_CHOICES + listOf(0.0155, 0.5, 0.005)).forEach { m ->
            assertEquals(m, LowUsageBids.parseMargin(LowUsageBids.marginText(m))!!, 0.0)
        }
    }

    // ---- the books Tj picks -----------------------------------------------------------------------------------------------

    @Test
    fun `the default is the three sharpest prop books in the research's order, and Pinnacle is not one`() {
        assertEquals(listOf("kalshi", "prophetx", "fanduel"), LowUsageBids.books(ScanSettings()).toList())
        assertFalse("pinnacle" in LowUsageBids.DEFAULT_BOOKS)
        assertEquals(listOf("kalshi", "prophetx", "fanduel", "williamhill_us", "draftkings", "pinnacle"), LowUsageBids.BOOKS.map { it.key })
    }

    @Test
    fun `a pick is two or three known books, in the ranking's order`() {
        assertEquals(listOf("kalshi", "fanduel"), LowUsageBids.books(ScanSettings(lowUsageBooks = setOf("fanduel", "kalshi"))).toList())
        // Unknown names are ignored; fewer than two valid ones are topped up from the defaults, ranked.
        assertEquals(listOf("kalshi", "prophetx"), LowUsageBids.books(ScanSettings(lowUsageBooks = setOf("kalshi", "nonsense"))).toList())
        assertEquals(listOf("kalshi", "prophetx"), LowUsageBids.books(ScanSettings(lowUsageBooks = emptySet())).toList())
        assertEquals(listOf("prophetx", "fanduel"), LowUsageBids.books(ScanSettings(lowUsageBooks = setOf("pinnacle", "prophetx", "fanduel", "draftkings"))).toList().take(2))
        // Four valid picks (a hand-edited file) keep the three ranked highest.
        assertEquals(listOf("kalshi", "prophetx", "fanduel"), LowUsageBids.books(ScanSettings(lowUsageBooks = setOf("pinnacle", "fanduel", "prophetx", "kalshi"))).toList())
    }

    @Test
    fun `a tap adds up to three and removes down to two, and does nothing that would break either`() {
        val two = ScanSettings(lowUsageBooks = setOf("kalshi", "prophetx"))
        assertEquals(listOf("kalshi", "prophetx", "fanduel"), LowUsageBids.toggled(two, "fanduel").toList())
        val three = ScanSettings()
        assertEquals("a fourth is not added", three.lowUsageBooks.toList(), LowUsageBids.toggled(three, "pinnacle").toList())
        assertEquals(listOf("kalshi", "fanduel"), LowUsageBids.toggled(three, "prophetx").toList())
        assertEquals("a third can't go below two", listOf("kalshi", "prophetx"), LowUsageBids.toggled(two, "kalshi").toList())
        assertEquals(two.lowUsageBooks.toList(), LowUsageBids.toggled(two, "unknown").toList())
        // The ranking's order, not the tap order.
        assertEquals(listOf("kalshi", "prophetx", "draftkings"), LowUsageBids.toggled(two, "draftkings").toList())
    }

    // ---- which feeds are asked --------------------------------------------------------------------------------------------

    private val all = setOf(LowUsageBids.FEED_KALSHI, LowUsageBids.FEED_PINNACLE, LowUsageBids.FEED_PROPLINE, LowUsageBids.FEED_PARLAY)

    @Test
    fun `ProphetX needs ParlayAPI, so FanDuel comes in the same call and PropLine is not asked`() {
        val plan = LowUsageBids.feedsFor(LowUsageBids.DEFAULT_BOOKS, all)
        assertEquals(listOf(LowUsageBids.FEED_KALSHI, LowUsageBids.FEED_PARLAY), plan.feeds)
        assertEquals(LowUsageBids.FEED_PARLAY, plan.assigned["fanduel"])
        assertEquals(LowUsageBids.FEED_PARLAY, plan.assigned["prophetx"])
        assertTrue(plan.unreachable.isEmpty())
    }

    @Test
    fun `without a ParlayAPI key ProphetX can't be read, FanDuel comes from PropLine, and the plan says what is missing`() {
        val plan = LowUsageBids.feedsFor(LowUsageBids.DEFAULT_BOOKS, all - LowUsageBids.FEED_PARLAY)
        assertEquals(listOf(LowUsageBids.FEED_KALSHI, LowUsageBids.FEED_PROPLINE), plan.feeds)
        assertEquals(listOf("prophetx"), plan.unreachable)
    }

    @Test
    fun `Pinnacle rides the paid call or PropLine when one is needed anyway, and PinnWire only when nothing else is`() {
        val withPinnacle = setOf("kalshi", "fanduel", "pinnacle")
        // FanDuel takes PropLine (free), and Pinnacle joins it there.
        assertEquals(listOf(LowUsageBids.FEED_KALSHI, LowUsageBids.FEED_PROPLINE), LowUsageBids.feedsFor(withPinnacle, all).feeds)
        // No PropLine key: FanDuel and Pinnacle share ParlayAPI's call... but PinnWire is the cheapest carrier for Pinnacle alone.
        val noPropLine = LowUsageBids.feedsFor(withPinnacle, all - LowUsageBids.FEED_PROPLINE)
        assertEquals(listOf(LowUsageBids.FEED_KALSHI, LowUsageBids.FEED_PARLAY), noPropLine.feeds)
        val pinnacleAlone = LowUsageBids.feedsFor(setOf("kalshi", "pinnacle"), all)
        assertEquals(listOf(LowUsageBids.FEED_KALSHI, LowUsageBids.FEED_PINNACLE), pinnacleAlone.feeds)
    }

    @Test
    fun `a book no available feed carries is unreachable, and no feed is asked for it`() {
        val plan = LowUsageBids.feedsFor(setOf("kalshi", "williamhill_us"), setOf(LowUsageBids.FEED_KALSHI, LowUsageBids.FEED_PROPLINE))
        assertEquals(listOf(LowUsageBids.FEED_KALSHI), plan.feeds)
        assertEquals(listOf("williamhill_us"), plan.unreachable)
        assertEquals(emptyList<String>(), LowUsageBids.feedsFor(LowUsageBids.DEFAULT_BOOKS, emptySet()).feeds)
    }

    @Test
    fun `ParlayAPI is asked for the picked books it carries, by its own names, and never for Kalshi`() {
        assertEquals(listOf("prophetx", "fanduel"), LowUsageBids.parlayBooks(on))
        assertEquals(listOf("prophetx", "caesars"), LowUsageBids.parlayBooks(on.copy(lowUsageBooks = setOf("kalshi", "prophetx", "williamhill_us"))))
    }

    // ---- the scan the mode makes -------------------------------------------------------------------------------------------

    @Test
    fun `while on, the scan is props only, the next 6 hours, the picked books' fair, two of them at least, and nothing else`() {
        val e = on.effective()
        assertTrue(e.lowUsageScan)
        assertEquals(setOf(MarketFamily.PLAYER_PROPS), e.families)
        assertEquals(6, e.scanWindowHours)
        assertEquals(6, e.bookPropWindowHours)
        assertEquals(FairSource.SHARP, e.fairSource)
        assertEquals(DevigMethod.WORST_CASE, e.devigMethod)
        assertEquals(setOf("kalshi", "prophetx", "fanduel"), e.sharpBooks)
        assertEquals(listOf("prophetx", "fanduel"), e.referenceBooks)
        assertFalse(e.fallbackToAverage)
        assertEquals(2, e.minSharpBooks)
        assertEquals(2, e.fairSettings().minSharp)
        assertFalse(e.useOddsApi)
        assertFalse(e.usePolymarket)
        assertFalse(e.usePinnacle)
        assertTrue(e.useKalshi)
        assertTrue(e.useParlay)
        // A feed that CAN carry a picked book is allowed (FanDuel is on PropLine too); which feeds are really asked is [LowUsageBids.feedsFor]'s call, tested above.
        assertTrue(e.usePropLine)
        assertFalse(e.includeLive)
        // Only the sources that carry a picked book can be read at all: not Pinnacle's feed, Polymarket or The Odds API.
        assertEquals(setOf("kalshi", "parlay", "parlay_1h", "parlay_props", "propline", "propline_props"), e.enabledSources)
        assertEquals(setOf("kalshi", "parlay", "parlay_1h", "parlay_props"), on.copy(lowUsageBooks = setOf("kalshi", "prophetx")).effective().enabledSources)
    }

    /**
     * Tj, 2026-10-07: "I changed the trap guard setting from 6 hours to 8 hours and then to no trap guard at all, but it is hard set at 6 hours trap guard no matter what I select."
     * The scan's window is the trap guard's hours (6 by default), never past the ordinary reach, and Off reads as far as the ordinary reach says.
     */
    @Test
    fun `the scan's window is the trap guard's hours - 6 by default, any hours he picks, and Off reads as far as the ordinary reach`() {
        assertEquals(6, LowUsageBids.windowHours(on))
        assertEquals(6, on.effective().scanWindowHours)
        // A shorter starts-within stays; a longer one does not widen what the trap guard allows.
        assertEquals(3, on.copy(startsWithinHours = 3).effective().scanWindowHours)
        assertEquals(6, on.copy(startsWithinHours = 24).effective().scanWindowHours)
        assertEquals(6, on.copy(startsWithinHours = 0, daysAhead = 10).effective().scanWindowHours)
        // The trap guard Tj sets is the window.
        assertEquals(8, on.copy(trapEarlyHours = 8).effective().scanWindowHours)
        assertEquals(12, on.copy(trapEarlyHours = 12, startsWithinHours = 0).effective().scanWindowHours)
        assertEquals(10, on.copy(trapEarlyHours = 12, startsWithinHours = 10).effective().scanWindowHours)
        assertEquals(3, on.copy(trapEarlyHours = 3).effective().scanWindowHours)
        // Off has no limit of its own: Days ahead and Starts within decide.
        assertEquals(7 * 24, on.copy(trapEarlyHours = 0).effective().scanWindowHours)
        assertEquals(24, on.copy(trapEarlyHours = 0, startsWithinHours = 24).effective().scanWindowHours)
        assertEquals(24, on.copy(trapEarlyHours = 0, daysAhead = 1).effective().scanWindowHours)
        // Never past the ordinary reach even when the trap guard is longer (Days ahead 1 = 24 h).
        assertEquals(24, on.copy(trapEarlyHours = 72, daysAhead = 1).effective().scanWindowHours)
        // The sportsbook-props horizon is Tj's own ("Games within"), and the window still bounds it.
        assertEquals(6, on.copy(bookPropHours = 24).effective().bookPropWindowHours)
        assertEquals(12, on.copy(trapEarlyHours = 12, bookPropHours = 24).effective().bookPropWindowHours)
        assertEquals(6, on.copy(trapEarlyHours = 12, bookPropHours = 6).effective().bookPropWindowHours)
        assertEquals("the default window is the trap guard's default", TrapGuard.DEFAULT_EARLY_HOURS, LowUsageBids.WINDOW_HOURS)
    }

    @Test
    fun `the scan is the mode's only while bids are on and Pinnacle only isn't, and a bets-only pass is never narrowed`() {
        assertTrue(on.lowUsageNow)
        assertFalse("bids off: the whole scan", on.copy(maker = false, makerRecommend = false).lowUsageNow)
        assertTrue("recommending bids counts", on.copy(maker = false, makerRecommend = true).lowUsageNow)
        assertFalse("another focus", on.copy(makerFocus = BidFocus.QUICK_LIKELY).lowUsageNow)
        assertFalse("Pinnacle only wins", on.copy(pinnacleOnly = true).lowUsageNow)
        assertEquals(on.copy(maker = false, makerRecommend = false), on.copy(maker = false, makerRecommend = false).effective())
        // Check odds now prices Tj's open bets of every kind: the families and sources are his.
        val bets = on.effective(forBets = true)
        assertFalse(bets.lowUsageScan)
        assertEquals(on, bets)
        // Applying it twice changes nothing more.
        assertEquals(on.effective(), on.effective().effective())
    }

    @Test
    fun `the mode never saves its own scan flag, and an old file reads with the mode's defaults`() {
        val text = json.encodeToString(ScanSettings.serializer(), on.effective())
        assertFalse(text.contains("lowUsageScan"))
        assertFalse(text.contains("minSharpBooks"))
        assertFalse(json.decodeFromString(ScanSettings.serializer(), text).lowUsageScan)
        val old = json.decodeFromString(ScanSettings.serializer(), """{"maker":true,"schema":12}""")
        assertEquals(BidFocus.ALL, old.makerFocus)
        assertEquals(LowUsageBids.DEFAULT_BOOKS, old.lowUsageBooks)
        assertEquals("a file from before v0.68.1 reads as Auto, not as the 10 minutes that left the bids down half the time", LowUsageBids.AUTO, old.lowUsagePace)
        assertEquals("a saved old pace is ignored, not carried over", LowUsageBids.AUTO, json.decodeFromString(ScanSettings.serializer(), """{"maker":true,"lowUsageMinutes":10}""").lowUsagePace)
        assertEquals(0.025, old.lowUsageMargin, 0.0)
        val picked = ScanSettings(makerFocus = BidFocus.LOW_USAGE, lowUsageBooks = setOf("kalshi", "draftkings"), lowUsagePace = 15, lowUsageMargin = 0.03)
        assertEquals(picked, json.decodeFromString(ScanSettings.serializer(), json.encodeToString(ScanSettings.serializer(), picked)))
    }

    @Test
    fun `Vigilant's scan runs at the pace Tj picked in the mode, and at the usual four minutes otherwise`() {
        assertEquals(240, ScanSettings().vigilantGapSeconds)
        assertEquals("Auto's short gap: the 5 minute limit less the 2 minute re-post window", 180, on.vigilantGapSeconds)
        assertEquals(600, on.copy(lowUsagePace = 10).vigilantGapSeconds)
        assertEquals(300, on.copy(lowUsagePace = 5).vigilantGapSeconds)
        assertEquals("a fixed pace is never faster than 5 minutes", 300, on.copy(lowUsagePace = 1).vigilantGapSeconds)
        assertEquals("bids off: the usual gap", 240, on.copy(maker = false, makerRecommend = false).vigilantGapSeconds)
        assertTrue(LowUsageBids.PACE_CHOICES.filter { it != LowUsageBids.AUTO }.all { it >= LowUsageBids.MIN_MINUTES })
        assertEquals("Auto leads the choices", LowUsageBids.AUTO, LowUsageBids.PACE_CHOICES.first())
    }

    // ---- what the low-usage scan prices a line from ------------------------------------------------------------------------

    private val now = Fixtures.START_MS - 2 * 3_600_000L
    private val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
    private val prop = NovigMarket(
        "p1", Fixtures.EVENT_ID, "PASSING_YARDS", "OPEN", "Lamar Jackson 224.5 PASSING_YARDS", Fixtures.START_MS, MarketFee.GAME,
        listOf(NovigOutcome("o1", "Over 224.5", "TBD"), NovigOutcome("u1", "Under 224.5", "TBD")),
    )

    private fun quote(key: String, over: Double, under: Double, seenAgoMs: Long?, oneSided: Boolean = false) = RefBookMarket(
        key, key.replaceFirstChar { it.uppercase() }, LineKind.PLAYER_PROP,
        listOfNotNull(RefQuote(Side.OVER, over, 224.5), if (oneSided) null else RefQuote(Side.UNDER, under, 224.5)),
        seenAgoMs?.let { now - it }, 0, "Lamar Jackson", "PASSING_YARDS",
    )

    private fun price(settings: ScanSettings, vararg quotes: RefBookMarket): Opportunity {
        val e = settings.effective()
        val refs = listOf(RefSnapshot("americanfootball_nfl", listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, "Dallas Cowboys", "Baltimore Ravens", quotes.toList())), now))
        val plan = Planner.plan(listOf(event), listOf(prop), refs, e, now)
        val book = NovigBook("p1", 1, mapOf("o1" to listOf(BidLevel(470, 10_000)), "u1" to listOf(BidLevel(500, 10_000))), now)
        return Pricing.price(plan, mapOf("p1" to book), e, now).opportunities.first { it.outcome.outcomeId == "o1" }
    }

    private val fresh = 60_000L
    private val stale = 6 * 60_000L

    @Test
    fun `two fresh two-sided picked books make the fair, devigged the worst way and averaged`() {
        val o = price(on, quote("kalshi", 1.95, 1.95, fresh), quote("prophetx", 1.90, 2.00, fresh))
        assertNotNull(o.fairProbability)
        assertEquals(setOf("Kalshi", "Prophetx"), o.fair!!.booksUsed.toSet())
        assertEquals(FairSource.SHARP, o.fair!!.sourceUsed)
        // The lowest of the four devigs per book, then the mean of the two books.
        val perBook = o.fair!!.perBook.map { it.fairProbabilities[0] }
        assertEquals(perBook.average(), o.fairProbability!!, 1e-12)
        assertEquals("the oldest quote dates the line", now - fresh, o.fairAsOfMs)
    }

    @Test
    fun `one picked book is not a fair, and neither is a picked book plus a soft one`() {
        assertNull(price(on, quote("kalshi", 1.95, 1.95, fresh)).fairProbability)
        // DraftKings isn't picked (Kalshi, ProphetX, FanDuel are): it can't stand in for a second sharp book, and nothing averages.
        assertNull(price(on, quote("kalshi", 1.95, 1.95, fresh), quote("draftkings", 1.90, 2.00, fresh)).fairProbability)
        // A sharp book that isn't one of the picks (Pinnacle) doesn't count either.
        assertNull(price(on, quote("kalshi", 1.95, 1.95, fresh), quote("pinnacle", 1.90, 2.00, fresh)).fairProbability)
    }

    @Test
    fun `a stale book is dropped before the devig - two fresh books still price the line, one fresh book does not`() {
        val three = price(on, quote("kalshi", 1.95, 1.95, fresh), quote("prophetx", 1.90, 2.00, fresh), quote("fanduel", 1.70, 2.30, stale))
        assertEquals("the stale FanDuel price is not in the fair", setOf("Kalshi", "Prophetx"), three.fair!!.booksUsed.toSet())
        assertEquals(now - fresh, three.fairAsOfMs)
        assertNull("one fresh book is not enough", price(on, quote("kalshi", 1.95, 1.95, fresh), quote("prophetx", 1.90, 2.00, stale)).fairProbability)
        // A quote with no time on it isn't known to be current.
        assertNull(price(on, quote("kalshi", 1.95, 1.95, fresh), quote("prophetx", 1.90, 2.00, null)).fairProbability)
    }

    @Test
    fun `the freshness limit is the app's own - 5 minutes inside 3 hours of the start, 10 beyond`() {
        // 2 h before the start: 4 min old passes, 6 min old does not (the stale case above).
        assertNotNull(price(on, quote("kalshi", 1.95, 1.95, 4 * 60_000L), quote("prophetx", 1.90, 2.00, 4 * 60_000L)).fairProbability)
        // 4 h before the start the limit is 10 minutes.
        val far = now - 2 * 3_600_000L
        val e = on.effective()
        val refs = listOf(
            RefSnapshot(
                "americanfootball_nfl",
                listOf(
                    RefEvent(
                        "r", "americanfootball_nfl", Fixtures.START_MS, "Dallas Cowboys", "Baltimore Ravens",
                        listOf(quote("kalshi", 1.95, 1.95, 0).copy(lastUpdateMs = far - 8 * 60_000L), quote("prophetx", 1.90, 2.00, 0).copy(lastUpdateMs = far - 8 * 60_000L)),
                    ),
                ),
                far,
            ),
        )
        val plan = Planner.plan(listOf(event), listOf(prop), refs, e, far)
        val book = NovigBook("p1", 1, mapOf("o1" to listOf(BidLevel(470, 10_000)), "u1" to listOf(BidLevel(500, 10_000))), far)
        val o = Pricing.price(plan, mapOf("p1" to book), e, far).opportunities.first { it.outcome.outcomeId == "o1" }
        assertNotNull("8 minutes old is current for a game 4 hours out", o.fairProbability)
    }

    @Test
    fun `a book that quotes only one side proves nothing - it is not counted`() {
        val o = price(on, quote("kalshi", 1.95, 1.95, fresh), quote("prophetx", 1.90, 2.00, fresh, oneSided = true), quote("fanduel", 1.92, 1.98, fresh))
        assertEquals(setOf("Kalshi", "Fanduel"), o.fair!!.booksUsed.toSet())
        assertNull(price(on, quote("kalshi", 1.95, 1.95, fresh), quote("prophetx", 1.90, 2.00, fresh, oneSided = true)).fairProbability)
    }

    @Test
    fun `outside the mode nothing is dropped and one sharp book still prices the line`() {
        val plain = ScanSettings(leagues = setOf("NFL"), fairSource = FairSource.SHARP, sharpBooks = setOf("kalshi"), fallbackToAverage = false, devigMethod = DevigMethod.MULTIPLICATIVE)
        val o = price(plain, quote("kalshi", 1.95, 1.95, stale))
        assertNotNull(o.fairProbability)
        assertEquals(now - stale, o.fairAsOfMs)
    }
}
