package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.bookPropEstimate
import com.tjshea.vigilant.app.ui.creditEstimate
import com.tjshea.vigilant.app.ui.minutesLabel
import com.tjshea.vigilant.app.ui.sourceNames
import com.tjshea.vigilant.app.ui.sourceSummary
import com.tjshea.vigilant.data.scanner.SourceReport
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

    /** RESEARCH.md §23: behind PropLine, The Odds API spends nothing while PropLine answers, and says so. */
    @Test
    fun `behind PropLine the estimates say credits go only to what PropLine couldn't give`() {
        val lines = creditEstimate(ScanSettings(leagues = setOf("NFL", "MLB")), backup = true)
        assertTrue(lines, lines.startsWith("Nothing while PropLine answers. When it can't, game lines cost about 6 credits per refresh"))
        val props = bookPropEstimate(ScanSettings(bookPropCreditsPerScan = 24, bookPropReuseMinutes = 60), backup = true)
        assertTrue(props, props.startsWith("Only games and prop types PropLine didn't price this scan"))
        assertTrue(props, props.contains("never more than 24 credits a scan"))
    }

    @Test
    fun `the scan line names a backup API that stood by`() {
        val status = ScanStatus(
            sources = listOf(
                SourceReport("pinnacle", "Pinnacle", 1, 0, 12, null),
                SourceReport("propline", "PropLine", 1, 0, 14, null),
                SourceReport("oddsapi", "The Odds API", 0, 0, 0, null, standingBy = 1),
            ),
        )
        assertEquals("Pinnacle 12 · PropLine 14 games · The Odds API on standby", sourceSummary(status))
        // Called after all (PropLine couldn't answer): no standby note.
        val called = status.copy(sources = status.sources.map { if (it.id == "oddsapi") it.copy(fetched = 1, matched = 14, standingBy = 0) else it })
        assertEquals("Pinnacle 12 · PropLine 14 · The Odds API 14 games", sourceSummary(called))
    }

    @Test
    fun `the feed names The Odds API as PropLine's backup`() {
        val s = SampleScan.state()
        assertEquals("Pinnacle, Polymarket, Kalshi and PropLine (The Odds API as backup)", sourceNames(s))
        assertEquals("Pinnacle, Polymarket, Kalshi and The Odds API", sourceNames(s.copy(proplineKeys = emptyList())))
    }
}
