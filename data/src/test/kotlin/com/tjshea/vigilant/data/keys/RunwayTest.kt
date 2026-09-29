package com.tjshea.vigilant.data.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tj, 2026-09-29: "tell me which apis deplete too quickly for daily use so I can add more keys". The runway says, per keyed provider, whether
 * what's left lasts to the reset at the pace it's being used. The first cases are Tj's own Diagnostics report (Sep 29, 2:57 PM Eastern).
 */
class RunwayTest {

    private fun at(s: String) = Instant.parse(s).toEpochMilli()
    private val now = at("2026-09-29T18:57:43Z")

    private fun views(book: UsageBook, keys: Map<QuotaPolicy, List<String>>, now: Long = this.now) =
        keys.map { (p, ks) -> UsageViews.build(p, ks, book.providers[p.id], now) }

    /** Tj's report: two Odds API keys (one spent), one PinnWire, one pinnapi, one PropLine. */
    private fun tjsBook(): UsageBook {
        val month = QuotaPolicy.ODDS_API.periodStart(now)
        val day = QuotaPolicy.PINNWIRE.periodStart(now)
        return UsageBook(
            mapOf(
                "oddsapi" to ProviderUsage(
                    keys = mapOf(
                        "key-16a7" to KeyUsage(periodStart = month, used = 500, remaining = 0, limit = 500, depletedUntil = at("2026-10-01T00:00:00Z")),
                        "key-71c4" to KeyUsage(periodStart = month, used = 162, remaining = 338, limit = 500),
                    ),
                ),
                "pinnwire" to ProviderUsage(keys = mapOf("key-c6e5" to KeyUsage(periodStart = day, used = 48)), dayStart = day, callsToday = 48),
                "pinnacle" to ProviderUsage(keys = mapOf("key-wLaD" to KeyUsage(periodStart = day, used = 2)), dayStart = day, callsToday = 1),
                "propline" to ProviderUsage(keys = mapOf("key-0c2f" to KeyUsage(periodStart = day, used = 156, remaining = 844, limit = 1000)), dayStart = day, callsToday = 155),
            ),
        )
    }

    private val tjsKeys = mapOf(
        QuotaPolicy.ODDS_API to listOf("key-16a7", "key-71c4"),
        QuotaPolicy.PINNWIRE to listOf("key-c6e5"),
        QuotaPolicy.PINNAPI to listOf("key-wLaD"),
        QuotaPolicy.PROPLINE to listOf("key-0c2f"),
    )

    @Test
    fun `Tj's report reads as it is - everything on pace, The Odds API's month almost over`() {
        val lines = Runway.lines(views(tjsBook(), tjsKeys), now).associateBy { it.id }
        val odds = lines.getValue("oddsapi")
        assertEquals(RunwayLevel.OK, odds.level)
        assertTrue(odds.text, odds.text.startsWith("The Odds API: 662 of 1,000 credits used this month (2 keys), 338 left · resets Oct 1"))
        assertTrue(odds.text, odds.text.contains("at this pace about 690 by the reset: OK"))
        val wire = lines.getValue("pinnwire")
        assertEquals(RunwayLevel.OK, wire.level)
        assertTrue(wire.text, wire.text.startsWith("Pinnacle (PinnWire): 48 of 100 requests used today (1 key), 52 left · resets in 5h 2m"))
        assertTrue(wire.text, wire.text.contains("at this pace about 60 by the reset: OK"))
        assertEquals(RunwayLevel.OK, lines.getValue("propline").level)
        assertEquals(RunwayLevel.OK, lines.getValue("pinnacle").level)
        // Order follows the views handed in; the free-of-key providers have no line at all.
        assertEquals(4, lines.size)
    }

    @Test
    fun `a key on course to run out before its reset is SHORT and says when the last of it goes`() {
        val noon = at("2026-09-29T12:00:00Z")
        val day = QuotaPolicy.PINNWIRE.periodStart(noon)
        val book = UsageBook(mapOf("pinnwire" to ProviderUsage(keys = mapOf("k" to KeyUsage(periodStart = day, used = 90)))))
        val line = Runway.line(UsageViews.build(QuotaPolicy.PINNWIRE, listOf("k"), book.providers["pinnwire"], noon), noon)!!
        assertEquals(RunwayLevel.SHORT, line.level)
        // 90 in 12 h leaves 10: gone in 80 minutes, 12 hours before the reset.
        assertTrue(line.text, line.text.contains("the last of it goes in 1h 20m, before the reset: SHORT (add keys, or scan less)"))
    }

    @Test
    fun `most of the way there is a WATCH`() {
        val noon = at("2026-09-29T12:00:00Z")
        val day = QuotaPolicy.PINNWIRE.periodStart(noon)
        val book = UsageBook(mapOf("pinnwire" to ProviderUsage(keys = mapOf("k" to KeyUsage(periodStart = day, used = 44)))))
        val line = Runway.line(UsageViews.build(QuotaPolicy.PINNWIRE, listOf("k"), book.providers["pinnwire"], noon), noon)!!
        // 44 by noon is 88 by midnight: inside the allowance, but not by much.
        assertEquals(RunwayLevel.WATCH, line.level)
        assertTrue(line.text, line.text.contains("at this pace about 88 by the reset: WATCH"))
    }

