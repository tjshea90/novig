package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.engine.Odds

/**
 * Canned Novig-shaped data so the app is demoable end to end with zero live credentials. Prices
 * are modeled loosely on real markets observed during RESEARCH.md §8.1's research (not live
 * data, just realistic shapes/magnitudes) — this is sample data, never presented as live.
 */
class SampleNovigRepository : NovigRepository {

    override suspend fun getOpenMarkets(limit: Int): List<NovigEvent> = SAMPLE_EVENTS.take(limit)

    companion object {
        private fun americanPrice(american: Int): Double = Odds.impliedProbability(Odds.americanToDecimal(american))

        val SAMPLE_EVENTS = listOf(
            NovigEvent(
                eventId = "sample-mia-sf-2026-09-20",
                homeTeam = "San Francisco 49ers",
                awayTeam = "Miami Dolphins",
                startTimeIso = "2026-09-20T20:25:00-04:00",
                isLive = false,
                markets = listOf(
                    NovigMarket(
                        marketId = "sample-mia-sf-ml",
                        marketType = "MONEY",
                        description = "Moneyline - Full Match",
                        outcomes = listOf(
                            NovigOutcome("mia", "Miami Dolphins", americanPrice(360)),
                            NovigOutcome("sf", "San Francisco 49ers", americanPrice(-450)),
                        ),
                    ),
                ),
            ),
            NovigEvent(
                eventId = "sample-sc-bama-2026-09-26",
                homeTeam = "Alabama",
                awayTeam = "South Carolina",
                startTimeIso = "2026-09-26T23:00:00-04:00",
                isLive = false,
                markets = listOf(
                    NovigMarket(
                        marketId = "sample-sc-bama-ml",
                        marketType = "MONEY",
                        description = "Moneyline - Full Match",
                        outcomes = listOf(
                            NovigOutcome("sc", "South Carolina", americanPrice(400)),
                            NovigOutcome("bama", "Alabama", americanPrice(-560)),
                        ),
                    ),
                ),
            ),
            NovigEvent(
                eventId = "sample-nyl-tor-2026-09-20",
                homeTeam = "Toronto Tempo",
                awayTeam = "New York Liberty",
                startTimeIso = "2026-09-20T19:00:00-04:00",
                isLive = true,
                markets = listOf(
                    NovigMarket(
                        marketId = "sample-nyl-tor-ml",
                        marketType = "MONEY",
                        description = "Moneyline - Full Match",
                        outcomes = listOf(
                            NovigOutcome("nyl", "New York Liberty", americanPrice(-260)),
                            NovigOutcome("tor", "Toronto Tempo", americanPrice(220)),
                        ),
                    ),
                ),
            ),
        )
    }
}
