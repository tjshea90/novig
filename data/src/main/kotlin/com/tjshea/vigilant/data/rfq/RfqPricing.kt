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
    /** The quote's margin over the parlay's fair probability, relative: price = fair x (1 + this). The maker's EV per $1 of pot is price - fair. Wide to start (others quoted 4-8% over fair, 2026-11-17). */
    val margin: Double = 0.08,
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
    val maxLegs: Int = 3,
    /** Legs must start at least this far ahead (ms) and no further than [maxDaysAhead]. */
    val minLeadMs: Long = 20 * 60_000L,
    val maxDaysAhead: Int = 5,
    val minFair: Double = 0.03,
    val maxFair: Double = 0.85,
    /** The day's loss at which quoting stops (dollars; informational for now, the kill is [Kill]). */
    val dayLossStop: Double = 40.0,
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
    fun decide(created: RfqProtocol.Created, now: Long, cfg: RfqConfig, fairs: LegFairs, openCollateral: Double): RfqDecision {
        val ids = created.outcomeIds
        if (ids.size < 2 || ids.size > cfg.maxLegs) return RfqDecision.Decline("${ids.size} legs (this maker prices 2 to ${cfg.maxLegs})")
        val legs = ArrayList<LegFair>(ids.size)
        for (id in ids) legs += fairs.leg(id) ?: return RfqDecision.Decline("a leg has no fair line here")
        var p = 1.0
        for (l in legs) p *= l.probability
        fun no(why: String) = RfqDecision.Decline(why, p, legs)
        if (legs.map { it.eventId }.toSet().size != legs.size) return no("two legs in one game (correlation unmodelled)")
        for (l in legs) {
            if (l.live) return no("a leg is in play")
            if (l.startsMs - now < cfg.minLeadMs) return no("a leg starts within ${cfg.minLeadMs / 60_000L} min")
            if (l.startsMs - now > cfg.maxDaysAhead * 86_400_000L) return no("a leg is more than ${cfg.maxDaysAhead} days away")
            if (l.books < cfg.minBooks) return no("a leg's fair rests on ${l.books} companies (need ${cfg.minBooks})")
            val asOf = l.asOfMs ?: return no("a leg's fair has no age")
            if (now - asOf > cfg.maxFairAgeMs) return no("a leg's fair is ${(now - asOf) / 60_000L} min old")
        }
        if (p < cfg.minFair || p > cfg.maxFair) return no("the parlay's fair probability ${"%.3f".format(p)} is outside ${cfg.minFair}-${cfg.maxFair}")
        // Rounded UP to a ten-thousandth: rounding down would give away part of the margin.
        val price = ceil(p * (1.0 + cfg.margin) * 10_000.0) / 10_000.0
        if (price > 0.999 || price < 0.001) return no("price ${"%.4f".format(price)} is outside Novig's 0.001-0.999")
        // The maker's collateral for a stake w is w (1 - P) / P.
        val perStake = (1.0 - price) / price
        val byParlay = cfg.maxCollateral / perStake
        val byOpen = (cfg.maxOpenCollateral - openCollateral) / perStake
        val stake = floor(minOf(cfg.maxStake, byParlay, byOpen) * 100.0) / 100.0
        if (stake < created.minWager.coerceAtLeast(10.0)) return no("the caps allow a $${"%.2f".format(stake)} stake, under the minimum $${"%.2f".format(created.minWager.coerceAtLeast(10.0))}")
        return RfqDecision.Quote(price, stake, p, legs)
    }
}
