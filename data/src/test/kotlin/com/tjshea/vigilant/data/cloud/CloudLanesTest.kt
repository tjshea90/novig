package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.scanner.FarLane
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLanesTest {
    private val now = 50_000_000L
    private val h = 3_600_000L

    private fun row(id: String, startInH: Long): Opportunity {
        val ev = NovigEvent("e-$id", "BASKETBALL", "NBA", NovigEvent.STATUS_PREGAME, "A @ B", now + startInH * h)
        val m = NovigMarket("m-$id", "e-$id", "MONEY", "OPEN", "Moneyline", now + startInH * h, MarketFee.GAME, listOf(NovigOutcome("o-$id", "B", "TBD"), NovigOutcome("x-$id", "A", "TBD")))
        return Opportunity(
            Leagues.byNovigName("NBA")!!, ev, m, m.outcomes.first(), "Moneyline", LineKind.MONEYLINE, "B", null, 0.55, EvQuote(0.55, 0.48, 0.0), emptyList(), null, null, null,
            bookFetchedAtMs = now, fairUpdatedMs = null, refEvent = null, lineKey = null, target = null, fairAsOfMs = now,
        )
    }

    @Test
    fun `a quick scan follows a full one for six minutes, then a full one again`() {
        val s = ScanSettings(daysAhead = 7)
        assertFalse("no full scan yet", CloudLanes.nearOnly(s, now, 0L, null, "k"))
        assertTrue(CloudLanes.nearOnly(s, now, now - 60_000L, "k", "k"))
        assertTrue(CloudLanes.nearOnly(s, now, now - CloudLanes.FULL_EVERY_MS + 1, "k", "k"))
        assertFalse("past the gap, a full scan", CloudLanes.nearOnly(s, now, now - CloudLanes.FULL_EVERY_MS - 1, "k", "k"))
        assertFalse("changed fetch settings start over with a full scan", CloudLanes.nearOnly(s, now, now - 60_000L, "old", "k"))
    }

    @Test
    fun `the list carries the old rows only for games past the quick scan`() {
        val near = listOf(row("a", 3), row("b", 30))
        val full = listOf(row("a", 3), row("b", 30), row("c", 60), row("d", 100))
        val merged = CloudLanes.merge(near, full, now)
        assertEquals(listOf("o-a", "o-b", "o-c", "o-d"), merged.map { it.outcome.outcomeId })
        assertTrue(CloudLanes.reachMs(now) > now + FarLane.NEAR_HOURS * h)
    }

    @Test
    fun `a scan that has not read a listed row yet does not take it off the list`() {
        val a = row("a", 3); val b = row("b", 3); val c = row("c", 3)
        val oldRead = row("d", 3).let { it.copy(bookFetchedAtMs = now - CloudLanes.CARRY_MAX_MS - 1) }
        val started = row("e", -1)
        // the new scan has read and listed "a", read "b" and found it no longer listed; "c" it has not reached; "d" was read too long ago; "e" has started
        val out = CloudLanes.carryUnread(listOf(a), listOf(a, b, c, oldRead, started), setOf("o-a", "o-b"), now)
        assertEquals(listOf("o-a", "o-c"), out.map { it.outcome.outcomeId })
        assertEquals(listOf(a), CloudLanes.carryUnread(listOf(a), emptyList(), emptySet(), now))
    }
}
