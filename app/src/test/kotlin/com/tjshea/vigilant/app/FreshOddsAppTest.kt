package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.emptyText
import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-09-27: "make sure it never gives me stale odds when comparing odds from other sports books
 * … The other sports books odds MUST be current or at most a few minutes old" (RESEARCH.md §24).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FreshOddsAppTest {

    private val now = SampleScan.NOW
    private val minute = 60_000L

    @Test
    fun `Vigilant's bets leave the feed and the widget once their book prices are too old for the game`() {
        // The sample's book prices were seen 3 minutes before NOW, on games two days off: 10 minutes' life (v0.19.3,
        // Freshness.maxAgeMs; 5 inside 3 hours of the start, FarOffOddsTest).
        val s = SampleScan.state().copy(settings = SampleScan.settings.copy(scanner = ScannerMode.VIGILANT))
        assertEquals(s.feed, s.feedAt(now))
        assertTrue(MiniWindow.items(s, now).isNotEmpty())
        assertEquals(s.feed, s.feedAt(now + 3 * minute)) // 6 minutes old: still listed
        assertTrue(s.feedAt(now + 8 * minute).isEmpty()) // 11: gone
        assertTrue(MiniWindow.items(s, now + 8 * minute).isEmpty())
    }

    @Test
    fun `CNO's bets are hidden while CNO's own odds are over five minutes old`() {
        val s = SampleCno.state().let { it.copy(settings = it.settings.copy(scanner = ScannerMode.CNO)) }
        assertFalse(s.cnoTooOld(now))
        assertTrue(s.cnoShown(now).isNotEmpty())
        // CNO's odds were 49 seconds old at NOW: over five minutes old five minutes on.
        val later = now + 5 * minute
        assertTrue(s.cnoTooOld(later))
        assertTrue(s.cnoShown(later).isEmpty())
        assertTrue(MiniWindow.items(s, later).isEmpty())
        assertTrue(emptyText(s, floating = true, now = later).startsWith("CrazyNinjaOdds' odds are over 5 min old"))
    }

    @Test
    fun `a book page over five minutes old is neither shown nor agreed with`() {
        val fresh = SampleCno.withBooks()
        val jj = SampleCno.rows[1]
        assertNotNull(fresh.booksAt(jj.key, now)?.view)
        val pick = fresh.cnoCandidates(now).single { it.row.key == jj.key }
        assertTrue(fresh.cnoAgrees(pick, now))
        val old = fresh.copy(books = mapOf(jj.key to CnoBooksState(view = SampleCno.jeffersonBooks().copy(fetchedAtMs = now - 6 * minute))))
        assertNull(old.booksAt(jj.key, now)?.view)
        assertTrue(old.booksAt(jj.key, now)!!.error!!.contains("over 5 minutes old"))
        assertFalse(old.cnoAgrees(pick, now))
    }

    @Test
    fun `a recheck close to five minutes scans everything instead`() {
        val s = SampleScan.state()
        val ids = s.feed.map { it.market.marketId }
        assertFalse(WidgetRescan.fairTooOldToRecheck(s, ids, now)) // seen 3 minutes ago
        assertFalse(WidgetRescan.fairTooOldToRecheck(s, ids, now + 60_000L)) // 4 minutes: a few seconds' recheck still fits
        assertTrue(WidgetRescan.fairTooOldToRecheck(s, ids, now + 2 * minute)) // 5 minutes: scan instead
    }
}
