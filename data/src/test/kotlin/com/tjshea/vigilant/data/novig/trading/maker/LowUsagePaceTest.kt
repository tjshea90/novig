package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.LowUsageBids
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Low API usage bids' pace (v0.68.1; Tj, 2026-10-05: "It will put up many bids, then leave them a couple minutes, then cancel all of them at the same time"; RESEARCH.md §93).
 * A bid never outlives the fair behind it (5 minutes after the oldest quote was seen, 10 for a game over 3 hours away), and the old pace (10 minutes) left every bid down for
 * the rest of the cycle. The first tests are the gap rule; the timeline replays one game's bids second by second through the real [MakerQuote.precheck] and the desk's
 * re-post rule ([MakerPlan]) at different gaps and counts the seconds with no bid up.
 */
class LowUsagePaceTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val now = 1_000_000_000_000L
    private val stop = 15 * minute
    private val auto = ScanSettings(leagues = setOf("NFL"), makerFocus = BidFocus.LOW_USAGE, maker = true)

    // ---- the gap rule -------------------------------------------------------------------------------------------------------

    @Test
    fun `the gaps are the freshness limit less the bids' re-post window - 3 minutes inside 3 hours, 8 beyond`() {
        assertEquals(5 * minute - MakerRules.REFRESH_BEFORE_MS, LowUsageBids.NEAR_GAP_SECONDS * 1_000L)
        assertEquals(10 * minute - MakerRules.REFRESH_BEFORE_MS, LowUsageBids.FAR_GAP_SECONDS * 1_000L)
        assertEquals(180, LowUsageBids.NEAR_GAP_SECONDS)
        assertEquals(480, LowUsageBids.FAR_GAP_SECONDS)
        assertEquals("the desk's default window is the constant", MakerRules.REFRESH_BEFORE_MS, MakerRules.of(auto).refreshBeforeMs)
    }

    @Test
    fun `Auto scans at the short gap while a game a bid could still go on is inside the 3 hour limit's reach, at the long gap when every game is far off`() {
        fun gap(vararg startsIn: Long) = LowUsageBids.autoGapSeconds(startsIn.map { now + it }, now, stop)
        assertEquals("a game 2 h out: the 5 minute limit", 180, gap(2 * hour))
        assertEquals("a game 6 h out: the 10 minute limit", 480, gap(6 * hour))
        assertEquals("one near game among far ones", 180, gap(6 * hour, 2 * hour + 30 * minute, 5 * hour))
        // A game 3 h 5 min out is inside 3 h before the next far scan (8 min): it must already be on the short gap.
        assertEquals(180, gap(3 * hour + 5 * minute))
        assertEquals(480, gap(3 * hour + 9 * minute))
        // A game inside the stop window can't take a bid: it doesn't keep the pace short. A started one neither.
        assertEquals(480, gap(10 * minute))
        assertEquals(480, gap(-5 * minute))
        assertEquals("no game in the window: nothing to keep up", 480, gap())
    }

    @Test
    fun `an unknown last scan keeps the short gap, and a fixed pace is Tj's, never under 5 minutes`() {
        assertEquals(180, LowUsageBids.gapSeconds(auto, null, now))
        assertEquals(480, LowUsageBids.gapSeconds(auto, emptyList(), now))
        assertEquals(180, LowUsageBids.gapSeconds(auto, listOf(now + 2 * hour), now))
        assertEquals(600, LowUsageBids.gapSeconds(auto.copy(lowUsagePace = 10), listOf(now + 2 * hour), now))
        assertEquals(600, LowUsageBids.gapSeconds(auto.copy(lowUsagePace = 10), emptyList(), now))
        assertEquals(300, LowUsageBids.gapSeconds(auto.copy(lowUsagePace = 1), null, now))
        // The stop setting is the bids': a 30 minute stop leaves a game 20 minutes out with no bid.
        assertEquals(480, LowUsageBids.gapSeconds(auto.copy(makerStopMinutes = 30), listOf(now + 20 * minute), now))
        assertEquals(180, LowUsageBids.gapSeconds(auto.copy(makerStopMinutes = 5), listOf(now + 20 * minute), now))
    }

    // ---- the markets a scan reads first -------------------------------------------------------------------------------------

    private fun bid(id: String, market: String, status: MakerStatus) = MakerBid(
        clientId = id, orderId = "ord-$id", marketId = market, eventId = "e1", outcomeId = "o-$id", league = "NFL", eventName = "A @ B",
        startsTs = now + 2 * hour, marketLabel = "Passing Yards", selection = "Over", price = 0.485, contracts = 10, fair = 0.50, evAtFair = 0.03, margin = 0.025,
        postedAtMs = now, status = status,
    )

    @Test
    fun `the markets with a bid still resting are read first, an ended one's is not`() {
        val bids = listOf(
            bid("a", "m-a", MakerStatus.RESTING), bid("b", "m-b", MakerStatus.SENT), bid("c", "m-c", MakerStatus.CANCELING),
            bid("d", "m-d", MakerStatus.FILLED), bid("e", "m-a", MakerStatus.RESTING),
        )
        assertEquals(setOf("m-a", "m-b"), LowUsage.restingMarkets(bids))
        assertEquals(emptySet<String>(), LowUsage.restingMarkets(emptyList()))
    }

    // ---- the timeline -------------------------------------------------------------------------------------------------------

    private val rules = MakerRules.of(auto.copy(makerStakeMode = AutoBetStake.CUSTOM, makerStake = 5.0))

    private fun market() = NovigMarket(
        "m1", "ev-m1", "PASSING_YARDS", "OPEN", "Player 224.5 PASSING_YARDS", Fixtures.START_MS, MarketFee.GAME,
        listOf(NovigOutcome("m1-over", "Over 224.5", "TBD"), NovigOutcome("m1-under", "Under 224.5", "TBD")),
    )

    /** One prop line as a scan that read its sharp books at [seenAt] (the oldest quote's stamp) prices it for a game starting at [startsTs]. */
    private fun line(startsTs: Long, seenAt: Long) = MakerLine(
        market = market(), outcomeId = "m1-over", startsTs = startsTs, league = "NFL", eventName = "A @ B", marketLabel = "Passing Yards", selection = "Player Over 224.5",
        kind = BetKind.PROP, fair = 0.50, fairAsOfMs = seenAt, fairOld = false, books = 2, fairBooks = listOf("Kalshi", "ProphetX"),
        bookFairs = listOf(0.50, 0.505), sharpFairs = listOf(0.50, 0.505), offer = null, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    /** What one bid's life looked like over [horizonMs]: the seconds with a bid up after the first, how many bids went up, and the longest stretch with none. */
    private data class Timeline(val upMs: Long, val downMs: Long, val longestDownMs: Long, val posts: Int)

    /**
     * Scans start every [gapMs] from t = 0. A scan's fair is the oldest quote it used, seen [quoteAgeMs] before the scan began; its bid is posted or rolled [postDelayMs]
     * after the scan began (when its market is read). The desk keeps a resting bid until it ends, except that one within [MakerRules.refreshBeforeMs] of its end is re-posted
     * from a fresher fair that lets it rest at least [MakerRules.minLifeMs] longer ([MakerPlan.plan]'s "About to expire: re-posted").
     */
    private fun timeline(startsIn: Long, gapMs: Long, quoteAgeMs: Long = 45_000L, postDelayMs: Long = 45_000L, horizonMs: Long = 2 * hour): Timeline {
        val startsTs = now + startsIn
        var endsAt = Long.MIN_VALUE
        var first: Long? = null
        var up = 0L
        var down = 0L
        var run = 0L
        var longest = 0L
        var posts = 0
        var t = 0L
        while (t < horizonMs) {
            val sinceScan = t - postDelayMs
            if (sinceScan >= 0 && sinceScan % gapMs == 0L) {
                val clock = now + t
                val pre = MakerQuote.precheck(line(startsTs, now + sinceScan - quoteAgeMs), rules, clock)
                if (pre is MakerQuote.Pre.Price) {
                    val resting = endsAt > clock
                    val roll = resting && endsAt - clock <= rules.refreshBeforeMs && pre.until - endsAt >= rules.minLifeMs
                    if (!resting || roll) {
                        endsAt = pre.until
                        posts++
                        if (first == null) first = t
                    }
                }
            }
            if (first != null) {
                if (now + t < endsAt) { up += 1_000; run = 0 } else { down += 1_000; run += 1_000; longest = maxOf(longest, run) }
            }
            t += 1_000
        }
        return Timeline(up, down, longest, posts)
    }

    @Test
    fun `old pace, 10 minutes on a game inside 3 hours - every bid ends together and most of the time nothing is up (Tj's report)`() {
        val old = timeline(startsIn = 2 * hour + 30 * minute, gapMs = 10 * minute)
        assertTrue("down ${old.downMs / 1_000} s", old.downMs > old.upMs)
        assertTrue("a stretch of ${old.longestDownMs / 1_000} s with no bid", old.longestDownMs >= 4 * minute)
    }

    @Test
    fun `Auto's short gap keeps a game inside 3 hours up with no gap at all`() {
        val t = timeline(startsIn = 2 * hour + 30 * minute, gapMs = LowUsageBids.NEAR_GAP_SECONDS * 1_000L)
        assertEquals("seconds with no bid after the first: ${t.downMs / 1_000}", 0L, t.downMs)
        assertTrue("every scan rolled the bid forward: ${t.posts}", t.posts >= 30)
    }

    @Test
    fun `Auto's long gap keeps a game over 3 hours away up with no gap at all`() {
        val t = timeline(startsIn = 5 * hour + 50 * minute, gapMs = LowUsageBids.FAR_GAP_SECONDS * 1_000L)
        assertEquals("seconds with no bid after the first: ${t.downMs / 1_000}", 0L, t.downMs)
        assertTrue(t.posts >= 10)
    }

    @Test
    fun `the 10 minute limit is why the long gap is for far games only - a gap of 8 minutes leaves a game inside 3 hours down`() {
        val t = timeline(startsIn = 2 * hour + 30 * minute, gapMs = LowUsageBids.FAR_GAP_SECONDS * 1_000L)
        assertTrue("down ${t.downMs / 1_000} s", t.downMs > 0)
    }

    @Test
    fun `a gap a minute longer than Auto's leaves a gap, the margin is the re-post window - nothing is wasted`() {
        val t = timeline(startsIn = 2 * hour + 30 * minute, gapMs = (LowUsageBids.NEAR_GAP_SECONDS + 60) * 1_000L)
        assertTrue("down ${t.downMs / 1_000} s", t.downMs > 0)
    }

    @Test
    fun `quotes already old when read, or a market read late in the scan, eat the margin - the limit of the 3 minute gap is 2 minutes of age plus delay`() {
        val gap = LowUsageBids.NEAR_GAP_SECONDS * 1_000L
        assertEquals(0L, timeline(2 * hour + 30 * minute, gap, quoteAgeMs = 60_000L, postDelayMs = 60_000L).downMs)
        assertTrue(timeline(2 * hour + 30 * minute, gap, quoteAgeMs = 90_000L, postDelayMs = 90_000L).downMs > 0)
    }

    @Test
    fun `no rule is loosened to keep bids up - the bid still ends 5 minutes after the oldest quote was seen`() {
        val seen = now - 90_000L
        val pre = MakerQuote.precheck(line(now + 2 * hour, seen), rules, now) as MakerQuote.Pre.Price
        assertEquals(seen + Freshness.MAX_QUOTE_AGE_MS, pre.until)
        val far = MakerQuote.precheck(line(now + 5 * hour, seen), rules, now) as MakerQuote.Pre.Price
        assertEquals(seen + Freshness.FAR_OFF_AGE_MS, far.until)
    }
}
