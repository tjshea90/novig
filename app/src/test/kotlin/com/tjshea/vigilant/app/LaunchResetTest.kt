package com.tjshea.vigilant.app

import android.os.Bundle
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-02 16:05Z: "I had auto bet running in the notifications in the background and when I opened vigilant it again turned off auto bet.
 * I want the app never to turn off auto bet unless I turn it off. The default is auto bet off but only when opening the app after a restart or
 * after I already turned off auto bet manually."
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LaunchResetTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    /** Both on, with limits and criteria set the way Tj had them: the reset must touch only the two switches. */
    private val on = ScanSettings(
        autoScan = AutoScanMode.BOTH, autoScanSeconds = 5, autoBet = true, autoBetMinEv = 0.035, autoBetBooks = 4, autoBetMaxStake = 8.0, apiMaxPerDay = 40.0,
        bankroll = 500.0, autoBetAllAgree = true, autoScanKeepAwake = false,
    ).migrate().copy(autoScanSeconds = 5)

    @Before fun setUp() {
        runBlocking { app.container.settingsStore.update { on } }
        bootCount(7)
    }

    /** Android's boot count: what changes when the phone restarts. */
    private fun bootCount(n: Int) = Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, n)

    private fun open(saved: Bundle? = null) = Robolectric.buildActivity(MainActivity::class.java).create(saved).also { shadowOf(android.os.Looper.getMainLooper()).idle() }

    @After fun tearDown() {
        runBlocking { app.container.settingsStore.update { ScanSettings() } }
    }

    private fun saved() = runBlocking { app.container.settingsStore.read() }

    @Test
    fun `a phone restart switches nothing off (Tj 2026-11-04)`() = runBlocking {
        assertEquals(on, LaunchReset.apply(on))
        assertNull(LaunchReset.note(on))
        assertFalse(LaunchReset.afterRestart(app, Boot(7, 1L)))
        assertTrue(LaunchReset.afterRestart(app, Boot(8, 2L)))
        val s = saved()
        assertTrue(s.autoBet)
        assertEquals(AutoScanMode.BOTH, s.autoScan)
        assertNull(app.container.launches.takeNote())
    }

    @Test
    fun `opening Vigilant never switches auto-bet off, fresh, restored or after a close, only after a phone restart`() {
        // Tj, 2026-10-02 16:05Z: auto-bet running in the notification, Vigilant opened: it must stay on, however the screen was made.
        open().destroy()
        assertTrue(saved().autoBet)
        assertEquals(AutoScanMode.BOTH, saved().autoScan)
        open(Bundle()).destroy()
        assertTrue(saved().autoBet)
        // Swiped out of the recent apps, force-stopped, updated, ended by Android: a new process, the same run of the phone.
        open().destroy()
        assertTrue(saved().autoBet)
        assertEquals(AutoScanMode.BOTH, saved().autoScan)
        assertTrue(app.container.eventLog.events().any { it.msg.contains("screen opened (auto-bet and auto-scan kept as they were)") })
        // The phone restarted (a boot the receiver never heard): nothing is switched off.
        bootCount(8)
        open().destroy()
        assertTrue(saved().autoBet)
        assertEquals(AutoScanMode.BOTH, saved().autoScan)
        // Switched on again: stays on.
        runBlocking { app.container.settingsStore.update { on } }
        open().destroy()
        assertTrue(saved().autoBet)
    }

    @Test
    fun `turned off by Tj stays off`() {
        open().destroy()
        runBlocking { app.container.settingsStore.update { it.copy(autoBet = false) } }
        open().destroy()
        open(Bundle()).destroy()
        assertFalse(saved().autoBet)
    }

    @Test
    fun `the boot receiver switches them off at a restart before anything can bet, and an update keeps them`() {
        open().destroy()
        assertTrue(runBlocking { app.container.currentSettings() }.activeAutoScan != AutoScanMode.OFF)
        // An update: the receiver starts what was on, nothing is switched off.
        runBlocking { AutoScanReceiver.afterBootOrUpdate(app) }
        assertEquals(AutoScanService::class.java.name, shadowOf(app).nextStartedService.component?.className)
        assertTrue(saved().autoBet)
        assertEquals(AutoScanMode.BOTH, saved().autoScan)
        // A phone restart (Tj 2026-11-04): nothing is switched off, the service starts.
        bootCount(8)
        runBlocking { AutoScanReceiver.afterBootOrUpdate(app) }
        assertTrue(saved().autoBet)
        assertEquals(AutoScanMode.BOTH, saved().autoScan)
    }

    @Test
    fun `nothing but a restart resets, in the screen, the services and the receiver`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt").readText()
        val create = src.substringAfter("override fun onCreate(savedInstanceState: Bundle?) {").substringBefore("// No refresh loop")
        assertTrue(create, create.contains("val switchedOff = LaunchReset.onOpen(app, Boot.now(this))"))
        // Before anything else in onCreate can read the settings.
        assertTrue(create.indexOf("LaunchReset.onOpen") < create.indexOf("super.onCreate(savedInstanceState)"))
        // No other reset: a swipe out of the recent apps or the mini window closing changes nothing.
        for (f in listOf("MainActivity.kt", "AutoScanService.kt", "ScanService.kt", "VigilantApp.kt", "MainViewModel.kt")) {
            val text = File("src/main/kotlin/com/tjshea/vigilant/app/$f").readText()
            assertFalse(f, text.contains("LaunchReset.apply") || text.contains("taskRemoved(") || text.contains("autoBet = false"))
        }
        // The boot receiver hands both a restart and an update to the one function the test above runs.
        val receiver = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScanService.kt").readText().substringAfter("class AutoScanReceiver")
        assertTrue(receiver.contains("Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {"))
        assertTrue(receiver.substringAfter("Intent.ACTION_BOOT_COMPLETED").substringBefore("companion object").contains("afterBootOrUpdate(app)"))
    }
}
