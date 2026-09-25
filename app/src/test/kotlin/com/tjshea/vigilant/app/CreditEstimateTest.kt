package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.bookPropEstimate
import com.tjshea.vigilant.app.ui.creditEstimate
import com.tjshea.vigilant.app.ui.minutesLabel
import com.tjshea.vigilant.data.scanner.BookPropSet
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings screen's credit math matches what the app actually buys. */
class CreditEstimateTest {

    @Test
    fun `game lines count only the markets The Odds API is asked for, not the alt markets`() {
        // Every family on (6), but only moneyline, spread and total are bought per league.
        val text = creditEstimate(ScanSettings(leagues = setOf("NFL", "MLB")))
        assertTrue(text, text.startsWith("Game lines cost about 6 credits per refresh (2 leagues × 3 markets)."))
        assertEquals(
            "No main-line markets are on, so game lines cost nothing.",
            creditEstimate(ScanSettings(families = setOf(MarketFamily.PLAYER_PROPS))),
        )
    }

    @Test
    fun `the props estimate says how many games a scan's credits buy`() {
        val core = bookPropEstimate(ScanSettings(bookPropSet = BookPropSet.CORE, bookPropCreditsPerScan = 24, bookPropReuseMinutes = 60))
        assertTrue(core, core.contains("up to 6 games a scan"))
        assertTrue(core, core.contains("re-used for 1h"))
        assertTrue(bookPropEstimate(ScanSettings(bookPropCreditsPerScan = 0)).startsWith("No credits"))
        assertEquals("30m", minutesLabel(30))
        assertEquals("4h", minutesLabel(240))
    }
}
