package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
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

    /**
     * The least an auto-bet stakes: one cent (Tj, 2026-10-01: "I don't want a $1 minimum bet … It can bet as low as 1 cent … usually it will be a
     * Kelly number and often under $1"). A stake or a wallet remainder under a cent is skipped, never rounded up. Novig's own minimum is one
     * contract (a winning contract pays 1¢, so a cent buys at least one at any price: docs.novig.com `PlaceOrder.qty`, minimum 1); it also lists an
     * `ORDER_TOO_SMALL` refusal without publishing its threshold, which [com.tjshea.vigilant.data.novig.trading.ApiBetPlacer] reports as a
     * too-small refusal and `AutoBettor` learns from.
     */
    const val MIN_STAKE = 0.01

    /**
     * Below this many dollars in the Vigilant wallet the whole app goes to sleep (Tj, 2026-10-11: "When the vigilant wallet gets below 20 cents, automatically stop all scanning, betting, and bidding
     * ... There is no reason to continue usage with little money left"): [com.tjshea.vigilant.app.AutoBettor.walletRanOut]. It is re-armed once the wallet is back at or above it.
     */
    const val SLEEP_BELOW = 0.20

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

    /** The most books a typed "books agreeing" or "books pricing both sides" count may ask for (a game page lists about a dozen). */
    const val MAX_BOOKS = 12

    /** The shortest a longest-odds limit can be: +100 is even money; under it would mean "favorites only", which isn't what the option is for. */
    const val MIN_MAX_ODDS = 100

    /** At most this many bets per background cycle (the best edges first); the rest wait for the next. */
    const val MAX_PER_CYCLE = 5

    /** A bet that was refused (the price moved, the edge went) isn't tried again for this long. */
    const val COOLDOWN_MS = 2 * 60_000L

    /** Why a bet is skipped for the per-game limit ([BetLimits.maxPerGame]); one reason for every game, the game and the dollars go in the log. */
    const val GAME_LIMIT_SKIP = "its game already has your most per game at risk (Settings › Betting & Novig account › Most on one game)"

    /**
     * What share of a shown edge a bet with no sharp book's own price behind it kept at the close: Tj's bets placed within 6 h of the start kept about
     * 70% (+2.2% CLV on +3.1% shown, RESEARCH.md §71.2). The soccer study (§72) found the same for the best price against the average's fair at the
     * close (74-105% kept).
     */
    const val NO_SHARP_KEEPS = 0.7

    /**
     * The edge a bet is most likely to keep by the close (RESEARCH.md §72), for choosing which bets get the money when not all of them can (the wallet,
     * [MAX_PER_CYCLE]): the sharpest book's own edge at Novig's price when it priced both sides ([sharpEv]; what bets kept on 48,394 soccer matches was
     * about the sharp book's edge, not the average's), else [NO_SHARP_KEEPS] of the shown edge ([shownEv]). It orders bets only: the stake and every
     * rule still use the shown edge.
     */
    fun credibleEv(shownEv: Double, sharpEv: Double?): Double = sharpEv ?: (shownEv * NO_SHARP_KEEPS)

    data class Rules(
        val minBooks: Int,
        val minEv: Double,
        val twoSided: Int,
        val stake: AutoBetStake,
        val customStake: Double,
        val maxStake: Double,
        /** The longest American odds to bet; 0 = no limit. */
        val maxOdds: Int = 0,
        /** Every book that prices both sides must say +EV on its own ("5 of 5"), as well as [minBooks]. */
        val allAgree: Boolean = false,
        /**
         * The shortest American odds to bet; 0 = no limit. Negative: no favorite shorter than it (−200); positive (+110, Tj 2026-10-07: "make a shortest odds
         * setting"): underdogs at least that long only.
         */
        val minOdds: Int = 0,
        /** A favorite (odds shorter than even money) needs this much more edge than [minEv] ([ScanSettings.autoBetFavouriteExtraEv]); 0 = none. */
        val favouriteExtraEv: Double = 0.0,
        /** The kinds of bet it places (a preset's, RESEARCH.md §66); every kind = no limit. */
        val kinds: Set<BetKind> = BetKind.entries.toSet(),
        /** The fewest stars ([com.tjshea.vigilant.data.scanner.BetRating]) a bet needs; 0 = any. */
        val minStars: Double = 0.0,
        /** A player-prop Over needs this much more edge than [minEv] ([ScanSettings.autoBetOverPropExtraEv]); 0 = none. */
        val overPropExtraEv: Double = 0.0,
    ) {
        /**
         * A minimum star rating is on (Tj, 2026-10-11: "If I have a star rating minimum turned on, I want the app to auto bet all of the scanned bets with at least that rating, regardless of any other
         * settings. But if I turn off the minimum star rating filter, I want it to return back to enforcing all of the other auto bet settings"): the rating decides WHICH bets are placed, so the
         * selection filters (edge, favourite bar, odds limits, kinds, books agreeing, the sharp rule, the trap guard, the small-prop guard) are not applied. What stays whatever the rating says is
         * the safety under them ([starsKept]): the dollar limits, pregame, a fresh Novig price and fair, a price that still matches, a bet that is still +EV, an injured player, one bet a market.
         */
        val byStars: Boolean get() = minStars > 0.0

        /** The least edge a bet's page is read for and an order may still go at: [minEv], or only the noise floor ([MIN_EV_FLOOR]) while the rating decides. */
        val readEv: Double get() = if (byStars) MIN_EV_FLOOR else minEv
    }

    /** What still applies while a minimum star rating decides, for the Auto-bet tab to say (and a test to pin). */
    const val starsKept: String = "the dollar limits (per bet, per day, per game), pregame only, a fresh Novig price and fair odds, a price that still matches, a bet that is still +EV, an injured player, and one bet a market"

    /** The "too good to be true" cap's reason, or null: an edge over [MAX_SANE_EV] is usually a stale or mismatched price (the rating cannot see that), so it is never bet unattended. */
    fun tooGoodReason(ev: Double): String? =
        if (ev > MAX_SANE_EV) "its edge ${percent(ev)} is over ${percent(MAX_SANE_EV)}, which is usually a stale or mismatched price (place it by hand if you trust it)" else null

    fun rules(s: ScanSettings) = Rules(
        minBooks = s.autoBetBooks.coerceIn(2, MAX_BOOKS),
        minEv = s.autoBetMinEv.coerceAtLeast(MIN_EV_FLOOR),
        twoSided = s.autoBetTwoSided.coerceIn(1, MAX_BOOKS),
        stake = s.autoBetStake,
        customStake = s.autoBetCustomStake.coerceAtLeast(0.0),
        maxStake = s.autoBetMaxStake.coerceAtLeast(0.0),
        maxOdds = s.autoBetMaxOdds.let { if (it <= 0) 0 else it.coerceAtLeast(MIN_MAX_ODDS) },
        allAgree = s.autoBetAllAgree,
        minOdds = normalizeMinOdds(s.autoBetMinOdds),
        favouriteExtraEv = s.autoBetFavouriteExtraEv.coerceIn(0.0, 0.2),
        kinds = s.autoBetKinds,
        minStars = s.autoBetMinStars.coerceIn(0.0, 5.0),
        overPropExtraEv = s.autoBetOverPropExtraEv.coerceIn(0.0, 0.1),
    )

    /** Whether [bet] ("Josh Allen Over 36.5") in [market] is the Over side of a player prop: the side that wins less often than its fair chance says ([ScanSettings.autoBetOverPropExtraEv]). */
    fun isOverProp(market: String, bet: String): Boolean {
        val pick = com.tjshea.vigilant.data.tracker.BetGrader.pickOf(market, bet)
        if (pick is com.tjshea.vigilant.data.tracker.BetGrader.Pick.Prop) return pick.over
        // A stat the grader cannot read from a score (sacks, pitcher outs) is still a prop told by its words.
        return pick == null && BetKind.of(market, bet) == BetKind.PROP && OVER_WORD.containsMatchIn(bet)
    }

    private val OVER_WORD = Regex("\\bover\\b", RegexOption.IGNORE_CASE)

    /**
     * Why a bet rated [stars] is under the minimum rating, or null (also null when no minimum is set). A bet with no rating yet has no stars to show and waits. [shown] is the rating the card
     * shows before Profit-proven only lowered it: when it differs from [stars] the reason says both (Tj, 2026-10-13).
     */
    fun starsReason(rules: Rules, stars: Double?, shown: Double? = null): String? {
        if (rules.minStars <= 0.0) return null
        val min = com.tjshea.vigilant.data.scanner.BetRating.label(rules.minStars)
        if (stars == null) return "it has no star rating yet (you need $min stars)"
        if (stars >= rules.minStars - 1e-9) return null
        if (stars < com.tjshea.vigilant.data.scanner.BetRating.MIN) return "it has no trusted edge (no stars; you need $min)"
        val had = shown?.takeIf { kotlin.math.abs(it - stars) > 1e-9 }
        return if (had == null) "its rating is under your $min-star minimum"
        else "its rating is ${com.tjshea.vigilant.data.scanner.BetRating.label(stars)}★ after Profit-proven only lowered the shown ${com.tjshea.vigilant.data.scanner.BetRating.label(had)}★ (your history says this type of bet loses money), under your $min-star minimum"
    }

    /** Whether [american] odds are a favorite's: shorter than even money (−101 or shorter). Even money (+100, −100) and every underdog is not. */
    fun isFavourite(american: Int): Boolean = american < -100

    /** The smallest edge a bet at [american] odds needs: [Rules.minEv], plus [Rules.favouriteExtraEv] for a favorite and [Rules.overPropExtraEv] for a player-prop Over ([overProp]). */
    fun evBar(rules: Rules, american: Int, overProp: Boolean = false): Double =
        rules.minEv + (if (isFavourite(american)) rules.favouriteExtraEv else 0.0) + (if (overProp) rules.overPropExtraEv else 0.0)

    /** The longest a favorite-side shortest-odds limit can be: −100 is even money. A positive limit (+100 or more) means underdogs only. */
    const val MAX_MIN_ODDS = -100

    /** [minOdds] as a rule: 0 = none, a negative one at most −100, a positive one at least +100 (anything between is even money's own side). */
    fun normalizeMinOdds(minOdds: Int): Int = when {
        minOdds == 0 -> 0
        minOdds < 0 -> minOdds.coerceAtMost(MAX_MIN_ODDS)
        else -> minOdds.coerceAtLeast(MIN_MAX_ODDS)
    }

    /**
     * Whether [american] odds are shorter than the [minOdds] limit (0 = no limit): with −200, −250 is shorter and an underdog never is; with +110 (underdogs only),
     * every favorite and any underdog under +110 is.
     */
    fun tooShort(minOdds: Int, american: Int): Boolean = when {
        minOdds == 0 -> false
        minOdds < 0 -> american < 0 && american < minOdds
        else -> american < minOdds
    }

    /** Whether [american] odds are longer than the [maxOdds] limit (0 = no limit). A favorite's negative odds never are. */
    fun tooLong(maxOdds: Int, american: Int): Boolean = maxOdds > 0 && american > maxOdds

    /**
     * Why a bet doesn't pass Tj's criteria, or null when it does. [shownEv]: the EV the CNO card shows (CNO's fair odds against Novig's price
     * now). [check]: what the books on the bet's game page say ([CnoBooks.check], judged at that same price). [american]: that price as
     * American odds, for the longest-odds limit.
     */
    fun judge(rules: Rules, shownEv: Double, check: CnoBooks.Check, american: Int, kind: BetKind? = null, overProp: Boolean = false): String? {
        // The rating decides which bets go: only the cap and "the books' own line says +EV" apply, then [starsReason] (the caller's) holds the rating to the minimum.
        if (rules.byStars) {
            tooGoodReason(shownEv)?.let { return it }
            val own = check.ev
            return if (own == null || own <= 0.0) "the books' own fair line says ${own?.let(::percent) ?: "nothing"}, not +EV" else null
        }
        if (kind != null && kind !in rules.kinds) return "${kind.label.lowercase()} aren't among the kinds of bet you auto-bet"
        if (shownEv < rules.minEv - 1e-9) return "its edge ${percent(shownEv)} is under your ${percent(rules.minEv)} minimum"
        // The bar a bet must clear (Tj, 2026-10-07, proposals 4 and 5): the minimum, and for a favorite more (the edge a short price keeps by the close is smaller), judged on the LOWER
        // of CNO's edge and the app's own book check's (9 of 99 bets in the first three days had a check edge under 2.5% at a CNO edge over it). Said apart from the plain minimum so
        // the report counts each on its own.
        val own = check.ev?.takeIf { it > 0.0 }   // a books' edge at or under zero is the "not +EV" check's below, with its own words
        val bar = evBar(rules, american, overProp)
        if (minOf(shownEv, own ?: shownEv) < bar - 1e-9) {
            return if (own != null && own < shownEv - 1e-9) "the books' own check puts its edge at ${percent(own)}, under the ${percent(bar)} it needs (the lower of CNO's and the books' edge is used)"
            else if (overProp && shownEv >= evBar(rules, american) - 1e-9) "it is a player-prop Over and its edge ${percent(shownEv)} is under the ${percent(bar)} Overs need"
            else "it is a favorite and its edge ${percent(shownEv)} is under the ${percent(bar)} favorites need"
        }
        tooGoodReason(shownEv)?.let { return it }
        // (No odds in the words: the report counts bets by reason, and each price would be a reason of its own.)
        if (tooLong(rules.maxOdds, american)) return "its odds are longer than your ${Odds.formatAmerican(rules.maxOdds)} limit"
        if (tooShort(rules.minOdds, american)) return "its odds are shorter than your ${Odds.formatAmerican(rules.minOdds)} limit"
        if (check.twoSided < rules.twoSided) return "${books(check.twoSided)} price${if (check.twoSided == 1) "s" else ""} both sides (you need ${rules.twoSided})"
        if (check.agreeing < rules.minBooks) return "${books(check.agreeing)} say${if (check.agreeing == 1) "s" else ""} +EV on their own (you need ${rules.minBooks})"
        // Every book scanned (those that price both sides) says +EV on its own: "5 of 5". Books that list only one side of the bet can't be judged and aren't counted.
        if (rules.allAgree && check.agreeing < check.twoSided) return "only ${check.agreeing} of ${check.twoSided} books say +EV on their own (you need every one)"
        val ev = check.ev
        if (ev == null || ev <= 0.0) return "the books' own fair line says ${ev?.let(::percent) ?: "nothing"}, not +EV"
        return null
    }

    /**
     * Why a bet from Vigilant's own scan (tennis, which CNO doesn't carry) doesn't pass, or null when it does. The same bars as [judge] on the scan's edge ([ev], Novig's price against
     * the Pinnacle-priced fair): the kinds, the minimum (and the favourite's extra), the too-good-to-be-true cap and the odds limits. There is no books page to check it against.
     */
    fun judgeScan(rules: Rules, ev: Double, american: Int, kind: BetKind?, overProp: Boolean = false): String? {
        if (rules.byStars) return tooGoodReason(ev)
        if (kind != null && kind !in rules.kinds) return "${kind.label.lowercase()} aren't among the kinds of bet you auto-bet"
        val bar = evBar(rules, american, overProp)
        if (ev < bar - 1e-9) return "its edge ${percent(ev)} is under the ${percent(bar)} it needs"
        tooGoodReason(ev)?.let { return it }
        if (tooLong(rules.maxOdds, american)) return "its odds are longer than your ${Odds.formatAmerican(rules.maxOdds)} limit"
        if (tooShort(rules.minOdds, american)) return "its odds are shorter than your ${Odds.formatAmerican(rules.minOdds)} limit"
        return null
    }

    /**
     * Why a bet from Vigilant's own scan (not tennis) doesn't pass, or null when it does (Tj, 2026-10-11: "make the auto bet feature also bet on vigilant scanned bets, but only if they meet
     * the criteria I set for auto bet"). [judgeScan]'s bars on the scan's edge, then the books-agree rules the CNO path applies to its books page, applied here to the books behind the
     * scan's fair line (one a company, odds that look wrong left out): [Rules.twoSided] books price both sides, [Rules.minBooks] of them say +EV on their own, and with [Rules.allAgree] every one.
     * [agreeing] and [twoSided] are [com.tjshea.vigilant.data.scanner.Agreement.Count]'s.
     */
    fun judgeVigilant(rules: Rules, ev: Double, american: Int, kind: BetKind?, twoSided: Int, agreeing: Int, overProp: Boolean = false): String? {
        judgeScan(rules, ev, american, kind, overProp)?.let { return it }
        if (rules.byStars) return null
        if (twoSided < rules.twoSided) return "${books(twoSided)} price${if (twoSided == 1) "s" else ""} both sides (you need ${rules.twoSided})"
        if (agreeing < rules.minBooks) return "${books(agreeing)} say${if (agreeing == 1) "s" else ""} +EV on their own (you need ${rules.minBooks})"
        if (rules.allAgree && agreeing < twoSided) return "only $agreeing of $twoSided books say +EV on their own (you need every one)"
        return null
    }

    /** The stake for a scan's bet at [quote] (see [stake]): the rule's amount, or Kelly on the quote's own fair and price, held to the per-bet maximum and the wallet. */
    fun scanStake(rules: Rules, quote: EvQuote, bankroll: Double, balance: Double, hoursToStart: Double? = null): Stake {
        if (balance < MIN_STAKE - 1e-9) return Stake.WalletEmpty
        val wanted = when (rules.stake) {
            AutoBetStake.ONE_DOLLAR -> 1.0
            AutoBetStake.CUSTOM -> rules.customStake
            else -> {
                val kelly = rules.stake.kelly ?: return Stake.Skip("no Kelly fraction")
                if (!(bankroll > 0.0)) return Stake.Skip("it has no Kelly stake (no bankroll set)")
                EvMath.suggestedStake(quote, bankroll, kelly, null, hoursToStart).takeIf { it > 0.0 } ?: return Stake.Skip("it has no Kelly stake (no edge at this price)")
            }
        }
        return cap(rules, wanted, balance)
    }

    /** How much to stake on one bet. */
    sealed interface Stake {
        data class Amount(val dollars: Double) : Stake

        /** Not this bet: [reason] in words. */
        data class Skip(val reason: String) : Stake

        /** The wallet can't fund a [MIN_STAKE] (one cent) bet: no bet at all, and none after it either. */
        object WalletEmpty : Stake
    }

    /**
     * The stake for [row] (judged at Novig's price now): the rule's amount, held to Tj's per-bet maximum and to what's left in the wallet
     * ([balance]), floored to the cent, never under [MIN_STAKE]. A Kelly stake is [bankroll] × the fraction × full Kelly for this bet's own
     * price and fair chance (`(fair − price) / (1 − price)`, no fee pregame), held to what Novig has for sale at +EV, so it changes with the
     * odds of every bet. [sharpFair]: the sharpest book's own fair for this side when its veto priced the bet; the Kelly fair is never above it
     * (RESEARCH.md §72: what a bet keeps is about the sharp book's edge, and Kelly on an edge overestimated by more than 2× loses money: Benter).
     */
    fun stake(rules: Rules, row: CnoRow, bankroll: Double, balance: Double, sharpFair: Double? = null, checkFair: Double? = null, hoursToStart: Double? = null): Stake {
        if (balance < MIN_STAKE - 1e-9) return Stake.WalletEmpty
        val wanted = when (rules.stake) {
            AutoBetStake.ONE_DOLLAR -> 1.0
            AutoBetStake.CUSTOM -> rules.customStake
            else -> kellyStake(row, bankroll, rules.stake.kelly ?: return Stake.Skip("no Kelly fraction"), sharpFair, checkFair, hoursToStart) ?: return Stake.Skip("it has no Kelly stake (no edge at this price, or its fair odds are missing, or no bankroll is set)")
        }
        return cap(rules, wanted, balance)
    }

    /** [wanted] dollars held to Tj's per-bet maximum and to the wallet ([balance]), floored to the cent; a skip with why when nothing fundable is left. */
    fun cap(rules: Rules, wanted: Double, balance: Double): Stake {
        if (!(wanted > 0.0)) return Stake.Skip("the amount to stake is $0")
        val capped = floorCents(minOf(wanted, rules.maxStake, balance))
        if (capped < MIN_STAKE - 1e-9) {
            return Stake.Skip(
                when {
                    rules.maxStake < MIN_STAKE -> "your maximum per bet is under a cent"
                    wanted < MIN_STAKE -> "its ${rules.stake.label} stake is under a cent"
                    else -> "what the wallet can fund is under a cent"
                },
            )
        }
        return Stake.Amount(capped)
    }

    /**
     * The Kelly stake in dollars at [fraction] of full Kelly; null when there's no edge, no fair probability or no bankroll. The fair is CNO's, or
     * [sharpFair] (the sharpest book's own) when that is lower: never more than the sharp book backs.
     */
    fun kellyStake(row: CnoRow, bankroll: Double, fraction: Double, sharpFair: Double? = null, checkFair: Double? = null, hoursToStart: Double? = null): Double? {
        if (!(bankroll > 0.0)) return null
        val cno = CnoChecks.fairProbability(row) ?: return null
        // The lowest of the three fairs: CNO's, the sharpest book's and the app's own book check's (Tj, 2026-10-07: size on the lower edge too, not only gate on it).
        val fair = listOfNotNull(cno, sharpFair?.takeIf { it > 0.0 }, checkFair?.takeIf { it > 0.0 }).min()
        val price = 1.0 / Odds.americanToDecimal(row.odds)
        val stake = EvMath.suggestedStake(EvQuote(fair, price, 0.0), bankroll, fraction, row.available, hoursToStart)
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
