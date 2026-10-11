package com.tjshea.vigilant.data.tracker

/** Open bets whose game is under way (Tj, 2026-10-11: "add a filter to hide live games ... hide live bets from the list and from the stats numbers until I untoggle it"). */
object LiveBets {
    /** Started, and not yet over by the same rule that decides whether a game's odds can still be read ([BetsScope.readable]): an open bet in that window is live; older ones are finished, waiting for a result. */
    fun isLive(bet: TrackedBet, now: Long): Boolean = bet.status == BetStatus.PENDING && now >= bet.startsTs && BetsScope.readable(bet, now)

    fun ids(bets: List<TrackedBet>, now: Long): Set<String> = bets.filter { isLive(it, now) }.mapTo(HashSet()) { it.id }
}
