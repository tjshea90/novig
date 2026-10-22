package com.tjshea.vigilant.data.tracker

import java.util.Locale

/**
 * The repeat and hedge guard (Tj, 2026-10-12: "When I live auto bet, a lot of the time it bets or bids the same side, player, team, or prop over and over many times repeatedly, because the odds change yet it
 * is still positive EV ... Bets and bids may be placed on both sides of a two sided bet, but only if it guarantees positive profit. It should never lock in a loss").
 *
 * Pure. Callers hand in what is held ([Pos]: open bets, resting bids and orders in flight, each with its dollars and contracts) and ask [check] before a bet or a bid:
 *  - **Same selection** ([Pos.outcomeId] is one side of one line, so a different price on it is the same selection): at most [Rules.maxBets] bets and bids and [Rules.maxDollars] dollars on it in all.
 *  - **The other side of the market**: allowed only while [Rules.hedge] is on AND, with every held position and this one counted (a resting bid as if it filled), every way the market can end pays a profit
 *    after every dollar spent (fees included). A market with an outcome nobody holds (a three-way) can't guarantee one, so it is refused. A push refunds, so it never makes a loss and is not tested.
 * A bet by hand is only warned (the caller decides what to do with the words).
 */
object RepeatGuard {

    /** [maxBets] and [maxDollars] of 0 = no limit on that. */
    data class Rules(val maxBets: Int = DEFAULT_MAX_BETS, val maxDollars: Double = DEFAULT_MAX_DOLLARS, val hedge: Boolean = true, /** Other lines of one team, player or side in one game ([family]); 0 = no limit (Tj, 2026-10-14). */ val maxLines: Int = DEFAULT_MAX_LINES,
        /** Most bets (a bid fill counts) in one game across every market and side; 0 = no limit (Tj, 2026-10-22: diversify, not too much on one game). */ val maxGameBets: Int = 0,
        /** Most dollars, bets and resting bids together, on one team, player or prop of one game ([family]); 0 = no limit. */ val maxFamilyDollars: Double = 0.0)

    /** One position on one outcome: what it cost (fees included) and what it pays if that outcome wins (a contract pays $1). A resting bid counts as if it filled. */
    data class Pos(val marketId: String, val outcomeId: String, val dollars: Double, val payout: Double, val count: Int = 1, /** [family] of the bet, "" = unknown (never counted as a line). */ val family: String = "")

    /**
     * Why a new bet or bid of [dollars] paying [payout] if it wins on [outcomeId] of [marketId] must not go, in words; null when it may. [outcomes]: every outcome id of the market when known (so a three-way
     * market with a side nobody holds is seen); [held]: everything held, in any market.
     */
    fun check(rules: Rules, held: List<Pos>, marketId: String, outcomeId: String, dollars: Double, payout: Double, outcomes: Collection<String>? = null, family: String = ""): String? {
        if (outcomeId.isBlank()) return null
        val same = held.filter { it.outcomeId == outcomeId }
        val n = same.sumOf { it.count }
        if (rules.maxBets > 0 && n >= rules.maxBets) {
            return "Already $n bet${if (n == 1) "" else "s"} or bid${if (n == 1) "" else "s"} on this same side (limit ${rules.maxBets}): a new price on it is not a new bet."
        }
        // Another line of the same team, player or side in this game wins and loses with the ones already held (Tj, 2026-10-14: the auto-bet took one team at +6.5 and +3.5).
        if (rules.maxLines > 0 && family.isNotBlank()) {
            val lines = held.filter { it.family == family && it.outcomeId != outcomeId && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId }.size
            if (lines >= rules.maxLines) {
                return "Already $lines other line${if (lines == 1) "" else "s"} of the same team or player in this game (limit ${rules.maxLines}): they win and lose together, so another line is the same risk again."
            }
        }
        if (family.isNotBlank()) {
            val game = gameOf(family)
            if (rules.maxGameBets > 0) {
                val inGame = held.filter { it.family.isNotBlank() && gameOf(it.family) == game }.sumOf { it.count }
                if (inGame >= rules.maxGameBets) return "Already $inGame bets in this game (limit ${rules.maxGameBets}): spread the risk over other games instead of piling onto one."
            }
            if (rules.maxFamilyDollars > 0.0) {
                val onFamily = held.filter { it.family == family }.sumOf { it.dollars }
                if (onFamily > 0.0 && onFamily + dollars > rules.maxFamilyDollars + EPS) {
                    return "${money(onFamily)} is already on this team, player or prop in this game; ${money(dollars)} more would pass the ${money(rules.maxFamilyDollars)} limit on one of them."
                }
            }
        }
        val sameDollars = same.sumOf { it.dollars }
        if (rules.maxDollars > 0.0 && same.isNotEmpty() && sameDollars + dollars > rules.maxDollars + EPS) {
            return "${money(sameDollars)} is already on this same side; ${money(dollars)} more would pass your ${money(rules.maxDollars)} limit on one side."
        }
        val others = held.filter { it.marketId == marketId && it.outcomeId != outcomeId && marketId.isNotBlank() }
        if (others.isEmpty()) return null
        if (!rules.hedge) return "The other side of this market is already held, and betting both sides is switched off."
        val inMarket = held.filter { it.marketId == marketId } + Pos(marketId, outcomeId, dollars, payout)
        val spent = inMarket.sumOf { it.dollars }
        val sides = (outcomes.orEmpty() + inMarket.map { it.outcomeId }).toSet()
        val worst = sides.minOf { side -> inMarket.filter { it.outcomeId == side }.sumOf { it.payout } - spent }
        if (worst <= MIN_LOCKED_PROFIT) {
            return "Both sides of this market would not be a sure profit (the worst way it ends is ${if (worst < 0) "-" else ""}${money(kotlin.math.abs(worst))} after ${money(spent)} spent): a bet or bid on the other side is held back."
        }
        return null
    }