    @Test
    fun `every key spent says until when and to add one`() {
        val month = QuotaPolicy.ODDS_API.periodStart(now)
        val spent = KeyUsage(periodStart = month, used = 500, remaining = 0, limit = 500, depletedUntil = at("2026-10-01T00:00:00Z"))
        val book = UsageBook(mapOf("oddsapi" to ProviderUsage(keys = mapOf("a" to spent, "b" to spent))))
        val line = Runway.line(UsageViews.build(QuotaPolicy.ODDS_API, listOf("a", "b"), book.providers["oddsapi"], now), now)!!
        assertEquals(RunwayLevel.SHORT, line.level)
        assertTrue(line.text, line.text.contains("SPENT until Oct 1: add a key"))
    }

    @Test
    fun `too early in the day or month it says so instead of projecting from nothing, and no keys means no line`() {
        val early = at("2026-09-29T01:00:00Z")
        val day = QuotaPolicy.PINNWIRE.periodStart(early)
        val book = UsageBook(mapOf("pinnwire" to ProviderUsage(keys = mapOf("k" to KeyUsage(periodStart = day, used = 30)))))
        val line = Runway.line(UsageViews.build(QuotaPolicy.PINNWIRE, listOf("k"), book.providers["pinnwire"], early), early)!!
        assertEquals(RunwayLevel.OK, line.level)
        assertTrue(line.text, line.text.contains("too early in the day to project a pace"))
        assertNull(Runway.line(UsageViews.build(QuotaPolicy.PINNWIRE, emptyList(), null, now), now))
        // Keyless providers have no allowance to run out of.
        assertNull(Runway.line(UsageViews.build(QuotaPolicy.KALSHI, emptyList(), ProviderUsage(callsToday = 700), now), now))
    }

    @Test
    fun `a round's cost is what the ledger gained between two looks, in requests and in allowance units`() {
        val before = tjsBook()
        val day = QuotaPolicy.PINNWIRE.periodStart(now)
        val after = UsageBook(
            before.providers +
                mapOf(
                    "pinnwire" to before.providers.getValue("pinnwire").let { it.copy(keys = mapOf("key-c6e5" to KeyUsage(periodStart = day, used = 52)), callsToday = 52) },
                    "propline" to before.providers.getValue("propline").let { it.copy(keys = mapOf("key-0c2f" to it.keys.getValue("key-0c2f").copy(used = 180, remaining = 820)), callsToday = 169) },
                    "kalshi" to ProviderUsage(dayStart = day, callsToday = 727),
                ),
        ).let { b -> b.copy(providers = b.providers + ("kalshi" to ProviderUsage(dayStart = day, callsToday = 727))) }
        val start = UsageBook(before.providers + ("kalshi" to ProviderUsage(dayStart = day, callsToday = 624)))
        val cost = UsageDelta.between(start, after).associateBy { it.id }
        assertEquals(ProviderCost("kalshi", 103, 103), cost.getValue("kalshi"))
        assertEquals(ProviderCost("pinnwire", 4, 4), cost.getValue("pinnwire"))
        // PropLine: 14 requests that used 24 of its day (props cost more than one).
        assertEquals(ProviderCost("propline", 14, 24), cost.getValue("propline"))
        assertNull(cost["oddsapi"])
        // Sorted by requests, the biggest spender first.
        assertEquals("kalshi", UsageDelta.between(start, after).first().id)
    }

    @Test
    fun `a round that spans midnight or a key's new period counts what the new day holds`() {
        val yesterday = at("2026-09-28T00:00:00Z")
        val today = at("2026-09-29T00:00:00Z")
        val before = UsageBook(mapOf("kalshi" to ProviderUsage(dayStart = yesterday, callsToday = 900), "pinnwire" to ProviderUsage(keys = mapOf("k" to KeyUsage(periodStart = yesterday, used = 97)), dayStart = yesterday, callsToday = 97)))
        val after = UsageBook(mapOf("kalshi" to ProviderUsage(dayStart = today, callsToday = 12), "pinnwire" to ProviderUsage(keys = mapOf("k" to KeyUsage(periodStart = today, used = 5)), dayStart = today, callsToday = 5)))
        val cost = UsageDelta.between(before, after).associateBy { it.id }
        assertEquals(12, cost.getValue("kalshi").calls)
        assertEquals(ProviderCost("pinnwire", 5, 5), cost.getValue("pinnwire"))
    }

    @Test
    fun `it says how many rounds an allowance buys`() {
        val view = UsageViews.build(QuotaPolicy.PINNWIRE, listOf("k"), null, now)
        assertEquals("a scan costs 6 → 16 a day", Runway.roundsNote(view, ProviderCost("pinnwire", 6, 6), "a scan"))
        val two = UsageViews.build(QuotaPolicy.PINNWIRE, listOf("k", "j"), null, now)
        assertEquals("a scan costs 6 → 16 a day per key, 33 with 2 keys", Runway.roundsNote(two, ProviderCost("pinnwire", 6, 6), "a scan"))
        assertNull(Runway.roundsNote(view, ProviderCost("pinnwire", 3, 0), "a scan"))
        assertNotNull(Runway.roundsNote(UsageViews.build(QuotaPolicy.ODDS_API, listOf("a"), null, now), ProviderCost("oddsapi", 3, 18), "a scan"))
        assertNull(Runway.roundsNote(UsageViews.build(QuotaPolicy.KALSHI, emptyList(), null, now), ProviderCost("kalshi", 57, 57), "a scan"))
    }
}
