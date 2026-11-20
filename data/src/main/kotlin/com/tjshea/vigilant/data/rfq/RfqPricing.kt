package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.floor

/**
 * What the parlay maker does and how much it may risk (MK, Tj 2026-11-18). Kept in a FILE on `cloud-config` (`rfq-maker.json`), not in the app's settings: an older phone build can never write it back
 * to a default, and a session changes it with one plain commit (`tools/rfq_set.py`). Modes: [Mode.OFF] nothing, [Mode.SHADOW] register, listen and journal, SEND NOTHING, [Mode.LIVE] quote and confirm
 * within the caps below. The default is OFF; LIVE needs the evidence rule in research/novig_rfq_maker_2026-11-18.md.
 */
@Serializable
data class RfqConfig(
    val mode: Mode = Mode.OFF,
    /**
     * The quote's FLOOR margin over the parlay's fair probability, relative: price >= fair x (1 + this). The maker's EV per $1 of pot is price - fair. The binding terms are [minReturn] and [minPoints]:
     * the first shadow day (188 priced trades) showed the market prices by what a parlay ties up, not by a percentage of its probability (a relative margin tied $22,000 of collateral for $50 of EV, a
     * 4% return on the collateral $2,700 for $82; research/novig_rfq_maker_2026-11-18.md section 9).
     */
    val margin: Double = 0.02,
    /** The return on the maker's collateral a quote must offer if the fair is true: price >= (fair + r) / (1 + r), where r = this x max(1, days the collateral stays tied up + 0.25). */
    val minReturn: Double = 0.06,
    /**
     * The return on collateral per day a LIVE quote must offer (MK14, Tj 2026-11-19: "tune it for volume and profitability"). The market asks for far more collateral than a small maker has: on 11-19 the quoteable
     * trades alone needed about $1,100 of new collateral an hour, held 18 hours on average, against $80 of cash, so the capital is the scarce thing and a trade that ties it up for little is a trade given away.
     * Simulated on the 123 trades the rule would have won in 1.35 hours: a 2%/day hurdle keeps 90% of the expected profit on 57% of the collateral (return on capital 2.1% -> 3.9% a day), 3% keeps 77% on 37%
     * (4.7%), 5% keeps 52% on 16% (9.5%). SHADOW keeps quoting at [minReturn] (the volume of paper evidence) and journals what the LIVE hurdle would have quoted (`livePrice`, `liveWouldWin`); LIVE uses
     * max([minReturn], this) in the rule and in the market model's cost of collateral.
     */
    val liveMinReturn: Double = 0.03,
    /**
     * Whether a parlay with a player-prop or any other leg that is not a game line may be quoted. OFF: on game lines the market paid a median 0.94 points of pot over our fair (p90 2.6), on parlays with a
     * player prop 6.0 points (+38%, 91% of them 15% or more above it): our prop fair runs high (prop Overs lose at Novig, research/propline_vs_vigilant_2026-10-16.md), so quoting those would win exactly the trades
     * where our fair is wrong (research/novig_rfq_maker_2026-11-18.md section 10).
     */
    val allowProps: Boolean = false,
    /** Points of pot (price - fair) a quote must offer per leg beyond the first: the fair of N legs carries N legs' errors, so a 3-leg parlay asks twice what a 2-leg one does. */
    val minPoints: Double = 0.01,
    /** Largest stake one quote covers, dollars (Novig needs at least $10 or the user's minimum). */
    val maxStake: Double = 12.0,
    /** Most collateral (the maker's money at risk) in one parlay, dollars. */
    val maxCollateral: Double = 45.0,
    /** Most collateral in all open RFQ parlays together, dollars. */
    val maxOpenCollateral: Double = 90.0,
    /** Fewest companies behind a leg's fair line. */
    val minBooks: Int = 3,
    /** A leg's fair must be younger than this (ms). */
    val maxFairAgeMs: Long = 5 * 60_000L,
    val maxLegs: Int = 5,
    /**
     * Legs of one game priced together (MK12, Tj 2026-11-19: "figure out 2 leg correlated parlays and what a fair price should be"): two or three game-line legs of one game (a side, the total, a team total, first-half
     * versions) are joined by [SameGame]'s Gaussian copula, calibrated on closing lines and results (a side and the total are nearly independent: NBA -0.01, NFL +0.03, NHL +0.04, NCAAB -0.01, NCAAF +0.17;
     * a team's total follows its game's total and margin at 0.6-0.7). This is the premium (relative) put on that joint probability for the model's own error. The market priced a side and the total 3% over independent
     * on 404 trades, so 2% keeps a margin on both. Before MK12 this was a flat 8% on a side and the total and 0 meant "decline every same-game parlay"; now 0 = no premium and a NEGATIVE value = decline them all.
     */
    val sameGamePremium: Double = 0.02,
    /**
     * Player-prop legs at a premium (Tj, 2026-11-19: "allow prop parlays but price them at a premium as well"). With [allowProps] off, a parlay of up to two prop legs is quoted when its fair probability is raised
     * by this much (relative) for EACH prop leg. The market paid a median +38% over our prop fair (91% of such trades 15% or more over it, research/novig_rfq_maker_2026-11-18.md section 10), so 20% a leg wins the
     * trades the market overpays and leaves room for our prop fair running high. 0 = decline prop parlays.
     */
    val propPremium: Double = 0.20,
    /** Legs must start at least this far ahead (ms) and no further than [maxDaysAhead]. */
    val minLeadMs: Long = 20 * 60_000L,
    val maxDaysAhead: Int = 5,
    /**
     * PS1 (the wallet is the limit: the study of 2026-11-20 found about 20-40 parlays we would win for every one the wallet can fund, so what the filters can buy is the return on each dollar of collateral per day it is locked):
     * no leg may start further ahead than this (ms; 0 = off). Collateral is locked until the LAST game ends: parlays whose last leg starts within 8 h returned 61% of the collateral per day of lock against 28% for the ones
     * that start 8 to 24 h out (research/parlay_study_2026-11-20.md).
     */
    val maxLeadMs: Long = 0L,
    val minFair: Double = 0.03,
    /**
     * The most likely a parlay may be to win and still be quoted (MK16, Tj 2026-11-19: "do not allow parlays that are too easy to win ... set a reasonable cap"). 0.60: above it the buyer wins more often than
     * not, our collateral buys little profit (an edge of about a point on a few dollars of collateral), and where the market trades such parlays our price would have won 18 of 19 (95%, against 17% for all of
     * them): the sign that the market prices favourites above our fair, so what we win is what we mis-price. It was 0.85, which no parlay the market traded ever reached; paper cost of 0.60: 2.5% of the
     * quoteable trades and 8% of the paper profit (research/parlay_expected_profit_2026-11-19.md section 7).
     */
    val maxFair: Double = 0.60,
    /**
     * The most likely any ONE leg may be to win (MK16): a leg we give over 90% is padding, a near-certainty the buyer adds for free and the place where our fair is most likely to be low (the favourite-longshot
     * bias: our 70-85% legs hit 82%, and our price won 42% of the trades with a leg over 90% against 17% of all). 0.90.
     */
    val maxLegFair: Double = 0.90,
    /** MK20: price two player props of one game from the measured ratio of how often such pairs hit together ([PropPairs]); off = they decline as "correlation unmodelled". */
    val propPairs: Boolean = true,
    /** MK20: quote a pair [PropPairs] prices (OFF = shadow: the model's fair is only journaled as the decline's fair, nothing is quoted until paper results say the price beats the market's trades). */
    val propPairsLive: Boolean = false,
    /**
     * MK38: dollars of the wallet's free cash a quote priced by [PropPairs] must leave alone. Same-game two-prop RFQs are 22% of all RFQs but only 0.04% of them trade (20 executed of 46,658, $342 of
     * wager), against 2.6% of in-play same-game game-line pairs (91 of 3,550): quoted without a reserve they would tie up the cash (a quote's collateral is held until it expires) that the pairs that
     * do trade need. A prop-pair quote may only use the free cash ABOVE this; 0 = no reserve.
     */
    val propPairsCashReserve: Double = 60.0,
    /**
     * MK45: dollars of free cash ANY quote that has a player-prop leg must leave alone. Measured on the journals of five jobs (125k fair-bearing RFQs): trades per $1,000 of collateral held for a minute run 4.1 for
     * in-play two-leg game-line parlays (4.5% of those RFQs trade, $13 of collateral a $10 quote), 2.0 pregame two-leg game lines, 1.5 in-play same-game pairs, down to 0.04-0.05 for parlays with a prop (0.08% of the
     * two-prop RFQs trade, 0.42% of three-prop ones; props are 53% of all RFQs). A quote's collateral is held about 48 s, so with ~$118 free the maker holds ~8 quotes a minute whatever it quotes: cash spent on a
     * prop parlay is cash the in-play game lines cannot use. A prop-involving quote may only use the free cash ABOVE this; 0 = no reserve.
     */
    val propCashReserve: Double = 90.0,
    /**
     * MK47: companies a GAME-LINE leg's fair must rest on (a prop leg keeps [minBooks]). After the coverage work 12.6% of RFQs were declined because a leg's fair rests on 2 companies (15,633 of 135k RFQs in the
     * last two jobs; 1,410 on 1). The market's executed price minus our fair on the executed RFQs: game lines on 2 companies (n=138) median +0.18 pt, p25 -0.26, p75 +0.35, p10 -0.48: as tight as the 3+ company ones
     * (n=3,274: +0.27, -0.03, +0.71), so a 2-company game-line fair is as good; props on 2 companies (n=112) p10 -6.9, p25 -2.5: too noisy, they keep [minBooks]; one company never passes.
     */
    val minBooksGameLines: Int = 2,
    /**
     * MK56: parlays (open or quoted) with the same set of legs the maker may hold: 1 = an identical parlay is never taken twice. 0 = no limit. The same two legs (Magic + Thunder) were filled twice in 7 minutes.
     */
    val maxSameParlay: Int = 1,
    /** MK56: open parlays and quotes still out one outcome may be in: the Houston Texans moneyline was in 6 of the 20 settled parlays, which all lost together. 0 = no limit. */
    val maxParlaysPerOutcome: Int = 2,
    /**
     * MK48: relative premium on the parlay's fair per EXTRAPOLATED game-line leg (an alternate number outside the game's posted ones, [RfqLadder.extrapolateGameLine]). The engine's own held-out check
     * (`RFQ alt-line check`, 232 outer rungs of posted ladders predicted from the rest): median error 1.8 points of probability, p90 5.6, worst 7.1; against a parlay margin of +0.16 pt that noise decides who
     * wins the auction (the winner is the one whose error favours the buyer), so each such leg asks 3% on top, as a prop leg asks 20% ([RfqConfig.propPremium]). 0 = none.
     */
    val extrapolatedLegPremium: Double = 0.03,
    /** MK45: how long a quote's collateral stays reserved after its 3 s auction closes: buyers accepted 6 to 29 s after the quote (16 fills, 9 more measured 2026-11-20: 6.6-25.3 s); 45 s was 1.5x the slowest, 36 s is 1.25x. */
    val reserveAfterCloseMs: Long = 36_000L,
    /** MK21: price a player-prop rung outside the posted range from the posted rung nearest even ([RfqLadder.extrapolate]); such a leg is a prop leg like any (premium, at most two). OFF = only posted and interpolated rungs. */
    val ladderExtrapolate: Boolean = true,
    /** MK22: price a player-prop rung from every book's price at that very strike, two-sided rows and "N+" ladders alike ([PropLadderFair]); OFF = only the scan's own fair lines, interpolation and extrapolation. */
    val ladderVotes: Boolean = true,
    /** MK33: correct each leg's fair for the devig's measured bias before the legs are multiplied ([RfqLegCalibration]: longshot legs win ~10% less often than priced, 60-75% legs 2-3% more often). OFF = the raw fair. */
    val legCalibration: Boolean = true,
    /**
     * MK36 (the other session's graded study of 5,951 executed game-line parlays, Release rfq-pnl-study; Tj 2026-11-20): a parlay whose legs are ALL at least [allHighLegFloor] and that has more than this many legs cost the makers
     * 20.6% of the pot (1,376 trades, se 1.2%; buyers won 53% against a price of 32%), while 2-leg parlays earned +3.2% and parlays with a leg under 50% earned +2.1%. 0 = no such rule.
     */
    val allHighMaxLegs: Int = 2,
    val allHighLegFloor: Double = 0.5,
    /** MK24: a parlay of 3 up to this many player-prop legs is priced at the plain product of the legs' fair and declined as shadow (journal evidence), never quoted; 0 = declined unpriced. */
    val propLegsShadowMax: Int = 6,
    /** The most player-prop legs a quoted parlay may carry (Tj 2026-11-20: "Put no limit on the amount of prop legs and the amount of legs total"): no limit by default; each prop leg still carries [propPremium]. Beyond it the parlay is priced in shadow up to [propLegsShadowMax]. */
    val maxPropLegs: Int = Int.MAX_VALUE,
    /**
     * MK31 (Tj 2026-11-20: "price and fill live, in game parlay orders ... not too risky and nothing over a 50% or so chance of winning"): parlays with a leg in play, each such leg priced from Novig's live book
     * ([LiveFair]). OFF = declined ("a leg is in play"); SHADOW = priced and declined with our fair in the journal (what the market pays against it is the evidence); LIVE = quoted.
     */
    val liveParlays: Mode = Mode.SHADOW,
    /** A parlay with a leg in play is not quoted above this win chance (Tj: "nothing over a 50% or so"). */
    val liveMaxFair: Double = 0.50,
    /**
     * MK35 (research/rfq_maker_pnl_2026-11-20.md, backtest on 5,951 executed game-line parlays graded by final scores): a pregame parlay of 3 or more legs needs at least one leg below this win chance. Parlays of 3+
     * legs where every leg is a favourite are the segment makers lose 20% of the pot in (buyers won 52.8% against a price of 32.2%); with a longshot among them it is about even. 0 = no such rule.
     */
    val manyLegsNeedLongshot: Double = 0.50,
    /** An in-play leg may be nearly decided (a buyer pads a parlay with a 97% leg): the leg cap is higher than the pregame [maxLegFair], the parlay's win chance is capped by [liveMaxFair] (journal of 2026-11-20: 1,500 of 1,938 priced in-play RFQs were declined by the 90% leg cap). */
    val liveMaxLegFair: Double = 0.99,
    /** An in-play leg's price must be younger than this at the decision (a buyer accepts 6-29 s after the auction; the game moves meanwhile). */
    val liveMaxAgeMs: Long = 4_000L,
    /** The widest best-bid to buy-price gap of a live market that is priced at all ([LiveFair.DEFAULT_MAX_SPREAD]). */
    val liveMaxSpread: Double = 0.04,
    /** In-play quote floors on top of the usual ones: the fair x (1 + this), and the fair + this per leg (game moves between the auction and the accept). */
    val livePremium: Double = 0.05,
    val livePointsPerLeg: Double = 0.01,
    /** The most collateral one in-play parlay may tie up, dollars (0 = the usual caps only). */
    val liveMaxCollateral: Double = 30.0,
    /**
     * MK17 (Tj 2026-11-19: "only accepts parlays that will finish today (the latest leg is a game on todays slate)"): quote only a parlay whose LAST leg starts before today's slate ends (the sports day
     * runs 5 am to 5 am Eastern, like the Tracker's). Off = any game within [maxDaysAhead].
     */
    val onlyToday: Boolean = false,
    /** MK17 (Tj: "accept fewer parlays but at longer odds ... more of a longshot, such as 25% or less chance to win"): quote only a parlay we give at most [longshotMaxChance] to win. Off = the [maxFair] cap alone. */
    val longshotsOnly: Boolean = false,
    val longshotMaxChance: Double = 0.25,
    /**
     * The safety premium (relative) a longshot parlay's fair carries in [longshotsOnly] (MK17, the positive-EV pass): of the 2,025 parlays graded from final scores, the ones we gave 0-10% hit 5.2% against a 4.6% fair
     * and the 10-20% ones 15.1% against 13.8% (our fair too low by about a point, the size of our whole edge; clustered, not yet significant). 3% on the fair covers about a third of it and 5% all of it (paper fills of a 25%-or-less, same-day setup over 5.9 h: 7 at 0%, 3 at 3%, 1 at 5%, at the 3%/day hurdle); 0 = none.
     */
    val longshotPremium: Double = 0.03,
    /**
     * MK17 (Tj: "the engine has to have a way to calculate this within the amount in my wallet so that it can accept parlays up to the collateral amount available in my wallet at any time"): the WALLET is
     * the collateral limit instead of the fixed dollar caps. With it on: one parlay may lock up at most [walletShare] of the wallet (cash + the collateral already held), one outcome (a leg shared by many
     * parlays) at most [walletLegShare] of it, the open total is whatever cash is available (the quote is sized to the balance that is there), and the loss stop is [dayLossStopPct] of the wallet when that is more
     * than [dayLossStop]. The balance is read every minute (LIVE, or SHADOW with this on, so the paper run judges as the live one would).
     */
    val walletCollateral: Boolean = false,
    val walletShare: Double = 0.30,
    val walletLegShare: Double = 0.50,
    val dayLossStopPct: Double = 0.20,
    /** The shortest lock-up (days) the return hurdle is charged for: 1 = a day however soon the games are; a same-day mode charges its real hold (about a quarter of a day). */
    val holdFloorDays: Double = 1.0,
    /** The loss over the last 24 hours of SETTLED parlays (dollars, from Novig's executions) at which LIVE quoting stops for six hours ([RfqMaker.onLedger]); 0 = no stop. */
    val dayLossStop: Double = 20.0,
    /**
     * Ask Novig what the other makers charge for a sample of the RFQs we price (MK12, [RfqProbe]: `POST /rfq/v2/quote/unauthenticated`, keyless, at most ~0.9 requests a second, each opening a real 3-second auction
     * whose best price is the competition's). Runs in SHADOW and, for RFQs we decline, in LIVE (a probe of an RFQ we would quote would meet our own quote). 100,000 observations a day against about 250 priced
     * trades a day: the demand curve [marketPricing] needs.
     */
    val probe: Boolean = true,
    /**
     * Quote at the margin that maximises the expected profit on the collateral, from what the probes showed the market pays for that class of parlay ([RfqMarketModel.best]), never below the floors above. OFF by
     * default: the model is judged in SHADOW first (each CREATED/EXECUTED row journals both the rule's price and the model's), and LIVE needs it switched on with the evidence in research/novig_parlay_maker_2026-11-19.md.
     */
    val marketPricing: Boolean = false,
    /** Fewest probe answers in a class before the market model prices it. */
    val probeMinSamples: Int = 40,
    /**
     * Pairs of player props in one game (assists + goals, TD + TD: 32% of the two-leg same-game RFQs) have no correlation model; the probes measure what the market charges over their independent product. ON: such a
     * parlay is priced on the market's MEDIAN ratio for its class (never below 0.5 or above 4), the floors on top, once the class has [probeMinSamples] answers; OFF: declined as "correlation unmodelled".
     */
    val propPairsFromMarket: Boolean = false,
    /**
     * The relative error of one leg's fair (2%): the market model's own floor under a quote is fair x this x sqrt(legs) (and never under [margin] x fair), and half of it is the allowance for our fair's error
     * in [RfqMarketModel.best]. Two legs at fair 0.25: 0.7 point, the old points floor; four legs at fair 0.08: 0.3 point (the old floor asked 2.1 points of a parlay the market prices at +0.4 over fair in
     * relative terms +5%). The market pays about the same points of pot whatever the number of legs (+0.37 at two, +0.50 at three, +0.39 at four, +0.62 at five: 7,626 trades), so a points floor per leg
     * priced the long parlays, 55% of all trades, out of every auction. Used only when [marketPricing] is on; the rule keeps its floors.
     */
    val fairErrorRel: Double = 0.02,
    /** MK13: dollars of the maker's balance a LIVE quote leaves untouched (fees, the engine's own bids); quotes are sized to the cash that is there beyond it. */
    val cashReserve: Double = 3.0,
    /**
     * MK13 (the 13 live parlays of 2026-11-19: 6 rode the same Houston game, about half the open collateral, and the same outcomes Shelton and Zverev in 3 more): the most collateral one OUTCOME may carry across the
     * open parlays and the quotes still out, dollars. A new quote that would take any of its legs over it is declined ("a leg already carries..."). 0 = off. LIVE only: SHADOW judges prices.
     */
    val maxLegCollateral: Double = 30.0,
) {
    enum class Mode { OFF, SHADOW, LIVE }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String?): RfqConfig? = text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** One leg's fair price as the engine's scan holds it. */
