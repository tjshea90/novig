package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * A fair-odds source for the Kalshi scan that first looks at what the Novig scan already holds (KV, Tj 2026-10-20: "adopt as many features as possible ... so that there is no double
 * work"). The same API is not asked twice for the same league within [maxAgeMs]: [borrow] returns the Novig scan's board for the source and league, and [sameRequest] says whether the
 * Kalshi scan would have asked for the same thing (the same books and market families); otherwise, or when nothing fresh is held, the source is asked for itself. Props sources (which read
 * per game for a catalog) are never borrowed.
 */
class BorrowedSource(
    private val inner: ReferenceSource,
    private val borrow: (sourceId: String, leagueName: String) -> RefSnapshot?,
    private val sameRequest: (ScanSettings) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = 120_000L,
) : ReferenceSource by inner {
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        if (!inner.needsCatalog && !inner.propsOnly && sameRequest(settings)) {
            val held = borrow(inner.id, league.novigName)
            if (held != null && clock() - held.fetchedAtMs in 0..maxAgeMs) return held
        }
        return inner.odds(league, settings)
    }
}
