package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.PositiveDepth
import com.tjshea.vigilant.engine.TakeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine's real-time list (PB19, Tj 2026-10-25: "keeping positive EV bets in the scanned results until the real time feeds from the books say it is no longer positive EV, or the real time novig
 * socket says it is no longer available at the listed price"): it watches every side within reach of +EV and lists exactly the ones its newest prices make +EV.
 */
class RealtimeListTest {
    private val now = 1_800_000_000_000L
    private val start = now + 3 * 3_600_000L
    private val settings = ScanSettings(leagues = setOf("NBA"))

    private fun side(id: String, p: Double, read: Long = now - 60_000): Opportunity {
        val event = NovigEvent("e$id", "BASKETBALL", "NBA", NovigEvent.STATUS_PREGAME, "Boston Celtics @ New York Knicks", start)
        val market = NovigMarket("m$id", "e$id", "SPREAD", "OPEN", "Spread", start, MarketFee.GAME, listOf(NovigOutcome("o$id", "NYK -3.5", "TBD"), NovigOutcome("x$id", "BOS +3.5", "TBD")), strike = -3.5)
        return Opportunity(
            league = Leagues.byNovigName("NBA")!!, event = event, market = market, outcome = market.outcomes.first(), marketLabel = "Spread", kind = LineKind.SPREAD,
            selection = "NYK -3.5", fair = null, fairProbability = p, quote = EvQuote(p, 0.49, 0.0075),
            ladder = listOf(TakeLevel(0.49, 300)), depth = PositiveDepth(120, 59.1, 2.4, 0.49), suggestedStake = null,
            novigWidth = 0.02, bestBid = 0.47, bidLevels = emptyList(), bookFetchedAtMs = read, fairUpdatedMs = read, refEvent = null,
            lineKey = LineKey("ref-$id", LineKind.SPREAD, -3.5, 0, null, null), target = OutcomeTarget.Is(Side.HOME), fairAsOfMs = read,
        )
    }

    private fun result(vararg o: Opportunity) = ScanResult(emptyList(), o.toList(), ScanStats(1, 1, o.size, o.size, 0), now)

    @Test
    fun `sides near +EV are watched, and each pass lists exactly the ones the newest prices make +EV`() {
        val listed = side("a", 0.52)       // +4.5%
        val near = side("b", 0.48)         // -3.5%: one move from a bet
        val far = side("c", 0.40)          // -20%: not watched
        val r = RealtimeList()
        r.adopt(result(listed, near, far), now)
        assertEquals(2, r.watched)
        // A sharp book moved: the near side is now +EV; the listed one's fair fell, it leaves.
        val list = r.pass(settings, now) { rows -> rows.map { o -> if (o.key == near.key) o.copy(fairProbability = 0.53, quote = EvQuote(0.53, 0.49, 0.0075)) else o.copy(fairProbability = 0.49, quote = EvQuote(0.49, 0.49, 0.0075)) } }
        assertEquals(listOf(near.key), list.map { it.key })
        assertEquals(1L, r.turnedPositive)
        assertEquals(1L, r.dropped)
        assertTrue(r.describe().contains("1 turned +EV"))
    }

    @Test
    fun `a scan's row replaces a held one only when it was read more recently, and started games leave`() {
        val r = RealtimeList()
        r.adopt(result(side("a", 0.52, read = now - 10_000)), now)
        // An older read of the same side from a scan still running does not undo the newer price.
        r.adopt(result(side("a", 0.48, read = now - 120_000)), now)
        assertEquals(listOf(0.52), r.pass(settings, now) { it }.map { it.fairProbability })
        // Once the game has started it is not a pregame bet: no longer watched.
        r.adopt(result(), start + 1)
        assertEquals(0, r.watched)
    }

    private fun ref(id: String) = RefEvent(id, "basketball_nba", start, "New York Knicks", "Boston Celtics", emptyList())

    private fun waiting(vararg o: Opportunity) = result(*o).copy(freshSinceMs = now - 30_000, waitingFor = setOf(ScanResult.waitKey("NBA", props = false), ScanResult.waitKey("NBA", props = true)))

    /** PX9: GB@DET's spread was listed with Kalshi alone beside the same game's 1st-half total priced by 7 feeds: a partial scan's league still waiting for a fair source was adopted. */
    @Test
    fun `a league still waiting for a fair-odds source is not adopted from a partial scan, and its rows come once it has answered`() {
        val r = RealtimeList()
        val partial = side("a", 0.52).copy(refEvent = ref("kalshi:K1"))
        r.adopt(waiting(partial), now)
        assertEquals(0, r.watched)
        assertEquals(1L, r.skippedWaiting)
        // The scan finishes: every source answered, the row is priced from all of them.
        val full = side("a", 0.51).copy(refEvent = ref("kalshi:K1+pl:9+pl:9+parlay:P"))
        r.adopt(result(full), now)
        assertEquals(listOf(0.51), r.pass(settings, now) { it }.map { it.fairProbability })
    }

    /** The finished scan's fuller fair puts the side out of reach: the row is gone, not left priced on the thinner fair it had. */
    @Test
    fun `a newer scan row that is out of reach takes the held row with it`() {
        val r = RealtimeList()
        r.adopt(result(side("a", 0.52, read = now - 60_000)), now)
        assertEquals(1, r.watched)
        r.adopt(result(side("a", 0.40, read = now - 5_000)), now)
        assertEquals(0, r.watched)
        assertTrue(r.pass(settings, now) { it }.isEmpty())
    }

    /** A scan row read earlier than the held row's live Novig price but matched to at least as many feeds gives the held row its fair; one matched to fewer feeds does not. */
    @Test
    fun `a scan that matched as many feeds gives the held row its fair even when its Novig read is older, a poorer one does not`() {
        val r = RealtimeList()
        val held = side("a", 0.52, read = now - 2_000).copy(refEvent = ref("kalshi:K1"))
        r.adopt(result(held), now)
        val seen = ArrayList<Pair<Double?, Double?>>()
        val rebase: (Opportunity, Opportunity) -> Opportunity? = { h, s -> seen += h.fairProbability to s.fairProbability; h.copy(fairProbability = s.fairProbability, quote = EvQuote(s.fairProbability!!, 0.49, 0.0075), refEvent = s.refEvent) }
        // Read earlier (older book) but from 3 feeds against the held row's 1: its fair applies, here 0.53.
        val richer = side("a", 0.53, read = now - 90_000).copy(refEvent = ref("kalshi:K1+pl:9+parlay:P"), fairAsOfMs = now - 90_000)
        r.adopt(result(richer), now, rebase)
        assertEquals(listOf(0.52 to 0.53), seen)
        assertEquals(listOf(0.53), r.pass(settings, now) { it }.map { it.fairProbability })
        assertEquals(1L, r.rebased)
        // A later scan that lost a feed (1 against the held 3) does not undo it.
        val poorer = side("a", 0.45, read = now - 80_000).copy(refEvent = ref("kalshi:K1"), fairAsOfMs = now - 80_000)
        r.adopt(result(poorer), now, rebase)
        assertEquals(listOf(0.53), r.pass(settings, now) { it }.map { it.fairProbability })
        // And a rebased fair that leaves reach drops the row.
        val out = side("a", 0.40, read = now - 70_000).copy(refEvent = ref("kalshi:K1+pl:9+parlay:P+sx:S"), fairAsOfMs = now - 70_000)
        r.adopt(result(out), now, rebase)
        assertEquals(0, r.watched)
    }
}
