package com.tjshea.vigilant.app

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.study.Line
import com.tjshea.vigilant.data.study.ScanStudy
import com.tjshea.vigilant.data.study.Sight
import com.tjshea.vigilant.data.study.StudyExport
import com.tjshea.vigilant.data.study.StudyJournal
import com.tjshea.vigilant.data.tracker.FreeScores
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * Tj, 2026-10-03: "on every cno scan, the vigilant app saves logs on all kinds of information … a button in the settings in the diagnosis section that can output a
 * file to Claude just like the other diagnosis buttons … utilizes already available features in the app … efficient and doesn't interrupt or break any other part of
 * the app". On a real app container: the file through the share sheet, Vigilant's own scan feeding the same log, the Diagnostics line, and how it is wired in.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScanStudyAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        DiagnosticsShare.dir(app).deleteRecursively()
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
    fun `sharing the scan study writes the file where the share sheet serves it, with its own message, and the log it holds is what the scans listed`() {
        val now = System.currentTimeMillis()
        val start = now + 3 * 3_600_000L
        val row = CnoRow(
            ev = 0.05, startsAtMs = start, league = "MLB", sport = "BASEBALL", event = "New York Mets @ Washington Nationals", market = "Moneyline", bet = "New York Mets",
            odds = 105, available = 40.0, book = "Novig", fairOdds = -105, fairProbability = 0.5122, books = 5, gameUrl = "https://x/game.aspx?game_id=9&side_id=1",
        )
        runBlocking { app.container.study.observeCno(CnoSnapshot("https://cno/view", listOf(row), fetchedAtMs = now, cnoAgeSeconds = 4), ScanSettings(), emptyMap(), emptyMap(), emptyMap()) }
        val vm = MainViewModel(app)
        waitFor("settings loaded") { vm.state.value.loaded }
        val sheets = java.util.concurrent.CopyOnWriteArrayList<Intent>()
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { vm.shareRequests.collect { sheets += it } }
        try {
            vm.shareScanStudy()
            waitFor("the share sheet") { sheets.isNotEmpty() }
            assertEquals(Intent.ACTION_CHOOSER, sheets.first().action)
            // Its own message to Claude and subject, the file attached through the same provider.
            @Suppress("DEPRECATION")
            val send = sheets.first().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertTrue(send.getStringExtra(Intent.EXTRA_SUBJECT)!!.startsWith("Vigilant scan study (v"))
            assertEquals(StudyExport.PROMPT, send.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(StudyExport.PROMPT.contains("analyze ALL of the data thoroughly"))
            val files = DiagnosticsShare.dir(app).listFiles()!!.filter { it.name.startsWith(DiagnosticsShare.STUDY_PREFIX) }
            assertEquals(1, files.size)
            val text = files.single().readText()
            assertTrue(text.take(120), text.startsWith("VIGILANT SCAN STUDY · version "))
            assertTrue(text.contains("THE GOAL IS PROFIT") && text.contains("== SUMMARY") && text.contains("== SPLITS"))
            // The bet the CNO scan listed, with what the scan showed.
            val line = text.substringAfter("<<<JSONL\n").substringBefore("\n>>>").lines().single { it.isNotBlank() }
            assertTrue(line, line.contains("\"selection\":\"New York Mets\"") && line.contains("\"american\":105") && line.contains("\"src\":\"c\"") && Regex("\"minutesToStart\":(179|180)").containsMatchIn(line))
            // The file, saved where the diagnostics file goes too, is the one the intent carries.
            assertEquals(files.single().name, send.clipData!!.description.label.toString())
            // The page's line says what is logged.
            vm.refreshStudy()
            waitFor("the study's line") { vm.state.value.studyNote != null }
            assertTrue(vm.state.value.studyNote!!, vm.state.value.studyNote!!.startsWith("1 bets logged over 1 day · 0 graded · 0 with a closing line"))
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `a finished Vigilant scan's +EV bets go into the same log, and a bet CNO listed too is one record with looks from both`() = runBlocking<Unit> {
        val clock = SampleScan.NOW
        val dir = File(app.cacheDir, "study-test").also { it.deleteRecursively() }
        val study = ScanStudy(StudyJournal(dir), clock = { clock }, version = { "0.57.0" }, flushEveryMs = 0)
        val settings = SampleScan.settings
        val result = SampleScan.result()
        val feed = result.feed(settings).filter { !it.isLive && it.event.startsTs > clock }
        assertTrue(feed.size >= 2)
        assertEquals(feed.size, study.observeVigilant(result, settings))
        // The same result again logs nothing.
        assertEquals(0, study.observeVigilant(result, settings))
        val day = FreeScores.etDate(feed.first().event.startsTs)
        val first = study.journal.fold(FreeScores.etDate(feed.first().event.startsTs)).values.first()
        assertEquals("vigilant", first.bet.source)
        assertEquals("study", first.bet.atBet!!.how)
        assertNotNull(first.bet.atBet!!.fairMethod)
        assertEquals(Sight.VIGILANT, first.sights.single().second.k)
        // Vigilant's own record is the bet's own (it listed it first); CNO's goes beside it once CNO lists it too.
        assertEquals(null, first.cnoRec)
        // CNO lists the first of them too: the same bet, not a second record.
        val o = feed.first { it.key == first.bet.marketId + "/" + first.bet.outcomeId }
        val cno = CnoRow(
            ev = 0.04, startsAtMs = o.event.startsTs, league = o.league.displayName, event = o.event.description, market = o.marketLabel, bet = o.selection, odds = 110,
            book = "Novig", fairOdds = -105, fairProbability = 0.4762 * 1.1, books = 5, gameUrl = "https://x/game.aspx?game_id=1&side_id=1",
        )
        val before = study.journal.read(day).count { it.e == Line.BET }
        assertEquals(0, study.observeCno(CnoSnapshot("https://cno/view", listOf(cno), fetchedAtMs = clock, cnoAgeSeconds = 1), settings, emptyMap(), emptyMap(), emptyMap()))
        assertEquals(before, study.journal.read(day).count { it.e == Line.BET })
        val merged = study.journal.fold(day).getValue(first.id)
        assertEquals(setOf(Sight.VIGILANT, Sight.CNO), merged.sights.map { it.second.k }.toSet())
        val row = StudyExport.rowOf(merged, clock, null)
        assertEquals("c+v", row.src)
        assertEquals("vigilant", row.atBet!!.scanner)
        assertEquals("cno", row.cno!!.scanner)
        assertEquals(5, row.cno!!.cnoBooks)
        dir.deleteRecursively()
    }

    @Test
    fun `Diagnostics says what the study has logged and whether it is on`() {
        val state = SampleScan.fresh()
        val now = SampleScan.NOW
        val extras = Diagnostics.Extras("t", 1, "d", study = ScanStudy.Overview(3, 410, 120, 90, 2_500_000L, now - 60_000L, 12), studyProblem = "writing the journal: disk full")
        val text = Diagnostics.report(state, extras, now)
        assertTrue(text, text.contains("Scan study: 410 bets logged over 3 days · 120 graded · 90 with a closing line · last logged 1m ago · 2.4 MB · 12 bets logged since the app opened · logging on · last problem: writing the journal: disk full"))
        val off = Diagnostics.report(SampleScan.fresh(SampleScan.settings.copy(scanStudy = false)), Diagnostics.Extras("t", 1, "d"), now)
        assertTrue(off, off.contains("Scan study: not read · logging OFF (Settings › Diagnostics & about)"))
    }

    // ---- how it is wired: nothing of it can stop a scan ---------------------------------------------------------------------

    private fun source(path: String) = File("src/main/kotlin/com/tjshea/vigilant/app/$path").readText()

    @Test
    fun `each scan's log step runs on the scan's own background threads, behind a catch, and the worker and the share button grade first`() {
        val container = source("VigilantApp.kt")
        // Seven watchers (the wide read's two among them), all on scanScope (background priority), each step through studyStep (a failure is a line in Recent problems, never thrown at the scan).
        val block = container.substringAfter("// The scan study (Tj, 2026-10-03; [ScanStudy])").substringBefore("// Make orders (RESEARCH.md §70)")
        assertEquals(7, Regex("scanScope\\.launch").findAll(block).count())
        assertFalse(block.contains("appScope.launch"))
        assertTrue(block.contains("studyStep(\"CNO scan\") { study.observeCno(snap, currentSettings(), cno.books.value, live.prices.value, cno.links.value) }"))
        assertTrue(block.contains("studyStep(\"Vigilant scan\") { study.observeVigilant(r, currentSettings()) }"))
        assertTrue(block.contains("studyStep(\"book check\") { study.observeBooks(books, currentSettings(), live.prices.value) }"))
        val step = container.substringAfter("private suspend fun studyStep").substringBefore("\n    }\n")
        assertTrue(step, step.contains("catch (e: kotlinx.coroutines.CancellationException) {\n            throw e") && step.contains("catch (e: Exception)"))
        // The study reads from the scans' state and asks CNO for one thing only, the wide read (its own session, kept in cno.wide, never the list's): behind both switches,
        // only after a fresh list read, paced by CnoFeed, and its rows are logged by a step of their own.
        assertFalse(block.contains("loadBooks(") || block.contains("refresh(") || block.contains("readNow("))
        assertEquals(1, Regex("readWide\\(").findAll(block).count())
        assertTrue(block.contains("if (s.scanStudy && s.scanStudyHidden && s.cnoOn && System.currentTimeMillis() - snap.fetchedAtMs <= com.tjshea.vigilant.data.study.ScanStudy.MAX_SCAN_AGE_MS)"))
        assertTrue(block.contains("studyStep(\"CNO wide read\") { cno.readWide(snap.url, snap.filters ?: s.cnoFilters) }"))
        assertTrue(block.contains("studyStep(\"CNO wide scan\") { study.observeCnoWide(wide, cno.state.value.snapshot, currentSettings(), cno.books.value, live.prices.value, cno.links.value) }"))
        // Its close lookups are the Tracker's own sources, ParlayAPI's behind a credit guard; its grading is the Tracker's settler's feed.
        assertTrue(container.contains("GuardedCloses(parlayCloses) { parlayCreditsPlentiful() }, espnCloses, novigCloses"))
        assertTrue(container.contains("study.settle(scores, studyCloses,") && container.contains("yieldTo = { trackerClosing.get() > 0 }"))
        // Whatever a scan logged is written within half a minute even when no scan follows.
        assertTrue(block.contains("delay(STUDY_FLUSH_MS)") && block.contains("studyStep(\"flush\") { study.flush() }"))
        assertTrue(container.contains("val settler = BetSettler(tracker, scores,"))
        assertTrue(source("SettleWorker.kt").contains("runCatching { app.container.settleStudy() }"))
        assertTrue(source("MainViewModel.kt").contains("kotlinx.coroutines.withTimeoutOrNull(STUDY_SETTLE_WAIT_MS) { c.settleStudy() }"))
        // The FileProvider still serves only the diagnostics folder: the study's file lives in it.
        assertEquals(listOf("<cache-path name=\"diagnostics\" path=\"diagnostics/\" />"), Regex("<[a-z-]+-path [^>]*>").findAll(File("src/main/res/xml/file_paths.xml").readText()).map { it.value }.toList())
    }
}
