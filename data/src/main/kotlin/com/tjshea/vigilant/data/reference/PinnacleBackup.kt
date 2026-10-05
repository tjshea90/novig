package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * A feed that carries Pinnacle's book among others, asked in Pinnacle only (Tj, 2026-10-05: "it only scans novig and pinnacle when this option is on so as not
 * to waste usage of other apis"; RESEARCH.md §88.5) only where the Pinnacle feeds ([PinnapiClient]: PinnWire, then pinnapi) did not answer. The scan hands it
 * [ScanSettings.effective]'s settings, so it asks its own API for Pinnacle's book alone ([ScanSettings.referenceBooks]); this decides whether it is asked at all:
 *  - **Game lines** ([ReferenceSource.propsOnly] false): only for a league the Pinnacle feeds did not answer this scan (no key, a day's limit, an error, a sport
 *    they don't carry). Their copy of Pinnacle is the same book; asking again for it would spend requests for nothing.
 *  - **Player props**: only for a league where the Pinnacle feeds priced no prop at all (PinnWire down, or only pinnapi, whose trial key has none), and then
 *    only for the games and stats Novig lists. The per-game requests are the expensive kind, so a league whose props PinnWire did price is never asked.
 * It is a fallback of the Pinnacle source ([ReferenceSource.fallbackFor]), so the scan runs it after that source and with what it gave
 * ([ScanContext.covered], [ScanContext.firstAnswered]).
 */
class PinnacleBackup(private val inner: ReferenceSource) : ReferenceSource by inner {

    override val fallbackFor: String get() = PinnapiClient.ID

    override suspend fun needed(league: League, settings: ScanSettings, context: ScanContext): Boolean {
        if (!settings.pinnacleOnly) return inner.needed(league, settings, context)
        val pricedProps = context.novigEvents.filter { it.league == league.novigName }
            .any { e -> context.covered[e.eventId].orEmpty().any { it.startsWith("PROP:") } }
        return if (inner.propsOnly) !pricedProps else league.novigName !in context.firstAnswered
    }
}
