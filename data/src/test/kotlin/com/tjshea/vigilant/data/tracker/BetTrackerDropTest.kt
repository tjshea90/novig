package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BetTrackerDropTest {
    private fun bet(id: String, status: BetStatus, at: Long) = TrackedBet(
        id = "$id-0000-0000", createdAtMs = at, league = "NFL", eventName = "Ravens @ Cowboys", startsTs = at + 3_600_000L, marketLabel = "Player Receptions", selection = "Joe Over 4.5",
        marketId = "m1", outcomeId = "o1", price = 0.488, cost = 0.488, fairAtBet = 0.508, evPercentAtBet = 0.04, stake = 2.0, status = status,
        settledAtMs = if (status == BetStatus.PENDING) null else at + 7_200_000L, source = BetTracker.SOURCE_CNO, american = 105,
    )

    @Test fun `old settled bets leave only when GitHub has their day`() = runBlocking {
        val t = BetTracker(File(java.nio.file.Files.createTempDirectory("bt").toFile(), "b.json"))
        val now = 100L * 86_400_000L
        val old = now - 60 * 86_400_000L
        val other = old - 3 * 86_400_000L
        t.addAll(listOf(bet("a", BetStatus.WON, old), bet("b", BetStatus.PENDING, old), bet("c", BetStatus.WON, now - 3_600_000L), bet("d", BetStatus.LOST, other)))
        val oldDay = BetHistory.day(old)
        // a (settled) shares its game day with b (still open): the day stays whole until b settles; d's day is entirely settled but GitHub does not have it
        assertEquals(0, t.dropArchived(now, 35) { it == oldDay })
        assertEquals(setOf("a", "b", "c", "d"), t.all().map { it.id.take(1) }.toSet())
    }

    @Test fun `a finished day leaves as one daily number worked out once, never counted twice`() = runBlocking {
        SlateArchive.reset()
        val t = BetTracker(File(java.nio.file.Files.createTempDirectory("bt2").toFile(), "b.json"))
        val now = 100L * 86_400_000L
        val old = now - 60 * 86_400_000L
        t.addAll(listOf(bet("a", BetStatus.WON, old), bet("c", BetStatus.WON, now - 3_600_000L)))
        val day = BetHistory.day(old)
        assertEquals(0, t.dropArchived(now, 35, withMoney = { null }) { it == day })   // money not known yet: nothing goes
        assertEquals(1, t.dropArchived(now, 35) { it == day })
        val book = SlateProfit.book(t.all(), now)
        assertEquals(2, book.days.size)
        assertEquals(1, book.days.count { it.settled == 1 && it.profit > 0.0 && it.open == 0 } .coerceAtLeast(1))
        val before = SlateArchive.days.sumOf { it.profit }
        SlateArchive.record(SlateArchive.days)   // recording the same day again replaces it
        assertEquals(before, SlateArchive.days.sumOf { it.profit }, 1e-9)
        assertEquals(1, SlateArchive.days.size)
        SlateArchive.reset()
    }
}
