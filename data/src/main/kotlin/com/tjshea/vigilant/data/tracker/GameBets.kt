package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid

/**
 * Tj's open bets and resting bids, by game, for the small button on every listed bet (Tj, 2026-10-04: "make a quick button next to each bet in the scanners
 * that involve the la rams game (and the team they are playing) which pulls up which bets I already placed involving that game, money per bet, and total
 * money across all bets for that game … the button itself show the total I already bet involving that game").
 *
 * A listed bet is asked about with its own [GameRef] (a Vigilant bet has Novig's event id, a CrazyNinjaOdds bet only its matchup and start), and
 * [GameExposure.sameGame] says which games are one: the same Novig id, else the same two teams starting within hours of each other, so the Rams'
 * opponent's side of the same game is the same game and the same teams a week later is not. A matchup that can't be read matches nothing.
 *
 * Pure and cheap to ask: built once when the bets or bids change, then every listed bet is a map lookup. Equal when it holds the same lines, so a flow
 * of it doesn't wake the screen for a change that isn't one.
 */
class GameBets private constructor(private val entries: List<Entry>) {

    /** What a line is: an open bet, a lock (the other side bought to guarantee a profit) or a bid still resting on Novig (not a bet yet). */
    enum class Kind { BET, LOCK, BID }

    /** One open bet or resting bid, as the sheet shows it. [dollars] is what the bet staked, or what the bid would cost if it filled. */
    data class Line(
        val id: String,
        val kind: Kind,
        val selection: String,
        val market: String,
        val american: Int?,
        val dollars: Double,
        val atMs: Long,
        /** Placed by the auto-bet or auto-make with nobody confirming it. */
        val auto: Boolean = false,
    )

    private data class Entry(val game: GameRef, val line: Line, val matchup: String?, val item: GameExposure.Item?)

    /** The open bets and resting bids on one game. */
    data class Summary(
        val game: GameRef,
        /** Open bets and locks, newest first. */
        val bets: List<Line>,
        /** Bids still resting on Novig, newest first. */
        val bids: List<Line>,
        /** What the per-game limit counts ([GameExposure.atRisk]): open bets and resting bids, locks and fully locked markets counted once. */
        val atRisk: Double,
    ) {
        /** The money in open bets on this game (every one, locks included): the number on the button. */
        val placed: Double get() = bets.sumOf { it.dollars }

        /** What bids still resting would cost if they filled. */
        val resting: Double get() = bids.sumOf { it.dollars }

        /** Open bets and bids together: what the game has on it or soon will. */
        val total: Double get() = placed + resting

        /** [placed] and [atRisk] differ: a lock or both sides of a market is on the game, so what's at risk is less than what's staked. */
        val hedged: Boolean get() = bids.isEmpty() && kotlin.math.abs(placed - atRisk) > 0.005
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    private val byMatchup: Map<String, List<Entry>> = entries.filter { it.matchup != null }.groupBy { it.matchup!! }
    private val byEvent: Map<String, List<Entry>> = entries.filter { it.game.eventId.isNotBlank() }.groupBy { it.game.eventId }

    /** What Tj has on [game]: null when nothing (no open bet and no resting bid is on it). */
    fun of(game: GameRef): Summary? {
        if (entries.isEmpty()) return null
        val candidates = LinkedHashSet<Entry>()
        game.eventId.takeIf { it.isNotBlank() }?.let { byEvent[it]?.let(candidates::addAll) }
        PlacedIndex.gameKey(game.eventName)?.let { byMatchup[it]?.let(candidates::addAll) }
        val mine = candidates.filter { GameExposure.sameGame(game, it.game) }
        if (mine.isEmpty()) return null
        val risk = GameExposure.atRisk(game, mine.mapNotNull { it.item })
        return Summary(
            game = game,
            bets = mine.map { it.line }.sortedByDescending { it.atMs },
            bids = mine.filter { it.line.kind == Kind.BID }.map { it.line }.sortedByDescending { it.atMs },
            atRisk = risk,
        )
    }

    /** Convenience for a list row that has only its names: [of] with a [GameRef] with no Novig id. */
    fun of(eventName: String, startsTs: Long?, league: String, eventId: String = ""): Summary? =
        if (isEmpty || eventName.isBlank()) null else of(GameRef(eventId, eventName, startsTs ?: 0L, league))

    override fun equals(other: Any?): Boolean = other is GameBets && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    companion object {
        val EMPTY = GameBets(emptyList())

        /**
         * The index of [bets]' open bets (every one still [BetStatus.PENDING], locks too) and of the [bids] still resting. A bid that is not up any more,
         * and the part of one already filled (that is a bet now), counts nothing.
         */
        fun of(bets: List<TrackedBet>, bids: List<MakerBid>): GameBets {
            val locked = LockedBets.markets(bets).keys
            val out = ArrayList<Entry>()
            for (b in bets) {
                if (b.status != BetStatus.PENDING) continue
                val game = GameExposure.gameOf(b)
                val item = if (b.isLock || b.marketId in locked) null else GameExposure.Item(game, b.marketId, b.outcomeId, b.stake)
                val line = Line(b.id, if (b.isLock) Kind.LOCK else Kind.BET, b.selection, b.marketLabel, b.american, b.stake, b.createdAtMs, b.auto)
                out += Entry(game, line, PlacedIndex.gameKey(b.eventName), item)
            }
            for (bid in bids) {
                if (!bid.active || bid.restingDollars <= 0.0) continue
                val game = GameRef(bid.eventId, bid.eventName, bid.startsTs, bid.league)
                val item = GameExposure.Item(game, bid.marketId, bid.outcomeId, bid.restingDollars)
                val line = Line(bid.clientId, Kind.BID, bid.selection, bid.marketLabel, americanOf(bid.price), bid.restingDollars, bid.postedAtMs, bid.auto)
                out += Entry(game, line, PlacedIndex.gameKey(bid.eventName), item)
            }
            return if (out.isEmpty()) EMPTY else GameBets(out)
        }

        /** The American odds of a price (a bid's cost of a $1 payout). */
        private fun americanOf(price: Double): Int? =
            if (price <= 0.0 || price >= 1.0) null else com.tjshea.vigilant.engine.Odds.probabilityToAmerican(price)
    }
}
