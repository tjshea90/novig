package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * A feed asked only for a league Novig has something to bid on (Tj, 2026-10-05: "it should not waste api usage"; RESEARCH.md §92). In the low-usage scan
 * ([ScanSettings.lowUsageScan]) a league with no pregame game starting inside the window, or none of whose games has an open player-prop market on Novig, is not asked at
 * all: before this, every source was asked for every picked league on every scan (one request, or 3 credits, each) however empty its board was. Anywhere else it is the
 * feed it wraps, unchanged. The window is the scan's own ([Planner.eligibleEvents]); the answer for a skipped league is an empty snapshot, which the scan counts as
 * answered (nothing to price, nothing spent).
 */
class LowUsageSource(private val inner: ReferenceSource) : ReferenceSource by inner {

    // The board is what says whether a league has anything to bid on.
    override val needsCatalog: Boolean get() = true

    override suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot {
        if (settings.lowUsageScan && !hasPropsToBidOn(league, settings, context)) {
            return RefSnapshot(league.oddsApiSportKey, emptyList(), context.now, provider = inner.id, skipped = true)
        }
        return if (inner.needsCatalog) inner.odds(league, settings, context) else inner.odds(league, settings)
    }

    companion object {
        /** Whether [league] has a game inside [settings]' window with an open player-prop market on Novig, as [context] (the scan's board) lists them. */
        fun hasPropsToBidOn(league: League, settings: ScanSettings, context: ScanContext): Boolean {
            val games = Planner.eligibleEvents(context.novigEvents.filter { it.league == league.novigName }, settings, context.now)
                .filter { it.startsTs > context.now }
                .mapTo(HashSet()) { it.eventId }
            if (games.isEmpty()) return false
            return context.novigMarkets.any { it.eventId in games && it.isOpen && it.marketType in PropStats.NOVIG_TYPES }
        }
    }
}