data class LegFair(
    val outcomeId: String,
    val eventId: String,
    val label: String,
    /** The leg's true-probability estimate (devigged, after the engine's own cuts). */
    val probability: Double,
    /** Companies behind the fair line. */
    val books: Int,
    /** When the oldest book price behind it was last seen (ms), or null when unknown. */
    val asOfMs: Long?,
    val startsMs: Long,
    val live: Boolean,
    /** Novig's market type for the leg (MONEY, SPREAD, TOTAL, POINTS...); empty = unknown, which counts as not a game line. */
    val marketType: String = "",
    /** What the line is about and which side of it the leg takes: what [SameGame] needs to price two or three legs of one game together (null = unknown: not modelled). */
    val kind: LineKind? = null,
    val side: Side? = null,
    /** 0 = full game, 1 = first half. */
    val period: Int = 0,
    /** A spread leg: the HOME team's handicap (negative = home favoured). */
    val homeLine: Double? = null,
    /** A team total: whether it is the AWAY team's. */
    val awayTeam: Boolean = false,
    /** Novig's league name (NBA, NFL, NCAAF...): picks the margin/total calibration in [SameGame.SPORTS]. */
    val league: String = "",
    /** The outcome's own name ("MIL +31.5", "Over 234.5", "MIL") and its event's "Away @ Home": journaled with the quote so the parlay can be graded from the final score later, whatever has left the catalog (MK14). */
    val outcomeName: String = "",
    val eventDesc: String = "",
    /** The market the outcome belongs to: the parlay hub reads the leg's result from it once the game is over (MK15). */
    val marketId: String = "",
    /** The fair was extrapolated beyond the posted rungs of the player's stat ([RfqLadder.extrapolate], MK21), not posted or interpolated. */
    val extrapolated: Boolean = false,
    /** Where the fair came from when it is not the scan's: [LiveFair.SOURCE] for a leg in play priced from Novig's live book (MK31). */
    val source: String = "",
)

