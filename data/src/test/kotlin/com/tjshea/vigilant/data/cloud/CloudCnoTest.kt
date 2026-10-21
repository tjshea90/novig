package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoException
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoSource
import com.tjshea.vigilant.data.cno.CnoVenues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The CNO scan on the GitHub engine (CK, 2026-10-21): what the engine publishes, when the phone adopts it, and that the phone then makes no CNO request. */
class CloudCnoTest {
    private fun row(book: String, ev: Double = 0.05) = CnoRow(ev, event = "A at B", market = "Total", bet = "Over 5.5 $book", odds = 110, book = book, gameUrl = "g?side_id=$book")
    private val novig = row("Novig")
    private val kalshi = row("Kalshi")
    private val snap = CnoSnapshot("u", listOf(kalshi, novig), 5_000L, cnoAgeSeconds = 12, filters = CnoFilters())
    private val view = CnoBooksView(novig.bet, null, null, false, listOf(CnoBookPrice("PN", -110, null, -110, null)), 5_000L)

    @Test
    fun `the published file round-trips`() {
        val f = CloudCnoFeed(9_000L, snap, mapOf(novig.key to view), null, 0, 5_000L)
        assertEquals(f, CloudCnoFeed.decode(CloudCnoFeed.encode(f)))
        assertNull(CloudCnoFeed.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `the phone adopts the engine's list and books and leaves out a book switched off since`() = runTest {
        val latest = CloudCnoLatest()
        val src = CloudCnoSource(latest)
        try { src.fetch("u", CnoFilters()); fail("no list yet") } catch (e: CnoException) { assertTrue(e.message!!.contains("first CNO read")) }
        latest.put(CloudCnoFeed(9_000L, null, error = "CrazyNinjaOdds is busy (HTTP 429)"), 1L)
        try { src.fetch("u", CnoFilters()); fail("only an error") } catch (e: CnoException) { assertTrue(e.message!!.contains("HTTP 429")) }
        latest.put(CloudCnoFeed(9_000L, snap, mapOf(novig.key to view)), 1L)
        assertEquals(snap, src.fetch("u", CnoFilters()))
        assertEquals(listOf(novig), src.fetch("u", CnoFilters(venues = CnoVenues(kalshi = false))).rows)
        assertEquals(listOf(kalshi), src.fetch("u", CnoFilters(venues = CnoVenues(novig = false))).rows)
        assertEquals(view, src.books(novig))
        assertNull(src.books(kalshi))
    }

    @Test
    fun `the switching source reads CNO itself only while the engine does not serve`() = runTest {
        var calls = 0
        val local = object : CnoSource {
            override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot { calls++; return snap.copy(rows = emptyList()) }
            override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot? { calls++; return null }
            override suspend fun books(row: CnoRow): CnoBooksView? { calls++; return null }
        }
        val latest = CloudCnoLatest().apply { put(CloudCnoFeed(9_000L, snap), 1L) }
        var cloud = true
        val s = SwitchingCnoSource(local, CloudCnoSource(latest), { cloud })
        assertEquals(2, s.fetch("u", CnoFilters()).rows.size)
        s.fetchWide("u", CnoFilters(), 100)
        s.books(novig)
        assertEquals(0, calls)
        cloud = false
        assertEquals(0, s.fetch("u", CnoFilters()).rows.size)
        assertEquals(1, calls)
    }

    @Test
    fun `the engine serves while it runs, its CNO loop is on and CNO answered within ten minutes`() {
        val now = 100 * 60_000L
        val ok = CloudStatus(heartbeatMs = now - 5_000L, startedAtMs = now - 60 * 60_000L, cnoOn = true, cnoGoodAtMs = now - 60_000L)
        assertTrue(CloudCno.engineServes(ok, now))
        assertFalse(CloudCno.engineServes(null, now))
        assertFalse(CloudCno.engineServes(ok.copy(idle = true), now))
        assertFalse(CloudCno.engineServes(ok.copy(heartbeatMs = now - CloudCno.ALIVE_MS - 1), now))
        assertFalse(CloudCno.engineServes(ok.copy(cnoOn = false), now))
        // CNO refusing the runner: no good read for over ten minutes, the phone reads itself.
        assertFalse(CloudCno.engineServes(ok.copy(cnoGoodAtMs = now - CloudCno.REFUSED_MS - 10_000L), now))
        // A new job's first minutes: no read yet, still trying.
        assertTrue(CloudCno.engineServes(ok.copy(cnoGoodAtMs = 0L, startedAtMs = now - 60_000L), now))
        assertFalse(CloudCno.engineServes(ok.copy(cnoGoodAtMs = 0L), now))
    }

    @Test
    fun `the engine reads CNO only while the cloud scan drives with the CNO scanner on and the switch on`() {
        val on = ScanSettings(scanner = ScannerMode.BOTH).let { it.copy(gates = it.gates.copy(cloudScan = true)) }
        assertTrue(CloudCno.wanted(on))
        assertFalse(CloudCno.wanted(on.copy(scanner = ScannerMode.VIGILANT)))
        assertFalse(CloudCno.wanted(on.copy(gates = on.gates.copy(cnoOnGitHub = false))))
        assertFalse(CloudCno.wanted(on.copy(gates = on.gates.copy(cloudScan = false))))
        assertFalse(CloudCno.wanted(on.copy(paused = true)))
    }
}
