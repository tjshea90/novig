package com.tjshea.vigilant.data.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SgoConvertTest {
    private val raw = javaClass.getResource("/sgo-events-sample.json")!!.readText()
    private val page = SgoParser.page(raw)
    private val wanted = SgoBooks.wanted(listOf("pinnacle", "draftkings", "fanduel", "williamhill_us"), extra = true)
    private val ref = SgoConvert.toRef(page.events.single(), "americanfootball_nfl", wanted)!!

    @Test fun pageReadsEventCursorAndNoticeAndSkipsTheBrokenEvent() {
        assertEquals(1, page.events.size)
        assertEquals("n.123.abc", page.nextCursor)
        assertEquals("Response is missing 1 events", page.notice)
        val e = page.events.single()
        assertEquals("Kansas City Chiefs", e.home); assertEquals("Las Vegas Raiders", e.away)
        assertEquals(7, e.homeScore); assertEquals(3, e.awayScore)
        assertFalse(e.live)
        assertNotNull(e.startsMs)
    }

    @Test fun americanAndDecimalAndNumbers() {
        assertEquals(-110.0, SgoParser.american("-110")!!, 0.0)
        assertEquals(130.0, SgoParser.american("+130")!!, 0.0)
        assertNull(SgoParser.american("garbage")); assertNull(SgoParser.american("50")); assertNull(SgoParser.american(null))
        assertEquals(2.3, SgoParser.decimal(130.0)!!, 1e-9)
        assertEquals(1.0 + 100.0 / 150.0, SgoParser.decimal(-150.0)!!, 1e-9)
        assertEquals(-3.5, SgoParser.number("-3.5")!!, 0.0); assertEquals(1.5, SgoParser.number("+1.5")!!, 0.0)
        assertEquals(1_700_000_000_000L, SgoParser.ms("1700000000000")); assertEquals(1_700_000_000_000L, SgoParser.ms("1700000000"))
        assertNotNull(SgoParser.ms("2026-10-09T18:00:00.000Z")); assertNull(SgoParser.ms("soon"))
    }

    @Test fun excludedBooksNeverPriceAFairLine() {
        val books = ref.markets.map { it.bookKey }.toSet()
        assertFalse("novig" in books); assertFalse("unknown" in books)
        assertTrue("pinnacle" in books && "draftkings" in books && "circa" in books)
    }

    @Test fun moneylinePairsBothSidesWithTheOlderStamp() {
        val ml = ref.markets.filter { it.kind == LineKind.MONEYLINE && it.period == 0 && it.bookKey == "pinnacle" }.single()
        assertEquals(Side.HOME, ml.quotes[0].side)
        assertEquals(1.0 + 100.0 / 150.0, ml.quotes[0].decimalOdds, 1e-9)
        assertEquals(2.3, ml.quotes[1].decimalOdds, 1e-9)
        assertEquals(SgoParser.ms("2026-10-09T18:00:00.000Z"), ml.lastUpdateMs)
    }

    @Test fun firstHalfMoneylineIsNotPriced() {
        assertTrue(ref.markets.none { it.kind == LineKind.MONEYLINE && it.period == 1 })
    }

    @Test fun spreadMainAndAlternateLinesAreSeparateMarketsAtMirroredNumbers() {
        val sp = ref.markets.filter { it.kind == LineKind.SPREAD && it.period == 0 && it.bookKey == "pinnacle" }.sortedBy { it.line }
        assertEquals(listOf(-6.5, -3.5, 0.5), sp.map { it.line })
        val main = sp.single { it.line == -3.5 }
        assertEquals(3.5, main.quotes.single { it.side == Side.AWAY }.point!!, 0.0)
        // the +0.5 alt: home +0.5 at -300, away -0.5 at +240 (the older stamp wins)
        val alt = sp.single { it.line == 0.5 }
        assertEquals(SgoParser.ms("2026-10-09T17:59:00.000Z"), alt.lastUpdateMs)
    }

    @Test fun anUnavailableOrMismatchedSideIsDropped() {
        // fanduel -3 home is unavailable, away +3: no pair
        assertTrue(ref.markets.none { it.bookKey == "fanduel" && it.kind == LineKind.SPREAD })
    }

    @Test fun totalsTeamTotalsAndFirstHalf() {
        val t = ref.markets.filter { it.kind == LineKind.TOTAL && it.period == 0 }
        assertEquals(setOf("pinnacle", "draftkings"), t.map { it.bookKey }.toSet())
        assertEquals(47.5, t.first().line!!, 0.0)
        assertTrue(t.first { it.bookKey == "draftkings" }.lastUpdateMs!! < t.first { it.bookKey == "pinnacle" }.lastUpdateMs!!)
        val team = ref.markets.single { it.kind == LineKind.TEAM_TOTAL }
        assertEquals(RefBookMarket.HOME, team.subject)
        assertEquals(24.5, team.line!!, 0.0)
        val h1 = ref.markets.single { it.kind == LineKind.SPREAD && it.period == 1 }
        assertEquals(-1.5, h1.line!!, 0.0)
    }

    @Test fun propsNameThePlayerAndMapTheStat() {
        val props = ref.markets.filter { it.kind == LineKind.PLAYER_PROP }
        val pass = props.filter { it.stat == "PASSING_YARDS" }
        assertEquals(2, pass.size)
        assertEquals("Patrick Mahomes", pass.first().subject)
        assertEquals(setOf(275.5, 274.5), pass.map { it.line!! }.toSet())
        // Yes/No anytime touchdown is Over/Under 0.5; the name comes from first + last
        val td = props.single { it.stat == "TOUCHDOWNS" }
        assertEquals("Travis Kelce", td.subject)
        assertEquals(0.5, td.line!!, 0.0)
        assertEquals(Side.OVER, td.quotes[0].side)
    }

    @Test fun withoutExtraBooksCircaIsNotAsked() {
        val w = SgoBooks.wanted(listOf("pinnacle"), extra = false)
        val r = SgoConvert.toRef(page.events.single(), "americanfootball_nfl", w)!!
        assertEquals(setOf("pinnacle"), r.markets.map { it.bookKey }.toSet())
    }

    @Test fun bookKeysTranslateBothWays() {
        assertEquals("williamhill_us", SgoBooks.appKey("williamhill")); assertEquals("williamhill", SgoBooks.sgoId("williamhill_us"))
        assertEquals("betonlineag", SgoBooks.appKey("betonline"))
        assertEquals("Circa Sports", SgoBooks.title("circa"))
    }

    @Test fun oddIdsAskOnlyForPickedFamilies() {
        val ids = SgoGamesSource.oddIds(setOf(com.tjshea.vigilant.data.scanner.MarketFamily.TOTAL), SgoConvert.Sport.FOOTBALL)
        assertEquals(listOf("points-all-game-ou-over", "points-all-game-ou-under"), ids)
        val props = SgoPropsSource.oddIds(SgoConvert.Sport.FOOTBALL)
        assertTrue("passing_yards-PLAYER_ID-game-ou-over" in props && "touchdowns-PLAYER_ID-game-yn-yes" in props)
    }

    @Test fun playerNameFromIdFallback() {
        assertEquals("Patrick Mahomes", SgoProps.nameFromId("PATRICK_MAHOMES_1_NFL"))
        assertNull(SgoProps.nameFromId("X_1_NFL"))
    }
}
