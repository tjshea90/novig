package com.tjshea.vigilant.app

import android.app.ApplicationExitInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02: "If I switch from vigilant to another app then back to vigilant, do not turn off auto bet. Auto bet should only be off by default
 * on a fresh app launch or restart, not just switching apps." When a screen being made is a fresh launch ([LaunchGate]).
 */
class LaunchGateTest {

    private val boot = 1_000_000L
    private val now = 5_000_000L

    private fun exit(reason: Int, at: Long = now - 60_000L, description: String? = null) = LastExit(reason, at, description)

    @Test
    fun `a new process's first screen is fresh after Tj's close, an update, a crash or a phone restart`() {
        assertTrue("no record at all", LaunchGate().opening(false, null, boot))
        assertTrue("swiped out of the recent apps", LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_USER_REQUESTED, description = "remove task"), boot))
        assertTrue("force-stopped", LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_USER_STOPPED), boot))
        assertTrue("updated", LaunchGate().opening(false, exit(16, description = "stop com.tjshea.vigilant due to installPackageLI"), boot))
        assertTrue("an update recorded as other", LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_OTHER, description = "installPackageLI"), boot))
        assertTrue("a crash", LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_CRASH), boot))
        assertTrue("the phone restarted since", LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_LOW_MEMORY, at = boot - 1), boot))
    }

    @Test
    fun `a new process after Android freed memory while Tj was in another app is not fresh`() {
        assertFalse(LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_LOW_MEMORY), boot))
        assertFalse(LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_OTHER, description = "too many cached"), boot))
        assertFalse(LaunchGate().opening(false, exit(14), boot))
        assertFalse(LaunchGate().opening(false, exit(ApplicationExitInfo.REASON_SIGNALED), boot))
    }

    @Test
    fun `a restored screen is never fresh`() {
        assertFalse(LaunchGate().opening(true, null, boot))
        assertFalse(LaunchGate().opening(true, exit(ApplicationExitInfo.REASON_USER_REQUESTED), boot))
    }

    @Test
    fun `in the same process only a swipe out of the recent apps makes the next screen fresh, not the mini window closing`() {
        val g = LaunchGate()
        assertTrue(g.opening(false, null, boot))
        // The mini window opened and was closed (the screen ended): back again, not fresh.
        g.miniWindow(inIt = true, expanded = false, now)
        g.miniWindow(inIt = false, expanded = false, now)
        assertFalse(g.opening(false, null, boot))
        // The mini window closing told a service its task went: still not Tj closing Vigilant.
        g.miniWindow(inIt = true, expanded = false, now)
        g.miniWindow(inIt = false, expanded = false, now)
        g.taskRemoved(now + 2_000L)
        assertFalse(g.opening(false, null, boot))
        // Removed while it was still the mini window: not a close either.
        g.miniWindow(inIt = true, expanded = false, now)
        g.taskRemoved(now)
        assertFalse(g.opening(false, null, boot))
        // Swiped away from full screen: fresh.
        g.taskRemoved(now)
        assertTrue(g.opening(false, null, boot))
        // The mini window back to full screen, then swiped away: fresh.
        g.miniWindow(inIt = true, expanded = false, now)
        g.miniWindow(inIt = false, expanded = true, now)
        g.taskRemoved(now)
        assertTrue(g.opening(false, null, boot))
        // Long after the mini window closed, a swipe is Tj's.
        g.miniWindow(inIt = false, expanded = false, now)
        g.taskRemoved(now + LaunchGate.MINI_WINDOW_GRACE_MS + 1)
        assertTrue(g.opening(false, null, boot))
        // Each swipe counts once.
        assertFalse(g.opening(false, null, boot))
    }
}