    /**
     * The red X on a hand bet's slip (Tj, 2026-10-22: "if I already bet the same player or team or prop, give me the big red x"): in words, why the side is already held: the same side again, or another
     * line of the same team, player or prop in this game ([family]). Null when nothing like it is held. A resting bid counts as held. The other side of a market is a hedge, not a repeat, and says nothing here.
     */
    fun heldWhy(held: List<Pos>, outcomeId: String, family: String): String? {
        if (outcomeId.isBlank()) return null
        val same = held.filter { it.outcomeId == outcomeId }
        if (same.isNotEmpty()) {
            val dollars = same.sumOf { it.dollars }
            val bets = same.sumOf { it.count }
            return "You already hold this same side: ${money(dollars)} on it (${if (bets > 0) "$bets bet${if (bets == 1) "" else "s"}" else "resting bid"}). A new price on it is not a new bet."
        }
        if (family.isNotBlank()) {
            val other = held.filter { it.family == family && it.outcomeId.isNotBlank() }
            if (other.isNotEmpty()) return "You already hold another line of this same team, player or prop in this game (${money(other.sumOf { it.dollars })} on ${other.mapTo(HashSet()) { it.outcomeId }.size}): they win and lose together."
        }
        return null
    }

    /** The open API bets of the Tracker as positions, locks included (a lock is a position on the other side like any other); a ✓ mark has no contracts and holds nothing here. */
    fun fromBets(bets: List<TrackedBet>): List<Pos> = bets
        .filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() && (it.contracts ?: 0L) > 0L }
        .map { Pos(it.marketId, it.outcomeId, it.stake, (it.contracts ?: 0L) * com.tjshea.vigilant.engine.EvMath.CONTRACT_PAYOUT_DOLLARS, family = family(it.eventName, it.startsTs, it.marketLabel, it.selection)) }

    /** What Vigilant's bids not yet ended would hold if they filled (the unfilled part; a filled part is already a bet in the Tracker). They hold the other side of a market but are not a count of bets. */
    fun fromBids(bids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid>): List<Pos> = bids
        .filter { it.active && it.restingDollars > 0.0 && it.outcomeId.isNotBlank() }
        .map { Pos(it.marketId, it.outcomeId, it.restingDollars, (it.contracts - it.filled).coerceAtLeast(0) * com.tjshea.vigilant.engine.EvMath.CONTRACT_PAYOUT_DOLLARS, count = 0, family = family(it.eventName, it.startsTs, it.marketLabel, it.selection)) }

    /**
     * What wins and loses together in one game: a team's spreads at any line and sign (+6.5, +3.5, -1.5), or one player's or side's over (or under) at any line, with the start in a 12-hour bucket so
     * a rematch another day is another game. Blank when the selection can't be read (never counted).
     */
    fun family(eventName: String, startsTs: Long, market: String, selection: String): String {
        val event = eventName.trim().lowercase()
        if (event.isBlank() || selection.isBlank()) return ""
        val who = com.tjshea.vigilant.data.match.Picks.family(selection)
        if (who.isBlank()) return ""
        val spread = who.endsWith(" +") || who.endsWith(" -")
        val key = if (spread) who.dropLast(2) + "|spread" else who + "|" + market.trim().lowercase()
        return "$event|${startsTs / BUCKET_MS}|$key"
    }

    /** The game part of a [family] (event and start bucket). */
    private fun gameOf(family: String): String = family.split('|').take(2).joinToString("|")

    private const val BUCKET_MS = 12L * 3_600_000L

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    private const val EPS = 1e-9

    /** Every way the market can end must beat everything spent by more than this (a tenth of a cent: strictly positive, not rounding noise). */
    const val MIN_LOCKED_PROFIT = 0.001

    const val DEFAULT_MAX_BETS = 2
    const val DEFAULT_MAX_DOLLARS = 100.0
    const val DEFAULT_MAX_LINES = 1
    const val DEFAULT_MAX_GAME_BETS = 6
    const val DEFAULT_MAX_FAMILY_DOLLARS = 12.0
}
