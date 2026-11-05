package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.novig.NovigParlay.Leg
import com.tjshea.vigilant.data.novig.NovigParlay.Result
import com.tjshea.vigilant.data.reference.LineKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parlays on Novig (NC1): the keyless price endpoint's answers as probed live on 2026-11-05, the documented combination rules, and the multi-leg deep link. */
class NovigParlayTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @After fun stop() = server.shutdown()

    @Test
    fun `the answers Novig gave on 2026-11-05 parse to a price, a refusal, no price and a rate limit`() {
        val ok = NovigParlay.parse(200, """{"price":"0.56","expiresAt":1793856682164,"serverNow":1793856652164}""") as Result.Priced
        assertEquals(0.56, ok.quote.price, 0.0)
        assertEquals(30_000L, ok.quote.lifetimeMs)
        assertEquals(1.0 / 0.56, ok.quote.decimalOdds, 1e-12)
        assertEquals(Result.Refused("A parlay must include two or more legs."), NovigParlay.parse(400, "A parlay must include two or more legs."))
        assertEquals(Result.Refused("Invalid parlay combination."), NovigParlay.parse(400, "Invalid parlay combination.\n"))
        assertEquals(Result.NoPrice, NovigParlay.parse(404, ""))
        assertEquals(Result.Limited(7), NovigParlay.parse(429, "", "7"))
        assertTrue(NovigParlay.parse(200, "<html>") is Result.Failed)
        assertTrue(NovigParlay.parse(503, "") is Result.Failed)
    }

    @Test
    fun `a quote posts the outcome ids as JSON with no key, and the same legs are not asked twice`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"price":"0.215","expiresAt":${System.currentTimeMillis() + 30_000},"serverNow":${System.currentTimeMillis()}}"""))
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val r = NovigParlay.quote(http, listOf("b", "a"), base) as Result.Priced
        assertEquals(0.215, r.quote.price, 0.0)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals(NovigParlay.PATH, req.path)
        assertEquals("""{"outcomeIds":["b","a"]}""", req.body.readUtf8())
        assertNull("no key, no signature", req.getHeader("Authorization"))
        // the same legs in another order come from memory
        val again = NovigParlay.quote(http, listOf("a", "b"), base) as Result.Priced
        assertEquals(r.quote, again.quote)
        assertEquals(1, server.requestCount)
        // fewer than two legs is refused before any request
        assertTrue(NovigParlay.quote(http, listOf("a"), base) is Result.Refused)
        assertEquals(1, server.requestCount)
    }

    private fun leg(id: String, event: String, kind: LineKind, vararg teams: String) = Leg(id, event, kind, teams.toList())

    @Test
    fun `the documented rules are checked before asking`() {
        val spread = leg("s1", "e1", LineKind.SPREAD, "Davidson", "Rider")
        val total = leg("t1", "e1", LineKind.TOTAL, "Davidson", "Rider")
        val money = leg("m1", "e1", LineKind.MONEYLINE, "Davidson", "Rider")
        val other = leg("s2", "e2", LineKind.SPREAD, "Kent State", "Ohio")
        assertNull("a spread and a total in one game", NovigParlay.problem(listOf(spread, total)))
        assertNull("different games, no shared team", NovigParlay.problem(listOf(spread, other)))
        assertNotNull("a spread and a moneyline in one game", NovigParlay.problem(listOf(spread, money)))
        assertNotNull("two spreads in one game", NovigParlay.problem(listOf(spread, leg("s9", "e1", LineKind.SPREAD, "Davidson", "Rider"))))
        assertNotNull("two totals in one game", NovigParlay.problem(listOf(total, leg("t9", "e1", LineKind.TOTAL, "Davidson", "Rider"))))
        assertNotNull("a shared team across games", NovigParlay.problem(listOf(spread, leg("s3", "e3", LineKind.SPREAD, "Davidson", "Dayton"))))
        assertNotNull("one leg", NovigParlay.problem(listOf(spread)))
        assertNotNull("the same bet twice", NovigParlay.problem(listOf(spread, spread)))
        assertNotNull("21 legs", NovigParlay.problem((1..21).map { leg("x$it", "ev$it", LineKind.SPREAD, "Team $it A", "Team $it B") }))
        assertNull("20 legs", NovigParlay.problem((1..20).map { leg("x$it", "ev$it", LineKind.SPREAD, "Alpha$it University", "Beta$it College") }))
    }

    @Test
    fun `the slip link carries every leg and the optional stake, and stacked straights multiply`() {
        assertEquals("novigapp://events/a,b,c/novig", NovigParlay.deepLink(listOf("a", "b", "c")))
        assertEquals("novigapp://events/a,b/novig/12.5", NovigParlay.deepLink(listOf("a", "b"), 12.5))
        assertEquals("novigapp://events/a,b/novig", NovigParlay.deepLink(listOf("a", "b"), 0.0))
        assertEquals(0.51 * 0.66, NovigParlay.stackedCost(listOf(0.51, 0.66)), 1e-12)
    }
}
