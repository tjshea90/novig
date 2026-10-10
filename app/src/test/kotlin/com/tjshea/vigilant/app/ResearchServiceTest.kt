package com.tjshea.vigilant.app

import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Tj, 2026-10-08: "the betting must be fast to react to the odds movement". The live engine lives in the app's scope and Android freezes a backgrounded process, so a foreground service
 * holds the process and the CPU awake exactly as long as Settings › Pinnodds live is on. On a Robolectric phone with the screen off: it goes foreground with a notification, holds a
 * partial wake lock, refreshes it, and lets go when the switch goes off, STOP ALL is on, or Stop is tapped.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ResearchServiceTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val pm: PowerManager get() = app.getSystemService(PowerManager::class.java)

    @Before fun setUp() {
        runBlocking { app.container.settingsStore.update { ScanSettings(researchMode = true) } }
        shadowOf(pm).turnScreenOn(false)
        shadowOf(pm).setIsDeviceIdleMode(true)
    }

    @After fun tearDown() {
        runBlocking { app.container.settingsStore.update { ScanSettings() } }
    }

    /** Real time for the disk, virtual seconds for the service's delays: the settings store writes on another thread and needs this (main) thread to be free to resume. */
    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) {
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what; running=${ResearchService.running} held=${ResearchService.keepAwakeHeld}")
    }

    private fun start(action: String? = null) =
        Robolectric.buildService(ResearchService::class.java, Intent(app, ResearchService::class.java).apply { this.action = action }).create().startCommand(0, 1)

    @Test
    fun `with the feed on the service is a foreground service holding the CPU awake`() {
        val c = start()
        try {
            waitFor("the CPU lock") { ResearchService.keepAwakeHeld }
            assertTrue(ResearchService.running)
            assertTrue("a foreground notification is up", shadowOf(c.get()).lastForegroundNotification != null)
        } finally {
            c.destroy()
        }
        assertFalse(ResearchService.running)
        assertFalse("destroying it lets the CPU go", ResearchService.keepAwakeHeld)
    }

    @Test
    fun `switching the feed off stops the service and releases the lock`() {
        val c = start()
        try {
            waitFor("the CPU lock") { ResearchService.keepAwakeHeld }
            runBlocking { app.container.settingsStore.update { it.copy(researchMode = false) } }
            waitFor("the lock is let go") { !ResearchService.keepAwakeHeld }
            assertTrue("the service stopped itself", shadowOf(c.get()).isStoppedBySelf)
        } finally {
            c.destroy()
        }
    }

    @Test
    fun `STOP ALL stops it too`() {
        val c = start()
        try {
            waitFor("the CPU lock") { ResearchService.keepAwakeHeld }
            runBlocking { app.container.settingsStore.update { it.copy(killed = true) } }
            waitFor("the lock is let go") { !ResearchService.keepAwakeHeld }
            assertTrue(shadowOf(c.get()).isStoppedBySelf)
        } finally {
            c.destroy()
        }
    }

    @Test
    fun `the notification's Stop turns the feed off in the settings`() {
        val c = start(ResearchService.ACTION_STOP)
        try {
            waitFor("the feed is off in the settings") { app.container.settingsStore.flow.value?.researchMode == false }
            waitFor("the service stopped itself") { shadowOf(c.get()).isStoppedBySelf }
            assertEquals(false, app.container.settingsStore.flow.value?.researchMode)
        } finally {
            c.destroy()
        }
    }
}
