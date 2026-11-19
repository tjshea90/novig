package com.tjshea.vigilant.data.rfq

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
     * Same-game parlays of exactly two legs, a SIDE (spread or moneyline) and the game TOTAL (Tj, 2026-11-19: "do the two leg parlays, price them at a premium for correlation"). The legs' joint probability is the
     * product of their fairs raised by this much (relative), on top of the usual margin. Measured on 18,063 college games' closing lines, a side and the total are within 2.4% of independent either way
     * (standard error 1.3%), while Novig's market priced such trades 13% over the independent price (9 of them), so 8% keeps a cushion on both. 0 = decline every same-game parlay (the old rule).
     */
    val sameGamePremium: Double = 0.08,
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
)

fun interface LegFairs { fun leg(outcomeId: String): LegFair? }

sealed interface RfqDecision {
    /** [price] is the probability the parlay wins that we quote, [maxWager] the stake we cover, [fair] the parlay's fair probability. */
    data class Quote(val price: Double, val maxWager: Double, val fair: Double, val legs: List<LegFair>) : RfqDecision {
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

    fun decide(created: RfqProtocol.Created, now: Long, cfg: RfqConfig, fairs: LegFairs, openCollateral: Double): RfqDecision {
        val ids = created.outcomeIds
        if (ids.size < 2 || ids.size > cfg.maxLegs) return RfqDecision.Decline("${ids.size} legs (this maker prices 2 to ${cfg.maxLegs})")
        val legs = ArrayList<LegFair>(ids.size)
        for (id in ids) legs += fairs.leg(id) ?: return RfqDecision.Decline("a leg has no fair line here")
        // Without a fair in the decline: these trades must not count in the evidence of what the market pays over OUR fair (the fair is the thing in doubt).
        val propLegs = legs.count { it.marketType !in GAME_LINES }
        val premiumProps = !cfg.allowProps && propLegs > 0
        if (premiumProps && (cfg.propPremium <= 0.0 || propLegs > 2)) return RfqDecision.Decline("a leg is not a game line (our prop fair runs high)")
        var p = 1.0
        for (l in legs) p *= l.probability
        if (premiumProps) p = minOf(0.999, p * Math.pow(1.0 + cfg.propPremium, propLegs.toDouble()))
        fun no(why: String) = RfqDecision.Decline(why, p, legs)
        if (legs.map { it.eventId }.toSet().size != legs.size) {
            val sideAndTotal = legs.size == 2 && cfg.sameGamePremium > 0.0 && legs[0].eventId == legs[1].eventId &&
                legs.count { it.marketType == "TOTAL" } == 1 && legs.count { it.marketType == "SPREAD" || it.marketType == "MONEY" } == 1
            if (!sideAndTotal) return no("two legs in one game (correlation unmodelled)")
            p = minOf(0.999, p * (1.0 + cfg.sameGamePremium))
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
        val price = quotePrice(p, ids.size, legs.maxOf { it.startsMs } - now, cfg)
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
        return RfqDecision.Quote(price, stake, p, legs)
    }
}
