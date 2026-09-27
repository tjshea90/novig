package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.betSlipLink
import com.tjshea.vigilant.app.ui.nextScanner
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.tracker.PlacedBet
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-09-27: "some bets are showing up in the vigilant positive EV scanner which I already
 * placed in the cno scanner widget … hides bets I already placed throughout the whole app
 * regardless of scanner". A bet marked on either scanner, or in the tracker, is the same bet
 * everywhere: the +EV feed, the widget, the mini window and the scan-done count.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlacedEverywhereTest {

    private val now = SampleScan.NOW
    private val vigilant = SampleScan.state()
    private val o = vigilant.feed.first()

    /** How CNO's widget saved it: CNO's key, CNO's wording, no link to Vigilant's outcome. */
    private fun cnoMark(market: String = o.marketLabel, selection: String = o.selection) = PlacedBet(
        key = "cno:some-cno-row", title = selection, placedAtMs = now,
        startsAtMs = o.event.startsTs, event = o.event.description, market = market,
    )

    @Test
    fun `a bet placed on CNO's widget leaves Vigilant's +EV feed and widget`() {
        val before = vigilant.indexed(now)
        assertTrue(before.feed.any { it.key == o.key }) // listed before
        val after = vigilant.copy(placed = listOf(cnoMark())).indexed(now)
        assertFalse(after.feed.any { it.key == o.key })
        val widget = after.copy(settings = after.settings.copy(scanner = ScannerMode.VIGILANT))
        assertFalse(MiniWindow.items(widget, now).any { it.key == o.key })
        // Everything else still shows.
        assertEquals(before.feed.size - 1, after.feed.size)
    }

    @Test
    fun `a bet already in the tracker is gone from the feed from the start`() {
        // The sample tracker holds Dallas's moneyline (b3): the scan lists it, the feed doesn't.
        assertTrue(vigilant.feed.any { it.key == "g1-ml/g1-ml-h" })
        assertFalse(vigilant.indexed(now).feed.any { it.key == "g1-ml/g1-ml-h" })
    }

    @Test
    fun `a bet placed on the Novig outcome itself is hidden whatever the wording`() {
        val mark = PlacedBet(key = "cno:x", title = "whatever CNO called it", placedAtMs = now, outcomeId = o.outcome.outcomeId)
        assertFalse(vigilant.copy(placed = listOf(mark)).indexed(now).feed.any { it.key == o.key })
    }

    @Test
    fun `a bet only in the tracker is hidden too, and undoing it brings it back`() {
        val bet = TrackedBet(
            id = "t", createdAtMs = now, league = o.event.league, eventName = o.event.description, startsTs = o.event.startsTs,
            marketLabel = o.marketLabel, selection = o.selection, marketId = "", outcomeId = "", price = 0.5, cost = 0.5,
            fairAtBet = null, evPercentAtBet = null, stake = 1.0,
        )
        val tracked = vigilant.copy(bets = vigilant.bets + bet).indexed(now)
        assertFalse(tracked.feed.any { it.key == o.key })
        assertTrue(tracked.copy(bets = vigilant.bets).indexed(now).feed.any { it.key == o.key })
    }

    @Test
    fun `a same-named bet on a different day's game still shows`() {
        val tomorrow = cnoMark().copy(startsAtMs = o.event.startsTs + 24 * 3_600_000L)
        assertTrue(vigilant.copy(placed = listOf(tomorrow)).indexed(now).feed.any { it.key == o.key })
    }

    @Test
    fun `Vigilant's bet sheet opens the exact bet slip`() {
        assertEquals("novigapp://events/${o.outcome.outcomeId}", betSlipLink(o))
        // The same link the widget and the mini window open for that bet.
        val item = MiniWindow.items(vigilant.copy(settings = vigilant.settings.copy(scanner = ScannerMode.VIGILANT)), now).first { it.key == o.key }
        assertEquals(MiniWindow.novigLink(item), betSlipLink(o))
    }

    @Test
    fun `the widget's switch goes CNO only, Both, Vigilant only, and round again`() {
        assertEquals(ScannerMode.BOTH, nextScanner(ScannerMode.CNO))
        assertEquals(ScannerMode.VIGILANT, nextScanner(ScannerMode.BOTH))
        assertEquals(ScannerMode.CNO, nextScanner(ScannerMode.VIGILANT))
    }
}
