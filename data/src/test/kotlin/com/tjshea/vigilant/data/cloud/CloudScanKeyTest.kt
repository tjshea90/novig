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
    private val base = ScanSettings(leagues = setOf("NBA"), makerRecommend = false)

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
            gates = base.gates.copy(cloudSave = !base.gates.cloudSave, usePinnodds = !base.gates.usePinnodds, streamScan = !base.gates.streamScan),
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
    fun `the cloud drives the scan when it is on and not paused, and the bid desk's lines come from GitHub only with Vigilant-priced bids on`() {
        val on = base.copy(gates = base.gates.copy(cloudScan = true))
        assertTrue(on.cloudDrives)
        assertFalse("no bids, no candidate lines", on.bidsFromCloud)
        assertFalse(base.cloudDrives)
        for (bids in listOf(on.copy(maker = true), on.copy(makerRecommend = true))) {
            assertTrue("bids do not stop the cloud scan any more", bids.cloudDrives)
            assertTrue(bids.bidsNeedLines)
            assertTrue("automatic and Recommend bids are both made from GitHub's candidates", bids.bidsFromCloud)
        }
        assertFalse("bids priced from CNO never read Vigilant's scan", on.copy(maker = true, makerSource = BidSource.CNO).bidsFromCloud)
        assertTrue(on.copy(maker = true, makerSource = BidSource.CNO).cloudDrives)
        assertFalse("paused by hand", on.copy(pausedByHand = true).cloudDrives)
        assertFalse("the kill switch", on.copy(killed = true, maker = true).bidsFromCloud)
    }

    @Test
    fun `bid rules reach the engine as a kind of their own, and the settings that change what is read stay fetch settings`() {
        val bidRule = base.copy(makerMargin = 0.07, makerKinds = emptySet(), vigGuards = base.vigGuards.copy(bidVolume = !base.vigGuards.bidVolume), trapBidHours = 3)
        assertEquals(CloudScanKey.scanKey(base), CloudScanKey.scanKey(bidRule))
        assertNotEquals(CloudScanKey.key(base, Kind.BIDS), CloudScanKey.key(bidRule, Kind.BIDS))
        // turning bids on, or setting low-usage bids, changes the scan's own profile: a restart
        assertNotEquals(CloudScanKey.fetchKey(base), CloudScanKey.fetchKey(base.copy(maker = true)))
        assertNotEquals(CloudScanKey.fetchKey(base), CloudScanKey.fetchKey(base.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE)))
    }

    @Test
    fun `the engine's setting of every kind never carries a trading key or a secret`() {
        // ScanSettings is written to a public branch: none of its fields may be a key. (Keys live in the api key store, never here.)
        val bad = CloudScanKey.flat(base).keys.filter { k -> listOf("key", "token", "secret", "pem", "password").any { k.contains(it, ignoreCase = true) } }
        assertEquals("a setting that looks like a secret would reach a public branch: $bad", emptyList<String>(), bad.filter { it != "gates.novigKeepOpen" })
    }

    @Test
    fun `the BoltOdds and Novig feed switches reach the engine as settings that change what it reads (Tj, 2026-10-15)`() {
        for (path in listOf("gates.useBoltOdds", "gates.boltAllBooks", "gates.boltSocket", "gates.novigSocket", "gates.novigBbo", "gates.novigKeepOpen", "gates.novigCompress")) {
            assertEquals(path, Kind.FETCH, CloudScanKey.kindOf(path))
        }
        // the orders the phone places and the live lane stay the phone's
        for (path in listOf("gates.novigOrderPush", "gates.novigSocketOrders", "gates.pinnSocket", "gates.usePinnodds")) assertEquals(path, Kind.PHONE, CloudScanKey.kindOf(path))
        val g = base.gates
        assertNotEquals(CloudScanKey.fetchKey(base), CloudScanKey.fetchKey(base.copy(gates = g.copy(useBoltOdds = !g.useBoltOdds))))
        assertNotEquals(CloudScanKey.fetchKey(base), CloudScanKey.fetchKey(base.copy(gates = g.copy(novigSocket = !g.novigSocket))))
        assertEquals(CloudScanKey.fetchKey(base), CloudScanKey.fetchKey(base.copy(gates = g.copy(novigSocketOrders = !g.novigSocketOrders))))
    }
}
