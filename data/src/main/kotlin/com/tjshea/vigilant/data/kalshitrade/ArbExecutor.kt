package com.tjshea.vigilant.data.kalshitrade

import java.util.Locale
import kotlin.math.floor

/** What one leg of an arbitrage did. [payout] is the dollars of payout it bought (contracts it filled x $1). */
sealed interface LegResult {
    data class Filled(val payout: Double, val cost: Double) : LegResult

    /** Nothing was bought and no money moved. */
    data class None(val reason: String) : LegResult

    /** The order was sent and its answer never came back: the state is unknown; nothing more is sent. */
    data class Unknown(val message: String) : LegResult
}

/** How an attempt at an arbitrage ended. */
sealed interface ArbOutcome {
    /** Both legs filled for [payout] dollars of payout: [profit] is guaranteed (before nothing else can change it). */
    data class Done(val payout: Double, val cost: Double, val profit: Double) : ArbOutcome

    /** Not tried: the prices no longer made a profit, or a check said no. */
    data class Skipped(val reason: String) : ArbOutcome

    /** One leg filled and the other did not: [payout] dollars of payout are held on [venue] alone. */
    data class Unhedged(val venue: String, val payout: Double, val cost: Double, val reason: String) : ArbOutcome

    /** A leg's answer was lost: stop, check both accounts. */
    data class Unknown(val message: String) : ArbOutcome
}

/**
 * Places the two legs of an arbitrage (KV5b), one venue after the other, each only while the pair still makes a profit at the prices read just before it. The order matters:
 * Novig first (its order takes a few seconds to land, and nothing is open while it does), then Kalshi sized to exactly what Novig filled (its answer is immediate, so the time one
 * leg is held alone is as short as it can be). Each leg is a call into the venue's own placer (every guard it has still applies), handed the break-even price as its ceiling.
 * If Kalshi will not fill what Novig did, the rest is tried once more at the break-even, and anything still unhedged is reported, never hidden.
 *
 * The venues are handed in: [refresh] re-reads both venues' prices for the pair (null when either is gone), [novig] buys [payout] dollars of payout at no more than [maxCostPerDollar] per
 * dollar of payout, [kalshi] buys exactly that many contracts at a price at or under [maxPrice].
 */
class ArbExecutor(
    private val refresh: suspend (ArbOpportunity) -> ArbOpportunity?,
    private val novig: suspend (ArbOpportunity, payout: Double, maxCostPerDollar: Double) -> LegResult,
    private val kalshi: suspend (ArbOpportunity, contracts: Long, maxPrice: Double) -> LegResult,
) {
    /**
     * Tries [opp] for up to [maxPayout] dollars of payout in whole dollars, at least [settings]' profit. A clip is [clipPayout] at most (the biggest amount ever held on one venue alone
     * while the other leg is placed).
     */
    suspend fun run(opp: ArbOpportunity, maxPayout: Double, clipPayout: Double, settings: ArbSettings): ArbOutcome {
        val fresh = refresh(opp) ?: return ArbOutcome.Skipped("one of the two prices could not be read just now")
        if (fresh.profitPerDollar < settings.minProfit - 1e-9) {
            return ArbOutcome.Skipped("the prices moved: the pair now makes ${percent(fresh.profitPerDollar)} per $1, under your ${percent(settings.minProfit)} minimum")
        }
        val payout = floor(minOf(maxPayout, clipPayout, fresh.maxPayout) + 1e-9)
        if (payout < 1.0) return ArbOutcome.Skipped("under one Kalshi contract fits both venues' sizes")
        // Novig may pay at most what keeps the minimum profit with Kalshi's price as it is now.
        val maxNovigCost = 1.0 - fresh.kalshi.cost - settings.minProfit
        val first = novig(fresh, payout, maxNovigCost)
        when (first) {
            is LegResult.None -> return ArbOutcome.Skipped("Novig: ${first.reason}")
            is LegResult.Unknown -> return ArbOutcome.Unknown("Novig's order is unconfirmed (${first.message}); nothing was sent to Kalshi")
            is LegResult.Filled -> Unit
        }
        val have = floor(first.payout + 1e-9)
        if (have < 1.0) return ArbOutcome.Unhedged("Novig", first.payout, first.cost, "Novig filled under one Kalshi contract's worth, which Kalshi cannot sell as a hedge")
        // Kalshi's ceiling: the price at which the pair still makes more than nothing, from Novig's real cost.
        val novigCostPerDollar = first.cost / first.payout
        val breakEven = 1.0 - novigCostPerDollar
        val price = breakEven - KalshiFees.takerPerContract(fresh.kalshi.price)
        var k = kalshi(fresh, have.toLong(), maxOf(price, 0.0).coerceAtMost(fresh.kalshi.price + MOVE_ALLOWED))
        var bought = (k as? LegResult.Filled)?.payout ?: 0.0
        var kalshiCost = (k as? LegResult.Filled)?.cost ?: 0.0
        if (k is LegResult.Unknown) return ArbOutcome.Unknown("Kalshi's order is unconfirmed (${k.message}) after Novig filled ${money(first.payout)} of payout: check both accounts")
        if (bought < have - 1e-9) {
            // Once more for what is left, at the break-even.
            val left = floor(have - bought + 1e-9).toLong()
            if (left >= 1L) {
                val again = refresh(fresh)
                val ceiling = again?.let { 1.0 - novigCostPerDollar - KalshiFees.takerPerContract(it.kalshi.price) } ?: price
                val k2 = kalshi(again ?: fresh, left, maxOf(ceiling, 0.0))
                if (k2 is LegResult.Unknown) return ArbOutcome.Unknown("Kalshi's order is unconfirmed (${k2.message}) after Novig filled ${money(first.payout)} of payout: check both accounts")
                if (k2 is LegResult.Filled) { bought += k2.payout; kalshiCost += k2.cost }
                k = k2
            }
        }
        if (bought < 1e-9) {
            val why = (k as? LegResult.None)?.reason ?: "no fill"
            return ArbOutcome.Unhedged("Novig", first.payout, first.cost, "Kalshi did not fill ($why)")
        }
        if (bought < first.payout - 1e-9) {
            val held = first.payout - bought
            return ArbOutcome.Unhedged("Novig", held, first.cost * (held / first.payout), "Kalshi filled ${money(bought)} of ${money(first.payout)}")
        }
        val cost = first.cost + kalshiCost
        return ArbOutcome.Done(bought, cost, bought - cost)
    }

    private fun percent(v: Double) = String.format(Locale.US, "%+.2f%%", v * 100)
    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    companion object {
        /** Kalshi's second leg may pay up to a cent more than the pair was found at if the pair still profits. */
        const val MOVE_ALLOWED = 0.01
    }
}
