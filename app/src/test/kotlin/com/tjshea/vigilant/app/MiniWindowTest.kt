package com.tjshea.vigilant.app

import android.util.Rational
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        assertTrue(MiniWindow.shouldAutoEnter(on, ScanStatus(scanning = true), feedSize = 0))
        assertTrue(MiniWindow.shouldAutoEnter(on, ScanStatus(rechecking = true), feedSize = 0))
        assertTrue(MiniWindow.shouldAutoEnter(on, ScanStatus(), feedSize = 3))
        assertFalse(MiniWindow.shouldAutoEnter(on, ScanStatus(), feedSize = 0))
        assertFalse(MiniWindow.shouldAutoEnter(on.copy(miniWindow = false), ScanStatus(scanning = true), feedSize = 3))
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
    fun `the manifest allows picture-in-picture and can see Novig's app`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:supportsPictureInPicture="true""""))
        assertTrue(manifest.contains("""<package android:name="${MiniWindow.NOVIG_PACKAGE}" />"""))
        assertEquals("us.novig.app", MiniWindow.NOVIG_PACKAGE)
    }
}
