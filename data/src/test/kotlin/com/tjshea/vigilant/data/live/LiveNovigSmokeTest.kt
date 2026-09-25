package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.NovigText
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
 * Hits Novig's REAL public API. Skipped unless VIGILANT_LIVE=1, so CI and ckpt never depend on
 * the network. Run by hand: `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveNovigSmokeTest'`.
 */
class LiveNovigSmokeTest {

    @Test
    fun `real catalog - every game-line outcome resolves to a side`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val client = NovigPublicClient(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val leagues = listOf("NFL", "NCAAF", "MLB", "EPL", "MLS", "UFC", "NHL", "NBA")
        val before = System.currentTimeMillis() + 4 * 86_400_000L
        val events = client.events(leagues, listOf("OPEN_PREGAME"), before).associateBy { it.eventId }
        val markets = client.markets(leagues, listOf("MONEY", "SPREAD", "MONEYLINE_3_WAY_WIN"), listOf("OPEN_PREGAME"), before)
        println("LIVE: ${events.size} events, ${markets.size} markets")

        val byType = HashMap<String, IntArray>() // [ok, failed]
        val failures = ArrayList<String>()
        for (m in markets) {
            val e = events[m.eventId] ?: continue
            val mu = e.matchup ?: continue
            val ok = when (m.marketType) {
                "MONEY" -> m.outcomes.size == 2 && TeamMatcher.firstLabelIsAway(m.outcomes[0].name, m.outcomes[1].name, mu.away, mu.home) != null
                "SPREAD" -> {
                    val a = NovigText.parseSpreadOutcome(m.outcomes.getOrNull(0)?.name ?: "")
                    val b = NovigText.parseSpreadOutcome(m.outcomes.getOrNull(1)?.name ?: "")
                    a != null && b != null && TeamMatcher.firstLabelIsAway(a.first, b.first, mu.away, mu.home) != null
                }
                else -> NovigText.threeWayTeam(m.description)?.let { TeamMatcher.labelIsAway(it, mu.away, mu.home) } != null
            }
            val c = byType.getOrPut("${e.league}/${m.marketType}") { IntArray(2) }
            if (ok) c[0]++ else { c[1]++; if (failures.size < 40) failures += "${e.league} ${m.marketType}: '${m.outcomes.joinToString { it.name }}' in '${e.description}' (${m.description})" }
        }
        byType.toSortedMap().forEach { (k, v) -> println("LIVE: $k ok=${v[0]} failed=${v[1]}") }
        failures.forEach { println("LIVE FAIL: $it") }
        val total = byType.values.sumOf { it[0] + it[1] }
        val ok = byType.values.sumOf { it[0] }
        println("LIVE: resolved $ok / $total")
        assertTrue("side resolution below 97%: $ok/$total", total == 0 || ok >= total * 0.97)

        // And the Novig-only board (no reference key) prices real books end to end.
        val s = ScanSettings(leagues = setOf("NFL", "MLB"))
        val plan = Planner.plan(events.values.toList(), markets, emptyMap(), s, System.currentTimeMillis())
        val batch = client.books(plan.marketIds.take(12))
        val r = Pricing.price(plan, batch.books, s, System.currentTimeMillis())
        println("LIVE: planned ${plan.markets.size} Novig-only markets, fetched ${batch.fetched} books (${batch.failed} failed, retryAfter=${batch.retryAfterSeconds}, last=${batch.lastError})")
        r.opportunities.take(8).forEach { println("LIVE BOARD: ${it.eventName} | ${it.selection} | take ${it.ladder.firstOrNull()?.price}") }
        assertTrue(batch.failed == 0)
    }
}
