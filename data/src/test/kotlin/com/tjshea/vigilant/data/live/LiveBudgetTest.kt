package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tj, 2026-09-28: "consider if the 1200 Max prices per novig scan is enough for me to find most or all positive EV bets
 * available". How many Novig lines a fair source prices on the real board, 7 days ahead, with the free sources (Kalshi,
 * Polymarket; Pinnacle and PropLine, which need keys, add more). Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveBudgetTest'`.
 */
class LiveBudgetTest {

    @Test
    fun `real board - Novig lines with a fair price, per game caps and in all`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient()
        val novig = NovigPublicClient(http, json)
        val now = System.currentTimeMillis()
        val base = ScanSettings(leagues = setOf("NFL", "NCAAF", "MLB", "WNBA", "ATP", "WTA"), daysAhead = 7, families = MarketFamily.entries.toSet())
        val names = base.selectedLeagues.map { it.novigName }
        val before = now + base.daysAhead * 86_400_000L
        val events = novig.events(names, listOf("OPEN_PREGAME", "DELAYED"), null)
        val markets = novig.markets(names, emptyList(), listOf("OPEN_PREGAME", "DELAYED"), before)
        val refs = ArrayList<RefSnapshot>()
        for (source in listOf(KalshiClient(http, json), PolymarketClient(http, json))) {
            for (league in base.selectedLeagues) {
                if (!source.supports(league)) continue
                runCatching { source.odds(league, base) }.getOrNull()?.let { refs += it.copy(provider = source.id) }
            }
        }
        println("LIVE BUDGET board: ${events.size} events, ${markets.size} markets within ${base.daysAhead} days; ${refs.sumOf { it.events.size }} fair-source games")
        for ((lines, props) in listOf(2 to 8, 5 to 24, 50 to 500)) {
            for (fill in listOf(false, true)) {
                val s = base.copy(linesPerGame = lines, propsPerGame = props, fillBudget = fill, maxBooksPerScan = 1_000_000)
                val plan = Planner.plan(events, markets, refs, s, now)
                val priced = plan.markets.filter { it.lineKey != null }
                println("LIVE BUDGET lines $lines, props $props, fill $fill: ${plan.markets.size} planned, ${priced.size} with a fair price " +
                    "(${priced.count { it.spare }} spare), ${plan.matchedEvents} games matched")
            }
        }
    }
}
