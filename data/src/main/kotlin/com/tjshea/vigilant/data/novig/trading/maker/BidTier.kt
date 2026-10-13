package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.BetKind

/**
 * How proven a kind of bid is, for the "More, smaller bids" mode of the Bids slider (Tj, 2026-10-13: "post the most profitable ones first (from the study) and then the riskier ones after,
 * but with more stringent rules for risky bids, such as all books plus a sharp book must agree"). The tiers come from `research/profitable_types_2026-10-13.md` (1,648 records, 373 bid fills):
 *  - [PROVEN]: team totals outside the NFL (bid fills +34% ROI, CLV +2.4% t=4.4) and anything in the NHL (+24% ROI, CLV +2.1% t=3.3). Judged by the slider's own rules.
 *  - [STEADY]: what beats the close but only breaks even or has too few fills to say: prop unders, 1st-half / period lines, and every other league's props. A sharp book must price it and
 *    one more book than the slider's minimum must agree.
 *  - [RISKY]: prop overs (-10% ROI), anything in the NFL (-16.5% ROI though it beats the close), and game lines (moneylines, spreads, totals: 1 bid in 694 fills). Every book that prices the
 *    line must agree (at least [RISKY_MIN_BOOKS]), a sharp book must be in the fair and give the bid [RISKY_SHARP_MIN_EV], the margin is [RISKY_EXTRA_MARGIN] wider and the stake half.
 * Pure. The tier orders the bids (proven first, riskiest last when the money or the bid count runs out) and sets how strict a bid is; it never loosens a rule the slider sets.
 */
object BidTier {
    const val PROVEN = 1
    const val STEADY = 2
    const val RISKY = 3

    const val RISKY_MIN_BOOKS = 4
    const val RISKY_SHARP_MIN_EV = 0.02
    const val RISKY_EXTRA_MARGIN = 0.015
    const val STEADY_EXTRA_MARGIN = 0.005
    const val RISKY_STAKE = 0.5

    fun of(line: MakerLine): Int {
        val league = line.league.uppercase()
        val over = line.selection.contains(" over ", ignoreCase = true) || line.selection.startsWith("over ", ignoreCase = true)
        return when {
            line.kind in MakerRules.GAME_LINES -> RISKY
            league == "NFL" && line.kind != BetKind.TEAM_TOTAL -> RISKY
            line.kind == BetKind.PROP && over && league != "NHL" -> RISKY
            league == "NHL" -> PROVEN
            line.kind == BetKind.TEAM_TOTAL && league != "NFL" -> PROVEN
            else -> STEADY
        }
    }

    /** Points added to the bid's margin under the tier (the price sits that much further under the fair). */
    fun extraMargin(tier: Int): Double = when (tier) { RISKY -> RISKY_EXTRA_MARGIN; STEADY -> STEADY_EXTRA_MARGIN; else -> 0.0 }

    /** The share of the normal stake the tier bids. */
    fun stakeShare(tier: Int): Double = if (tier == RISKY) RISKY_STAKE else 1.0

    fun label(tier: Int): String = when (tier) { PROVEN -> "proven type"; STEADY -> "steady type"; else -> "risky type" }
}
