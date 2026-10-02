package com.tjshea.vigilant.app

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.diag.Level
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-02: "when I output the diagnostic file, it opens an android 'share with' prompt, and I can share it directly with Claude app". The file written to the cache, the
 * share sheet's intent (the file attached through a FileProvider, read permission, the message that tells Claude what to do), the provider's limits, and the whole path from
 * the view model, on a real app container with a real recorder.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DiagnosticsShareTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        DiagnosticsShare.dir(app).deleteRecursively()
        // FileProvider keeps each authority's folders in a static map; every Robolectric test has a new cache folder, so the map is emptied (a phone has one).
        runCatching {
            val f = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            f.isAccessible = true
            (f.get(null) as MutableMap<*, *>).clear()
        }
    }

    @After fun tearDown() {
        DiagnosticsShare.dir(app).deleteRecursively()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    @Test
    fun `the file is written to the cache with its name, and only the newest three are kept`() {
        val names = (1..5).map { i -> "vigilant-diagnostics-v0.43.0-2026-10-02-09${i}0.txt" }
        names.forEachIndexed { i, n -> DiagnosticsShare.write(app, "report $i", n).setLastModified(1_000_000L + i * 1_000L) }
        // The last one written is the newest by time: write once more to prune by modified time.
        DiagnosticsShare.write(app, "report 5", "vigilant-diagnostics-v0.43.0-2026-10-02-0960.txt").setLastModified(9_000_000L)
        val kept = DiagnosticsShare.dir(app).listFiles()!!.map { it.name }.sorted()
        assertEquals(DiagnosticsShare.KEEP, kept.size)
        assertTrue(kept.toString(), "vigilant-diagnostics-v0.43.0-2026-10-02-0960.txt" in kept)
        assertEquals("report 5", File(DiagnosticsShare.dir(app), "vigilant-diagnostics-v0.43.0-2026-10-02-0960.txt").readText())
        // Another file in the folder (not ours) is left alone.
        // (made the oldest of all: only the rule that the pruning is for our own files saves it).
        File(DiagnosticsShare.dir(app), "other.txt").apply { writeText("x"); setLastModified(1_000L) }
        DiagnosticsShare.write(app, "again", "vigilant-diagnostics-v0.43.0-2026-10-02-0970.txt")
        assertTrue(File(DiagnosticsShare.dir(app), "other.txt").exists())
    }

    @Test
    fun `the report lists the app's own files, biggest first, and no folders`() {
        val made = listOf("zz-small.json" to 10, "zz-big.json" to 5_000, "zz-mid.json" to 600).map { (n, size) -> File(app.filesDir, n).apply { writeBytes(ByteArray(size)) } }
        File(app.filesDir, "zz-folder").mkdirs()
        try {
            val mine = DiagnosticsShare.storage(app).filter { it.first.startsWith("zz-") }
            assertEquals(listOf("zz-big.json" to 5_000L, "zz-mid.json" to 600L, "zz-small.json" to 10L), mine)
        } finally {
            made.forEach { it.delete() }
            File(app.filesDir, "zz-folder").delete()
        }
    }

    @Test
    fun `the share sheet carries the file through the provider with read permission and the message for Claude`() {
        val file = DiagnosticsShare.write(app, "the report text", "vigilant-diagnostics-v0.43.0-2026-10-02-0912.txt")
        val chooser = DiagnosticsShare.intent(app, file, "0.43.0")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share diagnostics with Claude", chooser.getStringExtra(Intent.EXTRA_TITLE) ?: chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(DiagnosticsFile.PROMPT, send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Vigilant diagnostics (v0.43.0)", send.getStringExtra(Intent.EXTRA_SUBJECT))
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("com.tjshea.vigilant.files", uri.authority)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        // The provider really opens it, and gives its text.
        val text = app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        assertEquals("the report text", text)
    }

    @Test
    fun `the provider hands out only the diagnostics folder of the cache, and is not exported`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val provider = manifest.substringAfter("<provider").substringBefore("</provider>")
        assertTrue(provider, provider.contains("androidx.core.content.FileProvider"))
        assertTrue(provider, provider.contains("android:authorities=\"\${applicationId}.files\""))
        assertTrue(provider, provider.contains("android:exported=\"false\"") && provider.contains("android:grantUriPermissions=\"true\""))
        val paths = File("src/main/res/xml/file_paths.xml").readText()
        assertEquals(listOf("<cache-path name=\"diagnostics\" path=\"diagnostics/\" />"), Regex("<[a-z-]+-path [^>]*>").findAll(paths).map { it.value }.toList())
        // A file outside that folder isn't served.
        val other = File(app.cacheDir, "secret.txt").apply { writeText("not for sharing") }
        val uri = runCatching { androidx.core.content.FileProvider.getUriForFile(app, DiagnosticsShare.authority(app), other) }
        assertTrue("a file outside the folder gets no URI", uri.isFailure)
        other.delete()
    }

    @Test
    fun `from the view model, sharing makes the file, hands over the sheet, remembers the numbers, and the next file compares with them`() {
        val vm = MainViewModel(app)
        waitFor("settings loaded") { vm.state.value.loaded }
        // The activity's collector, off the main thread so the test can pump the looper the view model works on.
        val sheets = java.util.concurrent.CopyOnWriteArrayList<Intent>()
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { vm.shareRequests.collect { sheets += it } }
        try {
            vm.shareDiagnostics()
            waitFor("the share sheet") { sheets.isNotEmpty() }
            assertEquals(Intent.ACTION_CHOOSER, sheets.first().action)
            val files = DiagnosticsShare.dir(app).listFiles()!!
            assertEquals(1, files.size)
            val text = files.single().readText()
            assertTrue(text.take(120), text.startsWith("VIGILANT DIAGNOSTICS FILE · version "))
            assertTrue(text.contains("== READ ME FIRST (for Claude) ==") && text.contains("== EVENT TIMELINE") && text.contains("This is the first report saved on this phone"))
            // The numbers of that file are kept; the next one is compared with them.
            assertEquals(1, runBlocking { app.container.diagHistory.all().size })
            assertTrue(app.container.eventLog.events().any { it.cat == "DIAG" && it.msg.startsWith("diagnostics file made") })
            vm.shareDiagnostics()
            waitFor("the second share sheet") { sheets.size == 2 }
            assertEquals(2, runBlocking { app.container.diagHistory.all().size })
            val second = DiagnosticsShare.dir(app).listFiles()!!.maxByOrNull { it.lastModified() }!!.readText()
            assertTrue(second.contains("Previous report: ") && !second.contains("This is the first report saved on this phone"))
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `the Show report page is the same file, so what Tj reads is what Claude gets`() {
        val vm = MainViewModel(app)
        waitFor("settings loaded") { vm.state.value.loaded }
        vm.showDiagnostics()
        waitFor("the report") { vm.state.value.report?.title == "Diagnostics" }
        val text = vm.state.value.report!!.text
        assertTrue(text.startsWith("VIGILANT DIAGNOSTICS FILE · version "))
        assertTrue(text.contains("== WHAT TO DO (ranked findings) ==") && text.contains("== CONNECTIONS") && text.contains("== MACHINE-READABLE"))
        // Showing it doesn't spend the "previous report": only a shared one does.
        assertEquals(0, runBlocking { app.container.diagHistory.all().size })
    }

    // ---- the recorder is wired into the app ---------------------------------------------------------------------------------

    private lateinit var server: MockWebServer

    @Test
    fun `a call through the app's shared client is recorded by host, a problem becomes an event, and the container loads and flushes both`() = runBlocking {
        server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody("hello").setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(500).setBody("busy"))
            app.container.http.newCall(Request.Builder().url(server.url("/v1/sports/nfl/odds?apiKey=sk-FAKE-LIVEKEYLIVEKEYLIVEKEY1234")).build()).execute().use { it.body!!.string() }
            app.container.http.newCall(Request.Builder().url(server.url("/v1/bad")).build()).execute().use { it.body!!.string() }
            val h = app.container.netStats.snapshot().hosts.getValue(server.hostName)
            assertEquals(2L, h.calls)
            assertEquals(1L, h.errors)
            assertEquals(setOf("/v1/sports/nfl/odds", "/v1/bad"), h.paths.keys)
            assertFalse(app.container.netStats.snapshot().toString().contains("LIVEKEY"))
            assertTrue(app.container.eventLog.events().any { it.level == Level.ERROR && it.msg.contains("server error 500") })
            // Problems the app already collects are events too, masked.
            app.container.problems.add("CrazyNinjaOdds", "failed with key abcdefghijklmnopqrstuvwxyz0123456789SECRET9")
            val problem = app.container.eventLog.events().last { it.cat == "PROBLEM" }
            assertEquals(Level.ERROR, problem.level)
            assertTrue(problem.msg, problem.msg.startsWith("CrazyNinjaOdds: failed with key …RET9"))
            // And they reach disk when flushed.
            app.container.eventLog.flush(force = true)
            app.container.netStats.flush(force = true)
            assertTrue(File(app.filesDir, "events.json").readText().contains("server error 500"))
            assertTrue(File(app.filesDir, "netstats.json").readText().contains("/v1/sports/nfl/odds"))
            assertNotNull(app.container.perf)
        } finally {
            server.shutdown()
        }
    }
}
