package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
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
}
