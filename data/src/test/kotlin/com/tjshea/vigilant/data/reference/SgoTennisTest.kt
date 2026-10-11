package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.MarketFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SGO's REAL tennis answer (Tj's Pro key, 2026-10-11, `/events?sportID=TENNIS&oddsAvailable=true&limit=2`: a WTA and an ATP match, both under way): `points` is SETS, `games` is GAMES. */
class SgoTennisTest {
    private val page = SgoParser.page(javaClass.getResource("/sgo-real-tennis.json")!!.readText())
    private val atp = page.events.single { it.leagueId == "ATP" }
    private val wanted = SgoBooks.everyBook()
    private val ref = SgoConvert.toRef(atp, "tennis_atp", wanted, props = false)!!

    @Test fun `the match reads as two singles players`() {
        assertEquals("Alex de Minaur", atp.home); assertEquals("Rei Sakamoto", atp.away)
        assertEquals(SgoConvert.Sport.TENNIS, SgoConvert.Sport.of("ATP"))
    }

    @Test fun `the match winner is the sets moneyline, in the match period`() {
        val ml = ref.markets.filter { it.kind == LineKind.MONEYLINE }
        assertTrue(ml.isNotEmpty()); assertTrue(ml.all { it.period == 0 })
        assertTrue("pinnacle" in ml.map { it.bookKey } || ml.size > 5)
    }

    @Test fun `games spread and total stay in games, sets spread and total are their own period`() {
        val total = ref.markets.filter { it.kind == LineKind.TOTAL }
        assertTrue("games total 24.5 in the match period: ${total.map { it.period to it.line }}", total.any { it.period == 0 && it.line == 24.5 })
        assertTrue("sets total 2.5 as sets", total.any { it.period == RefBookMarket.PERIOD_SETS && it.line == 2.5 })
        assertFalse("a sets total never counts as games", total.any { it.period == 0 && it.line!! < 6.0 })
        val spread = ref.markets.filter { it.kind == LineKind.SPREAD }
        assertTrue(spread.any { it.period == 0 }); assertTrue(spread.any { it.period == RefBookMarket.PERIOD_SETS })
        assertTrue("a games spread is never a sets one", spread.filter { it.period == 0 }.all { kotlin.math.abs(it.line!!) >= 0.5 })
    }

    @Test fun `a suspended market is never priced, and no prop or 1st set moneyline is`() {
        // every games-won line in this live match was `available:false` (Pinnacle had pulled it): none becomes a team total
        assertTrue(ref.markets.none { it.kind == LineKind.TEAM_TOTAL })
        assertTrue(ref.markets.none { it.kind == LineKind.PLAYER_PROP })
        assertTrue(ref.markets.filter { it.kind == LineKind.MONEYLINE }.all { it.period == 0 })
    }

    @Test fun `the request asks only for the tennis markets the families price`() {
        val ids = SgoGamesSource.oddIds(setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD, MarketFamily.TOTAL, MarketFamily.TEAM_TOTAL), SgoConvert.Sport.TENNIS)
        assertTrue("games-all-game-ou-over" in ids && "points-home-game-ml-home" in ids && "games-home-game-sp-home" in ids && "games-home-game-ou-over" in ids)
        assertTrue(ids.none { it.startsWith("points-all-game-ou-over") && false })
        assertTrue(SgoBooks.tennisLeagueId(com.tjshea.vigilant.data.scanner.Leagues.byNovigName("ATP")!!) == "ATP")
        assertTrue(SgoBooks.tennisLeagueId(com.tjshea.vigilant.data.scanner.Leagues.byNovigName("NFL")!!) == null)
    }
}
