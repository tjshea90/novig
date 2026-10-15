package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.cloud.CloudScanKey.Kind
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.BidSource
import com.tjshea.vigilant.data.scanner.FeedSort
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudScanKeyTest {
    private val base = ScanSettings(leagues = setOf("NBA"))

    @Test
    fun `every setting, and every member of the nested groups, has a decision`() {
        // A new setting added to ScanSettings, ApiGates, ScanGuards or Guards without a line in CloudScanKey fails here: it can no longer be mirrored (or ignored) by accident.
        assertEquals("settings no decision was made about", emptyList<String>(), CloudScanKey.unclassified(base))
        val paths = CloudScanKey.flat(base).keys
        assertTrue("gates are flattened", "gates.usePinnwire" in paths && "vigGuards.betLevel" in paths && "guards.bidGuard" in paths)
        // and the lists do not name settings that no longer exist (dead entries hide a rename)
        val flat = paths
        val named = listOf("leagues", "daysAhead", "minEvPercent", "autoBet", "gates.cloudScan", "vigGuards.bidLevel", "guards.bidGuardMinutes")
        named.forEach { assertTrue("$it is a real setting", it in flat) }
    }

    @Test
    fun `what is read, how it is priced, how it is filtered and what stays on the phone change different keys`() {
        val fetch = CloudScanKey.fetchKey(base)
        val scan = CloudScanKey.scanKey(base)
        // reading more: a restart
        assertNotEquals(fetch, CloudScanKey.fetchKey(base.copy(leagues = setOf("NBA", "NHL"))))
        assertNotEquals(fetch, CloudScanKey.fetchKey(base.copy(daysAhead = 2)))
        assertNotEquals(fetch, CloudScanKey.fetchKey(base.copy(usePropLine = !base.usePropLine)))
        assertNotEquals(fetch, CloudScanKey.fetchKey(base.copy(gates = base.gates.copy(usePinnwire = !base.gates.usePinnwire))))
        // pricing: not a restart, but the next scan's
        val priced = base.copy(minBooks = base.minBooks + 1, bankroll = 777.0)
        assertEquals(fetch, CloudScanKey.fetchKey(priced))
        assertNotEquals(scan, CloudScanKey.scanKey(priced))
        assertNotEquals(scan, CloudScanKey.scanKey(base.copy(vigGuards = base.vigGuards.copy(thinEarlyHaircut = !base.vigGuards.thinEarlyHaircut))))
        // filtering: neither
        val filtered = base.copy(minEvPercent = 0.05, maxEvPercent = 0.2, feedSort = FeedSort.START)
        assertEquals(scan, CloudScanKey.scanKey(filtered))
        // the phone's own: neither
        val phone = base.copy(
            autoBet = true, autoBetMinEv = 0.1, makerMargin = 0.02, makerMaxBids = 9, apiMaxStake = 3.0, includeLive = true, cnoEnabled = !base.cnoEnabled, startsWithinHours = 6,
            gates = base.gates.copy(cloudSave = !base.gates.cloudSave, useBoltOdds = !base.gates.useBoltOdds, streamScan = !base.gates.streamScan),
            vigGuards = base.vigGuards.copy(betLevel = 12, bidLevel = 90, bidVolume = !base.vigGuards.bidVolume),
        )
        assertEquals(scan, CloudScanKey.scanKey(phone))
        assertEquals(fetch, CloudScanKey.fetchKey(phone))
    }

    @Test
    fun `the list filters follow the phone's newest settings while the scan keeps what it began with`() {
        val scanning = base.copy(minEvPercent = 0.01, minBooks = 2)
        val newest = base.copy(minEvPercent = 0.04, maxEvPercent = 0.1, minBooks = 5, feedSort = FeedSort.START)
        val f = CloudScanKey.withFilters(scanning, newest)
        assertEquals(0.04, f.minEvPercent, 0.0)
        assertEquals(0.1, f.maxEvPercent, 0.0)
        assertEquals(FeedSort.START, f.feedSort)
        assertEquals("pricing stays as the scan began", 2, f.minBooks)
    }

    @Test
    fun `the cloud drives the scan only when it is on, scanning is not paused and no bid desk needs every priced line`() {
        val on = base.copy(gates = base.gates.copy(cloudScan = true))
        assertTrue(on.cloudDrives)
        assertFalse(base.cloudDrives)
        assertFalse("bids from Vigilant's scan need every line", on.copy(maker = true).cloudDrives)
        assertFalse(on.copy(makerRecommend = true).cloudDrives)
        assertTrue("bids priced from CNO never read Vigilant's scan", on.copy(maker = true, makerSource = BidSource.CNO).cloudDrives)
        assertFalse("paused by hand", on.copy(pausedByHand = true).cloudDrives)
        assertFalse("the kill switch", on.copy(killed = true).cloudDrives)
    }

    @Test
    fun `the engine's setting of every kind never carries a trading key or a secret`() {
        // ScanSettings is written to a public branch: none of its fields may be a key. (Keys live in the api key store, never here.)
        val bad = CloudScanKey.flat(base).keys.filter { k -> listOf("key", "token", "secret", "pem", "password").any { k.contains(it, ignoreCase = true) } }
        assertEquals("a setting that looks like a secret would reach a public branch: $bad", emptyList<String>(), bad.filter { it != "gates.novigKeepOpen" })
    }
}
