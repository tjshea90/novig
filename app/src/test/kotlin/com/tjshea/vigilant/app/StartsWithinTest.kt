package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.laterText
import com.tjshea.vigilant.app.ui.startsWithinLabel
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-09-27: "Make it so I can add a filter to only show games that start within the next 24
 * hours or 12 hours or 48 hours." [SampleScan]'s MLB games start in 6-7 h, its NFL games in 50-54 h;
 * [SampleCno]'s kept bets in 8 h (Ohio) and 24 h (the other three).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StartsWithinTest {

    private val now = SampleScan.NOW
    private fun UiState.within(h: Int) = copy(settings = settings.copy(startsWithinHours = h))

    @Test
    fun `the choices are any, 12, 24 and 48 hours, any by default`() {
        assertEquals(listOf(0, 12, 24, 48), ScanSettings.STARTS_WITHIN_CHOICES)
        assertEquals(0, ScanSettings().startsWithinHours)
        assertEquals(listOf("Any", "12h", "24h", "48h"), ScanSettings.STARTS_WITHIN_CHOICES.map(::startsWithinLabel))
    }

    @Test
    fun `the +EV feed shows only games starting within the window`() {
        val s = SampleScan.state().indexed(now)
        val all = s.feedAt(now)
        assertTrue(all.any { it.league.novigName == "NFL" } && all.any { it.league.novigName == "MLB" })
        for (h in listOf(12, 24, 48)) {
            val shown = s.within(h).feedAt(now)
            assertTrue(shown.isNotEmpty())
            assertTrue(shown.all { it.event.startsTs <= now + h * 3_600_000L })
            assertEquals(all.filter { it.league.novigName == "MLB" }.map { it.key }, shown.map { it.key })
        }
        // A window wide enough for the NFL games brings them back.
        assertEquals(all.size, s.within(0).feedAt(now).size)
        // The tab badge and the widget follow the feed.
        val widget = s.within(24).copy(settings = s.within(24).settings.copy(scanner = ScannerMode.VIGILANT))
        assertTrue(MiniWindow.items(widget, now).none { it.title.contains("Ravens") || it.title.contains("Chiefs") })
        assertEquals(widget.feedAt(now).size, MiniWindow.items(widget, now).size)
    }

    @Test
    fun `the window moves with the clock`() {
        val s = SampleScan.state().indexed(now).within(12)
        val nfl = s.feed.filter { it.league.novigName == "NFL" }
        assertTrue(nfl.isNotEmpty() && s.feedAt(now).none { it in nfl })
        // 40 hours later the NFL games (50-54 h out now) start within 12 hours.
        assertTrue(s.feedAt(now + 40 * 3_600_000L).containsAll(nfl.filter { !it.fairIsOld(now + 40 * 3_600_000L) }))
    }

    @Test
    fun `CNO's list, its widget rows and the count say what the window hides`() {
        val s = SampleCno.state()
        assertEquals(SampleCno.kept, s.cnoCandidates(now).map { it.row.bet })
        val twelve = s.within(12)
        assertEquals(listOf("Ohio -33.5"), twelve.cnoCandidates(now).map { it.row.bet })
        assertEquals(listOf("Ohio -33.5"), twelve.cnoShown(now).map { it.row.bet })
        assertTrue(MiniWindow.items(twelve, now).filter { it.fromCno }.all { it.title == "Ohio -33.5" })
        assertEquals("; 3 start after 12h", laterText(twelve, twelve.cnoPicks(now)!!.picks, now))
        assertEquals("", laterText(s, s.cnoPicks(now)!!.picks, now))
        // 24 h includes a game starting exactly 24 h from now.
        assertEquals(SampleCno.kept, s.within(24).cnoCandidates(now).map { it.row.bet })
        // A CNO row without a start time isn't hidden.
        val noStart = SampleCno.state(cno = CnoState(snapshot = SampleCno.snapshot(rows = SampleCno.rows.map { it.copy(startsAtMs = null) }))).within(12)
        assertEquals(SampleCno.kept, noStart.cnoCandidates(now).map { it.row.bet })
    }

    @Test
    fun `the Games board shows only games in the window, and games under way still pass`() {
        val s = SampleScan.state()
        assertEquals(5, s.gamesAt(now).size)
        assertEquals(setOf("g4", "g5"), s.within(24).gamesAt(now).map { it.event.eventId }.toSet())
        val settings = ScanSettings(startsWithinHours = 12)
        assertTrue(settings.startsInWindow(now - 3_600_000L, now)) // started an hour ago
        assertTrue(settings.startsInWindow(null, now))
        assertTrue(!settings.startsInWindow(now + 13 * 3_600_000L, now))
    }

    @Test
    fun `the filter is saved with the rest of the settings`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val saved = json.encodeToString(ScanSettings.serializer(), ScanSettings(startsWithinHours = 48))
        assertEquals(48, json.decodeFromString(ScanSettings.serializer(), saved).startsWithinHours)
        // An older settings file has no such field: any time.
        assertEquals(0, json.decodeFromString(ScanSettings.serializer(), "{}").startsWithinHours)
    }
}
