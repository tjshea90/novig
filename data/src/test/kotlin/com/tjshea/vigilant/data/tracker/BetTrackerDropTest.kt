package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BetTrackerDropTest {
    @Test fun old_settled_bets_leave_only_when_github_has_their_day() = runBlocking {
        val dir = createTempDir()
        val t = BetTracker(File(dir, "b.json"))
        val now = 100L * 86_400_000L
        val old = now - 60 * 86_400_000L
        fun bet(id: String, status: BetStatus, at: Long) = BetTrackerTestSupport.bet(id, status, at)
        BetTrackerTestSupport.put(t, listOf(bet("a", BetStatus.WON, old), bet("b", BetStatus.PENDING, old), bet("c", BetStatus.WON, now), bet("d", BetStatus.LOST, old - 86_400_000L * 3)))
        val oldDay = BetHistory.day(old)
        val dropped = t.dropArchived(now, 35) { it == oldDay }
        assertEquals(1, dropped)
        assertEquals(setOf("b", "c", "d"), t.all().map { it.id }.toSet())
    }
}
