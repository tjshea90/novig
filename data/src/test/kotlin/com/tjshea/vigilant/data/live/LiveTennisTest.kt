package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tennis against the REAL Novig catalog and Kalshi's live match markets (both free, no key). Skipped
 * unless VIGILANT_LIVE=1: `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveTennisTest'`.
 */
class LiveTennisTest {

    @Test
    fun `real tennis - Novig's outcomes resolve to a player and Kalshi prices the matches it lists`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient()
        val novig = NovigPublicClient(http, json)
        val leagues = listOf("ATP", "WTA")
        val before = System.currentTimeMillis() + 4 * 86_400_000L
        val events = novig.events(leagues, listOf("OPEN_PREGAME"), before)
        val types = listOf("MONEY", "SPREAD", "TOTAL", "PLAYER_GAMES_WON", "FIRST_SET_MONEYLINE")
        val markets = novig.markets(leagues, types, listOf("OPEN_PREGAME"), before)
        println("LIVE tennis: ${events.size} matches, ${markets.size} markets")

        // Every winner and games-spread outcome names one of the two players.
        val byId = events.associateBy { it.eventId }
        var ok = 0
        var total = 0
        for (m in markets) {
            val mu = byId[m.eventId]?.matchup ?: continue
            val labels = when (m.marketType) {
                "MONEY", "FIRST_SET_MONEYLINE" -> m.outcomes.map { it.name }
                "SPREAD" -> m.outcomes.mapNotNull { NovigText.parseSpreadOutcome(it.name)?.first }
                else -> continue
            }
            total++
            if (labels.size == 2 && TeamMatcher.firstLabelIsAway(labels[0], labels[1], mu.away, mu.home) != null) ok++
            else println("LIVE tennis FAIL: ${m.marketType} ${labels} in '${byId[m.eventId]?.description}'")
        }
        println("LIVE tennis: sides resolved $ok / $total")
        assertTrue("side resolution below 97%: $ok/$total", total == 0 || ok >= total * 0.97)

        // Kalshi's match markets pair with Novig's matches and price their winners.
        val kalshi = KalshiClient(http, json)
        val s = ScanSettings(leagues = leagues.toSet())
        val refs = Leagues.ALL.filter { it.novigName in leagues }.map { kalshi.odds(it, s) }
        refs.forEach { println("LIVE tennis: Kalshi ${it.sportKey} ${it.events.size} matches") }
        val now = System.currentTimeMillis()
        val plan = Planner.plan(events, markets, refs, s, now)
        println("LIVE tennis: ${plan.matchedEvents} of ${plan.events.size} Novig matches paired with Kalshi")
        plan.events.filter { it.refEvent != null }.take(12).forEach { println("LIVE tennis:   ${it.event.description}  <->  ${it.refEvent!!.away} vs ${it.refEvent!!.home}") }
        val priced = plan.markets.filter { it.lineKey != null }
        val batch = novig.books(priced.take(10).map { it.market.marketId })
        val r = Pricing.price(plan, batch.books, s, now)
        r.opportunities.filter { it.evPercent != null }.take(10).forEach { println("LIVE tennis:   ${it.selection} ${it.marketLabel} fair=${"%.3f".format(it.fairProbability)} price=${it.quote?.price} ev=${"%.2f".format(it.evPercent!! * 100)}%") }
        assertTrue("no Novig match paired with Kalshi", plan.matchedEvents > 0 || events.isEmpty())
    }
}
