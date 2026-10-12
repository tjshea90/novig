package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.pinnodds.PinnBook
import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.pinnodds.PinnSocketState
import com.tjshea.vigilant.data.pinnodds.parsePinnFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BoltOdds' soft-book consensus spoken as Pinnodds frames (Tj, 2026-10-12): the frames go into a real [PinnBook], the book the live lane reads, so what the lane would see is what is tested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoltLiveFeedTest {
    private val base = 1_800_000_000_000L
    private val start = base - 20 * 60_000L
    private val slugs = listOf("draftkings", "fanduel", "caesars", "hardrock", "betrivers")

    private fun update(slug: String, homeMl: Int, awayMl: Int, at: Long, live: Boolean = true, game: String = "Miami Heat vs Boston Celtics, 2026-10-12, 01"): BoltMessage.GameUpdate {
        fun o(label: String, odds: Int, name: String, target: String? = null, line: Double? = null, ou: String? = null) = BoltOutcome(label, odds, name, line, ou, target, at)
        return BoltMessage.GameUpdate(
            at,
            BoltData(
                sport = "NBA", sportsbook = slug, game = game, home = "Boston Celtics", away = "Miami Heat", startMs = start, infoId = "1",
                outcomes = listOf(
                    o("ml h", homeMl, "Moneyline", "Boston Celtics"), o("ml a", awayMl, "Moneyline", "Miami Heat"),
                    o("sp h", -110, "Spread", "Boston Celtics", -3.5), o("sp a", -110, "Spread", "Miami Heat", 3.5),
                    o("o", -110, "Total", null, 220.5, "Over"), o("u", -110, "Total", null, 220.5, "Under"),
                ),
                removed = emptyList(), live = live,
            ),
        )
    }

    private class Rig(val feed: BoltLiveFeed, val board: BoltBoard, val book: PinnBook, val frames: MutableList<String>)

    private fun kotlinx.coroutines.test.TestScope.rig(): Rig {
        val clock = { base + currentTime }
        val board = BoltBoard(clock)
        val book = PinnBook()
        val frames = ArrayList<String>()
        val feed = BoltLiveFeed(
            board = board, socketLive = { true }, ensureOpen = {},
            sink = { text, at -> frames += text; parsePinnFrame(text)?.let { book.apply(it, at) } },
            scope = backgroundScope, rules = { BoltLiveConsensus.Rules() }, clock = clock,
        )
        return Rig(feed, board, book, frames)
    }

    @Test
    fun `a push becomes frames within the debounce, and the lane's book reads a live game with its three lines`() = runTest {
        val r = rig()
        r.feed.start()
        advanceTimeBy(10)
        slugs.forEach { r.board.apply(update(it, -150, 130, base + currentTime)) }
        advanceTimeBy(150)
        val e = r.book.events.values.single()
        assertTrue(e.live)
        assertEquals("Boston Celtics", e.home)
        assertEquals("Miami Heat", e.away)
        assertEquals(3, e.sportId)
        val ml = e.lines.values.single { it.type == PinnLineType.MONEYLINE }
        assertTrue(ml.open)
        assertEquals(1.0, ml.fair.getValue(PinnSide.HOME) + ml.fair.getValue(PinnSide.AWAY), 1e-6)
        assertTrue(ml.fair.getValue(PinnSide.HOME) > 0.55)
        val sp = e.lines.values.single { it.type == PinnLineType.SPREAD }
        assertEquals(-3.5, sp.points!!, 0.0)
        assertEquals(220.5, e.lines.values.single { it.type == PinnLineType.TOTAL }.points!!, 0.0)
        assertTrue("the engine hears from the feed", r.feed.lastFrameAtMs > 0L)
        assertTrue(r.feed.state.value is PinnSocketState.Live || r.feed.state.value is PinnSocketState.Connecting)
    }

    @Test
    fun `a move of the books is a new version of the line, an unchanged board is not news`() = runTest {
        val r = rig()
        r.feed.start()
        advanceTimeBy(10)
        slugs.forEach { r.board.apply(update(it, -150, 130, base + currentTime)) }
        advanceTimeBy(150)
        val before = r.book.events.values.single().lines.values.single { it.type == PinnLineType.MONEYLINE }
        val v0 = before.version
        val f0 = before.fair.getValue(PinnSide.HOME)
        // Everybody reprices toward Boston.
        slugs.forEach { r.board.apply(update(it, -190, 160, base + currentTime)) }
        advanceTimeBy(150)
        val after = r.book.events.values.single().lines.values.single { it.type == PinnLineType.MONEYLINE }
        assertTrue(after.version > v0)
        assertTrue(after.fair.getValue(PinnSide.HOME) > f0)
        // The same prices again: the line is not touched.
        val v1 = after.version
        slugs.forEach { r.board.apply(update(it, -190, 160, base + currentTime)) }
        advanceTimeBy(1_500)
        assertEquals(v1, r.book.events.values.single().lines.values.single { it.type == PinnLineType.MONEYLINE }.version)
    }

    @Test
    fun `lines close when the quorum is lost - the books go quiet`() = runTest {
        val r = rig()
        r.feed.start()
        advanceTimeBy(10)
        slugs.forEach { r.board.apply(update(it, -150, 130, base + currentTime)) }
        advanceTimeBy(150)
        assertTrue(r.book.events.values.single().lines.values.all { it.open })
        // 12 s of silence: no company has pushed inside the 10 s window.
        advanceTimeBy(12_000)
        val e = r.book.events.values.singleOrNull()
        assertTrue("every line is closed (or the game dropped)", e == null || e.lines.values.none { it.open })
        assertTrue(r.feed.linesClosed >= 3)
    }

    @Test
    fun `a game that is not flagged live is never spoken`() = runTest {
        val r = rig()
        r.feed.start()
        advanceTimeBy(10)
        slugs.forEach { r.board.apply(update(it, -150, 130, base + currentTime, live = false)) }
        advanceTimeBy(1_500)
        assertTrue(r.book.events.isEmpty())
        assertFalse(r.frames.any { "\"markets\"" in it && "\"open\"" in it })
        assertNotNull(r.feed)
    }
}
