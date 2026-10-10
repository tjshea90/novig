package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.pinnodds.PinnBook
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames
import com.tjshea.vigilant.data.pinnodds.parsePinnFrame
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live bid rules, one reason at a time: a bid is posted only from a fresh, settled, trusted price, and every reason it stops being one is named. */
class LiveBidJudgeTest {
    private val now = 1_000_000L

    /** A line that passes everything: Pinnacle's fair 50% for the side, settled 10 s, heard from 1 s ago, Novig bids 0.45 and offers 0.52. */
    private fun view(
        fair: Double? = 0.50, problem: String? = null, pinnLive: Boolean = true, novigLive: Boolean = true, lineOpen: Boolean = true, overround: Double = 0.06, limit: Double? = 3000.0,
        quietMs: Long = 1_000L, sinceChangeMs: Long = 10_000L, scoreAgeMs: Long? = null, dangerAgeMs: Long? = null, bestBid: Double? = 0.45, offer: Double? = 0.52,
    ) = LiveBidView(now, problem, pinnLive, novigLive, lineOpen, fair, overround, limit, quietMs, sinceChangeMs, scoreAgeMs, dangerAgeMs, bestBid, offer, MarketFee.GAME)

    private val q = LiveBidQuality()

    private fun skip(v: LiveBidView, rules: LiveBidQuality = q): String = (LiveBidJudge.want(v, rules) as LiveBidVerdict.Skip).reason

