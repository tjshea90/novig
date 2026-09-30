package com.tjshea.vigilant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the app last ended, for Diagnostics (Tj, 2026-09-30: "The app just crashed a couple times"). */
class AppExitsTest {

    @Test
    fun `a crash is kept with its time, thread and stack, and read back as one line`() {
        val error = IllegalStateException("boom", RuntimeException("underneath"))
        val text = AppExits.crashText("main", error, 1_790_000_000_000L)
        assertTrue(text, text.startsWith("at=1790000000000 thread=main\njava.lang.IllegalStateException: boom"))
        assertTrue(text, text.contains("Caused by: java.lang.RuntimeException: underneath"))
        val (at, line) = AppExits.parseSaved(text)!!
        assertEquals(1_790_000_000_000L, at)
        assertTrue(line, line.startsWith("on main: java.lang.IllegalStateException: boom | at com.tjshea.vigilant.app.AppExitsTest"))
        assertNull(AppExits.parseSaved("not a saved crash"))
    }

    @Test
    fun `a freeze's dump gives the main thread's stack, where the app was stuck`() {
        val dump = """
            ----- pid 123 at 2026-09-30 22:05:01 -----
            "Signal Catcher" daemon prio=10 tid=4 Runnable
              at java.lang.Object.wait(Native method)

            "main" prio=5 tid=1 Runnable
              | group="main" sCount=0
              at com.tjshea.vigilant.app.UiState.feedAt(MainViewModel.kt:250)
              at com.tjshea.vigilant.app.MainActivityKt.TabIconWithCount(MainActivity.kt:546)
              at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1000)

            "DefaultDispatcher-worker-1" daemon prio=5 tid=20 Waiting
              at jdk.internal.misc.Unsafe.park(Native method)
        """.trimIndent()
        val main = AppExits.mainThread(dump)
        assertEquals("\"main\" prio=5 tid=1 Runnable", main.first())
        assertEquals("at com.tjshea.vigilant.app.UiState.feedAt(MainViewModel.kt:250)", main[1])
        assertEquals(4, main.size)
        assertTrue(AppExits.mainThread(null).isEmpty())
    }

    @Test
    fun `exits that went wrong are told from ordinary ones`() {
        assertEquals("not responding", AppExits.reasonName(android.app.ApplicationExitInfo.REASON_ANR))
        assertEquals("crash", AppExits.reasonName(android.app.ApplicationExitInfo.REASON_CRASH))
        assertTrue(AppExits.Exit(0, "not responding", null, true, 300).bad)
        assertTrue(AppExits.Exit(0, "low memory", null, false, null).bad)
        assertTrue(!AppExits.Exit(0, "closed by you", null, false, null).bad)
    }
}
