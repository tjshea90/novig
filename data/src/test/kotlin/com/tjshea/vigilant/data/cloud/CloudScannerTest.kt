package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudScannerTest {
    private val none = ScanStats(0, 0, 0, 0, 0)
    private fun feed(at: Long) = CloudFeed(at, 1, none, emptyList())

    private class Local : com.tjshea.vigilant.data.scanner.OddsScanner {
        var scans = 0
        override suspend fun scan(settings: ScanSettings, sources: List<com.tjshea.vigilant.data.reference.ReferenceSource>, pinned: Set<String>, onProgress: (com.tjshea.vigilant.data.scanner.ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): com.tjshea.vigilant.data.scanner.ScanReport {
            scans++
            return com.tjshea.vigilant.data.scanner.ScanReport(null, listOf("local"), null, 0, 0, 0, 0, null, emptyList(), null)
        }
        override suspend fun recheck(settings: ScanSettings, marketIds: Collection<String>, onProgress: (Int, Int) -> Unit) = com.tjshea.vigilant.data.scanner.RecheckReport(null, 0, 0, "local")
        override suspend fun reprice(settings: ScanSettings): ScanResult? = null
        override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = setOf("local")
    }

    @Test
    fun `before any list arrives a cloud scan says why, with no result`() = runBlocking {
        val latest = CloudLatest().also { it.why = "no GitHub token saved" }
        val r = CloudScanner(latest).scan(ScanSettings(gates = ApiGates(cloudScan = true)), emptyList(), emptySet(), {}, {})
        assertNull(r.result)
        assertTrue(r.errors.single().contains("no GitHub token saved"))
    }

    @Test
    fun `while the engine is on its first long scan the report says so, with an empty list or none`() = runBlocking {
        val now = 1_000_000_000L
        val st = CloudStatus(startedAtMs = now - 7 * 60_000L, heartbeatMs = now - 5_000L, passes = 0)
        val none = CloudLatest().also { it.status = st }
        val a = CloudScanner(none, clock = { now }).scan(ScanSettings(gates = ApiGates(cloudScan = true)), emptyList(), emptySet(), {}, {})
        assertTrue(a.errors.single().contains("first scan (7 min so far)"))
        val empty = CloudLatest().also { it.status = st; it.put(feed(now - 1_000L), now) }
        val b = CloudScanner(empty, clock = { now }).scan(ScanSettings(gates = ApiGates(cloudScan = true)), emptyList(), emptySet(), {}, {})
        assertTrue(b.errors.single().contains("first scan"))
        // an engine that is switched off says that instead
        val off = CloudLatest().also { it.status = st.copy(idle = true) }
        assertTrue(CloudScanner(off, clock = { now }).scan(ScanSettings(), emptyList(), emptySet(), {}, {}).errors.single().contains("stopped or restarting"))
    }

    @Test
    fun `a list is the scan's result, and an old one is said to be old`() = runBlocking {
        val now = 1_000_000_000L
        val latest = CloudLatest().also { it.put(feed(now - 10 * 60_000L), now) }
        val r = CloudScanner(latest, clock = { now }).scan(ScanSettings(gates = ApiGates(cloudScan = true)), emptyList(), emptySet(), {}, {})
        assertNotNull(r.result)
        assertEquals(now - 10 * 60_000L, r.result!!.computedAtMs)
        assertTrue(r.errors.single().contains("10 min old"))
    }

    @Test
    fun `the switch picks the scanner per call, and only scans and re-pricing move`() = runBlocking {
        val local = Local()
        val latest = CloudLatest().also { it.put(feed(5L), 5L) }
        val s = SwitchingScanner(local, CloudScanner(latest))
        val on = ScanSettings(gates = ApiGates(cloudScan = true), makerRecommend = false)
        val off = ScanSettings(gates = ApiGates(cloudScan = false))
        assertNotNull(s.scan(on, emptyList(), emptySet(), {}, {}).result)
        assertEquals(0, local.scans)
        s.scan(off, emptyList(), emptySet(), {}, {})
        assertEquals(1, local.scans)
        assertNotNull(s.reprice(on))
        assertNull(s.reprice(off))
        assertEquals(emptySet<String>(), s.unscannedLeagues(on))
        assertEquals(setOf("local"), s.unscannedLeagues(off))
        // re-checking open bets is always the phone's own
        assertEquals("local", s.recheck(on, emptyList()).error)
        // bids on do not take the scan back: the Feed's list and the bid desk's candidates both come from GitHub
        assertNotNull(s.scan(on.copy(maker = true), emptyList(), emptySet(), {}, {}).result)
        assertEquals(1, local.scans)
        // paused: the phone's own scanner (which then does not scan either: paused)
        s.scan(on.copy(pausedByHand = true), emptyList(), emptySet(), {}, {})
        assertEquals(2, local.scans)
    }
}
