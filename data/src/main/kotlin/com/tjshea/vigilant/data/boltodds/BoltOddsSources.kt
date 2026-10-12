package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.reference.OpProps
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * BoltOdds' game lines for the scan (BOLTODDS_API.md §7): moneylines, spreads and totals with their alternate lines, team totals, and the 1st-half / first-5-innings / 1st-inning lines, read from what the lines
 * socket has delivered. Reading costs nothing however often a scan asks, so it is not on an [com.tjshea.vigilant.data.scanner.ApiChain] (nothing rests for it, it rests for nothing) and its quotes win a book only
 * after SportsGameOdds' and OddsPapi's (the earlier in [com.tjshea.vigilant.data.scanner.Scanner.SOURCE_ORDER] wins), until the first sample proves its market names (BOLTODDS_API.md §8).
 */
class BoltGamesSource(private val feed: BoltOddsFeed, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "BoltOdds"
    override fun supports(league: League) = BoltBooks.supports(league)
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot =
        RefSnapshot(league.oddsApiSportKey, feed.events(league, settings, props = false), clock(), provider = ID)

    companion object { const val ID = "bolt" }
}

/** BoltOdds' player props, from the props socket: a props-only source, so a scan's game lines do not wait for it. */
class BoltPropsSource(private val feed: BoltOddsFeed, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "BoltOdds props"
    override val propsOnly = true
    override val extraPropTypes: Set<String> get() = OpProps.ALL_TYPES
    override fun supports(league: League) = BoltBooks.supports(league) && OpProps.supports(league.novigName)
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        if (MarketFamily.PLAYER_PROPS !in settings.families) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        return RefSnapshot(league.oddsApiSportKey, feed.events(league, settings, props = true), clock(), provider = ID)
    }

    companion object { const val ID = "bolt-props" }
}
