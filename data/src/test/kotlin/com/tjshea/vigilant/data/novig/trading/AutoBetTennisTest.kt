package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tennis in the auto-bet and the bids (Tj, 2026-10-10): CNO has none, so a Vigilant-scan bet is judged by [AutoBet.judgeScan] and staked by [AutoBet.scanStake]; one switch flips the lot. */
class AutoBetTennisTest {

    private val rules = AutoBet.rules(ScanSettings(autoBetMinEv = 0.03, autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 2.0, autoBetMaxStake = 10.0))

    @Test fun `a scan bet over the minimum passes`() {
        assertNull(AutoBet.judgeScan(rules, 0.045, 120, BetKind.MONEYLINE))
    }

    @Test fun `under the minimum, over the sane cap, and outside the odds limits are each refused`() {
        assertNotNull(AutoBet.judgeScan(rules, 0.02, 120, BetKind.MONEYLINE))
        assertNotNull(AutoBet.judgeScan(rules, 0.30, 120, BetKind.MONEYLINE))
        val capped = AutoBet.rules(ScanSettings(autoBetMinEv = 0.03, autoBetMaxOdds = 150))
        assertNotNull(AutoBet.judgeScan(capped, 0.05, 200, null))
        assertNull(AutoBet.judgeScan(capped, 0.05, 140, null))
    }

    @Test fun `a favourite needs the extra edge`() {
        val r = AutoBet.rules(ScanSettings(autoBetMinEv = 0.03, autoBetFavouriteExtraEv = 0.02))
        assertNotNull(AutoBet.judgeScan(r, 0.04, -150, null))
        assertNull(AutoBet.judgeScan(r, 0.05, -150, null))
    }

    @Test fun `a kind outside the chosen ones is refused`() {
        val r = AutoBet.rules(ScanSettings(autoBetKinds = setOf(BetKind.SPREAD)))
        assertNotNull(AutoBet.judgeScan(r, 0.05, 110, BetKind.MONEYLINE))
        assertNull(AutoBet.judgeScan(r, 0.05, 110, BetKind.SPREAD))
    }

    @Test fun `stake is the custom amount held to the maximum and the wallet, and an empty wallet stops`() {
        val q = EvQuote(fairProbability = 0.55, price = 0.50, fee = 0.0)
        assertEquals(2.0, (AutoBet.scanStake(rules, q, 100.0, 50.0) as AutoBet.Stake.Amount).dollars, 1e-9)
        assertEquals(1.5, (AutoBet.scanStake(rules, q, 100.0, 1.5) as AutoBet.Stake.Amount).dollars, 1e-9)
        assertTrue(AutoBet.scanStake(rules, q, 100.0, 0.0) is AutoBet.Stake.WalletEmpty)
    }

    @Test fun `Kelly stake follows the quote's own edge`() {
        val kelly = AutoBet.rules(ScanSettings(autoBetStake = AutoBetStake.entries.first { it.kelly != null }, autoBetMaxStake = 1000.0))
        val q = EvQuote(0.55, 0.50, 0.0)
        val amount = (AutoBet.scanStake(kelly, q, 1000.0, 1000.0) as AutoBet.Stake.Amount).dollars
        assertEquals(AutoBet.floorCents(1000.0 * kelly.stake.kelly!! * q.kellyFraction), amount, 1e-9)
        assertTrue(AutoBet.scanStake(kelly, EvQuote(0.45, 0.50, 0.0), 1000.0, 1000.0) is AutoBet.Stake.Skip)
    }

    @Test fun `the Tennis switch flips the leagues and the auto-bet together, and a saved file gets tennis once`() {
        val off = ScanSettings(leagues = setOf("NFL")).withTennis(false)
        assertFalse(off.tennisOn); assertFalse(off.autoBetTennis)
        val on = off.withTennis(true)
        assertTrue(on.tennisOn); assertTrue(on.autoBetTennis); assertTrue("NFL" in on.leagues)
        val migrated = ScanSettings(leagues = setOf("NFL"), autoBetTennis = false, schema = 14).migrate()
        assertTrue(migrated.tennisOn); assertTrue(migrated.autoBetTennis)
        // already migrated: a deliberate off stays off
        assertFalse(ScanSettings(leagues = setOf("NFL"), autoBetTennis = false, schema = 15).migrate().tennisOn)
    }
}
