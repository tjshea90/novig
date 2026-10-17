package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.LiveAges
import com.tjshea.vigilant.data.scanner.OddsAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How old the odds are (Tj, 2026-10-11), and what leaves the Feed when "Include live games" is on. */
class OddsAgeTest {
    private val o = SampleScan.state().feed.first { it.quote != null }

    @Test
    fun `spans are said in seconds, then minutes, then hours`() {
        assertEquals("<1 s", OddsAge.span(400))
        assertEquals("7 s", OddsAge.span(7_900))
        assertEquals("3 min", OddsAge.span(180_000))
        assertEquals("2 h", OddsAge.span(7_300_000))
        assertEquals("<1 s", OddsAge.span(-5_000))
    }

    @Test
    fun `a scanned row says when Novig was read and how old the other books are, or that it does not know`() {
        val r = o.copy(bookFetchedAtMs = 100_000L, fairUpdatedMs = 110_000L, fairAsOfMs = 40_000L, liveAges = null)
        assertEquals("Novig read 20 s ago · other books' prices 10 s to 80 s old", OddsAge.text(r, 120_000L))
        assertTrue(OddsAge.text(r.copy(fairAsOfMs = null, fairUpdatedMs = null), 120_000L)!!.contains("other books' ages unknown"))
    }

    @Test
    fun `a live row says the four feed ages exactly`() {
        val r = o.copy(liveAges = LiveAges(pinnLineChangedMs = 100_000L, pinnFeedMs = 118_000L, novigMarketMs = 95_000L, novigFeedMs = 119_000L))
        assertEquals("Live odds · Pinnacle line changed 20 s ago (its feed 2 s ago) · Novig market changed 25 s ago (its feed 1 s ago)", OddsAge.text(r, 120_000L))
        assertTrue(OddsAge.text(r.copy(liveAges = r.liveAges!!.copy(novigMarketMs = null)), 120_000L)!!.contains("market unchanged since it was first read"))
    }

    @Test
    fun `with live games on, a scanned row of a game that has started leaves the feed, a live-lane row stays`() {
        val now = o.event.startsTs + 60_000L
        val started = o.copy(fairAsOfMs = now, bookFetchedAtMs = now)
        val lane = started.copy(liveAges = LiveAges(now, now, now, now))
        val base = SampleScan.state()
        val off = base.copy(settings = base.settings.copy(includeLive = false), feed = listOf(started, lane))
        assertEquals("off: nothing changes", 2, off.feedAt(now).size)
        val on = base.copy(settings = base.settings.copy(includeLive = true), feed = listOf(started, lane))
        assertEquals(listOf(lane), on.feedAt(now))
        val before = base.copy(settings = base.settings.copy(includeLive = true), feed = listOf(started))
        assertEquals("before the start it is a pregame bet like any other", 1, before.feedAt(o.event.startsTs - 3_600_000L).size)
    }

    @Test
    fun `a bet whose Novig price was read more than ten minutes ago leaves the main list for the old list, an unknown age does not`() {
        val now = o.event.startsTs - 3_600_000L
        val base = SampleScan.state()
        val fresh = o.copy(fairAsOfMs = now, bookFetchedAtMs = now - 60_000L)
        val old = o.copy(fairAsOfMs = now, bookFetchedAtMs = now - 11 * 60_000L)
        val unknown = o.copy(fairAsOfMs = now, bookFetchedAtMs = null)
        val s = base.copy(feed = listOf(fresh, old, unknown))
        assertEquals(listOf(fresh, unknown), s.feedAt(now))
        assertEquals(listOf(old), s.feedOldAt(now))
    }

    @Test
    fun `the Feed can be ordered by stars, best rated first, the bigger edge first among equals`() {
        val rated = SampleScan.state().feed.filter { it.rating != null }
        org.junit.Assert.assertTrue("the sample has rated bets", rated.size >= 3)
        val sorted = com.tjshea.vigilant.data.scanner.FeedOrder.sort(rated, com.tjshea.vigilant.data.scanner.FeedSort.STARS)
        val stars = sorted.map { it.rating!!.stars }
        assertEquals(stars.sortedDescending(), stars)
        sorted.zipWithNext().filter { (a, b) -> a.rating!!.stars == b.rating!!.stars }.forEach { (a, b) -> org.junit.Assert.assertTrue(a.evPercent!! >= b.evPercent!!) }
        // The other orders are as they were.
        val byEv = com.tjshea.vigilant.data.scanner.FeedOrder.sort(rated, com.tjshea.vigilant.data.scanner.FeedSort.EV)
        assertEquals(byEv.map { it.evPercent }, byEv.map { it.evPercent }.sortedByDescending { it })
    }
}