fun interface LegFairs { fun leg(outcomeId: String): LegFair? }

sealed interface RfqDecision {
    /** [price] is the probability the parlay wins that we quote, [maxWager] the stake we cover, [fair] the parlay's fair probability. */
    data class Quote(val price: Double, val maxWager: Double, val fair: Double, val legs: List<LegFair>, val rulePrice: Double = price, val modelPrice: Double? = null, val via: String = "", val livePrice: Double? = null) : RfqDecision {
        /** Our EV per $1 of the pot if the fair is true: what winning the auction is worth. */
        val edge: Double get() = price - fair
    }
    /** [fair] is known when every leg priced and only a rule said no (the shadow journal still wants it). */
    data class Decline(val reason: String, val fair: Double? = null, val legs: List<LegFair> = emptyList()) : RfqDecision
}

/**
 * The maker's whole pricing rule, pure (tested in RfqPricerTest). It answers only what it can price with the same fair lines the auto-bet trusts, and declines everything else:
 * every leg priced from a fresh line behind enough companies, pregame and not about to start, no two legs in one game (their correlation is unknown: Novig allows the combination, the books charge
 * for it, and an independent product would misprice it), a probability inside the band, and a stake the caps allow and the minimum stake does not exceed.
 */
/** The sports day (MK17): the Tracker's slate, 5 am to 5 am Eastern. */
object RfqSlate {
    private val ET = java.time.ZoneId.of("America/New_York")

