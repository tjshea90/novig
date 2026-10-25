package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.reference.OpProps
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.launch

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

    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val inFlight: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** What each league last gave (Diagnostics, the engine's log). */
    val lastReads = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Why each league's rows did or did not become lines ([SharpApiConvert.census]). */
    val lastCensus = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        val slug = SharpApiBooks.leagueSlug(league)
        if (slug == null || client.down(now)) return RefSnapshot(league.oddsApiSportKey, emptyList(), now, provider = ID)
        if (client.rateLimit == null || client.lite()) {
            // The Free tier (12 requests a minute) reads a league whole in minutes, and the scan waited for it: every scan stopped at "Fair odds 68/74" and was cut after 150-270 s, so the list and its
            // fair odds were 5-12 minutes old (Tj, 2026-10-25: "Why are these odds so old if they are from a socket?"). The store is brought up to date in the background; the scan takes what it holds. Before the first answer says which plan the key is, the same.
            if (inFlight.add(slug)) background.launch { try { feed.ensure(slug) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } finally { inFlight.remove(slug) } }
        } else if (!feed.ensure(slug)) {
            feed.lastNote?.let { lastReads[league.novigName] = it }
            if (feed.freshAt(slug) == 0L) throw com.tjshea.vigilant.data.reference.ReferenceException("SharpAPI: ${feed.lastNote ?: client.lastError ?: "no answer"}")
        }
        val ctx = SharpApiConvert.Context(league, wanted = null, families = settings.families, includeLive = settings.includeLive, nowMs = clock())
        // The Free tier's data is 60 s behind its delivery stamp (`X-Data-Delay`): each line is dated by when its price was, so the freshness rule judges the real age.
        val delay = client.dataDelayMs
        val events = SharpApiConvert.events(feed.rows(slug), ctx).let { evs -> if (delay <= 0L) evs else evs.map { e -> e.copy(markets = e.markets.map { m -> m.copy(lastUpdateMs = m.lastUpdateMs?.minus(delay)) }) } }
        lastCensus[league.novigName] = SharpApiConvert.census(feed.rows(slug), ctx)
        lastReads[league.novigName] = "${events.size} games, ${events.sumOf { it.markets.size }} lines from ${events.flatMap { e -> e.markets.map { it.bookKey } }.distinct().size} books"
        return RefSnapshot(league.oddsApiSportKey, events, feed.freshAt(slug).takeIf { it > 0 } ?: now, provider = ID)
    }

    companion object {
        const val ID = "sharpapi"
    }
}
