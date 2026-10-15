package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDashboardTest {
    private val now = 1_792_092_000_000L   // 2026-10-15 19:20 UTC
    private val day = QuotaPolicy.PROPLINE.periodStart(now)

    private fun status(vararg feeds: FeedLine) = CloudStatus(
        runId = "123", heartbeatMs = now - 4_000, passes = 15, scanMs = 350_000, rows = 38, lastScanEndedAtMs = now - 60_000, feedReport = feeds.toList(), feedReportAtMs = now - 60_000,
        sources = feeds.map { it.id }, novigRoute = "Novig read-only key active: 16 reads a second, websocket on", tradeMode = "live", tradeNote = "live: looked 33, passed 0, ordered 0 (\$0.00)",
    )

    private val book = UsageBook(mapOf(
        "propline" to ProviderUsage(
            keys = mapOf(
                "aaaaaaaaaa" to KeyUsage(periodStart = day, used = 1000, remaining = 0, limit = 1000, depletedUntil = day + 86_400_000L, calls = 534, figureAtMs = now - 600_000),
                "bbbbbbbbbb" to KeyUsage(periodStart = day, used = 206, remaining = 794, limit = 1000, calls = 207, figureAtMs = now - 5_000),
            ), dayStart = day, callsToday = 741),
        "sgo" to ProviderUsage(keys = mapOf("cccccccccc" to KeyUsage(periodStart = day, used = 2590, calls = 2590)), dayStart = day, callsToday = 2590),
    ))

    private fun page(st: CloudStatus) = CloudDashboard.markdown(st, book, { p -> when (p.id) { "propline" -> listOf("aaaaaaaaaa", "bbbbbbbbbb"); "sgo" -> listOf("cccccccccc"); else -> emptyList() } }, now)

    @Test
    fun `health is read from what the feed answered on the last scan`() {
        assertEquals(CloudDashboard.Health.WORKING, CloudDashboard.health(listOf(FeedLine("sgo", fetched = 8), FeedLine("sgo-props", fetched = 0, reused = 6)), true))
        assertEquals(CloudDashboard.Health.FAILING, CloudDashboard.health(listOf(FeedLine("bolt", error = "the lines socket is not connected")), true))
        assertEquals(CloudDashboard.Health.WORKING, CloudDashboard.health(listOf(FeedLine("bolt", fetched = 6), FeedLine("bolt-props", error = "x")), true))
        assertEquals(CloudDashboard.Health.SILENT, CloudDashboard.health(listOf(FeedLine("parlay"), FeedLine("parlay_props")), true))
        assertEquals(CloudDashboard.Health.STANDING_BY, CloudDashboard.health(listOf(FeedLine("oddsapi", standingBy = 4)), true))
        assertEquals(CloudDashboard.Health.HELD_BACK, CloudDashboard.health(listOf(FeedLine("parlay", heldBack = "day's share spent")), true))
        assertEquals(CloudDashboard.Health.OFF, CloudDashboard.health(emptyList(), false))
        assertEquals(CloudDashboard.Health.NOT_ASKED, CloudDashboard.health(emptyList(), true))
    }

    @Test
    fun `feeds map to the meter they spend`() {
        assertEquals("sgo", CloudDashboard.providerOf("sgo-props"))
        assertEquals("boltodds", CloudDashboard.providerOf("bolt-props"))
        assertEquals("parlay", CloudDashboard.providerOf("parlay_1h"))
        assertEquals("oddspapi_free", CloudDashboard.providerOf("oddspapi_free-props"))
        assertEquals("propline", CloudDashboard.providerOf("propline_props"))
    }

    @Test
    fun `the page shows each api's health, what is left, the active key and a spent key, from the provider's own figure`() {
        val md = page(status(FeedLine("propline", fetched = 6, matched = 120), FeedLine("sgo", fetched = 8, matched = 300), FeedLine("parlay", fetched = 0), FeedLine("bolt", error = "BoltOdds: the lines socket is not connected")))
        assertTrue(md.contains("PropLine | ✅ working"))
        assertTrue("one key spent, one active: the total is the second's 794 of 2,000", md.contains("794 of 2,000 requests (39%)"))
        assertTrue(md.contains("▶ active"))
        assertTrue(md.contains("spent"))
        assertTrue("the provider's own figure and how old it is", md.contains("provider, read 5 s ago"))
        assertTrue("SGO has no allowance published, only a count", md.contains("SportsGameOdds | ✅ working") && md.contains("no allowance published"))
        assertTrue(md.contains("BoltOdds | ❌ failing"))
        assertTrue(md.contains("ParlayAPI | ⚠️ answered nothing"))
        assertTrue("keyless providers are not metered", md.contains("no key, not metered"))
        assertTrue(md.contains("Running") && md.contains("pass 15"))
        assertTrue(md.contains("accepted by Novig"))
    }

    @Test
    fun `a live wallet that was never read says unknown, never zero`() {
        val md = page(status(FeedLine("sgo", fetched = 1)).copy(walletCash = -1.0, walletReadAtMs = 0, walletCheckedAtMs = now - 600_000))
        assertTrue(md.contains("not read yet") && md.contains("NOT zero"))
        val read = page(status(FeedLine("sgo", fetched = 1)).copy(walletCash = 123.45, walletEquity = 130.0, walletReadAtMs = now - 30_000))
        assertTrue(read.contains("cash \$123.45") && read.contains("equity \$130.00"))
    }

    @Test
    fun `an engine whose heartbeat is old is called not responding, and a refused address is stated`() {
        val md = page(status(FeedLine("sgo", fetched = 1)).copy(heartbeatMs = now - 10 * 60_000, addressHandovers = 2, addressHandoverAtMs = now - 120_000))
        assertTrue(md.contains("NOT RESPONDING"))
        assertTrue(md.contains("REFUSED 2 time(s)"))
    }

    @Test
    fun `no key text can reach the page`() {
        val withNote = UsageBook(mapOf("sgo" to ProviderUsage(keys = mapOf("cccccccccc" to KeyUsage(periodStart = day, used = 1, lastNote = "••••")))))
        val md = CloudDashboard.markdown(status(FeedLine("sgo", fetched = 1)), withNote, { listOf("cccccccccc") }, now)
        assertFalse(md.contains("sk_") || md.contains("-----BEGIN"))
        assertTrue("only the id, masked", md.contains("cccc…cccc"))
    }
}