    /** The end (ms) of the sports day [now] is in: the next 5:00 am Eastern. */
    fun endMs(now: Long): Long {
        val t = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ET)
        var end = t.toLocalDate().atTime(5, 0).atZone(ET)
        if (!t.isBefore(end)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }
}

object RfqPricer {
    /** PF1: the most legs of a declined (over the limit) parlay that are still priced for the journal; longer ones decline unpriced. */
    const val SHADOW_LEGS = 12

    /** The market types whose fair the maker trusts (the Novig types the first shadow day measured at 0.94 points over our fair: moneyline, spread, total, team total, first-half versions, three-way moneyline). */
    val GAME_LINES = setOf("MONEY", "SPREAD", "TOTAL", "TEAM_TOTAL", "MONEY_1H", "SPREAD_1H", "TOTAL_1H", "MONEYLINE_3_WAY_WIN")
    /**
     * The price (the probability the parlay wins) we quote for a parlay of [n] legs whose fair probability is [fair] and whose last leg starts [leadMs] from now: the highest of the relative floor, the
     * return the collateral must earn (longer lock-up, higher return) and the points floor for [n] legs. Rounded UP to a THOUSANDTH, Novig's price grid ([up]): rounding down would give away part of the margin.
     */
    /** [x] rounded UP to the thousandth (Novig fills at a three-decimal price); the 1e-7 keeps 0.2 x 1.08 = 0.21600000000000003 at 0.216. */
    fun up(x: Double): Double = ceil(x * 1_000.0 - 1e-7) / 1_000.0

