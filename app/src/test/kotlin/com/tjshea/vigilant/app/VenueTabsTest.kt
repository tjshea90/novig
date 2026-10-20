package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.kalshitrade.VenueMode
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** KV6: the venue switches decide the tabs: Novig's tabs need Novig, the Kalshi tab needs Kalshi, the shared ones are always there, and the app as it was (no venues) is unchanged. */
class VenueTabsTest {
    private val mode = ScannerMode.BOTH

    @Test
    fun `with no venue information every tab is what it was and Kalshi has none`() {
        for (t in Tab.entries) {
            if (t == Tab.KALSHI) assertFalse(t.shownIn(mode, null)) else assertEquals(t.name, t.shownIn(mode), t.shownIn(mode, null))
        }
    }

    @Test
    fun `Novig only is the app as it was, Kalshi only drops the Novig tabs, both shows all`() {
        val novigOnly = VenueMode(novig = true, kalshi = false)
        for (t in Tab.entries) if (t != Tab.KALSHI) assertEquals(t.name, t.shownIn(mode), t.shownIn(mode, novigOnly))
        assertFalse(Tab.KALSHI.shownIn(mode, novigOnly))

        val kalshiOnly = VenueMode(novig = false, kalshi = true)
        assertTrue(Tab.KALSHI.shownIn(mode, kalshiOnly))
        assertTrue(Tab.EV.shownIn(mode, kalshiOnly))
        for (t in listOf(Tab.CNO, Tab.BIDS, Tab.LIVE, Tab.AUTOBET)) assertFalse(t.name, t.shownIn(mode, kalshiOnly))
        for (t in listOf(Tab.ENGINE, Tab.CLAUDE, Tab.TRACKER, Tab.SETTINGS)) assertTrue(t.name, t.shownIn(mode, kalshiOnly))

        val both = VenueMode(novig = true, kalshi = true)
        assertTrue(Tab.KALSHI.shownIn(mode, both))
        assertTrue(Tab.EV.shownIn(mode, both))
        assertTrue(both.both)
    }

    @Test
    fun `neither venue still leaves the engine, chat, tracker and settings`() {
        val none = VenueMode(novig = false, kalshi = false)
        val shown = Tab.entries.filter { it.shownIn(mode, none) }
        assertEquals(setOf(Tab.ENGINE, Tab.CLAUDE, Tab.TRACKER, Tab.SETTINGS), shown.toSet())
    }
}
