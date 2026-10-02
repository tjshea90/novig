package com.tjshea.vigilant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Bet sheet's amount (Tj, 2026-09-30: "I can type in a custom account for any bet manually. And if I have less than one dollar in the
 * wallet, it automatically enters whatever is left in the wallet as the bet amount").
 */
class BetAmountTest {

    @Test
    fun `a typed amount is dollars and cents up to the per-bet limit`() {
        assertEquals(3.75, BetAmount.parse("3.75", max = 10.0)!!, 0.0)
        assertEquals(0.5, BetAmount.parse("$.50", max = 10.0)!!, 0.0)
        assertEquals(10.0, BetAmount.parse("10", max = 10.0)!!, 0.0)
        assertNull(BetAmount.parse("10.01", max = 10.0))
        assertNull(BetAmount.parse("0", max = 10.0))
        assertNull(BetAmount.parse("abc", max = 10.0))
        assertNull(BetAmount.parse("1.234", max = 10.0))
    }

    @Test
    fun `what's wrong with an amount is said, and nothing while it's blank or fine`() {
        assertNull(BetAmount.problem("", max = 10.0))
        assertNull(BetAmount.problem("4", max = 10.0))
        assertEquals("Over your $10.00 limit per bet (Settings › Betting & Novig account)", BetAmount.problem("25", max = 10.0))
        assertEquals("Dollars and cents only (two decimal places)", BetAmount.problem("1.234", max = 10.0))
        assertEquals("More than \$0, please", BetAmount.problem("0", max = 10.0))
    }

    @Test
    fun `a sheet opens at the set amount, or at what's left in the wallet when that's less`() {
        assertEquals(5.0, BetAmount.starting(5.0, max = 10.0, balance = 25.0), 0.0)
        assertEquals(5.0, BetAmount.starting(5.0, max = 10.0, balance = null), 0.0)
        // Under a dollar: all of it, rounded down to the cent (never more than the wallet holds).
        assertEquals(0.63, BetAmount.starting(1.0, max = 10.0, balance = 0.638), 0.0)
        assertEquals(3.0, BetAmount.starting(5.0, max = 10.0, balance = 3.0), 0.0)
        // An empty wallet keeps the set amount (the sheet says to add money).
        assertEquals(1.0, BetAmount.starting(1.0, max = 10.0, balance = 0.0), 0.0)
        assertEquals(1.0, BetAmount.starting(1.0, max = 10.0, balance = 0.004), 0.0)
        // The set amount is held to the limit first.
        assertEquals(10.0, BetAmount.starting(20.0, max = 10.0, balance = 50.0), 0.0)
    }

    /** Tj, 2026-10-01: "I set the settings to put the Kelly value in my bet slips within vigilant automatically, but it is still entering only $1". */
    @Test
    fun `with Kelly chosen the sheet starts at the bet's own Kelly stake, to the cent, within the per-bet limit`() {
        val s = com.tjshea.vigilant.data.scanner.ScanSettings(
            bankroll = 1_000.0, kellyMultiplier = 0.25, slipStake = com.tjshea.vigilant.data.novig.SlipStake.KELLY, apiBetStake = 1.0, apiMaxStake = 10.0,
        )
        val (amount, note) = BetAmount.base(s, kelly = 4.3719)
        assertEquals(4.37, amount, 0.0)
        assertEquals("¼ Kelly of your $1,000.00 bankroll at this bet's odds", note)
        // Under a dollar stays under a dollar.
        assertEquals(0.43, BetAmount.base(s, 0.4299).first, 0.0)
        // Over the limit: held to it, and it says so.
        val (held, why) = BetAmount.base(s, 25.0)
        assertEquals(10.0, held, 0.0)
        assertEquals("Kelly says $25.00: held to your $10.00 limit per bet", why)
        // No edge at this price: Settings' amount, said.
        assertEquals(1.0 to "No Kelly stake for this bet (no edge at this price): Settings' amount instead", BetAmount.base(s, null))
        // "My amount" for bet slips: that amount. "$1": $1 in the Bet sheet too, whatever its own amount says (v0.46.0: it used to start the sheet at
        // its own amount while the slip said $1). Off ("I type it"): the Bet sheet's own amount, no note.
        assertEquals(7.5, BetAmount.base(s.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.CUSTOM, slipCustomStake = 7.5), 4.37).first, 0.0)
        assertEquals(1.0 to null, BetAmount.base(s.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR, apiBetStake = 5.0), 4.37))
        assertEquals(2.0 to null, BetAmount.base(s.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.OFF, apiBetStake = 2.0), 4.37))
    }
}