    fun quotePrice(fair: Double, n: Int, leadMs: Long, cfg: RfqConfig): Double {
        val days = leadMs.coerceAtLeast(0L) / 86_400_000.0 + 0.25
        val r = cfg.minReturn * maxOf(cfg.holdFloorDays, days)
        val byReturn = (fair + r) / (1.0 + r)
        val byPoints = fair + cfg.minPoints * (n - 1).coerceAtLeast(1)
        val byMargin = fair * (1.0 + cfg.margin)
        return up(maxOf(byReturn, byPoints, byMargin))
    }

    /**
     * The fair probability that every leg of [group] (two or three legs of ONE game) wins, with the model's error premium on the dependence, or null when a leg is not a modelled game line (a player prop,
     * an unknown market) or [RfqConfig.sameGamePremium] is negative (the switch off).
     */
    fun sameGameJoint(group: List<LegFair>, cfg: RfqConfig): Double? {
        if (cfg.sameGamePremium < 0.0 || group.size !in 2..SameGame.MAX_LEGS) return null
        val legs = group.map { l ->
            val kind = l.kind ?: return null
            val side = l.side ?: return null
            if (!SameGame.modelled(kind, l.period) || l.marketType == "MONEYLINE_3_WAY_WIN" || (l.marketType !in GAME_LINES && !l.marketType.endsWith("_1H"))) return null
            GameLeg(kind, side, l.awayTeam, l.period, l.probability, l.homeLine)
        }
        val sport = SameGame.SPORTS[group[0].league] ?: SameGame.DEFAULT
        val j = SameGame.joint(legs, sport) ?: return null
        return minOf(0.999, j * (1.0 + cfg.sameGamePremium))
    }

