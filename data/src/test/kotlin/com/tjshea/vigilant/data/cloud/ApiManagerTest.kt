package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * XO1 (Tj, 2026-10-21): the API manager's ladder. A failing check is diagnosed, the API restarted, an important API earns a fresh runner, and after a few new runners it is switched off with a message; a check that
 * fails while the scans read the API fine is left alone; a passing check clears everything; every API failing at once is the runner, not the APIs.
 */
class ApiManagerTest {
    private class Fake : ApiChecks {
        val map = LinkedHashMap<String, ApiCheckLine>()
        val rechecked = ArrayList<String?>()
        override fun lines() = map.values.toList()
        override suspend fun tick(s: ScanSettings, maxPerTick: Int) {}
        override fun recheckNow(id: String?) { rechecked += id }
    }

    private class Hooked : ApiManager.Hooks {
        val restarts = ArrayList<String>()
        val swaps = ArrayList<String>()
        val offs = ArrayList<String>()
        val said = ArrayList<Triple<String, String, Int>>()
        val sayBodies = ArrayList<String>()
        val answered = HashMap<String, Long>()
        var silent: List<DashboardModel.SilentFeed> = emptyList()
        var handOverFree = true
        override suspend fun restart(id: String) { restarts += id }
        override fun freshRunner(why: String): Boolean { swaps += why; return handOverFree }
        override suspend fun turnOff(id: String, why: String) { offs += id }
        override suspend fun say(key: String, title: String, body: String, priority: Int) { said += Triple(key, title, priority); sayBodies += body }
        override fun answeredAt(id: String) = answered[id] ?: 0L
        override fun silentFeeds() = silent
        override fun log(line: String) {}
    }

    private var now = 1_800_000_000_000L
    private fun line(id: String, ok: Boolean, streak: Int = 0, kind: String = "", atMs: Long = now, message: String = if (ok) "Active and accepted" else "Could not be reached") =
        ApiCheckLine(id, ApiToggles.name(id), on = true, hasKey = true, atMs = atMs, ok = ok, keysOk = if (ok) 1 else 0, keys = 1, message = message, failStreak = streak, kind = kind)

    private fun manager(f: Fake, h: Hooked, swaps: Map<String, Int> = emptyMap()) = ApiManager(f, h, { now }, swaps)
    private suspend fun ApiManager.turn() = step(ScanSettings())

