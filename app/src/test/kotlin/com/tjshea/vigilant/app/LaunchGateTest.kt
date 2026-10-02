package com.tjshea.vigilant.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-10-02 16:05Z: "I want the app never to turn off auto bet unless I turn it off. The default is auto bet off but only when opening the app
 * after a restart or after I already turned off auto bet manually." Whether the phone restarted since Vigilant last looked ([LaunchGate]).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LaunchGateTest {

    private fun gate() = LaunchGate(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(LaunchGate.PREFS, Context.MODE_PRIVATE))

    private val at = 1_000_000L

    @Test
    fun `only a new boot count is a restart, once, until it is handled`() {
        val g = gate()
        // The first look ever (a new install, the update that brought this rule): not a restart, whatever Tj had on stays on.
        assertFalse(g.restarted(Boot(7, at)))
        // The same run of the phone, however often the app is opened, swiped away, updated or ended by Android.
        assertFalse(g.restarted(Boot(7, at)))
        assertFalse(g.restarted(Boot(7, at + 3_600_000L)))
        // The phone restarted: a restart until it is handled, then not again.
        assertTrue(g.restarted(Boot(8, at + 7_200_000L)))
        assertTrue(g.restarted(Boot(8, at + 7_200_000L)))
        g.handled(Boot(8, at + 7_200_000L))
        assertFalse(g.restarted(Boot(8, at + 7_200_000L)))
        // A new gate in a new process reads what the old one saved.
        assertFalse(gate().restarted(Boot(8, at + 7_200_000L)))
        assertTrue(gate().restarted(Boot(9, at + 9_000_000L)))
    }

    @Test
    fun `without a boot count, the phone's start time tells a restart, past the wall clock being set`() {
        val g = gate()
        assertFalse(g.restarted(Boot(null, at)))
        assertFalse(g.restarted(Boot(null, at + LaunchGate.BOOT_SLACK_MS)))
        assertFalse(g.restarted(Boot(null, at - LaunchGate.BOOT_SLACK_MS)))
        assertTrue(g.restarted(Boot(null, at + LaunchGate.BOOT_SLACK_MS + 1)))
        // Saved without a count, read with one: the start time still decides.
        assertFalse(g.restarted(Boot(4, at)))
        assertTrue(g.restarted(Boot(4, at + LaunchGate.BOOT_SLACK_MS + 1)))
    }

    @Test
    fun `the note is kept for the next screen and given once`() {
        val g = gate()
        assertNull(g.takeNote())
        g.keepNote("Auto-bet is off after the phone restarted.")
        assertEquals("Auto-bet is off after the phone restarted.", gate().takeNote())
        assertNull(g.takeNote())
    }
}
