package com.tjshea.vigilant.data.novig.burst

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Fees
import com.tjshea.vigilant.engine.MarketFee

/**
 * The score-burst recorder's view of a game (RESEARCH.md §83-§84, §95): the lines of one game (moneyline, spreads, totals) are separate order books for ONE
 * number, the final margin (or the total). "Margin of the reference team > t" is a contract at every threshold t and can never be worth less at a lower t:
 * buying YES at the lower line and NOT at the higher one pays at least $1 whatever happens (both win when the margin lands between them). After a play the
 * makers re-quote the lines one after another, and for a moment a pair of neighbours costs under $1: a cover that is profitable after Novig's in-play taker fee.
 * Nothing here ever places or cancels an order: it reads books.
 */
enum class LadderKind { MARGIN, TOTAL }

/**
 * One market as a threshold on its ladder: [yesOutcomeId] wins when the number ends above [threshold] (a moneyline is 0, a spread of X −k is k, X +k is −k, a
 * total's Over s is s), [noOutcomeId] otherwise. [ladderKey] is shared by every line of one number of one game ("event|M|reference team", "event|T").
 */
data class LadderLine(
    val marketId: String,
    val eventId: String,
    val kind: LadderKind,
    val ref: String,
    val threshold: Double,
    val yesOutcomeId: String,
    val noOutcomeId: String,
    val label: String,
    val fee: MarketFee,
) {
    val ladderKey: String get() = if (kind == LadderKind.TOTAL) "$eventId|T" else "$eventId|M|$ref"
}

object Ladders {
    private val SPREAD_NAME = Regex("""^(.+?)\s+([+-]?\d+(?:\.\d+)?)$""")

    /** The market types that make a ladder. */
    val TYPES = setOf("MONEY", "SPREAD", "TOTAL")

    /**
     * [m] as a line of its game's ladder, or null: not a game line, not two outcomes, a fee Novig sent that can't be read, or names that can't be read as a side
     * and a line. The reference team of a margin ladder is the alphabetically first of the market's two names, so a spread named in another way than the
     * moneyline (a different reference) falls into a ladder of its own and is never crossed with it: a sign error would be a false cover.
     */
    fun line(m: NovigMarket): LadderLine? {
        val fee = m.fee ?: return null
        if (m.marketType !in TYPES || m.outcomes.size != 2) return null
        val a = m.outcomes[0]
        val b = m.outcomes[1]
        return when (m.marketType) {
            "TOTAL" -> {
                val over = m.outcomes.firstOrNull { it.name.trim().startsWith("Over", ignoreCase = true) } ?: return null
                val under = m.outcomes.first { it.outcomeId != over.outcomeId }
                val strike = m.strike ?: over.name.trim().substringAfter(' ', "").toDoubleOrNull() ?: return null
                LadderLine(m.marketId, m.eventId, LadderKind.TOTAL, "", strike, over.outcomeId, under.outcomeId, "Total ${fmt(strike)}", fee)
            }
            "MONEY" -> {
                val first = if (a.name.trim() <= b.name.trim()) a else b
                val other = if (first === a) b else a
                LadderLine(m.marketId, m.eventId, LadderKind.MARGIN, first.name.trim(), 0.0, first.outcomeId, other.outcomeId, "ML ${first.name.trim()}", fee)
            }
            else -> {
                val pa = SPREAD_NAME.matchEntire(a.name.trim()) ?: return null
                val pb = SPREAD_NAME.matchEntire(b.name.trim()) ?: return null
                val ta = pa.groupValues[1]
                val tb = pb.groupValues[1]
                if (ta == tb) return null
                val first = if (ta <= tb) a to pa else b to pb
                val other = if (first.first === a) b else a
                val value = first.second.groupValues[2].toDoubleOrNull() ?: return null
                LadderLine(m.marketId, m.eventId, LadderKind.MARGIN, first.second.groupValues[1], -value, first.first.outcomeId, other.outcomeId, "Spr ${first.second.groupValues[1]} ${signed(value)}", fee)
            }
        }
    }

    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toInt().toString() else d.toString()
    private fun signed(d: Double) = (if (d >= 0) "+" else "") + fmt(d)
}

/** One leg of a cover as the book offers it right now: the price to buy, and how many contracts are on offer at it. */
data class Leg(val price: Double, val contracts: Long)

/**
 * A cover: YES at the lower line ([lo]) and NOT at the higher ([hi]), each bought at what the book asks. [net] is the profit per $1 of payout after both
 * in-play taker fees (0.03·P·(1−P) a leg): positive = a guaranteed profit whatever the game does (before the chance the margin lands between the lines, which
 * pays $2 and is ignored). [contracts] is the depth of the thinner leg at its best price: nothing deeper is counted.
 */
data class Cover(val lo: LadderLine, val hi: LadderLine, val yes: Leg, val no: Leg) {
    val key: String get() = lo.marketId + ">" + hi.marketId
    val cost: Double get() = yes.price + no.price
    val contracts: Long get() = minOf(yes.contracts, no.contracts)
    val net: Double get() = 1.0 - cost - fee(yes.price, lo) - fee(no.price, hi)

    /** Dollars of profit if [contracts] fill both legs (a contract pays [EvMath.CONTRACT_PAYOUT_DOLLARS]). */
    val dollars: Double get() = net * contracts * EvMath.CONTRACT_PAYOUT_DOLLARS

    private fun fee(p: Double, line: LadderLine) = if (p > 0.0 && p < 1.0) Fees.takerFee(p, line.fee, eventLive = true) else 0.0
}

object CoverMath {
    /** The cheapest way to buy [outcomeId] of [line]'s market: every resting bid on the OTHER outcome, flipped to 1 − bid. Null with no bid there. */
    fun leg(book: NovigBook?, line: LadderLine, yes: Boolean): Leg? {
        val b = book ?: return null
        val other = if (yes) line.noOutcomeId else line.yesOutcomeId
        val best: BidLevel = b.bidsByOutcome[other]?.firstOrNull() ?: return null
        if (best.contracts <= 0L || best.priceMilli <= 0 || best.priceMilli >= 1000) return null
        return Leg((1000 - best.priceMilli) / 1000.0, best.contracts)
    }

    /** The cover of [lo] YES and [hi] NOT from their books, or null when either side has nothing to buy or [lo] is not below [hi] on the same ladder. */
    fun cover(lo: LadderLine, loBook: NovigBook?, hi: LadderLine, hiBook: NovigBook?): Cover? {
        if (lo.ladderKey != hi.ladderKey || lo.threshold >= hi.threshold) return null
        val yes = leg(loBook, lo, yes = true) ?: return null
        val no = leg(hiBook, hi, yes = false) ?: return null
        return Cover(lo, hi, yes, no)
    }
}
