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
 *
 * **Similar bets, everyone's together** (Tj, 2026-10-25: "The safeguard should look at: bets I hand placed, bids I hand placed, bets the engine placed, and bids the engine placed ... unless there are many small
 * bets that add up to a small amount of money ... Make a setting where I can adjust the maximum total money wagered (including both bids and bets) on identical or substantially similar bets"). [family] is
 * [SimilarBets.subject]: the game's total either way at any line and period, a team's total, a team's side (spreads and moneyline), a player's over or under, read the same from every source. With
 * [Rules.maxFamilyDollars] on, the dollars on one of them (open bets, filled and resting bids, as if they filled, whoever placed them: [Who]) plus the new one may not pass it, and the count limits
 * ([Rules.maxBets], [Rules.maxLines]) give way to it: many small bets are fine. A bid is never posted on what already holds a bet, by hand or by the engine, and an automatic bet is never placed on
 * what Tj bet by hand ([check]'s `who`).
 */
object RepeatGuard {

    /** [maxBets] and [maxDollars] of 0 = no limit on that. */
    data class Rules(val maxBets: Int = DEFAULT_MAX_BETS, val maxDollars: Double = DEFAULT_MAX_DOLLARS, val hedge: Boolean = true, /** Other lines of one team, player or side in one game ([family]); 0 = no limit (Tj, 2026-10-14). */ val maxLines: Int = DEFAULT_MAX_LINES,
        /** Most bets (a bid fill counts) in one game across every market and side; 0 = no limit (Tj, 2026-10-22: diversify, not too much on one game). */ val maxGameBets: Int = 0,
        /** Most dollars, bets and resting bids together, on one team, player or prop of one game ([family]); 0 = no limit. */ val maxFamilyDollars: Double = 0.0)

    /** Who placed a position, or who is about to: Tj by hand (a Bet-sheet tap, or an order Novig has that no desk of Vigilant's made), an automatic bet (the engine's or the phone's auto-bet), a bid (any desk's, resting or filled). */
    enum class Who(val word: String) { HAND("by you"), AUTO("by the engine"), BID("as bids") }

    /** One position on one outcome: what it cost (fees included) and what it pays if that outcome wins (a contract pays $1). A resting bid counts as if it filled. */
    data class Pos(val marketId: String, val outcomeId: String, val dollars: Double, val payout: Double, val count: Int = 1, /** [family] of the bet, "" = unknown (never counted as a line). */ val family: String = "",
        val who: Who = Who.AUTO, /** The selection in words ("Over 49.5"), for the red X. */ val label: String = "")

    /**
     * Why a new bet or bid of [dollars] paying [payout] if it wins on [outcomeId] of [marketId] must not go, in words; null when it may. [outcomes]: every outcome id of the market when known (so a three-way
     * market with a side nobody holds is seen); [held]: everything held, in any market.
     */
    fun check(rules: Rules, held: List<Pos>, marketId: String, outcomeId: String, dollars: Double, payout: Double, outcomes: Collection<String>? = null, family: String = "", who: Who = Who.AUTO): String? {
        if (outcomeId.isBlank()) return null
        val same = held.filter { it.outcomeId == outcomeId }
        // The similar group: this side and every bet that rides on the same thing ([SimilarBets]), whoever placed it.
        val similar = if (family.isBlank()) same else held.filter { it.outcomeId == outcomeId || it.family == family }
        if (who != Who.HAND) {
            val bets = similar.filter { it.who == Who.HAND || (who == Who.BID && it.who == Who.AUTO) }
            if (bets.isNotEmpty()) {
                val byHand = bets.any { it.who == Who.HAND }
                return "${if (byHand) "You" else "The engine"} already bet this or a bet just like it (${money(bets.sumOf { it.dollars })}${labels(bets)}): ${if (who == Who.BID) "no bid goes up" else "the engine does not bet"} on top of it."
            }
        }
        // With a dollar limit on similar bets the count of small bets doesn't matter (Tj, 2026-10-25): the dollars do.
        val byDollars = rules.maxFamilyDollars > 0.0 && family.isNotBlank()
        val n = same.sumOf { it.count }
        if (!byDollars && rules.maxBets > 0 && n >= rules.maxBets) {
            return "Already $n bet${if (n == 1) "" else "s"} or bid${if (n == 1) "" else "s"} on this same side (limit ${rules.maxBets}): a new price on it is not a new bet."
        }
        // Another line of the same team, player or side in this game wins and loses with the ones already held (Tj, 2026-10-14: the auto-bet took one team at +6.5 and +3.5).
        if (!byDollars && rules.maxLines > 0 && family.isNotBlank()) {
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
            if (byDollars) {
                val onFamily = similar.sumOf { it.dollars }
                if (onFamily + dollars > rules.maxFamilyDollars + EPS) {
                    return if (onFamily <= 0.0) "${money(dollars)} on one bet passes the ${money(rules.maxFamilyDollars)} limit on similar bets."
                    else "${money(onFamily)} is already on this bet or ones just like it${labels(similar)}; ${money(dollars)} more would pass the ${money(rules.maxFamilyDollars)} limit on similar bets."
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
     * The red X on a bet's slip (Tj, 2026-10-22: "if I already bet the same player or team or prop, give me the big red x"; 2026-10-25: "if I already bet or bid something identical or substantially
     * similar or if the engine already did"): in words, what is already held on this side or on a bet just like it ([family]), by whom, and what is left under the similar-bets limit [cap] (0 = none).
     * Null when nothing like it is held. A resting bid counts as held. The other side of a market is a hedge, not a repeat, and says nothing here.
     */
    fun heldWhy(held: List<Pos>, outcomeId: String, family: String, cap: Double = 0.0): String? {
        if (outcomeId.isBlank()) return null
        val similar = held.filter { it.outcomeId == outcomeId || (family.isNotBlank() && it.family == family) }
        if (similar.isEmpty()) return null
        val dollars = similar.sumOf { it.dollars }
        val sameSide = similar.any { it.outcomeId == outcomeId }
        val whom = Who.entries.mapNotNull { w ->
            val mine = similar.filter { it.who == w }
            if (mine.isEmpty()) null
            else if (w == Who.BID) "${money(mine.sumOf { it.dollars })} as bids"
            else "${money(mine.sumOf { it.dollars })} in ${mine.sumOf { it.count }.coerceAtLeast(1)} bet${if (mine.sumOf { it.count } == 1) "" else "s"} ${w.word}"
        }.joinToString(", ")
        val what = if (sameSide) "this same side" else "a bet just like it (the same team, total, player or prop in this game)"
        val left = if (cap > 0.0) " Limit on similar bets ${money(cap)}: ${if (dollars >= cap - EPS) "reached" else "${money(cap - dollars)} left"}." else ""
        return "Already held on $what${labels(similar)}: $whom.$left"
    }

    /** [heldWhy] in one short line for a card: "Already held: $2.38 (you 2, engine bids) · limit $3: $0.62 left". */
    fun heldShort(held: List<Pos>, outcomeId: String, family: String, cap: Double = 0.0): String? {
        if (outcomeId.isBlank()) return null
        val similar = held.filter { it.outcomeId == outcomeId || (family.isNotBlank() && it.family == family) }
        if (similar.isEmpty()) return null
        val dollars = similar.sumOf { it.dollars }
        val whom = buildList {
            similar.filter { it.who == Who.HAND }.sumOf { it.count }.let { if (it > 0) add("you $it") else if (similar.any { p -> p.who == Who.HAND }) add("you") }
            similar.filter { it.who == Who.AUTO }.sumOf { it.count }.let { if (it > 0) add("engine $it") }
            if (similar.any { it.who == Who.BID }) add("bids")
        }.joinToString(", ")
        val left = if (cap > 0.0) " · limit ${money(cap)}: ${if (dollars >= cap - EPS) "reached" else "${money(cap - dollars)} left"}" else ""
        return "Already held${if (similar.none { it.outcomeId == outcomeId }) " (a bet just like it)" else ""}: ${money(dollars)} ($whom)$left"
    }

    /** Whether a BET (Tj's by hand, or an automatic one) is held on [outcomeId] or on one just like it ([family]): then no bid goes up there and a resting one comes down. */
    fun betHeld(held: List<Pos>, outcomeId: String, family: String): Boolean =
        held.any { it.who != Who.BID && (it.outcomeId == outcomeId || (family.isNotBlank() && it.family == family)) }

    /** What may still go on [family] (and the side [outcomeId]) under the similar-bets limit; null when there is no limit to go by. */
    fun room(rules: Rules?, held: List<Pos>, outcomeId: String, family: String): Double? {
        if (rules == null || rules.maxFamilyDollars <= 0.0 || family.isBlank()) return null
        return (rules.maxFamilyDollars - held.filter { it.outcomeId == outcomeId || it.family == family }.sumOf { it.dollars }).coerceAtLeast(0.0)
    }

    /** " (Over 49.5, Over 50.5)": the held lines named, at most four. */
    private fun labels(ps: List<Pos>): String {
        val names = ps.map { it.label }.filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return ""
        return " (" + names.take(4).joinToString(", ") + (if (names.size > 4) ", +${names.size - 4} more" else "") + ")"
    }

    /** The open API bets of the Tracker as positions, locks included (a lock is a position on the other side like any other); a ✓ mark has no contracts and holds nothing here. */
    fun fromBets(bets: List<TrackedBet>, skipOrderIds: Set<String> = emptySet()): List<Pos> = bets
        .filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() && (it.contracts ?: 0L) > 0L && (it.orderId == null || it.orderId !in skipOrderIds) }
        .map { Pos(it.marketId, it.outcomeId, it.stake, (it.contracts ?: 0L) * com.tjshea.vigilant.engine.EvMath.CONTRACT_PAYOUT_DOLLARS, family = family(it.eventName, it.startsTs, it.marketLabel, it.selection), who = whoOf(it), label = it.selection) }

    /**
     * Who placed a Tracker bet: a filled bid is a bid, an auto-bet automatic, and everything else Tj's by hand (a Bet-sheet tap, or a fill Novig had that no desk of Vigilant's recorded, imported:
     * the engine's own orders are counted from its ledger, so the phone passes their order ids to [fromBets] as `skipOrderIds`).
     */
    fun whoOf(b: TrackedBet): Who = when {
        b.isBid -> Who.BID
        b.auto -> Who.AUTO
        else -> Who.HAND
    }

    /** What Vigilant's bids not yet ended would hold if they filled (the unfilled part; a filled part is already a bet in the Tracker). They hold the other side of a market but are not a count of bets. */
    fun fromBids(bids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid>): List<Pos> = bids
        .filter { it.active && it.restingDollars > 0.0 && it.outcomeId.isNotBlank() }
        .map { Pos(it.marketId, it.outcomeId, it.restingDollars, (it.contracts - it.filled).coerceAtLeast(0) * com.tjshea.vigilant.engine.EvMath.CONTRACT_PAYOUT_DOLLARS, count = 0, family = family(it.eventName, it.startsTs, it.marketLabel, it.selection), who = Who.BID, label = it.selection) }

    /**
     * What wins and loses together in one game ([SimilarBets.subject]): the game's total either way at any line and period, a team's total, a team's side (spreads at any line and its moneyline), or
     * one player's over or under, read the same whichever source wrote the bet, with the start in a 12-hour bucket so a rematch another day is another game. Blank when the selection can't be read
     * (never counted).
     */
    fun family(eventName: String, startsTs: Long, market: String, selection: String): String {
        val event = eventName.trim().lowercase()
        if (event.isBlank() || selection.isBlank()) return ""
        val subject = SimilarBets.subject(eventName, market, selection) ?: return ""
        return "$event|${startsTs / BUCKET_MS}|$subject"
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
    /**
     * The similar-bets limit (Tj, 2026-10-25: "not too much staked on identical or very similar bets") for a new install: the default bid's largest stake ([com.tjshea.vigilant.data.scanner.ScanSettings.makerMaxStake]),
     * so a first bid always fits. Tj's own value is set in his settings ($3 on 2026-10-25, against stakes of about $1 and bids of at most $2).
     */
    const val DEFAULT_MAX_FAMILY_DOLLARS = 10.0
}
