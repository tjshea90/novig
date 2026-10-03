package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.study.ScanStudy

/**
 * How the scan study gets what the scans produced (RESEARCH.md §75, §76, §77). Two ways, the same steps:
 *
 *  - **as it arrives**: the watchers in [AppContainer] call [list], [wideRead], [wide], [books] and [vigilant] when a CNO read, a wide read, a book page or a
 *    Vigilant scan lands (the app on screen, or a background cycle with the CPU held awake);
 *  - **at the end of a background cycle** ([catchUp], Tj, 2026-10-03: "Confirm that all the betting data is being logged even when the app is backgrounded but
 *    in auto scan background mode"): a cycle in the alarm-only mode holds the CPU only while [AutoScanner.cycle] runs, so what a watcher had left to do when
 *    it ended (the wide read, the last book pages' checks, the flush) waited for the next alarm, minutes on, when the list it was logging was a "saved list"
 *    older than a scan could be, or never came if Android ended the process first. [catchUp] does it all inside the cycle, and writes it to the disk.
 *
 * Every step is idempotent (the same read logged twice logs nothing: [ScanStudy] keeps what it last saw of each scan), so the watchers and [catchUp] never
 * double-log. Each step goes through [step]: a failure is a line in Recent problems, never a failure of the scan it watched.
 */
class StudySync(
    private val study: ScanStudy,
    private val cno: CnoFeed,
    private val livePrices: () -> Map<String, LivePrice>,
    /** The newest Vigilant scan that finished (not partial, not running), or null. */
    private val finishedScan: () -> ScanResult?,
    private val settings: suspend () -> ScanSettings,
    private val step: suspend (String, suspend () -> Unit) -> Unit,
) {

    /** A CNO read as the app's list has it. */
    suspend fun list(snap: CnoSnapshot, maxAgeMs: Long = ScanStudy.MAX_SCAN_AGE_MS) {
        step("CNO scan") { study.observeCno(snap, settings(), cno.books.value, livePrices(), cno.links.value, maxAgeMs) }
    }

    /** The wide read after [snap] (the list's newest read): whether it is due is [CnoFeed.readWide]'s to say. */
    suspend fun wideRead(snap: CnoSnapshot, maxAgeMs: Long = ScanStudy.MAX_SCAN_AGE_MS) {
        val s = settings()
        if (s.scanStudy && s.scanStudyHidden && s.cnoOn && System.currentTimeMillis() - snap.fetchedAtMs <= maxAgeMs) {
            step("CNO wide read") { cno.readWide(snap.url, snap.filters ?: s.cnoFilters) }
        }
    }

    /** A wide read's rows. */
    suspend fun wide(w: CnoSnapshot, maxAgeMs: Long = ScanStudy.MAX_SCAN_AGE_MS) {
        step("CNO wide scan") { study.observeCnoWide(w, cno.state.value.snapshot, settings(), cno.books.value, livePrices(), cno.links.value, maxAgeMs) }
    }

    /** The book pages the green check has read. */
    suspend fun books(books: Map<String, CnoBooksState>) {
        step("book check") { study.observeBooks(books, settings(), livePrices()) }
    }

    /** A finished Vigilant scan. */
    suspend fun vigilant(result: ScanResult) {
        step("Vigilant scan") { study.observeVigilant(result, settings()) }
    }

    /**
     * Everything the scans produced since a watcher last had its turn, logged now and written to disk: the list read at or after [cycleStartMs] (made in this
     * cycle, so never "the saved list" whatever its age by now), the wide read after it, the wide rows, the book pages, a finished Vigilant scan, then the flush.
     * Nothing for a study that is off.
     */
    suspend fun catchUp(cycleStartMs: Long) {
        if (!settings().scanStudy) return
        val narrow = cno.state.value.snapshot
        val own = narrow != null && narrow.fetchedAtMs >= cycleStartMs
        if (narrow != null) {
            list(narrow, if (own) Long.MAX_VALUE else ScanStudy.MAX_SCAN_AGE_MS)
            if (own) wideRead(narrow, Long.MAX_VALUE)
        }
        cno.wide.value.snapshot?.let { w -> wide(w, if (w.fetchedAtMs >= cycleStartMs) Long.MAX_VALUE else ScanStudy.MAX_SCAN_AGE_MS) }
        books(cno.books.value)
        finishedScan()?.let { vigilant(it) }
        step("flush") { study.flush() }
    }
}
