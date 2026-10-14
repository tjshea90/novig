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

    // Fills per 100 bid-hours, measured on 24,267 bids (research/bid_fill_speed_2026-10-14.md).
    private fun leadFactor(hours: Double): Double = when {
        hours < 1 -> 25.7
        hours < 3 -> 16.9
        hours < 6 -> 15.7
        hours < 12 -> 8.9
        hours < 24 -> 5.6
        hours < 72 -> 1.0
        else -> 0.5
    }

    private fun kindFactor(kind: BetKind): Double = when (kind) {
        BetKind.PROP -> 17.1
        BetKind.TEAM_TOTAL -> 5.8
        BetKind.MONEYLINE -> 7.8
        BetKind.PERIOD -> 3.1
        BetKind.SPREAD -> 1.5
        BetKind.TOTAL -> 1.3
        BetKind.OTHER -> 3.0
    }

    private fun leagueFactor(league: String): Double = when (league.uppercase()) {
        "WNBA" -> 33.0
        "NHL" -> 22.6
        "MLB" -> 16.1
        "NFL" -> 10.3
        "NBA" -> 9.3
        "NCAAF" -> 3.4
        "ATP" -> 1.5
        "WTA" -> 0.5
        else -> 8.0
    }

    /** How likely this bid is to fill fast, relative (bigger = sooner): the product of how fast its hours-to-start, kind, league and place in the queue have filled. */
    fun score(line: MakerLine, leadsSide: Boolean, now: Long): Double {
        val hours = ((line.startsTs - now) / 3_600_000.0).coerceAtLeast(0.0)
        return leadFactor(hours) * kindFactor(line.kind) * leagueFactor(line.league) * (if (leadsSide) 16.2 else 3.5) / 1_000.0
    }
}
