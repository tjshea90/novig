package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

/**
 * The auto-bet's rules (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all"), pure: whether a CrazyNinjaOdds bet
 * passes the criteria Tj picked, and how many dollars it stakes. Everything money-moving that is NOT here lives in [ApiBetPlanner] and
 * [ApiBetPlacer], which every API bet goes through, a tap or not: pregame only, the edge still there at the real book price, fresh fair odds,
 * the per-day limit, one order at a time at a price ceiling.
 *
 * Each answer is a reason in words (or null / an amount) so the Settings card and Diagnostics can say why a bet wasn't placed.
 */
object AutoBet {

    /** The least an auto-bet stakes: a Kelly stake or a wallet remainder under this is a bet not worth placing, never rounded up. */
    const val MIN_STAKE = 1.0

    /** The edge floor: a typed minimum under this is read as this (a 0.1% "edge" is noise, and 0 would bet everything). */
    const val MIN_EV_FLOOR = 0.005

    /**
     * How far (in probability) Novig's price in the order book may be from the price CNO's bet was judged at. Both are Novig's own book, read
     * seconds apart, so a bigger gap means the bet found isn't the bet shown (the wrong side or line) or the market just moved: neither is bet.
     */
    const val PRICE_TOLERANCE = 0.03

    /**
     * A bet this good is almost always a stale line, a mismatched bet or bad data, not an edge (CNO's best real ones are a few percent; the
     * Tracker leaves anything over [com.tjshea.vigilant.data.tracker.BetTracker.OUTLIER_EV] out of its stats): unattended, it isn't bet, and
     * the report says so. Tj can still place it by hand.
     */
    const val MAX_SANE_EV = 0.15

    /** The shortest a longest-odds limit can be: +100 is even money; under it would mean "favorites only", which isn't what the option is for. */
    const val MIN_MAX_ODDS = 100

    /** At most this many bets per background cycle (the best edges first); the rest wait for the next. */
    const val MAX_PER_CYCLE = 5

    /** A bet that was refused (the price moved, the edge went) isn't tried again for this long. */
    const val COOLDOWN_MS = 2 * 60_000L

    data class Rules(
        val minBooks: Int,
        val minEv: Double,
        val twoSided: Int,
        val stake: AutoBetStake,
        val customStake: Double,
        val maxStake: Double,
        /** The longest American odds to bet; 0 = no limit. */
        val maxOdds: Int = 0,
    )

    fun rules(s: ScanSettings) = Rules(
        minBooks = s.autoBetBooks.coerceIn(2, 5),
        minEv = s.autoBetMinEv.coerceAtLeast(MIN_EV_FLOOR),
        twoSided = s.autoBetTwoSided.coerceIn(1, 3),
        stake = s.autoBetStake,
        customStake = s.autoBetCustomStake.coerceAtLeast(0.0),
        maxStake = s.autoBetMaxStake.coerceAtLeast(0.0),
        maxOdds = s.autoBetMaxOdds.let { if (it <= 0) 0 else it.coerceAtLeast(MIN_MAX_ODDS) },
    )

    /** Whether [american] odds are longer than the [maxOdds] limit (0 = no limit). A favorite's negative odds never are. */
    fun tooLong(maxOdds: Int, american: Int): Boolean = maxOdds > 0 && american >= maxOdds

    /**
     * Why a bet doesn't pass Tj's criteria, or null when it does. [shownEv]: the EV the CNO card shows (CNO's fair odds against Novig's price
     * now). [check]: what the books on the bet's game page say ([CnoBooks.check], judged at that same price). [american]: that price as
     * American odds, for the longest-odds limit.
     */
    fun judge(rules: Rules, shownEv: Double, check: CnoBooks.Check, american: Int): String? {
        if (shownEv < rules.minEv - 1e-9) return "its edge ${percent(shownEv)} is under your ${percent(rules.minEv)} minimum"
        if (shownEv > MAX_SANE_EV) return "its edge ${percent(shownEv)} is over ${percent(MAX_SANE_EV)}, which is usually a stale or mismatched price (place it by hand if you trust it)"
        // (No odds in the words: the report counts bets by reason, and each price would be a reason of its own.)
        if (tooLong(rules.maxOdds, american)) return "its odds are longer than your ${Odds.formatAmerican(rules.maxOdds)} limit"
        if (check.twoSided < rules.twoSided) return "${books(check.twoSided)} price${if (check.twoSided == 1) "s" else ""} both sides (you need ${rules.twoSided})"
        if (check.agreeing < rules.minBooks) return "${books(check.agreeing)} say${if (check.agreeing == 1) "s" else ""} +EV on their own (you need ${rules.minBooks})"
        val ev = check.ev
        if (ev == null || ev <= 0.0) return "the books' own fair line says ${ev?.let(::percent) ?: "nothing"}, not +EV"
        return null
    }

