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
    val minFair: Double = 0.03,
    val maxFair: Double = 0.85,
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
)

fun interface LegFairs { fun leg(outcomeId: String): LegFair? }

sealed interface RfqDecision {
    /** [price] is the probability the parlay wins that we quote, [maxWager] the stake we cover, [fair] the parlay's fair probability. */
    data class Quote(val price: Double, val maxWager: Double, val fair: Double, val legs: List<LegFair>, val rulePrice: Double = price, val modelPrice: Double? = null, val via: String = "") : RfqDecision {
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
object RfqPricer {
    /** The market types whose fair the maker trusts (the Novig types the first shadow day measured at 0.94 points over our fair: moneyline, spread, total, team total, first-half versions, three-way moneyline). */
    val GAME_LINES = setOf("MONEY", "SPREAD", "TOTAL", "TEAM_TOTAL", "MONEY_1H", "SPREAD_1H", "TOTAL_1H", "MONEYLINE_3_WAY_WIN")
    /**
     * The price (the probability the parlay wins) we quote for a parlay of [n] legs whose fair probability is [fair] and whose last leg starts [leadMs] from now: the highest of the relative floor, the
     * return the collateral must earn (longer lock-up, higher return) and the points floor for [n] legs. Rounded UP to a ten-thousandth: rounding down would give away part of the margin.
     */
    fun quotePrice(fair: Double, n: Int, leadMs: Long, cfg: RfqConfig): Double {
        val days = leadMs.coerceAtLeast(0L) / 86_400_000.0 + 0.25
        val r = cfg.minReturn * maxOf(1.0, days)
        val byReturn = (fair + r) / (1.0 + r)
        val byPoints = fair + cfg.minPoints * (n - 1).coerceAtLeast(1)
        val byMargin = fair * (1.0 + cfg.margin)
        return ceil(maxOf(byReturn, byPoints, byMargin) * 10_000.0 - 1e-7) / 10_000.0      // the 1e-7 keeps 0.2 x 1.08 = 0.21600000000000003 at 0.2160
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

    fun decide(created: RfqProtocol.Created, now: Long, cfg: RfqConfig, fairs: LegFairs, openCollateral: Double, market: RfqMarketModel? = null): RfqDecision {
        val ids = created.outcomeIds
        if (ids.size < 2 || ids.size > cfg.maxLegs) return RfqDecision.Decline("${ids.size} legs (this maker prices 2 to ${cfg.maxLegs})")
        val legs = ArrayList<LegFair>(ids.size)
        for (id in ids) legs += fairs.leg(id) ?: return RfqDecision.Decline("a leg has no fair line here")
        // Without a fair in the decline: these trades must not count in the evidence of what the market pays over OUR fair (the fair is the thing in doubt).
        val propLegs = legs.count { it.marketType !in GAME_LINES }
        val premiumProps = !cfg.allowProps && propLegs > 0
        if (premiumProps && (cfg.propPremium <= 0.0 || propLegs > 2 || legs.any { it.marketType.isBlank() })) return RfqDecision.Decline("a leg is not a game line (our prop fair runs high)")
        var p = 1.0
        for (l in legs) p *= l.probability
        if (premiumProps) p = minOf(0.999, p * Math.pow(1.0 + cfg.propPremium, propLegs.toDouble()))
        fun no(why: String) = RfqDecision.Decline(why, p, legs)
        var via = ""
        if (legs.map { it.eventId }.toSet().size != legs.size) {
            // Legs of one game are not independent: price each such group through the copula (game lines only), the groups of different games still multiply. A group the model cannot price declines the RFQ,
            // with the PURE independent product as its fair (the evidence of what the market charges over it), unless the market's own ratio for the class prices it (props of one game, MK12).
            var joint = 1.0
            var unmodelled = false
            val groups = legs.groupBy { it.eventId }
            for ((_, group) in groups) {
                if (group.size == 1) { joint *= group[0].probability; continue }
                val g = sameGameJoint(group, cfg)
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
            }
        }
        for (l in legs) {
            if (l.live) return no("a leg is in play")
            if (l.startsMs - now < cfg.minLeadMs) return no("a leg starts within ${cfg.minLeadMs / 60_000L} min")
            if (l.startsMs - now > cfg.maxDaysAhead * 86_400_000L) return no("a leg is more than ${cfg.maxDaysAhead} days away")
            if (l.books < cfg.minBooks) return no("a leg's fair rests on ${l.books} companies (need ${cfg.minBooks})")
            val asOf = l.asOfMs ?: return no("a leg's fair has no age")
            if (now - asOf > cfg.maxFairAgeMs) return no("a leg's fair is ${(now - asOf) / 60_000L} min old")
        }
        if (p < cfg.minFair || p > cfg.maxFair) return no("the parlay's fair probability ${"%.3f".format(p)} is outside ${cfg.minFair}-${cfg.maxFair}")
        val leadMs = legs.maxOf { it.startsMs } - now
        val rulePrice = quotePrice(p, ids.size, leadMs, cfg)
        // The market model's price (MK12): the margin that maximises win share x net edge on what the collateral costs, from the probes' answers for this class; computed whenever a model exists so SHADOW journals both,
        // used only when the config says so and never below the rule's floors.
        val modelPrice = market?.best(RfqMarketModel.classOf(legs, p), p, ids.size, leadMs.coerceAtLeast(0L) / 86_400_000.0, (rulePrice - p).coerceAtLeast(0.0), cfg)
            ?.let { ceil((p + it) * 10_000.0 - 1e-7) / 10_000.0 }
        val price = if (cfg.marketPricing && modelPrice != null) maxOf(rulePrice, modelPrice) else rulePrice
        if (price > 0.999 || price < 0.001) return no("price ${"%.4f".format(price)} is outside Novig's 0.001-0.999")
        // The RFQ's minimum is the stake the user asked for (96% are the default $10; executed stakes sit just under it, the taker fee). The quote covers exactly that: more collateral than the trade needs
        // would only be tied up. The maker's collateral for a stake w is w (1 - P) / P.
        val want = created.minWager.coerceAtLeast(10.0)
        val perStake = (1.0 - price) / price
        val byParlay = cfg.maxCollateral / perStake
        val byOpen = (cfg.maxOpenCollateral - openCollateral) / perStake
        val room = floor(minOf(cfg.maxStake, byParlay, byOpen) * 100.0) / 100.0
        if (room < want) return no("the caps allow a $${"%.2f".format(room)} stake, under the $${"%.2f".format(want)} asked")
        val stake = ceil(want * 100.0 - 1e-7) / 100.0
        return RfqDecision.Quote(price, stake, p, legs, rulePrice, modelPrice, via)
    }
}
