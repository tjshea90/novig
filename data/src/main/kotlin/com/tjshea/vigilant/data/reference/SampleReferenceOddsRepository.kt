package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.engine.BookQuote

/**
 * Canned reference-book quotes matching [com.tjshea.vigilant.data.novig.SampleNovigRepository]'s
 * three sample events, deliberately mixed: one with a Pinnacle quote (exercises the "prefer a
 * sharp book" path), one with only mainstream books (exercises the "average of major
 * sportsbooks" fallback — Tj's own explicit request), and one live market sized so its tiny raw
 * edge gets wiped out by Novig's live taker fee (RESEARCH.md §3) — so the sample data itself
 * demonstrates why netting out fees matters, not just the happy path.
 */
class SampleReferenceOddsRepository : ReferenceOddsRepository {

    override suspend fun getOddsForSport(sportKey: String, marketKeys: List<String>): List<ReferenceEvent> = SAMPLE_EVENTS

    companion object {
        val SAMPLE_EVENTS = listOf(
            // Has Pinnacle -> Consensus prefers it alone.
            ReferenceEvent(
                homeTeam = "San Francisco 49ers",
                awayTeam = "Miami Dolphins",
                commenceTimeIso = "2026-09-20T20:25:00-04:00",
                quotesByMarket = mapOf(
                    "h2h" to listOf(
                        BookQuote("Pinnacle", listOf(4.35, 1.29)), // [Dolphins, 49ers]
                        BookQuote("DraftKings", listOf(4.10, 1.33)),
                    ),
                ),
            ),
            // No sharp book present -> Consensus averages every book fetched.
            ReferenceEvent(
                homeTeam = "Alabama",
                awayTeam = "South Carolina",
                commenceTimeIso = "2026-09-26T23:00:00-04:00",
                quotesByMarket = mapOf(
                    "h2h" to listOf(
                        BookQuote("DraftKings", listOf(4.90, 1.175)), // [South Carolina, Alabama]
                        BookQuote("FanDuel", listOf(4.70, 1.19)),
                        BookQuote("BetMGM", listOf(4.80, 1.182)),
                    ),
                ),
            ),
            // Live market with a genuine but tiny raw edge — small enough that Novig's live
            // taker fee should flip it net-negative (see EvScannerTest).
            ReferenceEvent(
                homeTeam = "Toronto Tempo",
                awayTeam = "New York Liberty",
                commenceTimeIso = "2026-09-20T19:00:00-04:00",
                quotesByMarket = mapOf(
                    "h2h" to listOf(
                        BookQuote("Pinnacle", listOf(1.36, 3.55)), // [Liberty, Tempo]
                    ),
                ),
            ),
        )
    }
}
