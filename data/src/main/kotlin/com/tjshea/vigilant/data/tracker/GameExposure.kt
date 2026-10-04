package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import java.util.Locale
import kotlin.math.abs

/**
 * One game as a bet, a bid or a new bet knows it: Novig's [eventId] when it has one, else the matchup [eventName] and its start ([startsTs]) that bets
 * saved before the id was kept still carry. [league] only says whether a doubleheader is possible.
 */
data class GameRef(val eventId: String, val eventName: String, val startsTs: Long, val league: String)

/**
 * The most Tj has at risk on one game (Tj, 2026-10-04: "auto bet placed bets on a team at +5, then the same team at +6, then the same team at +10 … if
 * that one team loses badly, I lose many bets due to one event … figure out how to make it without incorrectly blocking bets on different games").
 * Each alternate spread, total and prop is its own Novig market, so no per-market rule saw them as one bet; but they are one event, and they win and
 * lose together. Pure: the placer, the auto-bet and the make desk hand in what is open and ask [check].
 *
 *  - **One game is one Novig event** ([sameGame]): two ids that differ are two games whatever their names and times say, so a doubleheader and the
 *    same teams a day later are never mixed up. A bet saved without an id (before v0.59.0, a ✓ mark, an import) is matched by its matchup and start
 *    time, the way [PlacedIndex] hides placed bets (12 hours; baseball 2, where a doubleheader's second game starts 3+ hours after the first), and a
 *    matchup that can't be read matches nothing: the guard never blocks on a guess.
 *  - **What is at risk** is the dollars of open bets and resting bids. Within one market only the larger side counts (the other side can't also lose,
 *    which is why a two-sided bid or a hedge is not double-counted), a market held on both sides equally (locked in) counts nothing, and a lock is
 *    never exposure of its own. The game's exposure is those market amounts added up.
 *  - **Never in the way of less risk**: a bet that doesn't raise a game's exposure (the other side of a market already held, say) is never blocked, even
 *    when the game is over the limit, and a limit of 0 is no limit.
 */
object GameExposure {

    private const val EPS = 1e-9

    /** One side of one market of one game with [dollars] at risk on it (an open bet's stake, or what a resting bid would cost if it filled). */
    data class Item(val game: GameRef, val marketId: String, val outcomeId: String, val dollars: Double)

    /** What a new bet would do to its game: [now] at risk there before it, [after] with it, against [cap] (0 = no limit). */
    data class Check(val game: GameRef, val cap: Double, val now: Double, val after: Double, val dollars: Double) {
        /** Over the limit AND adding risk: a bet that leaves the game's exposure where it was never is. */
        val blocked: Boolean get() = cap > 0.0 && after > cap + EPS && after > now + EPS

        /** The refusal (or the warning, by hand) in words: which game, what is at risk, what this adds, the limit. */
        fun words(): String {
            val name = game.eventName.ifBlank { "this game" }
            return "${money(now)} is already at risk on $name; this ${money(dollars)} would make ${money(after)}, over your ${money(cap)} limit per game " +
                "(Settings › Betting & Novig account › Most on one game)."
        }
    }

    /** The game [bet] is on. */
    fun gameOf(bet: TrackedBet) = GameRef(bet.eventId, bet.eventName, bet.startsTs, bet.league)

    /** Whether [a] and [b] are one game (see the class comment). */
    fun sameGame(a: GameRef, b: GameRef): Boolean {
        if (a.eventId.isNotBlank() && b.eventId.isNotBlank()) return a.eventId == b.eventId
        val key = PlacedIndex.gameKey(a.eventName) ?: return false
        if (key != PlacedIndex.gameKey(b.eventName)) return false
        if (a.startsTs <= 0L || b.startsTs <= 0L) return false
        val window = if (PlacedIndex.isBaseball(a.league) || PlacedIndex.isBaseball(b.league)) PlacedIndex.SAME_BASEBALL_GAME_MS else PlacedIndex.SAME_GAME_MS
        return abs(a.startsTs - b.startsTs) <= window
    }

    /**
     * What [bets] have at risk now: the open ones ([BetStatus.PENDING]), not a lock, not in a market held on both sides equally ([LockedBets]), judged
     * over all of [bets] (a lock and its pick settle together).
     */
    fun items(bets: List<TrackedBet>): List<Item> {
        val locked = LockedBets.markets(bets).keys
        return bets.filter { it.status == BetStatus.PENDING && !it.isLock && it.stake > 0.0 && it.marketId !in locked }
            .map { Item(gameOf(it), it.marketId, it.outcomeId, it.stake) }
    }

    /**
     * What Vigilant's bids that are not ended would cost if they filled: the contracts still resting, since a filled part is already a bet in the
     * Tracker ([items]). A bid on its way down counts too (it can still fill until Novig confirms it gone).
     */
    fun bidItems(bids: List<MakerBid>): List<Item> =
        bids.filter { it.active && it.restingDollars > 0.0 }.map { Item(GameRef(it.eventId, it.eventName, it.startsTs, it.league), it.marketId, it.outcomeId, it.restingDollars) }

    /** The dollars at risk on [game] in [items]: each market's larger side, added up. */
    fun atRisk(game: GameRef, items: List<Item>): Double {
        val mine = items.filter { sameGame(game, it.game) }
        if (mine.isEmpty()) return 0.0
        // A market with no id can't be told from another: each such item stands alone (the larger exposure, never less).
        var alone = 0
        val bySide = HashMap<String, HashMap<String, Double>>()
        for (i in mine) {
            val market = i.marketId.ifBlank { "?${alone++}" }
            bySide.getOrPut(market) { HashMap() }.merge(i.outcomeId, i.dollars, Double::plus)
        }
        return bySide.values.sumOf { sides -> sides.values.max() }
    }

    /** What putting [dollars] on [outcomeId] of [marketId] in [game] does to its exposure, against [cap] (0 = no limit). */
    fun check(game: GameRef, items: List<Item>, marketId: String, outcomeId: String, dollars: Double, cap: Double): Check {
        val before = atRisk(game, items)
        val after = atRisk(game, items + Item(game, marketId, outcomeId, dollars))
        return Check(game, cap, before, after, dollars)
    }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)
}
