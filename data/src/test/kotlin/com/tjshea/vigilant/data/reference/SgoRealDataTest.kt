package com.tjshea.vigilant.data.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SportsGameOdds' REAL answer (Tj's Pro key, 2026-10-09: `/v2/events?leagueID=NFL&oddsAvailable=true&includeAltLines=true&includeOpenCloseOdds=true`, one game trimmed to its moneyline, spread, total, one passing-yards prop
 * and one anytime-touchdown prop; the odds, update times and alternate lines are SGO's own, deeplinks removed). The parser and converter were written from the docs; this is what they meet in the field.
 */
class SgoRealDataTest {
    private val raw = javaClass.getResource("/sgo-real-nfl.json")!!.readText()
    private val page = SgoParser.page(raw)
    private val e = page.events.single()
    private val wanted = SgoBooks.wanted(listOf("pinnacle", "draftkings", "fanduel", "williamhill_us", "betmgm", "hardrockbet", "bet365"), extra = true)
    private val ref = SgoConvert.toRef(e, "americanfootball_nfl", wanted)!!

    @Test fun theGameReadsAsSgoSendsIt() {
        assertEquals("Jacksonville Jaguars", e.home); assertEquals("Philadelphia Eagles", e.away)
        assertEquals("JAX", e.homeAbbr)
        assertEquals(SgoParser.ms("2026-10-11T13:30:00.000Z"), e.startsMs)
        assertFalse(e.live); assertFalse(e.started)
        assertEquals(8 + 2, e.odds.size)
    }

    @Test fun pinnaclesMoneylineIsPairedWithItsOwnUpdateTime() {
        val ml = ref.markets.single { it.bookKey == "pinnacle" && it.kind == LineKind.MONEYLINE }
        assertEquals(1.0 + 100.0 / 369.0, ml.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
        assertEquals(SgoParser.ms("2026-10-09T15:33:34.762Z"), ml.lastUpdateMs)
    }

    @Test fun theMainSpreadAndEveryAvailableAlternateArePairedAtMirroredNumbers() {
        val sp = ref.markets.filter { it.bookKey == "pinnacle" && it.kind == LineKind.SPREAD && it.period == 0 }
        val lines = sp.map { it.line!! }.toSet()
        assertTrue("main -7.5 is there: $lines", -7.5 in lines)
        // the home alternates -14.5 .. -1, paired with the away side's +14.5 .. +1
        assertTrue(-14.5 in lines && -9.5 in lines && -3.5 in lines)
        // -10.5 was stale and `available:false` in SGO's answer: never a market
        assertFalse(-10.5 in lines)
        sp.forEach { m -> assertEquals(-m.quotes.single { it.side == Side.HOME }.point!!, m.quotes.single { it.side == Side.AWAY }.point!!, 0.0) }
    }

    @Test fun anUnavailableBookIsNotAMarket() {
        // Circa's total at 41.5 was `available:false`, last updated two days before
        assertTrue(ref.markets.none { it.bookKey == "circa" && it.kind == LineKind.TOTAL && it.line == 41.5 && it.period == 0 })
        assertTrue(ref.markets.any { it.bookKey == "draftkings" && it.kind == LineKind.TOTAL && it.line == 41.5 })
    }

    @Test fun exchangesDfsAndUnknownNeverPriceAFairLine() {
        val books = ref.markets.map { it.bookKey }.toSet()
        assertTrue(books.toString(), books.intersect(setOf("novig", "kalshi", "unknown", "prizepicks", "polymarket")).isEmpty())
        assertTrue("pinnacle" in books && "draftkings" in books && "fanduel" in books && "bet365" in books)
    }

    @Test fun propsAreNamedFromTheirPlayersMapAndTheTouchdownYesNoIsOverUnderHalf() {
        val pass = ref.markets.filter { it.kind == LineKind.PLAYER_PROP && it.stat == "PASSING_YARDS" }
        assertTrue(pass.isNotEmpty())
        assertEquals("Trevor Lawrence", pass.first().subject)
        assertTrue("pinnacle" in pass.map { it.bookKey })
        val td = ref.markets.filter { it.kind == LineKind.PLAYER_PROP && it.stat == "TOUCHDOWNS" }
        assertTrue(td.isNotEmpty())
        assertEquals("Bhayshul Tuten", td.first().subject)
        assertTrue(td.all { it.line == 0.5 })
    }

    @Test fun updateTimesAreReadFromEverySideAndMissingOnesAreUnknownNotZero() {
        assertTrue(ref.markets.all { it.lastUpdateMs == null || it.lastUpdateMs!! > SgoParser.ms("2026-01-01T00:00:00Z")!! })
        assertNotNull(ref.markets.firstOrNull { it.lastUpdateMs != null })
    }

    @Test fun theUsageAnswerSgoReallySendsIsRead() {
        val c = SportsGameOddsClientAccess.parseUsage(
            """{"success":true,"data":{"keyID":"x","tier":"pro","isActive":true,"rateLimits":{"per-second":{"max-requests":"unlimited","max-entities":"unlimited"},"per-minute":{"max-requests":300,"current-requests":4,"max-entities":"unlimited"},"per-hour":{"max-requests":50000,"current-requests":4,"max-entities":250000,"current-entities":12},"per-day":{"max-requests":500000,"current-requests":4,"max-entities":3000000,"current-entities":12},"per-month":{"max-requests":"unlimited","max-entities":"unlimited"}}}}""",
        )
        assertEquals("plan pro · this minute 4/300 requests · this hour 12/250000 objects · this month ?/unlimited objects", c!!.summary())
    }
}

/** Reads a `/account/usage` body the way the client does (the client\'s own read needs a server). */
private object SportsGameOddsClientAccess {
    fun parseUsage(raw: String): SgoUsage? = SgoUsage.parse(raw)
}
