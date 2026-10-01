package com.tjshea.vigilant.app

import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScratchWakeLockTest {
    @Test fun scratch() {
        val app = ApplicationProvider.getApplicationContext<VigilantApp>()
        val pm = app.getSystemService(PowerManager::class.java)
        val l = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "x").apply { setReferenceCounted(false) }
        l.acquire(15 * 60_000L)
        System.err.println("SCRATCH held after timed acquire=${l.isHeld}")
        l.acquire()
        System.err.println("SCRATCH held after plain acquire=${l.isHeld}")
        val l2 = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "y")
        l2.acquire(1000L)
        System.err.println("SCRATCH refcounted timed held=${l2.isHeld}")
    }
}
