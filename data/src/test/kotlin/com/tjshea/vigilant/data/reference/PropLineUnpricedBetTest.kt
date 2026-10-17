package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cloud.CloudCodec
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TCL88 (Tj, 2026-10-17): a PropLine +EV row on a Novig market Vigilant had no fair for still gets a Bet button, priced from PropLine's fair. */
class PropLineUnpricedBetTest {
    private val start = System.currentTimeMillis() + 6 * 3_600_000L

    private fun unpriced(ladder: List<TakeLevel> = listOf(TakeLevel(0.46, 800)), live: Boolean = false): Opportunity {
        val event = NovigEvent("e1", "FOOTBALL", "NCAAF", if (live) NovigEvent.STATUS_LIVE else NovigEvent.STATUS_PREGAME, "Holy Cross @ Harvard", start)
        val market = NovigMarket("m1", "e1", "TOTAL", "OPEN", "Total", start, MarketFee.GAME, listOf(NovigOutcome("o1", "Over 51.5", "TBD"), NovigOutcome("o2", "Under 51.5", "TBD")), strike = 51.5)
        return Opportunity(
            league = Leagues.byNovigName("NCAAF")!!, event = event, market = market, outcome = market.outcomes.first(), marketLabel = "Total", kind = LineKind.TOTAL,
            selection = "Over 51.5", fair = null, fairProbability = null, quote = null, ladder = ladder, depth = null, suggestedStake = null, novigWidth = null,
            bookFetchedAtMs = System.currentTimeMillis(), fairUpdatedMs = null, refEvent = null,
            lineKey = LineKey("r1", LineKind.TOTAL, 51.5, 0), target = OutcomeTarget.Is(Side.OVER),
        )
    }

    private fun row() = PlEvRow(
        sport = "americanfootball_ncaaf", league = "NCAAF", eventId = "x", home = "Harvard", away = "Holy Cross", startsAtMs = start, market = "totals", player = "",
        point = 51.5, outcome = "Over", price = 117, evPct = 9.0, fairProb = 0.5, fairSource = "kalshi", updatedAtMs = System.currentTimeMillis(),
    )

    @Test
    fun `a line Vigilant had no fair for is priced from PropLine's fair and carries the slip, quote and stake`() {
        val r = PropLineMatch.attachUnpriced(row(), listOf(unpriced()), ScanSettings(), System.currentTimeMillis())
        assertTrue(r.oppFromPropLine)
        val o = CloudCodec.decode(kotlinx.serialization.json.Json.parseToJsonElement(r.opp!!).let { it as kotlinx.serialization.json.JsonObject })!!
        assertEquals(0.5, o.fairProbability!!, 1e-9)
        assertNotNull(o.quote)
        assertTrue("+EV at 0.46 against a 0.50 fair", o.evPercent!! > 0)
        assertEquals("o1", o.outcome.outcomeId)
    }

    @Test
    fun `no ladder or a live game means no button`() {
        assertNull(PropLineMatch.attachUnpriced(row(), listOf(unpriced(ladder = emptyList())), ScanSettings(), System.currentTimeMillis()).opp)
        assertNull(PropLineMatch.attachUnpriced(row(), listOf(unpriced(live = true)), ScanSettings(), System.currentTimeMillis()).opp)
    }
}
