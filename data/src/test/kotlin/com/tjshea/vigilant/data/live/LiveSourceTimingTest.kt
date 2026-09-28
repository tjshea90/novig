package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How long each free fair-odds source takes per league, as a scan calls them (Tj, 2026-09-28: "now it is reading
 * the API very slow"). Since v0.19.2 a league's bets wait for all its sources, so the slowest one sets when bets
 * first show. Skipped unless VIGILANT_LIVE=1: `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveSourceTimingTest'`.
 */
class LiveSourceTimingTest {

    @Test
    fun `real sources - seconds per league, in scan order`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient()
        val s = ScanSettings(leagues = setOf("NFL", "NCAAF", "MLB", "WNBA", "ATP", "WTA"), daysAhead = 7)
        for ((name, source) in listOf("Kalshi" to KalshiClient(http, json), "Polymarket" to PolymarketClient(http, json))) {
            val t0 = System.currentTimeMillis()
            for (league in s.selectedLeagues) {
                if (!source.supports(league)) continue
                val t = System.currentTimeMillis()
                val snap = runCatching { source.odds(league, s) }
                println("LIVE TIMING $name ${league.novigName}: ${System.currentTimeMillis() - t} ms (done at ${(System.currentTimeMillis() - t0) / 1000.0} s), ${snap.getOrNull()?.events?.size ?: "error " + snap.exceptionOrNull()?.message} events" +
                    if (source is KalshiClient) ", ${league.kalshiSeries.count { s2 -> KalshiClient.familyOf(s2)?.let { it in s.families } == true }} series" else "")
            }
            println("LIVE TIMING $name total ${(System.currentTimeMillis() - t0) / 1000.0} s")
        }
        println("LIVE TIMING leagues: ${Leagues.ALL.map { it.novigName }}")
    }

    /** v0.19.3: every league's game lines first, then the rest (as a scan now asks Kalshi). When is each league's ready? */
    @Test
    fun `real Kalshi - game lines first, then props`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val kalshi = KalshiClient(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val s = ScanSettings(leagues = setOf("NFL", "NCAAF", "MLB", "WNBA", "ATP", "WTA"), daysAhead = 7)
        val t0 = System.currentTimeMillis()
        for (league in s.selectedLeagues) {
            val lines = runCatching { kalshi.lines(league, s) }
            println("LIVE TIMING Kalshi lines ${league.novigName}: ready at ${(System.currentTimeMillis() - t0) / 1000.0} s, ${lines.getOrNull()?.events?.size ?: "error " + lines.exceptionOrNull()?.message} games")
        }
        for (league in s.selectedLeagues) {
            val full = runCatching { kalshi.odds(league, s) }
            println("LIVE TIMING Kalshi full ${league.novigName}: ready at ${(System.currentTimeMillis() - t0) / 1000.0} s, ${full.getOrNull()?.events?.sumOf { it.markets.size } ?: "error " + full.exceptionOrNull()?.message} markets")
        }
    }
}
