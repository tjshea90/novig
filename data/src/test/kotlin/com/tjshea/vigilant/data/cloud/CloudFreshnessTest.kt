package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFreshnessTest {
    private val now = 50_000_000L

    private fun row(novigAgeS: Long?, fairAgeS: Long?, id: String = "a"): Opportunity {
        val ev = NovigEvent("e-$id", "BASKETBALL", "NBA", NovigEvent.STATUS_PREGAME, "A @ B", now + 3_600_000L)
        val m = NovigMarket("m-$id", "e-$id", "MONEY", "OPEN", "Moneyline", now + 3_600_000L, MarketFee.GAME, listOf(NovigOutcome("o-$id", "B", "TBD"), NovigOutcome("x-$id", "A", "TBD")))
        return Opportunity(
            Leagues.byNovigName("NBA")!!, ev, m, m.outcomes.first(), "Moneyline", LineKind.MONEYLINE, "B", null, 0.55, EvQuote(0.55, 0.48, 0.0), emptyList(), null, null, null,
            bookFetchedAtMs = novigAgeS?.let { now - it * 1000L }, fairUpdatedMs = null, refEvent = null, lineKey = null, target = null, fairAsOfMs = fairAgeS?.let { now - it * 1000L },
        )
    }

    private val alive = CloudStatus(heartbeatMs = now - 4_000, computedAtMs = now - 20_000, rows = 3)

    @Test
    fun `the chip is the median and the oldest of what is shown, and says how they were worked out`() {
        val c = CloudFreshness.of(listOf(row(5, 60, "a"), row(9, 90, "b"), row(40, 200, "c")), alive, now)
        assertEquals(CloudText.Level.GOOD, c.level)
        assertEquals("Novig 9s (oldest 40s) · fair 1m 30s", c.label)
        assertTrue(c.detail, c.detail.contains("Across the 3 bets shown"))
        assertTrue(c.detail, c.detail.contains("the engine reported 4s ago; its last list went out 20s ago"))
    }

    @Test
    fun `old Novig prices, a silent engine or an engine that is off are never green`() {
        assertEquals(CloudText.Level.WARN, CloudFreshness.of(listOf(row(120, 60)), alive, now).level)
        assertEquals(CloudText.Level.BAD, CloudFreshness.of(listOf(row(5, 60), row(700, 60, "b")), alive, now).level)
        assertEquals(CloudText.Level.WARN, CloudFreshness.of(listOf(row(5, 900)), alive, now).level)
        val silent = CloudFreshness.of(listOf(row(5, 60)), alive.copy(heartbeatMs = now - 300_000), now)
        assertEquals(CloudText.Level.BAD, silent.level); assertTrue(silent.detail.contains("silent for 5m 00s"))
        assertEquals(CloudText.Level.BAD, CloudFreshness.of(listOf(row(5, 60)), alive.copy(idle = true), now).level)
        assertEquals(CloudText.Level.BAD, CloudFreshness.of(listOf(row(5, 60)), null, now, "no GitHub token saved").level)
    }

    @Test
    fun `with nothing shown there is no price to date, and a rows without stamps are not guessed`() {
        val none = CloudFreshness.of(emptyList(), alive, now)
        assertEquals(CloudText.Level.INFO, none.level); assertEquals("GitHub: no bets listed", none.label)
        // a row with no Novig stamp counts toward nothing
        assertEquals(CloudText.Level.INFO, CloudFreshness.of(listOf(row(null, null)), alive, now).level)
    }
}
