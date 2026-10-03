package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.tracker.CloseLookup
import com.tjshea.vigilant.data.tracker.CloseSource
import com.tjshea.vigilant.data.tracker.TrackedBet

/**
 * A close source the scan study asks only while [allow] says so. The study grades every bet a scan listed, hundreds a day, where the Tracker grades the few
 * Tj placed: a source that costs API credits (ParlayAPI's Pinnacle closes, 1 credit per 1,000 rows a league-day) is used for the study only while the credits
 * are plentiful, so the study never eats what Tj's own bets and scans need (API credits are a budget; BRIEF.md). A source that is off for now isn't counted
 * as having said "never": the bets it would have closed are asked of it again when it comes back ([com.tjshea.vigilant.data.tracker.CloseBackfill.reopened]).
 */
class GuardedCloses(private val inner: CloseSource, private val allow: () -> Boolean) : CloseSource {
    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> = inner.closes(bets)
    override val heavy: Boolean get() = inner.heavy
    override val active: Boolean get() = inner.active && allow()
    override val id: String get() = inner.id
}
