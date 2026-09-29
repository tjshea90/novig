package com.tjshea.vigilant.data.alerts

import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.PlacedBet
import com.tjshea.vigilant.data.tracker.PlacedBets
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.CancellationException

/**
 * "✓ Placed" on a +EV push notification (Tj, 2026-09-29: "Right now, if I click on the notification, it opens
 * novig, but there isn't a fast way for me to add the bet as a tracked bet in vigilant"): the bet is tracked
 * (so its odds are checked and its result graded like any other) and marked placed (so it leaves the +EV feed,
 * the CNO list, the widget and the alerts, whichever scanner listed it), exactly as the widget's ✓ does.
 */
object AlertPlacement {

    /** The placed-bet mark for [a]: under its own key and Novig outcome, so the other scanner's copy is hidden too. */
    fun placedOf(a: EvAlert, now: Long): PlacedBet = PlacedBet(
        key = a.key,
        title = a.bet,
        detail = "${a.market} · ${a.event}",
        family = Picks.familyKey(a.event, a.market, a.bet),
        odds = Odds.formatAmerican(a.american),
        placedAtMs = now,
        startsAtMs = a.startsAtMs,
        event = a.event,
        market = a.market,
        outcomeId = a.outcomeId,
        league = a.league,
    )

    /**
     * Marks [a] placed and logs it at [stake] dollars ($1 when null). Each write is tried on its own, so a full disk
     * on one doesn't lose the other; null when the bet couldn't be logged in the Tracker.
     */
    suspend fun place(a: EvAlert, stake: Double?, tracker: BetTracker, placed: PlacedBets, now: Long): TrackedBet? {
        try {
            placed.mark(placedOf(a, now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The Tracker's copy still hides it from every list ([com.tjshea.vigilant.data.tracker.PlacedIndex]).
        }
        return try {
            tracker.logAlert(a, stake ?: BetTracker.DEFAULT_STAKE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Undo: the mark and the open bet go, and the bet shows in the lists again. Settled bets stay. */
    suspend fun undo(a: EvAlert, tracker: BetTracker, placed: PlacedBets) {
        try {
            placed.unmark(a.key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: the open bet is what matters.
        }
        tracker.untrack(a.key)
    }
}
