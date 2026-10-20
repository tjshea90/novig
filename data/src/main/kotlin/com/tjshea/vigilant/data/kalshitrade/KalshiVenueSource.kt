package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * Kalshi as the PLACE a bet is made (KV, Tj 2026-10-20), not as a fair line: the board
 * [com.tjshea.vigilant.data.book.SportsbookScanner] builds for Kalshi comes from this source. It is a thin shell over
 * [KalshiClient.venueOdds], so the catalog, the socket's prices and the rate gates are the client's (the Novig scan's Kalshi fair-line
 * source and this one share them: a read made for either is free for the other while the socket vouches for it).
 */
class KalshiVenueSource(private val client: KalshiClient) : ReferenceSource {
    override val id = KalshiClient.VENUE_ID
    override val displayName = "Kalshi (to bet)"

    override fun supports(league: League) = client.supports(league)

    /** Never re-used across scans: a price to bet at is read now (the client's catalog already spares a request when the socket vouches). */
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = client.venueOdds(league, settings)
}
