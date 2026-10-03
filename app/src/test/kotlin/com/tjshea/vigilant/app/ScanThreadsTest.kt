package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-03: "the entire app gets laggy when vigilant is scanning, but not when cno only is scanning". The scan's work runs on its own threads at
 * Android's background priority (the screen's threads win the CPU when both want it), and each scan's CPU by thread group goes to Diagnostics so the
 * next file says where the time went.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScanThreadsTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `Vigilant's scan runs on its own threads at background priority`() = runBlocking {
        val (name, priority) = app.container.scanScope.async { Thread.currentThread().name to android.os.Process.getThreadPriority(android.os.Process.myTid()) }.await()
        assertTrue(name, name.startsWith(ScanThreads.PREFIX))
        assertEquals(android.os.Process.THREAD_PRIORITY_BACKGROUND, priority)
    }

    @Test
    fun `a thread's stat line gives its name and CPU, names with spaces and brackets included`() {
        val line = "4321 (OkHttp https://api) S 1 2 3 4 5 6 7 8 9 10 250 75 0 0 20 0 30 0"
        assertEquals("OkHttp https://api" to 325L, ThreadCpu.parse(line))
        assertEquals("a (b) c" to 3L, ThreadCpu.parse("9 (a (b) c) R 1 2 3 4 5 6 7 8 9 10 1 2 0"))
        assertNull(ThreadCpu.parse("garbage"))
    }

    @Test
    fun `threads are grouped by what they do, and a scan's split is each group's CPU between two snapshots`() {
        assertEquals(ThreadCpu.MAIN, ThreadCpu.group("com.tjshea.vigi", isMain = true))
        assertEquals(ThreadCpu.SCAN, ThreadCpu.group("vig-scan-3", isMain = false))
        assertEquals(ThreadCpu.GC, ThreadCpu.group("HeapTaskDaemon", isMain = false))
        assertEquals(ThreadCpu.DEFAULT, ThreadCpu.group("DefaultDispatch", isMain = false))
        assertEquals(ThreadCpu.RENDER, ThreadCpu.group("RenderThread", isMain = false))
        val before = mapOf(1 to ThreadCpu.Thread(ThreadCpu.MAIN, 1_000), 2 to ThreadCpu.Thread(ThreadCpu.SCAN, 500), 3 to ThreadCpu.Thread(ThreadCpu.GC, 100))
        // Thread 3 ended; thread 4 started during the scan.
        val after = mapOf(1 to ThreadCpu.Thread(ThreadCpu.MAIN, 1_600), 2 to ThreadCpu.Thread(ThreadCpu.SCAN, 9_500), 4 to ThreadCpu.Thread(ThreadCpu.SCAN, 1_000))
        val split = ThreadCpu.between(before, after, wallMs = 10_000)
        assertEquals(mapOf(ThreadCpu.MAIN to 600L, ThreadCpu.SCAN to 10_000L), split.byGroup)
        assertEquals("CPU during the last Vigilant scan (10 s; 100% = one core, 106% in all): scan workers 100% · main (the screen) 6%", ThreadCpu.text(split, "the last Vigilant scan"))
    }

    @Test
    fun `a snapshot reads every task directory`() {
        val dir = File.createTempFile("task", "").also { it.delete(); it.mkdirs() }
        File(dir, "100").mkdirs(); File(dir, "100/stat").writeText("100 (main) S 1 2 3 4 5 6 7 8 9 10 30 20 0")
        File(dir, "101").mkdirs(); File(dir, "101/stat").writeText("101 (vig-scan-1) R 1 2 3 4 5 6 7 8 9 10 400 100 0")
        val snap = ThreadCpu.snapshot(dir, pid = 100, tickMs = 10.0)
        assertEquals(ThreadCpu.Thread(ThreadCpu.MAIN, 500), snap[100])
        assertEquals(ThreadCpu.Thread(ThreadCpu.SCAN, 5_000), snap[101])
    }
}
