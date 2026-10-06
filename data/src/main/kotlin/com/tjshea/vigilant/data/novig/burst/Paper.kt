package com.tjshea.vigilant.data.novig.burst

import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Fees

/** What a taker who acted on a window after its own delay would have got, on paper. */
enum class PaperOutcome { BOTH, ONE_LEG, MISSED }

/**
 * [pnl] is dollars: both legs filled = the cover's profit after fees; one leg alone = a loss (its fee and [PaperTrader.NAKED_PENALTY]); none = 0. [contracts] is what
 * filled on the thinner leg (both) or on the leg that did (one).
 */
data class PaperFill(val outcome: PaperOutcome, val contracts: Long, val pnl: Double)

/**
 * The paper trade of the burst recorder (RESEARCH.md §95). Nothing is sent: at the moment an order of Tj's would have reached Novig (the window's first sight plus his
 * measured delay) the books are looked at again, and each leg is filled only at the price it was seen at or better (an `IOC` order at the seen ask), up to what is on
 * offer there and Tj's own per-bet limit. Both legs filled = the cover's profit; one alone = a naked bet, charged its fee and a flat [NAKED_PENALTY] (the legs are
 * independent fills, RESEARCH.md §83.2); none = nothing. Conservative on purpose: only the best price level counts, the chance the margin lands between the lines
 * (a $2 payout) is ignored, and a leg that fills alone is assumed to lose its fee and the penalty although the stale leg alone is a bet below its fair price.
 */
object PaperTrader {
    /** What a leg that filled without its partner is charged per $1 of payout, beyond its fee: about half a spread (the median spread is 4¢) less what a stale leg is worth. */
    const val NAKED_PENALTY = 0.02

    /**
     * [open] is the cover as it was first seen; [yesNow] and [noNow] are the two legs' offers when the order would have arrived (null: nothing on offer).
     * [capDollars] is Tj's most for one bet (0 = no limit): each leg is its own bet.
     */
    fun trade(open: Cover, yesNow: Leg?, noNow: Leg?, capDollars: Double): PaperFill {
        val yesOk = yesNow != null && yesNow.price <= open.yes.price + EPS
        val noOk = noNow != null && noNow.price <= open.no.price + EPS
        return when {
            yesOk && noOk -> {
                val y = yesNow!!
                val n = noNow!!
                val q = minOf(y.contracts, n.contracts, capContracts(y.price, capDollars), capContracts(n.price, capDollars))
                if (q <= 0L) return PaperFill(PaperOutcome.MISSED, 0L, 0.0)
                PaperFill(PaperOutcome.BOTH, q, Cover(open.lo, open.hi, y, n).net * q * EvMath.CONTRACT_PAYOUT_DOLLARS)
            }
            yesOk || noOk -> {
                val leg = if (yesOk) yesNow!! else noNow!!
                val line = if (yesOk) open.lo else open.hi
                val q = minOf(leg.contracts, capContracts(leg.price, capDollars))
                if (q <= 0L) return PaperFill(PaperOutcome.MISSED, 0L, 0.0)
                val fee = Fees.takerFee(leg.price, line.fee, eventLive = true)
                PaperFill(PaperOutcome.ONE_LEG, q, -(fee + NAKED_PENALTY) * q * EvMath.CONTRACT_PAYOUT_DOLLARS)
            }
            else -> PaperFill(PaperOutcome.MISSED, 0L, 0.0)
        }
    }

    private fun capContracts(price: Double, capDollars: Double): Long =
        if (capDollars <= 0.0 || price <= 0.0) Long.MAX_VALUE else Math.floor(capDollars / (price * EvMath.CONTRACT_PAYOUT_DOLLARS)).toLong()

    private const val EPS = 1e-9
}

/** One delay Tj's order would have: [totalMs] from the moment the recorder sees a window until the order has reached Novig and its answer is on the books we watch. */
data class Latency(val name: String, val totalMs: Long)

/**
 * Tj's own delays, measured on his phone with his key (RESEARCH.md §95): the signed round trip to Novig (`POST /v3/echo`, free, signed like an order) and how late a
 * book push arrives (a fill's removal from the book against the same trade's engine time on the public trades route). Until there are [MIN_SAMPLES] of a kind the
 * default stands, and the report says so.
 *
 * An order sent at the moment a window is seen reaches Novig half a round trip later (plus [SIGN_MS] to sign it); the books we then SEE hold that moment's state a
 * push delay after it, so a window counts as caught when the cover still pays [rtt/2 + SIGN_MS + push] after it was first seen.
 */
class LatencyModel {
    private val rtt = ArrayList<Long>()
    private val push = ArrayList<Long>()

    @Synchronized
    fun addRoundTrip(ms: Long) { if (ms in 1..MAX_SAMPLE_MS) rtt.add(ms) }

    @Synchronized
    fun addPushDelay(ms: Long) { if (ms in 0..MAX_SAMPLE_MS) push.add(ms) }

    @Synchronized
    fun roundTrips(): Int = rtt.size

    @Synchronized
    fun pushDelays(): Int = push.size

    /** The profiles the paper trade runs at: no delay (what a perfect taker sees), the typical (median) delay and the slow (95th percentile) one. */
    @Synchronized
    fun profiles(): List<Latency> {
        val r50 = pick(rtt, 0.5, DEFAULT_RTT_MS, MIN_SAMPLES)
        val r95 = pick(rtt, 0.95, DEFAULT_RTT_MS * 2, MIN_SAMPLES)
        val p50 = pick(push, 0.5, DEFAULT_PUSH_MS, MIN_SAMPLES)
        val p95 = pick(push, 0.95, DEFAULT_PUSH_MS * 2, MIN_SAMPLES)
        return listOf(Latency(OPTIMISTIC, 0L), Latency(TYPICAL, r50 / 2 + SIGN_MS + p50), Latency(SLOW, r95 / 2 + SIGN_MS + p95))
    }

    /** What the report says about where the numbers came from. */
    @Synchronized
    fun note(): String =
        "round trip: ${if (rtt.size >= MIN_SAMPLES) "measured, ${rtt.size} signed echoes, median ${pick(rtt, 0.5, 0, 1)} ms, 95th ${pick(rtt, 0.95, 0, 1)} ms" else "ASSUMED $DEFAULT_RTT_MS ms (${rtt.size} measured so far)"}; " +
            "push delay: ${if (push.size >= MIN_SAMPLES) "measured, ${push.size} fills, median ${pick(push, 0.5, 0, 1)} ms, 95th ${pick(push, 0.95, 0, 1)} ms" else "ASSUMED $DEFAULT_PUSH_MS ms (${push.size} measured so far)"}"

    private fun pick(xs: List<Long>, q: Double, default: Long, min: Int): Long =
        if (xs.size < min) default else xs.sorted().let { it[minOf(it.size - 1, (q * it.size).toInt())] }

    companion object {
        const val OPTIMISTIC = "no delay"
        const val TYPICAL = "typical delay"
        const val SLOW = "slow delay"

        /** Until measured: a mobile round trip to Novig (Tj's v0.60.0 file: `/v3/orders` averaged 121 ms) and a push 150 ms behind the engine. */
        const val DEFAULT_RTT_MS = 150L
        const val DEFAULT_PUSH_MS = 150L

        /** Signing an order and handing it to the network. */
        const val SIGN_MS = 15L
        const val MIN_SAMPLES = 20
        const val MAX_SAMPLE_MS = 10_000L
    }
}
