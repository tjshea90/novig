package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.reference.OpBooks
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.ScanContext
import com.tjshea.vigilant.data.reference.SgoBooks

/**
 * One API for overlapping data, the others asleep (Tj, 2026-10-11): "if I have multiple apis toggled on, I still want it to be efficient and if data overlaps, only pull from the best API and let the
 * others sleep. Only use additional apis for extra information that they carry that others don't or if the best api doesn't respond or runs out of usage, it automatically goes to the next best API".
 *
 * The aggregator feeds form chains, best first ([SOURCE_ORDER]'s order). A feed on the chain is asked for a league only after every better feed that is on has answered or failed, and then only when
 *  - no better feed answered that league (it failed, was out of credits, was held back, or has no such league): the next best takes over; or
 *  - some Novig game in the window has none of the data this feed serves from the better feeds (game lines, a period's lines, player props): it fills the gap; or
 *  - it carries a book the better feeds did not send and Tj has that book switched on ([extraBooks]).
 * Otherwise it sleeps: nothing is asked and nothing is spent. Exchanges and Pinnacle's own hosts are not on a chain: they carry prices no aggregator does.
 */
object ApiChain {
    enum class Kind { LINES, PERIODS, PROPS }

    /** The feeds of one chain, best first, each with what it serves. */
    private val LINES_CHAIN = listOf("sgo" to Kind.LINES, "oddspapi" to Kind.LINES, "oddspapi_free" to Kind.LINES, "parlay" to Kind.LINES, "parlay_1h" to Kind.PERIODS, "propline" to Kind.LINES, "oddsapi" to Kind.LINES, "unabated" to Kind.LINES)
    private val PROPS_CHAIN = listOf("sgo-props", "oddspapi-props", "oddspapi_free-props", "parlay_props", "propline_props", "oddsapi_props", "unabated-props").map { it to Kind.PROPS }
    private val CHAINS = listOf(LINES_CHAIN, PROPS_CHAIN)

    /** What a feed serves, or null when it is not on a chain (always asked). */
    fun kindOf(sourceId: String): Kind? = CHAINS.firstNotNullOfOrNull { chain -> chain.firstOrNull { it.first == sourceId }?.second }

    /** The feeds above [sourceId] on its chain that are on ([enabled]), best first: the ones it waits for. Empty for the best one and for a feed that is not on a chain. */
    fun better(sourceId: String, enabled: Collection<String>): List<String> {
        val chain = CHAINS.firstOrNull { c -> c.any { it.first == sourceId } } ?: return emptyList()
        val at = chain.indexOfFirst { it.first == sourceId }
        return chain.take(at).map { it.first }.filter { it in enabled }
    }

    private val GAME_LINES = setOf("MONEYLINE:0", "SPREAD:0", "TOTAL:0")

    /** Books [sourceId] carries beyond the common ones, when Tj switched them on (SGO's and OddsPapi's Circa, SuperBook, bet365). */
    fun extraBooks(sourceId: String, settings: ScanSettings): Set<String> = when (sourceId) {
        "sgo", "sgo-props" -> if (settings.sgoPro && settings.sgoExtraBooks) SgoBooks.EXTRA.toSet() else emptySet()
        "oddspapi", "oddspapi-props", "oddspapi_free", "oddspapi_free-props" -> if (settings.oddsPapi && settings.opExtraBooks) OpBooks.EXTRA.toSet() else emptySet()
        else -> emptySet()
    }

    /**
     * Whether [sourceId], below better feeds ([better] non-empty), should be asked for [league] now, given what the better ones gave ([context]: the leagues they answered, per Novig game what they priced, the
     * books they sent). Pure.
     */
    fun needed(sourceId: String, league: League, settings: ScanSettings, context: ScanContext): Boolean {
        val kind = kindOf(sourceId) ?: return true
        // Study mode: every API that is on is asked, so the Source race can compare them on one scan.
        if (settings.gates.askAllSources) return true
        // No better feed answered this league: it failed, ran out of credits, was held back, or does not list it. The next best takes over.
        if (league.novigName !in context.firstAnswered) return true
        val horizon = context.now + (settings.daysAhead.coerceAtLeast(1) + 1) * 86_400_000L
        val games = context.novigEvents.filter { it.league == league.novigName && it.startsTs <= horizon && it.matchup != null }
        if (games.isEmpty()) return false
        val gap = games.any { e -> missing(kind, e, context) }
        if (gap) return true
        // Extra information: a book the better feeds did not send, that Tj has on.
        val have = context.coveredBooks[league.novigName].orEmpty()
        return extraBooks(sourceId, settings).any { it !in have }
    }

    private fun missing(kind: Kind, e: NovigEvent, context: ScanContext): Boolean {
        val cov = context.covered[e.eventId].orEmpty()
        return when (kind) {
            Kind.LINES -> cov.none { it in GAME_LINES }
            // A period's lines (1st half, F5, a set): none priced for a game whose Novig board lists one.
            Kind.PERIODS -> context.novigMarkets.any { it.eventId == e.eventId && it.marketType in PERIOD_TYPES } && cov.none { !it.startsWith("PROP:") && !it.endsWith(":0") }
            // Player props: the game has props on Novig and none of the better feeds priced a single one.
            Kind.PROPS -> context.novigMarkets.any { it.eventId == e.eventId && it.marketType in PropStats.NOVIG_TYPES } && cov.none { it.startsWith("PROP:") }
        }
    }

    private val PERIOD_TYPES = MarketFamily.FIRST_HALF.novigTypes.toSet()

    /** Combines the chain rule with the feed's own [ReferenceSource.needed] (a feed with its own reasons to stand by keeps them). */
    suspend fun asked(source: ReferenceSource, league: League, settings: ScanSettings, context: ScanContext): Boolean =
        source.needed(league, settings, context) && needed(source.id, league, settings, context)
}
