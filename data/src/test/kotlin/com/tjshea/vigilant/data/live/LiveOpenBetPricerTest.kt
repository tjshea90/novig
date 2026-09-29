package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.OpenBetPricer
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Tracker's bets-only pricing pass (Tj, 2026-09-29: "update the EV for every single open bet, including bets added from vigilant scanner") against
 * the real Novig board and the free fair-odds sources (Polymarket, Kalshi): the first open game of each league becomes a $1 bet on each side of its
 * moneyline, and the pass prices them. Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 bash tools/test.sh :data:test --tests '*LiveOpenBetPricerTest'` (the numbers print as "LIVE PRICER ...").
 */
class LiveOpenBetPricerTest {

    @Test
    fun `real open games are priced from the free fair-odds sources, or say why not`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient()
        val novig = NovigPublicClient(http, json)
        val now = System.currentTimeMillis()
        val leagues = listOf("NFL", "MLB", "NBA", "NHL", "WNBA", "NCAAF")
        val events = novig.events(leagues, listOf(NovigEvent.STATUS_PREGAME), null).filter { it.startsTs > now + 30 * 60_000L }
        val picks = events.groupBy { it.league }.values.map { it.minByOrNull { e -> e.startsTs }!! }
        println("LIVE PRICER games: ${picks.map { "${it.league} ${it.description}" }}")
        assumeTrue(picks.isNotEmpty())
        val markets = novig.markets(picks.map { it.league }.distinct(), listOf("MONEY"), listOf(NovigEvent.STATUS_PREGAME), null)
            .filter { m -> picks.any { it.eventId == m.eventId } }
        val bets = markets.flatMap { m ->
            m.outcomes.map { o ->
                TrackedBet(
                    id = "${m.marketId}/${o.outcomeId}", createdAtMs = now - 3_600_000L, league = picks.first { it.eventId == m.eventId }.league,
                    eventName = picks.first { it.eventId == m.eventId }.description, startsTs = m.startsTs, marketLabel = "Moneyline", selection = o.name,
                    marketId = m.marketId, outcomeId = o.outcomeId, price = 0.5, cost = 0.5, fairAtBet = null, evPercentAtBet = null, stake = 1.0,
                    status = BetStatus.PENDING,
                )
            }
        }
        val file = File.createTempFile("live-bets", ".json").also { it.deleteOnExit() }
        file.writeText(Json.encodeToString(ListSerializer(TrackedBet.serializer()), bets))
        val tracker = BetTracker(file)
        // Tj's feed filters are narrower than the bets: only NFL, a day ahead.
        val settings = ScanSettings(leagues = setOf("NFL"), daysAhead = 1)
        val pricer = OpenBetPricer(tracker, Scanner(novig, betsOnly = true), { listOf(PolymarketClient(http, json), KalshiClient(http, json)) })
        val t0 = System.currentTimeMillis()
        val report = pricer.run(settings, bets.map { it.id })
        println("LIVE PRICER ${bets.size} bets in ${(System.currentTimeMillis() - t0) / 1000.0} s: $report")
        for (b in tracker.all()) {
            println("LIVE PRICER ${b.league} ${b.selection}: ${b.nowEv?.let { "EV vs 50% cost %.1f%% fair %.3f via ${b.nowVia}, ${b.nowBooks} books".format(it * 100, b.nowFair) } ?: "no EV: ${b.nowNote}"}")
        }
    }
}
