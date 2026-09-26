package com.tjshea.vigilant.app

import android.util.Rational
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.scanner.ScannerMode
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
    fun `both lists merge best EV first with CNO's checked rows tagged, or either list alone`() {
        val base = SampleCno.state()
        val both = MiniWindow.items(base, SampleScan.NOW)
        assertEquals(base.feed.size + SampleCno.kept.size, both.size)
        assertEquals(both.sortedByDescending { it.ev }, both)
        assertEquals(SampleCno.kept.sorted(), both.filter { it.fromCno }.map { it.title }.sorted()) // Buehler (4 books) and Perdomo (+167) are out
        val bowers = both.first { it.title == "Brock Bowers Under 4.5" }
        assertEquals("+100", bowers.price)
        assertEquals("\$109", bowers.available)
        assertEquals("Player Receptions · Las Vegas Raiders @ New Orleans Saints", bowers.subtitle)
        assertFalse(bowers.old)
        assertEquals("Brock Bowers Under 4.5", bowers.cno!!.row.bet)

        val ours = MiniWindow.items(base.copy(settings = base.settings.copy(scanner = ScannerMode.VIGILANT)), SampleScan.NOW)
        assertEquals(base.feed.map { it.key }, ours.map { it.key }) // Vigilant only: the feed's own order, no CNO
        val theirs = MiniWindow.items(base.copy(settings = base.settings.copy(scanner = ScannerMode.CNO)), SampleScan.NOW)
        assertEquals(SampleCno.kept, theirs.map { it.title }) // CNO only: no Vigilant rows, best EV first
    }

    @Test
    fun `the scanner mode decides what runs - CNO only hides Vigilant's list, Vigilant only never shows CNO`() {
        val s = ScanSettings()
        assertTrue(s.cnoOn && s.vigilantOn)
        assertFalse(s.copy(scanner = ScannerMode.CNO).vigilantOn)
        assertFalse(s.copy(scanner = ScannerMode.VIGILANT).cnoOn)
        // v0.13.0 saved "miniSource"; its value carries over, and its CNO switch (off) becomes Vigilant only.
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(ScannerMode.CNO, json.decodeFromString(ScanSettings.serializer(), """{"miniSource":"CNO","schema":4}""").migrate().scanner)
        assertEquals(ScannerMode.VIGILANT, json.decodeFromString(ScanSettings.serializer(), """{"cnoEnabled":false,"schema":4}""").migrate().scanner)
        assertEquals(15, json.decodeFromString(ScanSettings.serializer(), """{"cnoRefreshSeconds":60,"schema":4}""").migrate().cnoRefreshSeconds)
        assertEquals(30, json.decodeFromString(ScanSettings.serializer(), """{"cnoRefreshSeconds":30,"schema":4}""").migrate().cnoRefreshSeconds)
        // New installs: Conservative worst case, up to +150, 5+ books.
        assertEquals(150, s.cnoFilters.maxOdds)
        assertEquals(5, s.cnoFilters.minBooks)
        assertEquals(com.tjshea.vigilant.data.cno.CnoDevig.CONSERVATIVE, s.cnoFilters.devig)
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
    fun `the mini window rounds EV to one decimal instead of cutting it off`() {
        // Before v0.13.0 it dropped the second decimal: 7.18% read "+7.1%".
        assertEquals("+7.2%", com.tjshea.vigilant.app.ui.Format.evPercentShort(0.0718))
        assertEquals("+4.0%", com.tjshea.vigilant.app.ui.Format.evPercentShort(0.0404))
        assertEquals("−1.3%", com.tjshea.vigilant.app.ui.Format.evPercentShort(-0.0125))
    }

    @Test
    fun `with CNO's list alone the buttons are Refresh, Books and Next`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cno = MiniWindow.params(context, autoEnter = true, scanning = true, cnoOnly = true)
        assertEquals(listOf("Refresh", "Books", "Next"), cno.actions.map { it.title.toString() })
        assertTrue(cno.actions.all { it.isEnabled })
        // In the Books view the same button goes back to the list.
        assertEquals("List", MiniWindow.params(context, true, false, cnoOnly = true, books = true).actions[1].title.toString())
    }

    @Test
    fun `the manifest allows picture-in-picture and can see Novig's app`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:supportsPictureInPicture="true""""))
        assertTrue(manifest.contains("""<package android:name="${MiniWindow.NOVIG_PACKAGE}" />"""))
        assertEquals("us.novig.app", MiniWindow.NOVIG_PACKAGE)
    }

    @Test
    fun `a pick splits into name and line, so the line Tj bets on is never cut off`() {
        assertEquals("Justin Jefferson" to "Under 69.5", MiniWindow.splitPick("Justin Jefferson Under 69.5"))
        assertEquals("Jahmyr Gibbs" to "Over 4.5", MiniWindow.splitPick("Jahmyr Gibbs Over 4.5"))
        assertEquals("Ohio" to "-33.5", MiniWindow.splitPick("Ohio -33.5"))
        assertEquals("Long Island University" to "+36.5", MiniWindow.splitPick("Long Island University +36.5"))
        assertEquals("" to "Under 8.5", MiniWindow.splitPick("Under 8.5"))
        assertEquals("Charlotte FC" to "No", MiniWindow.splitPick("Charlotte FC No"))
        assertEquals("Dallas Cowboys" to null, MiniWindow.splitPick("Dallas Cowboys"))
        // Shorter names for a narrow window, longest first.
        assertEquals(listOf("Justin Jefferson", "J. Jefferson", "Jefferson"), MiniWindow.nameChoices("Justin Jefferson", player = true))
        assertEquals(listOf("Amon-Ra St. Brown", "A. St. Brown", "St. Brown"), MiniWindow.nameChoices("Amon-Ra St. Brown", player = true))
        assertEquals(listOf("Dallas Cowboys", "Cowboys"), MiniWindow.nameChoices("Dallas Cowboys", player = false))
        assertEquals(listOf("Ohio"), MiniWindow.nameChoices("Ohio", player = false))
    }
}
