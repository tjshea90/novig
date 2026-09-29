package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.NovigLinks
import com.tjshea.vigilant.data.novig.SlipStake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.Odds

/**
 * The Tracker's Replace button (Tj, 2026-09-29: "add a button next to each one to replace the bet. This
 * button will open novig with that exact bet in the betslip and any dollar amount preset in the options"):
 * the bet's Novig bet-slip link and the dollars Settings' bet-slip choice ($1, Kelly, my amount, off) fills in.
 */
object BetReplace {

    /** Novig's bet slip on the bet's exact outcome; null while the outcome isn't known (a CNO bet whose link hasn't been found). */
    fun link(b: TrackedBet, settings: ScanSettings): String? =
        b.outcomeId.takeIf { it.isNotBlank() }?.let { NovigLinks.withStake("novigapp://events/$it", stake(b, settings)) }

    /** [link] for an outcome found later (CNO's or Novig's catalog): the same stake. */
    fun withStake(link: String?, b: TrackedBet, settings: ScanSettings): String? = NovigLinks.withStake(link, stake(b, settings))

    /** The dollars the slip opens with: Settings' choice; Kelly is worked from the fair price now against the price now. Null = none. */
    fun stake(b: TrackedBet, settings: ScanSettings): Double? =
        settings.slipStakeFor(if (settings.slipStake == SlipStake.KELLY) kelly(b, settings) else null)

    /** Kelly stake for the bet as it stands: fair now (else when bet) against Novig's price now (else the price bet at), fee kept. */
    fun kelly(b: TrackedBet, settings: ScanSettings): Double? {
        val fair = b.nowFair ?: b.fairAtBet ?: return null
        val price = b.nowAmerican?.let { 1.0 / Odds.americanToDecimal(it) } ?: b.price
        val fee = (b.cost - b.price).coerceAtLeast(0.0)
        return EvMath.suggestedStake(EvQuote(fair, price, fee), settings.bankroll, settings.kellyMultiplier, null).takeIf { it > 0 }
    }
}
