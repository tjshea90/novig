package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.cloud.ApiState.Light
import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CH48 (Tj, 2026-10-20): green = checked up and active, yellow = slow / some keys refused / a failed check being retried, red = no connection, no credits or every key refused, grey = off; never green from the last scan alone. */
class ApiStateTest {
    private val now = 1_800_000_000_000L
    private val policy = QuotaPolicy.PARLAY
    private val month = policy.periodStart(now)

    private fun view(vararg keys: KeyUsage) = UsageViews.build(policy, keys.indices.map { "key$it" }, ProviderUsage(keys = keys.indices.associate { "key$it" to keys[it] }), now)
    private fun good(left: Int = 90_000) = KeyUsage(periodStart = month, used = 100_000 - left, remaining = left, limit = 100_000)
    private fun check(ok: Boolean = true, ms: Long = 300, at: Long = now - 30_000, streak: Int = if (ok) 0 else 1, keysOk: Int = if (ok) 1 else 0, keys: Int = 1) =
        ApiCheckLine("parlay", "ParlayAPI", on = true, atMs = at, ok = ok, keysOk = keysOk, keys = keys, ms = ms, message = if (ok) "Active and accepted" else "Could not be reached", failStreak = streak)

    @Test fun `a checked, fresh, accepted API with credits is green`() {
        val v = ApiState.of(check(), view(good()), CloudDashboard.Health.WORKING, now)
        assertEquals(Light.GREEN, v.light); assertTrue(v.words.startsWith("up"))
    }

    @Test fun `an API that is off or has no key is grey and says it is never called`() {
        assertEquals(Light.OFF, ApiState.of(ApiCheckLine("pinnapi", "pinnapi", on = false), view(good()), CloudDashboard.Health.WORKING, now).light)
        assertEquals("no key saved: never called", ApiState.of(ApiCheckLine("pinnapi", "pinnapi", on = false, hasKey = false), null, null, now).words)
    }

    @Test fun `the last scan alone never makes a card green`() {
        assertEquals(Light.UNKNOWN, ApiState.of(null, view(good()), CloudDashboard.Health.WORKING, now).light)
        assertEquals(Light.RED, ApiState.of(null, view(good()), CloudDashboard.Health.FAILING, now).light)
    }

    @Test fun `every key refused is red even when a check passed, one refused key is yellow`() {
        val refused = KeyUsage(periodStart = month, refused = true, refusals = 3)
        assertEquals(Light.RED, ApiState.of(check(), view(refused, refused), CloudDashboard.Health.WORKING, now).light)
        val v = ApiState.of(check(), view(good(), refused), CloudDashboard.Health.WORKING, now)
        assertEquals(Light.YELLOW, v.light); assertTrue(v.words.contains("1 of 2 keys refused"))
    }

    @Test fun `a first failed check is yellow and retrying, two in a row are red, a stale or slow one is yellow`() {
        assertEquals(Light.YELLOW, ApiState.of(check(ok = false, streak = 1), view(good()), null, now).light)
        assertEquals(Light.RED, ApiState.of(check(ok = false, streak = 2), view(good()), null, now).light)
        assertEquals(Light.YELLOW, ApiState.of(check(at = now - 20 * 60_000L), view(good()), CloudDashboard.Health.WORKING, now).light)
        assertEquals(Light.YELLOW, ApiState.of(check(ms = 9_000), view(good()), CloudDashboard.Health.WORKING, now).light)
    }

    @Test fun `credits all used is red and low credits is yellow`() {
        val spent = KeyUsage(periodStart = month, used = 100_000, remaining = 0, limit = 100_000, depletedUntil = now + 86_400_000L)
        assertEquals(Light.RED, ApiState.of(check(), view(spent), CloudDashboard.Health.WORKING, now).light)
        assertEquals(Light.YELLOW, ApiState.of(check(), view(good(left = 5_000)), CloudDashboard.Health.WORKING, now).light)
    }
}