    // ---- a bid goes up -------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `a good line gets a bid the margin under Pinnacle's fair, on Novig's grid, with the edge it was priced for`() {
        val p = LiveBidJudge.want(view(), q) as LiveBidVerdict.Post
        assertEquals("0.50 / 1.05 = 0.4762, floored to the grid (0.005 steps)", 0.475, p.price, 1e-9)
        assertEquals(0.50 / 0.475 - 1.0, p.ev, 1e-9)
        assertTrue("at least the margin", p.ev >= q.margin)
        assertTrue("above the best bid of 0.45, so it leads", p.leads)
        assertEquals(0.485, p.mid!!, 1e-9)
        assertEquals("maker credit per $1 of price: half the 3% fee on P(1-P), over the price", 0.5 * 0.03 * (1.0 - 0.475), p.credit, 1e-9)
    }

    @Test
    fun `a wider margin bids lower and never over what it asked for`() {
        for (m in listOf(0.02, 0.04, 0.05, 0.06, 0.08)) {
            val p = LiveBidJudge.want(view(fair = 0.62), q.copy(margin = m)) as LiveBidVerdict.Post
            assertTrue("margin $m: the edge is at least the margin", p.ev >= m - 1e-9)
            assertTrue("never above fair / (1 + margin)", p.price <= 0.62 / (1.0 + m) + 1e-9)
        }
    }

    @Test
    fun `each staleness and trust check names itself and stops the bid`() {
        assertEquals("Novig's feed is down", skip(view(problem = "Novig live feed problem")), "Novig live feed problem")
        assertEquals(LiveBidSkip.CLOSED, skip(view(lineOpen = false)))
        assertEquals(LiveBidSkip.NOT_LIVE, skip(view(pinnLive = false)))
        assertEquals(LiveBidSkip.NOVIG_PAUSED, skip(view(novigLive = false)))
        assertEquals("Pinnacle silent for 21 s against 20", LiveBidSkip.QUIET, skip(view(quietMs = 21_000L)))
        assertEquals("a danger-zone frame 5 s ago against a 10 s hold", LiveBidSkip.DANGER, skip(view(dangerAgeMs = 5_000L)))
        assertEquals(LiveBidSkip.SCORE_HOLD, skip(view(scoreAgeMs = 12_000L)))
        assertEquals("the price changed 1 s ago against a 3 s settle", LiveBidSkip.SETTLING, skip(view(sinceChangeMs = 1_000L)))
        assertEquals(LiveBidSkip.OVERROUND, skip(view(overround = 0.09)))
        assertEquals(LiveBidSkip.LIMIT, skip(view(limit = 100.0)))
        assertEquals("an unknown limit is not assumed to be fine", LiveBidSkip.LIMIT_UNKNOWN, skip(view(limit = null)))
        assertEquals(LiveBidSkip.NO_FAIR, skip(view(fair = null)))
        assertEquals(LiveBidSkip.EXTREME, skip(view(fair = 0.95)))
        assertEquals(LiveBidSkip.EXTREME, skip(view(fair = 0.05)))
    }

    @Test
    fun `a fair price that Novig's own middle disagrees with is a mismatch, not an edge`() {
        // Pinnacle says 50%, Novig's middle is (0.30 + 0.40) / 2 = 0.35: 15 points apart against a limit of 10.
        assertEquals(LiveBidSkip.MISMATCH, skip(view(bestBid = 0.30, offer = 0.40)))
        assertNotNull("ten points apart or less passes", LiveBidJudge.want(view(bestBid = 0.43, offer = 0.53), q) as? LiveBidVerdict.Post)
        assertTrue("0 turns the check off", LiveBidJudge.want(view(bestBid = 0.30, offer = 0.40), q.copy(maxBookGap = 0.0)) is LiveBidVerdict.Post)
    }

    @Test
    fun `a bid that Novig would take is not sent, and one that would lead the book is left alone when asked`() {
        assertEquals("Novig already offers it at 0.47: a post-only bid at 0.475 would be refused", LiveBidSkip.WOULD_TAKE, skip(view(offer = 0.47, bestBid = 0.40)))
        assertEquals(LiveBidSkip.LEADS, skip(view(), q.copy(neverLead = true)))
        assertEquals("no bid on the side at all counts as leading", LiveBidSkip.LEADS, skip(view(bestBid = null), q.copy(neverLead = true)))
        assertTrue("behind a bid of 0.48 it is fine", LiveBidJudge.want(view(bestBid = 0.48, offer = 0.53), q.copy(neverLead = true)) is LiveBidVerdict.Post)
    }

    @Test
    fun `the price window leaves long shots and near certainties alone`() {
        assertEquals(LiveBidSkip.PRICE_WINDOW, skip(view(fair = 0.50), q.copy(minPrice = 0.60)))
        assertEquals(LiveBidSkip.PRICE_WINDOW, skip(view(fair = 0.50), q.copy(maxPrice = 0.40)))
    }

    @Test
    fun `a fair price that has not changed for too long is too old when that limit is on`() {
        assertTrue("off by default", LiveBidJudge.want(view(sinceChangeMs = 600_000L), q) is LiveBidVerdict.Post)
        assertEquals(LiveBidSkip.FAIR_OLD, skip(view(sinceChangeMs = 31_000L), q.copy(maxFairAgeSec = 30)))
    }

    @Test
    fun `the quiet and danger limits can be turned off but a danger frame is never shorter than the feed's own marker`() {
        assertTrue(LiveBidJudge.want(view(quietMs = 300_000L), q.copy(maxQuietSec = 0)) is LiveBidVerdict.Post)
        assertEquals("0 s hold still respects the 3 s marker", LiveBidSkip.DANGER, skip(view(dangerAgeMs = 2_000L), q.copy(dangerHoldSec = 0)))
        assertTrue(LiveBidJudge.want(view(dangerAgeMs = 4_000L), q.copy(dangerHoldSec = 0)) is LiveBidVerdict.Post)
    }

    // ---- a bid comes down ----------------------------------------------------------------------------------------------------------------------------------------------------

    private val held = LiveBidHeld(price = 0.475, postedAtMs = now - 10_000L, midAtPost = 0.485)

    @Test
    fun `a bid that is still justified stays, through a small move`() {
        assertEquals(LiveBidKeep.Keep, LiveBidJudge.keep(view(), q, held))
        assertEquals("Pinnacle's fair fell to 0.49: still 3.2% over the price", LiveBidKeep.Keep, LiveBidJudge.keep(view(fair = 0.49), q, held))
        assertEquals("a price that changed a moment ago does not pull a bid that is already up (only a new bid waits for it to settle)", LiveBidKeep.Keep, LiveBidJudge.keep(view(sinceChangeMs = 200L), q, held))
    }

    @Test
    fun `each reason a bid stops being worth resting names itself`() {
        fun pull(v: LiveBidView, rules: LiveBidQuality = q, h: LiveBidHeld = held) = (LiveBidJudge.keep(v, rules, h) as LiveBidKeep.Pull).reason
        assertEquals(LiveBidSkip.EV, pull(view(fair = 0.478)))
        assertEquals("Pinnacle's price fell to the bid's own (EV 0): pulled", LiveBidSkip.EV, pull(view(fair = 0.475)))
        assertEquals(LiveBidSkip.CLOSED, pull(view(lineOpen = false)))
        assertEquals(LiveBidSkip.QUIET, pull(view(quietMs = 25_000L)))
        assertEquals(LiveBidSkip.DANGER, pull(view(dangerAgeMs = 1_000L)))
        assertEquals("a feed problem pulls at once", "Pinnodds feed quiet", pull(view(problem = "Pinnodds feed quiet")))
        assertEquals(LiveBidSkip.NOVIG_PAUSED, pull(view(novigLive = false)))
        assertEquals(LiveBidSkip.MISMATCH, pull(view(bestBid = 0.30, offer = 0.40)))
        assertEquals("Novig's middle fell 5 points from 0.485 to 0.435", LiveBidSkip.NOVIG_MOVED, pull(view(bestBid = 0.40, offer = 0.47)))
    }

    @Test
    fun `a score after the bid was decided pulls it, one before does not`() {
        // The bid was decided on at now - 10 s. A score 3 s ago came after it; one 40 s ago was already in the price.
        assertEquals(LiveBidSkip.SCORED, (LiveBidJudge.keep(view(scoreAgeMs = 3_000L), q, held) as LiveBidKeep.Pull).reason)
        assertEquals(LiveBidKeep.Keep, LiveBidJudge.keep(view(scoreAgeMs = 40_000L), q, held))
        assertEquals("pull-on-score off", LiveBidKeep.Keep, LiveBidJudge.keep(view(scoreAgeMs = 3_000L), q.copy(pullOnScore = false, scoreHoldSec = 0), held))
    }

    @Test
    fun `the pull floor and the Novig move are the rules' own numbers`() {
        assertEquals("floor 2%: fair 0.483 over 0.475 is 1.7%", LiveBidSkip.EV, (LiveBidJudge.keep(view(fair = 0.483), q.copy(pullBelowEv = 0.02), held) as LiveBidKeep.Pull).reason)
        assertEquals(LiveBidKeep.Keep, LiveBidJudge.keep(view(bestBid = 0.40, offer = 0.47), q.copy(novigMovePull = 0.0), held))
        assertEquals("credit counted lifts the EV", LiveBidKeep.Keep, LiveBidJudge.keep(view(fair = 0.479), q.copy(countCredit = true), held))
    }

    // ---- fair, book, stake --------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `fair comes from the line's own prices by the rules' method, with the margin it carries`() {
        val book = PinnBook()
        book.apply(parsePinnFrame(PinnTestFrames.live(markets = arrayOf(PinnTestFrames.money(1, -150, 125))))!!, 1_000L)
        val line = book.events.getValue(1).lines.getValue("s;0;m")
        val mult = LiveBidFair.of(line, PinnSide.HOME, DevigMethod.MULTIPLICATIVE)!!
        val worst = LiveBidFair.of(line, PinnSide.HOME, DevigMethod.WORST_CASE)!!
        assertEquals("-150 is 60.0%, +125 is 44.4%: a 4.4% margin", 0.0444, mult.overround, 0.001)
        assertEquals(0.6 / (0.6 + 100.0 / 225.0), mult.fair, 1e-6)
        assertTrue("worst case never gives a side more than the plain method", worst.fair <= mult.fair + 1e-9)
        assertEquals(1.0, LiveBidFair.of(line, PinnSide.HOME, DevigMethod.MULTIPLICATIVE)!!.fair + LiveBidFair.of(line, PinnSide.AWAY, DevigMethod.MULTIPLICATIVE)!!.fair, 1e-9)
        assertNull("a side the line does not have", LiveBidFair.of(line, PinnSide.OVER, DevigMethod.WORST_CASE))
    }

    @Test
    fun `the book as a bid sees it leaves its own orders out`() {
        val market = NovigMarket("m", "e", "MONEY", "OPEN", "x", 0L, MarketFee.GAME, listOf(NovigOutcome("a", "A", "TBD"), NovigOutcome("b", "B", "TBD")), 0.0)
        val book = NovigBook("m", 1, mapOf("a" to listOf(BidLevel(480, 1000), BidLevel(450, 5000)), "b" to listOf(BidLevel(500, 3000))), 0L)
        val plain = LiveBidBookView.of(book, market, "a", emptyMap())
        assertEquals(0.48, plain.bestBid!!, 1e-9)
        assertEquals("the offer is 1 - the best bid on the other side", 0.50, plain.offer!!, 1e-9)
        // 1,000 contracts at 0.480 are ours: the best bid of anyone else is 0.450.
        val ours = LiveBidBookView.of(book, market, "a", mapOf("a" to mapOf(480 to 1000L)))
        assertEquals(0.45, ours.bestBid!!, 1e-9)
        // 400 of 1,000 are ours: others still bid there.
        assertEquals(0.48, LiveBidBookView.of(book, market, "a", mapOf("a" to mapOf(480 to 400L))).bestBid!!, 1e-9)
        // Our bid on the other outcome is not liquidity for us.
        assertNull(LiveBidBookView.of(book, market, "a", mapOf("b" to mapOf(500 to 3000L))).offer)
    }

    @Test
    fun `an eighth Kelly stake is a fraction of the bankroll for this bid's own edge, raised to the minimum and held to the cap`() {
        val lim = LiveBidLimits(stakeMode = AutoBetStake.EIGHTH_KELLY, minStake = 1.0, maxStake = 5.0)
        // Full Kelly (0.50 - 0.475) / (1 - 0.475) = 4.76% of the bankroll; an eighth is 0.595%; of $1,000 that is $5.95, held to the $5 cap.
        assertEquals(5.0, LiveBidStake.dollars(lim, 0.50, 0.475, 1000.0, 0.0)!!, 1e-9)
        // Of $500 it is $2.98.
        assertEquals(0.125 * (0.025 / 0.525) * 500.0, LiveBidStake.dollars(lim, 0.50, 0.475, 500.0, 0.0)!!, 1e-9)
        // Of $100 it is $0.60: raised to the $1 minimum.
        assertEquals(1.0, LiveBidStake.dollars(lim, 0.50, 0.475, 100.0, 0.0)!!, 1e-9)
        // Settings' per-bet maximum for the API holds it down.
        assertEquals(2.0, LiveBidStake.dollars(lim, 0.50, 0.475, 1000.0, 2.0)!!, 1e-9)
        assertNull("Kelly needs a bankroll", LiveBidStake.dollars(lim, 0.50, 0.475, 0.0, 0.0))
        assertNull("and an edge", LiveBidStake.dollars(lim, 0.47, 0.475, 1000.0, 0.0))
        assertEquals(3.0, LiveBidStake.dollars(lim.copy(stakeMode = AutoBetStake.CUSTOM, customStake = 3.0), 0.50, 0.475, 0.0, 0.0)!!, 1e-9)
        assertEquals(1.0, LiveBidStake.dollars(lim.copy(stakeMode = AutoBetStake.ONE_DOLLAR), 0.50, 0.475, 0.0, 0.0)!!, 1e-9)
        assertEquals(1052L, LiveBidStake.contracts(5.0, 0.475))
    }
}
