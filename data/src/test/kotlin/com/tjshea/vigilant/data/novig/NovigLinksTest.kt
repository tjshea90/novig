package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tj, 2026-09-28: "Can the app automatically enter 1 dollar on every betslip inside novig when I click on a bet? If it can,
 * make an option to automatically enter 1 dollar per bet, the kelly value per bet, or an amount I can type into the
 * settings." Novig's deeplinks take a wager after the partner tag (docs.novig.com/affiliates/deeplinking).
 */
class NovigLinksTest {

    @Test
    fun `a bet slip link gets the amount after a partner tag, CNO's kept, Novig's own added when there's none`() {
        assertEquals("novigapp://events/o1/novig/1", NovigLinks.withStake("novigapp://events/o1", 1.0))
        assertEquals("novigapp://events/o1/cno/12.27", NovigLinks.withStake("novigapp://events/o1/cno", 12.27))
        assertEquals("novigapp://events/o1/cno/5", NovigLinks.withStake("novigapp://events/o1/cno/25", 5.0)) // an amount already there is replaced
        assertEquals("https://novig.com/events/o1,o2/novig/2.5?referralCode=X", NovigLinks.withStake("https://novig.com/events/o1,o2?referralCode=X", 2.5))
    }

    @Test
    fun `no amount, a game link, or another book's link stay as they were`() {
        assertEquals("novigapp://events/o1", NovigLinks.withStake("novigapp://events/o1", null))
        assertEquals("novigapp://event-markets/e1", NovigLinks.withStake("novigapp://event-markets/e1", 1.0))
        assertEquals("https://sports.nj.betmgm.com/en/sports/events/1", NovigLinks.withStake("https://sports.nj.betmgm.com/en/sports/events/1", 1.0))
        assertNull(NovigLinks.withStake(null, 1.0))
    }

    @Test
    fun `the setting picks the amount - off, one dollar, the Kelly stake (never under a dollar), or a typed amount`() {
        assertNull(NovigLinks.stake(SlipStake.OFF, 5.0, 12.0))
        assertEquals(1.0, NovigLinks.stake(SlipStake.ONE_DOLLAR, 5.0, 12.0)!!, 0.0)
        assertEquals(12.27, NovigLinks.stake(SlipStake.KELLY, 5.0, 12.2749)!!, 0.0)
        assertEquals(1.0, NovigLinks.stake(SlipStake.KELLY, 5.0, 0.4)!!, 0.0)
        assertNull(NovigLinks.stake(SlipStake.KELLY, 5.0, null)) // no Kelly stake (no edge): Novig's default
        assertEquals(7.5, NovigLinks.stake(SlipStake.CUSTOM, 7.5, 12.0)!!, 0.0)
        assertEquals("12.2", NovigLinks.amountText(12.2))
        assertEquals("10", NovigLinks.amountText(10.0))
        // A new install opens Novig's slip as before; the choice is Tj's.
        assertNull(ScanSettings().slipStakeFor(12.0))
        assertEquals(1.0, ScanSettings(slipStake = SlipStake.ONE_DOLLAR).slipStakeFor(null)!!, 0.0)
    }
}