    @Test
    fun `a healthy API is left completely alone`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h)
        f.map["sgo"] = line("sgo", true)
        m.turn()
        assertTrue(h.restarts.isEmpty() && h.swaps.isEmpty() && h.offs.isEmpty() && h.said.isEmpty())
        assertEquals("", m.lines().single().fix)
    }

    @Test
    fun `the climb is restart, then a fresh runner, then off after three runners that did not help`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h)
        f.map["sgo"] = line("sgo", false, streak = 1, kind = "unreachable")
        f.map["parlay"] = line("parlay", true)   // not everything is failing: this is the API, not the runner
        m.turn()
        assertTrue("one failure is only watched", h.restarts.isEmpty())
        f.map["sgo"] = line("sgo", false, streak = 3, kind = "unreachable"); now += 40_000L
        m.turn()
        assertEquals("restart at the third failure", listOf("sgo"), h.restarts)
        assertTrue("and it is checked again at once", f.rechecked.contains("sgo"))
        m.turn(); assertEquals("only once per failure", 1, h.restarts.size)
        f.map["sgo"] = line("sgo", false, streak = 5, kind = "unreachable"); now += 3 * 60_000L
        m.turn()
        assertEquals("not yet: it has not been failing for 4 minutes", 0, h.swaps.size)
        now += 2 * 60_000L; f.map["sgo"] = line("sgo", false, streak = 6, kind = "unreachable", atMs = now)
        m.turn()
        assertEquals("a fresh runner is asked for", 1, h.swaps.size)
        assertEquals(mapOf("sgo" to 1), m.swaps())
        assertEquals("fresh-runner", m.fixes().single().state)
        // The next jobs: the count is carried, the API fails again for long enough each time: the second and third swaps, then off.
        var prev = m
        for (n in 2..3) {
            val m2 = manager(f, h, prev.swaps())
            now += 60_000L
            f.map["sgo"] = line("sgo", false, streak = 1, kind = "unreachable", atMs = now); m2.turn()
            now += 5 * 60_000L
            f.map["sgo"] = line("sgo", false, streak = 5, kind = "unreachable", atMs = now); m2.turn()
            assertEquals("swap $n", n, h.swaps.size)
            assertEquals(mapOf("sgo" to n), m2.swaps())
            prev = m2
        }
        val m3 = manager(f, h, prev.swaps())
        now += 60_000L; f.map["sgo"] = line("sgo", false, streak = 1, kind = "unreachable", atMs = now); m3.turn()
        now += 5 * 60_000L; f.map["sgo"] = line("sgo", false, streak = 5, kind = "unreachable", atMs = now); m3.turn()
        assertEquals("off after the third runner did not help", listOf("sgo"), h.offs)
        assertEquals("no fourth runner", 3, h.swaps.size)
        assertTrue("Tj is told, with a notification", h.said.any { it.second.startsWith("Switched") && it.third == 1 })
        assertEquals("off", m3.fixes().single().state)
    }

    @Test
    fun `a check that fails while the scans read the API fine is the checks trouble, nothing is restarted or replaced`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h)
        f.map["parlay"] = line("parlay", false, streak = 6, kind = "unreachable", atMs = now - 10 * 60_000L)
        f.map["sgo"] = line("sgo", true)
        h.answered["parlay"] = now - 60_000L
        m.turn()
        assertTrue(h.restarts.isEmpty() && h.swaps.isEmpty() && h.offs.isEmpty())
        assertTrue(m.lines().first { it.id == "parlay" }.fix.contains("scans are reading it fine"))
        assertEquals(now - 60_000L, m.lines().first { it.id == "parlay" }.scanOkAtMs)
    }

    @Test
    fun `a passing check clears the climb and says so after a fresh runner`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h, mapOf("sgo" to 2))
        f.map["sgo"] = line("sgo", true)
        m.turn()
        assertEquals(emptyMap<String, Int>(), m.swaps())
        assertTrue(h.said.any { it.second.contains("is working again") })
    }

    @Test
    fun `every API failing at once is the runner, one swap and no switch touched`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h)
        for (id in listOf("sgo", "parlay", "propline", "boltodds")) f.map[id] = line(id, false, streak = 4, kind = "unreachable")
        m.turn()
        assertEquals(1, h.swaps.size)
        assertEquals(mapOf(ApiManager.RUNNER to 1), m.swaps())
        assertTrue(h.offs.isEmpty())
        // even when they go on failing, no API is switched off for it
        f.map.keys.toList().forEach { f.map[it] = line(it, false, streak = 9, kind = "unreachable", atMs = now) }
        now += 30 * 60_000L; m.turn(); m.turn()
        assertTrue("never off for a wide failure", h.offs.isEmpty())
    }

    @Test
    fun `an API that is not important gets no runner, only a restart and then off after 30 minutes`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h)
        f.map["odds_api"] = line("odds_api", false, streak = 3, kind = "error"); f.map["sgo"] = line("sgo", true)
        m.turn()
        assertEquals(listOf("odds_api"), h.restarts)
        f.map["odds_api"] = line("odds_api", false, streak = 7, kind = "error", atMs = now); now += 10 * 60_000L
        m.turn()
        assertTrue("no runner for it", h.swaps.isEmpty()); assertTrue(h.offs.isEmpty())
        now += 25 * 60_000L; f.map["odds_api"] = line("odds_api", false, streak = 12, kind = "error", atMs = now)
        m.turn()
        assertEquals(listOf("odds_api"), h.offs)
    }

    @Test
    fun `a hand-over that is already on its way is not asked for twice and not counted`() = runBlocking {
        val f = Fake(); val h = Hooked(); h.handOverFree = false; val m = manager(f, h)
        f.map["sgo"] = line("sgo", false, streak = 6, kind = "unreachable", atMs = now - 10 * 60_000L); f.map["parlay"] = line("parlay", true)
        m.turn(); m.turn()
        assertEquals(emptyMap<String, Int>(), m.swaps())
        assertFalse(h.offs.isNotEmpty())
    }

    @Test
    fun `switching an API that was turned off back on starts the ladder again`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h, mapOf("sgo" to 3))
        f.map["sgo"] = line("sgo", false, streak = 5, kind = "refused", atMs = now - 6 * 60_000L); f.map["parlay"] = line("parlay", true)
        m.turn()
        assertEquals(listOf("sgo"), h.offs)
        f.map["sgo"] = ApiCheckLine("sgo", "SportsGameOdds", on = false)
        m.turn()
        assertEquals("off", m.fixes().single { it.id == "sgo" }.state)
        f.map["sgo"] = line("sgo", true)   // Tj fixed the key and switched it on: it passes
        m.turn()
        assertTrue(m.fixes().none { it.id == "sgo" })
        assertEquals(emptyMap<String, Int>(), m.swaps())
    }

    @Test
    fun `switch ids of a feed name the right APIs`() {
        assertEquals(listOf("pinnwire", "pinnapi"), ApiManager.switchIdsOfFeed("pinnacle"))
        assertEquals(listOf("sgo"), ApiManager.switchIdsOfFeed("sgo-props"))
        assertEquals(listOf("boltodds"), ApiManager.switchIdsOfFeed("bolt-props"))
        assertEquals(listOf("odds_api"), ApiManager.switchIdsOfFeed("oddsapi_props"))
        assertEquals(listOf("parlay"), ApiManager.switchIdsOfFeed("parlay_1h"))
        assertEquals(listOf("kalshi"), ApiManager.switchIdsOfFeed("kalshi"))
    }

    @Test
    fun `an API whose check passes but whose feed stays silent says so on its card at once and to Tj once a day after twenty minutes, and nothing is restarted, replaced or switched off`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h)
        f.map["boltodds"] = line("boltodds", true)
        h.silent = listOf(DashboardModel.SilentFeed("bolt", 5, "BoltOdds: the lines socket is not connected (BoltOdds refused the key (Too many concurrent connections.))"))
        m.turn()
        assertTrue(m.lines().single().fix.startsWith("its check passes but feed bolt silent for 5 scans"))
        assertTrue("not yet said: the card says it, Tj is told after twenty minutes", h.said.isEmpty())
        now += 21 * 60_000L; f.map["boltodds"] = line("boltodds", true)
        m.turn()
        assertEquals(1, h.said.size)
        assertTrue(h.said[0].second.contains("a feed has been silent") && h.said[0].third == 0)
        m.turn(); now += 60_000L; m.turn()
        assertEquals("once a day", 1, h.said.size)
        assertTrue(h.restarts.isEmpty() && h.swaps.isEmpty() && h.offs.isEmpty())
        assertEquals("watching", m.fixes().single().state)
        // the feed answers again: the note goes
        h.silent = emptyList()
        m.turn()
        assertEquals("", m.lines().single().fix)
        assertTrue(m.fixes().isEmpty())
    }

    @Test
    fun `the explanation of a refused connection names the provider's connection limit`() = runBlocking {
        val f = Fake(); val h = Hooked(); val m = manager(f, h)
        f.map["boltodds"] = line("boltodds", true)
        h.silent = listOf(DashboardModel.SilentFeed("bolt", 5, "BoltOdds: the lines socket is not connected (BoltOdds refused the key (Too many concurrent connections.))"))
        m.turn(); now += 25 * 60_000L; f.map["boltodds"] = line("boltodds", true); m.turn()
        val body = h.sayBodies.single()
        assertTrue(body, body.contains("allows only a few connections at once") && body.contains("nothing was switched off"))
    }
}
