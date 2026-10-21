package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.reference.OpProps
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * SharpAPI as a fair-odds source (SHARPAPI.md §4): the league's rows from [feed]'s copy of the store, every book Tj picked on SharpAPI's dashboard but the ones [SharpApiBooks.EXCLUDED] leaves out, turned into
 * reference events by [SharpApiConvert]. On the engine the feed keeps itself current by delta polling, so asking costs nothing; a league asked for the first time (or after its delta broke) is read whole first.
 * Pinnacle, Circa, Bookmaker, Betfair, ProphetX and SBOBET come from here at their own delivery time (Pinnacle by push, p50 0.8 s at SharpAPI).
 */
class SharpApiSource(
    private val feed: SharpApiFeed,
    private val client: SharpApiClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReferenceSource {
    override val id = ID
    override val displayName = "SharpAPI"
    override val extraPropTypes: Set<String> get() = OpProps.ALL_TYPES

    override fun supports(league: League): Boolean = SharpApiBooks.leagueSlug(league) != null

    /** What each league last gave (Diagnostics, the engine's log). */
    val lastReads = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        val slug = SharpApiBooks.leagueSlug(league)
        if (slug == null || client.down(now)) return RefSnapshot(league.oddsApiSportKey, emptyList(), now, provider = ID)
        if (!feed.ensure(slug)) {
            feed.lastNote?.let { lastReads[league.novigName] = it }
            if (feed.freshAt(slug) == 0L) throw com.tjshea.vigilant.data.reference.ReferenceException("SharpAPI: ${feed.lastNote ?: client.lastError ?: "no answer"}")
        }
        val ctx = SharpApiConvert.Context(league, wanted = null, families = settings.families, includeLive = settings.includeLive, nowMs = clock())
        val events = SharpApiConvert.events(feed.rows(slug), ctx)
        lastReads[league.novigName] = "${events.size} games, ${events.sumOf { it.markets.size }} lines from ${events.flatMap { e -> e.markets.map { it.bookKey } }.distinct().size} books"
        return RefSnapshot(league.oddsApiSportKey, events, feed.freshAt(slug).takeIf { it > 0 } ?: now, provider = ID)
    }

    companion object {
        const val ID = "sharpapi"
    }
}
