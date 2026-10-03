package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoCache
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoSource
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.study.Line
import com.tjshea.vigilant.data.study.ScanStudy
import com.tjshea.vigilant.data.study.Sight
import com.tjshea.vigilant.data.study.StudyJournal
import com.tjshea.vigilant.data.tracker.FreeScores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tj, 2026-10-03: "Confirm that all the betting data is being logged even when the app is backgrounded but in auto scan background mode." The scan study's catch-up at
 * the end of a background cycle: what the study's watchers would have done with the CPU the cycle held, done inside the cycle, and written to the disk.
 */
class StudySyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private var now = SampleScan.NOW
    private val start get() = SampleScan.NOW + 3 * 3_600_000L
    private val day get() = FreeScores.etDate(start)
    private val event = "New York Mets @ Washington Nationals"
    private var settings = ScanSettings()
    private var finished: ScanResult? = null

    private fun row(market: String, bet: String, odds: Int, fair: Double, side: Int, books: Int = 5) = CnoRow(
        ev = fair * com.tjshea.vigilant.engine.Odds.americanToDecimal(odds) - 1.0, startsAtMs = start, league = "MLB", sport = "BASEBALL", event = event, market = market, bet = bet,
        odds = odds, available = 40.0, book = "Novig", fairOdds = -105, fairProbability = fair, books = books, gameUrl = "https://x/game.aspx?game_id=9&side_id=$side&devig_method=8",
    )

    private val moneyline = row("Moneyline", "New York Mets", 105, 0.5122, 1)
    private val lowEv = row("Total Runs", "Over 8.5", 110, 0.4775, 2) // under the 1% floor: only the wide read has it

    /** CNO as the list and the wide read see it: the wide read has the hidden row too. */
    private inner class Source : CnoSource {
        override suspend fun fetch(url: String, filters: CnoFilters) = CnoSnapshot(url, listOf(moneyline), now, cnoAgeSeconds = 4, filters = filters)
        override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int) =
            CnoSnapshot(url, listOf(moneyline, lowEv), now, cnoAgeSeconds = 4, filters = filters, wide = true, asked = "x", limit = rows)

        override suspend fun books(row: CnoRow) = CnoBooksView(
            row.bet, "other", null, false,
            listOf(CnoBookPrice("DK", -105, null, -110, null), CnoBookPrice("FD", -105, null, -110, null), CnoBookPrice("PN", -105, null, -110, null), CnoBookPrice("NV", 105, null, -125, null)),
            fetchedAtMs = now,
        )
    }

    private inner class Rig(store: JsonFileStore<CnoCache>? = null) {
        val journal = StudyJournal(File(tmp.newFolder(), "study"))

        // A flush gap of an hour: nothing reaches the disk unless something flushes it.
        val study = ScanStudy(journal, clock = { now }, version = { "0.58.2" }, flushEveryMs = 3_600_000L, io = Dispatchers.Unconfined)
        val cno = CnoFeed(Source(), store, clock = { now })
        val sync = StudySync(study, cno, livePrices = { emptyMap() }, finishedScan = { finished }, settings = { settings }, step = { _, block -> block() }, clock = { now })
        fun bets() = journal.fold(day).values
        fun lines() = journal.read(day).count()
    }

    @Test
    fun `a cycle's reads are logged and written at its end however long the cycle took`() = runBlocking {
        val rig = Rig()
        val cycleStart = now
        rig.cno.refresh("u", CnoFilters())
        // The cycle went on for three minutes (book pages, closing lines): the list it read is older than a scan can be by the time it ends.
        now += 180_000
        rig.sync.catchUp(cycleStart)
        val bets = rig.bets().associateBy { it.bet.marketLabel }
        assertEquals(setOf("Moneyline", "Total Runs"), bets.keys)
        // The wide read was made inside the cycle's end and its hidden row logged with why.
        assertEquals(1L, rig.cno.wide.value.reads)
        assertEquals("EV", bets.getValue("Total Runs").screen)
        assertEquals(listOf(Sight.WIDE), bets.getValue("Total Runs").sights.map { it.second.k })
        assertEquals(listOf(Sight.CNO, Sight.WIDE), bets.getValue("Moneyline").sights.map { it.second.k })
        // Written, not waiting for a flush that may never come.
        assertTrue(rig.journal.file(day).length() > 0)
    }

    @Test
    fun `without the catch-up nothing reaches the disk, and a late watcher drops the list as an old one`() = runBlocking {
        val rig = Rig()
        rig.cno.refresh("u", CnoFilters())
        now += 180_000
        // A watcher that gets its turn only now (the cycle's wake lock gone, minutes on): the read is a "saved list", and what it logged is only in memory.
        rig.sync.list(rig.cno.state.value.snapshot!!)
        assertEquals(0, rig.bets().size)
        assertEquals(0, rig.lines())
    }

    @Test
    fun `watchers that came late and dropped the reads as old ones don't stop the catch-up logging them`() = runBlocking {
        val rig = Rig()
        val cycleStart = now
        rig.cno.refresh("u", CnoFilters())
        // The wide read was made in the cycle (its watcher got that far); the watchers that log what was read got their turn only three minutes on.
        rig.sync.wideRead(rig.cno.state.value.snapshot!!)
        now += 180_000
        rig.sync.list(rig.cno.state.value.snapshot!!)
        rig.sync.wide(rig.cno.wide.value.snapshot!!)
        assertEquals(0, rig.bets().size)
        rig.sync.catchUp(cycleStart)
        val bets = rig.bets().associateBy { it.bet.marketLabel }
        assertEquals(2, bets.size)
        assertTrue("the app's list's own look of the Mets moneyline", bets.getValue("Moneyline").sights.any { it.second.k == Sight.CNO })
        assertTrue("the wide read's row the list never had", bets.getValue("Total Runs").sights.any { it.second.k == Sight.WIDE })
    }

    @Test
    fun `the book pages a cycle read are checked and logged at its end`() = runBlocking {
        val rig = Rig()
        val cycleStart = now
        rig.cno.refresh("u", CnoFilters())
        rig.cno.loadBooks(moneyline)
        now += 30_000
        rig.sync.catchUp(cycleStart)
        val sb = rig.bets().first { it.bet.marketLabel == "Moneyline" }
        assertNotNull(sb.bet.atBet!!.twoSided)
        assertTrue(sb.sights.any { it.second.k == Sight.CHECK })
    }

    @Test
    fun `what the watchers already logged isn't logged twice by the catch-up`() = runBlocking {
        val rig = Rig()
        val cycleStart = now
        rig.cno.refresh("u", CnoFilters())
        rig.cno.loadBooks(moneyline)
        // The watchers, as they get each result.
        rig.sync.list(rig.cno.state.value.snapshot!!)
        rig.sync.wideRead(rig.cno.state.value.snapshot!!)
        rig.sync.wide(rig.cno.wide.value.snapshot!!)
        rig.sync.books(rig.cno.books.value)
        rig.study.flush()
        val lines = rig.lines()
        assertTrue(lines > 0)
        now += 2_000
        rig.sync.catchUp(cycleStart)
        assertEquals(lines, rig.lines())
        assertEquals(1L, rig.cno.wide.value.reads)
    }

    @Test
    fun `a saved list from before the cycle is not a scan, and no wide read is made for it`() = runBlocking {
        val store = JsonFileStore(File(tmp.newFolder(), "cno.json"), CnoCache.serializer(), { CnoCache() }, Json { ignoreUnknownKeys = true; encodeDefaults = true })
        store.update { CnoCache(CnoSnapshot("u", listOf(moneyline), now - 3_600_000L, cnoAgeSeconds = 4, filters = CnoFilters())) }
        val rig = Rig(store)
        rig.cno.load()
        // A cycle that read nothing from CNO (the scanner is Vigilant's, or the read failed): the list shown is the saved one.
        rig.sync.catchUp(now)
        assertEquals(0, rig.bets().size)
        assertEquals(0L, rig.cno.wide.value.reads)
    }

    @Test
    fun `a finished Vigilant scan is logged by the catch-up, and a switched-off study logs and writes nothing`() = runBlocking {
        settings = SampleScan.settings
        finished = SampleScan.result()
        val rig = Rig()
        rig.sync.catchUp(Long.MAX_VALUE)
        val vig = rig.journal.days().flatMap { d -> rig.journal.fold(d).values }.filter { it.bet.source == "vigilant" }
        assertTrue(vig.isNotEmpty())
        assertTrue(rig.journal.days().isNotEmpty())

        val off = Rig()
        settings = SampleScan.settings.copy(scanStudy = false)
        off.cno.refresh("u", CnoFilters())
        off.sync.catchUp(now - 1_000)
        assertTrue(off.journal.days().isEmpty())
        assertFalse(off.journal.file(day).exists())
        assertEquals(0L, off.cno.wide.value.reads)
    }

    @Test
    fun `the background cycle and the scan's end both finish the study before the wake lock goes`() {
        fun source(path: String) = File("src/main/kotlin/com/tjshea/vigilant/app/$path").readText()
        val cycle = source("AutoScan.kt")
        val end = cycle.substringAfter("} finally {\n                _status.update { it.copy(running = false")
        // Inside the cycle's own NonCancellable finish, bounded, before the cycle log: the cycle's mutex and (in the service) its wake lock are still held.
        assertTrue(end.substringBefore("c.cycleLog.record").contains("kotlinx.coroutines.withTimeoutOrNull(STUDY_SYNC_MS) { c.studySync.catchUp(start) }"))
        assertTrue(end.substringBefore("c.cycleLog.record").contains("withContext(NonCancellable) {"))
        val service = source("AutoScanService.kt")
        val hold = service.substringAfter("scanHold = scope.launch {").substringBefore("\n        }\n")
        assertTrue(hold, hold.indexOf("container.studySync.catchUp(Long.MAX_VALUE)") in 0 until hold.indexOf("releaseWakeLock()"))
        // The study's watchers go through the same steps.
        val app = source("VigilantApp.kt")
        for (call in listOf("studySync.list(snap)", "studySync.wideRead(snap)", "studySync.wide(wide)", "studySync.books(books)", "studySync.vigilant(r)")) assertTrue(call, app.contains(call))
    }
}
