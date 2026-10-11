package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League

/**
 * [inner] asked for tennis alone (Tj, 2026-10-11: "For tennis, make the app use every api that offers tennis if I have tennis selected"): a feed whose own switch is off still prices ATP and WTA,
 * the only leagues it is asked for, so a scan with most feeds switched off for testing still has a tennis price from every one that carries the sport.
 */
class TennisOnly(private val inner: ReferenceSource) : ReferenceSource by inner {
    override fun supports(league: League): Boolean = inner.supports(league) && league.tennis

    companion object {
        /** The ids of the feeds that carry tennis today (Pinnacle through PinnWire/pinnapi, Kalshi, ParlayAPI's tour boards): [com.tjshea.vigilant.data.scanner.ScanSettings.enabledSources] lets their quotes price while tennis is picked. */
        val FEEDS = listOf("pinnacle", "kalshi", "parlay")
    }
}
