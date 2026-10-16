package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.scanner.ApiChain
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SourceScore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiScorecardTest {
    private val now = 1_792_092_000_000L   // mid-month
    private val monthStart = QuotaPolicy.PARLAY.periodStart(now)
    private val nextMonth = java.time.Instant.ofEpochMilli(monthStart).atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant().toEpochMilli()

    @After fun clean() { ApiScorecard.reset() }

    private fun parlay(starterLeft: Int, free: Int = 0) = UsageBook(mapOf("parlay" to ProviderUsage(keys = buildMap {
        put("starter", KeyUsage(periodStart = monthStart, used = 20_000 - starterLeft, remaining = starterLeft, limit = 20_000, resetAtMs = nextMonth))
        repeat(free) { put("free$it", KeyUsage(periodStart = monthStart, used = 950, remaining = 50, limit = 1000, resetAtMs = nextMonth)) }
    })))

    @Test
    fun `a paid plan is the one with a key allowance above the free tier, flat-rate subscriptions always`() {
        val paid = ApiScorecard.paidProviders(parlay(10_000, free = 3)) { true }
        assertTrue("parlay" in paid); assertTrue("sgo" in paid); assertFalse("odds_api" in paid)
        val freeOnly = UsageBook(mapOf("parlay" to ProviderUsage(keys = mapOf("a" to KeyUsage(limit = 1000, remaining = 100)))))
        assertFalse("parlay" in ApiScorecard.paidProviders(freeOnly) { true })
        assertFalse("sgo" in ApiScorecard.paidProviders(UsageBook()) { id -> id != "sgo" })
    }

    @Test
    fun `a paid monthly plan ahead of the calendar has a surplus and one on pace does not`() {
        // nearly everything left with about half the month gone -> its credits would expire unused
        assertTrue("parlay" in ApiScorecard.surplus(parlay(18_000), setOf("parlay"), now))
        // 20% left with half the month to go -> no surplus
        assertFalse("parlay" in ApiScorecard.surplus(parlay(4_000), setOf("parlay"), now))
        // free keys' leftovers do not count: only the paid key is judged
        assertFalse("parlay" in ApiScorecard.surplus(parlay(4_000, free = 8), setOf("parlay"), now))
    }

    @Test
    fun `a surplus provider is asked as a second opinion for a league now and then, others are not`() {
        ApiChain.reset()
        ApiChain.surplusProviders = setOf("parlay")
        assertEquals(setOf("parlay"), ApiChain.surplusProviders)
        ApiChain.reset()
        assertTrue(ApiChain.surplusProviders.isEmpty())
    }

    @Test
    fun `a paid feed wins a tie on freshness and loses to a fresher free one`() {
        fun m(id: String, p90: Long) = id to SourceScore.Measure(id, 500, p90, 0.5, 100, now)
        val prior = listOf("a_free", "b_paid")
        val tie = SourceScore.rank(prior, mapOf(m("a_free", 30_000), m("b_paid", 30_000)), paid = setOf("b_paid"))
        assertEquals(listOf("b_paid", "a_free"), tie)
        val fresher = SourceScore.rank(prior, mapOf(m("a_free", 10_000), m("b_paid", 200_000)), paid = setOf("b_paid"))
        assertEquals(listOf("a_free", "b_paid"), fresher)
    }

    @Test
    fun `the advice says a surplus is being used and that free keys are nearly spent, with numbers`() {
        val card = ApiScorecard.Card("parlay", "ParlayAPI", true, "active", surplus = true, leftFraction = 0.7, timeFraction = 0.48)
        val a = ApiAdvisor.manage(listOf(card), UsageBook(), { emptyList() }, ScanSettings(), now)
        assertTrue(a.single().body.contains("70%"))
        val spent = UsageBook(mapOf(QuotaPolicy.ODDS_API.id to ProviderUsage(keys = (0 until 4).associate { "k$it" to KeyUsage(limit = 500, remaining = 20) })))
        val free = ApiScorecard.Card("odds_api", "The Odds API", false, "active", answeredPct = 90)
        val b = ApiAdvisor.manage(listOf(free), spent, { emptyList() }, ScanSettings(useOddsApi = true), now)
        assertTrue(b.any { it.id == "keys:odds_api" && it.body.contains("90%") })
    }
}
