package com.tjshea.vigilant.data.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-15: "Make sure I can still see accurate api usage meters in the app on my phone" - with the scan on GitHub two machines spend one key. */
class UsageMergeTest {
    private val now = 1_800_000_000_000L
    private val day = QuotaPolicy.PROPLINE.periodStart(now)
    private val month = QuotaPolicy.PARLAY.periodStart(now)
    private val keyed = QuotaPolicy.PROPLINE // a daily provider that counts its own requests
    private val metered = QuotaPolicy.PARLAY // a monthly provider that sends credits left

    private fun book(id: String, vararg k: Pair<String, KeyUsage>, calls: Int = 0, throttled: Int = 0) =
        UsageBook(mapOf(id to ProviderUsage(keys = mapOf(*k), dayStart = QuotaPolicy.NOVIG.periodStart(now), callsToday = calls, throttledToday = throttled)))

    @Test
    fun `a provider that sends no figure is counted by both machines and the counts add up`() {
        val local = book("propline", "k" to KeyUsage(periodStart = day, used = 12, calls = 12, lastCallMs = now - 5_000), calls = 12)
        val cloud = book("propline", "k" to KeyUsage(periodStart = day, used = 300, calls = 300, lastCallMs = now - 1_000), calls = 300)
        val m = UsageMerge.merge(local, cloud, now).providers.getValue("propline")
        val k = m.keys.getValue("k")
        assertEquals(312, k.used); assertEquals(312, k.calls); assertEquals(312, m.callsToday)
        assertEquals(now - 1_000, k.lastCallMs)
        assertNull(k.remaining)
        // the meter then says 312 of the 1,000 a day
        assertEquals(688, k.left(keyed))
    }

    @Test
    fun `the provider's newest figure wins, whoever read it, and what was used follows from the allowance`() {
        val local = book("parlay", "k" to KeyUsage(periodStart = month, used = 100, remaining = 900, limit = 1000, calls = 3, lastCallMs = now - 600_000, figureAtMs = now - 600_000))
        val cloud = book("parlay", "k" to KeyUsage(periodStart = month, used = 380, remaining = 620, limit = 1000, calls = 80, lastCallMs = now - 20_000, figureAtMs = now - 20_000))
        val k = UsageMerge.merge(local, cloud, now).providers.getValue("parlay").keys.getValue("k")
        assertEquals(620, k.remaining); assertEquals(380, k.used); assertEquals(1000, k.limit)
        assertEquals(83, k.calls)
        // the other way round: the phone read its account page just now (an authoritative figure), after the engine's last call
        val phoneNewer = book("parlay", "k" to KeyUsage(periodStart = month, used = 400, remaining = 600, limit = 1000, calls = 3, lastCallMs = now - 600_000, figureAtMs = now - 2_000))
        val k2 = UsageMerge.merge(phoneNewer, cloud, now).providers.getValue("parlay").keys.getValue("k")
        assertEquals(600, k2.remaining); assertEquals(400, k2.used)
        assertEquals("the call counts still add up", 83, k2.calls)
    }

    @Test
    fun `a key spent on one machine is spent, a rest a newer figure lifted is lifted, and a throttle on either counts`() {
        val spent = KeyUsage(periodStart = month, used = 1000, remaining = 0, limit = 1000, depletedUntil = now + 3_600_000, calls = 5, lastCallMs = now - 30_000, figureAtMs = now - 30_000)
        val fresh = KeyUsage(periodStart = month, used = 0, calls = 0)
        val m1 = UsageMerge.merge(book("parlay", "k" to fresh), book("parlay", "k" to spent), now).providers.getValue("parlay").keys.getValue("k")
        assertNotNull(m1.depletedUntil); assertEquals(0, m1.remaining)
        // the phone's stale rest (set a day ago) against the engine's newer figure with credits left: not spent
        val staleRest = KeyUsage(periodStart = month, used = 1000, remaining = 0, limit = 1000, depletedUntil = now + 3_600_000, lastCallMs = now - 86_400_000, figureAtMs = now - 86_400_000)
        val newFigure = KeyUsage(periodStart = month, used = 100, remaining = 900, limit = 1000, lastCallMs = now - 10_000, figureAtMs = now - 10_000)
        val m2 = UsageMerge.merge(book("parlay", "k" to staleRest), book("parlay", "k" to newFigure), now).providers.getValue("parlay").keys.getValue("k")
        assertNull("the stale rest is lifted", m2.depletedUntil); assertEquals(900, m2.remaining)
        // a per-minute cool-down on either machine holds the key
        val cooling = KeyUsage(periodStart = day, used = 1, coolUntil = now + 20_000, lastCallMs = now - 1_000)
        val m3 = UsageMerge.merge(book("propline", "k" to KeyUsage(periodStart = day, used = 2)), book("propline", "k" to cooling), now).providers.getValue("propline").keys.getValue("k")
        assertEquals(now + 20_000, m3.coolUntil)
    }

    @Test
    fun `a new day or month starts fresh on both machines, keys and providers on one side only are kept, and nothing is invented`() {
        val yesterday = QuotaPolicy.NOVIG.periodStart(now) - 86_400_000L
        val oldLocal = UsageBook(mapOf("propline" to ProviderUsage(keys = mapOf("k" to KeyUsage(periodStart = yesterday, used = 900)), dayStart = yesterday, callsToday = 900)))
        val cloud = book("propline", "k" to KeyUsage(periodStart = day, used = 7, calls = 7, lastCallMs = now - 1_000), calls = 7)
        val m = UsageMerge.merge(oldLocal, cloud, now).providers.getValue("propline")
        assertEquals("yesterday's 900 do not count today", 7, m.keys.getValue("k").used)
        assertEquals(7, m.callsToday)
        // a key only the engine has, and a provider only the engine has
        val both = UsageMerge.merge(book("propline", "a" to KeyUsage(periodStart = day, used = 1)), UsageBook(mapOf("sgo" to ProviderUsage(callsToday = 4, dayStart = QuotaPolicy.NOVIG.periodStart(now))) + book("propline", "b" to KeyUsage(periodStart = day, used = 2)).providers), now)
        assertEquals(setOf("a", "b"), both.providers.getValue("propline").keys.keys)
        assertEquals(4, both.providers.getValue("sgo").callsToday)
        // no cloud usage at all: the phone's book is returned as it is
        val only = book("propline", "k" to KeyUsage(periodStart = day, used = 3))
        assertTrue(UsageMerge.merge(only, UsageBook(), now) === only)
        assertFalse(UsageMerge.merge(only, only, now).providers.getValue("propline").keys.getValue("k").used == 3)
    }

    @Test
    fun `the keyless counters add up too - Novig's reads from the phone and from the engine`() {
        val local = book("novig", calls = 120, throttled = 1)
        val cloud = book("novig", calls = 5_000, throttled = 4)
        val m = UsageMerge.merge(local, cloud, now).providers.getValue("novig")
        assertEquals(5_120, m.callsToday); assertEquals(5, m.throttledToday)
    }
}
