package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Tj, 2026-10-11: the auto-bet also bets Vigilant's scanned bets, only when they meet the auto-bet criteria (edge, odds, books agreeing). */
class AutoBetVigilantTest {
    private val rules = AutoBet.rules(ScanSettings(autoBetMinEv = 0.03, autoBetBooks = 3, autoBetTwoSided = 3))

    @Test
    fun `a bet with enough edge and agreeing books passes`() {
        assertNull(AutoBet.judgeVigilant(rules, 0.05, 120, null, twoSided = 4, agreeing = 4))
    }

    @Test
    fun `too little edge, too few books or a split is refused`() {
        assertNotNull(AutoBet.judgeVigilant(rules, 0.01, 120, null, 4, 4))
        assertNotNull(AutoBet.judgeVigilant(rules, 0.05, 120, null, 2, 2))
        assertNotNull(AutoBet.judgeVigilant(rules, 0.05, 120, null, 5, 2))
    }

    @Test
    fun `every book must agree when asked`() {
        val all = AutoBet.rules(ScanSettings(autoBetMinEv = 0.03, autoBetBooks = 3, autoBetTwoSided = 3, autoBetAllAgree = true))
        assertNotNull(AutoBet.judgeVigilant(all, 0.05, 120, null, 5, 4))
        assertNull(AutoBet.judgeVigilant(all, 0.05, 120, null, 5, 5))
    }
}