    /**
     * [cashRoom]: the dollars of the maker's subaccount balance not already promised (its cash less the collateral of quotes still out, less [RfqConfig.cashReserve]), or null when not known (SHADOW). Novig passes over
     * a quote whose collateral for the FULL `maxWager` the balance cannot cover (`quote_dropped unbacked`, docs api/rfq/overview) and fails a won quote it cannot fund at the fill (`quote_unfilled maker_insolvent`,
     * which stops the maker for 30 minutes): a quote is never sized beyond the cash that is there.
     */
    fun decide(created: RfqProtocol.Created, now: Long, cfg: RfqConfig, fairs: LegFairs, openCollateral: Double, market: RfqMarketModel? = null, cashRoom: Double? = null, walletTotal: Double? = null): RfqDecision {
        val ids = created.outcomeIds
        // PF1: a parlay over the leg limit is still PRICED (the plain product of its legs, in the decline) up to SHADOW_LEGS legs, so the journal keeps our fair beside the market's executed price for the classes the
        // profit profile does not quote, and the graded study (`tools/rfq_pnl_study.py` needs a CREATED row with a fair) can show later whether one of them has become worth quoting.
        if (ids.size < 2 || ids.size > maxOf(cfg.maxLegs, SHADOW_LEGS)) return RfqDecision.Decline("${ids.size} legs (this maker prices 2 to ${cfg.maxLegs})")
        val legs = ArrayList<LegFair>(ids.size)
        for (id in ids) legs += fairs.leg(id) ?: return RfqDecision.Decline("a leg has no fair line here")
        if (cfg.legCalibration) for (i in legs.indices) legs[i] = legs[i].copy(probability = RfqLegCalibration.adjust(legs[i].probability))
        // Without a fair in the decline: these trades must not count in the evidence of what the market pays over OUR fair (the fair is the thing in doubt).
        val propLegs = legs.count { it.marketType !in GAME_LINES }
        // More than two prop legs (MK24, Tj 2026-11-20: 24% of RFQs): never quoted, but PRICED at the plain product (no premium) so the journal carries our fair and the market's executed prices show what such a parlay is worth;
        // `propLegsShadowMax` is the most prop legs priced that way (0 = they decline unpriced, as before).
        val manyProps = !cfg.allowProps && cfg.propPremium > 0.0 && propLegs > cfg.maxPropLegs && propLegs <= cfg.propLegsShadowMax && legs.none { it.marketType.isBlank() }
        val premiumProps = !cfg.allowProps && propLegs > 0 && !manyProps
        if (!manyProps && premiumProps && (cfg.propPremium <= 0.0 || propLegs > cfg.maxPropLegs || legs.any { it.marketType.isBlank() })) return RfqDecision.Decline("a leg is not a game line (our prop fair runs high)")
        if (!manyProps && !cfg.allowProps && propLegs > cfg.maxPropLegs && !premiumProps) return RfqDecision.Decline("a leg is not a game line (our prop fair runs high)")
        var p = 1.0
        for (l in legs) p *= l.probability
        if (premiumProps) p = minOf(0.999, p * Math.pow(1.0 + cfg.propPremium, propLegs.toDouble()))
        val extrapolatedGameLegs = legs.count { it.extrapolated && it.marketType in GAME_LINES }
        if (extrapolatedGameLegs > 0 && cfg.extrapolatedLegPremium > 0.0) p = minOf(0.999, p * Math.pow(1.0 + cfg.extrapolatedLegPremium, extrapolatedGameLegs.toDouble()))
        fun no(why: String) = RfqDecision.Decline(why, p, legs)
        if (ids.size > cfg.maxLegs) return no("${ids.size} legs (this maker prices 2 to ${cfg.maxLegs}; priced at the plain product for the journal)")
        if (cfg.allHighMaxLegs > 0 && legs.size > cfg.allHighMaxLegs && legs.all { it.probability >= cfg.allHighLegFloor }) return no("${legs.size} legs that are all favourites (at least ${"%.0f".format(cfg.allHighLegFloor * 100)}%): makers lose on these (graded study)")
        var via = ""
        var pairModelUsed = false
        if (legs.map { it.eventId }.toSet().size != legs.size) {
            // Legs of one game are not independent: price each such group through the copula (game lines only), the groups of different games still multiply. A group the model cannot price declines the RFQ,
            // with the PURE independent product as its fair (the evidence of what the market charges over it), unless the market's own ratio for the class prices it (props of one game, MK12).
            var joint = 1.0
            var unmodelled = false
            var byPairModel = false
            val groups = legs.groupBy { it.eventId }
            for ((_, group) in groups) {
                if (group.size == 1) { joint *= group[0].probability; continue }
                var g = sameGameJoint(group, cfg)
                if (g == null && cfg.propPairs) { g = PropPairs.joint(group); if (g != null) byPairModel = true }
                if (g == null) { unmodelled = true; break }
                joint *= g
            }
            if (unmodelled) {
                val independent = legs.fold(1.0) { a, l -> a * l.probability }
                val ratio = if (cfg.propPairsFromMarket && ids.size <= 3 && groups.values.count { it.size > 1 } == 1) market?.medianRatio(RfqMarketModel.classOf(legs, independent), cfg.probeMinSamples) else null
                if (ratio == null) return RfqDecision.Decline("two legs in one game (correlation unmodelled)", independent, legs)
                p = minOf(0.999, independent * ratio.coerceIn(0.5, 4.0)); via = "market ratio %.2f".format(ratio)
            } else {
                if (premiumProps) joint *= Math.pow(1.0 + cfg.propPremium, propLegs.toDouble())
                p = minOf(0.999, joint)
                pairModelUsed = byPairModel
                if (byPairModel && !cfg.propPairsLive) return RfqDecision.Decline("two player props of one game (modelled, shadow: not quoted)", p, legs)
            }
        }
        val anyLive = legs.any { it.live }
        for (l in legs) {
            if (l.live) {
                // A leg in play is priced only from Novig's live book ([LiveFair], MK31), only when the switch is on, only for a game line, and only while its price is fresh.
                if (cfg.liveParlays == RfqConfig.Mode.OFF || l.source != LiveFair.SOURCE) return no("a leg is in play")
                if (l.marketType !in GAME_LINES) return no("a prop leg is in play")
                val liveAsOf = l.asOfMs ?: return no("a live leg's price has no age")
                if (now - liveAsOf > cfg.liveMaxAgeMs) return no("a live leg's price is ${(now - liveAsOf) / 1000L} s old")
                if (l.probability > cfg.liveMaxLegFair) return no("a leg is too likely to win (${"%.0f".format(l.probability * 100)}% against the ${"%.0f".format(cfg.liveMaxLegFair * 100)}% cap)")
                continue
            }
            if (l.startsMs - now < cfg.minLeadMs) return no("a leg starts within ${cfg.minLeadMs / 60_000L} min")
            if (l.startsMs - now > cfg.maxDaysAhead * 86_400_000L) return no("a leg is more than ${cfg.maxDaysAhead} days away")
            if (cfg.maxLeadMs > 0L && l.startsMs - now > cfg.maxLeadMs) return no("a leg starts more than ${"%.0f".format(cfg.maxLeadMs / 3_600_000.0)} h away (the collateral would be locked too long for what it earns)")
            val needBooks = if (l.marketType in GAME_LINES) minOf(cfg.minBooks, cfg.minBooksGameLines) else cfg.minBooks
            if (l.books < needBooks) return no("a leg's fair rests on ${l.books} companies (need $needBooks)")
            val asOf = l.asOfMs ?: return no("a leg's fair has no age")
            if (now - asOf > cfg.maxFairAgeMs) return no("a leg's fair is ${(now - asOf) / 60_000L} min old")
            if (l.probability > cfg.maxLegFair) return no("a leg is too likely to win (${"%.0f".format(l.probability * 100)}% against the ${"%.0f".format(cfg.maxLegFair * 100)}% cap)")
        }
        if (manyProps) return no("$propLegs prop legs (priced at the plain product, shadow: not quoted)")
        if (!anyLive && cfg.manyLegsNeedLongshot > 0.0 && ids.size >= 3 && legs.none { it.probability < cfg.manyLegsNeedLongshot }) return no("${ids.size} legs and every leg a favourite (makers lose about 20% of the pot on these)")
        if (anyLive) {
            if (p > cfg.liveMaxFair) return no("an in-play parlay: wins ${"%.0f".format(p * 100)}% of the time, over the ${"%.0f".format(cfg.liveMaxFair * 100)}% live cap")
            if (cfg.liveParlays == RfqConfig.Mode.SHADOW || cfg.mode != RfqConfig.Mode.LIVE) return no("an in-play parlay (priced from Novig's live book, shadow: not quoted)")
        }
        if (cfg.longshotsOnly && p > cfg.longshotMaxChance) return no("longshots only: the parlay wins ${"%.0f".format(p * 100)}% of the time, over the ${"%.0f".format(cfg.longshotMaxChance * 100)}% limit")
        if (p > cfg.maxFair) return no("the parlay is too likely to win (${"%.0f".format(p * 100)}% against the ${"%.0f".format(cfg.maxFair * 100)}% cap)")
        if (cfg.onlyToday) {
            val end = RfqSlate.endMs(now)
            val late = legs.maxOf { it.startsMs }
            if (late > end) return no("its last leg starts after today's slate ends (${(late - end) / 3_600_000L} h later)")
        }
        if (p < cfg.minFair) return no("the parlay is too unlikely to win (${"%.1f".format(p * 100)}% against the ${"%.0f".format(cfg.minFair * 100)}% floor)")
        if (cfg.longshotsOnly && cfg.longshotPremium > 0.0) p = minOf(0.999, p * (1.0 + cfg.longshotPremium))
        val leadMs = legs.maxOf { it.startsMs } - now
        // LIVE quotes demand the capital hurdle; SHADOW quotes at the looser rule (paper evidence) and records what the hurdle would have asked.
        val sameDay = if (cfg.onlyToday) cfg.copy(holdFloorDays = minOf(cfg.holdFloorDays, 0.25)) else cfg
        val hurdleCfg = if (cfg.liveMinReturn > cfg.minReturn) sameDay.copy(minReturn = cfg.liveMinReturn) else sameDay
        val effCfg = if (cfg.mode == RfqConfig.Mode.LIVE) hurdleCfg else sameDay
        // The safe-edge floor (MK17, "truly positive EV"): a real quote asks at least one standard error of the fair on top of it (relative error x sqrt(legs), the market model's own floor): the rule's 1.5% margin
        // is under that for any parlay of two legs or more (2% x sqrt(2) = 2.8%), and the trades we win are the ones where our fair is lowest against the market's (the winner's curse).
        val safe = up(p * (1.0 + cfg.fairErrorRel * Math.sqrt(ids.size.toDouble())))
        val rulePrice = if (cfg.mode == RfqConfig.Mode.LIVE) maxOf(quotePrice(p, ids.size, leadMs, effCfg), safe) else quotePrice(p, ids.size, leadMs, effCfg)
        val livePrice = if (cfg.mode != RfqConfig.Mode.LIVE) maxOf(quotePrice(p, ids.size, leadMs, hurdleCfg), safe).takeIf { it != rulePrice } else null
        // The market model's price (MK12): the margin that maximises win share x net edge on what the collateral costs, from the probes' answers for this class; computed whenever a model exists so SHADOW journals both,
        // used only when the config says so and never below the rule's floors.
        val errFloor = maxOf(p * cfg.margin, p * cfg.fairErrorRel * Math.sqrt(ids.size.toDouble()))
        val modelPrice = market?.best(RfqMarketModel.classOf(legs, p), p, ids.size, leadMs.coerceAtLeast(0L) / 86_400_000.0, errFloor, 0.5 * errFloor, effCfg)
            ?.let { up(p + it) }
        // With `marketPricing` the model's price is the quote (it never goes under its own floor: relative error x sqrt(legs)); without, the rule's.
        val basePrice = if (cfg.marketPricing && modelPrice != null) modelPrice else rulePrice
        // An in-play parlay asks more than the usual floors: the game moves between the auction and the buyer's accept (6-29 s), and the buyer sees the score.
        val price = if (anyLive) maxOf(basePrice, up(p * (1.0 + cfg.livePremium)), up(p + cfg.livePointsPerLeg * ids.size)) else basePrice
        if (price > 0.999 || price < 0.001) return no("price ${"%.4f".format(price)} is outside Novig's 0.001-0.999")
        // The RFQ's minimum is the stake the user asked for (96% are the default $10; executed stakes sit just under it, the taker fee). The quote covers exactly that: more collateral than the trade needs
        // would only be tied up. The maker's collateral for a stake w is w (1 - P) / P.
        val want = created.minWager.coerceAtLeast(10.0)
        val perStake = (1.0 - price) / price
        val wallet = if (cfg.walletCollateral) walletTotal else null
        val byParlay = (if (wallet != null) cfg.walletShare * wallet else cfg.maxCollateral) / perStake
        val byOpen = if (wallet != null) Double.MAX_VALUE else (cfg.maxOpenCollateral - openCollateral) / perStake      // the wallet's cash (byCash) is the open limit then
        // A prop-pair quote (a class that almost never trades) leaves [RfqConfig.propPairsCashReserve] of the free cash to the classes that do (MK38).
        val reserve = maxOf(if (pairModelUsed) cfg.propPairsCashReserve else 0.0, if (propLegs > 0) cfg.propCashReserve else 0.0).coerceAtLeast(0.0)
        val byCash = cashRoom?.let { (it - reserve).coerceAtLeast(0.0) / perStake } ?: Double.MAX_VALUE
        val byLive = if (anyLive && cfg.liveMaxCollateral > 0.0) cfg.liveMaxCollateral / perStake else Double.MAX_VALUE
        val room = floor(minOf(cfg.maxStake, byParlay, byOpen, byCash, byLive) * 100.0) / 100.0
        if (room < want) return no(if (byCash < minOf(cfg.maxStake, byParlay, byOpen) && byCash < want) "the balance covers a $${"%.2f".format(room)} stake, under the $${"%.2f".format(want)} asked (cash is short)" else "the caps allow a $${"%.2f".format(room)} stake, under the $${"%.2f".format(want)} asked")
        val stake = ceil(want * 100.0 - 1e-7) / 100.0
        return RfqDecision.Quote(price, stake, p, legs, rulePrice, modelPrice, via, livePrice)
    }
}
