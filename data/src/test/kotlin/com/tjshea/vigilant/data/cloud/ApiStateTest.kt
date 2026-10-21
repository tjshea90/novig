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

    @Test fun `every key refused is red unless a fresh check was accepted (then yellow), one refused key is yellow`() {
        val refused = KeyUsage(periodStart = month, refused = true, refusals = 3)
        assertEquals(Light.YELLOW, ApiState.of(check(), view(refused, refused), CloudDashboard.Health.WORKING, now).light)
        assertEquals(Light.RED, ApiState.of(check(), view(refused, refused), CloudDashboard.Health.WORKING, now, engineRunning = false).light)
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

    // ---- XO1 (Tj, 2026-10-21): "some of the apis claim to be failing, but when I test the keys they are green and accepted"

    @Test fun `a failing check while the scans read the API fine is yellow with both facts, never red`() {
        val c = check(ok = false, streak = 6, keys = 10).copy(scanOkAtMs = now - 60_000L, message = "no answer within 20 s")
        val v = ApiState.of(c, view(good()), CloudDashboard.Health.WORKING, now)
        assertEquals(Light.YELLOW, v.light)
        assertTrue(v.words, v.words.contains("scans are reading it fine") && v.words.contains("no answer within 20 s"))
        // the same check with no scan evidence is red
        assertEquals(Light.RED, ApiState.of(c.copy(scanOkAtMs = 0L), view(good()), CloudDashboard.Health.FAILING, now).light)
        // scan evidence older than ten minutes does not count
        assertEquals(Light.RED, ApiState.of(c.copy(scanOkAtMs = now - 11 * 60_000L), view(good()), CloudDashboard.Health.FAILING, now).light)
    }

    @Test fun `the engine cannot use it but the phones fresh test is accepted is yellow and says the keys or address differ`() {
        val c = check(ok = false, streak = 4).copy(message = "Refused (HTTP 403): this key is not accepted.", kind = "refused", fix = "asked for a fresh runner (1 of 3)")
        val phone = ApiState.PhoneCheck(now - 20_000L, ok = true, keysOk = 1, keys = 1, message = "Active and accepted")
        val v = ApiState.of(c, view(good()), CloudDashboard.Health.FAILING, now, phone)
        assertEquals(Light.YELLOW, v.light)
        assertTrue(v.words, v.words.contains("this phone's test is accepted") && v.words.contains("fresh runner (1 of 3)"))
        // a phone test that is old is not evidence
        assertEquals(Light.RED, ApiState.of(c, view(good()), CloudDashboard.Health.FAILING, now, phone.copy(atMs = now - 20 * 60_000L)).light)
    }

    @Test fun `the managers word is on the line of a failing API and a switched-off API says why`() {
        val c = check(ok = false, streak = 5).copy(fix = "restarted its connection after 3 failed checks (unreachable)")
        assertTrue(ApiState.of(c, view(good()), null, now).words.endsWith("engine: restarted its connection after 3 failed checks (unreachable)"))
        val off = ApiCheckLine("sgo", "SportsGameOdds", on = false, fix = "switched off: it was not accepted on 3 fresh runners in a row. Fix its key or plan, then switch it on in Settings › APIs.")
        val v = ApiState.of(off, view(good()), null, now)
        assertEquals(Light.OFF, v.light); assertTrue(v.words.startsWith("switched off: it was not accepted"))
    }

    @Test fun `with the engine not running the phones own fresh test is the evidence and an old engine check is not shown as live`() {
        val phone = ApiState.PhoneCheck(now - 5_000L, ok = true, keysOk = 3, keys = 3, message = "ok")
        val v = ApiState.of(check(at = now - 5 * 3_600_000L), view(good()), CloudDashboard.Health.WORKING, now, phone, engineRunning = false)
        assertEquals(Light.GREEN, v.light); assertTrue(v.words, v.words.contains("tested from this phone") && v.words.contains("engine is not running"))
        val none = ApiState.of(check(at = now - 5 * 3_600_000L), view(good()), CloudDashboard.Health.WORKING, now, null, engineRunning = false)
        assertEquals(Light.UNKNOWN, none.light); assertTrue(none.words, none.words.startsWith("engine not running"))
        val bad = ApiState.of(check(at = now - 5 * 3_600_000L), view(good()), null, now, phone.copy(ok = false, keysOk = 0, message = "Refused (HTTP 401)"), engineRunning = false)
        assertEquals(Light.RED, bad.light)
    }

    @Test fun `an API that checks out but has a feed silent for several scans is amber with the feed's own words`() {
        val problem = "feed bolt silent for 5 scans: BoltOdds: the lines socket is not connected (Too many concurrent connections.)"
        val v = ApiState.of(check(), view(good()), CloudDashboard.Health.WORKING, now, feedProblem = problem)
        assertEquals(Light.YELLOW, v.light); assertTrue(v.words, v.words.startsWith("feed bolt silent for 5 scans"))
        // never when the engine is not running (the alert is old news then), and a check that is red stays red
        assertEquals(Light.GREEN, ApiState.of(check(), view(good()), CloudDashboard.Health.WORKING, now, engineRunning = true, feedProblem = null).light)
        assertEquals(Light.RED, ApiState.of(check(ok = false, streak = 3), view(good()), CloudDashboard.Health.WORKING, now, feedProblem = problem).light)
    }
}
