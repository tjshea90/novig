package com.tjshea.vigilant.app

import android.os.Bundle
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
 * Tj, 2026-10-02: "anytime I close the app and reopen it, auto bet and background scan is turned off by default. Nothing should auto bet or background
 * scan unless I specifically set it in the settings."
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
    }

    @After fun tearDown() {
        runBlocking { app.container.settingsStore.update { ScanSettings() } }
    }

    private fun saved() = runBlocking { app.container.settingsStore.read() }

    @Test
    fun `the reset switches off auto-bet and background auto-scan and nothing else`() {
        val reset = LaunchReset.apply(on)
        assertFalse(reset.autoBet)
        assertEquals(AutoScanMode.OFF, reset.autoScan)
        assertEquals(on.copy(autoBet = false, autoScan = AutoScanMode.OFF), reset)
        // Kept: the criteria, the limits, the interval and the keep-awake choice are Tj's, for when he switches them on again.
        assertEquals(0.035, reset.autoBetMinEv, 0.0)
        assertEquals(4, reset.autoBetBooks)
        assertEquals(8.0, reset.autoBetMaxStake, 0.0)
        assertEquals(5, reset.autoScanSeconds)
        assertTrue(reset.autoBetAllAgree)
        assertFalse(reset.autoScanKeepAwake)
        // And what it does to a settings that already has them off.
        assertEquals(LaunchReset.apply(reset), reset)
    }

    @Test
    fun `the note says what was on, and nothing when nothing was`() {
        assertEquals("Auto-bet and background auto-scan are off again after reopening Vigilant. Switch them on in Settings when you want them.", LaunchReset.note(on))
        assertEquals("Auto-bet is off again after reopening Vigilant. Switch it on in Settings when you want it.", LaunchReset.note(on.copy(autoScan = AutoScanMode.OFF)))
        assertEquals("Background auto-scan is off again after reopening Vigilant. Switch it on in Settings when you want it.", LaunchReset.note(on.copy(autoBet = false)))
        assertNull(LaunchReset.note(on.copy(autoBet = false, autoScan = AutoScanMode.OFF)))
    }

    @Test
    fun `a fresh launch saves the reset before the screen reads the settings, and a second one finds nothing to do`() {
        val note = LaunchReset.onFreshLaunch(app)
        assertTrue(note, note!!.startsWith("Auto-bet and background auto-scan are off again"))
        val s = saved()
        assertFalse(s.autoBet)
        assertEquals(AutoScanMode.OFF, s.autoScan)
        assertEquals(0.035, s.autoBetMinEv, 0.0)
        assertEquals(5, s.autoScanSeconds)
        // The file on disk says it too (a killed process can't bring it back).
        val disk = File(app.filesDir, "settings.json").readText()
        assertTrue(disk, disk.contains("\"autoBet\":false"))
        assertNull(LaunchReset.onFreshLaunch(app))
    }

    @Test
    fun `opening the activity fresh resets, a rotation or a restore from saved state does not`() {
        // A restore (saved state: rotation, Android bringing the app back): whatever was on stays on.
        val restored = Robolectric.buildActivity(MainActivity::class.java).create(Bundle())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(saved().autoBet)
        assertEquals(AutoScanMode.BOTH, saved().autoScan)
        restored.destroy()
        // Opened from closed (no saved state): off, said on screen.
        val fresh = Robolectric.buildActivity(MainActivity::class.java).create()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(saved().autoBet)
        assertEquals(AutoScanMode.OFF, saved().autoScan)
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().startsWith("Auto-bet and background auto-scan are off again"))
        fresh.destroy()
    }

    @Test
    fun `back from another app in the same process keeps auto-bet on, swiped out of the recent apps resets it`() {
        // Tj, 2026-10-02: "If I switch from vigilant to another app then back to vigilant, do not turn off auto bet."
        val first = Robolectric.buildActivity(MainActivity::class.java).create()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse("the process's first screen is a fresh launch", saved().autoBet)
        first.destroy()
        // He switches auto-bet on, leaves (the mini window closed: the screen ended, the process lives), and comes back.
        runBlocking { app.container.settingsStore.update { on } }
        val back = Robolectric.buildActivity(MainActivity::class.java).create()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(saved().autoBet)
        assertEquals(AutoScanMode.BOTH, saved().autoScan)
        assertTrue(app.container.eventLog.events().any { it.text.contains("screen opened (back from another app: auto-bet and auto-scan kept)") })
        back.destroy()
        // Swiped out of the recent apps (a service saw it) and opened again: a fresh launch.
        app.container.launches.taskRemoved(System.currentTimeMillis())
        val reopened = Robolectric.buildActivity(MainActivity::class.java).create()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(saved().autoBet)
        assertEquals(AutoScanMode.OFF, saved().autoScan)
        reopened.destroy()
    }

    @Test
    fun `the activity resets first thing, only without saved state, and the boot receiver is left alone`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt").readText()
        val create = src.substringAfter("override fun onCreate(savedInstanceState: Bundle?) {").substringBefore("// No refresh loop")
        assertTrue(create, create.contains("val fresh = app.container.launches.opening(savedInstanceState != null, LastExit.read(this), bootAtMs = now - android.os.SystemClock.elapsedRealtime())"))
        assertTrue(create, create.contains("val switchedOff = if (fresh) LaunchReset.onFreshLaunch(app) else null"))
        // Before anything else in onCreate can read the settings.
        assertTrue(create.indexOf("LaunchReset.onFreshLaunch") < create.indexOf("super.onCreate(savedInstanceState)"))
        // The mini window and both services tell the gate what happened (LaunchGate).
        assertTrue(src.contains("container.launches.miniWindow(\n                info.isInPictureInPictureMode, expanded = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), System.currentTimeMillis(),"))
        assertTrue(File("src/main/kotlin/com/tjshea/vigilant/app/AutoScanService.kt").readText().contains("container.launches.taskRemoved(System.currentTimeMillis())"))
        assertTrue(File("src/main/kotlin/com/tjshea/vigilant/app/ScanService.kt").readText().contains("container.launches.taskRemoved(System.currentTimeMillis())"))
        // A reboot restarts what was on, until the app is opened (the receiver's rule is unchanged).
        assertTrue(File("src/main/kotlin/com/tjshea/vigilant/app/AutoScanService.kt").readText().contains("if (app.container.currentSettings().activeAutoScan != AutoScanMode.OFF) AutoScanService.start(app)"))
    }
}
