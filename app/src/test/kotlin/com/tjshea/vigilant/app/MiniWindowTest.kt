package com.tjshea.vigilant.app

import android.util.Rational
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.scanner.MiniSource
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The mini window (picture-in-picture over Novig, Tj 2026-09-26): when it opens, what it pages, what it asks Android for. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MiniWindowTest {

    @Test
    fun `pages wrap around, the last page holds the remainder, and an empty feed shows nothing`() {
        assertEquals(0 until 3, MiniWindow.page(size = 7, fit = 3, next = 0))
        assertEquals(3 until 6, MiniWindow.page(7, 3, 1))
        assertEquals(6 until 7, MiniWindow.page(7, 3, 2))
        assertEquals(0 until 3, MiniWindow.page(7, 3, 3)) // back to the top
        assertEquals(0 until 2, MiniWindow.page(2, 5, 4)) // everything fits: always page one
        assertEquals(0 until 1, MiniWindow.page(4, 0, 0)) // a tiny window still shows one bet
        assertTrue(MiniWindow.page(0, 3, 0).isEmpty())
    }

    @Test
    fun `leaving the app shrinks to the mini window only with something to watch, and never when switched off`() {
        val on = ScanSettings()
        assertTrue(on.miniWindow)
        assertTrue(MiniWindow.shouldAutoEnter(on, ScanStatus(scanning = true), rows = 0))
        assertTrue(MiniWindow.shouldAutoEnter(on, ScanStatus(rechecking = true), rows = 0))
        assertTrue(MiniWindow.shouldAutoEnter(on, ScanStatus(), rows = 3))
        assertFalse(MiniWindow.shouldAutoEnter(on, ScanStatus(), rows = 0))
        assertFalse(MiniWindow.shouldAutoEnter(on.copy(miniWindow = false), ScanStatus(scanning = true), rows = 3))
        // Settings saved before the switch existed read it as on.
        assertTrue(Json { ignoreUnknownKeys = true }.decodeFromString(ScanSettings.serializer(), """{"leagues":["NFL"]}""").miniWindow)
    }

    @Test
    fun `the window asks for a wide shape, auto-enter, and Scan, Recheck and Next buttons`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val idle = MiniWindow.params(context, autoEnter = true, scanning = false)
        assertEquals(Rational(3, 2), idle.aspectRatio)
        assertTrue(idle.isAutoEnterEnabled)
        assertEquals(listOf("Scan", "Recheck", "Next"), idle.actions.map { it.title.toString() })
        assertTrue(idle.actions.all { it.isEnabled })
        // Mid-scan, Scan and Recheck are greyed out; Next still pages.
        val busy = MiniWindow.params(context, autoEnter = false, scanning = true)
        assertFalse(busy.isAutoEnterEnabled)
        assertEquals(listOf(false, false, true), busy.actions.map { it.isEnabled })
    }

    @Test
    fun `both lists merge best EV first with CNO's rows tagged, or either list alone`() {
        val base = SampleCno.state()
        val both = MiniWindow.items(base, SampleScan.NOW)
        assertEquals(base.feed.size + SampleCno.rows.size, both.size)
        assertEquals(both.sortedByDescending { it.ev }, both)
        assertEquals(SampleCno.rows.size, both.count { it.fromCno })
        val bowers = both.first { it.title == "Brock Bowers Under 4.5" }
        assertEquals("+100", bowers.price)
        assertEquals("\$109", bowers.available)
        assertEquals("Player Receptions · Las Vegas Raiders @ New Orleans Saints", bowers.subtitle)
        assertFalse(bowers.old)

        val ours = MiniWindow.items(base.copy(settings = base.settings.copy(miniSource = MiniSource.VIGILANT)), SampleScan.NOW)
        assertEquals(base.feed.map { it.key }, ours.map { it.key }) // the feed's own order
        val theirs = MiniWindow.items(base.copy(settings = base.settings.copy(miniSource = MiniSource.CNO)), SampleScan.NOW)
        assertEquals(SampleCno.rows.map { it.bet }, theirs.map { it.title }) // CNO's order
        // CNO off: its rows go, even with "CNO only" picked.
        val off = base.copy(settings = base.settings.copy(cnoEnabled = false, miniSource = MiniSource.CNO))
        assertEquals(base.feed.size, MiniWindow.items(off, SampleScan.NOW).size)
    }

    @Test
    fun `CNO rows age with CNO's own clock, and a list read for another link isn't shown`() {
        val base = SampleCno.state()
        val later = SampleScan.NOW + MiniWindow.CNO_OLD_MS
        assertTrue(MiniWindow.items(base, later).filter { it.fromCno }.all { it.old }) // 20 s read + 29 s CNO age + 5 min
        val other = SampleCno.state(cno = CnoState(snapshot = SampleCno.snapshot(url = "https://crazyninjaodds.com/site/tools/positive-ev.aspx?site_id=15")))
        assertTrue(MiniWindow.items(other, SampleScan.NOW).none { it.fromCno })
    }

    @Test
    fun `with CNO's list alone the buttons are Refresh and Next`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cno = MiniWindow.params(context, autoEnter = true, scanning = true, cnoOnly = true)
        assertEquals(listOf("Refresh", "Next"), cno.actions.map { it.title.toString() })
        assertTrue(cno.actions.all { it.isEnabled })
    }

    @Test
    fun `the manifest allows picture-in-picture and can see Novig's app`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:supportsPictureInPicture="true""""))
        assertTrue(manifest.contains("""<package android:name="${MiniWindow.NOVIG_PACKAGE}" />"""))
        assertEquals("us.novig.app", MiniWindow.NOVIG_PACKAGE)
    }
}
