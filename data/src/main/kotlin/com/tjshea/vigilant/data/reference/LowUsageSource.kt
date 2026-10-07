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
 *
 * [windowGuard] is the same ask in every scan, not only the low-usage one, for a feed that costs credits: a league with no pregame game starting inside the window is not asked
 * (Tj's v0.70.1 diagnostics: 2 of the 4 ParlayAPI props calls, 6 of 40 credits, bought a board with no game in the window). It checks the games alone, not the Novig prop
 * markets: outside the low-usage mode the props feed also reads the lines only a book offers, which Novig lists no market for.
 */
class LowUsageSource(private val inner: ReferenceSource, private val windowGuard: Boolean = false) : ReferenceSource by inner {

    // The board is what says whether a league has anything to bid on.
    override val needsCatalog: Boolean get() = true

    override suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot {
        val nothingToBidOn = when {
            settings.lowUsageScan -> !hasPropsToBidOn(league, settings, context)
            windowGuard -> !hasGameInWindow(league, settings, context)
            else -> false
        }
        if (nothingToBidOn) return RefSnapshot(league.oddsApiSportKey, emptyList(), context.now, provider = inner.id, skipped = true)
        return if (inner.needsCatalog) inner.odds(league, settings, context) else inner.odds(league, settings)
    }

    companion object {
        /** The ids of [league]'s pregame games inside [settings]' window, as [context] (the scan's board) lists them. */
        private fun gamesInWindow(league: League, settings: ScanSettings, context: ScanContext): Set<String> =
            Planner.eligibleEvents(context.novigEvents.filter { it.league == league.novigName }, settings, context.now)
                .filter { it.startsTs > context.now }
                .mapTo(HashSet()) { it.eventId }

        /** Whether [league] has a pregame game inside [settings]' window on the scan's board. */
        fun hasGameInWindow(league: League, settings: ScanSettings, context: ScanContext): Boolean = gamesInWindow(league, settings, context).isNotEmpty()

        /** Whether [league] has a game inside [settings]' window with an open player-prop market on Novig, as [context] (the scan's board) lists them. */
        fun hasPropsToBidOn(league: League, settings: ScanSettings, context: ScanContext): Boolean {
            val games = gamesInWindow(league, settings, context)
            if (games.isEmpty()) return false
            return context.novigMarkets.any { it.eventId in games && it.isOpen && it.marketType in PropStats.NOVIG_TYPES }
        }
    }
}
