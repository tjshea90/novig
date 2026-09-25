package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.scanner.BookPropSet
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Player props from the major sportsbooks (DraftKings, FanDuel, BetMGM, Caesars, …) through The
 * Odds API's per-game endpoint (RESEARCH.md §14), so a prop's fair price can be the books'
 * devigged average, blended with Kalshi where it lists the same line.
 *
 * Props cost one credit per prop type per game, so this is careful with them:
 * - Only games Novig lists props for, that haven't started and start within
 *   [ScanSettings.bookPropHours], soonest first across every league in the scan.
 * - Only the prop types Novig lists for that game ([ScanSettings.bookPropSet] caps them).
 * - At most [ScanSettings.bookPropCreditsPerScan] credits a scan.
 * - A game's props are re-used for [ScanSettings.bookPropReuseMinutes]; the budget goes to games
 *   not yet bought first. Re-use is per game, here, so the scanner calls this every scan.
 * - Matching Novig's games to The Odds API's uses the free `/events` list and the same team and
 *   start-time rules as every other feed ([Planner.matchEvents]).
 */
class OddsApiPropsSource(
    private val client: TheOddsApiClient,
) : ReferenceSource {

    override val id = ID
    override val displayName = "Sportsbook props"
    override val metered = true
    override val needsCatalog = true

    override fun supports(league: League): Boolean =
        PropStats.oddsApiMarkets(league.oddsApiSportKey, BookPropSet.ALL).isNotEmpty()

    /** Re-use is per game, in [bought]; the scanner must not hold the whole league back. */
    override fun reuseMs(settings: ScanSettings): Long = 0L

    /** A game's props, bought at [atMs] with request [ask]. [ref] may have no markets (no book posted any). */
    private data class Bought(val novigEventId: String, val ref: RefEvent, val atMs: Long, val ask: String)

    private val mutex = Mutex()
    private val bought = HashMap<String, Bought>()
    private var scanAt = Long.MIN_VALUE
    private var toBuy: Map<String, List<String>> = emptyMap()

    /** Without Novig's board there's nothing to choose from: only props already bought. */
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot =
        odds(league, settings, ScanContext())

    override suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot = mutex.withLock {
        val now = context.now
        val sport = league.oddsApiSportKey
        val ask = ask(settings)
        val reuseMs = settings.bookPropReuseMinutes.coerceAtLeast(0) * 60_000L
        // The scanner calls once per league with the same clock reading: the first call of a scan
        // spends the budget across every league at once, soonest games first.
        if (now != scanAt) {
            scanAt = now
            bought.values.removeAll { now - it.atMs >= reuseMs }
            toBuy = allocate(settings, context, ask)
        }
        val leagueEvents = context.novigEvents.filter { it.league == league.novigName }
        val buying = leagueEvents.filter { it.eventId in toBuy }.sortedBy { it.startsTs }

        var remaining: Int? = null
        var used: Int? = null
        var failure: Exception? = null
        if (buying.isNotEmpty()) {
            try {
                // Free, and trimmed to the window (plus a day for football's loose kickoff times).
                val listed = client.events(sport, now + (settings.bookPropHours + 24) * 3_600_000L)
                remaining = listed.remaining ?: remaining
                used = listed.used ?: used
                val matches = Planner.matchEvents(buying, listOf(RefSnapshot(sport, listed.value, now, provider = ID)))
                for (m in matches.sortedBy { it.event.startsTs }) {
                    val ref = m.refEvent ?: continue
                    val markets = toBuy[m.event.eventId].orEmpty()
                    if (markets.isEmpty()) continue
                    val answer = client.eventOdds(sport, ref.id, settings.referenceBooks, markets)
                    remaining = answer.remaining ?: remaining
                    used = answer.used ?: used
                    // Keep the listing's teams and time (the ones matched on) with the odds call's quotes.
                    val odds = answer.value?.markets.orEmpty()
                    bought[m.event.eventId] = Bought(m.event.eventId, ref.copy(markets = odds), now, ask)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }

        val ids = leagueEvents.mapTo(HashSet()) { it.eventId }
        val events = bought.values
            .filter { it.novigEventId in ids && it.ask == ask && now - it.atMs < reuseMs && it.ref.markets.isNotEmpty() }
            .map { it.ref }
            .distinctBy { it.id }
        val snapshot = RefSnapshot(sport, events, now, remaining, used, ID)
        failure?.let { e ->
            val message = when (e) {
                is AllKeysExhaustedException, is ReferenceException -> e.message ?: displayName
                else -> "$displayName ${league.displayName}: ${e.message ?: e.javaClass.simpleName}"
            }
            throw PartialReferenceException(snapshot, message)
        }
        snapshot
    }

    /**
     * Novig event id -> the Odds API prop markets to buy for it this scan. Soonest games first;
     * a game whose props are still fresh costs nothing and is skipped; a game that doesn't fit
     * the remaining credits is skipped for a cheaper later one.
     */
    private fun allocate(settings: ScanSettings, context: ScanContext, ask: String): Map<String, List<String>> {
        if (MarketFamily.PLAYER_PROPS !in settings.families) return emptyMap()
        var credits = settings.bookPropCreditsPerScan
        if (credits <= 0) return emptyMap()
        val now = context.now
        val horizon = now + settings.bookPropHours.coerceAtLeast(1) * 3_600_000L
        val propTypes = context.novigMarkets
            .filter { it.isOpen && it.marketType in PropStats.ODDS_API_MARKETS.values }
            .groupBy({ it.eventId }, { it.marketType })
        val out = LinkedHashMap<String, List<String>>()
        val games = context.novigEvents
            .filter { it.status == NovigEvent.STATUS_PREGAME && it.startsTs > now && it.startsTs <= horizon && it.league in settings.leagues }
            .sortedBy { it.startsTs }
        for (e in games) {
            val have = bought[e.eventId]
            if (have != null && have.ask == ask) continue
            val league = Leagues.byNovigName(e.league) ?: continue
            val types = propTypes[e.eventId]?.toSet() ?: continue
            val markets = PropStats.oddsApiMarkets(league.oddsApiSportKey, settings.bookPropSet, types)
            if (markets.isEmpty() || markets.size > credits) continue
            out[e.eventId] = markets
            credits -= markets.size
        }
        return out
    }

    private fun ask(settings: ScanSettings) =
        "${settings.referenceBooks.filter { it != "novig" }.distinct().take(TheOddsApiClient.MAX_BOOKMAKERS_ONE_REGION).sorted()}|${settings.bookPropSet}"

    companion object {
        const val ID = "oddsapi_props"
    }
}
