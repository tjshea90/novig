package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiAdvisorTest {
    private val now = 1_792_092_000_000L
    private val day = QuotaPolicy.PROPLINE.periodStart(now)
    private val monthStart = QuotaPolicy.PARLAY.periodStart(now)

    private val allOff = ApiToggles.rest(ScanSettings(), ApiToggles.IDS)
    private fun on(vararg ids: String): ScanSettings = ids.fold(allOff) { s, id -> ApiToggles.setOn(s, id, true) }
    private fun keys(vararg byId: Pair<String, List<String>>): (QuotaPolicy) -> List<String> = { p -> byId.toMap().entries.firstOrNull { ApiToggles.policy(it.key)?.id == p.id }?.value.orEmpty() }

    @Test
    fun `an api that is on with no key on github is a key to add, and an api that is off is never judged`() {
        val a = ApiAdvisor.advise(UsageBook(), emptyList(), emptyList(), on("sgo"), keys(), now)
        assertEquals(listOf("key:sgo"), a.map { it.id })
        assertEquals(2, a.first().severity)
        assertTrue(ApiAdvisor.advise(UsageBook(), emptyList(), emptyList(), allOff, keys(), now).isEmpty())
    }

    @Test
    fun `a used up or low allowance is a refill, with the numbers and the reset`() {
        val book = UsageBook(mapOf("propline" to ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = day, used = 1000, remaining = 0, limit = 1000, depletedUntil = day + 86_400_000L)), dayStart = day, callsToday = 900)))
        val a = ApiAdvisor.advise(book, emptyList(), emptyList(), on("propline"), keys("propline" to listOf("k1")), now)
        assertEquals("refill:propline", a.first().id)
        assertEquals(2, a.first().severity)
        assertTrue(a.first().body.contains("0 of 1000"))
        val low = UsageBook(mapOf("propline" to ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = day, used = 850, remaining = 150, limit = 1000)), dayStart = day, callsToday = 10)))
        val b = ApiAdvisor.advise(low, emptyList(), emptyList(), on("propline"), keys("propline" to listOf("k1")), now)
        assertEquals(listOf("refill:propline" to 1), b.map { it.id to it.severity })
        val plenty = UsageBook(mapOf("propline" to ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = day, used = 100, remaining = 900, limit = 1000)), dayStart = day, callsToday = 10)))
        assertTrue(ApiAdvisor.advise(plenty, emptyList(), emptyList(), on("propline"), keys("propline" to listOf("k1")), now).isEmpty())
    }

    @Test
    fun `a monthly allowance that will not last to its reset is a buy, and a refused key is a fix`() {
        // 5 days into the month's pace: 6000 credits used today alone against 8000 left and 15+ days to go
        val book = UsageBook(mapOf("parlay" to ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = monthStart, used = 12_000, remaining = 8_000, limit = 20_000)), dayStart = day, callsToday = 6_000)))
        val a = ApiAdvisor.advise(book, emptyList(), emptyList(), on("parlay"), keys("parlay" to listOf("k1")), now)
        assertTrue(a.any { it.id == "buy:parlay" })
        // Thousands of free calls today (The Odds API's /events cost 0) with credits well inside the month's pace: no buy (AF, 2026-10-22)
        val free = UsageBook(mapOf("parlay" to ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = monthStart, used = 4_000, remaining = 16_000, limit = 20_000)), dayStart = day, callsToday = 9_000)))
        assertFalse(ApiAdvisor.advise(free, emptyList(), emptyList(), on("parlay"), keys("parlay" to listOf("k1")), now).any { it.id == "buy:parlay" })
        val refused = UsageBook(mapOf("sgo" to ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = day, refused = true)), dayStart = day)))
        val f = ApiAdvisor.advise(refused, emptyList(), emptyList(), on("sgo"), keys("sgo" to listOf("k1")), now)
        assertTrue(f.any { it.id == "fix:sgo" && it.severity == 2 })
    }

    @Test
    fun `a feed that stays silent is flagged for the api that owns it, and advice is sorted most urgent first`() {
        val silent = listOf("parlay_props (4 scans in a row)")
        val book = UsageBook(mapOf("parlay" to ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = monthStart, used = 100, remaining = 19_900, limit = 20_000)), dayStart = day, callsToday = 3)))
        val a = ApiAdvisor.advise(book, emptyList(), silent, on("parlay", "sgo"), keys("parlay" to listOf("k1")), now)
        assertEquals(listOf("key:sgo", "off:parlay"), a.map { it.id })
        assertFalse(a.any { it.id.startsWith("refill") })
    }
}
