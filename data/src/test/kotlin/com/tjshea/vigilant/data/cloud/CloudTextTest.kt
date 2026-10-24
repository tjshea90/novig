package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTextTest {
    private val now = 10_000_000L
    private fun st(
        beat: Long = 5, listAge: Long = 20, scanning: Boolean = false, acked: Long = 0, forRefresh: Long = 0, rows: Int = 40,
        novig: Long = 30, fair: Long = 90, idle: Boolean = false,
    ) = CloudStatus(
        heartbeatMs = now - beat * 1000, computedAtMs = now - listAge * 1000, scanning = scanning, scanStartedAtMs = now - 120_000, progressStep = "Reading Novig books", progressDone = 30, progressTotal = 120,
        ackedRefreshAtMs = acked, lastScanForRefreshAtMs = forRefresh, lastScanStartedAtMs = now - 600_000, lastScanEndedAtMs = now - 60_000, rows = rows, novigAgeMedianS = novig, novigAgeMaxS = novig * 3, fairAgeMedianS = fair, fairAgeMaxS = fair * 2, idle = idle,
    )

    @Test
    fun `a request not yet picked up is said to be waiting, then scanning with its progress, then done`() {
        val req = now - 12_000
        val waiting = CloudText.view(st(acked = 0), req, now)
        assertTrue(waiting.busy); assertTrue(waiting.headline.contains("waiting for the engine to pick it up"))
        val running = CloudText.view(st(scanning = true, acked = req), req, now)
        assertTrue(running.busy); assertTrue(running.headline.contains("Scanning on GitHub: Reading Novig books 30/120 (25%)")); assertTrue(running.headline.contains("acknowledged"))
        val done = CloudText.view(st(acked = req, forRefresh = req), req, now)
        assertFalse(done.busy); assertTrue(done.headline.contains("Last scan finished 60s ago and took 9m 00s"))
    }

    @Test
    fun `the ages are the published ages plus the time since the list went out`() {
        val v = CloudText.view(st(listAge = 20, novig = 30, fair = 90), 0, now)
        // 20 s since publish: Novig median 30+20 = 50 s, oldest 90+20 = 110 s; fair median 110 s, oldest 200 s = 3m 20s
        assertTrue(v.detail!!, v.detail!!.contains("List published 20s ago, 40 bets"))
        assertTrue(v.detail!!, v.detail!!.contains("Novig prices: median 50s, oldest 1m 50s"))
        assertTrue(v.detail!!, v.detail!!.contains("fair odds: median 1m 50s, oldest 3m 20s"))
        assertEquals(CloudText.Level.GOOD, v.level)
    }

    @Test
    fun `an old list, a silent engine and a switched-off engine are never called current`() {
        assertEquals(CloudText.Level.WARN, CloudText.view(st(listAge = 400, novig = 500), 0, now).level)
        assertEquals(CloudText.Level.WARN, CloudText.view(st(listAge = 30, novig = 400), 0, now).level)
        val silent = CloudText.view(st(beat = 300), 0, now)
        assertEquals(CloudText.Level.BAD, silent.level); assertTrue(silent.headline.contains("silent for 5m 00s"))
        val off = CloudText.view(st(idle = true), 0, now)
        assertEquals(CloudText.Level.WARN, off.level); assertTrue(off.headline.contains("stopped or restarting"))
        assertNotEquals(CloudText.Level.GOOD, CloudText.view(null, 0, now, "no GitHub token saved").level)
    }

    @Test
    fun `the settings line says whether GitHub has read and applied the latest settings`() {
        val ok = st().copy(configRev = 50)
        assertEquals(null, CloudText.settings(ok, 0, 0, now))
        assertEquals(CloudText.Level.GOOD, CloudText.settings(ok, 50, now - 5_000, now)!!.first)
        assertEquals(CloudText.Level.INFO, CloudText.settings(ok.copy(configRev = 40), 50, now - 5_000, now)!!.first)
        val stuck = CloudText.settings(ok.copy(configRev = 40), 50, now - 120_000, now)!!
        assertEquals(CloudText.Level.WARN, stuck.first); assertTrue(stuck.second.contains("has not read your latest settings"))
        val pending = CloudText.settings(ok.copy(settingsPending = true), 50, now - 5_000, now)!!
        assertEquals(CloudText.Level.WARN, pending.first); assertTrue(pending.second.contains("next scan"))
        assertEquals(CloudText.Level.INFO, CloudText.settings(null, 50, now - 1_000, now)!!.first)
    }
}
