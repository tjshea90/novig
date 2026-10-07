package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Odds

/**
 * The rules of an auto-bet in Pinnacle only (Tj, 2026-10-05: "auto bet … only comparing current novig odds on any market and any sport to the current pinnacle devigged
 * odds for the same bet … make sure the Pinnacle odds are as current as possible"; RESEARCH.md §88.5). Pure: what to bet, why not, how much.
 *
 * The bet is a Novig outcome whose price, taken now, is under Pinnacle's devigged price for the same outcome ([ScanSettings.effective]: the worst of the four devigs of
 * Pinnacle's two-sided price, no other book). What the CrazyNinjaOdds auto-bet asks of a bet's books (how many agree, two-sided counts) is meaningless with one book; what
 * replaces it is the price's age: Pinnacle's quote must have been read within [ScanSettings.pinnacleMaxAgeSeconds] of the order, or it is read again first.
 */
object PinnacleBet {
    /** No order is sent for a game starting sooner than this (the CNO auto-bet's own limit). */
    const val MIN_LEAD_MS = 60_000L

    /** The most bets of one scan's list a pass looks at (and so re-reads Pinnacle for): the best edges first. */
    const val TOP = 12

    /** The shortest and longest Pinnacle quote age Settings can ask for, in seconds (the app never bets on a price over [com.tjshea.vigilant.data.scanner.Freshness.MAX_QUOTE_AGE_MS]). */
    const val MIN_AGE_SECONDS = 10
    const val MAX_AGE_SECONDS = 300

    /** A scan that just ended with [result] is followed by a pass: Pinnacle only and auto-bet are on (not paused, not stopped, auto-scan running) and the result is a finished one. */
    fun passDue(s: ScanSettings, result: ScanResult?): Boolean = s.pinnacleOnly && s.autoBetsNow && result != null && !result.partial

    /** Pinnacle's quote may be this old (ms) at the order. */
    fun maxAgeMs(s: ScanSettings): Long = s.pinnacleMaxAgeSeconds.coerceIn(MIN_AGE_SECONDS, MAX_AGE_SECONDS) * 1_000L

    /** A quote this old is read again before a bet on it is judged (a third of the limit, at least [MIN_AGE_SECONDS]): near-new ones are not worth a request. */
    fun refreshAfterMs(s: ScanSettings): Long = maxOf(MIN_AGE_SECONDS * 1_000L, maxAgeMs(s) / 3)

    /** How old Pinnacle's quote behind [o] is at [now] (the oldest of the lines in its fair); null when the scan didn't say. */
    fun ageMs(o: Opportunity, now: Long): Long? = o.fairAsOfMs?.let { (now - it).coerceAtLeast(0L) }

    /** [o]'s fair line is Pinnacle's and nothing else's. */
    fun pinnacleAlone(o: Opportunity): Boolean =
        o.fair?.booksUsed?.let { used -> used.isNotEmpty() && used.all { it.equals("pinnacle", ignoreCase = true) } } == true

    /** The scan's priced outcomes the passes look at, best edge first: priced at Novig and against a fair, in a picked league and market family. */
    fun candidates(result: ScanResult?, settings: ScanSettings): List<Opportunity> =
        result?.opportunities.orEmpty()
            .filter { o ->
                o.quote != null && o.fairProbability != null && o.league.novigName in settings.leagues &&
                    MarketFamily.entries.any { it in settings.families && o.market.marketType in it.novigTypes }
            }
            .sortedByDescending { it.evPercent ?: Double.NEGATIVE_INFINITY }

    /**
     * Why [o] isn't bet at [now], or null when it passes. The words carry no numbers (the report counts bets by reason). Order matters only for which reason is named first:
     * what cannot be fixed by waiting (not Pinnacle's price, not pregame) comes before what a re-read cures (an old quote).
     */
    fun judge(rules: AutoBet.Rules, o: Opportunity, now: Long, maxAgeMs: Long): String? {
        val q = o.quote ?: return "nobody is selling it at Novig"
        if (o.fairProbability == null) return "Pinnacle has no price for it"
        if (!pinnacleAlone(o)) return "its fair line isn't Pinnacle's alone"
        if (o.isLive || o.event.startsTs - now < MIN_LEAD_MS) return "not pregame (live betting isn't available)"
        val kind = BetKind.of(o.marketLabel, o.selection)
        if (kind !in rules.kinds) return "${kind.label.lowercase()} aren't among the kinds of bet you auto-bet"
        val ev = q.evPercent
        if (ev < rules.minEv - 1e-9) return "its edge against Pinnacle is under your minimum"
        if (ev > AutoBet.MAX_SANE_EV) return "its edge against Pinnacle is over ${(AutoBet.MAX_SANE_EV * 100).toInt()}%, which is usually a mismatched line (place it by hand if you trust it)"
        val american = Odds.probabilityToAmerican(q.cost.coerceIn(0.001, 0.999))
        if (ev < AutoBet.evBar(rules, american) - 1e-9) return "it is a favorite and its edge against Pinnacle is under what favorites need"
        if (AutoBet.tooLong(rules.maxOdds, american)) return "its odds are longer than your limit"
        if (AutoBet.tooShort(rules.minOdds, american)) return "its odds are shorter than your limit"
        if (o.priceIsOld(now)) return "Novig's price for it was read too long ago"
        val age = ageMs(o, now) ?: return "Pinnacle's price has no time on it"
        if (age > maxAgeMs) return "Pinnacle's price is older than your limit"
        return null
    }

    /** How much to stake on [o]: the rule's amount (Kelly on Pinnacle's own fair and Novig's price now), held to the per-bet maximum, what Novig has for sale at +EV and the wallet. */
    fun stake(rules: AutoBet.Rules, o: Opportunity, bankroll: Double, balance: Double): AutoBet.Stake {
        if (balance < AutoBet.MIN_STAKE - 1e-9) return AutoBet.Stake.WalletEmpty
        val q = o.quote ?: return AutoBet.Stake.Skip("nobody is selling it at Novig")
        val wanted = when (rules.stake) {
            AutoBetStake.ONE_DOLLAR -> 1.0
            AutoBetStake.CUSTOM -> rules.customStake
            else -> {
                val fraction = rules.stake.kelly ?: return AutoBet.Stake.Skip("no Kelly fraction")
                if (!(bankroll > 0.0)) return AutoBet.Stake.Skip("it has no Kelly stake (no bankroll is set)")
                EvMath.suggestedStake(q, bankroll, fraction, o.depth?.dollarCost).takeIf { it > 0.0 }
                    ?: return AutoBet.Stake.Skip("it has no Kelly stake (no edge at this price, or its fair odds are missing)")
            }
        }
        return AutoBet.cap(rules, wanted, balance)
    }
}