    /** How much to stake on one bet. */
    sealed interface Stake {
        data class Amount(val dollars: Double) : Stake

        /** Not this bet: [reason] in words. */
        data class Skip(val reason: String) : Stake

        /** The wallet can't fund a [MIN_STAKE] bet: no bet at all, and none after it either. */
        object WalletEmpty : Stake
    }

    /**
     * The stake for [row] (judged at Novig's price now): the rule's amount, held to Tj's per-bet maximum and to what's left in the wallet
     * ([balance]), floored to the cent, never under [MIN_STAKE]. A Kelly stake is [bankroll] × the fraction × full Kelly for this bet's own
     * price and fair chance (`(fair − price) / (1 − price)`, no fee pregame), held to what Novig has for sale at +EV, so it changes with the
     * odds of every bet.
     */
    fun stake(rules: Rules, row: CnoRow, bankroll: Double, balance: Double): Stake {
        if (balance < MIN_STAKE - 1e-9) return Stake.WalletEmpty
        val wanted = when (rules.stake) {
            AutoBetStake.ONE_DOLLAR -> 1.0
            AutoBetStake.CUSTOM -> rules.customStake
            else -> kellyStake(row, bankroll, rules.stake.kelly ?: return Stake.Skip("no Kelly fraction")) ?: return Stake.Skip("it has no Kelly stake (no edge at this price, or its fair odds are missing, or no bankroll is set)")
        }
        if (!(wanted > 0.0)) return Stake.Skip("the amount to stake is $0")
        val capped = floorCents(minOf(wanted, rules.maxStake, balance))
        if (capped < MIN_STAKE - 1e-9) {
            return Stake.Skip(
                when {
                    rules.maxStake < MIN_STAKE -> "your maximum per bet is under ${money(MIN_STAKE)}"
                    wanted < MIN_STAKE -> "its ${rules.stake.label} stake is ${money(wanted)}, under the ${money(MIN_STAKE)} minimum"
                    else -> "${money(capped)} is under the ${money(MIN_STAKE)} minimum"
                },
            )
        }
        return Stake.Amount(capped)
    }

    /** The Kelly stake in dollars at [fraction] of full Kelly; null when there's no edge, no fair probability or no bankroll. */
    fun kellyStake(row: CnoRow, bankroll: Double, fraction: Double): Double? {
        if (!(bankroll > 0.0)) return null
        val fair = CnoChecks.fairProbability(row) ?: return null
        val price = 1.0 / Odds.americanToDecimal(row.odds)
        val stake = EvMath.suggestedStake(EvQuote(fair, price, 0.0), bankroll, fraction, row.available)
        return stake.takeIf { it > 0.0 }
    }

    /** Whether the book's best price is the price the bet was judged at, to [PRICE_TOLERANCE]. */
    fun priceMatches(expectedPrice: Double, bookPrice: Double): Boolean = kotlin.math.abs(expectedPrice - bookPrice) <= PRICE_TOLERANCE + 1e-9

    /** [row]'s price at Novig as a probability (what a contract costs, pregame). */
    fun priceOf(row: CnoRow): Double = 1.0 / Odds.americanToDecimal(row.odds)

    fun floorCents(v: Double): Double = Math.floor(v * 100.0 + 1e-9) / 100.0

    private fun books(n: Int) = "$n book${if (n == 1) "" else "s"}"
    private fun percent(v: Double) = String.format(Locale.US, "%+.2f%%", v * 100)
    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)
}
