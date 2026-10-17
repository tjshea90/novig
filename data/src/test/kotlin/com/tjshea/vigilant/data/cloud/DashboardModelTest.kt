package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.cloud.DashboardModel.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModelTest {
    private val now = 1_792_092_000_000L
    private fun st(f: CloudStatus.() -> CloudStatus = { this }) = CloudStatus(
        runId = "1", heartbeatMs = now - 5_000, passes = 3, scanMs = 300_000, rows = 38, lastScanEndedAtMs = now - 120_000, fairAgeMedianS = 200, novigAgeMedianS = 40,
        scanNote = "EV spread of 16792 priced lines: +EV 230 · 0 to -2% 1482; Last scan took 351 s", tradeMode = "live", tradeNote = "live: looked 33, passed 0, ordered 0 (\$0.00); held back: x",
        walletCash = 124.27, walletEquity = 124.27, walletReadAtMs = now - 30_000,
    ).f()

    @Test
    fun `the headline says what the engine is doing and when it is not to be trusted`() {
        assertEquals("Not connected", DashboardModel.headline(null, now).title)
        assertEquals(Tone.GOOD, DashboardModel.headline(st(), now).tone)
        assertEquals("Running", DashboardModel.headline(st(), now).title)
        assertEquals("Scanning", DashboardModel.headline(st { copy(scanning = true, progressStep = "Novig prices", progressDone = 10, progressTotal = 40) }, now).title)
        assertTrue(DashboardModel.headline(st { copy(scanning = true, progressStep = "Novig prices", progressDone = 10, progressTotal = 40) }, now).detail.contains("10 of 40"))
        assertEquals("Not responding", DashboardModel.headline(st { copy(heartbeatMs = now - 10 * 60_000) }, now).title)
        assertEquals(Tone.BAD, DashboardModel.headline(st { copy(heartbeatMs = now - 10 * 60_000) }, now).tone)
        assertEquals("Idle", DashboardModel.headline(st { copy(idle = true) }, now).title)
        assertEquals("Problem", DashboardModel.headline(st { copy(problem = "push failed") }, now).title)
        assertEquals("Trading stopped", DashboardModel.headline(st { copy(tradeStopped = "location refused") }, now).title)
        assertEquals("Sleeping: wallet empty", DashboardModel.headline(st { copy(walletSleep = "under \$0.50") }, now).title)
    }

    @Test
    fun `an engine that is not running shows its last readings as last known, never as live (Tj, 2026-10-16, trading LIVE and address accepted shown while idle)`() {
        val closed = java.time.Instant.parse("2026-10-16T07:00:00Z").toEpochMilli()   // 03:00 Eastern: closed
        val last = st { copy(heartbeatMs = closed - 9 * 60_000, tradeMode = "live", addressHandovers = 0) }
        assertEquals("Idle: outside its hours", DashboardModel.headline(last, closed).title)
        assertEquals(Tone.NEUTRAL, DashboardModel.headline(last, closed).tone)
        val tiles = DashboardModel.tiles(last, closed).associateBy { it.label }
        assertEquals("not trading", tiles.getValue("Trading").value)
        assertTrue(tiles.getValue("Trading").sub, tiles.getValue("Trading").sub.contains("outside its hours") && tiles.getValue("Trading").sub.contains("LIVE"))
        assertEquals("not checked", tiles.getValue("Address").value)
        assertTrue(tiles.values.all { it.tone == Tone.NEUTRAL })
        // in its hours with no heartbeat it is a real failure, still not shown as live
        val dead = st { copy(heartbeatMs = now - 9 * 60_000, tradeMode = "live") }
        assertEquals("Not responding", DashboardModel.headline(dead, now).title)
        assertEquals("not trading", DashboardModel.tiles(dead, now).first { it.label == "Trading" }.value)
        assertTrue(DashboardModel.tiles(dead, now).first { it.label == "Trading" }.sub.contains("no heartbeat"))
        // running: the live readings
        assertEquals("LIVE", DashboardModel.tiles(st { copy(tradeMode = "live") }, now).first { it.label == "Trading" }.value)
        assertEquals("accepted", DashboardModel.tiles(st(), now).first { it.label == "Address" }.value)
        // an idle engine inside its hours (switched off on the phone) is not live either
        assertEquals("not trading", DashboardModel.tiles(st { copy(idle = true, tradeMode = "live") }, now).first { it.label == "Trading" }.value)
    }

    @Test
    fun `scan progress is a fraction only while a scan runs and its size is known`() {
        assertNull(DashboardModel.scanProgress(st()))
        assertEquals(0.25f, DashboardModel.scanProgress(st { copy(scanning = true, progressDone = 10, progressTotal = 40) })!!, 0.0001f)
        assertNull(DashboardModel.scanProgress(st { copy(scanning = true, progressDone = 0, progressTotal = 0) }))
    }

    @Test
    fun `the plus EV count is read from the scan note`() {
        assertEquals(230, DashboardModel.posEv(st().scanNote))
        assertEquals(16792, DashboardModel.priced(st().scanNote))
        assertNull(DashboardModel.posEv("no spread here"))
    }

    @Test
    fun `tiles show the wallet as not read, never as zero, and colour the odds age`() {
        val t = DashboardModel.tiles(st(), now).associateBy { it.label }
        assertEquals("$124.27", t.getValue("Wallet").value)
        assertEquals(Tone.GOOD, t.getValue("Odds age").tone)
        assertEquals("38", t.getValue("Bets listed").value)
        assertEquals("accepted", t.getValue("Address").value)
        val unread = DashboardModel.tiles(st { copy(walletCash = -1.0, walletEquity = -1.0, walletReadAtMs = 0) }, now).associateBy { it.label }
        assertEquals("not read", unread.getValue("Wallet").value)
        assertTrue(unread.getValue("Wallet").sub.contains("not zero"))
        val old = DashboardModel.tiles(st { copy(fairAgeMedianS = 1000) }, now).associateBy { it.label }
        assertEquals(Tone.BAD, old.getValue("Odds age").tone)
        // The tile is the engine's verdict on THIS runner now, never the count of past swaps (2026-10-17: it said "accepted" under a RESTRICTED_NETWORK_REGION line).
        val swapped = DashboardModel.tiles(st { copy(addressHandovers = 2, novigAddress = "accepted by Novig") }, now).associateBy { it.label }
        assertEquals("accepted", swapped.getValue("Address").value); assertTrue(swapped.getValue("Address").sub.contains("2 swaps tried"))
        val refused = DashboardModel.tiles(st { copy(novigAddress = "REFUSED by Novig (RESTRICTED_NETWORK_REGION): this GitHub machine's location is not served; a swap to a new machine is due") }, now).associateBy { it.label }
        assertEquals("refused", refused.getValue("Address").value); assertEquals(Tone.BAD, refused.getValue("Address").tone)
        val oldEngine = DashboardModel.tiles(st { copy(novigRoute = "public routes: the read key was refused (Novig places this network's internet address outside the states it serves (RESTRICTED_NETWORK_REGION)).") }, now).associateBy { it.label }
        assertEquals("an engine without the field is judged by the refusal code in its Novig line", "refused", oldEngine.getValue("Address").value)
        assertTrue(DashboardModel.tiles(null, now).isEmpty())
    }

    @Test
    fun `api health and detail come from the last scan's feeds, by provider`() {
        val s = st { copy(feedReportAtMs = now - 1000, feedReport = listOf(FeedLine("sgo", fetched = 8), FeedLine("sgo-props", reused = 6), FeedLine("bolt", error = "socket not connected"), FeedLine("parlay"))) }
        val h = DashboardModel.apiHealth(s)
        assertEquals(CloudDashboard.Health.WORKING, h["sgo"])
        assertEquals(CloudDashboard.Health.FAILING, h["boltodds"])
        assertEquals(CloudDashboard.Health.SILENT, h["parlay"])
        assertEquals("sgo 8+0 · sgo-props 0+6", DashboardModel.apiDetail(s, "sgo"))
        assertTrue(DashboardModel.apiDetail(s, "boltodds").contains("socket not connected"))
        assertTrue("no report yet: no health claimed", DashboardModel.apiHealth(st()).isEmpty())
    }

    @Test
    fun `history adds one point per finished scan, keeps a day, and survives a round trip`() {
        val a = DashboardModel.record(emptyList(), st())
        assertEquals(1, a.size)
        assertEquals(230, a[0].posEv)
        assertEquals(124.27, a[0].cash, 0.001)
        assertSame("the same scan again adds nothing", a, DashboardModel.record(a, st()))
        val b = DashboardModel.record(a, st { copy(lastScanEndedAtMs = now - 60_000) })
        assertEquals(2, b.size)
        var long = emptyList<DashboardModel.Point>()
        for (i in 1..400) long = DashboardModel.record(long, st { copy(lastScanEndedAtMs = now + i * 1000L) })
        assertEquals(DashboardModel.MAX_POINTS, long.size)
        assertEquals(b, DashboardModel.decode(DashboardModel.encode(b)))
        assertTrue(DashboardModel.decode("not json").isEmpty())
        assertTrue("a status with no finished scan adds nothing", DashboardModel.record(emptyList(), CloudStatus()).isEmpty())
    }
}
