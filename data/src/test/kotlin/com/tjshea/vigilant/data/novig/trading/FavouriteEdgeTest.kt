package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-07, proposal 4 of the v0.70.1 analysis ("plus money only, or a higher EV bar for favourites"): the auto-bet asks a favorite (odds shorter than even money) for
 * [ScanSettings.autoBetFavouriteExtraEv] more edge than its minimum, one point by default; the plus-money-only limit is the shortest-odds chip "+100 or longer".
 */
class FavouriteEdgeTest {
    private fun check() = CnoBooks.Check(4, 0, 0.51, 4, 100, 0.05, CnoBooks.Verdict.CONFIRMED)

    private val rules = AutoBet.rules(ScanSettings(autoBetMinEv = 0.03, autoBetFavouriteExtraEv = 0.01))

    @Test
    fun `a favorite is shorter than even money, even money and underdogs are not`() {
        assertTrue(AutoBet.isFavourite(-101))
        assertTrue(AutoBet.isFavourite(-250))
        assertFalse(AutoBet.isFavourite(-100))
        assertFalse(AutoBet.isFavourite(100))
        assertFalse(AutoBet.isFavourite(180))
        assertEquals(0.04, AutoBet.evBar(rules, -150), 1e-12)
        assertEquals(0.03, AutoBet.evBar(rules, -100), 1e-12)
        assertEquals(0.03, AutoBet.evBar(rules, 130), 1e-12)
    }

    @Test
    fun `the auto-bet wants more edge from a favorite and says so apart from the plain minimum`() {
        // 3.5% is over the 3% minimum: an underdog and even money go, a favorite doesn't.
        assertNull(AutoBet.judge(rules, 0.035, check(), 120))
        assertNull(AutoBet.judge(rules, 0.035, check(), -100))
        val why = AutoBet.judge(rules, 0.035, check(), -150)!!
        assertTrue(why, why.startsWith("it is a favorite and its edge ") && why.endsWith(" favorites need") && why.contains("3.5") && why.contains("4.0"))
        // At the bar exactly it passes; under the plain minimum it is the plain reason.
        assertNull(AutoBet.judge(rules, 0.04, check(), -150))
        assertTrue(AutoBet.judge(rules, 0.025, check(), -150)!!.endsWith(" minimum"))
        // Zero extra = the same bar for every price (what it was before).
        assertNull(AutoBet.judge(AutoBet.rules(ScanSettings(autoBetMinEv = 0.03, autoBetFavouriteExtraEv = 0.0)), 0.035, check(), -150))
    }

    @Test
    fun `one point is the default and the setting is held to a sane range`() {
        assertEquals(0.01, ScanSettings().autoBetFavouriteExtraEv, 1e-12)
        assertTrue(0.01 in ScanSettings.AUTO_BET_FAVOURITE_EV_CHOICES && 0.0 in ScanSettings.AUTO_BET_FAVOURITE_EV_CHOICES)
        assertEquals(0.0, AutoBet.rules(ScanSettings(autoBetFavouriteExtraEv = -0.5)).favouriteExtraEv, 1e-12)
        assertEquals(0.2, AutoBet.rules(ScanSettings(autoBetFavouriteExtraEv = 5.0)).favouriteExtraEv, 1e-12)
    }

    @Test
    fun `plus money only is the shortest-odds limit of +100, which skips every favorite and even money's shorter side`() {
        val plus = AutoBet.rules(ScanSettings(autoBetMinOdds = 100))
        assertTrue(AutoBet.tooShort(plus.minOdds, -110))
        assertTrue(AutoBet.tooShort(plus.minOdds, 99))
        assertFalse(AutoBet.tooShort(plus.minOdds, 100))
        assertTrue(100 in ScanSettings.AUTO_BET_MIN_ODDS_CHOICES)
    }
}
