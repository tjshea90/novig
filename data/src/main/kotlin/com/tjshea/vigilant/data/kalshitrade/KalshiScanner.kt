package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.book.Sportsbook
import com.tjshea.vigilant.data.book.SportsbookScanner
import com.tjshea.vigilant.data.reference.KalshiClient

/**
 * Vigilant's scan for Kalshi (KV2, Tj 2026-10-20): the same machinery as the Novig scan (the planner, the pricing, the fair-line feeds, [com.tjshea.vigilant.data.scanner.ScanResult])
 * with Kalshi's board in place of Novig's. It is the MGM-era [SportsbookScanner] told that the book's lines come from [KalshiVenueSource] and that taking a price costs [KalshiFees.TAKER];
 * it never touches [com.tjshea.vigilant.data.scanner.Scanner], so the Novig scan cannot change because of it. Kalshi's own quotes are cut from the fair lines it prices against
 * (the book is never among its own fair feeds), so a bet at Kalshi is judged by the OTHER books.
 */
object KalshiScanner {
    fun create(clock: () -> Long = System::currentTimeMillis): SportsbookScanner = SportsbookScanner(
        book = Sportsbook.KALSHI,
        clock = clock,
        boardLineFeeds = listOf(KalshiClient.VENUE_ID),
        boardFeeds = listOf(KalshiClient.VENUE_ID),
        fee = KalshiFees.TAKER,
        askFeedsForBook = false,
    )
}
