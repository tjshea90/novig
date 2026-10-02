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
        // The heap at the crash is in the first line (Tj's 2026-10-01 report: an OutOfMemoryError names its limit, not how full the heap was).
        assertTrue(text, Regex("^at=1790000000000 thread=main heap=\\d+/\\d+MB\njava.lang.IllegalStateException: boom").containsMatchIn(text))
        assertTrue(text, text.contains("Caused by: java.lang.RuntimeException: underneath"))
        val (at, line) = AppExits.parseSaved(text)!!
        assertEquals(1_790_000_000_000L, at)
        assertTrue(line, Regex("^on main \\(heap \\d+ of \\d+ MB\\): java.lang.IllegalStateException: boom \\| at com.tjshea.vigilant.app.AppExitsTest").containsMatchIn(line))
        // An older saved crash (no heap in it) still reads.
        assertEquals("on main: x", AppExits.parseSaved("at=5 thread=main\nx")!!.second)
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

    @Test
    fun `a kill for memory counts as bad only when Vigilant was doing something`() {
        fun lowMemory(importance: Int?) = AppExits.Exit(0, "low memory", null, false, null, importance = importance)
        assertTrue(lowMemory(AppExits.CACHED).reclaimed)
        assertFalse(lowMemory(AppExits.CACHED).bad)
        assertEquals("cached in the background (nothing running)", lowMemory(AppExits.CACHED).where)
        // The widget over Novig, the mini window, a scan's service: those were in use.
        assertTrue(lowMemory(AppExits.PERCEPTIBLE).bad)
        assertEquals("perceptible (the widget over another app)", lowMemory(AppExits.PERCEPTIBLE).where)
        assertTrue(lowMemory(AppExits.VISIBLE).bad)
        assertTrue(lowMemory(AppExits.FOREGROUND_SERVICE).bad)
        assertEquals("running a foreground service (a scan or auto-scan)", AppExits.Exit(0, "low memory", null, true, null, importance = AppExits.FOREGROUND_SERVICE).where)
        // Not known (an older record): bad, as before.
        assertTrue(lowMemory(null).bad)
        // A crash is bad wherever it was.
        assertTrue(AppExits.Exit(0, "crash", null, false, null, importance = AppExits.CACHED).bad)
    }

    @Test
    fun `a saved crash says which version crashed`() {
        val text = AppExits.crashText("main", IllegalStateException("x"), 1_790_000_000_000L, "0.43.0")
        assertTrue(text.lineSequence().first().endsWith("version=0.43.0"))
        val (_, line) = AppExits.parseSaved(text)!!
        assertTrue(line, line.startsWith("on main"))
        assertTrue(line, line.contains("on v0.43.0"))
    }
}
