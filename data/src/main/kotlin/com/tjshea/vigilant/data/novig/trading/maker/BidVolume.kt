package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.BetKind

/**
 * Volume mode for the bids (Tj, 2026-10-14: "The key is volume. I want hundreds or thousands of bids up at any time, on markets and types of bids most likely to be filled put up first. It can go down to 1.5% positive
 * EV on the safest bid types and 2.5% positive EV on riskier ones. The goal right now is pure volume and bid fills"). A bid's EV at its own price is its margin under the fair, so Volume mode posts every bid at the
 * most the floor allows ([SAFE_EV] for the types that hold up, [RISKY_EV] for the rest and for lines no sharp book prices): the highest price that still has that edge is the one that fills fastest.
 * Order is by how fast that kind of bid has filled ([score], from `research/bid_fill_speed_2026-10-14.md`), so the money goes first where fills come fastest. Pure.
 */
object BidVolume {
    /** The EV floor of the safest bid types (proven and steady: [BidTier]) when a sharp book prices the line. */
    const val SAFE_EV = 0.015

    /** The floor of the riskier types (prop overs, the NFL, game lines), and of any line no sharp book prices. */
    const val RISKY_EV = 0.025

    /** Most bids up at once, bids a pass sends: thousands, in a few passes. */
    const val MAX_BIDS = 3_000
    const val POSTS_PER_PASS = 250

    /** The fair behind a bid on a game 1-12 hours out may be this old before the bid ends (a quick scan reads it again every few minutes; this is only the backstop). */
    const val MID_FAIR_MAX_AGE_MS = 15 * 60_000L

    /** The books that must agree on a steady or risky bid in Volume mode. */
    const val MIN_AGREEING = 3

    /** The floor a bid on [line] is posted at in Volume mode. */
    fun margin(line: MakerLine, smallMarket: Boolean): Double =
        if (smallMarket || line.sharpFairs.isEmpty() || BidTier.of(line) == BidTier.RISKY) RISKY_EV else SAFE_EV

    /** Why a steady or risky line in Volume mode is left alone, or null: enough books must agree (a sharp book is not required: the risky floor covers a line without one). */
    fun tierReason(line: MakerLine, agreeing: Int, minBooks: Int): String? {
        if (BidTier.of(line) == BidTier.PROVEN) return null
        val need = maxOf(MIN_AGREEING, minBooks + 1)
        return if (agreeing < need) "Volume mode needs $need books on a ${BidTier.label(BidTier.of(line))}'s side (only $agreeing)" else null
    }

    // Fills per 1,000 bids posted, by the hours to the start and by how far the bid sits under Novig's offer (the ask), measured on 339,555 bids of the engine's own desk, 2026-10-16..21 (528 fills; XO2,
    // research/bid_volume_study_2026-10-21.md). Ranked by these two alone, held out a day at a time, the top fifth of the bids held 59.5% of the fills against 38.4% for the earlier hours x kind x league x
    // lead-of-side factors (area under the capture curve 0.754 against 0.614, better on each of the six days): kind and league added nothing once these two were in. A bid close to the offer fills 25 times as
    // often as one 15 points under it, and one on a game inside the hour 20 times as often as one a day and more out.
    private fun hoursLift(hours: Double): Double = when {
        hours < 1 -> 12.8
        hours < 2 -> 7.1
        hours < 6 -> 4.6
        hours < 12 -> 3.9
        hours < 24 -> 2.2
        else -> 0.65
    }

    /** [gapPoints]: Novig's offer minus the bid's price, in points of probability; null = no offer is known (the middle of the table). */
    private fun gapLift(gapPoints: Double?): Double = when {
        gapPoints == null -> 1.3
        gapPoints <= 1.0 -> 13.7
        gapPoints <= 2.0 -> 6.1
        gapPoints <= 3.0 -> 2.1
        gapPoints <= 5.0 -> 1.3
        gapPoints <= 15.0 -> 0.9
        else -> 0.54
    }

    /**
     * How likely this bid is to fill fast, relative (bigger = sooner): how fast bids this near the start and this close to the offer have filled ([hoursLift] x [gapLift]), a bid that leads its side first (a bid
     * behind another fills only after it). [price] is the bid's own price; without it only the start counts.
     */
    fun score(line: MakerLine, leadsSide: Boolean, now: Long, price: Double? = null): Double {
        val hours = ((line.startsTs - now) / 3_600_000.0).coerceAtLeast(0.0)
        val gap = if (price != null && line.offer != null) (line.offer - price) * 100.0 else null
        return hoursLift(hours) * gapLift(gap) * (if (leadsSide) 1.5 else 1.0) / 10.0
    }
}
